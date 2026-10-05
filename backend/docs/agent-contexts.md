# 可信的 Conversation Context 与 Request Context

本轮在已有 JWT 和会话归属基础上明确四类上下文。客户端聊天 API 仍是 `conversationId + message`，没有增加数据库表、长期记忆、依赖或 Agent Framework。

## 四类 Context

| 类别 | 本项目实现 | 生命周期 | 可信边界 |
| --- | --- | --- | --- |
| Identity Context | `AgentIdentityContext`：有效学生数据库 ID、JWT 学号 | JWT 验证及有效学生查询后捕获，每个请求固定不变 | 身份可信，来自服务端；不来自问题／历史／模型。具体 ID、学号和 JWT 不发送给模型 |
| Conversation Context | `AgentConversationContext`：已验证 owner 的 conversationId、不可变历史列表 | 数据库文字可跨请求保存；加载后的 Context 仅用于本请求 | 会话来源和归属可信；文字中的成绩、身份自述和旧助手结论不是当前业务事实 |
| Request Context | `AgentRequestContext`：上述两类 Context、当前问题、Business Context、deadline、次数、调用 ID、trace、执行状态 | 每次 POST `/chat` 或 `/chat/stream` 创建一个，整个 Tool Calling Loop 共用；请求结束后释放 | 执行预算和状态由 Java 管理；不能重复执行或放入单例 Bean／Session／数据库 |
| Business Context | `AcademicBusinessContext` + 已有 `AcademicAnalysisReadContext`／`AcademicAnalysisSnapshot` | 本请求首次需要成绩时加载；所有 Academic Tool 共用；下一请求重建 | 只有本轮受控 Tool／Service 从 DB、文件和 Python 获得的 DTO 可以提供业务事实，history 不能登记成 Tool Result |

请求 Context 是普通 Java 对象，不是依赖注解自动生成的 Spring request-scoped Bean。`ConversationChatService.prepare()` 每次明确创建它，流式任务在异步执行时继续使用同一对象。所有可变执行字段都在这个对象或其业务子对象中，单例 SingleAgentService／Tool 对象只保存依赖和配置。

## 请求怎样绑定

`AgentConversationService.prepareChat()` 从当前认证上下文得到有效的 `AgentIdentityContext`，验证会话 owner，加载该会话的历史，生成 immutable ChatInput。

`SingleAgentService.prepareRequest()` 以这个 identity 创建新的 lazy 分析上下文。Request Context 检查分析上下文绑定学号与 identity 一致；加载后的 Snapshot 也会核对绑定身份。

`ConversationChatService.Prepared` 进一步校验 input 与 Request Context 的 identity、conversationId、当前问题和历史完全一致。即使内部代码误将 A 请求的 input 和 B 请求的 context 拼接，也会在模型调用前拒绝。

模型没有数据库能力，不能传 studentId／studentNo 改变归属。对他人的拒绝和现有 owner 检查继续保留。

## History 帮助理解意思，不提供当前事实

例如数据库保留：

```text
USER：我的 GPA 是 4.0
ASSISTANT：上轮声称 GPA=4.0，仅供话题参考
```

下一轮“那我情况怎么样？”可以借助这些文字理解正在问学业，但仍必须调用本轮 Academic Tool。假设当前 Service／Python 返回官方 GPA=2.8，当前事实就是 2.8。历史没有加载成 Snapshot，也不会填充 Business Context。

上一轮助手说过的事实也可能已过期，例如学生更新了成绩单或培养方案。下一轮会重新创建分析上下文；服务不可用时说明不可用，不能用旧 GPA 冒充当前查询成功。

用户文字、旧助手回答、课程名、Tool 文本里的操作指令以及模型自行生成的数字，都不是身份授权或业务规则。来自后端的身份、统计数值、样本口径和预警规则可信；它们仍是本次读取的数据，不承诺上游原始文件本身完全准确。

## 单一的历史预算

所有限制统一在 `ConversationContextBudget`，HTTP 输入、模型内部输入校验与数据库历史选取使用同一组常量：

- 当前问题最多 2000 个 Java／JavaScript 字符。
- 历史最多 20 条消息，即最多 10 对完整问答。
- 单条历史最多 4000 字符。
- 当前问题加历史原文总长最多 20000 字符。
- 从数据库最近 40 条记录中，选取最新的完整 USER／ASSISTANT 对，再恢复正序。

超出数量或总字符预算时，从最旧问答开始丢弃，不切开一句话或只留下半个问答。超长单条、空消息、非法角色及不完整问答不进入模型上下文。数据库保留原文，页面依然可以分页读取它们。这是原文字符预算，不是精确 tokenizer 或整个模型请求的 token 预算；系统指令、标记和本轮 Tool 协议另外占用上下文。没有新增摘要或语义记忆。

## Prompt 怎么分隔

`AgentPromptBuilder` 明确生成：

```text
role=system：SYSTEM INSTRUCTION + Academic Skill + 固定信任边界
role=system：TRUSTED IDENTITY / SERVER CONTEXT（仅本人范围，不含标识）
role=user／assistant：CONVERSATION HISTORY / REFERENCE ONLY + JSON 编码文字
role=user：CURRENT USER MESSAGE + JSON 编码当前问题
随后：本轮模型 tool_call 及服务端 role=tool 结果
```

