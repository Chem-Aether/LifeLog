<template>
  <main class="statistics-page" v-loading="loading">
    <header class="page-heading">
      <div><span class="eyebrow">LIFE IN LITTLE MOMENTS</span><h1>时光统计</h1><p>把走过的日子摊开来看，每一笔都算数。</p></div>
      <div class="year-picker"><span>回看年份</span><el-select v-model="year" aria-label="选择统计年份" @change="loadStatistics"><el-option v-for="item in years" :key="item" :label="`${item} 年`" :value="item" /></el-select></div>
    </header>

    <section class="year-banner">
      <div class="banner-copy"><span>{{ year }} · A YEAR IN REVIEW</span><h2>这一年，<br class="mobile-break">你为生活留下了 {{ entries.length }} 篇注脚。</h2><p>落笔 {{ writtenDays }} 天，<template v-if="topMood">最常出现的心情是「{{ topMood }}」。</template><template v-else>那些日子，都有被认真收好。</template></p></div>
      <div class="banner-number"><strong>{{ writtenDays }}</strong><span>个有记录的日子</span></div>
      <i class="banner-mark" aria-hidden="true">✳</i>
    </section>

    <section class="metric-row" aria-label="年度概览">
      <article class="metric"><span class="metric-icon teal"><el-icon><Notebook /></el-icon></span><div><small>写下日记</small><strong>{{ entries.length }} <em>篇</em></strong></div></article>
      <article class="metric"><span class="metric-icon coral"><el-icon><Calendar /></el-icon></span><div><small>记录日</small><strong>{{ writtenDays }} <em>天</em></strong></div></article>
      <article class="metric"><span class="metric-icon gold"><el-icon><Picture /></el-icon></span><div><small>影像片段</small><strong>{{ mediaCount }} <em>个</em></strong></div></article>
      <article class="metric"><span class="metric-icon green"><el-icon><LocationInformation /></el-icon></span><div><small>记下地点</small><strong>{{ locationCount }} <em>处</em></strong></div></article>
    </section>

    <section class="panel calendar-panel">
      <header class="panel-heading"><div><h2>日子的浓淡</h2><p>每一格是一日，颜色越深，写下的篇数越多</p></div><span class="year-stamp">{{ year }}</span></header>
      <div ref="calendarEl" class="calendar-chart" aria-label="日记记录日历热力图" />
      <div class="calendar-legend"><span>留白</span><i v-for="tone in 5" :key="tone" :class="`tone-${tone}`"></i><span>写了几笔</span></div>
    </section>

    <section class="lower-grid">
      <article class="panel trend-panel"><header class="panel-heading"><div><h2>十二个月的节奏</h2><p>日记随月份缓缓铺开</p></div></header><div ref="trendEl" class="trend-chart" /></article>
      <article class="panel mood-panel"><header class="panel-heading"><div><h2>心情天气</h2><p>文字里出现最多的心情</p></div></header><div v-if="moods.length" class="rank-list"><div v-for="(item, index) in moods" :key="item.name" class="rank-item"><span class="rank-index">0{{ index + 1 }}</span><span class="rank-name">{{ item.name }}</span><span class="rank-track"><i :style="{ width: `${item.ratio}%` }" /></span><strong>{{ item.count }}</strong></div></div><el-empty v-else :image-size="58" description="还没有心情记录" /></article>
    </section>

    <section class="lower-grid second-grid">
      <article class="panel tags-panel"><header class="panel-heading"><div><h2>常写下的词</h2><p>这些主题，反复陪你经过日常</p></div></header><div v-if="tags.length" class="tag-cloud"><button v-for="(item, index) in tags" :key="item.name" :class="`tag-tone-${index % 5}`" @click="router.push({ path: '/diary/manage', query: { keyword: item.name, year } })"># {{ item.name }} <small>{{ item.count }}</small></button></div><el-empty v-else :image-size="58" description="还没有添加标签" /></article>
      <article class="panel places-panel"><header class="panel-heading"><div><h2>生活坐标</h2><p>日记里提到的地方</p></div></header><div v-if="places.length" class="place-list"><button v-for="(item, index) in places" :key="item.name" @click="router.push({ path: '/diary/manage', query: { keyword: item.name, year } })"><span class="place-pin"><el-icon><LocationInformation /></el-icon></span><span class="place-name">{{ item.name }}</span><span class="place-count">{{ item.count }} 篇</span><span class="place-order">{{ String(index + 1).padStart(2, '0') }}</span></button></div><el-empty v-else :image-size="58" description="还没有地点记录" /></article>
    </section>

    <footer class="closing-note"><span>“</span><p>不是每一天都需要惊天动地，<br>但每一天都值得被记住。</p><span>✳</span></footer>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { Calendar, LocationInformation, Notebook, Picture } from '@element-plus/icons-vue'
