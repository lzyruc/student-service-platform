# 学生综合服务平台

学生综合服务平台由 Java 后端、Python 智能服务、微信小程序和 Web 管理端组成，覆盖学生档案、通知回执、培养方案、学业预警、政策问答和电子证明审批等业务。

## 系统组成

| 模块 | 技术栈 | 职责 |
| --- | --- | --- |
| [`backend/`](./backend/) | Java 17、Spring Boot、JdbcTemplate、MySQL 8 | 认证授权、学生数据、业务接口、文件与审批服务 |
| [`python-services/`](./python-services/) | Python、FastAPI、LangChain、Chroma | 政策知识库问答、成绩单解析与学业预警 |
| [`miniprogram/`](./miniprogram/) | 微信小程序原生框架 | 学生端业务入口 |
| [`web-admin/`](./web-admin/) | Vue 3、TypeScript、Vite、Element Plus | 管理员业务后台 |

## 主要功能

- 学生与管理员登录、JWT 身份认证及角色权限控制
- 学生档案批量导入、查询和状态管理
- 通知发布、附件下载和学生阅读回执
- 培养方案维护与版本管理
- 成绩单解析、课程匹配和学业风险分析
- 成绩单上传一次后保存，重新进入小程序可直接分析或更新成绩单
- 学生政策知识库问答
- 电子证明申请、审批、生成和下载
- 请求链路标识、统一异常处理和外部服务超时控制

## 架构

```text
微信小程序 ───────────────┐
                          ├──> Spring Boot API :8081 ──> MySQL 8
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
- MySQL 8.0+
- Python 3.11+
- Node.js 18+
- pnpm 9+
- 微信开发者工具

## Windows 本地启动（当前已跑通版本）

当前项目目录为 `D:\agent开发准备\简历项目\student-service-platform`。下面命令使用 PowerShell；项目迁到其他位置时，替换各命令中的目录即可。

| 服务 | 本地地址 | 启动入口 |
| --- | --- | --- |
| MySQL | `127.0.0.1:3306` | Windows MySQL 服务，数据库为 `student_platform` |
| 政策问答与知识库入库 | `http://127.0.0.1:8000` | `AIAnalysis:app` |
| 学业预警分析 | `http://127.0.0.1:8002` | `5_pdf_compare.py` |
| Java 业务后端 | `http://127.0.0.1:8081` | Spring Boot 可执行 JAR |
| Web 管理端 | `http://localhost:8848` | `pnpm.cmd dev` |
| 微信小程序 | 微信开发者工具 / 真机预览 | 导入 `miniprogram` |

### 日常启动：四个独立终端

确认 MySQL 服务已运行，然后依次启动下面四项。每个服务会持续占用当前终端，看到启动成功的日志后，另开终端执行下一项；保留这四个终端运行。

**终端 1：政策问答服务（8000）**

```powershell
cd "D:\agent开发准备\简历项目\student-service-platform\python-services"
.\.venv\Scripts\python.exe -m uvicorn AIAnalysis:app --host 0.0.0.0 --port 8000
```

等待出现 `Uvicorn running on http://0.0.0.0:8000`。首次加载本地向量模型可能较慢；已有模型缓存后不必每次重新下载。`python-services/.env` 中保留已填写的 DeepSeek 配置。

**终端 2：学业预警服务（8002）**

```powershell
cd "D:\agent开发准备\简历项目\student-service-platform\python-services"
.\.venv\Scripts\python.exe .\5_pdf_compare.py
```

等待出现 `Uvicorn running on http://0.0.0.0:8002`。这个服务只负责成绩分析；只启动它，政策问答仍无法使用。

**终端 3：Java 后端（8081）**

运行本地启动脚本。首次运行会提示输入 MySQL 用户名、数据库密码和已有的 JWT 密钥，并保存到仅本机使用的 `backend/local-config.json`；以后启动自动读取，无需每次设置环境变量。也可以在资源管理器中直接双击 `backend/start-local.cmd`。

```powershell
cd "D:\agent开发准备\简历项目\student-service-platform\backend"
.\start-local.cmd
```

首次配置各项含义：

| 配置 | 填写方式 |
| --- | --- |
| `DB_USERNAME` | MySQL Workbench 连接使用的用户名，通常为 `root`；提示时直接回车默认 `root` |
| `DB_PASSWORD` | 上述 MySQL 账号的密码，和平台管理员登录密码无关；输入时不显示字符 |
| `JWT_SECRET` | 粘贴此前使用的实际密钥，至少 32 个 UTF-8 字节；回车留空会生成并保存一个固定随机密钥，需要重新登录已有会话 |
| `DB_URL` | 默认连接本机 `3306` 端口的 `student_platform`，保持默认即可 |
| `CORS_ALLOWED_ORIGINS` | 默认允许管理端 `http://localhost:8848,http://127.0.0.1:8848`，保持默认即可 |

