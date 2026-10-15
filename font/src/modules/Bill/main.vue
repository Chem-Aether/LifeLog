<template>
  <div class="bill-layout">
    <header class="mobile-header">
      <button class="menu-toggle" :aria-label="navOpen ? '关闭导航' : '打开导航'" @click="navOpen = !navOpen"><el-icon><Close v-if="navOpen" /><Menu v-else /></el-icon></button>
      <div><strong>账单管理</strong><span>{{ currentSection }}</span></div>
      <RouterLink to="/home" aria-label="返回系统主页"><el-icon><HomeFilled /></el-icon></RouterLink>
    </header>
    <button v-if="navOpen" class="nav-backdrop" aria-label="关闭导航" @click="navOpen = false"></button>
    <aside class="sidebar" :class="{ open: navOpen }">
      <div class="brand"><span class="brand-mark">账</span><div><strong>账单管理</strong><small>PERSONAL FINANCE</small></div><button class="nav-close" aria-label="关闭导航" @click="navOpen = false"><el-icon><Close /></el-icon></button></div>
      <el-menu :default-active="activeMenu" router class="nav-menu">
        <el-menu-item index="/bill/home"><el-icon><DataAnalysis /></el-icon><span>总览</span></el-menu-item>
        <el-menu-item index="/bill/statistics"><el-icon><TrendCharts /></el-icon><span>统计</span></el-menu-item>
        <el-menu-item index="/bill/query"><el-icon><Tickets /></el-icon><span>账单</span></el-menu-item>
        <el-menu-item index="/bill/categories"><el-icon><PriceTag /></el-icon><span>分类</span></el-menu-item>
        <el-menu-item index="/bill/account"><el-icon><Wallet /></el-icon><span>账户</span></el-menu-item>
      </el-menu>
      <router-link class="back-link" to="/home"><el-icon><HomeFilled /></el-icon>返回系统主页</router-link>
    </aside>
    <main class="main-content">
      <router-view v-slot="{ Component, route }">
        <Transition name="bill-page" mode="out-in" appear>
          <component :is="Component" :key="route.fullPath" />
        </Transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Close, DataAnalysis, HomeFilled, Menu, PriceTag, Tickets, TrendCharts, Wallet } from '@element-plus/icons-vue'
import './config/theme.css'

const route = useRoute()
const navOpen = ref(false)
const activeMenu = computed(() => route.path.startsWith('/bill/account') ? '/bill/account' : route.path.startsWith('/bill/categories') ? '/bill/categories' : route.path.startsWith('/bill/query') ? '/bill/query' : route.path)
const currentSection = computed(() => route.path.includes('/statistics') ? '统计' : route.path.includes('/query') ? '账单' : route.path.includes('/categories') ? '分类' : route.path.includes('/account') ? '账户' : '总览')
watch(() => route.path, () => { navOpen.value = false })
</script>

