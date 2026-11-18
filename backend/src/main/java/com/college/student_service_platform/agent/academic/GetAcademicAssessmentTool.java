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
                "查询本人已获学分、官方 GPA、预警等级、未通过和核心课程状态。",
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
