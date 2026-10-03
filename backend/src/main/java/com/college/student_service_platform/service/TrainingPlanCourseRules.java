package com.college.student_service_platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

/** 培养方案管理和学业分析共享同一套课程类别规则。 */
public final class TrainingPlanCourseRules {
    private TrainingPlanCourseRules() {}

    public static String normalizeCategory(Object value) {
        String category = value == null ? "" : String.valueOf(value).replaceAll("\\s+", "");
        return switch (category) {
            case "部类核心课", "部类共同" -> "部类共同课";
            default -> category;
        };
    }

    public static boolean isCoreCourse(Object category) {
        String normalized = normalizeCategory(category);
        return normalized.contains("核心")
                || normalized.equals("部类基础课")
                || normalized.equals("部类共同课")
                || normalized.equals("思想政治理论课");
    }

    public static String normalizeJson(ObjectMapper mapper, String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        if (root == null) throw new IllegalArgumentException("培养方案内容不能为空");
        JsonNode plan = root.isArray() && !root.isEmpty() ? root.get(0) : root;
        JsonNode courses = plan.path("courses");
        if (courses.isArray()) {
            for (JsonNode course : courses) {
                if (course instanceof ObjectNode object && object.has("category")) {
                    object.put("category", normalizeCategory(object.path("category").asText("")));
                }
            }
        }
        return mapper.writeValueAsString(root);
    }
}
