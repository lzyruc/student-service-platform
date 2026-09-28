package com.college.student_service_platform.service;

import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.dto.PolicyDocumentItem;
import com.college.student_service_platform.dto.PolicyDocumentPageResponse;
import com.college.student_service_platform.dto.PolicyDocumentSaveRequest;
import com.college.student_service_platform.entity.PolicyDocument;
import com.college.student_service_platform.repository.PolicyDocumentRepository;
import com.college.student_service_platform.repository.PolicyDocumentRepository.PolicyDocumentRow;
import com.college.student_service_platform.repository.PolicyDocumentRepository.PolicyFileInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PolicyDocumentService {
    private static final Set<String> AUDIENCES = Set.of("ALL", "UNDERGRADUATE", "POSTGRADUATE");
    private static final Set<String> DOC_STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> INGEST_STATUSES = Set.of("PENDING", "PROCESSING", "READY", "FAILED");

    private final PolicyDocumentRepository repository;
    private final ObjectMapper objectMapper;
    private final PolicyIngestionService policyIngestionService;
    private final AtomicLong idSequence = new AtomicLong(System.currentTimeMillis());

    public PolicyDocumentService(
            PolicyDocumentRepository repository,
            ObjectMapper objectMapper,
            PolicyIngestionService policyIngestionService
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.policyIngestionService = policyIngestionService;
    }

    @Transactional
    public PolicyDocumentItem create(PolicyDocumentSaveRequest request, String adminUsername) {
        Long creatorId = repository.findAdminIdByUsername(normalizeRequired(adminUsername, "管理员账号不能为空"))
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "当前管理员账号不存在或已禁用"));
        validateRequest(request, null);

        LocalDateTime now = LocalDateTime.now();
        PolicyDocument document = buildDocument(request);
        document.setId(idSequence.incrementAndGet());
        document.setCreatedBy(creatorId);
        document.setDocStatus("DRAFT");
        document.setIngestStatus("PENDING");
        document.setChunkCount(0);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        try {
            repository.insert(document);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "该文件已经登记为政策文档");
        }
        return get(document.getId());
    }

    @Transactional
    public PolicyDocumentItem update(Long id, PolicyDocumentSaveRequest request) {
        PolicyDocumentRow existing = requireDocument(id);
        assertNotProcessing(existing);
        validateRequest(request, id);

        PolicyDocument document = buildDocument(request);
        document.setId(id);
        document.setUpdatedAt(LocalDateTime.now());
        try {
            if (repository.update(document) != 1) {
                throw notFound();
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "该文件已经登记为其他政策文档");
        }
        return get(id);
    }

    public PolicyDocumentItem get(Long id) {
        return toItem(requireDocument(id));
    }

    public PolicyDocumentPageResponse list(
            int page,
            int pageSize,
            String keyword,
            String category,
            String audience,
            String docStatus,
            String ingestStatus
    ) {
        if (page < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page必须大于等于1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "pageSize必须在1到100之间");
        }
        String normalizedAudience = normalizeOptionalUppercase(audience);
        String normalizedDocStatus = normalizeOptionalUppercase(docStatus);
        String normalizedIngestStatus = normalizeOptionalUppercase(ingestStatus);
        validateOptionalEnum(normalizedAudience, AUDIENCES, "audience只能是ALL、UNDERGRADUATE或POSTGRADUATE");
        validateOptionalEnum(normalizedDocStatus, DOC_STATUSES, "docStatus不合法");
        validateOptionalEnum(normalizedIngestStatus, INGEST_STATUSES, "ingestStatus不合法");

        int offset = (page - 1) * pageSize;
        List<PolicyDocumentItem> records = repository.findPage(
                        normalizeOptional(keyword),
                        normalizeOptional(category),
                        normalizedAudience,
                        normalizedDocStatus,
                        normalizedIngestStatus,
                        pageSize,
                        offset
                ).stream()
                .map(this::toItem)
                .toList();
        long total = repository.count(
                normalizeOptional(keyword),
                normalizeOptional(category),
                normalizedAudience,
                normalizedDocStatus,
                normalizedIngestStatus
        );
        return new PolicyDocumentPageResponse(records, total, page, pageSize);
    }

    public PolicyDocumentItem publish(Long id) {
        PolicyDocumentRow row = requireDocument(id);
        assertNotProcessing(row);
        if ("ARCHIVED".equals(row.document().getDocStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "已归档文档不能直接发布");
        }
        if (repository.publish(id) != 1) {
            throw notFound();
        }
        try {
            policyIngestionService.ingestAsync(id);
        } catch (RuntimeException exception) {
            repository.markIngestFailed(id, "无法提交 RAG 入库任务，请稍后重新发布");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "无法提交 RAG 入库任务，请稍后重试");
        }
        return get(id);
    }

    public void delete(Long id) {
        PolicyDocumentRow row = requireDocument(id);
        assertNotProcessing(row);
        policyIngestionService.deleteVectors(row);
        if (repository.delete(id) != 1) {
            throw notFound();
        }
    }

    private void validateRequest(PolicyDocumentSaveRequest request, Long excludedDocumentId) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请求不能为空");
        }
        if (request.getExpiryDate() != null
                && request.getEffectiveDate() != null
                && request.getExpiryDate().isBefore(request.getEffectiveDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "失效日期不能早于生效日期");
        }

        String audience = normalizeDefaultUppercase(request.getAudience(), "ALL");
        if (!AUDIENCES.contains(audience)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "audience只能是ALL、UNDERGRADUATE或POSTGRADUATE");
        }
        validateOfficialUrl(request.getOfficialUrl());

        PolicyFileInfo file = repository.findFileById(request.getFileId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "关联文件不存在，请先上传文件"));
        if (!"policy".equalsIgnoreCase(normalizeOptional(file.businessType()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "关联文件的businessType必须是policy");
        }
        String fileName = normalizeOptional(file.originalName()).toLowerCase(Locale.ROOT);
        String fileType = normalizeOptional(file.fileType()).toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".pdf") && !fileType.contains("pdf")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "知识库政策文件必须是PDF");
        }
        if (repository.isFileLinked(request.getFileId(), excludedDocumentId)) {
            throw new ApiException(HttpStatus.CONFLICT, "该文件已经登记为政策文档");
        }
    }

    private PolicyDocument buildDocument(PolicyDocumentSaveRequest request) {
        PolicyDocument document = new PolicyDocument();
        document.setTitle(normalizeRequired(request.getTitle(), "标题不能为空"));
        document.setCategory(normalizeRequired(request.getCategory(), "分类不能为空"));
        document.setAudience(normalizeDefaultUppercase(request.getAudience(), "ALL"));
        document.setVersion(normalizeDefault(request.getVersion(), "v1.0"));
        document.setEffectiveDate(request.getEffectiveDate());
        document.setExpiryDate(request.getExpiryDate());
        document.setTagsJson(writeTags(request.getTags()));
        document.setContent(emptyToNull(request.getContent()));
        document.setKeywords(emptyToNull(request.getKeywords()));
        document.setOfficialUrl(emptyToNull(request.getOfficialUrl()));
        document.setRemark(emptyToNull(request.getRemark()));
        document.setFileId(request.getFileId());
        return document;
    }

    private PolicyDocumentRow requireDocument(Long id) {
        if (id == null) {
            throw notFound();
        }
        return repository.findById(id).orElseThrow(this::notFound);
    }

    private void assertNotProcessing(PolicyDocumentRow row) {
        if ("PROCESSING".equals(row.document().getIngestStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "文档正在入库，请等待完成后再操作");
        }
    }

    private PolicyDocumentItem toItem(PolicyDocumentRow row) {
        PolicyDocument document = row.document();
        return new PolicyDocumentItem(
                document.getId(),
                document.getTitle(),
                document.getCategory(),
                document.getAudience(),
                document.getVersion(),
                document.getEffectiveDate(),
                document.getExpiryDate(),
                readTags(document.getTagsJson()),
                document.getContent(),
                document.getKeywords(),
                document.getOfficialUrl(),
                document.getRemark(),
                document.getFileId(),
                row.fileName(),
                row.fileType(),
                row.fileSize(),
                document.getCreatedBy(),
                row.creatorName(),
                document.getDocStatus(),
                document.getIngestStatus(),
                document.getIngestError(),
                document.getChunkCount(),
                document.getContentHash(),
                document.getLastIngestedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private String writeTags(List<String> tags) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (tags != null) {
            tags.stream()
                    .map(this::normalizeOptional)
                    .filter(value -> !value.isEmpty())
                    .forEach(normalized::add);
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "标签格式不合法");
        }
    }

    private List<String> readTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private void validateOfficialUrl(String value) {
        String url = normalizeOptional(value);
        if (url.isEmpty()) {
            return;
        }
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "officialUrl必须是有效的HTTP或HTTPS地址");
        }
    }

    private void validateOptionalEnum(String value, Set<String> allowed, String message) {
        if (!value.isEmpty() && !allowed.contains(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeDefault(String value, String defaultValue) {
        String normalized = normalizeOptional(value);
        return normalized.isEmpty() ? defaultValue : normalized;
    }

    private String normalizeDefaultUppercase(String value, String defaultValue) {
        return normalizeDefault(value, defaultValue).toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalUppercase(String value) {
        return normalizeOptional(value).toUpperCase(Locale.ROOT);
    }

    private String emptyToNull(String value) {
        String normalized = normalizeOptional(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "政策文档不存在");
    }
}
