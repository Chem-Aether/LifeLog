<template>
  <div class="picker-shell">
    <div class="poi-search">
      <el-select
        v-model="selectedPoi"
        filterable
        remote
        clearable
        reserve-keyword
        :remote-method="searchPois"
        :loading="poiLoading"
        placeholder="搜索全国景点或打卡点"
        @change="selectPoi"
      >
        <el-option
          v-for="item in poiOptions"
          :key="item.id"
          :label="item.name"
          :value="item.id"
        >
          <div class="poi-option">
            <span class="poi-main">
              <span class="poi-name">{{ item.name }}</span>
              <span v-if="item.kind" class="poi-kind">{{ item.kind }}</span>
            </span>
            <span class="poi-region" :title="item.fullName">{{ item.fullName || '行政区未知' }}</span>
          </div>
        </el-option>
      </el-select>
    </div>
    <div class="coordinate-search">
      <el-input v-model="longitudeInput" inputmode="decimal" placeholder="经度 -180 至 180" aria-label="经度" @keyup.enter="locateCoordinates">
        <template #prepend>经度</template>
      </el-input>
      <el-input v-model="latitudeInput" inputmode="decimal" placeholder="纬度 -90 至 90" aria-label="纬度" @keyup.enter="locateCoordinates">
        <template #prepend>纬度</template>
      </el-input>
      <el-button type="primary" @click="locateCoordinates">定位</el-button>
    </div>
    <div ref="mapContainer" class="picker-map"></div>
    <div class="picker-status">
      <span v-if="longitude != null && latitude != null">
        {{ Number(longitude).toFixed(6) }}, {{ Number(latitude).toFixed(6) }}
      </span>
      <span v-else>在地图上单击地点</span>
      <el-button v-if="longitude != null" link type="danger" @click="clearPoint">清除选点</el-button>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { forwardGeocode, reverseGeocode } from '@/modules/travel/apis/sysResource.js'
import { MAP_BASE_URL } from '@/utils/request.js'

const props = defineProps({
  longitude: { type: Number, default: null },
  latitude: { type: Number, default: null }
})
const emit = defineEmits(['pick', 'clear'])
const mapContainer = ref(null)
const selectedPoi = ref('')
const poiOptions = ref([])
const poiLoading = ref(false)
const longitudeInput = ref(props.longitude == null ? '' : String(props.longitude))
const latitudeInput = ref(props.latitude == null ? '' : String(props.latitude))
let map
let marker
let searchRequestId = 0

function vectorLayers(source, prefix) {
  const textName = ['coalesce', ['get', 'name:zh-Hans'], ['get', 'name']]
  return [
    { id: `${prefix}-earth`, type: 'fill', source, 'source-layer': 'earth', paint: { 'fill-color': '#e8edf0' } },
    { id: `${prefix}-landuse`, type: 'fill', source, 'source-layer': 'landuse', minzoom: 3, paint: { 'fill-color': ['match', ['get', 'kind'], ['park', 'forest', 'nature_reserve'], '#cfe5d4', '#e2e8e5'], 'fill-opacity': 0.8 } },
    { id: `${prefix}-water`, type: 'fill', source, 'source-layer': 'water', paint: { 'fill-color': '#b7dceb' } },
    { id: `${prefix}-boundaries`, type: 'line', source, 'source-layer': 'boundaries', paint: { 'line-color': '#82939d', 'line-width': 0.7 } },
    { id: `${prefix}-roads`, type: 'line', source, 'source-layer': 'roads', minzoom: 5, paint: { 'line-color': ['match', ['get', 'kind'], ['highway', 'major_road'], '#d3a85f', '#bbc3c5'], 'line-width': ['interpolate', ['linear'], ['zoom'], 5, 0.4, 14, 3.5] } },
    { id: `${prefix}-poi-labels`, type: 'symbol', source, 'source-layer': 'pois', minzoom: 5, layout: { 'text-field': textName, 'text-size': 11, 'text-offset': [0, 0.8], 'text-anchor': 'top', 'text-optional': true }, paint: { 'text-color': '#3f5158', 'text-halo-color': '#f4f7f7', 'text-halo-width': 1.2 } },
    { id: `${prefix}-road-labels`, type: 'symbol', source, 'source-layer': 'roads', minzoom: 11, layout: { 'symbol-placement': 'line', 'text-field': textName, 'text-size': 11 }, paint: { 'text-color': '#46565c', 'text-halo-color': '#f4f7f7', 'text-halo-width': 1.3 } },
    { id: `${prefix}-place-labels`, type: 'symbol', source, 'source-layer': 'places', minzoom: 2, layout: { 'text-field': textName, 'text-size': ['interpolate', ['linear'], ['zoom'], 2, 10, 10, 14] }, paint: { 'text-color': '#263a42', 'text-halo-color': '#f4f7f7', 'text-halo-width': 1.4 } }
  ]
}

function createStyle() {
  return {
    version: 8,
    sources: {
      basemap: { type: 'vector', url: `${MAP_BASE_URL}/api/tiles/tilejson.json` }
    },
    layers: [
      { id: 'picker-background', type: 'background', paint: { 'background-color': '#e8edf0' } },
      ...vectorLayers('basemap', 'picker-basemap')
    ]
  }
}

function setMarker(longitude, latitude, moveMap = false) {
  if (!map || longitude == null || latitude == null) return
  const coordinates = [Number(longitude), Number(latitude)]
  if (!marker) marker = new maplibregl.Marker({ color: '#d94343' })
  marker.setLngLat(coordinates).addTo(map)
  if (moveMap) map.flyTo({ center: coordinates, zoom: Math.max(map.getZoom(), 11) })
}

