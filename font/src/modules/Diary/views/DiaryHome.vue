<template>
  <main class="diary-page" :class="{ 'manage-mode': isManage }">
    <header class="page-header">
      <div>
        <div class="eyebrow"><span class="eyebrow-dot"></span> LIFE JOURNAL</div>
        <h1>{{ isManage ? '日记管理' : '日记动态' }}</h1>
        <p>{{ isManage ? '按记录整理、检索和维护每篇日记。' : '像翻看生活片段一样，浏览每一天。' }}</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="goToEditor()">写日记</el-button>
    </header>

    <section v-if="isManage" class="summary-row" aria-label="日记概览">
      <div class="summary-mark"><el-icon><Notebook /></el-icon></div>
      <div class="summary-copy">
        <span>{{ selectedYear ? `${selectedYear} 年记录` : '累计记录' }}</span>
        <strong>{{ summary.entries || 0 }} <small>篇</small></strong>
      </div>
      <div class="summary-divider"></div>
      <div class="summary-stat"><span>记录日</span><strong>{{ summary.daysWritten || 0 }} 天</strong></div>
      <div class="summary-stat"><span>本月新增</span><strong>{{ summary.thisMonth || 0 }} 篇</strong></div>
      <div class="summary-note"><el-icon><Calendar /></el-icon><span>每一页，都是生活经过的证据</span></div>
    </section>

    <section class="filter-row" aria-label="筛选日记">
      <el-input v-model="keyword" clearable :prefix-icon="Search" placeholder="搜索标题、正文或地点" @keyup.enter="applyFilters" @clear="applyFilters" />
      <el-select v-model="selectedYear" clearable placeholder="全部年份" @change="applyFilters">
        <el-option v-for="year in years" :key="year" :label="`${year} 年`" :value="year" />
      </el-select>
      <el-button :icon="Search" @click="applyFilters">查询</el-button>
      <el-button text @click="resetFilters">重置</el-button>
      <span class="result-count">{{ total }} 篇日记</span>
    </section>

    <section v-loading="loading" class="entry-list" aria-label="日记列表">
      <el-table v-if="isManage && entries.length" :data="entries" class="diary-table" row-key="id">
        <el-table-column prop="entryDate" label="日期" width="170">
          <template #default="{ row }">{{ formatDate(row.entryDate) }}</template>
        </el-table-column>
        <el-table-column label="日记内容" min-width="250">
          <template #default="{ row }">
            <button class="table-title" @click="openDetail(row)">{{ row.title }}</button>
            <p class="table-excerpt">{{ row.content || '（无正文）' }}</p>
          </template>
        </el-table-column>
        <el-table-column label="照片" width="82" align="center">
          <template #default="{ row }">{{ row.mediaIds?.length || 0 }} 个媒体</template>
        </el-table-column>
        <el-table-column label="心情" width="108">
          <template #default="{ row }">{{ row.mood || '—' }}</template>
        </el-table-column>
        <el-table-column label="天气" width="112" align="center">
          <template #default="{ row }"><span v-if="weatherOption(row.weatherCode)" class="weather-cell"><img :src="weatherIconUrl(row.weatherCode)" alt="" />{{ weatherOption(row.weatherCode).name }}</span><span v-else>—</span></template>
        </el-table-column>
        <el-table-column prop="location" label="地点" min-width="130" show-overflow-tooltip />
        <el-table-column label="标签" width="120">
          <template #default="{ row }"><span class="table-tags">{{ row.tags?.join('、') || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="112" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text :icon="Edit" title="编辑" aria-label="编辑日记" @click="goToEditor(row)" />
              <el-button text class="delete-action" :icon="Delete" title="删除" aria-label="删除日记" @click="removeEntry(row)" />
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-else-if="!isManage" class="feed-list">
        <article v-for="entry in entries" :key="entry.id" class="feed-card">
          <header class="feed-card-head">
            <span class="author-avatar"><el-icon><Notebook /></el-icon></span>
            <div class="author-copy"><strong>我的日记</strong><div class="entry-meta"><span>{{ formatDate(entry.entryDate) }}</span><span v-if="entry.mood" class="mood-label">{{ entry.mood }}</span><span v-if="weatherOption(entry.weatherCode)" class="weather-meta"><img :src="weatherIconUrl(entry.weatherCode)" alt="" />{{ weatherOption(entry.weatherCode).name }}</span><span v-if="entry.location"><el-icon><LocationInformation /></el-icon>{{ entry.location }}</span></div></div>
            <el-dropdown trigger="click" @command="command => command === 'edit' ? goToEditor(entry) : removeEntry(entry)">
              <el-button text circle :icon="MoreFilled" aria-label="日记操作" />
              <template #dropdown><el-dropdown-menu><el-dropdown-item command="edit">编辑</el-dropdown-item><el-dropdown-item command="delete" class="delete-item">删除</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </header>
          <button class="entry-title" @click="openDetail(entry)">{{ entry.title }}</button>
          <div v-if="entry.content" class="feed-content" :class="{ expanded: expandedEntries.has(entry.id) }">{{ entry.content }}</div>
          <button v-if="entry.content?.length > 180" class="content-toggle" @click="toggleContent(entry.id)">{{ expandedEntries.has(entry.id) ? '收起' : '全文' }}</button>
          <div v-if="entry.mediaIds?.length" class="feed-gallery" :class="galleryClass(entry)">
            <div v-for="(mediaId, index) in visibleMedia(entry)" :key="mediaId" class="photo-cell">
              <video v-if="entry.mediaTypes?.[mediaId]?.startsWith('video/')" :src="diaryMediaUrl(mediaId)" controls preload="metadata"></video>
              <el-image v-else :src="diaryMediaUrl(mediaId)" :preview-src-list="imageMedia(entry).map(diaryMediaUrl)" :initial-index="imageMedia(entry).indexOf(mediaId)" hide-on-click-modal fit="cover" loading="lazy" />
              <button v-if="index === 8 && entry.mediaIds.length > 9 && !expandedMediaEntries.has(entry.id)" class="photo-overflow" :aria-label="`展开其余 ${entry.mediaIds.length - 9} 个媒体`" @click="toggleMedia(entry)">+{{ entry.mediaIds.length - 9 }} 更多</button>
            </div>
          </div>
          <button v-if="expandedMediaEntries.has(entry.id) && entry.mediaIds.length > 9" class="media-collapse" @click="toggleMedia(entry)">收起照片</button>
          <div v-if="entry.tags?.length" class="entry-tags"><span v-for="tag in entry.tags" :key="tag"># {{ tag }}</span></div>
          <footer class="feed-card-footer"><span v-if="entry.mediaIds?.length" class="photo-count"><el-icon><Picture /></el-icon>{{ entry.mediaIds.length }} 个媒体</span><span v-else></span><el-button text :icon="ChatLineSquare" @click="openDetail(entry)">查看日记</el-button></footer>
        </article>
      </div>

      <div v-if="!isManage && entries.length" ref="feedSentinel" class="feed-load-state" aria-live="polite">
        <span v-if="loadingMore"><el-icon class="is-loading"><Loading /></el-icon>正在翻开更多日记</span>
        <button v-else-if="loadMoreFailed" @click="retryLoadMore">加载失败，点击重试</button>
        <span v-else-if="!hasMore">已经看到最近的日子</span>
      </div>

      <el-empty v-if="!loading && !entries.length" :image-size="92" :description="keyword || selectedYear ? '没有找到匹配的日记' : '还没有日记，写下第一篇吧'">
        <el-button v-if="!keyword && !selectedYear" type="primary" :icon="Plus" @click="goToEditor()">写下第一篇</el-button>
      </el-empty>
    </section>

    <footer v-if="isManage && total" class="pagination-row">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper" @current-change="changePage" @size-change="changePageSize" />
    </footer>

    <el-dialog v-model="detailVisible" width="min(760px, calc(100vw - 28px))" class="diary-dialog detail-dialog">
      <template #header>
        <div v-if="detailEntry" class="detail-heading">
          <div><span>{{ formatDate(detailEntry.entryDate) }}<template v-if="detailEntry.mood"> · {{ detailEntry.mood }}</template></span><h2>{{ detailEntry.title }}</h2></div>
          <el-button text :icon="Edit" @click="goToEditor(detailEntry)">编辑</el-button>
        </div>
      </template>
      <template v-if="detailEntry">
        <div v-if="weatherOption(detailEntry.weatherCode)" class="detail-weather"><img :src="weatherIconUrl(detailEntry.weatherCode)" alt="" />{{ weatherOption(detailEntry.weatherCode).name }}</div>
        <div v-if="detailEntry.location" class="detail-location"><el-icon><LocationInformation /></el-icon>{{ detailEntry.location }}</div>
        <div class="detail-content">{{ detailEntry.content || '这篇日记没有正文。' }}</div>
        <div v-if="detailEntry.mediaIds?.length" class="detail-gallery">
          <video v-for="id in detailEntry.mediaIds.filter(id => detailEntry.mediaTypes?.[id]?.startsWith('video/'))" :key="id" :src="diaryMediaUrl(id)" controls preload="metadata"></video>
          <el-image v-for="(id, index) in detailEntry.mediaIds.filter(id => !detailEntry.mediaTypes?.[id]?.startsWith('video/'))" :key="id" :src="diaryMediaUrl(id)" :preview-src-list="detailEntry.mediaIds.filter(value => !detailEntry.mediaTypes?.[value]?.startsWith('video/')).map(diaryMediaUrl)" :initial-index="index" hide-on-click-modal fit="cover" />
        </div>
        <div v-if="detailEntry.tags?.length" class="entry-tags detail-tags"><span v-for="tag in detailEntry.tags" :key="tag"># {{ tag }}</span></div>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, ChatLineSquare, Delete, Edit, Loading, LocationInformation, MoreFilled, Notebook, Picture, Plus, Search } from '@element-plus/icons-vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import {
  deleteDiaryEntry, diaryMediaUrl, getDiaryEntries, getDiarySummary, getDiaryYears,
} from '../apis/diary.js'
import { diaryFeedState } from '../utils/feedState.js'
import { weatherIconUrl, weatherOption } from '../utils/weather.js'

