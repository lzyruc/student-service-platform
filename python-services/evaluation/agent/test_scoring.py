import copy,json,tempfile,unittest
from pathlib import Path
from scoring import score,summarize,percentile,number_in

class ScoringTests(unittest.TestCase):
    def task(self,kind='overview'):
        return {'case_id':'T','category':'normal','messages':['question'],'expected':{'kind':kind},'rubric':['explanation']}
    def observation(self):
        return {'case_id':'T','trial_id':'T-1','mode':'live_e2e','real_model':True,'formal_warning_records':0,'turns':[
            {'turn':1,'http_status':200,'end_to_end_ms':10,'response':{'data':{'status':'COMPLETED','answer':'官方GPA2.63，已获15.0学分，操作系统未通过。'}},
             'execution':{'tool_calls':[{'tool':'get_academic_assessment','status':'OK','data':{'officialGpa':2.63,'totalEarnedCredits':15,'failedCourses':[{'name':'操作系统'}]}}],
                          'analysis_calls':[{'duration_ms':2}],'model_calls':[{'network_attempted':True,'outcome':'success','duration_ms':3,'usage':None}]}}]}
    def test_correct_facts_remain_pending_not_human_review(self):
        r=score(self.task(),self.observation());self.assertEqual('REVIEW_PENDING',r['verdict']);self.assertEqual('UNKNOWN',r['review']['status'])
    def test_numeric_formats_match_but_other_numbers_do_not(self):
        self.assertTrue(number_in('15.0 学分',15));self.assertTrue(number_in('3.30',3.3));self.assertFalse(number_in('150 学分',15));self.assertFalse(number_in('3.315',3.3))
    def test_http_failure_with_text_is_not_success(self):
        r=self.observation();r['turns'][0]['http_status']=500;self.assertEqual('FAIL',score(self.task(),r)['verdict'])
    def test_wrong_agent_status_is_not_success(self):
        r=self.observation();r['turns'][0]['response']['data']['status']='DATA_UNAVAILABLE';self.assertEqual('FAIL',score(self.task(),r)['verdict'])
    def test_answer_and_tool_must_both_have_current_fact(self):
        r=self.observation();r['turns'][0]['response']['data']['answer']='GPA4.0，15学分，操作系统';self.assertEqual('FAIL',score(self.task(),r)['verdict'])
        r=self.observation();r['turns'][0]['execution']['tool_calls'][0]['data']['officialGpa']=4;self.assertEqual('FAIL',score(self.task(),r)['verdict'])
    def test_missing_and_malformed_observation_not_executed(self):
        for record in ({'turns':[],'error':'startup'},{'turns':'bad'}):self.assertEqual('NOT_EXECUTED',score(self.task(),record)['verdict'])
    def test_missing_usage_is_null_and_partial_coverage_explicit(self):
        r=score(self.task(),self.observation());s=summarize([r],[self.task()]);self.assertIsNone(s['provider_usage']['totalTokens']['sum_observed']);self.assertFalse(s['provider_usage']['totalTokens']['complete_coverage']);self.assertEqual(0,s['provider_usage']['totalTokens']['sample_count'])
    def test_repeat_and_mode_denominators_are_separate(self):
        r=score(self.task(),self.observation());r2=copy.deepcopy(r);r2['trial_id']='T-2'
        b={'case_id':'R','trial_id':'R-1','mode':'engineering_regression','category':'regression','verdict':'PASS','turns':[],'junit_seconds':.2}
        s=summarize([r,r2,b],[self.task(),{'case_id':'R'}]);self.assertEqual(2,s['planned_unique_tasks']);self.assertEqual(3,s['trials']);self.assertEqual(2,s['modes']['live_e2e']['http_success_latency']['sample_count']);self.assertIsNone(s['modes']['engineering_regression']['http_success_latency']['p50_ms'])
    def test_success_failure_latencies_are_separate(self):
        r=score(self.task(),self.observation());failed=self.observation();failed['turns'][0]['http_status']=500;failed['turns'][0]['end_to_end_ms']=100
        s=summarize([r,score(self.task(),failed)],[self.task()]);g=s['modes']['live_e2e'];self.assertEqual(10,g['http_success_latency']['p50_ms']);self.assertEqual(100,g['http_failure_latency']['p50_ms'])
    def test_percentiles_and_empty_metrics(self):
        self.assertIsNone(percentile([],.95));self.assertEqual(15,percentile([0,10,20,30],.5));self.assertAlmostEqual(28.5,percentile([0,10,20,30],.95))
    def test_updated_fixture_rejects_old_gpa(self):
        r=self.observation();task=self.task('update');self.assertEqual('FAIL',score(task,r)['verdict'])
    def test_one_failed_trial_does_not_erase_other_results(self):
        good=score(self.task(),self.observation());bad=score(self.task(),{'case_id':'T','trial_id':'T-2','turns':[],'error':'network'})
        s=summarize([good,bad],[self.task()]);self.assertEqual(2,s['trials']);self.assertEqual(1,s['verdicts']['NOT_EXECUTED']);self.assertEqual(1,s['executed_trials'])
    def test_malformed_http_observation_cannot_pass(self):
        r=self.observation();r['turns'][0]['response']=[];self.assertEqual('FAIL',score(self.task(),r)['verdict'])
    def test_duplicate_tool_call_is_not_hidden_by_snapshot(self):
        r=self.observation();r['turns'][0]['execution']['tool_calls']*=2
        self.assertEqual('FAIL',score(self.task(),r)['verdict'])
    def test_reader_retains_valid_records_after_invalid_line(self):
        from run import read_observations
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'raw.jsonl';p.write_text(json.dumps({'case_id':'A','trial_id':'A-1'})+'\nnot-json\n[]\n'+json.dumps({'case_id':'B','trial_id':'B-1'}),encoding='utf-8')
            records,issues=read_observations(p);self.assertEqual({'A-1','B-1'},set(records));self.assertEqual(2,len(issues))
    def test_duplicate_trial_is_not_silently_replaced_as_success(self):
        from run import read_observations
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'raw.jsonl';item=json.dumps({'case_id':'A','trial_id':'A-1'});p.write_text(item+'\n'+item,encoding='utf-8')
            records,issues=read_observations(p);self.assertEqual('duplicate_trial_record',records['A-1']['error']);self.assertEqual(1,len(issues))
    def test_log_redaction_happens_before_disk(self):
        from run import safe_log_text
        from unittest.mock import patch
        with patch('run.log_secrets',return_value=('real-config-secret',)):
            text=safe_log_text('password=somePassword123 Authorization: Bearer abc.def.ghi real-config-secret')
            self.assertNotIn('somePassword123',text);self.assertNotIn('abc.def.ghi',text);self.assertNotIn('real-config-secret',text)
    def test_regression_missing_timing_is_unavailable(self):
        r={'case_id':'R','trial_id':'R-1','mode':'engineering_regression','category':'regression','verdict':'PASS','turns':[]}
        self.assertIsNone(summarize([r],[self.task()])['analysis_calls'])
    def test_correct_followup_tool_with_generic_fallback_is_failure(self):
        task=self.task('followup');task['messages']=['first','second']
        r=self.observation();first=copy.deepcopy(r['turns'][0]);last=copy.deepcopy(first);last['turn']=2
        first['response']['data']['answer']='本次未采用未经核实的GPA描述。'
        last['response']['data']['answer']='本次未采用未经核实的GPA描述。'
        last['execution']['server_history']=[{'role':'user','content':'first'}]
        last['execution']['tool_calls']=[{'tool':'get_recent_course_performance','status':'OK','arguments':{'semester':'previous'},'data':{'semester':'春季','focusCourses':[],'summary':{'failedCourseCount':0}}}]
        r['turns']=[first,last]
        result=score(task,r);self.assertEqual('FAIL',result['verdict']);self.assertTrue(any(c['name']=='previous_result_reaches_answer' and not c['pass'] for c in result['checks']))
    def test_generated_fixture_parses_into_independent_expected_facts(self):
        from fixtures import generate,verify
        with tempfile.TemporaryDirectory() as folder:
            generate(folder);result=verify(folder)
            self.assertEqual(2.63,result['facts']['base']['official_gpa']);self.assertEqual(3.3,result['facts']['updated']['official_gpa']);self.assertEqual(0,result['provider_calls'])
    def test_unexpected_grader_exception_does_not_abort_batch(self):
        from run import safe_score
        from unittest.mock import patch
        with patch('run.score',side_effect=RuntimeError('fixture validation bug')):
            bad=safe_score(self.task(),self.observation())
        good=safe_score(self.task(),self.observation())
        self.assertEqual('FAIL',bad['verdict']);self.assertEqual('scorer_exception',bad['checks'][0]['name']);self.assertEqual('REVIEW_PENDING',good['verdict'])
    def test_missing_data_response_without_tool_evidence_is_failure(self):
        task=self.task('missing');task['expected']['allowed_status']=['DATA_UNAVAILABLE']
        r=self.observation();r['turns'][0]['response']['data']={'status':'DATA_UNAVAILABLE','answer':'缺少资料，请先上传成绩单。'}
        r['turns'][0]['execution']['tool_calls']=[]
        self.assertEqual('FAIL',score(task,r)['verdict'])
if __name__=='__main__':unittest.main()
