package com.college.student_service_platform.config;

import com.college.student_service_platform.service.PasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;
    private final String username;
    private final String password;
    private final AtomicLong idSequence = new AtomicLong(System.currentTimeMillis());

    public AdminBootstrap(JdbcTemplate jdbcTemplate, PasswordService passwordService,
                          @Value("${security.bootstrap-admin.username:admin}") String username,
                          @Value("${security.bootstrap-admin.password:}") String password) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordService = passwordService;
        this.username = username == null ? "admin" : username.trim();
        this.password = password == null ? "" : password.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            log.info("ADMIN_BOOTSTRAP_PASSWORD is not set; admin bootstrap skipped");
            return;
        }
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_user WHERE username = ? AND role_code = 'admin'", Integer.class, username);
        if (existing != null && existing > 0) {
            log.info("Admin account already exists; bootstrap skipped");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO t_user(id,username,password,role_code,student_no,status,created_at,updated_at)
                VALUES(?,?,?,'admin',NULL,1,?,?)
                """, idSequence.incrementAndGet(), username, passwordService.encode(password), now, now);
        log.info("Bootstrap admin account created; remove ADMIN_BOOTSTRAP_PASSWORD after first startup");
    }
}
