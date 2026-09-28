<template>
  <div class="student-information content-box">
    <div class="card">
      <div class="header">
        <div class="title">学生信息管理（已对接后端）</div>
        <div class="actions">
          <el-button plain @click="refreshList">刷新列表</el-button>
          <el-button type="primary" plain @click="downloadTemplate">下载 Excel 模板</el-button>
          <el-upload
            :auto-upload="false"
            :multiple="false"
            :limit="1"
            accept=".xls,.xlsx,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            :show-file-list="false"
            :on-change="onExcelChange"
          >
            <el-button type="primary" :loading="importLoading">Excel 导入</el-button>
          </el-upload>
          <el-button type="primary" plain :disabled="students.length === 0" @click="exportJson">导出 JSON</el-button>
        </div>
      </div>
      <el-alert
        title="已对接后端：手动新增与 Excel 批量导入会写入数据库（t_student + t_user），并从后端拉取列表。"
        type="info"
        :closable="false"
        class="mb16"
      />

      <el-row :gutter="16">
        <el-col :xs="24" :md="8">
          <div class="card inner-card">
            <div class="inner-title">手动录入</div>
            <el-form ref="formRef" :model="form" :rules="rules" label-width="72px" label-suffix=" :" size="small">
              <el-row :gutter="12" class="compact-form">
                <el-col :span="12">
                  <el-form-item label="学号" prop="studentNo">
                    <el-input v-model.trim="form.studentNo" placeholder="20260001" clearable :disabled="isEdit" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="姓名" prop="name">
                    <el-input v-model.trim="form.name" placeholder="张三" clearable />
                  </el-form-item>
                </el-col>
                <el-col v-if="form.roleCode !== 'admin'" :span="12">
                  <el-form-item label="身份证" prop="idCardNo">
                    <el-input v-model.trim="form.idCardNo" placeholder="18 位身份证号" clearable />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="性别" prop="gender">
                    <el-select v-model="form.gender" placeholder="请选择">
                      <el-option label="男" value="男" />
                      <el-option label="女" value="女" />
                      <el-option label="未知" value="未知" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="民族" prop="ethnicity">
                    <el-input v-model.trim="form.ethnicity" placeholder="汉族" clearable />
                  </el-form-item>
                </el-col>
                <el-col v-if="form.roleCode !== 'admin'" :span="12">
                  <el-form-item label="年级" prop="grade">
                    <el-input v-model.trim="form.grade" placeholder="2026" clearable />
                  </el-form-item>
                </el-col>
                <el-col v-if="form.roleCode !== 'admin'" :span="12">
                  <el-form-item label="层次" prop="educationLevel">
                    <el-select v-model="form.educationLevel" placeholder="请选择">
                      <el-option label="本科" value="本科" />
                      <el-option label="硕士" value="硕士" />
                      <el-option label="博士" value="博士" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col v-if="form.roleCode !== 'admin'" :span="12">
                  <el-form-item label="班级" prop="className">
                    <el-input v-model.trim="form.className" placeholder="计科2601" clearable />
                  </el-form-item>
                </el-col>
                <el-col v-if="form.roleCode !== 'admin'" :span="24">
                  <el-form-item label="专业" prop="major">
                    <el-input v-model.trim="form.major" placeholder="计算机科学与技术" clearable />
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item label="联系" prop="contact">
                    <el-input v-model.trim="form.contact" placeholder="手机号或邮箱" clearable />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="权限" prop="roleCode">
                    <el-select v-model="form.roleCode" placeholder="请选择">
                      <el-option label="管理员" value="admin" />
                      <el-option label="学生" value="student" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="状态" prop="status">
                    <el-select v-model="form.status" placeholder="请选择">
                      <el-option label="正常（1）" :value="1" />
                      <el-option label="禁用（0）" :value="0" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item label="密码" prop="password">
                    <el-input
                      v-model.trim="form.password"
                      type="password"
                      :placeholder="isEdit ? '留空表示不修改密码' : '请输入初始密码（6-72 位）'"
                      show-password
                      clearable
                      autocomplete="new-password"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item class="form-actions">
                    <el-button type="primary" @click="submitForm">{{ isEdit ? "保存修改" : "新增" }}</el-button>
                    <el-button @click="resetForm">重置</el-button>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>
          </div>
        </el-col>

        <el-col :xs="24" :md="16" class="right-panel">
          <div class="card inner-card">
            <div class="inner-title">学生列表</div>
            <div class="toolbar mb12">
              <el-input v-model.trim="keyword" placeholder="搜索：学号/姓名/班级/专业" clearable class="w280" />
            </div>
            <el-table :data="filtered" row-key="studentNo" height="610" v-loading="listLoading" @row-dblclick="editRow">
              <el-table-column prop="studentNo" label="学号" width="140" />
              <el-table-column prop="name" label="姓名" width="120" />
              <el-table-column prop="gender" label="性别" width="80" />
              <el-table-column prop="ethnicity" label="民族" width="110" show-overflow-tooltip />
              <el-table-column prop="className" label="班级" width="120" show-overflow-tooltip />
              <el-table-column prop="major" label="专业" min-width="160" show-overflow-tooltip />
              <el-table-column prop="grade" label="年级" width="90" />
              <el-table-column prop="contact" label="联系方式" min-width="160" show-overflow-tooltip />
              <el-table-column prop="roleCode" label="权限" width="110" />
              <el-table-column prop="status" label="状态" width="90" />
              <el-table-column prop="updatedAt" label="更新时间" width="170" />
              <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="editRow(row)">修改</el-button>
                  <el-button link type="danger" @click="removeRow(row.studentNo)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="mt12 summary">
              <span>总数：{{ students.length }}</span>
              <span class="ml12">当前筛选：{{ filtered.length }}</span>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <el-dialog v-model="jsonVisible" title="导出内容（JSON）" width="820px">
      <el-input v-model="jsonValue" type="textarea" :rows="18" />
      <template #footer>
        <el-button @click="jsonVisible = false">关闭</el-button>
        <el-button type="primary" @click="copyJson">复制</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="collegeStudentInformation">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules, UploadFile } from "element-plus";
