package com.college.student_service_platform.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StudentStage {
    private Long id;
    private String studentNo;
    private Integer stageId;
    private LocalDateTime startDate;
    private String stageStatus; // 对应数据库的 stage_status
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}