<template>
  <div v-if="detail" class="patient-detail">
    <!-- 顶部信息栏 -->
    <div class="detail-header">
      <el-button :icon="ArrowLeft" text @click="router.back()">返回</el-button>
      <div class="patient-banner">
        <el-avatar :size="48" :style="{ background: '#1976D2', color: 'white', fontSize: '20px' }">
          {{ detail.patient.name.slice(0, 1) }}
        </el-avatar>
        <div class="banner-info">
          <h2>{{ detail.patient.name }}</h2>
          <div class="banner-meta">
            <StageTag :stage="detail.patient.stage" />
            <span class="meta-item">病房：{{ detail.patient.roomNumber || '未分配' }}</span>
            <span class="meta-item">入院：{{ detail.patient.admissionDate }}</span>
            <el-badge v-if="detail.pendingAlerts > 0" :value="detail.pendingAlerts" type="danger">
              <el-tag type="danger" size="small" effect="light">待处理预警</el-tag>
            </el-badge>
          </div>
        </div>
        <div class="banner-stats">
          <div class="mini-stat">
            <div class="mini-val" :class="energyClass(detail.patient.psychEnergy)">{{ detail.patient.psychEnergy }}</div>
            <div class="mini-label">心理能量</div>
          </div>
          <div class="mini-stat">
            <div class="mini-val" style="color:#FF9800">Lv.{{ detail.patient.treeLevel }}</div>
            <div class="mini-label">希望之树</div>
          </div>
          <div class="mini-stat">
            <div class="mini-val" :style="{ color: detail.todayCheckedIn ? '#4CAF50' : '#F44336' }">
              {{ detail.todayCheckedIn ? '已打卡' : '未打卡' }}
            </div>
            <div class="mini-label">今日状态</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Tab 内容 -->
    <el-tabs v-model="activeTab" class="detail-tabs">
      <!-- Tab 1: 基本信息 & 阶段管理 -->
      <el-tab-pane label="基本信息" name="info">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <div class="chart-card">
              <div class="chart-title">患者信息</div>
              <el-form :model="editForm" label-width="90px" :disabled="!editing">
                <el-form-item label="姓名"><el-input v-model="editForm.name" /></el-form-item>
                <el-form-item label="病房号"><el-input v-model="editForm.roomNumber" /></el-form-item>
                <el-form-item label="诊断"><el-input v-model="editForm.diagnosis" /></el-form-item>
                <el-form-item label="年龄"><el-input-number v-model="editForm.age" :min="1" style="width:100%" /></el-form-item>
                <el-form-item label="性别">
                  <el-radio-group v-model="editForm.gender">
                    <el-radio value="MALE">男</el-radio>
                    <el-radio value="FEMALE">女</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-form>
              <div class="form-actions">
                <el-button v-if="!editing" :icon="Edit" @click="editing = true">编辑</el-button>
                <template v-else>
                  <el-button @click="cancelEdit">取消</el-button>
                  <el-button type="primary" :loading="savingInfo" @click="saveInfo">保存</el-button>
                </template>
              </div>
            </div>
          </el-col>

          <el-col :xs="24" :md="12">
            <div class="chart-card">
              <div class="chart-title">临床阶段管理</div>
              <div class="stage-timeline">
                <div
                  v-for="(label, stage) in STAGE_LABELS"
                  :key="stage"
                  class="stage-step"
                  :class="{
                    active: detail.patient.stage === stage,
                    passed: stageOrder(stage as any) < stageOrder(detail.patient.stage),
                  }"
                >
                  <div class="step-dot" />
                  <div class="step-body">
                    <div class="step-name">{{ label }}</div>
                    <div v-if="detail.patient.stage === stage" class="step-badge">当前阶段</div>
                  </div>
                </div>
              </div>

              <el-divider />
              <div class="transition-area">
                <div class="transition-title">阶段流转</div>
                <el-select v-model="targetStage" placeholder="选择目标阶段" style="width:160px">
                  <el-option v-for="(label, stage) in STAGE_LABELS" :key="stage" :label="label" :value="stage" />
                </el-select>
                <el-input v-model="transitionNote" placeholder="备注（可选）" style="margin-top:10px" />
                <el-button
                  type="primary"
                  style="margin-top:10px;width:100%"
                  :loading="transitioning"
                  :disabled="!targetStage"
                  @click="doTransition"
                >
                  确认流转
                </el-button>
              </div>
            </div>

            <!-- 流转历史 -->
            <div class="chart-card" style="margin-top:16px">
              <div class="chart-title">阶段流转历史</div>
              <el-timeline>
                <el-timeline-item
                  v-for="h in stageHistory"
                  :key="h.id"
                  :timestamp="h.transitionDate"
                  placement="top"
                >
                  <div class="history-item">
                    <StageTag :stage="h.fromStage" />
                    <el-icon><Right /></el-icon>
                    <StageTag :stage="h.toStage" />
                    <span class="history-note">{{ h.operatorNote }}</span>
                  </div>
                </el-timeline-item>
              </el-timeline>
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- Tab 2: 心理数据 -->
      <el-tab-pane label="心理数据" name="psych">
        <el-row :gutter="16">
          <el-col :span="24">
            <div class="chart-card">
              <div class="chart-title-row">
                <span class="chart-title" style="margin-bottom:0">心理能量趋势</span>
                <el-radio-group v-model="energyDays" size="small" @change="loadEnergyTrend">
                  <el-radio-button :value="7">近7天</el-radio-button>
                  <el-radio-button :value="14">近14天</el-radio-button>
                  <el-radio-button :value="30">近30天</el-radio-button>
                </el-radio-group>
              </div>
              <v-chart v-if="energyChartOption" :option="energyChartOption" style="height:220px;margin-top:12px" autoresize />
              <el-skeleton v-else style="height:220px" animated />
            </div>
          </el-col>

          <el-col :xs="24" :md="12">
            <div class="chart-card">
              <div class="chart-title">打卡历史</div>
              <el-table :data="proRecords" size="small" max-height="300">
                <el-table-column label="日期" prop="recordDate" width="100" />
                <el-table-column label="总分" prop="totalScore" width="70" align="center" />
                <el-table-column label="时间" width="100">
                  <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="详情">
                  <template #default="{ row }">
                    <el-tooltip :content="row.answers?.map((a: any) => `${a.questionTitle}: ${a.answer}`).join(' | ')" placement="top">
                      <el-button text size="small">查看</el-button>
                    </el-tooltip>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-col>

          <el-col :xs="24" :md="12">
            <div class="chart-card">
              <div class="chart-title-row" style="margin-bottom:10px">
                <span class="chart-title" style="margin-bottom:0">症状趋势</span>
                <el-select v-model="symptomQuestion" size="small" style="width:120px" @change="loadSymptomTrend">
                  <el-option value="q_nausea" label="恶心" />
                  <el-option value="q_fatigue" label="乏力" />
                  <el-option value="q_mood" label="心情" />
                  <el-option value="q_pain" label="疼痛" />
                </el-select>
              </div>
              <v-chart v-if="symptomChartOption" :option="symptomChartOption" style="height:220px" autoresize />
              <el-skeleton v-else style="height:220px" animated />
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- Tab 3: 对话记录 -->
      <el-tab-pane label="对话记录" name="conversation">
        <div class="chart-card">
          <div class="chart-title-row" style="margin-bottom:12px">
            <span class="chart-title" style="margin-bottom:0">对话历史</span>
            <el-radio-group v-model="convAgentType" size="small" @change="loadConversations">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="psych">小芽·心理</el-radio-button>
              <el-radio-button value="nurse">小护士·宣教</el-radio-button>
            </el-radio-group>
          </div>

          <div class="conv-list">
            <div
              v-for="msg in conversations"
              :key="msg.id"
              class="conv-item"
              :class="msg.isFromUser ? 'from-user' : 'from-ai'"
            >
              <div class="conv-meta">
                <span class="conv-role">{{ msg.isFromUser ? '患者' : (msg.agentType === 'psych' ? '小芽' : '小护士') }}</span>
                <el-tag v-if="msg.crisisAlert" type="danger" size="small" effect="dark">危机信号</el-tag>
                <span class="conv-time">{{ formatTime(msg.createdAt) }}</span>
              </div>
              <div class="conv-bubble">{{ msg.message }}</div>
            </div>
            <div v-if="conversations.length === 0" class="empty-tip">暂无对话记录</div>
          </div>

          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="convPage"
              :total="convTotal"
              :page-size="20"
              layout="prev, pager, next"
              small
              background
              @current-change="loadConversations"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 4: 宣教进度 -->
      <el-tab-pane label="宣教进度" name="education">
        <div v-if="eduProgress" class="chart-card">
          <div class="chart-title">学习进度概览</div>
          <el-row :gutter="16" style="margin-bottom:20px">
            <el-col :span="8">
              <div class="edu-stat">
                <div class="edu-val">{{ eduProgress.completedContents }} / {{ eduProgress.totalContents }}</div>
                <div class="edu-label">已完成内容</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="edu-stat">
                <div class="edu-val">{{ (eduProgress.completionRate * 100).toFixed(0) }}%</div>
                <div class="edu-label">完成率</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="edu-stat">
                <div class="edu-val">{{ Math.floor(eduProgress.totalWatchedSeconds / 60) }}分钟</div>
                <div class="edu-label">总学习时长</div>
              </div>
            </el-col>
          </el-row>
          <el-progress :percentage="+(eduProgress.completionRate * 100).toFixed(0)" :stroke-width="10" color="#1976D2" />

          <el-table :data="eduProgress.progressList" style="margin-top:16px" size="small">
            <el-table-column label="内容名称" prop="contentTitle" show-overflow-tooltip />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.completed ? 'success' : 'info'" size="small" effect="light">
                  {{ row.completed ? '已完成' : '进行中' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="观看时长" width="100" align="center">
              <template #default="{ row }">{{ Math.floor(row.watchedSeconds / 60) }}分钟</template>
            </el-table-column>
            <el-table-column label="最近观看" width="130">
              <template #default="{ row }">{{ formatTime(row.lastWatchedAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
        <el-skeleton v-else :rows="6" animated />
      </el-tab-pane>

      <!-- Tab 5: 设备状态 -->
      <el-tab-pane label="设备状态" name="device">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <div class="chart-card">
              <div class="chart-title">机器人设备</div>
              <template v-if="deviceStatus">
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item label="设备ID">{{ deviceStatus.deviceId }}</el-descriptions-item>
                  <el-descriptions-item label="在线状态">
                    <el-tag :type="deviceStatus.onlineStatus === 'ONLINE' ? 'success' : 'danger'" size="small">
                      {{ deviceStatus.onlineStatus === 'ONLINE' ? '在线' : '离线' }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="网络类型">{{ deviceStatus.networkStatus }}</el-descriptions-item>
                  <el-descriptions-item label="电量">{{ deviceStatus.batteryLevel }}%</el-descriptions-item>
                  <el-descriptions-item label="应用版本">{{ deviceStatus.appVersion }}</el-descriptions-item>
                  <el-descriptions-item label="最后心跳">{{ formatTime(deviceStatus.lastHeartbeatAt) }}</el-descriptions-item>
                </el-descriptions>
                <div class="device-actions">
                  <el-button type="primary" plain @click="showBindCode">生成绑定码</el-button>
                  <el-button type="danger" plain @click="confirmUnbind">解绑设备</el-button>
                </div>
              </template>
              <el-empty v-else description="暂无绑定设备" :image-size="60">
                <el-button type="primary" @click="showBindCode">生成绑定码</el-button>
              </el-empty>
            </div>
          </el-col>

          <el-col :xs="24" :md="12">
            <div v-if="bindCodeData" class="chart-card bind-code-card">
              <div class="chart-title">设备绑定码</div>
              <div class="bind-code">{{ bindCodeData.bindCode }}</div>
              <div class="bind-hint">{{ bindCodeData.expiresIn / 60 }} 分钟内有效，请在机器人屏幕输入此码</div>
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
  </div>

  <div v-else-if="pageLoading" class="loading-wrap">
    <el-skeleton :rows="10" animated />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Edit, Right } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import dayjs from 'dayjs'

import { getPatientDetail, updatePatient } from '@/api/patient'
import { getEnergyTrend } from '@/api/patient'
import { transitionStage, getStageHistory } from '@/api/clinical'
import { getProHistory } from '@/api/pro'
import { getSymptomTrend } from '@/api/pro'
import { getEducationProgress } from '@/api/education'
import { getConversationHistory } from '@/api/agent'
import { getDeviceStatus } from '@/api/robot'
import { generateBindCode, unbindDevice } from '@/api/auth'

import type { PatientDetail, StageHistory, EducationProgress, DeviceStatus, BindCodeResponse, ClinicalStage, ConversationItem } from '@/types'
import { STAGE_LABELS } from '@/types'
import StageTag from '@/components/StageTag.vue'

use([LineChart, BarChart, GridComponent, TooltipComponent, TitleComponent, CanvasRenderer])

const route = useRoute()
const router = useRouter()
const patientId = computed(() => route.params.id as string)

const detail = ref<PatientDetail | null>(null)
const pageLoading = ref(true)
const activeTab = ref('info')

// ── 基本信息 ──
const editing = ref(false)
const savingInfo = ref(false)
const editForm = reactive({ name: '', roomNumber: '', diagnosis: '', age: undefined as number | undefined, gender: undefined as string | undefined })

function initEditForm() {
  if (!detail.value) return
  const p = detail.value.patient
  editForm.name = p.name
  editForm.roomNumber = p.roomNumber || ''
  editForm.diagnosis = p.diagnosis || ''
  editForm.age = p.age
  editForm.gender = p.gender
}

function cancelEdit() {
  editing.value = false
  initEditForm()
}

async function saveInfo() {
  savingInfo.value = true
  try {
    await updatePatient(patientId.value, editForm)
    ElMessage.success('保存成功')
    editing.value = false
    const d = await getPatientDetail(patientId.value)
    detail.value = d
  } finally {
    savingInfo.value = false
  }
}

// ── 阶段流转 ──
const stageHistory = ref<StageHistory[]>([])
const targetStage = ref<ClinicalStage | ''>('')
const transitionNote = ref('')
const transitioning = ref(false)

const STAGE_ORDER: Record<string, number> = { ADMISSION: 1, PRETREATMENT: 2, TRANSPLANT: 3, REBUILD: 4, DISCHARGE: 5 }
function stageOrder(stage: ClinicalStage) { return STAGE_ORDER[stage] || 0 }

async function doTransition() {
  if (!targetStage.value) return
  await ElMessageBox.confirm(
    `确认将患者 ${detail.value?.patient.name} 的临床阶段流转为「${STAGE_LABELS[targetStage.value]}」吗？`,
    '阶段流转确认', { type: 'warning' }
  )
  transitioning.value = true
  try {
    await transitionStage({ patientId: patientId.value, targetStage: targetStage.value, operatorNote: transitionNote.value })
    ElMessage.success('阶段流转成功')
    const d = await getPatientDetail(patientId.value)
    detail.value = d
    stageHistory.value = await getStageHistory(patientId.value)
    targetStage.value = ''
    transitionNote.value = ''
  } finally {
    transitioning.value = false
  }
}

// ── 心理数据 ──
const energyDays = ref(14)
const energyChartOption = ref<any>(null)
const proRecords = ref<any[]>([])
const symptomQuestion = ref('q_nausea')
const symptomChartOption = ref<any>(null)

async function loadEnergyTrend() {
  const data = await getEnergyTrend(patientId.value, energyDays.value)
  energyChartOption.value = {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.trend.map(t => t.date), axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { fontSize: 11 } },
    series: [{
      type: 'line',
      data: data.trend.map(t => t.psychEnergy),
      smooth: true,
      areaStyle: { opacity: 0.15 },
      itemStyle: { color: '#1976D2' },
      lineStyle: { width: 2 }
    }],
    grid: { left: 40, right: 20, top: 20, bottom: 30 }
  }
}

async function loadSymptomTrend() {
  const data = await getSymptomTrend({ patientId: patientId.value, questionId: symptomQuestion.value, days: 14 })
  symptomChartOption.value = {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.trend.map(t => t.date), axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', min: 0, axisLabel: { fontSize: 11 } },
    series: [{ type: 'bar', data: data.trend.map(t => t.score), itemStyle: { color: '#FF9800' } }],
    grid: { left: 40, right: 20, top: 20, bottom: 30 }
  }
}

// ── 对话记录 ──
const conversations = ref<ConversationItem[]>([])
const convPage = ref(1)
const convTotal = ref(0)
const convAgentType = ref<'psych' | 'nurse' | ''>('')

async function loadConversations() {
  const res = await getConversationHistory({
    patientId: patientId.value,
    agentType: convAgentType.value || undefined,
    page: convPage.value,
    pageSize: 20
  })
  conversations.value = res.list
  convTotal.value = res.total
}

// ── 宣教进度 ──
const eduProgress = ref<EducationProgress | null>(null)

// ── 设备 ──
const deviceStatus = ref<DeviceStatus | null>(null)
const bindCodeData = ref<BindCodeResponse | null>(null)

async function showBindCode() {
  const res = await generateBindCode(patientId.value)
  bindCodeData.value = res
}

async function confirmUnbind() {
  await ElMessageBox.confirm('确认解绑该机器人设备？解绑后设备 Token 立即失效。', '解绑确认', { type: 'warning' })
  if (!deviceStatus.value?.deviceId) return
  await unbindDevice(deviceStatus.value.deviceId, 'patient_discharge')
  ElMessage.success('解绑成功')
  deviceStatus.value = null
}

function formatTime(t: string) {
  return t ? dayjs(t).format('MM-DD HH:mm') : '-'
}

function energyClass(v: number) {
  if (v >= 60) return 'energy-good'
  if (v >= 40) return 'energy-warn'
  return 'energy-danger'
}

// Tab 懒加载
watch(activeTab, async (tab) => {
  if (tab === 'psych' && !energyChartOption.value) {
    const [, pro] = await Promise.allSettled([loadEnergyTrend(), getProHistory({ patientId: patientId.value, pageSize: 30 })])
    if (pro.status === 'fulfilled') proRecords.value = (pro.value as any).list || []
    loadSymptomTrend()
  }
  if (tab === 'conversation' && conversations.value.length === 0) loadConversations()
  if (tab === 'education' && !eduProgress.value) {
    const res = await getEducationProgress(patientId.value).catch(() => null)
    if (res) eduProgress.value = res
  }
  if (tab === 'device' && !deviceStatus.value) {
    const res = await getDeviceStatus(patientId.value).catch(() => null)
    if (res) deviceStatus.value = res
  }
})

onMounted(async () => {
  try {
    const [d, h] = await Promise.allSettled([
      getPatientDetail(patientId.value),
      getStageHistory(patientId.value)
    ])
    if (d.status === 'fulfilled') {
      detail.value = d.value
      initEditForm()
    }
    if (h.status === 'fulfilled') stageHistory.value = h.value
  } finally {
    pageLoading.value = false
  }
})
</script>

<style scoped lang="scss">
.patient-detail { max-width: 1200px; }

.detail-header { margin-bottom: 16px; }

.patient-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  margin-top: 8px;
  flex-wrap: wrap;
}

