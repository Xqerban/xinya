import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据看板', icon: 'DataAnalysis', affix: true }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/users/index.vue'),
        meta: { title: '用户管理', icon: 'UserFilled' }
      },
      {
        path: 'education',
        name: 'Education',
        component: () => import('@/views/education/index.vue'),
        meta: { title: '宣教内容', icon: 'VideoPlay' }
      },
      {
        path: 'audit',
        name: 'Audit',
        component: () => import('@/views/audit/index.vue'),
        meta: { title: '审计日志', icon: 'Document' }
      },
      {
        path: 'config',
        name: 'Config',
        redirect: '/config/keywords',
        meta: { title: '系统配置', icon: 'Setting' },
        children: [
          {
            path: 'keywords',
            name: 'ConfigKeywords',
            component: () => import('@/views/config/keywords.vue'),
            meta: { title: '危机关键词', icon: 'Warning' }
          },
          {
            path: 'pro-questions',
            name: 'ConfigProQuestions',
            component: () => import('@/views/config/pro-questions.vue'),
            meta: { title: 'PRO题目配置', icon: 'Edit' }
          }
        ]
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
