package com.college.student_service_platform.agent;

import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.service.AcademicAnalysisReadContext;
import org.springframework.http.HttpStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One HTTP request / Tool Calling Loop, never a singleton bean, session or persisted conversation. */
public final class AgentRequestContext {
    public enum State { PREPARED, RUNNING, SUCCEEDED, FAILED }
    private final AgentIdentityContext identity;
    private final AgentConversationContext conversation;
    private final String currentMessage;
    private final AcademicBusinessContext business;
    private final long deadlineNanos;
    private final int maxToolRounds, maxToolCalls;
    private final Set<String> callIds = new HashSet<>();
    private final List<AgentChatResponse.ToolTrace> trace = new ArrayList<>();
    private int rounds, calls;
    private State state = State.PREPARED;

    public AgentRequestContext(AgentIdentityContext identity, AgentConversationContext conversation, String currentMessage,
                               AcademicAnalysisReadContext analysis, long startedAtNanos, Duration timeout,
                               int maxToolRounds, int maxToolCalls) {
        this.identity = java.util.Objects.requireNonNull(identity);
        this.conversation = java.util.Objects.requireNonNull(conversation);
        new AgentChatRequest(currentMessage, conversation.history()); // Same validated budget as DB selection.
        if (!identity.studentNo().equals(analysis.studentNo()))
            throw new ApiException(HttpStatus.FORBIDDEN, "分析上下文与当前学生身份不一致");
        if (timeout == null || timeout.isZero() || timeout.isNegative() || maxToolRounds < 1 || maxToolCalls < 1)
            throw new IllegalArgumentException("请求执行预算不合法");
        this.currentMessage = currentMessage; this.business = new AcademicBusinessContext(analysis);
        this.deadlineNanos = startedAtNanos + timeout.toNanos();
        this.maxToolRounds = maxToolRounds; this.maxToolCalls = maxToolCalls;
    }
    public AgentIdentityContext identity() { return identity; }
    public AgentConversationContext conversation() { return conversation; }
    public String currentMessage() { return currentMessage; }
    public AcademicBusinessContext business() { return business; }
    public long deadlineNanos() { return deadlineNanos; }
    public synchronized State state() { return state; }
    public synchronized int toolRounds() { return rounds; }
    public synchronized int toolCallCount() { return calls; }
    public synchronized List<AgentChatResponse.ToolTrace> trace() { return List.copyOf(trace); }
    public synchronized boolean limitReached() { return rounds >= maxToolRounds || calls >= maxToolCalls; }
    public synchronized void beginExecution() {
        if (state != State.PREPARED) throw new ApiException(HttpStatus.CONFLICT, "请求上下文已执行，不能跨请求复用");
        state = State.RUNNING;
    }
    public synchronized void succeed() { state = State.SUCCEEDED; }
    public synchronized void fail() { state = State.FAILED; }
    public void checkDeadline() {
        if (System.nanoTime() - deadlineNanos >= 0)
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "本次 Agent 请求超过耗时预算，请稍后重试");
    }
    public synchronized boolean tryStartToolCall() {
        if (calls >= maxToolCalls) return false;
        calls++; return true;
    }
    public synchronized void finishToolRound() { rounds++; }
    public synchronized void addTrace(AgentChatResponse.ToolTrace item) { trace.add(item); }
    public synchronized void registerCallId(String id) {
        if (!callIds.add(id)) throw new ApiException(HttpStatus.BAD_GATEWAY, "模型返回了重复工具调用标识，请重试");
    }
}
