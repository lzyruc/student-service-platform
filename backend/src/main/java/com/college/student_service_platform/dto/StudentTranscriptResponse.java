package com.college.student_service_platform.dto;

import java.time.LocalDateTime;

/** 仅向学生返回展示信息，不暴露磁盘路径。 */
public record StudentTranscriptResponse(Long fileId, String originalName, Long fileSize,
                                        LocalDateTime uploadedAt, boolean available) {}