import { getDiaryEntries, getDiaryYears } from '../apis/diary.js'

const router = useRouter()
const currentYear = new Date().getFullYear()
const year = ref(currentYear)
const years = ref([currentYear])
const entries = ref([])
const loading = ref(false)
const calendarEl = ref(null)
const trendEl = ref(null)
let calendarChart
let trendChart

const dailyCounts = computed(() => {
  const counts = new Map()
  for (const entry of entries.value) {
    const date = String(entry.entryDate || '').slice(0, 10)
    if (date.startsWith(`${year.value}-`)) counts.set(date, (counts.get(date) || 0) + 1)
  }
  return counts
})
const writtenDays = computed(() => dailyCounts.value.size)
const mediaCount = computed(() => entries.value.reduce((sum, entry) => sum + (entry.mediaIds?.length || 0), 0))
const locationCount = computed(() => new Set(entries.value.map(entry => entry.location?.trim()).filter(Boolean)).size)
function frequency(key) {
  const counts = new Map()
  for (const entry of entries.value) {
    const value = entry[key]
    const values = Array.isArray(value) ? value : [value]
    for (const item of new Set(values.map(value => String(value || '').trim()).filter(Boolean))) counts.set(item, (counts.get(item) || 0) + 1)
  }
  return [...counts].map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count || a.name.localeCompare(b.name, 'zh-CN'))
}
const moods = computed(() => {
  const all = frequency('mood')
  const maximum = Math.max(...all.map(item => item.count), 1)
  return all.slice(0, 5).map(item => ({ ...item, ratio: Math.max(8, Math.round(item.count / maximum * 100)) }))
})
const topMood = computed(() => moods.value[0]?.name)
const tags = computed(() => frequency('tags').slice(0, 12))
const places = computed(() => frequency('location').slice(0, 5))

const chartBase = { animationDuration: 550, textStyle: { fontFamily: 'inherit' } }
function renderCharts() {
  if (!calendarEl.value || !trendEl.value) return
  const dayData = [...dailyCounts.value].map(([date, count]) => [date, count])
  calendarChart ||= echarts.init(calendarEl.value)
  calendarChart.setOption({
    ...chartBase,
    tooltip: { borderWidth: 0, backgroundColor: '#283e40', textStyle: { color: '#fff' }, formatter: params => `${params.value[0]} · ${params.value[1]} 篇` },
    visualMap: { show: false, min: 0, max: Math.max(4, ...dailyCounts.value.values()), inRange: { color: ['#edf2ef', '#c6dfd4', '#89bd9f', '#4c9579', '#286857'] } },
    calendar: { top: 18, left: 34, right: 12, cellSize: ['auto', 14], range: String(year.value), itemStyle: { borderWidth: 3, borderColor: '#fff', color: '#edf2ef' }, splitLine: { show: false }, yearLabel: { show: false }, monthLabel: { color: '#7b8c89', fontSize: 10, nameMap: 'cn' }, dayLabel: { firstDay: 1, color: '#8b9994', fontSize: 9, nameMap: 'cn' } },
    series: [{ type: 'heatmap', coordinateSystem: 'calendar', data: dayData }],
  }, true)

  const monthly = Array(12).fill(0)
  for (const [date, count] of dailyCounts.value) monthly[Number(date.slice(5, 7)) - 1] += count
  trendChart ||= echarts.init(trendEl.value)
  trendChart.setOption({
    ...chartBase,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, borderWidth: 0, backgroundColor: '#283e40', textStyle: { color: '#fff' }, formatter: params => {
      const item = Array.isArray(params) ? params[0] : params
      return `${item.axisValueLabel || item.name}<br/>日记 ${item.value ?? 0} 篇`
    } },
    grid: { top: 22, right: 10, bottom: 25, left: 30 },
    xAxis: { type: 'category', data: monthly.map((_, index) => `${index + 1}月`), axisTick: { show: false }, axisLine: { lineStyle: { color: '#e3e9e5' } }, axisLabel: { color: '#85928c', fontSize: 10 } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf1ed', type: 'dashed' } }, axisLabel: { color: '#a0aaa4', fontSize: 9 } },
    series: [{ type: 'bar', data: monthly, barMaxWidth: 22, itemStyle: { color: '#78aa91', borderRadius: [4, 4, 0, 0] }, showBackground: true, backgroundStyle: { color: '#f4f6f3', borderRadius: [4, 4, 0, 0] } }],
  }, true)
}
function resizeCharts() { calendarChart?.resize(); trendChart?.resize() }
async function loadStatistics() {
  loading.value = true
  try {
    const first = await getDiaryEntries({ year: year.value, page: 1, size: 100 }, { includeMediaTypes: false })
    const all = [...(first.items || [])]
    const pages = Math.ceil((first.total || all.length) / 100)
    const remaining = await Promise.all(Array.from({ length: pages - 1 }, (_, index) =>
      getDiaryEntries({ year: year.value, page: index + 2, size: 100 }, { includeMediaTypes: false })))
    remaining.forEach(result => all.push(...(result.items || [])))
    entries.value = all
    await nextTick()
    renderCharts()
  } catch { entries.value = [] }
  finally { loading.value = false }
}
onMounted(async () => {
  try { years.value = [...new Set([currentYear, ...(await getDiaryYears()) || []])].sort((a, b) => b - a) }
  catch { years.value = [currentYear] }
  window.addEventListener('resize', resizeCharts)
  await loadStatistics()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  calendarChart?.dispose()
  trendChart?.dispose()
  calendarChart = null
  trendChart = null
})
</script>

