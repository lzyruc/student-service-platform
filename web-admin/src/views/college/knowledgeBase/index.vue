<template>
  <div class="knowledge-base content-box">
    <div class="card">
      <div class="header">
        <div class="title">知识库（PDF 上传）</div>
        <div class="actions">
          <el-button type="primary" plain @click="resetPdf">重置表单</el-button>
        </div>
      </div>
      <el-alert title="上传 PDF 后填写信息保存到本地列表，后续再对接后端接口。" type="info" :closable="false" class="mb16" />
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <el-form ref="pdfFormRef" :model="pdfForm" :rules="pdfRules" label-width="96px" label-suffix=" :">
            <el-form-item label="文件" prop="file">
              <el-upload
                drag
                :auto-upload="false"
                :multiple="false"
                :limit="1"
                accept=".pdf,application/pdf"
                :show-file-list="true"
                :file-list="pdfUploadFileList"
                :on-change="onPdfChange"
                :on-remove="onPdfRemove"
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">把 PDF 拖到这里，或 <em>点击选择</em></div>
                <template #tip>
                  <div class="el-upload__tip">仅支持 PDF，单文件建议不超过 30MB</div>
                </template>
              </el-upload>
            </el-form-item>
            <el-form-item label="标题" prop="title">
              <el-input v-model.trim="pdfForm.title" placeholder="例如：奖助学金政策 2026 版" clearable />
            </el-form-item>
            <el-form-item label="分类" prop="category">
              <el-select v-model="pdfForm.category" placeholder="请选择" clearable filterable>
                <el-option label="奖助学金" value="奖助学金" />
                <el-option label="党团流程" value="党团流程" />
                <el-option label="请假管理" value="请假管理" />
                <el-option label="证明开具" value="证明开具" />
                <el-option label="就业实习" value="就业实习" />
                <el-option label="其他" value="其他" />
              </el-select>
            </el-form-item>
            <el-form-item label="标签" prop="tags">
              <el-select
                v-model="pdfForm.tags"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="选择标签或输入后回车添加"
              >
                <el-option v-for="t in tagOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
            <el-form-item label="版本号" prop="version">
              <el-input v-model.trim="pdfForm.version" placeholder="例如：v1.0" clearable />
            </el-form-item>
            <el-form-item label="生效日期" prop="effectiveDate">
              <el-date-picker v-model="pdfForm.effectiveDate" type="date" placeholder="请选择日期" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="备注" prop="remark">
              <el-input v-model.trim="pdfForm.remark" type="textarea" :rows="3" placeholder="可选：适用范围/关键变更点等" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="uploading" @click="savePdfDoc">保存到本地列表</el-button>
              <el-button :disabled="!pdfForm.file" @click="previewSelectedPdf">预览所选 PDF</el-button>
            </el-form-item>
          </el-form>
        </el-col>

        <el-col :xs="24" :md="12">
          <div class="card inner-card">
            <div class="inner-title">已保存文件（本地）</div>
            <el-table :data="pdfDocList" row-key="id" height="520">
              <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
              <el-table-column prop="category" label="分类" width="110" />
              <el-table-column prop="version" label="版本" width="90" />
              <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
              <el-table-column prop="createdAt" label="录入时间" width="160" />
              <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="previewPdfRow(row)">预览</el-button>
                  <el-button link type="danger" @click="removePdfRow(row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="mt12">
              <el-button type="primary" plain :disabled="pdfDocList.length === 0" @click="exportPdfJson">导出 JSON</el-button>
              <el-button type="warning" plain :disabled="pdfDocList.length === 0" @click="clearPdfList">清空列表</el-button>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <el-dialog v-model="jsonDialogVisible" title="导出内容（JSON）" width="780px">
      <el-input v-model="jsonDialogValue" type="textarea" :rows="16" />
      <template #footer>
        <el-button @click="jsonDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="copyJson">复制</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="collegeKnowledgeBase">
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules, UploadFile, UploadFiles } from "element-plus";
import { UploadFilled } from "@element-plus/icons-vue";
import { getDownloadUrl, uploadFile } from "@/api/modules/file";

type PdfDocForm = {
  file: File | null;
  title: string;
  category: string;
  tags: string[];
  version: string;
  effectiveDate: string;
  remark: string;
};

type PdfDocRow = {
  id: string;
  fileId: number;
  title: string;
  category: string;
  tags: string[];
  version: string;
  effectiveDate: string;
  remark: string;
  fileName: string;
  fileSize: number;
  fileType: string;
  downloadUrl: string;
  createdAt: string;
};

const pdfFormRef = ref<FormInstance>();
const pdfUploadFileList = ref<UploadFiles>([]);
const pdfForm = reactive<PdfDocForm>({
  file: null,
  title: "",
  category: "",
  tags: [],
  version: "v1.0",
  effectiveDate: "",
  remark: ""
});

const pdfRules: FormRules = reactive({
  file: [{ required: true, message: "请选择 PDF 文件", trigger: "change" }],
  title: [{ required: true, message: "请填写标题", trigger: "blur" }],
  category: [{ required: true, message: "请选择分类", trigger: "change" }]
});

const pdfDocList = ref<PdfDocRow[]>([]);
const uploading = ref(false);

