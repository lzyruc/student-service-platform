<template>
  <div class="examine-approve content-box">
    <div class="card">
      <div class="header">
        <div class="title">审批中心（电子证明）</div>
        <div class="actions">
          <el-upload
            :auto-upload="false"
            :multiple="false"
            :limit="1"
            accept=".doc,.docx,.pdf"
            :show-file-list="false"
            :on-change="onTemplateChange"
          >
            <el-button type="primary" plain :loading="templateUploading">上传模板</el-button>
          </el-upload>
          <el-button type="primary" plain @click="refreshList">刷新列表</el-button>
          <el-button type="primary" plain @click="seedDemo">生成测试数据</el-button>
        </div>
      </div>
      <el-alert
        title="已对接后端：审批数据来自数据库 t_certificate_apply + t_approval_task。"
        type="info"
        :closable="false"
        class="mb16"
      />

      <el-tabs v-model="activeTab">
        <el-tab-pane :label="`待审核（${pendingList.length}）`" name="pending">
          <div class="filters mb12">
            <el-select v-model="filter.certificateType" placeholder="证明类型" clearable class="w140">
              <el-option label="在校证明" value="在校证明" />
              <el-option label="请假条" value="请假条" />
              <el-option label="用章申请" value="用章申请" />
            </el-select>
            <el-input v-model.trim="filter.keyword" placeholder="搜索：学号/类型/申请内容" clearable class="w260" />
            <el-button type="primary" plain :disabled="pendingList.length === 0" @click="batchApprove">批量通过</el-button>
          </div>

          <el-table :data="pendingFiltered" row-key="id" height="560" @selection-change="onSelectionChange">
            <el-table-column type="selection" width="55" />
            <el-table-column prop="certificateType" label="类型" width="110" />
            <el-table-column prop="studentNo" label="学号" width="140" />
            <el-table-column prop="summary" label="申请内容" min-width="220" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="提交时间" width="170" />
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail(row)">查看</el-button>
                <el-button link type="success" @click="openDecision(row, 'approved')">通过</el-button>
                <el-button link type="danger" @click="openDecision(row, 'rejected')">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`已处理（${doneList.length}）`" name="done">
          <div class="filters mb12">
            <el-select v-model="filterDone.status" placeholder="状态" clearable class="w140">
              <el-option label="已通过" value="已通过" />
              <el-option label="已驳回" value="已驳回" />
            </el-select>
            <el-input v-model.trim="filterDone.keyword" placeholder="搜索：学号/类型/申请内容" clearable class="w260" />
          </div>
          <el-table :data="doneFiltered" row-key="id" height="560">
            <el-table-column prop="applyStatus" label="状态" width="90" />
            <el-table-column prop="certificateType" label="类型" width="110" />
            <el-table-column prop="studentNo" label="学号" width="140" />
            <el-table-column prop="summary" label="申请内容" min-width="220" show-overflow-tooltip />
            <el-table-column prop="lastHandledAt" label="处理时间" width="170" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail(row)">查看</el-button>
                <el-button link type="danger" @click="removeRow(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="模拟提交（写入数据库）" name="create">
          <el-row :gutter="16">
            <el-col :xs="24" :md="12">
              <div class="card inner-card">
                <div class="inner-title">申请信息</div>
                <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="96px" label-suffix=" :">
                  <el-form-item label="证明类型" prop="certificateType">
                    <el-select v-model="createForm.certificateType" placeholder="请选择">
                      <el-option label="在校证明" value="在校证明" />
                      <el-option label="请假条" value="请假条" />
                      <el-option label="用章申请" value="用章申请" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="学号" prop="studentNo">
                    <el-input v-model.trim="createForm.studentNo" placeholder="例如：20240001" clearable />
                  </el-form-item>
                  <el-form-item label="申请标题" prop="title">
                    <el-input v-model.trim="createForm.title" placeholder="例如：在校证明申请" clearable />
                  </el-form-item>
                  <el-form-item label="姓名" prop="applicantName">
                    <el-input v-model.trim="createForm.applicantName" placeholder="例如：张三" clearable />
                  </el-form-item>
                  <el-form-item label="班级" prop="className">
                    <el-input v-model.trim="createForm.className" placeholder="例如：计科2401" clearable />
                  </el-form-item>
                  <el-form-item label="起止时间" prop="range">
                    <el-date-picker
                      v-model="createForm.range"
                      type="datetimerange"
                      start-placeholder="开始时间"
                      end-placeholder="结束时间"
                      value-format="YYYY-MM-DD HH:mm"
                      format="YYYY-MM-DD HH:mm"
                    />
                  </el-form-item>
                  <el-form-item label="原因说明" prop="reason">
                    <el-input v-model.trim="createForm.reason" type="textarea" :rows="4" placeholder="例如：发烧就医，申请请假" />
                  </el-form-item>
                  <el-form-item label="附件" prop="attachments">
                    <el-upload
                      :auto-upload="false"
                      :multiple="true"
                      :limit="5"
                      :show-file-list="true"
                      :file-list="attachmentList"
                      :on-change="onAttachmentChange"
                      :on-remove="onAttachmentRemove"
                    >
                      <el-button type="primary" plain>选择文件</el-button>
                      <template #tip>
                        <div class="el-upload__tip">仅做展示：不会上传到服务器</div>
                      </template>
                    </el-upload>
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" :loading="submitting" @click="submitCreate">提交（进入待审核）</el-button>
                    <el-button @click="resetCreate">重置</el-button>
                  </el-form-item>
                </el-form>
              </div>
            </el-col>

            <el-col :xs="24" :md="12">
              <div class="card inner-card">
                <div class="inner-title">说明</div>
                <el-descriptions :column="1" border>
                  <el-descriptions-item label="如何模拟“收到申请”">
                    这里的“提交”按钮等价于学生端提交；数据会写入数据库，并进入“待审核”列表。
                  </el-descriptions-item>
                  <el-descriptions-item label="如何模拟“审批留痕”">
                    点击通过/驳回会写入审批任务（时间、审批人、意见），并进入“已处理”列表。
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="detailVisible" title="申请详情" width="860px">
      <div v-if="currentDetail">
        <el-descriptions :column="2" border class="mb12">
          <el-descriptions-item label="状态">{{ currentDetail.apply.applyStatus }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ currentDetail.apply.certificateType }}</el-descriptions-item>
          <el-descriptions-item label="学号">{{ currentDetail.apply.studentNo }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ normalizeTime(currentDetail.apply.createdAt) || "—" }}</el-descriptions-item>
          <el-descriptions-item label="申请标题" :span="2">{{ currentExtra?.title || "—" }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ detailApplicantName || "—" }}</el-descriptions-item>
          <el-descriptions-item label="班级">{{ detailClassName || "—" }}</el-descriptions-item>
          <el-descriptions-item label="起止时间" :span="2">
            {{ currentExtra?.startAt || "—" }} ～ {{ currentExtra?.endAt || "—" }}
          </el-descriptions-item>
          <el-descriptions-item label="原因说明" :span="2">{{ currentExtra?.reason || "—" }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ currentDetail.apply.lastApproverName || "—" }}</el-descriptions-item>
          <el-descriptions-item label="处理时间">
            {{ normalizeTime(currentDetail.apply.lastHandledAt) || "—" }}
          </el-descriptions-item>
          <el-descriptions-item label="审批意见" :span="2">{{ currentDetail.apply.lastOpinion || "—" }}</el-descriptions-item>
        </el-descriptions>

        <div class="inner-title">流程记录</div>
        <el-timeline>
          <el-timeline-item v-for="h in timelineItems" :key="h.id" :timestamp="h.at">
            <div class="timeline-line">
              <span class="tag">{{ h.action }}</span>
              <span class="by">（{{ h.by }}）</span>
              <span class="note">{{ h.note }}</span>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
      <template #footer>
        <el-button type="primary" @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="decisionVisible" :title="decisionTitle" width="520px">
      <el-form ref="decisionFormRef" :model="decisionForm" :rules="decisionRules" label-width="96px" label-suffix=" :">
        <el-form-item label="审批意见" prop="note">
          <el-input
            v-model.trim="decisionForm.note"
            type="textarea"
            :rows="4"
            placeholder="例如：同意，请按时销假 / 材料不全请补充"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="decisionVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDecision">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="collegeExamineApprove">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules, UploadFile, UploadFiles } from "element-plus";
