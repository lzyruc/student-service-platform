# 学业 Agent GPA 校验修复与评测交付

整理日期：2026-11-07（Asia/Hong_Kong）。真实复测已于2026-11-06完成；11月7日恢复后只做本地回归、只读复核和材料整理，未再次调用外部模型。

结论：修复了“GPA下降2.25”被误当成GPA水平2.25的校验问题。相同公开模型原文的2条配对回放均由整段误拦截变为原文完整保留；原主矩阵真实重采样中，3个失败trial变成待审，7个通过维持通过。**待审不是通过，不宣称准确率100%或提升比例。**

## 本轮范围与修改文件

本轮唯一改动的生产业务文件是 [AcademicAnswerGrounding.java](../../../../backend/src/main/java/com/college/student_service_platform/agent/AcademicAnswerGrounding.java)。未改Tool、风险算法、会话/JWT、数据层、提示词、模型配置、前端、缓存或简历。

| 文件 | 本轮用途 |
| --- | --- |
| `backend/src/main/java/.../agent/AcademicAnswerGrounding.java` | 按水平/变化量/方向/官方与学期类型匹配本轮证据 |
| `backend/src/test/java/.../AcademicAnswerGroundingTest.java` | 原4项保留，新增8项测试方法；每项含多组正反例 |
| `backend/src/test/java/.../AcademicEvaluationEvidenceTest.java` | 原缺陷特征用例更新为验证下降幅度及课程完整保留 |
| `backend/src/test/java/.../agent/LegacyAcademicAnswerGrounding.java` | 只在测试目录保留旧实现；仅类名/构造名改名，不进生产包 |
| `backend/src/test/java/.../AcademicGroundingReplayTest.java` | 用已保存的公开模型原文和真实DTO离线配对，不请求模型 |
| 本目录 `baseline-preservation.json`、3个 `*.before.java.txt` | 95个旧文件封存哈希及3份修改前源码逐字节归档 |
| 本目录 `replay-v2/`、`related-tests.log`、`full-tests.log` | 配对回放及修复后的实际Java测试证据 |
| 本目录 `collect_round.py`、`evidence-v1/` | 只读核对既有评分与汇总，导出前后对比和原5项待审材料；非新评分器 |
| 本目录 `scoring-tests.log`、`collector-negative-checks.json` | 既有22项评测器测试及汇总入口拒绝覆盖/路径越界的验证 |
| 本目录 `REPORT.md`、`COMPLETED_PROGRESS.json` | 本轮交付和最终进度 |
| 下列3个独立新run目录 | 两批真实复测、1批无模型工程分组回归；不覆盖旧结果 |

此前已有的请求内快照、预算/超时、所有权、并发隔离、usage采集、评测runner都不是本轮新增成果；当前工作树还存在上一轮和UI修改，未混入本轮修复的成果说明，也未提交/推送。

## 修复规则及边界

旧代码将官方GPA、学期GPA和学期变化量都放入一个无类型集合。新代码分别保留官方水平集合、学期水平集合、带方向的学期变化记录。数值仍使用 `BigDecimal`：精确值及原有HALF_UP两位/四位格式兼容，去除末尾0；不使用double近似比较。

以官方2.63、学期0.9、变化量-2.25/DOWN为例：

| 表述 | 匹配对象 | 结果 |
| --- | --- | --- |
| GPA下降2.25 / 下降了2.2500 / 加权GPA下降幅度为2.25 | 非负幅度按DOWN归一化成-2.25，并核对DOWN | 保留 |
| GPA上升2.25 | +2.25/UP | 拒绝 |
| GPA变化量为-2.25 | 带符号变化量 | 保留 |
| GPA下降至0.9 / 下降到0.90 / 降至0.9000 | 水平0.9；不当作delta | 保留 |
| GPA为-2.25 | 水平值，不能借用变化量 | 拒绝 |
| GPA下降0.9 | 变化量-0.9，不能借用水平0.9 | 拒绝 |
| 官方GPA为0.9 / 学期GPA为2.63 | 类型不匹配，不能互借 | 拒绝 |
| 官方GPA下降2.25 / 官方GPA变化量为-2.25 | 本轮工具无官方GPA变化证据 | 拒绝 |

