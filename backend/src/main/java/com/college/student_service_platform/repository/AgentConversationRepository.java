package com.college.student_service_platform.repository;

import com.college.student_service_platform.entity.AgentConversation;
import com.college.student_service_platform.entity.AgentConversationMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Same JDBC/handwritten mapper style as existing business repositories. */
@Repository
public class AgentConversationRepository {
    private final JdbcTemplate jdbc;
    public AgentConversationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Long> findActiveStudentId(String authenticatedStudentNo) {
        return jdbc.query("""
                SELECT s.id FROM t_student s JOIN t_user u ON u.student_no = s.student_no
                WHERE s.student_no = ? AND s.status = 1 AND u.role_code = 'student' AND u.status = 1
                """, (rs, n) -> rs.getLong("id"), authenticatedStudentNo).stream().findFirst();
    }
    public boolean isStudentActive(long id) {
        return !jdbc.query("""
                SELECT s.id FROM t_student s JOIN t_user u ON u.student_no = s.student_no
                WHERE s.id = ? AND s.status = 1 AND u.role_code = 'student' AND u.status = 1
                """, (rs, n) -> rs.getLong("id"), id).isEmpty();
    }
    public Optional<AgentConversation> findOwned(long id, long studentId, boolean lock) {
        return jdbc.query("SELECT * FROM agent_conversation WHERE id = ? AND student_id = ?"
                        + (lock ? " FOR UPDATE" : ""), this::mapConversation, id, studentId).stream().findFirst();
    }
    public long insert(long studentId, String title) {
        var keys = new GeneratedKeyHolder();
        var now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO agent_conversation (student_id, title, created_at, updated_at) VALUES (?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, studentId); statement.setString(2, title);
            statement.setTimestamp(3, now); statement.setTimestamp(4, now);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }
    public long count(long studentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM agent_conversation WHERE student_id = ?", Long.class, studentId);
    }
    public List<AgentConversation> list(long studentId, int limit, int offset) {
        return jdbc.query("""
                SELECT * FROM agent_conversation WHERE student_id = ?
                ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?
                """, this::mapConversation, studentId, limit, offset);
    }
    public List<AgentConversationMessage> recent(long conversationId, Long beforeId, int limit) {
        var rows = beforeId == null
                ? jdbc.query("SELECT * FROM agent_conversation_message WHERE conversation_id = ? ORDER BY id DESC LIMIT ?",
                        this::mapMessage, conversationId, limit)
                : jdbc.query("SELECT * FROM agent_conversation_message WHERE conversation_id = ? AND id < ? ORDER BY id DESC LIMIT ?",
                        this::mapMessage, conversationId, beforeId, limit);
        Collections.reverse(rows); // ID is committed append order, and breaks same-timestamp ties.
        return rows;
    }
    public long latestMessageId(long conversationId) {
        // A locking/current read matters under MySQL REPEATABLE READ: a previous ordinary
        // query in this transaction must not hide an exchange committed while we waited.
        return jdbc.query("SELECT id FROM agent_conversation_message WHERE conversation_id = ? ORDER BY id DESC LIMIT 1 FOR UPDATE",
                (rs, n) -> rs.getLong("id"), conversationId).stream().findFirst().orElse(0L);
    }
    public void append(long id, String role, String content, LocalDateTime time) {
        if (!List.of("USER", "ASSISTANT").contains(role)) throw new IllegalArgumentException("非法会话消息角色");
        jdbc.update("INSERT INTO agent_conversation_message (conversation_id, role, content, created_at) VALUES (?, ?, ?, ?)",
                id, role, content, Timestamp.valueOf(time));
    }
    public void touch(long id, long studentId, String title) {
        jdbc.update("UPDATE agent_conversation SET title = ?, updated_at = ? WHERE id = ? AND student_id = ?",
                title, Timestamp.valueOf(LocalDateTime.now()), id, studentId);
    }
    public void delete(long id, long studentId) {
        jdbc.update("DELETE FROM agent_conversation WHERE id = ? AND student_id = ?", id, studentId);
    }
    private AgentConversation mapConversation(ResultSet rs, int n) throws SQLException {
        return new AgentConversation(rs.getLong("id"), rs.getLong("student_id"), rs.getString("title"),
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime());
    }
    private AgentConversationMessage mapMessage(ResultSet rs, int n) throws SQLException {
        return new AgentConversationMessage(rs.getLong("id"), rs.getLong("conversation_id"), rs.getString("role"),
                rs.getString("content"), rs.getTimestamp("created_at").toLocalDateTime());
    }
}
