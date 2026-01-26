<template>
  <div class="knowledge-page">
    <div class="page-header">
      <h1 class="page-title">知识库管理</h1>
      <el-button type="primary">
        <el-icon><Plus /></el-icon>
        添加知识条目
      </el-button>
    </div>
    
    <el-card>
      <el-table :data="knowledgeItems" style="width: 100%">
        <el-table-column prop="title" label="标题" width="250" />
        <el-table-column prop="category" label="分类" width="150">
          <template #default="{ row }">
            <el-tag>{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容摘要" show-overflow-tooltip />
        <el-table-column prop="keywords" label="关键词" width="200">
          <template #default="{ row }">
            <el-tag v-for="kw in row.keywords" :key="kw" size="small" class="keyword-tag">
              {{ kw }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="150" />
        <el-table-column label="操作" width="150">
          <template #default>
            <el-button type="primary" link size="small">编辑</el-button>
            <el-button type="danger" link size="small">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <div class="info-section">
      <el-alert
        title="知识库说明"
        type="info"
        description="知识库用于存储护理相关的专业知识，供小护士智能体进行RAG检索增强回答。建议按照不同的护理主题进行分类管理。"
        show-icon
        :closable="false"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'

interface KnowledgeItem {
  id: string
  title: string
  category: string
  content: string
  keywords: string[]
  updatedAt: string
}

const knowledgeItems = ref<KnowledgeItem[]>([
  {
    id: '1',
    title: '预处理期常见症状及处理',
    category: '预处理',
    content: '预处理期间患者可能会出现恶心、呕吐、口腔溃疡等症状...',
    keywords: ['恶心', '呕吐', '口腔溃疡'],
    updatedAt: '2026-01-20'
  },
  {
    id: '2',
    title: '感染预防措施',
    category: '护理常识',
    content: '移植后患者免疫力低下，需要严格执行感染预防措施...',
    keywords: ['感染', '预防', '手卫生'],
    updatedAt: '2026-01-18'
  },
  {
    id: '3',
    title: '饮食注意事项',
    category: '饮食指导',
    content: '移植期间需要遵循无菌饮食原则，避免生冷食物...',
    keywords: ['饮食', '禁忌', '无菌'],
    updatedAt: '2026-01-15'
  },
  {
    id: '4',
    title: '口腔护理方法',
    category: '护理技巧',
    content: '正确的口腔护理可以预防口腔溃疡和感染...',
    keywords: ['口腔', '护理', '漱口'],
    updatedAt: '2026-01-12'
  }
])
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.keyword-tag {
  margin-right: 4px;
  margin-bottom: 4px;
}

.info-section {
  margin-top: 20px;
}
</style>
