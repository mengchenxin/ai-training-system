<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">组织架构</div>
        <div class="sub">部门、岗位与角色配置，岗位是技能模型的挂载点</div>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="card">
          <div class="flex-between mb16">
            <b>部门</b>
            <el-button size="small" type="primary" @click="openDept()">新增部门</el-button>
          </div>
          <el-table :data="depts" size="small">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="deptName" label="部门名称" min-width="160" />
            <el-table-column label="上级" width="140">
              <template #default="{ row }">{{ deptName(row.parentId) }}</template>
            </el-table-column>
            <el-table-column prop="sortNo" label="排序" width="70" align="center" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openDept(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeDept(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <el-col :span="12">
        <div class="card">
          <div class="flex-between mb16">
            <b>岗位</b>
            <el-button size="small" type="primary" @click="openPost()">新增岗位</el-button>
          </div>
          <el-table :data="posts" size="small">
            <el-table-column prop="postCode" label="编码" width="80" />
            <el-table-column prop="postName" label="岗位名称" min-width="140" />
            <el-table-column label="部门" width="140">
              <template #default="{ row }">{{ deptName(row.deptId) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openPost(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removePost(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <div class="card">
      <b>角色</b>
      <el-table :data="roles" size="small" class="mt16">
        <el-table-column prop="roleCode" label="角色标识" width="140" />
        <el-table-column prop="roleName" label="角色名称" width="140" />
        <el-table-column prop="remark" label="职责说明" min-width="260" />
      </el-table>
    </div>

    <el-dialog v-model="deptDialog" :title="deptForm.id ? '编辑部门' : '新增部门'" width="440px">
      <el-form :model="deptForm" label-width="90px">
        <el-form-item label="部门名称">
          <el-input v-model="deptForm.deptName" />
        </el-form-item>
        <el-form-item label="上级部门">
          <el-select v-model="deptForm.parentId" style="width: 100%">
            <el-option label="顶级部门" :value="0" />
            <el-option v-for="d in depts.filter((x: any) => x.id !== deptForm.id)" :key="d.id"
              :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="deptForm.sortNo" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDept">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="postDialog" :title="postForm.id ? '编辑岗位' : '新增岗位'" width="480px">
      <el-form :model="postForm" label-width="90px">
        <el-form-item label="岗位编码">
          <el-input v-model="postForm.postCode" placeholder="如 P09" />
        </el-form-item>
        <el-form-item label="岗位名称">
          <el-input v-model="postForm.postName" />
        </el-form-item>
        <el-form-item label="归属部门">
          <el-select v-model="postForm.deptId" style="width: 100%">
            <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位职责">
          <el-input v-model="postForm.postDesc" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="postDialog = false">取消</el-button>
        <el-button type="primary" @click="savePost">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { orgApi } from "@/api";

const depts = ref<any[]>([]);
const posts = ref<any[]>([]);
const roles = ref<any[]>([]);
const deptDialog = ref(false);
const postDialog = ref(false);

const deptForm = reactive<any>({ id: null, deptName: "", parentId: 0, sortNo: 10 });
const postForm = reactive<any>({ id: null, postCode: "", postName: "", deptId: undefined, postDesc: "" });

function deptName(id: number) {
  if (!id) return "—";
  return depts.value.find((d) => d.id === id)?.deptName || "—";
}

async function load() {
  const [d, p, r] = await Promise.all([orgApi.deptList(), orgApi.postList(), orgApi.roleList()]);
  depts.value = d;
  posts.value = p;
  roles.value = r;
}

function openDept(row?: any) {
  Object.assign(deptForm, { id: null, deptName: "", parentId: 0, sortNo: 10 });
  if (row) Object.assign(deptForm, row);
  deptDialog.value = true;
}

async function saveDept() {
  await orgApi.saveDept(deptForm);
  ElMessage.success("已保存");
  deptDialog.value = false;
  load();
}

async function removeDept(row: any) {
  await ElMessageBox.confirm(`确定删除部门「${row.deptName}」吗？`, "提示", { type: "warning" });
  await orgApi.removeDept(row.id);
  ElMessage.success("已删除");
  load();
}

function openPost(row?: any) {
  Object.assign(postForm, { id: null, postCode: "", postName: "", deptId: undefined, postDesc: "" });
  if (row) Object.assign(postForm, row);
  postDialog.value = true;
}

async function savePost() {
  await orgApi.savePost(postForm);
  ElMessage.success("已保存");
  postDialog.value = false;
  load();
}

async function removePost(row: any) {
  await ElMessageBox.confirm(`确定删除岗位「${row.postName}」吗？`, "提示", { type: "warning" });
  await orgApi.removePost(row.id);
  ElMessage.success("已删除");
  load();
}

onMounted(load);
</script>
