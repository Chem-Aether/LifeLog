<template>
  <main class="travel-board">
    <Map
      class="board-map"
      @select-route="showRouteDetail"
      @select-place="showPlaceDetail"
      @blank-click="closeDrawers"
      @footprint-summary="footprintSummary = $event"
      @footprint-timeline="setTimelineYears"
      :selected-year="selectedYear"
    />
    <header class="board-toolbar glass-surface">
      <div class="board-brand"><strong>旅行足迹</strong><span>个人出行地图</span></div>
      <el-checkbox-group v-model="visibleLayers" class="layer-switch" @change="changeLayers">
        <el-checkbox-button value="foot">足迹</el-checkbox-button>
        <el-checkbox-button value="flight">航线</el-checkbox-button>
        <el-checkbox-button value="train">铁路</el-checkbox-button>
      </el-checkbox-group>
    </header>
    <button
      v-if="!drawerOpen && hasRouteLayer"
      class="drawer-handle glass-surface"
      type="button"
      aria-label="展开行程列表"
      title="展开行程列表"
      @click="drawerOpen = true"
    ><span aria-hidden="true">›</span></button>
    <el-button class="admin-entry glass-surface" @click="goToAdmin">管理数据</el-button>
    <div v-if="visibleLayers.includes('foot')" class="foot-summary glass-surface">
      <div><strong>{{ footprintSummary.cities }}</strong><span>去过城市</span></div>
      <div><strong>{{ footprintSummary.transitCities }}</strong><span>途经城市</span></div>
      <div><strong>{{ footprintSummary.places }}</strong><span>去过地点</span></div>
    </div>
    <div v-if="timelineYears.length" class="timeline glass-surface">
      <button
        class="timeline-all"
        :class="{ active: selectedYear === 'all' }"
        :aria-pressed="selectedYear === 'all'"
        @click="selectedYear = 'all'"
      >全部</button>
      <div ref="yearTrack" class="year-track" @wheel="scrollYearTrack">
        <button
          v-for="year in timelineYears"
          :key="year"
          class="year-option"
          :class="{ active: selectedYear === year }"
          :aria-pressed="selectedYear === year"
          @click="selectedYear = year"
        >{{ year }}</button>
      </div>
    </div>
    <el-drawer
      v-model="drawerOpen"
      direction="ltr"
      :modal="true"
      :close-on-click-modal="true"
      :lock-scroll="false"
      modal-class="travel-drawer-overlay"
      :show-close="false"
      :with-header="false"
      size="min(430px, 92vw)"
      class="travel-data-drawer"
    >
      <TravelPanel :mode="activeMode" :route-selection="routeSelection" :selected-year="selectedYear" @close="drawerOpen = false" />
    </el-drawer>
    <el-drawer
      v-model="placeDrawerOpen"
      direction="rtl"
      :modal="true"
      :close-on-click-modal="true"
      :lock-scroll="false"
      modal-class="travel-drawer-overlay"
      :show-close="false"
      :with-header="false"
      size="min(380px, 92vw)"
      class="place-detail-drawer"
    >
      <PlaceDetailPanel :place="selectedPlace" @close="placeDrawerOpen = false" />
    </el-drawer>
  </main>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import Map from './Map.vue'
import TravelPanel from './TravelPanel.vue'
import PlaceDetailPanel from './PlaceDetailPanel.vue'
import { useTravleStore } from '@/modules/travel/stores/TravelStore.js'
import { storeToRefs } from 'pinia'

const router = useRouter()
const store = useTravleStore()
const { visibleLayers } = storeToRefs(store)
const initialRouteMode = visibleLayers.value.find(layer => ['flight', 'train'].includes(layer))
const activeMode = ref(['flight', 'train'].includes(store.mapType) && visibleLayers.value.includes(store.mapType)
  ? store.mapType
  : initialRouteMode || 'flight')
const previousLayers = ref([...visibleLayers.value])
const hasRouteLayer = computed(() => visibleLayers.value.some(layer => layer === 'flight' || layer === 'train'))
const drawerOpen = ref(false)
const routeSelection = ref(null)
const selectedPlace = ref(null)
const placeDrawerOpen = ref(false)
const footprintSummary = ref({ cities: 0, transitCities: 0, places: 0 })
const timelineYears = ref([])
const selectedYear = ref('all')
const yearTrack = ref(null)

