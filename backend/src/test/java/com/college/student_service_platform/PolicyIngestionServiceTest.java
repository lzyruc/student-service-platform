package com.college.student_service_platform;

import com.college.student_service_platform.dto.RagIngestResponse;
import com.college.student_service_platform.entity.FileRecord;
import com.college.student_service_platform.entity.PolicyDocument;
import com.college.student_service_platform.repository.PolicyDocumentRepository;
import com.college.student_service_platform.repository.PolicyDocumentRepository.PolicyDocumentRow;
import com.college.student_service_platform.service.FileService;
import com.college.student_service_platform.service.PolicyIngestionService;
import com.college.student_service_platform.service.external.AiServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyIngestionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void successfulIngestionWritesReadyStatus() throws Exception {
        long policyId = 100L;
        Path pdf = Files.writeString(tempDir.resolve("policy.pdf"), "fake pdf content");
        Fixture fixture = fixture(policyId, pdf);
        when(fixture.aiServiceClient().ingestPolicy(
                policyId, pdf, "policy.pdf", "本科政策", "学籍管理", "UNDERGRADUATE", "v1.0"
        )).thenReturn(new RagIngestResponse(
                "success",
                "ok",
                new RagIngestResponse.Data(policyId, 12, "a".repeat(64), "policy.pdf")
        ));

        fixture.service().ingestAsync(policyId);

        verify(fixture.repository()).markIngestReady(policyId, 12, "a".repeat(64));
        verify(fixture.repository(), never()).markIngestFailed(org.mockito.ArgumentMatchers.eq(policyId), anyString());
    }

    @Test
    void failedIngestionWritesFailureReason() throws Exception {
        long policyId = 101L;
        Path pdf = Files.writeString(tempDir.resolve("failed.pdf"), "fake pdf content");
        Fixture fixture = fixture(policyId, pdf);
        when(fixture.aiServiceClient().ingestPolicy(
                policyId, pdf, "policy.pdf", "本科政策", "学籍管理", "UNDERGRADUATE", "v1.0"
        )).thenThrow(new IllegalStateException("RAG service unavailable"));

        fixture.service().ingestAsync(policyId);

        verify(fixture.repository()).markIngestFailed(policyId, "RAG service unavailable");
        verify(fixture.repository(), never()).markIngestReady(
                org.mockito.ArgumentMatchers.eq(policyId),
                org.mockito.ArgumentMatchers.anyInt(),
                anyString()
        );
    }

    private Fixture fixture(long policyId, Path pdf) {
        PolicyDocumentRepository repository = mock(PolicyDocumentRepository.class);
        FileService fileService = mock(FileService.class);
        AiServiceClient aiServiceClient = mock(AiServiceClient.class);

        PolicyDocument document = new PolicyDocument();
        document.setId(policyId);
        document.setFileId(10L);
        document.setTitle("本科政策");
        document.setCategory("学籍管理");
        document.setAudience("UNDERGRADUATE");
        document.setVersion("v1.0");
        when(repository.findById(policyId)).thenReturn(Optional.of(
                new PolicyDocumentRow(document, "policy.pdf", "application/pdf", 100L, "admin")
        ));

        FileRecord file = new FileRecord();
        file.setId(10L);
        file.setOriginalName("policy.pdf");
        file.setStoredName("policy.pdf");
        when(fileService.getFileById(10L)).thenReturn(file);
        when(fileService.getFilePath(file)).thenReturn(pdf);

        return new Fixture(
                new PolicyIngestionService(repository, fileService, aiServiceClient),
                repository,
                aiServiceClient
        );
    }

    private record Fixture(
            PolicyIngestionService service,
            PolicyDocumentRepository repository,
            AiServiceClient aiServiceClient
    ) {
    }
}
