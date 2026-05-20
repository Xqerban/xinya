<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">患者管理</h2>
      <el-button type="primary" :icon="Plus" @click="showCreateDrawer = true">
        新增患者
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="filter-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索患者姓名..."
        :prefix-icon="Search"
        clearable
        style="width:220px"
        @input="onSearch"
      />
      <el-select v-model="stageFilter" placeholder="全部阶段" clearable style="width:140px" @change="loadList">
        <el-option v-for="(label, val) in STAGE_LABELS" :key="val" :label="label" :value="val" />
      </el-select>
      <el-button :icon="Refresh" circle plain @click="loadList" />
    </div>

    <!-- 患者表格 -->
    <div class="chart-card" style="margin-top:0">
      <el-table
        v-loading="loading"
        :data="patients"
        style="width:100%"
        row-key="id"
        @row-click="row => router.push(`/patients/${row.id}`)"
        :row-style="{ cursor: 'pointer' }"
      >
        <el-table-column label="患者" min-width="120">
          <template #default="{ row }">
            <div class="patient-name">
              <el-avatar :size="32" :style="{ background: '#1976D2', color: 'white', fontSize: '14px' }">
                {{ row.name.slice(0, 1) }}
              </el-avatar>
              <div>
                <div class="name-text">{{ row.name }}</div>
                <div class="room-text">{{ row.roomNumber || '暂无病房' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="临床阶段" width="110">
          <template #default="{ row }">
            <StageTag :stage="row.stage" />
          </template>
        </el-table-column>

        <el-table-column label="心理能量" min-width="140">
          <template #default="{ row }">
            <EnergyBar :value="row.psychEnergy" />
          </template>
        </el-table-column>

        <el-table-column label="希望之树" width="100" align="center">
          <template #default="{ row }">
            <span class="tree-level">Lv.{{ row.treeLevel }} 🌱</span>
          </template>
        </el-table-column>

        <el-table-column label="入院日期" prop="admissionDate" width="120" />

        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click.stop="router.push(`/patients/${row.id}`)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          small
          @change="loadList"
        />
      </div>
    </div>

    <!-- 新增患者抽屉 -->
    <el-drawer v-model="showCreateDrawer" title="新增患者档案" size="400px" direction="rtl">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="患者姓名" prop="name">
          <el-input v-model="createForm.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="病房号" prop="roomNumber">
          <el-input v-model="createForm.roomNumber" placeholder="如 A101" />
        </el-form-item>
        <el-form-item label="入院日期" prop="admissionDate">
          <el-date-picker
            v-model="createForm.admissionDate"
            type="date"
            placeholder="选择日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="诊断">
          <el-input v-model="createForm.diagnosis" placeholder="可选" />
        </el-form-item>
        <el-form-item label="年龄">
          <el-input-number v-model="createForm.age" :min="1" :max="120" style="width:100%" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="createForm.gender">
            <el-radio value="MALE">男</el-radio>
            <el-radio value="FEMALE">女</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDrawer = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreate">确认创建</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import { getPatients, createPatient } from '@/api/patient'
import type { PatientDto, ClinicalStage, CreatePatientRequest } from '@/types'
import { STAGE_LABELS } from '@/types'
import StageTag from '@/components/StageTag.vue'
import EnergyBar from '@/components/EnergyBar.vue'

const router = useRouter()

const patients = ref<PatientDto[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const keyword = ref('')
const stageFilter = ref<ClinicalStage | ''>('')

let searchTimer: ReturnType<typeof setTimeout>
function onSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadList, 400)
}

async function loadList() {
  loading.value = true
  try {
    const res = await getPatients({
      page: page.value,
      pageSize: pageSize.value,
      stage: stageFilter.value || undefined,
      keyword: keyword.value || undefined
    })
    patients.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

// 新增患者
const showCreateDrawer = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()

const createForm = reactive<CreatePatientRequest & { gender?: string }>({
  name: '',
  roomNumber: '',
  admissionDate: '',
  diagnosis: '',
  age: undefined,
  gender: undefined
})

const createRules: FormRules = {
  name: [{ required: true, message: '请填写患者姓名', trigger: 'blur' }],
  admissionDate: [{ required: true, message: '请选择入院日期', trigger: 'change' }]
}

async function handleCreate() {
  await createFormRef.value?.validate()
  createLoading.value = true
  try {
    await createPatient(createForm)
    ElMessage.success('患者档案创建成功')
    showCreateDrawer.value = false
    createFormRef.value?.resetFields()
    loadList()
  } finally {
    createLoading.value = false
  }
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.patient-name {
  display: flex;
  align-items: center;
  gap: 10px;

  .name-text {
    font-weight: 500;
    font-size: 14px;
  }

  .room-text {
    font-size: 12px;
    color: var(--text-secondary);
  }
}

.tree-level {
  font-size: 13px;
  color: var(--text-secondary);
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}
</style>
