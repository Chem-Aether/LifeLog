<template>
  <div class="travel-admin">
    <header class="mobile-header">
      <button class="menu-toggle" :aria-label="navOpen ? '关闭导航' : '打开导航'" @click="navOpen = !navOpen"><el-icon><Close v-if="navOpen" /><Menu v-else /></el-icon></button>
      <div><strong>旅行管理</strong><span>{{ currentSection }}</span></div>
      <router-link to="/home" aria-label="返回系统主页" title="返回系统主页"><el-icon><HomeFilled /></el-icon></router-link>
    </header>
    <div v-if="navOpen" class="nav-backdrop" @click="navOpen = false"></div>
    <aside class="admin-nav" :class="{ open: navOpen }">
      <div class="brand-block">
        <div class="brand-mark"><el-icon><MapLocation /></el-icon></div>
        <div><strong>旅行管理</strong><span>TRAVEL LOG</span></div>
        <button class="nav-close" aria-label="关闭导航" @click="navOpen = false"><el-icon><Close /></el-icon></button>
      </div>
      <nav aria-label="旅行管理导航">
        <p>旅行浏览</p>
        <router-link to="/travel/map"><el-icon><MapLocation /></el-icon><span>旅行地图<small>路线与地点总览</small></span></router-link>
        <router-link to="/travel/admin/album"><el-icon><Picture /></el-icon><span>城市图册<small>按城市浏览旅行照片</small></span></router-link>
        <router-link to="/travel/admin/statistics"><el-icon><DataAnalysis /></el-icon><span>旅行统计<small>探索出行数据</small></span></router-link>
        <p>数据管理</p>
        <router-link to="/travel/admin/trainTicket"><el-icon><Tickets /></el-icon><span>铁路行程<small>车票与途经站</small></span></router-link>
        <router-link to="/travel/admin/flightTicket"><el-icon><Position /></el-icon><span>航空行程<small>航班与里程记录</small></span></router-link>
        <router-link to="/travel/admin/footprint"><el-icon><LocationInformation /></el-icon><span>旅行足迹<small>地点与探索记录</small></span></router-link>
      </nav>
      <div class="nav-footer">
        <button class="export-button" :disabled="exporting" @click="exportAll"><el-icon><Download /></el-icon><span>{{ exporting ? '正在生成...' : '导出全部数据' }}</span></button>
        <div class="nav-links"><router-link to="/home"><el-icon><HomeFilled /></el-icon><span>返回系统主页</span></router-link></div>
      </div>
    </aside>
    <main class="admin-content"><div class="content-frame"><router-view /></div></main>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Close, DataAnalysis, Download, HomeFilled, LocationInformation, MapLocation, Menu, Picture, Position, Tickets } from '@element-plus/icons-vue'
import { exportTravelData } from '@/modules/travel/apis/travel.js'
import '../config/theme.css'

const route = useRoute()
const navOpen = ref(false)
const exporting = ref(false)
const currentSection = computed(() => route.path.includes('trainTicket') ? '铁路行程' : route.path.includes('flightTicket') ? '航空行程' : route.path.includes('/album') ? '城市图册' : route.path.includes('/statistics') ? '旅行统计' : '旅行足迹')
watch(() => route.path, () => { navOpen.value = false })

async function exportAll() {
  exporting.value = true
  try {
    const response = await exportTravelData()
    const blob = response.data
    if (!(blob instanceof Blob) || blob.size === 0) throw new Error('导出文件为空')
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `travel-data-${new Date().toISOString().slice(0, 10)}.zip`
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    ElMessage.success('旅行数据已导出')
  } catch { ElMessage.error('导出失败') }
  finally { exporting.value = false }
}
</script>

