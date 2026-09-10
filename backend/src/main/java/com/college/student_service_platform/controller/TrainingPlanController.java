package com.college.student_service_platform.controller;

import com.college.student_service_platform.common.Result;
import com.college.student_service_platform.dto.TrainingPlanItem;
import com.college.student_service_platform.dto.TrainingPlanSaveRequest;
import com.college.student_service_platform.service.TrainingPlanService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/training-plan")
public class TrainingPlanController {

    private final TrainingPlanService trainingPlanService;

    public TrainingPlanController(TrainingPlanService trainingPlanService) {
        this.trainingPlanService = trainingPlanService;
    }

    @PostMapping("/save")
    public Result<Long> save(@RequestBody TrainingPlanSaveRequest request) {
        Long id = trainingPlanService.save(request);
        return Result.success("保存成功", id);
    }

    @GetMapping("/list")
    public Result<List<TrainingPlanItem>> list() {
        return Result.success(trainingPlanService.list());
    }

    @GetMapping("/{id}")
    public Result<TrainingPlanItem> get(@PathVariable Long id) {
        return Result.success(trainingPlanService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        trainingPlanService.delete(id);
        return Result.success();
    }
}

