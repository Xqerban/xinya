<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <h1>心芽DTx</h1>
        <p>医护管理平台</p>
      </div>
      
      <el-form :model="loginForm" class="login-form">
        <el-form-item>
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="login-btn"
            size="large"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
      
      <div class="login-footer">
        <p>Demo版本 - 点击登录即可进入系统</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const loginForm = ref({
  username: 'admin',
  password: ''
})

function handleLogin() {
  // Demo版本直接跳转
  router.push('/dashboard')
}
</script>

<style scoped lang="scss">
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #4CAF50 0%, #8BC34A 100%);
}

.login-container {
  width: 400px;
  background: white;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
  
  h1 {
    margin: 0;
    font-size: 32px;
    color: #4CAF50;
  }
  
  p {
    margin: 8px 0 0;
    color: #666;
  }
}

.login-form {
  .login-btn {
    width: 100%;
  }
}

.login-footer {
  text-align: center;
  margin-top: 20px;
  
  p {
    color: #999;
    font-size: 12px;
  }
}
</style>
