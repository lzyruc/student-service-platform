package com.college.student_service_platform;

import com.college.student_service_platform.agent.*;
import com.college.student_service_platform.agent.academic.*;
import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SingleAgentServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private DeepSeekClient model;
    private AcademicToolExecutor tools;
    private AgentProperties properties;
    private AcademicAnalysisReadContext context;
    private SingleAgentService service;

    @BeforeEach
    void setUp() {
        model = mock(DeepSeekClient.class);
        tools = mock(AcademicToolExecutor.class);
        properties = new AgentProperties();
        context = mock(AcademicAnalysisReadContext.class);
        when(tools.beginRequest(request)).thenReturn(context);
        when(tools.definitions()).thenReturn(List.of());
        when(tools.execute(anyString(), anyString(), eq(context)))
                .thenAnswer(call -> new AcademicToolResult<>(call.getArgument(0), "OK", null, java.util.Map.of("status", "OK")));
        service = new SingleAgentService(model, tools, properties, mapper, new AcademicSkill(properties));
    }

    @Test
    void multipleCallsAndRoundsUseOneRequestContextAndCorrelateToolResults() {
        List<ArrayNode> sent = new ArrayList<>();
        when(model.complete(any(), anyList(), anyString())).thenAnswer(call -> {
            sent.add(((ArrayNode) call.getArgument(0)).deepCopy());
            return switch (sent.size()) {
                case 1 -> batch("context", "get_academic_context", "{}", "assessment", "get_academic_assessment", "{}");
                case 2 -> batch("recent", "get_recent_course_performance", "{\"limit\":3}");
                default -> answer("已按本轮数据分析");
            };
        });
        var result = service.chat(new AgentChatRequest("帮我分析最近学习情况", List.of()), request);
        assertEquals("COMPLETED", result.status());
        assertEquals("system", sent.get(0).get(0).path("role").asText());
        assertEquals(new AcademicSkill(properties).instructions(), sent.get(0).get(0).path("content").asText());
        assertEquals("已按本轮数据分析", result.answer());
        assertEquals(2, result.toolRounds());
        assertEquals(3, result.toolCalls().size());
        verify(tools, times(1)).beginRequest(request);
        verify(tools).execute("get_academic_assessment", "{}", context);
        assertEquals("context", sent.get(1).get(3).path("tool_call_id").asText());
        assertEquals("assessment", sent.get(1).get(4).path("tool_call_id").asText());
        assertEquals("tool", sent.get(1).get(3).path("role").asText());
        assertTrue(sent.get(1).get(3).path("content").asText().contains("\"status\":\"OK\""));
        verify(model, times(3)).complete(any(), anyList(), eq("auto"));
    }

    @Test
    void nextHttpRequestCreatesFreshContextEvenWithConversationHistory() {
        var second = mock(AcademicAnalysisReadContext.class);
        when(tools.beginRequest(request)).thenReturn(context, second);
        when(tools.execute(anyString(), anyString(), eq(second))).thenAnswer(call ->
                new AcademicToolResult<>(call.getArgument(0), "OK", null, java.util.Map.of("status", "OK")));
        when(model.complete(any(), anyList(), anyString()))
                .thenReturn(batch("one", "get_academic_assessment", "{}"), answer("第一轮"),
                        batch("two", "get_recent_course_performance", "{}"), answer("第二轮"));
        service.chat(new AgentChatRequest("学习情况", List.of()), request);
        service.chat(new AgentChatRequest("最近呢", List.of(new AgentChatRequest.Turn("assistant", "第一轮"))), request);
        verify(tools, times(2)).beginRequest(request);
        verify(tools).execute("get_academic_assessment", "{}", context);
        verify(tools).execute("get_recent_course_performance", "{}", second);
    }

    @Test
    void roundLimitStopsExecutionAndRequestsOneFinalSummary() {
        properties.setMaxToolRounds(2);
        when(model.complete(any(), anyList(), eq("auto"))).thenReturn(
                batch("one", "get_academic_assessment", "{}"), batch("two", "get_academic_trend", "{}"));
        when(model.complete(any(), anyList(), eq("none"))).thenReturn(answer("基于已得数据总结"));
        var result = service.chat(new AgentChatRequest("趋势", List.of()), request);
        assertEquals("TOOL_LIMIT", result.status());
        assertEquals(2, result.toolRounds());
        verify(tools, times(2)).execute(anyString(), anyString(), eq(context));
        verify(model, times(3)).complete(any(), anyList(), anyString());
    }

    @Test
    void callLimitDoesNotExecuteExcessCallsButProvidesAResultForEveryDeclaredId() {
        properties.setMaxToolCalls(1);
        List<ArrayNode> sent = new ArrayList<>();
        when(model.complete(any(), anyList(), anyString())).thenAnswer(call -> {
            sent.add(((ArrayNode) call.getArgument(0)).deepCopy());
            return sent.size() == 1 ? batch("one", "get_academic_assessment", "{}", "two", "get_academic_trend", "{}") : answer("已有结果");
        });
        var result = service.chat(new AgentChatRequest("分析", List.of()), request);
        assertEquals("TOOL_LIMIT", result.status());
        verify(tools, times(1)).execute(anyString(), anyString(), eq(context));
        assertEquals("two", sent.get(1).get(4).path("tool_call_id").asText());
        assertTrue(sent.get(1).get(4).path("content").asText().contains("TOOL_LIMIT"));
    }

    @Test
    void noSuccessfulAnalysisNeverReturnsInventedStudentGrades() {
        when(model.complete(any(), anyList(), anyString())).thenReturn(answer("你的 GPA 是 4.0，风险为零"));
        var result = service.chat(new AgentChatRequest("学习情况", List.of()), request);
        assertEquals("DATA_UNAVAILABLE", result.status());
        assertFalse(result.answer().contains("4.0"));
        verify(tools, never()).execute(anyString(), anyString(), any());
    }

    @Test
    void invalidToolResultsAreFedBackWithoutCallingOtherApis() {
        doReturn(new AcademicToolResult<>("unknown", "UNKNOWN_TOOL", "未注册", null))
                .when(tools).execute("query_mysql", "{}", context);
        when(model.complete(any(), anyList(), anyString()))
                .thenReturn(batch("one", "query_mysql", "{}"), answer("虚构分析"));
        var result = service.chat(new AgentChatRequest("帮我分析", List.of()), request);
        assertEquals("DATA_UNAVAILABLE", result.status());
        assertEquals("UNKNOWN_TOOL", result.toolCalls().get(0).status());
    }

    @Test
    void missingPrerequisitesProduceDeterministicNextActionInsteadOfHallucinatedScores() {
        var data = new AcademicToolDtos.ContextOutput("MISSING_DATA", true, "计算机", "2026",
                false, false, null, true, "v1", null, List.of("上传成绩单 PDF"));
        doReturn(new AcademicToolResult<>("get_academic_context", "OK", null, data))
                .when(tools).execute("get_academic_context", "{}", context);
        when(model.complete(any(), anyList(), anyString()))
                .thenReturn(batch("one", "get_academic_context", "{}"), answer("你所有课都通过了"));
        var result = service.chat(new AgentChatRequest("分析", List.of()), request);
        assertTrue(result.answer().contains("上传成绩单"));
        assertFalse(result.answer().contains("所有课都通过"));
    }

    @Test
    void modelFailuresAreNotRetriedAndAuthenticationHappensBeforeModelAccess() {
        when(model.complete(any(), anyList(), anyString())).thenThrow(new ApiException(HttpStatus.GATEWAY_TIMEOUT, "timeout"));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT,
                assertThrows(ApiException.class, () -> service.chat(new AgentChatRequest("分析", List.of()), request)).getStatus());
        verify(model, times(1)).complete(any(), anyList(), anyString());
        reset(model);
        when(tools.beginRequest(request)).thenThrow(new ApiException(HttpStatus.FORBIDDEN, "no student"));
        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ApiException.class, () -> service.chat(new AgentChatRequest("分析", List.of()), request)).getStatus());
        verifyNoInteractions(model);
    }

    @Test
    void exhaustedRequestBudgetStopsBeforeCallingModel() {
        properties.setRequestTimeout(Duration.ofNanos(1));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT,
                assertThrows(ApiException.class, () -> service.chat(new AgentChatRequest("分析", List.of()), request)).getStatus());
        verifyNoInteractions(model);
    }

    @Test
    void repeatedCallIdsAreRejectedBeforeSecondBatchExecution() {
        when(model.complete(any(), anyList(), anyString()))
                .thenReturn(batch("same", "get_academic_assessment", "{}"), batch("same", "get_academic_trend", "{}"));
        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(ApiException.class, () -> service.chat(new AgentChatRequest("分析", List.of()), request)).getStatus());
        verify(tools, times(1)).execute(anyString(), anyString(), eq(context));
    }

    @Test
    void otherStudentRequestReturnsFixedRefusalWithoutReadingAcademicData() {
        when(model.complete(any(), anyList(), eq("auto")))
                .thenReturn(answer("{\"responseType\":\"NON_ANALYSIS\",\"reason\":\"OTHER_STUDENT\"}"));
        var result = service.chat(new AgentChatRequest("帮我查一下其他同学的成绩", List.of()), request);
        assertEquals("REFUSED", result.status());
        assertTrue(result.answer().contains("不能查看其他同学的成绩"));
        assertEquals(0, result.toolRounds());
        assertTrue(result.toolCalls().isEmpty());
        verify(tools, never()).execute(anyString(), anyString(), any());
        verify(context, never()).getSnapshot();
    }

    @Test
    void unsupportedCapabilityAndClarificationRemainDistinctFromMissingData() {
        var cases = java.util.Map.of("UNSUPPORTED_CAPABILITY", "OUT_OF_SCOPE",
                "NEEDS_CLARIFICATION", "NEEDS_CLARIFICATION");
        for (var item : cases.entrySet()) {
            when(model.complete(any(), anyList(), eq("auto"))).thenReturn(answer(
                    "{\"responseType\":\"NON_ANALYSIS\",\"reason\":\"" + item.getKey() + "\"}"));
            var result = service.chat(new AgentChatRequest("下一步呢", List.of()), request);
            assertEquals(item.getValue(), result.status());
            assertFalse(result.answer().contains("本次尚未取得"));
        }
        verify(tools, never()).execute(anyString(), anyString(), any());
        verify(context, never()).getSnapshot();
    }

    @Test
    void malformedControlsCannotSupplyCustomAnswersOrGradesWithoutEvidence() {
        var invalid = List.of(
                "{\"responseType\":\"NON_ANALYSIS\",\"reason\":\"COMPLETED\"}",
                "{\"responseType\":\"NON_ANALYSIS\",\"reason\":\"OTHER_STUDENT\",\"answer\":\"GPA 4.0\"}",
                "{\"responseType\":\"NON_ANALYSIS\",\"reason\":\"OTHER_STUDENT\",\"studentId\":123}",
                "{\"responseType\":\"NON_ANALYSIS\",\"reason\":\"OTHER_STUDENT\"} GPA 4.0",
                "{\"responseType\":\"NON_ANALYSIS\",\"reason\":17}",
                "{broken JSON: GPA 4.0}");
        for (var text : invalid) {
            when(model.complete(any(), anyList(), eq("auto"))).thenReturn(answer(text));
            var result = service.chat(new AgentChatRequest("分析学习情况", List.of()), request);
            assertEquals("DATA_UNAVAILABLE", result.status(), text);
            assertFalse(result.answer().contains("4.0"), text);
        }
        verify(tools, never()).execute(anyString(), anyString(), any());
    }

    @Test
    void refusalIsNotReplacedByMissingPrerequisiteNextActions() {
        var data = new AcademicToolDtos.ContextOutput("MISSING_DATA", true, "计算机", "2026",
                false, false, null, true, "v1", null, List.of("上传成绩单 PDF"));
        doReturn(new AcademicToolResult<>("get_academic_context", "OK", null, data))
                .when(tools).execute("get_academic_context", "{}", context);
        when(model.complete(any(), anyList(), anyString())).thenReturn(
                batch("one", "get_academic_context", "{}"),
                answer("{\"responseType\":\"NON_ANALYSIS\",\"reason\":\"OTHER_STUDENT\"}"));
        var result = service.chat(new AgentChatRequest("查询其他同学", List.of()), request);
        assertEquals("REFUSED", result.status());
        assertFalse(result.answer().contains("上传成绩单"));
        assertEquals(1, result.toolCalls().size());
        verify(context, never()).getSnapshot();
    }

    @Test
    void businessAccessDenialStopsRemainingToolsAndNeverAsksModelToOverrideIt() {
        doReturn(new AcademicToolResult<>("get_academic_assessment", "ACCESS_DENIED", "permission denied", null))
                .when(tools).execute("get_academic_assessment", "{}", context);
        when(model.complete(any(), anyList(), anyString())).thenReturn(batch(
                "one", "get_academic_context", "{}", "denied", "get_academic_assessment", "{}",
                "later", "get_academic_trend", "{}"));
        var result = service.chat(new AgentChatRequest("分析学习情况", List.of()), request);
        assertEquals("REFUSED", result.status());
        assertEquals(1, result.toolRounds());
        assertEquals("ACCESS_DENIED", result.toolCalls().get(1).status());
        verify(tools, never()).execute(eq("get_academic_trend"), anyString(), any());
        verify(model, times(1)).complete(any(), anyList(), anyString());
    }

    private DeepSeekReply batch(String... triplets) {
        ObjectNode message = mapper.createObjectNode().put("role", "assistant").putNull("content");
        ArrayNode calls = message.putArray("tool_calls");
        for (int i = 0; i < triplets.length; i += 3) {
            ObjectNode call = calls.addObject().put("id", triplets[i]).put("type", "function");
            call.putObject("function").put("name", triplets[i + 1]).put("arguments", triplets[i + 2]);
        }
        return new DeepSeekReply(message, "tool_calls");
    }
    private DeepSeekReply answer(String content) {
        return new DeepSeekReply(mapper.createObjectNode().put("role", "assistant").put("content", content), "stop");
    }
}