import { uploadFile } from "@/api/modules/file";
import {
  BackendCertificate,
  decideCertificateApply,
  deleteCertificateApply,
  getCertificateApplyDetail,
  listCertificateApplies,
  submitCertificateApply
} from "@/api/modules/certificate";

type Attachment = {
  name: string;
  size: number;
  type: string;
};

type TimelineItem = {
  id: string;
  action: string;
  by: string;
  at: string;
  note: string;
};

type ApplyRow = BackendCertificate.ApplyItem & { summary: string };

const normalizeTime = (v: any) => {
  const s = String(v ?? "").trim();
  if (!s) return "";
  return s.includes("T") ? s.replace("T", " ").slice(0, 16) : s;
};

const activeTab = ref<"pending" | "done" | "create">("pending");

const templateName = ref("");
const templateFileId = ref<number | null>(null);
const templateUploading = ref(false);
const onTemplateChange = async (file: UploadFile) => {
  const raw = file.raw as File | undefined;
  if (!raw) return;
  if (templateUploading.value) return;
  templateUploading.value = true;
  try {
    const fd = new FormData();
    fd.append("file", raw);
    fd.append("businessType", "template");
    fd.append("uploaderId", "1");
    const res = await uploadFile(fd);
    templateName.value = res.data.originalName || raw.name;
    templateFileId.value = res.data.id;
    ElMessage.success(`模板已上传：${templateName.value}`);
  } catch (e: any) {
    ElMessage.error(e?.message ?? "模板上传失败");
  } finally {
    templateUploading.value = false;
  }
};

