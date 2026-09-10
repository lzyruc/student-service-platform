package com.college.student_service_platform.service.external;

import com.college.student_service_platform.config.ExternalServiceProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class AcademicWarningClient {
    private final RestTemplate restTemplate;
    private final ExternalServiceProperties properties;
    private final ExternalCallExecutor callExecutor;

    public AcademicWarningClient(RestTemplate restTemplate, ExternalServiceProperties properties,
                                 ExternalCallExecutor callExecutor) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.callExecutor = callExecutor;
    }

    public Object getTrainingPlan() {
        String url = properties.getWarningService().getBaseUrl() + "/api/admin/warning/training_plan";
        return callExecutor.executeRetryable("培养方案查询服务",
                () -> restTemplate.getForObject(url, Object.class));
    }

    public Object saveTrainingPlan(Object request) {
        String url = properties.getWarningService().getBaseUrl() + "/api/admin/warning/training_plan";
        return callExecutor.executeOnce("培养方案保存服务",
                () -> restTemplate.postForObject(url, request, Object.class));
    }

    public Object analyzeTranscript(MultipartFile file, String studentNo, String trainingPlanJson) throws IOException {
        String url = properties.getWarningService().getBaseUrl() + "/api/student/warning/analyze";
        ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("studentNo", studentNo);
        if (trainingPlanJson != null && !trainingPlanJson.isBlank()) {
            body.add("training_plan", trainingPlanJson);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        return callExecutor.executeOnce("成绩单分析服务", () -> {
            ResponseEntity<Object> response = restTemplate.postForEntity(url, requestEntity, Object.class);
            return response.getBody();
        });
    }
}
