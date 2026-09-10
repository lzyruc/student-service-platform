package com.college.student_service_platform.dto;

import java.time.LocalDateTime;

public class CertificateApplyItem {

    private Long id;
    private String studentNo;
    private String studentName;
    private String name;
    private String className;
    private String certificateType;
    private String applyStatus;
    private String extraData;
    private Long fileId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String lastApproverName;
    private String lastApprovalStatus;
    private String lastOpinion;
    private LocalDateTime lastHandledAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getCertificateType() {
        return certificateType;
    }

    public void setCertificateType(String certificateType) {
        this.certificateType = certificateType;
    }

    public String getApplyStatus() {
        return applyStatus;
    }

    public void setApplyStatus(String applyStatus) {
        this.applyStatus = applyStatus;
    }

    public String getExtraData() {
        return extraData;
    }

    public void setExtraData(String extraData) {
        this.extraData = extraData;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getLastApproverName() {
        return lastApproverName;
    }

    public void setLastApproverName(String lastApproverName) {
        this.lastApproverName = lastApproverName;
    }

    public String getLastApprovalStatus() {
        return lastApprovalStatus;
    }

    public void setLastApprovalStatus(String lastApprovalStatus) {
        this.lastApprovalStatus = lastApprovalStatus;
    }

    public String getLastOpinion() {
        return lastOpinion;
    }

    public void setLastOpinion(String lastOpinion) {
        this.lastOpinion = lastOpinion;
    }

    public LocalDateTime getLastHandledAt() {
        return lastHandledAt;
    }

    public void setLastHandledAt(LocalDateTime lastHandledAt) {
        this.lastHandledAt = lastHandledAt;
    }
}
