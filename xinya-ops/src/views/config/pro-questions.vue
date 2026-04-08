<template>
  <div>
    <div class="page-header">
      <span class="page-title">PRO 题目配置</span>
    </div>

    <el-alert
      title="PRO 题目由后端预置，此处仅支持调整排序和启用状态，变更后自动同步到临床端患者问卷。"
      type="info"
      show-icon
      :closable="false"
      class="mb-16"
    />

    <!-- 阶段筛选 -->
    <div class="filter-bar mb-16">
      <el-radio-group v-model="filterStage" @change="loadData">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button v-for="s in stageOptions" :key="s.value" :value="s.value">
          {{ s.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="questions" v-loading="loading" border>
      <el-table-column prop="id" label="题目ID" width="140" />
      <el-table-column label="适用阶段" width="100">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ stageLabel(row.stage) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="题目内容" min-width="200" show-overflow-tooltip />
      <el-table-column label="题型" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="量程" width="90">
        <template #default="{ row }">
          <span v-if="row.scaleMin != null">{{ row.scaleMin }}~{{ row.scaleMax }}</span>
          <span v-else class="text-secondary">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="symptomKey" label="症状键名" width="120" show-overflow-tooltip />
      <el-table-column label="排序" width="120">
        <template #default="{ row }">
          <el-input-number
            v-model="row.sortOrder"
            :min="0"
            size="small"
            style="width:90px"
            @change="(val: number) => handleSortChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch
            v-model="row.isActive"
            @change="(val: boolean) => handleActiveChange(row, val)"
          />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listProQuestions, updateProQuestion } from '@/api/config'
import type { ProQuestionDto } from '@/api/config'

const stageOptions = [
  { value: 'ALL', label: '全阶段' },
  { value: 'ADMISSION', label: '入仓期' },
  { value: 'PRETREATMENT', label: '预处理期' },
  { value: 'TRANSPLANT', label: '移植期' },
  { value: 'REBUILD', label: '重建期' },
  { value: 'DISCHARGE', label: '出仓期' }
]

function stageLabel(s: string) {
  return stageOptions.find(o => o.value === s)?.label ?? s
}

function typeLabel(t: string) {
  const map: Record<string, string> = {
    scale: '量表',
    single_choice: '单选',
    multi_choice: '多选'
  }
  return map[t] ?? t
}

const loading = ref(false)
const questions = ref<ProQuestionDto[]>([])
const filterStage = ref('')

async function loadData() {
  loading.value = true
  try {
    questions.value = await listProQuestions(filterStage.value || undefined)
  } finally {
    loading.value = false
  }
}

async function handleSortChange(row: ProQuestionDto, val: number | undefined) {
  if (val == null) return
  try {
    await updateProQuestion(row.id, { sortOrder: val })
    ElMessage.success('排序已更新并同步')
  } catch {
    loadData()
  }
}

async function handleActiveChange(row: ProQuestionDto, val: boolean) {
  try {
    await updateProQuestion(row.id, { isActive: val })
    ElMessage.success(val ? '已启用并同步' : '已停用并同步')
  } catch {
    loadData()
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.filter-bar { display: flex; gap: 10px; flex-wrap: wrap; }
</style>
