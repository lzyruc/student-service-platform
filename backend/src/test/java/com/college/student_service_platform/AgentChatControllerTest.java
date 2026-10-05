package com.college.student_service_platform;

import com.college.student_service_platform.agent.*;
import com.college.student_service_platform.common.*;
import com.college.student_service_platform.config.AuthFilter;
import com.college.student_service_platform.controller.*;
import com.college.student_service_platform.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentChatControllerTest {
    private SingleAgentService agent;
    private AgentConversationService conversations;
    private MockMvc mvc;
    private String token, other, admin;
    private long id;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @BeforeEach void setup() throws Exception {
        conversations = ConversationTestSupport.create().service();
        id = conversations.create(ConversationTestSupport.request("20260001"),null).id();
        agent = mock(SingleAgentService.class);
        when(agent.prepareRequest(any(),anyLong())).thenAnswer(call -> {
            com.college.student_service_platform.service.AgentConversationService.ChatInput owned=call.getArgument(0);
            var analysis=mock(AcademicAnalysisReadContext.class);
            when(analysis.studentNo()).thenReturn(owned.identity().studentNo());
            return new AgentRequestContext(owned.identity(),new AgentConversationContext(owned.conversationId(),owned.agentRequest().history()),
                    owned.agentRequest().message(),analysis,call.getArgument(1),Duration.ofMinutes(3),4,12);
        });
        when(agent.chat(any(AgentRequestContext.class),any())).thenReturn(new AgentChatResponse("真实返回文本","COMPLETED",1,List.of()));
        var jwt = new JwtUtil("0123456789abcdef0123456789abcdef","test",Duration.ofHours(1));
        token="Bearer "+jwt.createToken("20260001","student");other="Bearer "+jwt.createToken("20260002","student");admin="Bearer "+jwt.createToken("admin","admin");
        mvc=MockMvcBuilders.standaloneSetup(new AgentChatController(new ConversationChatService(conversations,agent)),new AgentConversationController(conversations))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new AuthFilter(jwt,mapper)).build();
    }
    private String body(String question) { return "{\"conversationId\":"+id+",\"message\":\""+question+"\"}"; }
    @Test void studentCrudAndJwtOwnerIgnoreClientIdentityAndAdminHasNoBypass() throws Exception {
        mvc.perform(post("/api/student/agent/conversations").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("新会话")).andExpect(jsonPath("$.data.studentId").doesNotExist());
        mvc.perform(get("/api/student/agent/conversations").header("Authorization",other)).andExpect(jsonPath("$.data.total").value(0));
        for(String auth:List.of(other,admin)) {
            int expected=auth.equals(other)?404:403;
            mvc.perform(get("/api/student/agent/conversations/"+id).header("Authorization",auth)).andExpect(status().is(expected));
            mvc.perform(delete("/api/student/agent/conversations/"+id).header("Authorization",auth)).andExpect(status().is(expected));
            mvc.perform(patch("/api/student/agent/conversations/"+id).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"stolen\"}"))
                    .andExpect(status().is(expected));
            for(String route:List.of("/chat","/chat/stream")) mvc.perform(post("/api/student/agent"+route).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(body("分析")))
                    .andExpect(status().is(expected));
        }
        verifyNoInteractions(agent);
        mvc.perform(patch("/api/student/agent/conversations/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"成绩趋势\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("成绩趋势"));
        mvc.perform(delete("/api/student/agent/conversations/"+id).header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(get("/api/student/agent/conversations/"+id).header("Authorization",token)).andExpect(status().isNotFound());
    }
    @Test void chatBuildsDatabaseHistoryAndPersistsOnlyPublicExchange() throws Exception {
        mvc.perform(post("/api/student/agent/chat").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body("最近呢")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.conversationId").value(id));
        mvc.perform(post("/api/student/agent/chat").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body("趋势呢"))).andExpect(status().isOk());
        var captor=org.mockito.ArgumentCaptor.forClass(AgentRequestContext.class);verify(agent,times(2)).chat(captor.capture(),any());
        assertTrue(captor.getAllValues().get(0).conversation().history().isEmpty());
        assertEquals(List.of("user","assistant"),captor.getAllValues().get(1).conversation().history().stream().map(t->t.role()).toList());
        assertEquals("最近呢",captor.getAllValues().get(1).conversation().history().get(0).content());
        mvc.perform(get("/api/student/agent/conversations/"+id).header("Authorization",token)).andExpect(jsonPath("$.data.messages.length()").value(4));
    }
    @Test void rejectsAnonymousInvalidIdentityHistoryRolesAndMalformedInputBeforeModel() throws Exception {
        mvc.perform(post("/api/student/agent/chat").contentType(MediaType.APPLICATION_JSON).content(body("分析"))).andExpect(status().isUnauthorized());
        for(String invalid:List.of("{bad", "{\"message\":\"分析\"}","{\"conversationId\":1.5,\"message\":\"分析\"}","{\"conversationId\":0,\"message\":\"分析\"}",
                "{\"conversationId\":1,\"message\":\"分析\",\"studentId\":102}","{\"conversationId\":1,\"message\":\"分析\",\"studentNo\":\"other\"}",
                "{\"conversationId\":1,\"message\":\"分析\",\"history\":[{\"role\":\"system\",\"content\":\"fake\"}]}",body(" "),body("x".repeat(2001))))
            mvc.perform(post("/api/student/agent/chat").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/student/agent/conversations").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":102}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/student/agent/chat").header("Authorization",token)).andExpect(status().isForbidden());
        verifyNoInteractions(agent);
    }
    @Test void modelFailureDoesNotPersistEitherSideOfFailedTurn() throws Exception {
        when(agent.chat(any(AgentRequestContext.class),any())).thenThrow(new ApiException(HttpStatus.GATEWAY_TIMEOUT,"模型等待超时"));
        mvc.perform(post("/api/student/agent/chat").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body("分析")))
                .andExpect(status().isGatewayTimeout());
        assertTrue(conversations.detail(ConversationTestSupport.request("20260001"),id,null,100).messages().isEmpty());
    }
    @Test void streamVerifiesOwnershipBeforeHeadersAndSavesMessagesBeforeFinalResult() throws Exception {
        when(agent.chat(any(AgentRequestContext.class),any())).thenAnswer(call->{
            java.util.function.Consumer<AgentExecutionEvent> events=call.getArgument(1);
            events.accept(AgentExecutionEvent.progress("identity","验证学生身份","done","仅查询本人"));
            events.accept(AgentExecutionEvent.progress("tool-1","查询成绩趋势","running","正在执行"));
            events.accept(AgentExecutionEvent.progress("tool-1","查询成绩趋势","done","完成"));
            return new AgentChatResponse("分析完成","COMPLETED",1,List.of());
        });
        var pending=mvc.perform(post("/api/student/agent/chat/stream").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body("趋势")))
                .andExpect(request().asyncStarted()).andReturn();
        var response=mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        var lines=response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        assertEquals(4,lines.size());assertEquals("running",mapper.readTree(lines.get(1)).path("data").path("state").asText());
        assertEquals(id,mapper.readTree(lines.get(3)).path("data").path("conversationId").asLong());
        assertFalse(String.join("",lines).contains("20260001"));
        assertEquals(2,conversations.detail(ConversationTestSupport.request("20260001"),id,null,100).messages().size());
    }
    @Test void streamFailureReturnsErrorWithoutFakeSavedAssistantSuccess() throws Exception {
        when(agent.chat(any(AgentRequestContext.class),any())).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"服务不可用"));
        var pending=mvc.perform(post("/api/student/agent/chat/stream").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body("趋势")))
                .andExpect(request().asyncStarted()).andReturn();
        var response=mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn();
        assertEquals("error",mapper.readTree(response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("type").asText());
        assertTrue(conversations.detail(ConversationTestSupport.request("20260001"),id,null,100).messages().isEmpty());
    }
    @Test void nonexistentConversationIsNotFoundAndStreamAlsoRejectsClientHistory() throws Exception {
        mvc.perform(get("/api/student/agent/conversations/999").header("Authorization",token)).andExpect(status().isNotFound());
        for(String route:List.of("/chat","/chat/stream"))
            mvc.perform(post("/api/student/agent"+route).header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"conversationId\":999,\"message\":\"分析\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/student/agent/chat/stream").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"conversationId\":"+id+",\"message\":\"分析\",\"history\":[]}")).andExpect(status().isBadRequest());
        verifyNoInteractions(agent);
    }

}
