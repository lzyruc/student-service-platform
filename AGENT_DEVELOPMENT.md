# Agent 开发记录与复盘

更新日期：2026-10-04。

这份文档按实际开发思路记录：为什么要做 Agent、每一步发现了什么问题、为什么这样改、怎样验证，以及哪些部分还没完成。以后复盘和准备面试时，可以沿着这个过程讲，不必先背类名和接口。

项目启动方式见同级 [README](./README.md)。成绩单解析、培养方案匹配和原有预警规则见 [学业分析说明](./backend/docs/academic-warning.md)。

**当前协议已升级为服务端会话：** 小程序仅发送 `conversationId` 和 `message`，历史由数据库读取。前面各步骤保留当时的做法以便复盘，最新会话实现见 [第十四节](#十四把页面里的对话升级为服务端会话) 和 [会话接口与升级说明](./backend/docs/agent-conversations.md)；四类上下文与可信数据边界见 [第十五节](#十五对话能帮助理解但不能代替当前业务事实)。现有环境需要执行会话增量建表 SQL，再启动新版 Java。多用户并发隔离回归见 [第十六节](#十六用并发测试验证不同学生不会串数据)。

## 一、我们是从什么基础上开始的

这个项目原来已经有学业分析功能：学生上传成绩单，Java 后端根据学生的专业和年级找到培养方案，Python 解析成绩单并比较课程，最后返回分析报告。

开发 Agent 前，我们已经让成绩单支持保存和复用。学生上传一次后，下一次进入小程序可以直接点击“课程分析”，不必重复选文件。

因此，学业 Agent 的目标是在已有业务能力上增加自然语言交互。学生可以说“帮我看看最近哪些课需要注意”，系统根据问题选择合适的查询和统计，再解释结果。

第一版计划支持：

| 学生怎么问 | 系统需要做什么 |
| --- | --- |
| 分析我的学习情况 | 汇总学分、绩点、未通过课程和核心课程完成情况 |
| 我有没有挂科风险 | 解释已有未通过记录和项目预警结果 |
| 最近哪些课程表现不好 | 找到最近一个有有效成绩的学期，筛选未通过和低分课程 |
| 我的成绩趋势怎么样 | 按学期比较成绩统计，不凭感觉判断“进步了” |
| 哪些课程需要重点关注 | 分清挂科、低分和需要核实的课程记录 |

目前没有实时作业、出勤和在读成绩，所以“挂科风险”只能解释现有证据，不能预测尚未考试课程的挂科概率。

## 二、先确定分工，再决定怎么写

我们先确定了一个原则：**模型理解问题和解释结果，程序负责身份、规则和数字。**

例如，模型可以理解“最近学习情况”是在询问近期成绩，但实际哪个学期算最近、多少分算低分、GPA 怎么计算，都由固定代码处理。同一份数据不会因为换一种提问方式，就得到另一个预警等级。

几个常见名字可以这样理解：

| 名字 | 在本项目里是什么 |
| --- | --- |
| Agent | 接收问题，选择工具，把工具结果组织成回答的程序 |
| Skill | 某个业务方向的说明和工具集合，例如学业分析 |
| Tool | Agent 可以调用的一项受控业务能力，例如“查询近期课程表现” |
| Controller | 接收前端 HTTP 请求的入口 |
| Service | 真正执行业务的地方，例如选培养方案、调用分析、保存结果 |
| DTO | 专门约定输入或输出字段的数据对象 |
| Snapshot | 当前一次 Agent 请求内，多个 Tool 共用的一份临时分析结果 |
| JWT | 登录后用来验证身份的凭证，身份由后端验证 |

这里的 Academic Skill 是项目内的业务模块，不是另一个 Agent，也不是 Codex 的本机 Skill。

第一版使用一个 Agent，只接 Academic Skill。以后增加 Policy Skill、Certificate Skill，是继续给同一个 Agent 注册业务能力。Agent 放在 Spring 后端，Python 继续负责解析和算法。

选择这个安排，是因为 Java 已经管理学生身份、文件归属和业务数据。Tool 可以直接调用受控 Service，继续使用原有权限边界。

我们暂时没有加入 Multi-Agent、MCP、Redis、Kafka、长期 Memory 或复杂 Planner。当前最需要解决的是业务能否正确调用、结果是否可信，现有服务足以支撑第一版。

## 三、现在做到哪里了

| 开发步骤 | 状态 | 解决的问题 |
| --- | --- | --- |
| 1. 提取业务 Service | 已完成 | 让业务能被复用，并区分正式分析和普通查询 |
| 2. 补充确定性统计 | 已完成 | 把“最近”“低分”“趋势”变成明确的程序规则 |
| 3. 实现 4 个 Tool 和专用 DTO | 已完成 | 给 Agent 提供稳定、受控、必要的数据 |
| 4. 接入模型和工具调用循环 | 已完成 | 让模型选工具，由程序校验并执行 |
| 5. 整理 Skill 说明和回答规则 | 已完成 | 独立 Markdown 提示词，接入系统消息，明确工具选择、证据和回答口径 |
| 6. 接入小程序并完整验收 | 页面已接入，真实验收待完成 | 首页入口和学业聊天已实现，继续用真实成绩单验证回答与真机体验 |
| 7. 展示真实执行过程 | 已完成，真机待验收 | 流式进度和来自 Tool DTO 的结果卡片 |
| 8. 服务端会话与多用户归属 | 已完成，真实 MySQL／微信待验收 | 两张会话表、JWT ownership、失败回滚和小程序历史管理 |
| 9. 四类上下文与可信数据 | 已完成，真实模型质量待验收 | 请求独立状态、统一历史预算、提示词信任分隔、快照失败缓存 |
| 10. 多用户并发隔离回归 | 已完成，真实服务压力待验收 | 80 次交错聊天、越权前置拒绝、快照复用与刷新、流式证据和失败隔离 |

**目前已完成业务基础、统计能力、4 个 Tool、专用 DTO、DeepSeek 客户端、调用循环、学生 HTTP 聊天入口和独立 Academic Skill 提示词。** 后端联合测试和真实模型协议测试已通过；小程序首页入口和聊天页面现已接入，真实学生完整对话验收仍待完成。

## 四、第一步：先拆业务，再给 Agent 用

### 为什么第一步不是直接接模型

原来的 `AcademicWarningProxyController` 里放了较多业务：查学生档案、找培养方案、转换培养方案、调用 Python、保存成绩单和预警记录。

如果 Agent 直接调用这个流程，会把“普通问一句话”和“正式执行一次课程分析”混在一起。因此，我们先把业务提取到 `AcademicAnalysisService`，让原接口和未来的 Tool 都能复用。

原 Controller 保留接收请求、解析身份和返回响应的职责。原来的接口地址、响应格式和分析算法保持不变。

### 检查代码时发现了两个重要问题

**第一个问题：分析接口会重新解析 PDF。**

`AcademicWarningClient` 每调用一次，就把 PDF 发给 Python。Python 会重新解析，并不是自动读取之前的完整解析缓存。

假设一次 Agent 请求的工具调用循环里用了“综合分析”“近期表现”“成绩趋势”三个 Tool，如果它们各自调用分析接口，就可能对同一份 PDF 重复解析。

**第二个问题：正式分析流程会写预警记录。**

原来的 Controller 在 Python 返回后调用 `WarningRecordService.saveFromPythonResponse()`。如果直接给 Agent 复用，学生只是追问一下，也会新增一条正式预警记录。

我们没有把这两个现象当成同一个问题：前者是重复工作，后者是业务写入行为。需要分别处理。

### 实际怎么改

我们把入口明确分成两类：

| 用户动作／用途 | Service 入口 | 是否保存正式记录 |
| --- | --- | --- |
| 上传成绩单并正式分析 | `analyzeUploadedAndSave(studentNo, file)` | 保存文件和预警记录 |
| 点击原来的“课程分析” | `analyzeSavedAndSave(studentNo)` | 复用已有文件，保存预警记录 |
| 未来 Agent 的普通查询 | `createReadContext(request)` → `getSnapshot()` | 不保存文件、不新增预警记录 |

正式分析入口里的学号仍由现有后端认证流程确定；未来 Tool 不接收模型传入的学号。

为了避免一次 Agent 请求里反复解析，我们又增加了两个对象：

- `AcademicAnalysisReadContext`：当前一次 Agent HTTP 请求的临时分析上下文。
- `AcademicAnalysisSnapshot`：该请求内这次分析的课程、完整报告和文件／培养方案来源。

每次 Agent HTTP 请求只创建一个上下文，该请求整个 Tool Calling Loop 内的所有 Academic Tool 共用它。第一个需要完整成绩数据的 Tool 才加载结果，其余 Tool 继续读同一份结果。下一轮追问即使属于同一个 Conversation，也会发起新 HTTP 请求并创建新上下文。

```java
var context = academicAnalysisService.createReadContext(request);

// 模拟同一次 Agent 请求的 Tool Calling Loop 内的两个 Tool：
var assessment = context.getSnapshot();
var recent = context.getSnapshot();
// 返回同一个快照，只调用一次 Python。
```

并发读取也只加载一次；本次加载失败后，其他 Tool 不会自动再调用一遍 Python。读取快照中的 JSON 时会给出副本，避免某个 Tool 筛选或修改数据后，影响另一个 Tool。

上下文从后端已经验证的身份里取得学号，只允许学生查询本人。模型和聊天文本都不能指定“改查另一个学生”。

### 为什么暂时不直接读历史预警记录

检查后发现，`t_warning_record` 没有完整保存报告的所有字段。例如完整的数据说明不能直接从任意最新历史记录里恢复出来。而且历史记录对应的文件或培养方案，也可能不是当前版本。

第一版因此使用本次生成的完整结果，不把不完整的旧记录当作当前报告。

这里保留了一个明确的取舍：**同一次 Agent 请求／Tool Calling Loop 内共享一个上下文和 Snapshot，最多解析一次 PDF；下一轮聊天请求仍会重新创建 Context，首次需要完整成绩数据时重新解析。** 只查背景或参数非法而没有加载快照时，解析次数为零。当前没有跨请求缓存。

两种“上下文”的职责和生命周期明确分开：

| 对象／概念 | 保存什么 | 生命周期 |
| --- | --- | --- |
| Conversation／对话上下文 | 对话消息及会话信息 | 已在第十四节独立持久化到 MySQL，可跨多个 HTTP 请求；不保存分析快照 |
| `AcademicAnalysisReadContext`（已实现） | 本请求绑定的可信身份、惰性加载器、分析快照和本请求加载失败 | 只用于一次 Agent HTTP 请求及其内部工具调用循环 |

`AcademicAnalysisReadContext` 是普通 Java 对象，不是自动管理生命周期的 Spring 请求作用域 Bean。`beginRequest()` 每调用一次都会创建新对象；后续 `SingleAgentService` 应将它保存在方法局部变量中，传给整个调用循环，请求结束后释放引用，之后由 Java 垃圾回收。不能把它存入单例字段、HTTP Session、Conversation 对象或会话持久化数据。

第三步先实现上下文工厂及工具共享机制；第四步的 `SingleAgentService.chat()` 已把上下文保存在方法局部变量中，整个循环复用它。未来保存对话消息，不会自动变成成绩分析缓存；若以后需要跨请求复用分析，要另行设计用户隔离和文件／培养方案版本校验。

“只读”指不改变业务文件和数据库记录。Python 仍会创建用于解析的临时 PDF，并在请求结束时删除。

### 怎么确认第一步没有破坏原功能

第一步的 27 项 Java 回归测试全部通过，重点检查：

- 原有正式分析仍能保存预警记录。
- 只读查询不会增加 `t_file` 和 `t_warning_record`。
- 同请求重复或并发读取，只调用一次 Python。
- 文件不可用、培养方案缺失、分析失败时返回错误，不生成空记录。
- 学生身份不能被请求中的其他学号替换。
- 一个 Tool 修改拿到的 JSON，不会污染其他 Tool 的证据。

这些测试使用 Mock、临时文件和 H2 内存数据库，没有操作运行中的 MySQL 或调用付费模型。

## 五、第二步：先把“学习情况”变成可计算的结果

### 为什么已经有预警算法，还要加统计

原算法擅长判断未通过课程、核心课程缺口和预警等级。但“最近哪些课不好”“比上学期怎么样”还需要按学期筛选和比较。

我们保留原风险算法，新增 `academic_statistics.py`，让它读取同一次解析得到的课程和报告。这样模型以后拿到的是已经算好的结果，不需要自己做算术或临时发明规则。

数据流是：

```text
解析一次 PDF
    ↓
课程数据 + 原风险报告
    ↓
Python 计算各学期统计
    ↓
放入同一份分析快照
    ↓
Java 按请求选择近期表现或趋势窗口
```

### “最近”到底按什么算

我们选择成绩单中最近一个具有可判定最终成绩的学期，而不是根据今天的日期、文件上传日期或账号年级猜测。

例如，现在已经进入新学期，但成绩单只有上一学期的成绩，就只能分析已有成绩。不能把尚未出分的新学期当成已经完成。

程序会先规范学期名称，再按学年和学期排序。空白、全半角和破折号差异不会影响顺序。相同学年的顺序固定为秋季、春季、国际小学期、夏季／暑期，夏季与暑期归为同一项。

未知学期、无效学年，以及全部待出分的学期，会保留说明，不用于最新学期选择。`previous` 指上一有效成绩学期；如果中间缺了一学期的数据，不会自动补出一份。

### “低分”和“挂科”为什么分开

第一版低分关注固定为 `60 ≤ 最终成绩 < 70`。这只是项目里的学习关注标准，不改变原预警等级。

例如，65 分已经通过，但可以提示重点巩固；59 分则属于未通过。使用的是最终成绩，不是某一次期末考试的分数。如果期末 59 分、最终成绩 77 分，应按最终成绩判断通过。

阈值比较使用未舍入的数值，避免先把 69.99999 四舍五入成 70 后漏掉。关注顺序固定为：当前未通过、失败记录需要核实、低分、成绩无法判定。

历史挂科但已存在通过记录的课程，保留“这个学期曾经未通过”的事实，但不重复当作当前待重修课程。原算法仍负责确定目前的预警结果。

### 统计时哪些值不能随便处理

百分制平均分只使用有效的 `0～100` 数值最终成绩。`A`、`P`、“通过”等仍按原规则判断是否通过，不凭空换算成百分制分数。

学期 GPA 按学分加权：

```text
学期 GPA = Σ（有效单科绩点 × 学分）÷ Σ（对应有效学分）
```

例如，一门 3 学分课程的绩点是 3，另一门 1 学分课程的绩点是 4，加权 GPA 为 `(3×3 + 1×4) ÷ 4 = 3.25`，不是直接平均得到的 3.5。

缺失绩点不填成零；真正识别到的零绩点则正常参与计算。我们也返回样本数量和覆盖率，说明到底用了多少课程，避免只识别了部分数据却声称分析了全部成绩。

官方整体 GPA 同样需要区分“没读出来”和“真的为零”。解析器增加了 `official_gpa_available` 标记；原接口保留兼容字段，第三步的 Assessment Tool 在不可用时返回空值和说明。

每学期修读学分和未通过数量描述该学期的记录，可能包含历史重修事实；它们不能替代整体去重后的已获学分和当前预警等级。

### 趋势怎么计算，哪些结论不能下

Python 按时间排列有效学期，计算相邻有效学期的 GPA、数值平均分变化量及方向。模型负责解释这些结果，不再计算差值。

后端可以选择最近 `2～8` 个有效学期；少于两个学期时，明确返回数据不足。即使有两个学期，缺少可比较的 GPA 时，也会单独说明 GPA 趋势不能判断。

“这一学期的统计值上升”不等于“学习能力一定提高”。课程难度、课程构成和数据覆盖可能不同，因此只能描述现有样本的变化，不能据此预测挂科。

### 怎么接到现有业务里

现有 Python 分析接口在原 `data.courses` 和 `data.report` 之外，增加 `data.statistics`，统计版本为 `schema_version=1`。

Java 的 `AcademicStatisticsService` 提供：

| 方法 | 做什么 |
| --- | --- |
| `recentPerformance(context, semester, limit)` | 选择最新／上一有效学期，返回近期关注；关注条数为 1～10 |
| `trend(context, lastSemesters)` | 选择最近 2～8 个有效学期，返回已有统计和比较结果 |

这两个方法只读同一个快照，不再调用 Python，也不写预警记录。第二步先完成 Service；第三步再包装成 Tool，实际模型调用在第四步接入。

### 怎么确认第二步算对了

第二步完成后，Python 学业回归 28 项、Java 学业回归 34 项全部通过。

测试重点是学期乱序、未知学期、未出分、低分边界、零绩点与缺失值、等级成绩、重修、年级差异和趋势数据不足。还验证了原 API 保留原风险报告，以及近期表现和趋势共同读取快照时，仍只调用一次 Python、没有正式预警写入。

测试数量是验证记录，不代表模型回答准确率，也没有做性能提升百分比的测量。

## 六、第三步：把已有能力包装成 Tool，先独立验证

### 为什么 Tool 和 DTO 要一起做

业务 Service 已经能给出结果，但它的原始返回内容比较多：完整课程、原 Python JSON、学号、文件 ID、文件名和培养方案 ID。模型回答问题并不需要知道所有内部细节。

所以这一步做两件事：Tool 约定“能做哪件业务”，DTO 约定“允许传什么、能看到什么”。以后数据库字段或 Python 返回结构变化，只需要调整业务层和转换层，尽量保持模型看到的 Tool 契约稳定。

DTO 使用 Java 17 的 `record`，字段名称和类型固定。例如近期表现输入是 `RecentInput(String semester, int limit)`，趋势输入是 `TrendInput(int lastSemesters)`。Tool 不接收整张数据库实体，也不直接返回原始 JSON。

### 实际实现的 4 个 Tool

| Tool／实现类 | 输入 DTO 与允许的输入 | 输出 DTO／主要信息 | 复用哪里 |
| --- | --- | --- | --- |
| `get_academic_context`／`GetAcademicContextTool` | `EmptyInput`；`{}` | `ContextOutput`：档案是否完整、专业、年级、成绩单是否存在／可用、上传时间、培养方案是否匹配／版本／更新时间、下一步操作 | `AcademicAnalysisService` 的受控档案查询、`StudentTranscriptService.getCurrent()`、`TrainingPlanService.getLatest()` |
| `get_academic_assessment`／`GetAcademicAssessmentTool` | `EmptyInput`；`{}` | `AssessmentOutput`：整体学业报告、原预警等级和数据说明 | `context.getSnapshot().report()`，继续使用原 Python 风险算法 |
| `get_recent_course_performance`／`GetRecentCoursePerformanceTool` | `RecentInput`；`semester=latest/previous`、`limit=1～10`；不传时默认 latest、5 | `RecentOutput`：实际学期、学期统计、限定数量的关注课程、关注总数、是否截断、统计规则和数据说明 | `AcademicStatisticsService.recentPerformance()` |
| `get_academic_trend`／`GetAcademicTrendTool` | `TrendInput`；`lastSemesters=2～8`；不传时默认 4 | `TrendOutput`：按时间排列的学期统计、程序已算好的变化量和方向、比较数量、数据不足状态 | `AcademicStatisticsService.trend()` |

无参数也需要传合法的空对象 `{}`，不是 `null` 或数组。参数缺省才使用默认值；显式传 `null` 不会被偷偷替换为默认值。

### 输出 DTO 到底保留了哪些信息

**综合分析**保留已获学分、官方整体 GPA 及可用标记、原预警等级、成绩单完成学期、问题课程数和已完成核心课程数。`coreCourseCount` 沿用原报告含义，是已完成核心课程数，不是培养方案所有核心课程的总数。

课程分成独立字段：当前未通过课程及最终成绩、已完成核心课程、已到开课学期但未见通过记录的核心课程、未来开课的核心课程、无法判断开课进度的核心课程，以及最终成绩未知的课程。还保留原课程建议和分析说明，补充“缺口需核实”的说明，防止模型把未见记录解释成必须补修。

官方 GPA 没读出来时，`officialGpaAvailable=false`、`officialGpa=null`；真正读到零则是 `true`、`0`。缺少必要报告字段时返回分析不可用，不把不完整报告包装成一份“正常、没有问题”的结果。

**近期表现**的每门关注课程保留：`name`、最终成绩文本 `score`、数值成绩 `numericScore`、`credit`、成绩单页码 `page`、是否通过 `passed`、当前未通过状态 `currentFailureStatus` 和关注原因 `reason`。未知成绩对应空值，不会变成零分。原因沿用统计结果：当前未通过、失败记录需核实、低分、成绩不可判定。

`focusCourseCount` 是全部关注课程数量，`focusCourses` 是按 `limit` 截取的课程，`truncated` 说明是否还有未展示的项。DTO 不再夹带另一份完整课程列表，避免传了 `limit=1`，结果其他字段又暴露全部课程。

**近期表现与趋势**共用 `SemesterSummary`：实际学期、课程数、成绩可判定／通过／未通过／未知数量、修读学分及有效学分样本数、加权 GPA、GPA 样本数／样本学分／覆盖率、数值平均分及样本数／覆盖率、相邻学期比较和该学期的数据说明。数字直接复制确定性统计结果，不在 Tool 中重新计算。

`SemesterChange` 保留上一有效成绩学期、GPA 差值和方向、平均分差值和方向、未通过课程数量差值。趋势窗口首项不与窗口外的学期比较；有效学期不足两个，或 GPA 没有可比较样本时，分别返回 `INSUFFICIENT_DATA`，不会生成虚假的“上升／下降”结论。

三个分析 Tool 共同返回 `Source`：专业、年级、成绩单上传时间、培养方案版本及更新时间。近期／趋势还返回 `Rules`，记录低分上下界、学期顺序及统计口径，帮助模型正确解释数字。学号、用户姓名、JWT、数据库密码、文件 ID、培养方案 ID、原始文件名、磁盘路径和完整 Python JSON 不作为 DTO 字段提供给模型。

### 怎样控制输入、身份和调用范围

四个类实现统一的 `AcademicTool<I, O>`：`definition()` 提供工具名称、业务说明和参数 JSON Schema；`parseInput()` 校验后生成输入 DTO；`execute()` 调用已有 Service，再经 `AcademicToolMapper` 生成输出 DTO。

`AcademicToolExecutor` 只注册这 4 个实现，不根据模型提供的类名、URL 或 Controller 方法反射执行。模型不能传 `studentId`、`studentNo`、文件路径、培养方案 ID、SQL 或任意 URL。

每份 Schema 都声明 `additionalProperties=false`。程序运行时也重新校验，拒绝额外字段、错误类型、越界数字、重复 JSON 字段，以及一个参数字符串夹带多个 JSON 对象。例如 `limit="5"`、`limit=true`、`limit=1.5` 都不被转换成合法整数。

执行结果外层统一为 `AcademicToolResult(tool, status, message, data)`：

| 情况 | 外层 status | data |
| --- | --- | --- |
| 工具成功执行 | `OK` | 专用输出 DTO；其中仍可能标记数据不足 |
| 参数非法／未知工具 | `INVALID_ARGUMENT`／`UNKNOWN_TOOL` | `null`，不会加载业务数据 |
| 文件、档案或培养方案缺失 | `MISSING_DATA` | `null` |
| 无权限 | `ACCESS_DENIED` | `null` |
| 下游异常／旧版统计／报告缺失 | `SERVICE_UNAVAILABLE` | `null`，不会把 SQL、路径或连接异常原文交给模型 |

身份在 `beginRequest(request)` 时从已验证 JWT 的 `AuthContext` 绑定。匿名和管理员不能创建学生 Agent 的查询上下文；修改请求里的 `studentNo` 参数也不能改变已绑定身份。

DTO 的列表通过 `List.copyOf()` 保持不可修改，工具定义的 JSON Schema 也返回副本。工具各自拿到的数据不会污染其他工具。

### 同一次 Agent 请求怎么复用，以及为什么背景检查不解析 PDF

第三步先在 Java 中验证的调用方式如下；第四步的对话 Service 已按这个方式使用工具：

```java
var context = academicToolExecutor.beginRequest(request); // 本次 HTTP 请求只创建一次，供整个工具调用循环复用
var prerequisites = academicToolExecutor.execute("get_academic_context", "{}", context);
var assessment = academicToolExecutor.execute("get_academic_assessment", "{}", context);
var recent = academicToolExecutor.execute("get_recent_course_performance", "{\"limit\":3}", context);
var trend = academicToolExecutor.execute("get_academic_trend", "{\"lastSemesters\":4}", context);
```

背景检查只查本人档案、成绩单可用性和匹配的培养方案。成绩单还没上传，也能告诉学生下一步该做什么，不必先调用一个一定会失败的 PDF 分析。

本请求首次需要完整成绩数据的 Tool 才生成 `AcademicAnalysisSnapshot`。综合分析、近期表现和趋势在整个 Tool Calling Loop 内共用这一份快照，即使并发调用也只解析一次；加载失败也不因本请求内其他 Tool 继续调用而重试。下一轮 HTTP 请求重新创建上下文，首次需要完整成绩数据时再次分析。工具对象没有保存某个学生的上下文，避免跨请求或跨用户混用结果。

分析前的背景信息是调用时的前置检查；如果此后文件或培养方案更新，实际分析以加载快照时的数据为准。快照生成后再查背景，则直接使用该快照的来源信息，不额外查另一份文件或方案。

这 4 个 Tool 都没有调用正式保存入口，不上传新业务文件，也不调用 `WarningRecordService.saveFromPythonResponse()`。原“课程分析”按钮仍保留正式分析行为。

### 怎样验证第三步

新增 16 项 Tool 测试，与原学业回归一起共 **50 项 Java 测试通过**。重点验证四个工具一起使用只调用一次 Python、并发工具共享结果、身份不能被替换、非法参数在查询前拒绝、GPA 空值和零值、课程分类与限制数量、趋势数据不足、只读及异常信息不泄露。原 H2 测试继续确认 `t_file`、`t_warning_record` 数量不增加，原正式分析接口仍正常写入记录。

测试中的成绩与统计是明确构造的样本，用于验证契约和边界，不是当前学生的真实分析结果。这一步没有修改 Python 算法，没有调用真实 Python、运行中的 MySQL 或模型，也没有重启服务。第二步的 Python 28 项通过记录仍保留，本轮没有重复执行未改动的 Python 测试。

先独立验证 Tool，是为了把“业务结果不对”和“模型选错工具”分开排查。现在前者有了可测试的边界，下一步才接模型。

## 七、第四步：接入 DeepSeek，让模型选择工具

### 为什么是这个时候接模型

前面三步解决了数据和权限问题，现在可以让模型选择已经测试过的工具。模型提出调用时，后端仍负责校验参数、执行业务和返回结果。

新增 `DeepSeekClient` 使用 HTTP Chat Completions，发送工具定义和消息，再读取模型返回的 `tool_calls`。本版使用非流式、关闭思考的工具调用模式，按 [DeepSeek 官方工具调用协议](https://api-docs.deepseek.com/guides/tool_calls/) 回传工具结果。没有增加模型 SDK 或新的基础设施。

模型只能提出工具名和 JSON 参数。`AcademicToolExecutor` 仍只执行现有 4 个工具，不反射调用 Controller，不让模型访问 MySQL。

本轮已明确确认允许将 DTO 中的课程名称、成绩、学分、预警统计及说明发送给 DeepSeek；JWT 只用于后端认证，原始 PDF、学号、文件路径和内部 ID 不作为工具输出字段发送。模型输入也包含用户问题与本次提交的对话历史。

### 政策问答和 Agent 怎样共用 API 配置

API 配置仍放在现有 `python-services/.env`，不需要重新填写 Key：

```dotenv
DEEPSEEK_API_KEY=在本机填写或保留已有值
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-flash
```

Java 新增 `DeepSeekSettings`，只读取这三个配置项，支持单行值、单／双引号、`export`、BOM 和行尾注释，不读取数据库或 JWT 配置，也不打印文件内容。本版不解析 `${变量}` 插值；需要环境变量时直接在进程环境设置对应项。

优先级是非空启动环境变量／Spring 覆盖配置 → 共用 `.env` → 地址和模型默认值。Key 没配置时，仅 Agent 请求返回明确的 503 提示，不因为缺少 Key 阻止原业务启动。

新增可直接修改的 `backend/agent-config.properties`：

| 配置项 | 默认值 | 用途 |
| --- | --- | --- |
| `agent.deepseek.env-file` | `../python-services/.env` | 从 backend 启动时定位共用 API 配置；可用 `AGENT_ENV_FILE` 覆盖为绝对路径 |
| `agent.deepseek.connect-timeout` | `5s` | 模型连接超时 |
| `agent.deepseek.read-timeout` | `60s` | 单次模型响应等待超时 |
| `agent.deepseek.max-output-tokens` | `2048` | 限制每次模型输出长度 |
| `agent.max-tool-rounds` | `4` | 一次 HTTP 请求最多执行的工具调用批次 |
| `agent.max-tool-calls` | `12` | 一次 HTTP 请求最多执行的工具调用总次数 |
| `agent.request-timeout` | `180s` | 整个请求的耗时预算，在各次调用前后检查 |

Spring 从 backend 工作目录自动导入这个配置文件；`start-local.cmd` 已固定工作目录，因此启动方式不变。修改 API 配置后重启使用它的 Java 和 Python 政策问答服务；只修改 Agent 运行参数时重启 Java。真实 Key 保留在被 Git 忽略的 `.env`，不要写入公开的配置示例。

### 简单循环实际怎么工作

`SingleAgentService.chat()` 每次 HTTP 请求按下面的步骤执行：

1. 从已验证 JWT 创建一个分析上下文，保存在方法局部变量中。
2. 组装后端系统说明、前端提交的历史消息和当前问题。
3. 把四个工具定义交给 DeepSeek，使用 `tool_choice=auto`。本人学业分析必须取得工具证据；拒绝查询他人、说明能力范围或澄清问题可以不读成绩。
4. 模型返回一批 `tool_calls`，后端逐个检查并执行，全部使用同一个分析上下文。
5. 将模型提出调用的 assistant 消息加入本请求消息列表，再为每个调用追加 `role=tool` 消息，使用对应的 `tool_call_id`。
6. 再调用模型，让它解释结果；如果需要继续取数据，就在预算内重复。
7. 模型返回后，后端先区分允许的非分析回复和学业结论；后者必须有本轮分析工具结果。下一轮 HTTP 请求新建上下文，重新读取当前分析数据。

一条模型消息可能提出多个工具调用，算作一轮；默认最多 4 轮、12 次执行。达到上限后不再执行工具，再发起一次 `tool_choice=none` 的总结请求。因此最多 5 次模型请求。超过总调用次数的调用也会获得明确的上限结果，保证协议中每个声明的调用都有对应回复。

总耗时预算在模型／工具调用前后检查。执行中的同步调用由各自连接和读取超时限制，当前没有强制取消 Python 请求的机制，因此不能保证恰好在第 180 秒中断。模型超时、限流或认证失败不自动重试，避免重复生成和重复计费。

模型返回空消息、重复调用 ID、截断输出或错误协议时，返回明确错误，不执行不完整调用，也不把服务错误原文交给前端或下一轮模型。

### 没有数据时怎样防止编造答案

循环会记录本请求有没有成功获得分析类 Tool 的结果。如果没有，即使模型声称“GPA 为 4.0，全部通过”，后端也不会把这段内容作为学业结论返回。

只有背景检查时，根据 `nextActions` 提示补充成绩单或档案；文件、方案缺失或分析服务异常时给出对应提示。成功取得分析结果后，模型负责解释已经算好的数字和预警等级。第四步先用基础提示词保留实际学期、统计覆盖、未知成绩和核心课程核实说明；第五步已将这些要求整理成独立 Markdown，并补充具体工具选择与异常解释。

### HTTP 入口和多轮消息（第四步时的方案，已在第十四节升级）

新增 **`POST /api/student/agent/chat`**，必须使用学生 JWT。管理员和匿名用户不能进行本人学业 Agent 查询。

```json
{
  "message": "帮我分析一下最近的学习情况，有哪些课程需要重点关注？",
  "history": [
    {"role": "user", "content": "之前的问题"},
    {"role": "assistant", "content": "之前的回答"}
  ]
}
```

首次请求可不传 `history`。当前问题最多 2000 字符，历史最多 20 条，每条最多 4000 字符，总文本最多 20000 字符。历史只允许 `user/assistant`；不能提交 `system/tool`、学号或内部文件参数。

历史消息是对话文本，不是可信身份或本请求分析证据。没有 Conversation 数据库表或长期 Memory；前端下一轮把当前历史再次提交，后端重新从 JWT 创建分析上下文。

成功响应沿用 `Result`，`data` 中包含：

| 字段 | 含义 |
| --- | --- |
| `answer` | 学业解释，或权限、能力范围、澄清、数据不足的明确提示 |
| `status` | `COMPLETED`：分析回答；`DATA_UNAVAILABLE`：缺少分析结果；`TOOL_LIMIT`：达到上限；`REFUSED`：权限拒绝；`OUT_OF_SCOPE`：尚未支持；`NEEDS_CLARIFICATION`：需要明确问题 |
| `toolRounds` | 本次执行了多少批工具调用 |
| `toolCalls` | 工具名、执行状态及数据状态；不包含原始参数或完整工具结果 |

`COMPLETED` 表示循环正常得到回答，不承诺所有工具都成功；具体工具状态仍在 `toolCalls` 中，模型需说明缺失部分。模型网络超时返回 HTTP 504，认证／协议错误返回 502，缺 Key 或限流返回 503。

### 本地怎样启用和测试

本轮已生成新版 `backend/target/student-service-platform-0.0.1-SNAPSHOT.jar`。在原 Java 终端按 Ctrl+C 停止旧包，再启动：

```powershell
cd "D:\agent开发准备\简历项目\student-service-platform\backend"
.\start-local.cmd -JarPath ".\target\student-service-platform-0.0.1-SNAPSHOT.jar"
```

数据库保持原样。已运行的新版 8002 服务可以继续使用；本轮没有改 Python 算法，单独使用学业 Agent 不依赖 8000 政策问答服务运行，只共用它的 API 配置文件。

另开 PowerShell，使用本人学生 JWT 测试（不能用管理员 token）：

```powershell
$agentStudentToken = Read-Host "粘贴本人学生 JWT"
$agentPayload = @{ message = "帮我分析一下最近的学习情况，有哪些课程需要重点关注？" } | ConvertTo-Json
$agentResponse = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8081/api/student/agent/chat" `
  -Headers @{ Authorization = "Bearer $agentStudentToken" } -ContentType "application/json; charset=utf-8" `
  -Body ([Text.Encoding]::UTF8.GetBytes($agentPayload)) -TimeoutSec 240
$agentResponse.data.answer
$agentResponse.data.toolCalls | Format-Table
```

学生 JWT 可使用小程序已有学生登录返回的 `data.token`，或通过原 `POST /api/auth/login` 的 `username/password/role=student` 登录获得。测试前确认本人已上传可解析成绩单，数据库有匹配专业、年级的培养方案，8002 已启动。

### 第四步的验证和边界

本轮全后端 **100 项测试通过**，其中 99 项使用模拟响应／临时文件／H2，另 1 项是显式启用的 DeepSeek 协议测试，真实发出两次模型请求，仅包含虚构测试专业、年级和数据可用标记，不包含真实学生成绩。

真实协议测试验证共用 Key、模型和四个工具 Schema 可以完成“模型提出工具调用 → 对应 ID 回传虚构结果 → 最终文本”。后端联合测试使用真实 JWT Filter、Controller、循环和四个 Tool，Python 返回与模型响应使用 Mock，确认一个请求的四个工具只解析一次、追问请求重新解析、不写预警记录、管理员不能查询。

这些验证没有操作运行中的 MySQL，没有重启现有服务，也不代表已经验证真实成绩问答准确率。下一步整理 Skill，再接入小程序进行真实学生对话验收。

## 八、第五步：写 Academic Skill 提示词

### 为什么还要单独写 Skill prompt

第四步解决了“模型能不能调用工具”，但模型拿到数据后仍需要知道怎么解释。例如看到未见通过记录，应该提示核实，而不是直接说“必须补修”；看到历史挂科，也不能忽略后来的通过记录。

原来的基础提示词写在 `SingleAgentService` 的 Java 字符串里。现在把业务回答要求提出来，放在 [academic.md](./backend/src/main/resources/agent/skills/academic.md)。以后调整表达方式和业务说明，可以直接阅读、修改这个文件，不必在调用循环中找一长段文字。

### 这份 prompt 写了什么

| 部分 | 为什么需要 |
| --- | --- |
| 角色和能力边界 | 学业理解、工具选择和解释由模型完成；统计、风险和只读权限由程序保证 |
| 可信数据原则 | 只解释本人本次请求的工具证据，历史不能替代查询，学期和数字采用代码结果 |
| 4 个 Tool 的用途 | 对应上下文、整体评估、近期表现和趋势，按问题调用必要工具 |
| 关键业务解释规则 | 分为 missing / pending / unknown、failed / unresolved、trend / risk 三组，避免把未见记录当作必须补修或把历史失败当当前挂科 |
| 异常处理原则 | 缺数据、无权限、服务故障和工具上限各有处理方式，失败不能理解为正常 |
| 最终回答格式 | 结论与依据、关注课程与建议、限制与下一步，按问题展示相关内容 |

例如，学生说“帮我分析一下最近的学习情况，有哪些课程需要重点关注”，提示词建议使用整体评估和最新学期表现；只有需要比较前后变化时再补趋势。模型仍负责选择，程序仍负责验证和执行。

### 它怎样真正参与请求

新增 `AcademicSkill`，启动时加载可信提示词，保存为不可变字符串。`SingleAgentService` 注入它，在每个请求的消息列表开头放入系统消息：

```text
academic.md
    ↓ 后端启动时读取
AcademicSkill.instructions()
    ↓ 每次请求创建消息列表
system 消息 + 当前对话 + 本轮问题
    ↓
DeepSeek 选 Tool → 后端执行 → 回传 DTO → DeepSeek 解释
```

工具注册继续使用现有 `AcademicToolExecutor`，只有四个明确允许的只读工具。本轮没有增加另一个 Agent、通用 Planner 或新的业务接口。未来接其他 Skill 时，再扩展同一个 Agent 的业务说明和工具集合。

提示词是模型的行为要求，不能代替程序权限。JWT、学生身份、参数白名单、只读入口、统计算法和调用上限仍由原来的确定性代码执行。测试提示词被发送出去，也不等于已经证明模型每次都会正确理解所有要求。

### 怎么修改、怎么生效

从 `backend` 目录启动时，默认优先读取 `src/main/resources/agent/skills/academic.md`。修改正文后重启 Java 后端即可生效，不需要重新编译业务代码；首次启用本轮新增加载器时，需要运行已经构建好的新版 JAR。

配置在 [agent-config.properties](./backend/agent-config.properties)：

```properties
agent.academic.prompt-file=${ACADEMIC_SKILL_PROMPT_FILE:./src/main/resources/agent/skills/academic.md}
```

也可以指定自己维护的可信 Markdown 文件，Windows 绝对路径建议使用 `/`。这是运维配置，聊天请求不能选择或覆盖它。文件保持 UTF-8 和首行 `# Academic Analysis Skill`，不写密钥或个人成绩。

默认源文件缺失时会读取 JAR 内打包的同一份提示词，便于只部署 JAR。自定义路径不存在、标题不符或大小超过 64 KiB 时，启动明确失败，不默默换回默认文本。提示词仅在启动时加载，不会在每个 Tool 调用中重新读取。

### 为什么后来收敛成六部分

最初提示词分成八部分，统计、课程和风险解释分散，部分边界重复。本次按“角色和能力边界 → 可信数据 → Tool 用途 → 业务解释 → 异常 → 回答格式”收敛成六部分，将关键业务规则集中在三个小节。参数、权限、共享快照和调用上限的实现仍在代码里，提示词只保留模型选择工具和解释证据需要的信息；请求内共享、下一轮重建的机制没有改变。

### 第五步怎样验证

首次接入 Skill 的默认离线构建：**104 项测试，其中 103 项通过，1 项真实模型协议测试跳过**。新增 4 项覆盖 JAR 资源加载、可信文件覆盖及重启加载、错误路径／文件和大小限制；已有 Agent 调用循环测试还验证发给模型的首条系统消息确实是完整 Skill prompt。

已有 JWT 与四个 Tool 的联合测试继续验证同请求只解析一次、下一轮请求重新解析、不新增正式预警记录。本轮没有调用真实 DeepSeek、操作运行中的 MySQL 或重启服务，也没有宣称回答准确率提升。下一步才接小程序并做真实学生对话验收。

六部分收敛后，运行 `AcademicSkillTest` 和 `SingleAgentServiceTest`，**14 项测试全部通过**，并重新打包确认 JAR 中的提示词与源文件一致。本次验证加载和调用循环，没有调用真实模型，不代表已经验证回答质量。

### 第六步：把入口接到小程序，准备实际验收（当时的页面内存方案）

后端能接收问题之后，学生还需要一个可以直接使用的入口。我们在服务大厅原来的四个功能下方增加横向“学业智能助手”卡片，打开新页面 `pages/agent/agent`。页面使用同一套蓝色风格，支持手动提问、五个快捷问题、等待状态、追问和“新对话”。原成绩单上传、更新和固定课程分析入口继续保留。

首页卡片只是导航，真正接入在聊天页面：调用 `POST /api/student/agent/chat`，沿用 `utils/request.js` 的当前后端地址与学生 JWT。请求体只有 `message` 和 `history`，不加入学号、姓名或 Tool 消息。后端继续验证身份并执行已有只读工具；点击“学业助手”不会自动调用正式课程分析或保存预警记录。

#### 为什么不能直接照搬政策问答页面

政策问答只发送一个问题。学业 Agent 要支持追问，需要携带有限的对话历史，而且一次回答可能经过多轮模型和工具调用，因此单独设置 240 秒客户端等待时间。这个设置只作用于 Agent 请求，不改变其他业务默认的 15 秒超时。

页面只在内存中保留当前对话，不写 Storage 或数据库。发给后端的历史最多 20 条，只包含完整的成功 user/assistant 消息对；每条不超过 4000 字符，连同当前问题不超过 20000 字符。超长回答在界面完整展示，但不截断后再作为历史发送。失败提示、欢迎语和没有可用数据的结果不加入后续历史。正常的拒绝、能力说明和澄清回复会保留，便于理解“那分析我自己的”或“第二个”。所有历史始终只是对话文本，不是本轮学业证据。

这仍然是对话文本，不是分析快照。每次提问都是独立 HTTP 请求，后端创建新分析上下文；同一请求内多个 Tool 共享 Snapshot 的机制没有改变。

#### 等待、失败和账号切换怎么处理

等待回答时禁用重复发送；失败后显示原因并恢复问题，便于重试。`DATA_UNAVAILABLE` 和 `TOOL_LIMIT` 会标明尚未取得完整结果。成绩单缺失时，学生可使用页面上的“上传或更新成绩单”进入已有成绩单管理页面。

登录过期会清空当前对话与登录凭证，返回学生登录页，同时保留后端地址配置。切换 JWT 或后端地址时会清空旧对话，忽略旧请求的迟到结果。关闭聊天页面时取消客户端请求并清空内存历史，避免已退出页面继续更新。

为此，公共请求工具增加了一个可选的 `onTask` 回调，让 Agent 页面取得本次请求任务并在退出时取消。未传这个选项的原页面行为不变。

#### 本轮怎样验证

小程序 **31 项回归测试全部通过**，其中新增 Agent 的 14 项验证请求协议、真实请求包装器携带 JWT、追问历史与长度、重复发送、超时、缺数据、部分结果、登录失效、账号／后端切换和页面退出。原政策问答、证明下载、成绩单保存和课程报告测试同时通过。

这些测试使用微信 API 模拟，没有调用真实模型，也没有操作运行中的数据库。首页布局和聊天页面还需在微信开发者工具编译查看，随后使用真实成绩单和真机进行完整验收；不把模拟测试当作问答准确率验证。

#### 现在怎么启用和实际验收

本轮只改小程序和文档，不需要重新打包 Java。保持已启动的新版 Java `8081`、学业分析 `8002` 和 MySQL；微信开发者工具重新编译，真机重新生成预览并进入。登录后在服务大厅四个功能下方点击“学业智能助手”。

先在原学业分析页面确认本人有可用成绩单，且管理员已维护匹配本人专业、年级的培养方案。依次测试五类问题和追问，核对实际学期、课程、统计与预警解释，再验证新对话、无成绩单、无培养方案和下游不可用等场景。普通 Agent 查询后检查正式预警记录没有新增；这一点由已有后端只读入口保证，还需要真实链路复核。

### 实测发现：工具能调用，不代表统计口径已经正确

用户实际比较最近四个学期时，春季 GPA 从 3.7065 变成 3.5185，被解释为下降。我们对原 PDF、课程解析和统计逐层对照，发现模型收到的数字就是错误的：`P` 课程被当作有 1.0 GPA 的课程参与计算。

修复由确定性代码完成：解析器不把 `P/通过` 当数值 GPA，统计也独立排除这种记录；它仍保留已获学分和通过状态，字母 `F` 和数值零绩点不被误删。春季 GPA 恢复 3.72，实际 PDF 117 已获学分和整体 GPA 3.65 不变。

另一个 88.1111 是 9 门数值最终成绩的算术平均，本身可复核，但回答必须说清它不是学校官方均分。最新学期 GPA 略升、数值均分略降，所以提示词要求分指标解释，不能把所有表现笼统判为下降。具体课程与全部学期对照见 [实测修复说明](./backend/docs/academic-warning.md#2026-10-03-实际-agent-对话p-课程导致-gpa-趋势错误)。

本轮 31 项 Python 学业测试、37 项 Java Agent／工具回归全部通过，并直接重新解析原 PDF 核对五个学期。没有重新测量模型回答准确率，也没有修改运行中的 MySQL。

这次复盘说明：先确认 Tool Result 是否正确，再讨论提示词。只让模型“更准确回答”，无法修复已经算错的统计证据。

### 实测发现：权限拒绝被“没有数据”的兜底覆盖（2026-10-04）

用户问“帮我查一下其他同学的成绩”，页面却显示“暂未取得完整分析”，建议换成学习概况或成绩趋势重试。这种反馈会把权限问题说成问题表达不清，应该修复。

之前为了阻止模型凭空回答成绩，第一次模型请求强制调用工具；最终如果没有分析类工具成功，就统一返回数据不足。这个规则适合学业结论，但权限拒绝本来就不需要成绩证据，也不应该调用本人工具来替代查询别人。

我们保留原来的证据要求，把非分析意图区分出来：

- 普通循环使用 `tool_choice=auto`，由模型识别需要分析、拒绝、说明范围还是澄清。
- 三种非分析情况只允许返回两个字段的控制 JSON：`responseType=NON_ANALYSIS`，以及白名单内的 `reason`（`OTHER_STUDENT`、`UNSUPPORTED_CAPABILITY`、`NEEDS_CLARIFICATION`）。后端严格解析，并生成固定中文提示，不接收自定义成绩、答案或学号。
- 本人学业分析仍必须有本轮分析工具结果。未知原因、多余字段、无效 JSON 或未经工具支持的“GPA 4.0”都不能变成学业结论。
- 工具真正返回 `ACCESS_DENIED` 时，后端立即停止本请求，不继续同批剩余工具，也不让模型再次尝试绕过。

这不是把权限交给模型。是否允许读取、读取谁的成绩，仍由 JWT 和现有业务 Service 决定；工具没有 studentId、学号或 SQL 参数。模型最多识别意图或拒绝，无法通过控制 JSON 获得额外权限。请求开始只创建轻量上下文，拒绝场景不加载 Snapshot，不解析 PDF。

小程序新增 `REFUSED`、`OUT_OF_SCOPE`、`NEEDS_CLARIFICATION` 的提示，分别显示“仅支持查询本人”“当前能力范围”“请补充问题”。真正缺少成绩单或服务不可用时，原数据不足处理仍保留。

本轮全后端 **108 项回归通过，2 项需要显式开启的真实模型测试默认跳过**；小程序 **33 项通过**。另单独运行真实 DeepSeek 拒绝测试：发送实际提示词、工具定义与截图中的问题，没有传真实学生数据，返回 `REFUSED`，工具调用次数和 Snapshot 读取次数均为零。验证了没有工具时不能返回编造成绩、拒绝不被上传成绩单提示覆盖、权限拒绝停止剩余工具，以及小程序拒绝后的正常追问。已重新打包 Java；启用时重启新版 Java 并重新编译小程序，数据库和 Python 无需因这次提示修复而重启。

## 九、用一句话串起目标链路

学生说：

> 帮我分析一下最近的学习情况，有哪些课程需要重点关注？

已接入的后端和小程序链路按下面的顺序工作，真实数据效果还需验收：

```text
后端验证这个学生是谁
    ↓
Agent 理解问题，选择 Academic Skill 的工具
    ↓
检查本人是否有成绩单和匹配的培养方案
    ↓
本次请求加载一份分析快照
    ↓
工具读取预警结果和近期课程统计
    ↓
模型解释：哪些课未通过、哪些只是低分、哪些需要核实
    ↓
小程序展示回答及依据
```

后端身份认证、工具选择、业务分析、共享快照、统计、模型解释、独立 Skill 提示词和小程序聊天入口已实现；真实学生完整对话与真机体验验收仍待完成。

## 十、面试时可以怎么讲

### 一分钟讲清目前真正完成的工作

> 我是在已有学生服务项目上扩展学业 Agent 能力。原项目能解析成绩单并生成固定分析报告，我想让学生用自然语言询问近期表现和成绩趋势。
>
> 我先梳理了已有链路，发现分析接口每次会重新解析 PDF，而且正式分析完成后会写预警记录。如果直接给 Agent 调用，多个工具会重复解析，普通追问也可能产生正式记录。
>
> 所以我先提取业务 Service，分开正式分析和只读查询，再做请求内共享快照。同一次 Agent HTTP 请求的工具调用循环内，多个工具共用结果，最多解析一次，也不写正式预警记录。下一轮请求重新创建上下文，没有跨请求缓存。
>
> 接着补了确定性的学期统计，把最新学期、低分阈值、加权 GPA 和趋势变化交给程序计算。随后封装 4 个只读工具，用 DTO 筛选必要证据，严格校验参数，不让模型指定学号、SQL 或文件路径。
>
> 最后接入 DeepSeek 和简单调用循环，由模型选择工具，程序执行并回传结果，再生成解释。身份每个请求都从 JWT 确定；一次请求内共享分析快照，下一轮重新创建。我设置了轮数、次数和超时预算，没有可用分析时返回明确提示，避免无证据的成绩结论。随后我将业务回答规则整理成独立 Skill 提示词，规定未见通过记录需要核实，低分不等于挂科，统计由代码完成。当前 108 项后端本地回归和 33 项小程序回归通过，之前也验证了真实模型工具调用协议。小程序实测进一步发现 P 课程参与 GPA 的统计错误和权限拒绝被兜底覆盖的问题，已经分别修复；更多真实学生问题和真机验收仍在继续。

这段描述只覆盖目前已完成的阶段。后面完成实际对话和真机验收后，再更新面试表述。

### 常见追问及回答思路

| 可能被问到的问题 | 可以怎么解释 |
| --- | --- |
| 为什么不是让模型直接读成绩单分析？ | 现有解析和风险规则能复用，计算结果可测试、可追溯。模型用于理解和解释，重要数字由程序保证一致 |
| 为什么不把所有 API 暴露成工具？ | 管理、上传、保存等接口用途不同，有些会写数据。Agent 只需要少量稳定业务能力，便于控制参数和权限 |
| 为什么 Agent 放在 Java 后端？ | 身份、文件和数据权限已在那里，工具能直接复用 Service；Python 继续做它已有的解析和算法 |
| 为什么先做快照？ | 一次 Agent HTTP 请求可能需要多个工具，它们应使用同一份成绩和培养方案，避免重复解析以及混用不同版本 |
| 分析上下文和对话上下文有什么不同？ | 分析上下文只用于本次 Agent 请求及内部工具调用循环；对话上下文可以跨多轮请求保存消息，两者不能混用 |
| 快照是不是长期 Memory？ | 不是。它只用于当前 Agent HTTP 请求。下一轮请求重新创建上下文，需要完整数据时重新解析；没有跨请求缓存 |
| 为什么不用最新预警记录直接回答？ | 历史记录没保存全部报告字段，还可能对应旧文件或旧培养方案，不能直接当成当前完整结果 |
| 怎么防止查到别的学生？ | 身份取自后端验证的 JWT，工具参数没有学号入口，模型传来的其他身份不能改变查询对象 |
| 怎么证明只读和复用？ | 用内存数据库检查文件和记录数量不增加，并验证多个读取甚至并发读取只调用一次 Python |
| 重修、缺失成绩怎么处理？ | 保留历史事实，区分已通过和仍未通过；未知成绩不是零分，未知学期不参与时间排序 |
| DTO 和数据库实体有什么不同？ | 实体描述数据库存什么，DTO 描述一次调用允许收发什么。工具只提供回答所需证据，不向模型提供整行数据或文件路径 |
| 当前 Agent 已经跑通了吗？ | 后端接口、模型循环和工具已实现，协议与联合测试通过，小程序页面已接入且回归通过；真实学生完整对话和真机验收待完成 |

## 十一、测试命令、代码入口和当前限制

当前学业回归命令：

```powershell
cd "D:\agent开发准备\简历项目\student-service-platform\python-services"
.\.venv\Scripts\python.exe -m unittest discover -s tests -p 'test_academic_*.py' -v

cd "D:\agent开发准备\简历项目\student-service-platform\backend"
.\mvnw.cmd -o test
# 默认不会运行真实模型测试；DeepSeekLiveSmokeTest 会跳过，不产生模型费用。
```

小程序回归：

```powershell
cd "D:\agent开发准备\简历项目\student-service-platform"
node --test miniprogram/tests/agent.test.js miniprogram/tests/agent-stream.test.js miniprogram/tests/download.test.js miniprogram/tests/qa.test.js miniprogram/tests/academic.test.js miniprogram/tests/transcript.test.js
```

需要主动验证真实模型时，单独运行（协议测试两次请求，权限拒绝测试一次；只用虚构数据或测试问题）：

```powershell
$env:RUN_DEEPSEEK_SMOKE = "1"
try { .\mvnw.cmd -o '-Dtest=DeepSeekLiveSmokeTest' test }
finally { Remove-Item Env:RUN_DEEPSEEK_SMOKE -ErrorAction SilentlyContinue }
```

| 想复习什么 | 看哪个文件 |
| --- | --- |
| 正式分析和只读查询怎样分开 | [AcademicAnalysisService](./backend/src/main/java/com/college/student_service_platform/service/AcademicAnalysisService.java) |
| 原接口如何调用 Service | [AcademicWarningProxyController](./backend/src/main/java/com/college/student_service_platform/controller/AcademicWarningProxyController.java) |
| 同请求怎么复用分析、保留失败 | [AcademicAnalysisReadContext](./backend/src/main/java/com/college/student_service_platform/service/AcademicAnalysisReadContext.java) |
| 快照里保存什么、怎样避免修改共享数据 | [AcademicAnalysisSnapshot](./backend/src/main/java/com/college/student_service_platform/dto/AcademicAnalysisSnapshot.java) |
| 学期、低分、GPA 和趋势怎样计算 | [academic_statistics.py](./python-services/academic_statistics.py) |
| 统计如何接到原 Python 接口 | [5_pdf_compare.py](./python-services/5_pdf_compare.py) |
| 后端怎样选择近期表现和趋势窗口 | [AcademicStatisticsService](./backend/src/main/java/com/college/student_service_platform/service/AcademicStatisticsService.java) |
| 4 个 Tool 输入和输出保留哪些字段 | [AcademicToolDtos](./backend/src/main/java/com/college/student_service_platform/agent/academic/AcademicToolDtos.java) |
| Tool 如何注册、执行并拒绝非法调用 | [AcademicToolExecutor](./backend/src/main/java/com/college/student_service_platform/agent/academic/AcademicToolExecutor.java) |
| 原始报告怎样投影成模型可用的 DTO | [AcademicToolMapper](./backend/src/main/java/com/college/student_service_platform/agent/academic/AcademicToolMapper.java) |
| 参数／身份／数据缺失／并发等怎样测试 | [AcademicToolExecutorTest](./backend/src/test/java/com/college/student_service_platform/AcademicToolExecutorTest.java) |
| 怎样读取共用 API 配置 | [DeepSeekSettings](./backend/src/main/java/com/college/student_service_platform/agent/DeepSeekSettings.java) |
| 怎样调用模型并校验协议 | [DeepSeekClient](./backend/src/main/java/com/college/student_service_platform/agent/DeepSeekClient.java) |
| Skill 提示词正文 | [academic.md](./backend/src/main/resources/agent/skills/academic.md) |
| 提示词加载与启动配置 | [AcademicSkill](./backend/src/main/java/com/college/student_service_platform/agent/academic/AcademicSkill.java) |
| 简单调用循环与请求级上下文 | [SingleAgentService](./backend/src/main/java/com/college/student_service_platform/agent/SingleAgentService.java) |
| 聊天接口和参数边界 | [AgentChatController](./backend/src/main/java/com/college/student_service_platform/controller/AgentChatController.java) |
| 小程序首页入口与聊天交互 | [agent.js](./miniprogram/pages/agent/agent.js)、[main.wxml](./miniprogram/pages/main/main.wxml) |
| 当前消息长度与异常提示 | [agent-chat.js](./miniprogram/utils/agent-chat.js)；服务端历史选择见第十四节 |
| 小程序 Agent 回归测试 | [agent.test.js](./miniprogram/tests/agent.test.js) |
| 修改 Agent 超时与调用上限 | [agent-config.properties](./backend/agent-config.properties) |

截至这里的最初六步没有新增数据库表、依赖、跨请求缓存或长期 Memory。第十四节后来单独增加了会话表，仍没有分析缓存或长期 Memory。本次开发没有重启运行中的服务；启用第四、五步只需启动新版 Java 包并保持已有新版 8002 服务运行。若从更早的版本升级，8002 也需更新到第二步的统计版本。

默认回归使用 Mock、临时文件和内存数据库。显式运行的真实协议测试不使用真实学生数据；两类验证都不等于已经测量模型选工具准确率或完整学生对话体验。

## 十二、后续按什么方式继续记录

以后每完成一步，都按下面的顺序补充，保持这份文档是开发复盘，而不只是接口清单：

1. 当时想解决什么用户问题。
2. 看现有代码时发现了什么，哪些能力可以复用。
3. 为什么选这个做法，有哪些取舍。
4. 实际改了哪里，举一个具体例子解释行为变化。
5. 怎样测试，结果是什么，还有什么没完成。

同步更新进度表、实际测试数量、运行或部署要求，以及面试表述。计划中的功能继续标记为待开发，不把设计目标写成已经完成的成果。

## 十三、让学生看见 Agent 的真实执行过程

这一步解决的是体验问题：以前点击提问后，学生只能等待最终文字，不知道是在读取资料还是等待模型。我们没有扩展工具或重新计算风险，而是在原调用循环的真实节点加入进度事件。

最初可以在前端固定播放几个步骤，但那会让没有执行的工具也看起来执行过。因此保留旧聊天接口，新增逐行返回事件的 `/chat/stream`。模型和工具开始、完成时，后端立即写出对应步骤；小程序收到分包就更新页面。没有调用的工具不会显示，失败步骤不会变成完成。

工具返回的 DTO 除了送给模型，还投影成页面需要的可信字段：官方 GPA、学分、待核实事项、预警等级、学期 GPA 和关注课程。卡片和趋势直接使用这些字段，不从模型文章里猜数字，更不让模型重新计算风险。

**仍是一轮 HTTP 请求一个分析 Context。** 原 JWT 在异步输出前完成校验，后续调用复用这个 Context。一个请求内的多个 Tool 最多解析一次 PDF；下一轮请求重新创建 Context。对话文字、请求内分析上下文、页面执行记录是三个不同概念，这里没有长期 Memory 或跨请求缓存。

我们展示的是 Tool Execution Trace：哪个能力正在执行、是否完成、取得了什么公开结果。它不是模型内部推理，也不包含学生标识、JWT、文件路径或原始工具参数。学业查询仍然只读，不调用正式预警保存流程。

工程上复用 Spring MVC 的异步响应与有界线程池，小程序用微信原有分包请求能力。中文字符可能被拆到两个网络包，所以单独处理 UTF-8 拼接，再按换行解析 JSON。旧客户端如果没有分包回调，只在完整响应后显示记录，界面不会假装有实时进度。

页面切换、身份切换或清空对话时终止本地请求并忽略迟到结果；已经发出的同步上游调用不能保证马上取消。这个限制和“界面取消后不再显示旧答案”需要分开说明。

本轮 Java 全量验证 112 项通过、2 项真实模型测试默认跳过；小程序 42 项回归通过，其中专门检查步骤先于答案到达、中文跨分包、断线不能误报成功，以及原证明下载和成绩单流程。管理端类型、构建和桌面浏览器检查也通过。微信原生显示及真实模型速度仍需实际验收，不能把 Mock 成功说成真实用户已经验收。

面试时可以这样讲：**我把已有 Single Agent 的模型/工具循环变成可观察的执行过程，前端进度由真实后端事件驱动；结果卡片来自确定性工具 DTO，同时保留 JWT 身份边界和请求内快照复用，没有为了展示效果增加新基础设施。**

代码入口与本地启用步骤见 [前端产品化改造记录](./UI_REFACTOR.md)。


## 十四、把页面里的对话升级为服务端会话

### 先解决什么问题

以前学生退出页面，聊天记录就丢了；下一次追问还要靠客户端把 history 再传回来。这适合先把 Agent 跑通，但服务端并不知道一段对话是谁的，也无法可靠恢复记录。本轮只补这块基础能力，不新增业务 Skill 或框架。

我们要保证的规则很直接：**每个会话的 student_id 必须等于当前 JWT 所代表的学生数据库 ID。** 对话内容不能决定 owner，前端隐藏别人的会话也不能代替后端权限检查。

### 读现有代码时发现了什么

这个项目的数据访问是 Spring JDBC 和手写 SQL，并没有 MyBatis Mapper 或统一 BaseEntity，因此新增 Repository 沿用 JdbcTemplate，时间字段沿用 LocalDateTime。JWT subject 实际存的是学号，需要先查有效学生档案，才能得到 t_student.id；不能把它当成 t_user.id，也不能接收模型给出的 studentId。

Agent 已经有四个只读 Tool、每个请求共用一个 Snapshot 和真实进度输出。会话层放在这些能力外面，就能补持久化，同时保持原来的分析规则。

### 为什么选择两张表和一个归属入口

一张 agent_conversation 保存 owner、标题和时间，一张 agent_conversation_message 保存普通 USER／ASSISTANT 文字。system 和 tool 消息只服务于本轮模型协议，不作为学生的聊天记录存下来。公开结果卡片和执行过程目前也不保存。

所有按 ID 的操作都进入同一 ownership 检查，查询直接带 `id AND student_id` 条件。学生 B 拿到 A 的会话 ID，请求详情、聊天、重命名或删除都会得到 404；不存在的 ID 也是同一错误。管理员不能绕过这个学生 Service。列表只查当前学生的记录。

这一边界归 Service 管：Controller 接收请求，JWT 提供可信身份，Repository 执行受约束查询，模型不接触 MySQL。

### 一次追问具体发生什么

学生说“再看看我的趋势”，前端只发会话 ID 和这句话。Java 验证会话归属，从数据库取最近完整问答，再创建本轮分析 Context，运行原 Agent 循环。多个 Tool 共用本轮 Snapshot；下一次请求仍重新读取分析数据，不把数据库对话当成分析缓存。

模型返回有效文字后，用一个短事务一起保存本轮 USER 和 ASSISTANT，再更新标题和最近时间。模型失败则都不保存；第二条写入失败，第一条也会回滚。正常的拒绝或“请先上传成绩单”可以作为普通对话保存，但不会被当成已经拿到学业结论。

模型可能很慢，所以调用期间不持有数据库锁。保存时再次锁住会话并检查最后消息 ID；如果另一个请求已经完成，这次返回 409，让用户刷新，避免基于旧历史的回答混进新上下文。短事务使用 READ COMMITTED，减少不同学生同时聊天时 MySQL 间隙锁和旧快照带来的干扰。没有为了这个场景增加 Redis 或消息队列。

数据库提交成功但最终网络包丢失时，服务端有记录、前端却可能显示失败。因此界面提示“刷新会话确认保存状态”，不能保证所有断线都等于未保存。目前没有幂等请求键，不自动重试模型。

### 页面和标题怎么变化

进入学业助手会恢复本人最近的服务端会话。页面新增历史会话、改名、删除、刷新和加载更早消息。账号／后端切换会清空页面的旧内容并忽略迟到结果；服务器上的原会话仍保留。点击“新对话”只准备一个新入口，第一次发送时才创建数据库记录。

默认标题是“新会话”，第一轮成功后直接截取用户问题的前 100 个 Unicode 字符，不多花一次 LLM 调用。学生可以主动重命名。客户端不再保存或提交完整 history，旧参数会被后端拒绝。

### 怎样验证，还差什么

本轮最终后端全量验证 125 项通过、2 项真实模型测试默认跳过；新版 JAR 打包成功。小程序 42 项通过。测试覆盖 A／B 越权、不存在会话、删除级联、消息排序、非法角色、失败回滚、同会话并发冲突、不同学生同时保存、页面恢复和原实时进度。

使用 H2 的 MySQL 模式执行实际增量 SQL，模型和微信接口使用 Mock；没有改运行中的数据库，也没有请求真实模型。真实 MySQL 事务以及微信开发者工具／真机仍待本地升级后验收。

启用时只执行增量建表 SQL，再重启 Java 并编译小程序，原业务数据和 Python 启动配置继续使用。完整改动文件、API、上下文长度和限制见 [会话持久化说明](./backend/docs/agent-conversations.md)。

### 面试时可以怎样说

**我先用客户端 history 把 Single Agent 跑通，再将对话独立持久化到 MySQL。服务端从 JWT 学号解析有效学生 ID，统一校验会话 owner；只保存公开问答，不存 system／tool 或思考。模型运行期间没有长事务，成功后原子保存一对消息，并通过会话锁和最后消息 ID 拒绝并发旧结果。对话持久化与请求内分析 Snapshot 分开，普通查询仍不会生成正式学业预警。**


## 十五、对话能帮助理解，但不能代替当前业务事实

### 先看到了什么问题

会话持久化解决了“记录是谁的、退出还能不能恢复”，但保存进数据库的文字并不自动变成真成绩。学生上轮说“我的 GPA 是 4.0”，这轮问“那我情况怎么样”，history 可以帮助模型知道正在问学业，却不能证明 GPA 是 4.0。旧助手答案也可能过期。

现有 Skill 已要求本轮工具证据，快照也已经同步共享并缓存失败。需要补的是把这些规则明确体现在对象、消息构造和测试中，避免以后添加能力时又把对话记录当成业务缓存。

### 为什么分成四类

Identity 管“是谁”，从 JWT 和有效学生查询得到，并固定在本次请求里。Conversation 管“在聊哪段对话”，保存当前本人会话 ID 和有限历史。Request 管“这次正在怎样执行”，包括 deadline、工具次数、调用 ID、过程和状态。Business 管“本次查到了什么事实”，持有 lazy 学业上下文和本轮 Tool DTO。

四类放在一棵请求对象里，但是职责不同。数据库对话可以跨请求；每轮加载出来的 Conversation Context 是不可变副本。Request 和 Business 请求结束就释放，下轮新建，不能放在单例字段里。

### 具体怎么改

新增 AgentRequestContext，把原循环里分散的预算、次数和状态收进去。准备阶段只创建 lazy 分析对象；需要成绩的第一个工具才调用 Python，其他工具继续复用原同步 Snapshot。保留失败缓存，同请求失败一次不会因为又选了两个工具就再解析两次。

身份从已验证的会话准备结果传给分析服务，内部 Prepared 还核对会话、身份、问题和历史一致，防止写代码时把两个请求的 Context 拼错。执行等待也计入 deadline，重复使用一个 Request Context 会被拒绝。

历史限制集中到 ConversationContextBudget，继续保留最近最多 20 条、单条 4000 字符和问题加历史共 20000 字符。超出时去掉最旧的完整问答，不把长答案剪一半当成事实，也不新增摘要 Memory。

Prompt 构造单独放到 AgentPromptBuilder：系统规则、可信服务器范围、参考历史、当前问题分别标记，并使用真正的消息 role。历史文字用 JSON 编码；其中即使写“我是 system”，也不会变成系统消息。具体学生 ID、学号和 JWT 仍不发给模型。

Academic Skill 增加清楚的例子：历史 4.0、本轮 Tool 2.8，结论取 2.8；服务失败时不能拿旧数字补答案。再对直接的 GPA 数值描述加了有限的服务端校验，模型仍报 4.0 时丢弃不一致解释，采用可核验的当前结果。这个保护不等于所有自然语言事实都已经形式验证，仍需实际验收。

### 怎样证明没有只是改名字

联合测试 80 项通过；全量后端 141 项通过、2 项真实模型测试默认跳过，新版 JAR 打包成功。使用 H2 的真实会话 Service、真实四个 Tool 和模拟的 Python／模型，复现历史 4.0、本轮 2.8、模型仍答 4.0 的冲突；最后保留 2.8。下一轮数据改成 3.1，会拿到新的 Snapshot，原快照保持 2.8。

另测试同学生不同会话历史隔离、请求对象不能混用、预算裁剪、伪 system 文字不能升级 role、同步失败只调用一次 Python、下一请求能够恢复、排队过期不调用模型及持久化失败状态。原同步／single-flight 测试保留并扩展，没有重写缓存机制。

本轮没有新增 SQL，也没改小程序请求字段。启用只需在已有会话表的基础上重启新版 Java；真实 DeepSeek 和实际成绩单问答仍需验收。代码入口、可信与不可信数据和具体限制见 [可信上下文说明](./backend/docs/agent-contexts.md)。

### 面试可以这样说

**会话历史是语义参考，不是事实数据库。我把已认证身份、会话文字、本轮执行状态和业务证据分开：历史解释追问，本轮工具决定成绩事实。每个请求有自己的 deadline、次数和 lazy Snapshot；同请求同步复用成功或失败，跨请求重新读取。消息角色和权限由服务器构造，用户写 system 或自述 GPA 都不能改变授权和当前业务数据。**


## 十六、用并发测试验证不同学生不会串数据

### 为什么还需要这一步

前两步已经把 owner 和四类 Context 写清楚，但这只是设计。Spring Service 默认是单例，多个学生会同时使用同一个对象。如果有人以后把“当前学生”或 Snapshot 放进 Service 字段，单用户测试可能一直通过，两个学生同时请求却会互相读到成绩。

所以这一步不继续增加功能，而是让两名学生真的同时走聊天链路，检查传入业务服务的身份和返回的数据。

### 怎样安排测试

我们在一个 Spring 容器中共享真实 Service 和四个 Tool，HTTP 请求经过真实 JWT 校验，会话与历史通过 JDBC 存取。只替换外部文件、培养方案、Python 和模型边界；没有调用收费模型或运行中的数据库。

A 和 B 的专业、年级、培养方案、成绩单、课程、成绩和会话历史都不同。8 个工作线程各持有自己的会话，每个连续发送 10 次，共 80 次交错聊天。每轮让 8 次 Python 分析同时到达屏障，实际观测到 8 个调用同时在途，避免代码实际上串行运行却说“测了并发”。

测试检查 Python 收到的是谁的学号、文件和培养方案，模型收到的是谁的 Tool DTO，答案和进度是否混进另一名学生的内容。80 个请求必须拥有 80 份独立 Context / Snapshot，而每一轮的四个 Tool 又必须共用一次分析。同样的 Tool Call ID 在不同请求中重复使用，也不能互相触发去重错误。

### 其余容易遗漏的场景

B 拿到 A 的会话 ID，普通和流式入口都在调用 Agent 前返回 404，Agent、模型与 Tool 完全不执行。另做同一个请求四工具并发加顺序调用，只允许一次 Python 分析；下一次请求把 GPA 从 2.8 改成 3.1，必须读到新数据，旧 Snapshot 保持原值。

历史声称 GPA 4.0、本轮 Tool 2.8、模拟模型仍输出 4.0，最终仍采用本轮证据。两名学生同时接收流式结果时，概览卡片、近期课程、趋势和会话 ID 分别归本人。A 分析失败而 B 成功时，A 的失败只在本请求缓存，不会重试，也不会让 B 失败。全部普通查询都检查不保存成绩单、不生成正式预警。

### 结果和取舍

新增 7 个测试场景通过；最终后端全量 150 项中 148 项通过、2 项真实模型测试默认跳过。没有发现需要修复的可变单例状态，因此本轮没有修改生产代码或整体锁住 Service。完整隔离矩阵、运行命令和测试范围见 [并发隔离说明](./backend/docs/agent-concurrency-isolation.md)。

这些测试证明受覆盖的调用边界保持隔离，不代表已经跑过生产压力或真实模型准确率验收。实际 MySQL、Python HTTP 服务的内部并发和真机网络仍需要单独测试；本次不改数据库、不重启服务。

### 面试时可以怎样讲

**我没有只看接口返回 200，而是在真实 JWT、共享 Spring 单例和 JDBC 会话链路下让两名学生交错发送 80 次请求，检查业务参数、工具结果、历史、流式证据和答案归属。每轮共享一个分析快照，下一轮重新创建，越权在模型调用前拒绝；普通查询不写正式预警。并发状态都归请求对象，Service 只持有依赖，所以不同学生没有共享“当前用户”状态。**
