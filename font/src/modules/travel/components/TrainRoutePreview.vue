<template>
  <section class="route-preview-panel">
    <div class="route-preview-toolbar">
      <div class="route-control-row">
        <slot name="route-action"></slot>
        <div v-if="modelValue && orderedStations.length >= 2" class="range-form">
          <el-select v-model="startIndex" placeholder="乘车起点">
            <el-option v-for="(station, index) in orderedStations" :key="`start-${index}`" :label="station.stationName" :value="index" :disabled="index >= endIndex" />
          </el-select>
          <span>至</span>
          <el-select v-model="endIndex" placeholder="乘车终点">
            <el-option v-for="(station, index) in orderedStations" :key="`end-${index}`" :label="station.stationName" :value="index" :disabled="index <= startIndex" />
          </el-select>
          <small>识别到 {{ orderedStations.length }} 站，当前保留 {{ selectedStations.length }} 站</small>
        </div>
      </div>
      <div v-if="modelValue && routeSegments.length" class="candidate-picker">
        <div class="candidate-heading">
          <strong>分段线路</strong>
          <span>逐段选择更符合实际乘车路线的铁路</span>
        </div>
        <div
          v-for="(segment, segmentIndex) in routeSegments"
          :key="`${segment.fromStation}-${segment.toStation}-${segmentIndex}`"
          class="segment-candidate-row"
          :class="{ active: activeSegmentIndex === segmentIndex }"
          @click="activeSegmentIndex = segmentIndex; renderSelection()"
        >
          <button class="segment-name" type="button" :aria-pressed="activeSegmentIndex === segmentIndex" @click.stop="activeSegmentIndex = segmentIndex; renderSelection()">
            <span class="segment-number">{{ String(segmentIndex + 1).padStart(2, '0') }}</span>
            <span class="segment-stations"><b>{{ segment.fromStation }}</b><small>至</small><b>{{ segment.toStation }}</b></span>
            <small v-if="activeSegmentIndex === segmentIndex" class="active-segment-label">地图调整中</small>
          </button>
          <div v-if="segment.candidates?.length" class="candidate-options">
            <button
              v-for="(candidate, index) in segment.candidates"
              :key="`${candidate.sourceLayer}-${index}`"
              type="button"
              class="candidate-choice"
              :class="{ selected: selectedCandidateIndices[segmentIndex] === index }"
              :aria-pressed="selectedCandidateIndices[segmentIndex] === index"
              @click.stop="selectCandidate(index, segmentIndex)"
            >
              <span class="choice-label">线路{{ chineseNumber(index + 1) }}</span>
              <span class="choice-distance">{{ candidate.distanceKm }} km</span>
              <span class="choice-lines" :title="candidateName(candidate)">
                {{ candidateName(candidate) }}
              </span>
            </button>
          </div>
          <div v-else class="no-candidate">未匹配到可用铁路路径</div>
        </div>
        <small class="candidate-hint">点击某段后，可在地图上点选对应候选线</small>
      </div>
      <div v-if="modelValue && orderedStations.length >= 2" class="preview-actions">
        <el-button @click="close">取消区间调整</el-button>
        <el-button type="primary" @click="confirm">使用该区间</el-button>
      </div>
    </div>
    <div v-if="!routeCoordinates.length" class="map-empty-state">轨迹尚未生成，使用上方“按车次获取”或“按途经站生成”加载线路</div>
    <el-alert v-else-if="modelValue && orderedStations.length < 2" class="route-alert" type="warning" :closable="false" title="铁路网未匹配到至少两个车站，无法按站点裁切。" />
    <div ref="mapContainer" class="route-preview"></div>
    <div v-if="modelValue && orderedStations.length >= 2" class="map-hint">点击高亮线路切换当前区间候选；拖动站点可微调位置，选择起终点可裁切乘坐区间</div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { maplibregl } from '@/utils/maplibre.js'
import 'maplibre-gl/dist/maplibre-gl.css'
import { MAP_BASE_URL } from '@/utils/request.js'

const props = defineProps({
  modelValue: Boolean,
  routeGeoJson: { type: String, default: '' },
  routeSegments: { type: Array, default: () => [] },
  stations: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'confirm', 'stations-change'])
const mapContainer = ref(null)
const startIndex = ref(0)
const endIndex = ref(1)
const activeSegmentIndex = ref(0)
const selectedCandidateIndices = ref([])
let map