const requests = ref<ApplyRow[]>([]);
const selectedIds = ref<number[]>([]);
const listLoading = ref(false);

const filter = reactive<{ certificateType: string; keyword: string }>({ certificateType: "", keyword: "" });
const filterDone = reactive<{ status: string; keyword: string }>({ status: "", keyword: "" });

const pendingList = computed(() => requests.value.filter(r => r.applyStatus === "待审核"));
const doneList = computed(() => requests.value.filter(r => r.applyStatus !== "待审核"));

const matchKeyword = (r: ApplyRow, keyword: string) => {
  const k = keyword.trim();
  if (!k) return true;
  const hay = `${r.studentNo} ${r.certificateType} ${r.summary}`.toLowerCase();
  return hay.includes(k.toLowerCase());
};

const pendingFiltered = computed(() => {
  return pendingList.value.filter(r => {
    if (filter.certificateType && r.certificateType !== filter.certificateType) return false;
    return matchKeyword(r, filter.keyword);
  });
});

const doneFiltered = computed(() => {
  return doneList.value.filter(r => {
    if (filterDone.status && r.applyStatus !== filterDone.status) return false;
    return matchKeyword(r, filterDone.keyword);
  });
});

const onSelectionChange = (rows: ApplyRow[]) => {
  selectedIds.value = rows.map(r => r.id);
};

const createFormRef = ref<FormInstance>();
const attachmentList = ref<UploadFiles>([]);
const createForm = reactive<{
  certificateType: string;
  studentNo: string;
  title: string;
  applicantName: string;
  className: string;
  range: [string, string] | [];
  reason: string;
}>({
  certificateType: "在校证明",
  studentNo: "",
  title: "",
  applicantName: "",
  className: "",
  range: [],
  reason: ""
});

