package com.college.student_service_platform.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    private final DeepSeek deepseek = new DeepSeek();
    private final Academic academic = new Academic();
    private int maxToolRounds = 4;
    private int maxToolCalls = 12;
    private Duration requestTimeout = Duration.ofSeconds(180);

    public DeepSeek getDeepseek() { return deepseek; }
    public Academic getAcademic() { return academic; }
    public int getMaxToolRounds() { return maxToolRounds; }
    public void setMaxToolRounds(int value) { maxToolRounds = value; }
    public int getMaxToolCalls() { return maxToolCalls; }
    public void setMaxToolCalls(int value) { maxToolCalls = value; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration value) { requestTimeout = value; }

    public void validate() {
        if (maxToolRounds < 1 || maxToolRounds > 4 || maxToolCalls < 1 || maxToolCalls > 16)
            throw new IllegalArgumentException("Agent 工具轮数必须为 1～4，总调用次数必须为 1～16");
        if (!valid(requestTimeout, 600) || !valid(deepseek.connectTimeout, 30)
                || !valid(deepseek.readTimeout, 180) || deepseek.maxOutputTokens < 128 || deepseek.maxOutputTokens > 8192)
            throw new IllegalArgumentException("Agent 超时或输出长度配置不合法");
    }
    private boolean valid(Duration value, int maxSeconds) {
        return value != null && !value.isNegative() && value.toMillis() >= 1
                && value.compareTo(Duration.ofSeconds(maxSeconds)) <= 0;
    }
    public static class Academic {
        private String promptFile = "./src/main/resources/agent/skills/academic.md";
        public String getPromptFile() { return promptFile; }
        public void setPromptFile(String value) { promptFile = value; }
    }

    public static class DeepSeek {
        private String envFile = "../python-services/.env";
        private String apiKey = "";
        private String baseUrl = "";
        private String model = "";
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(60);
        private int maxOutputTokens = 2048;

        public String getEnvFile() { return envFile; }
        public void setEnvFile(String value) { envFile = value; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String value) { apiKey = value; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String value) { baseUrl = value; }
        public String getModel() { return model; }
        public void setModel(String value) { model = value; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration value) { connectTimeout = value; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration value) { readTimeout = value; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int value) { maxOutputTokens = value; }
    }
}
