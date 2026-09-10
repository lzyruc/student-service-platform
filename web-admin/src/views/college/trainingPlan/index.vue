<template>
  <div class="training-plan content-box">
    <div class="card">
      <div class="header">
        <div class="title">培养方案管理</div>
        <div class="actions">
          <el-button type="primary" plain @click="resetPlan">重置表单</el-button>
        </div>
      </div>
      <el-alert title="培养方案会以 JSON 形式存入数据库，后续解析也基于该 JSON。" type="info" :closable="false" class="mb16" />
      <div class="card inner-card mb16">
        <div class="inner-title">图片识别导入（OCR）</div>
        <el-alert
          title="上传培养方案图片后，系统会尝试识别并自动填充：专业、课程名称、学分、开课学期。识别结果会覆盖当前课程明细，填充后你仍可手动修改再提交。"
          type="warning"
          :closable="false"
          class="mb12"
        />
        <div class="ocr-bar">
          <el-upload
            :auto-upload="false"
            :multiple="false"
            :limit="1"
            accept="image/*"
            :show-file-list="false"
            :on-change="onOcrImageChange"
          >
            <el-button type="primary" :loading="ocrLoading">上传图片并识别</el-button>
          </el-upload>
          <el-button type="primary" plain :disabled="!ocrText" @click="applyOcrResult">应用识别结果</el-button>
          <el-button plain :disabled="!ocrText" @click="ocrTextVisible = !ocrTextVisible">
            {{ ocrTextVisible ? "隐藏识别文本" : "查看识别文本" }}
          </el-button>
          <div class="ocr-progress" v-if="ocrLoading">
            <el-progress :percentage="ocrProgress" :stroke-width="10" />
          </div>
        </div>
        <div v-if="ocrTextVisible && ocrText" class="mt12">
          <el-input v-model="ocrText" type="textarea" :rows="10" />
        </div>
      </div>
      <el-row :gutter="16">
        <el-col :xs="24" :md="10">
          <div class="card inner-card">
            <div class="inner-title">培养方案信息</div>
            <el-alert
              v-if="editingPlanId !== null"
              title="当前处于“修改模式”，保存将更新现有培养方案"
              type="warning"
              :closable="false"
              class="mb12"
            />
            <el-form ref="planFormRef" :model="planForm" :rules="planRules" label-width="96px" label-suffix=" :">
              <el-form-item label="专业" prop="major">
                <el-input v-model.trim="planForm.major" placeholder="例如：计算机科学与技术" clearable />
              </el-form-item>
              <el-form-item label="年级" prop="grade">
                <el-input v-model.trim="planForm.grade" placeholder="例如：2024" clearable />
              </el-form-item>
              <el-form-item label="版本号" prop="version">
                <el-input v-model.trim="planForm.version" placeholder="例如：v1.0" clearable />
              </el-form-item>
              <el-form-item label="备注" prop="remark">
                <el-input v-model.trim="planForm.remark" type="textarea" :rows="3" placeholder="可选：说明/来源/更新时间等" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="addCourseRow">新增课程行</el-button>
                <el-button plain :disabled="planCourses.length === 0" @click="fillDemoCourses">填充示例</el-button>
                <el-button v-if="editingPlanId !== null" plain type="warning" @click="cancelEditPlan">取消修改</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-col>

        <el-col :xs="24" :md="14">
          <div class="card inner-card">
            <div class="inner-title">课程明细</div>
            <el-table
              :data="planCourses"
              row-key="id"
              height="520"
              class="editable-table"
              empty-text="暂无课程，点击“新增课程行”开始录入"
            >
              <el-table-column label="课程类别" width="160">
                <template #default="{ row }">
                  <el-select v-model="row.category" placeholder="请选择" clearable>
                    <el-option label="部类核心课" value="部类核心课" />
                    <el-option label="部类基础课" value="部类基础课" />
                    <el-option label="专业核心课" value="专业核心课" />
                    <el-option label="思想政治理论课" value="思想政治理论课" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="课程名称" min-width="220">
                <template #default="{ row }">
                  <el-input v-model.trim="row.courseName" placeholder="例如：数据结构" clearable />
                </template>
              </el-table-column>
              <el-table-column label="学分" width="140">
                <template #default="{ row }">
                  <el-input-number v-model="row.credits" :min="0" :max="50" :step="0.5" controls-position="right" />
                </template>
              </el-table-column>
              <el-table-column label="开课学期" width="200">
                <template #default="{ row }">
                  <el-select v-model="row.offeredAt" placeholder="请选择" clearable>
                    <el-option label="1" value="1" />
                    <el-option label="2" value="2" />
                    <el-option label="3" value="3" />
                    <el-option label="4" value="4" />
                    <el-option label="5" value="5" />
                    <el-option label="6" value="6" />
                    <el-option label="7" value="7" />
                    <el-option label="8" value="8" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button link type="danger" @click="removeCourseRow(row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="mt12 flx-between">
              <div class="summary">
                <span>课程数：{{ planCourses.length }}</span>
                <span class="ml12">总学分：{{ totalCredits }}</span>
              </div>
              <div class="actions">
                <el-button type="primary" :disabled="planCourses.length === 0" @click="saveTrainingPlan">
                  {{ editingPlanId === null ? "保存到数据库" : "保存修改" }}
                </el-button>
                <el-button type="primary" plain :disabled="trainingPlanList.length === 0" @click="exportPlanJson">
                  导出 JSON
                </el-button>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>

      <div class="card mt16">
        <div class="inner-title">已保存培养方案（数据库）</div>
        <el-table :data="trainingPlanList" row-key="id" v-loading="listLoading">
          <el-table-column prop="major" label="专业" min-width="180" show-overflow-tooltip />
          <el-table-column prop="grade" label="年级" width="100" />
          <el-table-column prop="version" label="版本" width="120" />
          <el-table-column prop="courseCount" label="课程数" width="100" />
          <el-table-column prop="totalCredits" label="总学分" width="100" />
          <el-table-column prop="createdAt" label="保存时间" width="160" />
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="viewPlanRow(row.id)">查看</el-button>
              <el-button link type="warning" @click="editPlanRow(row.id)">修改</el-button>
              <el-button link type="danger" @click="removePlanRow(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="jsonDialogVisible" title="导出内容（JSON）" width="780px">
      <el-input v-model="jsonDialogValue" type="textarea" :rows="16" />
      <template #footer>
        <el-button @click="jsonDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="copyJson">复制</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="planDetailVisible" title="培养方案详情" width="860px">
      <div class="detail">
        <div class="detail-meta">
          <div><span class="label">专业：</span>{{ planDetail?.major }}</div>
          <div><span class="label">年级：</span>{{ planDetail?.grade }}</div>
          <div><span class="label">版本：</span>{{ planDetail?.version }}</div>
          <div><span class="label">总学分：</span>{{ planDetail?.totalCredits }}</div>
        </div>
        <el-table :data="planDetail?.courses ?? []" height="420">
          <el-table-column prop="category" label="课程类别" width="140" />
          <el-table-column prop="courseName" label="课程名称" min-width="220" show-overflow-tooltip />
          <el-table-column prop="credits" label="学分" width="120" />
          <el-table-column prop="offeredAt" label="开课学期" width="120" />
        </el-table>
        <div class="mt12"><span class="label">备注：</span>{{ planDetail?.remark || "—" }}</div>
      </div>
      <template #footer>
        <el-button type="primary" @click="planDetailVisible = false">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="collegeTrainingPlan">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules, UploadFile } from "element-plus";