UP同样按正方向匹配，STABLE只接受有证据的带符号/零变化表述；“下降-2.25”之类歧义不通过取绝对值修复。工具给出的方向与delta符号矛盾或为UNKNOWN/UNAVAILABLE时不作为可用变化证据。兼容旧DTO省略direction时，由已给出的精确delta符号确定方向；不由LLM计算新风险。

识别范围是紧邻GPA/绩点的“官方（的）（加权）”“本/该/当前/上一/上学期（的）（加权）”“加权”等限定词，以及直接水平、上升/下降幅度、变化量、升/降至等结构。英文保留原有 `is/was/equals/of` 水平表达；只去除Markdown星号后匹配，原回答内容不改写。

**尚未解决：**无局部限定的水平表述，只能在本轮已取得的官方/学期水平中匹配；不跨句/表格绑定“某具体学期”与数值，不解析所有远距离修饰、中文数字、间接或英文变化表述。“下降至0.9”仅核对终点水平，未额外证明下降的历史关系；多个学期数据同时存在时也未逐项绑定学期标签。未匹配到的自然语言不应被宣称已完成事实验证。出现已识别且不受证据支持的数字时，原整段保护分支仍保留；本轮没有做局部句子修复或额外模型修复调用。

## 保留基线与可比性

- 原主基线：[`20261106-204134-baseline-dee37`](../results/20261106-204134-baseline-dee37/REPORT.md)。所有旧结果和报告保持原样。
- 定向：[`20261106-215409-grounding_targeted-22544`](../results/20261106-215409-grounding_targeted-22544/REPORT.md)。N02、M01各2次。
- 主矩阵：[`20261106-215525-grounding_full-b5a53`](../results/20261106-215525-grounding_full-b5a53/REPORT.md)。13项任务、15trial；N02/S01各重复1次，共18次chat HTTP。
- 工程分组：[`20261107-133102-grounding_regression-f3a8d`](../results/20261107-133102-grounding_regression-f3a8d/REPORT.md)。5组、61项JUnit；无外部模型。

95项封存中，92个旧评测文件（任务、fixture生成器、评分器、runner、原始记录、报告等）哈希全部保持不变；3个源码/测试按授权变更，修改前归档与封存哈希逐字节匹配。详见 [integrity.json](./evidence-v1/integrity.json)。

代码HEAD仍为 `5d1fe0e7ceb11b86e784eff200b6abe099dfa336`，工作区dirty；不能用相同HEAD冒充相同源码。新run保留生产源码指纹，Grounding修复前后哈希可核对。任务/提示词/模型配置保持：

| 项目 | 本轮核对 |
| --- | --- |
| 有效dataset SHA256 | `0b0afb6cd0e4a796c4505a2dc775f93b5887b67685378c055073109c00989fb1` |
| scorer SHA256 | `26658cfb882c29926c66653022646a5a406ac38bec295ea23219e1123651bba8` |
| runner SHA256 | `0d2812bb05865c79048a7cf139d3cb097ba325f643a25735d763e8763ecf5b6a` |
| academic.md SHA256 | `5f2e008ea87115b5a528f8fa8a9f36d06f6b002bf588e7aab0bd60d634e9097e` |
| 模型 | 原DeepSeek配置 `deepseek-flash`；实际响应名一致；未修改温度/输出限制 |
| fixture | `academic-eval-fixture-v1`，生成器未改；基础/更新事实、培养方案一致 |

原主基线最初manifest的runner/scorer/dataset哈希与当前不同，是**上轮已记录的评分校准**；本次比较使用其已封存的 `final_grader` 和 `grading-dataset.json`。旧manifest与原始记录不改写。本轮没有评分器修复；对原15、定向4、新主矩阵15共34个trial只读重新计算，所有verdict及逐项checks与已有scored.json完全一致，并复算分类分母及usage汇总。

