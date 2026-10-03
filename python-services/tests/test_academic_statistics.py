import copy
import unittest

from academic_analysis import AcademicWarningEngine
from academic_statistics import build_academic_statistics, parse_semester


AUTUMN = "2024-2025学年秋季学期"
SPRING = "2024-2025学年春季学期"


def course(name, score="80", credit=3, gpa=None, semester=AUTUMN):
    return {"name": name, "score": score, "credit": credit, "gpa": gpa, "semester": semester}


def statistics(courses):
    return build_academic_statistics(courses, AcademicWarningEngine().analyze(courses))


class AcademicStatisticsTests(unittest.TestCase):
    def test_latest_and_previous_follow_academic_time_not_input_order(self):
        data = statistics([
            course("最新", semester="2025-2026学年春季学期"),
            course("旧", semester=AUTUMN),
            course("上一", semester="2025-2026学年秋季学期"),
        ])
        self.assertEqual("2025-2026学年春季学期", data["latest_semester"])
        self.assertEqual("2025-2026学年秋季学期", data["previous_semester"])
        self.assertEqual([AUTUMN, "2025-2026学年秋季学期", "2025-2026学年春季学期"], data["valid_semesters"])

    def test_semester_normalization_merges_aliases_and_rejects_invalid_years(self):
        self.assertEqual((AUTUMN, (2024, 0)), parse_semester(" ２０２４ — ２０２５ 学年 秋季 "))
        self.assertEqual(parse_semester("2024-2025学年夏季"), parse_semester("2024-2025学年暑期学期"))
        for value in (None, "未知学期", "2024-2027学年春季", "2025-2024学年春季", "第4学期", "2024-2025学年春季考试"):
            self.assertIsNone(parse_semester(value))
        data = statistics([course("A", semester="2024-2025学年春季"), course("B", semester="2024-2025学年春季学期")])
        self.assertEqual(1, len(data["semesters"]))
        self.assertEqual(2, data["semesters"][0]["course_count"])

    def test_short_terms_have_an_explicit_order_and_summer_alias_is_one_period(self):
        data = statistics([course("夏", semester="2024-2025学年暑期"),
                           course("春", semester=SPRING), course("秋", semester=AUTUMN),
                           course("国际", semester="2024-2025学年国际小学期"),
                           course("夏2", semester="2024-2025学年夏季")])
        self.assertEqual([AUTUMN, SPRING, "2024-2025学年国际小学期", "2024-2025学年夏季学期"], data["valid_semesters"])
        self.assertEqual(2, data["semesters"][-1]["course_count"])

    def test_unknown_semester_is_preserved_but_excluded_from_time_selection(self):
        data = statistics([course("已知"), course("未知", "55", semester="未知学期")])
        self.assertEqual(AUTUMN, data["latest_semester"])
        self.assertEqual("未知", data["unknown_semester_courses"][0]["name"])
        self.assertEqual("UNRESOLVED", data["unknown_semester_courses"][0]["current_failure_status"])
        self.assertTrue(any("学期未知" in note for note in data["analysis_notes"]))

    def test_period_without_known_final_scores_is_not_latest(self):
        data = statistics([course("已完成"), course("待出分", "", semester=SPRING)])
        self.assertEqual(AUTUMN, data["latest_semester"])
        self.assertIsNone(data["previous_semester"])
        self.assertEqual(2, len(data["semesters"]))
        self.assertEqual(0, data["semesters"][1]["valid_score_course_count"])

    def test_all_unknown_scores_leave_latest_and_previous_unavailable(self):
        data = statistics([course("缓考", "缓考", gpa=4)])
        self.assertIsNone(data["latest_semester"])
        self.assertIsNone(data["previous_semester"])
        self.assertIsNone(data["semesters"][0]["weighted_gpa"])
        self.assertEqual("SCORE_UNAVAILABLE", data["semesters"][0]["focus_courses"][0]["reason"])

    def test_low_score_boundaries_use_unrounded_final_scores_and_do_not_change_risk(self):
        courses = [course("未过", "59.9"), course("边界", "60"), course("低", "69.99999"), course("正常", "70")]
        engine = AcademicWarningEngine()
        report = engine.analyze(courses)
        before = copy.deepcopy(report)
        data = build_academic_statistics(courses, report)
        period = data["semesters"][0]
        self.assertEqual(["边界", "低"], [item["name"] for item in period["low_score_courses"]])
        self.assertEqual(69.99999, period["low_score_courses"][1]["numeric_score"])
        self.assertEqual("UNRESOLVED_FAILURE", period["focus_courses"][0]["reason"])
        self.assertEqual(before, report)
        self.assertEqual("一般预警", report["warning_level"])
        passed = statistics([course("低", "60")])
        self.assertEqual("LOW_NUMERIC_SCORE", passed["semesters"][0]["focus_courses"][0]["reason"])

    def test_gpa_is_credit_weighted_and_numeric_average_excludes_letter_and_pass_grades(self):
        data = statistics([course("A", "90", 3, 4), course("B", "70", 1, 2),
                           course("C", "40", 2, 0), course("实践", "P", 3)])
        period = data["semesters"][0]
        self.assertEqual(2.3333, period["weighted_gpa"])
        self.assertEqual(66.6667, period["average_numeric_score"])
        self.assertEqual(9, period["attempted_credits"])
        self.assertEqual(3, period["passed_course_count"])
        self.assertEqual(1, period["failed_course_count"])
        self.assertEqual(3, period["gpa_course_count"])
        self.assertEqual(6, period["gpa_credits"])
        self.assertEqual(0.75, period["gpa_course_coverage"])
        self.assertEqual(0.6667, period["gpa_credit_coverage"])
        self.assertEqual(0.75, period["numeric_score_coverage"])

    def test_letter_grades_use_existing_pass_rules_without_inventing_numeric_scores(self):
        period = statistics([course("优秀", "A", gpa=4), course("未过", "F", gpa=0),
                             course("通过", "P"), course("中文", "良好")])["semesters"][0]
        self.assertEqual(3, period["passed_course_count"])
        self.assertEqual(1, period["failed_course_count"])
        self.assertEqual(2, period["weighted_gpa"])
        self.assertIsNone(period["average_numeric_score"])
        self.assertEqual([], period["low_score_courses"])

    def test_spring_transcript_pass_row_does_not_reverse_gpa_trend(self):
        autumn = "2025-2026学年秋季学期"
        spring = "2025-2026学年春季学期"
        # Nine final numeric grades plus one P row from the verified transcript.
        entries = [
            course("乒乓球", "83", 1, 3.3, spring),
            course("操作系统", "88", 4, 3.7, spring),
            course("数据结构与算法II", "83", 3, 3.3, spring),
            course("运筹学建模与算法", "88", 3, 3.7, spring),
            course("机器学习", "87", 3, 3.7, spring),
            course("普通物理B", "96", 4, 4.0, spring),
            course("毛概", "91", 3, 4.0, spring),
            course("思政实践课", "P", 2, 1.0, spring),
            course("软件工程导论", "89", 2, 3.7, spring),
            course("教育公平的理论与实践", "88", 2, 3.7, spring),
        ]
        data = statistics([course("上一学期统计样本", "88.2", 31, 3.7065, autumn)] + entries)
        period = data["semesters"][-1]
        self.assertEqual(10, period["course_count"])
        self.assertEqual(10, period["passed_course_count"])
        self.assertEqual(27, period["attempted_credits"])
        self.assertEqual(9, period["gpa_course_count"])
        self.assertEqual(25, period["gpa_credits"])
        self.assertEqual(3.72, period["weighted_gpa"])
        self.assertEqual(88.1111, period["average_numeric_score"])
        self.assertEqual(9, period["numeric_score_course_count"])
        self.assertEqual(0.0135, period["change_from_previous"]["weighted_gpa_delta"])
        self.assertEqual("UP", period["change_from_previous"]["gpa_direction"])
        self.assertEqual("DOWN", period["change_from_previous"]["numeric_score_direction"])
        self.assertFalse(any("统计覆盖不完整" in note for note in period["analysis_notes"]))
        self.assertTrue(any("P/通过制" in note for note in period["analysis_notes"]))
        self.assertEqual(27, AcademicWarningEngine().analyze(entries)["total_earned_credits"])

    def test_explicit_pass_grades_are_excluded_even_with_spurious_gpa_but_zero_failures_are_kept(self):
        for score in ("P", "p", "Ｐ", "通过"):
            with self.subTest(score=score):
                period = statistics([course("实践", score, 2, 1), course("普通课程", "90", 3, 4)])["semesters"][0]
                self.assertEqual(4, period["weighted_gpa"])
                self.assertEqual(5, period["attempted_credits"])
                self.assertEqual(2, period["passed_course_count"])
        period = statistics([course("字母挂科", "F", 3, 0), course("数值挂科", "0", 2, 0)])["semesters"][0]
        self.assertEqual(0, period["weighted_gpa"])
        self.assertEqual(2, period["gpa_course_count"])
        self.assertEqual(2, period["failed_course_count"])

    def test_pass_only_semester_has_credit_and_known_outcome_but_no_gpa(self):
        period = statistics([course("实践", "P", 2, 1), course("研讨", "通过", 1, 1)])["semesters"][0]
        self.assertIsNone(period["weighted_gpa"])
        self.assertIsNone(period["average_numeric_score"])
        self.assertEqual(0, period["gpa_course_count"])
        self.assertEqual(3, period["attempted_credits"])
        self.assertEqual(2, period["passed_course_count"])
        self.assertFalse(any("统计覆盖不完整" in note for note in period["analysis_notes"]))

    def test_missing_gpa_remains_null_while_real_zero_is_included(self):
        missing = statistics([course("通过", "80")])["semesters"][0]
        self.assertIsNone(missing["weighted_gpa"])
        zero = statistics([course("挂科", "0", 3, 0)])["semesters"][0]
        self.assertEqual(0, zero["weighted_gpa"])
        self.assertEqual(0, zero["average_numeric_score"])
        self.assertEqual(1, zero["failed_course_count"])

    def test_nonfinite_out_of_range_scores_and_invalid_credits_are_not_zero_filled(self):
        courses = [course(str(value), value, credit=None, gpa=float("nan"))
                   for value in ("NaN", "Infinity", "101", "-1", True, "缓考", "")]
        period = build_academic_statistics(courses, {"failed_courses": []})["semesters"][0]
        self.assertEqual(len(courses), period["unknown_score_course_count"])
        self.assertIsNone(period["weighted_gpa"])
        self.assertIsNone(period["average_numeric_score"])
        self.assertIsNone(period["attempted_credits"])

    def test_missing_or_zero_credit_cannot_supply_weighted_gpa(self):
        period = build_academic_statistics(
            [course("无学分", credit=None, gpa=4), course("零学分", credit=0, gpa=4)],
            {"failed_courses": []})["semesters"][0]
        self.assertIsNone(period["weighted_gpa"])
        self.assertIsNone(period["gpa_credit_coverage"])
        self.assertEqual(0, period["gpa_course_count"])
        self.assertEqual(1, period["valid_credit_course_count"])

    def test_retaken_pass_clears_current_focus_but_preserves_historical_failure(self):
        data = statistics([course("程序设计Ⅰ", "50", 4, 0), course("程序设计I", "85", 4, 3.5, SPRING)])
        historical = data["semesters"][0]
        self.assertEqual(1, historical["failed_course_count"])
        self.assertEqual("PASSED_RECORD_EXISTS", historical["failed_courses"][0]["current_failure_status"])
        self.assertEqual([], historical["focus_courses"])
        self.assertEqual(4, data["semesters"][1]["attempted_credits"])
        self.assertEqual(-1, data["semesters"][1]["change_from_previous"]["failed_course_count_delta"])

    def test_distinct_course_levels_are_not_cleared_by_another_pass(self):
        period = statistics([course("程序设计Ⅱ", "50"), course("程序设计I", "85")])["semesters"][0]
        self.assertEqual("UNRESOLVED", period["failed_courses"][0]["current_failure_status"])
        self.assertEqual("程序设计Ⅱ", period["focus_courses"][0]["name"])

    def test_trend_differences_are_deterministic_and_missing_metrics_stay_unavailable(self):
        data = statistics([course("春", "80", gpa=3.5, semester=SPRING), course("秋", "60", gpa=2)])
        change = data["semesters"][1]["change_from_previous"]
        self.assertEqual(AUTUMN, change["semester"])
        self.assertEqual(1.5, change["weighted_gpa_delta"])
        self.assertEqual(20, change["average_numeric_score_delta"])
        self.assertEqual("UP", change["gpa_direction"])
        missing = statistics([course("秋", "P"), course("春", "P", semester=SPRING)])
        self.assertIsNone(missing["semesters"][1]["change_from_previous"]["weighted_gpa_delta"])
        self.assertEqual("UNAVAILABLE", missing["semesters"][1]["change_from_previous"]["numeric_score_direction"])

    def test_cohort_warning_is_preserved_without_controlling_time_selection(self):
        engine = AcademicWarningEngine({"grade": "2026", "core_courses": []})
        engine.transcript_metadata = {"grade": "2024"}
        courses = [course("A", semester="2025-2026学年春季学期")]
        report = engine.analyze(courses)
        data = build_academic_statistics(courses, report)
        self.assertEqual("2025-2026学年春季学期", data["latest_semester"])
        self.assertTrue(any("2024" in note and "2026" in note for note in data["analysis_notes"]))
        self.assertEqual(4, report["completed_semester"])


if __name__ == "__main__":
    unittest.main()
