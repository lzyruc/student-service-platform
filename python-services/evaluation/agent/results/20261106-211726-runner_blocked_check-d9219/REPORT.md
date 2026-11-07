# Academic Agent evaluation baseline

Run: `20261106-211726-runner_blocked_check-d9219` · code `5d1fe0e7ceb1` · mode `live`

Dataset `academic-agent-tasks-v1`, fixture `academic-eval-fixture-v1`. Existing RAG retrieval results are excluded.

| Measure | Observed |
| --- | --- |
| Unique selected tasks | 1 |
| Trials / executed | 2 / 0 |
| PASS / FAIL / NOT_EXECUTED / REVIEW_PENDING | 0 / 0 / 2 / 0 |
| Actual model HTTP attempts / successful responses | 0 / 0 |
| Python analysis calls during Agent requests | None |

PASS is deterministic contract success. REVIEW_PENDING passed rules but explanation quality is UNKNOWN; it is not an accepted task-success claim. Model errors count as failures; missing environments remain NOT_EXECUTED. Mock choices never count as model-selection accuracy.

## Separate execution modes

### live_e2e

{
  "trials": 2,
  "verdicts": {
    "NOT_EXECUTED": 2
  },
  "http_success_latency": {
    "sample_count": 0,
    "p50_ms": null,
    "p95_ms": null
  },
  "http_failure_latency": {
    "sample_count": 0,
    "p50_ms": null,
    "p95_ms": null
  },
  "junit_seconds": null,
  "rule_pass_request_latency": {
    "sample_count": 0,
    "p50_ms": null,
    "p95_ms": null
  },
  "rule_fail_request_latency": {
    "sample_count": 0,
    "p50_ms": null,
    "p95_ms": null
  },
  "expected_rejection_latency": {
    "sample_count": 0,
    "p50_ms": null,
    "p95_ms": null
  }
}

HTTP latency is sampled per actual chat request, not per independent task. Successful and failed HTTP/business responses are separate; expected 404 is reported separately and can pass its task; rule-pass/rule-fail latencies distinguish HTTP-200 bad answers; JUnit durations are not E2E latency. Model-client timing includes serialization/transport/validation. Missing fields are null.

## Cases

| Case | Trial | Verdict | Failed checks |
| --- | --- | --- | --- |
| N02 | N02-1 | NOT_EXECUTED | completed_trial: environment_or_startup_blocked |
| N02 | N02-2 | NOT_EXECUTED | completed_trial: environment_or_startup_blocked |

## Provider usage (actual API fields)

```json
{
  "promptTokens": {
    "sample_count": 0,
    "sum_observed": null,
    "complete_coverage": false
  },
  "completionTokens": {
    "sample_count": 0,
    "sum_observed": null,
    "complete_coverage": false
  },
  "totalTokens": {
    "sample_count": 0,
    "sum_observed": null,
    "complete_coverage": false
  },
  "promptCacheHitTokens": {
    "sample_count": 0,
    "sum_observed": null,
    "complete_coverage": false
  },
  "promptCacheMissTokens": {
    "sample_count": 0,
    "sum_observed": null,
    "complete_coverage": false
  }
}
```

Partial usage totals cover only responses that supplied the field; a missing or failed call is not zero tokens. No cost estimate or character-based token estimate is made.

## Stability

```json
{
  "N02": {
    "trials": 2,
    "verdicts": {
      "NOT_EXECUTED": 2
    },
    "agent_statuses": {
      "None": 2
    }
  }
}
```

## Evidence and limits

- See manifest.json, database.json / java-environment.json (live), observations.jsonl, scored.json and regression-evidence.json (regression).
- Synthetic identities, transcripts and plans only. Real model + MySQL + Python are live only when recorded; replay uses existing H2/Mock tests.
- Explanation/source-attribution quality is UNKNOWN until a reviewer records a judgement. Optional LLM Judge is not implemented.
- This is a small serial task baseline, not throughput, production load or a measured improvement.
- Each live run creates its own student_agent_eval_v1_* schema; existing business tables are never initialized, reset or cleaned. See README for cleanup scope.