首次运行时，如果当前终端已有这些环境变量，会优先保存已有值。配置文件建立后，以文件中的值为准；修改账号、密码或连接地址时编辑该文件。文件含有本机密码，已排除在 Git 提交之外。手工配置可参考 [local-config.example.json](./backend/local-config.example.json)，复制为 `local-config.json` 后填写实际值；JSON 字符串中的反斜杠和双引号需要转义，使用首次输入向导会自动处理。

脚本自动从 `target` 及子目录中选择修改时间最新的应用 JAR，并显示实际路径；当前学业 Agent 包为 `target/student-service-platform-0.0.1-SNAPSHOT.jar`，包含成绩单复用、只读学业工具、DeepSeek 调用循环与 Skill 提示词。需要指定包时，可运行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-local.ps1 -JarPath .\target\student-service-platform-0.0.1-SNAPSHOT.jar`。构建产物不随 Git 分发，重新克隆或修改 Java 代码后仍需先打包。

等待出现 `Started StudentServicePlatformApplication`，并确认没有随后的数据库或启动异常。终端持续运行是正常状态，后端正在等待请求。脚本自动切换到 `backend` 目录，使默认相对路径 `uploads` 始终指向同一位置。已有后端正在运行时，先在旧终端按 `Ctrl+C`，再启动新进程。

管理员已经创建后，日常启动不需要设置 `ADMIN_BOOTSTRAP_PASSWORD`。如需仅检查配置格式、Java 和 JAR 是否存在，而不启动后端，可执行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-local.ps1 -CheckConfig`；这项检查不验证数据库密码或数据库连通性。

**终端 4：Web 管理端（8848）**

```powershell
cd "D:\agent开发准备\简历项目\student-service-platform\web-admin"
pnpm.cmd dev
```

浏览器打开 `http://localhost:8848`，使用已有管理员账号登录。开发配置位于 `web-admin/.env.development`，`/api` 通过 Vite 代理转发到 `http://localhost:8081/api`。

### 启动后检查

另开一个 PowerShell 终端：

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/health"
```

返回 `code=200`、`系统运行正常` 表示 Java HTTP 服务可用。再打开 `http://127.0.0.1:8000/docs` 和 `http://127.0.0.1:8002/docs`，确认两个 Python 服务都已启动。

当前数据库检查接口仅在 `dev` Profile 下开放，并在认证过滤器中放行，可直接执行：

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/test/db"
Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/test/user-count"
```

其余业务接口仍需登录。正式环境不要启用 `dev` Profile。

健康检查通过不能单独证明数据库和模型调用正常。最后在 Web 查看已有学生数据，发布政策并等待入库状态变为 `READY`，在小程序试一次政策问答和已审批证明下载，确认实际业务链路。

### 微信小程序：模拟器与真机

1. 用微信开发者工具导入 `D:\agent开发准备\简历项目\student-service-platform\miniprogram`，使用自己的 AppID；仓库中的 `touristappid` 仅用于公开示例。
2. 电脑模拟器默认连接 `http://127.0.0.1:8081`。本地调试时在开发者工具中启用不校验合法域名的调试设置，再编译并登录。
3. 真机与电脑连接同一局域网，用 `ipconfig` 查找电脑当前网络的 IPv4 地址，例如 `192.168.1.100`。真机的后端地址应为 `http://192.168.1.100:8081`，不能使用 `127.0.0.1`。
4. 本地真机预览时，可把 `miniprogram/utils/request.js` 的 `DEFAULT_BASE_URL` 改为电脑实际局域网地址，再编译并重新生成预览。Storage 中已有的 `baseUrl` 会覆盖默认值，需要同步更新或清除。模拟器和手机的 Storage 相互独立，在开发者工具控制台设置 `baseUrl` 不会自动修改手机上的值。
5. 电脑防火墙需要允许 Java 接收局域网请求；真机调试还需使用微信允许的调试模式。正式发布应改为 HTTPS 域名，并分别配置 `request`、`uploadFile`、`downloadFile` 合法域名。

修改小程序代码后需要重新编译、生成预览并重新进入；旧预览不会自动获取改动。证明审批通过后生成的是 DOCX，下载会携带 JWT，并按真实文件格式打开，文档菜单可用于保存或转发。

### 首次安装或重新克隆时的准备

当前已经跑通的本机环境可以跳过本节。保留现有 `.env`、数据库、上传文件和 Chroma 索引，不要每次启动覆盖配置或重建数据库。

