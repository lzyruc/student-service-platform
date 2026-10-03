package com.college.student_service_platform.agent.academic;

import com.college.student_service_platform.dto.AcademicAnalysisSnapshot;
import com.college.student_service_platform.dto.AcademicContextSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.college.student_service_platform.agent.academic.AcademicToolDtos.*;

/** Whitelist projection only: risk, selectors and arithmetic remain in existing business code. */
@Component
public class AcademicToolMapper {
    public ContextOutput context(AcademicContextSnapshot data) {
        List<String> actions = new ArrayList<>();
        if (!data.profileAvailable()) actions.add("完善本人专业和年级信息");
        if (!data.transcriptExists()) actions.add("上传成绩单 PDF");
        else if (!data.transcriptAvailable()) actions.add("重新上传可用的成绩单 PDF");
        if (data.profileAvailable() && !data.trainingPlanExists()) actions.add("联系管理员补充匹配的培养方案");
        return new ContextOutput(actions.isEmpty() ? "READY" : "MISSING_DATA", data.profileAvailable(),
                data.major(), data.grade(), data.transcriptExists(), data.transcriptAvailable(), data.transcriptUploadedAt(),
                data.trainingPlanExists(), data.trainingPlanVersion(), data.trainingPlanUpdatedAt(), actions);
    }

    public Source source(AcademicAnalysisSnapshot snapshot) {
        return new Source(snapshot.major(), snapshot.grade(), snapshot.transcriptUploadedAt(),
                snapshot.trainingPlanVersion(), snapshot.trainingPlanUpdatedAt());
    }

    public AssessmentOutput assessment(AcademicAnalysisSnapshot snapshot) {
        JsonNode report = snapshot.report();
        // Missing evidence must not silently become an empty list or a healthy report.
        for (String key : List.of("failed_courses", "core_courses", "missing_core_courses", "pending_core_courses",
                "unscheduled_core_courses", "unknown_score_courses", "course_suggestions", "analysis_notes")) {
            if (!report.path(key).isArray()) throw new IllegalStateException("学业报告缺少完整课程证据");
        }
        String warning = text(report, "warning_level");
        if (!List.of("正常", "一般预警", "严重预警").contains(warning == null ? "" : warning))
            throw new IllegalStateException("学业报告缺少有效预警结果");
        BigDecimal credits = number(report, "total_earned_credits");
        if (credits == null) throw new IllegalStateException("学业报告缺少有效学分");
        boolean gpaAvailable = report.path("official_gpa_available").isBoolean()
                && report.path("official_gpa_available").booleanValue() && number(report, "official_gpa") != null;
        List<String> notes = new ArrayList<>(strings(report.path("analysis_notes")));
        if (!gpaAvailable) notes.add("未识别到官方整体 GPA，空值不代表零绩点");
        notes.add("核心课程缺口表示已到计划开课学期但未见通过记录，需核实是否未修、待出分或课程名称差异");
        List<FailedCourse> failed = new ArrayList<>();
        for (JsonNode course : report.path("failed_courses"))
            failed.add(new FailedCourse(text(course, "name"), text(course, "score")));
        return new AssessmentOutput(source(snapshot), credits, gpaAvailable,
                gpaAvailable ? number(report, "official_gpa") : null, warning,
                integer(report, "completed_semester"), integer(report, "issue_course_count"), integer(report, "core_course_count"),
                failed, strings(report.path("core_courses")), strings(report.path("missing_core_courses")),
                strings(report.path("pending_core_courses")), strings(report.path("unscheduled_core_courses")),
                strings(report.path("unknown_score_courses")), strings(report.path("course_suggestions")), notes);
    }

    public RecentOutput recent(AcademicAnalysisSnapshot snapshot, RecentInput input, JsonNode data) {
        List<FocusCourse> courses = new ArrayList<>();
        for (JsonNode course : data.path("focus_courses")) {
            JsonNode passed = course.path("passed");
            courses.add(new FocusCourse(text(course, "name"), text(course, "score"), number(course, "numeric_score"),
                    number(course, "credit"), integer(course, "page"), passed.isBoolean() ? passed.booleanValue() : null,
                    text(course, "current_failure_status"), text(course, "reason")));
        }
        return new RecentOutput(source(snapshot), text(data, "status"), text(data, "message"), input.semester(),
                text(data, "semester"), input.limit(), rules(data.path("rules")), summary(data.path("summary")),
                data.path("focus_course_count").asInt(), data.path("truncated").asBoolean(), courses,
                strings(data.path("analysis_notes")));
    }

    public TrendOutput trend(AcademicAnalysisSnapshot snapshot, TrendInput input, JsonNode data) {
        List<SemesterSummary> semesters = new ArrayList<>();
        for (JsonNode semester : data.path("semesters")) semesters.add(summary(semester));
        return new TrendOutput(source(snapshot), text(data, "status"), input.lastSemesters(),
                data.path("semester_count").asInt(), rules(data.path("rules")), semesters,
                data.path("gpa_comparison_count").asInt(), text(data, "gpa_trend_status"),
                data.path("numeric_score_comparison_count").asInt(), text(data, "numeric_score_trend_status"),
                strings(data.path("analysis_notes")));
    }

    private SemesterSummary summary(JsonNode data) {
        if (!data.isObject()) return null;
        JsonNode change = data.path("change_from_previous");
        SemesterChange previous = !change.isObject() ? null : new SemesterChange(text(change, "semester"),
                number(change, "weighted_gpa_delta"), text(change, "gpa_direction"),
                number(change, "average_numeric_score_delta"), text(change, "numeric_score_direction"),
                integer(change, "failed_course_count_delta"));
        return new SemesterSummary(text(data, "semester"), integer(data, "course_count"), integer(data, "valid_score_course_count"),
                integer(data, "passed_course_count"), integer(data, "failed_course_count"), integer(data, "unknown_score_course_count"),
                number(data, "attempted_credits"), integer(data, "valid_credit_course_count"), number(data, "weighted_gpa"),
                integer(data, "gpa_course_count"), number(data, "gpa_credits"), number(data, "gpa_course_coverage"),
                number(data, "gpa_credit_coverage"), number(data, "average_numeric_score"), integer(data, "numeric_score_course_count"),
                number(data, "numeric_score_coverage"), previous, strings(data.path("analysis_notes")));
    }

    private Rules rules(JsonNode data) {
        return new Rules(number(data, "low_score_min"), number(data, "low_score_max_exclusive"),
                strings(data.path("semester_order")), text(data, "weighted_gpa"), text(data, "average_numeric_score"),
                text(data, "trend_direction"));
    }

    private String text(JsonNode data, String field) {
        JsonNode value = data.path(field);
        return value.isValueNode() && !value.isNull() ? value.asText() : null;
    }

    private BigDecimal number(JsonNode data, String field) {
        JsonNode value = data.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }

    private Integer integer(JsonNode data, String field) {
        JsonNode value = data.path(field);
        return value.isIntegralNumber() && value.canConvertToInt() ? value.intValue() : null;
    }

    private List<String> strings(JsonNode data) {
        List<String> values = new ArrayList<>();
        if (data.isArray()) for (JsonNode item : data) if (item.isTextual()) values.add(item.textValue());
        return values;
    }
}
