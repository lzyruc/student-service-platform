package com.college.student_service_platform.config;

import com.college.student_service_platform.common.AuthContext;
import com.college.student_service_platform.common.JwtUtil;
import com.college.student_service_platform.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class AuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public AuthFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || uri == null
                || !uri.startsWith("/api/")
                || uri.equals("/api/health")
                || uri.equals("/api/geeker/login")
                || uri.equals("/api/auth/login");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractToken(request);
        if (token == null) {
            writeJson(response, HttpStatus.UNAUTHORIZED, "未登录");
            return;
        }

        Map<String, Object> payload;
        try {
            payload = jwtUtil.verifyToken(token);
        } catch (Exception ignored) {
            writeJson(response, HttpStatus.UNAUTHORIZED, "登录凭证无效或已过期");
            return;
        }

        String subject = String.valueOf(payload.getOrDefault("sub", "")).trim();
        String role = String.valueOf(payload.getOrDefault("role", "")).trim();
        if (subject.isEmpty() || (!"student".equals(role) && !"admin".equals(role))) {
            writeJson(response, HttpStatus.UNAUTHORIZED, "登录凭证无效");
            return;
        }
        if ("student".equals(role) && !isStudentEndpoint(request)) {
            writeJson(response, HttpStatus.FORBIDDEN, "无权限访问该资源");
            return;
        }
        request.setAttribute(AuthContext.SUBJECT_ATTRIBUTE, subject);
        request.setAttribute(AuthContext.ROLE_ATTRIBUTE, role);
        filterChain.doFilter(request, response);
    }

    private boolean isStudentEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (uri.startsWith("/api/student/notice/")
                || uri.startsWith("/api/student/party/")
                || uri.startsWith("/api/student/certificate/")
                || uri.startsWith("/api/student/cert/")
                || uri.equals("/api/student/info")
                || uri.equals("/api/student/ai/ask")
                || uri.equals("/api/student/warning/analyze")
                || uri.startsWith("/api/file/download/")) {
            return true;
        }
        if (uri.equals("/api/certificate/apply/submit") || uri.equals("/api/certificate/apply/list")) {
            return true;
        }
        return uri.matches("^/api/certificate/apply/\\d+$")
                && (HttpMethod.GET.matches(method) || HttpMethod.DELETE.matches(method));
    }

    private String extractToken(HttpServletRequest request) {
        String token = request.getHeader("x-access-token");
        if (token == null || token.isBlank()) {
            token = request.getHeader("authorization");
            if (token != null && token.regionMatches(true, 0, "Bearer ", 0, 7)) {
                token = token.substring(7);
            }
        }
        return token == null || token.isBlank() ? null : token.trim();
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(
                objectMapper.writeValueAsBytes(Result.fail(status.value(), message))
        );
    }
}
