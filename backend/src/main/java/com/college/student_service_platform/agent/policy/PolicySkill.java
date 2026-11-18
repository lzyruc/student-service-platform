package com.college.student_service_platform.agent.policy;

import com.college.student_service_platform.agent.AgentIdentityContext;
import com.college.student_service_platform.agent.AgentProperties;
import com.college.student_service_platform.agent.academic.*;
import com.college.student_service_platform.service.external.AiServiceClient;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.util.*;

/** Reuses the existing RAG retrieval and Agent tool protocol. Holds no request state. */
@Component
public final class PolicySkill {
    public static final String TOOL = "search_policy_documents";
    private static final String DEFAULT_FILE = "./src/main/resources/agent/skills/policy.md";
    private final AiServiceClient client;
    private final ObjectMapper mapper;
    private final String instructions;
    public PolicySkill(AiServiceClient client, ObjectMapper mapper, AgentProperties properties) {
        this.client = client;
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.instructions = AcademicSkill.loadPrompt(properties.getPolicy().getPromptFile(), DEFAULT_FILE,
                "/agent/skills/policy.md", "# Policy Question Answering Skill");
    }
    public String instructions() { return instructions; }
    public AcademicToolDefinition definition() {
        var schema = mapper.createObjectNode().put("type", "object").put("additionalProperties", false);
        schema.putObject("properties").putObject("question").put("type", "string")
                .put("minLength", 1).put("maxLength", 4000).put("description", "结合当前追问整理的完整政策问题");
        schema.putArray("required").add("question");
        return new AcademicToolDefinition(TOOL, "检索已发布校园政策原文和页码，用于政策、学籍、考试纪律及校历问答，不需要成绩单。", schema);
    }
    public AcademicToolResult<?> execute(String arguments, AgentIdentityContext identity) {
        Objects.requireNonNull(identity, "authenticated identity");
        String question;
        try {
            if (arguments == null || arguments.length() > 16_384) throw new IllegalArgumentException();
            JsonNode args = mapper.readTree(arguments);
            if (args == null || !args.isObject() || args.size() != 1 || !args.path("question").isTextual())
                throw new IllegalArgumentException();
            question = args.path("question").asText().strip();
            if (question.isEmpty() || question.length() > 4000) throw new IllegalArgumentException();
        } catch (java.io.IOException | IllegalArgumentException e) {
            return new AcademicToolResult<>(TOOL, "INVALID_ARGUMENT", "只接受非空 question，最多4000字", null);
        }
        try {
            JsonNode response = client.retrievePolicies(question);
            if (response == null || !"success".equals(response.path("status").asText())) throw new IllegalStateException();
            JsonNode data = response.path("data"), rows = data.path("passages");
            if (!rows.isArray() || rows.size() > 4) throw new IllegalStateException();
            var passages = new ArrayList<Passage>();
            for (JsonNode row : rows) {
                String id = text(row, "policyId", 40), title = text(row, "title", 500);
                String page = text(row, "pageRange", 80), content = text(row, "content", 6000);
                if (id.isBlank() || title.isBlank() || content.isBlank()) throw new IllegalStateException();
                passages.add(new Passage(id, title, page, content));
            }
            return new AcademicToolResult<>(TOOL, "OK", null,
                    new Output(passages.isEmpty() ? "EMPTY" : "OK", List.copyOf(passages)));
        } catch (RuntimeException e) {
            return new AcademicToolResult<>(TOOL, "SERVICE_UNAVAILABLE", "政策检索暂不可用，请稍后重试", null);
        }
    }
    private String text(JsonNode row, String key, int max) {
        JsonNode value = row.path(key);
        if (!value.isTextual() || value.asText().length() > max) throw new IllegalStateException();
        return value.asText();
    }
    public record Passage(String policyId, String title, String pageRange, String content) { }
    public record Output(String status, List<Passage> passages) { }
}
