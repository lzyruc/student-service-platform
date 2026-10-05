<template>
  <button class="capability" @click="$emit('open')">
    <div class="cap-icon">
      <el-icon><component :is="icon" /></el-icon>
    </div>
    <div>
      <div class="cap-title">
        {{ title }}<span>{{ badge }}</span>
      </div>
      <p>{{ description }}</p>
    </div>
    <el-icon class="cap-arrow"><ArrowRight /></el-icon>
  </button>
</template>
<script setup lang="ts">
defineProps<{ title: string; description: string; icon: string; badge: string }>();
defineEmits(["open"]);
</script>
<style scoped>
.capability {
  display: flex;
  align-items: center;
  gap: 14px;
  text-align: left;
  width: 100%;
  padding: 20px;
  border: 1px solid var(--product-line);
  border-radius: 12px;
  background: var(--product-surface);
  color: var(--product-text);
  cursor: pointer;
  transition:
    border-color 0.18s,
    transform 0.18s;
}
.capability:hover {
  border-color: #abb8ec;
  transform: translateY(-2px);
}
.cap-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--product-soft);
  color: var(--product-primary);
  font-size: 20px;
  flex-shrink: 0;
}
.cap-title {
  font-size: 14px;
  font-weight: 600;
}
.cap-title span {
  font-size: 10px;
  margin-left: 8px;
  padding: 3px 5px;
  border-radius: 4px;
  background: var(--product-soft);
  color: var(--product-primary);
  font-weight: 500;
}
.capability p {
  color: var(--product-muted);
  font-size: 12px;
  line-height: 1.7;
  margin: 6px 0 0;
}
.cap-arrow {
  margin-left: auto;
  color: var(--product-muted);
}
</style>
