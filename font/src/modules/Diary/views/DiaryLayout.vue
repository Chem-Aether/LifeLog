<template>
  <div class="diary-layout">
    <header class="mobile-diary-header">
      <button class="menu-toggle" :aria-label="navOpen ? '关闭导航' : '打开导航'" @click="navOpen = !navOpen"><el-icon><Close v-if="navOpen" /><Menu v-else /></el-icon></button>
      <div class="mobile-title"><span class="brand-mark"><el-icon><Notebook /></el-icon></span><span><strong>生活日记</strong><small>{{ currentSection }}</small></span></div>
      <RouterLink to="/home" aria-label="返回系统主页"><el-icon><HomeFilled /></el-icon></RouterLink>
    </header>
    <button v-if="navOpen" class="nav-backdrop" aria-label="关闭导航" @click="navOpen = false"></button>
    <aside class="diary-sidebar" :class="{ open: navOpen }">
      <RouterLink class="diary-brand" to="/diary/home">
        <span class="brand-mark"><el-icon><Notebook /></el-icon></span>
        <span><strong>生活日记</strong><small>LIFE JOURNAL</small></span>
        <button class="nav-close" aria-label="关闭导航" @click="navOpen = false"><el-icon><Close /></el-icon></button>
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
    <main class="diary-main">
      <router-view v-slot="{ Component, route }"><Transition name="diary-page" mode="out-in" appear><component :is="Component" :key="route.path" /></Transition></router-view>
    </main>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChatLineSquare, Close, HomeFilled, House, Memo, Menu, Notebook } from '@element-plus/icons-vue'

const route = useRoute()
const navOpen = ref(false)
const currentSection = computed(() => route.path.endsWith('/feed') ? '日记动态' : route.path.endsWith('/manage') ? '日记管理' : '主页')
watch(() => route.path, () => { navOpen.value = false })
</script>

<style>
.diary-layout { --diary-ink:#293b49; --diary-muted:#7c8b95; --diary-line:#e3e9ec; --diary-accent:#258b88; --diary-canvas:#f3f5f8; display:flex; width:100%; min-height:100vh; background:var(--diary-canvas); color:var(--diary-ink); }
.diary-sidebar { position:sticky; top:0; display:flex; width:220px; height:100vh; flex:0 0 220px; flex-direction:column; padding:20px 12px; border-right:1px solid var(--diary-line); background:#fff; }
.diary-brand { display:flex; align-items:center; gap:11px; padding:6px 10px 28px; color:inherit; text-decoration:none; }.diary-brand > span:last-child { min-width:0; }.diary-brand strong,.diary-brand small { display:block; }.diary-brand strong { font-size:15px; }.diary-brand small { margin-top:4px; color:#98a4a9; font-size:9px; letter-spacing:1px; }
.diary-brand .brand-mark,.mobile-diary-header .brand-mark { display:grid; width:36px; height:36px; flex:0 0 auto; place-items:center; border-radius:8px; background:#258b88; color:#fff; font-size:17px; }
.nav-caption { padding:0 10px 8px; color:#9aa6aa; font-size:10px; font-weight:650; }.diary-nav { display:grid; gap:4px; }
.diary-nav-link { display:flex; min-height:43px; align-items:center; gap:10px; padding:0 11px; border-radius:6px; color:#667780; font-size:13px; text-decoration:none; transition:background-color .16s,color .16s; }.diary-nav-link .el-icon { font-size:16px; }.diary-nav-link:hover { background:#f3f7f7; color:#287d7a; }.diary-nav-link.is-active { background:#e9f4f2; color:#197d79; font-weight:620; }
.sidebar-bottom { margin-top:auto; }.sidebar-note { display:flex; align-items:center; gap:10px; padding:12px 10px 18px; color:#72838a; font-size:11px; line-height:1.65; }.note-mark { display:grid; width:31px; height:31px; flex:0 0 auto; place-items:center; border:1px solid #d6e8e5; border-radius:7px; color:#258b88; font-family:Georgia,serif; font-size:17px; }.sidebar-note small { color:#9aa6aa; font-size:9px; }
.back-link { display:flex; align-items:center; gap:8px; padding:13px 10px; border-top:1px solid #edf0f2; color:#71818b; font-size:12px; text-decoration:none; }.back-link:hover { color:#258b88; }.diary-main { min-width:0; flex:1; padding:26px clamp(20px,3vw,44px); }.mobile-diary-header,.nav-backdrop { display:none; }.nav-close { display:none; }
.diary-page-enter-active,.diary-page-leave-active { transition:opacity .18s ease,transform .18s ease; }.diary-page-enter-from { opacity:0; transform:translateY(6px); }.diary-page-leave-to { opacity:0; transform:translateY(-3px); }
@media(max-width:760px) {
  .diary-layout { display:block; min-height:100dvh; }.diary-sidebar { position:fixed; z-index:30; inset:0 auto 0 0; display:flex; width:min(286px,86vw); height:100dvh; box-sizing:border-box; transform:translateX(-105%); transition:transform .22s ease; box-shadow:12px 0 32px rgba(31,48,57,.12); }.diary-sidebar.open { transform:translateX(0); }.diary-brand { padding-bottom:28px; }.diary-sidebar .nav-close { display:grid; width:32px; height:32px; margin-left:auto; place-items:center; border:0; border-radius:5px; background:transparent; color:#71818b; font-size:17px; }.mobile-diary-header { position:fixed; z-index:20; inset:0 0 auto; display:grid; height:58px; grid-template-columns:42px 1fr 42px; align-items:center; padding:0 12px; border-bottom:1px solid var(--diary-line); background:rgba(255,255,255,.96); backdrop-filter:blur(12px); }.mobile-diary-header button,.mobile-diary-header>a { display:grid; width:36px; height:36px; place-items:center; border:0; border-radius:5px; background:transparent; color:#53676f; font-size:18px; text-decoration:none; }.mobile-diary-header button:hover,.mobile-diary-header>a:hover { background:#f0f5f5; color:#258b88; }.mobile-title { display:flex; align-items:center; justify-content:center; gap:8px; }.mobile-title .brand-mark { width:31px; height:31px; font-size:15px; }.mobile-title strong,.mobile-title small { display:block; }.mobile-title strong { font-size:12px; }.mobile-title small { margin-top:2px; color:#89979d; font-size:10px; text-align:center; }.nav-backdrop { position:fixed; z-index:25; inset:0; display:block; border:0; background:rgba(20,35,41,.45); backdrop-filter:blur(2px); }.diary-main { padding:78px 14px 30px; }
}
@media(prefers-reduced-motion:reduce) { .diary-page-enter-active,.diary-page-leave-active { transition:none; } }
</style>
