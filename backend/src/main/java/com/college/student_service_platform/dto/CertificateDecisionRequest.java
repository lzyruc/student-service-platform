package com.college.student_service_platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CertificateDecisionRequest {

    @NotBlank(message = "decision 不能为空")
    private String decision;
    @NotBlank(message = "opinion 不能为空")
    @Size(max = 500)
    private String opinion;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }
}
