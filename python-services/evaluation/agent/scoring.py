"""Deterministic checks; explanation quality remains UNKNOWN until an actual reviewer acts."""
import math,re,collections,statistics,json
from decimal import Decimal, InvalidOperation

def number_in(text,value):
    expected=Decimal(str(value))
    for token in re.findall(r"(?<![\d.])[+-]?\d+(?:\.\d+)?(?![\d.])",text):
        try:
            if Decimal(token)==expected:return True
        except InvalidOperation:pass
    return False

def score(task,record):
    checks=[]
    def add(name,ok,detail=''):checks.append({'name':name,'pass':bool(ok),'detail':detail})
    turns=record.get('turns') or []
    if not isinstance(turns,list) or any(not isinstance(t,dict) for t in turns):
        return {**record,'turns':[],'verdict':'NOT_EXECUTED','checks':[{'name':'valid_observation','pass':False,'detail':'malformed turn records'}],'review':{'status':'UNKNOWN'}}
    if record.get('error') or len(turns)!=len(task.get('messages',[])):
        return {**record,'verdict':'FAIL' if turns else 'NOT_EXECUTED','checks':[{'name':'completed_trial','pass':False,'detail':record.get('error') or 'missing turns'}],'review':{'status':'UNKNOWN','rubric':task.get('rubric',[])}}
    if any(not isinstance(t.get('response'),dict) or not isinstance(t.get('execution') or {},dict) for t in turns):
        return {**record,'verdict':'FAIL','checks':[{'name':'valid_http_observation','pass':False,'detail':'invalid response or execution shape'}],'review':{'status':'UNKNOWN'}}
    expected=task['expected'];kind=expected['kind'];last=turns[-1]
    for turn in turns:
        execution=turn.get('execution') or {}
        add('http_status_turn_'+str(turn['turn']),turn.get('http_status') in expected.get('allowed_http',[200]))
        add('no_foreign_tool_data_turn_'+str(turn['turn']),'乙方专属课程' not in str(execution.get('tool_calls',[])))
        add('single_analysis_per_request_turn_'+str(turn['turn']),len(execution.get('analysis_calls',[]))<=1)
        signatures=[(c.get('tool'),json.dumps(c.get('arguments',{}),sort_keys=True)) for c in execution.get('tool_calls',[])]
        add('no_duplicate_identical_tool_call_turn_'+str(turn['turn']),len(signatures)==len(set(signatures)))
    execution=last.get('execution') or {};calls=execution.get('tool_calls',[])
    payload=(last.get('response') or {}).get('data') or {};answer=payload.get('answer') or ''
    status=payload.get('status');allowed=expected.get('allowed_status',['COMPLETED'])
    if kind!='ownership':add('agent_status',status in allowed,repr(status))
    add('no_formal_warning_write',record.get('formal_warning_records')==0)
    add('no_foreign_answer_data','乙方专属课程' not in answer)
    for alternatives in expected.get('required_tools',[]):
        add('required_capability_'+'|'.join(alternatives),any(c['tool'] in alternatives and c.get('status')=='OK' for c in calls))
    if expected.get('forbid_tools'):add('no_tools',not calls)
    if expected.get('forbid_model'):add('ownership_before_agent',last.get('execution') is None)
    if expected.get('forbid_analysis'):add('no_python_analysis',all(not (t.get('execution') or {}).get('analysis_calls') for t in turns))
    rules=expected.get('argument_rules',{})
    for key,allowed_values in rules.items():
        selected=[c for c in calls if key in c.get('arguments',{}) or (key=='semester' and c['tool']=='get_recent_course_performance') or (key=='lastSemesters' and c['tool']=='get_academic_trend')]
        add('argument_'+key,bool(selected) and all(c.get('arguments',{}).get(key) in allowed_values for c in selected))
    assessment=[c.get('data') or {} for c in calls if c['tool']=='get_academic_assessment' and c.get('status')=='OK']
    if kind in ('overview','combined','history_fact','update'):
        gpa=3.3 if kind=='update' else 2.63
        add('current_gpa_in_tool',any(x.get('officialGpa')==gpa for x in assessment))
        add('current_gpa_in_answer',number_in(answer,gpa))
        if kind in ('overview','combined'):
            add('earned_credit_in_tool',any(x.get('totalEarnedCredits')==15 for x in assessment))
            add('earned_credit_in_answer',number_in(answer,15))
            add('failed_course_supported',any(any(c.get('name')=='操作系统' for c in x.get('failedCourses',[])) for x in assessment) and '操作系统' in answer)
        if kind=='update':add('updated_failure_cleared',any(x.get('failedCourses')==[] and x.get('totalEarnedCredits')==18 for x in assessment))
    if kind=='combined':
        add('combined_recent_evidence',all(name in answer for name in ('操作系统','计算机网络')) and number_in(answer,58) and number_in(answer,65))
        add('combined_trend_evidence',all(number_in(answer,n) for n in (3.85,3.15,.9)) and any(word in answer for word in ('下降','下滑','DOWN')))
    if kind=='recent':
        recent=[c.get('data') or {} for c in calls if c['tool']=='get_recent_course_performance' and c.get('status')=='OK']
        names={x.get('name') for data in recent for x in data.get('focusCourses',[])}
        add('recent_courses_from_fixture',{'操作系统','计算机网络'}<=names and all(n in answer for n in ('操作系统','计算机网络')))
        add('recent_scores_from_fixture',number_in(answer,58) and number_in(answer,65))
    if kind=='trend':
        trend=[c.get('data') or {} for c in calls if c['tool']=='get_academic_trend' and c.get('status')=='OK']
        add('trend_values_in_tool',any([x.get('weightedGpa') for x in d.get('semesters',[])]==[3.85,3.15,.9] for d in trend))
        add('trend_values_in_answer',all(number_in(answer,n) for n in (3.85,3.15,.9)))
    if kind in ('followup','history_fact','update'):
        history=execution.get('server_history') or []
        add('real_server_history_loaded',any(x.get('content')==task['messages'][0] for x in history))
        if kind=='followup':
            add('previous_semester_in_tool',any('春季' in str((c.get('data') or {}).get('semester','')) for c in calls if c['tool']=='get_recent_course_performance'))
            previous=[c.get('data') or {} for c in calls if c['tool']=='get_recent_course_performance' and c.get('status')=='OK']
            add('previous_no_focus_courses_from_tool',any(d.get('focusCourses')==[] and (d.get('summary') or {}).get('failedCourseCount')==0 for d in previous))
            add('previous_result_reaches_answer',any(x in answer for x in ('上一','春季')) and any(x in answer for x in ('低分','未通过')) and bool(re.search(r'没有|暂无|未发现|未见|不存在|0\s*门|为\s*0',answer)))
            first_answer=((turns[0].get('response') or {}).get('data') or {}).get('answer','')
            add('initial_recent_result_reaches_answer',all(n in first_answer for n in ('操作系统','计算机网络')) and number_in(first_answer,58) and number_in(first_answer,65))
    if kind=='missing':
        add('missing_data_verified_by_tool',any(c.get('status')=='MISSING_DATA' or (c.get('data') or {}).get('status')=='MISSING_DATA' for c in calls))
        add('no_invented_gpa',not re.search(r'(?:GPA|绩点)\s*(?:为|是|[:：])?\s*\d',answer,re.I))
    needs_review=kind in ('overview','recent','trend','combined','followup','history_fact','update')
    verdict='FAIL' if any(not x['pass'] for x in checks) else 'REVIEW_PENDING' if needs_review else 'PASS'
    return {**record,'verdict':verdict,'checks':checks,'agent_status':status,'review':{'status':'UNKNOWN' if needs_review else 'NOT_REQUIRED','rubric':task.get('rubric',[]),'reviewer':None,'notes':None}}

