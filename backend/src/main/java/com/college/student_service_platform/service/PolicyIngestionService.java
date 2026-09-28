package com.college.student_service_platform.service;

import com.college.student_service_platform.dto.RagIngestResponse;
import com.college.student_service_platform.entity.FileRecord;
import com.college.student_service_platform.repository.PolicyDocumentRepository;
import com.college.student_service_platform.repository.PolicyDocumentRepository.PolicyDocumentRow;
import com.college.student_service_platform.service.external.AiServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PolicyIngestionService {
    private static final Logger log = LoggerFactory.getLogger(PolicyIngestionService.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final PolicyDocumentRepository repository;
    private final FileService fileService;
    private final AiServiceClient aiServiceClient;

    public PolicyIngestionService(
            PolicyDocumentRepository repository,
            FileService fileService,
            AiServiceClient aiServiceClient
    ) {
        this.repository = repository;
        this.fileService = fileService;
        this.aiServiceClient = aiServiceClient;
    }

    @Async("policyIngestionExecutor")
    public void ingestAsync(Long policyId) {
        try {
            PolicyDocumentRow row = repository.findById(policyId)
                    .orElseThrow(() -> new IllegalStateException("政策文档不存在"));
            FileRecord file = fileService.getFileById(row.document().getFileId());
            Path filePath = fileService.getFilePath(file);
            if (!Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
                throw new IllegalStateException("政策文件不存在或不可读");
            }

            RagIngestResponse response = aiServiceClient.ingestPolicy(
                    policyId,
                    filePath,
                    file.getOriginalName(),
                    row.document().getTitle(),
                    row.document().getCategory(),
                    row.document().getAudience(),
                    row.document().getVersion()
            );
            RagIngestResponse.Data data = validateResponse(policyId, response);
            repository.markIngestReady(policyId, data.chunkCount(), data.contentHash());
            log.info("Policy document {} ingested into RAG with {} chunks", policyId, data.chunkCount());
        } catch (Exception exception) {
            String errorMessage = summarizeError(exception);
            repository.markIngestFailed(policyId, errorMessage);
            log.error("Policy document {} RAG ingestion failed: {}", policyId, errorMessage, exception);
        }
    }

    public void deleteVectors(PolicyDocumentRow row) {
        aiServiceClient.deletePolicyVectors(row.document().getId(), row.fileName());
    }

    private RagIngestResponse.Data validateResponse(Long policyId, RagIngestResponse response) {
        if (response == null || !"success".equalsIgnoreCase(response.status()) || response.data() == null) {
            throw new IllegalStateException("RAG 服务返回无效结果");
        }
        RagIngestResponse.Data data = response.data();
        if (data.policyId() == null || !policyId.equals(data.policyId())) {
            throw new IllegalStateException("RAG 服务返回的 policyId 不一致");
        }
        if (data.chunkCount() == null || data.chunkCount() <= 0) {
            throw new IllegalStateException("RAG 服务未生成有效文本块");
        }
        if (data.contentHash() == null || data.contentHash().length() != 64) {
            throw new IllegalStateException("RAG 服务未返回有效内容哈希");
        }
        return data;
    }

    private String summarizeError(Exception exception) {
        Throwable current = exception;
        String message = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage().trim();
            }
            current = current.getCause();
        }
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
