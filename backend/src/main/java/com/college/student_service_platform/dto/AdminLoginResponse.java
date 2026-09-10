package com.college.student_service_platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AdminLoginResponse {

    @JsonProperty("access_token")
    private String accessToken;

    public AdminLoginResponse() {
    }

    public AdminLoginResponse(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }
}

