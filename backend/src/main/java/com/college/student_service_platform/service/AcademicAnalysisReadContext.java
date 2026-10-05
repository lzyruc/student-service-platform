package com.college.student_service_platform.service;

import com.college.student_service_platform.dto.AcademicAnalysisSnapshot;
import com.college.student_service_platform.dto.AcademicContextSnapshot;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Request-local analysis context for one Agent HTTP request and its entire Tool Calling Loop.
 * The caller creates it once, passes it to every academic tool in that loop, and releases it afterward.
 * This is an ordinary Java object, not an automatically request-scoped Spring bean.
 * Never store it in a conversation, HTTP session, or singleton; the next request creates a new instance.
 */
public final class AcademicAnalysisReadContext {
    private final String studentNo;
    private final Supplier<AcademicAnalysisSnapshot> loader;
    private final Supplier<AcademicContextSnapshot> contextLoader;
    private AcademicAnalysisSnapshot snapshot;
    private AcademicContextSnapshot academicContext;
    private RuntimeException contextFailure;
    private RuntimeException failure;

    AcademicAnalysisReadContext(String studentNo, Supplier<AcademicAnalysisSnapshot> loader,
                                Supplier<AcademicContextSnapshot> contextLoader) {
        this.studentNo = Objects.requireNonNull(studentNo);
        this.loader = Objects.requireNonNull(loader);
        this.contextLoader = Objects.requireNonNull(contextLoader);
    }

    public String studentNo() { return studentNo; }

    /** A preliminary availability check; once analyzed, metadata comes from that exact snapshot. */
    public synchronized AcademicContextSnapshot getAcademicContext() {
        if (snapshot != null) return AcademicContextSnapshot.fromAnalysis(snapshot);
        if (contextFailure != null) throw contextFailure;
        if (academicContext == null) {
            try {
                academicContext = Objects.requireNonNull(contextLoader.get());
            } catch (RuntimeException e) {
                contextFailure = e;
                throw e;
            }
        }
        return academicContext;
    }

    /** Concurrent tools share one load. A failed load is not implicitly retried by another tool. */
    public synchronized AcademicAnalysisSnapshot getSnapshot() {
        if (failure != null) throw failure;
        if (snapshot == null) {
            try {
                var loaded = Objects.requireNonNull(loader.get());
                if (!studentNo.equals(loaded.studentNo())) throw new IllegalStateException("分析结果与当前学生身份不一致");
                snapshot = loaded;
            } catch (RuntimeException e) {
                failure = e;
                throw e;
            }
        }
        return snapshot;
    }
}
