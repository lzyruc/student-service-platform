<template>
  <el-dialog v-model="dialogVisible" title="个人信息" width="500px" draggable>
    <el-skeleton v-if="loading" animated :rows="5" />
    <el-descriptions v-else :column="1" border>
      <el-descriptions-item label="用户名">{{ me?.username || "—" }}</el-descriptions-item>
      <el-descriptions-item label="角色">{{ roleLabel }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ statusLabel }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ formatTime(me?.created_at) || "—" }}</el-descriptions-item>
      <el-descriptions-item label="更新时间">{{ formatTime(me?.updated_at) || "—" }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="dialogVisible = false">确认</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAdminMeApi, type AdminMeResponse } from "@/api/modules/login";

const dialogVisible = ref(false);
const loading = ref(false);
const me = ref<AdminMeResponse | null>(null);

const formatTime = (v: any) => {
  const s = String(v ?? "").trim();
  if (!s) return "";
  return s.includes("T") ? s.replace("T", " ").slice(0, 19) : s;
};

const roleLabel = computed(() => {
  const role = String(me.value?.role_code ?? "").trim();
  if (!role) return "—";
  if (role === "admin") return "管理员";
  if (role === "student") return "学生";
  return role;
});

const statusLabel = computed(() => {
  const s = Number(me.value?.status);
  if (Number.isNaN(s)) return "—";
  if (s === 1) return "正常";
  if (s === 0) return "禁用";
  return String(s);
});

const openDialog = async () => {
  dialogVisible.value = true;
  loading.value = true;
  try {
    const res = await getAdminMeApi();
    me.value = res.data ?? null;
  } catch (e: any) {
    ElMessage.error(e?.message ?? "获取用户信息失败");
  } finally {
    loading.value = false;
  }
};

defineExpose({ openDialog });
</script>
