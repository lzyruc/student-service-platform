package com.college.student_service_platform.service.external;

import com.college.student_service_platform.config.ExternalServiceProperties;
import com.college.student_service_platform.common.ExternalServiceException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

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

    public Object analyzeTranscript(MultipartFile file, String studentNo, String trainingPlanJson) throws IOException {
        ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
        return analyzeResource(fileResource, studentNo, trainingPlanJson);
    }

    public Object analyzeStoredTranscript(Path path, String originalName, String studentNo, String trainingPlanJson) {
        FileSystemResource resource = new FileSystemResource(path) {
            @Override
            public String getFilename() {
                return originalName;
            }
        };
        return analyzeResource(resource, studentNo, trainingPlanJson);
    }

    private Object analyzeResource(Resource fileResource, String studentNo, String trainingPlanJson) {
        String url = properties.getWarningService().getBaseUrl() + "/api/student/warning/analyze";
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("studentNo", studentNo);
        if (trainingPlanJson != null && !trainingPlanJson.isBlank()) {
            body.add("training_plan", trainingPlanJson);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        try {
            return callExecutor.executeOnce("成绩单分析服务", () -> {
                ResponseEntity<Object> response = restTemplate.postForEntity(url, requestEntity, Object.class);
                return response.getBody();
            });
        } catch (ExternalServiceException e) {
            if (e.getCause() instanceof HttpClientErrorException error && error.getStatusCode().value() == 422) {
                throw new IllegalArgumentException("成绩单未能解析出有效课程，请上传可提取文字的 PDF 或检查文件内容");
            }
            throw e;
        }
    }
}