def percentile(values,q):
    if not values:return None
    v=sorted(values);index=(len(v)-1)*q;lo=math.floor(index);hi=math.ceil(index)
    return v[lo]+(v[hi]-v[lo])*(index-lo)

def latency(values):return {'sample_count':len(values),'p50_ms':percentile(values,.5),'p95_ms':percentile(values,.95)}

def summarize(records,planned_cases):
    counts=collections.Counter(r['verdict'] for r in records)
    groups={}
    for r in records:
        mode=r.get('mode','unknown');g=groups.setdefault(mode,{'trials':0,'verdicts':collections.Counter(),'http_success_ms':[],'http_failure_ms':[],'junit_seconds':[],'rule_pass_ms':[],'rule_fail_ms':[],'expected_rejection_ms':[]})
        g['trials']+=1;g['verdicts'][r['verdict']]+=1
        for t in r.get('turns',[]):
            if t.get('end_to_end_ms') is None:continue
            data=(t.get('response') or {}).get('data') or {}
            success=t.get('http_status')==200 and data.get('status') in ('COMPLETED','REFUSED','OUT_OF_SCOPE','NEEDS_CLARIFICATION','DATA_UNAVAILABLE','TOOL_LIMIT')
            g['http_success_ms' if success else 'http_failure_ms'].append(t['end_to_end_ms'])
            g['rule_fail_ms' if r['verdict']=='FAIL' else 'rule_pass_ms'].append(t['end_to_end_ms'])
            if t.get('http_status')==404 and r['verdict']=='PASS':g['expected_rejection_ms'].append(t['end_to_end_ms'])
        if r.get('junit_seconds') is not None:g['junit_seconds'].append(r['junit_seconds'])
    model_calls=[m for r in records for t in r.get('turns',[]) for m in (t.get('execution') or {}).get('model_calls',[]) if m.get('network_attempted')]
    success_models=[m for m in model_calls if m.get('outcome')=='success']
    usage_keys=('promptTokens','completionTokens','totalTokens','promptCacheHitTokens','promptCacheMissTokens')
    usage={key:{'sample_count':sum((m.get('usage') or {}).get(key) is not None for m in model_calls),
                'sum_observed':sum((m.get('usage') or {}).get(key,0) or 0 for m in model_calls) if any((m.get('usage') or {}).get(key) is not None for m in model_calls) else None,
                'complete_coverage':bool(model_calls) and all((m.get('usage') or {}).get(key) is not None for m in model_calls)} for key in usage_keys}
    by_category={}
    for r in records:
        by_category.setdefault(r.get('category','unknown'),collections.Counter())[r['verdict']]+=1
    independent={}
    for mode,g in groups.items():independent[mode]={'trials':g['trials'],'verdicts':dict(g['verdicts']),'http_success_latency':latency(g['http_success_ms']),'http_failure_latency':latency(g['http_failure_ms']),'junit_seconds':sum(g['junit_seconds']) if g['junit_seconds'] else None,'rule_pass_request_latency':latency(g['rule_pass_ms']),'rule_fail_request_latency':latency(g['rule_fail_ms']),'expected_rejection_latency':latency(g['expected_rejection_ms'])}
    stability={}
    for id in {r['case_id'] for r in records}:
        same=[r for r in records if r['case_id']==id]
        if len(same)>1:stability[id]={'trials':len(same),'verdicts':dict(collections.Counter(r['verdict'] for r in same)),'agent_statuses':dict(collections.Counter(str(r.get('agent_status')) for r in same))}
    unique_verdicts=collections.Counter()
    for id in {r['case_id'] for r in records}:
        states={r['verdict'] for r in records if r['case_id']==id}
        unique_verdicts['FAIL' if 'FAIL' in states else 'NOT_EXECUTED' if 'NOT_EXECUTED' in states else 'REVIEW_PENDING' if 'REVIEW_PENDING' in states else 'PASS']+=1
    return {'unique_task_verdicts':dict(unique_verdicts),'executed_unique_tasks':len({r['case_id'] for r in records if r['verdict']!='NOT_EXECUTED'}),'planned_unique_tasks':len(planned_cases),'represented_unique_tasks':len({r['case_id'] for r in records}),'trials':len(records),'executed_trials':sum(r['verdict']!='NOT_EXECUTED' for r in records),
            'verdicts':dict(counts),'by_category':{k:dict(v) for k,v in by_category.items()},'modes':independent,'repeat_stability':stability,
            'model_http_attempts':len(model_calls),'model_successful_responses':len(success_models),'provider_usage':usage,
            'model_client_latency':latency([m['duration_ms'] for m in model_calls if m.get('duration_ms') is not None]),
            'analysis_calls':sum(len((t.get('execution') or {}).get('analysis_calls',[])) for r in records for t in r.get('turns',[])) if any(r.get('turns') for r in records) else None,
            'tool_calls':sum(len((t.get('execution') or {}).get('tool_calls',[])) for r in records for t in r.get('turns',[])) if any(r.get('turns') for r in records) else None,
            'quality_judge':'not implemented; UNKNOWN fields are not human review','cost':None,'token_estimation':False}
