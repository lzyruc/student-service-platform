-- 学生综合服务平台 MySQL 8 初始化脚本
-- 该脚本会删除并重建 student_platform 数据库，原有数据会全部清空。

DROP DATABASE IF EXISTS student_platform;

CREATE DATABASE student_platform
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE student_platform;
SET NAMES utf8mb4;

CREATE TABLE t_role (
    id BIGINT PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_code UNIQUE (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE t_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255),
    role_code VARCHAR(50) NOT NULL,
    student_no VARCHAR(50),
    wechat_openid VARCHAR(100),
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_student_no UNIQUE (student_no),
    CONSTRAINT uk_user_username_role UNIQUE (username, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE t_student (
    id BIGINT PRIMARY KEY,
    student_no VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    id_card_no VARCHAR(18),
    gender VARCHAR(20) DEFAULT '未知',
    ethnicity VARCHAR(50),
    class_name VARCHAR(100) NOT NULL,
    major VARCHAR(100) NOT NULL,
    grade VARCHAR(50) NOT NULL,
    education_level VARCHAR(20) DEFAULT '本科',
    contact VARCHAR(100),
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_no UNIQUE (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS agent_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL DEFAULT '新会话',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_agent_conversation_student FOREIGN KEY (student_id) REFERENCES t_student(id) ON DELETE CASCADE,
    INDEX idx_agent_conversation_owner_updated (student_id, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_conversation_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_agent_message_conversation FOREIGN KEY (conversation_id) REFERENCES agent_conversation(id) ON DELETE CASCADE,
    CONSTRAINT ck_agent_message_role CHECK (role IN ('USER', 'ASSISTANT')),
    INDEX idx_agent_message_conversation_order (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE t_file (
    id BIGINT PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_type VARCHAR(100),
    file_size BIGINT,
    uploader_id BIGINT,
    business_type VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_policy_doc_file UNIQUE (file_id),
    CONSTRAINT fk_policy_doc_file FOREIGN KEY (file_id) REFERENCES t_file (id),
    CONSTRAINT fk_policy_doc_creator FOREIGN KEY (created_by) REFERENCES t_user (id),
    CONSTRAINT ck_policy_doc_chunk_count CHECK (chunk_count >= 0),
    INDEX idx_policy_doc_category_audience (category, audience),
    INDEX idx_policy_doc_status (doc_status, ingest_status),
    INDEX idx_policy_doc_effective_date (effective_date),
    INDEX idx_policy_doc_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE t_warning_record (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    student_no VARCHAR(50) NOT NULL,
    transcript_file_id BIGINT,
    training_plan_id BIGINT,
    warning_level VARCHAR(50) DEFAULT '未知',
    total_earned_credits DECIMAL(10, 2),
    course_count INT,
    core_course_count INT,
    failed_course_count INT,
    missing_course_count INT,
    parsed_courses_json TEXT,
    core_courses_json TEXT,
    failed_courses_json TEXT,
    missing_courses_json TEXT,
    suggestions_json TEXT,
    analysis_status INT DEFAULT 1,
    error_message VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_warning_student_no (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE t_training_plan (
    id BIGINT PRIMARY KEY,
    major VARCHAR(100) NOT NULL,
    grade VARCHAR(50) NOT NULL,
    version VARCHAR(50) NOT NULL,
    remark VARCHAR(500),
    json_content TEXT NOT NULL,
    course_count INT,
    total_credits DECIMAL(10, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE t_operation_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(100),
    operation VARCHAR(255),
    request_method VARCHAR(20),
    request_url VARCHAR(500),
    request_ip VARCHAR(100),
    operation_status INT,
    error_message VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE t_certificate_apply (
    id BIGINT PRIMARY KEY,
    student_no VARCHAR(50) NOT NULL,
    certificate_type VARCHAR(100) NOT NULL,
    apply_status VARCHAR(50) DEFAULT '待审核',
    extra_data VARCHAR(500),
    file_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE t_approval_task (
    id BIGINT PRIMARY KEY,
    apply_id BIGINT NOT NULL,
    node_order INT NOT NULL,
    approver_id BIGINT,
    approver_name VARCHAR(100),
    approval_status VARCHAR(50) DEFAULT '待处理',
    opinion VARCHAR(500),
    handled_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE t_notification (
    id BIGINT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    tags VARCHAR(255),
    is_urgent BOOLEAN DEFAULT FALSE,
    file_id BIGINT,
    publisher_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE t_notification_receipt (
    id BIGINT PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    student_no VARCHAR(50) NOT NULL,
    is_confirmed BOOLEAN DEFAULT FALSE,
    confirmed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_receipt UNIQUE (notification_id, student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO t_role (id, role_code, role_name, description)
VALUES
    (1, 'student', '学生', '微信小程序学生端用户'),
    (2, 'admin', '管理员', 'Web 后台管理员');

-- 管理员账号不在 SQL 中保存明文密码。
-- 首次启动前设置 ADMIN_BOOTSTRAP_PASSWORD，由应用使用 BCrypt 创建管理员。
