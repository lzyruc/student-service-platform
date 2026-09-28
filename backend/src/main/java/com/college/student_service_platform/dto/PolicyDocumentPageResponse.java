package com.college.student_service_platform.dto;

import java.util.List;

public record PolicyDocumentPageResponse(
        List<PolicyDocumentItem> records,
        long total,
        int page,
        int pageSize
) {
}