import * as XLSX from "xlsx";
import { deleteStudent, importStudents, listStudents } from "@/api/modules/student";

type Gender = "男" | "女" | "未知";

type StudentRow = {
  studentNo: string;
  name: string;
  idCardNo: string;
  gender: Gender;
  ethnicity: string;
  className: string;
  major: string;
  grade: string;
  educationLevel: string;
  contact: string;
  password: string;
  roleCode: "student" | "admin";
  status: 0 | 1;
  wechatOpenid: string;
  updatedAt: string;
};

const formatTime = (d = new Date()) => {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const students = ref<StudentRow[]>([]);
const keyword = ref("");
const listLoading = ref(false);

const normalizeGender = (v: any): Gender => {
  const s = String(v ?? "").trim();
  if (s === "男" || s === "女") return s;
  return "未知";
};

const normalizeText = (v: any) => String(v ?? "").trim();

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase();
  if (!k) return students.value;
  return students.value.filter(s => {
    const hay = `${s.studentNo} ${s.name} ${s.className} ${s.major} ${s.contact}`.toLowerCase();
    return hay.includes(k);
  });
});

const formRef = ref<FormInstance>();
const isEdit = ref(false);
const editKey = ref<string>("");

const form = reactive<StudentRow>({
  studentNo: "",
  name: "",
  idCardNo: "",
  gender: "未知",
  ethnicity: "",
  className: "",
  major: "",
  grade: "",
  educationLevel: "本科",
  contact: "",
  password: "",
  roleCode: "student",
  status: 1,
  wechatOpenid: "",
  updatedAt: ""
});

const rules: FormRules = reactive({
  studentNo: [{ required: true, message: "请填写学号", trigger: "blur" }],
  name: [{ required: true, message: "请填写姓名", trigger: "blur" }],
  className: [
    {
      validator: (_rule, value, callback) => {
        if (form.roleCode === "admin") return callback();
        if (!String(value ?? "").trim()) return callback(new Error("请填写班级"));
        callback();
      },
      trigger: "blur"
    }
  ],
  major: [
    {
      validator: (_rule, value, callback) => {
        if (form.roleCode === "admin") return callback();
        if (!String(value ?? "").trim()) return callback(new Error("请填写专业"));
        callback();
      },
      trigger: "blur"
    }
  ],
  grade: [
    {
      validator: (_rule, value, callback) => {
        if (form.roleCode === "admin") return callback();
        if (!String(value ?? "").trim()) return callback(new Error("请填写年级"));
        callback();
      },
      trigger: "blur"
    }
  ],
  password: [
    {
      validator: (_rule, value, callback) => {
        const password = String(value ?? "");
        if (isEdit.value && !password) return callback();
        if (!password) return callback(new Error("请设置初始密码"));
        if (password.length < 6 || password.length > 72) {
          return callback(new Error("密码长度必须在 6 到 72 位之间"));
        }
        callback();
      },
      trigger: ["blur", "change"]
    }
  ]
});