<style scoped>
.statistics-page { --ink:#2c4144; --muted:#87948f; --line:#e4eae5; --accent:#3d806b; color:var(--ink); }
.page-heading { display:flex; align-items:flex-end; justify-content:space-between; gap:18px; margin-bottom:18px; }.eyebrow { color:#91a098; font-size:9px; font-weight:700; letter-spacing:1px; }.page-heading h1 { margin:5px 0 4px; font-size:25px; }.page-heading p { margin:0; color:var(--muted); font-size:12px; }.year-picker { display:flex; align-items:center; gap:10px; color:#86928d; font-size:11px; }.year-picker :deep(.el-select) { width:116px; }
.year-banner { position:relative; display:flex; min-height:164px; align-items:center; justify-content:space-between; overflow:hidden; padding:24px 30px; border:1px solid #dce9df; border-radius:7px; background:#eaf2e9; }.banner-copy { position:relative; z-index:1; }.banner-copy>span { color:#79937d; font-size:9px; font-weight:700; letter-spacing:.7px; }.banner-copy h2 { margin:9px 0 7px; color:#314f45; font-size:21px; line-height:1.55; }.banner-copy p { margin:0; color:#798f82; font-size:11px; }.banner-number { position:relative; z-index:1; display:flex; flex-direction:column; align-items:center; margin-right:6%; color:#4e8468; }.banner-number strong { font-family:Georgia,serif; font-size:52px; font-weight:500; line-height:1; }.banner-number span { margin-top:8px; color:#82958a; font-size:10px; }.banner-mark { position:absolute; right:15%; top:-63px; color:rgba(91,139,105,.08); font-size:240px; font-style:normal; line-height:1; }.mobile-break { display:none; }
.metric-row { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:11px; margin-top:13px; }.metric { display:flex; min-width:0; min-height:80px; align-items:center; gap:11px; padding:12px; border:1px solid var(--line); border-radius:6px; background:#fff; }.metric-icon { display:grid; width:35px; height:35px; flex:0 0 auto; place-items:center; border-radius:6px; font-size:16px; }.metric-icon.teal { background:#e8f2ed; color:#478168; }.metric-icon.coral { background:#f8eeeb; color:#bd7969; }.metric-icon.gold { background:#f7f2e5; color:#ad8b49; }.metric-icon.green { background:#edf2e8; color:#758c58; }.metric small,.metric strong { display:block; }.metric small { color:#8d9992; font-size:10px; }.metric strong { margin-top:4px; color:#344b44; font-size:20px; }.metric em { color:#8b9891; font-size:10px; font-style:normal; font-weight:500; }
.panel { min-width:0; padding:17px 18px; border:1px solid var(--line); border-radius:7px; background:#fff; }.calendar-panel { margin-top:13px; }.panel-heading { display:flex; align-items:center; justify-content:space-between; gap:12px; }.panel-heading h2 { margin:0; color:#354a43; font-size:14px; font-weight:680; }.panel-heading p { margin:4px 0 0; color:#99a39d; font-size:10px; }.year-stamp { color:#d8e5db; font-family:Georgia,serif; font-size:26px; }.calendar-chart { width:100%; height:154px; margin-top:4px; }.calendar-legend { display:flex; align-items:center; justify-content:flex-end; gap:5px; color:#99a39d; font-size:9px; }.calendar-legend i { width:11px; height:11px; border-radius:2px; }.tone-1 { background:#edf2ef; }.tone-2 { background:#c6dfd4; }.tone-3 { background:#89bd9f; }.tone-4 { background:#4c9579; }.tone-5 { background:#286857; }
.lower-grid { display:grid; grid-template-columns:minmax(0,1.4fr) minmax(260px,.8fr); gap:13px; margin-top:13px; }.trend-chart { width:100%; height:205px; margin-top:6px; }.rank-list { display:grid; gap:15px; margin-top:23px; }.rank-item { display:grid; grid-template-columns:24px 56px minmax(30px,1fr) 22px; align-items:center; gap:8px; }.rank-index { color:#bac5be; font-family:Georgia,serif; font-size:11px; }.rank-name { overflow:hidden; color:#566b61; font-size:11px; text-overflow:ellipsis; white-space:nowrap; }.rank-track { height:5px; overflow:hidden; border-radius:4px; background:#f0f3f0; }.rank-track i { display:block; height:100%; border-radius:4px; background:#78aa91; }.rank-item strong { color:#73847a; font-size:10px; text-align:right; }.mood-panel :deep(.el-empty),.tags-panel :deep(.el-empty),.places-panel :deep(.el-empty) { padding:4px 0; }.mood-panel :deep(.el-empty__description p),.tags-panel :deep(.el-empty__description p),.places-panel :deep(.el-empty__description p) { color:#a0aaa4; font-size:10px; }
.second-grid { grid-template-columns:minmax(0,1fr) minmax(0,1fr); }.tag-cloud { display:flex; flex-wrap:wrap; gap:8px; margin-top:19px; }.tag-cloud button { display:flex; align-items:center; gap:7px; min-height:31px; padding:0 10px; border:1px solid #dce9e1; border-radius:4px; background:#f2f7f3; color:#548069; font:inherit; font-size:10px; cursor:pointer; }.tag-cloud button:hover,.place-list button:hover { border-color:#9dc4ac; background:#f7faf7; }.tag-cloud small { color:#9aa9a0; font-size:9px; }.tag-tone-1 { border-color:#ebdfd5!important; background:#faf5ef!important; color:#a77d5e!important; }.tag-tone-2 { border-color:#e2e5ef!important; background:#f4f5fa!important; color:#7781a0!important; }.tag-tone-3 { border-color:#f0e5d3!important; background:#fbf7ed!important; color:#a78d54!important; }.tag-tone-4 { border-color:#eadfe5!important; background:#faf4f7!important; color:#a0788c!important; }.place-list { display:grid; margin-top:10px; }.place-list button { display:flex; min-width:0; align-items:center; gap:9px; padding:9px 2px; border:0; border-bottom:1px solid #eef1ee; background:transparent; text-align:left; cursor:pointer; }.place-pin { display:grid; width:25px; height:25px; flex:0 0 auto; place-items:center; border-radius:50%; background:#f8eeeb; color:#bd7969; font-size:12px; }.place-name { overflow:hidden; flex:1; color:#566b61; font-size:10px; text-overflow:ellipsis; white-space:nowrap; }.place-count { flex:0 0 auto; color:#99a49d; font-size:9px; }.place-order { width:20px; color:#ccd4ce; font-family:Georgia,serif; font-size:10px; text-align:right; }.closing-note { display:flex; align-items:center; justify-content:center; gap:14px; padding:24px 10px 8px; color:#9aaba0; text-align:center; }.closing-note span { color:#b3c8b8; font-family:Georgia,serif; font-size:24px; }.closing-note p { margin:0; font-size:10px; line-height:1.8; }
@media(max-width:900px) { .metric-row { grid-template-columns:repeat(2,minmax(0,1fr)); }.lower-grid { grid-template-columns:minmax(0,1fr) minmax(220px,.8fr); } }
@media(max-width:620px) { .page-heading { align-items:flex-start; }.page-heading h1 { font-size:22px; }.page-heading p { max-width:240px; font-size:10px; }.year-picker { flex-direction:column; align-items:flex-end; gap:4px; font-size:9px; }.year-picker :deep(.el-select) { width:102px; }.year-banner { min-height:154px; padding:19px 16px; }.banner-copy h2 { max-width:275px; font-size:17px; }.banner-copy p { max-width:250px; font-size:9px; line-height:1.6; }.banner-number { margin-right:0; }.banner-number strong { font-size:39px; }.banner-number span { max-width:62px; font-size:8px; text-align:center; }.banner-mark { right:8%; font-size:190px; }.mobile-break { display:block; }.metric-row { gap:8px; }.metric { min-height:71px; gap:8px; padding:9px; }.metric-icon { width:30px; height:30px; font-size:14px; }.metric strong { font-size:17px; }.panel { padding:14px 11px; }.calendar-chart { height:128px; }.lower-grid,.second-grid { grid-template-columns:minmax(0,1fr); gap:9px; margin-top:9px; }.trend-chart { height:184px; }.rank-list { gap:13px; margin-top:17px; }.closing-note { padding-top:18px; } }
</style>
