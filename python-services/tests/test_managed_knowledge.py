"""验证统一知识库边界；使用测试向量，不加载模型或调用 LLM。"""
import io
import os
import sys
import threading
import unittest
import uuid
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
with (
    patch.dict(os.environ, {"DEEPSEEK_API_KEY": "test-only"}),
    patch("langchain_huggingface.HuggingFaceEmbeddings"),
    patch("langchain_openai.ChatOpenAI"),
    patch("langchain_community.vectorstores.Chroma"),
    patch("rapidocr_onnxruntime.RapidOCR"),
):
    import AIAnalysis as api

import chromadb
from chromadb.config import Settings
from fastapi import UploadFile
from langchain_community.vectorstores import Chroma
from langchain_core.documents import Document
from langchain_core.embeddings import Embeddings


class TestEmbeddings(Embeddings):
    def embed_documents(self, texts):
        return [[1.0, 0.0] for _ in texts]

    def embed_query(self, text):
        return [1.0, 0.0]


class ManagedKnowledgeTest(unittest.TestCase):
    def setUp(self):
        self.client = chromadb.Client(Settings(anonymized_telemetry=False))
        self.collection_name = "test-" + uuid.uuid4().hex
        self.engine = api.RAGEngine.__new__(api.RAGEngine)
        self.engine.vector_lock = threading.RLock()
        self.engine.vectorstore = Chroma(client=self.client, collection_name=self.collection_name,
                                        embedding_function=TestEmbeddings())
        self.engine.llm = Mock()

    def tearDown(self):
        self.client.delete_collection(self.collection_name)

    def add_policy(self, vector_id, policy_id, source="same.pdf"):
        self.engine.vectorstore.add_documents([
            Document(page_content="测试政策条款", metadata={
                "managed": "true", "policy_id": policy_id, "source": source, "doc_type": "通用"
            })
        ], ids=[vector_id])

    def test_retrieval_excludes_legacy_vectors_and_policies_outside_database_scope(self):
        self.add_policy("ready", "100")
        self.add_policy("draft", "200")
        self.engine.vectorstore.add_documents([
            Document(page_content="旧文件夹政策", metadata={"source": "legacy.pdf"})
        ], ids=["legacy"])
        docs = self.engine.retrieve("政策", ["100"])
        self.assertEqual(["100"], [doc.metadata["policy_id"] for doc in docs])

    def test_empty_database_scope_never_calls_llm(self):
        self.add_policy("draft", "200")
        self.assertEqual([], self.engine.ask("政策", [])["sources"])
        self.engine.llm.invoke.assert_not_called()

    def test_startup_cleanup_preserves_managed_vectors(self):
        self.add_policy("managed", "100")
        self.engine.vectorstore.add_documents([
            Document(page_content="旧政策", metadata={"source": "legacy.pdf"})
        ], ids=["legacy"])
        self.engine._remove_unmanaged_vectors()
        self.assertEqual(["managed"], self.engine.vectorstore.get()["ids"])

    def test_delete_does_not_remove_another_policy_with_identical_filename(self):
        self.add_policy("first", "100")
        self.add_policy("second", "200")
        self.assertEqual(1, self.engine.delete_managed_document(100, "same.pdf"))
        self.assertEqual(["second"], self.engine.vectorstore.get()["ids"])

    def test_calendar_image_upload_uses_ocr_and_managed_metadata(self):
        self.engine.ocr = Mock(return_value=([([], "校历：9月1日开学", 0.99)], None))
        upload = UploadFile(file=io.BytesIO(b"test image bytes"), filename="calendar.png")
        with patch.object(api, "rag_engine", self.engine):
            result = api.api_ingest_policy_document(100, upload, "校历", "校历", "ALL", "v1.0", "calendar.png")
        self.assertEqual("success", result["status"])
        self.assertGreater(result["data"]["chunkCount"], 0)
        docs = self.engine.retrieve("开学", ["100"])
        self.assertEqual("calendar.png", docs[0].metadata["source"])
        self.assertEqual("校历", docs[0].metadata["doc_type"])
        self.assertIn("9月1日", docs[0].page_content)
        temporary_path = self.engine.ocr.call_args.args[0]
        self.assertEqual(".png", Path(temporary_path).suffix)
        self.assertFalse(Path(temporary_path).exists())


if __name__ == "__main__":
    unittest.main()