const resetForm = () => {
  isEdit.value = false;
  editKey.value = "";
  form.studentNo = "";
  form.name = "";
  form.idCardNo = "";
  form.gender = "未知";
  form.ethnicity = "";
  form.className = "";
  form.major = "";
  form.grade = "";
  form.educationLevel = "本科";
  form.contact = "";
  form.password = "";
  form.roleCode = "student";
  form.status = 1;
  form.wechatOpenid = "";
  form.updatedAt = "";
};

const normalizeBackendTime = (v: any) => {
  const s = String(v ?? "").trim();
  if (!s) return "";
  return s.includes("T") ? s.replace("T", " ").slice(0, 16) : s;
};

const refreshList = async () => {
  listLoading.value = true;
  try {
    const res = await listStudents({ keyword: keyword.value.trim() || undefined });
    students.value = (res.data || []).map(i => {
      const status = Number((i as any).status);
      const roleCodeRaw = normalizeText((i as any).roleCode || (i as any).role_code);
      const roleCode = roleCodeRaw === "admin" || roleCodeRaw === "管理员" ? "admin" : "student";
      return {
        studentNo: normalizeText((i as any).studentNo || (i as any).student_no),
        name: normalizeText((i as any).name),
        idCardNo: normalizeText((i as any).idCardNo || (i as any).id_card_no),
        gender: normalizeGender((i as any).gender),
        ethnicity: normalizeText((i as any).ethnicity || (i as any).nation),
        className: normalizeText((i as any).className || (i as any).class_name),
        major: normalizeText((i as any).major),
        grade: normalizeText((i as any).grade),
        educationLevel: normalizeText((i as any).educationLevel || (i as any).education_level) || "本科",
        contact: normalizeText((i as any).contact),
        password: "",
        roleCode,
        status: status === 0 ? 0 : 1,
        wechatOpenid: "",
        updatedAt: normalizeBackendTime((i as any).updatedAt || (i as any).updated_at) || formatTime()
      } satisfies StudentRow;
    });
  } catch (e: any) {
    students.value = [];
    ElMessage.error(e?.message ?? "学生列表读取失败");
  } finally {
    listLoading.value = false;
  }
};

const submitForm = async () => {
  if (!formRef.value) return;
  await formRef.value.validate(valid => {
    if (!valid) return;
    const now = formatTime();
    const row: StudentRow = {
      studentNo: normalizeText(form.studentNo),
      name: normalizeText(form.name),
      idCardNo: normalizeText(form.idCardNo),
      gender: normalizeGender(form.gender),
      ethnicity: normalizeText(form.ethnicity),
      className: normalizeText(form.className),
      major: normalizeText(form.major),
      grade: normalizeText(form.grade),
      educationLevel: normalizeText(form.educationLevel) || "本科",
      contact: normalizeText(form.contact),
      password: normalizeText(form.password),
      roleCode: form.roleCode,
      status: form.status,
      wechatOpenid: normalizeText(form.wechatOpenid),
      updatedAt: now
    };
    if (!row.studentNo) return;
    saveToBackend(row);
  });
};

const saveToBackend = async (row: StudentRow) => {
  try {
    const res = await importStudents([
      {
        studentNo: row.studentNo,
        name: row.name,
        idCardNo: row.roleCode === "admin" ? "" : row.idCardNo,
        gender: row.gender,
        ethnicity: row.ethnicity,
        className: row.roleCode === "admin" ? "" : row.className,
        major: row.roleCode === "admin" ? "" : row.major,
        grade: row.roleCode === "admin" ? "" : row.grade,
        educationLevel: row.roleCode === "admin" ? "" : row.educationLevel,
        contact: row.contact,
        password: row.password,
        roleCode: row.roleCode,
        status: row.status,
        wechatOpenid: row.wechatOpenid
      }
    ]);
    const inserted = Number((res.data as any)?.inserted ?? 0);
    const updated = Number((res.data as any)?.updated ?? 0);
    if (row.roleCode === "admin") {
      ElMessage.success("管理员账号已保存（不会出现在学生列表）");
    } else {
      ElMessage.success(inserted > 0 ? "已新增" : updated > 0 ? "已保存" : "已提交");
    }
    resetForm();
    await refreshList();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "保存失败");
  }
};

