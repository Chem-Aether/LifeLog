<template>
  <div v-loading="loading" class="city-album-page">
    <header class="page-header">
      <div>
        <h1>城市图册</h1>
        <p>按城市回看旅行足迹中的照片与地点。</p>
      </div>
      <div class="album-summary">
        <span><strong>{{ cityCount }}</strong> 个城市</span>
        <span><strong>{{ placeCount }}</strong> 个地点</span>
        <span><strong>{{ photoCount }}</strong> 张照片</span>
      </div>
    </header>

    <div class="album-toolbar">
      <el-input v-model="searchText" clearable placeholder="搜索城市或地点" class="album-search">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="selectedYear" aria-label="按年份筛选" class="year-select">
        <el-option label="全部年份" value="all" />
        <el-option v-for="year in years" :key="year" :label="year" :value="year" />
      </el-select>
    </div>

    <div v-if="!loading && !cities.length" class="empty-state">
      <el-icon><Picture /></el-icon>
      <strong>还没有匹配的旅行足迹</strong>
      <span>调整筛选条件，或先在旅行足迹中添加地点与照片。</span>
    </div>

    <div v-else-if="activeCity" class="album-layout">
      <aside class="city-navigation" aria-label="城市列表">
        <div class="city-navigation-heading">城市分组 <span>{{ cities.length }}</span></div>
        <button
          v-for="city in cities"
          :key="city.key"
          class="city-option"
          :class="{ active: city.key === activeCity.key }"
          :aria-pressed="city.key === activeCity.key"
          @click="activeCityKey = city.key"
        >
          <span class="city-option-icon"><el-icon><Location /></el-icon></span>
          <span class="city-option-copy"><strong>{{ city.name }}</strong><small>{{ city.photos.length }} 张 · {{ city.places.size }} 个地点</small></span>
          <el-icon class="city-option-arrow"><ArrowRight /></el-icon>
        </button>
      </aside>

      <section class="gallery-content">
        <header class="gallery-heading">
          <div>
            <span class="eyebrow">TRAVEL ALBUM</span>
            <h2>{{ activeCity.name }}</h2>
            <p>{{ activeCity.provinceName || '旅行足迹' }}<span v-if="activeCity.latestDate"> · 最近到访 {{ activeCity.latestDate }}</span></p>
          </div>
          <div class="gallery-count"><strong>{{ activeCity.photos.length }}</strong><span>张照片</span></div>
        </header>

        <div v-if="activeCity.photos.length" class="photo-grid">
          <article v-for="photo in activeCity.photos" :key="photo.key" class="photo-tile" :class="{ 'is-loaded': loadedPhotoKeys.has(photo.key) }">
            <el-image
              :src="photo.url"
              :preview-src-list="previewUrls"
              :initial-index="photo.index"
              hide-on-click-modal
              :alt="`${photo.placeName}旅行照片`"
              fit="cover"
              loading="lazy"
              @load="markPhotoLoaded(photo.key)"
            >
              <template #error><div class="photo-error"><el-icon><Picture /></el-icon></div></template>
            </el-image>
            <div class="photo-caption">
              <strong>{{ photo.placeName }}</strong>
              <span>{{ photo.visitDate || photo.regionName || '旅行记录' }}</span>
            </div>
          </article>
        </div>
        <div v-else class="gallery-empty">
          <el-icon><Picture /></el-icon>
          <span>这个城市暂时还没有旅行照片</span>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowRight, Location, Picture, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getFootprints } from '@/modules/travel/apis/travel.js'
import { enrichFootprintRegions } from '@/modules/travel/apis/sysResource.js'
import { mediaUrl } from '@/modules/travel/apis/media.js'
import { getFootprintRegion } from '@/modules/travel/utils/footprintRegion.js'

const records = ref([])
const loading = ref(false)
const loadedPhotoKeys = ref(new Set())
const searchText = ref('')
const selectedYear = ref('all')
const activeCityKey = ref('')

const years = computed(() => [...new Set(records.value.map(item => String(item.visitDate || '').slice(0, 4))
  .filter(year => /^\d{4}$/.test(year)))].sort((a, b) => b.localeCompare(a)))

