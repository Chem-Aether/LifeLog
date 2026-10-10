<template>
  <div class="workspace">
    <aside class="sidebar">
      <RouterLink class="brand" to="/home">
        <span class="brand-mark">L</span>
        <span>LifeLog<small>个人记录空间</small></span>
      </RouterLink>

      <div class="nav-caption">工作空间</div>
      <nav class="primary-nav" aria-label="主导航">
        <RouterLink class="nav-link" to="/home" active-class="is-active" exact-active-class="is-active">
          <el-icon><HomeFilled /></el-icon><span>总览</span>
        </RouterLink>
        <RouterLink class="nav-link" to="/travel" active-class="is-active">
          <el-icon><MapLocation /></el-icon><span>旅行记录</span>
        </RouterLink>
        <RouterLink class="nav-link" to="/bill/home" active-class="is-active">
          <el-icon><Coin /></el-icon><span>账单管理</span>
        </RouterLink>
        <RouterLink class="nav-link" to="/diary" active-class="is-active">
          <el-icon><Notebook /></el-icon><span>日记</span>
        </RouterLink>
      </nav>

      <div class="sidebar-bottom">
        <div class="user-card">
          <el-avatar :size="36" class="user-avatar">{{ initials }}</el-avatar>
          <RouterLink class="user-copy" to="/account"><strong>{{ username }}</strong><span>账户管理</span></RouterLink>
          <el-button text circle aria-label="账户管理" title="账户管理" @click="router.push('/account')">
            <el-icon><User /></el-icon>
          </el-button>
          <el-button text circle aria-label="退出登录" title="退出登录" @click="logout">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
      </div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div class="breadcrumb"><span>LifeLog</span><el-icon><ArrowRight /></el-icon><strong>总览</strong></div>
        <div class="topbar-actions">
          <time class="today-label" :datetime="todayIso"><el-icon><Calendar /></el-icon>{{ todayLabel }}</time>
          <el-button text circle aria-label="账户管理" title="账户管理" @click="router.push('/account')">
            <el-icon><User /></el-icon>
          </el-button>
          <el-button text circle aria-label="退出登录" title="退出登录" @click="logout">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
      </header>

      <div class="page-content">
        <section class="welcome-row">
          <div>
            <span class="section-eyebrow">你的生活档案</span>
            <h1>{{ greeting }}，{{ username }}</h1>
            <p>把去过的地方、日常收支，整理成自己的生活记录。</p>
          </div>
          <div class="welcome-mark" aria-hidden="true"><span>L</span><i></i></div>
        </section>

        <section class="module-section" aria-labelledby="module-heading">
          <div class="section-heading">
            <div><h2 id="module-heading">记录空间</h2><p>选择一个模块继续</p></div>
            <span class="module-count">03 个模块</span>
          </div>

          <div class="module-grid">
            <RouterLink class="module-card travel-card" to="/travel">
              <div class="module-card-top">
                <span class="module-icon"><el-icon><MapLocation /></el-icon></span>
                <span class="module-arrow" aria-hidden="true"><el-icon><TopRight /></el-icon></span>
              </div>
              <div class="module-copy">
                <span class="module-overline">TRAVEL</span>
                <h3>旅行记录</h3>
                <p>归档旅行足迹、铁路与航班行程。</p>
              </div>
              <div class="module-tags"><span>足迹</span><span>铁路</span><span>航班</span></div>
            </RouterLink>

            <RouterLink class="module-card bill-card" to="/bill/home">
              <div class="module-card-top">
                <span class="module-icon"><el-icon><Coin /></el-icon></span>
                <span class="module-arrow" aria-hidden="true"><el-icon><TopRight /></el-icon></span>
              </div>
              <div class="module-copy">
                <span class="module-overline">FINANCE</span>
                <h3>账单管理</h3>
                <p>记录资金流动，查看账户与收支统计。</p>
              </div>
              <div class="module-tags"><span>账单</span><span>账户</span><span>统计</span></div>
            </RouterLink>

            <RouterLink class="module-card diary-card" to="/diary">
              <div class="module-card-top">
                <span class="module-icon"><el-icon><Notebook /></el-icon></span>
                <span class="module-arrow" aria-hidden="true"><el-icon><TopRight /></el-icon></span>
              </div>
              <div class="module-copy">
                <span class="module-overline">JOURNAL</span>
                <h3>日记</h3>
                <p>用文字、照片和心情记录生活片段。</p>
              </div>
              <div class="module-tags"><span>图文</span><span>心情</span><span>地点</span></div>
            </RouterLink>
          </div>
        </section>

        <section class="quick-section" aria-labelledby="quick-heading">
          <div class="section-heading compact-heading">
            <div><h2 id="quick-heading">快捷入口</h2><p>直接前往常用页面</p></div>
          </div>
          <div class="quick-links">
            <RouterLink to="/bill/query" class="quick-link">
              <span class="quick-icon bill-quick"><el-icon><Tickets /></el-icon></span>
              <span><strong>账单流水</strong><small>查询与记录收支</small></span>
              <el-icon class="quick-arrow"><ArrowRight /></el-icon>
            </RouterLink>
            <RouterLink to="/bill/statistics" class="quick-link">
              <span class="quick-icon stats-quick"><el-icon><DataAnalysis /></el-icon></span>
              <span><strong>收支统计</strong><small>查看周期与分类分析</small></span>
              <el-icon class="quick-arrow"><ArrowRight /></el-icon>
            </RouterLink>
            <RouterLink to="/travel/admin/footprint" class="quick-link">
              <span class="quick-icon travel-quick"><el-icon><Location /></el-icon></span>
              <span><strong>旅行足迹</strong><small>管理地点与到访记录</small></span>
              <el-icon class="quick-arrow"><ArrowRight /></el-icon>
            </RouterLink>
            <RouterLink to="/diary" class="quick-link">
              <span class="quick-icon diary-quick"><el-icon><Notebook /></el-icon></span>
              <span><strong>写日记</strong><small>记录今天的文字与照片</small></span>
              <el-icon class="quick-arrow"><ArrowRight /></el-icon>
            </RouterLink>
          </div>
        </section>

        <footer class="page-footer"><span>LifeLog</span><span>记录生活，留住时间</span></footer>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  Calendar,
  Coin,
  DataAnalysis,
  HomeFilled,
  Location,
  MapLocation,
  Notebook,
  SwitchButton,
  Tickets,
  TopRight,
  User,
} from '@element-plus/icons-vue'

