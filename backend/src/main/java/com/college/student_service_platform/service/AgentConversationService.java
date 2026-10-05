package com.college.student_service_platform.service;

import com.college.student_service_platform.agent.AgentChatRequest;
import com.college.student_service_platform.agent.AgentChatResponse;
import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.dto.AgentConversationDtos.*;
import com.college.student_service_platform.entity.AgentConversation;
import com.college.student_service_platform.repository.AgentConversationRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import com.college.student_service_platform.agent.AgentIdentityContext;
import com.college.student_service_platform.agent.ConversationContextBudget;
import java.util.Set;

@Service
public class AgentConversationService {
    private static final Set<String> ROLES = Set.of("USER", "ASSISTANT");
    private static final Set<String> RESPONSES = Set.of("COMPLETED", "DATA_UNAVAILABLE", "TOOL_LIMIT", "REFUSED", "OUT_OF_SCOPE", "NEEDS_CLARIFICATION");
    private final AgentConversationRepository repository;
    private final TransactionTemplate transactions;
    public AgentConversationService(AgentConversationRepository repository, PlatformTransactionManager manager) {
        this.repository = repository; this.transactions = new TransactionTemplate(manager);
        // Row locks serialize one conversation; READ COMMITTED avoids broad message-gap
        // locks and stale snapshots when unrelated students chat concurrently in MySQL.
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public long currentStudentId(HttpServletRequest request) { return currentIdentity(request).studentId(); }
    public AgentIdentityContext currentIdentity(HttpServletRequest request) {
        String subject = AuthContext.subject(request);
        if (!"student".equals(AuthContext.role(request))) throw new ApiException(HttpStatus.FORBIDDEN, "会话仅支持学生本人使用");
        long studentId = repository.findActiveStudentId(subject).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "当前学生账号不可用"));
        return new AgentIdentityContext(studentId, subject);
    }
    /** Single ownership gate. Controllers never receive an owner parameter from clients. */
    public AgentConversation getOwnedConversation(long id, long currentStudentId) {
        return owned(id, currentStudentId, false);
    }
    private AgentConversation owned(long id, long studentId, boolean lock) {
        if (id <= 0) throw new IllegalArgumentException("会话 ID 必须为正整数");
        var row = repository.findOwned(id, studentId, lock)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会话不存在或无权访问"));
        if (row.studentId() != studentId) throw new ApiException(HttpStatus.NOT_FOUND, "会话不存在或无权访问");
        return row;
    }
    public Summary create(HttpServletRequest request, String requestedTitle) {
        long studentId = currentStudentId(request);
        String title = requestedTitle == null ? "新会话" : title(requestedTitle);
        return transactions.execute(tx -> Summary.from(getOwnedConversation(repository.insert(studentId, title), studentId)));
    }
    public Page list(HttpServletRequest request, int page, int pageSize) {
        long studentId = currentStudentId(request);
        if (page < 1 || page > 100000 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数不合法");
        return transactions.execute(tx -> new Page(repository.list(studentId, pageSize, (page - 1) * pageSize)
                .stream().map(Summary::from).toList(), repository.count(studentId), page, pageSize));
    }
    public Detail detail(HttpServletRequest request, long id, Long beforeId, int limit) {
        long studentId = currentStudentId(request);
        if (limit < 1 || limit > 100 || (beforeId != null && beforeId <= 0)) throw new IllegalArgumentException("消息分页参数不合法");
        return transactions.execute(tx -> {
            var conversation = owned(id, studentId, true);
            var fetched = repository.recent(id, beforeId, limit + 1);
            boolean more = fetched.size() > limit;
            var rows = more ? fetched.subList(1, fetched.size()) : fetched;
            var messages = rows.stream().filter(row -> ROLES.contains(row.role())).map(Message::from).toList();
            Long next = more && !rows.isEmpty() ? rows.get(0).id() : null;
            return new Detail(Summary.from(conversation), messages, more, next);
        });
    }
    public Summary rename(HttpServletRequest request, long id, String requestedTitle) {
        long studentId = currentStudentId(request);
        String validTitle = title(requestedTitle);
        return transactions.execute(tx -> {
            owned(id, studentId, true); repository.touch(id, studentId, validTitle);
            return Summary.from(getOwnedConversation(id, studentId));
        });
    }
    public void delete(HttpServletRequest request, long id) {
        long studentId = currentStudentId(request);
        transactions.executeWithoutResult(tx -> { owned(id, studentId, true); repository.delete(id, studentId); });
    }
    public record ChatInput(long conversationId, AgentIdentityContext identity, long expectedLastMessageId, AgentChatRequest agentRequest) {
        public long studentId() { return identity.studentId(); }
    }
    public ChatInput prepareChat(HttpServletRequest request, long id, String message) {
        var identity = currentIdentity(request);
        return transactions.execute(tx -> {
            owned(id, identity.studentId(), true);
            var rows = repository.recent(id, null, ConversationContextBudget.MAX_SCANNED_MESSAGES);
            return new ChatInput(id, identity, repository.latestMessageId(id),
                    new AgentChatRequest(message, ConversationContextBudget.recentCompletePairs(rows, message)));
        });
    }
    /** No transaction is held while the model runs. Commit a whole exchange or nothing. */
    public ChatResult saveExchange(ChatInput input, AgentChatResponse response) {
        if (response == null || response.answer() == null || response.answer().isBlank() || response.status() == null || !RESPONSES.contains(response.status()))
            throw new ApiException(HttpStatus.BAD_GATEWAY, "未收到有效的 Agent 回答");
        return transactions.execute(tx -> {
            if (!repository.isStudentActive(input.studentId())) throw new ApiException(HttpStatus.FORBIDDEN, "当前学生账号不可用");
            var conversation = owned(input.conversationId(), input.studentId(), true);
            if (repository.latestMessageId(conversation.id()) != input.expectedLastMessageId())
                throw new ApiException(HttpStatus.CONFLICT, "此会话已收到新的消息，请刷新后重试");
            var now = LocalDateTime.now();
            repository.append(conversation.id(), "USER", input.agentRequest().message(), now);
            repository.append(conversation.id(), "ASSISTANT", response.answer(), now);
            String nextTitle = input.expectedLastMessageId() == 0 && "新会话".equals(conversation.title())
                    ? automaticTitle(input.agentRequest().message()) : conversation.title();
            repository.touch(conversation.id(), input.studentId(), nextTitle);
            return ChatResult.from(conversation.id(), response);
        });
    }
    private String title(String value) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > 100)
            throw new IllegalArgumentException("标题必须为 1～100 字符的非空文本");
        return value.trim();
    }
    private String automaticTitle(String question) {
        String value = question.trim().replaceAll("\\s+", " ");
        return value.substring(0, value.offsetByCodePoints(0, Math.min(100, value.codePointCount(0, value.length()))));
    }
}
