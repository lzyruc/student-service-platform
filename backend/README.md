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
- KingbaseES、JJWT 0.12、BCrypt、Jakarta Validation
- Apache POI / PDFBox（证明文件生成）
- JUnit 5、Maven、REST + Python FastAPI 服务集成

## 启动

```powershell
cd "C:\Users\Lenovo\Desktop\agent开发准备\student\backend"
Copy-Item src/main/resources/application-example.properties src/main/resources/application.properties
```

至少配置以下环境变量：

```powershell
$env:DB_URL="jdbc:kingbase8://localhost:54321/student_platform"
$env:DB_USERNAME="your_user"
$env:DB_PASSWORD="your_password"
$env:JWT_SECRET="replace-with-at-least-32-random-bytes"
$env:ADMIN_BOOTSTRAP_PASSWORD="one-time-strong-admin-password"
```

先在 Kingbase 执行 [init.sql](docs/sql/init.sql)，再启动：

```powershell
.\mvnw.cmd spring-boot:run
```

首次启动会在不存在 admin 时以 BCrypt 创建管理员；创建成功后应删除 `ADMIN_BOOTSTRAP_PASSWORD`。不建议设置全局 `DEFAULT_STUDENT_PASSWORD`，批量导入新账号时直接提供各自初始密码更安全。

健康检查：`GET http://localhost:8081/api/health`。登录接口：

- 管理后台：`POST /api/geeker/login`
- 学生/管理员统一入口：`POST /api/auth/login`
- 管理员修改密码：`POST /api/geeker/user/change_password`（需登录，校验原密码，新密码为 6-72 位）
- 登录后使用 `Authorization: Bearer <token>` 或兼容头 `x-access-token`

两个 Python 服务默认是 `http://127.0.0.1:8000` 和 `http://127.0.0.1:8002`，可通过 `AI_SERVICE_BASE_URL`、`WARNING_SERVICE_BASE_URL` 修改。

## 测试

```powershell
.\mvnw.cmd test
```

当前自动化测试不依赖 Kingbase，重点验证安全组件。完整业务联调仍需要 Kingbase 和两个 Python 服务；这部分应作为本地/CI 集成环境单独运行，不能把“Java 单元测试通过”等同于四端系统已经全部联通。

## 权限边界

| 能力 | student | admin |
| --- | ---: | ---: |
| 查看本人信息、通知、党团进度、预警 | ✅ | ✅（可指定学生） |
| 提交/查看本人证明申请 | ✅ | ✅ |
| 查看或删除他人证明申请 | ❌ | ✅ |
| 导入、查询、删除学生 | ❌ | ✅ |
| 发布通知、维护培养方案、重建知识库 | ❌ | ✅ |
| 下载通知公共附件或本人证明 | ✅ | ✅ |

## 技术设计摘要

- 使用 JJWT 和 BCrypt 建立标准认证链路，并对历史密码进行渐进迁移。
- 同时实施接口级 RBAC 与资源级所有权检查，防止水平越权。
- 对文件路径、跨域来源和外部服务调用设置明确的安全边界。
- 通过统一异常、请求链路 ID 和安全组件测试提高可维护性。

## 仍应如实说明的边界

- Kingbase JDBC 驱动按团队已取得的许可使用，目前以项目内 jar 的 `systemPath` 引用；团队制品库可用时应发布到私有 Nexus/Artifactory，以消除 Maven 可移植性警告。
- 当前是单体服务且使用 BIGINT 时间序列 ID；真正多实例部署应改为数据库 sequence/雪花 ID 服务。
- 登录限流、验证码和账号锁定适合在网关/Redis 层实现，本仓库没有伪装成已完成。
- 真实环境还应补 Testcontainers/独立 Kingbase 测试库的 Controller/Repository 集成测试和依赖漏洞扫描。
