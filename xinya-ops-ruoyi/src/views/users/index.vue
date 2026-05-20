<template>
  <div>
    <div class="page-header">
      <span class="page-title">用户管理</span>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建用户</el-button>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar mb-16">
      <el-select v-model="filterRole" placeholder="角色" clearable style="width:120px" @change="loadData">
        <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
      </el-select>
    </div>

    <el-table :data="users" v-loading="loading" border>
      <el-table-column prop="username" label="用户名" width="130" />
      <el-table-column prop="displayName" label="姓名" width="100" />
      <el-table-column label="角色" width="90">
        <template #default="{ row }">
          <el-tag :type="roleTagType(row.role)" size="small">{{ roleLabel(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="130" show-overflow-tooltip />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" label="最近登录" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            text size="small"
            :type="row.enabled ? 'warning' : 'success'"
            @click="toggleEnabled(row)"
          >{{ row.enabled ? '停用' : '启用' }}</el-button>
          <el-button text size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="loadData"
      />
    </div>

    <!-- 新建/编辑 Dialog -->
    <el-dialog v-model="dialogVisible" :title="editId ? '编辑用户' : '新建用户'" width="440px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!editId" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="姓名" prop="displayName">
          <el-input v-model="form.displayName" placeholder="显示姓名" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width:100%">
            <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="可选" />
        </el-form-item>
        <el-form-item :label="editId ? '新密码' : '密码'" :prop="editId ? '' : 'password'">
          <el-input v-model="form.password" type="password" show-password
            :placeholder="editId ? '留空则不修改' : '请输入密码'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  listUsers, createUser, updateUser, deactivateUser, deleteUser
} from '@/api/users'
import type { UserDto } from '@/api/users'

const loading = ref(false)
const submitting = ref(false)
const users = ref<UserDto[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filterRole = ref('')

const dialogVisible = ref(false)
const editId = ref('')
const formRef = ref<FormInstance>()
const form = ref({ username: '', displayName: '', role: 'ADMIN', phone: '', password: '' })

const roleOptions = [
  { value: 'ADMIN', label: '运维管理员' },
  { value: 'NURSE', label: '护士' },
  { value: 'DOCTOR', label: '医生' }
]

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function roleLabel(r: string) {
  return roleOptions.find(o => o.value === r)?.label ?? r
}

function roleTagType(r: string): '' | 'warning' | 'success' | 'info' {
  if (r === 'ADMIN') return 'warning'
  if (r === 'DOCTOR') return 'success'
  return 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await listUsers({ role: filterRole.value || undefined, page: page.value, pageSize: pageSize.value })
    users.value = res.list
    total.value = Number(res.total)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editId.value = ''
  form.value = { username: '', displayName: '', role: 'ADMIN', phone: '', password: '' }
  dialogVisible.value = true
}

function openEdit(row: UserDto) {
  editId.value = row.id
  form.value = { username: row.username, displayName: row.displayName, role: row.role, phone: row.phone || '', password: '' }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (editId.value) {
      const payload: Record<string, string> = {
        displayName: form.value.displayName,
        role: form.value.role,
        phone: form.value.phone
      }
      if (form.value.password) payload.password = form.value.password
      await updateUser(editId.value, payload)
      ElMessage.success('用户已更新')
    } else {
      await createUser({
        username: form.value.username,
        password: form.value.password,
        displayName: form.value.displayName,
        role: form.value.role,
        phone: form.value.phone || undefined
      })
      ElMessage.success('用户已创建')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function toggleEnabled(row: UserDto) {
  const action = row.enabled ? '停用' : '启用'
  await ElMessageBox.confirm(`确认${action}用户 ${row.displayName}？`, '确认', {
    confirmButtonText: action, cancelButtonText: '取消', type: 'warning'
  }).catch(() => { throw new Error('cancel') })

  await deactivateUser(row.id)
  ElMessage.success(`${action}成功`)
  loadData()
}

async function handleDelete(row: UserDto) {
  await ElMessageBox.confirm(`确认删除用户 ${row.displayName}？此操作不可恢复。`, '警告', {
    confirmButtonText: '删除', cancelButtonText: '取消', type: 'error'
  }).catch(() => { throw new Error('cancel') })

  await deleteUser(row.id)
  ElMessage.success('用户已删除')
  loadData()
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.filter-bar { display: flex; gap: 10px; flex-wrap: wrap; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
