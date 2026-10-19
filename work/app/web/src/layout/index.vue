<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo" :class="{ mini: collapsed }">
        <el-icon :size="22"><Reading /></el-icon>
        <span v-if="!collapsed">AI 培训管理系统</span>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="activeMenu"
          :collapse="collapsed"
          :collapse-transition="false"
          background-color="#1f2937"
          text-color="#cbd5e1"
          active-text-color="#ffffff"
          router
        >
          <template v-for="group in menuGroups" :key="group.title">
            <el-menu-item-group v-if="group.items.length" :title="collapsed ? '' : group.title">
              <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
                <el-icon><component :is="item.icon" /></el-icon>
                <template #title>{{ item.title }}</template>
              </el-menu-item>
            </el-menu-item-group>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="right">
          <el-tag size="small" :type="userStore.isManager ? 'success' : 'info'" effect="light">
            {{ userStore.roleLabel }}
          </el-tag>
          <span class="uname">{{ userStore.info?.realName }}</span>
          <el-dropdown @command="onCommand">
            <el-avatar :size="30" class="avatar">
              {{ (userStore.info?.realName || "U").slice(0, 1) }}
            </el-avatar>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useUserStore } from "@/stores/user";
import { ElMessageBox } from "element-plus";
import { routes } from "@/router";

const userStore = useUserStore();
const route = useRoute();
const router = useRouter();
const collapsed = ref(false);

const activeMenu = computed(() => route.path);

/** 菜单分组：把路由按前缀归类 */
const GROUP_ORDER = [
  { title: "概览", prefix: "dashboard" },
  { title: "组织与权限", prefix: "system" },
  { title: "技能模型", prefix: "skill" },
  { title: "培训管理", prefix: "train" },
  { title: "培训实施", prefix: "progress" },
  { title: "考核评价", prefix: "exam" },
  { title: "AI 智能", prefix: "ai" }
];

const menuGroups = computed(() => {
  const layoutRoute = routes.find((r) => r.path === "/");
  const children = (layoutRoute?.children || []) as any[];
  return GROUP_ORDER.map((g) => ({
    title: g.title,
    items: children
      .filter((c) => c.meta?.title && c.meta?.menu !== false)
      .filter((c) => c.path.startsWith(g.prefix + "/") || (g.prefix === "dashboard" && c.path === "dashboard"))
      .filter((c) => userStore.canAccess(c.meta?.roles))
      .map((c) => ({
        path: "/" + c.path,
        title: c.meta.title,
        icon: c.meta.icon
      }))
  })).filter((g) => g.items.length > 0);
});

async function onCommand(cmd: string) {
  if (cmd === "profile") {
    router.push("/profile");
  } else if (cmd === "logout") {
    await ElMessageBox.confirm("确定退出登录吗？", "提示", { type: "warning" });
    await userStore.logout();
    router.push("/login");
  }
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  background: #1f2937;
  transition: width 0.2s;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 18px;
  color: #fff;
  font-weight: 600;
  font-size: 15px;
  white-space: nowrap;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo.mini {
  justify-content: center;
  padding: 0;
}

.aside :deep(.el-menu) {
  border-right: none;
}

.aside :deep(.el-menu-item-group__title) {
  color: #64748b;
  font-size: 12px;
  padding-left: 20px;
}

.aside :deep(.el-menu-item.is-active) {
  background: #2563eb !important;
}

.header {
  background: #fff;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
}

.left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  cursor: pointer;
  font-size: 18px;
  color: var(--text-2);
}

.right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.uname {
  font-size: 14px;
}

.avatar {
  cursor: pointer;
  background: #2563eb;
}

.main {
  background: var(--bg-page);
  padding: 0;
  overflow-y: auto;
}
</style>
