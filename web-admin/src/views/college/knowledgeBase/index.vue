<template>
  <div class="knowledge-base content-box">
    <div class="card">
      <div class="header">
        <div>
          <div class="title">政策知识库</div>
          <div class="subtitle">统一管理政策文件、发布状态和 RAG 向量入库状态</div>
        </div>
        <div class="actions">
          <el-button @click="loadDocuments()">刷新</el-button>
          <el-button type="primary" @click="openCreateDialog">新增政策</el-button>
        </div>
      </div>

      <el-alert
        title="发布后系统会在后台解析 PDF 并写入向量库；状态变为“已就绪”后，学生问答才能检索到该文档。"
        type="info"
        :closable="false"
        class="mb16"
      />

      <el-form :model="query" inline class="filter-form" @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model.trim="query.keyword" placeholder="标题、关键词或文件名" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.category" clearable filterable placeholder="全部分类" class="filter-select">
            <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用对象">
          <el-select v-model="query.audience" clearable placeholder="全部" class="filter-select">
            <el-option label="全部学生" value="ALL" />
            <el-option label="本科生" value="UNDERGRADUATE" />
            <el-option label="研究生" value="POSTGRADUATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="发布状态">
          <el-select v-model="query.docStatus" clearable placeholder="全部" class="filter-select">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="入库状态">
          <el-select v-model="query.ingestStatus" clearable placeholder="全部" class="filter-select">
            <el-option label="待入库" value="PENDING" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已就绪" value="READY" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="documents" row-key="id" empty-text="暂无政策文档">
        <el-table-column label="政策文件" min-width="250">
          <template #default="{ row }">
            <div class="document-title">{{ row.title }}</div>
            <div class="document-file">{{ row.fileName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120" show-overflow-tooltip />
        <el-table-column label="适用对象" width="105">
          <template #default="{ row }">{{ audienceLabel(row.audience) }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="90" />
        <el-table-column label="发布状态" width="100">
          <template #default="{ row }">
            <el-tag :type="docStatusType(row.docStatus)">{{ docStatusLabel(row.docStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="RAG 入库" width="130">
          <template #default="{ row }">
            <el-tooltip v-if="row.ingestStatus === 'FAILED'" :content="row.ingestError || '入库失败'" placement="top">
              <el-tag type="danger">入库失败</el-tag>
            </el-tooltip>
            <el-tag v-else :type="ingestStatusType(row.ingestStatus)">{{ ingestStatusLabel(row.ingestStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文本块" width="80" align="center">
          <template #default="{ row }">{{ row.chunkCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="165">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="isProcessing(row)" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="primary" @click="downloadDocument(row)">下载</el-button>
            <el-button
              link
              type="success"
              :loading="publishingId === row.id"
              :disabled="isProcessing(row)"
              @click="publishDocument(row)"
            >
              {{ row.docStatus === "PUBLISHED" ? "重新入库" : "发布" }}
            </el-button>
            <el-button link type="danger" :disabled="isProcessing(row)" @click="removeDocument(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadDocuments()"
          @size-change="handlePageSizeChange"
        />
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑政策文档' : '新增政策文档'"
      width="760px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px" label-suffix=" :">
        <el-form-item label="PDF 文件" prop="file">
          <el-upload
            :auto-upload="false"
            :multiple="false"
            :limit="1"
            accept=".pdf,application/pdf"
            :file-list="uploadFileList"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
          >
            <el-button type="primary" plain>{{ form.fileId ? "替换 PDF" : "选择 PDF" }}</el-button>
            <template #tip>
              <div class="el-upload__tip">
                {{ form.fileId ? `当前文件：${form.existingFileName}；不重新选择则保留原文件` : "仅支持 PDF，最大 20MB" }}
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="政策标题" prop="title">
              <el-input v-model.trim="form.title" maxlength="200" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="版本号" prop="version"><el-input v-model.trim="form.version" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-select v-model="form.category" filterable allow-create default-first-option class="full-width">
                <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用对象" prop="audience">
              <el-select v-model="form.audience" class="full-width">
                <el-option label="全部学生" value="ALL" />
                <el-option label="本科生" value="UNDERGRADUATE" />
                <el-option label="研究生" value="POSTGRADUATE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="生效日期">
              <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="失效日期">
              <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="标签">
          <el-select v-model="form.tags" multiple filterable allow-create default-first-option class="full-width">
            <el-option v-for="item in tagOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model.trim="form.keywords" placeholder="多个关键词可用逗号分隔" maxlength="500" />
        </el-form-item>
        <el-form-item label="官方链接">
          <el-input v-model.trim="form.officialUrl" placeholder="https://..." maxlength="500" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model.trim="form.remark" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDocument">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="collegeKnowledgeBase">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules, UploadFile, UploadFiles } from "element-plus";
import { uploadFile } from "@/api/modules/file";
import {
  createPolicyDocument,
  deletePolicyDocument,
  listPolicyDocuments,
  publishPolicyDocument,
  updatePolicyDocument
} from "@/api/modules/policyDocument";
import type { PolicyDocumentApi } from "@/api/modules/policyDocument";
import { useUserStore } from "@/stores/modules/user";

type PolicyForm = {
  id?: number;
  fileId?: number;
  existingFileName: string;
  file: File | null;
  title: string;
  category: string;
  audience: PolicyDocumentApi.Audience;
  version: string;
  effectiveDate: string;
  expiryDate: string;
  tags: string[];
  keywords: string;
  officialUrl: string;
  remark: string;
};

const categoryOptions = ["学籍管理", "奖助学金", "请假管理", "证明开具", "就业实习", "校历", "违纪处分", "其他"];
const tagOptions = ["全部学生", "本科", "研究生", "学籍", "休学", "复学", "毕业", "违纪处分", "校历"];
const query = reactive<PolicyDocumentApi.PageQuery>({
  page: 1,
  pageSize: 10,
  keyword: "",
  category: "",
  audience: "",
  docStatus: "",
  ingestStatus: ""
});
const documents = ref<PolicyDocumentApi.Item[]>([]);
const total = ref(0);
const loading = ref(false);
const publishingId = ref<number>();
let pollTimer: ReturnType<typeof setTimeout> | undefined;

const emptyForm = (): PolicyForm => ({
  existingFileName: "",
  file: null,
  title: "",
  category: "",
  audience: "ALL",
  version: "v1.0",
  effectiveDate: "",
  expiryDate: "",
  tags: [],
  keywords: "",
  officialUrl: "",
  remark: ""
});
const dialogVisible = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();
const form = reactive<PolicyForm>(emptyForm());
const uploadFileList = ref<UploadFiles>([]);
const formRules = computed<FormRules>(() => ({
  file: [
    { validator: (_r, _v, done) => (!form.file && !form.fileId ? done(new Error("请选择 PDF 文件")) : done()), trigger: "change" }
  ],
  title: [{ required: true, message: "请填写政策标题", trigger: "blur" }],
  category: [{ required: true, message: "请选择或填写分类", trigger: "change" }],
  audience: [{ required: true, message: "请选择适用对象", trigger: "change" }],
  version: [{ required: true, message: "请填写版本号", trigger: "blur" }]
}));

const loadDocuments = async (silent = false) => {
  if (!silent) loading.value = true;
  try {
    const response = await listPolicyDocuments({ ...query });
    documents.value = response.data.records;
    total.value = response.data.total;
    schedulePolling();
  } finally {
    loading.value = false;
  }
};
const schedulePolling = () => {
  if (pollTimer) clearTimeout(pollTimer);
  pollTimer = undefined;
  if (documents.value.some(item => item.ingestStatus === "PROCESSING")) {
    pollTimer = setTimeout(() => loadDocuments(true), 3000);
  }
};
const handleSearch = () => {
  query.page = 1;
  loadDocuments();
};
const resetSearch = () => {
  Object.assign(query, { page: 1, keyword: "", category: "", audience: "", docStatus: "", ingestStatus: "" });
  loadDocuments();
};
const handlePageSizeChange = () => {
  query.page = 1;
  loadDocuments();
};

const resetForm = () => {
  Object.assign(form, emptyForm());
  delete form.id;
  delete form.fileId;
  uploadFileList.value = [];
  nextTick(() => formRef.value?.clearValidate());
};
const openCreateDialog = () => {
  resetForm();
  dialogVisible.value = true;
};
const openEditDialog = (row: PolicyDocumentApi.Item) => {
  Object.assign(form, {
    id: row.id,
    fileId: row.fileId,
    existingFileName: row.fileName,
    file: null,
    title: row.title,
    category: row.category,
    audience: row.audience,
    version: row.version,
    effectiveDate: row.effectiveDate || "",
    expiryDate: row.expiryDate || "",
    tags: [...(row.tags || [])],
    keywords: row.keywords || "",
    officialUrl: row.officialUrl || "",
    remark: row.remark || ""
  });
  uploadFileList.value = [];
  dialogVisible.value = true;
  nextTick(() => formRef.value?.clearValidate());
};
const handleFileChange = (upload: UploadFile, files: UploadFiles) => {
  const raw = upload.raw as File | undefined;
  if (!raw) return;
  if (raw.type !== "application/pdf" && !raw.name.toLowerCase().endsWith(".pdf")) {
    ElMessage.error("仅支持 PDF 文件");
    handleFileRemove();
    return;
  }
  if (raw.size > 20 * 1024 * 1024) {
    ElMessage.error("PDF 文件不能超过 20MB");
    handleFileRemove();
    return;
  }
  form.file = raw;
  uploadFileList.value = files.slice(-1);
  if (!form.title) form.title = raw.name.replace(/\.pdf$/i, "");
  formRef.value?.validateField("file");
};
const handleFileRemove = () => {
  form.file = null;
  uploadFileList.value = [];
};

const saveDocument = async () => {
  if (!formRef.value || saving.value) return;
  if (!(await formRef.value.validate().catch(() => false))) return;
  saving.value = true;
  try {
    let fileId = form.fileId;
    if (form.file) {
      const body = new FormData();
      body.append("file", form.file);
      body.append("businessType", "policy");
      fileId = (await uploadFile(body)).data.id;
    }
    if (!fileId) throw new Error("未获得有效的文件 ID");
    const payload: PolicyDocumentApi.SaveRequest = {
      title: form.title,
      category: form.category,
      audience: form.audience,
      version: form.version,
      effectiveDate: form.effectiveDate || null,
      expiryDate: form.expiryDate || null,
      tags: [...form.tags],
      content: null,
      keywords: form.keywords || null,
      officialUrl: form.officialUrl || null,
      remark: form.remark || null,
      fileId
    };
    if (form.id) {
      await updatePolicyDocument(form.id, payload);
      ElMessage.success("政策文档已更新，需要重新发布入库");
    } else {
      await createPolicyDocument(payload);
      ElMessage.success("政策文档已保存为草稿");
    }
    dialogVisible.value = false;
    query.page = 1;
    await loadDocuments();
  } finally {
    saving.value = false;
  }
};

const publishDocument = async (row: PolicyDocumentApi.Item) => {
  const action = row.docStatus === "PUBLISHED" ? "重新向量化这份政策" : "发布并向量化这份政策";
  const confirmed = await ElMessageBox.confirm(`${action}？`, "确认发布", { type: "warning" })
    .then(() => true)
    .catch(() => false);
  if (!confirmed) return;
  publishingId.value = row.id;
  try {
    await publishPolicyDocument(row.id);
    ElMessage.success("发布任务已提交，正在后台进行 RAG 入库");
    await loadDocuments(true);
  } finally {
    publishingId.value = undefined;
  }
};
const removeDocument = async (row: PolicyDocumentApi.Item) => {
  const confirmed = await ElMessageBox.confirm(`确定删除政策“${row.title}”吗？原始 PDF 文件仍会保留。`, "删除确认", {
    type: "warning"
  })
    .then(() => true)
    .catch(() => false);
  if (!confirmed) return;
  await deletePolicyDocument(row.id);
  ElMessage.success("政策记录已删除");
  if (documents.value.length === 1 && query.page > 1) query.page -= 1;
  await loadDocuments();
};
const downloadDocument = async (row: PolicyDocumentApi.Item) => {
  const apiBase = String(import.meta.env.VITE_API_URL || "/api").replace(/\/$/, "");
  const response = await fetch(`${apiBase}/file/download/${row.fileId}`, {
    headers: { "x-access-token": useUserStore().token }
  });
  if (!response.ok) return void ElMessage.error("文件下载失败");
  const url = URL.createObjectURL(await response.blob());
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = row.fileName;
  anchor.click();
  URL.revokeObjectURL(url);
};

const isProcessing = (row: PolicyDocumentApi.Item) => row.ingestStatus === "PROCESSING";
const audienceLabel = (value: PolicyDocumentApi.Audience) =>
  ({ ALL: "全部学生", UNDERGRADUATE: "本科生", POSTGRADUATE: "研究生" })[value] || value;
const docStatusLabel = (value: PolicyDocumentApi.DocStatus) =>
  ({ DRAFT: "草稿", PUBLISHED: "已发布", ARCHIVED: "已归档" })[value] || value;
const docStatusType = (value: PolicyDocumentApi.DocStatus) =>
  (({ DRAFT: "info", PUBLISHED: "success", ARCHIVED: "warning" }) as const)[value];
const ingestStatusLabel = (value: PolicyDocumentApi.IngestStatus) =>
  ({ PENDING: "待入库", PROCESSING: "处理中", READY: "已就绪", FAILED: "入库失败" })[value] || value;
const ingestStatusType = (value: PolicyDocumentApi.IngestStatus) =>
  (({ PENDING: "info", PROCESSING: "warning", READY: "success", FAILED: "danger" }) as const)[value];
const formatDateTime = (value?: string) => (value ? value.replace("T", " ").slice(0, 19) : "-");

onMounted(() => loadDocuments());
onBeforeUnmount(() => pollTimer && clearTimeout(pollTimer));
</script>

<style scoped lang="scss">
.knowledge-base {
  .header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 16px;
  }
  .title {
    font-size: 20px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }
  .subtitle {
    margin-top: 5px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
  .actions {
    display: flex;
    flex-shrink: 0;
    gap: 8px;
  }
  .filter-form {
    padding: 16px 16px 0;
    margin-bottom: 16px;
    background: var(--el-fill-color-lighter);
    border-radius: 8px;
  }
  .filter-select {
    width: 145px;
  }
  .document-title {
    font-weight: 600;
    color: var(--el-text-color-primary);
  }
  .document-file {
    margin-top: 5px;
    overflow: hidden;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
    margin-top: 18px;
  }
  .full-width {
    width: 100%;
  }
}
@media (max-width: 900px) {
  .knowledge-base .header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
