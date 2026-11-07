# Academic Agent evaluation baseline

Run: `20261106-215525-grounding_full-b5a53` · code `5d1fe0e7ceb1` · mode `live`

Dataset `academic-agent-tasks-v1`, fixture `academic-eval-fixture-v1`. Existing RAG retrieval results are excluded.

| Measure | Observed |
| --- | --- |
| Unique selected tasks | 13 |
| Trials / executed | 15 / 15 |
| PASS / FAIL / NOT_EXECUTED / REVIEW_PENDING | 7 / 0 / 0 / 8 |
| Actual model HTTP attempts / successful responses | 30 / 30 |
| Python analysis calls during Agent requests | 10 |

PASS is deterministic contract success. REVIEW_PENDING passed rules but explanation quality is UNKNOWN; it is not an accepted task-success claim. Model errors count as failures; missing environments remain NOT_EXECUTED. Mock choices never count as model-selection accuracy.

## Separate execution modes

### live_e2e

{
  "trials": 15,
  "verdicts": {
    "REVIEW_PENDING": 8,
    "PASS": 7
  },
  "http_success_latency": {
    "sample_count": 17,
    "p50_ms": 3181.575501,
    "p95_ms": 4677.6356202
  },
  "http_failure_latency": {
    "sample_count": 1,
    "p50_ms": 35.64,
    "p95_ms": 35.64
  },
  "junit_seconds": null,
  "rule_pass_request_latency": {
    "sample_count": 18,
    "p50_ms": 3042.3605504999996,
    "p95_ms": 4647.850890149999
  },
  "rule_fail_request_latency": {
    "sample_count": 0,
    "p50_ms": null,
    "p95_ms": null
  },
  "expected_rejection_latency": {
    "sample_count": 1,
    "p50_ms": 35.64,
    "p95_ms": 35.64
  }
}

HTTP latency is sampled per actual chat request, not per independent task. Successful and failed HTTP/business responses are separate; expected 404 is reported separately and can pass its task; rule-pass/rule-fail latencies distinguish HTTP-200 bad answers; JUnit durations are not E2E latency. Model-client timing includes serialization/transport/validation. Missing fields are null.

## Cases

| Case | Trial | Verdict | Failed checks |
| --- | --- | --- | --- |
| N01 | N01-1 | REVIEW_PENDING |  |
| N02 | N02-1 | REVIEW_PENDING |  |
| N02 | N02-2 | REVIEW_PENDING |  |
| N03 | N03-1 | REVIEW_PENDING |  |
| N04 | N04-1 | REVIEW_PENDING |  |
| M01 | M01-1 | REVIEW_PENDING |  |
| M02 | M02-1 | REVIEW_PENDING |  |
| B01 | B01-1 | PASS |  |
| B02 | B02-1 | PASS |  |
| B03 | B03-1 | PASS |  |
| B04 | B04-1 | PASS |  |
| S01 | S01-1 | PASS |  |
| S01 | S01-2 | PASS |  |
| S02 | S02-1 | PASS |  |
| U01 | U01-1 | REVIEW_PENDING |  |

## Provider usage (actual API fields)

```json
{
  "promptTokens": {
    "sample_count": 30,
    "sum_observed": 95231,
    "complete_coverage": true
  },
  "completionTokens": {
    "sample_count": 30,
    "sum_observed": 4411,
    "complete_coverage": true
  },
  "totalTokens": {
    "sample_count": 30,
    "sum_observed": 99642,
    "complete_coverage": true
  },
  "promptCacheHitTokens": {
    "sample_count": 30,
    "sum_observed": 83838,
    "complete_coverage": true
  },
  "promptCacheMissTokens": {
    "sample_count": 30,
    "sum_observed": 11393,
    "complete_coverage": true
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
      "REVIEW_PENDING": 2
    },
    "agent_statuses": {
      "COMPLETED": 2
    }
  },
  "S01": {
    "trials": 2,
    "verdicts": {
      "PASS": 2
    },
    "agent_statuses": {
      "REFUSED": 2
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
