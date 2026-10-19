<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">知识库管理</div>
        <div class="sub">技能标准、课程大纲、培训制度等文档，切分后作为 RAG 检索语料</div>
      </div>
      <div>
        <el-button :loading="reloading" @click="reloadIndex">重建索引</el-button>
        <el-button type="primary" @click="openAdd">新增文档</el-button>
      </div>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-radio-group v-model="docType" @change="load">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="SKILL_STANDARD">技能标准</el-radio-button>
          <el-radio-button value="POLICY">培训制度</el-radio-button>
          <el-radio-button value="POST_DESC">岗位说明</el-radio-button>
        </el-radio-group>
        <span class="muted">当前索引片段数：{{ indexSize }}</span>
      </div>

      <el-table :data="rows" v-loading="loading" size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="docName" label="文档名称" min-width="260" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ TYPE[row.docType] || row.docType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="source" label="来源" min-width="180" show-overflow-tooltip />
        <el-table-column prop="chunkCount" label="片段数" width="90" align="center" />
        <el-table-column prop="status" label="状态" width="90" align="center" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openChunks(row)">查看片段</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="chunkDialog" :title="`片段预览 · ${currentDoc?.docName}`" width="720px">
      <div v-for="c in chunks" :key="c.id" class="chunk">
        <div class="chunk-head">
          <el-tag size="small" effect="plain">#{{ c.chunkIndex + 1 }}</el-tag>
          <span class="muted">{{ c.tokenCount }} 字符</span>
        </div>
        <div class="chunk-body">{{ c.content }}</div>
      </div>
    </el-dialog>

    <el-dialog v-model="addDialog" title="新增知识库文档" width="640px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="文档名称">
          <el-input v-model="form.docName" placeholder="如《焊接岗位技能标准》" />
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="form.docType" style="width: 100%">
            <el-option v-for="(v, k) in TYPE" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="form.source" placeholder="如：公司内部制度 / 国家职业技能标准" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="form.content" type="textarea" :rows="10"
            placeholder="按段落录入，系统会自动按句号切分为片段并建立索引" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存并索引</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { aiApi } from "@/api";

const rows = ref<any[]>([]);
const loading = ref(false);
const reloading = ref(false);
const saving = ref(false);
const docType = ref("");
const indexSize = ref(0);
const chunkDialog = ref(false);
const addDialog = ref(false);
const chunks = ref<any[]>([]);
const currentDoc = ref<any>(null);

const TYPE: Record<string, string> = {
  SKILL_STANDARD: "技能标准",
  COURSE_OUTLINE: "课程大纲",
  POLICY: "培训制度",
  POST_DESC: "岗位说明",
  OTHER: "其他"
};

const form = reactive({
  docName: "",
  docType: "SKILL_STANDARD",
  source: "",
  content: ""
});

async function load() {
  loading.value = true;
  try {
    rows.value = await aiApi.kbDocs(docType.value || undefined);
    const st = await aiApi.status();
    indexSize.value = st.ragIndexSize;
  } finally {
    loading.value = false;
  }
}

async function openChunks(row: any) {
  currentDoc.value = row;
  chunks.value = await aiApi.kbChunks(row.id);
  chunkDialog.value = true;
}

function openAdd() {
  Object.assign(form, { docName: "", docType: "SKILL_STANDARD", source: "", content: "" });
  addDialog.value = true;
}

async function save() {
  if (!form.docName || !form.content) {
    ElMessage.warning("文档名称与正文不能为空");
    return;
  }
  saving.value = true;
  try {
    await aiApi.addKbDoc(form);
    ElMessage.success("已保存并建立索引");
    addDialog.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确定删除「${row.docName}」吗？`, "提示", { type: "warning" });
  await aiApi.removeKbDoc(row.id);
  ElMessage.success("已删除");
  load();
}

async function reloadIndex() {
  reloading.value = true;
  try {
    const n = await aiApi.ragReload();
    ElMessage.success(`索引已重建，共 ${n} 个片段`);
    indexSize.value = n;
  } finally {
    reloading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.chunk {
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
}

.chunk-head {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}

.chunk-body {
  font-size: 12.5px;
  line-height: 1.85;
  color: #4b5563;
}
</style>
