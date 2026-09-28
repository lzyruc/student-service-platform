package com.college.student_service_platform.service.external;

import com.college.student_service_platform.config.ExternalServiceProperties;
import com.college.student_service_platform.dto.AiAskRequest;
import com.college.student_service_platform.dto.RagIngestResponse;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.file.Path;

@Service
public class AiServiceClient {
    private final RestTemplate restTemplate;
    private final RestTemplate ingestionRestTemplate;
    private final ExternalServiceProperties properties;
    private final ExternalCallExecutor callExecutor;

    public AiServiceClient(RestTemplate restTemplate, RestTemplateBuilder restTemplateBuilder,
                           ExternalServiceProperties properties,
                           ExternalCallExecutor callExecutor) {
        this.restTemplate = restTemplate;
        this.ingestionRestTemplate = restTemplateBuilder
                .setConnectTimeout(properties.getConnectTimeout())
                .setReadTimeout(properties.getIngestReadTimeout())
                .build();
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

    public RagIngestResponse ingestPolicy(
            Long policyId,
            Path filePath,
            String originalName,
            String title,
            String category,
            String audience,
            String version
    ) {
        String url = properties.getAiServiceBaseUrl()
                + "/api/admin/ai/documents/" + policyId + "/ingest";

        FileSystemResource fileResource = new FileSystemResource(filePath) {
            @Override
            public String getFilename() {
                return originalName;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("title", safe(title));
        body.add("category", safe(category));
        body.add("audience", safe(audience));
        body.add("version", safe(version));
        body.add("originalName", safe(originalName));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        return callExecutor.executeOnce("政策文档 RAG 入库服务",
                () -> ingestionRestTemplate.postForObject(url, request, RagIngestResponse.class));
    }

    public void deletePolicyVectors(Long policyId, String originalName) {
        URI url = UriComponentsBuilder
                .fromHttpUrl(properties.getAiServiceBaseUrl() + "/api/admin/ai/documents/" + policyId)
                .queryParam("originalName", safe(originalName))
                .build()
                .encode()
                .toUri();
        callExecutor.executeOnce("政策文档 RAG 向量删除服务", () -> {
            ingestionRestTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, Object.class);
            return null;
        });
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
