<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">题库管理</div>
        <div class="sub">题目按技能点组织，供自动组卷抽取使用</div>
      </div>
      <el-button type="primary" @click="openForm()">新增题目</el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-select v-model="query.skillId" placeholder="技能点" clearable filterable style="width: 180px">
          <el-option v-for="s in skills" :key="s.id" :label="s.skillName" :value="s.id" />
        </el-select>
        <el-select v-model="query.questionType" placeholder="题型" clearable style="width: 120px">
          <el-option label="单选" value="SINGLE" />
          <el-option label="多选" value="MULTI" />
          <el-option label="判断" value="JUDGE" />
          <el-option label="简答" value="SHORT" />
        </el-select>
        <el-select v-model="query.difficulty" placeholder="难度" clearable style="width: 110px">
          <el-option label="易" :value="1" />
          <el-option label="中" :value="2" />
          <el-option label="难" :value="3" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="skillName" label="技能点" width="150" show-overflow-tooltip />
        <el-table-column label="题型" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ TYPE[row.questionType] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="题干" min-width="320" show-overflow-tooltip />
        <el-table-column label="难度" width="70" align="center">
          <template #default="{ row }">{{ DIFF[row.difficulty] }}</template>
        </el-table-column>
        <el-table-column prop="score" label="分值" width="66" align="center" />
        <el-table-column prop="answer" label="答案" width="100" show-overflow-tooltip />
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

    <el-dialog v-model="dialog" :title="form.id ? '编辑题目' : '新增题目'" width="720px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="技能点">
          <el-select v-model="form.skillId" filterable style="width: 100%">
            <el-option v-for="s in skills" :key="s.id" :label="s.skillName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型">
          <el-radio-group v-model="form.questionType">
            <el-radio v-for="(v, k) in TYPE" :key="k" :value="k">{{ v }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题干">
          <el-input v-model="form.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item v-if="form.questionType !== 'SHORT'" label="选项">
          <el-input v-model="form.options" type="textarea" :rows="4"
            placeholder='JSON 数组，如 [{"key":"A","text":"选项A"},{"key":"B","text":"选项B"}]' />
        </el-form-item>
        <el-form-item label="答案">
          <el-input v-model="form.answer" :placeholder="form.questionType === 'SHORT' ? '参考答案' : '如 A 或 A,C'" />
        </el-form-item>
        <el-form-item v-if="form.questionType === 'SHORT'" label="评分要点">
          <el-input v-model="form.analysis" type="textarea" :rows="2" />
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="分值">
              <el-input-number v-model="form.score" :min="1" :max="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="难度">
              <el-select v-model="form.difficulty" style="width: 100%">
                <el-option label="易" :value="1" />
                <el-option label="中" :value="2" />
                <el-option label="难" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { examApi, skillApi } from "@/api";

const rows = ref<any[]>([]);
const total = ref(0);
const skills = ref<any[]>([]);
const loading = ref(false);
const dialog = ref(false);
const saving = ref(false);

const TYPE: Record<string, string> = { SINGLE: "单选", MULTI: "多选", JUDGE: "判断", SHORT: "简答" };
const DIFF: Record<number, string> = { 1: "易", 2: "中", 3: "难" };

const query = reactive({
  current: 1,
  size: 10,
  skillId: undefined as number | undefined,
  questionType: "",
  difficulty: undefined as number | undefined
});

const form = reactive<any>({
  id: null,
  skillId: undefined,
  questionType: "SINGLE",
  content: "",
  options: "",
  answer: "",
  analysis: "",
  score: 5,
  difficulty: 2
});

async function load() {
  loading.value = true;
  try {
    const res = await examApi.questionPage(query);
    rows.value = res.records;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function openForm(row?: any) {
  Object.assign(form, {
    id: null,
    skillId: undefined,
    questionType: "SINGLE",
    content: "",
    options: "",
    answer: "",
    analysis: "",
    score: 5,
    difficulty: 2
  });
  if (row) Object.assign(form, row);
  dialog.value = true;
}

async function save() {
  saving.value = true;
  try {
    await examApi.saveQuestion(form);
    ElMessage.success("已保存");
    dialog.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm("确定删除该题目吗？", "提示", { type: "warning" });
  await examApi.removeQuestion(row.id);
  ElMessage.success("已删除");
  load();
}

onMounted(async () => {
  skills.value = await skillApi.list();
  load();
});
</script>
