<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">群体短板分析</div>
        <div class="sub">按技能维度汇总平均差距与未达标比例，识别需要群体化培训的共性短板</div>
      </div>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-select v-model="deptId" placeholder="部门" clearable style="width: 170px" @change="load">
          <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
        <el-select v-model="postId" placeholder="岗位" clearable style="width: 170px" @change="load">
          <el-option v-for="p in posts" :key="p.id" :label="p.postName" :value="p.id" />
        </el-select>
        <span class="muted">不筛选则统计全部员工</span>
      </div>

      <div ref="chartRef" class="chart" v-loading="loading"></div>

      <el-table :data="rows" size="small" class="mt16">
        <el-table-column prop="skillName" label="技能点" min-width="180" />
        <el-table-column prop="category" label="类别" width="90" align="center" />
        <el-table-column label="平均差距" width="120" align="center">
          <template #default="{ row }">
            <b :style="{ color: Number(row.avgGap) >= 2 ? '#dc2626' : Number(row.avgGap) >= 1 ? '#d97706' : '#16a34a' }">
              {{ row.avgGap }}
            </b>
          </template>
        </el-table-column>
        <el-table-column label="未达标人数" width="120" align="center">
          <template #default="{ row }">{{ row.belowCount }}/{{ row.totalCount }}</template>
        </el-table-column>
        <el-table-column label="未达标比例" width="200">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.belowRate)" :stroke-width="10"
              :status="Number(row.belowRate) >= 60 ? 'exception' : undefined" />
          </template>
        </el-table-column>
        <el-table-column label="建议" width="140" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="Number(row.belowRate) >= 60 ? 'danger' : Number(row.belowRate) >= 30 ? 'warning' : 'success'">
              {{ Number(row.belowRate) >= 60 ? "优先群体培训" : Number(row.belowRate) >= 30 ? "建议关注" : "个别辅导" }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from "vue";
import * as echarts from "echarts";
import { orgApi, skillApi } from "@/api";

const rows = ref<any[]>([]);
const depts = ref<any[]>([]);
const posts = ref<any[]>([]);
const deptId = ref<number>();
const postId = ref<number>();
const loading = ref(false);
const chartRef = ref<HTMLElement>();

async function load() {
  loading.value = true;
  try {
    rows.value = await skillApi.heatmap({ deptId: deptId.value, postId: postId.value });
    await nextTick();
    render();
  } finally {
    loading.value = false;
  }
}

function render() {
  if (!chartRef.value) return;
  const top = rows.value.slice(0, 12).reverse();
  echarts.init(chartRef.value).setOption({
    grid: { left: 160, right: 50, top: 20, bottom: 20 },
    tooltip: { trigger: "axis" },
    xAxis: { type: "value", name: "平均差距（级）" },
    yAxis: { type: "category", data: top.map((r: any) => r.skillName) },
    series: [
      {
        type: "bar",
        data: top.map((r: any) => Number(r.avgGap)),
        barWidth: 14,
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          color: (p: any) => (p.value >= 2 ? "#dc2626" : p.value >= 1 ? "#f59e0b" : "#16a34a")
        },
        label: { show: true, position: "right" }
      }
    ]
  });
}

onMounted(async () => {
  const [d, p] = await Promise.all([orgApi.deptList(), orgApi.postList()]);
  depts.value = d;
  posts.value = p;
  load();
});
</script>

<style scoped>
.chart {
  height: 360px;
}
</style>
