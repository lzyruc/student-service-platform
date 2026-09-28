-- 将已有的 t_policy_doc 升级为知识库管理表。
-- 适用数据库：MySQL 8.0，数据库名：student_platform。
-- 本脚本只执行一次；执行前请先备份已有数据。

USE student_platform;
SET NAMES utf8mb4;

-- 兼容旧版中文状态值。
UPDATE t_policy_doc
SET doc_status = CASE doc_status
    WHEN '草稿' THEN 'DRAFT'
    WHEN '已发布' THEN 'PUBLISHED'
    WHEN '已归档' THEN 'ARCHIVED'
    ELSE COALESCE(NULLIF(TRIM(doc_status), ''), 'DRAFT')
END;

ALTER TABLE t_policy_doc
    ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT 'OTHER' AFTER title,
    ADD COLUMN audience VARCHAR(50) NOT NULL DEFAULT 'ALL' AFTER category,
    ADD COLUMN version VARCHAR(50) NOT NULL DEFAULT 'v1.0' AFTER audience,
    ADD COLUMN effective_date DATE NULL AFTER version,
    ADD COLUMN expiry_date DATE NULL AFTER effective_date,
    ADD COLUMN tags JSON NULL AFTER expiry_date,
    MODIFY COLUMN content LONGTEXT NULL,
    MODIFY COLUMN keywords VARCHAR(500) NULL,
    ADD COLUMN remark VARCHAR(1000) NULL AFTER official_url,
    MODIFY COLUMN file_id BIGINT NOT NULL,
    ADD COLUMN created_by BIGINT NULL AFTER file_id,
    MODIFY COLUMN doc_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN ingest_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' AFTER doc_status,
    ADD COLUMN ingest_error VARCHAR(1000) NULL AFTER ingest_status,
    ADD COLUMN chunk_count INT NOT NULL DEFAULT 0 AFTER ingest_error,
    ADD COLUMN content_hash CHAR(64) NULL AFTER chunk_count,
    ADD COLUMN last_ingested_at TIMESTAMP NULL AFTER content_hash,
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    ADD CONSTRAINT uk_policy_doc_file UNIQUE (file_id),
    ADD CONSTRAINT fk_policy_doc_file FOREIGN KEY (file_id) REFERENCES t_file (id),
    ADD CONSTRAINT fk_policy_doc_creator FOREIGN KEY (created_by) REFERENCES t_user (id),
    ADD CONSTRAINT ck_policy_doc_chunk_count CHECK (chunk_count >= 0),
    ADD INDEX idx_policy_doc_category_audience (category, audience),
    ADD INDEX idx_policy_doc_status (doc_status, ingest_status),
    ADD INDEX idx_policy_doc_effective_date (effective_date),
    ADD INDEX idx_policy_doc_created_by (created_by);

-- 状态约定：
-- doc_status: DRAFT / PUBLISHED / ARCHIVED
-- ingest_status: PENDING / PROCESSING / READY / FAILED
-- audience: ALL / UNDERGRADUATE / POSTGRADUATE

-- 执行后核对字段与索引。
SELECT
    column_name,
    column_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 't_policy_doc'
ORDER BY ordinal_position;

SHOW INDEX FROM t_policy_doc;
