<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">培训需求</div>
        <div class="sub">需求来源：技能差距自动生成 / 员工提报 / 讲师提报，经讲师审核后纳入计划</div>
      </div>
      <el-button type="primary" @click="openForm()">登记需求</el-button>
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
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { skillApi, trainApi } from "@/api";
import { useUserStore } from "@/stores/user";

const userStore = useUserStore();
const rows = ref<any[]>([]);
const total = ref(0);
const skills = ref<any[]>([]);
const loading = ref(false);
const dialog = ref(false);

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

async function load() {
  loading.value = true;
  try {
    const res = await trainApi.needPage(query);
    rows.value = res.records;
    total.value = res.total;
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
