"""Deterministic academic statistics over one parsed transcript; no model, DB or PDF I/O."""

from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
import re
import unicodedata

from academic_analysis import AcademicWarningEngine, normalize_course_name, is_pass_only_grade


LOW_SCORE_MIN = Decimal("60")
LOW_SCORE_MAX_EXCLUSIVE = Decimal("80")
_TERMS = {"秋季": (0, "秋季"), "春季": (1, "春季"),
          "国际小学期": (2, "国际小学期"), "夏季": (3, "夏季"), "暑期": (3, "夏季")}
_SEMESTER = re.compile(r"(\d{4})[-—－](\d{4})学年(秋季|春季|国际小学期|夏季|暑期)(?:学期)?")


def parse_semester(value):
    """Canonical label and chronological key; unknown/invalid years never infer a date."""
    text = re.sub(r"\s+", "", unicodedata.normalize("NFKC", str(value or "")))
    match = _SEMESTER.fullmatch(text)
    if not match:
        return None
    start, end = int(match[1]), int(match[2])
    if end != start + 1:
        return None
    rank, term = _TERMS[match[3]]
    term_label = term if term.endswith("学期") else term + "学期"
    return f"{start}-{end}学年{term_label}", (start, rank)


def _number(value):
    if value is None or isinstance(value, bool):
        return None
    try:
        result = Decimal(unicodedata.normalize("NFKC", str(value)).strip())
        return result if result.is_finite() and result >= 0 else None
    except InvalidOperation:
        return None


def _score(value):
    if value is None or isinstance(value, bool):
        return None, None
    text = unicodedata.normalize("NFKC", str(value)).strip()
    try:
        score = Decimal(text)
    except InvalidOperation:
        return AcademicWarningEngine.is_passed(text), None
    if not score.is_finite() or not 0 <= score <= 100:
        return None, None
    return AcademicWarningEngine.is_passed(str(score)), score


def _rounded(value, places=4):
    if value is None:
        return None
    return float(value.quantize(Decimal(1).scaleb(-places), rounding=ROUND_HALF_UP))


def _direction(change):
    if change is None:
        return "UNAVAILABLE"
    return "UP" if change > 0 else "DOWN" if change < 0 else "STABLE"


def _course_result(course, unresolved, passed_names):
    passed, score = _score(course.get("score"))
    name = str(course.get("name") or "")
    normalized = normalize_course_name(name)
    if passed is False:
        current_status = ("UNRESOLVED" if normalized in unresolved else
                          "PASSED_RECORD_EXISTS" if normalized in passed_names else "NEEDS_VERIFICATION")
    else:
        current_status = None
    return {
        "name": name,
        "score": str(course.get("score") if course.get("score") is not None else ""),
        "numeric_score": float(score) if score is not None else None,
        "credit": _rounded(_number(course.get("credit")), 2),
        "page": course.get("page"),
        "passed": passed,
        "current_failure_status": current_status,
    }


def _summarize(label, courses, unresolved, passed_names):
    items = [_course_result(course, unresolved, passed_names) for course in courses]
    credits = [_number(course.get("credit")) for course in courses]
    numeric = [_score(course.get("score"))[1] for course in courses]
    numeric = [value for value in numeric if value is not None]
    gpa_samples = []
    pass_only_count = sum(is_pass_only_grade(course.get("score")) for course in courses)
    for course in courses:
        # A transcript may put credit-equivalent points in a P row. They are not a GPA.
        if is_pass_only_grade(course.get("score")):
            continue
        credit, gpa = _number(course.get("credit")), _number(course.get("gpa"))
        # A missing/invalid final score cannot provide trustworthy GPA evidence.
        if credit is not None and credit > 0 and gpa is not None and _score(course.get("score"))[0] is not None:
            gpa_samples.append((credit, gpa))
    valid_credits = [credit for credit in credits if credit is not None]
    total_credit = sum(valid_credits, Decimal(0))
    gpa_credit = sum((credit for credit, _ in gpa_samples), Decimal(0))
    failed = [item for item in items if item["passed"] is False]
    low = [item for item, course in zip(items, courses) if _score(course.get("score"))[1] is not None
           and LOW_SCORE_MIN <= _score(course.get("score"))[1] < LOW_SCORE_MAX_EXCLUSIVE]
    unknown = [item for item in items if item["passed"] is None]
    focus = []
    for item in failed:
        if item["current_failure_status"] != "PASSED_RECORD_EXISTS":
            reason = "UNRESOLVED_FAILURE" if item["current_failure_status"] == "UNRESOLVED" else "FAILURE_NEEDS_VERIFICATION"
            focus.append(dict(item, reason=reason))
    focus.extend(dict(item, reason="LOW_NUMERIC_SCORE") for item in low)
    focus.extend(dict(item, reason="SCORE_UNAVAILABLE") for item in unknown)
    priorities = {"UNRESOLVED_FAILURE": 0, "FAILURE_NEEDS_VERIFICATION": 1,
                  "LOW_NUMERIC_SCORE": 2, "SCORE_UNAVAILABLE": 3}
    focus.sort(key=lambda item: (priorities[item["reason"]],
                                item["numeric_score"] if item["numeric_score"] is not None else 101,
                                normalize_course_name(item["name"])))
    notes = []
    if unknown:
        notes.append("部分课程成绩待确认")
    if len(gpa_samples) < len(courses) - pass_only_count:
        notes.append("部分课程缺少有效绩点或学分，GPA 统计覆盖不完整")
    if len(valid_credits) < len(courses):
        notes.append("部分课程学分缺失")
    return {
        "semester": label,
        "course_count": len(courses),
        "valid_score_course_count": sum(item["passed"] is not None for item in items),
        "passed_course_count": sum(item["passed"] is True for item in items),
        "failed_course_count": len(failed),
        "unknown_score_course_count": len(unknown),
        "attempted_credits": _rounded(total_credit, 2) if valid_credits else None,
        "valid_credit_course_count": len(valid_credits),
        "weighted_gpa": _rounded(sum((credit * gpa for credit, gpa in gpa_samples), Decimal(0)) / gpa_credit)
            if gpa_credit else None,
        "gpa_course_count": len(gpa_samples),
        "gpa_credits": _rounded(gpa_credit, 2),
        "gpa_course_coverage": _rounded(Decimal(len(gpa_samples)) / len(courses)),
        "gpa_credit_coverage": _rounded(gpa_credit / total_credit) if total_credit else None,
        "average_numeric_score": _rounded(sum(numeric, Decimal(0)) / len(numeric)) if numeric else None,
        "numeric_score_course_count": len(numeric),
        "numeric_score_coverage": _rounded(Decimal(len(numeric)) / len(courses)),
        "failed_courses": failed,
        "low_score_courses": low,
        "unknown_score_courses": unknown,
        "focus_courses": focus,
        "analysis_notes": notes,
    }


