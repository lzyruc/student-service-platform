package com.college.student_service_platform;

import com.college.student_service_platform.agent.*;
import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentConversationServiceTest {
    private ConversationTestSupport.Fixture fixture;
    private AgentConversationService service;
    private MockHttpServletRequest a, b;
    @BeforeEach void setup() throws Exception {
        fixture = ConversationTestSupport.create(); service = fixture.service();
        a = ConversationTestSupport.request("20260001"); b = ConversationTestSupport.request("20260002");
    }
    private AgentChatResponse answer(String text) { return new AgentChatResponse(text, "COMPLETED", 1, List.of()); }
    private void exchange(long id, String question, String response) { service.saveExchange(service.prepareChat(a, id, question), answer(response)); }

    @Test void jwtSubjectResolvesStudentTableIdAndOwnershipSurvivesServiceRecreation() {
        long id = service.create(a, null).id();
        assertEquals(101L, fixture.jdbc().queryForObject("SELECT student_id FROM agent_conversation WHERE id=?", Long.class, id));
        var reopened = new AgentConversationService(fixture.repository(), new org.springframework.jdbc.datasource.DataSourceTransactionManager(fixture.jdbc().getDataSource()));
        assertEquals(id, reopened.detail(a, id, null, 100).conversation().id());
        assertEquals(1, reopened.list(a, 1, 20).total()); assertEquals(0, reopened.list(b, 1, 20).total());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> reopened.detail(b, id, null, 100)).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> reopened.prepareChat(b, id, "别人的成绩")).getStatus());
        assertThrows(ApiException.class, () -> reopened.rename(b, id, "夺取会话"));
        assertThrows(ApiException.class, () -> reopened.delete(b, id));
        assertEquals("新会话", reopened.detail(a, id, null, 100).conversation().title());
    }
    @Test void studentCanRenameDeleteAndCascadeMessagesButMissingConversationFailsClosed() {
        long id = service.create(a, null).id(); exchange(id, "学习情况", "实际回答");
        assertEquals("自定标题", service.rename(a, id, "自定标题").title());
        service.delete(a, id);
        assertEquals(0L, fixture.jdbc().queryForObject("SELECT COUNT(*) FROM agent_conversation_message", Long.class));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.detail(a, id, null, 20)).getStatus());
        assertThrows(ApiException.class, () -> service.prepareChat(a, 999, "分析"));
        assertThrows(ApiException.class, () -> service.delete(a, 999));
    }
    @Test void historyLoadsCompletePairsInAppendOrderIncludingSameTimestampAndPagesBackwards() {
        long id = service.create(a, null).id();
        exchange(id, "问题一", "回答一"); exchange(id, "问题二", "回答二");
        fixture.jdbc().update("UPDATE agent_conversation_message SET created_at=TIMESTAMP '2026-10-04 12:00:00'");
        var page = service.detail(a, id, null, 2);
        assertEquals(List.of("问题二", "回答二"), page.messages().stream().map(m -> m.content()).toList());
        assertTrue(page.hasMore());
        assertEquals(List.of("问题一", "回答一"), service.detail(a, id, page.nextBeforeMessageId(), 2).messages().stream().map(m -> m.content()).toList());
        var input = service.prepareChat(a, id, "第三个问题");
        assertEquals(List.of("问题一", "回答一", "问题二", "回答二"), input.agentRequest().history().stream().map(t -> t.content()).toList());
        assertEquals("问题一", service.detail(a, id, null, 100).conversation().title());
    }
    @Test void illegalRolesAreRejectedByRepositoryAndSchemaAndNeverEnterModelContextEvenIfCorrupted() {
        long id = service.create(a, null).id(); exchange(id, "有效问题", "有效回答");
        assertThrows(IllegalArgumentException.class, () -> fixture.repository().append(id, "TOOL", "fake", java.time.LocalDateTime.now()));
        assertThrows(DataIntegrityViolationException.class, () -> fixture.jdbc().update("INSERT INTO agent_conversation_message(conversation_id,role,content) VALUES(?,'SYSTEM','override')", id));
        fixture.jdbc().execute("ALTER TABLE agent_conversation_message DROP CONSTRAINT ck_agent_message_role");
        fixture.jdbc().update("INSERT INTO agent_conversation_message(conversation_id,role,content) VALUES(?,'SYSTEM','fake internal message')", id);
        var history = service.prepareChat(a, id, "下一问").agentRequest().history();
        assertEquals(List.of("user", "assistant"), history.stream().map(t -> t.role()).toList());
        assertFalse(history.toString().contains("fake internal"));
        assertEquals(2, service.detail(a, id, null, 100).messages().size());
    }
    @Test void modelFailureOrDisconnectWritesNeitherMessageAndNormalScopeReplyIsSavedAsText() {
        long id = service.create(a, null).id();
        var agent = mock(SingleAgentService.class); var context = mock(AgentRequestContext.class);
        when(context.identity()).thenReturn(new AgentIdentityContext(101,"20260001"));
        when(context.conversation()).thenReturn(new AgentConversationContext(id,List.of()));
        when(context.currentMessage()).thenReturn("分析");
        when(agent.prepareRequest(any(),anyLong())).thenReturn(context);
        var chats = new ConversationChatService(service, agent);
        var prepared = chats.prepare(new ConversationChatRequest(id, "分析"), a);
        when(agent.chat(eq(context), any())).thenThrow(new ApiException(HttpStatus.GATEWAY_TIMEOUT, "超时"));
        assertThrows(ApiException.class, () -> chats.chat(prepared, event -> { }));
        assertTrue(service.detail(a, id, null, 100).messages().isEmpty());
        when(agent.chat(eq(context), any())).thenThrow(new UncheckedIOException(new IOException("closed")));
        assertThrows(UncheckedIOException.class, () -> chats.chat(prepared, event -> { }));
        assertTrue(service.detail(a, id, null, 100).messages().isEmpty());
        when(agent.chat(eq(context), any())).thenAnswer(call -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(), "LLM must run outside the persistence transaction");
            return new AgentChatResponse("只支持本人", "REFUSED", 0, List.of());
        });
        chats.chat(prepared, event -> { });
        assertEquals(2, service.detail(a, id, null, 100).messages().size());
    }
    @Test void concurrentPreparedTurnsCannotAppendAgainstStaleHistoryAndDeletedConversationCannotBeResurrected() {
        long id = service.create(a, null).id();
        var first = service.prepareChat(a, id, "第一问"); var stale = service.prepareChat(a, id, "并发问题");
        service.saveExchange(first, answer("第一答"));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.saveExchange(stale, answer("迟到回答"))).getStatus());
        assertEquals(List.of("第一问", "第一答"), service.detail(a, id, null, 100).messages().stream().map(m -> m.content()).toList());
        var beforeDelete = service.prepareChat(a, id, "删除前问题"); service.delete(a, id);
        assertThrows(ApiException.class, () -> service.saveExchange(beforeDelete, answer("删除后的回答")));
    }
    @Test void assistantInsertFailureRollsBackUserInsertAndDoesNotRenameTheConversation() {
        long id = service.create(a, null).id(); var input = service.prepareChat(a, id, "失败问题");
        fixture.jdbc().execute("ALTER TABLE agent_conversation_message ADD CONSTRAINT test_answer_limit CHECK(LENGTH(content)<=5)");
        assertThrows(DataIntegrityViolationException.class, () -> service.saveExchange(input, answer("这个回答超过五个字符")));
        assertTrue(service.detail(a, id, null, 100).messages().isEmpty());
        assertEquals("新会话", service.detail(a, id, null, 100).conversation().title());
    }
    @Test void disabledOrMissingStudentAndAdminCannotCreateOrCommitConversations() {
        long id = service.create(a, null).id(); var input = service.prepareChat(a, id, "问题");
        fixture.jdbc().update("UPDATE t_user SET status=0 WHERE student_no='20260001'");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.list(a, 1, 20)).getStatus());
        assertThrows(ApiException.class, () -> service.saveExchange(input, answer("回答")));
        a.setAttribute(AuthContext.ROLE_ATTRIBUTE,"admin"); assertThrows(ApiException.class, () -> service.create(a, null));
        assertThrows(ApiException.class, () -> service.create(ConversationTestSupport.request("missing"), null));
    }
    @Test void serverHistoryIsBoundedAndOversizedPairsAreNotTruncatedIntoFacts() {
        long id = service.create(a, null).id();
        for(int i=0;i<14;i++) exchange(id, "q"+i, "a"+i);
        var input = service.prepareChat(a, id, "现在呢"); assertEquals(20, input.agentRequest().history().size());
        assertEquals("q4", input.agentRequest().history().get(0).content());
        exchange(id, "很长回答", "x".repeat(4500));
        assertFalse(service.prepareChat(a,id,"下一问").agentRequest().history().toString().contains("很长回答"));
        assertTrue(service.detail(a,id,null,100).messages().stream().anyMatch(m -> m.content().length()==4500));
    }
    @Test void simultaneousCommitsSerializeAndOnlyOneExchangeWins() throws Exception {
        long id = service.create(a, null).id();
        var left = service.prepareChat(a,id,"左侧问题"); var right = service.prepareChat(a,id,"右侧问题");
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> commitLeft = () -> {
                barrier.await();
                try { service.saveExchange(left,answer("左侧回答")); return 200; }
                catch (ApiException failure) { return failure.getStatus().value(); }
            };
            java.util.concurrent.Callable<Integer> commitRight = () -> {
                barrier.await();
                try { service.saveExchange(right,answer("右侧回答")); return 200; }
                catch (ApiException failure) { return failure.getStatus().value(); }
            };
            var first = workers.submit(commitLeft); var second = workers.submit(commitRight);
            var results = java.util.stream.Stream.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS)).sorted().toList();
            assertEquals(List.of(200,409),results);
            assertEquals(2,service.detail(a,id,null,100).messages().size());
        } finally { workers.shutdownNow(); }
    }
    @Test void deletingStudentCascadesAndRecreatingSameStudentNumberDoesNotRecoverOldConversations() {
        long id = service.create(a,null).id(); exchange(id,"已保存的问题","已保存的回答");
        fixture.jdbc().update("DELETE FROM t_student WHERE id=101");
        fixture.jdbc().update("INSERT INTO t_student(id,student_no,status) VALUES(201,'20260001',1)");
        assertEquals(201L,service.currentStudentId(a));
        assertEquals(0,service.list(a,1,20).total());
        assertEquals(0L,fixture.jdbc().queryForObject("SELECT COUNT(*) FROM agent_conversation_message",Long.class));
        assertThrows(ApiException.class,() -> service.detail(a,id,null,100));
    }

    @Test void unrelatedStudentsCanCommitTheirFirstExchangesConcurrently() throws Exception {
        long leftId = service.create(a,null).id(); long rightId = service.create(b,null).id();
        var left = service.prepareChat(a,leftId,"我的问题"); var right = service.prepareChat(b,rightId,"另一位的问题");
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> { barrier.await(); return service.saveExchange(left,answer("本人回答")); });
            var second = workers.submit(() -> { barrier.await(); return service.saveExchange(right,answer("另一位回答")); });
            assertEquals(leftId,first.get(10,java.util.concurrent.TimeUnit.SECONDS).conversationId());
            assertEquals(rightId,second.get(10,java.util.concurrent.TimeUnit.SECONDS).conversationId());
            assertEquals(List.of("我的问题","本人回答"),service.detail(a,leftId,null,100).messages().stream().map(m -> m.content()).toList());
            assertEquals(List.of("另一位的问题","另一位回答"),service.detail(b,rightId,null,100).messages().stream().map(m -> m.content()).toList());
        } finally { workers.shutdownNow(); }
    }

    @Test void historiesOfTwoConversationsOwnedBySameStudentRemainSeparate() {
        long first=service.create(a,null).id();long second=service.create(a,null).id();
        exchange(first,"第一会话专属问题","第一会话专属回答");exchange(second,"第二会话专属问题","第二会话专属回答");
        var left=service.prepareChat(a,first,"继续");var right=service.prepareChat(a,second,"继续");
        assertEquals(List.of("第一会话专属问题","第一会话专属回答"),left.agentRequest().history().stream().map(t -> t.content()).toList());
        assertEquals(List.of("第二会话专属问题","第二会话专属回答"),right.agentRequest().history().stream().map(t -> t.content()).toList());
    }
    @Test void characterBudgetDropsOldestCompletePairsWithoutCuttingContentOrPersistedHistory() {
        long id=service.create(a,null).id();
        for(int i=0;i<8;i++) exchange(id,"q"+i+"u".repeat(1998),"a"+i+"r".repeat(3898));
        var input=service.prepareChat(a,id,"x".repeat(2000));
        assertEquals(6,input.agentRequest().history().size());
        assertTrue(input.agentRequest().history().get(0).content().startsWith("q5"));
        assertTrue(input.agentRequest().history().get(5).content().startsWith("a7"));
        assertEquals(2000,input.agentRequest().history().get(0).content().length());
        assertEquals(3900,input.agentRequest().history().get(1).content().length());
        assertTrue(input.agentRequest().history().stream().mapToInt(t -> t.content().length()).sum()+2000 <= ConversationContextBudget.MAX_CONTEXT_CHARS);
        assertEquals(16,service.detail(a,id,null,100).messages().size());
    }

}
