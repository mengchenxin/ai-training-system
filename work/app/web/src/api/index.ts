import { request, type PageResult } from "./request";

/** ---------------- 认证 ---------------- */
export const authApi = {
  login: (data: { username: string; password: string }) =>
    request<{ token: string; userId: number; username: string; realName: string; roles: string[] }>({
      url: "/auth/login",
      method: "post",
      data
    }),
  logout: () => request({ url: "/auth/logout", method: "post" }),
  info: () => request<any>({ url: "/auth/info" })
};

/** ---------------- 用户与组织 ---------------- */
export const userApi = {
  page: (params: any) => request<PageResult>({ url: "/system/user/page", params }),
  detail: (id: number) => request<any>({ url: `/system/user/${id}` }),
  save: (data: any) => request({ url: "/system/user", method: "post", data }),
  remove: (id: number) => request({ url: `/system/user/${id}`, method: "delete" }),
  resetPassword: (id: number, password?: string) =>
    request({ url: `/system/user/${id}/reset-password`, method: "post", data: { password } })
};

export const orgApi = {
  deptList: () => request<any[]>({ url: "/system/dept/list" }),
  saveDept: (data: any) => request({ url: "/system/dept", method: "post", data }),
  removeDept: (id: number) => request({ url: `/system/dept/${id}`, method: "delete" }),
  postList: (deptId?: number) => request<any[]>({ url: "/system/post/list", params: { deptId } }),
  savePost: (data: any) => request({ url: "/system/post", method: "post", data }),
  removePost: (id: number) => request({ url: `/system/post/${id}`, method: "delete" }),
  roleList: () => request<any[]>({ url: "/system/role/list" })
};

/** ---------------- 技能 ---------------- */
export const skillApi = {
  list: (params?: any) => request<any[]>({ url: "/skill/list", params }),
  page: (params: any) => request<PageResult>({ url: "/skill/page", params }),
  save: (data: any) => request({ url: "/skill", method: "post", data }),
  remove: (id: number) => request({ url: `/skill/${id}`, method: "delete" }),
  postRequirement: (postId: number) => request<any[]>({ url: `/skill/post/${postId}` }),
  savePostRequirement: (postId: number, data: any[]) =>
    request({ url: `/skill/post/${postId}`, method: "post", data }),
  empSkill: (userId: number) => request<any[]>({ url: `/skill/emp/${userId}` }),
  saveEmpSkill: (userId: number, data: any[]) =>
    request({ url: `/skill/emp/${userId}`, method: "post", data }),
  gap: (userId: number) => request<any>({ url: `/skill/gap/${userId}` }),
  myGap: () => request<any>({ url: "/skill/gap/me" }),
  batchGap: (params: any) => request<any[]>({ url: "/skill/gap/batch", params }),
  heatmap: (params: any) => request<any[]>({ url: "/skill/gap/heatmap", params })
};

/** ---------------- 课程与需求 ---------------- */
export const trainApi = {
  coursePage: (params: any) => request<PageResult>({ url: "/train/course/page", params }),
  courseList: (skillId?: number) => request<any[]>({ url: "/train/course/list", params: { skillId } }),
  saveCourse: (data: any) => request({ url: "/train/course", method: "post", data }),
  removeCourse: (id: number) => request({ url: `/train/course/${id}`, method: "delete" }),

  needPage: (params: any) => request<PageResult>({ url: "/train/need/page", params }),
  saveNeed: (data: any) => request({ url: "/train/need", method: "post", data }),
  auditNeed: (id: number, status: string) =>
    request({ url: `/train/need/${id}/audit`, method: "post", params: { status } }),
  removeNeed: (id: number) => request({ url: `/train/need/${id}`, method: "delete" })
};

/** ---------------- 培训计划 ---------------- */
export const planApi = {
  page: (params: any) => request<PageResult>({ url: "/train/plan/page", params }),
  detail: (id: number) => request<any>({ url: `/train/plan/${id}` }),
  save: (data: any) => request<number>({ url: "/train/plan", method: "post", data }),
  changeStatus: (id: number, status: string) =>
    request({ url: `/train/plan/${id}/status`, method: "post", params: { status } }),
  remove: (id: number) => request({ url: `/train/plan/${id}`, method: "delete" })
};

