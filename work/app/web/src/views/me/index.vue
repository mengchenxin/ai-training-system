<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">个人中心</div>
        <div class="sub">账号与岗位信息</div>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="8">
        <div class="card" style="text-align: center; padding: 30px 16px">
          <el-avatar :size="72" style="background: #2563eb; font-size: 26px">
            {{ (info?.realName || "U").slice(0, 1) }}
          </el-avatar>
          <div style="font-size: 18px; font-weight: 600; margin-top: 12px">{{ info?.realName }}</div>
          <div class="muted mt8">{{ info?.username }}</div>
          <el-tag class="mt16" :type="userStore.isManager ? 'success' : 'info'" effect="light">
            {{ info?.roleNames?.join(" / ") || userStore.roleLabel }}
          </el-tag>
        </div>
      </el-col>
      <el-col :span="16">
        <div class="card">
          <b>基本信息</b>
          <el-descriptions :column="2" border class="mt16">
            <el-descriptions-item label="账号">{{ info?.username }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ info?.realName }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ info?.deptName || "—" }}</el-descriptions-item>
            <el-descriptions-item label="岗位">{{ info?.postName || "—" }}</el-descriptions-item>
            <el-descriptions-item label="角色">
              {{ info?.roles?.join(", ") }}
            </el-descriptions-item>
            <el-descriptions-item label="权限点">
              {{ info?.permissions?.length || 0 }} 项
            </el-descriptions-item>
          </el-descriptions>

          <div class="mt16">
            <el-button v-if="userStore.isEmployee" type="primary" @click="$router.push('/skill/gap')">
              查看我的技能差距
            </el-button>
            <el-button @click="$router.push('/progress/my')">我的培训</el-button>
            <el-button @click="$router.push('/exam/record')">我的成绩</el-button>
          </div>
        </div>

        <div v-if="userStore.isEmployee" class="card">
          <div class="flex-between mb16">
            <b>我的技能档案</b>
            <el-button size="small" @click="editing = !editing">
              {{ editing ? "退出编辑" : "更新技能等级" }}
            </el-button>
          </div>
          <el-table :data="empSkills" size="small">
            <el-table-column prop="skillName" label="技能点" min-width="170" />
            <el-table-column prop="category" label="类别" width="80" align="center" />
            <el-table-column label="当前等级" width="160">
              <template #default="{ row }">
                <el-input-number v-if="editing" v-model="row.currentLevel" :min="0" :max="5" size="small" />
                <span v-else>{{ row.currentLevel }}</span>
              </template>
            </el-table-column>
            <el-table-column label="岗位要求" width="100" align="center">
              <template #default="{ row }">{{ row.targetLevel ?? "—" }}</template>
            </el-table-column>
            <el-table-column label="评定来源" width="120" align="center">
              <template #default="{ row }">{{ SOURCE[row.assessSource] || row.assessSource }}</template>
            </el-table-column>
          </el-table>
          <div v-if="editing" class="mt16">
            <el-button type="primary" size="small" :loading="saving" @click="saveSkills">保存</el-button>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { skillApi } from "@/api";
import { useUserStore } from "@/stores/user";

const userStore = useUserStore();
const info = ref<any>(null);
const empSkills = ref<any[]>([]);
const editing = ref(false);
const saving = ref(false);

const SOURCE: Record<string, string> = {
  SELF: "自评",
  LECTURER: "讲师评定",
  EXAM: "考核得出"
};

async function load() {
  info.value = userStore.info;
  if (userStore.isEmployee && userStore.info?.userId) {
    empSkills.value = await skillApi.empSkill(userStore.info.userId);
  }
}

async function saveSkills() {
  saving.value = true;
  try {
    await skillApi.saveEmpSkill(userStore.info!.userId, empSkills.value.map((s) => ({
      skillId: s.skillId,
      currentLevel: s.currentLevel,
      assessSource: "SELF"
    })));
    ElMessage.success("技能档案已更新");
    editing.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>
