<template>
  <div class="login-page">
    <div class="login-box">
      <div class="brand">
        <div class="logo">AI</div>
        <h1>AI 培训计划管理系统</h1>
        <p>技能差距驱动的个性化培训闭环</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="账号" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="onSubmit">
          登 录
        </el-button>
      </el-form>

      <div class="demo">
        <div class="demo-title">演示账号（密码均为 123456）</div>
        <div class="demo-list">
          <el-tag
            v-for="a in accounts"
            :key="a.username"
            :type="a.type"
            effect="plain"
            class="acc"
            @click="fill(a.username)"
          >
            {{ a.label }}：{{ a.username }}
          </el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, type FormInstance } from "element-plus";
import { User, Lock } from "@element-plus/icons-vue";
import { useUserStore } from "@/stores/user";

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const formRef = ref<FormInstance>();
const loading = ref(false);

const form = reactive({ username: "admin", password: "123456" });

const rules = {
  username: [{ required: true, message: "请输入账号", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }]
};

const accounts = [
  { label: "管理员", username: "admin", type: "danger" as const },
  { label: "讲师", username: "lecturer01", type: "warning" as const },
  { label: "员工", username: "emp001", type: "success" as const }
];

function fill(username: string) {
  form.username = username;
  form.password = "123456";
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  loading.value = true;
  try {
    await userStore.login(form.username, form.password);
    ElMessage.success("登录成功");
    const redirect = (route.query.redirect as string) || "/dashboard";
    router.push(redirect);
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 50%, #3b82f6 100%);
}

.login-box {
  width: 400px;
  background: #fff;
  border-radius: 14px;
  padding: 36px 32px 26px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.22);
}

.brand {
  text-align: center;
  margin-bottom: 26px;
}

.logo {
  width: 52px;
  height: 52px;
  margin: 0 auto 14px;
  border-radius: 14px;
  background: linear-gradient(135deg, #2563eb, #60a5fa);
  color: #fff;
  font-weight: 700;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand h1 {
  margin: 0;
  font-size: 19px;
  color: #1f2937;
}

.brand p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #6b7280;
}

.submit {
  width: 100%;
  margin-top: 4px;
}

.demo {
  margin-top: 22px;
  border-top: 1px dashed #e5e7eb;
  padding-top: 14px;
}

.demo-title {
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 8px;
}

.demo-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.acc {
  cursor: pointer;
}
</style>
