package com.college.student_service_platform;

import com.college.student_service_platform.config.ExternalServiceProperties;
import com.college.student_service_platform.service.external.AcademicWarningClient;
import com.college.student_service_platform.service.external.ExternalCallExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AcademicWarningClientTest {
    @TempDir Path temporary;

    @Test
    void sendsSavedPdfBytesAndOriginalFilenameToWarningService() throws Exception {
        RestTemplate rest = new RestTemplate();
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getWarningService().setBaseUrl("http://127.0.0.1:8002");
        AcademicWarningClient client = new AcademicWarningClient(rest, properties, new ExternalCallExecutor(properties));
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        Path pdf = temporary.resolve("stored.pdf");
        Files.writeString(pdf, "%PDF-1.7\nstored-student-transcript");
        server.expect(requestTo(properties.getWarningService().getBaseUrl() + "/api/student/warning/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("filename=\"grades.pdf\"")))
                .andExpect(content().string(containsString("stored-student-transcript")))
                .andExpect(content().string(containsString("20260001")))
                .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));
        client.analyzeStoredTranscript(pdf, "grades.pdf", "20260001", "{\"core_courses\":[]}");
        server.verify();
    }

    @Test
    void unreadableTranscriptBecomesActionableValidationError() {
        RestTemplate rest = mock(RestTemplate.class);
        ExternalServiceProperties properties = new ExternalServiceProperties();
        AcademicWarningClient client = new AcademicWarningClient(rest, properties, new ExternalCallExecutor(properties));
        when(rest.postForEntity(anyString(), any(), eq(Object.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> client.analyzeTranscript(
                new MockMultipartFile("file", "scanned.pdf", "application/pdf", new byte[]{1}),
                "20260001", "{\"core_courses\":[]}"));
        assertTrue(error.getMessage().contains("可提取文字"));
        verify(rest, times(1)).postForEntity(anyString(), any(), eq(Object.class));
    }
}
