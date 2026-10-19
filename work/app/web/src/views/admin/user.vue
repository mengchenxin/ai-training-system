<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="title">用户管理</div>
        <div class="sub">维护账号、部门岗位与角色（管理员 / 讲师 / 员工）</div>
      </div>
      <el-button type="primary" @click="openForm()">新增用户</el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="账号或姓名" clearable style="width: 180px"
          @keyup.enter="load" />
        <el-select v-model="query.deptId" placeholder="部门" clearable style="width: 150px">
          <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
        <el-select v-model="query.roleId" placeholder="角色" clearable style="width: 130px">
          <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" size="small">
        <el-table-column prop="username" label="账号" width="120" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="deptName" label="部门" width="140" />
        <el-table-column prop="postName" label="岗位" width="140" />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag v-for="r in row.roles" :key="r" size="small" class="mr4"
              :type="r === 'ADMIN' ? 'danger' : r === 'LECTURER' ? 'warning' : 'success'" effect="plain">
              {{ ROLE[r] || r }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机" width="130" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? "启用" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="resetPwd(row)">重置密码</el-button>
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

    <el-dialog v-model="dialog" :title="form.id ? '编辑用户' : '新增用户'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="账号">
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item :label="form.id ? '新密码' : '初始密码'">
          <el-input v-model="form.password" type="password" show-password
            :placeholder="form.id ? '留空表示不修改' : '默认 123456'" />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.deptId" clearable style="width: 100%">
            <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="form.postId" clearable style="width: 100%">
            <el-option v-for="p in posts" :key="p.id" :label="p.postName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="手机">
              <el-input v-model="form.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
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
import { orgApi, userApi } from "@/api";

const rows = ref<any[]>([]);
const total = ref(0);
const depts = ref<any[]>([]);
const posts = ref<any[]>([]);
const roles = ref<any[]>([]);
const loading = ref(false);
const dialog = ref(false);
const saving = ref(false);

const ROLE: Record<string, string> = { ADMIN: "管理员", LECTURER: "讲师", EMPLOYEE: "员工" };

const query = reactive({
  current: 1,
  size: 10,
  keyword: "",
  deptId: undefined as number | undefined,
  roleId: undefined as number | undefined,
  status: undefined as number | undefined
});

const form = reactive<any>({
  id: null,
  username: "",
  realName: "",
  password: "",
  deptId: undefined,
  postId: undefined,
  roleIds: [],
  phone: "",
  status: 1
});

async function load() {
  loading.value = true;
  try {
    const res = await userApi.page(query);
    rows.value = res.records;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function reset() {
  query.keyword = "";
  query.deptId = undefined;
  query.roleId = undefined;
  query.status = undefined;
  query.current = 1;
  load();
}

function openForm(row?: any) {
  Object.assign(form, {
    id: null, username: "", realName: "", password: "",
    deptId: undefined, postId: undefined, roleIds: [], phone: "", status: 1
  });
  if (row) Object.assign(form, { ...row, password: "" });
  dialog.value = true;
}

async function save() {
  saving.value = true;
  try {
    await userApi.save(form);
    ElMessage.success("已保存");
    dialog.value = false;
    load();
  } finally {
    saving.value = false;
  }
}

async function resetPwd(row: any) {
  await ElMessageBox.confirm(`将「${row.realName}」的密码重置为 123456？`, "提示", { type: "warning" });
  await userApi.resetPassword(row.id);
  ElMessage.success("已重置为 123456");
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确定删除用户「${row.realName}」吗？`, "警告", { type: "warning" });
  await userApi.remove(row.id);
  ElMessage.success("已删除");
  load();
}

onMounted(async () => {
  const [d, p, r] = await Promise.all([orgApi.deptList(), orgApi.postList(), orgApi.roleList()]);
  depts.value = d;
  posts.value = p;
  roles.value = r;
  load();
});
</script>

<style scoped>
.mr4 {
  margin-right: 4px;
}
</style>