import { deleteTrainingPlan, listTrainingPlans, saveTrainingPlan as saveTrainingPlanApi } from "@/api/modules/trainingPlan";

type TrainingCourseRow = {
  id: string;
  category: string;
  courseName: string;
  credits: number;
  offeredAt: string;
};

type TrainingPlanRow = {
  id: number;
  major: string;
  grade: string;
  version: string;
  remark: string;
  courses: TrainingCourseRow[];
  courseCount: number;
  totalCredits: number;
  createdAt: string;
  jsonContent?: string;
};

const planFormRef = ref<FormInstance>();
const planForm = reactive({
  major: "",
  grade: "",
  version: "v1.0",
  remark: ""
});

const planRules: FormRules = reactive({
  major: [{ required: true, message: "请填写专业", trigger: "blur" }],
  grade: [{ required: true, message: "请填写年级", trigger: "blur" }],
  version: [{ required: true, message: "请填写版本号", trigger: "blur" }]
});

const planCourses = ref<TrainingCourseRow[]>([]);
const trainingPlanList = ref<TrainingPlanRow[]>([]);
const listLoading = ref(false);
const editingPlanId = ref<number | null>(null);

const ocrLoading = ref(false);
const ocrProgress = ref(0);
const ocrText = ref("");
const ocrTextVisible = ref(false);
const ocrParsedMajor = ref("");
const ocrParsedCourses = ref<TrainingCourseRow[]>([]);

