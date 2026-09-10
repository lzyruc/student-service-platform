package com.college.student_service_platform.dto;

public class FileUploadResponse {

    private Long id;
    private String originalName;
    private String storedName;
    private String filePath;
    private String fileType;
    private Long fileSize;
    private String businessType;

    public FileUploadResponse() {
    }

    public FileUploadResponse(Long id, String originalName, String storedName, String filePath,
                              String fileType, Long fileSize, String businessType) {
        this.id = id;
        this.originalName = originalName;
        this.storedName = storedName;
        this.filePath = filePath;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.businessType = businessType;
    }

    public Long getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileType() {
        return fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public String getBusinessType() {
        return businessType;
    }
}