const parseRouteLines = value => {
  try {
    const geometry = JSON.parse(value)
    return geometry.type === 'LineString' ? [geometry.coordinates] : geometry.type === 'MultiLineString' ? geometry.coordinates : []
  } catch { return [] }
}
const routeSegments = computed(() => props.routeSegments || [])
const chineseNumbers = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']
const chineseNumber = value => chineseNumbers[value - 1] || String(value)
const candidateName = candidate => {
  const names = [...new Set((candidate.railwayLines || [])
    .filter(name => name && !/^OSM way \d+$/i.test(name)))]
  return names.slice(0, 2).join(' / ') || '未命名铁路'
}
const activeSegment = computed(() => routeSegments.value[activeSegmentIndex.value])
const selectedCandidateIndex = computed(() => selectedCandidateIndices.value[activeSegmentIndex.value] ?? 0)
const routeCoordinates = computed(() => {
  const lines = routeSegments.value.length
    ? routeSegments.value.map((segment, index) => segment.candidates?.[selectedCandidateIndices.value[index] ?? segment.selectedCandidate ?? 0]?.coordinates || segment.defaultCoordinates || [])
    : parseRouteLines(props.routeGeoJson)
  return lines.flatMap(line => line || []).filter((coordinate, index, coordinates) =>
      index === 0 || coordinate[0] !== coordinates[index - 1][0] || coordinate[1] !== coordinates[index - 1][1]
    )
})

function project(point) {
  let best = { distance: Infinity, position: 0, coordinate: point }
  const coordinates = routeCoordinates.value
  for (let index = 0; index < coordinates.length - 1; index++) {
    const a = coordinates[index]
    const b = coordinates[index + 1]
    const dx = b[0] - a[0]
    const dy = b[1] - a[1]
    const length = dx * dx + dy * dy
    const ratio = length ? Math.max(0, Math.min(1, ((point[0] - a[0]) * dx + (point[1] - a[1]) * dy) / length)) : 0
    const coordinate = [a[0] + ratio * dx, a[1] + ratio * dy]
    const distance = (point[0] - coordinate[0]) ** 2 + (point[1] - coordinate[1]) ** 2
    if (distance < best.distance) best = { distance, position: index + ratio, segment: index, ratio, coordinate }
  }
  return best
}

const orderedStations = computed(() => props.stations
  .filter(station => Number.isFinite(Number(station.longitude)) && Number.isFinite(Number(station.latitude)) && routeCoordinates.value.length > 1)
  .map((station, sourceIndex) => ({ ...station, sourceIndex, projection: project([Number(station.longitude), Number(station.latitude)]) }))
  .filter(station => Number.isFinite(station.projection.position))
  .sort((a, b) => a.projection.position - b.projection.position))
const selectedStations = computed(() => orderedStations.value.slice(startIndex.value, endIndex.value + 1))
const croppedCoordinates = computed(() => {
  if (selectedStations.value.length < 2) return []
  const start = selectedStations.value[0].projection
  const end = selectedStations.value.at(-1).projection
  return [start.coordinate, ...routeCoordinates.value.slice(start.segment + 1, end.segment + 1), end.coordinate]
})
const croppedDistanceKm = computed(() => {
  const radians = degree => degree * Math.PI / 180
  return croppedCoordinates.value.slice(1).reduce((distance, point, index) => {
    const previous = croppedCoordinates.value[index]
    const latitudeDelta = radians(point[1] - previous[1])
    const longitudeDelta = radians(point[0] - previous[0])
    const haversine = Math.sin(latitudeDelta / 2) ** 2
      + Math.cos(radians(previous[1])) * Math.cos(radians(point[1])) * Math.sin(longitudeDelta / 2) ** 2
    return distance + 6371.0088 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))
  }, 0)
})
const selectedRouteCoordinates = computed(() => props.modelValue ? croppedCoordinates.value : routeCoordinates.value)
const lineCollection = coordinates => ({
  type: 'FeatureCollection',
  features: coordinates.length > 1 ? [{ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates } }] : []
})
const stationFeatures = () => ({ type: 'FeatureCollection', features: orderedStations.value.map((station, index) => ({
  type: 'Feature', properties: { name: station.stationName, selected: !props.modelValue || (index >= startIndex.value && index <= endIndex.value), index },
  geometry: { type: 'Point', coordinates: [station.longitude, station.latitude] }
})) })
const candidateFeatures = () => ({
  type: 'FeatureCollection',
  features: (activeSegment.value?.candidates || []).map((candidate, index) => ({
    type: 'Feature',
    properties: { candidateIndex: index, selected: index === selectedCandidateIndex.value },
    geometry: { type: 'LineString', coordinates: candidate.coordinates }
  }))
})
let dragStation = null