合成PDF由原PyMuPDF生成器每次新建文档，PDF文档ID和字节哈希不同；没有把它们写成字节相同。保留了每批实际PDF哈希，并额外核对旧/新3个PDF的逐页文字及文字位置相同、培养方案JSON哈希相同、原解析器得到的基础/更新学业事实相同。这是同一事实fixture，不是重新修改成绩/课程后的任务。

采集harness在上轮主基线之后已增加公开assistant消息保存、独立日志配置和缺密钥预检；本轮未改harness。与旧主基线的这3处差异已有源码快照可查，尤其日志差异与外部模型波动会影响耗时。不得把耗时变化归因于GPA校验的性能优化。

## 离线配对回放

先完成离线回放，再执行修复后相关/完整测试，然后才启动付费定向与主矩阵采样；回放本身没有外部模型调用。

遍历旧pilot、原主基线和旧badcase_capture的已存记录，共23个请求记录：2个有可用公开模型原文、19个没有原文、2个未进入Agent。只有有原文的2个N02进入配对；旧主基线N02/M01均没有原文，未拿最终fallback假装模型输出。

| 样本 | 旧逻辑 | 新逻辑 | 原有课程保留 |
| --- | --- | --- | --- |
| badcase_capture N02-1 | 整段替换；与当时HTTP最终回答一致 | 原文完整保留 | 从两门均丢失到两门均保留 |
| badcase_capture N02-2 | 整段替换；与当时HTTP最终回答一致 | 原文完整保留 | 从两门均丢失到两门均保留 |

原文含学期GPA0.9、下降2.25，DTO提供-2.25/DOWN，以及操作系统58分、计算机网络65分。完整前后文本与工具证据见 [paired.json](./replay-v2/paired.json)、[summary.json](./replay-v2/summary.json)。旧实现是修改前文件仅重命名的测试副本，已核对。这个配对控制住了模型原文，可以说明本次校验改动纠正了这2条误拦截；真实重采样则有输出波动，不能全部归因于该改动。

## 测试执行结果

| 检查 | 实际结果 | 模型模式/证据 |
| --- | --- | --- |
| 旧原文配对回放JUnit | 1通过，2个实际样本 | 无网络；backend/target/grounding-paired-replay.log |
| 相关GPA/事实/权限/会话/预算/并发/Tool/DeepSeek客户端测试 | 95通过，0失败 | Mock/本地；[related-tests.log](./related-tests.log) |
| 完整后端 | 163项：159通过、4跳过，0失败 | [full-tests.log](./full-tests.log) |
| 本次恢复后的工程矩阵 | 5组通过；其61项JUnit全部通过 | 独立regression run；不当作模型工具选择准确率 |
| 既有评测runner/评分/汇总/fixture测试 | 22通过 | [scoring-tests.log](./scoring-tests.log) |
| 只读评分复核 | 34trial全部与已存verdict/checks一致 | [integrity.json](./evidence-v1/integrity.json) |
| 新材料收集器负向检查 | 已有目录拒绝覆盖、越界输出参数拒绝，原文件不变 | [collector-negative-checks.json](./collector-negative-checks.json) |

完整测试4个跳过是：显式开启的真实评测入口1个、离线回放入口1个、已有DeepSeekLiveSmokeTest 2个。前两个分别已显式执行成功，跳过不冒充通过；现有两个协议live smoke未另外开启。本轮真实业务采样已单独执行，不能拿Mock回归代替它。

针对性测试覆盖：下降正幅度与带符号delta允许；反方向、错误幅度、水平/delta互借、官方/学期互借拒绝；精确/等价小数及两位四位舍入兼容；方向缺失兼容与矛盾/不可用方向处理；错误数值、历史伪造4.0、无本轮证据仍受保护；原正确答案、日期、空回答回归。没有只测一个“下降2.25”正例。

工程隔离矩阵（原有实现的回归，不是新增隔离机制）：

| 组 | 核心约束 | 本次结果 |
| --- | --- | --- |
| E01 下游 | 失败/超时不伪造成功；同request失败快照不隐式重解析 | PASS |
| E02 预算 | 工具与轮次边界；耗尽后不无上限执行 | PASS |
| E03 协议 | 非法工具/参数、重复调用标识、模型协议异常 | PASS |
| E04 隔离 | 多用户交错执行、ownership先于Agent、history不充当事实 | PASS |
| E05 快照 | 同请求single-flight；下一请求读更新数据 | PASS |

