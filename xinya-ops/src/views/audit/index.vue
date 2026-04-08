<template>
  <div>
    <div class="page-header">
      <span class="page-title">审计日志</span>
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
    </div>

    <!-- 筛选栏 -->
    <el-card class="filter-card mb-16" shadow="never">
      <el-row :gutter="12">
        <el-col :xs="24" :sm="8" :md="5">
          <el-input
            v-model="filter.userId"
            placeholder="操作人ID"
            clearable
            @change="handleFilterChange"
          />
        </el-col>
        <el-col :xs="24" :sm="8" :md="4">
          <el-select
            v-model="filter.action"
            placeholder="操作类型"
            clearable
            style="width:100%"
            @change="handleFilterChange"
          >
            <el-option v-for="a in actionOptions" :key="a" :label="a" :value="a" />
          </el-select>
        </el-col>
        <el-col :xs="24" :sm="8" :md="4">
          <el-select
            v-model="filter.targetType"
            placeholder="目标类型"
            clearable
            style="width:100%"
            @change="handleFilterChange"
          >
            <el-option value="user" label="用户" />
            <el-option value="content" label="宣教内容" />
            <el-option value="keyword" label="危机关键词" />
            <el-option value="question" label="PRO题目" />
          </el-select>
        </el-col>
        <el-col :xs="24" :sm="12" :md="7">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width:100%"
            @change="handleFilterChange"
          />
        </el-col>
        <el-col :xs="24" :sm="12" :md="4">
          <el-button type="primary" @click="handleFilterChange">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-table :data="logs" v-loading="loading" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="操作人" width="110" show-overflow-tooltip>
        <template #default="{ row }">
          <div>{{ row.operatorName || '—' }}</div>
          <div class="sub-text">{{ row.operatorId || '' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="操作类型" width="140">
        <template #default="{ row }">
          <el-tag size="small" :type="actionTagType(row.action)">{{ row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="目标" width="120" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.targetType">{{ row.targetType }} / {{ row.targetId }}</span>
          <span v-else class="text-secondary">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="detail" label="详情" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tooltip v-if="row.detail" :content="row.detail" placement="top" :show-after="300">
            <span class="detail-text">{{ truncate(row.detail, 60) }}</span>
          </el-tooltip>
          <span v-else class="text-secondary">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" label="IP" width="120" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="时间" width="160" show-overflow-tooltip />
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @change="loadData"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { listAuditLogs } from '@/api/audit'
import type { AuditLogDto } from '@/api/audit'

const loading = ref(false)
const logs = ref<AuditLogDto[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const dateRange = ref<[string, string] | null>(null)
const filter = ref({
  userId: '',
  action: '',
  targetType: ''
})

const actionOptions = [
  'CREATE_USER', 'UPDATE_USER', 'DEACTIVATE_USER', 'DELETE_USER',
  'CREATE_CONTENT', 'UPDATE_CONTENT', 'DEACTIVATE_CONTENT',
  'CREATE_KEYWORD', 'DELETE_KEYWORD',
  'UPDATE_PRO_QUESTION', 'LOGIN', 'LOGOUT'
]

function actionTagType(action: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  if (action.startsWith('DELETE') || action.startsWith('DEACTIVATE')) return 'danger'
  if (action.startsWith('CREATE')) return 'success'
  if (action.startsWith('UPDATE')) return 'warning'
  if (action === 'LOGIN' || action === 'LOGOUT') return 'info'
  return ''
}

function truncate(str: string, len: number) {
  return str.length > len ? str.slice(0, len) + '…' : str
}

async function loadData() {
  loading.value = true
  try {
    const res = await listAuditLogs({
      userId: filter.value.userId || undefined,
      action: filter.value.action || undefined,
      targetType: filter.value.targetType || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    logs.value = res.list
    total.value = Number(res.total)
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  page.value = 1
  loadData()
}

function resetFilter() {
  filter.value = { userId: '', action: '', targetType: '' }
  dateRange.value = null
  handleFilterChange()
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.filter-card {
  :deep(.el-card__body) { padding: 16px; }
}

.sub-text {
  font-size: 11px;
  color: var(--text-placeholder);
  margin-top: 2px;
}

.detail-text {
  font-size: 12px;
  color: var(--text-secondary);
  cursor: default;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
