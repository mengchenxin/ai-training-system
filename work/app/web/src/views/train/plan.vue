<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">培训计划</div>
        <div class="sub">状态流转：草稿 → 待审核 → 已发布 → 进行中 → 已结束</div>
      </div>
      <el-button v-if="userStore.isManager" type="primary" @click="$router.push('/ai/plan')">
        <el-icon class="mr4"><MagicStick /></el-icon>AI 生成个性化计划
      </el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="计划名称/编号" clearable style="width: 200px"
          @keyup.enter="load" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
          <el-option v-for="(v, k) in STATUS" :key="k" :label="v" :value="k" />
        </el-select>
        <el-select v-model="query.planType" placeholder="类型" clearable style="width: 130px">
          <el-option v-for="(v, k) in TYPES" :key="k" :label="v" :value="k" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" size="small">
        <el-table-column prop="planNo" label="计划编号" width="160" />
        <el-table-column prop="planName" label="计划名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ TYPES[row.planType] || row.planType }}</template>
        </el-table-column>
        <el-table-column label="对象" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.targetUserName || row.targetPostName || row.targetDeptName || "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="courseCount" label="课程数" width="76" align="center" />
        <el-table-column label="周期" width="180">
          <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
        </el-table-column>
        <el-table-column label="完成率" width="150">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.finishRate)" :stroke-width="8"
              :status="row.risk ? 'exception' : undefined" />
          </template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.genType === 'MANUAL' ? 'info' : 'success'" effect="plain">
              {{ row.genType === "MANUAL" ? "人工" : row.genType === "AI_HYBRID" ? "AI混合" : "AI规则" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ STATUS[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="$router.push(`/train/plan/${row.id}`)">
              详情
            </el-button>
            <template v-if="userStore.isManager">
              <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small"
                @click="change(row, 'PENDING')">提交审核</el-button>
              <el-button v-if="row.status === 'PENDING'" link type="success" size="small"
                @click="change(row, 'PUBLISHED')">审核发布</el-button>
              <el-button v-if="row.status === 'PUBLISHED'" link type="primary" size="small"
                @click="change(row, 'RUNNING')">开始</el-button>
              <el-button v-if="row.status === 'RUNNING'" link type="success" size="small"
                @click="change(row, 'FINISHED')">结束</el-button>
              <el-button v-if="['DRAFT','PENDING'].includes(row.status)" link type="danger" size="small"
                @click="remove(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
          :page-size="query.size" :current-page="query.current"
          @current-change="(p: number) => { query.current = p; load(); }" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { planApi } from "@/api";
import { useUserStore } from "@/stores/user";

const userStore = useUserStore();
const rows = ref<any[]>([]);
const total = ref(0);
const loading = ref(false);

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

const query = reactive({
  current: 1,
  size: 10,
  keyword: "",
  status: "",
  planType: ""
});

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
    const res = await planApi.page(query);
    rows.value = res.records;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function reset() {
  query.keyword = "";
  query.status = "";
  query.planType = "";
  query.current = 1;
  load();
}

async function change(row: any, status: string) {
  const label: Record<string, string> = {
    PENDING: "提交审核",
    PUBLISHED: "审核并发布",
    RUNNING: "开始计划",
    FINISHED: "结束计划"
  };
  await ElMessageBox.confirm(`确定要${label[status]}「${row.planName}」吗？`, "提示", { type: "warning" });
  await planApi.changeStatus(row.id, status);
  ElMessage.success("操作成功");
  load();
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确定删除「${row.planName}」吗？`, "警告", { type: "warning" });
  await planApi.remove(row.id);
  ElMessage.success("已删除");
  load();
}

onMounted(load);
</script>

<style scoped>
.mr4 {
  margin-right: 4px;
  vertical-align: -2px;
}
</style>
