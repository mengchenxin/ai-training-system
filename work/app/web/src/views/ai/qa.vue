<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">技能标准知识问答</div>
        <div class="sub">基于知识库检索（BM25）后再由大模型作答，答案附带引用来源</div>
      </div>
      <el-switch v-model="showRef" active-text="显示检索片段" />
    </div>

    <div class="card chat-card">
      <div ref="listRef" class="chat-list">
        <div v-if="!messages.length" class="empty">
          <el-icon :size="42" color="#cbd5e1"><ChatDotRound /></el-icon>
          <div class="mt8 muted">提一个关于岗位技能标准、培训制度或课程内容的问题</div>
          <div class="samples">
            <el-tag v-for="s in samples" :key="s" class="sample" @click="ask(s)">{{ s }}</el-tag>
          </div>
        </div>

        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="avatar">{{ m.role === "user" ? "我" : "AI" }}</div>
          <div class="bubble-wrap">
            <div class="bubble">{{ m.content }}</div>
            <div v-if="m.refs?.length" class="refs">
              <div class="muted mb8">引用来源</div>
              <div v-for="(r, j) in m.refs" :key="j" class="ref-row">
                <el-tag size="small" effect="plain">{{ (r.score * 100).toFixed(0) }}%</el-tag>
                <span class="ref-name">{{ r.docName }}</span>
                <span class="ref-snippet">{{ r.snippet }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="thinking" class="msg assistant">
          <div class="avatar">AI</div>
          <div class="bubble">正在检索知识库并生成回答…</div>
        </div>
      </div>

      <div class="chat-input">
        <el-input v-model="question" placeholder="输入问题，回车发送" :disabled="thinking"
          @keyup.enter="ask()" />
        <el-button type="primary" :loading="thinking" @click="ask()">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from "vue";
import { ElMessage } from "element-plus";
import { aiApi } from "@/api";

const question = ref("");
const messages = ref<any[]>([]);
const thinking = ref(false);
const showRef = ref(true);
const listRef = ref<HTMLElement>();

const samples = [
  "安全生产规范等级 4 的要求是什么？",
  "培训计划的状态如何流转？",
  "质量检验员需要达到哪些技能等级？",
  "未达标后补训怎么验收？"
];

async function ask(text?: string) {
  const q = (text || question.value).trim();
  if (!q || thinking.value) return;
  messages.value.push({ role: "user", content: q });
  question.value = "";
  thinking.value = true;
  scroll();
  try {
    const res = await aiApi.qa(q);
    messages.value.push({
      role: "assistant",
      content: res.answer,
      refs: showRef.value ? res.refs : []
    });
  } catch {
    messages.value.push({ role: "assistant", content: "抱歉，回答失败，请稍后再试。" });
  } finally {
    thinking.value = false;
    scroll();
  }
}

async function scroll() {
  await nextTick();
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight;
  }
}
</script>

<style scoped>
.chat-card {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 190px);
  padding: 0;
  overflow: hidden;
}

.chat-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.empty {
  text-align: center;
  padding-top: 60px;
}

.samples {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
  margin-top: 18px;
}

.sample {
  cursor: pointer;
}

.msg {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}

.msg.user {
  flex-direction: row-reverse;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: #2563eb;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}

.msg.user .avatar {
  background: #0f766e;
}

.bubble-wrap {
  max-width: 76%;
}

.bubble {
  background: #f3f4f6;
  border-radius: 10px;
  padding: 10px 14px;
  line-height: 1.85;
  font-size: 13.5px;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg.user .bubble {
  background: #2563eb;
  color: #fff;
}

.refs {
  margin-top: 10px;
  border-top: 1px dashed var(--border);
  padding-top: 8px;
}

.ref-row {
  display: flex;
  gap: 8px;
  align-items: baseline;
  font-size: 12px;
  padding: 3px 0;
}

.ref-name {
  font-weight: 600;
  color: #374151;
  white-space: nowrap;
}

.ref-snippet {
  color: #9ca3af;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-input {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  background: #fff;
}
</style>
