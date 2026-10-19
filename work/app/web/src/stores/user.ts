import { defineStore } from "pinia";
import { authApi } from "@/api";

export interface UserInfo {
  userId: number;
  username: string;
  realName: string;
  deptId?: number;
  deptName?: string;
  postId?: number;
  postName?: string;
  roles: string[];
  roleNames: string[];
  permissions: string[];
}

export const useUserStore = defineStore("user", {
  state: () => ({
    token: localStorage.getItem("token") || "",
    info: null as UserInfo | null
  }),
  getters: {
    roles: (s) => s.info?.roles || [],
    isAdmin: (s) => (s.info?.roles || []).includes("ADMIN"),
    isLecturer: (s) => (s.info?.roles || []).includes("LECTURER"),
    isEmployee: (s) => (s.info?.roles || []).includes("EMPLOYEE"),
    /** 管理员或讲师：具备培训管理职责 */
    isManager(): boolean {
      return this.isAdmin || this.isLecturer;
    },
    primaryRole(): string {
      if (this.isAdmin) return "ADMIN";
      if (this.isLecturer) return "LECTURER";
      return "EMPLOYEE";
    },
    roleLabel(): string {
      const map: Record<string, string> = {
        ADMIN: "系统管理员",
        LECTURER: "讲师",
        EMPLOYEE: "员工"
      };
      return map[this.primaryRole] || "用户";
    }
  },
  actions: {
    async login(username: string, password: string) {
      const res = await authApi.login({ username, password });
      this.token = res.token;
      localStorage.setItem("token", res.token);
      await this.fetchInfo();
      return res;
    },
    async fetchInfo() {
      this.info = await authApi.info();
      return this.info;
    },
    async logout() {
      try {
        await authApi.logout();
      } catch {
        /* 忽略 */
      }
      this.token = "";
      this.info = null;
      localStorage.removeItem("token");
    },
    canAccess(roles?: string[]) {
      if (!roles || roles.length === 0) return true;
      return roles.some((r) => this.roles.includes(r));
    }
  }
});
