# 学业 Agent 小型评测

本目录在现有 `python-services/evaluation` 下，和旧政策 RAG 检索基准分开。统一入口是 `run.py`，不依赖新的评测平台。

## 两种模式

| 模式 | 实际执行内容 | 不应声称的结论 |
| --- | --- | --- |
| `live` | 新建专用 MySQL 评测库；真实 BCrypt 登录/JWT、Tomcat HTTP、会话/JDBC、原注册 Tool、Python HTTP/PDF 解析和 DeepSeek | 不是生产负载、不是所有答案都已人工验收 |
| `regression` | 复用现有 JUnit：H2、Mock 模型/下游、预算、协议、所有权、隔离和快照断言 | Mock 选对工具不能算真实模型选工具准确率 |

默认真实评测为 13 个独立任务，N02/S01 各多执行一次，共 15 个 trial、18 次 chat HTTP 请求。另有 5 组工程回归，共 18 个任务定义。一个多轮任务、同题重复和一次模型调用是不同计数单位。

## 环境前提

从项目根目录运行，Windows PowerShell：

- Java 17、Maven Wrapper；项目 Python 虚拟环境，已有 FastAPI/Uvicorn、pdfplumber、PyMuPDF。评分器测试使用 unittest，不需要 pytest。
- 离线 Maven 命令要求相关依赖已在本机缓存；新机器应先正常构建下载原项目依赖。
- `backend/local-config.json` 保留现有 DB_URL/DB_USERNAME/DB_PASSWORD。live 仅接受带显式端口的 localhost/127.0.0.1 MySQL URL，账号需能新建专用评测库；不会修改原库。
- `python-services/.env` 保留现有 `DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL`，不要把密钥写进任务集。当前实测配置是 deepseek-flash。
- 不要求先启动 8081、8000、8002。runner 启动只绑定 127.0.0.1:18002 的专用 Python；JUnit 启动随机端口的真实 Java HTTP 实例，结束后停止。已有 18002 被占用时拒绝复用，可用 `--python-port` 选择其他端口。

没有运行 DROP DATABASE 初始化脚本。Java harness 只从已有脚本提取 CREATE TABLE，在随机的 `student_agent_eval_v1_<10位随机串>` 库建表。不会启动管理员初始化、旧政策迁移或 RAG 模型下载。

## 不调用外部模型的命令

```powershell
.\python-services\.venv\Scripts\python.exe -m unittest discover -s python-services/evaluation/agent -p test_scoring.py -v
.\python-services\.venv\Scripts\python.exe python-services/evaluation/agent/run.py --mode regression --label regression
```

回归入口读取本次生成的 Surefire XML；缺报告或 skipped 不计通过。一项失败仍保存其他任务结果。已有工程能力是项目原有实现，本轮只是将其映射进评测矩阵，没有重新开发一套权限/快照机制。

## 小批真实模型与基线

```powershell
# 先导：3个任务，最多8次模型尝试，不额外重复
.\python-services\.venv\Scripts\python.exe python-services/evaluation/agent/run.py --mode live --cases N01,S01,S02 --extra-repeats 0 --model-budget 8 --label pilot

# 第一版基线：13项，N02和S01各增加1次重复，最多48次模型尝试
.\python-services\.venv\Scripts\python.exe python-services/evaluation/agent/run.py --mode live --repeat N02,S01 --extra-repeats 1 --model-budget 48 --label baseline

# 仅针对失败题补采公开模型回答；不是实施优化后的对比
.\python-services\.venv\Scripts\python.exe python-services/evaluation/agent/run.py --mode live --cases N02 --repeat N02 --extra-repeats 1 --model-budget 6 --label failure_capture
```

这些命令会产生实际 API 请求，预算按本次进程计数，不自动重试模型。`--extra-repeats` 为额外次数，范围 0–3；实际调用数、重复次数和 usage 在结果中报告。H2 live 开关未在本轮真实采样中验证，仅预留作显式排障对照（`--database h2`），必须保留 engine 标签，不能描述成 MySQL 评测；本轮真实采样全部使用 MySQL。

## 任务、数据与评分