const jsonDialogVisible = ref(false);
const jsonDialogValue = ref("");

const planDetailVisible = ref(false);
const planDetail = ref<TrainingPlanRow | null>(null);

const formatTime = (d = new Date()) => {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const genId = () => `${Date.now()}-${Math.random().toString(16).slice(2)}`;

const normalizeBackendTime = (v: any) => {
  const s = String(v ?? "").trim();
  if (!s) return "";
  return s.includes("T") ? s.replace("T", " ").slice(0, 16) : s;
};

const refreshList = async () => {
  listLoading.value = true;
  try {
    const res = await listTrainingPlans();
    trainingPlanList.value = (res.data || []).map(i => {
      const courseCount = Number((i as any).courseCount ?? (i as any).course_count ?? 0);
      const totalCredits = Number((i as any).totalCredits ?? (i as any).total_credits ?? 0);
      const jsonContent = String((i as any).jsonContent ?? (i as any).json_content ?? "");
      return {
        id: Number((i as any).id),
        major: String((i as any).major ?? ""),
        grade: String((i as any).grade ?? ""),
        version: String((i as any).version ?? ""),
        remark: String((i as any).remark ?? ""),
        courses: [],
        courseCount,
        totalCredits,
        createdAt: normalizeBackendTime((i as any).createdAt || (i as any).created_at) || formatTime(),
        jsonContent
      } satisfies TrainingPlanRow;
    });
  } finally {
    listLoading.value = false;
  }
};

const normalizeText = (text: string) => {
  return text
    .replace(/\r/g, "\n")
    .split("\n")
    .map(l => l.replace(/\s+/g, " ").trim())
    .filter(Boolean);
};

const stripCourseCode = (s: string) => {
  const tokens = s.split(" ").filter(Boolean);
  const filtered = tokens.filter(t => !/[A-Za-z]/.test(t) || !/\d/.test(t) || t.length < 6);
  return filtered.join(" ").trim();
};

const parseOcrToTrainingPlan = (text: string) => {
  const lines = normalizeText(text);
  const courses: TrainingCourseRow[] = [];
  const unparsed: string[] = [];

  for (const line of lines) {
    const line1 = line.replace(/[|丨]/g, " ").replace(/\s+/g, " ").trim();

    const m1 = line1.match(/^(.*?)[ ]+([A-Za-z0-9]{6,})[ ]+(\d+(?:\.\d+)?)[ ]+([1-8])$/);
    if (m1) {
      const name = m1[1].replace(/\s+/g, "").trim();
      courses.push({
        id: genId(),
        category: "",
        courseName: name,
        credits: Number(m1[3]) || 0,
        offeredAt: m1[4] ?? ""
      });
      continue;
    }

    const m2 = line1.match(/^(.*?)[ ]+(\d+(?:\.\d+)?)[ ]+([1-8])$/);
    if (m2) {
      const prefix = stripCourseCode(m2[1] ?? "");
      const name = prefix.replace(/\s+/g, "").trim();
      if (name) {
        courses.push({
          id: genId(),
          category: "",
          courseName: name,
          credits: Number(m2[2]) || 0,
          offeredAt: m2[3] ?? ""
        });
        continue;
      }
    }

    unparsed.push(line1);
  }

  const majorCandidates = unparsed
    .map(l => l.replace(/\s+/g, "").trim())
    .filter(l => l && !/\d/.test(l) && !/[A-Za-z]/.test(l) && l.length <= 16 && l.length >= 4);

  const major = majorCandidates.sort((a, b) => b.length - a.length)[0] ?? "";

  return {
    major,
    courses
  };
};

const onOcrImageChange = async (file: UploadFile) => {
  const raw = file.raw as File | undefined;
  if (!raw) return;
  if (!raw.type.startsWith("image/")) {
    ElMessage.error("请选择图片文件");
    return;
  }
  ocrLoading.value = true;
  ocrProgress.value = 0;
  ocrText.value = "";
  ocrParsedMajor.value = "";
  ocrParsedCourses.value = [];
  try {
    const { default: Tesseract } = await import("tesseract.js");
    const res = await Tesseract.recognize(raw, "chi_sim+eng", {
      logger: m => {
        if (m.status === "recognizing text" && typeof m.progress === "number") {
          ocrProgress.value = Math.round(m.progress * 100);
        }
      }
    });
    const text = (res.data?.text ?? "").trim();
    ocrText.value = text;
    ocrTextVisible.value = true;
    const parsed = parseOcrToTrainingPlan(text);
    ocrParsedMajor.value = parsed.major;
    ocrParsedCourses.value = parsed.courses;
    if (!parsed.courses.length) ElMessage.warning("未识别到课程行，可在“识别文本”里手动调整后再应用");
    else ElMessage.success(`识别完成：${parsed.courses.length} 门课程`);
  } catch (e: any) {
    ElMessage.error(`识别失败：${e?.message ?? "未知错误"}`);
  } finally {
    ocrLoading.value = false;
    ocrProgress.value = 0;
  }
};

const applyOcrResult = () => {
  if (!ocrText.value) return;
  const parsed = parseOcrToTrainingPlan(ocrText.value);
  if (parsed.major) planForm.major = parsed.major;
  planCourses.value = parsed.courses;
  ElMessage.success("已填充到表单，可继续手动修改");
};

const totalCredits = computed(() => {
  const sum = planCourses.value.reduce((acc, cur) => acc + (Number(cur.credits) || 0), 0);
  return Number.isFinite(sum) ? Number(sum.toFixed(2)) : 0;
});

const addCourseRow = () => {
  planCourses.value.push({
    id: genId(),
    category: "",
    courseName: "",
    credits: 0,
    offeredAt: ""
  });
};

const removeCourseRow = (id: string) => {
  const idx = planCourses.value.findIndex(i => i.id === id);
  if (idx === -1) return;
  planCourses.value.splice(idx, 1);
};

const fillDemoCourses = () => {
  if (planCourses.value.length > 0) return;
  planCourses.value = [
    { id: genId(), category: "部类基础课", courseName: "高等数学", credits: 5, offeredAt: "1" },
    { id: genId(), category: "部类核心课", courseName: "程序设计基础", credits: 4, offeredAt: "2" },
    { id: genId(), category: "专业核心课", courseName: "数据结构", credits: 4, offeredAt: "3" }
  ];
};

const normalizeCourses = (courses: TrainingCourseRow[]) => {
  return courses
    .map(c => ({
      ...c,
      category: c.category?.trim?.() ?? "",
      courseName: c.courseName?.trim?.() ?? "",
      credits: Number(c.credits) || 0,
      offeredAt: c.offeredAt ?? ""
    }))
    .filter(c => c.courseName.length > 0);
};

const editPlanRow = async (id: number) => {
  const row = trainingPlanList.value.find(i => i.id === id);
  if (!row) return;
  try {
    const payload = JSON.parse(row.jsonContent || "{}");
    const coursesRaw = Array.isArray(payload?.courses) ? payload.courses : [];
    planForm.major = String(payload?.major ?? row.major ?? "");
    planForm.grade = String(payload?.grade ?? row.grade ?? "");
    planForm.version = String(payload?.version ?? row.version ?? "v1.0");
    planForm.remark = String(payload?.remark ?? row.remark ?? "");
    planCourses.value = coursesRaw.map((c: any) => ({
      id: genId(),
      category: String(c?.category ?? ""),
      courseName: String(c?.courseName ?? c?.name ?? ""),
      credits: Number(c?.credits ?? 0),
      offeredAt: String(c?.offeredAt ?? "")
    }));
    editingPlanId.value = id;
    ElMessage.success("已加载到表单，可修改后保存");
  } catch {
    ElMessage.error("解析 JSON 失败");
  }
};

const cancelEditPlan = () => {
  editingPlanId.value = null;
  resetPlan();
};

const saveTrainingPlan = async () => {
  if (!planFormRef.value) return;
  await planFormRef.value.validate(valid => {
    if (!valid) return;
    const courses = normalizeCourses(planCourses.value);
    if (courses.length === 0) {
      ElMessage.error("请至少填写 1 门课程名称");
      return;
    }
    const totalCredits = Number(courses.reduce((acc, cur) => acc + (Number(cur.credits) || 0), 0).toFixed(2));
    const payload = {
      major: planForm.major,
      grade: planForm.grade,
      version: planForm.version,
      remark: planForm.remark,
      courses
    };
    saveTrainingPlanApi({
      id: editingPlanId.value ?? undefined,
      major: planForm.major,
      grade: planForm.grade,
      version: planForm.version,
      remark: planForm.remark,
      courseCount: courses.length,
      totalCredits,
      jsonContent: JSON.stringify(payload)
    })
      .then(() => {
        ElMessage.success(editingPlanId.value === null ? "已保存培养方案" : "已更新培养方案");
        editingPlanId.value = null;
        planForm.major = "";
        planForm.grade = "";
        planForm.version = "v1.0";
        planForm.remark = "";
        planCourses.value = [];
        refreshList();
      })
      .catch((e: any) => ElMessage.error(e?.message ?? "保存失败"));
  });
};

const exportPlanJson = () => {
  const data = trainingPlanList.value
    .map(p => {
      const raw = p.jsonContent;
      if (raw) {
        try {
          return JSON.parse(raw);
        } catch {
          return raw;
        }
      }
      return {
        major: p.major,
        grade: p.grade,
        version: p.version,
        remark: p.remark,
        courses: [],
        courseCount: p.courseCount,
        totalCredits: p.totalCredits,
        createdAt: p.createdAt
      };
    })
    .filter(Boolean);
  jsonDialogValue.value = JSON.stringify(data, null, 2);
  jsonDialogVisible.value = true;
};

const copyJson = async () => {
  const s = String(jsonDialogValue.value ?? "");
  if (!s) return;
  if ((window as any).isSecureContext && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(s);
      ElMessage.success("已复制");
      return;
    } catch {}
  }
  try {
    const textarea = document.createElement("textarea");
    textarea.value = s;
    textarea.setAttribute("readonly", "true");
    textarea.style.position = "fixed";
    textarea.style.left = "-9999px";
    textarea.style.top = "0";
    textarea.style.opacity = "0";
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();
    textarea.setSelectionRange(0, textarea.value.length);
    const ok = document.execCommand("copy");
    document.body.removeChild(textarea);
    if (ok) ElMessage.success("已复制");
    else ElMessage.error("复制失败，请手动复制");
  } catch {
    ElMessage.error("复制失败，请手动复制");
  }
};

