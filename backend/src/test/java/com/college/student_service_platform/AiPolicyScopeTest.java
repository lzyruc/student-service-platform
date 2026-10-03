package com.college.student_service_platform;

import com.college.student_service_platform.config.ExternalServiceProperties;
import com.college.student_service_platform.dto.AiAskRequest;
import com.college.student_service_platform.repository.PolicyDocumentRepository;
import com.college.student_service_platform.service.external.AiServiceClient;
import com.college.student_service_platform.service.external.ExternalCallExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.ClientHttpRequestFactory;
import com.college.student_service_platform.common.ExternalServiceException;
import java.io.IOException;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiPolicyScopeTest {
    @Test
    void generationTimeoutDoesNotRetryTheSameModelRequest() throws Exception {
        ClientHttpRequestFactory factory = mock(ClientHttpRequestFactory.class);
        when(factory.createRequest(any(java.net.URI.class), any(org.springframework.http.HttpMethod.class)))
                .thenThrow(new IOException("read timed out"));
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getAiService().setBaseUrl("http://rag-service");
        properties.getHttp().setMaxAttempts(3);
        PolicyDocumentRepository repository = mock(PolicyDocumentRepository.class);
        when(repository.findQueryablePolicyIds()).thenReturn(List.of("100"));
        List<java.time.Duration> timeouts = new java.util.ArrayList<>();
        RestTemplateBuilder builder = new RestTemplateBuilder().requestFactory(settings -> {
            timeouts.add(settings.readTimeout());
            return factory;
        });
        AiServiceClient client = new AiServiceClient(builder,
                properties, new ExternalCallExecutor(properties), repository);
        AiAskRequest request = new AiAskRequest();
        request.setQuestion("政策问题");
        assertEquals(List.of(java.time.Duration.ofSeconds(90), java.time.Duration.ofMinutes(5)), timeouts);

        assertThrows(ExternalServiceException.class, () -> client.ask(request));
        verify(factory, times(1)).createRequest(any(java.net.URI.class), any(org.springframework.http.HttpMethod.class));
    }

    @Test
    void ignoresClientPolicyIdsAndForwardsOnlyDatabaseReadyPolicies() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getAiService().setBaseUrl("http://rag-service");
        PolicyDocumentRepository repository = mock(PolicyDocumentRepository.class);
        when(repository.findQueryablePolicyIds()).thenReturn(List.of("100"));
        RestTemplateBuilder builder = new RestTemplateBuilder().requestFactory(settings -> http.getRequestFactory());
        AiServiceClient client = new AiServiceClient(builder, properties,
                new ExternalCallExecutor(properties), repository);
        AiAskRequest request = new AiAskRequest();
        request.setQuestion("休学规定是什么？");
        request.setPolicyIds(List.of("unauthorized-draft"));
        server.expect(requestTo("http://rag-service/api/student/ai/ask"))
                .andExpect(content().json("""
                        {"question":"休学规定是什么？", "policyIds":["100"]}
                        """))
                .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));

        client.ask(request);
        server.verify();
    }
}
