<template>
  <div class="notification-page content-box">
    <div class="card">
      <div class="header">
        <div class="title">通知公告 / 知识库</div>
        <div class="actions">
          <el-button type="primary" @click="openCreate">新增</el-button>
          <el-button plain @click="refreshList">刷新</el-button>
        </div>
      </div>

      <div class="toolbar mb12">
        <el-input v-model.trim="keyword" placeholder="搜索：标题/标签" clearable class="w280" @keyup.enter="refreshList" />
        <el-button type="primary" plain @click="refreshList">查询</el-button>
      </div>

      <el-table :data="list" row-key="id" height="610" v-loading="loading">
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="tags" label="标签" width="180" show-overflow-tooltip />
        <el-table-column prop="is_urgent" label="紧急" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.is_urgent" type="danger">是</el-tag>
            <el-tag v-else type="info">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="170" />
        <el-table-column label="附件" width="120">
          <template #default="{ row }">
            <el-button v-if="row.file_id" link type="primary" @click="download(row.file_id)">下载</el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewRow(row)">查看</el-button>
            <el-button link type="primary" @click="editRow(row)">编辑</el-button>
            <el-button link type="danger" @click="removeRow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑' : '新增'" width="820px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" label-suffix=" :">
        <el-form-item label="标题" prop="title">
          <el-input v-model.trim="form.title" placeholder="请输入通知标题" clearable />
        </el-form-item>
        <el-form-item label="标签" prop="tags">
          <el-input v-model.trim="form.tags" placeholder="多个标签用逗号分隔（可选）" clearable />
        </el-form-item>
        <el-form-item label="紧急" prop="is_urgent">
          <el-switch v-model="form.is_urgent" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="8" placeholder="请输入通知内容（可选）" />
        </el-form-item>
        <el-form-item label="附件">
          <div class="file-row">
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :multiple="false"
              :limit="1"
              :show-file-list="false"
              :on-change="onFileChange"
            >
              <el-button :loading="fileUploading">上传附件</el-button>
            </el-upload>
            <el-button v-if="form.file_id" link type="primary" @click="download(form.file_id)">下载当前附件</el-button>
            <el-button v-if="form.file_id" link type="danger" @click="removeAttachment">移除附件</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="通知详情" width="820px">
      <div class="detail">
        <div class="detail-title">{{ detail?.title }}</div>
        <div class="detail-meta">
          <span>标签：{{ detail?.tags || "—" }}</span>
          <span class="ml12">紧急：{{ detail?.is_urgent ? "是" : "否" }}</span>
          <span class="ml12">创建：{{ detail?.created_at || "—" }}</span>
        </div>
        <div class="detail-content" v-if="detail?.content">{{ detail?.content }}</div>
        <div class="mt12">
          <el-button v-if="detail?.file_id" type="primary" plain @click="download(detail.file_id)">下载附件</el-button>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="detailVisible = false">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="collegeNotification">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules, UploadFile, UploadInstance } from "element-plus";
import { deleteNotification, listNotifications, saveNotification } from "@/api/modules/notification";
import { getDownloadUrl, uploadFile } from "@/api/modules/file";

type NotificationRow = {
  id: number;
  title: string;
  tags: string;
  content: string;
  is_urgent: boolean;
  file_id: number | null;
  created_at: string;
};

const keyword = ref("");
const loading = ref(false);
const list = ref<NotificationRow[]>([]);

const normalizeBackendTime = (v: any) => {
  const s = String(v ?? "").trim();
  if (!s) return "";
  return s.includes("T") ? s.replace("T", " ").slice(0, 16) : s;
};

const refreshList = async () => {
  loading.value = true;
  try {
    const res = await listNotifications({ keyword: keyword.value.trim() || undefined });
    list.value = (res.data || []).map(i => {
      const fileId = Number((i as any).file_id);
      return {
        id: Number((i as any).id),
        title: String((i as any).title ?? ""),
        tags: String((i as any).tags ?? ""),
        is_urgent: Boolean((i as any).is_urgent),
        content: String((i as any).content ?? ""),
        file_id: Number.isFinite(fileId) && fileId > 0 ? fileId : null,
        created_at: normalizeBackendTime((i as any).created_at)
      };
    });
  } catch (e: any) {
    ElMessage.error(e?.message ?? "加载失败");
  } finally {
    loading.value = false;
  }
};

const download = (fileId: number) => {
  window.open(getDownloadUrl(fileId));
};

const formVisible = ref(false);
const saving = ref(false);
const fileUploading = ref(false);
const uploadRef = ref<UploadInstance>();
const formRef = ref<FormInstance>();
const form = reactive({
  id: 0,
  title: "",
  tags: "",
  is_urgent: false,
  content: "",
  file_id: null as number | null
});

const rules: FormRules = reactive({
  title: [{ required: true, message: "请填写标题", trigger: "blur" }]
});

const clearUpload = () => {
  uploadRef.value?.clearFiles();
};

const resetForm = () => {
  form.id = 0;
  form.title = "";
  form.tags = "";
  form.is_urgent = false;
  form.content = "";
  form.file_id = null;
  clearUpload();
};

const openCreate = () => {
  resetForm();
  formVisible.value = true;
};

const editRow = (row: NotificationRow) => {
  clearUpload();
  form.id = row.id;
  form.title = row.title;
  form.tags = row.tags;
  form.is_urgent = row.is_urgent;
  form.content = row.content;
  form.file_id = row.file_id;
  formVisible.value = true;
};

const removeAttachment = () => {
  form.file_id = null;
  clearUpload();
};

const submit = async () => {
  if (!formRef.value) return;
  await formRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      await saveNotification({
        id: form.id || undefined,
        title: form.title,
        tags: form.tags || undefined,
        is_urgent: form.is_urgent,
        content: form.content || undefined,
        file_id: form.file_id,
        publisher_id: 1
      });
      ElMessage.success("已保存");
      formVisible.value = false;
      await refreshList();
    } catch (e: any) {
      ElMessage.error(e?.message ?? "保存失败");
    } finally {
      saving.value = false;
    }
  });
};

const onFileChange = async (file: UploadFile) => {
  const raw = file.raw as File | undefined;
  if (!raw) return;
  fileUploading.value = true;
  try {
    const fd = new FormData();
    fd.append("file", raw);
    fd.append("businessType", "notice");
    fd.append("uploaderId", "1");
    const res = await uploadFile(fd);
    form.file_id = res.data.id;
    clearUpload();
    ElMessage.success("附件已上传");
  } catch (e: any) {
    ElMessage.error(e?.message ?? "上传失败");
  } finally {
    fileUploading.value = false;
  }
};

const detailVisible = ref(false);
const detail = ref<NotificationRow | null>(null);

const viewRow = (row: NotificationRow) => {
  detail.value = row;
  detailVisible.value = true;
};

const removeRow = async (row: NotificationRow) => {
  try {
    await ElMessageBox.confirm(`确认删除《${row.title}》吗？`, "提示", { type: "warning" });
  } catch {
    return;
  }
  try {
    await deleteNotification(row.id);
    ElMessage.success("已删除");
    await refreshList();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "删除失败");
  }
};

onMounted(() => {
  refreshList();
});
</script>

<style scoped lang="scss">
.notification-page {
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
  .toolbar {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }
  .w280 {
    width: 240px;
  }
  .file-row {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }
  .detail-title {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 10px;
  }
  .detail-meta {
    color: var(--el-text-color-secondary);
    margin-bottom: 10px;
  }
  .detail-content {
    white-space: pre-wrap;
    line-height: 1.6;
  }
}
</style>