const currentYear = new Date().getFullYear()
const route = useRoute()
const router = useRouter()
const isManage = computed(() => route.path.endsWith('/manage'))
const selectedYear = ref(route.query.year ? Number(route.query.year) : null)
const years = ref([currentYear])
const keyword = ref(String(route.query.keyword || ''))
const page = ref(readPositiveInt(route.query.page, 1))
const pageSize = ref(isManage.value ? readPositiveInt(route.query.size, 10) : 10)
const total = ref(0)
const entries = ref([])
const summary = ref({ entries: 0, daysWritten: 0, thisMonth: 0 })
const loading = ref(false)
const loadingMore = ref(false)
const loadMoreFailed = ref(false)
const feedReloadFailed = ref(false)
const feedSentinel = ref(null)
const hasMore = computed(() => entries.value.length < total.value)
const detailVisible = ref(false)
const detailEntry = ref(null)
const expandedEntries = ref(new Set())
const expandedMediaEntries = ref(new Set())
watch(() => [route.query.keyword, route.query.year, route.query.page, route.query.size], ([nextKeyword, nextYear, nextPage, nextSize]) => {
  const nextKeywordValue = String(nextKeyword || '')
  const nextYearValue = nextYear ? Number(nextYear) : null
  const nextPageValue = readPositiveInt(nextPage, 1)
  const nextSizeValue = isManage.value ? readPositiveInt(nextSize, 10) : 10
  const filtersChanged = keyword.value !== nextKeywordValue || selectedYear.value !== nextYearValue
  const paginationChanged = page.value !== nextPageValue || pageSize.value !== nextSizeValue
  keyword.value = nextKeywordValue
  selectedYear.value = nextYearValue
  page.value = nextPageValue
  pageSize.value = nextSizeValue
  if (filtersChanged) applyFilters()
  else if (paginationChanged) loadEntries()
})

