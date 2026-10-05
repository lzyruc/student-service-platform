package com.college.student_service_platform;

import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.repository.AgentConversationRepository;
import com.college.student_service_platform.service.AgentConversationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

final class ConversationTestSupport {
    record Fixture(JdbcTemplate jdbc, AgentConversationRepository repository, AgentConversationService service) { }
    static Fixture create() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:conversation-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE t_student(id BIGINT PRIMARY KEY, student_no VARCHAR(50) UNIQUE, status INT)");
        jdbc.execute("CREATE TABLE t_user(id BIGINT PRIMARY KEY, student_no VARCHAR(50), role_code VARCHAR(50), status INT)");
        jdbc.execute("INSERT INTO t_student VALUES(101,'20260001',1),(102,'20260002',1)");
        jdbc.execute("INSERT INTO t_user VALUES(11,'20260001','student',1),(12,'20260002','student',1)");
        // Test the actual additive migration, removing only MySQL-specific charset/table options.
        String migration = Files.readString(Path.of("docs/sql/migrations/2026-10-04-agent-conversations.sql"))
                .replace("USE student_platform;", "").replace("SET NAMES utf8mb4;", "")
                .replace("CHARACTER SET ascii COLLATE ascii_bin", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", "");
        for (String sql : migration.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        var repository = new AgentConversationRepository(jdbc);
        return new Fixture(jdbc, repository, new AgentConversationService(repository, new DataSourceTransactionManager(source)));
    }
    static MockHttpServletRequest request(String no) {
        var request = new MockHttpServletRequest();
        request.setAttribute(AuthContext.SUBJECT_ATTRIBUTE, no);
        request.setAttribute(AuthContext.ROLE_ATTRIBUTE, "student");
        return request;
    }
}