- `cases.v1.json`：任务编号、类别、前置状态、身份/数据版本、用户消息、允许/必要/禁止的工具行为和评分范围。允许不同工具顺序，不要求固定单一路径。
- `fixtures.py`：两个纯合成身份与成绩单、同一培养方案；基础/更新 PDF 实际通过原解析器核对后才发付费请求。基线 GPA=2.63，15学分；更新 GPA=3.30，18学分。不存在真实学生数据。
- `scoring.py`：同时核对 HTTP/业务状态、Tool DTO 数值与课程、回答交付、历史读取、身份/调用约束、同请求只分析一次及更新后事实；不只看 HTTP 200。
- 解释质量仍需复核。规则通过的分析回答为 REVIEW_PENDING，边界固定回复可为 PASS；规则失败为 FAIL，未能运行为 NOT_EXECUTED。
- `review-template.json` 是人工抽检入口，不会自动并入任务通过率。每个维度可填 0/1/2/UNKNOWN：事实归属（错误/部分可追溯/全部关键事实可追溯）；解释相关性（答非所问/部分完成/直接完成任务）；不确定性（编造或过度推断/限制不充分/准确区分未知与缺失）。填写 reviewer 和原始证据路径。当前全部未进行人工评分，也没有把 Codex 的诊断叫作人工评审。
- 可选 LLM Judge 尚未实现、默认不调用；没有长期 Memory、MCP、多 Agent、Redis 或跨请求分析缓存。

数值允许等价小数（15、15.0），不允许把别的数值当成同一事实。历史自称4.0不能代替 Tool 的2.63。M01必须检查回答交付，不能仅因调用了 previous 工具而通过。字符预算、单次模型输出上限等仍沿用原项目。

## 结果、指标与复现

每次结果保存到 `results/<run_id>/`：

- manifest.json：代码 revision、dirty 标记、任务/提示词/runner/scorer/harness 哈希、重复配置；新运行还保存源码指纹。未提交代码以 dirty + 文件哈希区分。
- fixture-manifest.json、fixture-verification.json：合成事实、PDF/方案哈希与付费调用前的解析检查。早期采样没有独立 verification 文件，其实际 Tool 结果及首次解析输出已用于核对；不能补称采样前已由这个新 hook 自动验证。
- database.json / java-environment.json：专用库、数据库引擎、实际模型配置与链路类型。
- observations.jsonl：逐 trial、逐 turn 的原始 HTTP、回答和内部采集。早期主基线没有保存模型原文；badcase_capture 增加了 assistant_message，仅保存公开 answer/tool_calls，不保存 reasoning_content。
- scored.json、summary.json、REPORT.md：检查结果、失败原因、分类与模式分别统计、P50/P95和样本数。
- regression-evidence.json：本次 XML 中真实执行的测试名和结果，不复用过期 XML。
- review-template.json：未评分的人工入口。损坏/重复原始记录另记 read issues，不能被算成功。

可测：每次 chat HTTP 耗时、每次模型客户端调用与实际 provider usage、Tool 名/参数/DTO/耗时、分析 HTTP 客户端耗时、轮数和调用数。不能拆出的 Python 内部纯解析/统计 CPU 时间、网络耗时、TTFT、费用等不补成0。Tool耗时包含分析调用，阶段存在嵌套，不能全部相加。API的缓存命中token字段来自供应商，不代表本轮新增了业务缓存。

HTTP-200 bad answer 可在规则评分中失败；预期404属于成功的越权测试。报告分别给出 HTTP 分组、规则通过/失败耗时和预期拒绝耗时。回归无端到端采集时 P50/P95为null，不能拿JUnit用时冒充真实延迟。usage缺失为null，部分总量明确标记覆盖率，不按字符猜token，不估费用。

```powershell
# 用已有原始记录重新评分，不请求模型
.\python-services\.venv\Scripts\python.exe python-services/evaluation/agent/run.py --mode summarize --run-dir python-services/evaluation/agent/results/<run_id>

# 比较两个版本：先检查模式、任务、fixture和prompt是否可比，再看逐题变化
.\python-services\.venv\Scripts\python.exe python-services/evaluation/agent/run.py --mode compare --run-dir python-services/evaluation/agent/results/<before> --other-run python-services/evaluation/agent/results/<after>
```

前后比较不自动声称优化收益；需要保持任务、数据、重复次数与配置可比，保存两个版本的原始记录。当前没有“优化后”结果。

## 创建与清理范围

每个live进程新建一个eval前缀库，带 `evaluation_marker`；只在该新库创建合成学生/用户/方案/文件/会话。每个trial重置的也仅是这个库的指定测试表。评测不会使用或清理 `student_platform` 的数据、真实上传目录或原政策向量库。

Java/Python专用进程已自动退出；测试数据库和 `backend/target/agent-evaluation/<run_id>/` 的合成文件留作证据。清理时从该run的database.json取得精确库名，在Workbench确认前缀与evaluation_marker，再只清理这一个测试库中的会话、文件记录、方案、学生和用户。勿将业务库套入此操作。结果目录是公开的合成材料；日志虽已脱敏，仍默认由Git忽略。

## 当前结论

本轮真实结果、bad case、第一次回归失败与确认运行都见 [BASELINE_REPORT.md](./BASELINE_REPORT.md)。当前最有证据的后续工作是修正 GPA下降幅度的误拦截，而不是先增加解析缓存；本轮只定位和建立复现，没有修复这项业务逻辑。
