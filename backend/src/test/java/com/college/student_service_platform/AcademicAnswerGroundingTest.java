package com.college.student_service_platform;

import com.college.student_service_platform.agent.*;
import com.college.student_service_platform.agent.academic.*;
import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcademicAnswerGroundingTest {
    private AcademicBusinessContext business(String gpa) {
        var result=new AcademicBusinessContext(mock(AcademicAnalysisReadContext.class));
        result.record(new AcademicToolResult<>("get_academic_assessment","OK",null,new AcademicToolDtos.AssessmentOutput(null,
                BigDecimal.TEN,gpa!=null,gpa==null?null:new BigDecimal(gpa),"一般预警",4,1,12,
                List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of())));
        return result;
    }
    @Test void correctGpaIsKeptButDirectHistoricalClaimsAreNot() {
        var context=business("2.8");
        for(String text:List.of("官方 GPA 为 2.80。","GPA: 2.8","绩点是 2.8")) assertEquals(text,AcademicAnswerGrounding.ground(text,context));
        for(String text:List.of("GPA is 4.0","**GPA**：**4.0**","你的绩点为4.0，风险为零")) {
            var answer=AcademicAnswerGrounding.ground(text,context);assertTrue(answer.contains("2.8"));assertFalse(answer.contains("4.0"));
        }
    }
    @Test void datesAfterGpaAreNotMisreadAsGradeValues() {
        String text="GPA 在 2025-2026 春季有所变化，实际官方 GPA 为 2.8。";
        assertEquals(text,AcademicAnswerGrounding.ground(text,business("2.8")));
    }
    @Test void validNegativePrecomputedDeltaAndRoundedSemesterGpaRemainSeparateFromOfficialGpa() {
        var context=business("2.8");var mapper=new ObjectMapper();
        var summary=mapper.convertValue(Map.of("semester","春季","weightedGpa",new BigDecimal("3.5185"),
                "changeFromPrevious",Map.of("semester","秋季","weightedGpaDelta",new BigDecimal("-0.188")),"analysisNotes",List.of()),AcademicToolDtos.SemesterSummary.class);
        context.record(new AcademicToolResult<>("get_academic_trend","OK",null,new AcademicToolDtos.TrendOutput(null,"OK",4,1,null,
                List.of(summary),1,"DOWN",0,"UNAVAILABLE",List.of())));
        String text="官方 GPA 为 2.8；学期 GPA 为 3.52；GPA 变化为 -0.188。";
        assertEquals(text,AcademicAnswerGrounding.ground(text,context));
    }
    @Test void unavailableGpaIsNotPromotedFromHistoryOrReplacedWithZero() {
        var answer=AcademicAnswerGrounding.ground("当前 GPA 为 4.0",business(null));
        assertFalse(answer.contains("4.0"));assertFalse(answer.contains("0.0"));assertTrue(answer.contains("未采用"));
    }

    private AcademicBusinessContext typed(String official, String level, String delta, String direction) {
        var context=business(official);
        var node=new ObjectMapper().createObjectNode();node.put("semester","春季");
        if(level!=null)node.put("weightedGpa",new BigDecimal(level));
        node.putArray("analysisNotes");
        if(delta!=null) {
            var change=node.putObject("changeFromPrevious").put("weightedGpaDelta",new BigDecimal(delta));
            if(direction!=null)change.put("gpaDirection",direction);
        }
        var summary=new ObjectMapper().convertValue(node,AcademicToolDtos.SemesterSummary.class);
        context.record(new AcademicToolResult<>("get_academic_trend","OK",null,new AcademicToolDtos.TrendOutput(null,"OK",3,1,null,
                List.of(summary),1,"OK",0,"UNAVAILABLE",List.of())));
        return context;
    }
    private void kept(AcademicBusinessContext context,String... texts) {
        for(String text:texts)assertEquals(text,AcademicAnswerGrounding.ground(text,context),text);
    }
    private void rejected(AcademicBusinessContext context,String... texts) {
        for(String text:texts)assertNotEquals(text,AcademicAnswerGrounding.ground(text,context),text);
    }
    @Test void downwardMagnitudesAndSignedChangesUseDeltaEvidence() {
        var context=typed("2.63","0.9","-2.25","DOWN");
        kept(context,"GPA下降2.25。","GPA下降了2.2500。","加权 GPA下降幅度为2.25。","GPA变化量为-2.25。","学期GPA变化为-2.2500。");
        rejected(context,"GPA上升2.25。","GPA上升了2.25。","GPA下降0.9。","GPA变化量为0.9。","GPA下降-2.25。","GPA上升-2.25。");
    }
    @Test void levelsAndChangesCannotSubstituteForEachOther() {
        var context=typed("2.63","0.9","-2.25","DOWN");
        kept(context,"GPA下降至0.9。","学期 GPA 下降到 0.90。","GPA降至0.9000。","GPA为0.9。","官方 GPA 为2.6300。");
        rejected(context,"GPA为-2.25。","GPA为2.25。","GPA下降至-2.25。","GPA变化量为2.63。");
    }
    @Test void explicitlyQualifiedLevelsMatchOnlyTheirOwnEvidence() {
        var context=typed("2.63","0.9","-2.25","DOWN");
        kept(context,"官方GPA为2.63；学期GPA为0.9。","本学期加权GPA为0.90。","官方的 GPA 是2.63。");
        rejected(context,"官方GPA为0.9。","学期GPA为2.63。","加权GPA为2.63。","官方GPA下降至0.9。",
                "官方GPA下降2.25。","官方GPA变化量为-2.25。");
    }
    @Test void explicitScopeCannotBorrowEvidenceWhenItsSourceIsAbsent() {
        kept(business("2.63"),"GPA为2.63。");
        rejected(business("2.63"),"学期GPA为2.63。","GPA下降2.63。");
        rejected(typed(null,"0.9","-2.25","DOWN"),"官方GPA为0.9。","官方GPA为4.0。");
    }
    @Test void upwardAndStableEvidenceAreNotHardcodedAsDownward() {
        kept(typed(null,"3.15","2.25","UP"),"GPA上升2.25。","GPA变化量为+2.25。");
        rejected(typed(null,"3.15","2.25","UP"),"GPA下降2.25。","GPA变化量为-2.25。");
        kept(typed(null,"0.9","0","STABLE"),"GPA变化量为0.00。");
        rejected(typed(null,"0.9","0","STABLE"),"GPA上升0。","GPA下降0。");
    }
    @Test void exactDecimalAndExistingTwoAndFourPlaceRoundingRemainCompatible() {
        var context=typed("2.63333","3.5185","-0.188","DOWN");
        kept(context,"官方GPA为2.63。","官方GPA为2.6333。","学期GPA为3.52。","学期GPA为3.518500。",
                "GPA下降0.1880。","GPA下降0.19。","GPA变化量为-0.19。");
        rejected(context,"学期GPA为3.53。","GPA下降0.18。","GPA变化量为0.19。");
    }
    @Test void missingContradictoryAndUnknownDirectionDoNotCreateEvidence() {
        rejected(typed(null,"0.9",null,null),"GPA下降0.9。","GPA变化量为-2.25。");
        rejected(typed(null,"0.9","-2.25","UP"),"GPA下降2.25。","GPA变化量为-2.25。");
        rejected(typed(null,"0.9","-2.25","UNAVAILABLE"),"GPA下降2.25。");
        kept(typed(null,"0.9","-2.25",null),"GPA下降2.25。","GPA变化量为-2.25。");
    }
    @Test void wrongAndHistoricalNumbersStayBlockedAndDatesStayUntouched() {
        var context=typed("2.63","0.9","-2.25","DOWN");
        rejected(context,"官方GPA为4.0。","GPA下降9.9。","学期GPA为9.9。","GPA为4.0。");
        kept(context,"GPA 在 2025-2026 春季有所变化，官方GPA为2.63。","2025-2026学年的学期GPA为0.9。");
        var empty=new AcademicBusinessContext(mock(AcademicAnalysisReadContext.class));
        rejected(empty,"GPA为0.0。","GPA下降2.25。","学期GPA为0.9。");
        assertNull(AcademicAnswerGrounding.ground(null,context));
    }
    @Test void invalidModelGpaUsesAvailableTrendWithoutLosingTheAnswer() {
        var mapper = new ObjectMapper();
        var periods = new java.util.ArrayList<AcademicToolDtos.SemesterSummary>();
        String[] names={"2024-2025学年春季学期","2024-2025学年国际小学期","2025-2026学年秋季学期","2025-2026学年春季学期"};
        String[] values={"3.3938","3.3","3.7065","3.72"};
        String[] changes={null,"-0.0938","0.4065","0.0135"};
        for(int i=0;i<names.length;i++) {
            var row=mapper.createObjectNode().put("semester",names[i]).put("weightedGpa",new BigDecimal(values[i]));
            row.putArray("analysisNotes");
            if(i>0) row.putObject("changeFromPrevious").put("semester",names[i-1])
                    .put("weightedGpaDelta",new BigDecimal(changes[i])).put("gpaDirection",i==1?"DOWN":"UP");
            periods.add(mapper.convertValue(row,AcademicToolDtos.SemesterSummary.class));
        }
        var context=new AcademicBusinessContext(mock(AcademicAnalysisReadContext.class));
        context.record(new AcademicToolResult<>("get_academic_trend","OK",null,
                new AcademicToolDtos.TrendOutput(null,"OK",4,4,null,periods,3,"OK",0,"UNAVAILABLE",List.of())));
        String correct="学期 GPA 为3.72，上一学期GPA为3.71。";
        assertEquals(correct,AcademicAnswerGrounding.ground(correct,context));
        String fallback=AcademicAnswerGrounding.ground("学期GPA为4.0，完全没有问题。",context);
        for(String value:List.of("3.39","3.30","3.71","3.72")) assertTrue(fallback.contains(value),fallback);
        assertTrue(fallback.contains("上升0.01"));
        assertFalse(fallback.contains("4.0")); assertFalse(fallback.contains("重试")); assertFalse(fallback.contains("完全没有问题"));
    }
    @Test void fallbackKeepsZeroButDoesNotExplainAnInconsistentChange() {
        var zero=typed(null,"0","0","STABLE");
        String answer=AcademicAnswerGrounding.ground("GPA为4.0。",zero);
        assertTrue(answer.contains("0.00")); assertTrue(answer.contains("持平"));
        var inconsistent=typed(null,"0.9","-2.25","UP");
        answer=AcademicAnswerGrounding.ground("GPA为4.0。",inconsistent);
        assertTrue(answer.contains("0.90"));
        assertFalse(answer.contains("上升")); assertFalse(answer.contains("下降"));
    }
    @Test void trendWithoutAnyGpaStillDoesNotInventZeroOrRisk() {
        String answer=AcademicAnswerGrounding.ground("GPA为4.0。",typed(null,null,null,null));
        assertTrue(answer.contains("未采用")); assertFalse(answer.contains("0.00")); assertFalse(answer.contains("4.0"));
    }

}
