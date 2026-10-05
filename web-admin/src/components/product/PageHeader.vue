<template>
  <header class="page-heading">
    <div>
      <div class="product-kicker">{{ eyebrow }}</div>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </div>
    <div class="product-actions"><slot /></div>
  </header>
</template>
<script setup lang="ts">
withDefaults(defineProps<{ title: string; description: string; eyebrow?: string }>(), { eyebrow: "CAMPUS OPERATIONS" });
</script>
<style scoped>
.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 24px;
}
.page-heading h1 {
  font-size: 25px;
  letter-spacing: -0.5px;
  font-weight: 650;
  margin: 8px 0;
  color: var(--product-text);
}
.page-heading p {
  margin: 0;
  color: var(--product-muted);
  font-size: 13px;
  line-height: 1.7;
}
</style>
