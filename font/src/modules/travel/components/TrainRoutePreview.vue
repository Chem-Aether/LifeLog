<template>
  <el-dialog :model-value="modelValue" title="选择乘坐区间" width="min(880px, 94vw)" destroy-on-close @close="close">
    <div ref="mapContainer" class="route-preview"></div>
    <div class="map-hint">拖动站点可微调位置，途经顺序可在下方调整</div>
    <el-alert v-if="orderedStations.length < 2" type="warning" :closable="false" title="铁路网未匹配到至少两个车站，无法按站点裁切。" />
    <div v-else class="range-form">
      <el-select v-model="startIndex" placeholder="乘车起点">
        <el-option v-for="(station, index) in orderedStations" :key="`start-${index}`" :label="station.stationName" :value="index" :disabled="index >= endIndex" />
      </el-select>
      <span>至</span>
      <el-select v-model="endIndex" placeholder="乘车终点">
        <el-option v-for="(station, index) in orderedStations" :key="`end-${index}`" :label="station.stationName" :value="index" :disabled="index <= startIndex" />
      </el-select>
      <small>识别到 {{ orderedStations.length }} 站，将保留 {{ selectedStations.length }} 个途经站。</small>
    </div>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :disabled="orderedStations.length < 2" @click="confirm">使用该区间</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import * as maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'

const props = defineProps({
  modelValue: Boolean,
  routeGeoJson: { type: String, default: '' },
  stations: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'confirm'])
const mapContainer = ref(null)
const startIndex = ref(0)
const endIndex = ref(1)
let map

const routeCoordinates = computed(() => {
  try {
    const geometry = JSON.parse(props.routeGeoJson)
    return geometry.type === 'LineString' ? geometry.coordinates : (geometry.coordinates || []).flat()
  } catch { return [] }
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
  .map(station => ({ ...station, projection: project([Number(station.longitude), Number(station.latitude)]) }))
  .filter(station => Number.isFinite(station.projection.position))
  .sort((a, b) => a.projection.position - b.projection.position))
const selectedStations = computed(() => orderedStations.value.slice(startIndex.value, endIndex.value + 1))
const croppedCoordinates = computed(() => {
  if (selectedStations.value.length < 2) return []
  const start = selectedStations.value[0].projection
  const end = selectedStations.value.at(-1).projection
  return [start.coordinate, ...routeCoordinates.value.slice(start.segment + 1, end.segment + 1), end.coordinate]
})
const lineFeature = coordinates => ({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates } })
const stationFeatures = computed(() => ({ type: 'FeatureCollection', features: orderedStations.value.map((station, index) => ({
  type: 'Feature', properties: { name: station.stationName, selected: index >= startIndex.value && index <= endIndex.value, index },
  geometry: { type: 'Point', coordinates: [station.longitude, station.latitude] }
})) }))
let dragStation = null

function renderSelection() {
  if (!map?.isStyleLoaded()) return
  map.getSource('selected')?.setData(lineFeature(croppedCoordinates.value))
  map.getSource('stations')?.setData(stationFeatures.value)
}
async function initialize() {
  await nextTick()
  map?.remove()
  map = new maplibregl.Map({ container: mapContainer.value, style: { version: 8, sources: {}, layers: [{ id: 'background', type: 'background', paint: { 'background-color': '#eef3f4' } }] }, center: [104, 35], zoom: 4, attributionControl: false })
  map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right')
  map.on('load', () => {
    map.addSource('full', { type: 'geojson', data: lineFeature(routeCoordinates.value) })
    map.addSource('selected', { type: 'geojson', data: lineFeature(croppedCoordinates.value) })
    map.addSource('stations', { type: 'geojson', data: stationFeatures.value })
    map.addLayer({ id: 'full', type: 'line', source: 'full', paint: { 'line-color': '#8b9ba0', 'line-width': 3 } })
    map.addLayer({ id: 'selected', type: 'line', source: 'selected', paint: { 'line-color': '#008d98', 'line-width': 6 } })
    map.addLayer({ id: 'stations', type: 'circle', source: 'stations', paint: { 'circle-radius': ['case', ['get', 'selected'], 6, 4], 'circle-color': ['case', ['get', 'selected'], '#e2583e', '#72848a'], 'circle-stroke-color': '#fff', 'circle-stroke-width': 2 } })
    map.addLayer({ id: 'station-labels', type: 'symbol', source: 'stations', layout: { 'text-field': ['get', 'name'], 'text-size': 11, 'text-offset': [0, 1.15], 'text-anchor': 'top', 'text-optional': true }, paint: { 'text-color': '#263a42', 'text-halo-color': '#eef3f4', 'text-halo-width': 1.5 } })
    map.on('mouseenter', 'stations', () => { map.getCanvas().style.cursor = 'move' })
    map.on('mouseleave', 'stations', () => { map.getCanvas().style.cursor = '' })
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
      dragStation = null
      map.getCanvas().style.cursor = ''
      map.dragPan.enable()
    })
    const bounds = new maplibregl.LngLatBounds()
    routeCoordinates.value.forEach(coordinate => bounds.extend(coordinate))
    if (!bounds.isEmpty()) map.fitBounds(bounds, { padding: 42, maxZoom: 12, duration: 0 })
  })
}
function close() { emit('update:modelValue', false) }
function confirm() {
  emit('confirm', {
    routeGeoJson: JSON.stringify({ type: 'LineString', coordinates: croppedCoordinates.value }),
    waypoints: selectedStations.value.map((station, index) => ({ stationName: station.stationName, longitude: station.longitude, latitude: station.latitude, arrivalTime: station.arrivalTime || null, departureTime: station.departureTime || null, sequence: index + 1 })),
    routeOrigin: orderedStations.value[0],
    routeTerminal: orderedStations.value.at(-1)
  })
}
watch(() => props.modelValue, visible => {
  if (!visible) { map?.remove(); map = null; return }
  startIndex.value = 0
  endIndex.value = Math.max(1, orderedStations.value.length - 1)
  initialize()
})
watch([startIndex, endIndex], renderSelection)
onBeforeUnmount(() => map?.remove())
</script>

<style scoped>
.route-preview { width: 100%; height: min(50vh, 440px); border: 1px solid #d6e0e2; }
.map-hint { margin: 5px 0 12px; color: #718187; font-size: 12px; }
.range-form { display: grid; grid-template-columns: minmax(150px, 1fr) auto minmax(150px, 1fr); align-items: center; gap: 12px; }
.range-form small { grid-column: 1 / -1; color: #718187; }
@media (max-width: 640px) { .route-preview { height: 330px; } .range-form { grid-template-columns: 1fr; } .range-form span { display: none; } }
</style>
