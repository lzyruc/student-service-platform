package com.college.student_service_platform.agent;

import com.college.student_service_platform.agent.academic.AcademicToolExecutor;
import com.college.student_service_platform.agent.policy.PolicySkill;
import org.springframework.beans.factory.annotation.Autowired;
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

/** One Agent with Academic and Policy skills. All mutable state is local to this HTTP request. */
@Service
public class SingleAgentService {
    private final DeepSeekClient model;
    private final AcademicToolExecutor tools;
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    private final AcademicSkill skill;
    private final PolicySkill policy;

    public SingleAgentService(DeepSeekClient model, AcademicToolExecutor tools, AgentProperties properties, ObjectMapper mapper, AcademicSkill skill) {
        this(model, tools, properties, mapper, skill, null);
    }

    @Autowired
    public SingleAgentService(DeepSeekClient model, AcademicToolExecutor tools, AgentProperties properties,
                              ObjectMapper mapper, AcademicSkill skill, PolicySkill policy) {
        properties.validate();
        this.model = model;
        this.tools = tools;
        this.properties = properties;
        this.mapper = mapper;
        this.skill = skill;
        this.policy = policy;
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
        String instructions = policy == null ? skill.instructions() : """
                # Student Service Agent
                你是校园助手，包含 Academic Skill 和 Policy Skill。根据当前问题与历史指代选择工具，可按需组合，所有查询只读。
                本人课程、GPA、趋势和预警使用学业工具；政策、休复学规定、考试纪律和校历使用 search_policy_documents。
                政策查询不需要成绩单，不先检查学业资料。政策问答已经接入，不属于 UNSUPPORTED_CAPABILITY。
                每个 Skill 的规则只适用于对应业务；最终直接回答问题，不介绍路由过程或罗列能力。
                """ + "\n" + skill.instructions() + "\n\n" + policy.instructions();
        ArrayNode messages = AgentPromptBuilder.build(mapper, instructions, context);
        var definitions = new java.util.ArrayList<>(tools.definitions());
        if (policy != null) definitions.add(policy.definition());
        while (true) {
            context.checkDeadline();
            boolean limitReached = context.limitReached();
            // Scope refusals need no student data. Evidence is still required for academic conclusions.
            String choice = limitReached ? "none" : "auto";
            String modelId = "model-" + (context.toolRounds() + 1);
            String modelLabel = context.toolRounds() == 0 ? "理解问题并选择技能" : "整理工具结果";
            events.accept(AgentExecutionEvent.progress(modelId, modelLabel, "running", "正在等待模型响应"));
            DeepSeekReply reply = model.complete(messages, definitions, choice);
            events.accept(AgentExecutionEvent.progress(modelId, modelLabel, "done", "已收到模型响应"));
            context.checkDeadline();
            ObjectNode assistant = reply.message();
            JsonNode requested = assistant.path("tool_calls");
            if (!requested.isArray() || requested.isEmpty()) {
                var nonAnalysis = nonAnalysisReply(assistant.path("content").textValue(), context.trace(), context.toolRounds());
                if (nonAnalysis != null) return nonAnalysis;
                if (!context.business().evidenceAvailable()) return noEvidence(context.trace(), context.business().nextActions(), context.toolRounds(), limitReached);
                return new AgentChatResponse(context.business().analysisAvailable()
                                ? AcademicAnswerGrounding.ground(assistant.path("content").asText(), context.business())
                                : assistant.path("content").asText(),
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
                    boolean policyCall = PolicySkill.TOOL.equals(name) && policy != null;
                    events.accept(AgentExecutionEvent.progress("skill-" + (policyCall ? "policy" : "academic"),
                            policyCall ? "政策问答" : "学业分析", "done", policyCall ? "已选择政策 Skill" : "已选择学业 Skill"));
                    events.accept(AgentExecutionEvent.progress(eventId, eventLabel, "running", policyCall ? "正在检索政策原文" : "正在查询学业数据"));
                    String arguments = call.path("function").path("arguments").asText();
                    result = policyCall ? policy.execute(arguments, context.identity())
                            : tools.execute(name, arguments, context.business().analysis());
                }
                context.checkDeadline();
                String dataStatus = result.data() == null ? null : mapper.valueToTree(result.data()).path("status").textValue();
                context.addTrace(new AgentChatResponse.ToolTrace(result.tool(), result.status(), dataStatus));
                boolean complete = "OK".equals(result.status()) && !"MISSING_DATA".equals(dataStatus)
                        && !"INSUFFICIENT_DATA".equals(dataStatus) && !"EMPTY".equals(dataStatus);
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
            case "search_policy_documents" -> "检索政策与来源";
            case "get_academic_context" -> "读取学业资料与培养方案";
            case "get_academic_assessment" -> "读取成绩单并分析学业情况";
            case "get_recent_course_performance" -> "分析近期课程表现";
            case "get_academic_trend" -> "查询成绩趋势";
            default -> "检查查询能力";
        };
    }

    private String toolStatus(String status, String dataStatus) {
        if ("ACCESS_DENIED".equals(status)) return "当前请求无权读取";
        if ("SERVICE_UNAVAILABLE".equals(status)) return "查询服务暂不可用";
        if ("EMPTY".equals(dataStatus)) return "未找到相关政策";
        if ("MISSING_DATA".equals(status) || "MISSING_DATA".equals(dataStatus)) return "资料尚未齐全";
        if ("INSUFFICIENT_DATA".equals(dataStatus)) return "取得部分数据，覆盖不足";
        if ("TOOL_LIMIT".equals(status)) return "已达到本轮查询上限";
        return "本次查询未完成";
    }

    /** UI projections use computed Tool DTO values, not numbers extracted from model prose. */
    private void emitEvidence(Object data, Consumer<AgentExecutionEvent> events) {
        if (data instanceof PolicySkill.Output policyData && !policyData.passages().isEmpty()) {
            events.accept(new AgentExecutionEvent("evidence", Map.of("kind", "policy", "sources",
                    policyData.passages().stream().map(p -> Map.of("title", p.title(), "pageRange", p.pageRange())).distinct().toList())));
        } else if (data instanceof AcademicToolDtos.AssessmentOutput assessment) {
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
                        "证明办理请使用服务大厅的电子证明功能。",
                        "OUT_OF_SCOPE", rounds, trace);
                case "NEEDS_CLARIFICATION" -> new AgentChatResponse(
                        "你想了解课程成绩、学业情况，还是校园政策？",
                        "NEEDS_CLARIFICATION", rounds, trace);
                default -> null;
            };
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private AgentChatResponse refused(List<AgentChatResponse.ToolTrace> trace, int rounds) {
        return new AgentChatResponse(
                "不能查看其他同学的成绩，只能查询你自己的学业信息。",
                "REFUSED", rounds, trace);
    }

    private String resultJson(AcademicToolResult<?> result) {
        try { return mapper.writeValueAsString(result); }
        catch (JsonProcessingException e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "学业工具结果无法编码"); }
    }

