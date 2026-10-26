/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端 API 根地址；本地留空走 vite proxy，线上填 https://xxx.onrender.com/api */
  readonly VITE_API_BASE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
