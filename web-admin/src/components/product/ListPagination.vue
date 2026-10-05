<template>
  <div class="list-pagination">
    <span>共 {{ total }} 条记录</span
    ><el-pagination
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      :page-sizes="[15, 25, 50]"
      layout="sizes, prev, pager, next"
      @update:current-page="$emit('update:page', $event)"
      @update:page-size="$emit('update:pageSize', $event)"
    />
  </div>
</template>
<script setup lang="ts">
defineProps<{ page: number; pageSize: number; total: number }>();
defineEmits(["update:page", "update:pageSize"]);
</script>
<style scoped>
.list-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 20px;
  color: var(--product-muted);
  font-size: 12px;
}
</style>
