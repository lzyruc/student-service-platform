"""Read-only audit/export of completed GPA runs. Never calls a model or rewrites old results.
Run from project root; every invocation needs a fresh --output name.
"""
import argparse, collections, hashlib, json, re, sys
from pathlib import Path

HERE=Path(__file__).resolve().parent
BASE=HERE.parent
ROOT=BASE.parents[2]
RUNS={"baseline":"20261106-204134-baseline-dee37", "targeted":"20261106-215409-grounding_targeted-22544",
      "full":"20261106-215525-grounding_full-b5a53", "engineering":"20261107-133102-grounding_regression-f3a8d"}
CHECKLIST={
 "N01":["官方 GPA=2.63，已获15学分，操作系统58分仍未通过。", "软件工程未见通过记录须核实；数据库系统尚未到开课学期；不可编造风险概率。"],
 "N03":["按秋24、春24、秋25顺序：学期加权 GPA=3.85、3.15、0.9。", "相邻变化量=-0.7、-2.25；学期 GPA 不等于官方 GPA；小样本不能推断学习能力。"],
 "N04":["整体、近期和趋势各结论可追溯至本轮对应工具，不能混用 GPA/变化量。", "重点课程操作系统58分、计算机网络65分；说明两门样本及缺口需核实。"],
 "M02":["history 自称4.0只作对话背景；本轮当前官方 GPA 应采用 Tool 的2.63。", "解释有依据，不能将自述或上一轮助手文字作为新事实。"],
 "U01":["首轮基线2.63/15；替换测试成绩单后，末轮应为3.30/18且无未通过课程。", "同一 conversation 可读历史，但末轮事实必须来自新 request 的当前 Tool。"]}

