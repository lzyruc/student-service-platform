"""Synthetic-only PDF/plan fixture. No real student files are read."""
import json, hashlib
from pathlib import Path
VERSION = "academic-eval-fixture-v1"
COURSES = [("2024-2025学年秋季学期", "数学分析",3,90,4.0),
 ("2024-2025学年秋季学期","程序设计",3,88,3.7),
 ("2024-2025学年春季学期","数据结构",3,75,3.0),
 ("2024-2025学年春季学期","计算机组成",3,80,3.3),
 ("2025-2026学年秋季学期","操作系统",3,58,0.0),
 ("2025-2026学年秋季学期","计算机网络",3,65,1.8)]
def generate(directory):
 import pymupdf
 directory=Path(directory);directory.mkdir(parents=True,exist_ok=True)
 for version,official in (("base",2.63),("updated",3.3)):
  doc=pymupdf.open();page=doc.new_page(width=900,height=700)
  page.insert_text((24,30),"EVALUATION SYNTHETIC TRANSCRIPT",fontsize=12)
  page.insert_text((24,60),f"年级：2024 平均学分绩点(GPA)：{official:.2f}",fontname="china-s",fontsize=10)
  for side in range(2):
   selected=COURSES[:4] if side==0 else COURSES[4:]
   y=105;previous=None
   for semester,name,credit,score,gpa in selected:
    if version=="updated" and name=="操作系统":score,gpa=93,4.0
    if semester!=previous:
     page.insert_text((24+450*side,y),semester,fontname="china-s",fontsize=10);y+=25;previous=semester
    page.insert_text((24+450*side,y),f"{name} {credit} {score} {gpa}",fontname="china-s",fontsize=10);y+=25
  doc.save(directory/f"alpha-{version}.pdf");doc.close()
 doc=pymupdf.open();page=doc.new_page(width=900,height=700)
 for y,text in ((60,"年级：2024 平均学分绩点(GPA)：3.90"),(105,"2025-2026学年秋季学期"),(135,"乙方专属课程 3 97 3.9")):
  page.insert_text((24,y),text,fontname="china-s",fontsize=10)
 doc.save(directory/"beta.pdf");doc.close()
 names=[("数学分析",1),("程序设计",1),("数据结构",2),("计算机组成",2),("操作系统",3),("计算机网络",3),("软件工程",2),("数据库系统",4)]
 plan={"courses":[{"category":"部类基础课","courseName":name,"credits":3,"offeredAt":str(semester)} for name,semester in names]}
 (directory/"plan.json").write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding="utf-8")
 return {"version":VERSION,"base":{"official_gpa":2.63,"earned_credits":15,"failed":["操作系统"],"low":["计算机网络"],"semester_gpas":[3.85,3.15,0.9],"missing":["软件工程"],"pending":["数据库系统"]},"updated":{"official_gpa":3.3,"earned_credits":18,"failed":[],"semester_gpas":[3.85,3.15,2.9]},"files":{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in directory.iterdir() if p.suffix in (".pdf",".json")}}

def verify(directory):
 """Validate synthetic documents before any paid call; expected values above are independent constants."""
 import sys
 service=Path(__file__).resolve().parents[2]
 if str(service) not in sys.path:sys.path.insert(0,str(service))
 from academic_analysis import AcademicWarningEngine
 from academic_statistics import build_academic_statistics
 directory=Path(directory)
 plan=json.loads((directory/"plan.json").read_text(encoding="utf-8"))
 normalized={"core_courses":[x["courseName"] for x in plan["courses"]],"core_course_details":[{"name":x["courseName"],"offered_at":x["offeredAt"]} for x in plan["courses"]]}
 expected={"base":(2.63,15,[3.85,3.15,.9]),"updated":(3.3,18,[3.85,3.15,2.9])}
 evidence={}
 for version,(official,credits,gpas) in expected.items():
  engine=AcademicWarningEngine(normalized);courses,gpa=engine.parse_ruc_transcript(str(directory/f"alpha-{version}.pdf"))
  report=engine.analyze(courses,gpa);stats=build_academic_statistics(courses,report)
  actual=[x["weighted_gpa"] for x in stats["semesters"]]
  if len(courses)!=6 or gpa!=official or report["total_earned_credits"]!=credits or actual!=gpas:raise ValueError("Synthetic fixture/parser contract mismatch: "+version)
  evidence[version]={"course_count":len(courses),"official_gpa":gpa,"earned_credits":credits,"semester_gpas":actual}
 return {"fixture_version":VERSION,"local_parse_calls":2,"provider_calls":0,"facts":evidence}
