package com.college.student_service_platform.agent;

import com.college.student_service_platform.agent.academic.AcademicToolDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Narrow deterministic protection for direct GPA claims; not a universal natural-language fact checker. */
public final class AcademicAnswerGrounding {
    private static final Pattern GPA_CLAIM = Pattern.compile("(?iu)(?:GPA|绩点)\\s*(?:(?:[:：=]|is|was|equals|of|约为|为约|达到|变为|升至|降至|上升|下降|是|为|约|变化量为|变化为)\\s*)?([+-]?\\d+(?:\\.\\d+)?)");
    private AcademicAnswerGrounding() { }
    public static String ground(String answer, AcademicBusinessContext business) {
        if (answer == null) return null;
        Set<BigDecimal> values = new HashSet<>(); BigDecimal official = null;
        for (var result : business.results()) {
            if (result.data() instanceof AssessmentOutput assessment && assessment.officialGpaAvailable()) {
                official = assessment.officialGpa(); add(values,official);
            } else if (result.data() instanceof RecentOutput recent && recent.summary() != null) {
                add(values,recent.summary().weightedGpa());
                if (recent.summary().changeFromPrevious() != null) add(values,recent.summary().changeFromPrevious().weightedGpaDelta());
            } else if (result.data() instanceof TrendOutput trend) {
                for (var semester : trend.semesters()) {
                    add(values,semester.weightedGpa());
                    if (semester.changeFromPrevious() != null) add(values,semester.changeFromPrevious().weightedGpaDelta());
                }
            }
        }
        var claims = GPA_CLAIM.matcher(answer.replace("*", ""));
        while (claims.find()) {
            var claimed = new BigDecimal(claims.group(1)).stripTrailingZeros();
            if (!values.contains(claimed)) {
                if (official != null) return "当前可核验的官方 GPA 为 " + official.stripTrailingZeros().toPlainString()
                        + "。对话中的自述数字不能代替成绩单结果；其余与当前数据未能核对一致的解释暂不展示。";
                return "本次未采用未经当前数据核实的 GPA 描述。请以本次成绩数据卡片为准；对话中的自述不能作为成绩或风险结论。";
            }
        }
        return answer;
    }
    private static void add(Set<BigDecimal> values, BigDecimal value) {
        if (value == null) return;
        values.add(value.stripTrailingZeros());
        values.add(value.setScale(2,RoundingMode.HALF_UP).stripTrailingZeros());
        values.add(value.setScale(4,RoundingMode.HALF_UP).stripTrailingZeros());
    }
}