def read(path): return json.loads(path.read_text(encoding="utf-8"))
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def raw(run): return [json.loads(line) for line in (BASE/"results"/run/"observations.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
def latency(values):
 from scoring import latency as existing_latency
 return existing_latency(values)
def metrics(records):
 turns=[t for r in records for t in r.get("turns",[])]
 def stage(name):
  values=[c.get("duration_ms") for t in turns for c in (t.get("execution") or {}).get(name,[])]
  observed=[x for x in values if x is not None]
  return {"calls":len(values),"observed":len(observed),"sum_ms":sum(observed) if observed else None,**latency(observed)}
 return {"chat_requests":len(turns),"chat_sum_ms":sum(t["end_to_end_ms"] for t in turns) if turns and all(t.get("end_to_end_ms") is not None for t in turns) else None,
         "model":stage("model_calls"),"tool":stage("tool_calls"),"analysis":stage("analysis_calls")}
def public_answer(turn):
 messages=[c["assistant_message"]["content"] for c in (turn.get("execution") or {}).get("model_calls",[]) if c.get("finish_reason")=="stop" and isinstance((c.get("assistant_message") or {}).get("content"),str)]
 return messages[-1] if messages else None

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--output",default="evidence-v1");args=parser.parse_args()
 if not re.fullmatch(r"[A-Za-z0-9_-]+",args.output): parser.error("output must be a new directory name")
 output=HERE/args.output
 if output.exists(): parser.error("refusing to overwrite existing evidence")
 sys.path.insert(0,str(BASE))
 from scoring import score,summarize
 sealed=read(HERE/"baseline-preservation.json")["files"]
 changed=[name for name,h in sealed.items() if not (ROOT/name).exists() or digest(ROOT/name)!=h]
 frozen=[name for name in sealed if name.startswith("python-services")]
 assert not any(name in changed for name in frozen),"old evaluation artifact changed"
 assert len(changed)==3 and all(name.startswith("backend/") for name in changed)
 cases=read(BASE/"cases.v1.json");tasks={c["case_id"]:c for c in cases["cases"]}
 summaries={key:read(BASE/"results"/run/"summary.json") for key,run in RUNS.items()}
 manifests={key:read(BASE/"results"/run/"manifest.json") for key,run in RUNS.items()}
 records={key:raw(run) for key,run in RUNS.items() if key!="engineering"}
 stored={key:read(BASE/"results"/run/"scored.json") for key,run in RUNS.items()}
 rescore={}
 for key,rows in records.items():
  observed={r["trial_id"]:r for r in rows};checks=[];regraded=[]
  for saved in stored[key]:
   current={**score(tasks[saved["case_id"]],observed[saved["trial_id"]]),"category":tasks[saved["case_id"]]["category"]}
   same=current["verdict"]==saved["verdict"] and current["checks"]==saved["checks"]
   checks.append({"trial_id":saved["trial_id"],"same_verdict_and_checks":same});regraded.append(current)
  assert all(x["same_verdict_and_checks"] for x in checks),"stored grading differs"
  recomputed=summarize(regraded,[tasks[id] for id in manifests[key]["selected_cases"]])
  for name in ("verdicts","by_category","model_http_attempts","provider_usage","trials","unique_task_verdicts"):
   assert recomputed[name]==summaries[key][name],"summary mismatch: "+name
  rescore[key]=checks
 before=manifests["baseline"];after=manifests["full"];final=before["final_grader"]
 comparable={"selected_cases":before["selected_cases"]==after["selected_cases"],"repeat_ids":before["repeat_ids"]==after["repeat_ids"],
  "extra_repeats":before["extra_repeats"]==after["extra_repeats"],"fixture_version":before["fixture_version"]==after["fixture_version"],
  "prompt_sha256":before["prompt_sha256"]==after["prompt_sha256"],"effective_dataset_sha256":final["dataset_sha256"]==after["dataset_sha256"],
  "effective_scorer_sha256":final["scorer_sha256"]==after["scorer_sha256"],"effective_runner_sha256":final["runner_sha256"]==after["runner_sha256"],
  "model":read(BASE/"results"/RUNS["baseline"]/"java-environment.json")["model"]==read(BASE/"results"/RUNS["full"]/"java-environment.json")["model"],
  "task_snapshot":read(BASE/"results"/RUNS["baseline"]/"grading-dataset.json")["cases"]==[tasks[id] for id in after["selected_cases"]]}
 assert all(comparable.values()),comparable
 # PyMuPDF generates document IDs afresh. Verify preserved old/new PDF text and parsed business facts without regenerating files.
 import pymupdf
 from fixtures import verify
 pdf_comparison=[]
 old_dir=ROOT/"backend/target/agent-evaluation"/RUNS["baseline"]/"fixtures"
 new_dir=ROOT/"backend/target/agent-evaluation"/RUNS["full"]/"fixtures"
 for filename in ("alpha-base.pdf","alpha-updated.pdf","beta.pdf"):
  pair=[]
  for directory in (old_dir,new_dir):
   with pymupdf.open(directory/filename) as doc:
    words=[page.get_text("words") for page in doc]
    pair.append({"file_sha256":digest(directory/filename),"words_sha256":hashlib.sha256(json.dumps(words,ensure_ascii=False).encode()).hexdigest(),"document_id":doc.xref_get_key(-1,"ID")[1]})
  assert pair[0]["words_sha256"]==pair[1]["words_sha256"]
  pdf_comparison.append({"file":filename,"old":pair[0],"new":pair[1],"same_text_and_word_positions":True})
 parse_before=verify(old_dir);parse_after=verify(new_dir);assert parse_before==parse_after
 fixture_before=read(BASE/"results"/RUNS["baseline"]/"fixture-manifest.json");fixture_after=read(BASE/"results"/RUNS["full"]/"fixture-manifest.json")
 assert all(fixture_before[k]==fixture_after[k] for k in ("version","base","updated"))
 assert fixture_before["files"]["plan.json"]==fixture_after["files"]["plan.json"]
 pending=[]
 for saved in stored["baseline"]:
  if saved["verdict"]!="REVIEW_PENDING":continue
  task=tasks[saved["case_id"]]
  pending.append({"case_id":saved["case_id"],"trial_id":saved["trial_id"],"source_run":RUNS["baseline"],"identity":task["identity"],"fixture_version":task["fixture_version"],
   "user_task":task["messages"],"precondition":task["precondition"],"review_status":"UNKNOWN","reviewer":None,
   "reason":"确定性规则通过，但解释相关性、完整事实支持和不确定性尚未由用户/指定验收人核对；不计通过。",
   "rubric":task.get("rubric",[]),"checklist":CHECKLIST[task["case_id"]],"checks":saved["checks"],
   "turns":[{"turn":t["turn"],"user_message":t["message"],"fixture_state":t.get("fixture_state"),"tool_evidence":(t.get("execution") or {}).get("tool_calls",[]),
    "server_history":(t.get("execution") or {}).get("server_history",[]),"final_answer":((t.get("response") or {}).get("data") or {}).get("answer")} for t in saved["turns"]]})
 assert {x["case_id"] for x in pending}==set(CHECKLIST) and len(pending)==5
 diagnosis=[]
 for key in ("targeted","full"):
  for record in records[key]:
   if record["case_id"] not in ("N02","M01"):continue
   for t in record["turns"]:
    answer=((t.get("response") or {}).get("data") or {}).get("answer");original=public_answer(t)
    assert original is not None
    diagnosis.append({"run":RUNS[key],"case_id":record["case_id"],"trial_id":record["trial_id"],"turn":t["turn"],
     "message":t["message"],"model_public_text":original,"final_answer":answer,"public_text_preserved":original==answer,
     "tool_evidence":(t.get("execution") or {}).get("tool_calls",[])})
 assert all(x["public_text_preserved"] for x in diagnosis)
 totals={name:sum(summaries[k]["provider_usage"][name]["sum_observed"] for k in ("targeted","full")) for name in ("promptTokens","completionTokens","totalTokens","promptCacheHitTokens","promptCacheMissTokens")}
 comparison={"runs":RUNS,"comparable":comparable,"effective_grader":final,"metrics":{k:metrics(v) for k,v in records.items()},
  "summaries":summaries,"new_round_model_calls":sum(summaries[k]["model_http_attempts"] for k in ("targeted","full")),"new_round_usage":totals,
  "by_trial":[{"trial_id":r["trial_id"],"before":r["verdict"],"after":next(x["verdict"] for x in stored["full"] if x["trial_id"]==r["trial_id"])} for r in stored["baseline"]]}
 integrity={"sealed_files":len(sealed),"unchanged_old_evaluation_files":len(frozen),"changed_archived_sources":changed,
  "old_evaluation_files_changed":[],"rescore_read_only_identical":rescore,"comparable":comparable,
  "pdf_comparison":pdf_comparison,"same_parsed_facts":parse_before==parse_after,"fixture_verification_now":parse_after,
  "note":"This audit parses saved synthetic PDFs locally (4 parse calls, no HTTP/model); it does not rewrite original fixture verification."}
 output.mkdir()
 def write(name,value): (output/name).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding="utf-8")
 write("comparison.json",comparison);write("integrity.json",integrity);write("pending-original.json",pending);write("targeted-evidence.json",diagnosis)
 lines=["# 原主基线 5 个待审 trial：验收材料", "", "这些是原始结果的只读摘录，全部仍为 UNKNOWN；没有由 Codex 或其他模型冒充人工验收。", "", "评分维度：事实归属、解释相关性、不确定性，各可记 0/1/2/UNKNOWN；0=明显不满足，1=部分满足，2=所列关键项均有证据，无法判断保留 UNKNOWN。请填写验收人、日期和证据。", ""]
 for row in pending:
  lines.extend(["## "+row["trial_id"],"", "身份/数据：`"+row["identity"]+"` / `"+row["fixture_version"]+"`；原 run：`"+row["source_run"]+"`。", "", "待审原因："+row["reason"],"", "原任务：",""]+["- "+m for m in row["user_task"]]+["", "原 rubric：", ""]+["- "+r for r in row["rubric"]]+["", "待核对项：", ""]+["- [ ] "+c for c in row["checklist"]]+["", "验收人：未填写；事实归属/相关性/不确定性：UNKNOWN / UNKNOWN / UNKNOWN。", ""])
  for turn in row["turns"]:
   lines.extend(["### 第 "+str(turn["turn"])+" 轮", "", "用户："+turn["user_message"],"", "数据状态：`"+str(turn["fixture_state"])+"`。关键工具证据（去掉无关耗时/上传时间，完整 DTO 见配套 JSON）：", ""])
   for tool in turn["tool_evidence"]:
    data={k:v for k,v in (tool.get("data") or {}).items() if k!="source"}
    lines.extend(["`"+tool["tool"]+"` 参数 `"+json.dumps(tool.get("arguments"),ensure_ascii=False)+"`；状态 `"+str(tool.get("status"))+"`。", "", "```json", json.dumps(data,ensure_ascii=False,indent=2),"```", ""])
   lines.extend(["最终回答（完整原文）：", "", turn["final_answer"] or "（无回答）", ""])
 (output/"pending-original.md").write_text("\n".join(lines),encoding="utf-8")
 print(json.dumps({"output":str(output),"original_pending_trials":len(pending),"rescore_trials":sum(len(v) for v in rescore.values()),"unchanged_old_files":len(frozen),"new_round_calls":comparison["new_round_model_calls"],"new_round_usage":totals},ensure_ascii=False))

if __name__=="__main__":main()