def build_academic_statistics(courses, report):
    """Build all evidence once; latest/previous use only semesters with known final scores."""
    unresolved = {normalize_course_name(item["name"]) for item in report.get("failed_courses", [])}
    passed_names = {normalize_course_name(course.get("name")) for course in courses
                    if _score(course.get("score"))[0] is True}
    grouped, keys, unknown_semesters = {}, {}, []
    for course in courses:
        parsed = parse_semester(course.get("semester"))
        if parsed is None:
            unknown_semesters.append(dict(_course_result(course, unresolved, passed_names),
                                          semester=str(course.get("semester") or "未知学期")))
            continue
        label, key = parsed
        keys[label] = key
        grouped.setdefault(label, []).append(course)
    summaries = [_summarize(label, grouped[label], unresolved, passed_names)
                 for label in sorted(grouped, key=keys.get)]
    valid = [summary for summary in summaries if summary["valid_score_course_count"] > 0]
    previous = None
    for summary in valid:
        if previous is None:
            summary["change_from_previous"] = None
        else:
            delta_gpa = (Decimal(str(summary["weighted_gpa"])) - Decimal(str(previous["weighted_gpa"]))
                         if summary["weighted_gpa"] is not None and previous["weighted_gpa"] is not None else None)
            delta_score = (Decimal(str(summary["average_numeric_score"])) - Decimal(str(previous["average_numeric_score"]))
                           if summary["average_numeric_score"] is not None and previous["average_numeric_score"] is not None else None)
            summary["change_from_previous"] = {
                "semester": previous["semester"],
                "weighted_gpa_delta": _rounded(delta_gpa),
                "gpa_direction": _direction(delta_gpa),
                "average_numeric_score_delta": _rounded(delta_score),
                "numeric_score_direction": _direction(delta_score),
                "failed_course_count_delta": summary["failed_course_count"] - previous["failed_course_count"],
            }
        previous = summary
    notes = list(report.get("analysis_notes", []))
    if unknown_semesters:
        notes.append("部分课程学期未知，未参与学期统计")
    if len(valid) < len(summaries):
        notes.append("部分学期尚无有效成绩，未参与趋势比较")
    return {
        "schema_version": 1,
        "rules": {"low_score_min": 60, "low_score_max_exclusive": 80,
                  "semester_order": ["秋季", "春季", "国际小学期", "夏季/暑期"],
                  "weighted_gpa": "sum(credit * gpa) / sum(valid credit); exclude P/pass-only records, retain earned credit; GPA coverage uses all course records",
                  "average_numeric_score": "arithmetic mean of valid 0..100 final scores",
                  "trend_direction": "sign of the difference between reported statistics"},
        "latest_semester": valid[-1]["semester"] if valid else None,
        "previous_semester": valid[-2]["semester"] if len(valid) >= 2 else None,
        "valid_semesters": [summary["semester"] for summary in valid],
        "semesters": summaries,
        "unknown_semester_courses": unknown_semesters,
        "analysis_notes": notes,
    }
