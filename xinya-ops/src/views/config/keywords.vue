<template>
  <div>
    <div class="page-header">
      <span class="page-title">危机关键词</span>
    </div>

    <el-row :gutter="20">
      <!-- 关键词列表 -->
      <el-col :xs="24" :md="16">
        <div class="chart-card">
          <div class="chart-title">
            当前词库（共 {{ keywords.length }} 条）
            <el-text type="info" size="small" style="margin-left:8px">变更后自动同步到AI识别引擎</el-text>
          </div>

          <div v-if="loading" class="empty-tip">加载中…</div>
          <div v-else-if="keywords.length === 0" class="empty-tip">暂无关键词</div>

          <div class="keyword-groups" v-else>
            <div class="kw-group">
              <div class="kw-group-title">
                <el-tag type="danger" size="small">紧急 critical</el-tag>
              </div>
              <div class="kw-tags">
                <el-tag
                  v-for="kw in criticalKeywords"
                  :key="kw.id"
                  type="danger"
                  closable
                  @close="handleDelete(kw)"
                >{{ kw.keyword }}</el-tag>
              </div>
            </div>

            <div class="kw-group">
              <div class="kw-group-title">
                <el-tag type="warning" size="small">预警 warning</el-tag>
              </div>
              <div class="kw-tags">
                <el-tag
                  v-for="kw in warningKeywords"
                  :key="kw.id"
                  type="warning"
                  closable
                  @close="handleDelete(kw)"
                >{{ kw.keyword }}</el-tag>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 新增表单 -->
      <el-col :xs="24" :md="8">
        <div class="chart-card">
          <div class="chart-title">添加关键词</div>
          <el-form :model="addForm" label-position="top" @submit.prevent="handleAdd">
            <el-form-item label="关键词">
              <el-input v-model="addForm.keyword" placeholder="如：想死" clearable />
            </el-form-item>
            <el-form-item label="危机等级">
              <el-radio-group v-model="addForm.crisisLevel">
                <el-radio-button value="warning">预警 warning</el-radio-button>
                <el-radio-button value="critical">紧急 critical</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-button
              type="primary"
              :loading="adding"
              :disabled="!addForm.keyword.trim()"
              @click="handleAdd"
            >
              添加并同步
            </el-button>
          </el-form>
        </div>

        <div class="chart-card" style="margin-top:16px">
          <div class="chart-title">说明</div>
          <ul class="tips-list">
            <li>关键词由 AI 心理陪护智能体实时匹配患者对话</li>
            <li><b>critical</b>：立即触发护士预警推送</li>
            <li><b>warning</b>：触发正念引导并记录日志</li>
            <li>删除关键词后会立即同步，历史预警记录不受影响</li>
          </ul>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCrisisKeywords, createCrisisKeyword, deleteCrisisKeyword } from '@/api/config'
import type { CrisisKeywordDto } from '@/api/config'

const loading = ref(false)
const adding = ref(false)
const keywords = ref<CrisisKeywordDto[]>([])

const criticalKeywords = computed(() => keywords.value.filter(k => k.crisisLevel === 'critical'))
const warningKeywords = computed(() => keywords.value.filter(k => k.crisisLevel === 'warning'))

const addForm = ref({ keyword: '', crisisLevel: 'warning' })

async function loadData() {
  loading.value = true
  try {
    keywords.value = await listCrisisKeywords()
  } finally {
    loading.value = false
  }
}

async function handleAdd() {
  const kw = addForm.value.keyword.trim()
  if (!kw) return

  adding.value = true
  try {
    await createCrisisKeyword({ keyword: kw, crisisLevel: addForm.value.crisisLevel })
    ElMessage.success('已添加并同步到 AI 引擎')
    addForm.value.keyword = ''
    loadData()
  } finally {
    adding.value = false
  }
}

async function handleDelete(kw: CrisisKeywordDto) {
  await ElMessageBox.confirm(`确认删除关键词"${kw.keyword}"？`, '确认', {
    confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
  }).catch(() => { throw new Error('cancel') })

  await deleteCrisisKeyword(kw.id)
  ElMessage.success('已删除并同步')
  loadData()
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.keyword-groups { display: flex; flex-direction: column; gap: 16px; }

.kw-group {
  .kw-group-title { margin-bottom: 10px; }
  .kw-tags { display: flex; flex-wrap: wrap; gap: 8px; }
}

.empty-tip { text-align: center; color: var(--text-placeholder); padding: 24px 0; }

.tips-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 2;

  li { margin-bottom: 2px; }
}
</style>
