<template>
  <div class="diary-layout">
    <aside class="diary-sidebar">
      <RouterLink class="diary-brand" to="/diary/home">
        <span class="brand-mark"><el-icon><Notebook /></el-icon></span>
        <span><strong>生活日记</strong><small>LIFE JOURNAL</small></span>
      </RouterLink>
      <div class="nav-caption">日记空间</div>
      <nav class="diary-nav" aria-label="日记导航">
        <RouterLink to="/diary/home" class="diary-nav-link" active-class="is-active"><el-icon><House /></el-icon><span>主页</span></RouterLink>
        <RouterLink to="/diary/feed" class="diary-nav-link" active-class="is-active"><el-icon><ChatLineSquare /></el-icon><span>日记动态</span></RouterLink>
        <RouterLink to="/diary/manage" class="diary-nav-link" active-class="is-active"><el-icon><Memo /></el-icon><span>日记管理</span></RouterLink>
      </nav>
      <div class="sidebar-bottom">
        <div class="sidebar-note"><span class="note-mark">L</span><span>把日子写下来<br><small>让平凡的瞬间有迹可循</small></span></div>
        <RouterLink to="/home" class="back-link"><el-icon><HomeFilled /></el-icon>返回系统主页</RouterLink>
      </div>
    </aside>
    <header class="mobile-diary-header">
      <RouterLink to="/diary/home"><span class="brand-mark"><el-icon><Notebook /></el-icon></span><strong>生活日记</strong></RouterLink>
      <nav aria-label="日记导航"><RouterLink to="/diary/home" active-class="is-active">主页</RouterLink><RouterLink to="/diary/feed" active-class="is-active">动态</RouterLink><RouterLink to="/diary/manage" active-class="is-active">管理</RouterLink></nav>
    </header>
    <main class="diary-main">
      <router-view v-slot="{ Component, route }"><Transition name="diary-page" mode="out-in" appear><component :is="Component" :key="route.path" /></Transition></router-view>
    </main>
  </div>
</template>

<script setup>
import { ChatLineSquare, HomeFilled, House, Memo, Notebook } from '@element-plus/icons-vue'
</script>

<style>
.diary-layout { --diary-ink:#293b49; --diary-muted:#7c8b95; --diary-line:#e3e9ec; --diary-accent:#258b88; --diary-canvas:#f3f5f8; display:flex; width:100%; min-height:100vh; background:var(--diary-canvas); color:var(--diary-ink); }
.diary-sidebar { position:sticky; top:0; display:flex; width:220px; height:100vh; flex:0 0 220px; flex-direction:column; padding:20px 12px; border-right:1px solid var(--diary-line); background:#fff; }
.diary-brand { display:flex; align-items:center; gap:11px; padding:6px 10px 28px; color:inherit; text-decoration:none; }.diary-brand > span:last-child { min-width:0; }.diary-brand strong,.diary-brand small { display:block; }.diary-brand strong { font-size:15px; }.diary-brand small { margin-top:4px; color:#98a4a9; font-size:9px; letter-spacing:1px; }
.diary-brand .brand-mark,.mobile-diary-header .brand-mark { display:grid; width:36px; height:36px; flex:0 0 auto; place-items:center; border-radius:8px; background:#258b88; color:#fff; font-size:17px; }
.nav-caption { padding:0 10px 8px; color:#9aa6aa; font-size:10px; font-weight:650; }.diary-nav { display:grid; gap:4px; }
.diary-nav-link { display:flex; min-height:43px; align-items:center; gap:10px; padding:0 11px; border-radius:6px; color:#667780; font-size:13px; text-decoration:none; transition:background-color .16s,color .16s; }.diary-nav-link .el-icon { font-size:16px; }.diary-nav-link:hover { background:#f3f7f7; color:#287d7a; }.diary-nav-link.is-active { background:#e9f4f2; color:#197d79; font-weight:620; }
.sidebar-bottom { margin-top:auto; }.sidebar-note { display:flex; align-items:center; gap:10px; padding:12px 10px 18px; color:#72838a; font-size:11px; line-height:1.65; }.note-mark { display:grid; width:31px; height:31px; flex:0 0 auto; place-items:center; border:1px solid #d6e8e5; border-radius:7px; color:#258b88; font-family:Georgia,serif; font-size:17px; }.sidebar-note small { color:#9aa6aa; font-size:9px; }
.back-link { display:flex; align-items:center; gap:8px; padding:13px 10px; border-top:1px solid #edf0f2; color:#71818b; font-size:12px; text-decoration:none; }.back-link:hover { color:#258b88; }.diary-main { min-width:0; flex:1; padding:26px clamp(20px,3vw,44px); }.mobile-diary-header { display:none; }
.diary-page-enter-active,.diary-page-leave-active { transition:opacity .18s ease,transform .18s ease; }.diary-page-enter-from { opacity:0; transform:translateY(6px); }.diary-page-leave-to { opacity:0; transform:translateY(-3px); }
@media(max-width:700px) {
  .diary-layout { display:block; }.diary-sidebar { display:none; }.mobile-diary-header { position:sticky; z-index:10; top:0; display:flex; min-height:58px; align-items:center; justify-content:space-between; gap:12px; padding:7px 14px; border-bottom:1px solid var(--diary-line); background:rgba(255,255,255,.95); backdrop-filter:blur(12px); }.mobile-diary-header>a { display:flex; align-items:center; gap:8px; color:var(--diary-ink); font-size:13px; text-decoration:none; }.mobile-diary-header .brand-mark { width:31px; height:31px; font-size:15px; }.mobile-diary-header nav { display:flex; gap:3px; }.mobile-diary-header nav a { padding:7px 8px; border-radius:5px; color:#75858b; font-size:11px; text-decoration:none; }.mobile-diary-header nav a.is-active { background:#e9f4f2; color:#197d79; font-weight:600; }.diary-main { padding:20px 14px 30px; }
}
@media(prefers-reduced-motion:reduce) { .diary-page-enter-active,.diary-page-leave-active { transition:none; } }
</style>