/** ---------------- 进度 ---------------- */
export const progressApi = {
  my: (status?: string) => request<any[]>({ url: "/train/progress/my", params: { status } }),
  report: (data: any) => request({ url: "/train/progress/report", method: "post", data }),
  planProgress: (planId: number, params: any) =>
    request<PageResult>({ url: `/train/progress/plan/${planId}`, params }),
  dashboard: (deptId?: number) => request<any>({ url: "/train/progress/dashboard", params: { deptId } }),
  risk: () => request<any[]>({ url: "/train/progress/risk" })
};

/** ---------------- 考核 ---------------- */
export const examApi = {
  questionPage: (params: any) => request<PageResult>({ url: "/exam/question/page", params }),
  questionDetail: (id: number) => request<any>({ url: `/exam/question/${id}` }),
  saveQuestion: (data: any) => request({ url: "/exam/question", method: "post", data }),
  removeQuestion: (id: number) => request({ url: `/exam/question/${id}`, method: "delete" }),
  questionStat: () => request<any[]>({ url: "/exam/question/stat" }),

  generatePaper: (data: any) => request<number>({ url: "/exam/paper/generate", method: "post", data }),
  paperPage: (params: any) => request<PageResult>({ url: "/exam/paper/page", params }),
  paper: (id: number) => request<any>({ url: `/exam/paper/${id}` }),
  start: (id: number) => request({ url: `/exam/paper/${id}/start`, method: "post" }),
  submit: (id: number, data: any) => request<any>({ url: `/exam/paper/${id}/submit`, method: "post", data }),

  myRecords: () => request<any[]>({ url: "/exam/record/my" }),
  recordPage: (params: any) => request<PageResult>({ url: "/exam/record/page", params })
};

/** ---------------- AI ---------------- */
export const aiApi = {
  status: () => request<any>({ url: "/ai/status" }),
  generatePlan: (userId: number, params?: any) =>
    request<any>({ url: "/ai/plan/generate", method: "post", params: { userId, ...params } }),
  adopt: (recordId: number, status: string, reason?: string) =>
    request({ url: `/ai/plan/${recordId}/adopt`, method: "post", params: { status, reason } }),
  recordPage: (params: any) => request<PageResult>({ url: "/ai/record/page", params }),
  metrics: () => request<any>({ url: "/ai/metrics" }),

  qa: (question: string) => request<any>({ url: "/ai/qa", method: "post", data: { question } }),
  qaFeedback: (logId: number, feedback: number) =>
    request({ url: `/ai/qa/${logId}/feedback`, method: "post", params: { feedback } }),
  ragSearch: (q: string) => request<any[]>({ url: "/ai/rag/search", params: { q } }),
  ragReload: () => request<number>({ url: "/ai/rag/reload", method: "post" }),

  effect: (planId: number, userId: number) =>
    request<any>({ url: `/ai/effect/${planId}/${userId}`, method: "post" }),
  effectList: (planId: number) => request<any[]>({ url: `/ai/effect/${planId}` }),
  retrain: (planId: number, userId: number) =>
    request<string>({ url: `/ai/effect/${planId}/${userId}/retrain`, method: "post" }),

  promptList: () => request<any[]>({ url: "/ai/prompt/list" }),
  savePrompt: (data: any) => request({ url: "/ai/prompt", method: "post", data }),

  kbDocs: (docType?: string) => request<any[]>({ url: "/ai/kb/doc/list", params: { docType } }),
  kbChunks: (docId: number) => request<any[]>({ url: `/ai/kb/doc/${docId}/chunks` }),
  addKbDoc: (data: any) => request<number>({ url: "/ai/kb/doc", method: "post", data }),
  removeKbDoc: (id: number) => request({ url: `/ai/kb/doc/${id}`, method: "delete" })
};
