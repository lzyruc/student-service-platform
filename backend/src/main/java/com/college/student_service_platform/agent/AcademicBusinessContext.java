package com.college.student_service_platform.agent;

import com.college.student_service_platform.agent.academic.AcademicToolDtos;
import com.college.student_service_platform.agent.academic.AcademicToolResult;
import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Request-local backend evidence. No history text can be registered as a Tool result here. */
public final class AcademicBusinessContext {
    private static final Set<String> ANALYSIS_TOOLS = Set.of("get_academic_assessment", "get_recent_course_performance", "get_academic_trend");
    private final AcademicAnalysisReadContext analysis;
    private final List<AcademicToolResult<?>> results = new ArrayList<>();
    private List<String> nextActions = List.of();
    private boolean analysisAvailable;
    private boolean policyAvailable;
    public AcademicBusinessContext(AcademicAnalysisReadContext analysis) { this.analysis = java.util.Objects.requireNonNull(analysis); }
    public AcademicAnalysisReadContext analysis() { return analysis; }
    public void record(AcademicToolResult<?> result) {
        if (!"OK".equals(result.status())) return;
        results.add(result);
        if (result.data() instanceof com.college.student_service_platform.agent.policy.PolicySkill.Output policy)
            policyAvailable |= "OK".equals(policy.status()) && !policy.passages().isEmpty();
        if (ANALYSIS_TOOLS.contains(result.tool())) analysisAvailable = true;
        if (result.data() instanceof AcademicToolDtos.ContextOutput context) nextActions = context.nextActions();
    }
    public boolean analysisAvailable() { return analysisAvailable; }
    public boolean evidenceAvailable() { return analysisAvailable || policyAvailable; }
    public List<String> nextActions() { return nextActions; }
    public List<AcademicToolResult<?>> results() { return List.copyOf(results); }
}
