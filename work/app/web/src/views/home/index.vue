<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">工作台</div>
        <div class="sub">{{ greeting }}，{{ userStore.info?.realName }} · {{ userStore.info?.postName || "未分配岗位" }}</div>
      </div>
      <el-tag v-if="aiStatus" :type="aiStatus.llmAvailable ? 'success' : 'warning'" effect="light">
        {{ aiStatus.mode }}
      </el-tag>
    </div>

    <!-- 员工视角：个人技能达标情况 -->
    <template v-if="userStore.isEmployee">
      <div class="stat-grid" v-loading="loading">
        <div class="stat-card">
          <div class="label">岗位技能达标率</div>
          <div class="value" :style="{ color: rateColor }">
            {{ myGap?.matchRate ?? "-" }}<span class="unit">%</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="label">待补齐技能项</div>
          <div class="value">{{ myGap?.gapCount ?? "-" }}<span class="unit">项</span></div>
        </div>
        <div class="stat-card">
          <div class="label">加权总差距</div>
          <div class="value">{{ myGap?.totalWeightedGap ?? "-" }}</div>
        </div>
        <div class="stat-card">
          <div class="label">待学习课程</div>
          <div class="value">{{ tasks.length }}<span class="unit">门</span></div>
        </div>
      </div>

      <div class="card">
        <div class="flex-between mb16">
          <b>我的培训任务</b>
          <el-button text type="primary" @click="$router.push('/progress/my')">全部</el-button>
        </div>
        <el-table :data="tasks.slice(0, 5)" size="small" empty-text="暂无培训任务">
          <el-table-column prop="planName" label="培训计划" min-width="180" show-overflow-tooltip />
          <el-table-column prop="courseName" label="课程" min-width="160" show-overflow-tooltip />
          <el-table-column label="进度" width="160">
            <template #default="{ row }">
              <el-progress :percentage="Number(row.progressPercent || 0)" :stroke-width="8" />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="card">
        <div class="flex-between mb16">
          <b>我的技能画像</b>
          <el-button text type="primary" @click="$router.push('/skill/gap')">查看差距分析</el-button>
        </div>
        <div ref="chartRef" class="chart"></div>
      </div>
    </template>

    <!-- 管理视角：全局统计 -->
    <template v-else>
      <div class="stat-grid" v-loading="loading">
        <div class="stat-card">
          <div class="label">培训计划总数</div>
          <div class="value">{{ dash?.planTotal ?? 0 }}</div>
        </div>
        <div class="stat-card">
          <div class="label">进行中计划</div>
          <div class="value" style="color: #2563eb">{{ dash?.planRunning ?? 0 }}</div>
        </div>
        <div class="stat-card">
          <div class="label">整体完成率</div>
          <div class="value" style="color: #16a34a">
            {{ dash?.overallFinishRate ?? 0 }}<span class="unit">%</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="label">累计学习学时</div>
          <div class="value">{{ dash?.learnHours ?? 0 }}<span class="unit">h</span></div>
        </div>
        <div class="stat-card">
          <div class="label">拖期风险计划</div>
          <div class="value" style="color: #dc2626">{{ risks.length }}</div>
        </div>
      </div>

      <el-row :gutter="16">
        <el-col :span="12">
          <div class="card">
            <b>各部门完成率</b>
            <div ref="chartRef" class="chart"></div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="card">
            <b>课程完成状态分布</b>
            <div ref="pieRef" class="chart"></div>
          </div>
        </el-col>
      </el-row>

      <div class="card">
        <div class="flex-between mb16">
          <b>拖期风险提醒</b>
          <el-button text type="primary" @click="$router.push('/progress/dashboard')">进度看板</el-button>
        </div>
        <el-table :data="risks" size="small" empty-text="暂无风险计划">
          <el-table-column prop="planName" label="计划名称" min-width="200" />
          <el-table-column prop="endDate" label="截止日期" width="120" />
          <el-table-column label="完成率" width="150">
            <template #default="{ row }">
              <el-progress :percentage="Number(row.finishRate)" :stroke-width="8"
                :status="Number(row.finishRate) < 40 ? 'exception' : 'warning'" />
            </template>
          </el-table-column>
          <el-table-column label="时间进度" width="110">
            <template #default="{ row }">{{ row.timeProgress }}%</template>
          </el-table-column>
          <el-table-column label="风险" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.riskLevel === '高' ? 'danger' : 'warning'">
                {{ row.riskLevel }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, nextTick } from "vue";