const editRow = (row: StudentRow) => {
  isEdit.value = true;
  editKey.value = row.studentNo;
  form.studentNo = row.studentNo;
  form.name = row.name;
  form.idCardNo = row.idCardNo;
  form.gender = row.gender;
  form.ethnicity = row.ethnicity;
  form.className = row.className;
  form.major = row.major;
  form.grade = row.grade;
  form.educationLevel = row.educationLevel;
  form.contact = row.contact;
  form.password = "";
  form.roleCode = row.roleCode;
  form.status = row.status;
  form.wechatOpenid = row.wechatOpenid;
  form.updatedAt = row.updatedAt;
};

const removeRow = async (studentNo: string) => {
  const row = students.value.find(s => s.studentNo === studentNo);
  if (!row) return;
  try {
    await ElMessageBox.confirm(`确认删除学号 ${studentNo} 的记录吗？`, "提示", { type: "warning" });
  } catch {
    return;
  }
  try {
    await deleteStudent(studentNo);
    ElMessage.success("已删除");
    if (isEdit.value && editKey.value === studentNo) resetForm();
    await refreshList();
  } catch (e: any) {
    ElMessage.error(e?.message ?? "删除失败");
  }
};

const importLoading = ref(false);

const headerMap: Record<string, keyof StudentRow> = {
  学号: "studentNo",
  姓名: "name",
  身份证号: "idCardNo",
  性别: "gender",
  民族: "ethnicity",
  培养层次: "educationLevel",
  班级: "className",
  专业: "major",
  年级: "grade",
  联系方式: "contact",
  联系: "contact",
  电话: "contact",
  手机号: "contact",
  联系电话: "contact",
  邮箱: "contact",
  密码: "password",
  "初始密码（新增必填，6-72位）": "password",
  权限: "roleCode",
  角色: "roleCode",
  状态: "status",
  账号状态: "status"
};

const toStudentRow = (obj: Record<string, any>): StudentRow | null => {
  const get = (key: keyof StudentRow) => {
    const v = obj[key] ?? obj[String(key)] ?? "";
    return v;
  };
  const studentNo = normalizeText(get("studentNo"));
  const name = normalizeText(get("name"));
  if (!studentNo || !name) return null;
  const rawRole = normalizeText(get("roleCode"));
  const roleCode = rawRole === "admin" || rawRole === "管理员" ? "admin" : "student";
  const rawStatus = String(get("status") ?? "").trim();
  const status = rawStatus === "0" || rawStatus === "禁用" ? 0 : 1;
  return {
    studentNo,
    name,
    idCardNo: normalizeText(get("idCardNo")),
    gender: normalizeGender(get("gender")),
    ethnicity: normalizeText(get("ethnicity")),
    className: normalizeText(get("className")),
    major: normalizeText(get("major")),
    grade: normalizeText(get("grade")),
    educationLevel: normalizeText(get("educationLevel")) || "本科",
    contact: normalizeText(get("contact")),
    password: normalizeText(get("password")),
    roleCode,
    status,
    wechatOpenid: "",
    updatedAt: formatTime()
  };
};

const parseExcel = async (file: File) => {
  const buf = await file.arrayBuffer();
  const wb = XLSX.read(buf, { type: "array" });
  const sheetName = wb.SheetNames[0];
  const ws = wb.Sheets[sheetName];
  const raw = XLSX.utils.sheet_to_json<Record<string, any>>(ws, { defval: "" });
  const mapped = raw.map(r => {
    const o: Record<string, any> = {};
    for (const [k, v] of Object.entries(r)) {
      const key = String(k).trim();
      const mappedKey = headerMap[key];
      if (!mappedKey) continue;
      if (mappedKey !== "contact") {
        o[mappedKey] = v;
        continue;
      }
      const next = normalizeText(v);
      const prev = normalizeText(o.contact);
      if (!prev) {
        o.contact = next;
        continue;
      }
      const prevIsEmail = prev.includes("@");
      const nextIsEmail = next.includes("@");
      if (prevIsEmail && !nextIsEmail) {
        o.contact = next;
      }
    }
    return o;
  });
  const invalidRows: number[] = [];
  const rows = mapped
    .map((item, index) => {
      const row = toStudentRow(item);
      if (!row) invalidRows.push(index + 2);
      return row;
    })
    .filter(Boolean) as StudentRow[];
  if (invalidRows.length > 0) {
    const sample = invalidRows.slice(0, 5).join("、");
    throw new Error(`Excel 第 ${sample}${invalidRows.length > 5 ? " 等" : ""} 行缺少学号或姓名`);
  }
  return rows;
};

