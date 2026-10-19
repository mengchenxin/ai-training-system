import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import { useUserStore } from "@/stores/user";

/**
 * meta.roles 为空表示所有登录用户可见；
 * meta.menu 为 false 表示不在侧边菜单显示。
 */
export const routes: RouteRecordRaw[] = [
  {
    path: "/login",
    component: () => import("@/views/auth/login.vue"),
    meta: { public: true }
  },
  {
    path: "/",
    component: () => import("@/layout/index.vue"),
    redirect: "/dashboard",
    children: [
      {
        path: "dashboard",
        component: () => import("@/views/home/index.vue"),
        meta: { title: "工作台", icon: "HomeFilled" }
      },
      {
        path: "system/user",
        component: () => import("@/views/admin/user.vue"),
        meta: { title: "用户管理", icon: "User", roles: ["ADMIN"] }
      },
      {
        path: "system/org",
        component: () => import("@/views/admin/org.vue"),
        meta: { title: "组织架构", icon: "OfficeBuilding", roles: ["ADMIN"] }
      },
      {
        path: "skill/list",
        component: () => import("@/views/skill/list.vue"),
        meta: { title: "技能点管理", icon: "Collection", roles: ["ADMIN", "LECTURER"] }
      },
      {
        path: "skill/model",
        component: () => import("@/views/skill/model.vue"),
        meta: { title: "岗位技能模型", icon: "Grid", roles: ["ADMIN", "LECTURER"] }
      },
      {
        path: "skill/gap",
        component: () => import("@/views/skill/gap.vue"),
        meta: { title: "技能差距分析", icon: "DataAnalysis" }
      },
      {
        path: "skill/heatmap",
        component: () => import("@/views/skill/heatmap.vue"),
        // 扩展功能：不在六模块核心需求内，保留入口仅隐藏菜单（直接访问 /skill/heatmap 可用）
        meta: { title: "群体短板分析", icon: "TrendCharts", roles: ["ADMIN", "LECTURER"], menu: false }
      },
      {
        path: "train/need",
        component: () => import("@/views/train/need.vue"),
        meta: { title: "培训需求", icon: "Tickets" }
      },
      {
        path: "train/course",
        component: () => import("@/views/train/course.vue"),
        meta: { title: "课程库", icon: "Reading" }
      },
      {
        path: "train/plan",
        component: () => import("@/views/train/plan.vue"),
        meta: { title: "培训计划", icon: "Calendar" }
      },
      {
        path: "train/plan/:id",
        component: () => import("@/views/train/planDetail.vue"),
        meta: { title: "计划详情", menu: false }
      },
      {
        path: "progress/my",
        component: () => import("@/views/progress/my.vue"),
        meta: { title: "我的培训", icon: "Notebook" }
      },
      {
        path: "progress/dashboard",
        component: () => import("@/views/progress/dashboard.vue"),
        meta: { title: "进度看板", icon: "Odometer", roles: ["ADMIN", "LECTURER"] }
      },
      {
        path: "exam/question",
        component: () => import("@/views/exam/question.vue"),
        meta: { title: "题库管理", icon: "EditPen", roles: ["ADMIN", "LECTURER"] }
      },
      {
        path: "exam/paper",
        component: () => import("@/views/exam/paper.vue"),
        meta: { title: "试卷与组卷", icon: "Document", roles: ["ADMIN", "LECTURER"] }
      },
      {
        path: "exam/take/:id",
        component: () => import("@/views/exam/take.vue"),
        meta: { title: "在线考试", menu: false }
      },
      {
        path: "exam/record",
        component: () => import("@/views/exam/record.vue"),
        meta: { title: "考试成绩", icon: "Medal" }
      },
      {
        path: "ai/plan",
        component: () => import("@/views/ai/plan.vue"),
        meta: { title: "AI 方案生成", icon: "MagicStick" }
      },
      {
        path: "ai/qa",
        component: () => import("@/views/ai/qa.vue"),
        // 扩展功能：知识库问答（可选增强），隐藏菜单，直接访问 /ai/qa 可用
        meta: { title: "知识问答", icon: "ChatDotRound", menu: false }
      },
      {
        path: "ai/kb",
        component: () => import("@/views/ai/kb.vue"),
        // 扩展功能：知识库管理，支撑知识问答，隐藏菜单
        meta: { title: "知识库管理", icon: "Files", roles: ["ADMIN", "LECTURER"], menu: false }
      },
      {
        path: "ai/effect",
        component: () => import("@/views/ai/effect.vue"),
        meta: { title: "效果诊断", icon: "Aim", roles: ["ADMIN", "LECTURER"] }
      },
      {
        path: "ai/metrics",
        component: () => import("@/views/ai/metrics.vue"),
        // 辅助页面：AI 效果量化指标，论文实验数据用，隐藏菜单
        meta: { title: "AI 效果指标", icon: "Histogram", roles: ["ADMIN", "LECTURER"], menu: false }
      },
      {
        path: "profile",
        component: () => import("@/views/me/index.vue"),
        meta: { title: "个人中心", menu: false }
      }
    ]
  },
  {
    path: "/:pathMatch(.*)*",
    redirect: "/dashboard"
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async (to) => {
  if (to.meta.public) {
    return true;
  }
  const store = useUserStore();
  if (!store.token) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }
  if (!store.info) {
    try {
      await store.fetchInfo();
    } catch {
      return { path: "/login" };
    }
  }
  const roles = to.meta.roles as string[] | undefined;
  if (roles && roles.length > 0 && !store.canAccess(roles)) {
    return { path: "/dashboard" };
  }
  return true;
});

export default router;