- **数据库**：只在首次创建空库或明确需要清空重建时，用 MySQL Workbench 执行 [init-mysql.sql](./backend/docs/sql/init-mysql.sql)。该脚本会 `DROP DATABASE`，清空整个 `student_platform`。
- **Java 配置**：只有 `application.properties` 不存在时，才从 `application-example.properties` 复制。按前述终端 3 运行启动脚本并完成一次本机配置。首次创建管理员时额外在 `backend/local-config.json` 中增加 `"ADMIN_BOOTSTRAP_PASSWORD": "实际初始密码"`；创建成功后删除该字段。
- **Python 依赖**：在 `python-services` 下执行以下命令；项目从 C 盘迁移到 D 盘后，如果旧虚拟环境无法使用，也可在确认后重新创建虚拟环境并安装依赖。

```powershell
cd "D:\agent开发准备\简历项目\student-service-platform\python-services"
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
if (!(Test-Path .env)) { Copy-Item .env.example .env }
```

在 `.env` 中填写 `DEEPSEEK_API_KEY`；模型配置以当前 `.env` 为准，示例默认是 `deepseek-flash`。RAG 向量由本地 `BAAI/bge-small-zh-v1.5` 生成；首次启动会下载向量模型。更换向量模型后需要重新为政策入库。

- **Web 依赖**：在 `web-admin` 下执行一次 `pnpm.cmd install`，之后日常直接执行 `pnpm.cmd dev`。
- **Java 构建**：按下一节生成标准 JAR，再启动四个服务。

### 代码更新后重新打包

Java 代码修改后，在运行后端的终端按 `Ctrl+C` 停止旧进程，然后执行：

```powershell
cd "D:\agent开发准备\简历项目\student-service-platform\backend"
.\mvnw.cmd package
.\start-local.cmd
```

重新打包后，标准 `target` 目录里的 JAR 就是新的运行包，脚本会按修改时间自动选择它。已保存的本机配置会继续使用，新开的终端无需重复填写。在 Windows + JDK 17 下，中文目录可能使 `spring-boot:run` 的类路径解析失败，因此本项目本地使用可执行 JAR 启动。

Python 代码修改后重启对应 Python 终端；Web 开发服务器通常会自动更新；小程序重新编译和预览。关闭本地环境时，在四个服务终端分别按 `Ctrl+C`。

### 政策文件和知识库的位置

政策知识库统一由 Web 管理端维护：原文件保存到 Java `backend/uploads`，政策和文件记录存放在 MySQL 的 `t_policy_doc`、`t_file`；发布后 Python 将文本和向量写入 `python-services/chroma_db`。上传后还需要登记并发布政策，入库完成后才能参与问答。

问答只使用数据库中“已发布、已就绪”的政策，支持 PDF 和通过 OCR 识别的 PNG/JPG 校历图片。不要再向旧 `政策文件库` 文件夹放文件，Python 已移除旧目录扫描入口。

旧版本升级时，先启动新版 Python，再从 `backend` 目录启动新版 Java。Java 会尝试将原 `python-services/政策文件库` 的文件迁入管理端并发布入库；全部登记完成后将旧目录重命名为 `政策文件库.imported` 作为备份。入库失败的政策可在 Web 重新发布；目录被占用时可能无法重命名。原有数据库和 Web 上传文件会保留，无需重跑初始化脚本。详见 [政策知识库迁移说明](./backend/docs/knowledge-base-api.md)。

## Agent 开发

采用可扩展的 Single Agent 架构，第一版开发 Academic Analysis Skill。目前已完成业务 Service、共享快照、确定性统计、4 个只读 Tool、专用 DTO、DeepSeek 客户端、简单调用循环、学生聊天接口和独立 Skill 提示词。当前 108 项本地回归测试通过，2 项真实模型测试默认跳过；此前真实模型协议测试只使用虚构数据。小程序首页四个功能下方已增加“学业智能助手”入口，支持提问和追问，33 项小程序回归测试通过；真实成绩单问答和真机体验验收正在继续。实际开发思路、实现与测试记录见 [Agent 开发记录与复盘](./AGENT_DEVELOPMENT.md)。

分析上下文仅在一次 Agent HTTP 请求及其整个 Tool Calling Loop 内共享，最多解析一次 PDF；同一会话下一轮请求仍创建新上下文。当前没有跨请求缓存，未来对话消息持久化与这个请求内分析机制分开设计。

政策问答和学业 Agent **共用 `python-services/.env` 中的 `DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL`**，已有配置可直接使用。修改这三项后重启对应的 Java／Python 服务。Agent 超时和工具调用上限在 [backend/agent-config.properties](./backend/agent-config.properties) 修改，真实 Key 不要写入公开配置文件。

