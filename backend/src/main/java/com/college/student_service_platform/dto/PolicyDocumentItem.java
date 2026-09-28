package com.college.student_service_platform.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PolicyDocumentItem(
        Long id,
        String title,
        String category,
        String audience,
        String version,
        LocalDate effectiveDate,
        LocalDate expiryDate,
        List<String> tags,
        String content,
        String keywords,
        String officialUrl,
        String remark,
        Long fileId,
        String fileName,
        String fileType,
        Long fileSize,
        Long createdBy,
        String creatorName,
        String docStatus,
        String ingestStatus,
        String ingestError,
        Integer chunkCount,
        String contentHash,
        LocalDateTime lastIngestedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
