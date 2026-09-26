-- 学生综合服务平台 MySQL 8 初始化脚本
-- 该脚本会重建业务表，请勿在需要保留数据的数据库中直接执行。

CREATE DATABASE IF NOT EXISTS student_platform
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE student_platform;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS t_notification_receipt;
DROP TABLE IF EXISTS t_notification;
DROP TABLE IF EXISTS t_approval_task;
DROP TABLE IF EXISTS t_certificate_apply;
DROP TABLE IF EXISTS t_student_political_info;
DROP TABLE IF EXISTS t_process_stage;
DROP TABLE IF EXISTS t_training_plan;
DROP TABLE IF EXISTS t_warning_record;
DROP TABLE IF EXISTS t_policy_doc;
DROP TABLE IF EXISTS t_operation_log;
DROP TABLE IF EXISTS t_file;
DROP TABLE IF EXISTS t_student;
DROP TABLE IF EXISTS t_user;
DROP TABLE IF EXISTS t_role;

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
    political_status VARCHAR(50) DEFAULT '未知',
    party_stage_id INT DEFAULT 0,
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

CREATE TABLE t_student_political_info (
    id BIGINT PRIMARY KEY,
    student_no VARCHAR(50) NOT NULL,
    join_league_date DATE,
    league_member_no VARCHAR(100),
    join_party_date DATE,
    party_branch_name VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_political_student_no UNIQUE (student_no)
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
    content TEXT,
    keywords VARCHAR(255),
    official_url VARCHAR(500),
    file_id BIGINT,
    doc_status VARCHAR(50) DEFAULT '已发布',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

CREATE TABLE t_process_stage (
    stage_id INT PRIMARY KEY,
    stage_name VARCHAR(100) NOT NULL,
    order_num INT NOT NULL,
    duration INT,
    description VARCHAR(255)
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

INSERT INTO t_process_stage (stage_id, stage_name, order_num, duration, description)
VALUES
    (0, '未申请', 0, NULL, '未提交入党申请书'),
    (1, '入党申请人', 1, NULL, '提交入党申请书后的初始阶段'),
    (2, '入党积极分子', 2, NULL, '经推荐和培养后确定为入党积极分子'),
    (3, '发展对象', 3, NULL, '经过培养考察后确定为发展对象'),
    (4, '预备党员', 4, NULL, '支部大会通过并经上级党组织批准后成为预备党员'),
    (5, '正式党员', 5, NULL, '预备期满并转正后成为正式党员');

SET FOREIGN_KEY_CHECKS = 1;

-- 管理员账号不在 SQL 中保存明文密码。
-- 首次启动前设置 ADMIN_BOOTSTRAP_PASSWORD，由应用使用 BCrypt 创建管理员。