    private AgentChatResponse noEvidence(List<AgentChatResponse.ToolTrace> trace, List<String> nextActions, int rounds, boolean limit) {
        String answer;
        if (trace.stream().anyMatch(item -> PolicySkill.TOOL.equals(item.tool()))) {
            boolean failed = trace.stream().anyMatch(item -> PolicySkill.TOOL.equals(item.tool()) && "SERVICE_UNAVAILABLE".equals(item.status()));
            boolean empty = trace.stream().anyMatch(item -> PolicySkill.TOOL.equals(item.tool()) && "EMPTY".equals(item.dataStatus()));
            String message = failed ? "政策检索暂不可用，请稍后重试。" : empty ? "知识库中暂未找到相关政策。" : "本次未完成政策查询，请重试。";
            return new AgentChatResponse(message, limit ? "TOOL_LIMIT" : "DATA_UNAVAILABLE", rounds, trace);
        }
        if (!nextActions.isEmpty()) answer = "请先：" + String.join("；", nextActions) + "。";
        else if (trace.stream().anyMatch(item -> "SERVICE_UNAVAILABLE".equals(item.status())))
            answer = "学业分析暂不可用，请稍后重试。";
        else if (trace.stream().anyMatch(item -> "MISSING_DATA".equals(item.status())))
            answer = "缺少学业资料，请检查成绩单和匹配的培养方案。";
        else answer = "暂未取得分析结果，请说明想了解的学业问题后重试。";
        return new AgentChatResponse(answer, limit ? "TOOL_LIMIT" : "DATA_UNAVAILABLE", rounds, trace);
    }

}
