# Student Service Platform Backend

学生综合服务平台的 Java 核心后端，负责用户认证、学生档案、通知回执、培养方案、学业预警代理、电子证明审批和文件服务。当前版本已完成认证授权、文件安全、下游可靠性和可观测性改造。

## 本轮完成的关键改造

- 认证：用 JJWT 的标准签名/过期/issuer 校验替换手写 JWT；Secret 必须由环境变量注入且不少于 32 字节。
- 密码：登录与修改密码接收 HTTPS 传输的原始密码，并在后端统一使用 BCrypt；历史明文和 MD5 仅保留登录兼容，首次成功登录后自动升级 BCrypt。
- 授权：从“`/api/student/**` 全部放行”改为精确路由白名单，学生导入、列表、删除等管理接口仅 admin 可用。
- 防 IDOR：学生学号从 JWT subject 获取，请求中的 `studentNo/account` 不能切换为其他学生；证明申请详情/删除和文件下载增加资源所有权校验。
- 文件安全：下载不再信任数据库中的任意 `file_path`，统一在上传根目录内按 `stored_name` 解析并校验路径边界；原文件名去除目录部分。
- 接口治理：参数校验、统一异常到正确 HTTP 状态、服务端异常不再把数据库/下游错误详情返回客户端。
- 下游可靠性：Java 调用两个 Python 服务时具有连接/读取超时；只读/可安全重试的调用使用有限重试，写操作不盲目重试。
- 配置安全：CORS 使用明确来源列表；数据库密码、服务地址、JWT、默认密码均支持环境变量；数据库脚本不再写入公开的 MD5 管理员密码。
- 可观测性：每个响应返回 `X-Request-ID`，日志 MDC 同步写入 requestId，便于跨服务排障。
- 测试：加入 BCrypt、旧密码迁移兼容、JWT claims/签名配置测试。

## 技术栈

- Java 17、Spring Boot 3.3.5、Spring MVC、JdbcTemplate
- MySQL 8、JJWT 0.12、BCrypt、Jakarta Validation
- Apache POI / PDFBox（证明文件生成）
- JUnit 5、Maven、REST + Python FastAPI 服务集成

## 启动

