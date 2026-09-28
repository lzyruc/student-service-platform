package com.college.student_service_platform.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserIdentityService {
    private final JdbcTemplate jdbcTemplate;

    public UserIdentityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long requireUserId(String subject, String role) {
        String normalizedSubject = subject == null ? "" : subject.trim();
        String normalizedRole = role == null ? "" : role.trim();
        if (normalizedSubject.isEmpty() || (!"admin".equals(normalizedRole) && !"student".equals(normalizedRole))) {
            throw new IllegalArgumentException("当前登录用户信息不完整");
        }
        String accountColumn = "admin".equals(normalizedRole) ? "username" : "student_no";
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM t_user WHERE " + accountColumn + " = ? AND role_code = ? AND status = 1",
                Long.class,
                normalizedSubject,
                normalizedRole
        );
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("当前登录用户不存在或已停用");
        }
        return ids.get(0);
    }
}
