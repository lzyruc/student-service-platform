package com.college.student_service_platform.service;

import com.college.student_service_platform.dto.ApprovalTaskItem;
import com.college.student_service_platform.dto.CertificateApplyDetail;
import com.college.student_service_platform.dto.CertificateApplyItem;
import com.college.student_service_platform.dto.CertificateApplySubmitRequest;
import com.college.student_service_platform.dto.CertificateDecisionRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CertificateApplyService {
    private static final Set<String> SUPPORTED_CERTIFICATE_TYPES = Set.of("在校证明", "请假条", "用章申请");

    private final JdbcTemplate jdbcTemplate;
    private final CertificateFileService certificateFileService;
    private final AtomicLong idSeq = new AtomicLong(System.currentTimeMillis());

    public CertificateApplyService(JdbcTemplate jdbcTemplate, CertificateFileService certificateFileService) {
        this.jdbcTemplate = jdbcTemplate;
        this.certificateFileService = certificateFileService;
    }

    @Transactional
    public Long submit(CertificateApplySubmitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String studentNo = normalize(request.getStudentNo());
        String certificateType = normalize(request.getCertificateType());
        if (studentNo.isEmpty()) {
            throw new IllegalArgumentException("学号不能为空");
        }
        if (studentNo.length() > 50) {
            throw new IllegalArgumentException("学号长度不能超过 50 位");
        }
        if (certificateType.isEmpty()) {
            throw new IllegalArgumentException("证明类型不能为空");
        }
        if (!SUPPORTED_CERTIFICATE_TYPES.contains(certificateType)) {
            throw new IllegalArgumentException("不支持的证明类型");
        }
        Integer studentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_student WHERE student_no = ? AND status = 1",
                Integer.class,
                studentNo
        );
        if (studentCount == null || studentCount == 0) {
            throw new IllegalArgumentException("学生不存在或已被禁用");
        }
        String extraData = request.getExtraData() == null ? null : request.getExtraData().trim();
        if (extraData != null && extraData.length() > 500) {
            throw new IllegalArgumentException("申请信息长度不能超过 500 个字符");
        }

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Long applyId = idSeq.incrementAndGet();
        jdbcTemplate.update(
                """
                        INSERT INTO t_certificate_apply
                        (id, student_no, certificate_type, apply_status, extra_data, file_id, created_at, updated_at)
                        VALUES (?, ?, ?, '待审核', ?, NULL, ?, ?)
                        """,
                applyId,
                studentNo,
                certificateType,
                extraData,
                now,
                now
        );

        Long taskId = idSeq.incrementAndGet();
        jdbcTemplate.update(
                """
                        INSERT INTO t_approval_task
                        (id, apply_id, node_order, approver_id, approver_name, approval_status, opinion, handled_at, created_at, updated_at)
                        VALUES (?, ?, 1, NULL, NULL, '待处理', NULL, NULL, ?, ?)
                        """,
                taskId,
                applyId,
                now,
                now
        );

        return applyId;
    }

    public List<CertificateApplyItem> list(String status, String keyword) {
        return list(status, keyword, null);
    }

    public List<CertificateApplyItem> listByStudentNo(String studentNo) {
        String normalized = normalize(studentNo);
        if (normalized.isEmpty()) return List.of();
        return list(null, null, normalized);
    }

    private List<CertificateApplyItem> list(String status, String keyword, String studentNo) {
        String s = normalize(status);
        String k = normalize(keyword).toLowerCase();
        boolean hasStatus = !s.isEmpty();
        boolean hasKeyword = !k.isEmpty();

        String sql = """
                SELECT a.id,
                       TRIM(a.student_no) AS student_no,
                       s.name AS student_name,
                       s.class_name AS class_name,
                       a.certificate_type,
                       a.apply_status,
                       a.extra_data,
                       a.file_id,
                       a.created_at,
                       a.updated_at,
                       (
                           SELECT x.approver_name
                           FROM t_approval_task x
                           WHERE x.apply_id = a.id
                           ORDER BY x.node_order DESC
                           LIMIT 1
                       ) AS last_approver_name,
                       (
                           SELECT x.approval_status
                           FROM t_approval_task x
                           WHERE x.apply_id = a.id
                           ORDER BY x.node_order DESC
                           LIMIT 1
                       ) AS last_approval_status,
                       (
                           SELECT x.opinion
                           FROM t_approval_task x
                           WHERE x.apply_id = a.id
                           ORDER BY x.node_order DESC
                           LIMIT 1
                       ) AS last_opinion,
                       (
                           SELECT x.handled_at
                           FROM t_approval_task x
                           WHERE x.apply_id = a.id
                           ORDER BY x.node_order DESC
                           LIMIT 1
                       ) AS last_handled_at
                FROM t_certificate_apply a
                LEFT JOIN t_student s ON s.student_no = TRIM(a.student_no)
                """;

        List<Object> args = new ArrayList<>();
        List<String> wheres = new ArrayList<>();
        if (hasStatus) {
            wheres.add("a.apply_status = ?");
            args.add(s);
        }
        if (hasKeyword) {
            wheres.add("(LOWER(TRIM(a.student_no)) LIKE ? OR LOWER(COALESCE(s.name, '')) LIKE ? OR LOWER(COALESCE(s.class_name, '')) LIKE ? OR LOWER(a.certificate_type) LIKE ? OR LOWER(COALESCE(a.extra_data, '')) LIKE ?)");
            String like = "%" + k + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (studentNo != null) {
            wheres.add("TRIM(a.student_no) = ?");
            args.add(studentNo);
        }
        if (!wheres.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", wheres);
        }
        sql += " ORDER BY a.created_at DESC";

        return jdbcTemplate.query(sql, args.toArray(), (rs, rowNum) -> {
            CertificateApplyItem item = new CertificateApplyItem();
            item.setId(rs.getLong("id"));
            item.setStudentNo(rs.getString("student_no"));
            String studentName = rs.getString("student_name");
            item.setStudentName(studentName);
            item.setName(studentName);
            item.setClassName(rs.getString("class_name"));
            item.setCertificateType(rs.getString("certificate_type"));
            item.setApplyStatus(rs.getString("apply_status"));
            item.setExtraData(rs.getString("extra_data"));
            long fileId = rs.getLong("file_id");
            item.setFileId(rs.wasNull() ? null : fileId);
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (createdAt != null) item.setCreatedAt(createdAt.toLocalDateTime());
            if (updatedAt != null) item.setUpdatedAt(updatedAt.toLocalDateTime());

            item.setLastApproverName(rs.getString("last_approver_name"));
            item.setLastApprovalStatus(rs.getString("last_approval_status"));
            item.setLastOpinion(rs.getString("last_opinion"));
            Timestamp lastHandledAt = rs.getTimestamp("last_handled_at");
            if (lastHandledAt != null) item.setLastHandledAt(lastHandledAt.toLocalDateTime());
            return item;
        });
    }

    public CertificateApplyDetail detail(Long applyId) {
        if (applyId == null) {
            throw new IllegalArgumentException("申请ID不能为空");
        }
        CertificateApplyItem apply = getApplyById(applyId);
        List<ApprovalTaskItem> tasks = listTasks(applyId);
        CertificateApplyDetail detail = new CertificateApplyDetail();
        detail.setApply(apply);
        detail.setTasks(tasks);
        return detail;
    }

    @Transactional
    public void decide(Long applyId, CertificateDecisionRequest request, Long approverId, String approverName) {
        if (applyId == null) {
            throw new IllegalArgumentException("申请ID不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String decision = normalize(request.getDecision()).toLowerCase();
        String opinion = normalize(request.getOpinion());
        if (opinion.isEmpty()) {
            throw new IllegalArgumentException("审批意见不能为空");
        }

        String applyStatus;
        String approvalStatus;
        if (decision.equals("approved") || decision.equals("pass") || decision.equals("通过") || decision.equals("已通过")) {
            applyStatus = "已通过";
            approvalStatus = "已通过";
        } else if (decision.equals("rejected") || decision.equals("reject") || decision.equals("驳回") || decision.equals("已驳回")) {
            applyStatus = "已驳回";
            approvalStatus = "已驳回";
        } else {
            throw new IllegalArgumentException("不支持的审批结果");
        }

        CertificateApplyItem existing = getApplyById(applyId);
        if (!"待审核".equals(normalize(existing.getApplyStatus()))) {
            throw new IllegalArgumentException("该申请已处理");
        }

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Long generatedFileId = null;

        if ("已通过".equals(applyStatus)) {
            generatedFileId = certificateFileService.generate(
                    existing,
                    approverId,
                    normalize(approverName),
                    opinion,
                    now
            );
        }

        Long taskId = jdbcTemplate.query(
                """
                        SELECT id
                        FROM t_approval_task
                        WHERE apply_id = ? AND approval_status = '待处理'
                        ORDER BY node_order ASC
                        """,
                rs -> rs.next() ? rs.getLong("id") : null,
                applyId
        );

        if (taskId != null) {
            jdbcTemplate.update(
                    """
                            UPDATE t_approval_task
                            SET approver_id = ?, approver_name = ?, approval_status = ?, opinion = ?, handled_at = ?, updated_at = ?
                            WHERE id = ?
                            """,
                    approverId,
                    approverName,
                    approvalStatus,
                    opinion,
                    now,
                    now,
                    taskId
            );
        } else {
            Integer maxOrder = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(node_order), 0) FROM t_approval_task WHERE apply_id = ?",
                    Integer.class,
                    applyId
            );
            int nextOrder = (maxOrder == null ? 0 : maxOrder) + 1;
            Long newTaskId = idSeq.incrementAndGet();
            jdbcTemplate.update(
                    """
                            INSERT INTO t_approval_task
                            (id, apply_id, node_order, approver_id, approver_name, approval_status, opinion, handled_at, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    newTaskId,
                    applyId,
                    nextOrder,
                    approverId,
                    approverName,
                    approvalStatus,
                    opinion,
                    now,
                    now,
                    now
            );
        }

        jdbcTemplate.update(
                """
                        UPDATE t_certificate_apply
                        SET apply_status = ?, file_id = ?, updated_at = ?
                        WHERE id = ?
                        """,
                applyStatus,
                generatedFileId,
                now,
                applyId
        );
    }

    @Transactional
    public void delete(Long applyId) {
        if (applyId == null) return;
        CertificateApplyItem existing = getApplyById(applyId);
        jdbcTemplate.update("DELETE FROM t_approval_task WHERE apply_id = ?", applyId);
        jdbcTemplate.update("DELETE FROM t_certificate_apply WHERE id = ?", applyId);
        certificateFileService.delete(existing.getFileId());
    }

    private CertificateApplyItem getApplyById(Long applyId) {
        String sql = """
                SELECT a.id,
                       TRIM(a.student_no) AS student_no,
                       s.name AS student_name,
                       s.class_name AS class_name,
                       a.certificate_type,
                       a.apply_status,
                       a.extra_data,
                       a.file_id,
                       a.created_at,
                       a.updated_at,
                       (
                           SELECT x.approver_name
                           FROM t_approval_task x
                           WHERE x.apply_id = a.id
                           ORDER BY x.node_order DESC
                           LIMIT 1
                       ) AS last_approver_name,
                       (
                           SELECT x.approval_status
                           FROM t_approval_task x
                           WHERE x.apply_id = a.id
                           ORDER BY x.node_order DESC
                           LIMIT 1
                       ) AS last_approval_status,
                       (
                           SELECT x.opinion
                           FROM t_approval_task x
                           WHERE x.apply_id = a.id
                           ORDER BY x.node_order DESC
                           LIMIT 1
                       ) AS last_opinion,
                       (
                           SELECT x.handled_at
                           FROM t_approval_task x
                           WHERE x.apply_id = a.id
                           ORDER BY x.node_order DESC
                           LIMIT 1
                       ) AS last_handled_at
                FROM t_certificate_apply a
                LEFT JOIN t_student s ON s.student_no = TRIM(a.student_no)
                WHERE a.id = ?
                """;
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                throw new IllegalArgumentException("申请不存在");
            }
            CertificateApplyItem item = new CertificateApplyItem();
            item.setId(rs.getLong("id"));
            item.setStudentNo(rs.getString("student_no"));
            String studentName = rs.getString("student_name");
            item.setStudentName(studentName);
            item.setName(studentName);
            item.setClassName(rs.getString("class_name"));
            item.setCertificateType(rs.getString("certificate_type"));
            item.setApplyStatus(rs.getString("apply_status"));
            item.setExtraData(rs.getString("extra_data"));
            long fileId = rs.getLong("file_id");
            item.setFileId(rs.wasNull() ? null : fileId);
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (createdAt != null) item.setCreatedAt(createdAt.toLocalDateTime());
            if (updatedAt != null) item.setUpdatedAt(updatedAt.toLocalDateTime());
            item.setLastApproverName(rs.getString("last_approver_name"));
            item.setLastApprovalStatus(rs.getString("last_approval_status"));
            item.setLastOpinion(rs.getString("last_opinion"));
            Timestamp lastHandledAt = rs.getTimestamp("last_handled_at");
            if (lastHandledAt != null) item.setLastHandledAt(lastHandledAt.toLocalDateTime());
            return item;
        }, applyId);
    }

    private List<ApprovalTaskItem> listTasks(Long applyId) {
        String sql = """
                SELECT id, apply_id, node_order, approver_id, approver_name, approval_status, opinion, handled_at, created_at, updated_at
                FROM t_approval_task
                WHERE apply_id = ?
                ORDER BY node_order ASC, created_at ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            ApprovalTaskItem item = new ApprovalTaskItem();
            item.setId(rs.getLong("id"));
            item.setApplyId(rs.getLong("apply_id"));
            item.setNodeOrder(rs.getInt("node_order"));
            long approverId = rs.getLong("approver_id");
            item.setApproverId(rs.wasNull() ? null : approverId);
            item.setApproverName(rs.getString("approver_name"));
            item.setApprovalStatus(rs.getString("approval_status"));
            item.setOpinion(rs.getString("opinion"));
            Timestamp handledAt = rs.getTimestamp("handled_at");
            if (handledAt != null) item.setHandledAt(handledAt.toLocalDateTime());
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (createdAt != null) item.setCreatedAt(createdAt.toLocalDateTime());
            if (updatedAt != null) item.setUpdatedAt(updatedAt.toLocalDateTime());
            return item;
        }, applyId);
    }

    private String normalize(String v) {
        return v == null ? "" : v.trim();
    }
}
