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
        <el-table-column prop="entryDate" label="日期" width="132">
          <template #default="{ row }">{{ formatDate(row.entryDate) }}</template>
        </el-table-column>
        <el-table-column label="日记内容" min-width="250">
          <template #default="{ row }">
            <button class="table-title" @click="openDetail(row)">{{ row.title }}</button>
            <p class="table-excerpt">{{ row.content || '（无正文）' }}</p>
          </template>
        </el-table-column>
        <el-table-column label="照片" width="82" align="center">
          <template #default="{ row }">{{ row.mediaIds?.length || 0 }} 张</template>
        </el-table-column>
        <el-table-column label="心情" width="108">
          <template #default="{ row }">{{ row.mood || '—' }}</template>
        </el-table-column>
        <el-table-column prop="location" label="地点" min-width="130" show-overflow-tooltip />
        <el-table-column label="标签" min-width="150">
          <template #default="{ row }"><span class="table-tags">{{ row.tags?.join('、') || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="112" fixed="right" align="center">
          <template #default="{ row }">
            <el-button text :icon="Edit" title="编辑" aria-label="编辑日记" @click="goToEditor(row)" />
            <el-button text class="delete-action" :icon="Delete" title="删除" aria-label="删除日记" @click="removeEntry(row)" />
          </template>
        </el-table-column>
      </el-table>

      <div v-else-if="!isManage" class="feed-columns">
        <article v-for="entry in entries" :key="entry.id" class="feed-card">
          <header class="feed-card-head">
            <span class="feed-date"><strong>{{ dayOf(entry.entryDate) }}</strong><small>{{ monthOf(entry.entryDate) }}</small></span>
            <div class="feed-title-wrap">
              <button class="entry-title" @click="openDetail(entry)">{{ entry.title }}</button>
              <div class="entry-meta">
                <span>{{ formatDate(entry.entryDate) }}</span><span v-if="entry.mood">{{ entry.mood }}</span>
                <span v-if="entry.location"><el-icon><LocationInformation /></el-icon>{{ entry.location }}</span>
              </div>
            </div>
            <el-dropdown trigger="click" @command="command => command === 'edit' ? goToEditor(entry) : removeEntry(entry)">
              <el-button text circle :icon="MoreFilled" aria-label="日记操作" />
              <template #dropdown><el-dropdown-menu><el-dropdown-item command="edit">编辑</el-dropdown-item><el-dropdown-item command="delete" class="delete-item">删除</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </header>
          <p v-if="entry.content" class="feed-content">{{ entry.content }}</p>
          <div v-if="entry.mediaIds?.length" class="feed-gallery" :class="{ single: entry.mediaIds.length === 1, triple: entry.mediaIds.length === 3 }">
            <el-image v-for="(mediaId, index) in entry.mediaIds.slice(0, 4)" :key="mediaId" :src="diaryMediaUrl(mediaId)" :preview-src-list="entry.mediaIds.map(diaryMediaUrl)" :initial-index="index" fit="cover" loading="lazy" />
            <button v-if="entry.mediaIds.length > 4" class="photo-overflow" @click="openDetail(entry)">+{{ entry.mediaIds.length - 4 }}</button>
          </div>
          <div v-if="entry.tags?.length" class="entry-tags"><span v-for="tag in entry.tags" :key="tag"># {{ tag }}</span></div>
        </article>
      </div>

      <el-empty v-if="!loading && !entries.length" :image-size="92" :description="keyword || selectedYear ? '没有找到匹配的日记' : '还没有日记，写下第一篇吧'">
        <el-button v-if="!keyword && !selectedYear" type="primary" :icon="Plus" @click="goToEditor()">写下第一篇</el-button>
      </el-empty>
    </section>

    <footer v-if="total > pageSize" class="pagination-row">
      <span>第 {{ page }} / {{ pageCount }} 页</span>
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="loadEntries" />
    </footer>

    <el-dialog v-model="detailVisible" width="min(760px, calc(100vw - 28px))" class="diary-dialog detail-dialog">
      <template #header>
        <div v-if="detailEntry" class="detail-heading">
          <div><span>{{ formatDate(detailEntry.entryDate) }}<template v-if="detailEntry.mood"> · {{ detailEntry.mood }}</template></span><h2>{{ detailEntry.title }}</h2></div>
          <el-button text :icon="Edit" @click="goToEditor(detailEntry)">编辑</el-button>
        </div>
      </template>
      <template v-if="detailEntry">
        <div v-if="detailEntry.location" class="detail-location"><el-icon><LocationInformation /></el-icon>{{ detailEntry.location }}</div>
        <div class="detail-content">{{ detailEntry.content || '这篇日记没有正文。' }}</div>
        <div v-if="detailEntry.mediaIds?.length" class="detail-gallery">
          <el-image v-for="(id, index) in detailEntry.mediaIds" :key="id" :src="diaryMediaUrl(id)" :preview-src-list="detailEntry.mediaIds.map(diaryMediaUrl)" :initial-index="index" fit="cover" />
        </div>
        <div v-if="detailEntry.tags?.length" class="entry-tags detail-tags"><span v-for="tag in detailEntry.tags" :key="tag"># {{ tag }}</span></div>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, Delete, Edit, LocationInformation, MoreFilled, Notebook, Plus, Search } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import {
  deleteDiaryEntry, diaryMediaUrl, getDiaryEntries, getDiarySummary, getDiaryYears,
} from '../apis/diary.js'

