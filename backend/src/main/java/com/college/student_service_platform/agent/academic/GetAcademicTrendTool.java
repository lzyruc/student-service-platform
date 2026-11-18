package com.college.student_service_platform.agent.academic;

import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import com.college.student_service_platform.service.AcademicStatisticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import static com.college.student_service_platform.agent.academic.AcademicToolDtos.*;

@Component
public class GetAcademicTrendTool implements AcademicTool<TrendInput, TrendOutput> {
    private final AcademicStatisticsService statistics;
    private final AcademicToolMapper mapper;
    public GetAcademicTrendTool(AcademicStatisticsService statistics, AcademicToolMapper mapper) {
        this.statistics = statistics;
        this.mapper = mapper;
    }
    @Override public AcademicToolDefinition definition() {
        ObjectNode schema = AcademicToolArguments.schema();
        ((ObjectNode) schema.path("properties")).set("lastSemesters", AcademicToolArguments.integerSchema(2, 8, 4));
        return new AcademicToolDefinition("get_academic_trend",
                "查询本人最近 2～8 个有效成绩学期的统计、变化量和方向。", schema);
    }
    @Override public TrendInput parseInput(JsonNode arguments) {
        AcademicToolArguments.object(arguments, "lastSemesters");
        return new TrendInput(AcademicToolArguments.integer(arguments, "lastSemesters", 4));
    }
    @Override public TrendOutput execute(TrendInput input, AcademicAnalysisReadContext context) {
        JsonNode result = statistics.trend(context, input.lastSemesters());
        return mapper.trend(context.getSnapshot(), input, result);
    }
}
