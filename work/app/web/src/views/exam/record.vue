<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">考试成绩</div>
        <div class="sub">{{ userStore.isManager ? "全体学员成绩记录，用于前后测对比" : "我的考试记录" }}</div>
      </div>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="card">
      <el-table :data="rows" v-loading="loading" size="small" empty-text="暂无成绩记录">
        <el-table-column v-if="userStore.isManager" prop="realName" label="姓名" width="100" />
        <el-table-column prop="paperName" label="试卷" min-width="200" show-overflow-tooltip />
        <el-table-column label="阶段" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.examStage === 'PRE' ? 'info' : 'success'" effect="plain">
              {{ row.examStage === "PRE" ? "培训前测" : "培训后测" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="objectiveScore" label="客观题" width="90" align="center" />
        <el-table-column prop="subjectiveScore" label="主观题" width="90" align="center" />
        <el-table-column label="总分" width="90" align="center">
          <template #default="{ row }">
            <b :style="{ color: row.passed === 1 ? '#16a34a' : '#dc2626' }">{{ row.totalScore }}</b>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.passed === 1 ? 'success' : 'danger'">
              {{ row.passed === 1 ? "通过" : "未通过" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitTime" label="交卷时间" width="170" />
        <el-table-column prop="aiComment" label="AI 评分说明" min-width="220" show-overflow-tooltip />
      </el-table>
      <div v-if="userStore.isManager" class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
          :page-size="10" :current-page="current"
          @current-change="(p: number) => { current = p; load(); }" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { examApi } from "@/api";
import { useUserStore } from "@/stores/user";

const userStore = useUserStore();
const rows = ref<any[]>([]);
const total = ref(0);
const current = ref(1);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    if (userStore.isManager) {
      const res = await examApi.recordPage({ current: current.value, size: 10 });
      rows.value = res.records;
      total.value = res.total;
    } else {
      rows.value = await examApi.myRecords();
      total.value = rows.value.length;
    }
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