function changeLayers(layers) {
  store.setVisibleLayers(layers)
  const addedRoute = layers.find(layer => ['flight', 'train'].includes(layer) && !previousLayers.value.includes(layer))
  if (addedRoute) {
    activeMode.value = addedRoute
    drawerOpen.value = true
  } else if (!layers.includes(activeMode.value)) {
    activeMode.value = ['flight', 'train'].find(layer => layers.includes(layer)) || 'flight'
    if (!hasRouteLayer.value) drawerOpen.value = false
  }
  store.setMapType(activeMode.value)
  store.mapName = activeMode.value === 'flight' ? '航空' : '铁路'
  previousLayers.value = [...layers]
  routeSelection.value = null
  placeDrawerOpen.value = false
}

function showPlaceDetail(place) {
  selectedPlace.value = place
  placeDrawerOpen.value = true
}

function closeDrawers() {
  drawerOpen.value = false
  placeDrawerOpen.value = false
  routeSelection.value = null
  selectedPlace.value = null
}

function setTimelineYears(years) {
  const currentYear = new Date().getFullYear()
  const oldestRecordedYear = years.length ? Math.min(...years) : currentYear
  const firstYear = Math.min(oldestRecordedYear, currentYear - 9)
  timelineYears.value = Array.from(
    { length: currentYear - firstYear + 1 },
    (_, index) => firstYear + index
  )
  if (selectedYear.value !== 'all' && !timelineYears.value.includes(selectedYear.value)) {
    selectedYear.value = 'all'
  }
  nextTick(() => {
    if (yearTrack.value) yearTrack.value.scrollLeft = yearTrack.value.scrollWidth
  })
}

function scrollYearTrack(event) {
  if (!yearTrack.value || Math.abs(event.deltaY) <= Math.abs(event.deltaX)) return
  event.preventDefault()
  yearTrack.value.scrollLeft += event.deltaY
}

function showRouteDetail(selection) {
  if (!selection?.type || selection.type === 'foot') return
  activeMode.value = selection.type
  if (!visibleLayers.value.includes(selection.type)) {
    store.setVisibleLayers([...visibleLayers.value, selection.type])
  }
  store.setMapType(selection.type)
  store.mapName = selection.type === 'flight' ? '航空' : '铁路'
  routeSelection.value = { ...selection, nonce: Date.now() }
  drawerOpen.value = true
}

function goToAdmin() {
  router.push('/travel/admin')
}
</script>