## N02 / M01 定向与真实重采样

定向4trial全部满足确定性规则，仍为4待审；6次HTTP聊天，12次真实模型调用。主矩阵另含N02两次、M01一次：因此**本轮合计N02四次、M01三次**，而不是7个独立任务。

N02四次均保存公开模型原文；本轮学期0.9、delta=-2.25/DOWN与“下降2.25”匹配，最终回答等于原文，操作系统58分及计算机网络65分没有被移除。相对于旧主基线两次及旧失败补采两次的稳定失败，本轮这些样本未再次触发同一误拦截；不推断所有未来表达均正确。

M01三次均使用真实服务端history，首轮 `semester=latest` 得秋季0.9/-2.25；追问 `semester=previous` 得上一春季3.15/-0.7，未通过和低分课程都为空。6个回答的公开原文与最终回答一致，原文中“下降2.25”和“下降0.7”均有工具证据。本轮没有M01规则失败可进一步定位。

**旧M01的限制：**只保存了工具正确、最终答复落入GPA保护fallback的记录，缺少模型原文，无法核实具体触发措辞。不能断言其两个回合当时都使用了与N02完全相同的措辞，也不能用本轮新采样补造旧原文。

逐轮原文、最终回答与Tool DTO见 [targeted-evidence.json](./evidence-v1/targeted-evidence.json)；完整HTTP、model usage和执行轨迹仍以各run的 observations.jsonl 为准。

## 与原主基线同口径对比

| 指标 | 原主基线 | 本轮主矩阵 |
| --- | --- | --- |
| 独立任务 / 实际执行 | 13 / 13 | 13 / 13 |
| Trial / 实际执行 | 15 / 15 | 15 / 15 |
| PASS / FAIL / REVIEW_PENDING / NOT_EXECUTED | 7 / 3 / 5 / 0 | 7 / 0 / 8 / 0 |
| 独立任务判定 PASS / FAIL / REVIEW_PENDING | 6 / 2 / 5 | 6 / 0 / 7 |
| chat HTTP / 实际模型调用 | 18 / 30 | 18 / 30 |
| Tool / Python分析调用 | 17 / 10 | 17 / 10 |

按类别（列顺序均为通过/失败/待审/未执行）：

| 类别 | 原主基线trial | 新主矩阵trial |
| --- | --- | --- |
| 整体/近期/趋势/组合 | 0 / 2 / 3 / 0 | 0 / 0 / 5 / 0 |
| 多轮指代/历史旧值 | 0 / 1 / 1 / 0 | 0 / 0 / 2 / 0 |
| 资料缺失 | 2 / 0 / 0 / 0 | 2 / 0 / 0 / 0 |
| 澄清/能力边界 | 2 / 0 / 0 / 0 | 2 / 0 / 0 / 0 |
| 请求其他学生数据 | 2 / 0 / 0 / 0 | 2 / 0 / 0 / 0 |
| 他人会话 | 1 / 0 / 0 / 0 | 1 / 0 / 0 / 0 |
| 数据更新 | 0 / 0 / 1 / 0 | 0 / 0 / 1 / 0 |

逐trial：

| Trial | 原主基线 | 本轮主矩阵 |
| --- | --- | --- |
| N01-1 | REVIEW_PENDING | REVIEW_PENDING |
| N02-1 | FAIL | REVIEW_PENDING |
| N02-2 | FAIL | REVIEW_PENDING |
| N03-1 | REVIEW_PENDING | REVIEW_PENDING |
| N04-1 | REVIEW_PENDING | REVIEW_PENDING |
| M01-1 | FAIL | REVIEW_PENDING |
| M02-1 | REVIEW_PENDING | REVIEW_PENDING |
| B01-1 | PASS | PASS |
| B02-1 | PASS | PASS |
| B03-1 | PASS | PASS |
| B04-1 | PASS | PASS |
| S01-1 | PASS | PASS |
| S01-2 | PASS | PASS |
| S02-1 | PASS | PASS |
| U01-1 | REVIEW_PENDING | REVIEW_PENDING |

