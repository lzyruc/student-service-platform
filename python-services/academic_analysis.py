"""成绩单解析和培养方案比对；不调用模型，不访问业务数据库。"""

import re
import unicodedata

import pdfplumber


def normalize_course_name(value):
    # 保留 I/II、A/B 等课程区别，仅统一全半角、罗马数字和空白。
    return re.sub(r"\s+", "", unicodedata.normalize("NFKC", str(value or ""))).casefold()


def is_pass_only_grade(value):
    """P/通过 grants credit but does not supply a GPA; F may be a valid letter-grade zero."""
    return unicodedata.normalize("NFKC", str(value or "")).strip().upper() in {"P", "通过"}


class AcademicWarningEngine:
    SEMESTER_PATTERN = re.compile(r"(\d{4})\s*[-—－]\s*(\d{4})\s*学年\s*(秋季|春季|国际小学期|夏季|暑期)\s*(?:学期)?")
    NUMBER = r"\d+(?:\.\d+)?"

    def __init__(self, training_plan=None):
        self.training_plan = training_plan or {"required_credits": 150.0, "core_courses": []}
        self.transcript_metadata = {}

    @staticmethod
    def _cell(value):
        return re.sub(r"\s+", "", str(value or ""))

    def parse_ruc_transcript(self, pdf_path):
        courses, pending = [], []
        official_gpa = 0.0
        official_gpa_available = False
        columns = None
        texts = []
        with pdfplumber.open(pdf_path) as pdf:
            for page_index, page in enumerate(pdf.pages):
                text = page.extract_text() or ""
                texts.append(text)
                # 列名定位，而不是按页面中线裁剪；跨页沿用上一页列定义。
                for table in page.extract_tables():
                    for row in table:
                        cells = [self._cell(cell) for cell in row]
                        if "课程名称" in cells and "学分" in cells and "最终成绩" in cells:
                            columns = {name: cells.index(name) for name in cells if name}
                            continue
                        summary = " ".join(str(cell or "") for cell in row)
                        semester = self.SEMESTER_PATTERN.search(summary)
                        if semester:
                            # 此格式的学期汇总行在课程之后，待确认课程可跨越页面。
                            label = semester.group(0)
                            for course in pending:
                                course["semester"] = label
                            pending.clear()
                            continue
                        if not columns or len(cells) <= max(columns.values()):
                            continue
                        name = cells[columns["课程名称"]]
                        credit_text = cells[columns["学分"]]
                        score = cells[columns["最终成绩"]]
                        if not name or not re.fullmatch(self.NUMBER, credit_text):
                            continue
                        credit = float(credit_text)
                        point_text = cells[columns["学分绩点"]] if "学分绩点" in columns else ""
                        points = float(point_text) if re.fullmatch(self.NUMBER, point_text) else None
                        course = {
                            "semester": "未知学期", "name": name, "credit": credit,
                            "score": score, "gpa": round(points / credit, 4) if points is not None and credit and not is_pass_only_grade(score) else None,
                            "category": cells[columns["课程性质"]] if "课程性质" in columns else "",
                            "page": page_index + 1,
                        }
                        courses.append(course)
                        pending.append(course)
            full_text = "\n".join(texts)
            # 优先读取全学期总汇 GPA，不能取最后一个学期的 GPA。
            overall = re.search(r"各学期汇总[^\n]*平均学分绩点\s*[:：]\s*(" + self.NUMBER + r")", full_text)
            if overall:
                official_gpa = float(overall.group(1))
                official_gpa_available = True
            else:
                values = re.findall(r"平均学分绩点\s*\(GPA\)\s*[:：]\s*(" + self.NUMBER + r")", full_text)
                if values:
                    official_gpa = float(values[-1])
                    official_gpa_available = True
            grade = re.search(r"年级\s*[:：]\s*(\d{4})", full_text)
            self.transcript_metadata = {"grade": grade.group(1) if grade else None,
                                        "official_gpa_available": official_gpa_available}
            if not courses:
                courses, official_gpa = self._parse_compact_transcript(pdf, official_gpa)
        if not courses:
            raise ValueError("未识别到成绩单课程，请上传包含可提取文字的成绩单 PDF；当前不支持扫描版成绩单")
        return courses, official_gpa

    def _parse_compact_transcript(self, pdf, official_gpa):
        """保留旧双栏格式：课程名称、学分、最终成绩、单科绩点。"""
        courses = []
        pattern = re.compile(r"^(.*?)\s+(" + self.NUMBER + r")\s+(\S+)\s+(" + self.NUMBER + r")$")
        semester = "未知学期"
        for page_index, page in enumerate(pdf.pages):
            for bbox in ((0, 0, page.width / 2, page.height), (page.width / 2, 0, page.width, page.height)):
                for line in (page.within_bbox(bbox).extract_text() or "").splitlines():
                    heading = self.SEMESTER_PATTERN.search(line)
                    if heading:
                        semester = heading.group(0)
                        continue
                    match = pattern.fullmatch(line.strip())
                    if match and "课程名称" not in match.group(1):
                        courses.append({"name": self._cell(match.group(1)), "credit": float(match.group(2)),
                                        "score": match.group(3), "gpa": None if is_pass_only_grade(match.group(3)) else float(match.group(4)),
                                        "semester": semester, "page": page_index + 1})
        return courses, official_gpa

    @staticmethod
    def is_passed(score_str):
        score = str(score_str or "").strip().upper()
        if score in {"A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D+", "D", "P", "及格", "通过", "优秀", "良好", "中等"}:
            return True
        if score in {"F", "不及格", "未通过", "不通过", "缺考", "旷考"}:
            return False
        try:
            return float(score) >= 60
        except ValueError:
            return None

    def _completed_semester(self, student_courses):
        semesters = [self.SEMESTER_PATTERN.search(course.get("semester", "")) for course in student_courses]
        semesters = [semester for semester in semesters if semester]
        regular = [semester for semester in semesters if semester.group(3) in {"秋季", "春季"}]
        if not regular:
            return None
        # 按成绩单的实际入学年级计算进度；测试账号年级不能改写 PDF 中的学期。
        start_year = int(self.transcript_metadata.get("grade") or min(int(s.group(1)) for s in semesters))
        return max((int(s.group(1)) - start_year) * 2 + (1 if s.group(3) == "秋季" else 2) for s in regular)

    @staticmethod
    def _offered_semester(value):
        value = unicodedata.normalize("NFKC", str(value or "")).strip()
        match = re.fullmatch(r"(?:第)?([1-8])(?:学期)?", value)
        return int(match.group(1)) if match else None

    def analyze(self, student_courses, official_gpa=0.0):
        if not student_courses:
            raise ValueError("没有有效课程数据，不能生成学业预警报告")
        completed_semester = self._completed_semester(student_courses)
        report = {
            "total_earned_credits": 0.0, "official_gpa": official_gpa,
            "failed_courses": [], "core_courses": [], "missing_core_courses": [],
            "pending_core_courses": [], "unscheduled_core_courses": [], "unknown_score_courses": [],
            "warning_level": "正常", "course_suggestions": [], "analysis_notes": [],
            "completed_semester": completed_semester,
        }
        attempts = {}
        for course in student_courses:
            attempts.setdefault(normalize_course_name(course["name"]), []).append(course)
        passed_names = set()
        for name, records in attempts.items():
            passed = [course for course in records if self.is_passed(course["score"]) is True]
            if passed:
                passed_names.add(name)
                # 重修后通过只计一次学分，历史不及格不再作为待重修课程。
                report["total_earned_credits"] += max(float(course["credit"]) for course in passed)
            elif self.is_passed(records[-1]["score"]) is False:
                report["failed_courses"].append({"name": records[-1]["name"], "score": records[-1]["score"]})
            else:
                report["unknown_score_courses"].append(records[-1]["name"])
        report["total_earned_credits"] = round(report["total_earned_credits"], 2)
        failed_names = {normalize_course_name(course["name"]) for course in report["failed_courses"]}
        plan_courses = self.training_plan.get("core_course_details")
        if not isinstance(plan_courses, list):
            plan_courses = [{"name": name} for name in self.training_plan.get("core_courses", [])]
        seen = set()
        for course in plan_courses:
            display_name = course["name"]
            name = normalize_course_name(display_name)
            if name in seen:
                continue
            seen.add(name)
            if name in passed_names:
                report["core_courses"].append(display_name)
                continue
            # 已确认不及格由 failed_courses 单独列出，避免重复计数。
            if name in failed_names:
                continue
            offered = self._offered_semester(course.get("offered_at"))
            if offered is None or completed_semester is None:
                report["unscheduled_core_courses"].append(display_name)
            elif offered > completed_semester:
                report["pending_core_courses"].append(display_name)
            else:
                report["missing_core_courses"].append(display_name)
                report["course_suggestions"].append(f"已到计划开课学期，但成绩单未见通过记录：{display_name}；请核实是否未修、尚未出分或课程名称不同")
        for course in report["failed_courses"]:
            report["course_suggestions"].append(f"建议重修未通过课程：{course['name']}（最终成绩：{course['score']}）")
        if report["pending_core_courses"]:
            report["analysis_notes"].append(f"{len(report['pending_core_courses'])} 门核心课程尚未到计划开课学期，不计入缺口")
        if report["unscheduled_core_courses"]:
            report["analysis_notes"].append(f"{len(report['unscheduled_core_courses'])} 门未见通过记录的核心课程缺少可用开课学期或成绩单进度，需核实，不自动判定补修")
        if report["unknown_score_courses"]:
            report["analysis_notes"].append("部分课程最终成绩为空或无法识别，未计为已通过，请核实：" + "、".join(report["unknown_score_courses"]))
        plan_grade = str(self.training_plan.get("grade", "")).removesuffix("级")
        transcript_grade = self.transcript_metadata.get("grade")
        if transcript_grade and plan_grade and transcript_grade != plan_grade:
            report["analysis_notes"].append(f"成绩单年级为 {transcript_grade}，当前培养方案年级为 {plan_grade}；本次按所选培养方案比对，课程差异需人工确认")
        if len(report["failed_courses"]) >= 3 or len(report["missing_core_courses"]) >= 2:
            report["warning_level"] = "严重预警"
        elif report["failed_courses"] or report["missing_core_courses"]:
            report["warning_level"] = "一般预警"
        report["issue_course_count"] = len(report["failed_courses"]) + len(report["missing_core_courses"])
        report["core_course_count"] = len(report["core_courses"])
        return report