const cities = computed(() => {
  const query = searchText.value.trim().toLocaleLowerCase()
  const groups = new Map()
  const matchingRecords = records.value.filter(item => {
    const region = getFootprintRegion(item)
    if (item.visitType === 'transit') return false
    if (selectedYear.value !== 'all' && !String(item.visitDate || '').startsWith(selectedYear.value)) return false
    if (!query) return true
    return `${region.cityName} ${region.provinceName} ${item.placeName || ''} ${region.regionName}`.toLocaleLowerCase().includes(query)
  })

  for (const item of matchingRecords) {
    const region = getFootprintRegion(item)
    const provinceName = String(region.provinceName || '').trim()
    const cityName = String(region.cityName || '').trim()
    const name = cityName || '未标注城市'
    const key = cityName ? `${provinceName}:${cityName}` : 'unknown-city'
    if (!groups.has(key)) {
      groups.set(key, { key, name, provinceName, unassigned: !cityName, places: new Map(), photos: [], latestDate: '' })
    }
    const city = groups.get(key)
    if (!city.places.has(item.footprintId ?? item.placeName)) city.places.set(item.footprintId ?? item.placeName, item)
    if (item.visitDate && item.visitDate > city.latestDate) city.latestDate = item.visitDate
    for (const mediaId of (Array.isArray(item.mediaIds) ? item.mediaIds : [])) {
      const url = mediaUrl(mediaId)
      if (!url) continue
      city.photos.push({
        key: `${item.footprintId}-${mediaId}`,
        mediaId,
        url,
        placeName: item.placeName || '未命名地点',
        regionName: region.regionName,
        visitDate: item.visitDate || '',
      })
    }
  }

  return [...groups.values()]
    .map(city => {
      city.photos.sort((a, b) => b.visitDate.localeCompare(a.visitDate))
      city.photos.forEach((photo, index) => { photo.index = index })
      return city
    })
    .sort((a, b) => b.latestDate.localeCompare(a.latestDate) || a.name.localeCompare(b.name, 'zh-CN'))
})

const activeCity = computed(() => cities.value.find(city => city.key === activeCityKey.value) || cities.value[0] || null)
const previewUrls = computed(() => activeCity.value?.photos.map(photo => photo.url) || [])
const cityCount = computed(() => cities.value.filter(city => !city.unassigned).length)
const placeCount = computed(() => cities.value.reduce((sum, city) => sum + city.places.size, 0))
const photoCount = computed(() => cities.value.reduce((sum, city) => sum + city.photos.length, 0))

function markPhotoLoaded(key) {
  if (loadedPhotoKeys.value.has(key)) return
  loadedPhotoKeys.value = new Set(loadedPhotoKeys.value).add(key)
}

watch(activeCity, city => { if (city) activeCityKey.value = city.key }, { immediate: true })

