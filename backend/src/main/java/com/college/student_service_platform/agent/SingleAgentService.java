package com.college.student_service_platform.agent;

import com.college.student_service_platform.agent.academic.AcademicToolExecutor;
import com.college.student_service_platform.agent.academic.AcademicSkill;
import com.college.student_service_platform.agent.academic.AcademicToolResult;
import com.college.student_service_platform.common.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import com.college.student_service_platform.service.AgentConversationService;
import com.college.student_service_platform.agent.academic.AcademicToolDtos;

/** One Agent with an Academic tool set. All mutable state is local to this HTTP request. */
@Service
public class SingleAgentService {
    private final DeepSeekClient model;
    private final AcademicToolExecutor tools;
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    private final AcademicSkill skill;

    public SingleAgentService(DeepSeekClient model, AcademicToolExecutor tools, AgentProperties properties, ObjectMapper mapper, AcademicSkill skill) {
        properties.validate();
        this.model = model;
        this.tools = tools;
        this.properties = properties;
        this.mapper = mapper;
        this.skill = skill;
    }

    /** Capture one owned identity and a fresh lazy business context before streaming/queueing. */
    public AgentRequestContext prepareRequest(AgentConversationService.ChatInput owned, long startedAtNanos) {
        return new AgentRequestContext(owned.identity(), new AgentConversationContext(owned.conversationId(), owned.agentRequest().history()),
                owned.agentRequest().message(), tools.beginRequest(owned.identity()), startedAtNanos, properties.getRequestTimeout(),
                properties.getMaxToolRounds(), properties.getMaxToolCalls());
    }

    public AgentChatResponse chat(AgentRequestContext context, Consumer<AgentExecutionEvent> events) {
        context.beginExecution();
        try {
            var response = run(context, events);
            context.succeed(); return response;
        } catch (RuntimeException failure) {
            context.fail(); throw failure;
        }
    }
    private AgentChatResponse run(AgentRequestContext context, Consumer<AgentExecutionEvent> events) {
        events.accept(AgentExecutionEvent.progress("identity", "验证学生身份", "done", "仅查询当前登录学生"));
        ArrayNode messages = AgentPromptBuilder.build(mapper, skill.instructions(), context);
        var definitions = tools.definitions();
        while (true) {
            context.checkDeadline();
            boolean limitReached = context.limitReached();
            // Scope refusals need no student data. Evidence is still required for academic conclusions.
            String choice = limitReached ? "none" : "auto";
            String modelId = "model-" + (context.toolRounds() + 1);
            String modelLabel = context.toolRounds() == 0 ? "理解学业问题" : "整理工具结果";
            events.accept(AgentExecutionEvent.progress(modelId, modelLabel, "running", "正在等待模型响应"));
            DeepSeekReply reply = model.complete(messages, definitions, choice);
            events.accept(AgentExecutionEvent.progress(modelId, modelLabel, "done", "已收到模型响应"));
            context.checkDeadline();
            ObjectNode assistant = reply.message();
            JsonNode requested = assistant.path("tool_calls");
            if (!requested.isArray() || requested.isEmpty()) {
                var nonAnalysis = nonAnalysisReply(assistant.path("content").textValue(), context.trace(), context.toolRounds());
                if (nonAnalysis != null) return nonAnalysis;
                if (!context.business().analysisAvailable()) return noEvidence(context.trace(), context.business().nextActions(), context.toolRounds(), limitReached);
                return new AgentChatResponse(AcademicAnswerGrounding.ground(assistant.path("content").asText(), context.business()),
                        limitReached ? "TOOL_LIMIT" : "COMPLETED", context.toolRounds(), context.trace());
            }
            if (limitReached) return noEvidence(context.trace(), context.business().nextActions(), context.toolRounds(), true);
            // Validate IDs for the entire batch before executing anything.
            for (JsonNode call : requested) {
                context.registerCallId(call.path("id").asText());
            }
            messages.add(assistant);
            for (JsonNode call : requested) {
                context.checkDeadline();
                String name = call.path("function").path("name").asText();
                String eventId = "tool-" + (context.trace().size() + 1);
                String eventLabel = toolLabel(name);
                AcademicToolResult<?> result;
                if (!context.tryStartToolCall()) {
                    result = new AcademicToolResult<>(name, "TOOL_LIMIT", "本请求工具执行次数已达上限", null);
                } else {
                    events.accept(AgentExecutionEvent.progress(eventId, eventLabel, "running", "正在执行受控学业查询"));
                    result = tools.execute(name, call.path("function").path("arguments").asText(), context.business().analysis());
                }
                context.checkDeadline();
                String dataStatus = result.data() == null ? null : mapper.valueToTree(result.data()).path("status").textValue();
                context.addTrace(new AgentChatResponse.ToolTrace(result.tool(), result.status(), dataStatus));
                boolean complete = "OK".equals(result.status()) && !"MISSING_DATA".equals(dataStatus)
                        && !"INSUFFICIENT_DATA".equals(dataStatus);
                events.accept(AgentExecutionEvent.progress(eventId, eventLabel, complete ? "done" : "warning",
                        complete ? "查询完成" : toolStatus(result.status(), dataStatus)));
                if ("OK".equals(result.status())) emitEvidence(result.data(), events);
                // A real business permission denial stops this request, including the remaining batch.
                if ("ACCESS_DENIED".equals(result.status())) return refused(context.trace(), context.toolRounds() + 1);
                context.business().record(result);
                messages.addObject().put("role", "tool").put("tool_call_id", call.path("id").asText())
                        .put("content", resultJson(result));
            }
            context.finishToolRound();
            if (context.limitReached()) {
                messages.addObject().put("role", "system")
                        .put("content", "本请求工具执行已达上限。只基于当前工具结果总结，明确说明未完成的部分，不再请求工具。");
            }
        }
    }