<style scoped>
.travel-admin { --ink:var(--travel-ink); --muted:var(--travel-muted); --line:var(--travel-line); --paper:var(--travel-surface); --accent:var(--travel-accent); --accent-strong:var(--travel-accent-hover); --accent-soft:var(--travel-accent-soft); --danger:var(--travel-danger); --el-color-primary:var(--travel-accent); --el-color-primary-dark-2:var(--travel-accent-active); --el-color-danger:var(--travel-danger); min-height:100vh; background:var(--travel-canvas); color:var(--ink); }
.admin-nav { position:fixed; z-index:30; inset:0 auto 0 0; display:flex; width:220px; flex-direction:column; box-sizing:border-box; padding:20px 12px; border-right:1px solid var(--line); background:#fff; color:var(--ink); }
.brand-block { display:flex; align-items:center; gap:11px; padding:6px 10px 28px; }.brand-mark { display:grid; width:36px; height:36px; flex:0 0 auto; place-items:center; border-radius:7px; background:#258b88; color:#fff; font-size:17px; }
.brand-block strong,.brand-block span { display:block; letter-spacing:0; }.brand-block strong { font-size:15px; }.brand-block span { margin-top:3px; color:#8b999f; font-size:9px; letter-spacing:.8px; }.nav-close { display:none; margin-left:auto; border:0; background:transparent; color:#71818b; font-size:17px; }
nav p { margin:0 10px 8px; color:#9aa6aa; font-size:10px; font-weight:650; } nav a { display:flex; align-items:center; gap:10px; min-height:43px; margin:3px 0; padding:0 11px; border-radius:6px; color:#667780; font-size:13px; text-decoration:none; transition:background-color .16s,color .16s; }
nav a:hover { background:#f3f7f7; color:#287d7a; } nav a.router-link-active { background:#e9f4f2; color:#197d79; font-weight:620; } nav a>.el-icon { flex:0 0 auto; font-size:16px; }
nav a span,nav a small { display:block; } nav a span { font-size:13px; font-weight:inherit; } nav a small { margin-top:3px; color:#98a4a9; font-size:10px; font-weight:400; }
.nav-footer { margin-top:auto; }.export-button { display:flex; width:100%; height:40px; align-items:center; justify-content:center; gap:8px; padding:0 12px; border:1px solid #d7e4e5; border-radius:6px; background:#fff; color:#53676f; font-size:13px; cursor:pointer; }.export-button:hover { border-color:#a9cecc; background:#f2f8f7; color:#258b88; }.export-button:disabled { opacity:.55; }
.nav-links { display:flex; padding:14px 10px 0; }.nav-links a { display:flex; align-items:center; gap:8px; color:#71818b; font-size:12px; text-decoration:none; }.nav-links a:hover { color:#258b88; }
.admin-content { min-height:100vh; margin-left:220px; box-sizing:border-box; padding:26px clamp(20px,3vw,44px); }.content-frame { width:min(1400px,100%); margin:0 auto; }.mobile-header,.nav-backdrop { display:none; }
.content-frame :deep(.page-title),.content-frame :deep(h1) { position:relative; margin:0 0 20px; padding:0 0 14px 15px; border-bottom:1px solid var(--line); color:var(--ink); font-size:24px; font-weight:720; line-height:1.25; }
.content-frame :deep(.page-title::before),.content-frame :deep(h1::before) { position:absolute; top:1px; bottom:15px; left:0; width:4px; border-radius:4px; background:var(--accent); content:""; }
.content-frame :deep(.page-header) { align-items:center; margin-bottom:18px; }
.content-frame :deep(.page-header p) { color:var(--muted); font-size:13px; line-height:1.5; }
.content-frame :deep(.query-card),.content-frame :deep(.toolbar) { margin-bottom:16px; border:1px solid var(--line); border-radius:7px; background:var(--paper); box-shadow:0 2px 8px rgba(29,48,53,.035); }
.content-frame :deep(.query-card .el-card__body) { padding:16px 18px 4px; }
.content-frame :deep(.el-card__body) { padding:18px; }
.content-frame :deep(.tool-bar),.content-frame :deep(.header-actions),.content-frame :deep(.bulk-toolbar),.content-frame :deep(.bulk-actions) { display:flex; align-items:center; flex-wrap:wrap; gap:8px; }
.content-frame :deep(.tool-bar) { margin:0 0 16px; padding:11px 12px; border:1px solid var(--line); border-radius:7px; background:#fff; box-shadow:0 2px 8px rgba(29,48,53,.035); }
.content-frame :deep(.bulk-toolbar) { min-height:44px; margin:0 0 10px; padding:8px 12px; border:1px solid var(--line); border-radius:6px; background:#fff; }
.content-frame :deep(.bulk-actions) { margin-left:auto; }
.content-frame :deep(.el-button) { --el-button-border-radius:5px; --el-button-disabled-text-color:#a0adaf; --el-button-disabled-bg-color:#f0f3f3; --el-button-disabled-border-color:#e0e6e6; min-height:34px; border-radius:5px; font-weight:550; transition:background-color .16s,border-color .16s,color .16s; }
.content-frame :deep(.el-button--default) { --el-button-bg-color:#fff; --el-button-border-color:#cfdcde; --el-button-text-color:#344b50; --el-button-hover-bg-color:#f2f7f7; --el-button-hover-border-color:#9fb9bc; --el-button-hover-text-color:#203b40; --el-button-active-bg-color:#e8f1f1; --el-button-active-border-color:#8aa9ad; --el-button-active-text-color:#203b40; }
.content-frame :deep(.el-button--primary) { --el-button-bg-color:var(--accent); --el-button-border-color:var(--accent); --el-button-text-color:#fff; --el-button-hover-bg-color:var(--accent-strong); --el-button-hover-border-color:var(--accent-strong); --el-button-hover-text-color:#fff; --el-button-active-bg-color:#055c65; --el-button-active-border-color:#055c65; --el-button-active-text-color:#fff; --el-button-disabled-bg-color:#b8d4d5; --el-button-disabled-border-color:#b8d4d5; --el-button-disabled-text-color:#fff; }
.content-frame :deep(.el-button--danger) { --el-button-bg-color:#fff; --el-button-border-color:#e3c5c6; --el-button-text-color:var(--danger); --el-button-hover-bg-color:#fff2f2; --el-button-hover-border-color:#d5a2a4; --el-button-hover-text-color:#963c40; --el-button-active-bg-color:#fbe7e8; --el-button-active-border-color:#c98589; --el-button-active-text-color:#88363a; --el-button-disabled-bg-color:#f7f1f1; --el-button-disabled-border-color:#eadede; --el-button-disabled-text-color:#c2a6a7; }
.content-frame :deep(.el-button--danger:not(.is-plain):not(.is-link)) { --el-button-bg-color:var(--danger); --el-button-border-color:var(--danger); --el-button-text-color:#fff; --el-button-hover-bg-color:#963c40; --el-button-hover-border-color:#963c40; --el-button-hover-text-color:#fff; --el-button-active-bg-color:#85363a; --el-button-active-border-color:#85363a; }
.content-frame :deep(.el-button.is-link) { min-height:28px; border-color:transparent; background:transparent; box-shadow:none; }
.content-frame :deep(.el-button--primary.is-link),.content-frame :deep(.el-button--info.is-link) { --el-button-text-color:var(--accent); --el-button-hover-text-color:var(--accent-strong); --el-button-hover-link-text-color:var(--accent-strong); --el-button-hover-bg-color:var(--accent-soft); --el-button-hover-border-color:transparent; }
.content-frame :deep(.el-button--danger.is-link) { --el-button-text-color:var(--danger); --el-button-hover-text-color:#963c40; --el-button-hover-link-text-color:#963c40; --el-button-hover-bg-color:#fff2f2; --el-button-hover-border-color:transparent; }
.content-frame :deep(.el-radio-button__inner) { border-color:#cfdcde; color:#42585d; box-shadow:none; }
.content-frame :deep(.el-radio-button__inner:hover) { color:var(--accent); }
.content-frame :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { border-color:var(--accent); background:var(--accent); color:#fff; box-shadow:-1px 0 0 0 var(--accent); }
.content-frame :deep(.el-radio-button__original-radio:focus-visible + .el-radio-button__inner) { outline:2px solid #80bdc0; outline-offset:2px; }
.content-frame :deep(.el-input__wrapper),.content-frame :deep(.el-select__wrapper),.content-frame :deep(.el-textarea__inner) { min-height:34px; border-radius:5px; box-shadow:0 0 0 1px #d5e0e1 inset; }
.content-frame :deep(.el-input__wrapper:hover),.content-frame :deep(.el-select__wrapper:hover),.content-frame :deep(.el-textarea__inner:hover) { box-shadow:0 0 0 1px #a8c4c6 inset; }
.content-frame :deep(.el-input__wrapper.is-focus),.content-frame :deep(.el-select__wrapper.is-focused),.content-frame :deep(.el-textarea__inner:focus) { box-shadow:0 0 0 1px var(--accent) inset; }
.content-frame :deep(.el-table) { --el-table-border-color:#e2e9e9; --el-table-header-bg-color:#f3f7f7; --el-table-row-hover-bg-color:#f0f8f7; --el-table-current-row-bg-color:#eaf5f4; overflow:hidden; border:1px solid var(--line); border-radius:7px; background:#fff; }
.content-frame :deep(.el-table th.el-table__cell) { height:46px; color:#52656b; font-size:12px; font-weight:650; }
.content-frame :deep(.el-table td.el-table__cell) { height:48px; color:#2b3d42; }
.content-frame :deep(.el-table .el-table__inner-wrapper::before) { display:none; }
.content-frame :deep(.el-tag--success) { --el-tag-bg-color:#eaf5f1; --el-tag-border-color:#c7e4d9; --el-tag-text-color:#25745c; }
.content-frame :deep(.el-pagination) { --el-pagination-button-bg-color:#fff; --el-pagination-hover-color:var(--accent); color:var(--muted); }
.content-frame :deep(.el-pagination button),.content-frame :deep(.el-pager li) { border:1px solid var(--line); border-radius:5px; }
.content-frame :deep(.el-pager li.is-active) { border-color:var(--accent); background:var(--accent); color:#fff; }
.content-frame :deep(.el-dialog) { overflow:hidden; border:1px solid var(--line); border-radius:8px; box-shadow:0 18px 55px rgba(21,43,48,.18); }
.content-frame :deep(.el-dialog__header) { margin:0; padding:18px 22px 14px; border-bottom:1px solid #e7eded; background:#fbfdfd; }
.content-frame :deep(.el-dialog__title) { color:var(--ink); font-size:17px; font-weight:680; }
.content-frame :deep(.el-dialog__body) { padding:20px 22px; }
.content-frame :deep(.el-dialog__footer) { padding:12px 22px 18px; border-top:1px solid #edf1f1; }
.content-frame :deep(.train-card > .el-card),.content-frame :deep(.flight-card > .el-card) { overflow:hidden; border:1px solid var(--line); border-radius:7px; box-shadow:0 2px 8px rgba(29,48,53,.035); }
.content-frame :deep(.train-card .card-header),.content-frame :deep(.flight-card .card-header) { min-height:38px; }
.content-frame :deep(.train-card.editing),.content-frame :deep(.flight-card.editing) { border-color:var(--accent); }
@media (max-width:760px) {
  .mobile-header { position:fixed; z-index:20; inset:0 0 auto; display:grid; height:58px; grid-template-columns:42px 1fr 42px; align-items:center; padding:0 12px; border-bottom:1px solid #dbe5e6; background:rgba(250,252,252,.94); backdrop-filter:blur(14px); }.mobile-header div strong,.mobile-header div span { display:block; }.mobile-header div strong { font-size:14px; }.mobile-header div span { margin-top:1px; color:#7a8d92; font-size:10px; }.mobile-header>a { display:grid; width:36px; height:36px; place-items:center; justify-self:end; border-radius:5px; color:#087f8c; font-size:19px; text-decoration:none; }.mobile-header>a:hover { background:#e9f5f4; }
  .travel-admin .mobile-header .menu-toggle { display:grid; width:36px; height:36px; place-items:center; padding:0; border:0; border-radius:5px; background:transparent; color:#53676f!important; cursor:pointer; }.travel-admin .mobile-header .menu-toggle:hover { background:#f0f5f5; color:#258b88!important; }.travel-admin .mobile-header .menu-toggle .el-icon { color:inherit; font-size:18px; }.admin-nav { width:min(286px,86vw); transform:translateX(-105%); transition:transform .22s ease; box-shadow:12px 0 32px rgba(31,48,57,.12); }.admin-nav.open { transform:translateX(0); }.nav-close { display:grid; width:32px; height:32px; place-items:center; border-radius:5px; }.nav-close:hover { background:#f0f5f5; color:#258b88; }.nav-backdrop { position:fixed; z-index:25; inset:0; display:block; background:rgba(20,35,41,.45); backdrop-filter:blur(2px); }
  .admin-content { margin-left:0; padding:82px 12px 28px; }.content-frame :deep(.page-title),.content-frame :deep(h1) { margin-bottom:15px; font-size:21px; }.content-frame :deep(.page-header) { align-items:stretch; gap:10px; }.content-frame :deep(.header-actions) { justify-content:flex-start; }.content-frame :deep(.query-card .el-form) { display:grid; grid-template-columns:1fr; }.content-frame :deep(.query-card .el-form-item) { width:100%; margin-right:0; }.content-frame :deep(.query-card .el-input),.content-frame :deep(.query-card .el-select),.content-frame :deep(.query-card .el-date-editor) { width:100%!important; }
  .content-frame :deep(.bulk-toolbar),.content-frame :deep(.bulk-actions) { align-items:stretch; }.content-frame :deep(.bulk-actions) { margin-left:0; }.content-frame :deep(.el-dialog) { width:calc(100vw - 20px)!important; margin:10px auto; }.content-frame :deep(.el-dialog__body) { max-height:calc(100vh - 145px); overflow-y:auto; padding:16px; }.content-frame :deep(.el-dialog__header),.content-frame :deep(.el-dialog__footer) { padding-right:16px; padding-left:16px; }
}
</style>