const onExcelChange = async (file: UploadFile) => {
  const raw = file.raw as File | undefined;
  if (!raw) return;
  const isExcel =
    raw.type === "application/vnd.ms-excel" ||
    raw.type === "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
    raw.name.toLowerCase().endsWith(".xls") ||
    raw.name.toLowerCase().endsWith(".xlsx");
  if (!isExcel) {
    ElMessage.error("请上传 xls/xlsx 文件");
    return;
  }
  importLoading.value = true;
  try {
    const rows = await parseExcel(raw);
    if (rows.length === 0) {
      ElMessage.warning("未解析到有效行，请确认表头与内容");
      return;
    }
    const invalidPasswords = rows.filter(row => row.password && (row.password.length < 6 || row.password.length > 72));
    if (invalidPasswords.length > 0) {
      const sample = invalidPasswords
        .slice(0, 5)
        .map(row => row.studentNo)
        .join("、");
      ElMessage.error(`密码必须为 6-72 位：${sample}${invalidPasswords.length > 5 ? " 等" : ""}`);
      return;
    }
    const res = await importStudents(
      rows.map(r => ({
        studentNo: r.studentNo,
        name: r.name,
        idCardNo: r.roleCode === "admin" ? "" : r.idCardNo,
        gender: r.gender,
        ethnicity: r.ethnicity,
        className: r.className,
        major: r.major,
        grade: r.grade,
        educationLevel: r.roleCode === "admin" ? "" : r.educationLevel,
        contact: r.contact,
        password: r.password,
        roleCode: r.roleCode,
        status: r.status,
        wechatOpenid: r.wechatOpenid
      }))
    );
    const inserted = Number((res.data as any)?.inserted ?? 0);
    const updated = Number((res.data as any)?.updated ?? 0);
    ElMessage.success(`导入成功：新增 ${inserted}，更新 ${updated}`);
    resetForm();
    await refreshList();
  } catch (e: any) {
    ElMessage.error(`导入失败：${e?.message ?? "未知错误"}`);
  } finally {
    importLoading.value = false;
  }
};

const downloadTemplate = () => {
  const header = [
    "学号",
    "姓名",
    "身份证号",
    "性别",
    "民族",
    "班级",
    "专业",
    "年级",
    "培养层次",
    "联系方式",
    "初始密码（新增必填，6-72位）",
    "权限",
    "账号状态"
  ];
  const example = [
    "20240001",
    "张三",
    "",
    "男",
    "汉族",
    "计科2401",
    "计算机科学与技术",
    "2024",
    "本科",
    "13800000000",
    "",
    "student",
    1
  ];
  const ws = XLSX.utils.aoa_to_sheet([header, example]);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, "学生信息");
  const data = XLSX.write(wb, { bookType: "xlsx", type: "array" });
  const blob = new Blob([data], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "学生信息模板.xlsx";
  a.click();
  URL.revokeObjectURL(url);
};

const jsonVisible = ref(false);
const jsonValue = ref("");

const exportJson = () => {
  jsonValue.value = JSON.stringify(students.value, null, 2);
  jsonVisible.value = true;
};

const copyText = async (text: string) => {
  const s = String(text ?? "");
  if (!s) return false;
  if ((window as any).isSecureContext && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(s);
      return true;
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
    return ok;
  } catch {
    return false;
  }
};

const copyJson = async () => {
  const ok = await copyText(jsonValue.value);
  if (ok) ElMessage.success("已复制");
  else ElMessage.error("复制失败，请手动复制");
};

onMounted(() => {
  refreshList();
});
</script>

<style scoped lang="scss">
.student-information {
  .right-panel {
    zoom: 0.8;
  }

  @supports not (zoom: 1) {
    .right-panel {
      transform: scale(0.8);
      transform-origin: 0 0;
    }
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
  .toolbar {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
  }
  .compact-form {
    :deep(.el-form-item) {
      margin-bottom: 10px;
    }
  }
  .form-actions {
    margin-bottom: 0;
  }
  .w280 {
    width: 240px;
  }
  .summary {
    color: var(--el-text-color-secondary);
  }
}
</style>
