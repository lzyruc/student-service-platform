# Agent 服务端会话与学生归属

本文件记录 Conversation 持久化和多用户归属，复用 Spring JDBC、MySQL、JWT 和已有 Single Agent。没有新增依赖或 Agent Framework。后续四类 Context 已接入，见 [可信上下文说明](./agent-contexts.md)，聊天字段和本文件的 SQL 不变。

## 从现有版本升级

1. 在 MySQL Workbench 打开并执行 [增量 SQL](./sql/migrations/2026-10-04-agent-conversations.sql)。它只创建两张会话表，不清空已有学生、成绩单、政策和业务记录。本轮没有连接或修改你正在运行的 MySQL。
2. 停止旧 Java 服务，在 `backend` 目录运行原来的 `start-local.cmd`。新版 JAR 已打包为 `target/student-service-platform-0.0.1-SNAPSHOT.jar`，数据库和 JWT 配置沿用本地配置文件。
3. 微信开发者工具重新编译小程序；真机需要重新预览。8000／8002 服务启动方式不变。
4. 用学生 A 提问，退出页面再进入，确认历史恢复；切换学生 B 后列表只能出现 B 的会话。

**现有数据库不要重跑 `init-mysql.sql`，它会删除并重建数据库。** 该初始化文件也已包含新表，供全新安装使用，共 14 张表。新表没有自动迁移执行器，需要手动执行上述增量脚本。

## 数据模型

| 表 | 字段与关系 |
| --- | --- |
| `agent_conversation` | 自增 `id`、`student_id`、`title`、`created_at`、`updated_at`；owner 外键关联 `t_student.id` |
| `agent_conversation_message` | 自增 `id`、`conversation_id`、`role`、`content`、`created_at`；外键关联会话 |

消息角色仅允许大写 `USER`／`ASSISTANT`。Repository 写入检查、MySQL 的 CHECK 和 ASCII 大小写敏感比较共同限制角色；构建模型上下文时再次白名单过滤。不会保存 system／tool 消息、完整工具 DTO、内部思考或请求内 Snapshot。

会话删除级联删除消息。学生删除也级联删除会话，重新创建同学号学生不会恢复已删除学生的会话。索引分别支持“当前学生的最近会话”和“指定会话按消息 ID 分页”。每对消息写入同一时间，按递增 ID 恢复追加顺序，避免时间相同导致问答交换位置。

## HTTP API

以下接口统一要求学生 JWT，沿用 `Result` 的 `code/message/data`。

| 方法 | 地址 | 输入 | 返回的 data |
| --- | --- | --- | --- |
| POST | `/api/student/agent/conversations` | `{}` 或 `{"title":"自定标题"}` | 会话摘要：id、title、createdAt、updatedAt |
| GET | `/api/student/agent/conversations` | `page=1&pageSize=20`，pageSize 1～100 | records、total、page、pageSize；仅本人会话，最近更新优先 |
| GET | `/api/student/agent/conversations/{id}` | `limit=100`，可选 `beforeMessageId`；limit 1～100 | conversation、messages、hasMore、nextBeforeMessageId；本页消息正序 |
| PATCH | `/api/student/agent/conversations/{id}` | `{"title":"学期复盘"}` | 更新后的会话摘要 |
| DELETE | `/api/student/agent/conversations/{id}` | 无业务参数 | null；同时删除消息 |
| POST | `/api/student/agent/chat` | conversationId、message | conversationId、answer、status、toolRounds、toolCalls |
| POST | `/api/student/agent/chat/stream` | 同上 | 保留 NDJSON progress／evidence／result／error 事件；result 同聊天输出 |

先创建会话，再聊天：

```json
{"conversationId":123,"message":"我的成绩趋势怎么样？"}
```

旧 `history` 输入已经取消；传 history、studentId、studentNo、system／tool 字段会返回 400。message 必须是非空字符串，最多 2000 字符；conversationId 必须是正整数。标题默认“新会话”，第一轮成功后用用户问题前 100 个 Unicode 字符填充，不额外请求 LLM。主动命名的标题不会被覆盖。

历史分页先取得最近的消息，再用 `nextBeforeMessageId` 加载更早内容；返回每页都按追加顺序排列。小程序首次取最近 40 条，提供“加载更早的消息”。会话列表每页 20 条，支持加载更多。

## Ownership 如何保证

JWT subject 在这个项目中是学号字符串，不是数据库主键。`currentStudentId()` 读取已验证的 `AuthContext`，确认角色是 student，再关联有效 `t_user`／`t_student` 得到 **t_student.id**。身份参数不来自客户端或模型。

`getOwnedConversation()` 和内部带锁版本共用同一个 ownership 检查：查询必须带 `id = ? AND student_id = ?`，返回后还会核对 owner。读取、改名、删除、准备聊天和保存回答都走这一检查，Controller 不自行决定 owner。列表也在 SQL 中按当前学生筛选。

不存在会话和其他学生的会话统一返回 404“会话不存在或无权访问”，不暴露会话是否存在。匿名 401；管理员、停用账号、无对应学生档案的身份返回 403。即使管理员在通用 Filter 中获准访问路由，业务 Service 也不会绕过学生边界。

