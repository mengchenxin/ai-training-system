import axios, { type AxiosRequestConfig } from "axios";
import { ElMessage } from "element-plus";
import router from "@/router";

export interface ApiResult<T = any> {
  code: number;
  msg: string;
  data: T;
}

export interface PageResult<T = any> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

const service = axios.create({
  baseURL: "/api",
  timeout: 120000
});

service.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

service.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult;
    if (res.code === 0) {
      return res.data;
    }
    if (res.code === 401) {
      localStorage.removeItem("token");
      if (router.currentRoute.value.path !== "/login") {
        router.push("/login");
      }
      ElMessage.error(res.msg || "登录已失效");
      return Promise.reject(new Error(res.msg));
    }
    ElMessage.error(res.msg || "请求失败");
    return Promise.reject(new Error(res.msg));
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem("token");
      router.push("/login");
      ElMessage.error("登录状态已失效，请重新登录");
    } else {
      ElMessage.error(error.response?.data?.msg || error.message || "网络异常");
    }
    return Promise.reject(error);
  }
);

/**
 * 统一请求方法，返回业务 data。
 *
 * 响应拦截器已把 axios 的 AxiosResponse 解包成后端返回的 data 字段，
 * 但 axios 的类型声明无法表达这种改写，因此这里做一次显式断言，
 * 而不是把返回值标成 AxiosResponse 让所有调用方再取一层 .data。
 */
export function request<T = any>(config: AxiosRequestConfig): Promise<T> {
  return service.request(config) as unknown as Promise<T>;
}

export default service;
