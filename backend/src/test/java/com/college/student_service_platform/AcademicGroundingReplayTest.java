package com.college.student_service_platform;

import com.college.student_service_platform.agent.*;
import com.college.student_service_platform.agent.academic.*;
import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Offline paired replay: only previously saved public assistant messages, never reconstructed model output. */
@EnabledIfEnvironmentVariable(named="AGENT_GPA_REPLAY",matches="1")
class AcademicGroundingReplayTest {
    @Test void replaySavedPublicAnswersThroughBothImplementations() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules();
        Path results=Path.of("../python-services/evaluation/agent/results");
        Path output=Path.of(System.getenv().getOrDefault("AGENT_GPA_REPLAY_DIR","../python-services/evaluation/agent/grounding_fix/replay-v2"));
        Files.createDirectory(output);
        var rows=new ArrayList<Map<String,Object>>();int paired=0,unavailable=0,noAgent=0,beforeFiltered=0,afterKept=0;
        // Frozen list prevents a later real resample from being silently included in the old-output replay.
        for(String run:List.of("20261106-203842-pilot-1e5bc","20261106-204134-baseline-dee37","20261106-204637-badcase_capture-c2ec8")) {
            for(String line:Files.readAllLines(results.resolve(run).resolve("observations.jsonl"))) {
                JsonNode record=mapper.readTree(line);
                for(JsonNode turn:record.path("turns")) {
                    var row=new LinkedHashMap<String,Object>();
                    row.put("source_run",run);row.put("case_id",record.path("case_id").asText());row.put("trial_id",record.path("trial_id").asText());row.put("turn",turn.path("turn").asInt());
                    JsonNode execution=turn.path("execution");
                    if(execution.isNull()||execution.isMissingNode()) {row.put("disposition","NO_AGENT_EXECUTION");noAgent++;rows.add(row);continue;}
                    String original=null;
                    for(JsonNode call:execution.path("model_calls")) {
                        JsonNode message=call.path("assistant_message");
                        if("stop".equals(call.path("finish_reason").asText()) && message.path("content").isTextual()
                                && (!message.path("tool_calls").isArray()||message.path("tool_calls").isEmpty())) original=message.path("content").asText();
                    }
                    if(original==null) {row.put("disposition","UNAVAILABLE_NO_SAVED_MODEL_PUBLIC_TEXT");unavailable++;rows.add(row);continue;}
                    var business=new AcademicBusinessContext(mock(AcademicAnalysisReadContext.class));
                    for(JsonNode tool:execution.path("tool_calls")) {
                        if(!"OK".equals(tool.path("status").asText())||tool.path("data").isNull())continue;
                        String name=tool.path("tool").asText();
                        Class<?> dto=switch(name) {
                            case "get_academic_context" -> AcademicToolDtos.ContextOutput.class;
                            case "get_academic_assessment" -> AcademicToolDtos.AssessmentOutput.class;
                            case "get_recent_course_performance" -> AcademicToolDtos.RecentOutput.class;
                            case "get_academic_trend" -> AcademicToolDtos.TrendOutput.class;
                            default -> throw new IllegalArgumentException("Unexpected saved tool");
                        };
                        business.record(new AcademicToolResult<>(name,"OK",null,mapper.treeToValue(tool.path("data"),dto)));
                    }
                    String before=LegacyAcademicAnswerGrounding.ground(original,business);
                    String after=AcademicAnswerGrounding.ground(original,business);
                    row.put("disposition","PAIRED_REPLAY");row.put("model_public_text",original);row.put("tool_evidence",execution.path("tool_calls"));
                    row.put("before_answer",before);row.put("after_answer",after);
                    row.put("before_matches_saved_response",before.equals(turn.path("response").path("data").path("answer").asText()));
                    row.put("before_preserves_original",before.equals(original));row.put("after_preserves_original",after.equals(original));
                    row.put("before_retained_courses",List.of("操作系统","计算机网络").stream().filter(before::contains).toList());
                    row.put("after_retained_courses",List.of("操作系统","计算机网络").stream().filter(after::contains).toList());
                    paired++;if(!before.equals(original))beforeFiltered++;if(after.equals(original))afterKept++;rows.add(row);
                }
            }
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("paired.json").toFile(),rows);
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("summary.json").toFile(),Map.of(
            "paired",paired,"unavailable_public_text",unavailable,"no_agent_execution",noAgent,
            "before_filtered",beforeFiltered,"after_preserved",afterKept,"external_model_calls",0));
        assertEquals(2,paired);assertEquals(2,afterKept);
        assertTrue(rows.stream().filter(r->r.get("disposition").equals("PAIRED_REPLAY")).allMatch(r->Boolean.TRUE.equals(r.get("before_matches_saved_response"))));
    }
}