const tagOptions = computed(() => {
  const base = ["全部学生"];
  if (pdfForm.category === "党团流程") return [...base, "群众", "共青团员", "入党积极分子", "共产党员"];
  if (pdfForm.category === "就业实习") return [...base, "毕业年级"];
  return base;
});

watch(
  () => pdfForm.category,
  () => {
    if (!pdfForm.tags.length) pdfForm.tags = ["全部学生"];
  }
);

const onPdfChange = (uploadFile: UploadFile, uploadFiles: UploadFiles) => {
  const raw = uploadFile.raw as File | undefined;
  if (!raw) return;
  const isPdf = raw.type === "application/pdf" || raw.name.toLowerCase().endsWith(".pdf");
  if (!isPdf) {
    ElMessage.error("仅支持 PDF 文件");
    pdfUploadFileList.value = [];
    pdfForm.file = null;
    return;
  }
  const maxMB = 30;
  const isLt = raw.size / 1024 / 1024 <= maxMB;
  if (!isLt) {
    ElMessage.error(`文件过大，建议不超过 ${maxMB}MB`);
    pdfUploadFileList.value = [];
    pdfForm.file = null;
    return;
  }
  pdfUploadFileList.value = uploadFiles.slice(-1);
  pdfForm.file = raw;
  if (!pdfForm.title) pdfForm.title = raw.name.replace(/\.pdf$/i, "");
};

const onPdfRemove = () => {
  pdfUploadFileList.value = [];
  pdfForm.file = null;
};

const formatTime = (d = new Date()) => {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const genId = () => `${Date.now()}-${Math.random().toString(16).slice(2)}`;

const previewFile = (file: File) => {
  const url = URL.createObjectURL(file);
  window.open(url, "_blank", "noopener,noreferrer");
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
};

const previewSelectedPdf = () => {
  if (!pdfForm.file) return;
  previewFile(pdfForm.file);
};

const savePdfDoc = async () => {
  if (!pdfFormRef.value) return;
  await pdfFormRef.value.validate(async valid => {
    if (!valid) return;
    if (!pdfForm.file) return;
    if (uploading.value) return;
    uploading.value = true;
    let fileId = 0;
    let downloadUrl = "";
    try {
      const fd = new FormData();
      fd.append("file", pdfForm.file);
      fd.append("businessType", "policy");
      fd.append("uploaderId", "1");
      const res = await uploadFile(fd);
      fileId = res.data.id;
      downloadUrl = getDownloadUrl(fileId);
    } catch (e: any) {
      ElMessage.error(e?.message ?? "上传失败");
      uploading.value = false;
      return;
    }
    pdfDocList.value.unshift({
      id: genId(),
      fileId,
      title: pdfForm.title,
      category: pdfForm.category,
      tags: [...pdfForm.tags],
      version: pdfForm.version,
      effectiveDate: pdfForm.effectiveDate,
      remark: pdfForm.remark,
      fileName: pdfForm.file.name,
      fileSize: pdfForm.file.size,
      fileType: pdfForm.file.type,
      downloadUrl,
      createdAt: formatTime()
    });
    uploading.value = false;
    ElMessage.success("已上传并保存到本地列表");
    pdfForm.title = "";
    pdfForm.category = "";
    pdfForm.tags = [];
    pdfForm.version = "v1.0";
    pdfForm.effectiveDate = "";
    pdfForm.remark = "";
    pdfUploadFileList.value = [];
    pdfForm.file = null;
  });
};

const previewPdfRow = (row: PdfDocRow) => {
  window.open(row.downloadUrl, "_blank", "noopener,noreferrer");
};

const removePdfRow = (id: string) => {
  const idx = pdfDocList.value.findIndex(i => i.id === id);
  if (idx === -1) return;
  pdfDocList.value.splice(idx, 1);
  ElMessage.success("已删除");
};

const clearPdfList = () => {
  pdfDocList.value = [];
  ElMessage.success("已清空");
};

const jsonDialogVisible = ref(false);
const jsonDialogValue = ref("");

const exportPdfJson = () => {
  const data = pdfDocList.value.map(d => ({
    fileId: d.fileId,
    title: d.title,
    category: d.category,
    tags: d.tags,
    version: d.version,
    effectiveDate: d.effectiveDate,
    remark: d.remark,
    fileName: d.fileName,
    fileSize: d.fileSize,
    fileType: d.fileType,
    downloadUrl: d.downloadUrl,
    createdAt: d.createdAt
  }));
  jsonDialogValue.value = JSON.stringify(data, null, 2);
  jsonDialogVisible.value = true;
};

const copyJson = async () => {
  try {
    await navigator.clipboard.writeText(jsonDialogValue.value);
    ElMessage.success("已复制");
  } catch {
    ElMessage.error("复制失败，请手动复制");
  }
};

const resetPdf = () => {
  pdfUploadFileList.value = [];
  pdfForm.file = null;
  pdfForm.title = "";
  pdfForm.category = "";
  pdfForm.tags = [];
  pdfForm.version = "v1.0";
  pdfForm.effectiveDate = "";
  pdfForm.remark = "";
  ElMessage.success("已重置");
};
</script>

<style scoped lang="scss">
.knowledge-base {
  .header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;

    .title {
      font-size: 18px;
      font-weight: 600;
    }
  }

  .inner-card {
    padding: 16px;
    border-radius: 8px;
  }

  .inner-title {
    font-weight: 600;
    margin-bottom: 12px;
  }
}
</style>