onMounted(async () => {
  loading.value = true
  try {
    const response = await getFootprints()
    records.value = await enrichFootprintRegions(response.data || [])
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '旅行足迹加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.city-album-page { --ink:#293b49; --muted:#7c8b95; --line:#e3e9ec; --accent:#258b88; --accent-soft:#e9f4f2; min-height:calc(100vh - 52px); color:var(--ink); }
.page-header { display:flex; align-items:flex-end; justify-content:space-between; gap:18px; margin-bottom:18px; }
.page-header h1 { margin:0 0 6px; padding:0; border:0; color:var(--ink); font-size:24px; font-weight:720; line-height:1.25; }
.page-header h1::before { display:none; }
.page-header p { margin:0; color:var(--muted); font-size:13px; }
.album-summary { display:flex; flex-wrap:wrap; gap:17px; color:#74838a; font-size:12px; }
.album-summary strong { margin-right:4px; color:#315b5a; font-size:17px; font-weight:700; }
.album-toolbar { display:flex; align-items:center; gap:9px; margin-bottom:17px; padding:11px 12px; border:1px solid var(--line); border-radius:7px; background:#fff; }
.album-search { width:min(300px, 100%); }.year-select { width:140px; }
.album-layout { display:grid; grid-template-columns:250px minmax(0,1fr); gap:22px; align-items:start; }
.city-navigation { position:sticky; top:20px; max-height:calc(100vh - 40px); overflow:auto; padding:12px 9px; border:1px solid var(--line); border-radius:7px; background:#fff; scrollbar-width:thin; scrollbar-color:rgba(37,139,136,.38) transparent; }
.city-navigation::-webkit-scrollbar { width:6px; height:6px; }
.city-navigation::-webkit-scrollbar-track { background:transparent; }
.city-navigation::-webkit-scrollbar-thumb { border:1px solid #fff; border-radius:6px; background:rgba(37,139,136,.38); }
.city-navigation::-webkit-scrollbar-thumb:hover { background:rgba(37,139,136,.68); }
.city-navigation-heading { display:flex; align-items:center; justify-content:space-between; margin:2px 7px 9px; color:#86959b; font-size:11px; font-weight:650; }
.city-navigation-heading span { color:#9aabad; font-weight:500; }
.city-option { display:flex; width:100%; min-height:55px; align-items:center; gap:10px; padding:7px 8px; border:0; border-radius:5px; background:transparent; color:var(--ink); text-align:left; cursor:pointer; }
.city-option:hover { background:#f5f8f8; }.city-option.active { background:var(--accent-soft); color:#176e6c; }
.city-option-icon { display:grid; width:30px; height:30px; flex:0 0 auto; place-items:center; border:1px solid #dce9e8; border-radius:5px; background:#fff; color:var(--accent); }
.city-option-copy { display:block; min-width:0; flex:1; }.city-option-copy strong,.city-option-copy small { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.city-option-copy strong { font-size:12px; font-weight:650; }.city-option-copy small { margin-top:4px; color:#91a0a4; font-size:10px; }.city-option-arrow { color:#9aa9ad; font-size:13px; }
.gallery-content { min-width:0; }
.gallery-heading { display:flex; align-items:flex-end; justify-content:space-between; gap:14px; margin-bottom:14px; padding-bottom:12px; border-bottom:1px solid var(--line); }
.eyebrow { color:var(--accent); font-size:9px; font-weight:700; letter-spacing:1px; }.gallery-heading h2 { margin:4px 0 3px; color:#293b49; font-size:22px; font-weight:700; }.gallery-heading p { margin:0; color:#87969b; font-size:11px; }
.gallery-count { display:flex; flex-direction:column; align-items:flex-end; color:#8a999e; font-size:10px; }.gallery-count strong { color:#315b5a; font-size:18px; }
.photo-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:9px; }
.photo-tile { position:relative; min-width:0; overflow:hidden; aspect-ratio:1; border:1px solid #e2e9e9; border-radius:5px; background:#e8efee; }
.photo-tile :deep(.el-image) { display:block; width:100%; height:100%; cursor:zoom-in; }.photo-tile :deep(.el-image__inner) { opacity:0; transition:opacity .32s ease,transform .24s ease; }.photo-tile.is-loaded :deep(.el-image__inner) { opacity:1; }.photo-tile:hover :deep(.el-image__inner) { transform:scale(1.035); }
.photo-caption { position:absolute; inset:auto 0 0; display:flex; flex-direction:column; gap:3px; padding:25px 9px 8px; background:linear-gradient(transparent,rgba(9,23,25,.76)); color:#fff; pointer-events:none; }.photo-caption strong,.photo-caption span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.photo-caption strong { font-size:11px; font-weight:650; }.photo-caption span { color:rgba(255,255,255,.78); font-size:9px; }
.photo-error,.gallery-empty,.empty-state { display:flex; align-items:center; justify-content:center; flex-direction:column; color:#829397; }.photo-error { width:100%; height:100%; color:#91a6a6; }.gallery-empty { min-height:250px; gap:9px; border:1px dashed #cedbdb; border-radius:6px; background:#fbfcfc; font-size:12px; }.gallery-empty .el-icon,.empty-state .el-icon { color:#68a6a2; font-size:26px; }
.empty-state { min-height:330px; gap:9px; border:1px solid var(--line); border-radius:7px; background:#fff; text-align:center; }.empty-state strong { color:#40565b; font-size:14px; }.empty-state span { color:#87969b; font-size:11px; }
@media(max-width:1050px) { .photo-grid { grid-template-columns:repeat(3,minmax(0,1fr)); } }
@media(max-width:760px) {
  .city-album-page { min-height:calc(100vh - 90px); }
  .page-header { align-items:flex-start; flex-direction:column; gap:10px; }.album-summary { gap:13px; }
  .album-toolbar { align-items:stretch; }.album-search { min-width:0; flex:1; }.year-select { flex:0 0 124px; }
  .album-layout { grid-template-columns:minmax(0,1fr); gap:14px; }
  .city-navigation { position:static; display:flex; max-height:none; gap:5px; overflow-x:auto; padding:8px; scrollbar-width:thin; }.city-navigation::-webkit-scrollbar { height:4px; }.city-navigation-heading { display:none; }
  .city-option { width:auto; min-width:145px; flex:0 0 auto; }.city-option-arrow { display:none; }
  .gallery-heading { margin-bottom:10px; }.photo-grid { grid-template-columns:repeat(3,minmax(0,1fr)); gap:6px; }
}
@media(max-width:430px) { .photo-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.album-summary { width:100%; justify-content:space-between; }.album-toolbar { flex-wrap:wrap; }.album-search { flex-basis:100%; }.year-select { width:100%; flex-basis:100%; } }
@media(prefers-reduced-motion:reduce) { .photo-tile :deep(.el-image__inner) { transition:none; } }
</style>
