<template>
  <div>
    <div class="page-header">
      <span class="page-title">宣教内容管理</span>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建内容</el-button>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar mb-16">
      <el-select v-model="filterStage" placeholder="临床阶段" clearable style="width:130px" @change="loadData">
        <el-option v-for="s in stageOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-select v-model="filterType" placeholder="内容类型" clearable style="width:120px" @change="loadData">
        <el-option value="video" label="视频" />
        <el-option value="article" label="图文" />
      </el-select>
      <el-input v-model="filterKeyword" placeholder="搜索标题" clearable style="width:200px" @change="loadData" />
    </div>

    <el-table :data="contents" v-loading="loading" border>
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column label="阶段" width="100">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ stageLabel(row.stage) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="110" show-overflow-tooltip />
      <el-table-column label="类型" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.contentType === 'video' ? 'primary' : ''">
            {{ row.contentType === 'video' ? '视频' : '图文' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.isActive ? 'success' : 'info'">
            {{ row.isActive ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已同步" width="80">
        <template #default="{ row }">
          <el-icon v-if="row.syncedToClinical" color="#4CAF50"><Select /></el-icon>
          <el-icon v-else color="#BDBDBD"><Clock /></el-icon>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="70" />
      <el-table-column prop="updatedAt" label="更新时间" width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            text size="small"
            :type="row.isActive ? 'warning' : 'success'"
            @click="toggleActive(row)"
          >{{ row.isActive ? '下架' : '上架' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="loadData"
      />
    </div>

    <!-- 新建/编辑 Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="editId ? '编辑宣教内容' : '新建宣教内容'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="内容标题" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="适用阶段">
              <el-select v-model="form.stage" clearable style="width:100%" placeholder="全阶段">
                <el-option v-for="s in stageOptions" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-input v-model="form.category" placeholder="如：移植护理" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="内容类型" prop="contentType">
              <el-select v-model="form.contentType" style="width:100%">
                <el-option value="video" label="视频" />
                <el-option value="article" label="图文" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="时长(秒)">
              <el-input-number v-model="form.durationSeconds" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="封面URL">
          <el-input v-model="form.thumbnailUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="资源URL">
          <el-input v-model="form.mediaUrl" placeholder="视频/图文资源地址" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="标签">
              <el-input v-model="form.tags" placeholder="逗号分隔" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存并同步</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Select, Clock } from '@element-plus/icons-vue'
import {
  listEducationContents, createEducationContent,
  updateEducationContent, deactivateEducationContent
} from '@/api/education'
import type { EducationContentDto } from '@/api/education'

const stageOptions = [
  { value: 'ADMISSION', label: '入仓期' },
  { value: 'PRETREATMENT', label: '预处理期' },
  { value: 'TRANSPLANT', label: '移植期' },
  { value: 'REBUILD', label: '重建期' },
  { value: 'DISCHARGE', label: '出仓期' }
]

function stageLabel(s: string | null) {
  return s ? (stageOptions.find(o => o.value === s)?.label ?? s) : '全阶段'
}

const loading = ref(false)
const submitting = ref(false)
const contents = ref<EducationContentDto[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filterStage = ref('')
const filterType = ref('')
const filterKeyword = ref('')

const dialogVisible = ref(false)
const editId = ref('')
const formRef = ref<FormInstance>()

const defaultForm = () => ({
  title: '', stage: '', category: '', description: '',
  contentType: 'video', durationSeconds: 0,
  thumbnailUrl: '', mediaUrl: '', tags: '', sortOrder: 0
})
const form = ref(defaultForm())

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  category: [{ required: true, message: '请输入分类', trigger: 'blur' }],
  contentType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await listEducationContents({
      stage: filterStage.value || undefined,
      contentType: filterType.value || undefined,
      keyword: filterKeyword.value || undefined,
      page: page.value, pageSize: pageSize.value
    })
    contents.value = res.list
    total.value = Number(res.total)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editId.value = ''
  form.value = defaultForm()
  dialogVisible.value = true
}

function openEdit(row: EducationContentDto) {
  editId.value = row.id
  form.value = {
    title: row.title, stage: row.stage || '',
    category: row.category, description: row.description || '',
    contentType: row.contentType,
    durationSeconds: row.durationSeconds || 0,
    thumbnailUrl: row.thumbnailUrl || '',
    mediaUrl: row.mediaUrl || '',
    tags: row.tags || '', sortOrder: row.sortOrder
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const payload = {
      ...form.value,
      stage: form.value.stage || undefined,
      durationSeconds: form.value.durationSeconds || undefined
    }
    if (editId.value) {
      await updateEducationContent(editId.value, payload)
      ElMessage.success('已更新并同步到临床端')
    } else {
      await createEducationContent(payload)
      ElMessage.success('已创建并同步到临床端')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function toggleActive(row: EducationContentDto) {
  if (row.isActive) {
    await ElMessageBox.confirm(`确认下架《${row.title}》？患者端将立即不可见。`, '确认', {
      confirmButtonText: '下架', cancelButtonText: '取消', type: 'warning'
    }).catch(() => { throw new Error('cancel') })
    await deactivateEducationContent(row.id)
    ElMessage.success('已下架')
  } else {
    await updateEducationContent(row.id, { isActive: true })
    ElMessage.success('已重新上架')
  }
  loadData()
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.filter-bar { display: flex; gap: 10px; flex-wrap: wrap; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