<style scoped>
.travel-board { position: fixed; inset: 0; overflow: hidden; background: #07101d; }
.board-map { position: absolute; inset: 0; }
.glass-surface { border: 1px solid rgba(157, 222, 231, .2); background: rgba(7, 18, 29, .7); box-shadow: 0 12px 32px rgba(0, 0, 0, .22); backdrop-filter: blur(18px) saturate(125%); }
.board-toolbar { position: absolute; z-index: 5; top: 18px; left: 50%; display: flex; align-items: center; gap: 26px; min-height: 54px; padding: 7px 18px; transform: translateX(-50%); border-radius: 6px; }
.board-brand { display: flex; flex-direction: column; color: #ecf8fa; }
.board-brand strong { font-size: 17px; letter-spacing: 0; }
.board-brand span { margin-top: 2px; color: #8cabb3; font-size: 11px; }
.layer-switch { display: flex; gap: 4px; }
.layer-switch :deep(.el-checkbox-button__inner) { min-width: 66px; border-color: rgba(120, 194, 207, .2); background: rgba(14, 36, 50, .68); color: #afc8ce; box-shadow: none; }
.layer-switch :deep(.el-checkbox-button:nth-child(1).is-checked .el-checkbox-button__inner) { border-color: #43d6a0; background: #167c67; color: #fff; box-shadow: none; }
.layer-switch :deep(.el-checkbox-button:nth-child(2).is-checked .el-checkbox-button__inner) { border-color: #f4bd62; background: #95641b; color: #fff; box-shadow: none; }
.layer-switch :deep(.el-checkbox-button:nth-child(3).is-checked .el-checkbox-button__inner) { border-color: #43c9de; background: #176d86; color: #fff; box-shadow: none; }
.drawer-handle { position: absolute; z-index: 5; top: 50%; left: 0; display: grid; width: 30px; height: 62px; padding: 0; transform: translateY(-50%); place-items: center; border-radius: 0 7px 7px 0; color: #d8edf1; cursor: pointer; }
.drawer-handle span { font-size: 27px; line-height: 1; }
.drawer-handle:hover { width: 36px; border-color: rgba(67, 201, 222, .55); color: #fff; }
.admin-entry { position: absolute; z-index: 5; top: 20px; right: 18px; height: 42px; border-radius: 5px; color: #d8edf1; }
.foot-summary { position: absolute; z-index: 5; bottom: 18px; left: 18px; display: grid; grid-template-columns: repeat(3, minmax(86px, 1fr)); padding: 10px 12px; border-radius: 6px; }
.foot-summary > div { min-width: 86px; padding: 3px 12px; border-right: 1px solid rgba(141, 205, 216, .16); }
.foot-summary > div:last-child { border-right: 0; }
.foot-summary strong, .foot-summary span { display: block; }
.foot-summary strong { color: #eefafb; font-size: 18px; }
.foot-summary span { margin-top: 2px; color: #819da5; font-size: 11px; }
.timeline { position: absolute; z-index: 5; bottom: 18px; left: 50%; display: grid; grid-template-columns: 62px minmax(0, 580px); align-items: center; width: min(680px, calc(100vw - 420px)); min-height: 52px; padding: 0 12px; transform: translateX(-50%); border-radius: 6px; }
.timeline button { height: 32px; border: 0; border-radius: 4px; background: transparent; color: #8eabb2; font: inherit; cursor: pointer; transition: background-color .18s, color .18s; }
.timeline button:hover { background: rgba(53, 176, 181, .12); color: #d7eff1; }
.timeline button.active { background: #168ba0; color: #fff; box-shadow: inset 0 0 0 1px rgba(111, 224, 229, .4); }
.timeline-all { width: 52px; font-size: 12px; }
.year-track { display: grid; grid-auto-flow: column; grid-auto-columns: 58px; overflow-x: auto; overscroll-behavior-inline: contain; scroll-behavior: smooth; scrollbar-width: thin; scrollbar-color: rgba(88, 179, 187, .45) transparent; }
.year-track::-webkit-scrollbar { height: 3px; }
.year-track::-webkit-scrollbar-thumb { border-radius: 2px; background: rgba(88, 179, 187, .45); }
.year-option { width: 58px; font-size: 12px; }
:global(.travel-data-drawer.el-drawer) { top: 90px; bottom: 18px; height: auto; margin-left: 18px; overflow: hidden; border: 1px solid rgba(131, 215, 226, .22); border-radius: 6px; background: rgba(5, 17, 28, .72); box-shadow: 18px 0 48px rgba(0, 0, 0, .3); backdrop-filter: blur(22px) saturate(130%); }
:global(.travel-data-drawer .el-drawer__body) { padding: 0; overflow: hidden; }
:global(.place-detail-drawer.el-drawer) { top: 90px; right: 18px; bottom: 18px; height: auto; overflow: hidden; border: 1px solid rgba(131, 215, 226, .22); border-radius: 6px; background: rgba(5, 17, 28, .76); box-shadow: -18px 0 48px rgba(0, 0, 0, .3); backdrop-filter: blur(22px) saturate(130%); }
:global(.place-detail-drawer .el-drawer__body) { padding: 0; overflow: hidden; }
:global(.travel-drawer-overlay) { background: transparent !important; }
@media (max-width: 900px) {
  .board-toolbar { top: 10px; left: 10px; right: 10px; gap: 8px; min-height: 48px; padding: 6px 8px; transform: none; }
  .board-brand { display: none; }
  .board-toolbar { justify-content: center; }
  .layer-switch :deep(.el-checkbox-button__inner) { min-width: 44px; padding: 8px 9px; }
  .admin-entry { top: 66px; right: 10px; height: 34px; }
  .foot-summary { bottom: 68px; left: 10px; grid-template-columns: repeat(3, minmax(72px, 1fr)); }
  .foot-summary > div { min-width: 0; padding: 3px 7px; }
  .timeline { right: 10px; bottom: 10px; left: 10px; grid-template-columns: 58px minmax(0, 1fr); width: auto; transform: none; }
  :global(.travel-data-drawer.el-drawer) { top: 112px; bottom: 10px; margin-left: 10px; }
  :global(.place-detail-drawer.el-drawer) { top: 112px; right: 10px; bottom: 10px; }
}
</style>
