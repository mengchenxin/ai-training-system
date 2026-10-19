<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">岗位技能模型</div>
        <div class="sub">为每个岗位配置「技能点 + 目标等级 + 重要度权重」，是差距量化的基准</div>
      </div>
      <el-button type="primary" :disabled="!postId" @click="save">保存模型</el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <span class="muted">选择岗位</span>
        <el-select v-model="postId" placeholder="岗位" filterable style="width: 240px" @change="load">
          <el-option v-for="p in posts" :key="p.id" :label="`${p.postName}（${p.postCode}）`" :value="p.id" />
        </el-select>
        <el-button @click="addRow">添加技能项</el-button>
        <span class="muted">权重越高，该技能在差距排序中越优先</span>
      </div>

      <el-table :data="items" size="small" v-loading="loading">
        <el-table-column label="技能点" min-width="240">
          <template #default="{ row, $index }">
            <el-select v-model="row.skillId" filterable placeholder="选择技能" style="width: 100%"
              @change="(v: number) => onSkillChange(row, v)">
              <el-option v-for="s in skills" :key="s.id" :disabled="usedByOther(s.id, $index)"
                :label="`${s.skillName}（${s.category}）`" :value="s.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="类别" width="90" align="center">
          <template #default="{ row }">{{ categoryOf(row.skillId) }}</template>
        </el-table-column>
        <el-table-column label="目标等级" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.targetLevel" :min="0" :max="5" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="权重" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.weight" :min="0.1" :max="3" :step="0.1" :precision="2" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="技能说明" min-width="280">
          <template #default="{ row }">
            <span class="muted">{{ descOf(row.skillId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="items.splice($index, 1)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="muted mt16">
        当前配置 {{ items.length }} 项技能，目标等级合计权重
        {{ items.reduce((s: number, i: any) => s + Number(i.weight || 0), 0).toFixed(2) }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { orgApi, skillApi } from "@/api";

const posts = ref<any[]>([]);
const skills = ref<any[]>([]);
const postId = ref<number>();
const items = ref<any[]>([]);
const loading = ref(false);

function categoryOf(id: number) {
  return skills.value.find((s) => s.id === id)?.category || "-";
}
function descOf(id: number) {
  return skills.value.find((s) => s.id === id)?.skillDesc || "";
}
function usedByOther(id: number, index: number) {
  return items.value.some((it, i) => i !== index && it.skillId === id);
}

async function load() {
  if (!postId.value) return;
  loading.value = true;
  try {
    const list = await skillApi.postRequirement(postId.value);
    items.value = list.map((x: any) => ({
      skillId: x.skillId,
      targetLevel: x.targetLevel,
      weight: Number(x.weight)
    }));
  } finally {
    loading.value = false;
  }
}

function addRow() {
  items.value.push({ skillId: undefined, targetLevel: 3, weight: 1 });
}

function onSkillChange(_row: any, _v: number) {
  /* 占位：选择技能后无需额外处理 */
}

async function save() {
  const valid = items.value.filter((i) => i.skillId);
  if (!valid.length) {
    ElMessage.warning("请至少配置一项技能");
    return;
  }
  await skillApi.savePostRequirement(postId.value as number, valid);
  ElMessage.success("岗位技能模型已保存");
  load();
}

onMounted(async () => {
  const [p, s] = await Promise.all([orgApi.postList(), skillApi.list()]);
  posts.value = p;
  skills.value = s;
  if (p.length) {
    postId.value = p[0].id;
    await load();
  }
});
</script>
