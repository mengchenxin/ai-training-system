<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">培训需求</div>
        <div class="sub">需求来源：技能差距自动生成 / 员工提报 / 讲师提报，经讲师审核后纳入计划</div>
      </div>
      <el-button type="primary" @click="openForm()">登记需求</el-button>
    </div>

    <div class="card mb16">
      <div class="flex-between mb12">
        <b style="font-size: 14px">需求汇总</b>
        <span class="muted" style="font-size: 12px">按部门与技能聚合，用于判断「该先给谁排课」</span>
      </div>
      <div class="stat-row">
        <div class="stat">
          <div class="stat-num">{{ summary?.total ?? 0 }}</div>
          <div class="stat-label">需求总数</div>
        </div>
        <div class="stat">
          <div class="stat-num warn">{{ pendingCount }}</div>
          <div class="stat-label">待审核</div>
        </div>
        <div class="stat">
          <div class="stat-num danger">{{ urgentCount }}</div>
          <div class="stat-label">高优先级</div>
        </div>
        <div class="stat">
          <div class="stat-num">{{ (summary?.byDept || []).length }}</div>
          <div class="stat-label">涉及部门</div>
        </div>
      </div>
      <div class="chart-row">
        <div ref="deptRef" class="chart"></div>
        <div ref="skillRef" class="chart"></div>
      </div>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="需求标题" clearable style="width: 200px"
          @keyup.enter="load" />
        <el-select v-model="query.auditStatus" placeholder="审核状态" clearable style="width: 140px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" size="small">
        <el-table-column prop="needTitle" label="需求标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="skillName" label="关联技能" width="150" show-overflow-tooltip />
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ SOURCE[row.source] || row.source }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.priority === 1 ? 'danger' : row.priority === 2 ? 'warning' : 'info'">
              {{ PRIORITY[row.priority] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="needDesc" label="需求描述" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="auditType(row.auditStatus)">{{ AUDIT[row.auditStatus] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="提报时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <template v-if="userStore.isManager && row.auditStatus === 'PENDING'">
              <el-button link type="success" size="small" @click="audit(row, 'APPROVED')">通过</el-button>
              <el-button link type="danger" size="small" @click="audit(row, 'REJECTED')">驳回</el-button>
            </template>
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
          :page-size="query.size" :current-page="query.current"
          @current-change="(p: number) => { query.current = p; load(); }" />
      </div>
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑需求' : '登记培训需求'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="需求标题">
          <el-input v-model="form.needTitle" />
        </el-form-item>
        <el-form-item label="关联技能">
          <el-select v-model="form.skillId" filterable clearable style="width: 100%">
            <el-option v-for="s in skills" :key="s.id" :label="s.skillName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="form.priority">
            <el-radio :value="1">高</el-radio>
            <el-radio :value="2">中</el-radio>
            <el-radio :value="3">低</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="需求描述">
          <el-input v-model="form.needDesc" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from "vue";
import * as echarts from "echarts";
import { ElMessage } from "element-plus";
import { skillApi, trainApi } from "@/api";
import { useUserStore } from "@/stores/user";

const userStore = useUserStore();
const rows = ref<any[]>([]);
const total = ref(0);
const skills = ref<any[]>([]);
const loading = ref(false);
const dialog = ref(false);

const summary = ref<any>(null);
const deptRef = ref<HTMLElement>();
const skillRef = ref<HTMLElement>();
const pendingCount = computed(() => {
  const item = (summary.value?.byStatus || []).find((x: any) => x.name === "PENDING");
  return item?.value ?? 0;
});
const urgentCount = computed(() => {
  const item = (summary.value?.byPriority || []).find((x: any) => x.name === "1");
  return item?.value ?? 0;
});

const SOURCE: Record<string, string> = { GAP: "差距生成", EMP: "员工提报", LECTURER: "讲师提报" };
const PRIORITY: Record<number, string> = { 1: "高", 2: "中", 3: "低" };
const AUDIT: Record<string, string> = { PENDING: "待审核", APPROVED: "已通过", REJECTED: "已驳回" };

const query = reactive({ current: 1, size: 10, keyword: "", auditStatus: "" });
const form = reactive<any>({
  id: null, needTitle: "", skillId: undefined, priority: 2, needDesc: ""
});

function auditType(s: string) {
  return s === "APPROVED" ? "success" : s === "REJECTED" ? "danger" : "warning";
}

async function loadSummary() {
  if (!userStore.isManager) return;
  summary.value = await trainApi.needSummary();
  await nextTick();
  renderBar(deptRef.value, summary.value?.byDept || [], "按部门（需求条数）", "#2563eb");
  renderBar(skillRef.value, summary.value?.bySkill || [], "按技能（需求条数）", "#0d9488");
}

function renderBar(el: HTMLElement | undefined, data: any[], title: string, color: string) {
  if (!el) return;
  const top = data.slice(0, 8).reverse();
  const chart = echarts.getInstanceByDom(el) || echarts.init(el);
  chart.setOption({
    title: { text: title, left: 0, top: 0, textStyle: { fontSize: 12, color: "#6b7280", fontWeight: "normal" } },
    grid: { left: 8, right: 24, top: 28, bottom: 8, containLabel: true },
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    xAxis: { type: "value", splitLine: { lineStyle: { color: "#f1f5f9" } }, axisLabel: { color: "#94a3b8" } },
    yAxis: {
      type: "category",
      data: top.map((x) => x.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: "#4b5563", fontSize: 11 }
    },
    series: [{
      type: "bar",
      data: top.map((x) => x.value),
      barWidth: 12,
      itemStyle: { color, borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: "right", color: "#6b7280", fontSize: 11 }
    }]
  });
}

async function load() {
  loading.value = true;
  try {
    const res = await trainApi.needPage(query);
    rows.value = res.records;
    total.value = res.total;
    await loadSummary();
  } finally {
    loading.value = false;
  }
}

function openForm(row?: any) {
  Object.assign(form, { id: null, needTitle: "", skillId: undefined, priority: 2, needDesc: "" });
  if (row) Object.assign(form, row);
  dialog.value = true;
}

async function save() {
  await trainApi.saveNeed(form);
  ElMessage.success("已保存");
  dialog.value = false;
  load();
}

async function audit(row: any, status: string) {
  await trainApi.auditNeed(row.id, status);
  ElMessage.success(status === "APPROVED" ? "已通过" : "已驳回");
  load();
}

onMounted(async () => {
  skills.value = await skillApi.list();
  load();
});
</script>

<style scoped>
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 8px;
}

.stat {
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  padding: 10px 14px;
}

.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.3;
}

.stat-num.warn {
  color: #d97706;
}

.stat-num.danger {
  color: #dc2626;
}

.stat-label {
  font-size: 12px;
  color: #94a3b8;
}

.chart-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.chart {
  width: 100%;
  height: 220px;
}
</style>
