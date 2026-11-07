"""One entry point: offline regression, opt-in real E2E, summarize, compare. Standard library only."""
import argparse,collections,datetime,hashlib,json,os,re,socket,subprocess,sys,time,uuid,xml.etree.ElementTree as ET
from pathlib import Path
from functools import lru_cache
from fixtures import generate,verify,VERSION
from scoring import score,summarize
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
BACKEND=ROOT/'backend'
DATASET=HERE/'cases.v1.json'
RESULTS=HERE/'results'

def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def revision():return subprocess.check_output(['git','-C',str(ROOT),'rev-parse','HEAD'],text=True).strip()
@lru_cache(maxsize=1)
def log_secrets():
    secrets=[]
    cfg=BACKEND/'local-config.json'
    if cfg.exists():
        values=json.loads(cfg.read_text(encoding='utf-8-sig'))
        secrets.extend(str(values.get(key,'')) for key in ('DB_PASSWORD','JWT_SECRET','ADMIN_BOOTSTRAP_PASSWORD'))
    envfile=ROOT/'python-services/.env'
    if envfile.exists():
        for line in envfile.read_text(encoding='utf-8-sig').splitlines():
            if line.startswith('DEEPSEEK_API_KEY='):secrets.append(line.split('=',1)[1].strip().strip('"\''))
    return tuple(secret for secret in secrets if len(secret)>=4)

def safe_log_text(text):
    for secret in log_secrets():text=text.replace(secret,'[REDACTED]')
    text=re.sub(r"(?i)(password[\"']?\s*[=:]\s*[\"']?)[A-Za-z0-9_-]{4,}",r'\1[REDACTED]',text)
    text=re.sub(r'(?i)(Bearer\s+)[A-Za-z0-9_.-]+',r'\1[REDACTED]',text)
    return re.sub(r'\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b','[REDACTED_JWT]',text)

