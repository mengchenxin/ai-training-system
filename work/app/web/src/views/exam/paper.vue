<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">试卷与组卷</div>
        <div class="sub">按技能范围与难度分布自动组卷，也可手工指定题目</div>
      </div>
      <el-button type="primary" @click="genDialog = true">自动组卷</el-button>
    </div>

    <div class="card">
      <el-table :data="rows" v-loading="loading" size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="paperName" label="试卷名称" min-width="220" />
        <el-table-column prop="questionCount" label="题量" width="80" align="center" />
        <el-table-column prop="totalScore" label="总分" width="80" align="center" />
        <el-table-column prop="passScore" label="及格线" width="80" align="center" />
        <el-table-column label="组卷方式" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.genType === "AI_BY_SKILL" ? "按技能自动" : "人工" }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="$router.push(`/exam/take/${row.id}`)">
              去作答
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
          :page-size="10" :current-page="current"
          @current-change="(p: number) => { current = p; load(); }" />
      </div>
    </div>

    <el-dialog v-model="genDialog" title="自动组卷" width="640px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="试卷名称">
          <el-input v-model="form.paperName" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="技能范围">
          <el-select v-model="form.skillIds" multiple filterable style="width: 100%"
            placeholder="选择覆盖的技能点">
            <el-option v-for="s in skills" :key="s.id" :label="s.skillName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-row>
          <el-col :span="8">
            <el-form-item label="易题数">
              <el-input-number v-model="form.easyCount" :min="0" :max="50" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="中等题数">
              <el-input-number v-model="form.mediumCount" :min="0" :max="50" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="难题数">
              <el-input-number v-model="form.hardCount" :min="0" :max="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="总分">
              <el-input-number v-model="form.totalScore" :min="10" :max="200" :step="10" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="及格线">
              <el-input-number v-model="form.passScore" :min="10" :max="200" :step="10" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert type="info" :closable="false" show-icon
          title="简答题会自动纳入。若某难度题量不足，将按实际可用题目数量组卷。" />
      </el-form>
      <template #footer>
        <el-button @click="genDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="generate">生成试卷</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { examApi, skillApi } from "@/api";

const router = useRouter();
const rows = ref<any[]>([]);
const total = ref(0);
const current = ref(1);
const skills = ref<any[]>([]);
const loading = ref(false);
const genDialog = ref(false);
const saving = ref(false);

const form = reactive<any>({
  paperName: "",
  skillIds: [],
  easyCount: 4,
  mediumCount: 3,
  hardCount: 2,
  totalScore: 100,
  passScore: 60
});

async function load() {
  loading.value = true;
  try {
    const res = await examApi.paperPage({ current: current.value, size: 10 });
    rows.value = res.records;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

async function generate() {
  if (!form.skillIds.length) {
    ElMessage.warning("请选择技能范围");
    return;
  }
  saving.value = true;
  try {
    const id = await examApi.generatePaper(form);
    ElMessage.success("组卷成功");
    genDialog.value = false;
    load();
    router.push(`/exam/take/${id}`);
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  skills.value = await skillApi.list();
  load();
});
</script>
