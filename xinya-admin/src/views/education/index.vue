<template>
  <div class="education-page">
    <div class="page-header">
      <h1 class="page-title">宣教内容管理</h1>
      <el-button type="primary">
        <el-icon><Upload /></el-icon>
        上传内容
      </el-button>
    </div>
    
    <!-- 分类筛选 -->
    <el-card class="filter-card">
      <el-radio-group v-model="selectedCategory" @change="loadContents">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="入仓准备">入仓准备</el-radio-button>
        <el-radio-button value="预处理">预处理</el-radio-button>
        <el-radio-button value="康复护理">康复护理</el-radio-button>
        <el-radio-button value="饮食指导">饮食指导</el-radio-button>
        <el-radio-button value="心理支持">心理支持</el-radio-button>
      </el-radio-group>
    </el-card>
    
    <!-- 内容列表 -->
    <el-row :gutter="20">
      <el-col :span="6" v-for="item in contents" :key="item.id">
        <el-card class="content-card" shadow="hover">
          <div class="content-thumbnail">
            <el-icon class="play-icon"><VideoPlay /></el-icon>
          </div>
          <div class="content-info">
            <h3 class="content-title">{{ item.title }}</h3>
            <p class="content-desc">{{ item.description }}</p>
            <div class="content-meta">
              <el-tag size="small">{{ item.category }}</el-tag>
              <span class="duration">{{ formatDuration(item.durationSeconds) }}</span>
            </div>
          </div>
          <div class="content-actions">
            <el-button type="primary" link size="small">编辑</el-button>
            <el-button type="info" link size="small">预览</el-button>
            <el-button type="danger" link size="small">删除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <el-empty v-if="contents.length === 0" description="暂无内容" />
    
    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="12"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadContents"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Upload, VideoPlay } from '@element-plus/icons-vue'
import { getEducationContents } from '@/api/education'
import type { EducationContent } from '@/types'

const selectedCategory = ref('')
const currentPage = ref(1)
const total = ref(0)
const contents = ref<EducationContent[]>([])

function formatDuration(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

async function loadContents() {
  try {
    const data = await getEducationContents({
      category: selectedCategory.value || undefined,
      page: currentPage.value,
      pageSize: 12
    })
    contents.value = data.contents
    total.value = data.total
  } catch (e) {
    // Mock数据
    contents.value = [
      { id: '1', title: '认识骨髓移植', category: '入仓准备', description: '了解骨髓移植的基本流程', contentType: 'video', durationSeconds: 320, tags: [] },
      { id: '2', title: '预处理期护理要点', category: '预处理', description: '预处理期间的注意事项', contentType: 'video', durationSeconds: 450, tags: [] },
      { id: '3', title: '感染预防指南', category: '康复护理', description: '如何有效预防感染', contentType: 'video', durationSeconds: 280, tags: [] },
      { id: '4', title: '口腔护理技巧', category: '康复护理', description: '保持口腔健康的方法', contentType: 'video', durationSeconds: 200, tags: [] },
      { id: '5', title: '饮食禁忌须知', category: '饮食指导', description: '移植期间的饮食注意事项', contentType: 'video', durationSeconds: 360, tags: [] },
      { id: '6', title: '情绪调节方法', category: '心理支持', description: '保持积极心态的技巧', contentType: 'video', durationSeconds: 400, tags: [] }
    ]
    total.value = 6
  }
}

onMounted(() => {
  loadContents()
})
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.filter-card {
  margin-bottom: 20px;
}

.content-card {
  margin-bottom: 20px;
  
  .content-thumbnail {
    height: 120px;
    background: linear-gradient(135deg, #4CAF50, #8BC34A);
    border-radius: 4px;
    display: flex;
    align-items: center;
    justify-content: center;
    
    .play-icon {
      font-size: 48px;
      color: white;
    }
  }
  
  .content-info {
    padding: 12px 0;
    
    .content-title {
      margin: 0 0 8px;
      font-size: 14px;
      font-weight: 600;
    }
    
    .content-desc {
      margin: 0 0 8px;
      font-size: 12px;
      color: #666;
      height: 36px;
      overflow: hidden;
    }
    
    .content-meta {
      display: flex;
      justify-content: space-between;
      align-items: center;
      
      .duration {
        font-size: 12px;
        color: #999;
      }
    }
  }
  
  .content-actions {
    border-top: 1px solid #eee;
    padding-top: 10px;
    display: flex;
    justify-content: space-around;
  }
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>
