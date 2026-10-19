<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">我的培训</div>
        <div class="sub">进行中的培训任务，完成后上报进度</div>
      </div>
      <div class="filter-bar" style="margin: 0">
        <el-radio-group v-model="statusFilter" @change="load">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="NOT_STARTED">未开始</el-radio-button>
          <el-radio-button value="LEARNING">学习中</el-radio-button>
          <el-radio-button value="FINISHED">已完成</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div class="card" v-loading="loading">
      <el-table :data="rows" size="small" empty-text="暂无培训任务">
        <el-table-column prop="planName" label="培训计划" min-width="180" show-overflow-tooltip />
        <el-table-column prop="courseName" label="课程" min-width="170" show-overflow-tooltip />
        <el-table-column prop="durationHours" label="建议学时" width="90" align="center" />
        <el-table-column label="截止日期" width="120">
          <template #default="{ row }">
            <span :class="{ overdue: row.overdue }">{{ row.endDate || "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="160">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.progressPercent)"
              :status="row.status === 'FINISHED' ? 'success' : row.overdue ? 'exception' : undefined"
              :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="spentHours" label="已投入" width="80" align="center" />
        <el-table-column prop="reportTime" label="最近上报" width="160" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.planStatus !== 'FINISHED'" link type="primary" size="small"
              @click="openReport(row)">上报进度</el-button>
            <span v-else class="muted">已归档</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="上报学习进度" width="420px">
      <el-form label-width="90px">
        <el-form-item label="课程">
          <span>{{ current?.courseName }}</span>
        </el-form-item>
        <el-form-item label="完成进度">
          <el-slider v-model="form.progressPercent" :step="5" show-input />
        </el-form-item>
        <el-form-item label="投入学时">
          <el-input-number v-model="form.spentHours" :min="0" :max="200" :step="0.5" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { progressApi } from "@/api";

const rows = ref<any[]>([]);
const loading = ref(false);
const statusFilter = ref("");
const dialog = ref(false);
const saving = ref(false);
const current = ref<any>(null);

const form = reactive({
  progressPercent: 0,
  spentHours: 0,
  remark: ""
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
    rows.value = await progressApi.my(statusFilter.value || undefined);
  } finally {
    loading.value = false;
  }
}

function openReport(row: any) {
  current.value = row;
  form.progressPercent = Number(row.progressPercent || 0);
  form.spentHours = Number(row.spentHours || 0);
  form.remark = "";
  dialog.value = true;
}

async function submit() {
  saving.value = true;
  try {
    await progressApi.report({
      id: current.value.id,
      progressPercent: form.progressPercent,
      spentHours: form.spentHours,
      remark: form.remark
    });
    ElMessage.success("进度已上报");
    dialog.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.overdue {
  color: #dc2626;
  font-weight: 600;
}
</style>
