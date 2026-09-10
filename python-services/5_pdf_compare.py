import os
import re
import shutil
import json
import pdfplumber
import uvicorn
from fastapi import FastAPI, HTTPException, UploadFile, File, Form
from pydantic import BaseModel

class AcademicWarningEngine:
    def __init__(self, training_plan=None):
        """
        初始化培养方案
        :param training_plan: 字典格式，如 {"required_credits": 40, "core_courses": ["高等数学 I", "程序设计"]}
        """
        self.training_plan = training_plan or {
            "required_credits": 150.0,
            "core_courses": []
        }

    def parse_ruc_transcript(self, pdf_path):
        """
        专门解析双栏排版的成绩单 PDF（如中国人民大学成绩单）
        """
        courses = []
        official_gpa = 0.0
        
        # 正则表达式：匹配 "课程名称 学分 成绩 绩点" 格式的行
        course_pattern = re.compile(r"^(.*?)\s+(\d+\.\d+)\s+(\S+)\s+(\d+\.\d+)$")
        # 正则表达式：匹配成绩单底部的平均学分绩点
        gpa_pattern = re.compile(r"平均学分绩点\s*\(GPA\)[:：]\s*(\d+\.\d+)")

        with pdfplumber.open(pdf_path) as pdf:
            for page in pdf.pages:
                width = page.width
                height = page.height
                
                # 1. 裁剪左半边和右半边
                left_bbox = (0, 0, width / 2, height)
                right_bbox = (width / 2, 0, width, height)
                
                left_text = page.within_bbox(left_bbox).extract_text()
                right_text = page.within_bbox(right_bbox).extract_text()
                
                # 2. 合并左右两边的文本行，按顺序解析
                all_lines = []
                if left_text:
                    all_lines.extend(left_text.split('\n'))
                if right_text:
                    all_lines.extend(right_text.split('\n'))
                    
                current_semester = "未知学期"
                
                # 3. 逐行匹配
                for line in all_lines:
                    line = line.strip()
                    if not line:
                        continue
                        
                    # 尝试匹配官方 GPA
                    gpa_match = gpa_pattern.search(line)
                    if gpa_match:
                        official_gpa = float(gpa_match.group(1))
                        
                    # 识别学期标题（例如："2024-2025学年 秋季学期"）
                    if "学年" in line and "学期" in line:
                        current_semester = line
                        continue
                        
                    # 使用正则匹配课程成绩行
                    match = course_pattern.match(line)
                    if match:
                        course_name = match.group(1).strip()
                        # 过滤掉偶然匹配到的表头
                        if "课程名称" in course_name:
                            continue
                            
                        credit = float(match.group(2))
                        score_str = match.group(3)
                        gpa = float(match.group(4))
                        
                        courses.append({
                            "semester": current_semester,
                            "name": course_name,
                            "credit": credit,
                            "score": score_str,
                            "gpa": gpa
                        })

        return courses, official_gpa

    def is_passed(self, score_str):
        """判断成绩是否及格"""
        if score_str in ["A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D+", "D", "P", "及格", "通过", "优秀", "良好", "中等"]:
            return True
        if score_str in ["F", "不及格", "未通过"]:
            return False
        # 如果是纯数字成绩
        try:
            return float(score_str) >= 60.0
        except ValueError:
            # 无法解析的成绩默认算及格(或者根据需要调整)
            return True

    def analyze(self, student_courses, official_gpa=0.0):
        """
        比调成绩与培养方案，生成预警报告和选课建议
        """
        report = {
            "total_earned_credits": 0.0,
            "official_gpa": official_gpa,
            "failed_courses": [],
            "missing_core_courses": [],
            "warning_level": "正常", # 正常 / 一般预警 / 严重预警
            "course_suggestions": []
        }

        completed_courses = []

        for course in student_courses:
            passed = self.is_passed(course["score"])
            if passed:
                completed_courses.append(course["name"])
                report["total_earned_credits"] += course["credit"]
            else:
                report["failed_courses"].append({
                    "name": course["name"],
                    "score": course["score"]
                })

        # 检查是否缺失核心必修课
        for core_course in self.training_plan.get("core_courses", []):
            if core_course not in completed_courses:
                report["missing_core_courses"].append(core_course)
                # 将缺失的核心课加入选课建议
                report["course_suggestions"].append(f"建议重修或补修核心课程: {core_course}")

        # 挂科课程加入选课建议
        for failed in report["failed_courses"]:
            if f"建议重修或补修核心课程: {failed['name']}" not in report["course_suggestions"]:
                report["course_suggestions"].append(f"建议重修挂科课程: {failed['name']}")

        # 预警逻辑判断
        if len(report["failed_courses"]) >= 3 or len(report["missing_core_courses"]) >= 2:
            report["warning_level"] = "严重预警"
        elif len(report["failed_courses"]) > 0:
            report["warning_level"] = "一般预警"

        return report

app = FastAPI(title="学业预警分析 API")

DEFAULT_TRAINING_PLAN = {
    "required_credits": 150.0,
    "core_courses": [
        "高等数学Ⅰ", "高等数学Ⅱ", "高等代数Ⅰ", "高等代数Ⅱ",
        "程序设计", "数据结构与算法Ⅰ", "计算机系统基础Ⅰ", "离散数学A", "操作系统"
    ]
}
UPLOAD_DIR = "./uploaded_transcripts"
os.makedirs(UPLOAD_DIR, exist_ok=True)

class TrainingPlanRequest(BaseModel):
    required_credits: float
    core_courses: list[str]

@app.get("/api/admin/warning/training_plan")
def get_training_plan():
    return {
        "status": "success",
        "data": DEFAULT_TRAINING_PLAN
    }

@app.post("/api/admin/warning/training_plan")
def update_training_plan(req: TrainingPlanRequest):
    DEFAULT_TRAINING_PLAN["required_credits"] = req.required_credits
    DEFAULT_TRAINING_PLAN["core_courses"] = req.core_courses
    return {
        "status": "success",
        "message": "培养方案已更新",
        "data": DEFAULT_TRAINING_PLAN
    }

@app.post("/api/student/warning/analyze")
async def analyze_warning(
    file: UploadFile = File(...),
    training_plan: str = Form(None)
):
    if not file.filename:
        raise HTTPException(status_code=400, detail="未上传文件")
    if not file.filename.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="仅支持上传 PDF 成绩单")

    safe_filename = os.path.basename(file.filename)
    temp_path = os.path.join(UPLOAD_DIR, safe_filename)

    try:
        with open(temp_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)

        plan = DEFAULT_TRAINING_PLAN
        if training_plan:
            try:
                plan = json.loads(training_plan)
            except Exception as e:
                print("解析传入的培养方案失败，降级使用默认方案:", e)

        engine = AcademicWarningEngine(training_plan=plan)
        courses, official_gpa = engine.parse_ruc_transcript(temp_path)
        report = engine.analyze(courses, official_gpa)
        return {
            "status": "success",
            "data": {
                "file_name": safe_filename,
                "course_count": len(courses),
                "courses": courses,
                "report": report
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        file.file.close()
        if os.path.exists(temp_path):
            os.remove(temp_path)

if __name__ == "__main__":
    print("正在启动学业预警分析服务：http://127.0.0.1:8002/docs")
    uvicorn.run(app, host="0.0.0.0", port=8002)