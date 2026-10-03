package com.college.student_service_platform.agent.academic;

import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import static com.college.student_service_platform.agent.academic.AcademicToolDtos.*;

@Component
public class GetAcademicAssessmentTool implements AcademicTool<EmptyInput, AssessmentOutput> {
    private final AcademicToolMapper mapper;
    public GetAcademicAssessmentTool(AcademicToolMapper mapper) { this.mapper = mapper; }

    @Override public AcademicToolDefinition definition() {
        return new AcademicToolDefinition("get_academic_assessment",
                "查询本人整体学业报告：已获学分、官方 GPA、已有预警等级、未通过和核心课程状态。缺口需核实；不能预测未来挂科概率。",
                AcademicToolArguments.schema());
    }
    @Override public EmptyInput parseInput(JsonNode arguments) {
        AcademicToolArguments.object(arguments);
        return new EmptyInput();
    }
    @Override public AssessmentOutput execute(EmptyInput input, AcademicAnalysisReadContext context) {
        return mapper.assessment(context.getSnapshot());
    }
}
