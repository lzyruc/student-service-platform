import os
import shutil
import json
import uuid
import uvicorn
from fastapi import FastAPI, HTTPException, UploadFile, File, Form

from academic_analysis import AcademicWarningEngine
from academic_statistics import build_academic_statistics

app = FastAPI(title="学业预警分析 API")

UPLOAD_DIR = "./uploaded_transcripts"
os.makedirs(UPLOAD_DIR, exist_ok=True)

@app.post("/api/student/warning/analyze")
async def analyze_warning(
    file: UploadFile = File(...),
    training_plan: str = Form(...)
):
    if not file.filename:
        raise HTTPException(status_code=400, detail="未上传文件")
    if not file.filename.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="仅支持上传 PDF 成绩单")

    safe_filename = os.path.basename(file.filename)
    temp_path = os.path.join(UPLOAD_DIR, f"{uuid.uuid4().hex}_{safe_filename}")

    try:
        plan = json.loads(training_plan)
        if not isinstance(plan, dict):
            raise ValueError("培养方案必须是 JSON 对象")
        if not isinstance(plan.get("core_courses"), list):
            raise ValueError("培养方案缺少 core_courses 数组")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"培养方案格式错误: {e}")

    try:
        with open(temp_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)

        engine = AcademicWarningEngine(training_plan=plan)
        courses, official_gpa = engine.parse_ruc_transcript(temp_path)
        report = engine.analyze(courses, official_gpa)
        report["official_gpa_available"] = engine.transcript_metadata.get("official_gpa_available", False)
        statistics = build_academic_statistics(courses, report)
        return {
            "status": "success",
            "data": {
                "file_name": safe_filename,
                "course_count": len(courses),
                "courses": courses,
                "report": report,
                "statistics": statistics
            }
        }
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        file.file.close()
        if os.path.exists(temp_path):
            os.remove(temp_path)

if __name__ == "__main__":
    print("正在启动学业预警分析服务：http://127.0.0.1:8002/docs")
    uvicorn.run(app, host="0.0.0.0", port=8002)
