<template>
  <div class="account-shell">
    <aside class="account-side">
      <RouterLink class="brand" to="/home">
        <span class="brand-mark">L</span>
        <span>LifeLog<small>账户中心</small></span>
      </RouterLink>
      <nav class="side-actions" aria-label="账户导航">
        <RouterLink to="/home" class="side-link"><el-icon><HomeFilled /></el-icon><span>返回总览</span></RouterLink>
        <button class="side-link danger" type="button" @click="logout"><el-icon><SwitchButton /></el-icon><span>退出登录</span></button>
      </nav>
    </aside>

    <main class="account-main">
      <header class="page-header">
        <div>
          <span class="section-eyebrow">ACCOUNT</span>
          <h1>账户管理</h1>
          <p>维护当前 LifeLog 空间的登录资料与密码。</p>
        </div>
        <el-avatar :size="48" class="user-avatar">{{ initials }}</el-avatar>
      </header>

      <section class="profile-panel">
        <div class="profile-card">
          <span class="profile-mark">{{ initials }}</span>
          <div class="profile-copy">
            <small>当前账号</small>
            <strong>{{ profile.account || '加载中' }}</strong>
            <span>{{ profile.userName || '尚未设置昵称' }}</span>
          </div>
        </div>
        <div class="profile-meta">
          <div><small>角色</small><strong>{{ roleLabel }}</strong></div>
          <div><small>登录状态</small><strong>已登录</strong></div>
        </div>
      </section>

      <section class="settings-grid">
        <article class="settings-panel" v-loading="loading">
          <header><h2>个人资料</h2><p>昵称会显示在系统首页与侧边栏。</p></header>
          <el-form label-position="top">
            <el-form-item label="账号">
              <el-input v-model="profile.account" disabled />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model.trim="profileForm.userName" maxlength="50" show-word-limit placeholder="输入展示昵称" />
            </el-form-item>
            <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
          </el-form>
        </article>

        <article class="settings-panel">
          <header><h2>修改密码</h2><p>密码更新后需要重新登录。</p></header>
          <el-form label-position="top">
            <el-form-item label="当前密码">
              <el-input v-model="passwordForm.currentPassword" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-button type="primary" :loading="savingPassword" @click="savePassword">更新密码</el-button>
          </el-form>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { HomeFilled, SwitchButton } from '@element-plus/icons-vue'
import { getCurrentUser, updateCurrentUser, updatePassword } from '@/apis/auth.js'

const router = useRouter()
const loading = ref(false)
const savingProfile = ref(false)
const savingPassword = ref(false)
const profile = reactive({ account: '', userName: '', role: '' })
const profileForm = reactive({ userName: '' })
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const displayName = computed(() => profile.userName || profile.account || localStorage.getItem('username') || 'L')
const initials = computed(() => [...displayName.value][0] || 'L')
const roleLabel = computed(() => ({ 0: '管理员', 1: '管理员', 2: '普通用户' }[profile.role] || '个人用户'))

function clearSession() {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
}

async function loadProfile() {
  loading.value = true
  try {
    const result = await getCurrentUser()
    Object.assign(profile, result.data || {})
    profileForm.userName = profile.userName || ''
    localStorage.setItem('username', profile.userName || profile.account || '个人用户')
  } catch { /* interceptor has already reported auth issues */ }
  finally { loading.value = false }
}

async function saveProfile() {
  savingProfile.value = true
  try {
    const result = await updateCurrentUser({ userName: profileForm.userName })
    Object.assign(profile, result.data || {})
    localStorage.setItem('username', profile.userName || profile.account || '个人用户')
    ElMessage.success('账户资料已保存')
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '账户资料保存失败')
  } finally { savingProfile.value = false }
}

async function savePassword() {
  if (!passwordForm.currentPassword || !passwordForm.newPassword) return ElMessage.warning('请填写当前密码和新密码')
  if (passwordForm.newPassword.length < 8) return ElMessage.warning('新密码至少需要 8 位')
  if (passwordForm.newPassword !== passwordForm.confirmPassword) return ElMessage.warning('两次输入的新密码不一致')
  savingPassword.value = true
  try {
    await updatePassword({ currentPassword: passwordForm.currentPassword, newPassword: passwordForm.newPassword })
    ElMessage.success('密码已更新，请重新登录')
    clearSession()
    router.push('/login')
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '密码更新失败')
  } finally { savingPassword.value = false }
}