function readPositiveInt(value, fallback) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

function dateText(value) {
  if (!value) return null
  const parts = String(value).slice(0, 10).split('-').map(Number)
  return new Date(parts[0], parts[1] - 1, parts[2])
}

function galleryClass(entry) {
  const count = visibleMedia(entry).length
  const expanded = expandedMediaEntries.value.has(entry.id)
  const layout = count === 1 ? 'single' : count === 2 ? 'double' : count === 4 ? 'grid-two' : 'grid-three'
  return [layout, expanded ? 'expanded' : ''].filter(Boolean).join(' ')
}
function visibleMedia(entry) {
  return expandedMediaEntries.value.has(entry.id) ? entry.mediaIds : entry.mediaIds.slice(0, 9)
}
function imageMedia(entry) {
  return entry.mediaIds.filter(id => !entry.mediaTypes?.[id]?.startsWith('video/'))
}
function toggleMedia(entry) {
  const next = new Set(expandedMediaEntries.value)
  if (next.has(entry.id)) next.delete(entry.id)
  else next.add(entry.id)
  expandedMediaEntries.value = next
}
function toggleContent(id) {
  const next = new Set(expandedEntries.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedEntries.value = next
}
function formatDate(value) {
  const date = dateText(value)
  return date ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' }).format(date) : ''
}

function cacheFeedState() {
  if (isManage.value) return
  Object.assign(diaryFeedState, {
    entries: entries.value,
    page: page.value,
    total: total.value,
    scrollTop: window.scrollY,
    keyword: keyword.value,
    year: selectedYear.value,
    expandedIds: [...expandedEntries.value],
    savedAt: Date.now(),
  })
}

onBeforeRouteLeave(cacheFeedState)

async function loadEntries({ append = false } = {}) {
  if (append ? loadingMore.value || loadMoreFailed.value || !hasMore.value : loading.value) return
  const requestedPage = append ? page.value + 1 : page.value
  if (append) { loadingMore.value = true; loadMoreFailed.value = false }
  else loading.value = true
  try {
    const result = await getDiaryEntries({ keyword: keyword.value || undefined, year: selectedYear.value || undefined, page: requestedPage, size: pageSize.value })
    const items = result.items || []
    entries.value = append ? [...entries.value, ...items] : items
    total.value = result.total || 0
    page.value = requestedPage
    if (!isManage.value) await nextTick()
  } catch {
    if (!append) { entries.value = []; total.value = 0 }
    else loadMoreFailed.value = true
  } finally {
    if (append) loadingMore.value = false
    else loading.value = false
  }
}

async function loadSummary() {
  try { summary.value = await getDiarySummary(selectedYear.value || undefined) }
  catch { summary.value = { entries: 0, daysWritten: 0, thisMonth: 0 } }
}

async function loadMetadata() {
  try {
    const result = await getDiaryYears()
    years.value = [...new Set([currentYear, ...(result || [])])].sort((a, b) => b - a)
  } catch { years.value = [currentYear] }
}

async function applyFilters() {
  page.value = 1
  entries.value = []
  loadMoreFailed.value = false
  syncManageQuery()
  await Promise.all([loadEntries(), loadSummary()])
}

function manageQuery() {
  const query = {}
  if (keyword.value) query.keyword = keyword.value
  if (selectedYear.value) query.year = selectedYear.value
  if (page.value > 1) query.page = page.value
  if (pageSize.value !== 10) query.size = pageSize.value
  return query
}

function syncManageQuery() {
  if (isManage.value) router.replace({ path: '/diary/manage', query: manageQuery() })
}

function changePage(nextPage) {
  page.value = nextPage
  syncManageQuery()
  loadEntries()
}

function changePageSize(nextSize) {
  pageSize.value = nextSize
  page.value = 1
  syncManageQuery()
  loadEntries()
}

function retryLoadMore() {
  if (feedReloadFailed.value) return reloadLoadedFeed()
  loadMoreFailed.value = false
  return loadEntries({ append: true })
}

async function reloadLoadedFeed(deletedId) {
  const loadedPageCount = Math.max(1, page.value)
  loadingMore.value = true
  loadMoreFailed.value = false
  feedReloadFailed.value = false
  try {
    const results = await Promise.all(Array.from({ length: loadedPageCount }, (_, index) => getDiaryEntries({
      keyword: keyword.value || undefined,
      year: selectedYear.value || undefined,
      page: index + 1,
      size: pageSize.value,
    })))
    entries.value = results.flatMap(result => result.items || [])
    total.value = results[0]?.total || 0
    page.value = Math.min(loadedPageCount, Math.max(1, Math.ceil(total.value / pageSize.value)))
  } catch {
    const hadEntry = entries.value.some(item => item.id === deletedId)
    entries.value = entries.value.filter(item => item.id !== deletedId)
    if (hadEntry) total.value = Math.max(0, total.value - 1)
    feedReloadFailed.value = true
    loadMoreFailed.value = true
  } finally {
    loadingMore.value = false
  }
}

let feedObserver
onMounted(async () => {
  if (!isManage.value && 'IntersectionObserver' in window) {
    feedObserver = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) loadEntries({ append: true })
    }, { rootMargin: '500px 0px' })
    await nextTick()
    if (feedSentinel.value) feedObserver.observe(feedSentinel.value)
  }
})
watch(feedSentinel, (element, previous) => {
  if (previous) feedObserver?.unobserve(previous)
  if (element) feedObserver?.observe(element)
})
onBeforeUnmount(() => feedObserver?.disconnect())

