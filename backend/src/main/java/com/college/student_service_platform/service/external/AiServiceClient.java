package com.college.student_service_platform.service.external;

import com.college.student_service_platform.config.ExternalServiceProperties;
import com.college.student_service_platform.dto.AiAskRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AiServiceClient {
    private final RestTemplate restTemplate;
    private final ExternalServiceProperties properties;
    private final ExternalCallExecutor callExecutor;

    public AiServiceClient(RestTemplate restTemplate, ExternalServiceProperties properties,
                           ExternalCallExecutor callExecutor) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.callExecutor = callExecutor;
    }

    public Object ask(AiAskRequest request) {
        String url = properties.getAiService().getBaseUrl() + "/api/student/ai/ask";
        return callExecutor.executeRetryable("AI 问答服务",
                () -> restTemplate.postForObject(url, request, Object.class));
    }

    public Object ingestAll() {
        String url = properties.getAiService().getBaseUrl() + "/api/admin/ai/ingest-all";
        return callExecutor.executeOnce("知识库重建服务",
                () -> restTemplate.postForObject(url, null, Object.class));
    }
}
