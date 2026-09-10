import os
import glob
import re
import shutil
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, BackgroundTasks

# 自动加载当前目录下的 .env 文件，保护 API Key 不被直接暴露在代码中
load_dotenv()
load_dotenv("key.env", override=False)
from typing import Optional
from pydantic import BaseModel
from langchain_community.document_loaders import PyPDFLoader, Docx2txtLoader, TextLoader
from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_openai import OpenAIEmbeddings, ChatOpenAI
from langchain_community.vectorstores import Chroma
from langchain_core.prompts import ChatPromptTemplate
from rapidocr_onnxruntime import RapidOCR
import uvicorn

class RAGEngine:
    def __init__(self, persist_directory="./chroma_db"):
        # 智谱 API 配置
        zhipu_api_key = os.getenv("ZHIPU_API_KEY")
        if not zhipu_api_key:
            print("警告: 未在环境变量中找到 ZHIPU_API_KEY，请检查 .env 文件")
        
        # 智谱开放平台的 OpenAI 兼容接口地址
        zhipu_api_base = "https://open.bigmodel.cn/api/paas/v4/"
        
        self.llm = ChatOpenAI(
            temperature=0,
            model="glm-4-flash", # 智谱的高性价比/免费推理模型
            openai_api_key=zhipu_api_key,
            openai_api_base=zhipu_api_base
        )
        self.embeddings = OpenAIEmbeddings(
            model="embedding-3", # 智谱的文本向量模型
            openai_api_key=zhipu_api_key,
            openai_api_base=zhipu_api_base
        )
        self.persist_directory = persist_directory
        self.vectorstore = self._init_vectorstore()
        self.ocr = RapidOCR() # 初始化 OCR 引擎用于处理图片

    def _init_vectorstore(self):
        """初始化 Chroma 本地向量数据库"""
        if not os.path.exists(self.persist_directory):
            os.makedirs(self.persist_directory)
        return Chroma(persist_directory=self.persist_directory, embedding_function=self.embeddings)

    def _load_image_with_ocr(self, file_path):
        """使用 OCR 读取图片（如校历）并转为 Document 对象"""
        result, _ = self.ocr(file_path)
        if result:
            text = "\n".join([line[1] for line in result])
            content = f"[图片文件 OCR 解析内容，可能为校历或通知]\n{text}"
            return [Document(page_content=content, metadata={"source": file_path, "doc_type": self._infer_doc_type(file_path), "page_range": "image"})]
        return []

    def _infer_doc_type(self, file_path):
        name = os.path.basename(file_path)
        if "本科" in name:
            return "本科"
        if "研究生" in name:
            return "研究生"
        if "校历" in name:
            return "校历"
        if "违纪" in name or "处分" in name:
            return "违纪处分"
        return "通用"

    def _build_pdf_docs(self, file_path):
        """将PDF按相邻两页合并，减少条款跨页被切断的问题"""
        loader = PyPDFLoader(file_path)
        page_docs = loader.load()
        if not page_docs:
            return []

        merged_docs = []
        if len(page_docs) == 1:
            page_no = page_docs[0].metadata.get("page", 0) + 1
            merged_docs.append(Document(
                page_content=page_docs[0].page_content,
                metadata={"source": file_path, "doc_type": self._infer_doc_type(file_path), "page_range": f"{page_no}"}
            ))
            return merged_docs

        for i in range(len(page_docs) - 1):
            start_page = page_docs[i].metadata.get("page", i) + 1
            end_page = page_docs[i + 1].metadata.get("page", i + 1) + 1
            merged_docs.append(Document(
                page_content=f"[第{start_page}-{end_page}页]\n" + page_docs[i].page_content + "\n" + page_docs[i + 1].page_content,
                metadata={"source": file_path, "doc_type": self._infer_doc_type(file_path), "page_range": f"{start_page}-{end_page}"}
            ))
        return merged_docs

    def _infer_question_doc_type(self, question):
        if "本科" in question:
            return "本科"
        if "研究生" in question or "硕士" in question or "博士" in question:
            return "研究生"
        if "校历" in question or "放假" in question or "开学" in question or "考试周" in question:
            return "校历"
        if "违纪" in question or "处分" in question or "作弊" in question:
            return "违纪处分"
        return "通用"

    def _rerank_docs(self, question, docs):
        expected_type = self._infer_question_doc_type(question)
        keywords = [kw for kw in ["休学", "复学", "退学", "处分", "违纪", "校历", "开学", "放假", "毕业", "学籍"] if kw in question]

        def score(doc):
            text = doc.page_content
            meta = doc.metadata
            s = 0
            if expected_type != "通用":
                if meta.get("doc_type") == expected_type:
                    s += 100
                else:
                    s -= 50
            for kw in keywords:
                if kw in text:
                    s += 10
            if "第二十一条" in text and "休学" in question:
                s += 20
            return s

        return sorted(docs, key=score, reverse=True)[:4]

    def rebuild_from_folder(self, folder_path):
        """重建知识库，避免重复向量污染结果"""
        if os.path.exists(self.persist_directory):
            shutil.rmtree(self.persist_directory, ignore_errors=True)
        self.vectorstore = self._init_vectorstore()
        return self.ingest_folder(folder_path)

    def ingest_document(self, file_path):
        """将政策文件录入知识库"""
        ext = os.path.splitext(file_path)[-1].lower()
        if ext == '.pdf':
            docs = self._build_pdf_docs(file_path)
        elif ext in ['.doc', '.docx']:
            loader = Docx2txtLoader(file_path)
            docs = loader.load()
            for doc in docs:
                doc.metadata["source"] = file_path
                doc.metadata["doc_type"] = self._infer_doc_type(file_path)
                doc.metadata["page_range"] = "doc"
        elif ext in ['.png', '.jpg', '.jpeg']:
            docs = self._load_image_with_ocr(file_path)
        else:
            loader = TextLoader(file_path, encoding='utf-8')
            docs = loader.load()
            for doc in docs:
                doc.metadata["source"] = file_path
                doc.metadata["doc_type"] = self._infer_doc_type(file_path)
                doc.metadata["page_range"] = "text"

        if not docs:
            return "文件为空或无法解析"

        text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=1200,
            chunk_overlap=250,
            separators=["\n\n", "\n", "。", "；", "，", " "]
        )
        splits = text_splitter.split_documents(docs)
        self.vectorstore.add_documents(splits)
        return f"成功录入 {len(splits)} 个文本块 (来源: {os.path.basename(file_path)})"

    def ingest_folder(self, folder_path):
        """批量录入整个文件夹的文件"""
        if not os.path.exists(folder_path):
            return "文件夹不存在"
        
        results = []
        for root, dirs, files in os.walk(folder_path):
            for file in files:
                file_path = os.path.join(root, file)
                try:
                    res = self.ingest_document(file_path)
                    results.append(res)
                except Exception as e:
                    results.append(f"解析失败 {file}: {str(e)}")
        return results

    def ask(self, question):
        """基于知识库回答学生问题"""
        candidates = self.vectorstore.similarity_search(question, k=8)
        docs = self._rerank_docs(question, candidates)
        context = "\n\n".join([
            f"[来源: {os.path.basename(doc.metadata.get('source', '未知'))} | 页码: {doc.metadata.get('page_range', '未知')}]\n{doc.page_content}"
            for doc in docs
        ])

        system_prompt = (
            "你是一个中国人民大学信息学院的官方智能问答助手。\n"
            "【规则】\n"
            "1. 请严格、仅基于以下提供的官方文件和校历内容来回答学生的问题。\n"
            "2. 如果你的回答需要推导，请确保推导逻辑符合给定文件。\n"
            "3. 如果提供的文件中完全没有相关信息，或者你不知道答案，请直接回答：根据现有政策文件暂无相关信息，请联系辅导员或教务处咨询。\n"
            "4. 绝对不要编造、猜测或使用你自带的常识库来回答有关学校政策、时间、规定的问题。\n"
            "5. 不要混用本科生与研究生规定；如果问题问的是本科生，优先且仅依据本科生文件回答。\n"
            "6. 若条款列出了多个分项，必须完整列出上下文中已经给出的全部分项，不要漏项。\n"
            "7. 只有当上下文中明确出现某条条文编号时，才可以引用该条编号；否则不要擅自写条号。\n"
            "8. 回答要礼貌、清晰，优先给出结论，再简要说明依据。\n\n"
            "【已知文件内容】\n{context}"
        )

        prompt = ChatPromptTemplate.from_messages([
            ("system", system_prompt),
            ("human", "问题：{input}")
        ])
        messages = prompt.format_messages(context=context, input=question)
        response = self.llm.invoke(messages)
        sources = list(dict.fromkeys([
            f"{os.path.basename(doc.metadata.get('source', '未知'))}（页码范围：{doc.metadata.get('page_range', '未知')}）"
            for doc in docs
        ]))

        return {
            "answer": response.content,
            "sources": sources
        }

