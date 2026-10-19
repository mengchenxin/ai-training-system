import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { resolve } from "path";

/**
 * AI 培训计划管理系统 —— 前端构建配置
 * 后端 context-path 为 /api，因此 /api 前缀直接透传，不做 rewrite
 */
export default defineConfig({
  // 使用独立缓存目录：避免与模板遗留的 node_modules/.vite 冲突，
  // 也避免开发环境批量删除缓存目录触发安全策略拦截
  cacheDir: "node_modules/.vite-app",
  plugins: [
    vue(),
    AutoImport({
      imports: ["vue", "pinia", "vue-router"],
      resolvers: [ElementPlusResolver()],
      dts: false
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: false
    })
  ],
  resolve: {
    alias: {
      "@": resolve(__dirname, "src")
    }
  },
  server: {
    host: "127.0.0.1",
    port: 3000,
    open: false,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8000",
        changeOrigin: true
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 2000
  }
});
