# 成绩单解析与学业预警

## 业务链路

小程序上传成绩单 → Java 从 JWT 获取学生身份 → 查询学生专业和年级 → 选择数据库中对应的最新培养方案 → Python 解析 PDF 并比对 → Java 保存成绩单文件及分析记录 → 小程序显示结果。

当前小程序将保存与分析分为两步：首次上传时先保存 PDF；之后进入页面读取服务器上的成绩单信息，点击“课程分析”即可使用已保存文件。无需再次选择文件，也不依赖微信临时文件路径。

学生客户端提交的学号不能改变分析归属。学生年级用于选择培养方案；成绩单实际年级和已完成学期用于确定成绩单进度。两者不一致时在报告中提示，不改写学生档案。

## 成绩单保存与复用（2026-10-02）

| 方法 | 地址 | 功能 |
| --- | --- | --- |
| POST | `/api/student/transcript` | 单独上传并保存成绩单，multipart 字段为 `file` |
| GET | `/api/student/transcript` | 读取当前登录学生最后上传的成绩单信息 |
| POST | `/api/student/warning/analyze-saved` | 使用已保存的成绩单和最新培养方案重新分析，无需传文件或文件 ID |
| POST | `/api/student/warning/analyze` | 保留原有上传并分析接口，成功后同样保存成绩单 |

这三个新入口均需要 JWT。学生身份取自 Token，客户端传入其他学号不能改变文件归属。只允许读取本人上传、`business_type=transcript` 的文件。

保存接口只依赖 Java 和 MySQL，不需要 Python 服务成功运行。检查 PDF 扩展名、文件头及不超过 20MB 的大小后保存；是否能解析出课程在分析时判断。上传新成绩单后，按 `created_at DESC, id DESC` 选择最新文件。旧文件保留，已有历史分析仍指向原文件。

存储复用现有结构，无需新增表或修改数据库：

- `backend/uploads` 保存 PDF 原文件。
- MySQL `t_file` 保存文件信息、上传人及业务类型，文件内容不以 BLOB 写入 MySQL。
- MySQL `t_warning_record` 保存每次分析结果、解析后的课程，以及对应的成绩单文件 ID 和培养方案 ID。

读取接口返回 `fileId`、`originalName`、`fileSize`、`uploadedAt`、`available`，不返回磁盘路径。未上传时返回 `data=null`；数据库有记录但原文件不可读时，`available=false`，页面提示重新上传。

以前通过分析接口成功保存的成绩单会自动被识别，通常不需再上传。再次分析只新增分析记录，不复制 PDF、不新增 `t_file` 文件行；每次重新查询学生当前专业、年级和最新培养方案。原文件不可用或尚未上传时返回明确错误，不创建空分析记录。

小程序页面提供“课程分析”和“更新成绩单”。页面重新进入时查询服务器，上传和分析期间防止重复操作；分析失败后保留已保存成绩单，可稍后重试。此能力已封装为后端服务，后续学业 Agent 可复用读取和分析能力。

## 2026-10-01 修复与实测

用户提供的双页成绩单是整页十列表格。旧解析器按页面中线切成两栏，只接受“课程名称、带小数的学分、成绩、绩点”四列文本，实际识别了 0 门课程；后续将培养方案所有核心课判为缺失，形成错误补修建议。

新版以列名定位课程名称、学分、最终成绩和学分绩点，合并单元格内换行，保留跨页的学期归属。学期汇总行在课程之后，不能当作下一批课程的标题。旧双栏四列文本格式保留为备用解析方式。

本次对原 PDF 的直接解析结果：

| 指标 | 修复前 | 修复后 / PDF 汇总 |
| --- | ---: | ---: |
| 识别课程 | 0 | 45 |
| 已获学分 | 0 | 117 |
| 全学期平均绩点 | 0 | 3.65 |
| 按最终成绩未通过课程 | 无有效识别 | 0 |

例如高等代数Ⅱ的期末成绩为 59，但最终成绩为 77，应判为通过。表格“学分绩点”是学分乘以单科绩点，不能直接当作单科绩点；全学期平均绩点优先采用成绩单最后的官方汇总。

该 PDF 标注 2024 级，包含至 2025-2026 学年春季的成绩，进度为第 4 学期。使用 2026 级账号测试时仍选账号对应的培养方案，同时提示年级不同。上述实测验证解析结果，不代表已逐项核对运行中 MySQL 的全部培养方案课程。