const router = useRouter()
const username = localStorage.getItem('username') || '个人用户'
const initials = computed(() => [...username][0] || 'L')
const now = new Date()
const todayIso = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
const todayLabel = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric', month: 'long', day: 'numeric', weekday: 'long',
}).format(now)
const hour = now.getHours()
const greeting = hour < 11 ? '早上好' : hour < 18 ? '下午好' : '晚上好'

async function logout() {
  try {
    await ElMessageBox.confirm('确定退出当前账户吗？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    })
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    router.push('/login')
  } catch { /* cancelled */ }
}
</script>

<style scoped>
.workspace { min-height: 100vh; display: grid; grid-template-columns: 236px minmax(0, 1fr); color: #263a42; }
.sidebar { position: sticky; top: 0; height: 100vh; display: flex; flex-direction: column; padding: 25px 16px 16px; background: #fff; border-right: 1px solid #e2e9eb; }
.brand { display: flex; align-items: center; gap: 11px; padding: 0 8px 29px; color: #203a42; font-size: 16px; font-weight: 700; text-decoration: none; }
.brand-mark { width: 36px; height: 36px; display: grid; place-items: center; border-radius: 8px; color: #fff; background: #198b88; font-family: Georgia, serif; font-size: 22px; }
.brand small { display: block; margin-top: 3px; color: #84949a; font-size: 11px; font-weight: 400; }
.nav-caption { padding: 0 12px 10px; color: #97a5aa; font-size: 11px; }
.primary-nav { display: grid; gap: 4px; }
.nav-link { min-height: 42px; display: flex; align-items: center; gap: 11px; padding: 0 12px; border-radius: 6px; color: #61747b; font-size: 13px; text-decoration: none; transition: background-color 0.18s ease, color 0.18s ease; }
.nav-link .el-icon { font-size: 16px; }
.nav-link:hover { color: #176d6b; background: #f1f7f6; }
.nav-link.is-active { color: #176d6b; background: #e9f4f2; font-weight: 650; }
.sidebar-bottom { margin-top: auto; padding-top: 13px; border-top: 1px solid #edf0f1; }
.user-card { display: flex; align-items: center; gap: 10px; min-width: 0; padding: 5px 2px; }
.user-avatar { flex: 0 0 auto; color: #176d6b; background: #dcefed; font-weight: 700; }
.user-copy { flex: 1; min-width: 0; text-decoration: none; }
.user-copy strong, .user-copy span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-copy strong { color: #364b52; font-size: 12px; }
.user-copy span { margin-top: 3px; color: #91a0a5; font-size: 11px; }
.user-card :deep(.el-button) { color: #778a90; }
.main-area { min-width: 0; min-height: 100vh; }
.topbar { height: 60px; display: flex; justify-content: space-between; align-items: center; padding: 0 38px; border-bottom: 1px solid #e5ebed; background: rgba(255, 255, 255, 0.72); }
.breadcrumb { display: flex; align-items: center; gap: 9px; color: #8a9ba1; font-size: 12px; }
.breadcrumb .el-icon { font-size: 12px; }
.breadcrumb strong { color: #435960; font-weight: 600; }
.today-label { display: inline-flex; align-items: center; gap: 8px; color: #778a90; font-size: 12px; }
.today-label .el-icon { color: #198b88; font-size: 15px; }
.topbar-actions { display: flex; align-items: center; gap: 7px; }
.topbar-actions :deep(.el-button) { color: #778a90; }
.page-content { width: min(1080px, 100%); margin: 0 auto; padding: 43px 38px 25px; }
.welcome-row { min-height: 130px; display: flex; align-items: center; justify-content: space-between; gap: 24px; padding-bottom: 31px; border-bottom: 1px solid #e2e9eb; }
.section-eyebrow { color: #198b88; font-size: 12px; font-weight: 650; }
.welcome-row h1 { margin: 9px 0 7px; color: #203a42; font-size: 27px; font-weight: 650; }
.welcome-row p { margin: 0; color: #768990; font-size: 13px; }
.welcome-mark { position: relative; width: 94px; height: 94px; flex: 0 0 auto; display: grid; place-items: center; overflow: hidden; border: 1px solid #d7e8e5; border-radius: 50%; color: #198b88; background: #e5f1ef; }
.welcome-mark span { z-index: 1; font-family: Georgia, serif; font-size: 48px; }
.welcome-mark i { position: absolute; width: 30px; height: 94px; right: 15px; border-left: 1px solid rgba(25, 139, 136, .25); transform: rotate(35deg); }
.module-section { padding-top: 31px; }
.section-heading { display: flex; align-items: end; justify-content: space-between; gap: 16px; margin-bottom: 17px; }
.section-heading h2 { margin: 0; color: #293f46; font-size: 16px; font-weight: 650; }
.section-heading p { margin: 5px 0 0; color: #87969b; font-size: 12px; }
.module-count { color: #8b999e; font-size: 11px; }
.module-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.module-card { min-height: 228px; display: flex; flex-direction: column; padding: 22px; border: 1px solid #e0e8e9; border-radius: 7px; color: inherit; background: #fff; text-decoration: none; transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease; }
.module-card:hover { transform: translateY(-2px); border-color: #b9d8d4; box-shadow: 0 9px 24px rgba(35, 78, 82, .08); }
.module-card-top { display: flex; justify-content: space-between; align-items: center; }
.module-icon { width: 42px; height: 42px; display: grid; place-items: center; border-radius: 7px; font-size: 20px; }
.travel-card .module-icon { color: #187d79; background: #e3f2ef; }
.bill-card .module-icon { color: #b9792a; background: #fbefdc; }
.diary-card .module-icon { color: #bd7552; background: #f7eee7; }
.module-arrow { color: #8ba0a4; font-size: 17px; transition: color .18s ease, transform .18s ease; }
.module-card:hover .module-arrow { color: #198b88; transform: translate(2px, -2px); }
.module-copy { margin-top: auto; padding-top: 18px; }
.module-overline { color: #8a9a9e; font-size: 10px; font-weight: 700; }
.module-copy h3 { margin: 5px 0 6px; color: #2c4249; font-size: 18px; font-weight: 650; }
.module-copy p { margin: 0; color: #7c8d92; font-size: 12px; line-height: 1.65; }
.module-tags { display: flex; gap: 7px; padding-top: 13px; }
.module-tags span { padding: 4px 8px; border: 1px solid #e7ecec; border-radius: 4px; color: #74868b; font-size: 10px; }
.quick-section { padding-top: 32px; }
.compact-heading { margin-bottom: 10px; }
.quick-links { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border-top: 1px solid #e0e8e9; border-bottom: 1px solid #e0e8e9; }
.quick-link { min-width: 0; display: grid; grid-template-columns: 34px minmax(0, 1fr) 18px; align-items: center; gap: 10px; padding: 14px 12px; color: inherit; text-decoration: none; transition: background-color .18s ease; }
.quick-link + .quick-link { border-left: 1px solid #e0e8e9; }
.quick-link:hover { background: #fff; }
.quick-icon { width: 32px; height: 32px; display: grid; place-items: center; border-radius: 6px; font-size: 15px; }
.bill-quick { color: #b9792a; background: #fbefdc; }
.stats-quick { color: #657db3; background: #e9edf8; }
.travel-quick { color: #187d79; background: #e3f2ef; }
.diary-quick { color: #bd7552; background: #f7eee7; }
.quick-link strong, .quick-link small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.quick-link strong { color: #40545a; font-size: 12px; font-weight: 600; }
.quick-link small { margin-top: 4px; color: #909da1; font-size: 10px; }
.quick-arrow { color: #9aa8ac; font-size: 13px; }
.page-footer { display: flex; justify-content: space-between; padding-top: 20px; color: #9aa7ab; font-size: 10px; }

@media (max-width: 900px) {
  .workspace { grid-template-columns: 204px minmax(0, 1fr); }
  .sidebar { padding-inline: 12px; }
  .topbar { padding-inline: 24px; }
  .page-content { padding: 34px 24px 22px; }
  .module-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .quick-links { grid-template-columns: 1fr; }
  .quick-link + .quick-link { border-left: 0; border-top: 1px solid #e0e8e9; }
}

@media (max-width: 640px) {
  .workspace { display: block; }
  .sidebar { position: static; height: auto; padding: 12px 14px 0; border-right: 0; border-bottom: 1px solid #e2e9eb; }
  .brand { padding: 0 2px 12px; }
  .nav-caption, .sidebar-bottom { display: none; }
  .primary-nav { display: flex; gap: 5px; overflow-x: auto; padding-bottom: 9px; }
  .nav-link { min-height: 36px; flex: 0 0 auto; padding: 0 10px; font-size: 12px; }
  .topbar { height: 46px; padding-inline: 17px; }
  .today-label { font-size: 11px; }
  .page-content { padding: 27px 17px 20px; }
  .welcome-row { min-height: 112px; padding-bottom: 23px; }
  .welcome-row h1 { font-size: 23px; }
  .welcome-mark { width: 68px; height: 68px; }
  .welcome-mark span { font-size: 36px; }
  .welcome-mark i { height: 68px; }
  .module-grid { grid-template-columns: 1fr; gap: 10px; }
  .module-card { min-height: 194px; padding: 18px; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { scroll-behavior: auto !important; transition-duration: 0.01ms !important; animation-duration: 0.01ms !important; }
}
</style>
