<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">宣教内容</h2>
      <span class="total-tip">共 {{ total }} 个内容</span>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索内容标题..."
        :prefix-icon="Search"
        clearable
        style="width:220px"
        @input="onSearch"
      />
      <el-select v-model="stageFilter" placeholder="全部阶段" clearable style="width:140px" @change="loadContents">
        <el-option v-for="(label, val) in STAGE_LABELS" :key="val" :label="label" :value="val" />
      </el-select>
      <el-select v-model="typeFilter" placeholder="全部类型" clearable style="width:120px" @change="loadContents">
        <el-option value="video" label="视频" />
        <el-option value="article" label="图文" />
      </el-select>
      <el-button :icon="Refresh" circle plain @click="loadContents" />
    </div>

    <!-- 内容卡片网格 -->
    <div v-loading="loading" class="contents-grid">
      <div v-for="item in contents" :key="item.id" class="content-card" @click="openDetail(item)">
        <!-- 封面 -->
        <div class="content-thumb" :style="item.thumbnailUrl ? `background-image:url(${item.thumbnailUrl})` : ''">
          <div v-if="!item.thumbnailUrl" class="thumb-placeholder">
            <el-icon :size="32" color="#ccc"><component :is="item.contentType === 'video' ? 'VideoPlay' : 'Document'" /></el-icon>
          </div>
          <el-tag
            class="type-badge"
            :type="item.contentType === 'video' ? 'danger' : 'info'"
            size="small"
            effect="dark"
          >
            {{ item.contentType === 'video' ? '视频' : '图文' }}
          </el-tag>
          <div v-if="item.contentType === 'video' && item.durationSeconds" class="duration-badge">
            {{ formatDuration(item.durationSeconds) }}
          </div>
        </div>

        <!-- 信息 -->
        <div class="content-body">
          <div class="content-title">{{ item.title }}</div>
          <div class="content-desc">{{ item.description }}</div>
          <div class="content-meta">
            <StageTag v-if="item.stage" :stage="item.stage" />
            <el-tag v-else type="info" size="small" effect="light">{{ item.category }}</el-tag>
            <div class="content-tags">
              <el-tag v-for="tag in (item.tags || []).slice(0, 2)" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
            </div>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && contents.length === 0" description="暂无相关内容" />
    </div>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next"
        background
        small
        @change="loadContents"
      />
    </div>

    <!-- 内容详情对话框 -->
    <el-dialog v-model="showDetail" :title="selectedItem?.title" width="600px" top="8vh">
      <template v-if="selectedItem">
        <div v-if="selectedItem.contentType === 'video' && selectedItem.mediaUrl" class="video-wrap">
          <video :src="selectedItem.mediaUrl" controls style="width:100%;border-radius:8px;max-height:300px" />
        </div>
        <div v-else class="video-wrap" style="background:#f5f5f5;height:200px;display:flex;align-items:center;justify-content:center;border-radius:8px">
          <el-icon :size="48" color="#ccc"><Document /></el-icon>
        </div>

        <el-descriptions :column="2" size="small" style="margin-top:16px">
          <el-descriptions-item label="阶段">
            <StageTag v-if="selectedItem.stage" :stage="selectedItem.stage" />
            <span v-else>{{ selectedItem.category }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="类型">{{ selectedItem.contentType === 'video' ? '视频' : '图文' }}</el-descriptions-item>
          <el-descriptions-item v-if="selectedItem.durationSeconds" label="时长">{{ formatDuration(selectedItem.durationSeconds) }}</el-descriptions-item>
          <el-descriptions-item label="标签">
            <span>{{ (selectedItem.tags || []).join('、') || '无' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <div style="margin-top:12px;font-size:14px;line-height:1.7;color:#555">
          {{ selectedItem.description }}
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Search, Refresh, Document } from '@element-plus/icons-vue'
import { getContents } from '@/api/education'
import type { EducationContent, ClinicalStage } from '@/types'
import { STAGE_LABELS } from '@/types'
import StageTag from '@/components/StageTag.vue'

const contents = ref<EducationContent[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(12)
const loading = ref(false)
const keyword = ref('')
const stageFilter = ref<ClinicalStage | ''>('')
const typeFilter = ref('')

let searchTimer: ReturnType<typeof setTimeout>
function onSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadContents, 400)
}

async function loadContents() {
  loading.value = true
  try {
    const res = await getContents({
      stage: stageFilter.value || undefined,
      contentType: typeFilter.value || undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    contents.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const showDetail = ref(false)
const selectedItem = ref<EducationContent | null>(null)

function openDetail(item: EducationContent) {
  selectedItem.value = item
  showDetail.value = true
}

function formatDuration(sec: number) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

onMounted(loadContents)
</script>

<style scoped lang="scss">
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.total-tip {
  font-size: 13px;
  color: var(--text-secondary);
}

.contents-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.content-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(0,0,0,0.06);
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;

  &:hover {
    box-shadow: 0 6px 24px rgba(0,0,0,0.12);
    transform: translateY(-2px);
  }
}

.content-thumb {
  height: 160px;
  background: #f0f4f8;
  background-size: cover;
  background-position: center;
  position: relative;

  .thumb-placeholder {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .type-badge {
    position: absolute;
    top: 8px;
    left: 8px;
  }

  .duration-badge {
    position: absolute;
    bottom: 8px;
    right: 8px;
    background: rgba(0,0,0,0.6);
    color: white;
    font-size: 11px;
    padding: 2px 8px;
    border-radius: 4px;
  }
}

.content-body {
  padding: 14px;
}

.content-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.content-desc {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.content-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;

  .content-tags {
    display: flex;
    gap: 4px;
  }
}

.video-wrap { margin-bottom: 4px; }

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 20px;
}

@media (max-width: 767px) {
  .contents-grid { grid-template-columns: repeat(2, 1fr); gap: 10px; }
}

@media (max-width: 480px) {
  .contents-grid { grid-template-columns: 1fr; }
}
</style>
