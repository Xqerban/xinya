<template>
  <div class="dashboard-page">
    <h1 class="page-title">数据驾驶舱</h1>
    
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stat-row">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ dashboardData.totalPatients }}</div>
          <div class="stat-label">当前患者总数</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value" style="color: #FF9800">{{ dashboardData.avgPsychEnergy?.toFixed(1) }}%</div>
          <div class="stat-label">平均心理能量</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value" style="color: #42A5F5">{{ dashboardData.learningStats?.avgCompletionRate?.toFixed(1) }}%</div>
          <div class="stat-label">学习完成率</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value" style="color: #E53935">{{ alerts.length }}</div>
          <div class="stat-label">待处理预警</div>
        </div>
      </el-col>
    </el-row>
    
    <el-row :gutter="20" class="chart-row">
      <!-- 阶段分布图 -->
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <span>患者阶段分布</span>
          </template>
          <div class="chart-container">
            <v-chart :option="stageChartOption" autoresize />
          </div>
        </el-card>
      </el-col>
      
      <!-- 症状趋势 -->
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <span>症状趋势对比</span>
          </template>
          <div class="chart-container">
            <v-chart :option="symptomChartOption" autoresize />
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 预警列表 -->
    <el-card class="alert-card">
      <template #header>
        <span>预警信息</span>
      </template>
      <el-table :data="alerts" style="width: 100%">
        <el-table-column prop="level" label="级别" width="100">
          <template #default="{ row }">
            <el-tag :type="getAlertType(row.level)">
              {{ row.level === 'warning' ? '警告' : row.level === 'error' ? '严重' : '提示' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="内容" />
        <el-table-column prop="patientId" label="相关患者" width="150">
          <template #default="{ row }">
            <router-link v-if="row.patientId" :to="`/patients/${row.patientId}`">
              {{ row.patientId }}
            </router-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default>
            <el-button type="primary" link size="small">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { getDashboardOverview } from '@/api/dashboard'
import type { DashboardData, AlertItem } from '@/types'
import { ClinicalStageLabels } from '@/types'

use([CanvasRenderer, PieChart, BarChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const dashboardData = ref<DashboardData>({
  totalPatients: 0,
  patientsByStage: {},
  avgPsychEnergy: 0,
  symptomTrends: [],
  alerts: [],
  learningStats: { avgCompletionRate: 0, avgWatchTime: 0, engagementRate: 0 }
})

const alerts = computed<AlertItem[]>(() => dashboardData.value.alerts || [])

const stageChartOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: '0%' },
  series: [{
    type: 'pie',
    radius: ['40%', '70%'],
    data: Object.entries(dashboardData.value.patientsByStage || {}).map(([key, value]) => ({
      name: ClinicalStageLabels[key as keyof typeof ClinicalStageLabels] || key,
      value
    })),
    emphasis: {
      itemStyle: {
        shadowBlur: 10,
        shadowOffsetX: 0,
        shadowColor: 'rgba(0, 0, 0, 0.5)'
      }
    }
  }]
}))

const symptomChartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['上周', '本周'] },
  xAxis: {
    type: 'category',
    data: (dashboardData.value.symptomTrends || []).map(t => t.symptom)
  },
  yAxis: { type: 'value' },
  series: [
    {
      name: '上周',
      type: 'bar',
      data: (dashboardData.value.symptomTrends || []).map(t => t.lastWeek)
    },
    {
      name: '本周',
      type: 'bar',
      data: (dashboardData.value.symptomTrends || []).map(t => t.thisWeek)
    }
  ]
}))

function getAlertType(level: string) {
  switch (level) {
    case 'error': return 'danger'
    case 'warning': return 'warning'
    default: return 'info'
  }
}

onMounted(async () => {
  try {
    const data = await getDashboardOverview()
    dashboardData.value = data
  } catch (e) {
    console.error('获取驾驶舱数据失败', e)
  }
})
</script>

<style scoped lang="scss">
.stat-row {
  margin-bottom: 20px;
}

.chart-row {
  margin-bottom: 20px;
}

.chart-card {
  height: 400px;
}

.chart-container {
  height: 320px;
}

.alert-card {
  margin-top: 20px;
}
</style>