流式接口在返回 200 和建立输出流之前完成身份及归属检查。越权请求不会进入 Agent／Python／模型。

## 一次聊天怎样执行

```text
JWT → 有效学生 → 加锁验证本人会话
    → 从数据库加载最近的完整问答，记录最后一条消息 ID
    → 结束短事务
    → 创建本次 AgentRequestContext（含 lazy AcademicAnalysisReadContext），执行已有 Tool Calling Loop
    → 取得有效回答
    → 新短事务重新检查本人会话及最后消息 ID
    → 原子写入 USER + ASSISTANT，更新标题和时间
    → 返回结果
```

模型、Python 和进度输出期间不持有数据库事务。多个 Academic Tool 仍共享本轮请求唯一的 Snapshot；下一轮 HTTP 请求仍创建新 Context。数据库对话文字和请求内分析数据是两个独立对象。

保存时锁定会话行，并比较准备阶段的最后消息 ID。若另一个请求已经提交新问答，当前请求返回 409，避免将基于旧历史的回答追加到新对话后。短事务使用 READ COMMITTED，并使用当前读检查最后消息，减少 MySQL 的旧快照及不同会话间的间隙锁干扰。这里没有新增分布式锁或任务基础设施。

模型超时、协议错误或执行失败时，两条消息都不保存。写 ASSISTANT 失败时，USER 一并回滚。正常的拒绝、澄清、能力说明和数据不足说明是有效文字回复，保存成普通问答，不代表成功完成学业分析。正式预警记录仍不会因 Agent 查询而新增。

若流已经打开，执行错误通过 `error` 事件传递状态；网络恰好在数据库提交后断开时，问答可能已经保存，但客户端没有收到最后结果。界面提示刷新会话确认，不能承诺断线一定没有保存。

## 代码在哪里

| 层 | 新增／调整文件 |
| --- | --- |
| 实体 | [AgentConversation](../src/main/java/com/college/student_service_platform/entity/AgentConversation.java)、[AgentConversationMessage](../src/main/java/com/college/student_service_platform/entity/AgentConversationMessage.java) |
| DTO 与输入校验 | [AgentConversationDtos](../src/main/java/com/college/student_service_platform/dto/AgentConversationDtos.java)、[ConversationChatRequest](../src/main/java/com/college/student_service_platform/agent/ConversationChatRequest.java)、模型内部的 AgentChatRequest |
| JDBC／手写 Mapper | [AgentConversationRepository](../src/main/java/com/college/student_service_platform/repository/AgentConversationRepository.java) |
| 归属与事务 | [AgentConversationService](../src/main/java/com/college/student_service_platform/service/AgentConversationService.java) |
| Agent 接入适配 | [ConversationChatService](../src/main/java/com/college/student_service_platform/service/ConversationChatService.java) |
| HTTP 与鉴权 | 新增 AgentConversationController；调整 AgentChatController 和 AuthFilter |
| 小程序 | pages/agent/agent.js、agent.wxml、agent.wxss；utils/agent-chat.js 移除客户端 history 构建 |
| 表结构 | sql/init-mysql.sql、sql/migrations/2026-10-04-agent-conversations.sql |
| 回归 | AgentConversationServiceTest、ConversationTestSupport、AgentChatControllerTest、AcademicToolExecutorTest、小程序 agent.test.js |
| 说明 | README.md、AGENT_DEVELOPMENT.md、本文件 |

保持现有 JDBC 风格，没有引入 MyBatis Mapper 或修改核心工具算法。

## 验证与限制

会话持久化完成时后端完整回归 125 项通过，2 项真实模型测试默认跳过；Maven 离线打包成功。小程序 42 项通过，模板结构和 15 个事件绑定检查通过。后端测试使用 H2 的 MySQL 模式执行实际增量 DDL，移除仅属于 MySQL 的字符集、排序规则和表引擎选项；模型与微信 API 使用 Mock。

覆盖：A 创建后 B 不能读／聊天／改名／删除；管理员无旁路；本人删除成功；不存在会话返回 404；相同时间下消息顺序稳定；非法 role 不进入模型上下文；客户端 history 和身份字段被拒绝；失败不落库；第二条插入失败整对回滚；相同会话并发只提交一个问答；不同学生并发互不串写；服务重新创建后历史仍可读；小程序重新进入、切换身份、加载旧消息和实时进度。

以下限制保留：

- 本轮没有操作真实 MySQL，也没有调用真实 DeepSeek；MySQL 原生 DDL／事务和微信真机仍需按升级步骤验收。
- 模型上下文只选最近 40 条记录中的最多 10 对完整问答；单条超过 4000 字符的整对不加入上下文，当前问题加历史总长不超过 20000 字符。数据库保留全文，页面支持分页。没有长期记忆或历史摘要。
- 数据库只保存两类普通文本。执行过程、工具卡片和 status 不持久化，重进页面可恢复文字，不能恢复旧请求的过程卡片。
- 并发请求可能都已消耗上游模型调用，提交时只有一个被接受。没有自动重试、幂等请求键或可靠投递机制；断线后先刷新确认保存状态。
- 在页面里点“新对话”只是切换到新会话，不删除历史。过去只在页面内存中的旧对话无法补迁到数据库。
