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
}
