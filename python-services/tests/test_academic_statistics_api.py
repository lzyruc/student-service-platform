import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from fastapi import UploadFile
from academic_analysis import AcademicWarningEngine


class AcademicStatisticsApiTests(unittest.IsolatedAsyncioTestCase):
    @classmethod
    def setUpClass(cls):
        # Import the existing service in a temporary cwd so its runtime directory stays out of the project.
        path = Path(__file__).resolve().parents[1] / "5_pdf_compare.py"
        spec = importlib.util.spec_from_file_location("academic_statistics_api_test", path)
        cls.api = importlib.util.module_from_spec(spec)
        with tempfile.TemporaryDirectory() as work:
            previous = os.getcwd()
            try:
                os.chdir(work)
                spec.loader.exec_module(cls.api)
            finally:
                os.chdir(previous)

    async def test_existing_analysis_returns_statistics_from_same_single_parse_and_cleans_temp_pdf(self):
        plan = {"grade": "2026", "core_courses": []}
        engine = AcademicWarningEngine(plan)
        engine.transcript_metadata = {"grade": "2024", "official_gpa_available": True}
        courses = [{"name": "程序设计", "score": "65", "credit": 4, "gpa": 2,
                    "semester": "2025-2026学年春季学期"}]
        expected_report = engine.analyze(courses, 2)
        with tempfile.TemporaryDirectory() as work, patch.object(self.api, "UPLOAD_DIR", work), \
                patch.object(self.api, "AcademicWarningEngine", return_value=engine), \
                patch.object(engine, "parse_ruc_transcript", return_value=(courses, 2)) as parse:
            upload = UploadFile(file=io.BytesIO(b"%PDF-1.7 test"), filename="grades.pdf")
            result = await self.api.analyze_warning(upload, json.dumps(plan))
            parse.assert_called_once()
            self.assertEqual([], list(Path(work).iterdir()))
            self.assertTrue(upload.file.closed)
        data = result["data"]
        self.assertEqual(courses, data["courses"])
        self.assertEqual(expected_report, {key: value for key, value in data["report"].items() if key != "official_gpa_available"})
        self.assertTrue(data["report"]["official_gpa_available"])
        self.assertEqual("2025-2026学年春季学期", data["statistics"]["latest_semester"])
        self.assertEqual("LOW_NUMERIC_SCORE", data["statistics"]["semesters"][0]["focus_courses"][0]["reason"])
        self.assertTrue(any("2024" in note for note in data["statistics"]["analysis_notes"]))
        # Decimal/NaN cannot leak through the JSON boundary.
        json.dumps(result, ensure_ascii=False, allow_nan=False)

    async def test_missing_official_gpa_has_an_availability_flag_and_no_fake_sample_gpa(self):
        plan = {"core_courses": []}
        engine = AcademicWarningEngine(plan)
        courses = [{"name": "课程", "score": "80", "credit": 3, "gpa": None,
                    "semester": "2024-2025学年秋季学期"}]
        with tempfile.TemporaryDirectory() as work, patch.object(self.api, "UPLOAD_DIR", work), \
                patch.object(self.api, "AcademicWarningEngine", return_value=engine), \
                patch.object(engine, "parse_ruc_transcript", return_value=(courses, 0)):
            result = await self.api.analyze_warning(
                UploadFile(file=io.BytesIO(b"%PDF-1.7 test"), filename="grades.pdf"), json.dumps(plan))
        self.assertFalse(result["data"]["report"]["official_gpa_available"])
        self.assertIsNone(result["data"]["statistics"]["semesters"][0]["weighted_gpa"])


if __name__ == "__main__":
    unittest.main()