const createRules: FormRules = reactive({
  certificateType: [{ required: true, message: "请选择证明类型", trigger: "change" }],
  studentNo: [{ required: true, message: "请填写学号", trigger: "blur" }]
});

const onAttachmentChange = (_file: UploadFile, uploadFiles: UploadFiles) => {
  attachmentList.value = uploadFiles.slice(0, 5);
};

const onAttachmentRemove = (_file: UploadFile, uploadFiles: UploadFiles) => {
  attachmentList.value = uploadFiles;
};

const buildAttachments = (): Attachment[] => {
  return attachmentList.value
    .map(f => ({
      name: f.name,
      size: f.size ?? 0,
      type: f.raw?.type ?? ""
    }))
    .filter(a => a.name);
};

const resetCreate = () => {
  createForm.certificateType = "在校证明";
  createForm.studentNo = "";
  createForm.title = "";
  createForm.applicantName = "";
  createForm.className = "";
  createForm.range = [];
  createForm.reason = "";
  attachmentList.value = [];
};

const submitting = ref(false);

const submitCreate = async () => {
  if (!createFormRef.value) return;
  if (submitting.value) return;
  await createFormRef.value.validate(async valid => {
    if (!valid) return;
    submitting.value = true;
    try {
      const range = createForm.range as [string, string];
      const payload = {
        title: createForm.title,
        applicantName: createForm.applicantName,
        className: createForm.className,
        startAt: range?.[0] ?? "",
        endAt: range?.[1] ?? "",
        reason: createForm.reason,
        attachments: buildAttachments(),
        templateFileId: templateFileId.value,
        templateName: templateName.value
      };
      await submitCertificateApply({
        studentNo: createForm.studentNo,
        certificateType: createForm.certificateType,
        extraData: JSON.stringify(payload)
      });
      ElMessage.success("已提交到待审核");
      activeTab.value = "pending";
      resetCreate();
      await refreshList();
    } catch (e: any) {
      ElMessage.error(e?.message ?? "提交失败");
    } finally {
      submitting.value = false;
    }
  });
};

const detailVisible = ref(false);
const currentDetail = ref<BackendCertificate.ApplyDetail | null>(null);
const currentExtra = ref<any>(null);

const pickText = (...values: any[]) => {
  for (const v of values) {
    const s = String(v ?? "").trim();
    if (s) return s;
  }
  return "";
};

const detailApplicantName = computed(() => {
  const apply: any = currentDetail.value?.apply ?? {};
  const extra: any = currentExtra.value ?? {};
  return pickText(extra.applicantName, extra.studentName, apply.studentName, apply.student_name, apply.name);
});

const detailClassName = computed(() => {
  const apply: any = currentDetail.value?.apply ?? {};
  const extra: any = currentExtra.value ?? {};
  return pickText(extra.className, extra.clazzName, apply.className, apply.class_name, apply.clazzName, apply.clazz_name);
});

const parseExtraData = (raw: any) => {
  const s = String(raw ?? "").trim();
  if (!s) return null;
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
};

const timelineItems = computed<TimelineItem[]>(() => {
  const tasks = currentDetail.value?.tasks || [];
  return tasks.map(t => ({
    id: String(t.id),
    action: t.approvalStatus || "待处理",
    by: t.approverName || "—",
    at: normalizeTime(t.handledAt || t.updatedAt || t.createdAt),
    note: t.opinion || ""
  }));
});

const openDetail = async (row: ApplyRow) => {
  try {
    const res = await getCertificateApplyDetail(row.id);
    currentDetail.value = res.data;
    currentExtra.value = parseExtraData(res.data?.apply?.extraData);
    detailVisible.value = true;
  } catch (e: any) {
    ElMessage.error(e?.message ?? "获取详情失败");
  }
};