const currentYear = new Date().getFullYear()
const route = useRoute()
const router = useRouter()
const isManage = computed(() => route.path.endsWith('/manage'))
const selectedYear = ref(null)
const years = ref([currentYear])
const keyword = ref('')
const page = ref(1)
const pageSize = 10
const total = ref(0)
const entries = ref([])
const summary = ref({ entries: 0, daysWritten: 0, thisMonth: 0 })
const loading = ref(false)
const detailVisible = ref(false)
const detailEntry = ref(null)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function dateText(value) {
  if (!value) return null
  const parts = String(value).slice(0, 10).split('-').map(Number)
  return new Date(parts[0], parts[1] - 1, parts[2])
}

function dayOf(value) { return String(value || '').slice(8, 10) }
function monthOf(value) { return `${String(value || '').slice(5, 7)} 月` }
function formatDate(value) {
  const date = dateText(value)
  return date ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' }).format(date) : ''
}

async function loadEntries() {
  loading.value = true
  try {
    const result = await getDiaryEntries({ keyword: keyword.value || undefined, year: selectedYear.value || undefined, page: page.value, size: pageSize })
    entries.value = result.items || []
    total.value = result.total || 0
  } catch { entries.value = []; total.value = 0 }
  finally { loading.value = false }
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
  await Promise.all([loadEntries(), loadSummary()])
}

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
    await Promise.all([loadMetadata(), loadEntries(), loadSummary()])
  } catch { /* cancelled or reported by request interceptor */ }
}

function openDetail(entry) { detailEntry.value = entry; detailVisible.value = true }
function goToEditor(entry = null) {
  detailVisible.value = false
  router.push({ path: entry ? `/diary/write/${entry.id}` : '/diary/write', query: { from: isManage.value ? 'manage' : 'feed' } })
}

