# 学生综合服务平台

学生综合服务平台由 Java 后端、Python 智能服务、微信小程序和 Web 管理端组成，覆盖学生档案、通知回执、培养方案、学业预警、政策问答、党团进度和电子证明审批等业务。

## 系统组成

| 模块 | 技术栈 | 职责 |
| --- | --- | --- |
| [`backend/`](./backend/) | Java 17、Spring Boot、JdbcTemplate、KingbaseES | 认证授权、学生数据、业务接口、文件与审批服务 |
| [`python-services/`](./python-services/) | Python、FastAPI、LangChain、Chroma | 政策知识库问答、成绩单解析与学业预警 |
| [`miniprogram/`](./miniprogram/) | 微信小程序原生框架 | 学生端业务入口 |
| [`web-admin/`](./web-admin/) | Vue 3、TypeScript、Vite、Element Plus | 管理员业务后台 |

## 主要功能

- 学生与管理员登录、JWT 身份认证及角色权限控制
- 学生档案批量导入、查询和状态管理
- 通知发布、附件下载和学生阅读回执
- 培养方案维护与版本管理
- 成绩单解析、课程匹配和学业风险分析
- 学生政策知识库问答
- 党团发展阶段查询
- 电子证明申请、审批、生成和下载
- 请求链路标识、统一异常处理和外部服务超时控制

## 架构

```text
微信小程序 ───────────────┐
                          ├──> Spring Boot API :8081 ──> KingbaseES
Vue Web 管理端 :8848 ─────┘             │
                                        ├──> 政策问答服务 :8000
                                        └──> 学业预警服务 :8002
```

Spring Boot 是统一业务入口和权限边界。Python 服务负责模型、文档检索和成绩分析，不直接向前端暴露管理权限。

## 安全设计

- JWT Secret、数据库密码和外部服务地址通过环境变量注入。
- 浏览器通过 HTTPS 提交原始密码，由后端统一使用 BCrypt 校验和哈希；前端不再使用 MD5。
- 新账号必须显式填写 6-72 位初始密码；编辑账号时留空表示不修改，密码不会写入前端缓存或导出数据。
- 历史明文或 MD5 密码仅用于兼容迁移，登录成功后自动升级为 BCrypt。
- 学生身份取自 JWT，客户端提交的学号不能用于访问其他学生数据。
- 管理接口使用精确路由授权，敏感资源同时检查数据所有权。
- 文件下载在受控根目录内解析路径，并校验通知、证明或上传者归属。
- CORS 使用明确来源列表；下游调用具有连接超时、读取超时和有限重试。
- 响应携带 `X-Request-ID`，服务日志使用同一标识关联请求。

后端设计与配置详见 [backend/README.md](./backend/README.md)。

## 环境要求

- Java 17
- Maven 3.9+
- KingbaseES 8/9
- Python 3.11+
- Node.js 18+
- pnpm 9+
- 微信开发者工具

## 配置与启动

### 1. 数据库与 Java 后端

在 KingbaseES 中执行：

```text
backend/docs/sql/init.sql
```

复制配置文件并通过环境变量提供敏感配置：

```powershell
cd backend
Copy-Item src/main/resources/application-example.properties src/main/resources/application.properties

$env:DB_URL="jdbc:kingbase8://localhost:54321/student_platform"
$env:DB_USERNAME="your_user"
$env:DB_PASSWORD="your_password"
$env:JWT_SECRET="replace-with-at-least-32-random-bytes"
$env:ADMIN_BOOTSTRAP_PASSWORD="one-time-strong-admin-password"

.\mvnw.cmd spring-boot:run
```

后端默认运行在 `http://localhost:8081`。首次成功创建管理员后，应删除 `ADMIN_BOOTSTRAP_PASSWORD`。

### 2. Python 智能服务

```powershell
cd python-services
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
Copy-Item .env.example .env
```

分别启动：

```powershell
python AIAnalysis.py
python 5_pdf_compare.py
```

默认端口：

- 政策问答服务：`8000`
- 学业预警服务：`8002`

### 3. Web 管理端

```powershell
cd web-admin
Copy-Item .env.example .env
pnpm install
pnpm dev
```

开发服务器默认运行在 `http://localhost:8848`，API 代理在环境配置中指向 Java 后端。

### 4. 微信小程序

使用微信开发者工具导入 `miniprogram/`，再在本地项目设置中填写团队自己的 AppID。仓库中的 `touristappid` 仅用于公开示例。后端地址可以写入小程序 Storage 的 `baseUrl`，正式部署时必须使用已备案且配置到微信后台的 HTTPS 域名。

## 测试与构建

Java 后端：

```powershell
cd backend
.\mvnw.cmd test
```

Web 管理端：

```powershell
cd web-admin
pnpm type:check
pnpm build:dev
```

## 部署说明

- Java 后端、两个 Python 服务和数据库应部署在受控内网。
- 前端只访问 Java API，不应直接连接数据库或模型服务。
- 生产环境应在 API Gateway 接入 HTTPS、限流、统一认证和审计。
- 上传文件应使用对象存储，并为私有文件生成短期签名地址。
- Chroma 索引、上传目录和数据库必须配置持久化卷与备份策略。

## 当前边界

- KingbaseES 与项目内 JDBC 驱动按团队已取得的许可使用；团队制品库可用时可迁移到私有 Maven 仓库，以改善依赖可移植性。
- Web 管理端仍保留原 Geeker-Admin 的 MIT License 和署名。
- 四个模块需要按本地数据库、模型服务和微信环境完成联合配置后才能进行端到端运行。
- 生产部署前还应补充登录限流、依赖漏洞扫描，以及基于独立 Kingbase 测试库的接口集成测试。

## 许可证与第三方组件

Web 管理端基于 Geeker-Admin，相关许可证位于 [`web-admin/LICENSE`](./web-admin/LICENSE)。其他模块和数据文件在公开发布前应补充顶层许可证，并确认数据库驱动、字体及政策文档的再分发权限。
