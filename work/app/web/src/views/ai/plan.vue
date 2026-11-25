<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">AI 个性化培训方案生成</div>
        <div class="sub">
          三种生成策略可切换：纯规则（确定性基线）· 规则+大模型（受候选集约束）· 纯大模型（自由生成，用于对照实验）
        </div>
      </div>
      <el-tag v-if="aiStatus" :type="aiStatus.llmAvailable ? 'success' : 'warning'" effect="dark">
        {{ aiStatus.llmAvailable ? `大模型在线：${aiStatus.model}` : "未配置 API Key · 规则降级模式" }}
      </el-tag>
    </div>

    <div class="card">
      <div class="filter-bar">
        <template v-if="userStore.isManager">
          <el-select v-model="targetUserId" filterable placeholder="选择员工" style="width: 240px">
            <el-option v-for="u in employees" :key="u.id" :value="u.id"
              :label="`${u.realName}（${u.postName || '无岗位'}）`" />
          </el-select>
        </template>
        <el-input-number v-model="budget" :min="4" :max="200" :step="4" />
        <span class="muted">学时预算</span>
        <el-radio-group v-model="mode" size="small">
          <el-radio-button v-for="m in modes" :key="m.value" :value="m.value" :disabled="!m.available">
            {{ m.label }}
          </el-radio-button>
        </el-radio-group>
        <el-button type="primary" :loading="loading" @click="generate">
          <el-icon class="mr4"><MagicStick /></el-icon>生成方案
        </el-button>
        <el-button v-if="userStore.isManager" :loading="comparing" @click="openCompare">
          <el-icon class="mr4"><Files /></el-icon>三方案盲评
        </el-button>
        <el-button text @click="router.push('/ai/metrics#records')">
          <el-icon class="mr4"><Histogram /></el-icon>生成记录
        </el-button>
      </div>
    </div>

    <div v-if="result" class="card mt16">
      <div class="flex-between mb16">
        <div>
          <b style="font-size: 15px">{{ result.realName }} · {{ result.postName }}</b>
          <el-tag size="small" class="ml8" :type="result.llmUsed ? 'success' : 'info'" effect="plain">
            {{ genTypeLabel(result.genType) }}
          </el-tag>
          <el-tag size="small" class="ml8" effect="plain">合计 {{ result.totalHours }} 学时</el-tag>
          <el-tag v-if="isOverBudget(result)" size="small" class="ml8" type="danger" effect="plain">
            超预算 {{ (Number(result.totalHours) - Number(result.budget)).toFixed(1) }} 学时
          </el-tag>
          <el-tag v-if="result.cached" size="small" class="ml8" type="success" effect="plain">
            命中缓存
          </el-tag>
          <span class="muted ml8">耗时 {{ result.costMs }} ms</span>
          <span v-if="result.totalTokens" class="muted ml8">
            tokens {{ result.promptTokens }}+{{ result.completionTokens }}={{ result.totalTokens }}
          </span>
        </div>
        <div>
          <el-button size="small" @click="adopt('REJECTED')">驳回</el-button>
          <el-button size="small" type="warning" @click="adopt('MODIFIED')">修改后采纳</el-button>
          <el-button size="small" type="success" @click="adopt('ADOPTED')">采纳</el-button>
          <el-button size="small" type="primary" @click="toPlan">转为培训计划</el-button>
        </div>
      </div>

      <el-alert v-if="result.fallbackReason" :title="result.fallbackReason" type="warning"
        :closable="false" show-icon class="mb16" />

      <el-alert v-if="result.hallucinationCount > 0" type="error" :closable="false" show-icon class="mb16"
        :title="`本次自由生成有 ${result.hallucinationCount} 门课程不在课程库中，已剔除：${(result.hallucinatedCourses || []).join('、')}`">
        <template #default>
          这正是「约束必要性」的量化证据：混合模式靠候选集把这类输出挡在前面，纯大模型模式只能事后剔除。
        </template>
      </el-alert>

      <div class="summary-box">
        <div class="summary-label">方案说明</div>
        {{ result.summary }}
      </div>

      <el-table :data="result.courses" size="small" class="mt16">
        <el-table-column prop="seqNo" label="顺序" width="60" align="center" />
        <el-table-column prop="courseName" label="课程" min-width="180" />
        <el-table-column prop="coverSkill" label="对应技能" width="150" show-overflow-tooltip />
        <el-table-column prop="gapBefore" label="安排时差距" width="100" align="center">
          <template #default="{ row }">{{ row.gapBefore }} 级</template>
        </el-table-column>
        <el-table-column prop="courseLevel" label="课程等级" width="90" align="center" />
        <el-table-column prop="hours" label="学时" width="70" align="center" />
        <el-table-column prop="reason" label="安排依据" min-width="300">
          <template #default="{ row }">
            <span class="reason">{{ row.reason }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="result.refs?.length" class="refs mt16">
        <div class="summary-label">知识库引用来源（RAG 检索）</div>
        <div v-for="(r, i) in result.refs" :key="i" class="ref-item">
          <el-tag size="small" effect="plain">{{ (r.score * 100).toFixed(0) }}%</el-tag>
          <span class="ref-name">{{ r.docName }}</span>
          <span class="muted">{{ r.snippet }}</span>
        </div>
      </div>
    </div>

    <!-- 转为计划表单 -->
    <el-dialog v-model="planDialog" title="转为培训计划" width="520px">
      <el-form :model="planForm" label-width="90px">
        <el-form-item label="计划名称">
          <el-input v-model="planForm.planName" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="planForm.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="planForm.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmPlan">创建（草稿）</el-button>
      </template>
    </el-dialog>

    <!-- 三方案盲评：隐去模式标签，人工打分 -->
    <el-dialog v-model="compareDialog" title="方案盲评（不知道哪个是大模型生成的）" width="1080px" top="4vh">
      <el-alert type="info" :closable="false" show-icon class="mb16"
        title="三个方案分别来自纯规则、规则+大模型、纯大模型，顺序已打乱。请按「这套方案是否贴合该岗位的实际需要」打 1-5 分。" />

      <div v-loading="comparing" class="compare-grid">
        <div v-for="v in variants" :key="v.label" class="compare-card">
          <div class="compare-head">
            <span class="compare-label">方案 {{ v.label }}</span>
            <el-tag size="small" effect="plain">{{ v.totalHours }} 学时 / {{ v.courses?.length || 0 }} 门</el-tag>
          </div>
          <div class="compare-summary">{{ v.summary }}</div>
          <div class="compare-courses">
            <div v-for="c in v.courses" :key="c.courseId" class="compare-course">
              <span class="seq">{{ c.seqNo }}</span>
              <span class="cname">{{ c.courseName }}</span>
              <span class="muted">{{ c.hours }}h</span>
            </div>
            <el-empty v-if="!v.courses?.length" description="未产出可执行课程" :image-size="50" />
          </div>
          <div class="compare-score">
            <span>合理性</span>
            <el-rate v-model="scores[v.label]" :max="5" show-score score-template="{value} 分" />
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="compareDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!variants.length" @click="submitBlind">
          提交评分
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="summaryDialog" title="盲评汇总（按生成模式解盲）" width="720px">
      <el-table :data="blindSummary" size="small">
        <el-table-column prop="label" label="模式" min-width="140" />
        <el-table-column prop="samples" label="样本数" width="90" align="center" />
        <el-table-column prop="avgScore" label="平均分" width="90" align="center" />
        <el-table-column prop="firstChoice" label="获首选次数" width="110" align="center" />
        <el-table-column prop="avgHallucination" label="平均幻觉课程" width="120" align="center" />
        <el-table-column prop="avgCostMs" label="平均耗时(ms)" width="120" align="center" />
      </el-table>
      <div class="muted mt8" style="font-size: 12px">
        只统计已提交过盲评的记录；平均分满分为 5，样本数不足时结论仅作参考。
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { aiApi, planApi, userApi } from "@/api";
import { useUserStore } from "@/stores/user";

const userStore = useUserStore();
const route = useRoute();
const router = useRouter();

const aiStatus = ref<any>(null);
const employees = ref<any[]>([]);
const targetUserId = ref<number>();
const budget = ref(40);
const mode = ref<string>("HYBRID");
const modes = ref<any[]>([
  { value: "RULE", label: "纯规则", available: true },
  { value: "HYBRID", label: "规则+大模型", available: true },
  { value: "LLM_ONLY", label: "纯大模型", available: true }
]);
const loading = ref(false);
const result = ref<any>(null);
const planDialog = ref(false);
const saving = ref(false);

const comparing = ref(false);
const compareDialog = ref(false);
const submitting = ref(false);
const summaryDialog = ref(false);
const variants = ref<any[]>([]);
const blindId = ref("");
const scores = ref<Record<string, number>>({});
const blindSummary = ref<any[]>([]);

function genTypeLabel(t: string) {
  if (t === "AI_HYBRID") return "规则 + 大模型混合";
  if (t === "AI_LLM_ONLY") return "纯大模型（自由生成）";
  if (t === "AI_RULE") return "纯规则";
  return t || "-";
}

/** 纯大模型组常见超预算，单独标出来，答辩时是「约束必要性」的直接证据 */
function isOverBudget(r: any) {
  return r?.budget && Number(r.totalHours) > Number(r.budget);
}

const planForm = reactive({
  planName: "",
  startDate: "",
  endDate: ""
});

async function init() {
  aiStatus.value = await aiApi.status();
  if (Array.isArray(aiStatus.value?.modes) && aiStatus.value.modes.length) {
    modes.value = aiStatus.value.modes;
    const firstAvailable = aiStatus.value.modes.find((m: any) => m.available);
    if (firstAvailable && !aiStatus.value.modes.find((m: any) => m.value === mode.value)?.available) {
      mode.value = firstAvailable.value;
    }
  }
  if (userStore.isManager) {
    const res = await userApi.page({ current: 1, size: 500, status: 1 });
    employees.value = res.records.filter((u: any) => u.roles?.includes("EMPLOYEE"));
    const qid = Number(route.query.userId);
    targetUserId.value = qid || employees.value[0]?.id;
  } else {
    targetUserId.value = userStore.info?.userId;
  }
}

async function generate() {
  if (!targetUserId.value) {
    ElMessage.warning("请选择员工");
    return;
  }
  loading.value = true;
  try {
    result.value = await aiApi.generatePlan(targetUserId.value, {
      budget: budget.value,
      mode: mode.value
    });
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false;
  }
}

async function openCompare() {
  if (!targetUserId.value) {
    ElMessage.warning("请先选择员工");
    return;
  }
  compareDialog.value = true;
  comparing.value = true;
  variants.value = [];
  scores.value = {};
  try {
    const res = await aiApi.comparePlans(targetUserId.value, budget.value);
    blindId.value = res.blindId;
    variants.value = res.variants || [];
    variants.value.forEach((v: any) => (scores.value[v.label] = 0));
  } catch {
    compareDialog.value = false;
  } finally {
    comparing.value = false;
  }
}

async function submitBlind() {
  const filled = Object.entries(scores.value).filter(([, v]) => v > 0);
  if (filled.length < 2) {
    ElMessage.warning("请至少给两个方案打分");
    return;
  }
  submitting.value = true;
  try {
    const res = await aiApi.blindPick({ blindId: blindId.value, scores: scores.value });
    ElMessage.success(`已记录，首选为方案 ${res.bestLabel}（${res.bestScore} 分）`);
    compareDialog.value = false;
    blindSummary.value = await aiApi.blindSummary();
    summaryDialog.value = true;
  } finally {
    submitting.value = false;
  }
}

async function adopt(status: string) {
  if (!result.value?.aiRecordId) return;
  await aiApi.adopt(result.value.aiRecordId, status);
  ElMessage.success("已记录采纳结果");
}

function toPlan() {
  if (!result.value) return;
  planForm.planName = `${result.value.realName}的个性化培训计划`;
  const today = new Date();
  const end = new Date(today.getTime() + 30 * 86400000);
  planForm.startDate = today.toISOString().slice(0, 10);
  planForm.endDate = end.toISOString().slice(0, 10);
  planDialog.value = true;
}

async function confirmPlan() {
  saving.value = true;
  try {
    const id = await planApi.save({
      planName: planForm.planName,
      planType: "PERSONAL",
      targetUserId: result.value.userId,
      startDate: planForm.startDate,
      endDate: planForm.endDate,
      genType: result.value.genType,
      aiRecordId: result.value.aiRecordId,
      courses: result.value.courses.map((c: any) => ({
        courseId: c.courseId,
        seqNo: c.seqNo,
        expectHours: c.hours,
        coverSkillId: c.coverSkillId,
        gapBefore: c.gapBefore,
        planReason: c.reason
      }))
    });
    ElMessage.success("计划已创建，当前为草稿状态");
    planDialog.value = false;
    router.push(`/train/plan/${id}`);
  } finally {
    saving.value = false;
  }
}

onMounted(init);
</script>

<style scoped>
.mr4 {
  margin-right: 4px;
  vertical-align: -2px;
}

.ml8 {
  margin-left: 8px;
}

.summary-box {
  background: #f0f7ff;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  padding: 14px 16px;
  line-height: 1.9;
  font-size: 13.5px;
  color: #1e40af;
}

.summary-label {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 6px;
  font-weight: 600;
}

.reason {
  color: #4b5563;
  font-size: 12.5px;
  line-height: 1.7;
}

.refs {
  border-top: 1px dashed var(--border);
  padding-top: 12px;
}

.ref-item {
  display: flex;
  gap: 10px;
  align-items: baseline;
  padding: 6px 0;
  font-size: 12.5px;
}

.ref-name {
  font-weight: 600;
  color: #374151;
  white-space: nowrap;
}

.compare-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  min-height: 200px;
}

.compare-card {
  border: 1px solid var(--border, #e5e7eb);
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #fff;
}

.compare-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.compare-label {
  font-weight: 700;
  font-size: 14px;
  color: #1d4ed8;
}

.compare-summary {
  font-size: 12.5px;
  line-height: 1.75;
  color: #374151;
  background: #f8fafc;
  border-radius: 6px;
  padding: 8px 10px;
  min-height: 84px;
}

.compare-courses {
  flex: 1;
  font-size: 12.5px;
  max-height: 260px;
  overflow-y: auto;
}

.compare-course {
  display: flex;
  gap: 8px;
  align-items: baseline;
  padding: 3px 0;
  border-bottom: 1px dashed #f1f5f9;
}

.compare-course .seq {
  width: 18px;
  color: #94a3b8;
  text-align: right;
}

.compare-course .cname {
  flex: 1;
  color: #1f2937;
}

.compare-score {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--border, #e5e7eb);
  padding-top: 8px;
  font-size: 12.5px;
  color: #4b5563;
}
</style>
