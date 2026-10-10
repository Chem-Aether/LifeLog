import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const normalizeBaseUrl = (value, name) => {
  const url = value?.trim().replace(/\/+$/, '')
  if (!url) throw new Error(`Missing environment variable: ${name}`)
  return url
}

export const AUTH_BASE_URL = normalizeBaseUrl(import.meta.env.VITE_AUTH_BASE_URL, 'VITE_AUTH_BASE_URL')
export const BILL_BASE_URL = normalizeBaseUrl(import.meta.env.VITE_BILL_BASE_URL, 'VITE_BILL_BASE_URL')
export const TRAVEL_BASE_URL = normalizeBaseUrl(import.meta.env.VITE_TRAVEL_BASE_URL, 'VITE_TRAVEL_BASE_URL')
export const DIARY_BASE_URL = normalizeBaseUrl(import.meta.env.VITE_DIARY_BASE_URL, 'VITE_DIARY_BASE_URL')

export const MAP_BASE_URL = normalizeBaseUrl(
  import.meta.env.VITE_MAP_BASE_URL,
  'VITE_MAP_BASE_URL',
)
export const MEDIA_BASE_URL = normalizeBaseUrl(import.meta.env.VITE_MEDIA_BASE_URL, 'VITE_MEDIA_BASE_URL')

const attachInterceptors = (service) => {
  service.interceptors.request.use(
    (config) => {
      const token = localStorage.getItem('token')
      if (token) config.headers.token = token
      return config
    },
    error => Promise.reject(error),
  )

  service.interceptors.response.use(
    response => response.config.fullResponse ? response : response.data,
    (error) => {
      const response = error.response
      if (!response) {
        ElMessage.error('网络异常或服务器未启动')
        return Promise.reject(error)
      }

      switch (response.status) {
        case 401:
          if (!String(response.config?.url || '').includes('/user/login')) {
            ElMessage.error('登录已过期，请重新登录')
            localStorage.removeItem('token')
            router.push('/login')
          }
          break
        case 403:
          ElMessage.error('无权限访问')
          break
        case 404:
          ElMessage.error('接口不存在')
          break
        case 500:
          ElMessage.error('服务器异常')
          break
        default:
          break
      }

      return Promise.reject(error)
    },
  )

  return service
}

export const createRequest = baseURL => attachInterceptors(axios.create({
  baseURL,
  timeout: 10000,
}))

export const authRequest = createRequest(AUTH_BASE_URL)
export const billRequest = createRequest(BILL_BASE_URL)
export const travelRequest = createRequest(TRAVEL_BASE_URL)
export const diaryRequest = createRequest(DIARY_BASE_URL)
export const mapRequest = createRequest(MAP_BASE_URL)
export const mediaRequest = createRequest(MEDIA_BASE_URL)

export default authRequest