function baseLayers(source) {
  const textName = ['coalesce', ['get', 'name:zh-Hans'], ['get', 'name']]
  return [
    { id: 'route-earth', type: 'fill', source, 'source-layer': 'earth', paint: { 'fill-color': '#e8edf0' } },
    { id: 'route-landuse', type: 'fill', source, 'source-layer': 'landuse', minzoom: 3, paint: { 'fill-color': ['match', ['get', 'kind'], ['park', 'forest', 'nature_reserve'], '#cfe5d4', '#e2e8e5'], 'fill-opacity': 0.8 } },
    { id: 'route-water', type: 'fill', source, 'source-layer': 'water', paint: { 'fill-color': '#b7dceb' } },
    { id: 'route-boundaries', type: 'line', source, 'source-layer': 'boundaries', paint: { 'line-color': '#a9b9be', 'line-width': 0.65, 'line-opacity': 0.72 } },
    { id: 'route-railway-casing', type: 'line', source, 'source-layer': 'roads', minzoom: 4, filter: ['==', ['get', 'kind'], 'rail'], paint: { 'line-color': '#91a9ad', 'line-width': ['interpolate', ['linear'], ['zoom'], 5, 1.1, 14, 5.2], 'line-opacity': 0.7 } },
    { id: 'route-railway', type: 'line', source, 'source-layer': 'roads', minzoom: 4, filter: ['==', ['get', 'kind'], 'rail'], paint: { 'line-color': '#d4a35f', 'line-width': ['interpolate', ['linear'], ['zoom'], 5, 0.55, 14, 2.8], 'line-opacity': 0.92 } },
    { id: 'route-place-labels', type: 'symbol', source, 'source-layer': 'places', minzoom: 2, layout: { 'text-field': textName, 'text-size': ['interpolate', ['linear'], ['zoom'], 2, 10, 10, 14] }, paint: { 'text-color': '#263a42', 'text-halo-color': '#f4f7f7', 'text-halo-width': 1.4 } }
  ]
}

function createMapStyle() {
  return {
    version: 8,
    sources: { basemap: { type: 'vector', url: `${MAP_BASE_URL}/api/tiles/tilejson.json` } },
    layers: [
      { id: 'route-background', type: 'background', paint: { 'background-color': '#e8edf0' } },
      ...baseLayers('basemap')
    ]
  }
}

