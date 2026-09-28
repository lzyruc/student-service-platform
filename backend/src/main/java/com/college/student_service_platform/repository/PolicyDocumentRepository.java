package com.college.student_service_platform.repository;

import com.college.student_service_platform.entity.PolicyDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class PolicyDocumentRepository {
    private static final String DETAIL_SELECT = """
            SELECT p.id, p.title, p.category, p.audience, p.version,
                   p.effective_date, p.expiry_date, p.tags, p.content,
                   p.keywords, p.official_url, p.remark, p.file_id, p.created_by,
                   p.doc_status, p.ingest_status, p.ingest_error, p.chunk_count,
                   p.content_hash, p.last_ingested_at, p.created_at, p.updated_at,
                   f.original_name AS file_name, f.file_type, f.file_size,
                   u.username AS creator_name
            FROM t_policy_doc p
            JOIN t_file f ON f.id = p.file_id
            LEFT JOIN t_user u ON u.id = p.created_by
            """;

    private final JdbcTemplate jdbcTemplate;

    public PolicyDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> findAdminIdByUsername(String username) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM t_user WHERE username = ? AND role_code = 'admin' AND status = 1",
                (rs, rowNum) -> rs.getLong("id"),
                username
        );
        return ids.stream().findFirst();
    }

    public Optional<PolicyFileInfo> findFileById(Long fileId) {
        List<PolicyFileInfo> files = jdbcTemplate.query("""
                SELECT id, original_name, file_type, business_type
                FROM t_file
                WHERE id = ?
                """, (rs, rowNum) -> new PolicyFileInfo(
                rs.getLong("id"),
                rs.getString("original_name"),
                rs.getString("file_type"),
                rs.getString("business_type")
        ), fileId);
        return files.stream().findFirst();
    }

    public boolean isFileLinked(Long fileId, Long excludedDocumentId) {
        String sql = "SELECT COUNT(1) FROM t_policy_doc WHERE file_id = ?";
        List<Object> args = new ArrayList<>();
        args.add(fileId);
        if (excludedDocumentId != null) {
            sql += " AND id <> ?";
            args.add(excludedDocumentId);
        }
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args.toArray());
        return count != null && count > 0;
    }

    public void insert(PolicyDocument document) {
        jdbcTemplate.update("""
                INSERT INTO t_policy_doc (
                    id, title, category, audience, version, effective_date, expiry_date,
                    tags, content, keywords, official_url, remark, file_id, created_by,
                    doc_status, ingest_status, ingest_error, chunk_count, content_hash,
                    last_ingested_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                document.getId(),
                document.getTitle(),
                document.getCategory(),
                document.getAudience(),
                document.getVersion(),
                document.getEffectiveDate(),
                document.getExpiryDate(),
                document.getTagsJson(),
                document.getContent(),
                document.getKeywords(),
                document.getOfficialUrl(),
                document.getRemark(),
                document.getFileId(),
                document.getCreatedBy(),
                document.getDocStatus(),
                document.getIngestStatus(),
                document.getIngestError(),
                document.getChunkCount(),
                document.getContentHash(),
                document.getLastIngestedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    public int update(PolicyDocument document) {
        return jdbcTemplate.update("""
                UPDATE t_policy_doc
                SET title = ?, category = ?, audience = ?, version = ?,
                    effective_date = ?, expiry_date = ?, tags = ?, content = ?,
                    keywords = ?, official_url = ?, remark = ?, file_id = ?,
                    doc_status = 'DRAFT', ingest_status = 'PENDING', ingest_error = NULL,
                    chunk_count = 0, content_hash = NULL, last_ingested_at = NULL,
                    updated_at = ?
                WHERE id = ?
                """,
                document.getTitle(),
                document.getCategory(),
                document.getAudience(),
                document.getVersion(),
                document.getEffectiveDate(),
                document.getExpiryDate(),
                document.getTagsJson(),
                document.getContent(),
                document.getKeywords(),
                document.getOfficialUrl(),
                document.getRemark(),
                document.getFileId(),
                document.getUpdatedAt(),
                document.getId()
        );
    }

    public Optional<PolicyDocumentRow> findById(Long id) {
        List<PolicyDocumentRow> rows = jdbcTemplate.query(
                DETAIL_SELECT + " WHERE p.id = ?",
                this::mapRow,
                id
        );
        return rows.stream().findFirst();
    }

    public List<PolicyDocumentRow> findPage(
            String keyword,
            String category,
            String audience,
            String docStatus,
            String ingestStatus,
            int limit,
            int offset
    ) {
        QueryParts query = buildFilters(keyword, category, audience, docStatus, ingestStatus);
        query.args().add(limit);
        query.args().add(offset);
        return jdbcTemplate.query(
                DETAIL_SELECT + query.whereClause() + " ORDER BY p.created_at DESC, p.id DESC LIMIT ? OFFSET ?",
                this::mapRow,
                query.args().toArray()
        );
    }

    public long count(String keyword, String category, String audience, String docStatus, String ingestStatus) {
        QueryParts query = buildFilters(keyword, category, audience, docStatus, ingestStatus);
        Long total = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(1)
                        FROM t_policy_doc p
                        JOIN t_file f ON f.id = p.file_id
                        """ + query.whereClause(),
                Long.class,
                query.args().toArray()
        );
        return total == null ? 0L : total;
    }

    public int publish(Long id) {
        return jdbcTemplate.update("""
                UPDATE t_policy_doc
                SET doc_status = 'PUBLISHED', updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, id);
    }

    public int delete(Long id) {
        return jdbcTemplate.update("DELETE FROM t_policy_doc WHERE id = ?", id);
    }

    private QueryParts buildFilters(
            String keyword,
            String category,
            String audience,
            String docStatus,
            String ingestStatus
    ) {
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            conditions.add("""
                    (LOWER(p.title) LIKE ?
                     OR LOWER(COALESCE(p.keywords, '')) LIKE ?
                     OR LOWER(f.original_name) LIKE ?)
                    """);
            String like = "%" + keyword.trim().toLowerCase() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        addExactFilter(conditions, args, "p.category", category);
        addExactFilter(conditions, args, "p.audience", audience);
        addExactFilter(conditions, args, "p.doc_status", docStatus);
        addExactFilter(conditions, args, "p.ingest_status", ingestStatus);

        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return new QueryParts(where, args);
    }

    private void addExactFilter(List<String> conditions, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            conditions.add(column + " = ?");
            args.add(value.trim());
        }
    }

    private PolicyDocumentRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        PolicyDocument document = new PolicyDocument();
        document.setId(rs.getLong("id"));
        document.setTitle(rs.getString("title"));
        document.setCategory(rs.getString("category"));
        document.setAudience(rs.getString("audience"));
        document.setVersion(rs.getString("version"));
        Date effectiveDate = rs.getDate("effective_date");
        Date expiryDate = rs.getDate("expiry_date");
        document.setEffectiveDate(effectiveDate == null ? null : effectiveDate.toLocalDate());
        document.setExpiryDate(expiryDate == null ? null : expiryDate.toLocalDate());
        document.setTagsJson(rs.getString("tags"));
        document.setContent(rs.getString("content"));
        document.setKeywords(rs.getString("keywords"));
        document.setOfficialUrl(rs.getString("official_url"));
        document.setRemark(rs.getString("remark"));
        document.setFileId(rs.getLong("file_id"));
        long createdBy = rs.getLong("created_by");
        document.setCreatedBy(rs.wasNull() ? null : createdBy);
        document.setDocStatus(rs.getString("doc_status"));
        document.setIngestStatus(rs.getString("ingest_status"));
        document.setIngestError(rs.getString("ingest_error"));
        document.setChunkCount(rs.getInt("chunk_count"));
        document.setContentHash(rs.getString("content_hash"));
        document.setLastIngestedAt(toLocalDateTime(rs.getTimestamp("last_ingested_at")));
        document.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        document.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));

        long fileSizeValue = rs.getLong("file_size");
        Long fileSize = rs.wasNull() ? null : fileSizeValue;
        return new PolicyDocumentRow(
                document,
                rs.getString("file_name"),
                rs.getString("file_type"),
                fileSize,
                rs.getString("creator_name")
        );
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record PolicyFileInfo(Long id, String originalName, String fileType, String businessType) {
    }

    public record PolicyDocumentRow(
            PolicyDocument document,
            String fileName,
            String fileType,
            Long fileSize,
            String creatorName
    ) {
    }

    private record QueryParts(String whereClause, List<Object> args) {
    }
}
