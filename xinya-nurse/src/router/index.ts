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
        component: () => import('@/views/patients/list.vue'),
        meta: { title: '患者管理', icon: 'User' }
      },
      {
        path: 'patients/:id',
        name: 'PatientDetail',
        component: () => import('@/views/patients/detail.vue'),
        meta: { title: '患者详情', hidden: true }
      },
      {
        path: 'alerts',
        name: 'Alerts',
        component: () => import('@/views/alerts/index.vue'),
        meta: { title: '预警中心', icon: 'Bell' }
      },
      {
        path: 'education',
        name: 'Education',
        component: () => import('@/views/education/index.vue'),
        meta: { title: '宣教内容', icon: 'VideoCamera' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('nurse_token')
  if (!to.meta.public && !token) {
    return '/login'
  }
  if (to.path === '/login' && token) {
    return '/dashboard'
  }
  document.title = `${to.meta.title as string || '医护端'} · 心芽DTx`
})

export default router
