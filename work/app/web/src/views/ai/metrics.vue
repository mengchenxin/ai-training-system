<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">AI 效果指标</div>
        <div class="sub">结构合规率、方案采纳率、平均耗时等量化指标，直接支撑论文实验章节</div>
      </div>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="stat-grid" v-loading="loading">
      <div class="stat-card">
        <div class="label">方案生成总次数</div>
        <div class="value">{{ metrics?.total ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="label">大模型调用次数</div>
        <div class="value">{{ metrics?.llmCalls ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="label">结构合规率</div>
        <div class="value" style="color: #16a34a">
          <template v-if="metrics?.schemaPassRate === null || metrics?.schemaPassRate === undefined">
            <span class="muted" style="font-size: 20px">—</span>
          </template>
          <template v-else>{{ metrics.schemaPassRate }}<span class="unit">%</span></template>
        </div>
      </div>
      <div class="stat-card">
        <div class="label">方案采纳率</div>
        <div class="value" style="color: #2563eb">
          {{ metrics?.adoptRate ?? 0 }}<span class="unit">%</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="label">规则降级率</div>
        <div class="value" style="color: #f59e0b">
          {{ metrics?.fallbackRate ?? 0 }}<span class="unit">%</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="label">RAG 命中率</div>
        <div class="value">{{ metrics?.ragHitRate ?? 0 }}<span class="unit">%</span></div>
      </div>
      <div class="stat-card">
        <div class="label">平均生成耗时</div>
        <div class="value">{{ metrics?.avgCostMs ?? 0 }}<span class="unit">ms</span></div>
      </div>
      <div class="stat-card">
        <div class="label">平均重试次数</div>
        <div class="value">{{ metrics?.avgAttempts ?? 0 }}</div>
      </div>
    </div>

    <el-alert v-if="!metrics?.llmCalls" type="info" :closable="false" show-icon class="mb16"
      title="当前为纯规则降级模式（未配置 DEEPSEEK_API_KEY）"
      description="规则引擎独立产出完整方案，结构合规率不适用因此显示为「—」。配置 API Key 后自动切换为「规则约束 + 大模型生成说明」混合模式，该指标开始生效。" />

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="card">
          <b>生成状态分布</b>
          <div ref="statusRef" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="card">
          <b>人工采纳分布</b>
          <div ref="adoptRef" class="chart"></div>
        </div>
      </el-col>
    </el-row>

    <div class="card">
      <div class="flex-between mb16">
        <b>AI 生成记录</b>
        <el-select v-model="bizType" size="small" style="width: 150px" clearable placeholder="业务类型"
          @change="loadRecords">
          <el-option label="方案生成" value="PLAN_GEN" />
          <el-option label="效果诊断" value="EFFECT_DIAG" />
        </el-select>
      </div>
      <el-table :data="records" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="bizType" label="业务" width="120" />
        <el-table-column prop="model" label="模型" width="140" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)" effect="plain">
              {{ STATUS[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="采纳" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="adoptType(row.adoptStatus)" effect="plain">
              {{ ADOPT[row.adoptStatus] || row.adoptStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="promptTokens" label="输入tokens" width="100" align="center" />
        <el-table-column prop="completionTokens" label="输出tokens" width="100" align="center" />
        <el-table-column prop="costMs" label="耗时(ms)" width="90" align="center" />
        <el-table-column prop="attemptCount" label="尝试" width="70" align="center" />
        <el-table-column prop="createTime" label="时间" width="170" />
      </el-table>
      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
          :page-size="10" :current-page="current"
          @current-change="(p: number) => { current = p; loadRecords(); }" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from "vue";
import * as echarts from "echarts";
import { aiApi } from "@/api";

const metrics = ref<any>(null);
const records = ref<any[]>([]);
const total = ref(0);
const current = ref(1);
const bizType = ref("");
const loading = ref(false);
const statusRef = ref<HTMLElement>();
const adoptRef = ref<HTMLElement>();

const STATUS: Record<string, string> = {
  SUCCESS: "成功",
  FALLBACK: "降级规则",
  SCHEMA_FAILED: "结构校验失败",
  API_ERROR: "接口异常"
};
const ADOPT: Record<string, string> = {
  PENDING: "待确认",
  ADOPTED: "已采纳",
  MODIFIED: "修改后采纳",
  REJECTED: "已驳回"
};

function statusType(s: string) {
  return s === "SUCCESS" ? "success" : s === "FALLBACK" ? "warning" : "danger";
}
function adoptType(s: string) {
  return s === "ADOPTED" ? "success" : s === "MODIFIED" ? "primary" : s === "REJECTED" ? "danger" : "info";
}

async function load() {
  loading.value = true;
  try {
    metrics.value = await aiApi.metrics();
    await nextTick();
    renderPies();
    await loadRecords();
  } finally {
    loading.value = false;
  }
}

async function loadRecords() {
  const res = await aiApi.recordPage({ current: current.value, size: 10, bizType: bizType.value });
  records.value = res.records;
  total.value = res.total;
}

function renderPies() {
  const sd = metrics.value?.statusDist || {};
  const ad = metrics.value?.adoptDist || {};
  if (statusRef.value) {
    echarts.init(statusRef.value).setOption({
      tooltip: { trigger: "item" },
      color: ["#16a34a", "#f59e0b", "#ef4444", "#7f1d1d"],
      series: [
        {
          type: "pie",
          radius: ["42%", "66%"],
          label: { formatter: "{b}\n{c}" },
          data: Object.entries(sd).map(([k, v]) => ({ name: STATUS[k] || k, value: v }))
        }
      ]
    });
  }
  if (adoptRef.value) {
    echarts.init(adoptRef.value).setOption({
      tooltip: { trigger: "item" },
      color: ["#94a3b8", "#16a34a", "#2563eb", "#ef4444"],
      series: [
        {
          type: "pie",
          radius: ["42%", "66%"],
          label: { formatter: "{b}\n{c}" },
          data: Object.entries(ad).map(([k, v]) => ({ name: ADOPT[k] || k, value: v }))
        }
      ]
    });
  }
}

onMounted(load);
</script>

<style scoped>
.chart {
  height: 280px;
  margin-top: 12px;
}
</style>