const decisionVisible = ref(false);
const decisionMode = ref<"approved" | "rejected">("approved");
const decisionTargetId = ref<number | null>(null);
const decisionTitle = computed(() => (decisionMode.value === "approved" ? "审批通过" : "审批驳回"));

const decisionFormRef = ref<FormInstance>();
const decisionForm = reactive<{ note: string }>({ note: "" });
const decisionRules: FormRules = reactive({
  note: [{ required: true, message: "请填写审批意见", trigger: "blur" }]
});

const openDecision = (row: ApplyRow, mode: "approved" | "rejected") => {
  decisionMode.value = mode;
  decisionTargetId.value = row.id;
  decisionForm.note = "";
  decisionVisible.value = true;
};

const submitDecision = async () => {
  if (!decisionFormRef.value) return;
  if (decisionTargetId.value == null) return;
  const id = decisionTargetId.value;
  await decisionFormRef.value.validate(async valid => {
    if (!valid) return;
    try {
      await decideCertificateApply(id, { decision: decisionMode.value, opinion: decisionForm.note });
      decisionVisible.value = false;
      ElMessage.success("已处理");
      await refreshList();
    } catch (e: any) {
      ElMessage.error(e?.message ?? "处理失败");
    }
  });
};

const batchApprove = async () => {
  if (selectedIds.value.length === 0) {
    ElMessage.warning("请先勾选要批量通过的申请");
    return;
  }
  try {
    for (const id of selectedIds.value) {
      await decideCertificateApply(id, { decision: "approved", opinion: "批量通过" });
    }
    selectedIds.value = [];
    ElMessage.success("批量通过完成");
    await refreshList();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "批量通过失败");
  }
};

const removeRow = async (id: number) => {
  try {
    await deleteCertificateApply(id);
    ElMessage.success("已删除");
    await refreshList();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "删除失败");
  }
};

const buildSummary = (item: BackendCertificate.ApplyItem) => {
  const extra = parseExtraData(item.extraData);
  const title = String(extra?.title ?? "").trim();
  const reason = String(extra?.reason ?? "").trim();
  if (title) return title;
  if (reason) return reason;
  const raw = String(item.extraData ?? "").trim();
  return raw ? raw.slice(0, 80) : "—";
};

const refreshList = async () => {
  listLoading.value = true;
  try {
    const res = await listCertificateApplies();
    requests.value = (res.data || []).map(i => ({
      ...i,
      createdAt: normalizeTime(i.createdAt),
      lastHandledAt: normalizeTime(i.lastHandledAt),
      summary: buildSummary(i)
    }));
  } catch (e: any) {
    ElMessage.error(e?.message ?? "拉取列表失败");
  } finally {
    listLoading.value = false;
  }
};

const seedDemo = async () => {
  try {
    await submitCertificateApply({
      studentNo: "20240001",
      certificateType: "在校证明",
      extraData: JSON.stringify({ title: "在校证明申请", applicantName: "张三", className: "计科2401", reason: "用于奖学金材料" })
    });
    await submitCertificateApply({
      studentNo: "20240002",
      certificateType: "请假条",
      extraData: JSON.stringify({ title: "请假条申请", applicantName: "李四", className: "计科2401", reason: "发烧就医" })
    });
    ElMessage.success("已生成测试数据");
    await refreshList();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "生成失败");
  }
};

watch(activeTab, () => {
  refreshList();
});

onMounted(() => {
  refreshList();
});
</script>

<style scoped lang="scss">
.examine-approve {
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
    margin-bottom: 12px;
    font-weight: 600;
  }
  .filters {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
  }
  .w120 {
    width: 120px;
  }
  .w140 {
    width: 140px;
  }
  .w260 {
    width: 260px;
  }
  .timeline-line {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
  }
  .tag {
    font-weight: 600;
  }
  .by {
    color: var(--el-text-color-secondary);
  }
  .note {
    color: var(--el-text-color-regular);
  }
}
</style>
