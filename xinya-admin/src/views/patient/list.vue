<template>
  <div class="patient-list-page">
    <div class="page-header">
      <h1 class="page-title">患者管理</h1>
      <el-button type="primary" @click="showAddDialog = true">
        <el-icon><Plus /></el-icon>
        新增患者
      </el-button>
    </div>
    
    <!-- 筛选 -->
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="临床阶段">
          <el-select v-model="filterStage" placeholder="全部阶段" clearable>
            <el-option
              v-for="(label, key) in ClinicalStageLabels"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="搜索">
          <el-input v-model="searchText" placeholder="患者姓名/病房号" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadPatients">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <!-- 患者列表 -->
    <el-card class="list-card">
      <el-table :data="filteredPatients" v-loading="loading" style="width: 100%">
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="roomNumber" label="病房号" width="100" />
        <el-table-column prop="stage" label="临床阶段" width="120">
          <template #default="{ row }">
            <el-tag :type="getStageTagType(row.stage)">
              {{ ClinicalStageLabels[row.stage] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="psychEnergy" label="心理能量" width="150">
          <template #default="{ row }">
            <div class="energy-bar">
              <el-progress
                :percentage="row.psychEnergy"
                :color="getEnergyColor(row.psychEnergy)"
                :stroke-width="10"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="treeLevel" label="希望之树" width="100">
          <template #default="{ row }">
            <span class="tree-level">Lv.{{ row.treeLevel }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="admissionDate" label="入仓日期" width="120" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button type="primary" link @click="$router.push(`/patients/${row.id}`)">
              查看详情
            </el-button>
            <el-button type="success" link @click="handleStageTransition(row)">
              阶段流转
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <!-- 新增患者对话框 -->
    <el-dialog v-model="showAddDialog" title="新增患者" width="500px">
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="姓名" required>
          <el-input v-model="addForm.name" placeholder="请输入患者姓名" />
        </el-form-item>
        <el-form-item label="病房号">
          <el-input v-model="addForm.roomNumber" placeholder="请输入病房号" />
        </el-form-item>
        <el-form-item label="入仓日期" required>
          <el-date-picker v-model="addForm.admissionDate" type="date" placeholder="选择日期" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddPatient">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPatients, createPatient, updatePatientStage } from '@/api/patient'
import type { Patient, ClinicalStage } from '@/types'
import { ClinicalStageLabels } from '@/types'
import dayjs from 'dayjs'

const loading = ref(false)
const patients = ref<Patient[]>([])
const filterStage = ref('')
const searchText = ref('')
const showAddDialog = ref(false)

const addForm = ref({
  name: '',
  roomNumber: '',
  admissionDate: new Date()
})

const filteredPatients = computed(() => {
  let result = patients.value
  if (filterStage.value) {
    result = result.filter(p => p.stage === filterStage.value)
  }
  if (searchText.value) {
    const text = searchText.value.toLowerCase()
    result = result.filter(p => 
      p.name.toLowerCase().includes(text) ||
      (p.roomNumber && p.roomNumber.toLowerCase().includes(text))
    )
  }
  return result
})

function getStageTagType(stage: ClinicalStage) {
  switch (stage) {
    case 'ADMISSION': return 'info'
    case 'PRETREATMENT': return 'warning'
    case 'TRANSPLANT': return 'danger'
    case 'REBUILD': return 'primary'
    case 'DISCHARGE': return 'success'
    default: return 'info'
  }
}

function getEnergyColor(energy: number) {
  if (energy >= 70) return '#4CAF50'
  if (energy >= 40) return '#FF9800'
  return '#E53935'
}

async function loadPatients() {
  loading.value = true
  try {
    patients.value = await getPatients()
  } catch (e) {
    // 使用mock数据
    patients.value = [
      { id: 'P001', name: '张三', stage: 'PRETREATMENT', psychEnergy: 75, treeLevel: 3, admissionDate: '2026-01-20', roomNumber: '101' },
      { id: 'P002', name: '李四', stage: 'TRANSPLANT', psychEnergy: 60, treeLevel: 4, admissionDate: '2026-01-15', roomNumber: '102' },
      { id: 'P003', name: '王五', stage: 'REBUILD', psychEnergy: 85, treeLevel: 5, admissionDate: '2026-01-10', roomNumber: '103' },
      { id: 'P004', name: '赵六', stage: 'ADMISSION', psychEnergy: 50, treeLevel: 1, admissionDate: '2026-01-25', roomNumber: '104' }
    ]
  } finally {
    loading.value = false
  }
}

async function handleAddPatient() {
  if (!addForm.value.name) {
    ElMessage.warning('请输入患者姓名')
    return
  }
  
  try {
    await createPatient({
      name: addForm.value.name,
      roomNumber: addForm.value.roomNumber,
      admissionDate: dayjs(addForm.value.admissionDate).format('YYYY-MM-DD')
    })
    ElMessage.success('患者创建成功')
    showAddDialog.value = false
    loadPatients()
  } catch (e) {
    console.error(e)
  }
}

async function handleStageTransition(patient: Patient) {
  const stageOrder: ClinicalStage[] = ['ADMISSION', 'PRETREATMENT', 'TRANSPLANT', 'REBUILD', 'DISCHARGE']
  const currentIndex = stageOrder.indexOf(patient.stage)
  
  if (currentIndex >= stageOrder.length - 1) {
    ElMessage.info('患者已处于出仓期，无法继续流转')
    return
  }
  
  const nextStage = stageOrder[currentIndex + 1]
  
  await ElMessageBox.confirm(
    `确定将患者 ${patient.name} 从「${ClinicalStageLabels[patient.stage]}」流转到「${ClinicalStageLabels[nextStage]}」？`,
    '阶段流转确认'
  )
  
  try {
    await updatePatientStage(patient.id, nextStage)
    ElMessage.success('阶段流转成功')
    loadPatients()
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  loadPatients()
})
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.filter-card {
  margin-bottom: 20px;
}

.energy-bar {
  width: 100px;
}

.tree-level {
  color: #4CAF50;
  font-weight: bold;
}
</style>