function resetFilters() {
  keyword.value = ''
  selectedYear.value = null
  applyFilters()
}

async function removeEntry(entry) {
  try {
    await ElMessageBox.confirm(`确定删除《${entry.title}》？`, '删除日记', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await deleteDiaryEntry(entry.id)
    ElMessage.success('日记已删除')
    await Promise.all([
      loadMetadata(),
      isManage.value ? loadEntries() : reloadLoadedFeed(entry.id),
      loadSummary(),
    ])
  } catch { /* cancelled or reported by request interceptor */ }
}

function openDetail(entry) { detailEntry.value = entry; detailVisible.value = true }
function goToEditor(entry = null) {
  detailVisible.value = false
  const query = { from: isManage.value ? 'manage' : 'feed' }
  if (isManage.value) {
    Object.assign(query, manageQuery())
  } else {
    if (selectedYear.value) query.year = selectedYear.value
    if (keyword.value) query.keyword = keyword.value
  }
  router.push({ path: entry ? `/diary/write/${entry.id}` : '/diary/write', query })
}

onMounted(async () => {
  await loadMetadata()
  const restoreFeed = !isManage.value
    && diaryFeedState.restoreOnReturn
    && diaryFeedState.entries.length > 0
    && Date.now() - diaryFeedState.savedAt < 5 * 60 * 1000
    && diaryFeedState.keyword === keyword.value
    && diaryFeedState.year === selectedYear.value
  if (restoreFeed) {
    entries.value = diaryFeedState.entries
    page.value = diaryFeedState.page
    total.value = diaryFeedState.total
    expandedEntries.value = new Set(diaryFeedState.expandedIds)
    await nextTick()
    await loadSummary()
    return
  }
  await Promise.all([loadEntries(), loadSummary()])
})
</script>

<style scoped>
.diary-page { --ink:#293b49; --muted:#7c8b95; --line:#e2e9e9; --accent:#258b88; --soft:#e9f4f2; min-height:100%; color:var(--ink); }
.page-header { display:flex; align-items:center; justify-content:space-between; gap:18px; margin-bottom:20px; }
.page-header h1 { margin:3px 0 4px; font-size:26px; font-weight:700; line-height:1.2; }
.page-header p { margin:0; color:var(--muted); font-size:13px; }
.eyebrow { display:flex; align-items:center; gap:7px; color:#809096; font-size:10px; font-weight:700; letter-spacing:1px; }
.eyebrow-dot { width:7px; height:7px; border-radius:50%; background:#d07a61; }
.page-header :deep(.el-button) { height:38px; padding:0 17px; border-radius:6px; background:var(--accent); border-color:var(--accent); }
.summary-row { display:flex; align-items:center; gap:18px; min-height:96px; margin-bottom:15px; padding:16px 20px; border:1px solid var(--line); border-radius:7px; background:#fff; }
.summary-mark { display:grid; width:44px; height:44px; flex:0 0 auto; place-items:center; border-radius:7px; background:#f7eee7; color:#bd7552; font-size:20px; }
.summary-copy span,.summary-stat span { display:block; color:#839198; font-size:11px; }
.summary-copy strong { display:block; margin-top:3px; color:#2e454d; font-size:22px; font-weight:700; }
.summary-copy small { color:#829197; font-size:12px; font-weight:500; }
.summary-divider { width:1px; height:38px; margin:0 5px; background:var(--line); }
.summary-stat { min-width:105px; }.summary-stat strong { display:block; margin-top:7px; color:#42585e; font-size:13px; font-weight:650; }
.summary-note { display:flex; align-items:center; gap:7px; margin-left:auto; color:#9aa7ab; font-size:11px; }.summary-note .el-icon { color:#258b88; font-size:15px; }
.filter-row { display:flex; align-items:center; gap:9px; margin-bottom:18px; padding:11px 12px; border:1px solid var(--line); border-radius:7px; background:#fff; }
.filter-row .el-input { width:min(330px,34vw); }.filter-row .el-select { width:145px; }.filter-row :deep(.el-input__wrapper),.filter-row :deep(.el-select__wrapper) { min-height:35px; box-shadow:0 0 0 1px #dce4e8 inset; }
.filter-row :deep(.el-button) { border-radius:5px; }.result-count { margin-left:auto; color:#829096; font-size:11px; white-space:nowrap; }
.entry-list { min-height:240px; }.entry-title { min-width:0; padding:0; border:0; background:transparent; color:#2f454b; font:inherit; font-size:16px; font-weight:680; text-align:left; cursor:pointer; }.entry-title:hover { color:var(--accent); }
.entry-meta { display:flex; flex-wrap:wrap; align-items:center; gap:13px; margin-top:5px; color:#929fa3; font-size:10px; }.entry-meta span { display:inline-flex; align-items:center; gap:4px; }
.entry-tags { display:flex; flex-wrap:wrap; gap:6px; margin-top:12px; }.entry-tags span { padding:4px 7px; border:1px solid #deece9; border-radius:4px; background:#f5faf9; color:#60817d; font-size:10px; }
.pagination-row { display:flex; align-items:center; justify-content:space-between; gap:16px; padding:14px 3px 20px 108px; color:#8a989d; font-size:11px; }
.diary-dialog :deep(.el-dialog) { overflow:hidden; border:1px solid var(--line); border-radius:8px; }.diary-dialog :deep(.el-dialog__header) { padding:19px 22px 14px; border-bottom:1px solid #edf1f1; }.diary-dialog :deep(.el-dialog__title) { color:#30454b; font-size:17px; font-weight:680; }.diary-dialog :deep(.el-dialog__body) { max-height:72vh; overflow:auto; padding:18px 22px 8px; }.diary-dialog :deep(.el-dialog__footer) { padding:12px 22px 18px; border-top:1px solid #edf1f1; }.diary-dialog :deep(.el-form-item__label) { color:#566b70; font-size:12px; font-weight:600; }.diary-dialog :deep(.el-textarea__inner) { line-height:1.75; }.diary-dialog :deep(.el-upload--picture-card) { --el-upload-picture-card-size:82px; border-radius:5px; }.diary-dialog :deep(.el-upload-list--picture-card .el-upload-list__item) { --el-upload-picture-card-size:82px; border-radius:5px; }
.tag-editor { display:flex; flex-wrap:wrap; align-items:center; gap:7px; width:100%; }.tag-editor :deep(.el-tag) { border-color:#d7e8e4; color:#557b75; background:#f3f8f7; }
.detail-dialog :deep(.el-dialog__header) { padding:18px 22px 14px; }.detail-dialog :deep(.el-dialog__body) { padding-top:17px; }.detail-heading { display:flex; align-items:center; justify-content:space-between; gap:12px; }.detail-heading span { color:#89979c; font-size:11px; }.detail-heading h2 { margin:5px 0 0; color:#2f454b; font-size:20px; font-weight:700; }
.detail-location { display:flex; align-items:center; gap:6px; margin-bottom:14px; color:#7c8d92; font-size:12px; }.detail-content { color:#43585e; font-size:14px; line-height:1.9; white-space:pre-wrap; overflow-wrap:anywhere; }.detail-gallery { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:9px; margin-top:18px; }.detail-gallery :deep(.el-image),.detail-gallery video { width:100%; aspect-ratio:4/3; border-radius:5px; background:#172329; object-fit:cover; }.detail-tags { margin-top:20px; }
.manage-mode .page-header { margin-bottom:18px; }
.diary-table { --el-table-border-color:#e6ecee; --el-table-header-bg-color:#f4f7f8; --el-table-row-hover-bg-color:#f4f9f8; overflow:hidden; border:1px solid #e3e9ec; border-radius:7px; background:#fff; }
.diary-table :deep(th.el-table__cell) { height:44px; color:#63757d; font-size:11px; font-weight:650; }
.diary-table :deep(td.el-table__cell) { height:66px; color:#495d64; font-size:12px; }
.diary-table :deep(.el-table__inner-wrapper::before) { display:none; }
.weather-cell,.weather-meta { display:inline-flex; align-items:center; gap:5px; white-space:nowrap; }.weather-cell img,.weather-meta img { width:24px; height:24px; object-fit:contain; }.weather-meta img { width:18px; height:18px; }.detail-weather { display:flex; align-items:center; gap:7px; margin-bottom:12px; color:#71827e; font-size:12px; }.detail-weather img { width:30px; height:30px; object-fit:contain; }
.diary-table :deep(td:first-child .cell) { white-space:nowrap; }
.table-actions { display:flex; width:100%; align-items:center; justify-content:center; gap:6px; }
.table-actions :deep(.el-button) { width:30px; height:30px; margin:0; }
.table-title { display:block; max-width:100%; overflow:hidden; padding:0; border:0; background:none; color:#30474e; font:inherit; font-size:13px; font-weight:650; text-align:left; text-overflow:ellipsis; white-space:nowrap; cursor:pointer; }
.table-title:hover { color:#258b88; }.table-excerpt { display:-webkit-box; overflow:hidden; max-width:560px; margin:5px 0 0; color:#87959a; font-size:11px; line-height:1.5; -webkit-box-orient:vertical; -webkit-line-clamp:1; }
.table-tags { display:block; overflow:hidden; color:#64847f; font-size:11px; text-overflow:ellipsis; white-space:nowrap; }
.feed-list { display:grid; width:min(760px,100%); gap:13px; margin:0 auto; }
.feed-card { min-width:0; padding:18px 20px 10px; border:1px solid #e3e9ec; border-radius:8px; background:#fff; box-shadow:0 3px 12px rgba(36,60,65,.045); }
.feed-card-head { display:flex; align-items:center; gap:11px; }.author-avatar { display:grid; width:42px; height:42px; flex:0 0 auto; place-items:center; border:1px solid #d7e8e4; border-radius:50%; background:#eaf4f1; color:#258b88; font-size:19px; }.author-copy { min-width:0; flex:1; }.author-copy > strong { color:#334b51; font-size:13px; font-weight:680; }.author-copy .entry-meta { gap:8px; margin-top:4px; font-size:10px; }.author-copy .mood-label { color:#b66e50; }.feed-card-head > .el-dropdown :deep(.el-button) { width:30px; height:30px; color:#87969b; }
.feed-card > .entry-title { display:block; max-width:100%; margin:14px 0 0; color:#30474e; font-size:17px; line-height:1.5; }.feed-card > .entry-title:hover { color:#258b88; }
.feed-content { display:-webkit-box; overflow:hidden; margin:7px 0 0; color:#4d6268; font-size:13px; line-height:1.85; white-space:pre-wrap; overflow-wrap:anywhere; -webkit-box-orient:vertical; -webkit-line-clamp:6; }.feed-content.expanded { display:block; }
.content-toggle { margin:4px 0 0; padding:0; border:0; background:none; color:#258b88; font-size:11px; font-weight:600; cursor:pointer; }
.feed-gallery { display:grid; width:min(100%,420px); grid-template-columns:repeat(3,minmax(0,1fr)); gap:5px; margin-top:12px; }.photo-cell { position:relative; min-width:0; aspect-ratio:1; overflow:hidden; background:#f0f3f3; }.photo-cell :deep(.el-image),.photo-cell video { display:block; width:100%; height:100%; object-fit:cover; background:#172329; }.photo-cell :deep(img) { transition:transform .22s ease; }.photo-cell:hover :deep(img) { transform:scale(1.035); }.feed-gallery.single { grid-template-columns:minmax(0,1fr); width:min(100%,240px); }.feed-gallery.double,.feed-gallery.grid-two { grid-template-columns:repeat(2,minmax(0,1fr)); width:min(100%,360px); }.feed-gallery.grid-three { grid-template-columns:repeat(3,minmax(0,1fr)); }.feed-gallery.expanded { grid-auto-rows:auto; }.feed-gallery.expanded .photo-cell { aspect-ratio:1; }.photo-overflow { position:absolute; inset:0; display:flex; align-items:center; justify-content:center; gap:4px; width:100%; height:100%; border:0; background:rgba(20,35,40,.56); color:#fff; font-size:16px; font-weight:600; cursor:pointer; }.media-collapse { margin-top:7px; padding:3px 0; border:0; background:none; color:#258b88; font-size:11px; cursor:pointer; }
.feed-card .entry-tags { margin-top:11px; }.feed-card .entry-tags span { border-color:#e5edeb; background:#f7faf9; }
.feed-card-footer { display:flex; align-items:center; justify-content:space-between; min-height:42px; margin-top:9px; border-top:1px solid #edf1f2; }.feed-card-footer :deep(.el-button) { color:#71838a; font-size:11px; }.feed-card-footer :deep(.el-button:hover) { color:#258b88; background:#eff7f6; }.photo-count { display:flex; align-items:center; gap:5px; color:#8a999e; font-size:10px; }
.feed-load-state { display:flex; min-height:52px; align-items:center; justify-content:center; color:#91a0a3; font-size:11px; }.feed-load-state span { display:flex; align-items:center; gap:7px; }.feed-load-state button { padding:7px 12px; border:1px solid #e0e9e7; border-radius:5px; background:#fff; color:#64847f; font:inherit; cursor:pointer; }
@media(max-width:760px) {
  .page-header { margin-bottom:15px; }.page-header h1 { font-size:23px; }.summary-row { gap:12px; min-height:82px; padding:12px; }.summary-note { display:none; }.summary-stat { min-width:0; }.summary-divider { margin:0; }.filter-row { flex-wrap:wrap; gap:8px; }.filter-row .el-input { width:100%; }.filter-row .el-select { flex:1; min-width:120px; }.result-count { margin-left:auto; }
  .entry-title { font-size:14px; }.pagination-row { padding-left:3px; }.detail-gallery { grid-template-columns:repeat(2,minmax(0,1fr)); }.feed-card { padding:15px 14px 8px; }.feed-gallery { width:100%; gap:4px; }.feed-gallery.single { width:min(100%,240px); }.feed-gallery.double,.feed-gallery.grid-two { width:min(100%,360px); }
}
@media(max-width:900px) { .summary-row { gap:12px; } }
@media(max-width:460px) {
  .summary-mark { width:36px; height:36px; }.summary-copy strong { font-size:19px; }.summary-stat strong { font-size:12px; }.summary-divider { display:none; }.entry-meta { gap:8px; }
}
</style>
