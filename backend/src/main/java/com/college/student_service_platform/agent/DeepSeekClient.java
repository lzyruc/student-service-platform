package com.college.student_service_platform.agent;

import com.college.student_service_platform.agent.academic.AcademicToolDefinition;
import com.college.student_service_platform.common.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Approved model DTO transmission; non-streaming calls with no automatic retry or raw error disclosure. */
@Service
public class DeepSeekClient {
    private final RestTemplate rest;
    private final ObjectMapper mapper;
    private final DeepSeekSettings settings;
    private final int maxTokens;

    public DeepSeekClient(RestTemplateBuilder builder, AgentProperties properties, ObjectMapper mapper) {
        properties.validate();
        settings = new DeepSeekSettings(properties.getDeepseek());
        rest = builder.setConnectTimeout(properties.getDeepseek().getConnectTimeout())
                .setReadTimeout(properties.getDeepseek().getReadTimeout()).build();
        maxTokens = properties.getDeepseek().getMaxOutputTokens();
        this.mapper = mapper;
    }

    public DeepSeekReply complete(ArrayNode messages, List<AcademicToolDefinition> definitions, String toolChoice) {
        if (settings.apiKey().isBlank())
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "请在 python-services/.env 配置 DEEPSEEK_API_KEY 后重启后端");
        ObjectNode body = mapper.createObjectNode().put("model", settings.model()).put("stream", false)
                .put("temperature", 0).put("max_tokens", maxTokens).put("tool_choice", toolChoice);
        body.putObject("thinking").put("type", "disabled");
        body.set("messages", messages.deepCopy());
        ArrayNode functions = body.putArray("tools");
        for (AcademicToolDefinition definition : definitions) {
            ObjectNode function = functions.addObject().put("type", "function").putObject("function");
            function.put("name", definition.name()).put("description", definition.description());
            function.set("parameters", definition.parameters());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(settings.apiKey());
        try {
            String raw = rest.postForObject(settings.completionsUri(), new HttpEntity<>(body, headers), String.class);
            return parse(raw);
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "DeepSeek 连接超时或网络不可用，请稍后重试");
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403)
                throw new ApiException(HttpStatus.BAD_GATEWAY, "DeepSeek 认证失败，请检查共用配置中的 API Key");
            if (status == 429)
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DeepSeek 请求受限，请稍后重试");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DeepSeek 请求失败，请检查模型配置或稍后重试");
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DeepSeek 服务暂时不可用");
        }
    }

    private DeepSeekReply parse(String raw) {
        try {
            if (raw == null || raw.isBlank()) throw new IllegalArgumentException();
            JsonNode choice = mapper.readTree(raw).path("choices").path(0);
            JsonNode original = choice.path("message");
            String reason = choice.path("finish_reason").asText();
            if (!original.isObject() || !"assistant".equals(original.path("role").asText())) throw new IllegalArgumentException();
            if ("length".equals(reason))
                throw new ApiException(HttpStatus.BAD_GATEWAY, "DeepSeek 输出达到长度上限，请缩小问题范围后重试");
            JsonNode calls = original.path("tool_calls");
            if (!calls.isMissingNode() && !calls.isNull() && !calls.isArray()) throw new IllegalArgumentException();
            ObjectNode message = mapper.createObjectNode().put("role", "assistant");
            JsonNode content = original.path("content");
            if (!content.isMissingNode() && !content.isNull() && !content.isTextual()) throw new IllegalArgumentException();
            if (content.isTextual()) message.put("content", content.textValue());
            else message.putNull("content");
            if (calls.isArray() && !calls.isEmpty()) {
                if (!"tool_calls".equals(reason) || calls.size() > 16) throw new IllegalArgumentException();
                Set<String> ids = new HashSet<>();
                ArrayNode sanitized = message.putArray("tool_calls");
                for (JsonNode call : calls) {
                    JsonNode function = call.path("function");
                    String id = call.path("id").asText();
                    if (!call.path("id").isTextual() || id.isBlank() || id.length() > 128 || !ids.add(id)
                            || !"function".equals(call.path("type").asText()) || !function.path("name").isTextual()
                            || function.path("name").asText().length() > 128 || !function.path("arguments").isTextual()
                            || function.path("arguments").asText().length() > 16_384) throw new IllegalArgumentException();
                    ObjectNode clean = sanitized.addObject().put("id", id).put("type", "function");
                    clean.putObject("function").put("name", function.path("name").textValue())
                            .put("arguments", function.path("arguments").textValue());
                }
            } else if (!"stop".equals(reason) || !content.isTextual() || content.textValue().isBlank()) {
                throw new IllegalArgumentException();
            }
            return new DeepSeekReply(message, reason);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DeepSeek 返回格式不完整，请稍后重试");
        }
    }
}
