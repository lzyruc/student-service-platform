package com.college.student_service_platform.agent;

import com.college.student_service_platform.agent.academic.AcademicToolDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Limited validation of direct GPA level/change claims against this request's typed evidence. */
public final class AcademicAnswerGrounding {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AcademicAnswerGrounding.class);
    private static final Pattern GPA_CLAIM = Pattern.compile(
        "(?iu)(?:(?<scope>官方(?:\\s*的)?(?:\\s*加权)?|(?:本|该|当前|上一|上)?学期(?:\\s*的)?(?:\\s*加权)?|加权)\\s*)?"
        + "(?:GPA|绩点)\\s*"
        + "(?<op>(?:上升|下降)\\s*(?:至|到)|升至|降至|"
        + "(?:变化量|变化|变动量|变动)\\s*(?:为|是|[:：=])?|"
        + "(?:上升|下降)(?:幅度)?\\s*(?:了|为|是|[:：=])?|"
        + "约为|为约|达到|变为|is|was|equals|of|[:：=]|是|为|约)?\\s*"
        + "(?<value>[+-]?\\d+(?:\\.\\d+)?)(?![\\d.])");
    private enum Scope { OFFICIAL, SEMESTER, UNSPECIFIED }
    private AcademicAnswerGrounding() { }

    public static String ground(String answer, AcademicBusinessContext business) {
        if (answer == null) return null;
        Evidence evidence = collect(business);
        var claims = GPA_CLAIM.matcher(answer.replace("*", ""));
        while (claims.find()) {
            String qualifier = claims.group("scope");
            Scope scope = qualifier == null ? Scope.UNSPECIFIED
                    : qualifier.contains("官方") ? Scope.OFFICIAL : Scope.SEMESTER;
            String op = claims.group("op") == null ? "" : claims.group("op").replaceAll("\\s+", "");
            BigDecimal value = new BigDecimal(claims.group("value")).stripTrailingZeros();
            boolean accepted;
            if (op.startsWith("变化") || op.startsWith("变动")) {
                accepted = scope != Scope.OFFICIAL && matchesChange(evidence.changes, value, null);
            } else if ((op.startsWith("下降") || op.startsWith("上升")) && !op.endsWith("至") && !op.endsWith("到")) {
                // A negative magnitude is not silently absolutized: "下降-2.25" is ambiguous.
                String direction = op.startsWith("下降") ? "DOWN" : "UP";
                BigDecimal signed = "DOWN".equals(direction) ? value.negate() : value;
                accepted = value.signum() >= 0 && scope != Scope.OFFICIAL
                        && matchesChange(evidence.changes, signed, direction);
            } else {
                // "下降至/到" asserts an endpoint level here, not a change magnitude.
                accepted = switch (scope) {
                    case OFFICIAL -> contains(evidence.officialLevels, value);
                    case SEMESTER -> contains(evidence.semesterLevels, value);
                    case UNSPECIFIED -> contains(evidence.officialLevels, value) || contains(evidence.semesterLevels, value);
                };
            }
            if (!accepted) {
                log.warn("GPA claim rejected: scope={}, operation={}, value={}, officialValues={}, semesterValues={}, changes={}",
                        scope, op, value, evidence.officialLevels.size(), evidence.semesterLevels.size(), evidence.changes.size());
                return fallback(evidence);
            }
        }
        return answer;
    }

    private static Evidence collect(AcademicBusinessContext business) {
        var evidence = new Evidence();
        for (var result : business.results()) {
            if (result.data() instanceof AssessmentOutput assessment && assessment.officialGpaAvailable()) {
                evidence.official = assessment.officialGpa();
                add(evidence.officialLevels, assessment.officialGpa());
            } else if (result.data() instanceof RecentOutput recent && recent.summary() != null) {
                addSummary(evidence, recent.summary());
            } else if (result.data() instanceof TrendOutput trend) {
                for (var semester : trend.semesters()) addSummary(evidence, semester);
                evidence.trend = trend;
            }
        }
        return evidence;
    }
    private static void addSummary(Evidence evidence, SemesterSummary summary) {
        add(evidence.semesterLevels, summary.weightedGpa());
        var change = summary.changeFromPrevious();
        if (change == null || change.weightedGpaDelta() == null) return;
        BigDecimal delta = change.weightedGpaDelta();
        String fromSign = delta.signum() < 0 ? "DOWN" : delta.signum() > 0 ? "UP" : "STABLE";
        String direction = change.gpaDirection();
        // Older DTOs can omit direction. A supplied contradictory/unknown direction is not usable evidence.
        if (direction != null && !direction.isBlank() && !fromSign.equals(direction)) return;
        var values = new HashSet<BigDecimal>(); add(values, delta);
        evidence.changes.add(new Change(fromSign, values));
    }
    private static boolean matchesChange(List<Change> changes, BigDecimal value, String direction) {
        return changes.stream().anyMatch(change -> (direction == null || direction.equals(change.direction))
                && contains(change.values, value));
    }
    private static boolean contains(Set<BigDecimal> values, BigDecimal value) {
        return values.contains(value.stripTrailingZeros());
    }
    private static void add(Set<BigDecimal> values, BigDecimal value) {
        if (value == null) return;
        values.add(value.stripTrailingZeros());
        values.add(value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros());
        values.add(value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros());
    }
    private static String fallback(Evidence evidence) {
        // Keep useful current-request evidence when model prose fails validation.
        if (evidence.trend != null) {
            var periods = evidence.trend.semesters().stream().filter(s -> s.weightedGpa() != null).toList();
            if (!periods.isEmpty()) {
                String answer = "学期 GPA：" + String.join("；", periods.stream()
                        .map(s -> s.semester() + " " + s.weightedGpa().setScale(2, RoundingMode.HALF_UP).toPlainString()).toList()) + "。";
                var last = evidence.trend.semesters().get(evidence.trend.semesters().size() - 1);
                var change = last.changeFromPrevious();
                if (last.weightedGpa() != null && change != null && change.weightedGpaDelta() != null
                        && matchesChange(evidence.changes, change.weightedGpaDelta(), change.gpaDirection())) {
                    BigDecimal delta = change.weightedGpaDelta();
                    answer += delta.signum() == 0 ? "最近一学期较上一有效学期持平。"
                            : "最近一学期较上一有效学期" + (delta.signum() > 0 ? "上升" : "下降")
                            + delta.abs().setScale(2, RoundingMode.HALF_UP).toPlainString() + "。";
                }
                return answer;
            }
        }
        BigDecimal official = evidence.official;
        if (official != null) return "本次 GPA 描述与成绩单不符。整体 GPA 为 " + official.stripTrailingZeros().toPlainString()
                + "。";
        return "未采用无法核实的 GPA 描述，请重试。";
    }
    private record Change(String direction, Set<BigDecimal> values) { }
    private static final class Evidence {
        private BigDecimal official;
        private TrendOutput trend;
        private final Set<BigDecimal> officialLevels = new HashSet<>();
        private final Set<BigDecimal> semesterLevels = new HashSet<>();
        private final List<Change> changes = new ArrayList<>();
    }
}
