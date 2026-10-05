package com.college.student_service_platform.agent.academic;

import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import com.college.student_service_platform.service.AcademicAnalysisService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Explicit whitelist; not a reflective controller/API dispatcher. Stores no request/conversation context. */
@Component
public class AcademicToolExecutor {
    private final AcademicAnalysisService analysis;
    private final ObjectMapper argumentMapper;
    private final Map<String, AcademicTool<?, ?>> tools;

    public AcademicToolExecutor(AcademicAnalysisService analysis, ObjectMapper mapper,
                                GetAcademicContextTool context, GetAcademicAssessmentTool assessment,
                                GetRecentCoursePerformanceTool recent, GetAcademicTrendTool trend) {
        this.analysis = analysis;
        this.argumentMapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        Map<String, AcademicTool<?, ?>> registry = new LinkedHashMap<>();
        for (AcademicTool<?, ?> tool : List.of(context, assessment, recent, trend))
            registry.put(tool.definition().name(), tool);
        this.tools = java.util.Collections.unmodifiableMap(registry);
    }

    /**
     * Future Agent service calls once per authenticated HTTP request, before the Tool Calling Loop.
     * Keep the returned context in a method-local variable and reuse it throughout that loop.
     * Calling again always creates a fresh context, including follow-ups in the same conversation.
     */
    public AcademicAnalysisReadContext beginRequest(HttpServletRequest request) {
        return analysis.createReadContext(request);
    }

    public AcademicAnalysisReadContext beginRequest(com.college.student_service_platform.agent.AgentIdentityContext identity) {
        return analysis.createReadContext(identity);
    }

    public List<AcademicToolDefinition> definitions() {
        return tools.values().stream().map(AcademicTool::definition).toList();
    }

    public AcademicToolResult<?> execute(String name, String arguments, AcademicAnalysisReadContext context) {
        Objects.requireNonNull(context, "authenticated request context");
        if (!tools.containsKey(name)) return error("unknown", "UNKNOWN_TOOL", "未注册的学业工具");
        if (arguments == null || arguments.length() > 16_384)
            return error(name, "INVALID_ARGUMENT", "工具参数为空或过长");
        try {
            return execute(name, argumentMapper.readTree(arguments), context);
        } catch (JsonProcessingException e) {
            return error(name, "INVALID_ARGUMENT", "工具参数不是有效 JSON，或包含重复字段");
        }
    }

    public AcademicToolResult<?> execute(String name, JsonNode arguments, AcademicAnalysisReadContext context) {
        Objects.requireNonNull(context, "authenticated request context");
        AcademicTool<?, ?> tool = tools.get(name);
        if (tool == null) return error("unknown", "UNKNOWN_TOOL", "未注册的学业工具");
        return run(tool, arguments, context);
    }

    private <I, O> AcademicToolResult<?> run(AcademicTool<I, O> tool, JsonNode arguments,
                                          AcademicAnalysisReadContext context) {
        String name = tool.definition().name();
        I input;
        try {
            input = tool.parseInput(arguments);
        } catch (IllegalArgumentException e) {
            return error(name, "INVALID_ARGUMENT", e.getMessage());
        }
        try {
            return new AcademicToolResult<>(name, "OK", null, tool.execute(input, context));
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND)
                return error(name, "MISSING_DATA", "本人学业数据缺失或不可用，请检查成绩单、学生档案和培养方案");
            if (e.getStatus() == HttpStatus.UNAUTHORIZED || e.getStatus() == HttpStatus.FORBIDDEN)
                return error(name, "ACCESS_DENIED", "无权读取学业数据，请重新登录本人学生账号");
            return unavailable(name);
        } catch (RuntimeException e) {
            // Raw transport/SQL/IO exceptions can contain paths or credentials; never feed them to the model.
            return unavailable(name);
        }
    }

    private AcademicToolResult<?> unavailable(String name) {
        return error(name, "SERVICE_UNAVAILABLE", "学业数据暂时无法分析，请检查 8002 服务及统计版本后重试");
    }

    private AcademicToolResult<?> error(String name, String status, String message) {
        return new AcademicToolResult<>(name, status, message, null);
    }
}
