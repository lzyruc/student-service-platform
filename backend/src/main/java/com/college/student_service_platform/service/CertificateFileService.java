package com.college.student_service_platform.service;

import com.college.student_service_platform.dto.CertificateApplyItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CertificateFileService {
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Path uploadPath;
    private final AtomicLong fileIdSeq = new AtomicLong(System.currentTimeMillis() * 1000);

    public CertificateFileService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${file.upload-dir:uploads}") String uploadDir
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public Long generate(
            CertificateApplyItem apply,
            Long approverId,
            String approverName,
            String opinion,
            Timestamp generatedAt
    ) {
        Map<String, Object> student = loadStudent(apply.getStudentNo());
        Map<String, Object> extra = parseExtraData(apply.getExtraData());
        String storedName = UUID.randomUUID() + ".docx";
        String originalName = safeFilePart(apply.getCertificateType()) + "_"
                + safeFilePart(apply.getStudentNo()) + "_" + apply.getId() + ".docx";
        Path targetPath = uploadPath.resolve(storedName).normalize();
        if (!targetPath.startsWith(uploadPath)) {
            throw new IllegalStateException("生成文件路径不合法");
        }

        try {
            Files.createDirectories(uploadPath);
            byte[] content = buildDocument(apply, student, extra, approverName, opinion, generatedAt);
            Files.write(targetPath, content, StandardOpenOption.CREATE_NEW);

            Long fileId = fileIdSeq.incrementAndGet();
            jdbcTemplate.update(
                    """
                            INSERT INTO t_file
                            (id, original_name, stored_name, file_path, file_type, file_size, uploader_id, business_type, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, 'certificate', ?)
                            """,
                    fileId,
                    originalName,
                    storedName,
                    targetPath.toString(),
                    DOCX_CONTENT_TYPE,
                    (long) content.length,
                    approverId,
                    generatedAt
            );
            return fileId;
        } catch (Exception exception) {
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {
                // 保留原始异常。
            }
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("生成证明文件失败", exception);
        }
    }

    public void delete(Long fileId) {
        if (fileId == null) return;
        String storedName = jdbcTemplate.query(
                "SELECT stored_name FROM t_file WHERE id = ?",
                rs -> rs.next() ? rs.getString("stored_name") : null,
                fileId
        );
        jdbcTemplate.update("DELETE FROM t_file WHERE id = ?", fileId);
        if (storedName == null || storedName.isBlank()) return;

        Path path = uploadPath.resolve(storedName).normalize();
        if (!path.startsWith(uploadPath)) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("删除证明文件失败", exception);
        }
    }

    private Map<String, Object> loadStudent(String studentNo) {
        return jdbcTemplate.query(
                """
                        SELECT student_no, name, id_card_no, class_name, major, grade, education_level, contact
                        FROM t_student
                        WHERE student_no = ? AND status = 1
                        """,
                rs -> {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("学生不存在或已被禁用");
                    }
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("studentNo", rs.getString("student_no"));
                    data.put("name", rs.getString("name"));
                    data.put("idCardNo", rs.getString("id_card_no"));
                    data.put("className", rs.getString("class_name"));
                    data.put("major", rs.getString("major"));
                    data.put("grade", rs.getString("grade"));
                    data.put("educationLevel", rs.getString("education_level"));
                    data.put("contact", rs.getString("contact"));
                    return data;
                },
                studentNo
        );
    }

    private Map<String, Object> parseExtraData(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of("reason", raw.trim());
        }
    }

    private byte[] buildDocument(
            CertificateApplyItem apply,
            Map<String, Object> student,
            Map<String, Object> extra,
            String approverName,
            String opinion,
            Timestamp generatedAt
    ) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            setChineseFont(titleRun);
            titleRun.setBold(true);
            titleRun.setFontSize(20);
            titleRun.setText(text(apply.getCertificateType(), "电子证明"));

            addLine(document, "姓名", student.get("name"));
            addLine(document, "学号", student.get("studentNo"));
            addLine(document, "班级", student.get("className"));
            addLine(document, "专业", student.get("major"));
            addLine(document, "年级", student.get("grade"));
            addLine(document, "培养层次", student.get("educationLevel"));
            addLine(document, "申请事由", extra.get("reason"));

            String startAt = text(extra.get("startAt"), "");
            String endAt = text(extra.get("endAt"), "");
            if (!startAt.isBlank() || !endAt.isBlank()) {
                addLine(document, "时间范围", startAt + " 至 " + endAt);
            }

            addLine(document, "审批结果", "已通过");
            addLine(document, "审批意见", opinion);
            addLine(document, "审批人", approverName);
            addLine(document, "出具时间", generatedAt.toLocalDateTime().format(DATE_TIME_FORMATTER));

            XWPFParagraph footer = document.createParagraph();
            footer.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun footerRun = footer.createRun();
            setChineseFont(footerRun);
            footerRun.setFontSize(10);
            footerRun.setText("本文件由学生综合服务平台生成");

            document.write(output);
            return output.toByteArray();
        }
    }

    private void addLine(XWPFDocument document, String label, Object value) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        setChineseFont(run);
        run.setFontSize(12);
        run.setText(label + "：" + text(value, "—"));
    }

    private void setChineseFont(XWPFRun run) {
        run.setFontFamily("宋体");
        run.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String safeFilePart(String value) {
        String safe = text(value, "证明").replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return safe.length() > 40 ? safe.substring(0, 40) : safe;
    }
}
