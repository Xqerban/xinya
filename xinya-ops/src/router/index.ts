import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/index.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/index.vue'),
          meta: { title: '数据看板' }
        },
        {
          path: 'users',
          name: 'Users',
          component: () => import('@/views/users/index.vue'),
          meta: { title: '用户管理' }
        },
        {
          path: 'education',
          name: 'Education',
          component: () => import('@/views/education/index.vue'),
          meta: { title: '宣教内容' }
        },
        {
          path: 'config/keywords',
          name: 'ConfigKeywords',
          component: () => import('@/views/config/keywords.vue'),
          meta: { title: '危机关键词' }
        },
        {
          path: 'config/pro-questions',
          name: 'ConfigProQuestions',
          component: () => import('@/views/config/pro-questions.vue'),
          meta: { title: 'PRO题目配置' }
        },
        {
          path: 'audit',
          name: 'Audit',
          component: () => import('@/views/audit/index.vue'),
          meta: { title: '审计日志' }
        }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach(to => {
  const token = localStorage.getItem('ops_token')
  if (!to.meta.public && !token) {
    return '/login'
  }
  if (to.path === '/login' && token) {
    return '/dashboard'
  }
})

export default router
