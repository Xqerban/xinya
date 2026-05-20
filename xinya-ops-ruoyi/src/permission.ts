import router from './router'

const TOKEN_KEY = 'xinya_ops_token'

router.beforeEach((to) => {
  const token = localStorage.getItem(TOKEN_KEY)

  if (!to.meta.public && !token) {
    return '/login'
  }

  if (to.path === '/login' && token) {
    return '/dashboard'
  }

  const title = (to.meta.title as string) || '运维管理'
  document.title = `${title} · 心芽DTx`
})