完整的本地启动顺序、环境变量、Python 服务、Web 和小程序配置以 [项目根 README](../README.md#windows-本地启动当前已跑通版本) 为准。先确认 MySQL 已运行，并分别启动政策问答服务 `8000` 和学业预警服务 `8002`。

运行 `start-local.cmd`：首次提示输入 MySQL 用户名（默认 `root`）、Workbench 连接密码和已有的 JWT 密钥，保存到本机私有的 `local-config.json`。输入密码和密钥时不显示字符；JWT 留空会生成并保存固定随机密钥，已有登录需要重新登录。以后直接运行脚本或双击该文件即可，无需重复设置环境变量。

`DB_URL` 默认指向本机 `3306` 的 `student_platform`，`CORS_ALLOWED_ORIGINS` 默认允许管理端 `http://localhost:8848,http://127.0.0.1:8848`。如需修改，编辑 `local-config.json`；手工配置模板为 [local-config.example.json](local-config.example.json)。本机配置文件已加入 Git 忽略规则，不要分享其中的密码和密钥。首次运行可从当前终端的已有环境变量保存配置；之后以本机文件为准。

当前本机最新包包含之前的修复，以及成绩单上传后持久保存、直接再次分析的功能，启动命令：

```powershell
cd "D:\agent开发准备\简历项目\student-service-platform\backend"
.\start-local.cmd
```

脚本自动切换到 `backend` 目录，并从 `target` 及子目录选择修改时间最新的应用 JAR；当前最新包位于 `target/transcript-build`。构建产物不随 Git 分发，重新克隆或修改 Java 代码后，停止旧后端并重新构建：

```powershell
.\mvnw.cmd package
.\start-local.cmd
```

已有数据库和配置可直接使用，不要日常重跑 [init-mysql.sql](docs/sql/init-mysql.sql)：该脚本会删除并重建整个数据库。仅首次创建空环境时执行它；`application.properties` 不存在时才从示例复制。

首次创建管理员时在 `local-config.json` 增加 `ADMIN_BOOTSTRAP_PASSWORD` 字符串字段，后端会在不存在 admin 时以 BCrypt 创建账号；创建成功后删除该字段。重启时保持相同的 JWT 密钥，否则旧 Token 失效。不建议设置全局 `DEFAULT_STUDENT_PASSWORD`，批量导入新账号时直接提供各自初始密码更安全。

可用以下命令检查配置格式、Java 和 JAR，而不启动任何服务或连接数据库：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-local.ps1 -CheckConfig
```

需要指定运行包时，使用 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-local.ps1 -JarPath .\target\student-service-platform-0.0.1-SNAPSHOT.jar`。脚本固定启用本地 `dev` Profile。

在 Windows + JDK 17 下，如果项目路径包含中文，`spring-boot:run` 的类路径参数文件可能发生编码错误；运行可执行 jar 可避免该问题。

健康检查：`GET http://localhost:8081/api/health`。登录接口：

- 管理后台：`POST /api/geeker/login`
- 学生/管理员统一入口：`POST /api/auth/login`
- 管理员修改密码：`POST /api/geeker/user/change_password`（需登录，校验原密码，新密码为 6-72 位）
- 登录后使用 `Authorization: Bearer <token>` 或兼容头 `x-access-token`

`/api/test/db` 和 `/api/test/user-count` 仅在 `dev` Profile 下提供，当前在认证过滤器中放行；其余业务接口仍需登录。正式环境不要启用 `dev`。健康检查成功不等于数据库和 Python 服务已经联通。

两个 Python 服务默认是 `http://127.0.0.1:8000` 和 `http://127.0.0.1:8002`，可通过 `AI_SERVICE_BASE_URL`、`WARNING_SERVICE_BASE_URL` 修改。

成绩单按表格列读取最终成绩，核心课程包含部类基础课、部类共同课和思想政治理论课，按计划开课学期区分缺课与未到开课学期。实测及字段说明见 [成绩单解析与学业预警](docs/academic-warning.md)。

成绩单支持上传后持久复用：`POST /api/student/transcript` 保存，`GET /api/student/transcript` 读取本人最新文件，`POST /api/student/warning/analyze-saved` 使用该文件重新分析。复用现有 `t_file` 与 `t_warning_record`，无需数据库迁移；原 PDF 保存在 `uploads`。

## 测试

```powershell
.\mvnw.cmd test
```

当前自动化测试覆盖安全组件及部分业务链路，使用 Mock 或内存测试数据库，不依赖运行中的 MySQL。完整业务联调仍需要 MySQL 和两个 Python 服务；不能把 Java 单元测试通过等同于四端系统已经全部联通。

## 权限边界

| 能力 | student | admin |
| --- | ---: | ---: |
| 查看本人信息、通知、预警 | ✅ | ✅（可指定学生） |
| 提交/查看本人证明申请 | ✅ | ✅ |
| 查看或删除他人证明申请 | ❌ | ✅ |
| 导入、查询、删除学生 | ❌ | ✅ |
| 发布通知、维护培养方案、维护政策知识库 | ❌ | ✅ |
| 下载通知公共附件或本人证明 | ✅ | ✅ |

## 技术设计摘要

- 使用 JJWT 和 BCrypt 建立标准认证链路，并对历史密码进行渐进迁移。
- 同时实施接口级 RBAC 与资源级所有权检查，防止水平越权。
- 对文件路径、跨域来源和外部服务调用设置明确的安全边界。
- 通过统一异常、请求链路 ID 和安全组件测试提高可维护性。

## 仍应如实说明的边界

- 关系数据库基线已切换为 MySQL 8；`docs/sql/init.sql` 仅作为历史 Kingbase 脚本保留，新环境使用 `docs/sql/init-mysql.sql`。
- 当前是单体服务且使用 BIGINT 时间序列 ID；真正多实例部署应改为数据库 sequence/雪花 ID 服务。
- 登录限流、验证码和账号锁定适合在网关/Redis 层实现，本仓库没有伪装成已完成。
- 真实环境还应补 Testcontainers/独立 MySQL 测试库的 Controller/Repository 集成测试和依赖漏洞扫描。
