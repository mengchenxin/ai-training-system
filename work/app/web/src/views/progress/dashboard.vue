<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">培训进度看板</div>
        <div class="sub">按部门 / 状态统计完成率，识别拖期风险计划</div>
      </div>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="stat-grid" v-loading="loading">
      <div class="stat-card">
        <div class="label">计划总数</div>
        <div class="value">{{ dash?.planTotal ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="label">进行中</div>
        <div class="value" style="color: #2563eb">{{ dash?.planRunning ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="label">已结束</div>
        <div class="value" style="color: #16a34a">{{ dash?.planFinished ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="label">整体完成率</div>
        <div class="value">{{ dash?.overallFinishRate ?? 0 }}<span class="unit">%</span></div>
      </div>
      <div class="stat-card">
        <div class="label">课程完成 / 总数</div>
        <div class="value" style="font-size: 22px">
          {{ dash?.courseRecordFinished ?? 0 }}<span class="unit">/ {{ dash?.courseRecordTotal ?? 0 }}</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="label">累计学时</div>
        <div class="value">{{ dash?.learnHours ?? 0 }}<span class="unit">h</span></div>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="13">
        <div class="card">
          <b>各部门完成率</b>
          <div ref="barRef" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="11">
        <div class="card">
          <b>课程状态分布</b>
          <div ref="pieRef" class="chart"></div>
        </div>
      </el-col>
    </el-row>

    <div class="card">
      <div class="flex-between mb16">
        <b>拖期风险计划</b>
        <span class="muted">判定：完成率低于时间进度 20 个百分点以上</span>
      </div>
      <el-table :data="risks" size="small" empty-text="暂无风险计划">
        <el-table-column prop="planName" label="计划名称" min-width="220" />
        <el-table-column prop="endDate" label="截止日期" width="120" />
        <el-table-column label="完成率" width="160">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.finishRate)" :stroke-width="8" status="exception" />
          </template>
        </el-table-column>
        <el-table-column label="时间进度" width="160">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.timeProgress)" :stroke-width="8" color="#94a3b8" />
          </template>
        </el-table-column>
        <el-table-column label="落后" width="100" align="center">
          <template #default="{ row }">
            <span class="text-danger">{{ row.riskValue }} 个百分点</span>
          </template>
        </el-table-column>
        <el-table-column label="风险等级" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.riskLevel === '高' ? 'danger' : 'warning'" size="small">
              {{ row.riskLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="$router.push(`/train/plan/${row.planId}`)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from "vue";
import * as echarts from "echarts";
import { progressApi } from "@/api";

const dash = ref<any>(null);
const risks = ref<any[]>([]);
const loading = ref(false);
const barRef = ref<HTMLElement>();
const pieRef = ref<HTMLElement>();

async function load() {
  loading.value = true;
  try {
    const [d, r] = await Promise.all([progressApi.dashboard(), progressApi.risk()]);
    dash.value = d;
    risks.value = r;
    await nextTick();
    renderBar();
    renderPie();
  } finally {
    loading.value = false;
  }
}

function renderBar() {
  if (!barRef.value) return;
  const list = dash.value?.deptStats || [];
  echarts.init(barRef.value).setOption({
    grid: { left: 100, right: 40, top: 20, bottom: 20 },
    tooltip: { trigger: "axis" },
    xAxis: { type: "value", max: 100, axisLabel: { formatter: "{value}%" } },
    yAxis: { type: "category", data: list.map((d: any) => d.deptName) },
    series: [
      {
        type: "bar",
        data: list.map((d: any) => Number(d.finishRate)),
        barWidth: 18,
        itemStyle: { color: "#2563eb", borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: "right", formatter: "{c}%" }
      }
    ]
  });
}

function renderPie() {
  if (!pieRef.value) return;
  echarts.init(pieRef.value).setOption({
    tooltip: { trigger: "item" },
    legend: { bottom: 0 },
    color: ["#94a3b8", "#f59e0b", "#16a34a"],
    series: [
      {
        type: "pie",
        radius: ["45%", "68%"],
        data: dash.value?.distribution || [],
        label: { formatter: "{b} {c}" }
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

.text-danger {
  color: #dc2626;
  font-weight: 600;
}
</style>
