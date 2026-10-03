package com.college.student_service_platform.agent.academic;

import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import static com.college.student_service_platform.agent.academic.AcademicToolDtos.*;

@Component
public class GetAcademicContextTool implements AcademicTool<EmptyInput, ContextOutput> {
    private final AcademicToolMapper mapper;
    public GetAcademicContextTool(AcademicToolMapper mapper) { this.mapper = mapper; }

    @Override public AcademicToolDefinition definition() {
        return new AcademicToolDefinition("get_academic_context",
                "检查当前登录学生的专业、年级、已存成绩单和培养方案是否可用。不解析 PDF；缺少数据时说明下一步操作。",
                AcademicToolArguments.schema());
    }
    @Override public EmptyInput parseInput(JsonNode arguments) {
        AcademicToolArguments.object(arguments);
        return new EmptyInput();
    }
    @Override public ContextOutput execute(EmptyInput input, AcademicAnalysisReadContext context) {
        return mapper.context(context.getAcademicContext());
    }
}
