/**
 * 心芽DTx类型定义
 */

// API响应包装
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

// 临床阶段
export type ClinicalStage = 'ADMISSION' | 'PRETREATMENT' | 'TRANSPLANT' | 'REBUILD' | 'DISCHARGE'

export const ClinicalStageLabels: Record<ClinicalStage, string> = {
  ADMISSION: '入仓期',
  PRETREATMENT: '预处理期',
  TRANSPLANT: '移植期',
  REBUILD: '重建期',
  DISCHARGE: '出仓期'
}

// 患者
export interface Patient {
  id: string
  name: string
  stage: ClinicalStage
  psychEnergy: number
  treeLevel: number
  admissionDate: string
  roomNumber?: string
}

// 宣教内容
export interface EducationContent {
  id: string
  title: string
  category: string
  description: string
  contentType: 'video' | 'article'
  durationSeconds: number
  thumbnailUrl?: string
  mediaUrl?: string
  tags: string[]
}

// 驾驶舱数据
export interface DashboardData {
  totalPatients: number
  patientsByStage: Record<string, number>
  avgPsychEnergy: number
  symptomTrends: SymptomTrend[]
  alerts: AlertItem[]
  learningStats: LearningStats
}

export interface SymptomTrend {
  symptom: string
  lastWeek: number
  thisWeek: number
  changePercent: number
}

export interface AlertItem {
  level: 'warning' | 'error' | 'info'
  message: string
  patientId?: string
}

export interface LearningStats {
  avgCompletionRate: number
  avgWatchTime: number
  engagementRate: number
}

// 希望之树
export interface HopeTreeStatus {
  currentLevel: number
  currentExp: number
  nextLevelExp: number
  totalGrowthDays: number
}
