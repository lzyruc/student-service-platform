package com.college.student_service_platform.agent;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Only imports shared DeepSeek settings, without logging file contents or credentials. */
public final class DeepSeekSettings {
    private static final Set<String> KEYS = Set.of("DEEPSEEK_API_KEY", "DEEPSEEK_BASE_URL", "DEEPSEEK_MODEL");
    private final String apiKey;
    private final URI completionsUri;
    private final String model;

    public DeepSeekSettings(AgentProperties.DeepSeek properties) {
        Map<String, String> file = readEnv(properties.getEnvFile());
        apiKey = choose(properties.getApiKey(), file.get("DEEPSEEK_API_KEY"), "");
        model = choose(properties.getModel(), file.get("DEEPSEEK_MODEL"), "deepseek-flash");
        String base = choose(properties.getBaseUrl(), file.get("DEEPSEEK_BASE_URL"), "https://api.deepseek.com")
                .replaceAll("/+$", "");
        try {
            URI uri = URI.create(base);
            if ((!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
                throw new IllegalArgumentException();
            completionsUri = URI.create(base + "/chat/completions");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("DeepSeek API 地址配置不合法");
        }
        if (apiKey.contains("\n") || apiKey.contains("\r") || model.length() > 128)
            throw new IllegalArgumentException("DeepSeek Key 或模型配置不合法");
    }

    public String apiKey() { return apiKey; }
    public URI completionsUri() { return completionsUri; }
    public String model() { return model; }

    private String choose(String override, String file, String fallback) {
        return override != null && !override.isBlank() ? override.trim()
                : file != null && !file.isBlank() ? file.trim() : fallback;
    }

    private Map<String, String> readEnv(String filename) {
        Map<String, String> values = new HashMap<>();
        if (filename == null || filename.isBlank()) return values;
        try {
            Path path = Path.of(filename);
            if (!Files.exists(path)) return values;
            for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = raw.replace("\uFEFF", "").trim();
                if (line.startsWith("export ")) line = line.substring(7).trim();
                int equal = line.indexOf('=');
                if (equal < 0) continue;
                String key = line.substring(0, equal).trim();
                if (!KEYS.contains(key)) continue;
                String value = line.substring(equal + 1).trim();
                if (value.startsWith("\"") || value.startsWith("'")) {
                    int end = value.indexOf(value.charAt(0), 1);
                    if (end < 0) throw new IllegalArgumentException("DeepSeek 配置必须使用单行完整值");
                    String trailing = value.substring(end + 1).trim();
                    if (!trailing.isEmpty() && !trailing.startsWith("#"))
                        throw new IllegalArgumentException("DeepSeek 配置格式不合法");
                    value = value.substring(1, end);
                } else {
                    int comment = value.indexOf(" #");
                    if (comment >= 0) value = value.substring(0, comment).trim();
                }
                if (value.contains("${")) throw new IllegalArgumentException("共用 DeepSeek 配置请填写直接值，环境变量通过启动环境覆盖");
                values.put(key, value);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取共用 DeepSeek 配置文件，请检查路径和权限");
        }
        return values;
    }
}