    private String toolLabel(String name) {
        return switch (name) {
            case "get_academic_context" -> "读取学业资料与培养方案";
            case "get_academic_assessment" -> "读取成绩单并分析学业情况";
            case "get_recent_course_performance" -> "分析近期课程表现";
            case "get_academic_trend" -> "查询成绩趋势";
            default -> "检查查询能力";
        };
    }

    private String toolStatus(String status, String dataStatus) {
        if ("ACCESS_DENIED".equals(status)) return "当前请求无权读取";
        if ("SERVICE_UNAVAILABLE".equals(status)) return "学业数据服务暂不可用";
        if ("MISSING_DATA".equals(status) || "MISSING_DATA".equals(dataStatus)) return "资料尚未齐全";
        if ("INSUFFICIENT_DATA".equals(dataStatus)) return "取得部分数据，覆盖不足";
        if ("TOOL_LIMIT".equals(status)) return "已达到本轮查询上限";
        return "本次查询未完成";
    }

    /** UI projections use computed Tool DTO values, not numbers extracted from model prose. */
    private void emitEvidence(Object data, Consumer<AgentExecutionEvent> events) {
        if (data instanceof AcademicToolDtos.AssessmentOutput assessment) {
            var metrics = List.of(metric("官方 GPA", assessment.officialGpaAvailable() ? assessment.officialGpa() : null),
                    metric("已获学分", assessment.totalEarnedCredits()), metric("待核实事项", assessment.issueCourseCount()),
                    metric("预警等级", assessment.warningLevel()));
            events.accept(new AgentExecutionEvent("evidence", Map.of("kind", "overview", "metrics", metrics)));
        } else if (data instanceof AcademicToolDtos.TrendOutput trend) {
            events.accept(new AgentExecutionEvent("evidence", Map.of("kind", "trend", "semesters", trend.semesters().stream().map(semester -> {
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("semester", semester.semester()); row.put("weightedGpa", semester.weightedGpa());
                row.put("gpaCourseCount", semester.gpaCourseCount()); row.put("averageNumericScore", semester.averageNumericScore());
                return row;
            }).toList())));
        } else if (data instanceof AcademicToolDtos.RecentOutput recent) {
            events.accept(new AgentExecutionEvent("evidence", Map.of("kind", "recent", "semester", recent.semester() == null ? "" : recent.semester(),
                    "courses", recent.focusCourses().stream().map(course -> {
                        Map<String,Object> row = new LinkedHashMap<>(); row.put("name", course.name());
                        row.put("score", course.score()); row.put("reason", course.reason()); return row;
                    }).toList())));
        }
    }

    private Map<String,Object> metric(String label, Object value) {
        Map<String,Object> item = new LinkedHashMap<>(); item.put("label", label); item.put("value", value); return item;
    }

    /** Model identifies intent only. Allowed non-analysis replies are fixed server text, never model claims. */
    private AgentChatResponse nonAnalysisReply(String content, List<AgentChatResponse.ToolTrace> trace, int rounds) {
        if (content == null || content.length() > 512 || !content.stripLeading().startsWith("{")) return null;
        try {
            JsonNode decision = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(content);
            if (!decision.isObject() || decision.size() != 2
                    || !"NON_ANALYSIS".equals(decision.path("responseType").textValue())
                    || !decision.path("reason").isTextual()) return null;
            return switch (decision.path("reason").textValue()) {
                case "OTHER_STUDENT" -> refused(trace, rounds);
                case "UNSUPPORTED_CAPABILITY" -> new AgentChatResponse(
                        "目前学业助手支持本人学习概况、课程表现、预警情况和成绩趋势。政策问答或证明办理，请使用服务大厅的对应功能。",
                        "OUT_OF_SCOPE", rounds, trace);
                case "NEEDS_CLARIFICATION" -> new AgentChatResponse(
                        "你想了解自己的学习概况、近期课程、预警情况，还是成绩趋势？请说明想分析的方面。",
                        "NEEDS_CLARIFICATION", rounds, trace);
                default -> null;
            };
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private AgentChatResponse refused(List<AgentChatResponse.ToolTrace> trace, int rounds) {
        return new AgentChatResponse(
                "我只能查询当前登录账号的学业信息，不能查看其他同学的成绩。你可以让我分析自己的学习情况、近期课程或成绩趋势。",
                "REFUSED", rounds, trace);
    }

    private String resultJson(AcademicToolResult<?> result) {
        try { return mapper.writeValueAsString(result); }
        catch (JsonProcessingException e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "学业工具结果无法编码"); }
    }

    private AgentChatResponse noEvidence(List<AgentChatResponse.ToolTrace> trace, List<String> nextActions, int rounds, boolean limit) {
        String answer;
        if (!nextActions.isEmpty()) answer = "暂时无法完成学业分析，请先：" + String.join("；", nextActions) + "。";
        else if (trace.stream().anyMatch(item -> "SERVICE_UNAVAILABLE".equals(item.status())))
            answer = "学业分析服务暂时不可用，请检查 8002 服务并稍后重试。";
        else if (trace.stream().anyMatch(item -> "MISSING_DATA".equals(item.status())))
            answer = "本人学业数据缺失或不可用，请检查成绩单、专业年级和匹配的培养方案。";
        else answer = "本次尚未取得可用学业分析结果，请明确要分析学习概况、近期课程或成绩趋势后重试。";
        return new AgentChatResponse(answer, limit ? "TOOL_LIMIT" : "DATA_UNAVAILABLE", rounds, trace);
    }

}
