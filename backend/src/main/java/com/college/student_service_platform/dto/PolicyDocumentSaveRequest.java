package com.college.student_service_platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public class PolicyDocumentSaveRequest {
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;

    @NotBlank(message = "分类不能为空")
    @Size(max = 50, message = "分类不能超过50个字符")
    private String category;

    @Size(max = 50, message = "适用对象不能超过50个字符")
    private String audience;

    @Size(max = 50, message = "版本号不能超过50个字符")
    private String version;

    private LocalDate effectiveDate;
    private LocalDate expiryDate;

    @Size(max = 20, message = "标签最多20个")
    private List<@Size(max = 50, message = "单个标签不能超过50个字符") String> tags;

    private String content;

    @Size(max = 500, message = "关键词不能超过500个字符")
    private String keywords;

    @Size(max = 500, message = "官方链接不能超过500个字符")
    private String officialUrl;

    @Size(max = 1000, message = "备注不能超过1000个字符")
    private String remark;

    @NotNull(message = "fileId不能为空")
    private Long fileId;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getOfficialUrl() {
        return officialUrl;
    }

    public void setOfficialUrl(String officialUrl) {
        this.officialUrl = officialUrl;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }
}
