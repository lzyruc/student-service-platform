package com.college.student_service_platform.agent.academic;

import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import com.college.student_service_platform.service.AcademicStatisticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import static com.college.student_service_platform.agent.academic.AcademicToolDtos.*;

@Component
public class GetRecentCoursePerformanceTool implements AcademicTool<RecentInput, RecentOutput> {
    private final AcademicStatisticsService statistics;
    private final AcademicToolMapper mapper;
    public GetRecentCoursePerformanceTool(AcademicStatisticsService statistics, AcademicToolMapper mapper) {
        this.statistics = statistics;
        this.mapper = mapper;
    }
    @Override public AcademicToolDefinition definition() {
        ObjectNode schema = AcademicToolArguments.schema();
        ObjectNode properties = (ObjectNode) schema.path("properties");
        ObjectNode semester = properties.putObject("semester").put("type", "string").put("default", "latest");
        semester.putArray("enum").add("latest").add("previous");
        semester.put("description", "latest 为最近有有效最终成绩的学期，previous 为上一有效成绩学期，由程序选择实际学期");
        properties.set("limit", AcademicToolArguments.integerSchema(1, 10, 5));
        return new AcademicToolDefinition("get_recent_course_performance",
                "查询本人最近或上一有效成绩学期的统计与重点关注课程。低分固定为 60≤最终成绩<70；保留未知成绩和已通过重修记录说明。", schema);
    }
    @Override public RecentInput parseInput(JsonNode arguments) {
        AcademicToolArguments.object(arguments, "semester", "limit");
        return new RecentInput(AcademicToolArguments.text(arguments, "semester", "latest"),
                AcademicToolArguments.integer(arguments, "limit", 5));
    }
    @Override public RecentOutput execute(RecentInput input, AcademicAnalysisReadContext context) {
        JsonNode result = statistics.recentPerformance(context, input.semester(), input.limit());
        return mapper.recent(context.getSnapshot(), input, result);
    }
}