async function logout() {
  try {
    await ElMessageBox.confirm('确定退出当前账户吗？', '退出登录', { type: 'warning', confirmButtonText: '退出', cancelButtonText: '取消' })
    clearSession()
    router.push('/login')
  } catch { /* cancelled */ }
}

onMounted(loadProfile)
</script>

<style scoped>
.account-shell { min-height:100vh; display:grid; grid-template-columns:236px minmax(0,1fr); background:#f5f7f8; color:#263a42; }
.account-side { position:sticky; top:0; height:100vh; display:flex; flex-direction:column; padding:25px 16px 16px; border-right:1px solid #e2e9eb; background:#fff; }
.brand { display:flex; align-items:center; gap:11px; padding:0 8px 29px; color:#203a42; font-size:16px; font-weight:700; text-decoration:none; }
.brand-mark { display:grid; width:36px; height:36px; place-items:center; border-radius:8px; color:#fff; background:#198b88; font-family:Georgia,serif; font-size:22px; }
.brand small { display:block; margin-top:3px; color:#84949a; font-size:11px; font-weight:400; }
.side-actions { display:grid; gap:5px; }
.side-link { display:flex; min-height:42px; align-items:center; gap:11px; padding:0 12px; border:0; border-radius:6px; background:transparent; color:#61747b; font:inherit; font-size:13px; text-align:left; text-decoration:none; cursor:pointer; }
.side-link:hover { color:#176d6b; background:#f1f7f6; }
.side-link.danger:hover { color:#bd5d54; background:#fff2f1; }
.account-main { min-width:0; width:min(980px,100%); margin:0 auto; padding:43px 38px 28px; }
.page-header { display:flex; align-items:center; justify-content:space-between; gap:20px; padding-bottom:28px; border-bottom:1px solid #e2e9eb; }
.section-eyebrow { color:#198b88; font-size:12px; font-weight:650; }
.page-header h1 { margin:9px 0 7px; color:#203a42; font-size:27px; font-weight:650; }
.page-header p { margin:0; color:#768990; font-size:13px; }
.user-avatar,.profile-mark { color:#176d6b; background:#dcefed; font-weight:700; }
.profile-panel { display:flex; align-items:stretch; justify-content:space-between; gap:16px; margin-top:24px; }
.profile-card,.profile-meta,.settings-panel { border:1px solid #e0e8e9; border-radius:7px; background:#fff; }
.profile-card { display:flex; min-width:0; flex:1; align-items:center; gap:15px; padding:20px; }
.profile-mark { display:grid; width:54px; height:54px; flex:0 0 auto; place-items:center; border-radius:8px; font-size:22px; }
.profile-copy { min-width:0; }
.profile-copy small,.profile-copy span,.profile-meta small { display:block; color:#84949a; font-size:11px; }
.profile-copy strong { display:block; overflow:hidden; margin:4px 0; color:#293f46; font-size:20px; text-overflow:ellipsis; white-space:nowrap; }
.profile-meta { display:grid; grid-template-columns:repeat(2,minmax(110px,1fr)); gap:1px; overflow:hidden; }
.profile-meta div { padding:18px; background:#fbfcfc; }
.profile-meta strong { display:block; margin-top:7px; color:#334950; font-size:13px; }
.settings-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; margin-top:17px; }
.settings-panel { padding:20px; }
.settings-panel header { margin-bottom:17px; }
.settings-panel h2 { margin:0; color:#293f46; font-size:16px; font-weight:650; }
.settings-panel p { margin:5px 0 0; color:#87969b; font-size:12px; }
.settings-panel :deep(.el-form-item__label) { color:#53676e; font-size:12px; font-weight:600; }
.settings-panel :deep(.el-button--primary) { min-height:36px; padding:0 16px; border-color:#198b88; border-radius:5px; background:#198b88; }
@media(max-width:760px) {
  .account-shell { display:block; }
  .account-side { position:static; height:auto; padding:12px 14px; border-right:0; border-bottom:1px solid #e2e9eb; }
  .brand { padding:0 2px 12px; }
  .side-actions { display:flex; gap:5px; overflow-x:auto; }
  .side-link { flex:0 0 auto; min-height:36px; padding:0 10px; font-size:12px; }
  .account-main { padding:27px 17px 22px; }
  .page-header h1 { font-size:23px; }
  .profile-panel,.settings-grid { grid-template-columns:1fr; flex-direction:column; }
  .profile-meta { grid-template-columns:1fr 1fr; }
}
</style>
