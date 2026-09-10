package com.college.student_service_platform.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CertificateApplication {
    private Long id;
    private String studentNo;
    private String certificateType;
    private String applyStatus; // 对应数据库的 apply_status
    private String extraData;
    private Long fileId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}