S02在HTTP404处拒绝，模型/Tool/分析均0次；S01是模型识别拒绝（每trial1次模型调用），Tool/分析0次，不能写成模型之前拒绝。N04组合3个工具仍只分析1次；缺成绩单/方案任务分析调用0次。这些是既有边界保持有效的证据。

工程分组原确认运行和本轮均5组PASS，分开于上述真实模型指标；不相加计算模型准确率。

## 实际模型调用、usage与耗时

以下usage全部来自API实际字段；42个本轮响应完整提供prompt/completion/total及缓存字段，没有按字符估算。

| 批次 | 模型HTTP尝试/成功 | prompt | completion | total | cache hit / miss |
| --- | --- | --- | --- | --- | --- |
| 原主基线（对照，不计本轮开销） | 30 | 94681 | 4367 | 99048 | 83072 / 11609 |
| 本轮定向 | 12 | 39930 | 2119 | 42049 | 34048 / 5882 |
| 本轮主矩阵 | 30 | 95231 | 4411 | 99642 | 83838 / 11393 |
| 本轮两批合计 | 42 / 42 | 135161 | 6530 | 141691 | 117886 / 17275 |

离线回放、工程回归、只读评分复核及文档导出模型调用均0；11月7日续作外部模型调用0。供应商cache token字段不是本轮实现业务缓存。费用、模型首token时间、Python内部纯解析CPU时间不可用，不以0补填。

| 批次 | 请求组 | 样本数 | P50 ms | P95 ms |
| --- | --- | --- | --- | --- |
| 原主基线 | HTTP200/有效业务状态 | 17 | 4032.17 | 4695.11 |
| 原主基线 | 规则满足（含待审、预期404） | 14 | 3301.63 | 4653.25 |
| 原主基线 | 规则失败 | 4 | 4100.43 | 4508.12 |
| 原主基线 | 预期越权404 | 1 | 38.45 | 38.45 |
| 本轮定向 | HTTP200/有效业务状态 | 6 | 3339.48 | 4757.30 |
| 本轮定向 | 规则满足（含待审、预期404） | 6 | 3339.48 | 4757.30 |
| 本轮定向 | 规则失败 | 0 | 不可用 | 不可用 |
| 本轮定向 | 预期越权404 | 0 | 不可用 | 不可用 |
| 本轮主矩阵 | HTTP200/有效业务状态 | 17 | 3181.58 | 4677.64 |
| 本轮主矩阵 | 规则满足（含待审、预期404） | 18 | 3042.36 | 4647.85 |
| 本轮主矩阵 | 规则失败 | 0 | 不可用 | 不可用 |
| 本轮主矩阵 | 预期越权404 | 1 | 35.64 | 35.64 |

“规则满足”包含待审和符合预期的404；这不是最终验收成功率。17个HTTP200之外的1个404是预期的ownership拒绝，不是异常失效。新批没有规则失败样本，其P50/P95为不可用，不能记为0。

阶段累计时间（数量/累计秒）：

| 批次 | chat HTTP | 模型客户端 | Tool | 分析HTTP客户端 |
| --- | --- | --- | --- | --- |
| 原主基线 | 18 / 53.135 | 30 / 50.691 | 17 / 1.156 | 10 / 0.560 |
| 本轮定向 | 6 / 21.910 | 12 / 20.713 | 6 / 0.706 | 6 / 0.424 |
| 本轮主矩阵 | 18 / 47.467 | 30 / 45.580 | 17 / 0.878 | 10 / 0.458 |

定向模型客户端P50/P95为1950.34/2733.34ms（n=12）；新主矩阵为1174.03/2896.80ms（n=30）。全量原始阶段统计见 [comparison.json](./evidence-v1/comparison.json)。Tool内嵌套分析，模型客户端含传输与序列化；阶段不能盲目相加。启动/建测试库的整批墙钟耗时未独立采集。这些是小型串行本地环境样本，不是生产吞吐量或SLA，不宣称性能提升。

## 待审和剩余问题

