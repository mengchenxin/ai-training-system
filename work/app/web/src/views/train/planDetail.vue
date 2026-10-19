<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div>
        <div class="title">{{ detail?.plan?.planName }}</div>
        <div class="sub">
          编号 {{ detail?.plan?.planNo }} ·
          {{ TYPES[detail?.plan?.planType] }}计划 ·
          {{ detail?.plan?.startDate }} ~ {{ detail?.plan?.endDate }}
        </div>
      </div>
      <div>
        <el-button @click="$router.back()">返回</el-button>
        <el-button v-if="userStore.isManager" type="primary" @click="$router.push('/ai/effect')">
          效果诊断
        </el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="label">计划状态</div>
        <div class="value" style="font-size: 20px">
          <el-tag :type="statusType(detail?.plan?.status)" effect="dark">
            {{ STATUS[detail?.plan?.status] }}
          </el-tag>
        </div>
      </div>
      <div class="stat-card">
        <div class="label">整体完成率</div>
        <div class="value" style="color: #2563eb">
          {{ detail?.finishRate ?? 0 }}<span class="unit">%</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="label">参与人数</div>
        <div class="value">{{ detail?.userCount ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="label">已完成人次</div>
        <div class="value" style="color: #16a34a">{{ detail?.finishedUserCount ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="label">方案来源</div>
        <div class="value" style="font-size: 16px; padding-top: 8px">
          {{ GEN[detail?.plan?.genType] || detail?.plan?.genType }}
        </div>
      </div>
    </div>

    <div class="card">
      <b>课程编排</b>
      <el-table :data="detail?.courseStats || []" size="small" class="mt16">
        <el-table-column prop="seqNo" label="顺序" width="60" align="center" />
        <el-table-column prop="courseName" label="课程" min-width="180" />
        <el-table-column prop="coverSkillName" label="覆盖技能" width="150" />
        <el-table-column prop="gapBefore" label="安排时差距" width="100" align="center">
          <template #default="{ row }">{{ row.gapBefore ?? "-" }}</template>
        </el-table-column>
        <el-table-column prop="expectHours" label="计划学时" width="90" align="center" />
        <el-table-column label="平均进度" width="150">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.avgProgress)" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="完成人数" width="100" align="center">
          <template #default="{ row }">{{ row.finishedCount }}/{{ row.totalCount }}</template>
        </el-table-column>
        <el-table-column prop="planReason" label="安排依据" min-width="280">
          <template #default="{ row }">
            <span class="reason">{{ row.planReason || "-" }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="userStore.isManager" class="card">
      <div class="flex-between mb16">
        <b>学员进度明细</b>
        <el-button size="small" @click="loadProgress">刷新</el-button>
      </div>
      <el-table :data="progressRows" size="small" max-height="420">
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="deptName" label="部门" width="140" />
        <el-table-column prop="courseName" label="课程" min-width="170" show-overflow-tooltip />
        <el-table-column label="进度" width="150">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.progressPercent)" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'FINISHED' ? 'success' : row.status === 'LEARNING' ? 'primary' : 'info'">
              {{ row.status === "FINISHED" ? "已完成" : row.status === "LEARNING" ? "学习中" : "未开始" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="spentHours" label="已投入" width="80" align="center" />
        <el-table-column prop="reportTime" label="最近上报" width="160" />
      </el-table>
      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="progressTotal"
          :page-size="20" :current-page="progressPage"
          @current-change="(p: number) => { progressPage = p; loadProgress(); }" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { planApi, progressApi } from "@/api";
import { useUserStore } from "@/stores/user";

const userStore = useUserStore();
const route = useRoute();
const detail = ref<any>(null);
const loading = ref(false);
const progressRows = ref<any[]>([]);
const progressTotal = ref(0);
const progressPage = ref(1);

const STATUS: Record<string, string> = {
  DRAFT: "草稿",
  PENDING: "待审核",
  PUBLISHED: "已发布",
  RUNNING: "进行中",
  FINISHED: "已结束",
  CANCELED: "已取消"
};
const TYPES: Record<string, string> = {
  PERSONAL: "个人",
  DEPT: "部门",
  POST: "岗位",
  BATCH: "批量"
};
const GEN: Record<string, string> = {
  MANUAL: "人工编排",
  AI_RULE: "AI 规则方案",
  AI_LLM: "AI 大模型方案",
  AI_HYBRID: "AI 规则+大模型"
};

function statusType(s: string) {
  const map: Record<string, string> = {
    DRAFT: "info",
    PENDING: "warning",
    PUBLISHED: "primary",
    RUNNING: "success",
    FINISHED: "",
    CANCELED: "danger"
  };
  return (map[s] || "info") as any;
}

async function load() {
  loading.value = true;
  try {
    detail.value = await planApi.detail(Number(route.params.id));
    if (userStore.isManager) await loadProgress();
  } finally {
    loading.value = false;
  }
}

async function loadProgress() {
  const res = await progressApi.planProgress(Number(route.params.id), {
    current: progressPage.value,
    size: 20
  });
  progressRows.value = res.records;
  progressTotal.value = res.total;
}

onMounted(load);
</script>

<style scoped>
.reason {
  font-size: 12.5px;
  color: #4b5563;
  line-height: 1.7;
}
</style>