function renderSelection() {
  if (!map?.isStyleLoaded()) return
  map.getSource('full')?.setData(props.modelValue ? lineCollection(routeCoordinates.value) : lineCollection([]))
  map.getSource('selected')?.setData(lineCollection(selectedRouteCoordinates.value))
  map.getSource('stations')?.setData(stationFeatures())
  map.getSource('candidates')?.setData(candidateFeatures())
}
async function initialize() {
  await nextTick()
  map?.remove()
  map = new maplibregl.Map({ container: mapContainer.value, style: createMapStyle(), center: [104, 35], zoom: 4, attributionControl: false })
  map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right')
  map.on('load', () => {
    map.addSource('full', { type: 'geojson', data: props.modelValue ? lineCollection(routeCoordinates.value) : lineCollection([]) })
    map.addSource('selected', { type: 'geojson', data: lineCollection(selectedRouteCoordinates.value) })
    map.addSource('stations', { type: 'geojson', data: stationFeatures() })
    map.addSource('candidates', { type: 'geojson', data: candidateFeatures() })
    map.addLayer({ id: 'full', type: 'line', source: 'full', paint: { 'line-color': '#8b9ba0', 'line-width': 3 } })
    map.addLayer({ id: 'candidate-lines', type: 'line', source: 'candidates', paint: {
      'line-color': ['case', ['get', 'selected'], '#008d98', '#e3a14b'],
      'line-width': ['case', ['get', 'selected'], 8, 6],
      'line-opacity': ['case', ['get', 'selected'], 0.96, 0.78]
    } })
    map.addLayer({ id: 'selected', type: 'line', source: 'selected', paint: { 'line-color': '#008d98', 'line-width': 6 } })
    map.addLayer({ id: 'stations', type: 'circle', source: 'stations', paint: { 'circle-radius': ['case', ['get', 'selected'], 6, 4], 'circle-color': ['case', ['get', 'selected'], '#e2583e', '#72848a'], 'circle-stroke-color': '#fff', 'circle-stroke-width': 2 } })
    map.addLayer({ id: 'station-labels', type: 'symbol', source: 'stations', layout: { 'text-field': ['get', 'name'], 'text-size': 11, 'text-offset': [0, 1.15], 'text-anchor': 'top', 'text-optional': true }, paint: { 'text-color': '#263a42', 'text-halo-color': '#eef3f4', 'text-halo-width': 1.5 } })
    map.on('mouseenter', 'stations', () => { map.getCanvas().style.cursor = 'move' })
    map.on('mouseleave', 'stations', () => { map.getCanvas().style.cursor = '' })
    map.on('mouseenter', 'candidate-lines', () => { map.getCanvas().style.cursor = 'pointer' })
    map.on('mouseleave', 'candidate-lines', () => { map.getCanvas().style.cursor = '' })
    map.on('click', 'candidate-lines', event => {
      const index = Number(event.features?.[0]?.properties?.candidateIndex)
      if (Number.isInteger(index)) selectCandidate(index)
    })
    map.on('mousedown', 'stations', event => {
      if (event.originalEvent.button !== 0) return
      dragStation = orderedStations.value[event.features?.[0]?.properties?.index]
      if (!dragStation) return
      map.getCanvas().style.cursor = 'grabbing'
      map.dragPan.disable()
      event.preventDefault()
    })
    map.on('mousemove', event => {
      if (!dragStation) return
      dragStation.longitude = event.lngLat.lng
      dragStation.latitude = event.lngLat.lat
      renderSelection()
    })
    map.on('mouseup', () => {
      if (!dragStation) return
      const updatedStations = props.stations.map(station => ({ ...station }))
      orderedStations.value.forEach(station => {
        updatedStations[station.sourceIndex] = {
          ...updatedStations[station.sourceIndex],
          longitude: station.longitude,
          latitude: station.latitude
        }
      })
      emit('stations-change', updatedStations)
      dragStation = null
      map.getCanvas().style.cursor = ''
      map.dragPan.enable()
    })
    fitRoute()
  })
}
function fitRoute() {
  if (!map || routeCoordinates.value.length < 2) return
  const bounds = new maplibregl.LngLatBounds()
  routeCoordinates.value.forEach(coordinate => bounds.extend(coordinate))
  if (!bounds.isEmpty()) map.fitBounds(bounds, { padding: 42, maxZoom: 12, duration: 0 })
}
function close() { emit('update:modelValue', false) }
function selectCandidate(index, segmentIndex = activeSegmentIndex.value) {
  activeSegmentIndex.value = segmentIndex
  const next = [...selectedCandidateIndices.value]
  next[segmentIndex] = index
  selectedCandidateIndices.value = next
  renderSelection()
}
function confirm() {
  emit('confirm', {
    routeGeoJson: JSON.stringify({ type: 'LineString', coordinates: croppedCoordinates.value }),
    mileageKm: Math.round(croppedDistanceKm.value * 100) / 100,
    waypoints: selectedStations.value.map((station, index) => ({ stationName: station.stationName, longitude: station.longitude, latitude: station.latitude, arrivalTime: station.arrivalTime || null, departureTime: station.departureTime || null, sequence: index + 1 })),
    routeOrigin: orderedStations.value[0],
    routeTerminal: orderedStations.value.at(-1)
  })
}
watch(() => props.modelValue, async visible => {
  if (visible) {
    startIndex.value = 0
    endIndex.value = Math.max(1, orderedStations.value.length - 1)
  }
  await nextTick()
  renderSelection()
})
watch(() => props.routeSegments, segments => {
  selectedCandidateIndices.value = (segments || []).map(segment => segment.selectedCandidate ?? 0)
  activeSegmentIndex.value = Math.max(0, (segments || []).findIndex(segment => segment.candidates?.length > 1))
}, { deep: true, immediate: true })
watch([() => props.routeGeoJson, () => props.routeSegments], async () => {
  await nextTick()
  renderSelection()
  fitRoute()
})
watch(() => props.stations, async () => {
  await nextTick()
  renderSelection()
}, { deep: true })
watch([startIndex, endIndex], renderSelection)
onMounted(initialize)
onBeforeUnmount(() => map?.remove())
</script>

