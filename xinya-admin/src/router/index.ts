import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据驾驶舱', icon: 'DataBoard' }
      },
      {
        path: 'patients',
        name: 'Patients',
        component: () => import('@/views/patient/list.vue'),
        meta: { title: '患者管理', icon: 'User' }
      },
      {
        path: 'patients/:id',
        name: 'PatientDetail',
        component: () => import('@/views/patient/detail.vue'),
        meta: { title: '患者详情', hidden: true }
      },
      {
        path: 'education',
        name: 'Education',
        component: () => import('@/views/education/index.vue'),
        meta: { title: '宣教内容管理', icon: 'VideoCamera' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/index.vue'),
        meta: { title: '知识库管理', icon: 'Document' }
      }
    ]
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