## 2026-10-03 实际 Agent 对话：P 课程导致 GPA 趋势错误

实际测试“比较最近四个学期成绩是进步还是下降”时，2025-2026 春季 GPA 被报告为 3.5185，而原成绩单该学期官方汇总是 3.72。直接重新解析已上传的同一 PDF，可以复现截图所有统计值，确认错误来自确定性统计，不是模型改写数字。

原因是“思政实践课”的最终成绩为 `P`，学分为 2，表格中的“学分绩点”也为 2。旧解析把它除以学分得到 1.0，又把该记录参与 GPA 加权：总点数 95 / 总学分 27 = 3.5185。排除该通过制课程的 GPA 样本后，(95 - 2) / (27 - 2) = **3.72**，与成绩单一致。

修复分两层：解析器不把明确的 `P/通过` 记录输出成数值 GPA；统计函数即使收到旧数据中的错误 GPA，也排除这类记录。保留通过状态、已获学分、修读课程数和学期归属。不能因此排除所有字母成绩：`A/A-` 仍可使用原始绩点，`F` 的真实零 GPA 和数值挂科记录仍参加原有统计。

同一份实际成绩单重新核对：

| 学期 | 修复前课程加权 GPA | 修复后课程加权 GPA | PDF 官方学期 GPA |
| --- | ---: | ---: | ---: |
| 2024-2025 秋季 | 3.5375 | 3.9000 | 3.90 |
| 2024-2025 春季 | 3.3212 | 3.3938 | 3.39 |
| 2024-2025 国际小学期 | 3.3000 | 3.3000 | 3.30 |
| 2025-2026 秋季 | 3.7065 | 3.7065 | 3.71 |
| 2025-2026 春季 | 3.5185 | 3.7200 | 3.72 |

课程加权统计保留四位小数，PDF 官方汇总通常保留两位，回答时必须说明来源。最新春季相对秋季的 GPA 变化从 -0.1880 / DOWN 修正为 **+0.0135 / UP**。

春季共 10 门课程、27 学分，其中 1 门为 `P`；GPA 的有效样本是其余 9 门、25 学分。数值最终成绩的算术平均为 **88.1111**，使用 9 门百分制最终成绩，不使用 `P`、期末考试单列成绩或自行换算的分数。它并不是成绩单直接给出的官方均分；相对秋季 88.2 略降 0.0889。正确表述是“GPA 略升，数值最终成绩均分基本持平、略降”，不能笼统说所有成绩下降。

统计注释说明 GPA 参与率仍以所有课程记录／学分为分母，按规则排除通过制课程不等于成绩缺失。原成绩单 45 门、117 已获学分、官方整体 GPA 3.65 均保持不变。提示词补充样本口径与不同指标方向，并要求纯文本／简短列表，避免小程序直接展示 Markdown 表格和星号。

回归增加了真实春季数据形状、P 的不同写法、全通过制学期与零分失败保留等场景；原解析、风险与学期规则同时复查。本轮 31 项 Python 学业测试和 37 项 Java Agent／统计／工具测试全部通过，未调用真实模型或写运行中的数据库。启用需要重启 `8002` 和 Java（加载更新的提示词），不需要重传 PDF、重建数据库或新增正式预警记录。聊天页面选择“新对话”后重新提问；旧气泡不会自动改写。

## 核心课程与名称

- 核心课程比对包含类别名称含“核心”的课程、部类基础课、部类共同课、思想政治理论课。部类基础课中的程序设计等课程也会参与缺课及开课学期判断。
- 原“部类核心课”统一展示为“部类共同课”；已有 JSON 在读取时规范化，保存时写入新名称，无需重建数据库。
- Java 向 Python 同时发送课程名称、课程类别及 `offeredAt` 开课学期，保留旧的 `core_courses` 字段。
- 名称匹配统一全半角、空白及罗马数字，例如 `数据结构与算法Ⅰ` 与 `数据结构与算法I` 可匹配；I 与 II、A 与 B 仍是不同课程。不使用宽泛模糊匹配自动抵扣另一门课程。

## 报告字段和规则

