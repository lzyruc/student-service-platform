package com.college.student_service_platform;

import com.college.student_service_platform.agent.*;
import com.college.student_service_platform.agent.academic.*;
import com.college.student_service_platform.agent.policy.PolicySkill;
import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import com.college.student_service_platform.service.AgentConversationService;
import com.college.student_service_platform.service.external.AiServiceClient;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PolicySkillTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AgentIdentityContext identity = new AgentIdentityContext(101, "20240001");
    private PolicySkill skill(AiServiceClient client) {
        var properties = new AgentProperties(); properties.getPolicy().setPromptFile("");
        return new PolicySkill(client, mapper, properties);
    }
    private JsonNode evidence() throws Exception {
        return mapper.readTree("""
            {"status":"success","data":{"passages":[{"policyId":"100","title":"本科生学籍规定",
             "pageRange":"3-4","content":"休学需提交申请。GPA低于2.0时按规定处理。"}]}}
            """);
    }
    private DeepSeekReply call(String tool, String args) {
        ObjectNode message = mapper.createObjectNode().put("role", "assistant").putNull("content");
        message.putArray("tool_calls").addObject().put("id", "call-1").put("type", "function")
                .putObject("function").put("name", tool).put("arguments", args);
        return new DeepSeekReply(message, "tool_calls");
    }
    private DeepSeekReply answer(String text) {
        return new DeepSeekReply(mapper.createObjectNode().put("role", "assistant").put("content", text), "stop");
    }
    @Test void rejectsIdentityPolicyIdsDuplicateAndMalformedArgumentsBeforeRetrieval() {
        var client = mock(AiServiceClient.class); var policy = skill(client);
        for (String args : List.of("{}", "[]", "null", "{\"question\":1}", "{\"question\":\" \"}",
                "{\"question\":\"政策\",\"studentId\":102}", "{\"question\":\"政策\",\"policyIds\":[\"draft\"]}",
                "{\"question\":\"A\",\"question\":\"B\"}", "{\"question\":\"政策\"} {}")) {
            assertEquals("INVALID_ARGUMENT", policy.execute(args, identity).status(), args);
        }
        verifyNoInteractions(client);
        assertFalse(policy.definition().parameters().path("properties").has("studentId"));
        assertTrue(policy.instructions().contains("search_policy_documents"));
    }
    @Test void modelSelectsPolicyToolWithoutTranscriptAndSourcesReachProgress() throws Exception {
        var client = mock(AiServiceClient.class); when(client.retrievePolicies("本科生休学条件")).thenReturn(evidence());
        var tools = mock(AcademicToolExecutor.class); var read = mock(AcademicAnalysisReadContext.class);
        when(read.studentNo()).thenReturn(identity.studentNo()); when(tools.beginRequest(identity)).thenReturn(read);
        when(tools.definitions()).thenReturn(List.of()); var model = mock(DeepSeekClient.class);
        when(model.complete(any(), anyList(), anyString())).thenReturn(
                call(PolicySkill.TOOL, "{\"question\":\"本科生休学条件\"}"),
                answer("需提交休学申请。依据：《本科生学籍规定》第3-4页。"));
        var properties = new AgentProperties();
        var service = new SingleAgentService(model, tools, properties, mapper, new AcademicSkill(properties), skill(client));
        var request = new AgentConversationService.ChatInput(1, identity, 0, new AgentChatRequest("我想休学，需要什么条件", List.of()));
        var events = new ArrayList<AgentExecutionEvent>();
        var result = service.chat(service.prepareRequest(request, System.nanoTime()), events::add);
        assertEquals("COMPLETED", result.status()); assertTrue(result.answer().contains("休学申请"));
        assertEquals(PolicySkill.TOOL, result.toolCalls().get(0).tool());
        verify(tools, never()).execute(anyString(), anyString(), any()); verify(read, never()).getSnapshot();
        verify(client).retrievePolicies("本科生休学条件");
        assertTrue(events.stream().anyMatch(e -> mapper.valueToTree(e).toString().contains("skill-policy")));
        assertTrue(events.stream().anyMatch(e -> mapper.valueToTree(e).toString().contains("本科生学籍规定")));
        verify(model, times(2)).complete(any(), anyList(), anyString());
    }
    @Test void policyGpaThresholdDoesNotBecomeAClaimAboutStudentGpa() throws Exception {
        var client = mock(AiServiceClient.class); when(client.retrievePolicies(anyString())).thenReturn(evidence());
        var tools = mock(AcademicToolExecutor.class); var read = mock(AcademicAnalysisReadContext.class);
        when(read.studentNo()).thenReturn(identity.studentNo()); when(tools.beginRequest(identity)).thenReturn(read);
        when(tools.definitions()).thenReturn(List.of()); var model = mock(DeepSeekClient.class);
        String policyAnswer = "规定中的 GPA 低于2.0条件见《本科生学籍规定》第3-4页。";
        when(model.complete(any(), anyList(), anyString())).thenReturn(call(PolicySkill.TOOL, "{\"question\":\"政策绩点要求\"}"), answer(policyAnswer));
        var properties = new AgentProperties();
        var service = new SingleAgentService(model, tools, properties, mapper, new AcademicSkill(properties), skill(client));
        var owned = new AgentConversationService.ChatInput(1, identity, 0, new AgentChatRequest("政策中的绩点要求", List.of()));
        assertEquals(policyAnswer, service.chat(service.prepareRequest(owned, System.nanoTime()), e -> {}).answer());
        verify(read, never()).getSnapshot();
    }
    @Test void emptyAndFailedRetrievalCannotTurnIntoInventedSuccess() throws Exception {
        for (boolean failed : List.of(false, true)) {
            var client = mock(AiServiceClient.class);
            if (failed) when(client.retrievePolicies(anyString())).thenThrow(new IllegalStateException("downstream failure"));
            else when(client.retrievePolicies(anyString())).thenReturn(mapper.readTree("{\"status\":\"success\",\"data\":{\"passages\":[]}}"));
            var tools = mock(AcademicToolExecutor.class); var read = mock(AcademicAnalysisReadContext.class);
            when(read.studentNo()).thenReturn(identity.studentNo()); when(tools.beginRequest(identity)).thenReturn(read);
            when(tools.definitions()).thenReturn(List.of()); var model = mock(DeepSeekClient.class);
            when(model.complete(any(), anyList(), anyString())).thenReturn(call(PolicySkill.TOOL, "{\"question\":\"休学\"}"), answer("可无条件休学"));
            var properties = new AgentProperties();
            var service = new SingleAgentService(model, tools, properties, mapper, new AcademicSkill(properties), skill(client));
            var owned = new AgentConversationService.ChatInput(1, identity, 0, new AgentChatRequest("休学条件", List.of()));
            var result = service.chat(service.prepareRequest(owned, System.nanoTime()), e -> {});
            assertEquals("DATA_UNAVAILABLE", result.status()); assertFalse(result.answer().contains("无条件"));
            assertTrue(result.answer().contains(failed ? "暂不可用" : "未找到"));
        }
    }
}
