<template>
  <main class="diary-overview">
    <header class="overview-heading">
      <div><span class="overview-kicker">LIFE JOURNAL</span><h1>日记主页</h1><p>{{ todayLabel }}，给今天留一点位置。</p></div>
      <el-button type="primary" :icon="Plus" @click="writeToday">写日记</el-button>
    </header>

    <section class="welcome-panel">
      <div class="welcome-copy">
        <span>生活不是待办清单</span>
        <h2>今天也值得<br>被认真记下来。</h2>
        <p>一段文字、一张照片，都是以后回看的路标。</p>
        <el-button text @click="router.push('/diary/feed')">翻看日记动态 <el-icon><ArrowRight /></el-icon></el-button>
      </div>
      <div class="welcome-art" aria-hidden="true">
        <div class="paper-sheet"><span class="paper-date">{{ monthDay }}</span><i></i><i></i><i></i><b>✳</b></div>
        <span class="art-orbit orbit-one"></span><span class="art-orbit orbit-two"></span>
      </div>
    </section>

    <section class="overview-stats" aria-label="日记统计">
      <article class="stat-card total-stat"><span class="stat-icon"><el-icon><Notebook /></el-icon></span><div><small>累计日记</small><strong>{{ allSummary.entries || 0 }} <em>篇</em></strong></div><span class="stat-caption">所有记录</span></article>
      <article class="stat-card"><span class="stat-icon year-icon"><el-icon><Calendar /></el-icon></span><div><small>{{ currentYear }} 年</small><strong>{{ yearSummary.entries || 0 }} <em>篇</em></strong></div><span class="stat-caption">今年写下</span></article>
      <article class="stat-card"><span class="stat-icon days-icon"><el-icon><Edit /></el-icon></span><div><small>记录日</small><strong>{{ yearSummary.daysWritten || 0 }} <em>天</em></strong></div><span class="stat-caption">今年留下</span></article>
      <article class="stat-card"><span class="stat-icon photos-icon"><el-icon><Picture /></el-icon></span><div><small>本月新增</small><strong>{{ allSummary.thisMonth || 0 }} <em>篇</em></strong></div><span class="stat-caption">持续记录</span></article>
    </section>

    <section class="recent-section">
      <header class="section-heading">
        <div><h2>最近的日子</h2><p>刚刚写下的生活片段</p></div>
        <RouterLink to="/diary/manage">管理全部 <el-icon><ArrowRight /></el-icon></RouterLink>
      </header>
      <div v-if="recent.length" class="recent-list">
        <article v-for="entry in recent" :key="entry.id" class="recent-entry">
          <div class="recent-date"><strong>{{ dayOf(entry.entryDate) }}</strong><span>{{ monthOf(entry.entryDate) }}</span></div>
          <div class="recent-copy"><h3>{{ entry.title }}</h3><p>{{ entry.content || '照片与心情，也是一种记录。' }}</p><div class="recent-meta"><span>{{ formatDate(entry.entryDate) }}</span><span v-if="entry.mood">{{ entry.mood }}</span><span v-if="weatherOption(entry.weatherCode)" class="weather-meta"><img :src="weatherIconUrl(entry.weatherCode)" alt="" />{{ weatherOption(entry.weatherCode).name }}</span><span v-if="entry.location">{{ entry.location }}</span></div></div>
          <el-image v-if="entry.mediaIds?.length" class="recent-photo" :src="diaryMediaUrl(entry.mediaIds[0])" :preview-src-list="entry.mediaIds.map(diaryMediaUrl)" fit="cover" />
        </article>
      </div>
      <el-empty v-else-if="!loading" :image-size="76" description="还没有日记，写下第一篇生活记录吧"><el-button type="primary" :icon="Plus" @click="writeToday">开始记录</el-button></el-empty>
      <div v-if="loading" class="loading-line"><el-icon class="is-loading"><Loading /></el-icon>正在整理最近的记录</div>
    </section>

    <section class="shortcut-row">
      <RouterLink to="/diary/feed" class="shortcut-card"><span class="shortcut-icon feed-shortcut"><el-icon><ChatLineSquare /></el-icon></span><span><strong>日记动态</strong><small>瀑布流浏览图文记录</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
      <RouterLink to="/diary/statistics" class="shortcut-card"><span class="shortcut-icon statistics-shortcut"><el-icon><DataAnalysis /></el-icon></span><span><strong>时光统计</strong><small>看看日子如何慢慢积累</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
      <RouterLink to="/diary/manage" class="shortcut-card"><span class="shortcut-icon manage-shortcut"><el-icon><Memo /></el-icon></span><span><strong>日记管理</strong><small>检索、编辑与整理内容</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Calendar, ChatLineSquare, DataAnalysis, Edit, Loading, Memo, Notebook, Picture, Plus } from '@element-plus/icons-vue'
