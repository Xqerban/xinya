<template>
  <div class="layout-container">
    <!-- 平板遮罩 -->
    <div v-if="isMobile && sidebarOpen" class="sidebar-overlay" @click="sidebarOpen = false" />

    <!-- 侧边栏 -->
    <aside class="layout-sidebar" :class="{ 'mobile-open': isMobile && sidebarOpen, 'mobile-hidden': isMobile && !sidebarOpen }">
      <div class="logo">
        <div class="logo-icon">🌱</div>
        <div class="logo-text">
          <div class="logo-title">心芽 DTx</div>
          <div class="logo-sub">医护工作台</div>
        </div>
      </div>

      <el-menu
        :default-active="currentPath"
        class="sidebar-menu"
        background-color="transparent"
        text-color="rgba(255,255,255,0.8)"
        active-text-color="#ffffff"
        router
        @select="onMenuSelect"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>数据驾驶舱</span>
        </el-menu-item>
        <el-menu-item index="/patients">
          <el-icon><User /></el-icon>
          <span>患者管理</span>
        </el-menu-item>
        <el-menu-item index="/alerts">
          <el-icon><Bell /></el-icon>
          <span>
            预警中心
            <el-badge v-if="alertStore.unresolvedCount > 0" :value="alertStore.unresolvedCount" :max="99" class="alert-badge" />
          </span>
        </el-menu-item>
        <el-menu-item index="/education">
          <el-icon><VideoCamera /></el-icon>
          <span>宣教内容</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <div class="version-tag">v1.0.0</div>
      </div>
    </aside>

    <!-- 主区域 -->
    <main class="layout-main">
      <!-- 顶栏 -->
      <header class="layout-header">
        <div class="header-left">
          <el-button
            :icon="isMobile ? (sidebarOpen ? 'Close' : 'Expand') : (sidebarOpen ? 'Fold' : 'Expand')"
            circle
            text
            @click="toggleSidebar"
          />
          <span class="header-title">{{ pageTitle }}</span>
        </div>

        <div class="header-right">
          <!-- 预警铃铛 -->
          <el-tooltip content="预警中心" placement="bottom">
            <el-badge :value="alertStore.unresolvedCount" :hidden="alertStore.unresolvedCount === 0" :max="99">
              <el-button :icon="Bell" circle text @click="router.push('/alerts')" />
            </el-badge>
          </el-tooltip>

          <!-- 用户菜单 -->
          <el-dropdown trigger="click" @command="handleUserCommand">
            <div class="user-info">
              <el-avatar :size="32" :style="{ background: 'var(--primary-color)', color: 'white' }">
                {{ authStore.displayName.slice(0, 1) }}
              </el-avatar>
              <span class="user-name">{{ authStore.displayName }}</span>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile" :icon="UserFilled">个人信息</el-dropdown-item>
                <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区 -->
      <section class="layout-content">
        <router-view />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { DataBoard, User, Bell, VideoCamera, ArrowDown, UserFilled, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useAlertStore } from '@/stores/alert'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const alertStore = useAlertStore()

const sidebarOpen = ref(true)
const isMobile = ref(false)

const currentPath = computed(() => '/' + route.path.split('/')[1])
const pageTitle = computed(() => route.meta.title as string || '心芽DTx')

function checkMobile() {
  isMobile.value = window.innerWidth < 992
  if (isMobile.value) sidebarOpen.value = false
  else sidebarOpen.value = true
}

function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value
}

function onMenuSelect() {
  if (isMobile.value) sidebarOpen.value = false
}

async function handleUserCommand(cmd: string) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
    authStore.logout()
    router.push('/login')
  }
}

let resizeObserver: ReturnType<typeof setTimeout>
function onResize() {
  clearTimeout(resizeObserver)
  resizeObserver = setTimeout(checkMobile, 100)
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', onResize)
  alertStore.fetchUnresolvedCount()
  // 每 60 秒刷新预警数
  const timer = setInterval(() => alertStore.fetchUnresolvedCount(), 60000)
  onUnmounted(() => clearInterval(timer))
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped lang="scss">
.layout-container {
  display: flex;
  min-height: 100vh;
  position: relative;
}

// ── 侧边栏 ──────────────────────────────────────
.layout-sidebar {
  width: 220px;
  min-height: 100vh;
  background: linear-gradient(180deg, #1565C0 0%, #0D47A1 100%);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width 0.3s ease, transform 0.3s ease;
  overflow: hidden;
  position: relative;
  z-index: 200;

  &.mobile-hidden {
    position: fixed;
    transform: translateX(-100%);
  }

  &.mobile-open {
    position: fixed;
    transform: translateX(0);
    height: 100%;
    box-shadow: 4px 0 20px rgba(0,0,0,0.3);
  }
}

.sidebar-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.4);
  z-index: 190;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.12);

  .logo-icon { font-size: 28px; line-height: 1; }

  .logo-title {
    font-size: 16px;
    font-weight: 700;
    color: #fff;
    letter-spacing: 1px;
  }

  .logo-sub {
    font-size: 11px;
    color: rgba(255,255,255,0.6);
    margin-top: 2px;
  }
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  padding: 8px 0;

  :deep(.el-menu-item) {
    height: 48px;
    line-height: 48px;
    margin: 2px 8px;
    border-radius: 8px;
    font-size: 14px;

    &:hover {
      background-color: rgba(255,255,255,0.1) !important;
      color: #fff !important;
    }

    &.is-active {
      background-color: rgba(255,255,255,0.2) !important;
      color: #fff !important;
      font-weight: 600;
    }

    .el-icon { font-size: 18px; margin-right: 10px; }
  }
}

.alert-badge {
  :deep(.el-badge__content) {
    top: -2px;
    right: -8px;
  }
}

.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid rgba(255,255,255,0.1);

  .version-tag {
    font-size: 11px;
    color: rgba(255,255,255,0.4);
    text-align: center;
  }
}

// ── 主区域 ──────────────────────────────────────
.layout-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.layout-header {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 8px;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);

  .header-left {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .header-title {
    font-size: 15px;
    font-weight: 500;
    color: #333;
    margin-left: 4px;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;

  &:hover { background: #f5f7fa; }

  .user-name {
    font-size: 13px;
    color: #333;
    max-width: 80px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .user-arrow {
    font-size: 12px;
    color: #999;
  }
}

.layout-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: var(--bg-color);
}

@media (max-width: 991px) {
  .layout-main { width: 100%; }
  .layout-content { padding: 12px; }
}

@media (max-width: 767px) {
  .layout-content { padding: 8px; }
  .user-name { display: none; }
}
</style>