function setCoordinateInputs(longitude, latitude) {
  longitudeInput.value = String(longitude)
  latitudeInput.value = String(latitude)
}

async function locationAt(longitude, latitude) {
  const response = await reverseGeocode(longitude, latitude)
  const location = response.data
  return {
    adcode: location?.district?.code || '',
    districtName: location?.district?.name || '',
    fullName: location?.formattedRegion || ''
  }
}

async function handleMapClick(event) {
  const longitude = Number(event.lngLat.lng.toFixed(7))
  const latitude = Number(event.lngLat.lat.toFixed(7))
  setCoordinateInputs(longitude, latitude)
  setMarker(longitude, latitude)
  try {
    emit('pick', { longitude, latitude, ...await locationAt(longitude, latitude) })
  } catch {
    emit('pick', { longitude, latitude, adcode: '', districtName: '', fullName: '' })
  }
}

async function locateCoordinates() {
  const longitude = Number(longitudeInput.value)
  const latitude = Number(latitudeInput.value)
  if (!longitudeInput.value.trim() || !latitudeInput.value.trim()
      || !Number.isFinite(longitude) || !Number.isFinite(latitude)
      || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
    ElMessage.warning('请输入有效坐标：经度 -180 至 180，纬度 -90 至 90')
    return
  }
  if (!map) return
  const point = { longitude: Number(longitude.toFixed(7)), latitude: Number(latitude.toFixed(7)) }
  setCoordinateInputs(point.longitude, point.latitude)
  setMarker(point.longitude, point.latitude, true)
  try {
    emit('pick', { ...point, ...await locationAt(point.longitude, point.latitude) })
  } catch {
    emit('pick', { ...point, adcode: '', districtName: '', fullName: '' })
  }
}

async function searchPois(keyword) {
  const text = keyword?.trim()
  if (!text) {
    poiOptions.value = []
    return
  }
  const requestId = ++searchRequestId
  poiLoading.value = true
  try {
    const response = await forwardGeocode(text, 'poi', 20)
    if (requestId !== searchRequestId) return
    poiOptions.value = (response.data || []).map(item => ({
      id: item.id,
      name: item.name,
      kind: item.subcategory || item.category || '',
      coordinates: [item.longitude, item.latitude],
      adcode: item.region?.district?.code || '',
      districtName: item.region?.district?.name || '',
      fullName: item.region?.formattedRegion || ''
    }))
  } catch {
    if (requestId === searchRequestId) poiOptions.value = []
  } finally {
    if (requestId === searchRequestId) poiLoading.value = false
  }
}

function selectPoi(id) {
  const poi = poiOptions.value.find(item => item.id === id)
  if (!poi) return
  const [longitude, latitude] = poi.coordinates.map(Number)
  setCoordinateInputs(Number(longitude.toFixed(7)), Number(latitude.toFixed(7)))
  map.flyTo({ center: [longitude, latitude], zoom: Math.max(map.getZoom(), 13) })
  setMarker(longitude, latitude)
  emit('pick', {
    longitude: Number(longitude.toFixed(7)),
    latitude: Number(latitude.toFixed(7)),
    adcode: poi.adcode,
    districtName: poi.districtName,
    fullName: poi.fullName,
    suggestedName: poi.name
  })
}

function clearPoint() {
  marker?.remove()
  marker = null
  emit('clear')
}

onMounted(async () => {
  await nextTick()
  const hasPoint = props.longitude != null && props.latitude != null
  map = new maplibregl.Map({
    container: mapContainer.value,
    style: createStyle(),
    center: hasPoint ? [props.longitude, props.latitude] : [104, 35],
    zoom: hasPoint ? 11 : 5.2,
    minZoom: 1,
    maxZoom: 16,
    attributionControl: false,
    localIdeographFontFamily: 'Microsoft YaHei, sans-serif'
  })
  map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right')
  map.on('load', () => {
    if (hasPoint) setMarker(props.longitude, props.latitude)
  })
  map.on('click', handleMapClick)
})

watch(() => [props.longitude, props.latitude], ([longitude, latitude]) => {
  if (longitude == null || latitude == null) {
    longitudeInput.value = ''
    latitudeInput.value = ''
    marker?.remove()
    marker = null
    return
  }
  setCoordinateInputs(longitude, latitude)
  setMarker(longitude, latitude)
})

onBeforeUnmount(() => {
  marker?.remove()
  map?.remove()
})
</script>

<style scoped>
.picker-shell { border: 1px solid #d7dde5; background: #f6f8fa; }
.poi-search { padding: 10px; border-bottom: 1px solid #d7dde5; background: #fff; }
.poi-search :deep(.el-select) { width: 100%; }
.coordinate-search { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto; gap: 8px; padding: 10px; border-bottom: 1px solid #d7dde5; background: #fff; }
.poi-option { width: 100%; display: flex; align-items: center; gap: 16px; }
.poi-main { min-width: 0; display: flex; align-items: baseline; gap: 8px; }
.poi-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.poi-kind { flex: none; color: #98a2b3; font-size: 12px; }
.poi-region { min-width: 130px; margin-left: auto; overflow: hidden; color: #667085; font-size: 12px; text-align: right; text-overflow: ellipsis; white-space: nowrap; }
.picker-map { width: 100%; height: 330px; }
.picker-status { min-height: 38px; padding: 0 12px; display: flex; align-items: center; justify-content: space-between; color: #52606d; font-size: 13px; font-variant-numeric: tabular-nums; }
:deep(.maplibregl-ctrl-group) { border-radius: 4px; }
@media (max-width: 560px) { .coordinate-search { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); } .coordinate-search :deep(.el-button) { grid-column: 1 / -1; } }
</style>
