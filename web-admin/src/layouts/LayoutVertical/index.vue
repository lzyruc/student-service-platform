<template>
  <el-container class="layout"
    ><el-aside
      ><div class="aside-box product-sidebar" :style="{ width: isCollapse ? '72px' : '236px' }">
        <div class="logo">
          <div class="product-brand-mark">✦</div>
          <div v-show="!isCollapse">
            <div class="product-brand-name">学生综合服务平台</div>
            <div class="product-brand-sub">CAMPUS INTELLIGENCE</div>
          </div>
        </div>
        <el-scrollbar
          ><el-menu
            :router="false"
            :default-active="activeMenu"
            :collapse="isCollapse"
            :unique-opened="accordion"
            :collapse-transition="false"
            ><template v-if="isCollapse"><SubMenu :menu-list="menuList" /></template
            ><template v-else
              ><el-menu-item-group v-for="group in groups" :key="group.title" :title="group.title"
                ><SubMenu :menu-list="group.items" /></el-menu-item-group></template></el-menu
        ></el-scrollbar>
        <div v-show="!isCollapse" class="sidebar-note">学生服务 · 学业支持 · AI<br />面向真实校园事务</div>
      </div></el-aside
    ><el-container
      ><el-header><ToolBarLeft /><ToolBarRight /></el-header><Main /></el-container
  ></el-container>
</template>
<script setup lang="ts" name="layoutVertical">
import { computed } from "vue";
import { useRoute } from "vue-router";
import { useAuthStore } from "@/stores/modules/auth";
import { useGlobalStore } from "@/stores/modules/global";
import Main from "@/layouts/components/Main/index.vue";
import ToolBarLeft from "@/layouts/components/Header/ToolBarLeft.vue";
import ToolBarRight from "@/layouts/components/Header/ToolBarRight.vue";
import SubMenu from "@/layouts/components/Menu/SubMenu.vue";
const route = useRoute(),
  authStore = useAuthStore(),
  globalStore = useGlobalStore();
const accordion = computed(() => globalStore.accordion),
  isCollapse = computed(() => globalStore.isCollapse),
  menuList = computed(() => authStore.showMenuListGet);
const activeMenu = computed(() => (route.meta.activeMenu || route.path) as string);
const groups = computed(() => {
  const definitions = [
    { title: "工作台", paths: ["/home/index"] },
    { title: "学生管理", paths: ["/college/studentInformation", "/college/trainingPlan"] },
    { title: "学生事务", paths: ["/college/notification", "/college/examineApprove"] },
    { title: "AI 能力", paths: ["/college/knowledgeBase"] }
  ];
  const known = new Set(definitions.flatMap(g => g.paths));
  return [
    ...definitions.map(g => ({ title: g.title, items: g.paths.flatMap(path => menuList.value.filter(i => i.path === path)) })),
    { title: "其他", items: menuList.value.filter(i => !known.has(i.path)) }
  ].filter(g => g.items.length);
});
</script>
<style scoped lang="scss">
@import "./index.scss";
</style>
