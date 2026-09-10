-- =====================================================
-- 学院学生综合服务与党团管理平台
-- 数据库初始化脚本
-- 数据库：Kingbase
-- 维护人：开发者 A
-- =====================================================

-- =====================================================
-- 开发阶段允许重复执行，先删除旧表
-- 注意：有依赖关系的表要先删
-- =====================================================
DROP TABLE IF EXISTS t_notification_receipt;
DROP TABLE IF EXISTS t_notification;
DROP TABLE IF EXISTS t_approval_task;
DROP TABLE IF EXISTS t_certificate_apply;
DROP TABLE IF EXISTS t_student_political_info;
DROP TABLE IF EXISTS t_student_stage;
DROP TABLE IF EXISTS t_process_stage;
DROP TABLE IF EXISTS t_course;
DROP TABLE IF EXISTS t_policy_doc;
DROP TABLE IF EXISTS t_operation_log;
DROP TABLE IF EXISTS t_file;
DROP TABLE IF EXISTS t_student;
DROP TABLE IF EXISTS t_user;
DROP TABLE IF EXISTS t_role;

-- =====================================================
-- 1. 角色表
-- 说明：当前系统只区分学生和管理员
-- =====================================================
CREATE TABLE t_role (
                        id BIGINT PRIMARY KEY,
                        role_code VARCHAR(50) NOT NULL UNIQUE,
                        role_name VARCHAR(100) NOT NULL,
                        description VARCHAR(255),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_role IS '角色表';
COMMENT ON COLUMN t_role.id IS '角色ID';
COMMENT ON COLUMN t_role.role_code IS '角色编码：student/admin';
COMMENT ON COLUMN t_role.role_name IS '角色名称';
COMMENT ON COLUMN t_role.description IS '角色描述';
COMMENT ON COLUMN t_role.created_at IS '创建时间';
COMMENT ON COLUMN t_role.updated_at IS '更新时间';

-- =====================================================
-- 2. 用户表
-- 说明：
-- t_user 只负责登录账号和身份识别
-- 学生详细信息放到 t_student
-- 管理员账号也存这里
-- 登录时建议支持 username 或 student_no 登录：
-- WHERE username = ? OR student_no = ?
-- =====================================================
CREATE TABLE t_user (
                        id BIGINT PRIMARY KEY,
                        username VARCHAR(100) NOT NULL,
                        password VARCHAR(255),
                        role_code VARCHAR(50) NOT NULL,
                        student_no VARCHAR(50),
                        wechat_openid VARCHAR(100),
                        status INTEGER DEFAULT 1,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_user IS '用户表，负责登录账号和身份识别';
COMMENT ON COLUMN t_user.id IS '用户ID';
COMMENT ON COLUMN t_user.username IS '登录用户名。学生可使用姓名，管理员可使用账号名；登录逻辑应同时支持 student_no';
COMMENT ON COLUMN t_user.password IS '登录密码，后续应加密存储';
COMMENT ON COLUMN t_user.role_code IS '角色编码：student/admin';
COMMENT ON COLUMN t_user.student_no IS '学号，学生用户使用，用于关联 t_student.student_no';
COMMENT ON COLUMN t_user.wechat_openid IS '微信小程序openid';
COMMENT ON COLUMN t_user.status IS '账号状态：1正常，0禁用';
COMMENT ON COLUMN t_user.created_at IS '创建时间';
COMMENT ON COLUMN t_user.updated_at IS '更新时间';

CREATE UNIQUE INDEX uk_user_student_no ON t_user(student_no);
CREATE UNIQUE INDEX uk_user_username_role ON t_user(username, role_code);

-- =====================================================
-- 3. 学生基础信息表
-- 说明：
-- 对应学生信息管理页面的手动录入字段
-- 包含学号、姓名、性别、民族、政治面貌、班级、专业、年级、联系方式
-- =====================================================
CREATE TABLE t_student (
                           id BIGINT PRIMARY KEY,
                           student_no VARCHAR(50) NOT NULL UNIQUE,
                           name VARCHAR(100) NOT NULL,
                           id_card_no VARCHAR(18),
                           gender VARCHAR(20) DEFAULT '未知',
                           ethnicity VARCHAR(50),
                           political_status VARCHAR(50) DEFAULT '未知',
                           party_stage_id INTEGER DEFAULT 0,
                           class_name VARCHAR(100) NOT NULL,
                           major VARCHAR(100) NOT NULL,
                           grade VARCHAR(50) NOT NULL,
                           education_level VARCHAR(20) DEFAULT '本科',
                           contact VARCHAR(100),
                           status INTEGER DEFAULT 1,
                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_student IS '学生基础信息表';
COMMENT ON COLUMN t_student.id IS '学生记录ID';
COMMENT ON COLUMN t_student.student_no IS '学号';
COMMENT ON COLUMN t_student.name IS '姓名';
COMMENT ON COLUMN t_student.id_card_no IS '身份证号';
COMMENT ON COLUMN t_student.gender IS '性别：男/女/未知';
COMMENT ON COLUMN t_student.ethnicity IS '民族';
COMMENT ON COLUMN t_student.political_status IS '政治面貌';
COMMENT ON COLUMN t_student.party_stage_id IS '党团流程阶段：0未申请，1入党申请人，2入党积极分子，3发展对象，4预备党员，5正式党员';
COMMENT ON COLUMN t_student.class_name IS '班级';
COMMENT ON COLUMN t_student.major IS '专业';
COMMENT ON COLUMN t_student.grade IS '年级';
COMMENT ON COLUMN t_student.education_level IS '培养层次：本科/硕士/博士';
COMMENT ON COLUMN t_student.contact IS '联系方式，手机号或邮箱';
COMMENT ON COLUMN t_student.status IS '学生状态：1正常，0无效';
COMMENT ON COLUMN t_student.created_at IS '创建时间';
COMMENT ON COLUMN t_student.updated_at IS '更新时间';

-- =====================================================
-- 3.1 学生党团信息表
-- 说明：
-- 存储电子证明生成所需的党团专项信息
-- 一名学生对应一条党团信息记录
-- =====================================================
CREATE TABLE t_student_political_info (
                                          id BIGINT PRIMARY KEY,
                                          student_no VARCHAR(50) NOT NULL UNIQUE,
                                          join_league_date DATE,
                                          league_member_no VARCHAR(100),
                                          join_party_date DATE,
                                          party_branch_name VARCHAR(200),
                                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_student_political_info IS '学生党团专项信息表';
COMMENT ON COLUMN t_student_political_info.id IS '记录ID';
COMMENT ON COLUMN t_student_political_info.student_no IS '学号，对应 t_student.student_no';
COMMENT ON COLUMN t_student_political_info.join_league_date IS '入团时间';
COMMENT ON COLUMN t_student_political_info.league_member_no IS '团员编号';
COMMENT ON COLUMN t_student_political_info.join_party_date IS '入党时间';
COMMENT ON COLUMN t_student_political_info.party_branch_name IS '所属党支部';
COMMENT ON COLUMN t_student_political_info.created_at IS '创建时间';
COMMENT ON COLUMN t_student_political_info.updated_at IS '更新时间';

-- =====================================================
-- 4. 文件表
-- 说明：
-- 统一管理上传文件、模板、通知附件、成绩单、证明 PDF
-- =====================================================
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
);

COMMENT ON TABLE t_file IS '文件表，统一管理上传文件、模板、通知附件、成绩单、证明PDF等';
COMMENT ON COLUMN t_file.id IS '文件ID';
COMMENT ON COLUMN t_file.original_name IS '原始文件名';
COMMENT ON COLUMN t_file.stored_name IS '服务器保存文件名';
COMMENT ON COLUMN t_file.file_path IS '文件存储路径';
COMMENT ON COLUMN t_file.file_type IS '文件类型';
COMMENT ON COLUMN t_file.file_size IS '文件大小，单位字节';
COMMENT ON COLUMN t_file.uploader_id IS '上传人ID，对应 t_user.id';
COMMENT ON COLUMN t_file.business_type IS '业务类型：policy/template/notice/certificate/transcript/other';
COMMENT ON COLUMN t_file.created_at IS '创建时间';

-- =====================================================
-- 5. 政策知识库文档表
-- 说明：
-- 存储政策文件、办事说明、模板说明等知识库内容
-- 用于 AI 问答和 Web 管理端知识库维护
-- =====================================================
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
);

COMMENT ON TABLE t_policy_doc IS '政策知识库文档表';
COMMENT ON COLUMN t_policy_doc.id IS '政策文档ID';
COMMENT ON COLUMN t_policy_doc.title IS '政策标题';
COMMENT ON COLUMN t_policy_doc.content IS '政策正文内容';
COMMENT ON COLUMN t_policy_doc.keywords IS '关键词，多个关键词可用逗号分隔';
COMMENT ON COLUMN t_policy_doc.official_url IS '官方链接';
COMMENT ON COLUMN t_policy_doc.file_id IS '关联文件ID，对应 t_file.id';
COMMENT ON COLUMN t_policy_doc.doc_status IS '文档状态：草稿/已发布/已归档';
COMMENT ON COLUMN t_policy_doc.created_at IS '创建时间';
COMMENT ON COLUMN t_policy_doc.updated_at IS '更新时间';

-- =====================================================
-- 15. 学业预警分析结果表 (新增)
-- 说明：
-- 存储你的 Python 解析引擎对比生成的完整预警结果，
-- 包括提取的课程JSON、挂科记录和学习建议。
-- =====================================================
CREATE TABLE t_warning_record (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    student_no VARCHAR(50) NOT NULL,
    transcript_file_id BIGINT,
    training_plan_id BIGINT,
    warning_level VARCHAR(50) DEFAULT '未知',
    total_earned_credits DECIMAL(10,2),
    course_count INTEGER,
    core_course_count INTEGER,
    failed_course_count INTEGER,
    missing_course_count INTEGER,
    parsed_courses_json TEXT,
    core_courses_json TEXT,
    failed_courses_json TEXT,
    missing_courses_json TEXT,
    suggestions_json TEXT,
    analysis_status INTEGER DEFAULT 1,
    error_message VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_warning_record IS '学业预警分析结果记录表';
COMMENT ON COLUMN t_warning_record.id IS '记录ID';
COMMENT ON COLUMN t_warning_record.user_id IS '上传成绩单的用户ID，对应 t_user.id';
COMMENT ON COLUMN t_warning_record.student_no IS '学生学号，对应 t_student.student_no';
COMMENT ON COLUMN t_warning_record.transcript_file_id IS '成绩单文件ID，对应 t_file.id';
COMMENT ON COLUMN t_warning_record.training_plan_id IS '本次比对所使用的培养方案ID，对应 t_training_plan.id';
COMMENT ON COLUMN t_warning_record.warning_level IS '预警等级（正常/一般预警/严重预警/退学预警等）';
COMMENT ON COLUMN t_warning_record.total_earned_credits IS '累计获得学分';
COMMENT ON COLUMN t_warning_record.course_count IS '成绩单中解析出的总课程数';
COMMENT ON COLUMN t_warning_record.core_course_count IS '解析出的核心课总数';
COMMENT ON COLUMN t_warning_record.failed_course_count IS '挂科课程数';
COMMENT ON COLUMN t_warning_record.missing_course_count IS '缺失核心课程数';
COMMENT ON COLUMN t_warning_record.parsed_courses_json IS '解析出的所有课程信息 JSON 数组';
COMMENT ON COLUMN t_warning_record.core_courses_json IS '解析出的核心课信息 JSON 数组';
COMMENT ON COLUMN t_warning_record.failed_courses_json IS '挂科课程信息 JSON 数组';
COMMENT ON COLUMN t_warning_record.missing_courses_json IS '缺失核心课信息 JSON 数组';
COMMENT ON COLUMN t_warning_record.suggestions_json IS '引擎生成的学习建议 JSON 数组';
COMMENT ON COLUMN t_warning_record.analysis_status IS '分析状态：1成功，0失败';
COMMENT ON COLUMN t_warning_record.error_message IS '如果分析失败，存储错误信息';
COMMENT ON COLUMN t_warning_record.created_at IS '分析时间';
COMMENT ON COLUMN t_warning_record.updated_at IS '更新时间';

-- 创建学号索引，方便按学生查询历史预警记录
CREATE INDEX idx_warning_student_no ON t_warning_record(student_no);

-- =====================================================
-- 7. 培养方案表（JSON 存储）
-- 说明：
-- 直接存储前端生成的培养方案 JSON，后续解析也基于该 JSON
-- =====================================================
CREATE TABLE t_training_plan (
                                 id BIGINT PRIMARY KEY,
                                 major VARCHAR(100) NOT NULL,
                                 grade VARCHAR(50) NOT NULL,
                                 version VARCHAR(50) NOT NULL,
                                 remark VARCHAR(500),
                                 json_content TEXT NOT NULL,
                                 course_count INTEGER,
                                 total_credits DECIMAL(10, 2),
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_training_plan IS '培养方案表（JSON 存储）';
COMMENT ON COLUMN t_training_plan.id IS '记录ID';
COMMENT ON COLUMN t_training_plan.major IS '专业';
COMMENT ON COLUMN t_training_plan.grade IS '年级';
COMMENT ON COLUMN t_training_plan.version IS '版本';
COMMENT ON COLUMN t_training_plan.remark IS '备注';
COMMENT ON COLUMN t_training_plan.json_content IS '培养方案JSON内容';
COMMENT ON COLUMN t_training_plan.course_count IS '课程数';
COMMENT ON COLUMN t_training_plan.total_credits IS '总学分';
COMMENT ON COLUMN t_training_plan.created_at IS '创建时间';
COMMENT ON COLUMN t_training_plan.updated_at IS '更新时间';

-- =====================================================
-- 8. 操作日志表
-- 说明：
-- 记录用户操作，便于后续追踪问题和答辩展示
-- =====================================================
CREATE TABLE t_operation_log (
                                 id BIGINT PRIMARY KEY,
                                 user_id BIGINT,
                                 username VARCHAR(100),
                                 operation VARCHAR(255),
                                 request_method VARCHAR(20),
                                 request_url VARCHAR(500),
                                 request_ip VARCHAR(100),
                                 operation_status INTEGER,
                                 error_message VARCHAR(1000),
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_operation_log IS '操作日志表';
COMMENT ON COLUMN t_operation_log.id IS '日志ID';
COMMENT ON COLUMN t_operation_log.user_id IS '操作用户ID，对应 t_user.id';
COMMENT ON COLUMN t_operation_log.username IS '操作用户名';
COMMENT ON COLUMN t_operation_log.operation IS '操作内容';
COMMENT ON COLUMN t_operation_log.request_method IS '请求方法，如 GET/POST';
COMMENT ON COLUMN t_operation_log.request_url IS '请求地址';
COMMENT ON COLUMN t_operation_log.request_ip IS '请求IP';
COMMENT ON COLUMN t_operation_log.operation_status IS '操作状态：1成功，0失败';
COMMENT ON COLUMN t_operation_log.error_message IS '错误信息';
COMMENT ON COLUMN t_operation_log.created_at IS '创建时间';

-- =====================================================
-- 9. 党团事务标准流程表
-- 说明：
-- 定义入党流程的标准阶段
-- =====================================================
CREATE TABLE t_process_stage (
                                 stage_id INTEGER PRIMARY KEY,
                                 stage_name VARCHAR(100) NOT NULL,
                                 order_num INTEGER NOT NULL,
                                 duration INTEGER,
                                 description VARCHAR(255)
);

COMMENT ON TABLE t_process_stage IS '党团流程标准阶段定义表';
COMMENT ON COLUMN t_process_stage.stage_id IS '阶段ID';
COMMENT ON COLUMN t_process_stage.stage_name IS '阶段名称';
COMMENT ON COLUMN t_process_stage.order_num IS '阶段顺序';
COMMENT ON COLUMN t_process_stage.duration IS '标准持续时长，单位可按业务约定为天';
COMMENT ON COLUMN t_process_stage.description IS '阶段说明';

-- =====================================================
-- 10. 学生党团阶段
-- 说明：
-- 党团阶段已合并到 t_student.party_stage_id 字段，不再单独建表
-- =====================================================

-- =====================================================
-- 11. 电子证明申请表
-- 说明：
-- 记录学生在线申请电子证明的主申请流水
-- 一条申请可以对应多条 t_approval_task 审批任务
-- =====================================================
CREATE TABLE t_certificate_apply (
                                     id BIGINT PRIMARY KEY,
                                     student_no VARCHAR(50) NOT NULL,
                                     certificate_type VARCHAR(100) NOT NULL,
                                     apply_status VARCHAR(50) DEFAULT '待审核',
                                     extra_data VARCHAR(500),
                                     file_id BIGINT,
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_certificate_apply IS '电子证明在线申请流水表';
COMMENT ON COLUMN t_certificate_apply.id IS '申请ID';
COMMENT ON COLUMN t_certificate_apply.student_no IS '申请学生学号，对应 t_student.student_no';
COMMENT ON COLUMN t_certificate_apply.certificate_type IS '证明类型，如在校证明/请假条/用章申请';
COMMENT ON COLUMN t_certificate_apply.apply_status IS '申请状态：待审核/已通过/已驳回/已撤销';
COMMENT ON COLUMN t_certificate_apply.extra_data IS '额外申请信息，可存放简单JSON字符串';
COMMENT ON COLUMN t_certificate_apply.file_id IS '生成的证明文件ID，对应 t_file.id';
COMMENT ON COLUMN t_certificate_apply.created_at IS '创建时间';
COMMENT ON COLUMN t_certificate_apply.updated_at IS '更新时间';

-- =====================================================
-- 12. 审批任务表
-- 说明：
-- 一个电子证明申请可以对应多个审批任务节点
-- 用于记录各级老师/管理员的审批状态、意见和处理时间
-- =====================================================
CREATE TABLE t_approval_task (
                                 id BIGINT PRIMARY KEY,
                                 apply_id BIGINT NOT NULL,
                                 node_order INTEGER NOT NULL,
                                 approver_id BIGINT,
                                 approver_name VARCHAR(100),
                                 approval_status VARCHAR(50) DEFAULT '待处理',
                                 opinion VARCHAR(500),
                                 handled_at TIMESTAMP,
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_approval_task IS '电子证明审批任务表';
COMMENT ON COLUMN t_approval_task.id IS '审批任务ID';
COMMENT ON COLUMN t_approval_task.apply_id IS '证明申请ID，对应 t_certificate_apply.id';
COMMENT ON COLUMN t_approval_task.node_order IS '审批节点顺序';
COMMENT ON COLUMN t_approval_task.approver_id IS '审批人用户ID，对应 t_user.id';
COMMENT ON COLUMN t_approval_task.approver_name IS '审批人姓名';
COMMENT ON COLUMN t_approval_task.approval_status IS '审批状态：待处理/已通过/已驳回';
COMMENT ON COLUMN t_approval_task.opinion IS '审批意见';
COMMENT ON COLUMN t_approval_task.handled_at IS '审批处理时间';
COMMENT ON COLUMN t_approval_task.created_at IS '创建时间';
COMMENT ON COLUMN t_approval_task.updated_at IS '更新时间';

-- =====================================================
-- 13. 通知公告表
-- 说明：
-- 管理员发布精准通知，支持标签和附件
-- =====================================================
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
);

COMMENT ON TABLE t_notification IS '精准通知发布表';
COMMENT ON COLUMN t_notification.id IS '通知ID';
COMMENT ON COLUMN t_notification.title IS '通知标题';
COMMENT ON COLUMN t_notification.content IS '通知正文';
COMMENT ON COLUMN t_notification.tags IS '目标标签，多个标签可用逗号分隔';
COMMENT ON COLUMN t_notification.is_urgent IS '是否紧急通知';
COMMENT ON COLUMN t_notification.file_id IS '附件文件ID，对应 t_file.id';
COMMENT ON COLUMN t_notification.publisher_id IS '发布人ID，对应 t_user.id';
COMMENT ON COLUMN t_notification.created_at IS '创建时间';
COMMENT ON COLUMN t_notification.updated_at IS '更新时间';

-- =====================================================
-- 14. 通知已读回执表
-- 说明：
-- 记录学生是否确认阅读通知
-- =====================================================
CREATE TABLE t_notification_receipt (
                                        id BIGINT PRIMARY KEY,
                                        notification_id BIGINT NOT NULL,
                                        student_no VARCHAR(50) NOT NULL,
                                        is_confirmed BOOLEAN DEFAULT FALSE,
                                        confirmed_at TIMESTAMP,
                                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_notification_receipt ON t_notification_receipt(notification_id, student_no);

COMMENT ON TABLE t_notification_receipt IS '学生通知已读回执表';
COMMENT ON COLUMN t_notification_receipt.id IS '回执ID';
COMMENT ON COLUMN t_notification_receipt.notification_id IS '通知ID，对应 t_notification.id';
COMMENT ON COLUMN t_notification_receipt.student_no IS '学生学号，对应 t_student.student_no';
COMMENT ON COLUMN t_notification_receipt.is_confirmed IS '是否已确认阅读';
COMMENT ON COLUMN t_notification_receipt.confirmed_at IS '确认阅读时间';
COMMENT ON COLUMN t_notification_receipt.created_at IS '创建时间';

-- =====================================================
-- 初始化基础角色数据
-- =====================================================
INSERT INTO t_role (id, role_code, role_name, description)
VALUES
    (1, 'student', '学生', '微信小程序学生端用户'),
    (2, 'admin', '管理员', 'Web后台管理员');
--  增加的初始用户
-- 不再在 SQL 中内置公开的管理员密码。
-- 首次启动前设置 ADMIN_BOOTSTRAP_PASSWORD，应用将使用 BCrypt 创建管理员账号。

-- =====================================================
-- 初始化党团流程标准阶段
-- =====================================================
INSERT INTO t_process_stage (stage_id, stage_name, order_num, duration, description)
VALUES
    (0, '未申请', 0, NULL, '未提交入党申请书'),
    (1, '入党申请人', 1, NULL, '提交入党申请书后的初始阶段'),
    (2, '入党积极分子', 2, NULL, '经推荐和培养后确定为入党积极分子'),
    (3, '发展对象', 3, NULL, '经过培养考察后确定为发展对象'),
    (4, '预备党员', 4, NULL, '支部大会通过并经上级党组织批准后成为预备党员'),
    (5, '正式党员', 5, NULL, '预备期满并转正后成为正式党员');

-- =====================================================
-- 可选测试数据
-- 正式提交时保持注释，不默认插入测试数据
-- 如果需要测试，可以手动取消注释执行
-- 注意：
-- 学生登录时，后端应支持用 student_no 登录：
-- SELECT * FROM t_user WHERE username = ? OR student_no = ?
-- =====================================================

-- INSERT INTO t_student (
--     id, student_no, name, gender, ethnicity, political_status,
--     party_stage_id, class_name, major, grade, contact, status
-- )
-- VALUES
--     (1, '20240001', '张三', '未知', '汉族', '未知',
--      1, '计科2401', '计算机科学与技术', '2024', '13800000000', 1);

-- 测试账号也应通过导入接口或 ADMIN_BOOTSTRAP_PASSWORD 创建，禁止在 SQL 中保存明文密码。

-- INSERT INTO t_certificate_apply (
--     id, student_no, certificate_type, apply_status, extra_data, file_id
-- )
-- VALUES
--     (1, '20240001', '在校证明', '待审核', '{"reason":"测试申请"}', NULL);

-- INSERT INTO t_approval_task (
--     id, apply_id, node_order, approver_id, approver_name, approval_status, opinion
-- )
-- VALUES
--     (1, 1, 1, 2, '测试管理员', '待处理', NULL);

-- INSERT INTO t_notification (
--     id, title, content, tags, is_urgent, file_id, publisher_id
-- )
-- VALUES
--     (1, '测试通知', '这是一条测试通知', '2024级,计科', FALSE, NULL, 2);

-- INSERT INTO t_notification_receipt (
--     id, notification_id, student_no, is_confirmed, confirmed_at
-- )
-- VALUES
--     (1, 1, '20240001', TRUE, CURRENT_TIMESTAMP);
