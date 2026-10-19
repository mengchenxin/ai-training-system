<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">AI 个性化培训方案生成</div>
        <div class="sub">规则引擎按「加权差距」召回课程并排序 → 大模型生成方案说明与安排依据（受候选集合约束）</div>
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
        <el-checkbox v-model="useLlm" :disabled="!aiStatus?.llmAvailable">
          启用大模型（关闭则纯规则方案，用于对比实验）
        </el-checkbox>
        <el-button type="primary" :loading="loading" @click="generate">
          <el-icon class="mr4"><MagicStick /></el-icon>生成方案
        </el-button>
      </div>
    </div>

    <div v-if="result" class="card mt16">
      <div class="flex-between mb16">
        <div>
          <b style="font-size: 15px">{{ result.realName }} · {{ result.postName }}</b>
          <el-tag size="small" class="ml8" :type="result.llmUsed ? 'success' : 'info'" effect="plain">
            {{ result.genType === "AI_HYBRID" ? "规则 + 大模型混合" : "纯规则方案（降级）" }}
          </el-tag>
          <el-tag size="small" class="ml8" effect="plain">合计 {{ result.totalHours }} 学时</el-tag>
          <span class="muted ml8">耗时 {{ result.costMs }} ms</span>
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
const useLlm = ref(true);
const loading = ref(false);
const result = ref<any>(null);
const planDialog = ref(false);
const saving = ref(false);

const planForm = reactive({
  planName: "",
  startDate: "",
  endDate: ""
});

async function init() {
  aiStatus.value = await aiApi.status();
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
      useLlm: useLlm.value
    });
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false;
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
</style>
