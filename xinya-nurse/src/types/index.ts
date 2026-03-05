// ────────────────────────────────────────────────
// 通用
// ────────────────────────────────────────────────
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

// ────────────────────────────────────────────────
// 枚举
// ────────────────────────────────────────────────
export type ClinicalStage = 'ADMISSION' | 'PRETREATMENT' | 'TRANSPLANT' | 'REBUILD' | 'DISCHARGE'
export type UserRole = 'PATIENT' | 'NURSE' | 'DOCTOR' | 'ADMIN'
export type AlertLevel = 'info' | 'warning' | 'critical'
export type ContentType = 'video' | 'article'

export const STAGE_LABELS: Record<ClinicalStage, string> = {
  ADMISSION: '入仓期',
  PRETREATMENT: '预处理期',
  TRANSPLANT: '移植期',
  REBUILD: '重建期',
  DISCHARGE: '出仓期'
}

export const STAGE_COLORS: Record<ClinicalStage, string> = {
  ADMISSION: '#42A5F5',
  PRETREATMENT: '#FFA726',
  TRANSPLANT: '#EF5350',
  REBUILD: '#66BB6A',
  DISCHARGE: '#78909C'
}

// ────────────────────────────────────────────────
// 认证
// ────────────────────────────────────────────────
export interface LoginResponse {
  token: string
  refreshToken: string
  expiresIn: number
  userId: string
  username: string
  role: UserRole
  displayName: string
}

// ────────────────────────────────────────────────
// 患者
// ────────────────────────────────────────────────
export interface PatientDto {
  id: string
  name: string
  stage: ClinicalStage
  psychEnergy: number
  treeLevel: number
  admissionDate: string
  roomNumber?: string
  diagnosis?: string
  age?: number
  gender?: 'MALE' | 'FEMALE'
  createdAt: string
  updatedAt: string
}

export interface PatientDetail {
  patient: PatientDto
  hopeTree: HopeTreeDto
  latestProRecord?: ProRecord
  todayCheckedIn: boolean
  pendingAlerts: number
}

export interface CreatePatientRequest {
  name: string
  roomNumber?: string
  admissionDate: string
  diagnosis?: string
  age?: number
  gender?: 'MALE' | 'FEMALE'
}

export interface UpdatePatientRequest {
  name?: string
  roomNumber?: string
  diagnosis?: string
  age?: number
  gender?: 'MALE' | 'FEMALE'
}

export interface EnergyTrend {
  patientId: string
  trend: { date: string; psychEnergy: number }[]
  avgEnergy: number
  minEnergy: number
  maxEnergy: number
}

// ────────────────────────────────────────────────
// 临床路径
// ────────────────────────────────────────────────
export interface ClinicalStageInfo {
  stage: ClinicalStage
  stageName: string
  stageOrder: number
  stageStartDate: string
  daysInStage: number
}

export interface StageHistory {
  id: number
  fromStage: ClinicalStage
  toStage: ClinicalStage
  transitionDate: string
  operatorId: string
  operatorNote?: string
  daysInStage: number
}

export interface TransitionRequest {
  patientId: string
  targetStage: ClinicalStage
  operatorNote?: string
}

// ────────────────────────────────────────────────
// PRO 打卡
// ────────────────────────────────────────────────
export interface ProRecord {
  recordDate: string
  totalScore: number
  answers: ProAnswer[]
  createdAt: string
}

export interface ProAnswer {
  questionId: string
  questionTitle: string
  answer: string
  score: number
}

export interface SymptomTrend {
  questionTitle: string
  trend: { date: string; score: number }[]
  avgScore: number
  peakScore: number
  peakDate: string
}

// ────────────────────────────────────────────────
// 希望之树
// ────────────────────────────────────────────────
export interface HopeTreeDto {
  currentLevel: number
  currentExp: number
  nextLevelExp: number
  totalGrowthDays: number
  levelName: string
  levelImageUrl?: string
  todayExpGained: number
  lastGrowthTime: string
}

export interface GrowthHistoryItem {
  id: number
  growthSource: string
  growthSourceName: string
  expAmount: number
  levelBefore: number
  levelAfter: number
  levelUp: boolean
  createdAt: string
}

// ────────────────────────────────────────────────
// 宣教内容
// ────────────────────────────────────────────────
export interface EducationContent {
  id: string
  title: string
  stage?: ClinicalStage
  category: string
  description: string
  contentType: ContentType
  durationSeconds: number
  thumbnailUrl?: string
  mediaUrl?: string
  tags: string[]
  sortOrder: number
  isActive: boolean
}

export interface EducationProgress {
  totalContents: number
  completedContents: number
  completionRate: number
  totalWatchedSeconds: number
  progressList: ContentProgress[]
}

export interface ContentProgress {
  contentId: string
  contentTitle: string
  watchedSeconds: number
  completed: boolean
  lastWatchedAt: string
}

// ────────────────────────────────────────────────
// 驾驶舱
// ────────────────────────────────────────────────
export interface DashboardOverview {
  totalPatients: number
  patientsByStage: Record<ClinicalStage, number>
  avgPsychEnergy: number
  lowEnergyCount: number
  todayCheckInCount: number
  checkInRate: number
  symptomTrends: DashboardSymptomTrend[]
  alerts: AlertDto[]
  learningStats: LearningStats
}

export interface DashboardSymptomTrend {
  symptom: string
  lastWeek: number
  thisWeek: number
  changePercent: number
  trend: 'UP' | 'DOWN' | 'STABLE'
}

export interface LearningStats {
  avgCompletionRate: number
  avgWatchTimeMinutes: number
  engagementRate: number
}

export interface PsychDistribution {
  healthy: { count: number; range: string; percent: number }
  mild: { count: number; range: string; percent: number }
  warning: { count: number; range: string; percent: number }
}

// ────────────────────────────────────────────────
// 预警
// ────────────────────────────────────────────────
export interface AlertDto {
  id: string
  patientId: string
  patientName: string
  alertType: string
  level: AlertLevel
  message: string
  triggerMessage?: string
  resolved: boolean
  resolvedBy?: string
  resolvedNote?: string
  resolvedAt?: string
  createdAt: string
}

export interface AlertsResponse {
  list: AlertDto[]
  total: number
  unresolvedCount: number
}

// ────────────────────────────────────────────────
// 对话历史
// ────────────────────────────────────────────────
export interface ConversationItem {
  id: number
  sessionId: string
  agentType: 'psych' | 'nurse'
  message: string
  isFromUser: boolean
  psychEnergyDelta: number
  crisisAlert: boolean
  createdAt: string
}

// ────────────────────────────────────────────────
// 机器人设备
// ────────────────────────────────────────────────
export interface DeviceStatus {
  deviceId: string
  patientId?: string
  onlineStatus: 'ONLINE' | 'OFFLINE'
  lastHeartbeatAt: string
  networkStatus: string
  batteryLevel: number
  appVersion: string
}

export interface BindCodeResponse {
  bindCode: string
  expiresIn: number
}
