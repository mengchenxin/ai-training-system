<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">技能点管理</div>
        <div class="sub">技能点是岗位技能模型与员工技能档案的最小颗粒度，等级 0-5</div>
      </div>
      <el-button type="primary" @click="openForm()">新增技能点</el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="技能名称/编码" clearable style="width: 200px"
          @keyup.enter="load" />
        <el-select v-model="query.category" placeholder="类别" clearable style="width: 130px">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" size="small">
        <el-table-column prop="skillCode" label="编码" width="100" />
        <el-table-column prop="skillName" label="技能名称" width="180" />
        <el-table-column label="类别" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="catType(row.category)">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="maxLevel" label="等级上限" width="100" align="center" />
        <el-table-column prop="skillDesc" label="技能说明" min-width="360" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
          :page-size="query.size" :current-page="query.current"
          @current-change="(p: number) => { query.current = p; load(); }" />
      </div>
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑技能点' : '新增技能点'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="技能编码">
          <el-input v-model="form.skillCode" placeholder="如 SK021" />
        </el-form-item>
        <el-form-item label="技能名称">
          <el-input v-model="form.skillName" />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级上限">
          <el-input-number v-model="form.maxLevel" :min="1" :max="10" />
        </el-form-item>
        <el-form-item label="技能说明">
          <el-input v-model="form.skillDesc" type="textarea" :rows="4"
            placeholder="说明将作为知识库语料与 Prompt 上下文使用" />
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
import { ElMessage, ElMessageBox } from "element-plus";
import { skillApi } from "@/api";

const rows = ref<any[]>([]);
const total = ref(0);
const loading = ref(false);
const dialog = ref(false);
const categories = ["安全", "技术", "通用", "管理"];

const query = reactive({
  current: 1,
  size: 10,
  keyword: "",
  category: ""
});

const form = reactive<any>({
  id: null, skillCode: "", skillName: "", category: "技术", maxLevel: 5, skillDesc: ""
});

function catType(c: string) {
  return c === "安全" ? "danger" : c === "技术" ? "primary" : c === "通用" ? "success" : "warning";
}

async function load() {
  loading.value = true;
  try {
    const res = await skillApi.page(query);
    rows.value = res.records;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function openForm(row?: any) {
  Object.assign(form, { id: null, skillCode: "", skillName: "", category: "技术", maxLevel: 5, skillDesc: "" });
  if (row) Object.assign(form, row);
  dialog.value = true;
}

async function save() {
  await skillApi.save(form);
  ElMessage.success("已保存");
  dialog.value = false;
  load();
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确定删除技能点「${row.skillName}」吗？`, "提示", { type: "warning" });
  await skillApi.remove(row.id);
  ElMessage.success("已删除");
  load();
}

onMounted(load);
</script>
