import argparse
import json
import re
import sys
import time
from datetime import datetime
from pathlib import Path


EVALUATION_DIR = Path(__file__).resolve().parent
SERVICE_DIR = EVALUATION_DIR.parent
if str(SERVICE_DIR) not in sys.path:
    sys.path.insert(0, str(SERVICE_DIR))

from AIAnalysis import rag_engine  # noqa: E402


def normalize_text(value):
    return re.sub(r"\s+", "", str(value or ""))


def evidence_coverage(text, groups):
    normalized = normalize_text(text)
    hits = []
    for alternatives in groups:
        hits.append(any(normalize_text(term) in normalized for term in alternatives))
    return hits, (sum(hits) / len(hits) if hits else 1.0)


def source_name(doc):
    return Path(doc.metadata.get("source", "unknown")).name


def retrieve(question):
    candidates = rag_engine.vectorstore.similarity_search(question, k=8, filter={"managed": "true"})
    selected = rag_engine._rerank_docs(question, candidates)
    return candidates, selected


def summarize_metrics(case_results, with_answers):
    total = len(case_results)
    source_hits = sum(item["retrieval"]["correct_source_hit"] for item in case_results)
    full_evidence_hits = sum(item["retrieval"]["full_evidence_hit"] for item in case_results)
    mean_coverage = sum(item["retrieval"]["evidence_coverage"] for item in case_results) / total
    mean_source_mrr = sum(item["retrieval"]["source_reciprocal_rank"] for item in case_results) / total
    expected_type_precision = sum(item["retrieval"]["expected_type_precision"] for item in case_results) / total
    metrics = {
        "case_count": total,
        "retrieval_correct_source_hit_at_4": source_hits / total,
        "retrieval_full_evidence_hit_at_4": full_evidence_hits / total,
        "retrieval_mean_evidence_coverage_at_4": mean_coverage,
        "retrieval_mean_source_reciprocal_rank_at_4": mean_source_mrr,
        "retrieval_mean_expected_type_precision_at_4": expected_type_precision,
    }
    if with_answers:
        successful = [item for item in case_results if item.get("answer", {}).get("success")]
        metrics.update(
            {
                "answer_success_rate": len(successful) / total,
                "answer_full_evidence_hit_rate": (
                    sum(item["answer"]["full_evidence_hit"] for item in successful) / len(successful)
                    if successful
                    else 0.0
                ),
                "answer_mean_evidence_coverage": (
                    sum(item["answer"]["evidence_coverage"] for item in successful) / len(successful)
                    if successful
                    else 0.0
                ),
                "answer_mean_latency_seconds": (
                    sum(item["answer"]["latency_seconds"] for item in successful) / len(successful)
                    if successful
                    else 0.0
                ),
            }
        )
    return metrics


def main():
    parser = argparse.ArgumentParser(description="Evaluate the current RAG retrieval and answer baseline.")
    parser.add_argument("--label", default="baseline")
    parser.add_argument("--with-answers", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    cases = json.loads((EVALUATION_DIR / "cases.json").read_text(encoding="utf-8"))
    case_results = []

    for index, case in enumerate(cases, start=1):
        candidates, selected = retrieve(case["question"])
        context = "\n\n".join(doc.page_content for doc in selected)
        evidence_hits, coverage = evidence_coverage(context, case["evidence_groups"])
        source_matches = [case["expected_source_contains"] in source_name(doc) for doc in selected]
        source_rank = next((rank for rank, matched in enumerate(source_matches, start=1) if matched), None)
        expected_type_count = sum(
            doc.metadata.get("doc_type") == case["expected_doc_type"] for doc in selected
        )
        result = {
            "id": case["id"],
            "question": case["question"],
            "expected_doc_type": case["expected_doc_type"],
            "expected_source_contains": case["expected_source_contains"],
            "evidence_groups": case["evidence_groups"],
            "retrieval": {
                "candidate_count": len(candidates),
                "selected_count": len(selected),
                "correct_source_hit": any(source_matches),
                "source_rank": source_rank,
                "source_reciprocal_rank": 1.0 / source_rank if source_rank else 0.0,
                "expected_type_precision": expected_type_count / len(selected) if selected else 0.0,
                "evidence_hits": evidence_hits,
                "evidence_coverage": coverage,
                "full_evidence_hit": all(evidence_hits),
                "selected": [
                    {
                        "source": source_name(doc),
                        "doc_type": doc.metadata.get("doc_type"),
                        "page_range": doc.metadata.get("page_range"),
                        "preview": normalize_text(doc.page_content)[:180],
                    }
                    for doc in selected
                ],
            },
        }

        if args.with_answers:
            started = time.perf_counter()
            try:
                answer_result = rag_engine.ask(case["question"])
                latency = time.perf_counter() - started
                answer_hits, answer_coverage = evidence_coverage(
                    answer_result["answer"], case["evidence_groups"]
                )
                result["answer"] = {
                    "success": True,
                    "latency_seconds": round(latency, 3),
                    "evidence_hits": answer_hits,
                    "evidence_coverage": answer_coverage,
                    "full_evidence_hit": all(answer_hits),
                    "text": answer_result["answer"],
                    "sources": answer_result["sources"],
                }
            except Exception as exc:
                result["answer"] = {
                    "success": False,
                    "latency_seconds": round(time.perf_counter() - started, 3),
                    "error": str(exc),
                }

        case_results.append(result)
        print(f"[{index:02d}/{len(cases):02d}] {case['id']}: retrieval={coverage:.0%}")

    output = {
        "label": args.label,
        "generated_at": datetime.now().astimezone().isoformat(timespec="seconds"),
        "configuration": {
            "embedding_model": "BAAI/bge-small-zh-v1.5",
            "vector_collection": "student_policy_bge_zh",
            "candidate_k": 8,
            "selected_k": 4,
            "with_answers": args.with_answers,
        },
        "metrics": summarize_metrics(case_results, args.with_answers),
        "cases": case_results,
    }

    output_path = args.output or EVALUATION_DIR / "results" / f"{args.label}.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(output["metrics"], ensure_ascii=False, indent=2))
    print(f"result={output_path}")


if __name__ == "__main__":
    main()
