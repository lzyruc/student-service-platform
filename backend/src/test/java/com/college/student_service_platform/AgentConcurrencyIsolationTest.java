package com.college.student_service_platform;

import com.college.student_service_platform.agent.*;
import com.college.student_service_platform.agent.academic.*;
import com.college.student_service_platform.common.*;
import com.college.student_service_platform.config.*;
import com.college.student_service_platform.controller.*;
import com.college.student_service_platform.dto.*;
import com.college.student_service_platform.entity.FileRecord;
import com.college.student_service_platform.repository.AgentConversationRepository;
import com.college.student_service_platform.service.*;
import com.college.student_service_platform.service.external.AcademicWarningClient;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real singleton Spring services, real JWT/MVC and JDBC; only upstream file/plan/Python/model boundaries mocked. */
@SpringBootTest(classes=AgentConcurrencyIsolationTest.Config.class, webEnvironment=SpringBootTest.WebEnvironment.MOCK, properties={"logging.level.root=WARN","logging.level.org.springframework=WARN"})
@Timeout(60)
class AgentConcurrencyIsolationTest {
    @Configuration(proxyBeanMethods=false) @EnableWebMvc
    @Import({AgentConversationRepository.class,AgentConversationService.class,ConversationChatService.class,
        AcademicAnalysisService.class,AcademicStatisticsService.class,AcademicToolMapper.class,AcademicToolExecutor.class,
        GetAcademicContextTool.class,GetAcademicAssessmentTool.class,GetRecentCoursePerformanceTool.class,
        GetAcademicTrendTool.class,AcademicSkill.class,SingleAgentService.class,AgentChatController.class,
        AgentConversationController.class,GlobalExceptionHandler.class,AgentStreamingConfig.class})
    static class Config {
        @Bean JdbcTemplate jdbc() throws Exception {
            var jdbc=ConversationTestSupport.create().jdbc();
            jdbc.execute("ALTER TABLE t_student ADD major VARCHAR(100)");
            jdbc.execute("ALTER TABLE t_student ADD grade VARCHAR(50)");
            for (Actor a:ACTORS) jdbc.update("UPDATE t_student SET major=?,grade=? WHERE id=?",a.major(),a.grade(),a.id());
            return jdbc;
        }
        @Bean PlatformTransactionManager transactions(JdbcTemplate jdbc) { return new DataSourceTransactionManager(jdbc.getDataSource()); }
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean AgentProperties properties() { var p=new AgentProperties(); p.getAcademic().setPromptFile(""); return p; }
        @Bean JwtUtil jwt() { return new JwtUtil("0123456789abcdef0123456789abcdef","isolation-test",Duration.ofHours(1)); }
        @Bean AuthFilter auth(JwtUtil jwt,ObjectMapper mapper) { return new AuthFilter(jwt,mapper); }
    }
    record Actor(long id,String no,String label,String grade,double gpa,int score) {
        String major() { return label+"_MAJOR_ONLY"; }
        String course() { return label+"_COURSE_ONLY"; }
        String version() { return label+"_PLAN_ONLY"; }
        String history() { return label+"_HISTORY_ONLY"; }
        Path pdf() { return Path.of("uploads",label+"-private-transcript.pdf"); }
    }
    static final Actor A=new Actor(101,"20260001","ALPHA","2025",2.8,65);
    static final Actor B=new Actor(102,"20260002","BETA","2026",3.9,68);
    static final List<Actor> ACTORS=List.of(A,B);
    static final List<String> NAMES=List.of("get_academic_context","get_academic_assessment","get_recent_course_performance","get_academic_trend");
    @Autowired WebApplicationContext spring;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @Autowired AuthFilter auth;
    @Autowired ConversationChatService chats;
    @SpyBean SingleAgentService agent;
    @SpyBean AcademicToolExecutor tools;
    @MockBean DeepSeekClient model;
    @MockBean AcademicWarningClient python;
    @MockBean StudentTranscriptService transcripts;
    @MockBean TrainingPlanService plans;
    @MockBean WarningRecordService records;
    MockMvc mvc;
    final Map<String,FileRecord> files=new ConcurrentHashMap<>();
    final Map<String,Double> gpas=new ConcurrentHashMap<>();
    final Map<Long,Actor> owners=new ConcurrentHashMap<>();
    final Queue<AgentRequestContext> contexts=new ConcurrentLinkedQueue<>();
    final Queue<String> pythonReads=new ConcurrentLinkedQueue<>();
    final Queue<String> toolReads=new ConcurrentLinkedQueue<>();
    final AtomicInteger active=new AtomicInteger(),peak=new AtomicInteger();
    volatile CyclicBarrier pythonBarrier;
    volatile boolean wrongGpa,failA;

