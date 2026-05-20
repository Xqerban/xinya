<template>
  <div>
    <!-- 概览卡片 -->
    <el-row :gutter="16" class="mb-20">
      <el-col :xs="12" :sm="6" v-for="card in statCards" :key="card.label">
        <div class="stat-card" style="margin-bottom:0">
          <div class="stat-icon" :style="{ background: card.bg }">{{ card.icon }}</div>
          <div class="stat-body">
            <div class="stat-value">{{ card.value }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16" class="mb-20">
      <!-- 心理状态分布 -->
      <el-col :xs="24" :md="8">
        <div class="chart-card">
          <div class="chart-title">心理状态分布</div>
          <v-chart :option="psychOption" style="height:240px" autoresize />
        </div>
      </el-col>

      <!-- 症状热力图 -->
      <el-col :xs="24" :md="16">
        <div class="chart-card">
          <div class="chart-title">
            症状热力图（近
            <el-select v-model="heatmapDays" size="small" style="width:70px" @change="loadHeatmap">
              <el-option :value="7" label="7天" />
              <el-option :value="14" label="14天" />
              <el-option :value="30" label="30天" />
            </el-select>
            天）
          </div>
          <v-chart :option="heatmapOption" style="height:240px" autoresize />
        </div>
      </el-col>
    </el-row>

    <!-- 最新预警 -->
    <div class="chart-card">
      <div class="chart-title">最新预警记录</div>
      <el-table :data="recentAlerts" size="small" :border="false">
        <el-table-column prop="patientName" label="患者" width="100" />
        <el-table-column label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="row.level === 'critical' ? 'danger' : 'warning'" size="small">
              {{ row.level === 'critical' ? '紧急' : '预警' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="内容" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="160" />
      </el-table>
      <div v-if="recentAlerts.length === 0 && !loading" class="empty-tip">暂无预警记录</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, HeatmapChart } from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, VisualMapComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import { getStatsOverview, getPsychDistribution, getSymptomHeatmap } from '@/api/stats'
import type { StatsOverview, PsychDistribution, SymptomHeatmap } from '@/api/stats'

use([CanvasRenderer, PieChart, HeatmapChart,
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, VisualMapComponent])

const loading = ref(false)
const overview = ref<StatsOverview | null>(null)
const psychDist = ref<PsychDistribution | null>(null)
const heatmapData = ref<SymptomHeatmap | null>(null)
const heatmapDays = ref(14)

const recentAlerts = computed(() => overview.value?.recentAlerts || [])

const statCards = computed(() => [
  { label: '在院患者', value: overview.value?.activePatients ?? '-', icon: '🏥', bg: 'rgba(230,81,0,0.12)' },
  { label: '患者总数', value: overview.value?.totalPatients ?? '-', icon: '👥', bg: 'rgba(25,118,210,0.12)' },
  { label: '平均心理能量', value: overview.value?.avgPsychEnergy != null ? `${overview.value.avgPsychEnergy}分` : '-', icon: '💚', bg: 'rgba(76,175,80,0.12)' },
  { label: '打卡率', value: overview.value?.checkinRate != null ? `${Math.round(overview.value.checkinRate * 100)}%` : '-', icon: '✅', bg: 'rgba(255,152,0,0.12)' }
])

const psychOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
  legend: { bottom: 0, itemWidth: 10, itemHeight: 10 },
  color: ['#4CAF50', '#FF9800', '#F44336'],
  series: [{
    type: 'pie',
    radius: ['40%', '65%'],
    center: ['50%', '45%'],
    data: [
      { name: '心理健康', value: psychDist.value?.healthy ?? 0 },
      { name: '轻度预警', value: psychDist.value?.mild ?? 0 },
      { name: '危机预警', value: psychDist.value?.warning ?? 0 }
    ],
    label: { show: false }
  }]
}))

const heatmapOption = computed(() => {
  const d = heatmapData.value
  if (!d || !d.dates?.length) {
    return { title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 13 } } }
  }

  const symp = d.symptoms
  const matrixData: [number, number, number][] = []
  d.matrix.forEach((row, si) => {
    row.forEach((val, di) => { matrixData.push([di, si, val]) })
  })

  return {
    grid: { top: 10, bottom: 60, left: 80, right: 20 },
    xAxis: { type: 'category', data: d.dates, axisLabel: { fontSize: 10, rotate: 45 } },
    yAxis: { type: 'category', data: symp, axisLabel: { fontSize: 11 } },
    visualMap: {
      min: 0, max: 10, calculable: true, orient: 'horizontal',
      left: 'center', bottom: 0, itemHeight: 80,
      inRange: { color: ['#f5f5f5', '#FF8A65', '#E65100'] },
      textStyle: { fontSize: 10 }
    },
    tooltip: {
      formatter: (p: any) => `${symp[p.data[1]]}  ${d.dates[p.data[0]]}<br/>得分：${p.data[2]}`
    },
    series: [{ type: 'heatmap', data: matrixData, emphasis: { itemStyle: { shadowBlur: 6 } } }]
  }
})

async function loadHeatmap() {
  heatmapData.value = await getSymptomHeatmap(heatmapDays.value).catch(() => null)
}

onMounted(async () => {
  loading.value = true
  try {
    ;[overview.value, psychDist.value] = await Promise.all([
      getStatsOverview().catch(() => null),
      getPsychDistribution().catch(() => null)
    ])
    await loadHeatmap()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="scss">
.empty-tip {
  text-align: center;
  color: var(--text-placeholder);
  padding: 20px 0;
  font-size: 13px;
}
</style>
