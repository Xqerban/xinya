<template>
  <div class="patient-detail-page">
    <div class="page-header">
      <el-page-header @back="$router.back()">
        <template #content>
          <span>患者详情 - {{ patient?.name }}</span>
        </template>
      </el-page-header>
    </div>
    
    <el-row :gutter="20">
      <!-- 基本信息 -->
      <el-col :span="8">
        <el-card class="info-card">
          <template #header>
            <span>基本信息</span>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="姓名">{{ patient?.name }}</el-descriptions-item>
            <el-descriptions-item label="病房号">{{ patient?.roomNumber || '-' }}</el-descriptions-item>
            <el-descriptions-item label="入仓日期">{{ patient?.admissionDate }}</el-descriptions-item>
            <el-descriptions-item label="当前阶段">
              <el-tag>{{ ClinicalStageLabels[patient?.stage || 'ADMISSION'] }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="入仓天数">{{ daysInHospital }} 天</el-descriptions-item>
          </el-descriptions>
        </el-card>
        
        <el-card class="tree-card">
          <template #header>
            <span>希望之树</span>
          </template>
          <div class="tree-info">
            <div class="tree-level">Lv.{{ patient?.treeLevel || 1 }}</div>
            <div class="tree-name">{{ getTreeStageName(patient?.treeLevel || 1) }}</div>
            <el-progress
              :percentage="60"
              :stroke-width="12"
              color="#8BC34A"
            />
            <div class="tree-exp">经验值: 60/100</div>
          </div>
        </el-card>
      </el-col>
      
      <!-- 心理能量 -->
      <el-col :span="16">
        <el-card class="energy-card">
          <template #header>
            <span>心理能量监测</span>
          </template>
          <div class="energy-display">
            <div class="energy-value">{{ patient?.psychEnergy || 50 }}%</div>
            <el-progress
              type="dashboard"
              :percentage="patient?.psychEnergy || 50"
              :color="getEnergyColor(patient?.psychEnergy || 50)"
              :width="200"
            />
          </div>
          <div class="energy-tips">
            <el-alert
              v-if="(patient?.psychEnergy || 50) < 40"
              title="心理能量较低，建议关注患者情绪状态"
              type="warning"
              show-icon
            />
            <el-alert
              v-else-if="(patient?.psychEnergy || 50) >= 70"
              title="心理状态良好，继续保持！"
              type="success"
              show-icon
            />
            <el-alert
              v-else
              title="心理能量处于正常范围"
              type="info"
              show-icon
            />
          </div>
        </el-card>
        
        <el-card class="records-card">
          <template #header>
            <span>最近对话记录</span>
          </template>
          <el-timeline>
            <el-timeline-item timestamp="2026-01-26 10:30" placement="top">
              <el-card>
                <h4>与小芽的对话</h4>
                <p>患者表达了对治疗的担忧，小芽进行了情绪疏导和放松引导。</p>
              </el-card>
            </el-timeline-item>
            <el-timeline-item timestamp="2026-01-25 15:00" placement="top">
              <el-card>
                <h4>与小护士的对话</h4>
                <p>患者询问了预处理期的注意事项，小护士提供了详细解答。</p>
              </el-card>
            </el-timeline-item>
            <el-timeline-item timestamp="2026-01-25 09:00" placement="top">
              <el-card>
                <h4>每日打卡</h4>
                <p>心情：还行 | 睡眠：一般 | 食欲：正常</p>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getPatient } from '@/api/patient'
import type { Patient } from '@/types'
import { ClinicalStageLabels } from '@/types'
import dayjs from 'dayjs'

const route = useRoute()
const patient = ref<Patient | null>(null)

const daysInHospital = computed(() => {
  if (!patient.value?.admissionDate) return 0
  return dayjs().diff(dayjs(patient.value.admissionDate), 'day')
})

function getTreeStageName(level: number): string {
  const names = ['种子', '发芽', '幼苗', '小树', '成长', '茂盛', '参天大树']
  return names[level - 1] || '种子'
}

function getEnergyColor(energy: number): string {
  if (energy >= 70) return '#4CAF50'
  if (energy >= 40) return '#FF9800'
  return '#E53935'
}

onMounted(async () => {
  const id = route.params.id as string
  try {
    patient.value = await getPatient(id)
  } catch (e) {
    // Mock数据
    patient.value = {
      id,
      name: '示例患者',
      stage: 'PRETREATMENT',
      psychEnergy: 72,
      treeLevel: 3,
      admissionDate: '2026-01-20',
      roomNumber: '101'
    }
  }
})
</script>

<style scoped lang="scss">
.page-header {
  margin-bottom: 20px;
}

.info-card, .tree-card, .energy-card, .records-card {
  margin-bottom: 20px;
}

.tree-info {
  text-align: center;
  
  .tree-level {
    font-size: 48px;
    font-weight: bold;
    color: #4CAF50;
  }
  
  .tree-name {
    font-size: 18px;
    color: #666;
    margin-bottom: 20px;
  }
  
  .tree-exp {
    margin-top: 10px;
    color: #999;
  }
}

.energy-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  
  .energy-value {
    font-size: 36px;
    font-weight: bold;
    margin-bottom: 20px;
  }
}

.energy-tips {
  margin-top: 20px;
}
</style>
