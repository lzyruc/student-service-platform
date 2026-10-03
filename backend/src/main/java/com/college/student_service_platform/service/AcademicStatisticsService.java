package com.college.student_service_platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.Set;

/** Read-only projections of the deterministic statistics already stored in one chat snapshot. */
@Service
public class AcademicStatisticsService {
    private static final Set<String> COURSE_LISTS = Set.of(
            "failed_courses", "low_score_courses", "unknown_score_courses", "focus_courses");

    public JsonNode recentPerformance(AcademicAnalysisReadContext context, String semester, int limit) {
        if (!"latest".equals(semester) && !"previous".equals(semester)) {
            throw new IllegalArgumentException("semester 只允许 latest 或 previous");
        }
        if (limit < 1 || limit > 10) throw new IllegalArgumentException("limit 必须为 1～10");
        JsonNode statistics = requireStatistics(context);
        JsonNode label = statistics.path(semester + "_semester");
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("requested_semester", semester);
        result.set("rules", statistics.path("rules").deepCopy());
        result.set("analysis_notes", statistics.path("analysis_notes").deepCopy());
        result.set("semester", label.isTextual() ? label.deepCopy() : JsonNodeFactory.instance.nullNode());
        if (!label.isTextual()) {
            result.put("status", "INSUFFICIENT_DATA");
            result.put("message", "没有可用的" + ("latest".equals(semester) ? "最近" : "上一") + "成绩学期");
            return result;
        }
        for (JsonNode summary : statistics.path("semesters")) {
            if (!label.asText().equals(summary.path("semester").asText())) continue;
            ObjectNode selected = (ObjectNode) summary.deepCopy();
            ArrayNode allFocus = (ArrayNode) selected.path("focus_courses");
            result.put("focus_course_count", allFocus.size());
            ArrayNode focus = result.putArray("focus_courses");
            for (int i = 0; i < Math.min(limit, allFocus.size()); i++) focus.add(allFocus.get(i).deepCopy());
            result.put("truncated", allFocus.size() > limit);
            result.put("status", "OK");
            result.set("summary", selected);
            // Return the complete lists later through dedicated Tool DTOs; limit applies to focus evidence.
            selected.remove("focus_courses");
            return result;
        }
        throw new IllegalStateException("学期统计缺少所选学期");
    }

    public JsonNode trend(AcademicAnalysisReadContext context, int lastSemesters) {
        if (lastSemesters < 2 || lastSemesters > 8) {
            throw new IllegalArgumentException("lastSemesters 必须为 2～8");
        }
        JsonNode statistics = requireStatistics(context);
        JsonNode labels = statistics.path("valid_semesters");
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("requested_semester_count", lastSemesters);
        result.set("rules", statistics.path("rules").deepCopy());
        result.set("analysis_notes", statistics.path("analysis_notes").deepCopy());
        ArrayNode periods = result.putArray("semesters");
        int start = Math.max(0, labels.size() - lastSemesters);
        int gpaComparisons = 0, scoreComparisons = 0;
        for (int i = start; i < labels.size(); i++) {
            for (JsonNode summary : statistics.path("semesters")) {
                if (!labels.get(i).asText().equals(summary.path("semester").asText())) continue;
                ObjectNode period = (ObjectNode) summary.deepCopy();
                period.remove(COURSE_LISTS);
                if (i == start) period.putNull("change_from_previous");
                JsonNode change = period.path("change_from_previous");
                if (change.path("weighted_gpa_delta").isNumber()) gpaComparisons++;
                if (change.path("average_numeric_score_delta").isNumber()) scoreComparisons++;
                periods.add(period);
                break;
            }
        }
        result.put("semester_count", periods.size());
        result.put("status", periods.size() >= 2 ? "OK" : "INSUFFICIENT_DATA");
        result.put("gpa_comparison_count", gpaComparisons);
        result.put("gpa_trend_status", gpaComparisons > 0 ? "OK" : "INSUFFICIENT_DATA");
        result.put("numeric_score_comparison_count", scoreComparisons);
        result.put("numeric_score_trend_status", scoreComparisons > 0 ? "OK" : "INSUFFICIENT_DATA");
        return result;
    }

    private JsonNode requireStatistics(AcademicAnalysisReadContext context) {
        JsonNode statistics = context.getSnapshot().statistics();
        if (!statistics.isObject() || statistics.path("schema_version").asInt() != 1
                || !statistics.path("semesters").isArray() || !statistics.path("valid_semesters").isArray()) {
            throw new IllegalStateException("学业分析服务缺少新版统计数据，请更新并重启 8002 服务");
        }
        return statistics;
    }
}
