package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.common.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student/party")
public class PartyController {

    private final JdbcTemplate jdbcTemplate;

    public PartyController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/progress")
    public Result<List<Map<String, Object>>> getProgress(
            @RequestParam(value = "studentNo", required = false) String requestedStudentNo,
            HttpServletRequest request) {
        String studentNo = AuthContext.studentNo(request, requestedStudentNo);
        Integer partyStageId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(party_stage_id, 0) FROM t_student WHERE student_no = ?",
                Integer.class,
                studentNo
        );
        int currentStageId = partyStageId == null ? 0 : partyStageId;

        List<Map<String, Object>> stages = jdbcTemplate.queryForList(
                "SELECT stage_id, stage_name, order_num, duration, description FROM t_process_stage ORDER BY order_num ASC"
        );

        List<Map<String, Object>> progressList = new ArrayList<>();

        Map<String, Object> notApplied = new HashMap<>();
        notApplied.put("stageId", 0);
        notApplied.put("stageName", "\u672a\u7533\u8bf7");
        notApplied.put("orderNum", 0);
        notApplied.put("duration", null);
        notApplied.put("stageDescription", null);
        notApplied.put("startDate", null);
        notApplied.put("stageStatus", currentStageId == 0 ? "\u8fdb\u884c\u4e2d" : "\u5df2\u5b8c\u6210");
        progressList.add(notApplied);

        for (Map<String, Object> stage : stages) {
            Object stageIdObj = stage.get("stage_id");
            if (!(stageIdObj instanceof Number)) {
                continue;
            }

            int stageId = ((Number) stageIdObj).intValue();
            Map<String, Object> row = new HashMap<>();
            row.put("stageId", stageId);
            row.put("stageName", stage.get("stage_name"));
            row.put("orderNum", stage.get("order_num"));
            row.put("duration", stage.get("duration"));
            row.put("stageDescription", stage.get("description"));
            row.put("startDate", null);

            if (currentStageId <= 0) {
                row.put("stageStatus", "\u672a\u5f00\u59cb");
            } else if (stageId < currentStageId) {
                row.put("stageStatus", "\u5df2\u5b8c\u6210");
            } else if (stageId == currentStageId) {
                row.put("stageStatus", "\u8fdb\u884c\u4e2d");
            } else {
                row.put("stageStatus", "\u672a\u5f00\u59cb");
            }

            progressList.add(row);
        }

        return Result.success("Party progress loaded", progressList);
    }
}
