package com.college.student_service_platform.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

public final class AuthContext {
    public static final String SUBJECT_ATTRIBUTE = "authenticatedSubject";
    public static final String ROLE_ATTRIBUTE = "authenticatedRole";

    private AuthContext() {
    }

    public static String subject(HttpServletRequest request) {
        Object value = request.getAttribute(SUBJECT_ATTRIBUTE);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        return String.valueOf(value);
    }

    public static String role(HttpServletRequest request) {
        Object value = request.getAttribute(ROLE_ATTRIBUTE);
        return value == null ? "" : String.valueOf(value);
    }

    public static boolean isAdmin(HttpServletRequest request) {
        return "admin".equals(role(request));
    }

    public static String studentNo(HttpServletRequest request, String requestedStudentNo) {
        if (!isAdmin(request)) {
            return subject(request);
        }
        if (requestedStudentNo == null || requestedStudentNo.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "studentNo 不能为空");
        }
        return requestedStudentNo.trim();
    }
}