| 字段 | 含义 |
| --- | --- |
| `core_courses` | 已通过的培养方案核心课程 |
| `missing_core_courses` | 已到计划开课学期，但未见通过记录且未列入未通过课程；需要核实，不直接断言必须补修 |
| `failed_courses` | 存在未通过成绩且没有通过记录的课程；同一课程后续通过后清除旧失败 |
| `pending_core_courses` | 尚未到计划开课学期的核心课程，不计为缺口 |
| `unscheduled_core_courses` | 未见通过记录，但缺少可用开课学期或成绩单进度，不能自动判定缺课 |
| `unknown_score_courses` | 最终成绩为空或无法识别，不默认判为通过 |
| `analysis_notes` | 年级差异、未来课程、无法判定的课程等说明 |
| `completed_semester` | 从成绩单实际年级及学期推算的已完成学期 |
| `issue_course_count` | 去除重复后的未通过与已到计划学期未见通过记录课程数 |

重修通过课程只计一次学分；同一课程不会同时计入缺课和未通过。PDF 未识别到有效课程时返回错误，不生成“全部核心课程缺失”的报告，也不保存这类错误分析。

现有严重预警阈值保留：3 门及以上未通过课程或 2 门及以上已到计划学期未见通过记录的课程为严重预警；其余存在未通过或已到计划学期未见通过记录的情况为一般预警。这是项目当前的提示规则，不代表学校正式学业预警标准。扫描版 PDF 目前需要先转为可提取文字的成绩单。

## 本地回归与启用

