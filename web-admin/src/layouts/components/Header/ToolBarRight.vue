<template>
  <div class="tool-bar-ri">
    <span class="workspace-label">管理工作台</span>
    <div class="header-user">
      <span class="username">{{ username || "管理员" }}</span
      ><span class="role">管理员</span>
    </div>
    <Avatar />
  </div>
</template>
<script setup lang="ts">
import { computed } from "vue";
import { useUserStore } from "@/stores/modules/user";
import Avatar from "./components/Avatar.vue";
const userStore = useUserStore();
const username = computed(() => userStore.userInfo.name);
</script>
<style scoped>
.tool-bar-ri {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-right: 8px;
}
.workspace-label {
  font-size: 11px;
  color: var(--product-muted);
  padding-right: 16px;
  border-right: 1px solid var(--product-line);
}
.header-user {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.username {
  font-size: 12px;
  font-weight: 600;
  color: var(--product-text);
}
.role {
  font-size: 10px;
  color: var(--product-muted);
}
@media (max-width: 800px) {
  .workspace-label {
    display: none;
  }
}
</style>