<style scoped>
.route-preview-panel { position:relative; margin-top: 14px; }
.route-preview-toolbar { display:grid; grid-template-columns:minmax(0,1fr); gap:12px; margin:12px 0; }
.route-control-row { display:grid; grid-template-columns:auto minmax(0,1fr); align-items:start; gap:10px; min-width:0; }
.route-control-row :deep(.generate-route-button) { min-height:40px; }
.route-control-row :deep(.range-form) { min-width:0; }
.candidate-picker { display:flex; flex-direction:column; gap:8px; max-height:250px; min-width:0; overflow-y:auto; padding:2px 7px 2px 0; scrollbar-width:thin; scrollbar-color:#b8c9cc transparent; }
.candidate-picker::-webkit-scrollbar { width:7px; }
.candidate-picker::-webkit-scrollbar-track { background:transparent; }
.candidate-picker::-webkit-scrollbar-thumb { border:2px solid transparent; border-radius:8px; background:#b8c9cc; background-clip:padding-box; }
.candidate-picker::-webkit-scrollbar-thumb:hover { background:#91a9ad; background-clip:padding-box; }
.candidate-heading { display:flex; align-items:baseline; flex-wrap:wrap; gap:10px; padding:0 2px; color:#31494e; }
.candidate-heading strong { font-size:14px; font-weight:650; }
.candidate-heading span, .candidate-hint { color:#718187; font-size:12px; }
.segment-candidate-row { display:grid; grid-template-columns:minmax(0, 1fr); gap:7px; min-width:0; padding:9px 10px; border:1px solid #e2e9eb; border-radius:5px; background:#fff; transition:border-color .16s ease, background-color .16s ease; }
.segment-candidate-row.active { border-color:#83c2bd; background:#f4faf9; }
.segment-name { display:flex; align-items:center; gap:9px; width:100%; min-width:0; padding:0; border:0; background:none; color:#31494e; text-align:left; cursor:pointer; }
.segment-number { display:grid; place-items:center; flex:0 0 27px; width:27px; height:24px; border-radius:4px; background:#eef2f3; color:#718187; font-size:11px; font-weight:650; }
.active .segment-number { background:#dcefed; color:#147d79; }
.segment-stations { display:flex; align-items:center; flex-wrap:wrap; gap:7px; min-width:0; font-size:13px; }
.segment-stations b { font-weight:620; }
.segment-stations small, .active-segment-label { color:#7c8c91; font-size:11px; white-space:nowrap; }
.active-segment-label { margin-left:auto; color:#147d79; }
.candidate-options { display:flex; align-items:stretch; flex-wrap:wrap; gap:7px; min-width:0; padding-left:36px; }
.candidate-choice { position:relative; display:grid; grid-template-columns:minmax(0,1fr) auto; align-items:center; column-gap:9px; row-gap:3px; min-height:38px; max-width:100%; min-width:180px; padding:6px 10px; border:1px solid #dbe4e6; border-radius:4px; background:#fff; color:#40565c; cursor:pointer; transition:border-color .16s ease, background-color .16s ease, color .16s ease; }
.candidate-choice:hover { border-color:#80b8b5; background:#f6fbfa; }
.candidate-choice.selected { border-color:#218d88; background:#eaf6f4; color:#126e6b; box-shadow:inset 0 0 0 1px #218d88; }
.choice-label { overflow:hidden; font-size:12px; font-weight:600; text-overflow:ellipsis; white-space:nowrap; }
.choice-distance { color:#718187; font-size:11px; white-space:nowrap; }
.choice-lines { grid-column:1 / -1; overflow:hidden; color:#718187; font-size:10px; text-align:left; text-overflow:ellipsis; white-space:nowrap; }
.candidate-choice.selected .choice-distance { color:#397c79; }
.no-candidate { padding:7px 10px 7px 36px; border-radius:4px; background:#f5f7f7; color:#718187; font-size:12px; }
.candidate-hint { padding:0 2px; }
.route-preview { width: 100%; height: 520px; border: 1px solid #d6e0e2; border-radius: 4px; background:#e8edf0; }
.map-hint { margin-top: 7px; color: #718187; font-size: 12px; }
.map-empty-state { position:absolute; z-index:1; top:50%; left:50%; width:max-content; max-width:calc(100% - 32px); padding:9px 12px; transform:translate(-50%,-50%); border:1px solid #d8e1e2; border-radius:4px; background:rgba(255,255,255,.94); color:#596c71; font-size:13px; text-align:center; pointer-events:none; }
.range-form { display:grid; grid-template-columns:minmax(0,1fr) auto minmax(0,1fr); align-items:center; gap:10px; min-width:0; }
.range-form :deep(.el-select) { width:100%; min-width:0; }
.range-form small { grid-column:1 / -1; color:#718187; white-space:normal; }
.preview-actions { display:flex; justify-content:flex-end; flex-wrap:wrap; gap:8px; min-width:0; }
.route-alert { margin-bottom: 10px; }
@media (max-width: 760px) {
  .route-control-row { grid-template-columns:minmax(0,1fr); }
  .route-control-row :deep(.generate-route-button) { justify-self:start; }
  .range-form { grid-template-columns:minmax(0,1fr) auto minmax(0,1fr); }
  .route-preview { height: 420px; }
  .candidate-picker { max-height:220px; }
  .candidate-options { padding-left:0; }
}
@media (max-width: 480px) { .range-form { grid-template-columns: 1fr; } .range-form span { display: none; } }
</style>
