<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="mb-20">
      <el-col :xs="12" :sm="12" :md="6" v-for="card in statCards" :key="card.key">
        <div class="stat-card" :style="{ borderLeft: `4px solid ${card.color}` }">
          <div class="stat-icon" :style="{ background: card.color + '20', color: card.color }">
            <el-icon :size="22"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-value" :style="{ color: card.color }">
              <span v-if="overview">{{ card.getValue(overview) }}</span>
              <el-skeleton v-else :rows="0" animated style="width:60px" />
            </div>
            <div class="stat-label">{{ card.label }}</div>
            <div v-if="card.sub && overview" class="stat-sub">{{ card.sub(overview) }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16" class="mb-20">
      <!-- 阶段分布饼图 -->
      <el-col :xs="24" :sm="24" :md="8">
        <div class="chart-card">
          <div class="chart-title">患者临床阶段分布</div>
          <v-chart v-if="stageChartOption" :option="stageChartOption" style="height:240px" autoresize />
          <el-skeleton v-else style="height:240px" animated />
        </div>
      </el-col>

      <!-- 心理状态分布环形图 -->
      <el-col :xs="24" :sm="24" :md="8">
        <div class="chart-card">
          <div class="chart-title">患者心理状态分布</div>
          <v-chart v-if="psychChartOption" :option="psychChartOption" style="height:240px" autoresize />
          <el-skeleton v-else style="height:240px" animated />
        </div>
      </el-col>

      <!-- 症状趋势 -->
      <el-col :xs="24" :sm="24" :md="8">
        <div class="chart-card">
          <div class="chart-title">本周症状趋势</div>
          <div v-if="overview" class="symptom-list">
            <div
              v-for="item in overview.symptomTrends"
              :key="item.symptom"
              class="symptom-row"
            >
              <span class="symptom-name">{{ SYMPTOM_LABELS[item.symptom] || item.symptom }}</span>
              <div class="symptom-bar-wrap">
                <div
                  class="symptom-bar"
                  :style="{ width: Math.min((item.thisWeek / 12) * 100, 100) + '%', background: trendColor(item.trend) }"
                />
              </div>
              <el-tag
                :type="item.trend === 'DOWN' ? 'success' : item.trend === 'UP' ? 'danger' : 'info'"
                size="small"
                effect="light"
                style="flex-shrink:0"
              >
                {{ item.trend === 'DOWN' ? '↓ 好转' : item.trend === 'UP' ? '↑ 上升' : '— 平稳' }}
              </el-tag>
            </div>
          </div>
          <el-skeleton v-else :rows="5" animated />
        </div>
      </el-col>
    </el-row>

    <!-- 预警快速列表 -->
    <div class="chart-card">
      <div class="chart-title-row">
        <span class="chart-title" style="margin-bottom:0">未处理预警</span>
        <el-button text type="primary" size="small" @click="router.push('/alerts')">
          查看全部 →
        </el-button>
      </div>

      <el-table
        v-if="overview"
        :data="overview.alerts.filter(a => !a.resolved).slice(0, 5)"
        style="margin-top:12px"
        :row-class-name="alertRowClass"
      >
        <el-table-column label="患者" prop="patientName" width="100" />
        <el-table-column label="级别" width="80">
          <template #default="{ row }">
            <AlertLevelTag :level="row.level" />
          </template>
        </el-table-column>
        <el-table-column label="预警内容" prop="message" show-overflow-tooltip />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="router.push(`/alerts?id=${row.id}`)">
              处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="overview && overview.alerts.filter(a => !a.resolved).length === 0" class="no-alert">
        <el-icon color="#67C23A" :size="20"><CircleCheck /></el-icon>
        <span>当前无未处理预警</span>
      </div>

      <el-skeleton v-if="!overview" :rows="3" animated style="margin-top:12px" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import dayjs from 'dayjs'
import { CircleCheck } from '@element-plus/icons-vue'
import { getOverview, getPsychDistribution } from '@/api/dashboard'
import type { DashboardOverview, PsychDistribution } from '@/types'
import { STAGE_LABELS, STAGE_COLORS } from '@/types'
import AlertLevelTag from '@/components/AlertLevelTag.vue'

use([PieChart, TitleComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const SYMPTOM_LABELS: Record<string, string> = {
  nausea: '恶心呕吐',
  fatigue: '乏力',
  oral_mucositis: '口腔黏膜炎',
  fever: '发热',
  diarrhea: '腹泻',
  anxiety: '焦虑',
  appetite_loss: '食欲不振',
  skin_rash: '皮疹',
  pain: '疼痛',
  insomnia: '失眠'
}

const router = useRouter()
const overview = ref<DashboardOverview | null>(null)
const psychDist = ref<PsychDistribution | null>(null)

const statCards = [
  {
    key: 'total',
    label: '在院患者',
    color: '#1976D2',
    icon: 'User',
    getValue: (o: DashboardOverview) => o.totalPatients,
    sub: (o: DashboardOverview) => `平均能量 ${o.avgPsychEnergy.toFixed(0)}`
  },
  {
    key: 'checkin',
    label: '今日打卡',
    color: '#4CAF50',
    icon: 'Calendar',
    getValue: (o: DashboardOverview) => o.todayCheckInCount,
    sub: (o: DashboardOverview) => `完成率 ${(o.checkInRate * 100).toFixed(0)}%`
  },
  {
    key: 'energy',
    label: '平均心理能量',
    color: '#FF9800',
    icon: 'Sunny',
    getValue: (o: DashboardOverview) => o.avgPsychEnergy.toFixed(1),
    sub: (o: DashboardOverview) => `${o.lowEnergyCount} 人低于预警值`
  },
  {
    key: 'alerts',
    label: '未处理预警',
    color: '#F44336',
    icon: 'Bell',
    getValue: (o: DashboardOverview) => o.alerts.filter((a: any) => !a.resolved).length,
    sub: null
  }
]

const stageChartOption = computed(() => {
  if (!overview.value) return null
  const data = Object.entries(overview.value.patientsByStage).map(([stage, count]) => ({
    name: STAGE_LABELS[stage as any] || stage,
    value: count,
    itemStyle: { color: STAGE_COLORS[stage as any] || '#ccc' }
  })).filter(d => d.value > 0)
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['35%', '65%'],
      center: ['50%', '45%'],
      data,
      label: { show: false }
    }]
  }
})

