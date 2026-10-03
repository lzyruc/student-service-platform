package com.college.student_service_platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public class AiAskRequest {

    @NotBlank(message = "question 不能为空")
    @Size(max = 4000, message = "question 不能超过 4000 字")
    private String question;
    private String studentNo;
    // 由 Java 根据数据库状态覆盖，客户端不能指定可检索政策。
    private List<String> policyIds;

    public List<String> getPolicyIds() {
        return policyIds;
    }

    public void setPolicyIds(List<String> policyIds) {
        this.policyIds = policyIds;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }
}