def command(args,log,cwd=BACKEND,env=None):
    with log.open('w',encoding='utf-8') as output:
        proc=subprocess.Popen(args,cwd=cwd,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        try:
            for line in proc.stdout:output.write(safe_log_text(line))
            return proc.wait()
        except BaseException:
            subprocess.run(['taskkill','/PID',str(proc.pid),'/T','/F'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL);raise

def maven(tests,log,env=None):
    return command([os.environ.get('COMSPEC','cmd.exe'),'/d','/c',str(BACKEND/'mvnw.cmd'),'-o',f'-Dtest={tests}',f'-Dlogging.config=file:{HERE / "logback-evaluation.xml"}',f'-Dlogback.configurationFile={HERE / "logback-evaluation.xml"}','test'],log,env=env)

def scrub_log(path):
    if not path.exists():return
    text=path.read_text(encoding='utf-8',errors='replace')
    secrets=[]
    cfg=BACKEND/'local-config.json'
    if cfg.exists():
        values=json.loads(cfg.read_text(encoding='utf-8-sig'));secrets.extend(str(values.get(k,'')) for k in ('DB_PASSWORD','JWT_SECRET','ADMIN_BOOTSTRAP_PASSWORD'))
    file=ROOT/'python-services/.env'
    if file.exists():
        for line in file.read_text(encoding='utf-8-sig').splitlines():
            if line.startswith('DEEPSEEK_API_KEY='):secrets.append(line.split('=',1)[1].strip().strip('"\''))
    # Also scrub transient synthetic login credentials if an inherited DEBUG logger printed them.
    secrets.extend(re.findall(r'(?i)password[\"\']?\s*[=:]\s*[\"\']?([A-Za-z0-9_-]{16,})',text))
    for secret in secrets:
        if len(secret)>=4:text=text.replace(secret,'[REDACTED]')
    text=re.sub(r'(?i)(Bearer\s+)[A-Za-z0-9_.-]+',r'\1[REDACTED]',text)
    text=re.sub(r'\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b','[REDACTED_JWT]',text)
    path.write_text(text,encoding='utf-8')

def read_observations(path):
    rows={};issues=[]
    if not path.exists():return rows,issues
    for index,line in enumerate(path.read_text(encoding='utf-8').splitlines(),1):
        try:
            item=json.loads(line)
            if not isinstance(item,dict) or not isinstance(item.get('trial_id'),str) or not isinstance(item.get('case_id'),str):raise ValueError('invalid record shape')
            key=item['trial_id']
            if key in rows:
                rows[key]={**item,'turns':[],'error':'duplicate_trial_record'};issues.append({'line':index,'reason':'duplicate trial id'});continue
            rows[key]=item
        except (json.JSONDecodeError,ValueError):issues.append({'line':index,'reason':'invalid JSON or record shape'})
    return rows,issues

def safe_score(task,record):
    try:return score(task,record)
    except Exception as error:
        return {'case_id':task['case_id'],'trial_id':record.get('trial_id',task['case_id']+'-unknown'),
                'mode':record.get('mode','live_e2e'),'real_model':record.get('real_model',False),'turns':[],
                'verdict':'FAIL','checks':[{'name':'scorer_exception','pass':False,'detail':type(error).__name__}],
                'review':{'status':'UNKNOWN'}}

def save_report(directory,manifest,records,tasks):
    for record in records:record['category']=next(t['category'] for t in tasks if t['case_id']==record['case_id'])
    stats=summarize(records,tasks);write(directory/'scored.json',records);write(directory/'summary.json',stats)
    template=directory/'review-template.json'
    if not template.exists():write(template,[{'case_id':r['case_id'],'trial_id':r['trial_id'],'reviewer':None,'source':'manual_entry_required',
        'fact_attribution':'UNKNOWN','explanation_relevance':'UNKNOWN','uncertainty_handling':'UNKNOWN','evidence':[],'notes':None}
        for r in records if r.get('review',{}).get('status')=='UNKNOWN'])
    lines=['# Academic Agent evaluation baseline','',f"Run: `{manifest['run_id']}` · code `{manifest['code_revision'][:12]}` · mode `{manifest['mode']}`",'',
           f"Dataset `{manifest['dataset_version']}`, fixture `{manifest['fixture_version']}`. Existing RAG retrieval results are excluded.",'',
           '| Measure | Observed |','| --- | --- |',f"| Unique selected tasks | {stats['planned_unique_tasks']} |",f"| Trials / executed | {stats['trials']} / {stats['executed_trials']} |",
           f"| PASS / FAIL / NOT_EXECUTED / REVIEW_PENDING | {' / '.join(str(stats['verdicts'].get(x,0)) for x in ('PASS','FAIL','NOT_EXECUTED','REVIEW_PENDING'))} |",
           f"| Actual model HTTP attempts / successful responses | {stats['model_http_attempts']} / {stats['model_successful_responses']} |",f"| Python analysis calls during Agent requests | {stats['analysis_calls']} |",'',
           'PASS is deterministic contract success. REVIEW_PENDING passed rules but explanation quality is UNKNOWN; it is not an accepted task-success claim. Model errors count as failures; missing environments remain NOT_EXECUTED. Mock choices never count as model-selection accuracy.','',
           '## Separate execution modes','']
    for mode,group in stats['modes'].items():
        lines.extend([f'### {mode}','',json.dumps(group,ensure_ascii=False,indent=2),''])
    lines.extend(['HTTP latency is sampled per actual chat request, not per independent task. Successful and failed HTTP/business responses are separate; expected 404 is reported separately and can pass its task; rule-pass/rule-fail latencies distinguish HTTP-200 bad answers; JUnit durations are not E2E latency. Model-client timing includes serialization/transport/validation. Missing fields are null.','',
                  '## Cases','', '| Case | Trial | Verdict | Failed checks |','| --- | --- | --- | --- |'])
    for r in records:
        reasons=[c['name']+(': '+c.get('detail','') if c.get('detail') else '') for c in r.get('checks',[]) if not c['pass']]
        lines.append(f"| {r['case_id']} | {r['trial_id']} | {r['verdict']} | {'; '.join(reasons).replace('|','/')} |")
    lines.extend(['','## Provider usage (actual API fields)','', '```json',json.dumps(stats['provider_usage'],ensure_ascii=False,indent=2),'```','',
                  'Partial usage totals cover only responses that supplied the field; a missing or failed call is not zero tokens. No cost estimate or character-based token estimate is made.','',
                  '## Stability','', '```json',json.dumps(stats['repeat_stability'],ensure_ascii=False,indent=2),'```','',
                  '## Evidence and limits','',
                  '- See manifest.json, database.json / java-environment.json (live), observations.jsonl, scored.json and regression-evidence.json (regression).',
                  '- Synthetic identities, transcripts and plans only. Real model + MySQL + Python are live only when recorded; replay uses existing H2/Mock tests.',
                  '- Explanation/source-attribution quality is UNKNOWN until a reviewer records a judgement. Optional LLM Judge is not implemented.',
                  '- This is a small serial task baseline, not throughput, production load or a measured improvement.',
                  '- Each live run creates its own student_agent_eval_v1_* schema; existing business tables are never initialized, reset or cleaned. See README for cleanup scope.'])
    (directory/'REPORT.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    return stats

def regression(directory,tasks):
    classes=sorted({name for task in tasks for name in task['expected']['junit_methods']})
    reports=BACKEND/'target/surefire-reports'
    for name in classes:
        path=reports/f'TEST-com.college.student_service_platform.{name}.xml'
        if path.exists():path.unlink()
    code=maven(','.join(classes),directory/'maven.log');scrub_log(directory/'maven.log')
    found={};evidence=[]
    for name in classes:
        path=reports/f'TEST-com.college.student_service_platform.{name}.xml'
        if not path.exists():continue
        try:root=ET.parse(path).getroot()
        except ET.ParseError:continue
        for test in root.findall('testcase'):
            key=(name,test.attrib['name']);verdict='FAIL' if test.find('failure') is not None or test.find('error') is not None else 'NOT_EXECUTED' if test.find('skipped') is not None else 'PASS'
            found[key]={'class':name,'method':key[1],'verdict':verdict,'seconds':float(test.attrib.get('time','0'))};evidence.append(found[key])
    write(directory/'regression-evidence.json',{'maven_exit':code,'tests':evidence,'real_model':False})
    rows=[]
    for task in tasks:
        selected=[found.get((name,method)) for name,methods in task['expected']['junit_methods'].items() for method in methods]
        checks=[{'name':name+'.'+method,'pass':found.get((name,method),{}).get('verdict')=='PASS','detail':found.get((name,method),{}).get('verdict','missing test report')} for name,methods in task['expected']['junit_methods'].items() for method in methods]
        verdict='NOT_EXECUTED' if not selected or any(x is None or x['verdict']=='NOT_EXECUTED' for x in selected) else 'FAIL' if any(x['verdict']=='FAIL' for x in selected) else 'PASS'
        rows.append({'case_id':task['case_id'],'trial_id':task['case_id']+'-1','mode':'engineering_regression','real_model':False,'verdict':verdict,'checks':checks,'turns':[],
                     'junit_seconds':sum(x['seconds'] for x in selected) if selected and all(x is not None for x in selected) else None,'model_call_count':None,'usage':None})
    return rows

def live(directory,tasks,args):
    work=BACKEND/'target/agent-evaluation'/directory.name;work.mkdir(parents=True,exist_ok=False)
    fixture=generate(work/'fixtures');write(directory/'fixture-manifest.json',fixture)
    write(directory/'fixture-verification.json',verify(work/'fixtures'))
    env=os.environ.copy();env.update({'AGENT_EVAL_EXECUTE':'1','AGENT_EVAL_WORK':str(work),'AGENT_EVAL_OUTPUT':str(directory),
       'AGENT_EVAL_CASES':','.join(t['case_id'] for t in tasks),'AGENT_EVAL_REPEAT_IDS':args.repeat,'AGENT_EVAL_EXTRA_REPEATS':str(args.extra_repeats),
       'AGENT_EVAL_MODEL_BUDGET':str(args.model_budget),'AGENT_EVAL_PYTHON_PORT':str(args.python_port),'AGENT_EVAL_DATABASE':args.database})
    child=None;stream=None
    try:
        with socket.socket() as sock:
            sock.settimeout(.3);occupied=sock.connect_ex(('127.0.0.1',args.python_port))==0
        if occupied:raise RuntimeError('dedicated Python port occupied; choose --python-port, existing service left untouched')
        stream=(directory/'python.log').open('w',encoding='utf-8')
        child=subprocess.Popen([sys.executable,'-m','uvicorn','5_pdf_compare:app','--app-dir',str(ROOT/'python-services'),'--host','127.0.0.1','--port',str(args.python_port)],cwd=work,env=env,stdout=stream,stderr=subprocess.STDOUT,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        for _ in range(100):
            with socket.socket() as sock:
                sock.settimeout(.2)
                if sock.connect_ex(('127.0.0.1',args.python_port))==0:break
            if child.poll() is not None:raise RuntimeError('dedicated Python startup failed; see python.log')
            time.sleep(.2)
        else:raise RuntimeError('dedicated Python startup timeout')
        code=maven('AcademicAgentEvaluationTest',directory/'maven.log',env=env)
        write(directory/'runner-exit.json',{'maven_exit':code})
    except Exception as exc:write(directory/'blocked.json',{'reason':str(exc),'exception':type(exc).__name__})
    finally:
        if child is not None:
            child.terminate()
            try:child.wait(timeout=8)
            except subprocess.TimeoutExpired:child.kill();child.wait()
        if stream is not None:stream.close()
        scrub_log(directory/'maven.log');scrub_log(directory/'python.log')
    observed,read_issues=read_observations(directory/'observations.jsonl')
    if read_issues:write(directory/'record-read-issues.json',read_issues)
    rows=[]
    for task in tasks:
        repeats=1+(args.extra_repeats if task['case_id'] in args.repeat.split(',') else 0)
        for trial in range(1,repeats+1):
            tid=task['case_id']+'-'+str(trial);record=observed.get(tid,{'case_id':task['case_id'],'trial_id':tid,'mode':'live_e2e','real_model':False,'turns':[],'error':'environment_or_startup_blocked'})
            rows.append(safe_score(task,record))
    return rows

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--mode',choices=['live','regression','summarize','compare'],required=True)
    parser.add_argument('--cases',default='');parser.add_argument('--repeat',default='N02,S01');parser.add_argument('--extra-repeats',type=int,default=1)
    parser.add_argument('--model-budget',type=int,default=48);parser.add_argument('--python-port',type=int,default=18002);parser.add_argument('--database',choices=['mysql','h2'],default='mysql')
    parser.add_argument('--label',default='baseline');parser.add_argument('--run-dir',type=Path);parser.add_argument('--other-run',type=Path)
    args=parser.parse_args()
    if args.extra_repeats<0 or args.extra_repeats>3 or args.model_budget<1 or args.model_budget>80:parser.error('repeat range 0..3, model budget 1..80')
    dataset=json.loads(DATASET.read_text(encoding='utf-8'));all_tasks=dataset['cases']
    if args.mode=='compare':
        if not args.run_dir or not args.other_run:parser.error('compare needs --run-dir and --other-run')
        a=json.loads((args.run_dir/'manifest.json').read_text(encoding='utf-8'));b=json.loads((args.other_run/'manifest.json').read_text(encoding='utf-8'))
        print(json.dumps({'same_mode':a['mode']==b['mode'],'same_selected_cases':a['selected_cases']==b['selected_cases'],'same_fixture_version':a['fixture_version']==b['fixture_version'],'comparable_dataset':a['dataset_sha256']==b['dataset_sha256'],'same_prompt':a['prompt_sha256']==b['prompt_sha256'],
            'before':json.loads((args.run_dir/'summary.json').read_text(encoding='utf-8')),'after':json.loads((args.other_run/'summary.json').read_text(encoding='utf-8'))},ensure_ascii=False,indent=2));return
    if args.mode=='summarize':
        if not args.run_dir:parser.error('summarize needs --run-dir')
        manifest=json.loads((args.run_dir/'manifest.json').read_text(encoding='utf-8'));tasks=[t for t in all_tasks if t['case_id'] in manifest['selected_cases']]
        rows=json.loads((args.run_dir/'scored.json').read_text(encoding='utf-8'))
        if manifest['mode']=='live':
            raw,read_issues=read_observations(args.run_dir/'observations.jsonl')
            if read_issues:write(args.run_dir/'record-read-issues.json',read_issues)
            mapping={t['case_id']:t for t in tasks}
            rows=[safe_score(mapping[r['case_id']],raw.get(r['trial_id'],r)) for r in rows]
            manifest.setdefault('rescoring',[]).append({'scorer_sha256':digest(HERE/'scoring.py'),'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'uses_original_observations':True})
            write(args.run_dir/'manifest.json',manifest)
        print(json.dumps(save_report(args.run_dir,manifest,rows,tasks),ensure_ascii=False,indent=2));return
    selected=set(args.cases.split(',')) if args.cases else None
    if selected and not selected<={t['case_id'] for t in all_tasks}:parser.error('unknown case id')
    tasks=[t for t in all_tasks if t['mode']==('live' if args.mode=='live' else 'regression') and (selected is None or t['case_id'] in selected)]
    if not tasks:parser.error('no tasks selected for mode')
    RESULTS.mkdir(parents=True,exist_ok=True)
    runid=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).strftime('%Y%m%d-%H%M%S')+'-'+re.sub('[^A-Za-z0-9_-]','_',args.label)+'-'+uuid.uuid4().hex[:5]
    directory=RESULTS/runid;directory.mkdir()
    manifest={'run_id':runid,'created_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'mode':args.mode,'code_revision':revision(),
      'working_tree_dirty':bool(subprocess.check_output(['git','-C',str(ROOT),'status','--porcelain'],text=True)),'dataset_version':dataset['dataset_version'],'dataset_sha256':digest(DATASET),
      'fixture_version':VERSION,'prompt_sha256':digest(ROOT/'backend/src/main/resources/agent/skills/academic.md'),'selected_cases':[t['case_id'] for t in tasks],
      'repeat_ids':args.repeat.split(',') if args.mode=='live' else [],'extra_repeats':args.extra_repeats if args.mode=='live' else 0,'database':args.database if args.mode=='live' else 'H2 mock regressions',
      'runner_sha256':digest(Path(__file__)),'scorer_sha256':digest(HERE/'scoring.py'),'harness_sha256':digest(ROOT/'backend/src/test/java/com/college/student_service_platform/AcademicAgentEvaluationTest.java')}
    source_paths=list((ROOT/'backend/src/main/java/com/college/student_service_platform').rglob('*.java'))+list((ROOT/'python-services').glob('*.py'))+list(HERE.glob('*.py'))
    write(directory/'source-fingerprints.json',{str(p.relative_to(ROOT)):digest(p) for p in sorted(source_paths)})
    manifest['source_fingerprints_sha256']=digest(directory/'source-fingerprints.json')
    write(directory/'manifest.json',manifest);print('Run output:',directory,flush=True)
    try:rows=live(directory,tasks,args) if args.mode=='live' else regression(directory,tasks)
    except Exception as failure:
        write(directory/'blocked.json',{'exception':type(failure).__name__,'reason':'runner preflight/startup failed; no success is inferred'})
        rows=[{'case_id':t['case_id'],'trial_id':t['case_id']+'-'+str(trial),'mode':'live_e2e' if args.mode=='live' else 'engineering_regression',
               'real_model':False,'turns':[],'verdict':'NOT_EXECUTED','checks':[{'name':'runner_preflight','pass':False,'detail':type(failure).__name__}]} for t in tasks for trial in range(1,2+(args.extra_repeats if args.mode=='live' and t['case_id'] in args.repeat.split(',') else 0))]
    stats=save_report(directory,manifest,rows,tasks);print(json.dumps(stats,ensure_ascii=False,indent=2))
    return 1 if any(r['verdict'] in ('FAIL','NOT_EXECUTED') for r in rows) else 0
if __name__=='__main__':raise SystemExit(main())
