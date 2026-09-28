-- 重建政策文档表，用于知识库管理闭环。
-- 适用数据库：MySQL 8.0，数据库名：student_platform。
-- 本脚本只删除并重建 t_policy_doc，不影响其他业务表。

USE student_platform;
SET NAMES utf8mb4;

DROP TABLE IF EXISTS t_policy_doc;

CREATE TABLE t_policy_doc (
    id BIGINT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT 'OTHER',
    audience VARCHAR(50) NOT NULL DEFAULT 'ALL',
    version VARCHAR(50) NOT NULL DEFAULT 'v1.0',
    effective_date DATE,
    expiry_date DATE,
    tags JSON,
    content LONGTEXT,
    keywords VARCHAR(500),
    official_url VARCHAR(500),
    remark VARCHAR(1000),
    file_id BIGINT NOT NULL,
    created_by BIGINT,
    doc_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    ingest_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ingest_error VARCHAR(1000),
    chunk_count INT NOT NULL DEFAULT 0,
    content_hash CHAR(64),
    last_ingested_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_policy_doc_file UNIQUE (file_id),
    CONSTRAINT fk_policy_doc_file FOREIGN KEY (file_id) REFERENCES t_file (id),
    CONSTRAINT fk_policy_doc_creator FOREIGN KEY (created_by) REFERENCES t_user (id),
    CONSTRAINT ck_policy_doc_chunk_count CHECK (chunk_count >= 0),
    INDEX idx_policy_doc_category_audience (category, audience),
    INDEX idx_policy_doc_status (doc_status, ingest_status),
    INDEX idx_policy_doc_effective_date (effective_date),
    INDEX idx_policy_doc_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
