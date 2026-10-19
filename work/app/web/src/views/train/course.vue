<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">课程库</div>
        <div class="sub">课程按技能点组织，课程等级与学时是规则排期的选课依据</div>
      </div>
      <el-button v-if="userStore.isManager" type="primary" @click="openForm()">新增课程</el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="课程名称/编码" clearable style="width: 200px"
          @keyup.enter="load" />
        <el-select v-model="query.skillId" placeholder="技能点" clearable filterable style="width: 180px">
          <el-option v-for="s in skills" :key="s.id" :label="s.skillName" :value="s.id" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" size="small">
        <el-table-column prop="courseCode" label="编码" width="90" />
        <el-table-column prop="courseName" label="课程名称" min-width="220" />
        <el-table-column prop="skillName" label="覆盖技能" width="150" show-overflow-tooltip />
        <el-table-column prop="category" label="类别" width="80" align="center" />
        <el-table-column prop="level" label="等级" width="70" align="center" />
        <el-table-column prop="durationHours" label="学时" width="80" align="center" />
        <el-table-column prop="intro" label="课程简介" min-width="320" show-overflow-tooltip />
        <el-table-column v-if="userStore.isManager" label="操作" width="120" fixed="right">
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

    <el-dialog v-model="dialog" :title="form.id ? '编辑课程' : '新增课程'" width="600px">
      <el-form :model="form" label-width="90px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="课程编码">
              <el-input v-model="form.courseCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="覆盖技能">
              <el-select v-model="form.skillId" filterable style="width: 100%">
                <el-option v-for="s in skills" :key="s.id" :label="s.skillName" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="课程名称">
          <el-input v-model="form.courseName" />
        </el-form-item>
        <el-row>
          <el-col :span="8">
            <el-form-item label="课程等级">
              <el-input-number v-model="form.level" :min="1" :max="5" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="建议学时">
              <el-input-number v-model="form.durationHours" :min="1" :max="100" :step="0.5" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="类别">
              <el-input v-model="form.category" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="课程简介">
          <el-input v-model="form.intro" type="textarea" :rows="4"
            placeholder="简介同时作为知识库语料，影响 RAG 检索质量" />
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
import { skillApi, trainApi } from "@/api";
import { useUserStore } from "@/stores/user";

const userStore = useUserStore();
const rows = ref<any[]>([]);
const total = ref(0);
const skills = ref<any[]>([]);
const loading = ref(false);
const dialog = ref(false);

const query = reactive({ current: 1, size: 10, keyword: "", skillId: undefined as number | undefined });
const form = reactive<any>({
  id: null, courseCode: "", courseName: "", skillId: undefined,
  category: "技术", level: 2, durationHours: 8, intro: ""
});

async function load() {
  loading.value = true;
  try {
    const res = await trainApi.coursePage(query);
    rows.value = res.records;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function openForm(row?: any) {
  Object.assign(form, {
    id: null, courseCode: "", courseName: "", skillId: undefined,
    category: "技术", level: 2, durationHours: 8, intro: ""
  });
  if (row) Object.assign(form, row);
  dialog.value = true;
}

async function save() {
  await trainApi.saveCourse(form);
  ElMessage.success("已保存");
  dialog.value = false;
  load();
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确定删除课程「${row.courseName}」吗？`, "提示", { type: "warning" });
  await trainApi.removeCourse(row.id);
  ElMessage.success("已删除");
  load();
}

onMounted(async () => {
  skills.value = await skillApi.list();
  load();
});
</script>
