<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">技能差距分析</div>
        <div class="sub">
          差距 = 岗位目标等级 − 员工当前等级；优先级 = 等级差 × 技能权重，归一化后排序
        </div>
      </div>
      <div v-if="userStore.isManager" class="filter-bar" style="margin: 0">
        <el-select v-model="selectedUserId" filterable placeholder="选择员工" style="width: 220px"
          @change="loadGap">
          <el-option v-for="u in employees" :key="u.id" :value="u.id"
            :label="`${u.realName}（${u.username}·${u.postName || '无岗位'}）`" />
        </el-select>
      </div>
    </div>

    <div v-if="!gap && !loading" class="card" style="text-align: center; padding: 60px 0">
      <el-empty description="请选择员工进行差距分析" />
    </div>

    <template v-if="gap">
      <div class="stat-grid">
        <div class="stat-card">
          <div class="label">岗位技能达标率</div>
          <div class="value" :style="{ color: rateColor }">
            {{ gap.matchRate }}<span class="unit">%</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="label">存在差距的技能项</div>
          <div class="value">{{ gap.gapCount }}<span class="unit">项</span></div>
        </div>
        <div class="stat-card">
          <div class="label">加权总差距</div>
          <div class="value">{{ gap.totalWeightedGap }}</div>
        </div>
        <div class="stat-card">
          <div class="label">岗位要求加权总量</div>
          <div class="value">{{ gap.totalWeightedTarget }}</div>
        </div>
      </div>

      <el-row :gutter="16">
        <el-col :span="10">
          <div class="card">
            <b>技能画像雷达图</b>
            <div ref="radarRef" class="chart"></div>
          </div>
        </el-col>
        <el-col :span="14">
          <div class="card">
            <div class="flex-between mb16">
              <b>差距明细（按优先级排序）</b>
              <el-button size="small" @click="copySummary">复制摘要</el-button>
            </div>
            <el-table :data="gap.items" size="small" max-height="380">
              <el-table-column prop="priorityRank" label="优先级" width="72" align="center">
                <template #default="{ row }">
                  <span v-if="row.priorityRank" class="rank">{{ row.priorityRank }}</span>
                  <span v-else class="muted">—</span>
                </template>
              </el-table-column>
              <el-table-column prop="skillName" label="技能点" min-width="140" show-overflow-tooltip />
              <el-table-column prop="category" label="类别" width="70" />
              <el-table-column label="目标" width="60" align="center">
                <template #default="{ row }">{{ row.targetLevel }}</template>
              </el-table-column>
              <el-table-column label="当前" width="60" align="center">
                <template #default="{ row }">
                  <span :class="{ 'text-danger': row.currentLevel < row.targetLevel }">
                    {{ row.currentLevel }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="差距" width="60" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.gap > 0" size="small" type="danger" effect="plain">{{ row.gap }}</el-tag>
                  <el-tag v-else size="small" type="success" effect="plain">达标</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="weight" label="权重" width="66" align="center" />
              <el-table-column prop="weightedGap" label="加权差距" width="86" align="center" />
              <el-table-column label="优先级" width="80" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="levelType(row.priorityLevel)" effect="dark">
                    {{ row.priorityLevel }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-col>
      </el-row>

      <div class="card">
        <div class="flex-between mb8">
          <b>差距摘要（可直接作为 AI 方案生成的输入）</b>
          <el-button type="primary" size="small" @click="$router.push({ path: '/ai/plan', query: { userId: gap.userId } })">
            用该差距生成培训方案
          </el-button>
        </div>
        <pre class="summary">{{ gap.gapSummary || "该员工已全部达标" }}</pre>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from "vue";
import * as echarts from "echarts";
import { ElMessage } from "element-plus";
import { skillApi, userApi } from "@/api";
import { useUserStore } from "@/stores/user";

const userStore = useUserStore();
const employees = ref<any[]>([]);
const selectedUserId = ref<number>();
const gap = ref<any>(null);
const loading = ref(false);
const radarRef = ref<HTMLElement>();

const rateColor = computed(() => {
  const r = Number(gap.value?.matchRate || 0);
  return r >= 80 ? "#16a34a" : r >= 60 ? "#d97706" : "#dc2626";
});

function levelType(l: string) {
  return l === "高" ? "danger" : l === "中" ? "warning" : l === "低" ? "info" : "success";
}

async function loadEmployees() {
  if (!userStore.isManager) return;
  const res = await userApi.page({ current: 1, size: 500, status: 1 });
  employees.value = res.records;
  if (!selectedUserId.value && res.records.length) {
    const first = res.records.find((u: any) => u.roles?.includes("EMPLOYEE"));
    selectedUserId.value = first?.id;
    if (selectedUserId.value) await loadGap();
  }
}

async function loadGap() {
  if (!selectedUserId.value) return;
  loading.value = true;
  try {
    gap.value = await skillApi.gap(selectedUserId.value);
    await nextTick();
    renderRadar();
  } catch {
    gap.value = null;
  } finally {
    loading.value = false;
  }
}

function renderRadar() {
  if (!radarRef.value) return;
  const chart = echarts.init(radarRef.value);
  chart.setOption({
    tooltip: {},
    legend: { bottom: 0, data: ["当前等级", "岗位要求"] },
    radar: {
      indicator: gap.value.radarIndicators.map((n: string) => ({ name: n, max: 5 })),
      radius: "60%",
      splitArea: { areaStyle: { color: ["#fafafa", "#fff"] } }
    },
    series: [
      {
        type: "radar",
        data: [
          {
            value: gap.value.radarCurrent,
            name: "当前等级",
            itemStyle: { color: "#f59e0b" },
            areaStyle: { opacity: 0.35 }
          },
          {
            value: gap.value.radarTarget,
            name: "岗位要求",
            itemStyle: { color: "#2563eb" },
            areaStyle: { opacity: 0.12 }
          }
        ]
      }
    ]
  });
}

async function copySummary() {
  if (!gap.value?.gapSummary) return;
  try {
    await navigator.clipboard.writeText(gap.value.gapSummary);
    ElMessage.success("已复制");
  } catch {
    ElMessage.warning("复制失败，请手动选择文本");
  }
}

onMounted(async () => {
  if (userStore.isManager) {
    await loadEmployees();
  } else {
    gap.value = await skillApi.myGap();
    await nextTick();
    renderRadar();
  }
});
</script>

<style scoped>
.chart {
  height: 340px;
}

.rank {
  display: inline-block;
  width: 22px;
  height: 22px;
  line-height: 22px;
  border-radius: 50%;
  background: #2563eb;
  color: #fff;
  font-size: 12px;
}

.text-danger {
  color: #dc2626;
  font-weight: 600;
}

.summary {
  background: #f8fafc;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 12px;
  font-size: 12.5px;
  line-height: 1.9;
  color: #374151;
  white-space: pre-wrap;
  margin: 0;
  max-height: 240px;
  overflow: auto;
}
</style>
