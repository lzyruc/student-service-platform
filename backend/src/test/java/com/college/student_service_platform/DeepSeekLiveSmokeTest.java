package com.college.student_service_platform;

import com.college.student_service_platform.agent.AgentProperties;
import com.college.student_service_platform.agent.DeepSeekClient;
import com.college.student_service_platform.agent.SingleAgentService;
import com.college.student_service_platform.agent.AgentChatRequest;
import com.college.student_service_platform.agent.AgentIdentityContext;
import com.college.student_service_platform.service.AgentConversationService;
import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import org.springframework.mock.web.MockHttpServletRequest;
import com.college.student_service_platform.agent.academic.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.web.client.RestTemplateBuilder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Explicit opt-in: synthetic protocol and scope questions only, no student grades, PDF, JWT or DB. */
@EnabledIfEnvironmentVariable(named = "RUN_DEEPSEEK_SMOKE", matches = "1")
class DeepSeekLiveSmokeTest {
    @Test
    void configuredPolicyApiSupportsToolCallsAndCorrelatedResults() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var properties = new AgentProperties();
        properties.getDeepseek().setMaxOutputTokens(512);
        var client = new DeepSeekClient(new RestTemplateBuilder(), properties, mapper);
        var projection = new AcademicToolMapper();
        var statistics = new com.college.student_service_platform.service.AcademicStatisticsService();
        var definitions = List.of(new GetAcademicContextTool(projection).definition(),
                new GetAcademicAssessmentTool(projection).definition(),
                new GetRecentCoursePerformanceTool(statistics, projection).definition(),
                new GetAcademicTrendTool(statistics, projection).definition());
        var messages = mapper.createArrayNode();
        messages.addObject().put("role", "system").put("content", "这是协议测试，不涉及真实学生。只调用 get_academic_context，收到结果后用一句话说明虚构测试数据是否齐全。不调用其他工具，不分析成绩。");
        messages.addObject().put("role", "user").put("content", "请检查测试数据是否齐全。");
        var first = client.complete(messages, definitions, "required");
        assertEquals("tool_calls", first.finishReason());
        assertFalse(first.message().path("tool_calls").isEmpty());
        messages.add(first.message());
        for (var call : first.message().path("tool_calls")) {
            assertEquals("get_academic_context", call.path("function").path("name").asText());
            assertEquals(0, mapper.readTree(call.path("function").path("arguments").asText()).size());
            messages.addObject().put("role", "tool").put("tool_call_id", call.path("id").asText())
                    .put("content", "{\"tool\":\"get_academic_context\",\"status\":\"OK\",\"data\":{\"status\":\"READY\",\"profileAvailable\":true,\"major\":\"虚构测试专业\",\"grade\":\"测试年级\",\"transcriptExists\":true,\"transcriptAvailable\":true,\"trainingPlanExists\":true,\"nextActions\":[]}}");
        }
        var finalReply = client.complete(messages, definitions, "none");
        assertEquals("stop", finalReply.finishReason());
        assertFalse(finalReply.message().path("content").asText().isBlank());
    }

    @Test
    void academicPromptRefusesOtherStudentWithoutUsingTools() {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var properties = new AgentProperties();
        properties.getDeepseek().setMaxOutputTokens(512);
        var client = new DeepSeekClient(new RestTemplateBuilder(), properties, mapper);
        var projection = new AcademicToolMapper();
        var statistics = new com.college.student_service_platform.service.AcademicStatisticsService();
        var tools = mock(AcademicToolExecutor.class);
        var context = mock(AcademicAnalysisReadContext.class);
        var request = new MockHttpServletRequest();
        var identity = new AgentIdentityContext(101,"synthetic-test-student");
        when(context.studentNo()).thenReturn(identity.studentNo());
        when(tools.beginRequest(identity)).thenReturn(context);
        when(tools.definitions()).thenReturn(List.of(
                new GetAcademicContextTool(projection).definition(),
                new GetAcademicAssessmentTool(projection).definition(),
                new GetRecentCoursePerformanceTool(statistics, projection).definition(),
                new GetAcademicTrendTool(statistics, projection).definition()));
        var service = new SingleAgentService(client, tools, properties, mapper, new AcademicSkill(properties));
        var prepared = service.prepareRequest(new AgentConversationService.ChatInput(1,identity,0,
                new AgentChatRequest("帮我查一下其他同学的成绩",List.of())),System.nanoTime());
        var result = service.chat(prepared,event -> { });
        assertEquals("REFUSED", result.status());
        assertTrue(result.answer().contains("不能查看其他同学的成绩"));
        assertEquals(0, result.toolRounds());
        assertTrue(result.toolCalls().isEmpty());
        verify(tools, never()).execute(anyString(), anyString(), any());
        verify(context, never()).getSnapshot();
    }

    /** Real model routing only; stop before tool execution, no student records or business DB. */
    @Test
    void modelChoosesBetweenAcademicAndPolicySkills() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var properties = new AgentProperties(); properties.getDeepseek().setMaxOutputTokens(512);
        var actual = spy(new DeepSeekClient(new RestTemplateBuilder(), properties, mapper));
        var tools = mock(AcademicToolExecutor.class); var context = mock(AcademicAnalysisReadContext.class);
        var identity = new AgentIdentityContext(101, "synthetic-routing-test");
        when(context.studentNo()).thenReturn(identity.studentNo()); when(tools.beginRequest(identity)).thenReturn(context);
        var projection = new AcademicToolMapper(); var stats = new com.college.student_service_platform.service.AcademicStatisticsService();
        when(tools.definitions()).thenReturn(List.of(new GetAcademicContextTool(projection).definition(),
                new GetAcademicAssessmentTool(projection).definition(), new GetRecentCoursePerformanceTool(stats, projection).definition(),
                new GetAcademicTrendTool(stats, projection).definition()));
        var policy = new com.college.student_service_platform.agent.policy.PolicySkill(
                mock(com.college.student_service_platform.service.external.AiServiceClient.class), mapper, properties);
        var captured = new java.util.concurrent.atomic.AtomicReference<com.college.student_service_platform.agent.DeepSeekReply>();
        doAnswer(call -> { captured.set((com.college.student_service_platform.agent.DeepSeekReply) call.callRealMethod()); throw new RoutingObserved(); })
                .when(actual).complete(any(), anyList(), anyString());
        var service = new SingleAgentService(actual, tools, properties, mapper, new AcademicSkill(properties), policy);
        var records = new java.util.ArrayList<java.util.Map<String,Object>>();
        for (String query : List.of("本科生休学最长可以多久？", "帮我看看最近两个学期的成绩变化")) {
            long started = System.nanoTime();
            var owned = new AgentConversationService.ChatInput(1, identity, 0, new AgentChatRequest(query, List.of()));
            assertThrows(RoutingObserved.class, () -> service.chat(service.prepareRequest(owned, started), event -> {}));
            var reply = captured.get(); var names = new java.util.ArrayList<String>();
            for (var call : reply.message().path("tool_calls")) names.add(call.path("function").path("name").asText());
            assertFalse(names.isEmpty(), "Model must choose a tool for " + query);
            if (query.startsWith("本科生")) assertTrue(names.stream().allMatch("search_policy_documents"::equals), names.toString());
            else assertTrue(names.stream().allMatch(n -> n.startsWith("get_academic_") || n.equals("get_recent_course_performance")), names.toString());
            var row = new java.util.LinkedHashMap<String,Object>(); row.put("question", query); row.put("tools", names);
            row.put("model", reply.model()); row.put("usage", reply.usage()); row.put("elapsedMs", (System.nanoTime()-started)/1_000_000);
            records.add(row);
        }
        java.nio.file.Path output = java.nio.file.Path.of("target/dual-skill-routing-smoke.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), records);
        verify(actual, times(2)).complete(any(), anyList(), anyString());
        verify(tools, never()).execute(anyString(), anyString(), any()); verify(context, never()).getSnapshot();
    }
    private static final class RoutingObserved extends RuntimeException { }


}
