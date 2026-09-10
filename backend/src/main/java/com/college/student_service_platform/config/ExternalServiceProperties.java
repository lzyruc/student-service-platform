package com.college.student_service_platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "external")
public class ExternalServiceProperties {

    private final ServiceEndpoint aiService = new ServiceEndpoint();
    private final ServiceEndpoint warningService = new ServiceEndpoint();
    private final Http http = new Http();

    public ServiceEndpoint getAiService() {
        return aiService;
    }

    public ServiceEndpoint getWarningService() {
        return warningService;
    }

    public Http getHttp() {
        return http;
    }

    public String getAiServiceBaseUrl() {
        return normalizeUrl(aiService.baseUrl);
    }

    public String getWarningServiceBaseUrl() {
        return normalizeUrl(warningService.baseUrl);
    }

    public Duration getConnectTimeout() {
        return http.connectTimeout;
    }

    public Duration getReadTimeout() {
        return http.readTimeout;
    }

    public int getMaxAttempts() {
        return Math.max(1, Math.min(3, http.maxAttempts));
    }

    private String normalizeUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("外部服务地址不能为空");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public static class ServiceEndpoint {
        private String baseUrl;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Http {
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(30);
        private int maxAttempts = 2;

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }
}
