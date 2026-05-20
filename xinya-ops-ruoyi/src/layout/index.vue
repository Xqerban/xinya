<template>
  <div class="layout-container">
    <!-- 移动端遮罩 -->
    <div v-if="isMobile && sidebarOpen" class="sidebar-overlay" @click="sidebarOpen = false" />

    <!-- 侧边栏 -->
    <aside
      class="layout-sidebar"
      :class="{
        'mobile-open': isMobile && sidebarOpen,
        'mobile-hidden': isMobile && !sidebarOpen
      }"
    >
      <div class="sidebar-brand">
        <span class="brand-icon">⚙️</span>
        <span class="brand-text">心芽运维</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        background-color="transparent"
        text-color="rgba(255,255,255,0.75)"
        active-text-color="#FF8A65"
        router
        :unique-opened="true"
        @select="onMenuSelect"
      >
        <template v-for="item in menuRoutes" :key="item.path">
          <!-- 有子菜单 -->
          <el-sub-menu v-if="item.children && item.children.length" :index="item.path">
            <template #title>
              <el-icon><component :is="item.meta?.icon" /></el-icon>
              <span>{{ item.meta?.title }}</span>
            </template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.path"
              :index="child.path"
            >
              <el-icon><component :is="child.meta?.icon" /></el-icon>
              <span>{{ child.meta?.title }}</span>
            </el-menu-item>
          </el-sub-menu>

          <!-- 无子菜单 -->
          <el-menu-item v-else :index="item.path">
            <el-icon><component :is="item.meta?.icon" /></el-icon>
            <span>{{ item.meta?.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>

      <div class="sidebar-footer">
        <div class="version-tag">v1.0.0</div>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="layout-main">
      <!-- 顶栏 -->
      <header class="layout-header">
        <div class="header-left">
          <el-button
            :icon="sidebarOpen ? Fold : Expand"
            circle
            text
            @click="toggleSidebar"
          />
          <span class="header-title">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <el-tag type="warning" size="small" class="role-tag">运维管理</el-tag>
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="32" class="user-avatar">
                {{ userStore.displayName.charAt(0) || 'A' }}
              </el-avatar>
              <span class="user-name">{{ userStore.displayName }}</span>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ArrowDown, SwitchButton, Fold, Expand } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { logout as apiLogout } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const sidebarOpen = ref(true)
const isMobile = ref(false)

const pageTitle = computed(() => (route.meta.title as string) || '心芽运维平台')
const activeMenu = computed(() => route.path)

const menuRoutes = computed(() => {
  const rootRoute = router.getRoutes().find((r) => r.path === '/')
  if (!rootRoute?.children) return []
  return rootRoute.children
    .filter((r) => !r.meta?.hidden && r.path !== '')
    .map((r) => {
      const fullPath = '/' + r.path
      const children = r.children
        ?.filter((c) => !c.meta?.hidden)
        .map((c) => ({ ...c, path: fullPath + '/' + c.path }))
      return { ...r, path: fullPath, children: children && children.length ? children : undefined }
    })
})

function checkMobile() {
  isMobile.value = window.innerWidth < 992
  sidebarOpen.value = !isMobile.value
}

function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value
}

function onMenuSelect() {
  if (isMobile.value) sidebarOpen.value = false
}

async function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确认退出登录？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    }).catch(() => null)

    try { await apiLogout() } catch {}
    userStore.logout()
    router.push('/login')
  }
}

let resizeTimer: ReturnType<typeof setTimeout>
function onResize() {
  clearTimeout(resizeTimer)
  resizeTimer = setTimeout(checkMobile, 100)
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', onResize)
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
  background: linear-gradient(180deg, #2d3a4b 0%, #1f2d3d 100%);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: transform 0.3s ease;
  z-index: 200;

  &.mobile-hidden {
    position: fixed;
    transform: translateX(-100%);
  }

  &.mobile-open {
    position: fixed;
    transform: translateX(0);
    height: 100%;
    box-shadow: 4px 0 20px rgba(0, 0, 0, 0.3);
  }
}

.sidebar-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 190;
}

.sidebar-brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  flex-shrink: 0;

  .brand-icon {
    font-size: 22px;
  }

  .brand-text {
    font-size: 16px;
    font-weight: 700;
    color: #ff8a65;
    letter-spacing: 1px;
    white-space: nowrap;
  }
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  overflow-y: auto;

  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    height: 48px;
    line-height: 48px;
    color: rgba(255, 255, 255, 0.75);
    white-space: nowrap;

    &:hover {
      background-color: rgba(255, 255, 255, 0.08) !important;
      color: #fff !important;
    }
  }

  :deep(.el-menu-item.is-active) {
    color: #ff8a65 !important;
    background-color: rgba(255, 138, 101, 0.15) !important;
  }

  :deep(.el-menu--inline .el-menu-item) {
    padding-left: 48px !important;
    height: 44px;
    line-height: 44px;
  }

  :deep(.el-icon) {
    font-size: 16px;
    margin-right: 8px;
  }
}

.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;

  .version-tag {
    font-size: 11px;
    color: rgba(255, 255, 255, 0.3);
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
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);

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
    gap: 10px;
  }
}

.role-tag {
  font-weight: 500;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;

  &:hover {
    background: #f5f5f5;
  }

  .user-name {
    font-size: 14px;
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

.user-avatar {
  background: linear-gradient(135deg, #e65100, #ff8a65);
  color: white;
  font-weight: 600;
  flex-shrink: 0;
}

.layout-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: var(--bg-color, #f5f7fa);
}

@media (max-width: 991px) {
  .layout-main {
    width: 100%;
  }

  .layout-content {
    padding: 12px;
  }
}

@media (max-width: 767px) {
  .layout-content {
    padding: 8px;
  }

  .user-name {
    display: none;
  }
}
</style>
