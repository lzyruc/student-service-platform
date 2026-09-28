package com.college.student_service_platform.dto;

public record RagIngestResponse(
        String status,
        String message,
        Data data
) {
    public record Data(
            Long policyId,
            Integer chunkCount,
            String contentHash,
            String source
    ) {
    }
}
