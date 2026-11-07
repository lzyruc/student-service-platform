# 学业 GPA 校验修复：暂停进度

用户于本轮明确要求暂停、保存进度。已停止开展新工作；无需再次执行付费模型评测。

项目：`D:\agent开发准备\简历项目\student-service-platform`。当前代码未提交；不要覆盖此前未提交的评测和 UI 变更。所有命令目前需要 exec_command 的 require_escalated（默认沙箱初始化失败）。

## 已完成

- 仅修改生产 `backend/src/main/java/com/college/student_service_platform/agent/AcademicAnswerGrounding.java`：区分官方 GPA 水平、学期 GPA 水平、带方向的学期变化量；下降正幅度转负变化量，下降至/到作为水平值；BigDecimal 保留精确、HALF_UP 两位/四位兼容；未取消保护或新增模型修复。
- 更新 AcademicAnswerGroundingTest、AcademicEvaluationEvidenceTest，新增测试专用 LegacyAcademicAnswerGrounding 与 AcademicGroundingReplayTest。生产新增能力限于上述校验修复。
- 修改前源码及两个测试逐字节归档在本目录 `*.before.java.txt`；`baseline-preservation.json` 封存 95 个旧文件的哈希。旧评测文件变化 0 个，缺失 0 个。变动的 3 个源码/测试已有原始归档。
- 离线回放：`replay-v2/paired.json` / `summary.json`。2 条已有公开模型原文的 N02 均：旧逻辑整段替换，新逻辑保留完整答案和课程；旧逻辑输出与旧实录一致。19 条因没存模型原文不能回放，2 条没有 Agent 执行。外部调用 0。旧 M01 无原文，不能断言其具体触发表述。
- `related-tests.log`：95 项通过，无失败。
- `full-tests.log`：163 项，159 通过、4 按配置跳过，无失败。
- 定向真实复测完成：2 个任务 / 4 trials；通过 0、失败 0、待审 4、未执行 0；实际模型调用 12 次，total tokens 42049。
- 原主矩阵真实复测也已在用户暂停前完成并落盘：13 个任务 / 15 trials；通过 7、失败 0、待审 8、未执行 0；实际模型调用 30 次，total tokens 99642。
- 原主基线（未覆盖）：13 个任务 / 15 trials；通过 7、失败 3、待审 5、未执行 0；实际模型调用 30 次，total tokens 99048。
- 本轮两批真实复测合计 42 次实际模型调用、141691 total tokens。这不是累计历史所有运行；离线回放未调用模型。
- 所有解释性正常任务仍按原评分器标 REVIEW_PENDING；不得视为通过/人工验收。

## 已保存的独立 run

- 原主基线：`../results/20261106-204134-baseline-dee37`
- 定向：`../results/20261106-215409-grounding_targeted-22544`（N02、M01 各两次，6 个 HTTP chat）
- 全矩阵：`../results/20261106-215525-grounding_full-b5a53`（13 个任务、15 trials；N02/S01 各重复一次）

每个新 run 已有 observations.jsonl、scored.json、summary.json、REPORT.md、manifest/source fingerprints、fixture 验证、模型公开原文和实际 usage。旧结果、任务集、fixture、评分器、runner 均未覆盖。

## 下次继续做的事（不再调用外部模型）

1. 汇总上述新旧结果，使用旧 manifest.final_grader 的 dataset/scorer 哈希匹配当前版本；旧运行开始时 manifest 原始哈希不同是上一轮已完成的评分修正，不能更改旧文件。任务快照比较，prompt/model/fixture/repeat 保持可比。
2. 原来的 5 个待审 trials：N01-1, N03-1, N04-1, M02-1, U01-1。从旧 scored.json 导出用户任务、关键工具证据、完整最终回答、待审原因、rubric 和待核对项。保持 UNKNOWN，供用户验收；Codex 不算人工评审。
3. 检查 M01 新实录：两次均正确从最近秋季追问到上一春季、无未通过/低分课程；公开原文已保留。旧 M01 只知道最终回答被保护替换，没有模型原文，不能用本轮新采样冒充旧原文。
4. 写本目录 REPORT.md，给出文件、识别边界、离线因果证据与真实重采样区别、逐任务对比、调用次数/usage/耗时、限制/待审清单。可复用 summary 指标，不改评分器、不把待审计通过。
5. 必要时运行不调用模型的工程分组回归入口保存 matrix（Java 相关和完整回归已经通过，不必重复全套）。旧分组回归报告保持不变。
6. 最后校验旧 seal、结果无凭证、评测临时服务退出，然后交付停止。不要扩展缓存、性能、新业务、简历或推送。

## 识别范围和限制

直接靠近 GPA/绩点的“官方”“本/该/当前/上一学期”“加权”限定词决定证据类型；无明确限定的水平值可对应本轮已取得的官方或学期水平，不能借用变化量。官方变化量缺少工具证据，拒绝借用学期变化量。“下降至”仅核对终点水平，未证明历史方向。尚不跨句绑定具体学期，不处理表格语义、中文数字、所有间接表达或通用自然语言事实。未识别范围不应宣称全面保护。

## 已执行入口

离线：设置 AGENT_GPA_REPLAY=1，Maven -o -Dtest=AcademicGroundingReplayTest test；日志 backend/target/grounding-paired-replay.log（已经成功，默认输出目录已存在，勿直接覆盖重跑）。

相关/全量日志分别位于本目录 related-tests.log / full-tests.log。

真实定向：python-services/.venv/Scripts/python.exe python-services/evaluation/agent/run.py --mode live --cases N02,M01 --repeat N02,M01 --extra-repeats 1 --model-budget 16 --label grounding_targeted

真实全矩阵：同入口 --mode live --repeat N02,S01 --extra-repeats 1 --model-budget 48 --label grounding_full

上面两条真实运行均已完成，不要为取统计重复运行。当前结构化进度：PAUSED_PROGRESS.json。