.banner-info {
  flex: 1;
  min-width: 0;

  h2 { margin: 0; font-size: 20px; font-weight: 700; }

  .banner-meta {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 8px;
    flex-wrap: wrap;

    .meta-item { font-size: 13px; color: var(--text-secondary); }
  }
}

.banner-stats {
  display: flex;
  gap: 24px;
  flex-shrink: 0;

  .mini-stat {
    text-align: center;
    .mini-val { font-size: 22px; font-weight: 700; }
    .mini-label { font-size: 11px; color: var(--text-secondary); margin-top: 2px; }
  }
}

.energy-good { color: #4CAF50; }
.energy-warn  { color: #FF9800; }
.energy-danger { color: #F44336; }

.detail-tabs { margin-top: 4px; }

.chart-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

// 阶段时间线
.stage-timeline {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;
}

.stage-step {
  display: flex;
  align-items: center;
  gap: 12px;
  opacity: 0.45;

  &.passed, &.active { opacity: 1; }

  .step-dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: #ddd;
    flex-shrink: 0;
    border: 2px solid #ccc;
  }

  &.passed .step-dot { background: #4CAF50; border-color: #4CAF50; }
  &.active .step-dot { background: #1976D2; border-color: #1976D2; width: 16px; height: 16px; }

  .step-body { display: flex; align-items: center; gap: 8px; }
  .step-name { font-size: 14px; font-weight: 500; }
  .step-badge { font-size: 11px; background: #1976D2; color: white; padding: 1px 8px; border-radius: 20px; }
}

.transition-area { display: flex; flex-direction: column; gap: 0; }
.transition-title { font-size: 13px; font-weight: 600; color: var(--text-secondary); margin-bottom: 10px; }

.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  .history-note { font-size: 12px; color: var(--text-secondary); }
}

.form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }

// 对话记录
.conv-list {
  max-height: 400px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0;
}

.conv-item {
  &.from-user .conv-bubble { background: #E3F2FD; margin-left: 40px; border-radius: 12px 12px 4px 12px; }
  &.from-ai .conv-bubble { background: #f5f5f5; margin-right: 40px; border-radius: 12px 12px 12px 4px; }
}

.conv-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  .conv-role { font-size: 12px; font-weight: 600; color: var(--text-secondary); }
  .conv-time { font-size: 11px; color: var(--text-placeholder); margin-left: auto; }
}

.conv-bubble { font-size: 14px; line-height: 1.6; padding: 10px 14px; }

.empty-tip { text-align: center; color: var(--text-secondary); padding: 40px 0; font-size: 14px; }

// 宣教
.edu-stat {
  text-align: center;
  .edu-val { font-size: 24px; font-weight: 700; color: #1976D2; }
  .edu-label { font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
}

// 设备
.device-actions { display: flex; gap: 12px; margin-top: 16px; flex-wrap: wrap; }

.bind-code-card { text-align: center; }
.bind-code { font-size: 48px; font-weight: 900; letter-spacing: 12px; color: #1976D2; padding: 20px 0; }
.bind-hint { font-size: 13px; color: var(--text-secondary); }

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

.loading-wrap { padding: 40px; }

@media (max-width: 767px) {
  .banner-stats { width: 100%; justify-content: space-around; }
}
</style>