<style>
.bill-layout { display:flex; width:100%; min-height:100vh; background:var(--bill-canvas); color:var(--bill-ink); }
.sidebar { display:flex; width:220px; height:100vh; flex:0 0 220px; flex-direction:column; padding:20px 12px; border-right:1px solid var(--bill-line); background:#fff; color:var(--bill-ink); }
.brand { display:flex; align-items:center; gap:11px; padding:6px 10px 28px; }.brand-mark { display:grid; place-items:center; width:36px; height:36px; flex:0 0 auto; border-radius:7px; background:#258b88; color:#fff; font-weight:700; font-size:18px; }.brand strong,.brand small { display:block; }.brand strong { font-size:15px; }.brand small { margin-top:3px; color:#8b999f; font-size:9px; letter-spacing:.8px; }.nav-close,.mobile-header,.nav-backdrop { display:none; }
.nav-menu { flex:1; border:0!important; background:transparent!important; }.nav-menu .el-menu-item { height:44px; margin:3px 0; border-radius:7px; color:#63747e!important; }.nav-menu .el-menu-item:hover { background:#f1f6f6!important; color:#287d7a!important; }.nav-menu .el-menu-item.is-active { background:#e9f4f2!important; color:#197d79!important; font-weight:600; }.nav-menu .el-icon { margin-right:9px; }
.back-link { display:flex; align-items:center; gap:9px; padding:12px 10px; border-top:1px solid #edf0f2; color:#71818b; font-size:13px; text-decoration:none; }.main-content { flex:1; min-width:0; padding:22px 24px; }
.bill-layout .el-button--primary { --el-button-bg-color:#258b88; --el-button-border-color:#258b88; --el-button-hover-bg-color:#1f7775; --el-button-hover-border-color:#1f7775; --el-button-active-bg-color:#176d6b; --el-button-active-border-color:#176d6b; }
.bill-layout .el-input__wrapper,.bill-layout .el-select__wrapper,.bill-layout .el-textarea__inner { box-shadow:0 0 0 1px #dce4e8 inset; }.bill-layout .el-input__wrapper.is-focus,.bill-layout .el-select__wrapper.is-focused { box-shadow:0 0 0 1px #68aaa7 inset; }
.bill-layout .bill-record-table { --el-table-header-bg-color:#f5f7f9; --el-table-row-hover-bg-color:#f4f9f8; --el-table-border-color:#edf0f2; --el-table-header-text-color:#667984; color:#344957; }
.bill-layout .bill-record-table th.el-table__cell { font-size:12px; font-weight:600; }
.bill-layout .bill-record-table .cell { line-height:1.55; }
.bill-layout .bill-record-table .category-cell { display:inline-flex; align-items:center; gap:6px; }
.bill-layout .bill-record-table .category-cell > span { font-size:15px; }
.bill-layout .bill-record-table .account-name { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.bill-layout .bill-record-table .income { color:#288d76; }.bill-layout .bill-record-table .expense { color:#d76069; }.bill-layout .bill-record-table .transfer { color:#537fbd; }
.bill-layout .bill-record-table .tag-income { color:#288d76; border-color:#b9ded2; background:#eff8f4; }.bill-layout .bill-record-table .tag-expense { color:#c95e66; border-color:#efc6c8; background:#fff4f4; }.bill-layout .bill-record-table .tag-transfer { color:#537fbd; border-color:#c5d5eb; background:#f2f6fc; }
.bill-page-enter-active,.bill-page-leave-active { transition:opacity .22s ease,transform .22s cubic-bezier(.2,.7,.2,1); }.bill-page-enter-from { opacity:0; transform:translateY(10px); }.bill-page-leave-to { opacity:0; transform:translateY(-4px); }
@media(prefers-reduced-motion:reduce) { .bill-page-enter-active,.bill-page-leave-active { transition:none; } }
@media(max-width:760px) {
  .bill-layout { display:block; min-height:100dvh; }.mobile-header { position:fixed; z-index:20; inset:0 0 auto; display:grid; height:58px; grid-template-columns:42px 1fr 42px; align-items:center; padding:0 12px; border-bottom:1px solid #dfe7ea; background:rgba(255,255,255,.96); backdrop-filter:blur(12px); }.mobile-header button,.mobile-header>a { display:grid; width:36px; height:36px; place-items:center; border:0; border-radius:5px; background:transparent; color:#53676f; font-size:18px; text-decoration:none; }.mobile-header button:hover,.mobile-header>a:hover { background:#f0f5f5; color:#258b88; }.mobile-header>div { min-width:0; text-align:center; }.mobile-header strong,.mobile-header span { display:block; }.mobile-header strong { color:#293b49; font-size:13px; }.mobile-header span { margin-top:2px; color:#89979d; font-size:10px; }
  .sidebar { position:fixed; z-index:30; inset:0 auto 0 0; width:min(286px,86vw); height:100dvh; box-sizing:border-box; transform:translateX(-105%); transition:transform .22s ease; box-shadow:12px 0 32px rgba(31,48,57,.12); }.sidebar.open { transform:translateX(0); }.brand { padding-bottom:28px; }.brand small { display:block; }.nav-close { display:grid; width:32px; height:32px; margin-left:auto; place-items:center; border:0; border-radius:5px; background:transparent; color:#71818b; font-size:17px; }.nav-backdrop { position:fixed; z-index:25; inset:0; display:block; border:0; background:rgba(20,35,41,.45); backdrop-filter:blur(2px); }
  .main-content { min-height:100dvh; padding:78px 13px 28px; }
}
</style>