import * as echarts from "echarts";
import { useUserStore } from "@/stores/user";
import { aiApi, progressApi, skillApi } from "@/api";

const userStore = useUserStore();
const loading = ref(false);
const dash = ref<any>(null);
const risks = ref<any[]>([]);
const tasks = ref<any[]>([]);
const myGap = ref<any>(null);
const aiStatus = ref<any>(null);
const chartRef = ref<HTMLElement>();
const pieRef = ref<HTMLElement>();

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return "凌晨好";
  if (h < 12) return "上午好";
  if (h < 18) return "下午好";
  return "晚上好";
});

const rateColor = computed(() => {
  const r = Number(myGap.value?.matchRate || 0);
  return r >= 80 ? "#16a34a" : r >= 60 ? "#d97706" : "#dc2626";
});

function statusType(s: string) {
  return s === "FINISHED" ? "success" : s === "LEARNING" ? "primary" : "info";
}
function statusText(s: string) {
  return s === "FINISHED" ? "已完成" : s === "LEARNING" ? "学习中" : "未开始";
}

async function load() {
  loading.value = true;
  try {
    aiStatus.value = await aiApi.status();
    if (userStore.isEmployee) {
      const [gap, my] = await Promise.all([
        skillApi.myGap().catch(() => null),
        progressApi.my()
      ]);
      myGap.value = gap;
      tasks.value = my.filter((t: any) => t.status !== "FINISHED");
      await nextTick();
      renderRadar();
    } else {
      const [d, r] = await Promise.all([progressApi.dashboard(), progressApi.risk()]);
      dash.value = d;
      risks.value = r;
      await nextTick();
      renderDeptBar();
      renderPie();
    }
  } finally {
    loading.value = false;
  }
}

function renderRadar() {
  if (!chartRef.value || !myGap.value) return;
  const chart = echarts.init(chartRef.value);
  chart.setOption({
    radar: {
      indicator: (myGap.value.radarIndicators || []).map((n: string) => ({ name: n, max: 5 })),
      radius: "62%"
    },
    legend: { bottom: 0, data: ["当前等级", "岗位要求"] },
    series: [
      {
        type: "radar",
        data: [
          { value: myGap.value.radarCurrent, name: "当前等级", areaStyle: { opacity: 0.25 } },
          { value: myGap.value.radarTarget, name: "岗位要求", areaStyle: { opacity: 0.1 } }
        ]
      }
    ]
  });
}

function renderDeptBar() {
  if (!chartRef.value) return;
  const list = dash.value?.deptStats || [];
  const chart = echarts.init(chartRef.value);
  chart.setOption({
    grid: { left: 90, right: 30, top: 20, bottom: 20 },
    xAxis: { type: "value", max: 100, axisLabel: { formatter: "{value}%" } },
    yAxis: { type: "category", data: list.map((d: any) => d.deptName) },
    tooltip: { trigger: "axis" },
    series: [
      {
        type: "bar",
        data: list.map((d: any) => Number(d.finishRate)),
        barWidth: 16,
        itemStyle: { color: "#2563eb", borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: "right", formatter: "{c}%" }
      }
    ]
  });
}

function renderPie() {
  if (!pieRef.value) return;
  const chart = echarts.init(pieRef.value);
  chart.setOption({
    tooltip: { trigger: "item" },
    legend: { bottom: 0 },
    color: ["#94a3b8", "#f59e0b", "#16a34a"],
    series: [
      {
        type: "pie",
        radius: ["42%", "65%"],
        data: dash.value?.distribution || [],
        label: { formatter: "{b}\n{c} 条" }
      }
    ]
  });
}

onMounted(load);
</script>

<style scoped>
.chart {
  height: 300px;
  margin-top: 12px;
}
</style>
