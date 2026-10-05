<template>
  <div v-if="loading" class="data-skeleton" aria-busy="true"><el-skeleton animated :rows="rows" /></div>
  <div v-else-if="error" class="data-error" role="alert">
    <el-icon><Warning /></el-icon>
    <div>
      <strong>暂时无法获取{{ label }}</strong>
      <p>请检查服务连接后重试。已有操作和数据不会被更改。</p>
    </div>
    <el-button @click="$emit('retry')">重新加载</el-button>
  </div>
</template>
<script setup lang="ts">
withDefaults(defineProps<{ loading: boolean; error: boolean; label: string; rows?: number }>(), { rows: 4 });
defineEmits(["retry"]);
</script>
<style scoped>
.data-skeleton {
  padding: 24px 12px;
}
.data-error {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 20px;
  margin: 16px 0;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 10px;
  font-size: 13px;
}
.data-error .el-icon {
  font-size: 22px;
  color: #c47920;
}
.data-error p {
  color: var(--product-muted);
  margin: 6px 0;
}
.data-error .el-button {
  margin-left: auto;
}
</style>
