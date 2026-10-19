<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div>
        <div class="title">{{ paper?.paperName }}</div>
        <div class="sub">
          共 {{ paper?.questions?.length || 0 }} 题 · 总分 {{ paper?.totalScore }} · 及格线 {{ paper?.passScore }}
        </div>
      </div>
      <div class="filter-bar" style="margin: 0">
        <el-select v-model="examStage" size="default" style="width: 130px" :disabled="submitted">
          <el-option label="培训前测" value="PRE" />
          <el-option label="培训后测" value="POST" />
        </el-select>
        <el-button @click="$router.back()">返回</el-button>
      </div>
    </div>

    <el-alert v-if="paper?.sheetStatus === 'SUBMITTED'" type="success" :closable="false" show-icon
      title="该试卷已提交，不能重复作答" class="mb16" />

    <div class="card">
      <div v-for="q in paper?.questions || []" :key="q.questionId" class="q-item">
        <div class="q-head">
          <span class="q-no">{{ q.seqNo }}.</span>
          <el-tag size="small" effect="plain" class="mr8">{{ TYPE[q.questionType] }}</el-tag>
          <span class="q-score">{{ q.score }} 分</span>
          <span v-if="q.skillName" class="muted ml8">· {{ q.skillName }}</span>
        </div>
        <div class="q-content">{{ q.content }}</div>

        <!-- 单选 / 判断 -->
        <el-radio-group v-if="['SINGLE', 'JUDGE'].includes(q.questionType)"
          v-model="answers[q.questionId]" :disabled="submitted" class="opts">
          <el-radio v-for="o in parseOptions(q.options)" :key="o.key" :value="o.key" class="opt">
            <b>{{ o.key }}.</b> {{ o.text }}
          </el-radio>
        </el-radio-group>

        <!-- 多选 -->
        <el-checkbox-group v-else-if="q.questionType === 'MULTI'"
          :model-value="multiValue(q.questionId)" :disabled="submitted" class="opts"
          @update:model-value="(v: any) => (answers[q.questionId] = v.join(','))">
          <el-checkbox v-for="o in parseOptions(q.options)" :key="o.key" :value="o.key" :label="o.key" class="opt">
            <b>{{ o.key }}.</b> {{ o.text }}
          </el-checkbox>
        </el-checkbox-group>

        <!-- 简答 -->
        <el-input v-else v-model="answers[q.questionId]" type="textarea" :rows="4"
          :disabled="submitted" placeholder="请输入你的回答" />
      </div>

      <div v-if="!submitted" style="text-align: center; margin-top: 20px">
        <el-button type="primary" size="large" :loading="submitting" @click="submit">提交试卷</el-button>
      </div>
    </div>

    <el-dialog v-model="resultDialog" title="考试结果" width="480px" :close-on-click-modal="false">
      <div class="result">
        <div class="score" :class="{ pass: result.passed }">{{ result.totalScore }}</div>
        <div class="muted">满分 {{ paper?.totalScore }} · 及格线 {{ paper?.passScore }}</div>
        <el-tag :type="result.passed ? 'success' : 'danger'" effect="dark" class="mt8">
          {{ result.passed ? "已达到及格要求" : "未达到及格要求" }}
        </el-tag>
        <el-descriptions :column="2" border class="mt16" size="small">
          <el-descriptions-item label="客观题">{{ result.objectiveScore }}</el-descriptions-item>
          <el-descriptions-item label="主观题">{{ result.subjectiveScore }}</el-descriptions-item>
          <el-descriptions-item label="答对题数">{{ result.correctCount }}/{{ result.questionCount }}</el-descriptions-item>
          <el-descriptions-item label="考试阶段">{{ examStage === "PRE" ? "培训前测" : "培训后测" }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="result.aiComment" class="ai-comment">
          <div class="muted mb8">AI 评分说明</div>
          {{ result.aiComment }}
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="goRecords">查看成绩记录</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { examApi } from "@/api";

const route = useRoute();
const router = useRouter();
const paper = ref<any>(null);
const loading = ref(false);
const submitting = ref(false);
const submitted = ref(false);
const examStage = ref("POST");
const resultDialog = ref(false);
const result = reactive<any>({});
const answers = reactive<Record<number, string>>({});

const TYPE: Record<string, string> = { SINGLE: "单选", MULTI: "多选", JUDGE: "判断", SHORT: "简答" };

function parseOptions(raw: any) {
  if (!raw) return [];
  try {
    return typeof raw === "string" ? JSON.parse(raw) : raw;
  } catch {
    return [];
  }
}

function multiValue(qid: number) {
  const v = answers[qid];
  return v ? String(v).split(",").filter(Boolean) : [];
}

async function load() {
  loading.value = true;
  try {
    paper.value = await examApi.paper(Number(route.params.id));
    submitted.value = paper.value.sheetStatus === "SUBMITTED";
    if (!submitted.value) {
      await examApi.start(Number(route.params.id));
    }
  } finally {
    loading.value = false;
  }
}

async function submit() {
  const unanswered = (paper.value.questions || []).filter((q: any) => !answers[q.questionId]);
  submitting.value = true;
  try {
    const res = await examApi.submit(Number(route.params.id), {
      examStage: examStage.value,
      planId: paper.value.planId,
      answers: Object.entries(answers).map(([qid, ans]) => ({
        questionId: Number(qid),
        userAnswer: ans
      }))
    });
    Object.assign(result, res);
    submitted.value = true;
    resultDialog.value = true;
    if (unanswered.length) {
      ElMessage.warning(`有 ${unanswered.length} 题未作答`);
    }
  } finally {
    submitting.value = false;
  }
}

function goRecords() {
  resultDialog.value = false;
  router.push("/exam/record");
}

onMounted(load);
</script>

<style scoped>
.q-item {
  padding: 16px 0;
  border-bottom: 1px dashed var(--border);
}

.q-item:last-of-type {
  border-bottom: none;
}

.q-head {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.q-no {
  font-weight: 700;
  margin-right: 8px;
  color: #2563eb;
}

.q-score {
  font-size: 12px;
  color: #9ca3af;
}

.q-content {
  font-size: 14px;
  line-height: 1.8;
  margin-bottom: 10px;
  color: #1f2937;
}

.opts {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.opt {
  height: auto;
  padding: 6px 0;
  align-items: flex-start;
  white-space: normal;
}

.mr8 {
  margin-right: 8px;
}

.ml8 {
  margin-left: 8px;
}

.result {
  text-align: center;
}

.score {
  font-size: 54px;
  font-weight: 700;
  color: #dc2626;
  line-height: 1.1;
}

.score.pass {
  color: #16a34a;
}

.ai-comment {
  text-align: left;
  margin-top: 16px;
  background: #f8fafc;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 12px;
  font-size: 12.5px;
  line-height: 1.8;
  color: #4b5563;
}
</style>
