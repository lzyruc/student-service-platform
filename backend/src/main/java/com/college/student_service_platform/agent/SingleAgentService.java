package com.college.student_service_platform.agent;

import com.college.student_service_platform.agent.academic.AcademicToolDtos.ContextOutput;
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
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One Agent with an Academic tool set. All mutable state is local to this HTTP request. */
@Service
public class SingleAgentService {
    private static final Set<String> ANALYSIS_TOOLS = Set.of(
            "get_academic_assessment", "get_recent_course_performance", "get_academic_trend");
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

    public AgentChatResponse chat(AgentChatRequest input, HttpServletRequest request) {
        // Once per HTTP request, outside the loop. Never place this in a bean field or Conversation.
        var analysisContext = tools.beginRequest(request);
        long deadline = System.nanoTime() + properties.getRequestTimeout().toNanos();
        ArrayNode messages = mapper.createArrayNode();
        messages.addObject().put("role", "system").put("content", skill.instructions());
        for (AgentChatRequest.Turn turn : input.history())
            messages.addObject().put("role", turn.role()).put("content", turn.content());
        messages.addObject().put("role", "user").put("content", input.message());
        List<AgentChatResponse.ToolTrace> trace = new ArrayList<>();
        Set<String> callIds = new HashSet<>();
        List<String> nextActions = List.of();
        boolean analysisAvailable = false;
        int rounds = 0, calls = 0;
        var definitions = tools.definitions();
        while (true) {
            checkDeadline(deadline);
            boolean limitReached = rounds >= properties.getMaxToolRounds() || calls >= properties.getMaxToolCalls();
            // Scope refusals need no student data. Evidence is still required for academic conclusions.
            String choice = limitReached ? "none" : "auto";
            DeepSeekReply reply = model.complete(messages, definitions, choice);
            checkDeadline(deadline);
            ObjectNode assistant = reply.message();
            JsonNode requested = assistant.path("tool_calls");
            if (!requested.isArray() || requested.isEmpty()) {
                var nonAnalysis = nonAnalysisReply(assistant.path("content").textValue(), trace, rounds);
                if (nonAnalysis != null) return nonAnalysis;
                if (!analysisAvailable) return noEvidence(trace, nextActions, rounds, limitReached);
                return new AgentChatResponse(assistant.path("content").asText(),
                        limitReached ? "TOOL_LIMIT" : "COMPLETED", rounds, trace);
            }
            if (limitReached) return noEvidence(trace, nextActions, rounds, true);
            // Validate IDs for the entire batch before executing anything.
            for (JsonNode call : requested) {
                if (!callIds.add(call.path("id").asText()))
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "模型返回了重复工具调用标识，请重试");
            }
            messages.add(assistant);
            for (JsonNode call : requested) {
                checkDeadline(deadline);
                String name = call.path("function").path("name").asText();
                AcademicToolResult<?> result;
                if (calls >= properties.getMaxToolCalls()) {
                    result = new AcademicToolResult<>(name, "TOOL_LIMIT", "本请求工具执行次数已达上限", null);
                } else {
                    calls++;
                    result = tools.execute(name, call.path("function").path("arguments").asText(), analysisContext);
                }
                checkDeadline(deadline);
                String dataStatus = result.data() == null ? null : mapper.valueToTree(result.data()).path("status").textValue();
                trace.add(new AgentChatResponse.ToolTrace(result.tool(), result.status(), dataStatus));
                // A real business permission denial stops this request, including the remaining batch.
                if ("ACCESS_DENIED".equals(result.status())) return refused(trace, rounds + 1);
                if ("OK".equals(result.status()) && ANALYSIS_TOOLS.contains(name)) analysisAvailable = true;
                if (result.data() instanceof ContextOutput context) nextActions = context.nextActions();
                messages.addObject().put("role", "tool").put("tool_call_id", call.path("id").asText())
                        .put("content", resultJson(result));
            }
            rounds++;
            if (rounds >= properties.getMaxToolRounds() || calls >= properties.getMaxToolCalls()) {
                messages.addObject().put("role", "system")
                        .put("content", "本请求工具执行已达上限。只基于当前工具结果总结，明确说明未完成的部分，不再请求工具。");
            }
        }
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

    private void checkDeadline(long deadline) {
        if (System.nanoTime() >= deadline)
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "本次 Agent 请求超过耗时预算，请稍后重试");
    }
}
