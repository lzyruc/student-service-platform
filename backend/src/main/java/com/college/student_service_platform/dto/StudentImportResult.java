package com.college.student_service_platform.dto;

public class StudentImportResult {

    private Integer inserted;
    private Integer updated;

    public StudentImportResult() {
    }

    public StudentImportResult(Integer inserted, Integer updated) {
        this.inserted = inserted;
        this.updated = updated;
    }

    public Integer getInserted() {
        return inserted;
    }

    public Integer getUpdated() {
        return updated;
    }
}

