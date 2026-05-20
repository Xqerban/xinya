import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('xinya_ops_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  res => {
    const data = res.data
    if (data.code !== undefined && data.code !== 200) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('xinya_ops_token')
      localStorage.removeItem('xinya_ops_user')
      window.location.href = '/login'
    } else {
      ElMessage.error(err.response?.data?.message || '网络异常，请稍后重试')
    }
    return Promise.reject(err)
  }
)

export default request