    @BeforeEach void setup() throws Exception {
        jdbc.update("DELETE FROM agent_conversation");
        files.clear();gpas.clear();owners.clear();contexts.clear();pythonReads.clear();toolReads.clear();
        active.set(0);peak.set(0);pythonBarrier=null;wrongGpa=false;failA=false;
        mvc=MockMvcBuilders.webAppContextSetup(spring).addFilters(auth).build();
        for (Actor a:ACTORS) {
            gpas.put(a.no(),a.gpa());
            var file=new FileRecord(); file.setId(a.id()*10);file.setOriginalName(a.label()+".pdf");
            file.setCreatedAt(LocalDateTime.of(2026,10,1,10,0));files.put(a.no(),file);
            when(transcripts.requireCurrent(a.no())).thenReturn(file);
            when(transcripts.getCurrent(a.no())).thenReturn(new StudentTranscriptResponse(file.getId(),file.getOriginalName(),100L,file.getCreatedAt(),true));
            when(transcripts.resolveForAnalysis(a.no(),file)).thenReturn(a.pdf());
            var plan=new TrainingPlanItem();plan.setId(a.id()*20);plan.setMajor(a.major());plan.setGrade(a.grade());
            plan.setVersion(a.version());plan.setUpdatedAt(file.getCreatedAt());
            plan.setJsonContent(mapper.writeValueAsString(Map.of("courses",List.of(Map.of("category","部类基础课","courseName",a.course(),"credits",4,"offeredAt","1")))));
            when(plans.getLatest(a.major(),a.grade())).thenReturn(plan);
        }
        when(python.analyzeStoredTranscript(any(),anyString(),anyString(),anyString())).thenAnswer(call -> {
            Actor a=actor(call.getArgument(2));
            assertEquals(a.pdf(),call.getArgument(0)); assertEquals(files.get(a.no()).getOriginalName(),call.getArgument(1));
            JsonNode plan=mapper.readTree((String)call.getArgument(3));
            assertEquals(a.major(),plan.path("major").asText());assertEquals(a.grade(),plan.path("grade").asText());
            assertTrue(plan.toString().contains(a.course()));assertPrivate(plan.toString(),a);
            pythonReads.add(a.no());int simultaneous=active.incrementAndGet();peak.accumulateAndGet(simultaneous,Math::max);
            try {
                CyclicBarrier gate=pythonBarrier;if(gate!=null)gate.await(10,TimeUnit.SECONDS);
                if(failA && a==A)throw new IllegalStateException("simulated upstream failure");
                return response(a,gpas.get(a.no()));
            } finally {active.decrementAndGet();}
        });
        doAnswer(call -> {
            AgentRequestContext ctx=(AgentRequestContext)call.callRealMethod();
            Actor a=owners.get(ctx.conversation().conversationId());assertNotNull(a);
            assertEquals(a.id(),ctx.identity().studentId());assertEquals(a.no(),ctx.identity().studentNo());
            assertPrivate(ctx.conversation().history().toString(),a);assertTrue(ctx.business().results().isEmpty());
            contexts.add(ctx);return ctx;
        }).when(agent).prepareRequest(any(),anyLong());
        doAnswer(call -> {
            AcademicAnalysisReadContext ctx=call.getArgument(2);Actor a=actor(ctx.studentNo());
            AcademicToolResult<?> result=(AcademicToolResult<?>)call.callRealMethod();
            assertPrivate(mapper.writeValueAsString(result),a);toolReads.add(a.no());return result;
        }).when(tools).execute(anyString(),anyString(),any(AcademicAnalysisReadContext.class));
        when(model.complete(any(),anyList(),anyString())).thenAnswer(call -> modelReply(call.getArgument(0)));
    }