Academic Skill 的完整提示词在 [academic.md](./backend/src/main/resources/agent/skills/academic.md)。默认从 `backend` 启动时读取这个文件，修改正文后重启 Java 生效；首次使用本轮加载器需要启动新版 JAR。文件只部署在 JAR 内时，使用打包资源。自定义可信文件路径可在 `agent-config.properties` 的 `agent.academic.prompt-file` 修改。提示词如何选择工具、解释证据和处理数据不足，见 [第五步开发记录](./AGENT_DEVELOPMENT.md#八第五步写-academic-skill-提示词)。

启用新版后端（先在旧 Java 终端按 Ctrl+C 停止）：

```powershell
cd "D:\agent开发准备\简历项目\student-service-platform\backend"
.\start-local.cmd -JarPath ".\target\student-service-platform-0.0.1-SNAPSHOT.jar"
```

保留数据库及新版 `8002` 服务。新接口为 `POST /api/student/agent/chat`，使用学生 JWT，请求示例：

```json
{"message":"帮我分析一下最近的学习情况，有哪些课程需要重点关注？"}
```

2026-10-03 实测修复：`P/通过` 课程不参与学期 GPA，但仍保留已获学分；成绩单春季 GPA 已由 3.5185 修正为 3.72。启用这次修复需重启 `8002` 和新版 Java（加载统计解释提示词），无需重传成绩单或重建数据库。详情见 [学业统计修复记录](./backend/docs/academic-warning.md#2026-10-03-实际-agent-对话p-课程导致-gpa-趋势错误)。

小程序重新编译后，登录并点击服务大厅四个功能下方的“学业智能助手”即可提问和追问。页面有五个快捷问题、“新对话”和成绩单管理入口；当前对话只保存在页面内存，关闭页面或切换账号后清空。仅新增这个小程序入口时无需重启 Java；应用上文的 GPA 统计修复时，按要求重启 `8002` 和新版 Java。真机需要重新生成预览，连接地址仍按前面的局域网配置。

返回 `data.answer` 和工具调用状态。查询他人成绩会返回 `REFUSED`，未支持业务返回 `OUT_OF_SCOPE`，需要明确问题返回 `NEEDS_CLARIFICATION`，不再统一显示数据不足。本轮拒绝提示修复需要重启已打包的新版 Java，并重新编译小程序；Python 和数据库无需因此重启。追问时可携带仅含 `user/assistant` 的 `history`；不能传学号或系统／工具消息。完整 PowerShell 调用、限制和失败处理见 [第四步说明](./AGENT_DEVELOPMENT.md#七第四步接入-deepseek让模型选择工具)。

## 测试与构建

RAG 检索基准：

```powershell
cd python-services
.\.venv\Scripts\python.exe .\evaluation\rag_eval.py `
  --label baseline_retrieval_2026-09-27 `
  --output .\evaluation\results\baseline_retrieval_2026-09-27.json
```

当前16题检索基准、指标定义和逐题发现见 [RAG 检索基准报告](./python-services/evaluation/BASELINE_REPORT.md)。

Java 后端：

```powershell
cd backend
.\mvnw.cmd test
```

统一知识库和小程序证明下载回归测试：

```powershell
cd python-services
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

```powershell
cd "D:\agent开发准备\简历项目\student-service-platform"
node --test miniprogram/tests/agent.test.js miniprogram/tests/download.test.js miniprogram/tests/qa.test.js miniprogram/tests/academic.test.js miniprogram/tests/transcript.test.js
```

以上测试使用本地测试数据库、测试向量和微信 API 模拟，不调用付费模型，也不修改运行中的知识库。

成绩单解析实测、核心课程范围、开课学期判定与报告字段见 [成绩单解析与学业预警说明](./backend/docs/academic-warning.md)。

成绩单页面首次点击“上传成绩单”，保存后点击“课程分析”；之后重新进入页面会读取本人已保存的成绩单，可直接分析，也可点击“更新成绩单”。原 PDF 保存在 `backend/uploads`，文件归属与分析结果存放在 MySQL，继续使用现有数据库即可。

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

- 当前关系数据库基线为 MySQL 8；历史 Kingbase 初始化脚本仅用于迁移核对，新环境使用 `init-mysql.sql`。
- Web 管理端仍保留原 Geeker-Admin 的 MIT License 和署名。
- 当前已完成本地业务联调；实际模型响应速度和真机文档预览仍受网络、模型服务及微信客户端环境影响。
- 生产部署前还应补充登录限流、依赖漏洞扫描，以及基于独立 MySQL 测试库的接口集成测试。

## 许可证与第三方组件

Web 管理端基于 Geeker-Admin，相关许可证位于 [`web-admin/LICENSE`](./web-admin/LICENSE)。其他模块和数据文件在公开发布前应补充顶层许可证，并确认数据库驱动、字体及政策文档的再分发权限。
