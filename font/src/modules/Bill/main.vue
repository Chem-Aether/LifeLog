<template>
  <div class="bill-layout">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">账</span><div><strong>账单管理</strong></div></div>
      <el-menu :default-active="$route.path.startsWith('/bill/account') ? '/bill/account' : $route.path" router class="nav-menu">
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
import { DataAnalysis, HomeFilled, PriceTag, Tickets, TrendCharts, Wallet } from '@element-plus/icons-vue'
</script>

<style>
.bill-layout { --bill-ink:#293b49; --bill-muted:#7c8b95; --bill-line:#e4e9ee; --bill-accent:#258b88; --bill-canvas:#f3f5f8; display:flex; width:100vw; height:100vh; overflow:hidden; background:var(--bill-canvas); color:var(--bill-ink); }
.sidebar { display:flex; flex:0 0 220px; flex-direction:column; padding:20px 12px; border-right:1px solid var(--bill-line); background:#fff; color:var(--bill-ink); }
.brand { display:flex; align-items:center; gap:11px; padding:6px 10px 28px; }.brand-mark { display:grid; place-items:center; width:36px; height:36px; border-radius:9px; background:#258b88; color:#fff; font-weight:700; font-size:18px; }.brand strong,.brand small { display:block; }.brand strong { font-size:15px; }.brand small { margin-top:3px; color:#8b999f; font-size:9px; letter-spacing:.8px; }
.nav-menu { flex:1; border:0!important; background:transparent!important; }.nav-menu .el-menu-item { height:44px; margin:3px 0; border-radius:7px; color:#63747e!important; }.nav-menu .el-menu-item:hover { background:#f1f6f6!important; color:#287d7a!important; }.nav-menu .el-menu-item.is-active { background:#e9f4f2!important; color:#197d79!important; font-weight:600; }.nav-menu .el-icon { margin-right:9px; }
.back-link { display:flex; align-items:center; gap:9px; padding:12px 10px; border-top:1px solid #edf0f2; color:#71818b; font-size:13px; text-decoration:none; }.main-content { flex:1; min-width:0; overflow:auto; padding:22px 24px; }
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
@media(max-width:700px) { .bill-layout { flex-direction:column; }.sidebar { flex:0 0 auto; flex-direction:row; align-items:center; padding:8px 12px; border-right:0; border-bottom:1px solid var(--bill-line); }.brand { padding:0; }.brand small,.back-link { display:none; }.nav-menu { display:flex; justify-content:flex-end; }.nav-menu .el-menu-item { margin:0 2px; padding:0 10px; }.nav-menu .el-menu-item span { font-size:12px; }.main-content { padding:16px 12px; } }
</style>