const psychChartOption = computed(() => {
  if (!psychDist.value) return null
  const d = psychDist.value
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['35%', '65%'],
      center: ['50%', '45%'],
      data: [
        { name: '健康 (60-100)', value: d.healthy.count, itemStyle: { color: '#4CAF50' } },
        { name: '轻度 (40-59)', value: d.mild.count, itemStyle: { color: '#FF9800' } },
        { name: '预警 (0-39)', value: d.warning.count, itemStyle: { color: '#F44336' } }
      ],
      label: { show: false }
    }]
  }
})

function trendColor(trend: string) {
  if (trend === 'DOWN') return '#4CAF50'
  if (trend === 'UP') return '#F44336'
  return '#FF9800'
}

function alertRowClass({ row }: { row: any }) {
  return row.level === 'critical' ? 'critical-row' : ''
}

function formatTime(t: string) {
  return dayjs(t).format('MM-DD HH:mm')
}

onMounted(async () => {
  const [o, p] = await Promise.allSettled([getOverview(), getPsychDistribution()])
  if (o.status === 'fulfilled') overview.value = o.value
  if (p.status === 'fulfilled') psychDist.value = p.value
})
</script>

<style scoped lang="scss">
.dashboard { max-width: 1400px; }

.chart-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0;
}

.symptom-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 8px;
}

.symptom-row {
  display: flex;
  align-items: center;
  gap: 8px;

  .symptom-name {
    width: 76px;
    min-width: 76px;
    font-size: 13px;
    color: var(--text-secondary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .symptom-bar-wrap {
    flex: 1;
    min-width: 0;
    height: 8px;
    background: #f0f4f8;
    border-radius: 4px;
    overflow: hidden;

    .symptom-bar {
      height: 100%;
      border-radius: 4px;
      transition: width 0.5s ease;
    }
  }
}

.no-alert {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  color: var(--text-secondary);
  font-size: 14px;
}

:deep(.critical-row td) {
  background: #fff5f5 !important;
}
</style>
