<template>
  <div class="layout-container">
    <!-- 侧边栏 -->
    <aside class="layout-sidebar" :class="{ collapsed: !sidebarOpen }">
      <div class="sidebar-brand">
        <span class="brand-icon">⚙️</span>
        <span class="brand-text">心芽运维</span>
      </div>

      <nav class="sidebar-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="active"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>

        <div class="nav-divider" />

        <div class="nav-group-title">系统配置</div>
        <router-link
          v-for="item in configItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="active"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
    </aside>

    <!-- 主区域 -->
    <div class="layout-main">
      <!-- 顶栏 -->
      <header class="layout-header">
        <div class="header-left">
          <el-button
            text
            :icon="sidebarOpen ? 'Fold' : 'Expand'"
            @click="sidebarOpen = !sidebarOpen"
          />
          <span class="header-title">{{ currentTitle }}</span>
        </div>
        <div class="header-right">
          <el-tag type="warning" size="small" class="role-tag">运维管理</el-tag>
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="32" class="user-avatar">
                {{ displayName.charAt(0) }}
              </el-avatar>
              <span class="user-name">{{ displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="layout-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { logout } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const sidebarOpen = ref(window.innerWidth > 991)
const displayName = computed(() => authStore.displayName)

const navItems = [
  { path: '/dashboard', label: '数据看板', icon: 'DataAnalysis' },
  { path: '/users', label: '用户管理', icon: 'UserFilled' },
  { path: '/education', label: '宣教内容', icon: 'VideoPlay' },
  { path: '/audit', label: '审计日志', icon: 'Document' }
]

const configItems = [
  { path: '/config/keywords', label: '危机关键词', icon: 'Warning' },
  { path: '/config/pro-questions', label: 'PRO题目', icon: 'Edit' }
]

const currentTitle = computed(() => {
  return route.meta.title as string || '心芽运维平台'
})

async function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确认退出登录？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    }).catch(() => null)

    try { await logout() } catch {}
    authStore.clearLogin()
    router.push('/login')
  }
}
</script>

<style scoped lang="scss">
.sidebar-brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  white-space: nowrap;

  .brand-icon { font-size: 22px; }
  .brand-text {
    font-size: 16px;
    font-weight: 700;
    color: #FF8A65;
    letter-spacing: 1px;
  }
}

.sidebar-nav {
  padding: 12px 0;

  .nav-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 11px 20px;
    color: rgba(255,255,255,0.7);
    text-decoration: none;
    font-size: 14px;
    transition: all 0.2s;
    white-space: nowrap;

    &:hover { color: white; background: rgba(255,255,255,0.08); }
    &.active { color: #FF8A65; background: rgba(255, 138, 101, 0.15); }
    .el-icon { font-size: 16px; }
  }

  .nav-divider {
    height: 1px;
    background: rgba(255,255,255,0.1);
    margin: 8px 12px;
  }

  .nav-group-title {
    font-size: 11px;
    color: rgba(255,255,255,0.4);
    padding: 4px 20px 8px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    white-space: nowrap;
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;

  .header-title {
    font-size: 16px;
    font-weight: 500;
    color: var(--text-primary);
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-tag { font-weight: 500; }

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;
  &:hover { background: #f5f5f5; }

  .user-name { font-size: 14px; color: var(--text-primary); }
}

.user-avatar {
  background: linear-gradient(135deg, #E65100, #FF8A65);
  color: white;
  font-weight: 600;
}
</style>