# ==========================================
# 考虑到双端实现（Web 管理端 + 小程序端），提供 RESTful API
# ==========================================
app = FastAPI(title="学院智能问答 RAG API")
rag_engine = RAGEngine()

class QuestionRequest(BaseModel):
    question: str
    studentNo: Optional[str] = None

@app.post("/api/admin/ingest_all")
def api_ingest_all(background_tasks: BackgroundTasks):
    """
    [Web管理端 API] 批量扫描并录入 '政策文件库' 文件夹中的所有文件。
    使用后台任务避免请求超时。
    """
    folder = "./政策文件库"
    background_tasks.add_task(rag_engine.rebuild_from_folder, folder)
    return {"status": "success", "message": f"已启动后台任务，正在重建知识库并解析 {folder} 中的文件及校历图片。"}

@app.post("/api/admin/ai/ingest-all")
def api_ingest_all_alias(background_tasks: BackgroundTasks):
    return api_ingest_all(background_tasks)

@app.post("/api/student/ask")
def api_student_ask(req: QuestionRequest):
    """
    [小程序端 API] 学生提问接口
    """
    if not req.question:
        raise HTTPException(status_code=400, detail="问题不能为空")
    
    try:
        result = rag_engine.ask(req.question)
        return {
            "status": "success",
            "data": {
                "question": req.question,
                "answer": result["answer"],
                "sources": [os.path.basename(s) for s in result["sources"]]
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/student/ai/ask")
def api_student_ask_alias(req: QuestionRequest):
    return api_student_ask(req)

if __name__ == "__main__":
    print("="*50)
    print("正在启动 学院智能问答 RAG API 服务...")
    print("="*50)
    
    # 尝试加载环境变量
    if not os.getenv("ZHIPU_API_KEY"):
        print("【注意】未检测到 ZHIPU_API_KEY，请确保 .env 文件存在且配置正确！")
        
    # 启动 Uvicorn 服务器
    # 注意：在 Windows 下如果直接运行可能需要指定 reload=False
    uvicorn.run("AIAnalysis:app", host="0.0.0.0", port=8000, reload=False)
