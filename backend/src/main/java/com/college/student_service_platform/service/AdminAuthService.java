package com.college.student_service_platform.service;

import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.common.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {
    private final JdbcTemplate jdbcTemplate;
    private final JwtUtil jwtUtil;
    private final PasswordService passwordService;

    public AdminAuthService(JdbcTemplate jdbcTemplate, JwtUtil jwtUtil, PasswordService passwordService) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtUtil = jwtUtil;
        this.passwordService = passwordService;
    }

    public String loginAdmin(String username, String password) {
        return authenticate(username, password, "admin").token();
    }

    @Transactional
    public void changeAdminPassword(String username, String oldPassword, String newPassword) {
        String account = normalize(username);
        String oldRaw = normalize(oldPassword);
        String newRaw = normalize(newPassword);
        if (account.isEmpty() || oldRaw.isEmpty() || newRaw.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "原密码和新密码不能为空");
        }
        if (oldRaw.equals(newRaw)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "新密码不能与原密码相同");
        }

        jdbcTemplate.query("""
                SELECT id, password, status
                FROM t_user
                WHERE username = ? AND role_code = 'admin'
                """, resultSet -> {
            if (!resultSet.next()) throw invalidCredentials();
            if (resultSet.getInt("status") == 0) {
                throw new ApiException(HttpStatus.FORBIDDEN, "账号已禁用");
            }
            long userId = resultSet.getLong("id");
            String storedPassword = resultSet.getString("password");
            if (!passwordService.matches(oldRaw, storedPassword)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "原密码错误");
            }
            String encodedPassword = passwordService.encode(newRaw);
            int updated = jdbcTemplate.update("""
                    UPDATE t_user
                    SET password = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND password = ?
                    """, encodedPassword, userId, storedPassword);
            if (updated != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "密码已被修改，请重新登录后再试");
            }
            return null;
        }, account);
    }

    public LoginResult authenticate(String account, String password, String role) {
        String normalizedAccount = normalize(account);
        String rawPassword = normalize(password);
        if (normalizedAccount.isEmpty() || rawPassword.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "账号和密码不能为空");
        }
        if (!"admin".equals(role) && !"student".equals(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "role 只能是 admin 或 student");
        }
        String accountColumn = "student".equals(role) ? "student_no" : "username";
        String sql = "SELECT id, username, role_code, student_no, password, status FROM t_user " +
                "WHERE " + accountColumn + " = ? AND role_code = ?";
        return jdbcTemplate.query(sql, resultSet -> {
            if (!resultSet.next()) throw invalidCredentials();
            long userId = resultSet.getLong("id");
            String storedPassword = resultSet.getString("password");
            if (resultSet.getInt("status") == 0) {
                throw new ApiException(HttpStatus.FORBIDDEN, "账号已禁用");
            }
            if (!passwordService.matches(rawPassword, storedPassword)) throw invalidCredentials();
            if (passwordService.needsUpgrade(storedPassword)) {
                jdbcTemplate.update("UPDATE t_user SET password = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                        passwordService.encode(rawPassword), userId);
            }
            String subject = "student".equals(role) ? resultSet.getString("student_no") : resultSet.getString("username");
            return new LoginResult(userId, resultSet.getString("username"), subject, role,
                    jwtUtil.createToken(subject, role));
        }, normalizedAccount, role);
    }

    public JwtUtil getJwtUtil() {
        return jwtUtil;
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record LoginResult(long id, String username, String subject, String role, String token) {
    }
}
