<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <span class="brand-icon">⚙️</span>
        <h1>心芽 DTx</h1>
        <p>运维管理平台</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            size="large"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <p class="login-hint">仅限授权运维人员使用</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = ref({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value?.validate()
  loading.value = true
  try {
    const data = await login(form.value.username, form.value.password)
    authStore.setLogin(data)
    ElMessage.success(`欢迎，${data.displayName}`)
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #212121 0%, #37474F 50%, #546E7A 100%);
  padding: 16px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  background: white;
  border-radius: 20px;
  padding: 48px 40px 36px;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.35);
}

.login-brand {
  text-align: center;
  margin-bottom: 36px;

  .brand-icon {
    font-size: 48px;
    display: block;
    margin-bottom: 8px;
  }

  h1 {
    font-size: 28px;
    font-weight: 700;
    color: #E65100;
    margin: 0;
    letter-spacing: 2px;
  }

  p {
    font-size: 13px;
    color: #999;
    margin: 8px 0 0;
  }
}

.login-form {
  :deep(.el-input__wrapper) {
    border-radius: 10px;
  }
}

.login-btn {
  width: 100%;
  border-radius: 10px;
  height: 44px;
  font-size: 15px;
  font-weight: 500;
  background: linear-gradient(90deg, #BF360C, #E65100);
  border: none;

  &:hover { background: linear-gradient(90deg, #870000, #BF360C); }
}

.login-hint {
  text-align: center;
  font-size: 12px;
  color: #bbb;
  margin-top: 16px;
  margin-bottom: 0;
}

@media (max-width: 480px) {
  .login-card { padding: 36px 24px 28px; }
}
</style>