    @AfterEach void ordinaryAgentQueriesNeverWriteFormalWarningsOrTranscripts() throws Exception {
        verifyNoInteractions(records);
        verify(transcripts,never()).save(anyString(),any());
    }

    @Test void eightyInterleavedJwtChatsUseSharedSingletonsWithoutCrossUserData() throws Exception {
        assertTrue(spring.isSingleton(spring.getBeanNamesForType(SingleAgentService.class)[0]));
        assertSame(agent,spring.getBean(SingleAgentService.class));assertSame(tools,spring.getBean(AcademicToolExecutor.class));
        pythonBarrier=new CyclicBarrier(8);var startTurn=new CyclicBarrier(8);
        var work=new ArrayList<Callable<Void>>();
        for(int lane=0;lane<8;lane++) {
            Actor a=ACTORS.get(lane%2);long id=create(a);seed(id,a.history(),a.history());
            work.add(() -> { for(int turn=0;turn<10;turn++) {startTurn.await(10,TimeUnit.SECONDS);assertChat(chat(a,id),a,gpas.get(a.no()));} return null;});
        }
        parallel(work,8);
        assertEquals(80,pythonReads.size());assertEquals(40,Collections.frequency(new ArrayList<>(pythonReads),A.no()));
        assertEquals(40,Collections.frequency(new ArrayList<>(pythonReads),B.no()));assertEquals(320,toolReads.size());assertEquals(8,peak.get());
        assertEquals(80,contexts.size());
        Set<AcademicAnalysisReadContext> reads=Collections.newSetFromMap(new IdentityHashMap<>());
        Set<AcademicAnalysisSnapshot> snapshots=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var ctx:contexts) {
            assertEquals(AgentRequestContext.State.SUCCEEDED,ctx.state());assertEquals(4,ctx.toolCallCount());
            assertTrue(reads.add(ctx.business().analysis()));assertTrue(snapshots.add(ctx.business().analysis().getSnapshot()));
            assertSame(ctx.business().analysis().getSnapshot(),ctx.business().analysis().getSnapshot());
        }
        assertEquals(176,jdbc.queryForObject("SELECT COUNT(*) FROM agent_conversation_message",Integer.class));
        verify(model,times(160)).complete(any(),anyList(),anyString());verifyNoInteractions(records);
    }

    @Test void foreignConversationIsRejectedBeforeAgentToolsOrModelEvenForStream() throws Exception {
        long id=create(A);
        for(String route:List.of("/chat","/chat/stream")) mvc.perform(post("/api/student/agent"+route)
            .header("Authorization",bearer(B)).contentType("application/json").content(body(id,B)))
            .andExpect(status().isNotFound()).andExpect(request().asyncNotStarted());
        verifyNoInteractions(agent,tools,model,python,transcripts,plans,records);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM agent_conversation_message",Integer.class));
    }

    @Test void parallelAndSequentialFourToolsShareOneRequestSingleFlight() throws Exception {
        long id=create(A);var prepared=prepare(A,id);var read=prepared.requestContext().business().analysis();
        assertEquals("OK",tools.execute(NAMES.get(0),"{}",read).status());verifyNoInteractions(python);
        var calls=new ArrayList<Callable<Void>>();var gate=new CyclicBarrier(4);
        for(String name:NAMES)calls.add(() -> {gate.await(5,TimeUnit.SECONDS);assertEquals("OK",tools.execute(name,"{}",read).status());return null;});
        parallel(calls,4);
        for(String name:NAMES)assertEquals("OK",tools.execute(name,"{}",read).status());
        assertEquals(1,pythonReads.size());assertSame(read.getSnapshot(),read.getSnapshot());verifyNoInteractions(records);
    }

    @Test void nextHttpRequestReadsUpdatedGradesAndCreatesNewSnapshot() throws Exception {
        long id=create(A);assertChat(chat(A,id),A,2.8);
        var first=contexts.peek();gpas.put(A.no(),3.1);assertChat(chat(A,id),A,3.1);
        var second=new ArrayList<>(contexts).get(1);
        assertNotSame(first,second);assertNotSame(first.business().analysis(),second.business().analysis());
        assertEquals(2.8,first.business().analysis().getSnapshot().report().path("official_gpa").asDouble());
        assertEquals(3.1,second.business().analysis().getSnapshot().report().path("official_gpa").asDouble());
        assertEquals(2,pythonReads.size());verifyNoInteractions(records);
    }

    @Test void historyCannotOverrideFreshToolFactsEvenWhenModelRepeatsClaim() throws Exception {
        long id=create(A);seed(id,"我的 GPA 是 4.0", "你声称 GPA 4.0");wrongGpa=true;
        JsonNode result=chat(A,id);String answer=result.path("answer").asText();
        assertTrue(answer.contains("2.8"),answer);assertFalse(answer.contains("4.0"),answer);
        var ctx=contexts.peek();assertTrue(ctx.conversation().history().toString().contains("4.0"));
        var assessment=ctx.business().results().stream().filter(r->r.tool().equals("get_academic_assessment")).findFirst().orElseThrow();
        assertEquals(2.8,mapper.valueToTree(assessment.data()).path("officialGpa").asDouble());assertEquals(1,pythonReads.size());
    }

    @Test void concurrentStreamingEvidenceAndResultStayWithTheirOwners() throws Exception {
        pythonBarrier=new CyclicBarrier(2);var work=new ArrayList<Callable<Void>>();
        for(Actor a:ACTORS) {long id=create(a);work.add(() -> {
            MvcResult pending=mvc.perform(post("/api/student/agent/chat/stream").header("Authorization",bearer(a))
                .contentType("application/json").content(body(id,a))).andExpect(request().asyncStarted()).andReturn();
            pending.getAsyncResult(20000);
            String raw=mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertPrivate(raw,a);Set<String> evidence=new HashSet<>();boolean result=false;
            for(String line:raw.lines().toList()) {
                JsonNode event=mapper.readTree(line);
                if(event.path("type").asText().equals("evidence")) {
                    JsonNode data=event.path("data");String kind=data.path("kind").asText();evidence.add(kind);
                    switch(kind) {
                        case "overview" -> {assertEquals(a.gpa(),data.path("metrics").get(0).path("value").asDouble());assertEquals(a==A?13:27,data.path("metrics").get(1).path("value").asInt());}
                        case "recent" -> {assertEquals(a.course(),data.path("courses").get(0).path("name").asText());assertEquals(Integer.toString(a.score()),data.path("courses").get(0).path("score").asText());}
                        case "trend" -> assertEquals(a.gpa(),data.path("semesters").get(1).path("weightedGpa").asDouble());
                        default -> fail("Unexpected evidence: "+kind);
                    }
                }
                if(event.path("type").asText().equals("result")) {result=true;assertEquals(id,event.path("data").path("conversationId").asLong());assertChat(event.path("data"),a,a.gpa());}
                assertNotEquals("error",event.path("type").asText(),line);
            }
            assertEquals(Set.of("overview","recent","trend"),evidence);assertTrue(result);return null;
        });}
        parallel(work,2);assertEquals(2,peak.get());assertEquals(2,pythonReads.size());verifyNoInteractions(records);
    }

    @Test void cachedFailureInOneStudentRequestDoesNotPoisonAnotherStudent() throws Exception {
        pythonBarrier=new CyclicBarrier(2);failA=true;long aid=create(A),bid=create(B);
        parallel(List.of(() -> {assertEquals("DATA_UNAVAILABLE",chat(A,aid).path("status").asText());return null;},
            () -> {assertChat(chat(B,bid),B,3.9);return null;}),2);
        assertEquals(1,Collections.frequency(new ArrayList<>(pythonReads),A.no()));
        assertEquals(1,Collections.frequency(new ArrayList<>(pythonReads),B.no()));
        var failed=contexts.stream().filter(c->c.identity().studentNo().equals(A.no())).findFirst().orElseThrow();
        RuntimeException one=assertThrows(RuntimeException.class,()->failed.business().analysis().getSnapshot());
        RuntimeException two=assertThrows(RuntimeException.class,()->failed.business().analysis().getSnapshot());
        assertSame(one,two);assertEquals(2,pythonReads.size());verifyNoInteractions(records);
    }

    Actor actor(String no) {return ACTORS.stream().filter(a->a.no().equals(no)).findFirst().orElseThrow();}
    void assertPrivate(String text,Actor a) {
        Actor other=a==A?B:A;
        for(String forbidden:List.of(other.no(),other.major(),other.course(),other.version(),other.history()))assertFalse(text.contains(forbidden),"Foreign data: "+forbidden);
    }
    String bearer(Actor a) {return "Bearer "+jwt.createToken(a.no(),"student");}
    String body(long id,Actor a) throws Exception {return mapper.writeValueAsString(Map.of("conversationId",id,"message",a.label()+"_QUERY 帮我分析最近学习情况及成绩趋势"));}
    long create(Actor a) throws Exception {
        var r=mvc.perform(post("/api/student/agent/conversations").header("Authorization",bearer(a)))
            .andExpect(status().isOk()).andReturn();long id=mapper.readTree(r.getResponse().getContentAsString()).path("data").path("id").asLong();
        assertTrue(id>0);owners.put(id,a);return id;
    }
    void seed(long id,String user,String assistant) {
        jdbc.update("INSERT INTO agent_conversation_message(conversation_id,role,content) VALUES(?,'USER',?)",id,user);
        jdbc.update("INSERT INTO agent_conversation_message(conversation_id,role,content) VALUES(?,'ASSISTANT',?)",id,assistant);
    }
    JsonNode chat(Actor a,long id) throws Exception {
        var r=mvc.perform(post("/api/student/agent/chat").header("Authorization",bearer(a)).contentType("application/json").content(body(id,a)))
            .andExpect(status().isOk()).andReturn();return mapper.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }
    ConversationChatService.Prepared prepare(Actor a,long id) throws Exception {
        var request=new MockHttpServletRequest("POST","/api/student/agent/chat");request.addHeader("Authorization",bearer(a));
        auth.doFilter(request,new MockHttpServletResponse(),(req,res)->{});
        return chats.prepare(ConversationChatRequest.parse(mapper.readTree(body(id,a))),request);
    }
    void assertChat(JsonNode result,Actor a,double gpa) {
        assertEquals("COMPLETED",result.path("status").asText(),result.toString());String answer=result.path("answer").asText();
        assertTrue(answer.contains(Double.toString(gpa)),answer);assertTrue(answer.contains(a.course()),answer);assertTrue(answer.contains(a.major()),answer);
        assertPrivate(result.toString(),a);assertEquals(4,result.path("toolCalls").size());
        for(JsonNode trace:result.path("toolCalls"))assertEquals("OK",trace.path("status").asText());
        assertFalse(result.toString().contains(a.no()));assertFalse(result.toString().contains("private-transcript.pdf"));
    }
    void parallel(List<Callable<Void>> work,int threads) throws Exception {
        var pool=Executors.newFixedThreadPool(threads);
        try {var futures=new ArrayList<Future<Void>>();for(var task:work)futures.add(pool.submit(task));for(var future:futures)future.get(45,TimeUnit.SECONDS);}
        finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
    DeepSeekReply modelReply(ArrayNode messages) throws Exception {
        String current="";for(JsonNode m:messages)if(m.path("content").asText().startsWith("CURRENT USER MESSAGE\n"))current=m.path("content").asText();
        assertFalse(current.isEmpty());Actor a=current.contains("ALPHA_QUERY")?A:B;
        JsonNode assessment=null;
        for(JsonNode m:messages) {
            if(!m.path("role").asText().equals("system"))assertPrivate(m.path("content").asText(),a);
            if(m.path("role").asText().equals("tool")) {
                JsonNode tool=mapper.readTree(m.path("content").asText());
                if(tool.path("status").asText().equals("OK")) {
                    var data=tool.path("data");var source=data.has("source")?data.path("source"):data;
                    assertEquals(a.major(),source.path("major").asText());
                    switch(tool.path("tool").asText()) {
                        case "get_academic_assessment" -> {
                            assessment=data;assertEquals(a==A?13:27,data.path("totalEarnedCredits").asInt());
                            assertEquals(a.course(),data.path("completedCoreCourses").get(0).asText());
                            assertEquals(gpas.get(a.no()),data.path("officialGpa").asDouble());
                        }
                        case "get_recent_course_performance" -> {
                            assertEquals(a.course(),data.path("focusCourses").get(0).path("name").asText());
                            assertEquals(a.score(),data.path("focusCourses").get(0).path("numericScore").asInt());
                        }
                        case "get_academic_trend" -> assertEquals(gpas.get(a.no()),data.path("semesters").get(1).path("weightedGpa").asDouble());
                        default -> { }
                    }
                }
            }
        }
        boolean hasTools=false;for(JsonNode m:messages)if(m.path("role").asText().equals("tool"))hasTools=true;
        var message=mapper.createObjectNode().put("role","assistant");
        if(!hasTools) {
            message.putNull("content");var calls=message.putArray("tool_calls");
            for(int i=0;i<NAMES.size();i++) {var c=calls.addObject().put("id","shared-id-"+i).put("type","function");c.putObject("function").put("name",NAMES.get(i)).put("arguments","{}");}
            return new DeepSeekReply(message,"tool_calls");
        }
        if(assessment==null || wrongGpa)message.put("content","你的 GPA 是 4.0，没有风险");
        else {
            assertEquals(gpas.get(a.no()),assessment.path("officialGpa").asDouble());
            message.put("content","官方 GPA 为 "+assessment.path("officialGpa").asDouble()+"；课程 "+assessment.path("completedCoreCourses").get(0).asText()
                +"；专业 "+assessment.path("source").path("major").asText()+"；方案 "+assessment.path("source").path("trainingPlanVersion").asText());
        }
        return new DeepSeekReply(message,"stop");
    }
    ObjectNode response(Actor a,double gpa) {
        var root=mapper.createObjectNode().put("status","success");var data=root.putObject("data");
        data.putArray("courses").addObject().put("name",a.course()).put("score",a.score()).put("gpa",gpa).put("credit",4);
        var report=data.putObject("report").put("total_earned_credits",a==A?13:27).put("official_gpa",gpa).put("official_gpa_available",true)
            .put("warning_level",a==A?"一般预警":"正常").put("completed_semester",2).put("issue_course_count",0).put("core_course_count",1);
        for(String field:List.of("failed_courses","core_courses","missing_core_courses","pending_core_courses","unscheduled_core_courses","unknown_score_courses","course_suggestions","analysis_notes"))report.putArray(field);
        report.withArray("core_courses").add(a.course());
        var statistics=data.putObject("statistics").put("schema_version",1).put("latest_semester","2025-2026春季").put("previous_semester","2025-2026秋季");
        statistics.putArray("valid_semesters").add("2025-2026秋季").add("2025-2026春季");statistics.putArray("analysis_notes");
        statistics.putObject("rules").put("low_score_min",60).put("low_score_max_exclusive",70);
        var semesters=statistics.putArray("semesters");
        for(int i=0;i<2;i++) {
            var s=semesters.addObject().put("semester",i==0?"2025-2026秋季":"2025-2026春季").put("course_count",1).put("weighted_gpa",i==0?gpa-.4:gpa)
                .put("average_numeric_score",a.score()-1+i).put("failed_course_count",0);
            var focus=s.putArray("focus_courses");s.putArray("analysis_notes");
            if(i==1) {
                focus.addObject().put("name",a.course()).put("score",Integer.toString(a.score())).put("numeric_score",a.score()).put("credit",4).put("page",1).put("passed",true).put("reason","LOW_NUMERIC_SCORE");
                s.putObject("change_from_previous").put("semester","2025-2026秋季").put("weighted_gpa_delta",.4).put("gpa_direction","UP").put("average_numeric_score_delta",1).put("numeric_score_direction","UP").put("failed_course_count_delta",0);
            } else s.putNull("change_from_previous");
        }
        return root;
    }
}