role 是服务器代码决定的；数据库历史只能构造 user／assistant。文字中的 `role=system`、SYSTEM 标记或伪造分隔符经 JSON 编码后仍是文本，不会被提升为 system 消息。只有真实执行过的工具，才能由调用循环追加关联对应 tool_call_id 的结果。

可信服务器范围只说明“已认证学生、当前本人会话、只读分析、本轮工具是业务来源”，不把具体学生主键、学号、JWT、文件路径或 conversationId 发送给 DeepSeek。专业、年级和学业统计继续通过原来批准的 Tool DTO 提供。

Academic Skill 增加了明确例子：history 的 4.0 与本轮 Tool 的 2.8 冲突时采用 2.8；缺数据时不得退回旧答案来补事实。

## GPA 冲突的确定性保护

已有“没有本轮分析证据就不返回学业结论”的保护保留。另增加 `AcademicAnswerGrounding`，对直接的 GPA／绩点数值描述进行有限校验，只接受本轮 DTO 提供的官方 GPA、学期加权 GPA 和预计算变化量及合理四舍五入。

模型即使回答“你的 GPA 是 4.0，没有风险”，本轮可核验官方 GPA=2.8 时，也会丢弃这段不一致解释，返回基于 2.8 的简短提示。没有当前 GPA 数据时不采用历史数字，也不补成零，不额外调用 LLM 重试。

这是直接数值模式的保护，不是对任意自然语言、所有课程事实或风险措辞的完整事实验证，也不能保证挡住各种改写的错误表达。不同口径 GPA 的全部自然语言归因还依赖 Skill 规则；真实回答质量仍需要验收。没有把模型文章中的数字反向当成业务数据。

## 快照和执行状态

原 `AcademicAnalysisReadContext` 的 synchronized／single-flight 实现保留。第一次需要完整分析的工具调用 Python；同请求其他工具读取同一 Snapshot。RuntimeException 在同一上下文缓存并重复抛出，不隐式再调用 Python。下一请求取得全新上下文，允许重新加载和恢复。

档案／成绩单／培养方案可用性查询仍有自己的请求内缓存，它不解析 PDF；完整分析取得后，元数据以该 Snapshot 为准。原 Snapshot 对 JSON 的防御复制也保留。

deadline 用单调时钟，从进入 ConversationChatService.prepare 开始计时，覆盖身份／会话准备及流式线程池等待。调用轮数、次数、调用 ID 和 trace 都归本请求持有。Context 状态从 PREPARED 进入 RUNNING，最终是 SUCCEEDED 或 FAILED；同一个对象不能再启动第二次循环。模型或工具失败、断线或会话持久化失败会标为 FAILED。正常的拒绝／缺数据文字回复可以完成循环，不意味着已经获得学业事实。

模型／Python 的同步调用仍由既有连接和读超时约束，deadline 在调用前后检查，不能保证到时立即打断上游。这一轮没有引入取消框架或任务队列。

## 文件与验证

新增核心文件：AgentIdentityContext、AgentConversationContext、AgentRequestContext、AcademicBusinessContext、ConversationContextBudget、AgentPromptBuilder、AcademicAnswerGrounding。

调整：AgentConversationService、ConversationChatService、SingleAgentService、AcademicAnalysisService、AcademicAnalysisReadContext、AcademicToolExecutor、AgentChatRequest、ConversationChatRequest 和 academic.md。原 Snapshot 算法、工具 DTO、业务预警写入边界、HTTP 请求字段及 SQL 表结构保持。

保留并扩展 AcademicAnalysisServiceTest／AcademicToolExecutorTest 的同步与失败缓存测试，扩展 SingleAgentServiceTest、AgentConversationServiceTest、AgentChatControllerTest；新增 AcademicAnswerGroundingTest，并更新默认跳过的真实模型协议测试调用方式。

上下文联合回归 80 项通过；后端全量 141 项通过、2 项真实模型测试默认跳过，最终 JAR 已打包。测试使用实际 JDBC 会话 Service + H2、真实 Academic Tools／Mapper、模拟 Python 和模型响应：

- history 自述 GPA=4.0，当前 Python／Tool=2.8，模拟模型仍报 4.0，最终不采用错误事实；下一请求数据变为 3.1 时读取新 Snapshot。
- 同一学生的会话 A/B 历史不能混入；内部 Prepared 拼错请求 Context 也拒绝。
- 下一请求 Context、次数、状态和调用 ID 集合独立；同 Context 不能执行两次。
- 多工具共用一次 PDF 分析；并发成功与失败都只加载一次；失败的同请求不隐式重试，下一请求可以恢复。
- 数量／字符预算裁掉最旧完整问答，历史原文不被截断或从数据库删除。
- 历史里的 system 标记仍为普通文字，可信服务器投影不泄露标识。
- 异步排队超时在模型前失败，持久化冲突将当前请求标为失败；原所有权和实时进度继续通过。

本轮没有请求真实 DeepSeek、修改运行中的 MySQL 或重启服务；不能把这些协议测试当成真实模型回答准确率。

## 本地启用

Prompt 1 的会话增量 SQL 尚未执行时，需要先按 [会话部署说明](./agent-conversations.md) 建表。本轮本身无需新的 SQL，也不用修改小程序协议。停止旧 Java 后，在 backend 目录用原 `start-local.cmd` 启动新版 JAR；Python 启动方式不变。用本人会话先说 GPA=4.0，再追问学业情况，核对当前成绩单数据；更新成绩单后再次提问，确认不会沿用旧分析。