在 `python-services` 目录运行：

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests -p test_academic_analysis.py -v
```

在 `backend` 目录运行：

```powershell
.\mvnw.cmd '-Dtest=AcademicAnalysisServiceTest,StudentTranscriptServiceTest,TrainingPlanServiceTest,AcademicWarningProxyControllerTest,AcademicWarningClientTest,WarningRecordServiceTest' test
```

在项目根目录运行：

```powershell
node --test miniprogram/tests/academic.test.js miniprogram/tests/transcript.test.js
```

启用修复需要重启 `8002` 学业预警 Python 服务和 Java 新包，并重新编译小程序。使用同一成绩单重新分析，会生成新的预警记录；已有错误历史记录不会自动重算。完整启动步骤见 [根 README](../../README.md)。

仅启用本次成绩单保存与复用功能时，更新 Java 包并重新编译小程序即可；Python 代码未变，保留已运行的 `8002` 服务。数据库和原上传目录继续使用，不需要重建或重导入。

## Academic Skill 第一步：业务 Service 与只读快照（2026-10-02）

已将 `AcademicWarningProxyController` 中的学生档案查询、培养方案转换、Python 分析调用和记录保存提取到 `AcademicAnalysisService`。Controller 负责从现有 JWT 上下文确定身份、委托 Service 和保持原有响应格式；接口地址、学生身份规则和分析算法不变。

正式课程分析与 Agent 普通查询使用不同入口：

| 入口 | 行为 |
| --- | --- |
| `analyzeUploadedAndSave(authenticatedStudentNo, file)` | 用于现有上传并分析接口；成功后保存 PDF 和正式预警记录 |
| `analyzeSavedAndSave(authenticatedStudentNo)` | 用于现有“课程分析”按钮；复用已有 PDF，成功后新增正式预警记录 |
| `createReadContext(request)` → `getSnapshot()` | 供后续 Agent 查询使用；读取本人已保存 PDF 并分析，不保存文件、不调用 `WarningRecordService.saveFromPythonResponse()` |

后续 Agent 在每个 HTTP 请求中只创建一次 `AcademicAnalysisReadContext`，将这个对象传给本请求整个 Tool Calling Loop 内的所有 Academic Tool。不要在每个 Tool 内重新创建上下文；同一 Conversation 的下一轮 HTTP 请求也要重新创建：

```java
var academicContext = academicAnalysisService.createReadContext(request);
// 所有 Academic Tool 接收同一个 academicContext。
var assessmentSnapshot = academicContext.getSnapshot();
var recentPerformanceSnapshot = academicContext.getSnapshot();
// 两次返回同一个 AcademicAnalysisSnapshot，仅调用一次 Python。
```

上下文从已验证的 `AuthContext` 取得学生身份，只允许 `student` 角色；不接收模型生成的学号、文件 ID 或培养方案 ID。创建上下文不读取成绩单；第一次需要快照时才分析。并发读取也仅执行一次分析，失败在当前上下文内保留，不因其他 Tool 调用自动重试。请求结束后丢弃上下文，不作为跨用户、跨请求或长期对话缓存。

`AcademicAnalysisSnapshot` 保存课程、完整报告和成绩单／培养方案来源信息，保留 `official_gpa`、`completed_semester`、`analysis_notes` 等历史预警表没有完整保存的字段。JSON 在构建及读取时防御性复制，避免一个 Tool 修改结果影响其他 Tool。快照是内部证据对象，后续每个 Tool 应投影成专用 DTO，不向模型暴露身份凭据或文件磁盘路径。

**解析次数边界**：现有 `AcademicWarningClient` 每次调用都会上传 PDF 到 Python；Python 会重新解析，使用的临时文件在请求结束时删除。同一次 Agent HTTP 请求／Tool Calling Loop 内共享一个上下文和 Snapshot，最多解析一次 PDF；下一轮聊天请求仍会重新创建 Context，首次需要快照时重新解析。只查背景时不解析。当前没有跨请求缓存，也不从不完整的历史预警记录重建完整报告。这里“只读”指不改变业务文件和数据库记录，Python 临时文件处理仍保留。

上下文是调用方显式创建、传递的普通 Java 对象，没有自动请求作用域或会话缓存。第四步的 `SingleAgentService` 已将它保存在请求处理方法的局部变量中，请求结束后释放引用；不能存入单例字段、HTTP Session 或 Conversation。未来 Conversation 消息持久化与本请求分析上下文是两个独立机制。

最新学期选择、低分阈值和趋势计算将在下一步用确定性代码实现，LLM 只识别意图和解释结果。本轮未新增 Agent 对话接口、Tool Calling 或模型调用，不需要数据库迁移。

回归测试覆盖现有正式分析仍写入预警记录、Agent 只读查询不增加 `t_file`／`t_warning_record`、同请求重复及并发读取只分析一次、身份绑定、异常处理和快照防御性复制。测试使用 Mock、临时文件和 H2 内存数据库，不访问运行中的 MySQL、Python 或付费模型。

## Academic Skill 第二步：确定性统计（2026-10-02）

已新增 Python `academic_statistics.py`，现有分析接口在原 `data.courses`、`data.report` 之外返回 `data.statistics`。统计复用同一次 PDF 解析得到的课程和原预警报告，不修改原风险算法。

最新／上一学期按成绩单实际学期确定，只选择具有可判定最终成绩的学期；未知学期和全部待出分的学期不用于时间选择。低分关注为数值最终成绩 `60 ≤ 分数 < 70`。等级成绩不转换成百分制平均分，学期 GPA 按非 P/通过制记录的有效绩点和正学分加权计算；通过制课程保留学分而不参与 GPA，缺失值保持为空，真实零值正常参与。

结果包含近期关注课程、学期统计、有效数据覆盖情况、相邻有效学期的变化量和方向。历史未通过但已有通过记录不再重复作为当前待重修关注。年级差异和无法判定的信息保留；少于两个有效学期或缺少可比较的 GPA 时明确标记数据不足。

Java `AcademicStatisticsService.recentPerformance(context, semester, limit)` 和 `trend(context, lastSemesters)` 读取同一个 `AcademicAnalysisSnapshot.statistics()`，不额外调用 Python、不保存正式预警记录。`latest/previous`、关注数量 `1～10`、趋势窗口 `2～8` 均由代码验证和处理。

报告新增 `official_gpa_available` 标记；未识别到官方 GPA 与真实 GPA 为零可区分，后续 Agent DTO 应在不可用时输出空值。

完整统计口径、阶段进度和回归命令见根目录 [Agent 开发文档](../../AGENT_DEVELOPMENT.md)。Python 学业回归 28 项、Java 学业回归 34 项通过；数据库不需要迁移，本轮没有重启服务。启用时更新 Java 包并重启 `8002` 服务；若仍使用旧 Python，统计 Service 会明确提示升级。

## Academic Skill 第三步：4 个只读 Tool 与 DTO（2026-10-03）

已增加 `get_academic_context`、`get_academic_assessment`、`get_recent_course_performance` 和 `get_academic_trend`，统一由 `AcademicToolExecutor` 按固定注册表执行。输入只能是工具约定的 JSON 对象，额外字段、错误类型、越界参数和重复 JSON 字段均拒绝；模型不能指定其他学生、文件或任意接口。

`beginRequest(request)` 复用现有 JWT 身份边界，每次调用创建新上下文；后续 Agent Service 应在一次 HTTP 请求开始时调用一次，让整个工具调用循环复用。背景 Tool 只检查档案、已存成绩单和匹配培养方案，不解析 PDF；三个分析 Tool 在本请求内共用完整快照，不调用正式预警保存。快照生成后，背景 Tool 也复用快照来源信息。

输出使用 `AcademicToolDtos` 内的专用 record，经 `AcademicToolMapper` 逐字段投影。保留风险证据、核心课程不同状态、实际学期、统计覆盖和变化量；GPA 缺失为空而不是零。输出不包含学号、文件／方案 ID、原始文件名、磁盘路径或原始 Python JSON。执行状态与数据不足状态分别表达，下游异常原文不进入模型。

本轮新增 16 项 Tool 测试，Java 学业回归合计 50 项通过。没有新增 HTTP 聊天接口、接入模型、修改 Python 算法、迁移数据库或重启服务。详细输入输出、实现过程和下一步见 [Agent 开发记录与复盘](../../AGENT_DEVELOPMENT.md)。

## Academic Skill 第四步：DeepSeek 与简单工具循环（2026-10-03）

新增学生 JWT 保护的 `POST /api/student/agent/chat`。`SingleAgentService` 在请求方法中创建一次上下文，再执行“模型提出工具调用 → 受控工具执行 → 按 tool_call_id 回传 → 模型解释”的循环。下一轮请求重新创建上下文，没有跨请求缓存或 Conversation 持久化。

`DeepSeekClient` 读取现有 `python-services/.env` 的三项 DeepSeek 配置，复用政策问答的 Key、地址和模型；`backend/agent-config.properties` 提供配置路径、超时、输出长度和工具调用预算。默认最多执行 4 轮／12 次工具调用，上限后只请求总结。模型调用失败不自动重试；总耗时预算在同步调用前后检查，执行中的请求由各自超时限制。

聊天输入只允许当前问题和 user／assistant 历史消息，不接受模型或前端提供的其他身份。基础提示词要求解释程序已计算的风险、低分、学期和趋势；没有本请求的可用分析结果时，后端返回明确提示，不使用无证据的模型成绩结论。

全后端 100 项测试通过，包含四工具与 JWT 聊天入口的联合测试，以及仅使用虚构数据、两次真实模型请求的 DeepSeek 协议验证。当前已经打包新版 Java，没有重启运行中的服务、修改 Python 算法或迁移数据库。启用时重启 Java，保留已有新版 8002。小程序学业聊天页面与真实学生完整验收仍待完成。操作和开发复盘见 [根目录开发文档](../../AGENT_DEVELOPMENT.md)。


## 2026-10-04：权限拒绝与分析数据缺失分开处理

`SingleAgentService` 原先首次强制调用工具，最终没有可用分析结果时统一返回 `DATA_UNAVAILABLE`，导致“查其他同学成绩”被显示成数据不足。现在普通循环使用 `auto`，本人学业结论仍要求本轮工具证据；拒绝、能力说明和澄清通过严格的两字段控制 JSON 表达，由后端生成固定提示，不能携带任意答案、成绩或查询身份。

新增响应状态 `REFUSED`、`OUT_OF_SCOPE`、`NEEDS_CLARIFICATION`，小程序分别展示。`ACCESS_DENIED` 工具结果会立即停止剩余调用。权限仍由 JWT 和业务 Service 决定，Tool 不接受其他学号；正常拒绝不读取 Snapshot 或解析 PDF，Agent 查询仍只读。

108 项后端回归及 33 项小程序回归通过；默认跳过 2 项真实模型测试。另单独开启真实 DeepSeek 拒绝测试，截图中的问题返回 `REFUSED`，没有执行工具或读取 Snapshot，也没有向模型发送真实学生数据。已重新打包 Java。使用当前版本需重启 Java、重新编译小程序，保留当前数据库和 Python 服务。开发原因与取舍见根目录 [Agent 开发记录](../../AGENT_DEVELOPMENT.md)。
