package com.college.student_service_platform;

import com.college.student_service_platform.common.JwtUtil;
import com.college.student_service_platform.common.ApiException;
import com.college.student_service_platform.service.AdminAuthService;
import com.college.student_service_platform.service.PasswordService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityComponentsTest {
    @Test
    void bcryptPasswordsAreSaltedAndVerified() {
        PasswordService service = new PasswordService(10);
        String first = service.encode("StrongPassword!2026");
        String second = service.encode("StrongPassword!2026");
        assertNotEquals(first, second);
        assertTrue(service.matches("StrongPassword!2026", first));
        assertFalse(service.matches("wrong-password", first));
        assertThrows(IllegalArgumentException.class, () -> service.encode("short"));
        assertThrows(IllegalArgumentException.class, () -> service.encode("x".repeat(73)));
    }

    @Test
    void legacyMd5CanLoginButIsMarkedForUpgrade() {
        PasswordService service = new PasswordService(10);
        String legacy = "e10adc3949ba59abbe56e057f20f883e";
        assertTrue(service.matches("123456", legacy));
        assertTrue(service.needsUpgrade(legacy));
    }

    @Test
    void jwtRoundTripIncludesTrustedSubjectAndRole() {
        JwtUtil jwt = new JwtUtil("0123456789abcdef0123456789abcdef", "student-service-platform", Duration.ofHours(1));
        String token = jwt.createToken("20240001", "student");
        Map<String, Object> claims = jwt.verifyToken(token);
        assertEquals("20240001", claims.get("sub"));
        assertEquals("student", claims.get("role"));
        assertNotNull(claims.get("exp"));
    }

    @Test
    void shortJwtSecretIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtUtil("short", "issuer", Duration.ofHours(1)));
    }

    @Test
    void adminPasswordChangeVerifiesOldPasswordAndStoresBcrypt() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:password-change;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE t_user (
                    id BIGINT PRIMARY KEY,
                    username VARCHAR(100) NOT NULL,
                    password VARCHAR(255),
                    role_code VARCHAR(50) NOT NULL,
                    student_no VARCHAR(50),
                    status INTEGER NOT NULL,
                    updated_at TIMESTAMP
                )
                """);
        PasswordService passwordService = new PasswordService(10);
        String oldHash = passwordService.encode("old-password");
        jdbcTemplate.update(
                "INSERT INTO t_user(id,username,password,role_code,status) VALUES(1,'admin',?,'admin',1)",
                oldHash);
        AdminAuthService service = new AdminAuthService(
                jdbcTemplate,
                new JwtUtil("0123456789abcdef0123456789abcdef", "test", Duration.ofHours(1)),
                passwordService);

        assertThrows(ApiException.class,
                () -> service.changeAdminPassword("admin", "wrong-password", "new-password"));
        service.changeAdminPassword("admin", "old-password", "new-password");

        String newHash = jdbcTemplate.queryForObject(
                "SELECT password FROM t_user WHERE id = 1", String.class);
        assertNotNull(newHash);
        assertNotEquals(oldHash, newHash);
        assertTrue(passwordService.matches("new-password", newHash));
        assertFalse(passwordService.matches("old-password", newHash));
    }
}
