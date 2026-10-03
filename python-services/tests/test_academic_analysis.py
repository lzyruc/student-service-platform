import unittest
from unittest.mock import MagicMock, patch

from academic_analysis import AcademicWarningEngine, normalize_course_name


class AcademicAnalysisTests(unittest.TestCase):
    def test_reads_final_score_integer_credits_wrapped_names_and_cross_page_semesters(self):
        header = ["课程名称", "教师", "课程性质", "学分", "平时成绩", "期中成绩", "期末成绩", "最终成绩", "学分绩点", "成绩标志"]
        first = MagicMock()
        first.extract_text.return_value = "年级：2024"
        first.extract_tables.return_value = [[
            header,
            ["高等代数Ⅱ", "教师", "部类共同", "4", "94", "", "59", "77", "10.8", ""],
            ["习近平新时代中国特色\n社会主义思想概论", "教师", "思想政治理\n论课", "3", "90", "", "92", "91", "12", ""],
        ]]
        second = MagicMock()
        second.extract_text.return_value = "各学期汇总: 已取得总学分:9 总学分绩点:24.8 平均学分绩点:3.65"
        second.extract_tables.return_value = [[
            ["2025-2026学年秋季学期: 已取得总学分:7 平均学分绩点:3.25"] + [None] * 9,
            ["思政实践课", "教师", "思想政治理\n论课", "2", "", "", "", "P", "2", ""],
            ["2025-2026学年春季学期: 已取得总学分:2 平均学分绩点:1.00"] + [None] * 9,
        ]]
        with patch("academic_analysis.pdfplumber.open") as opened:
            opened.return_value.__enter__.return_value.pages = [first, second]
            engine = AcademicWarningEngine()
            courses, gpa = engine.parse_ruc_transcript("fixture.pdf")
        self.assertEqual(3, len(courses))
        self.assertEqual("77", courses[0]["score"])
        self.assertEqual(2.7, courses[0]["gpa"])
        self.assertEqual("习近平新时代中国特色社会主义思想概论", courses[1]["name"])
        self.assertEqual("思想政治理论课", courses[1]["category"])
        self.assertEqual("2025-2026学年秋季学期", courses[0]["semester"])
        self.assertEqual("2025-2026学年春季学期", courses[2]["semester"])
        self.assertIsNone(courses[2]["gpa"])
        self.assertEqual(3.65, gpa)
        self.assertTrue(engine.transcript_metadata["official_gpa_available"])
        self.assertEqual(9, engine.analyze(courses, gpa)["total_earned_credits"])

    def test_keeps_compact_two_column_transcript_support(self):
        page = MagicMock()
        page.width, page.height = 600, 800
        page.extract_text.return_value = "年级：2024 平均学分绩点(GPA)：3.80"
        page.extract_tables.return_value = []
        left, right = MagicMock(), MagicMock()
        left.extract_text.return_value = "2024-2025学年秋季学期\n数据结构Ⅰ 4 90 4.0"
        right.extract_text.return_value = "2024-2025学年春季学期\n操作系统 3.0 88 3.7\n实践课程 2 P 1.0"
        page.within_bbox.side_effect = [left, right]
        with patch("academic_analysis.pdfplumber.open") as opened:
            opened.return_value.__enter__.return_value.pages = [page]
            courses, gpa = AcademicWarningEngine().parse_ruc_transcript("fixture.pdf")
        self.assertEqual(3, len(courses))
        self.assertIsNone(courses[2]["gpa"])
        self.assertEqual("P", courses[2]["score"])
        self.assertEqual(3.8, gpa)

    def test_rejects_unreadable_pdf_instead_of_reporting_all_courses_missing(self):
        page = MagicMock()
        page.width, page.height = 600, 800
        page.extract_text.return_value = ""
        page.extract_tables.return_value = []
        page.within_bbox.return_value.extract_text.return_value = ""
        with patch("academic_analysis.pdfplumber.open") as opened:
            opened.return_value.__enter__.return_value.pages = [page]
            with self.assertRaisesRegex(ValueError, "未识别到"):
                AcademicWarningEngine().parse_ruc_transcript("scanned.pdf")

    @staticmethod
    def course(name, score="90", credit=3, semester="2024-2025学年春季学期"):
        return {"name": name, "score": score, "credit": credit, "semester": semester}

    def test_normalizes_roman_numerals_but_keeps_distinct_courses(self):
        engine = AcademicWarningEngine({"core_course_details": [
            {"name": "数据结构与算法I", "offered_at": "1"},
            {"name": "数据结构与算法II", "offered_at": "2"},
        ]})
        report = engine.analyze([self.course("数据结构与算法Ⅰ")])
        self.assertEqual(["数据结构与算法I"], report["core_courses"])
        self.assertEqual(["数据结构与算法II"], report["missing_core_courses"])
        self.assertNotEqual(normalize_course_name("高等数学Ⅰ"), normalize_course_name("高等数学Ⅱ"))

    def test_future_and_unknown_schedule_are_not_remedial_courses(self):
        engine = AcademicWarningEngine({"core_course_details": [
            {"name": "操作系统", "offered_at": "4"},
            {"name": "计算机网络", "offered_at": ""},
        ]})
        report = engine.analyze([self.course("思想道德与法治")])
        self.assertEqual(["操作系统"], report["pending_core_courses"])
        self.assertEqual(["计算机网络"], report["unscheduled_core_courses"])
        self.assertEqual([], report["missing_core_courses"])
        self.assertEqual("正常", report["warning_level"])

    def test_retaken_course_counts_credits_once_and_clears_old_failure(self):
        engine = AcademicWarningEngine({"core_course_details": [{"name": "高等数学II", "offered_at": "2"}]})
        report = engine.analyze([
            self.course("高等数学Ⅱ", "50", 5, "2024-2025学年秋季学期"),
            self.course("高等数学II", "80", 5),
            self.course("高等数学Ⅱ", "90", 5),
        ])
        self.assertEqual(5, report["total_earned_credits"])
        self.assertEqual([], report["failed_courses"])
        self.assertEqual(0, report["issue_course_count"])

    def test_failed_core_course_is_not_counted_twice(self):
        engine = AcademicWarningEngine({"core_course_details": [{"name": "高等数学II", "offered_at": "2"}]})
        report = engine.analyze([self.course("高等数学Ⅱ", "50")])
        self.assertEqual(1, len(report["failed_courses"]))
        self.assertEqual([], report["missing_core_courses"])
        self.assertEqual(1, report["issue_course_count"])

    def test_unknown_scores_are_not_silently_passed(self):
        report = AcademicWarningEngine().analyze([self.course("操作系统", "缓考")])
        self.assertEqual(0, report["total_earned_credits"])
        self.assertEqual(["操作系统"], report["unknown_score_courses"])
        self.assertTrue(report["analysis_notes"])

    def test_cohort_mismatch_is_visible_without_overriding_transcript_progress(self):
        engine = AcademicWarningEngine({"grade": "2026", "core_course_details": []})
        engine.transcript_metadata = {"grade": "2024"}
        report = engine.analyze([self.course("操作系统", semester="2025-2026学年春季学期")])
        self.assertEqual(4, report["completed_semester"])
        self.assertTrue(any("2024" in note and "2026" in note for note in report["analysis_notes"]))

    def test_missing_official_gpa_is_distinguishable_from_a_real_zero(self):
        page = MagicMock()
        page.width, page.height = 600, 800
        page.extract_tables.return_value = []
        page.within_bbox.return_value.extract_text.return_value = "2024-2025学年秋季学期\n数据结构 4 50 0.0"
        for text, available in [("年级：2024", False), ("年级：2024 平均学分绩点(GPA)：0.00", True)]:
            with self.subTest(text=text), patch("academic_analysis.pdfplumber.open") as opened:
                page.extract_text.return_value = text
                opened.return_value.__enter__.return_value.pages = [page]
                engine = AcademicWarningEngine()
                _, gpa = engine.parse_ruc_transcript("fixture.pdf")
                self.assertEqual(0.0, gpa)
                self.assertEqual(available, engine.transcript_metadata["official_gpa_available"])


if __name__ == "__main__":
    unittest.main()