const viewPlanRow = async (id: number) => {
  const row = trainingPlanList.value.find(i => i.id === id);
  if (!row) return;
  try {
    const payload = JSON.parse(row.jsonContent || "{}");
    const courses = Array.isArray(payload?.courses) ? payload.courses : [];
    planDetail.value = {
      id,
      major: String(payload?.major ?? row.major),
      grade: String(payload?.grade ?? row.grade),
      version: String(payload?.version ?? row.version),
      remark: String(payload?.remark ?? row.remark),
      courses,
      courseCount: Number(row.courseCount ?? courses.length ?? 0),
      totalCredits: Number(row.totalCredits ?? 0),
      createdAt: row.createdAt
    };
    planDetailVisible.value = true;
  } catch {
    ElMessage.error("解析 JSON 失败");
  }
};

const removePlanRow = async (id: number) => {
  try {
    await ElMessageBox.confirm("确认删除该培养方案吗？", "提示", { type: "warning" });
  } catch {
    return;
  }
  try {
    await deleteTrainingPlan(id);
    ElMessage.success("已删除");
    await refreshList();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "删除失败");
  }
};

const resetPlan = () => {
  planForm.major = "";
  planForm.grade = "";
  planForm.version = "v1.0";
  planForm.remark = "";
  planCourses.value = [];
  ElMessage.success("已重置");
};

onMounted(() => {
  refreshList();
});
</script>

<style scoped lang="scss">
.training-plan {
  .ocr-bar {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
  }
  .ocr-progress {
    width: 260px;
  }
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
  .flx-between {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .summary {
    color: var(--el-text-color-secondary);
  }
  .detail {
    .detail-meta {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 8px 12px;
      margin-bottom: 12px;
    }
    .label {
      color: var(--el-text-color-secondary);
    }
  }
}
</style>
