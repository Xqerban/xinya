<template>
  <div class="layout-container">
    <!-- 侧边栏 -->
    <aside class="layout-sidebar">
      <div class="logo">
        <h2>心芽DTx</h2>
        <p>医护管理平台</p>
      </div>
      <el-menu
        :default-active="currentPath"
        class="sidebar-menu"
        background-color="#388E3C"
        text-color="#fff"
        active-text-color="#FFD700"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>数据驾驶舱</span>
        </el-menu-item>
        <el-menu-item index="/patients">
          <el-icon><User /></el-icon>
          <span>患者管理</span>
        </el-menu-item>
        <el-menu-item index="/education">
          <el-icon><VideoCamera /></el-icon>
          <span>宣教内容管理</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Document /></el-icon>
          <span>知识库管理</span>
        </el-menu-item>
      </el-menu>
    </aside>
    
    <!-- 主内容区 -->
    <main class="layout-main">
      <header class="layout-header">
        <span class="header-title">{{ pageTitle }}</span>
        <div class="header-right">
          <el-dropdown>
            <span class="user-info">
              <el-avatar :size="32" icon="User" />
              <span class="user-name">管理员</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>个人设置</el-dropdown-item>
                <el-dropdown-item divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      
      <section class="layout-content">
        <router-view />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { DataBoard, User, VideoCamera, Document } from '@element-plus/icons-vue'

const route = useRoute()

const currentPath = computed(() => route.path)
const pageTitle = computed(() => route.meta.title as string || '心芽DTx')
</script>

<style scoped lang="scss">
.logo {
  padding: 20px;
  text-align: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  
  h2 {
    margin: 0;
    font-size: 22px;
    color: #fff;
  }
  
  p {
    margin: 5px 0 0;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.7);
  }
}

.sidebar-menu {
  border-right: none;
}

.header-title {
  font-size: 16px;
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  
  .user-name {
    margin-left: 8px;
    font-size: 14px;
  }
}
</style>
