<template>
  <el-dialog v-model="dialogVisible" title="修改密码" width="500px" draggable>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" label-suffix=" :">
      <el-form-item label="原密码" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确认</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { useRouter } from "vue-router";
import { LOGIN_URL } from "@/config";
import { changeAdminPasswordApi } from "@/api/modules/login";
import { useUserStore } from "@/stores/modules/user";

const dialogVisible = ref(false);
const submitting = ref(false);
const formRef = ref<FormInstance>();
const router = useRouter();
const userStore = useUserStore();

const form = reactive({
  oldPassword: "",
  newPassword: "",
  confirmPassword: ""
});

const rules: FormRules = reactive({
  oldPassword: [{ required: true, message: "请输入原密码", trigger: "blur" }],
  newPassword: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    { min: 6, max: 72, message: "密码长度必须在 6 到 72 位之间", trigger: "blur" }
  ],
  confirmPassword: [
    { required: true, message: "请再次输入新密码", trigger: "blur" },
    {
      validator: (_rule, value, callback) => {
        if (String(value ?? "") !== String(form.newPassword ?? "")) return callback(new Error("两次输入的新密码不一致"));
        callback();
      },
      trigger: "blur"
    }
  ]
});

const resetForm = () => {
  form.oldPassword = "";
  form.newPassword = "";
  form.confirmPassword = "";
  formRef.value?.clearValidate();
};

const openDialog = () => {
  resetForm();
  dialogVisible.value = true;
};

const submit = async () => {
  if (!formRef.value) return;
  await formRef.value.validate(async valid => {
    if (!valid) return;
    if (submitting.value) return;
    submitting.value = true;
    try {
      await changeAdminPasswordApi({
        oldPassword: String(form.oldPassword ?? ""),
        newPassword: String(form.newPassword ?? "")
      });
      ElMessage.success("密码修改成功，请重新登录");
      dialogVisible.value = false;
      userStore.setToken("");
      await router.replace(LOGIN_URL);
    } catch (e: any) {
      ElMessage.error(e?.message ?? "密码修改失败");
    } finally {
      submitting.value = false;
    }
  });
};

defineExpose({ openDialog });
</script>