onMounted(async () => {
  await loadMetadata()
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
.detail-location { display:flex; align-items:center; gap:6px; margin-bottom:14px; color:#7c8d92; font-size:12px; }.detail-content { color:#43585e; font-size:14px; line-height:1.9; white-space:pre-wrap; overflow-wrap:anywhere; }.detail-gallery { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:9px; margin-top:18px; }.detail-gallery :deep(.el-image) { width:100%; aspect-ratio:4/3; border-radius:5px; background:#f1f4f4; }.detail-tags { margin-top:20px; }
.manage-mode .page-header { margin-bottom:18px; }
.diary-table { --el-table-border-color:#e6ecee; --el-table-header-bg-color:#f4f7f8; --el-table-row-hover-bg-color:#f4f9f8; overflow:hidden; border:1px solid #e3e9ec; border-radius:7px; background:#fff; }
.diary-table :deep(th.el-table__cell) { height:44px; color:#63757d; font-size:11px; font-weight:650; }
.diary-table :deep(td.el-table__cell) { height:66px; color:#495d64; font-size:12px; }
.diary-table :deep(.el-table__inner-wrapper::before) { display:none; }
.table-title { display:block; max-width:100%; overflow:hidden; padding:0; border:0; background:none; color:#30474e; font:inherit; font-size:13px; font-weight:650; text-align:left; text-overflow:ellipsis; white-space:nowrap; cursor:pointer; }
.table-title:hover { color:#258b88; }.table-excerpt { display:-webkit-box; overflow:hidden; max-width:560px; margin:5px 0 0; color:#87959a; font-size:11px; line-height:1.5; -webkit-box-orient:vertical; -webkit-line-clamp:1; }
.table-tags { display:block; overflow:hidden; color:#64847f; font-size:11px; text-overflow:ellipsis; white-space:nowrap; }
.feed-columns { column-count:2; column-gap:15px; }.feed-card { display:inline-block; width:100%; margin:0 0 15px; padding:16px; break-inside:avoid; border:1px solid #e2e9e9; border-radius:7px; background:#fff; box-shadow:0 2px 8px rgba(36,60,65,.035); }
.feed-card-head { display:grid; grid-template-columns:42px minmax(0,1fr) 30px; align-items:start; gap:10px; }.feed-date { display:flex; width:40px; height:45px; flex-direction:column; align-items:center; justify-content:center; border:1px solid #dbeae7; border-radius:6px; background:#f1f8f7; color:#258b88; }.feed-date strong { font-size:17px; line-height:1; }.feed-date small { margin-top:4px; color:#7c9995; font-size:9px; }
.feed-title-wrap { min-width:0; padding-top:2px; }.feed-title-wrap .entry-title { display:block; width:100%; overflow-wrap:anywhere; font-size:14px; line-height:1.5; }.feed-title-wrap .entry-meta { gap:8px; margin-top:4px; font-size:10px; }.feed-card-head > .el-dropdown :deep(.el-button) { width:28px; height:28px; color:#8b999e; }
.feed-content { margin:13px 0 0; color:#4d6268; font-size:12px; line-height:1.8; white-space:pre-wrap; overflow-wrap:anywhere; }.feed-gallery { position:relative; display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:5px; margin-top:12px; overflow:hidden; border-radius:5px; }.feed-gallery :deep(.el-image) { width:100%; height:116px; background:#f1f4f4; }.feed-gallery.single :deep(.el-image) { height:235px; }.feed-gallery.triple :deep(.el-image:first-child) { grid-column:span 2; height:166px; }.feed-gallery.triple :deep(.el-image:not(:first-child)) { height:95px; }
.photo-overflow { position:absolute; right:0; bottom:0; display:grid; width:calc(50% - 3px); height:116px; place-items:center; border:0; background:rgba(20,35,40,.56); color:#fff; font-size:20px; cursor:pointer; }
.feed-card .entry-tags { margin-top:11px; }.feed-card .entry-tags span { border-color:#e5edeb; background:#f7faf9; }
@media(max-width:760px) {
  .page-header { margin-bottom:15px; }.page-header h1 { font-size:23px; }.summary-row { gap:12px; min-height:82px; padding:12px; }.summary-note { display:none; }.summary-stat { min-width:0; }.summary-divider { margin:0; }.filter-row { flex-wrap:wrap; gap:8px; }.filter-row .el-input { width:100%; }.filter-row .el-select { flex:1; min-width:120px; }.result-count { margin-left:auto; }
  .entry-title { font-size:14px; }.pagination-row { padding-left:3px; }.detail-gallery { grid-template-columns:repeat(2,minmax(0,1fr)); }
}
@media(max-width:900px) { .feed-columns { column-count:1; }.summary-row { gap:12px; } }
@media(max-width:460px) {
  .summary-mark { width:36px; height:36px; }.summary-copy strong { font-size:19px; }.summary-stat strong { font-size:12px; }.summary-divider { display:none; }.entry-meta { gap:8px; }
}
</style>
