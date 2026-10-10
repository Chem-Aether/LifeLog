<template>
  <AuthShell>
    <div class="auth-form">
      <div class="form-heading">
        <span class="form-eyebrow">欢迎回来</span>
        <h2>登录 LifeLog</h2>
        <p>登录后继续管理你的旅行与账单记录。</p>
      </div>

      <el-form ref="formRef" :model="loginForm" :rules="rules" :validate-on-rule-change="false" label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="账号" prop="username">
          <el-input
            v-model.trim="loginForm.username"
            autocomplete="username"
            placeholder="请输入账号"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            autocomplete="current-password"
            placeholder="请输入密码"
            type="password"
            show-password
            size="large"
            :prefix-icon="Lock"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item label="验证码" prop="captcha">
          <div class="captcha-row">
            <el-input
              v-model.trim="loginForm.captcha"
              autocomplete="off"
              placeholder="输入图中字符"
              size="large"
              :prefix-icon="Key"
              @keyup.enter="handleLogin"
            />
            <el-button
              class="captcha-refresh"
              :loading="captchaLoading"
              aria-label="刷新验证码"
              title="点击刷新验证码"
              @click="loadCaptcha"
            >
              <img v-if="captchaImage" :src="captchaImage" alt="验证码，点击刷新" />
              <span v-else>刷新</span>
            </el-button>
          </div>
        </el-form-item>

        <el-button class="submit-button" type="primary" native-type="submit" :loading="submitting">
          {{ submitting ? '正在登录' : '登录' }}
        </el-button>
      </el-form>

      <div class="form-footer">
        <span>首次使用 LifeLog？</span>
        <el-link :underline="false" @click="router.push('/register')">设置账号</el-link>
      </div>
    </div>
  </AuthShell>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Key, Lock, User } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import AuthShell from './AuthShell.vue'
import { captcha, login } from '@/apis/auth.js'

const router = useRouter()
const formRef = ref()
const loginForm = reactive({ username: '', password: '', captcha: '' })
const captchaKey = ref('')
const captchaImage = ref('')
const captchaLoading = ref(false)
const submitting = ref(false)
let captchaObjectUrl = ''

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captcha: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

async function loadCaptcha() {
  if (captchaLoading.value) return
  captchaLoading.value = true
  try {
    const response = await captcha()
    const nextUrl = URL.createObjectURL(new Blob([response.data]))
    if (captchaObjectUrl) URL.revokeObjectURL(captchaObjectUrl)
    captchaObjectUrl = nextUrl
    captchaImage.value = nextUrl
    captchaKey.value = response.headers['captcha-key'] || ''
  } catch {
    ElMessage.error('验证码暂时无法加载，请重试')
  } finally {
    captchaLoading.value = false
  }
}

async function handleLogin() {
  if (submitting.value) return
  submitting.value = true
  try {
    try {
      await formRef.value.validate()
    } catch {
      return
    }
    if (!captchaKey.value) {
      ElMessage.warning('验证码尚未就绪，请刷新后重试')
      return
    }
    const response = await login({
      account: loginForm.username,
      password: loginForm.password,
      captchaCode: loginForm.captcha,
      captchaKey: captchaKey.value,
    })
    const token = response.headers.token
    if (!token) throw new Error('登录响应缺少认证令牌')
    localStorage.setItem('token', token)
    localStorage.setItem('username', response.data.data?.userName || loginForm.username)
    ElMessage.success(response.data.msg || '登录成功')
    await router.replace('/home')
  } catch (error) {
    ElMessage.error(error.response?.data?.msg || error.message || '登录失败，请稍后重试')
    loginForm.captcha = ''
    await loadCaptcha()
  } finally {
    submitting.value = false
  }
}

onMounted(loadCaptcha)
onBeforeUnmount(() => {
  if (captchaObjectUrl) URL.revokeObjectURL(captchaObjectUrl)
})
</script>

<style scoped>
.auth-form { width: 100%; max-width: 370px; margin: 0 auto; }
.form-heading { margin-bottom: 34px; }
.form-eyebrow { color: #198b88; font-size: 13px; font-weight: 700; }
.form-heading h2 { margin: 10px 0 8px; color: #203a42; font-size: 27px; font-weight: 650; }
.form-heading p { margin: 0; color: #778990; font-size: 14px; line-height: 1.7; }
.auth-form :deep(.el-form-item) { margin-bottom: 21px; }
.auth-form :deep(.el-form-item__label) { margin-bottom: 7px; color: #3f555d; font-size: 13px; font-weight: 600; }
.auth-form :deep(.el-input__wrapper) { min-height: 44px; box-shadow: 0 0 0 1px #d9e2e4 inset; }
.auth-form :deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px #88b9b7 inset; }
.auth-form :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px #198b88 inset; }
.captcha-row { display: grid; grid-template-columns: minmax(0, 1fr) 116px; width: 100%; gap: 10px; }
.captcha-refresh { width: 116px; height: 44px; padding: 0; overflow: hidden; border-color: #d9e2e4; background: #f7f9f9; }
.captcha-refresh img { width: 100%; height: 100%; object-fit: cover; }
.submit-button { width: 100%; height: 46px; margin-top: 8px; font-size: 15px; font-weight: 650; }
.form-footer { display: flex; justify-content: center; gap: 8px; margin-top: 26px; color: #809198; font-size: 13px; }

@media (max-width: 380px) {
  .form-heading { margin-bottom: 25px; }
  .form-heading h2 { font-size: 24px; }
  .captcha-row { grid-template-columns: minmax(0, 1fr) 96px; gap: 7px; }
  .captcha-refresh { width: 96px; }
}
</style>
