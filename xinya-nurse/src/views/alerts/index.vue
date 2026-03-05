<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">预警中心</h2>
      <el-button type="primary" :icon="Plus" @click="showCreateDialog = true">手动创建预警</el-button>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-radio-group v-model="resolvedFilter" @change="loadAlerts">
        <el-radio-button :value="undefined">全部</el-radio-button>
        <el-radio-button :value="false">
          未处理
          <el-badge v-if="unresolvedCount > 0" :value="unresolvedCount" :max="99" style="margin-left:4px" />
        </el-radio-button>
        <el-radio-button :value="true">已处理</el-radio-button>
      </el-radio-group>

      <el-select v-model="levelFilter" placeholder="全部级别" clearable style="width:130px" @change="loadAlerts">
        <el-option value="critical" label="严重" />
        <el-option value="warning" label="警告" />
        <el-option value="info" label="提示" />
      </el-select>

      <el-button :icon="Refresh" circle plain @click="loadAlerts" />
    </div>

    <!-- 预警卡片列表 -->
    <div v-loading="loading" class="alerts-grid">
      <div
        v-for="alert in alerts"
        :key="alert.id"
        class="alert-card"
        :class="`alert-card--${alert.level}`"
      >
        <div class="alert-card__header">
          <AlertLevelTag :level="alert.level" />
          <span class="alert-time">{{ formatTime(alert.createdAt) }}</span>
        </div>

        <div class="alert-card__patient">
          <el-icon><User /></el-icon>
          <span class="patient-link" @click="router.push(`/patients/${alert.patientId}`)">
            {{ alert.patientName }}
          </span>
        </div>

        <div class="alert-card__msg">{{ alert.message }}</div>

        <div v-if="alert.triggerMessage" class="alert-card__trigger">
          触发语句：「{{ alert.triggerMessage }}」
        </div>

        <div class="alert-card__footer">
          <template v-if="!alert.resolved">
            <el-button type="primary" size="small" @click="openResolveDrawer(alert)">处理预警</el-button>
            <el-button size="small" @click="router.push(`/patients/${alert.patientId}`)">查看患者</el-button>
          </template>
          <template v-else>
            <el-tag type="success" size="small" effect="light">已处理</el-tag>
            <span class="resolved-note">{{ alert.resolvedNote }}</span>
          </template>
        </div>
      </div>

      <el-empty v-if="!loading && alerts.length === 0" description="暂无预警记录" />
    </div>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        background
        small
        @change="loadAlerts"
      />
    </div>

    <!-- 处理预警抽屉 -->
    <el-drawer v-model="showResolveDrawer" title="处理预警" size="420px" direction="rtl">
      <template v-if="resolvingAlert">
        <el-descriptions :column="1" border size="small" style="margin-bottom:20px">
          <el-descriptions-item label="患者">{{ resolvingAlert.patientName }}</el-descriptions-item>
          <el-descriptions-item label="级别">
            <AlertLevelTag :level="resolvingAlert.level" />
          </el-descriptions-item>
          <el-descriptions-item label="预警内容">{{ resolvingAlert.message }}</el-descriptions-item>
          <el-descriptions-item v-if="resolvingAlert.triggerMessage" label="触发语句">
            「{{ resolvingAlert.triggerMessage }}」
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(resolvingAlert.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <el-form label-width="80px">
          <el-form-item label="处理备注">
            <el-input
              v-model="resolveNote"
              type="textarea"
              :rows="4"
              placeholder="请填写处理情况，如：已与患者进行面谈，情绪稳定..."
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="showResolveDrawer = false">取消</el-button>
        <el-button type="primary" :loading="resolving" @click="submitResolve">确认处理</el-button>
      </template>
    </el-drawer>

    <!-- 手动创建预警对话框 -->
    <el-dialog v-model="showCreateDialog" title="手动创建预警" width="460px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="患者" prop="patientId">
          <el-select v-model="createForm.patientId" placeholder="选择患者" filterable style="width:100%">
            <el-option
              v-for="p in patientOptions"
              :key="p.id"
              :label="`${p.name}（${p.roomNumber || '无病房'}）`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="预警级别" prop="level">
          <el-radio-group v-model="createForm.level">
            <el-radio value="info">提示</el-radio>
            <el-radio value="warning">警告</el-radio>
            <el-radio value="critical">严重</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="预警内容" prop="message">
          <el-input v-model="createForm.message" type="textarea" :rows="3" placeholder="描述预警情况..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, User } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { getAlerts, resolveAlert, createAlert } from '@/api/alerts'
import { getPatients } from '@/api/patient'
import type { AlertDto, AlertLevel, PatientDto } from '@/types'
import { useAlertStore } from '@/stores/alert'
import AlertLevelTag from '@/components/AlertLevelTag.vue'

const router = useRouter()
const alertStore = useAlertStore()

const alerts = ref<AlertDto[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const resolvedFilter = ref<boolean | undefined>(false)
const levelFilter = ref<AlertLevel | ''>('')

const unresolvedCount = computed(() => alertStore.unresolvedCount)

async function loadAlerts() {
  loading.value = true
  try {
    const res = await getAlerts({
      resolved: resolvedFilter.value,
      level: levelFilter.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    alerts.value = res.list
    total.value = res.total
    alertStore.unresolvedCount = res.unresolvedCount
  } finally {
    loading.value = false
  }
}

// 处理预警
const showResolveDrawer = ref(false)
const resolvingAlert = ref<AlertDto | null>(null)
const resolveNote = ref('')
const resolving = ref(false)

function openResolveDrawer(alert: AlertDto) {
  resolvingAlert.value = alert
  resolveNote.value = ''
  showResolveDrawer.value = true
}

async function submitResolve() {
  if (!resolvingAlert.value) return
  resolving.value = true
  try {
    await resolveAlert(resolvingAlert.value.id, resolveNote.value)
    ElMessage.success('预警已处理')
    showResolveDrawer.value = false
    alertStore.decrement()
    loadAlerts()
  } finally {
    resolving.value = false
  }
}

// 手动创建预警
const showCreateDialog = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const patientOptions = ref<PatientDto[]>([])

const createForm = reactive({
  patientId: '',
  level: 'info' as AlertLevel,
  message: '',
  alertType: 'manual'
})

const createRules: FormRules = {
  patientId: [{ required: true, message: '请选择患者', trigger: 'change' }],
  message: [{ required: true, message: '请填写预警内容', trigger: 'blur' }]
}

async function submitCreate() {
  await createFormRef.value?.validate()
  creating.value = true
  try {
    await createAlert(createForm)
    ElMessage.success('预警创建成功')
    showCreateDialog.value = false
    createFormRef.value?.resetFields()
    loadAlerts()
  } finally {
    creating.value = false
  }
}

function formatTime(t: string) {
  return dayjs(t).format('MM-DD HH:mm')
}

onMounted(async () => {
  loadAlerts()
  const res = await getPatients({ pageSize: 200 }).catch(() => null)
  if (res) patientOptions.value = res.list
})
</script>

<style scoped lang="scss">
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.alerts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}

.alert-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.06);
  border-left: 4px solid #ccc;
  transition: box-shadow 0.2s;

  &:hover { box-shadow: 0 4px 20px rgba(0,0,0,0.1); }

  &--critical { border-left-color: #F44336; background: #fff5f5; }
  &--warning  { border-left-color: #FF9800; }
  &--info     { border-left-color: #42A5F5; }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
  }

  &__patient {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: var(--text-secondary);
    margin-bottom: 8px;
  }

  &__msg {
    font-size: 14px;
    color: var(--text-primary);
    line-height: 1.5;
    margin-bottom: 8px;
  }

  &__trigger {
    font-size: 12px;
    color: #F44336;
    background: #fff0f0;
    padding: 6px 10px;
    border-radius: 6px;
    margin-bottom: 10px;
    font-style: italic;
  }

  &__footer {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
}

.alert-time { font-size: 12px; color: var(--text-secondary); }

.patient-link {
  cursor: pointer;
  color: var(--primary-color);
  &:hover { text-decoration: underline; }
}

.resolved-note {
  font-size: 12px;
  color: var(--text-secondary);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 20px;
}

@media (max-width: 767px) {
  .alerts-grid { grid-template-columns: 1fr; }
}
</style>