原5个待审trial：N01-1、N03-1、N04-1、M02-1、U01-1。所需用户任务、各轮完整最终回答、关键工具证据、待审原因、原rubric和待核对项已放入 [pending-original.md](./evidence-v1/pending-original.md)；配套 [pending-original.json](./evidence-v1/pending-original.json) 保留完整DTO和history供追溯。

本轮主矩阵8个待审trial：N01-1、N02-1、N02-2、N03-1、N04-1、M01-1、M02-1、U01-1；定向另4个待审不作为新独立任务。对应新回答、证据和未评分维度见各run的 `scored.json` / `review-template.json`。当前没有用户/指定验收人的评分，也没有调用LLM Judge；Codex生成这份定位报告不等于人工验收。解释相关性、是否完整支持关键结论和不确定性仍为UNKNOWN。

本轮修复了可复现的误拦截，但没有解决通用语义事实校验、具体学期绑定、所有格式解析；有明确错误数字时仍整段替换。两条配对不足以证明所有表达都正确，真实数据复杂PDF和生产并发不在这套小型合成基线覆盖内。没有缓存/性能改动；本轮到此停止扩展。

## 复现与证据位置

项目根目录：`D:\agent开发准备\简历项目\student-service-platform`。复用原Java17/Maven缓存、Python虚拟环境、localhost MySQL和现有配置；不覆盖.env/local-config，不执行任何DROP初始化脚本。真实链路为真实JWT登录、独立评测库、嵌入Tomcat、原业务Tool、专用Python HTTP/PDF和DeepSeek；只涉及合成测试身份。

本轮定向专用库 `student_agent_eval_v1_a34f7c25b4`、主矩阵专用库 `student_agent_eval_v1_165766b800`，库及backend/target合成文件保留供核对；业务库未重建。专用服务随runner退出，清理如有需要应仅按各database.json精确名称进行，不执行本报告未提供的批量删除。

已执行的真实命令（均已完成，不为查看结果重复计费）：

```powershell
.\python-services\.venv\Scripts\python.exe python-services/evaluation/agent/run.py --mode live --cases N02,M01 --repeat N02,M01 --extra-repeats 1 --model-budget 16 --label grounding_targeted
.\python-services\.venv\Scripts\python.exe python-services/evaluation/agent/run.py --mode live --repeat N02,S01 --extra-repeats 1 --model-budget 48 --label grounding_full
```

无模型命令：

```powershell
.\python-services\.venv\Scripts\python.exe python-services/evaluation/agent/run.py --mode regression --label grounding_regression
.\python-services\.venv\Scripts\python.exe -m unittest discover -s python-services/evaluation/agent -p test_scoring.py -v
# 只读采集既有结果，输出目录必须新建，不能覆盖 evidence-v1
.\python-services\.venv\Scripts\python.exe python-services/evaluation/agent/grounding_fix/collect_round.py --output evidence-<新名称>
```

Java相关命令由原 `run.maven()` 执行 `backend/mvnw.cmd -o -Dtest=AcademicAnswerGroundingTest,AcademicEvaluationEvidenceTest,SingleAgentServiceTest,AgentConversationServiceTest,AgentChatControllerTest,AcademicToolExecutorTest,AgentConcurrencyIsolationTest,DeepSeekClientTest test`（使用原evaluation日志配置）；完整为 `mvnw.cmd -o test`。两份日志保存实际汇总，避免误用后续Surefire覆盖后的报告。

离线回放已执行 `AGENT_GPA_REPLAY=1` + `mvnw.cmd -o -Dtest=AcademicGroundingReplayTest test`。重放时可设置 `AGENT_GPA_REPLAY_DIR` 到一个新的目录（从backend计算相对路径）；默认replay-v2已存在，会拒绝覆盖。其冻结的旧run列表不会自动纳入新采样。

旧根README/BASELINE_REPORT按封存要求不改；其“尚未修复”等描述反映当时基线状态，本次修复的最终依据是本报告和独立新run。所有API Key、JWT、密码不得进入结果；最终完整性/敏感信息/临时服务检查见本目录 `final-verification.json`。
