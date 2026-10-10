<template>
  <div class="travel-admin">
    <header class="mobile-header">
      <button class="menu-toggle" aria-label="打开导航" @click="navOpen = true"><i></i><i></i><i></i></button>
      <div><strong>旅行管理</strong><span>{{ currentSection }}</span></div>
      <router-link to="/travel">地图</router-link>
    </header>
    <div v-if="navOpen" class="nav-backdrop" @click="navOpen = false"></div>
    <aside class="admin-nav" :class="{ open: navOpen }">
      <div class="brand-block">
        <div class="brand-mark">旅</div>
        <div><strong>TRAVEL LOG</strong><span>个人旅行档案</span></div>
        <button class="nav-close" aria-label="关闭导航" @click="navOpen = false">×</button>
      </div>
      <nav aria-label="旅行管理导航">
        <p>数据管理</p>
        <router-link to="/travel/admin/trainTicket"><b>铁</b><span>铁路行程<small>车票与途经站</small></span></router-link>
        <router-link to="/travel/admin/flightTicket"><b>航</b><span>航空行程<small>航班与里程记录</small></span></router-link>
        <router-link to="/travel/admin/footprint"><b>迹</b><span>旅行足迹<small>地点与探索记录</small></span></router-link>
      </nav>
      <div class="nav-footer">
        <button class="export-button" :disabled="exporting" @click="exportAll"><span>{{ exporting ? '正在生成...' : '导出全部数据' }}</span><b>↓</b></button>
        <div class="nav-links"><router-link to="/home">返回首页</router-link><router-link to="/travel">查看地图</router-link></div>
      </div>
    </aside>
    <main class="admin-content"><div class="content-frame"><router-view /></div></main>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { exportTravelData } from '@/modules/travel/apis/travel.js'

const route = useRoute()
const navOpen = ref(false)
const exporting = ref(false)
const currentSection = computed(() => route.path.includes('trainTicket') ? '铁路行程' : route.path.includes('flightTicket') ? '航空行程' : '旅行足迹')
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
.travel-admin { --ink:#1d3035; --muted:#65777c; --line:#d9e3e4; --paper:#fff; --accent:#087f8c; --accent-strong:#066d77; --accent-soft:#e9f5f4; --danger:#b5474a; --el-color-primary:#087f8c; --el-color-primary-light-3:#4fa5ab; --el-color-primary-light-5:#80bdc0; --el-color-primary-light-7:#b4d8d9; --el-color-primary-light-8:#d0e7e7; --el-color-primary-light-9:#e9f5f4; --el-color-primary-dark-2:#066d77; --el-color-danger:#b5474a; --el-color-danger-light-9:#fff2f2; min-height:100vh; background:#f0f4f4; color:var(--ink); }
.admin-nav { position:fixed; z-index:30; inset:0 auto 0 0; display:flex; width:250px; flex-direction:column; box-sizing:border-box; padding:24px 16px 18px; background:#142329; color:#eaf3f3; box-shadow:12px 0 32px rgba(20,35,41,.12); }
.brand-block { display:flex; align-items:center; gap:12px; padding:0 8px 28px; }.brand-mark { display:grid; width:40px; height:40px; flex:0 0 auto; place-items:center; border-radius:6px; background:#e2583e; color:#fff; font-weight:800; }
.brand-block strong,.brand-block span { display:block; letter-spacing:0; }.brand-block strong { font-size:14px; }.brand-block span { margin-top:3px; color:#89a1a7; font-size:11px; }.nav-close { display:none; margin-left:auto; border:0; background:transparent; color:#a9bdc1; font-size:25px; }
nav p { margin:0 10px 9px; color:#708a90; font-size:10px; font-weight:700; } nav a { display:flex; align-items:center; gap:12px; min-height:58px; margin-bottom:5px; padding:0 11px; border-radius:6px; color:#b8c9cc; text-decoration:none; transition:background-color .18s,color .18s; }
nav a:hover { background:rgba(255,255,255,.055); color:#fff; } nav a.router-link-active { background:#edf6f5; color:#18343a; } nav a>b { display:grid; width:29px; height:29px; flex:0 0 auto; place-items:center; border:1px solid currentColor; border-radius:50%; font-size:11px; }
nav a span,nav a small { display:block; } nav a span { font-size:13px; font-weight:650; } nav a small { margin-top:3px; color:#708a90; font-size:10px; font-weight:400; }
.nav-footer { margin-top:auto; }.export-button { display:flex; width:100%; height:42px; align-items:center; justify-content:space-between; padding:0 14px; border:1px solid #3b565c; border-radius:5px; background:transparent; color:#d7e5e7; cursor:pointer; }.export-button:hover { border-color:#68b9bd; background:rgba(104,185,189,.08); }.export-button:disabled { opacity:.55; }
.nav-links { display:flex; justify-content:space-between; padding:18px 4px 0; }.nav-links a { color:#789097; font-size:11px; text-decoration:none; }.nav-links a:hover { color:#fff; }
.admin-content { min-height:100vh; margin-left:250px; box-sizing:border-box; padding:34px clamp(24px,4vw,64px); }.content-frame { width:min(1400px,100%); margin:0 auto; }.mobile-header,.nav-backdrop { display:none; }
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
  .mobile-header { position:fixed; z-index:20; inset:0 0 auto; display:grid; height:58px; grid-template-columns:42px 1fr 42px; align-items:center; padding:0 12px; border-bottom:1px solid #dbe5e6; background:rgba(250,252,252,.94); backdrop-filter:blur(14px); }.mobile-header div strong,.mobile-header div span { display:block; }.mobile-header div strong { font-size:14px; }.mobile-header div span { margin-top:1px; color:#7a8d92; font-size:10px; }.mobile-header>a { color:#087f8c; font-size:12px; text-align:right; text-decoration:none; }
  .menu-toggle { display:flex; width:34px; height:34px; flex-direction:column; justify-content:center; gap:4px; padding:0 7px; border:0; background:transparent; }.menu-toggle i { height:2px; background:#263a40; }.admin-nav { width:min(286px,86vw); transform:translateX(-105%); transition:transform .22s ease; }.admin-nav.open { transform:translateX(0); }.nav-close { display:block; }.nav-backdrop { position:fixed; z-index:25; inset:0; display:block; background:rgba(7,17,22,.48); backdrop-filter:blur(2px); }
  .admin-content { margin-left:0; padding:82px 12px 28px; }.content-frame :deep(.page-title),.content-frame :deep(h1) { margin-bottom:15px; font-size:21px; }.content-frame :deep(.page-header) { align-items:stretch; gap:10px; }.content-frame :deep(.header-actions) { justify-content:flex-start; }.content-frame :deep(.query-card .el-form) { display:grid; grid-template-columns:1fr; }.content-frame :deep(.query-card .el-form-item) { width:100%; margin-right:0; }.content-frame :deep(.query-card .el-input),.content-frame :deep(.query-card .el-select),.content-frame :deep(.query-card .el-date-editor) { width:100%!important; }
  .content-frame :deep(.bulk-toolbar),.content-frame :deep(.bulk-actions) { align-items:stretch; }.content-frame :deep(.bulk-actions) { margin-left:0; }.content-frame :deep(.el-dialog) { width:calc(100vw - 20px)!important; margin:10px auto; }.content-frame :deep(.el-dialog__body) { max-height:calc(100vh - 145px); overflow-y:auto; padding:16px; }.content-frame :deep(.el-dialog__header),.content-frame :deep(.el-dialog__footer) { padding-right:16px; padding-left:16px; }
}
</style>