import { diaryMediaUrl, getDiaryEntries, getDiarySummary } from '../apis/diary.js'
import { weatherIconUrl, weatherOption } from '../utils/weather.js'

const router = useRouter()
const currentYear = new Date().getFullYear()
const recent = ref([])
const allSummary = ref({ entries: 0, thisMonth: 0 })
const yearSummary = ref({ entries: 0, daysWritten: 0 })
const loading = ref(true)
const todayDate = new Date()
const todayLabel = new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(todayDate)
const monthDay = `${String(todayDate.getMonth() + 1).padStart(2, '0')} / ${String(todayDate.getDate()).padStart(2, '0')}`

function writeToday() { router.push({ path: '/diary/write', query: { from: 'home' } }) }
function dateValue(value) {
  if (!value) return null
  const [year, month, day] = String(value).slice(0, 10).split('-').map(Number)
  return new Date(year, month - 1, day)
}
function dayOf(value) { return String(value || '').slice(8, 10) }
function monthOf(value) { return `${String(value || '').slice(5, 7)} 月` }
function formatDate(value) { const date = dateValue(value); return date ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' }).format(date) : '' }

onMounted(async () => {
  try {
    const [all, year, entries] = await Promise.all([
      getDiarySummary(), getDiarySummary(currentYear), getDiaryEntries({ page: 1, size: 5 }),
    ])
    allSummary.value = all
    yearSummary.value = year
    recent.value = entries.items || []
  } catch { /* the API interceptor reports connection failures */ }
  finally { loading.value = false }
})
</script>

<style scoped>
.diary-overview { --ink:#293b49; --muted:#7c8b95; --line:#e3e9ec; --accent:#258b88; color:var(--ink); }
.overview-heading { display:flex; align-items:center; justify-content:space-between; gap:18px; margin-bottom:18px; }.overview-heading h1 { margin:4px 0; font-size:25px; font-weight:700; }.overview-heading p { margin:0; color:var(--muted); font-size:12px; }.overview-kicker { color:#89999e; font-size:9px; font-weight:700; letter-spacing:1px; }.overview-heading :deep(.el-button) { min-height:37px; padding:0 16px; border-radius:5px; background:var(--accent); border-color:var(--accent); }
.welcome-panel { position:relative; display:flex; min-height:224px; overflow:hidden; align-items:center; justify-content:space-between; padding:25px clamp(24px,4vw,50px); border:1px solid #dce9e7; border-radius:7px; background:#e9f3f1; }.welcome-copy { position:relative; z-index:1; }.welcome-copy>span { color:#6f928d; font-size:11px; font-weight:600; }.welcome-copy h2 { margin:9px 0 7px; color:#24474a; font-size:25px; font-weight:700; line-height:1.45; }.welcome-copy p { margin:0 0 8px; color:#71898a; font-size:12px; }.welcome-copy :deep(.el-button) { margin-left:-9px; color:#217e7a; font-size:12px; }
.welcome-art { position:relative; display:grid; width:210px; height:166px; flex:0 0 auto; place-items:center; }.paper-sheet { position:relative; z-index:1; display:flex; width:120px; height:144px; flex-direction:column; gap:12px; padding:18px 16px; transform:rotate(5deg); border:1px solid #d8e5e2; border-radius:5px; background:#fff; box-shadow:0 10px 24px rgba(38,75,74,.09); }.paper-date { color:#258b88; font-family:Georgia,serif; font-size:14px; }.paper-sheet i { height:1px; background:#dfe9e7; }.paper-sheet i:nth-of-type(2) { width:72%; }.paper-sheet b { position:absolute; right:13px; bottom:10px; color:#d27d61; font-size:21px; font-weight:400; }.art-orbit { position:absolute; display:block; border:1px solid rgba(37,139,136,.14); border-radius:50%; }.orbit-one { width:154px; height:154px; }.orbit-two { width:194px; height:194px; border-color:rgba(37,139,136,.08); }
.overview-stats { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:12px; margin-top:14px; }.stat-card { position:relative; display:flex; min-width:0; min-height:91px; align-items:center; gap:12px; padding:14px; border:1px solid var(--line); border-radius:6px; background:#fff; }.stat-icon { display:grid; width:36px; height:36px; flex:0 0 auto; place-items:center; border-radius:6px; background:#e9f4f2; color:#258b88; font-size:16px; }.year-icon { background:#edf1f8; color:#687eae; }.days-icon { background:#f7eee7; color:#bc7854; }.photos-icon { background:#f1f4e9; color:#778c53; }.stat-card small { display:block; color:#8b999e; font-size:10px; }.stat-card strong { display:block; margin-top:4px; color:#30474d; font-size:20px; }.stat-card em { color:#829096; font-size:10px; font-style:normal; font-weight:500; }.stat-caption { position:absolute; right:11px; bottom:10px; color:#a3adaf; font-size:9px; }
.recent-section { margin-top:25px; }.section-heading { display:flex; align-items:end; justify-content:space-between; gap:12px; margin-bottom:10px; }.section-heading h2 { margin:0; color:#30474d; font-size:16px; font-weight:680; }.section-heading p { margin:4px 0 0; color:#8a989d; font-size:10px; }.section-heading>a { display:flex; align-items:center; gap:4px; color:#258b88; font-size:11px; text-decoration:none; }
.recent-list { overflow:hidden; border:1px solid var(--line); border-radius:7px; background:#fff; }.recent-entry { display:flex; min-height:82px; align-items:center; gap:15px; padding:11px 15px; }.recent-entry + .recent-entry { border-top:1px solid #edf1f2; }.recent-date { display:flex; width:43px; flex:0 0 auto; flex-direction:column; align-items:center; color:#829096; }.recent-date strong { color:#258b88; font-size:20px; }.recent-date span { font-size:9px; }.recent-copy { min-width:0; flex:1; }.recent-copy h3 { overflow:hidden; margin:0; color:#354d53; font-size:12px; font-weight:650; text-overflow:ellipsis; white-space:nowrap; }.recent-copy p { display:-webkit-box; overflow:hidden; margin:5px 0; color:#829096; font-size:10px; line-height:1.5; -webkit-box-orient:vertical; -webkit-line-clamp:1; }.recent-meta { display:flex; flex-wrap:wrap; gap:9px; color:#9ba7aa; font-size:9px; }.recent-photo { width:66px; height:56px; flex:0 0 auto; border-radius:4px; background:#f1f4f4; }.loading-line { display:flex; align-items:center; justify-content:center; gap:8px; min-height:80px; color:#8b999e; font-size:11px; }
.shortcut-row { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; margin-top:15px; }.shortcut-card { display:flex; min-width:0; align-items:center; gap:11px; padding:12px; border:1px solid var(--line); border-radius:6px; background:#fff; color:inherit; text-decoration:none; }.shortcut-card:hover { border-color:#bad8d5; background:#fbfdfd; }.shortcut-icon { display:grid; width:34px; height:34px; flex:0 0 auto; place-items:center; border-radius:6px; font-size:15px; }.feed-shortcut { background:#e9f4f2; color:#258b88; }.manage-shortcut { background:#f7eee7; color:#bc7854; }.shortcut-card>span:nth-child(2) { min-width:0; flex:1; }.shortcut-card strong,.shortcut-card small { display:block; }.shortcut-card strong { color:#40565c; font-size:11px; }.shortcut-card small { overflow:hidden; margin-top:3px; color:#8e9b9f; font-size:9px; text-overflow:ellipsis; white-space:nowrap; }.shortcut-arrow { color:#9aa7ab; font-size:12px; }
.weather-meta { display:inline-flex; align-items:center; gap:3px; }.weather-meta img { width:18px; height:18px; object-fit:contain; }
@media(max-width:900px) { .overview-stats { grid-template-columns:repeat(2,minmax(0,1fr)); }.welcome-art { width:160px; } }
@media(max-width:560px) { .overview-heading h1 { font-size:22px; }.welcome-panel { min-height:185px; padding:20px 17px; }.welcome-copy h2 { font-size:21px; }.welcome-copy p { max-width:230px; font-size:10px; }.welcome-art { position:absolute; right:-37px; width:150px; opacity:.58; }.paper-sheet { width:100px; height:125px; }.overview-stats { gap:8px; }.stat-card { min-height:78px; gap:8px; padding:10px; }.stat-icon { width:31px; height:31px; }.stat-card strong { font-size:17px; }.stat-caption { display:none; }.recent-entry { gap:9px; padding:10px; }.recent-date { width:36px; }.recent-photo { width:50px; height:47px; }.shortcut-row { gap:8px; }.shortcut-card { gap:7px; padding:9px; }.shortcut-icon { width:30px; height:30px; } }
</style>
