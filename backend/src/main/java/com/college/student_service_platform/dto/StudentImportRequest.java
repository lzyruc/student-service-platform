package com.college.student_service_platform.dto;

import java.util.List;

public class StudentImportRequest {

    private List<StudentImportItem> students;

    public List<StudentImportItem> getStudents() {
        return students;
    }

    public void setStudents(List<StudentImportItem> students) {
        this.students = students;
    }
}

