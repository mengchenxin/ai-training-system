<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">培训效果诊断</div>
        <div class="sub">前后测对比 + 技能差距缩小率 → 结论诊断与补训方案（闭环最后一环）</div>
      </div>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-select v-model="planId" placeholder="选择培训计划" filterable style="width: 300px" @change="loadList">
          <el-option v-for="p in plans" :key="p.id" :label="p.planName" :value="p.id" />
        </el-select>
        <el-button type="primary" @click="loadList">查询</el-button>
        <span class="muted">诊断会依据后测成绩自动回写技能等级提升</span>
      </div>

      <el-table :data="rows" v-loading="loading" size="small" empty-text="暂无诊断记录">
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="preScore" label="前测" width="80" align="center">
          <template #default="{ row }">{{ row.preScore ?? "-" }}</template>
        </el-table-column>
        <el-table-column prop="postScore" label="后测" width="80" align="center" />
        <el-table-column label="提升" width="90" align="center">
          <template #default="{ row }">
            <span :style="{ color: Number(row.scoreImprove) > 0 ? '#16a34a' : '#dc2626' }">
              {{ Number(row.scoreImprove) > 0 ? "+" : "" }}{{ row.scoreImprove }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="差距缩小" width="100" align="center">
          <template #default="{ row }">{{ row.gapReduce }}%</template>
        </el-table-column>
        <el-table-column label="完成率" width="150">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.finishRate)" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="结论" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="resultType(row.evalResult)">
              {{ RESULT[row.evalResult] || row.evalResult }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="diagnosis" label="诊断结论" min-width="280" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="效果诊断详情" width="720px">
      <template v-if="current">
        <div class="stat-grid" style="margin-bottom: 16px">
          <div class="stat-card">
            <div class="label">前测 → 后测</div>
            <div class="value" style="font-size: 20px">
              {{ current.preScore ?? "-" }} → {{ current.postScore }}
            </div>
          </div>
          <div class="stat-card">
            <div class="label">成绩提升</div>
            <div class="value" style="color: #16a34a">{{ current.scoreImprove }}</div>
          </div>
          <div class="stat-card">
            <div class="label">差距缩小率</div>
            <div class="value">{{ current.gapReduce }}<span class="unit">%</span></div>
          </div>
          <div class="stat-card">
            <div class="label">结论</div>
            <div class="value" style="font-size: 18px; padding-top: 6px">
              <el-tag :type="resultType(current.evalResult)" effect="dark">
                {{ RESULT[current.evalResult] }}
              </el-tag>
            </div>
          </div>
        </div>

        <div class="block">
          <div class="block-title">诊断结论</div>
          {{ current.diagnosis }}
        </div>

        <div v-if="current.retrainPlan" class="block retrain">
          <div class="block-title">补训方案（系统自动生成）</div>
          {{ current.retrainPlan }}
        </div>

        <el-button class="mt16" size="small" :loading="regenerating" @click="regenerate">
          重新诊断（含大模型增强）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { aiApi, planApi } from "@/api";

const plans = ref<any[]>([]);
const planId = ref<number>();
const rows = ref<any[]>([]);
const loading = ref(false);
const dialog = ref(false);
const current = ref<any>(null);
const regenerating = ref(false);

const RESULT: Record<string, string> = { EXCELLENT: "优秀", PASS: "达标", FAIL: "未达标" };

function resultType(r: string) {
  return r === "EXCELLENT" ? "success" : r === "PASS" ? "primary" : "danger";
}

async function loadPlans() {
  const res = await planApi.page({ current: 1, size: 100 });
  plans.value = res.records;
  if (plans.value.length) {
    planId.value = plans.value[0].id;
    await loadList();
  }
}

async function loadList() {
  if (!planId.value) return;
  loading.value = true;
  try {
    rows.value = await aiApi.effectList(planId.value);
  } finally {
    loading.value = false;
  }
}

function openDetail(row: any) {
  current.value = row;
  dialog.value = true;
}

async function regenerate() {
  regenerating.value = true;
  try {
    const res = await aiApi.effect(planId.value as number, current.value.userId);
    ElMessage.success("诊断已更新");
    current.value = { ...current.value, diagnosis: res.diagnosis, retrainPlan: res.retrainPlan };
    loadList();
  } finally {
    regenerating.value = false;
  }
}

onMounted(loadPlans);
</script>

<style scoped>
.block {
  background: #f8fafc;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 14px 16px;
  line-height: 1.9;
  font-size: 13.5px;
  color: #374151;
  margin-bottom: 12px;
}

.block.retrain {
  background: #fef7ed;
  border-color: #fed7aa;
  color: #9a3412;
}

.block-title {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 6px;
}
</style>
