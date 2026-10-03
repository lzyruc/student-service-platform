package com.college.student_service_platform;

import com.college.student_service_platform.agent.*;
import com.college.student_service_platform.agent.academic.AcademicToolDefinition;
import com.college.student_service_platform.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.hamcrest.Matchers.containsString;

class DeepSeekClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private DeepSeekClient client;
    private MockRestServiceServer server;
    private ArrayNode messages;
    private final List<AcademicToolDefinition> definitions = List.of(new AcademicToolDefinition(
            "get_academic_context", "Check prerequisites", new ObjectMapper().createObjectNode().put("type", "object")));

    @BeforeEach
    void setUp() {
        var p = new AgentProperties();
        p.getDeepseek().setEnvFile("");
        p.getDeepseek().setApiKey("test-only-key");
        var customizer = new MockServerRestTemplateCustomizer();
        client = new DeepSeekClient(new RestTemplateBuilder(customizer), p, mapper);
        server = customizer.getServer();
        messages = mapper.createArrayNode();
        messages.addObject().put("role", "user").put("content", "synthetic test");
    }

    @Test
    void sendsFunctionSchemaDisabledThinkingAndBearerWithoutChangingCallerMessages() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-only-key"))
                .andExpect(jsonPath("$.model").value("deepseek-flash"))
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.tool_choice").value("required"))
                .andExpect(jsonPath("$.tools[0].function.name").value("get_academic_context"))
                .andRespond(withSuccess("""
                    {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
                      "reasoning_content":"must-not-be-forwarded","tool_calls":[{"id":"call_1","type":"function",
                        "function":{"name":"get_academic_context","arguments":"{}"}}]}}]}
                    """, MediaType.APPLICATION_JSON));
        var reply = client.complete(messages, definitions, "required");
        assertEquals("call_1", reply.message().path("tool_calls").get(0).path("id").asText());
        assertFalse(reply.message().has("reasoning_content"));
        assertEquals(1, messages.size());
        server.verify();
    }

    @Test
    void sendsToolResultsWithTheirCallIdsAndReadsFinalAnswer() {
        messages.addObject().put("role", "tool").put("tool_call_id", "call_1").put("content", "{\"status\":\"OK\"}");
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(jsonPath("$.messages[1].tool_call_id").value("call_1"))
                .andExpect(jsonPath("$.tool_choice").value("none"))
                .andRespond(withSuccess("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"样本结果\"}}]}", MediaType.APPLICATION_JSON));
        assertEquals("样本结果", client.complete(messages, definitions, "none").message().path("content").asText());
        server.verify();
    }

    @Test
    void authenticationFailureDoesNotRetryOrExposeProviderBody() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret-provider-key private-student-info"));
        var error = assertThrows(ApiException.class, () -> client.complete(messages, definitions, "required"));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
        assertFalse(error.getMessage().contains("secret-provider-key"));
        assertFalse(error.getMessage().contains("private-student-info"));
        assertNull(error.getCause());
        server.verify();
    }

    @Test
    void timeoutAndRateLimitHaveSafeActionableErrors() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withException(new java.net.SocketTimeoutException("private path secret")));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, assertThrows(ApiException.class,
                () -> client.complete(messages, definitions, "required")).getStatus());
        server.verify();
    }

    @Test
    void missingCredentialsFailBeforeNetworkRequest() {
        var p = new AgentProperties();
        p.getDeepseek().setEnvFile("");
        var customizer = new MockServerRestTemplateCustomizer();
        var empty = new DeepSeekClient(new RestTemplateBuilder(customizer), p, mapper);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ApiException.class, () -> empty.complete(messages, definitions, "required")).getStatus());
        customizer.getServer().verify();
    }

    @Test
    void rejectsTruncatedOutputAndMalformedProtocolBeforeToolDispatch() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"partial\"}}]}", MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ApiException.class,
                () -> client.complete(messages, definitions, "required")).getStatus());
        server.verify();
    }

    @Test
    void duplicateIdsAndBadArgumentsCannotExecuteTools() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess("""
                    {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","tool_calls":[
                      {"id":"same","type":"function","function":{"name":"get_academic_context","arguments":"{}"}},
                      {"id":"same","type":"function","function":{"name":"get_academic_context","arguments":"{}"}}]}}]}
                    """, MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ApiException.class,
                () -> client.complete(messages, definitions, "required")).getStatus());
        server.verify();
    }

    @Test
    void emptyProviderResponseIsA502InsteadOfAnUnhandledError() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess(" ", MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ApiException.class,
                () -> client.complete(messages, definitions, "required")).getStatus());
        server.verify();
    }

    @Test
    void rateLimitDoesNotRetryOrExposeResponseDetails() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("secret provider details"));
        var error = assertThrows(ApiException.class, () -> client.complete(messages, definitions, "required"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertFalse(error.getMessage().contains("secret"));
        server.verify();
    }
}
