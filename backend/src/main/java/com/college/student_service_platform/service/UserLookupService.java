package com.college.student_service_platform.service;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserLookupService {

    private final JdbcTemplate jdbcTemplate;

    public UserLookupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long findUserIdByStudentNo(String studentNo) {
        if (studentNo == null || studentNo.isBlank()) {
            return null;
        }

        try {
            String sql = "SELECT id FROM t_user WHERE student_no = ?";
            return jdbcTemplate.queryForObject(sql, Long.class, studentNo);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }
}

