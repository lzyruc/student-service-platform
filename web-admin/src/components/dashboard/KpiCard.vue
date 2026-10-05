<template>
  <article class="kpi">
    <div class="kpi-top">
      <span>{{ label }}</span
      ><span :class="['kpi-icon', tone]"
        ><el-icon><component :is="icon" /></el-icon
      ></span>
    </div>
    <div v-if="loading">
      <el-skeleton animated
        ><template #template><el-skeleton-item variant="text" style="width: 90px; height: 38px; margin: 14px 0" /></template
      ></el-skeleton>
    </div>
    <div v-else class="kpi-value">
      {{ value === null ? "—" : value.toLocaleString() }}<span>{{ unit }}</span>
    </div>
    <div class="product-caption">{{ caption }}</div>
  </article>
</template>
<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string;
    icon: string;
    value: number | null;
    caption: string;
    loading: boolean;
    tone?: string;
    unit?: string;
  }>(),
  { tone: "blue", unit: "" }
);
</script>
<style scoped>
.kpi {
  padding: 22px;
  background: var(--product-surface);
  border: 1px solid var(--product-line);
  border-radius: 12px;
  box-shadow: var(--product-shadow);
  transition:
    transform 0.18s,
    border-color 0.18s;
}
.kpi:hover {
  transform: translateY(-2px);
  border-color: #ccd5f5;
}
.kpi-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--product-muted);
}
.kpi-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #eef2ff;
  color: #425bd8;
}
.kpi-icon.orange {
  background: #fff4e8;
  color: #ba7425;
}
.kpi-icon.green {
  background: #eaf7f1;
  color: #26876a;
}
.kpi-value {
  font-size: 34px;
  font-weight: 650;
  letter-spacing: -1px;
  margin: 12px 0 9px;
  font-variant-numeric: tabular-nums;
}
.kpi-value span {
  font-size: 12px;
  font-weight: 400;
  color: var(--product-muted);
  margin-left: 8px;
  letter-spacing: 0;
}
</style>
