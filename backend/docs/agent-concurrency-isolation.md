# Agent 多用户并发隔离回归

本次只增加测试与开发记录，未修改生产 Service、接口、表结构或前端，也未添加依赖或基础设施。

## 测试怎样接近真实链路

`AgentConcurrencyIsolationTest` 使用同一个 Spring Web ApplicationContext。SingleAgentService、ConversationChatService、AcademicAnalysisService、AcademicToolExecutor 和四个 Tool 都是共享的默认 singleton 实例；Spy 调用真实方法，不替换核心业务结果。

HTTP 请求经过真实 JwtUtil / AuthFilter、Controller、会话 Service 和 JDBC Repository。测试使用 H2 MySQL 模式，并执行既有会话增量 SQL；两个学生的 t_student.id 分别是 101/102，t_user.id 则是 11/12，避免把用户 ID 混当学生 ID。

仅模拟成绩单文件提供器、培养方案 Service、Python 分析和 DeepSeek。两名学生的学号、文件 ID、文件路径、专业、年级、培养方案版本、课程、成绩、GPA、学分和会话历史都有不同标记。Python Mock 会核对实际传入的身份、文件及归一化培养方案；模型 Mock 会核对实际收到的 Tool JSON 和历史，正常答案从实际 DTO 生成。

## 隔离矩阵

| 测试 | 验证的规则 | 结果 |
| --- | --- | --- |
| 8 个工作线程、80 次交错 JWT 聊天 | A/B 的身份、历史、4 个 Tool DTO、响应和 trace 各归本人 | 通过 |
| B 使用 A 的会话 ID，普通与流式接口 | 返回 404；Agent、LLM、Tool、Python 均未调用 | 通过 |
| 同一请求并发调用四个 Tool，再顺序调用四个 Tool | 只运行一次 Python；读取同一个 Snapshot | 通过 |
| 同一学生下一次 HTTP 请求 | GPA 从 2.8 改成 3.1 后读取新数据，原 Snapshot 保持 2.8 | 通过 |
| 历史 GPA=4.0，Tool=2.8，模拟模型仍回答 4.0 | 事实来自本轮 DTO，最终回答不采用 4.0 | 通过 |
| A/B 同时接收 NDJSON 流 | 概览 GPA/学分、近期课程/分数、趋势 GPA 与最终会话 ID 归本人 | 通过 |
| A 的 Python 失败、B 成功 | A 同请求缓存失败且不重试，B 正常分析，彼此不污染 | 通过 |

80 次压力场景使用 8 个独立会话，每个会话顺序发送 10 次。这样检查跨用户和跨请求隔离，不把同一会话的并发 409 冲突误算成串数据。每一轮用 barrier 确保交错开始，并在 Python 边界同时到达：观测到 8 个分析调用同时在途，证明测试确实触发并发，没有通过整体串行化 Service 过关。

断言包括 80 次 Python 调用、A/B 各 40 次、320 次 Tool 执行、160 次模型调用，以及 80 个不同的分析 Context / Snapshot 对象。每个请求复用相同的 Tool Call ID，仍能独立完成，说明 ID 去重集合不在单例字段里。成功问答与种子历史最终共保存 176 条消息。

流式场景单独并发两次，使用项目真实的有界异步执行器。证据字段直接核对工具 DTO，不只检查 HTTP 200 或文字是否像正确答案。普通查询还统一断言不调用 WarningRecordService，也不保存成绩单。

## 为什么当前结构能隔离

Service 单例仅保存依赖，不保存当前学生、当前会话、模型消息、Snapshot 或本轮结果。已经验证的身份从 JWT / owned conversation 进入每次新建的 AgentRequestContext，再通过方法参数传到业务层。

会话 SQL 带 id 和 student_id 条件，ownership 在准备 Agent 前验证。Conversation history 是本人会话的有限不可变副本；模型协议消息在当前方法内创建。身份不由问题、history 或 Tool 参数决定。

次数、deadline、Tool Call ID 集合、trace、Business Context 以及 lazy AcademicAnalysisReadContext 都属于当前请求。既有 synchronized 只保护一个请求内的快照加载，成功或失败只在这个对象内复用；不同请求持有不同锁与对象。没有新增整个 Service 的 synchronized。

最终全量后端测试：150 项，148 项通过，2 项真实模型测试默认跳过；本次新增 7 项全部通过。

## 运行和限制

在 backend 目录运行：

```powershell
.\mvnw.cmd -o "-Dtest=AgentConcurrencyIsolationTest" test
.\mvnw.cmd -o test
```

没有新 SQL，也不用为了本次测试重启服务。测试不连接运行中的 MySQL、不读取真实成绩单、不请求真实 Python 或 DeepSeek。

这些测试覆盖 Spring / JWT / 业务调用 / JDBC / 流式投影边界，不能替代真实 MySQL 事务验收、Python 服务内部并发验证、网络压力测试、真实模型选工具准确率或真机验收。80 次测试是回归场景，不是吞吐量基准。

历史数值冲突验证使用既有有限 GPA 校验，不代表任意自然语言事实都已得到形式验证。同请求并发四个 Tool 是测试驱动调用能力；实际 Agent 循环仍按既有顺序执行 Tool，没有顺便改成并行执行器。
