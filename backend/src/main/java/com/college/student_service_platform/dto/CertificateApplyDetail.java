package com.college.student_service_platform.dto;

import java.util.List;

public class CertificateApplyDetail {

    private CertificateApplyItem apply;
    private List<ApprovalTaskItem> tasks;

    public CertificateApplyItem getApply() {
        return apply;
    }

    public void setApply(CertificateApplyItem apply) {
        this.apply = apply;
    }

    public List<ApprovalTaskItem> getTasks() {
        return tasks;
    }

    public void setTasks(List<ApprovalTaskItem> tasks) {
        this.tasks = tasks;
    }
}
