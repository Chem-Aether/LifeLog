<template>
  <div v-if="editing" v-loading="loading || saving" class="trajectory-editor">
    <header class="editor-header"><div><el-button text @click="requestClose">返回轨迹管理</el-button><h1>{{ form.trajectoryId ? '编辑轨迹' : '新增轨迹' }}</h1></div><div class="header-actions"><el-button :disabled="form.points.length < 2" @click="exportGpxRecords([form], `${safeFileName(form.name || 'GPS轨迹')}.gpx`)">导出 GPX</el-button><el-button @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存轨迹</el-button></div></header>
    <section class="editor-fields">
      <el-form label-position="top"><div class="metadata-grid">
        <el-form-item label="轨迹名称" required><el-input v-model="form.name" maxlength="200" placeholder="例如：周末骑行" /></el-form-item>
        <el-form-item label="活动类型"><el-select v-model="form.activityType" style="width:100%"><el-option v-for="item in activityOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="开始时间" required><el-date-picker v-model="form.startedAt" type="datetime" format="YYYY/M/D HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" editable :clearable="false" style="width:100%" @change="shiftTrajectoryTimes" /><small class="time-adjust-hint">修改开始时间会同步平移结束时间和全部采样点，点间隔保持不变</small></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="form.endedAt" type="datetime" format="YYYY/M/D HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" editable clearable style="width:100%" /></el-form-item>
      </div><el-form-item label="备注"><el-input v-model="form.note" type="textarea" :rows="2" maxlength="1000" show-word-limit /></el-form-item></el-form>
    </section>
    <section class="point-workspace"><div class="map-section"><div class="map-heading"><div><strong>轨迹地图</strong><span>{{ form.points.length }} 个采样点 · {{ distanceLabel }}</span></div><div class="map-actions"><input ref="gpxInput" class="gpx-input" type="file" accept=".gpx,application/gpx+xml,application/xml,text/xml" @change="importGpx"><el-button type="primary" :disabled="saving" @click="gpxInput?.click()">导入 GPX</el-button></div></div><div ref="mapElement" class="editor-map"></div><p class="map-hint">地图仅用于查看轨迹；点击已有点位可编辑信息。</p></div>
      <section class="points-section"><div class="points-heading"><div><strong>采样点信息</strong><span v-if="selectedPoint">第 {{ selectedPoint.sequence }} / {{ form.points.length }} 点 · 分段 {{ selectedPoint.segmentNo }}</span><span v-else>点击地图上的点位进行编辑</span></div><div v-if="selectedPoint" class="point-navigation"><el-button :disabled="selectedPoint.sequence <= 1" @click="selectPointOffset(-1)">上一点</el-button><el-button :disabled="selectedPoint.sequence >= form.points.length" @click="selectPointOffset(1)">下一点</el-button></div></div>
        <el-form v-if="selectedPoint" label-position="top" class="point-fields"><el-form-item label="采集时间"><el-date-picker v-model="selectedPoint.recordedAt" type="datetime" format="YYYY/M/D HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" editable style="width:100%" /></el-form-item><el-form-item label="经度"><el-input-number v-model="selectedPoint.longitude" :precision="6" :step="0.0001" controls-position="right" /></el-form-item><el-form-item label="纬度"><el-input-number v-model="selectedPoint.latitude" :precision="6" :step="0.0001" controls-position="right" /></el-form-item><el-form-item label="海拔 (m)"><el-input-number v-model="selectedPoint.altitudeM" :precision="1" :step="1" controls-position="right" /></el-form-item><el-form-item label="精度 (m)"><el-input-number v-model="selectedPoint.accuracyM" :precision="1" :min="0" controls-position="right" /></el-form-item><el-form-item label="速度 (m/s)"><el-input-number v-model="selectedPoint.speedMps" :precision="2" :min="0" controls-position="right" /></el-form-item></el-form>
        <el-empty v-else :description="form.points.length ? '请点击地图中的采样点' : '请先导入 GPX 轨迹'" :image-size="56" />
      </section></section>
  </div>
  <div v-else v-loading="loading || deleting || importing || exporting" class="trajectory-admin">
    <div class="page-header"><div><h1>GPS 轨迹管理</h1><p>管理运动、步行与日常出行轨迹，并在地图中查看路线。</p></div></div>
    <el-card class="query-card" shadow="never"><div class="query-filters"><el-input v-model="keyword" clearable placeholder="搜索轨迹名称" class="keyword-filter" /><el-select v-model="activityType" clearable placeholder="全部活动" class="activity-filter"><el-option v-for="item in activityOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select><el-date-picker v-model="dateFilter" type="date" format="YYYY/M/D" value-format="YYYY-MM-DD" editable clearable placeholder="按开始日期筛选" class="date-filter" /><el-button class="filter-refresh" :disabled="loading" @click="loadRows">刷新</el-button><span class="count">共 {{ total }} 条</span></div></el-card>
    <div class="action-bar"><input ref="batchGpxInput" class="gpx-input" type="file" accept=".gpx,application/gpx+xml,application/xml,text/xml" multiple @change="importGpxBatch"><el-button :loading="importing" @click="batchGpxInput?.click()">导入 GPX</el-button><el-button type="primary" @click="openCreate">新增轨迹</el-button><el-button :disabled="!selected.length || exporting" :loading="exporting" @click="exportSelected">导出所选</el-button><el-button :disabled="!rows.length || exporting" @click="exportCurrentPage">导出本页</el-button></div>
    <div class="bulk-toolbar"><span>已选择 {{ selected.length }} 条（当前页）</span><el-button type="danger" plain :disabled="!selected.length" :loading="deleting" @click="removeSelected">批量删除</el-button></div>
    <el-table :data="rows" row-key="trajectoryId" :default-sort="{ prop: 'startedAt', order: 'descending' }" stripe empty-text="暂无轨迹记录" @sort-change="handleSortChange" @selection-change="selected = $event">
      <el-table-column type="selection" width="48" /><el-table-column prop="name" label="轨迹名称" min-width="190" sortable="custom" /><el-table-column prop="activityType" label="活动" width="110" sortable="custom"><template #default="scope">{{ activityLabel(scope.row.activityType) }}</template></el-table-column>
      <el-table-column prop="startedAt" label="开始时间" min-width="190" sortable="custom"><template #default="scope">{{ formatDate(scope.row.startedAt) }}</template></el-table-column><el-table-column prop="durationS" label="时长" width="110" sortable="custom"><template #default="scope">{{ formatDuration(scope.row.durationS) }}</template></el-table-column>
      <el-table-column prop="distanceM" label="距离" width="120" sortable="custom"><template #default="scope">{{ formatDistance(scope.row.distanceM) }}</template></el-table-column><el-table-column prop="pointCount" label="采样点" width="100" sortable="custom" />
      <el-table-column label="操作" width="150" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button><el-button link type="danger" @click="removeOne(scope.row)">删除</el-button></template></el-table-column>
    </el-table>
    <div class="pagination-bar"><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10,20,50]" :total="total" layout="total, sizes, prev, pager, next, jumper" background @current-change="loadRows" @size-change="changePageSize" /></div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { addTrajectory, deleteTrajectories, getTrajectories, getTrajectoryById, updateTrajectory } from '@/modules/travel/apis/travel.js'
import { MAP_BASE_URL } from '@/utils/request.js'

const activityOptions = [{ label: '步行', value: 'walk' }, { label: '骑行', value: 'bike' }, { label: '驾车', value: 'drive' }, { label: '公共交通', value: 'transit' }, { label: '火车', value: 'train' }, { label: '飞机', value: 'plane' }, { label: '船舶', value: 'ship' }, { label: '其他', value: 'other' }]
const route = useRoute()
const router = useRouter()
const editing = computed(() => route.name === 'TravelTrajectoryEdit')
const rows = ref([]), selected = ref([]), total = ref(0), page = ref(1), pageSize = ref(10)
const keyword = ref(''), activityType = ref(''), dateFilter = ref(''), loading = ref(false), saving = ref(false), deleting = ref(false)
const sortState = ref({ prop: 'startedAt', order: 'desc' })
const mapElement = ref(null)
const gpxInput = ref(null)
const batchGpxInput = ref(null)
const selectedPointSequence = ref(null)
const form = reactive(emptyForm())
let map, routeSnapshot = '', allowNavigation = false, filterTimer, lastStartTime = ''
const importing = ref(false)
const exporting = ref(false)
const selectedPoint = computed(() => form.points.find(point => point.sequence === selectedPointSequence.value) || null)
const distanceLabel = computed(() => formatDistance(calculateDistance(form.points)))

watch([keyword, activityType, dateFilter], () => { page.value = 1; clearTimeout(filterTimer); filterTimer = setTimeout(loadRows, 250) })
watch(() => route.fullPath, initializePage)
watch(() => form.points.map(point => `${point.longitude},${point.latitude},${point.altitudeM}`).join('|'), drawRoute)
watch(selectedPointSequence, drawRoute)
onMounted(initializePage)
onBeforeUnmount(() => { clearTimeout(filterTimer); map?.remove() })

function emptyForm() { return { trajectoryId: null, name: '', activityType: 'walk', startedAt: localDateTime(new Date()), endedAt: '', note: '', points: [] } }

async function initializePage() {
  if (!editing.value) { map?.remove(); map = null; await loadRows(); return }
  loading.value = true
  try {
    if (route.params.id) {
      const response = await getTrajectoryById(route.params.id)
      Object.assign(form, response.data, { startedAt: localDateTime(response.data.startedAt), endedAt: localDateTime(response.data.endedAt), points: (response.data.points || []).map(point => ({ ...point, recordedAt: localDateTime(point.recordedAt) })) })
      if (!response.data.points?.length) throw new Error('轨迹采样点为空')
    } else Object.assign(form, emptyForm())
    lastStartTime = form.startedAt
    selectedPointSequence.value = form.points[0]?.sequence ?? null
    await nextTick()
    initializeMap()
    routeSnapshot = snapshot()
  } catch {
    ElMessage.error('轨迹详情加载失败')
    allowNavigation = true
    router.replace('/travel/admin/trajectory')
  } finally { loading.value = false }
}

async function loadRows() {
  loading.value = true
  try { const response = await getTrajectories({ page: page.value, pageSize: pageSize.value, keyword: keyword.value || undefined, activityType: activityType.value || undefined, date: dateFilter.value || undefined, sortBy: sortState.value.prop, sortOrder: sortState.value.order }); rows.value = response.data.records || []; total.value = response.data.total || 0 }
  catch { ElMessage.error('轨迹列表加载失败') }
  finally { loading.value = false }
}

function initializeMap() {
  map?.remove()
  map = new maplibregl.Map({ container: mapElement.value, style: editorMapStyle(), center: [104, 35], zoom: 4, localIdeographFontFamily: 'Microsoft YaHei, sans-serif', attributionControl: false })
  map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right')
  map.on('load', () => {
    map.addSource('trajectory-edit', { type: 'geojson', data: routeFeatureCollection() })
    map.addLayer({ id: 'trajectory-edit-line', type: 'line', source: 'trajectory-edit', paint: { 'line-color': '#ff5b7d', 'line-width': 2.8, 'line-opacity': 0.98 } })
    map.addLayer({ id: 'trajectory-edit-points', type: 'circle', source: 'trajectory-edit', filter: ['==', ['geometry-type'], 'Point'], paint: { 'circle-radius': ['case', ['==', ['get', 'sequence'], selectedPointSequence.value ?? -1], 7, 3.5], 'circle-color': ['case', ['==', ['get', 'sequence'], selectedPointSequence.value ?? -1], '#ff5b7d', '#f29a78'], 'circle-stroke-color': '#fff', 'circle-stroke-width': 1.5 } })
    drawRoute(); fitRoute()
  })
  map.on('click', event => {
    const hit = map.queryRenderedFeatures(event.point, { layers: ['trajectory-edit-points'] })[0]
    if (hit) selectedPointSequence.value = Number(hit.properties.sequence)
  })
  map.on('mousemove', event => { map.getCanvas().style.cursor = map.queryRenderedFeatures(event.point, { layers: ['trajectory-edit-points'] }).length ? 'pointer' : '' })
}

function routeFeatureCollection() {
  const points = form.points
  const groups = new Map()
  points.forEach(point => {
    const key = point.segmentNo || 1
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(point)
  })
  const features = []
  const step = Math.max(1, Math.ceil(points.length / 12000))
  for (const [segmentNo, segment] of groups) {
    const coordinates = segment.filter((_, index) => index === 0 || index === segment.length - 1 || index % step === 0).map(point => [Number(point.longitude), Number(point.latitude), Number(point.altitudeM || 0)]).filter(point => point.every(Number.isFinite))
    if (coordinates.length >= 2) features.push({ type: 'Feature', properties: { segmentNo }, geometry: { type: 'LineString', coordinates } })
  }
  const markerStep = Math.max(1, Math.ceil(points.length / 2500))
  points.forEach((point, index) => {
    if (index % markerStep !== 0 && index !== points.length - 1 && point.sequence !== selectedPointSequence.value) return
    features.push({ type: 'Feature', properties: { sequence: point.sequence }, geometry: { type: 'Point', coordinates: [Number(point.longitude), Number(point.latitude)] } })
  })
  return { type: 'FeatureCollection', features }
}

function editorMapStyle() {
  const source = 'basemap'
  const label = ['coalesce', ['get', 'name:zh-Hans'], ['get', 'name']]
  return {
    version: 8,
    sources: { [source]: { type: 'vector', url: `${MAP_BASE_URL}/api/tiles/tilejson.json`, attribution: '© OpenStreetMap contributors' } },
    layers: [
      { id: 'background', type: 'background', paint: { 'background-color': '#e8eff0' } },
      { id: 'editor-land', type: 'fill', source, 'source-layer': 'earth', paint: { 'fill-color': '#e8eff0' } },
      { id: 'editor-landuse', type: 'fill', source, 'source-layer': 'landuse', minzoom: 4, paint: { 'fill-color': ['match', ['get', 'kind'], ['park', 'forest', 'nature_reserve'], '#d4e6d6', '#f0f2ed'], 'fill-opacity': 0.8 } },
      { id: 'editor-water', type: 'fill', source, 'source-layer': 'water', paint: { 'fill-color': '#b9dce7' } },
      { id: 'editor-boundaries', type: 'line', source, 'source-layer': 'boundaries', paint: { 'line-color': '#aebfc3', 'line-width': ['interpolate', ['linear'], ['zoom'], 3, 0.4, 10, 1], 'line-opacity': 0.72 } },
      { id: 'editor-roads-casing', type: 'line', source, 'source-layer': 'roads', minzoom: 9, filter: ['!=', ['get', 'kind'], 'rail'], paint: { 'line-color': '#fff', 'line-width': ['interpolate', ['linear'], ['zoom'], 9, 0.45, 14, 3.2], 'line-opacity': 0.72 } },
      { id: 'editor-roads', type: 'line', source, 'source-layer': 'roads', minzoom: 9, filter: ['!=', ['get', 'kind'], 'rail'], paint: { 'line-color': ['match', ['get', 'kind'], ['highway', 'major_road'], '#eee1c9', '#e1e7e8'], 'line-width': ['interpolate', ['linear'], ['zoom'], 9, 0.25, 14, 2.1], 'line-opacity': 0.68 } },
      { id: 'editor-buildings', type: 'fill', source, 'source-layer': 'buildings', minzoom: 12, paint: { 'fill-color': '#d4dcdd', 'fill-outline-color': '#bcc9ca' } },
      { id: 'editor-road-labels', type: 'symbol', source, 'source-layer': 'roads', minzoom: 11, layout: { 'symbol-placement': 'line', 'text-field': label, 'text-size': 10 }, paint: { 'text-color': '#617278', 'text-halo-color': '#f7f9f8', 'text-halo-width': 1.3 } },
      { id: 'editor-place-labels-overview', type: 'symbol', source, 'source-layer': 'places', minzoom: 3, maxzoom: 8, filter: ['<', ['get', 'min_zoom'], 8], layout: { 'text-field': label, 'text-size': ['interpolate', ['linear'], ['zoom'], 3, 10, 8, 13], 'text-optional': true }, paint: { 'text-color': '#53686d', 'text-halo-color': '#f7f9f8', 'text-halo-width': 1.5 } },
      { id: 'editor-place-labels-city', type: 'symbol', source, 'source-layer': 'places', minzoom: 8, filter: ['>=', ['get', 'min_zoom'], 8], layout: { 'text-field': label, 'text-size': ['interpolate', ['linear'], ['zoom'], 8, 10, 13, 13], 'text-optional': true }, paint: { 'text-color': '#53686d', 'text-halo-color': '#f7f9f8', 'text-halo-width': 1.5 } }
    ]
  }
}

async function importGpx(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  try {
    const parsed = await parseGpxFile(file)
    form.points = parsed.points
    selectedPointSequence.value = parsed.points[0]?.sequence ?? null
    if (!form.name.trim()) form.name = parsed.name || file.name.replace(/\.gpx$/i, '')
    if (!form.note.trim()) form.note = parsed.note
    if (parsed.activityType) form.activityType = parsed.activityType
    form.startedAt = parsed.points[0].recordedAt
    form.endedAt = parsed.points.at(-1).recordedAt
    lastStartTime = form.startedAt
    await nextTick()
    drawRoute()
    fitRoute()
    ElMessage.success(`已导入 ${parsed.points.length.toLocaleString()} 个采样点${parsed.hasMissingValues ? '；缺失时间或海拔的点已按间隔/0 米补齐' : ''}`)
  } catch (error) { ElMessage.error(error.message || 'GPX 导入失败') }
}

async function importGpxBatch(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return
  importing.value = true
  let succeeded = 0
  let totalItems = 0
  const failures = []
  try {
    for (const file of files) {
      let parsedTracks
      try {
        parsedTracks = await parseGpxFile(file, true)
        totalItems += parsedTracks.length
      } catch (error) {
        totalItems++
        failures.push(`${file.name}：${error.message || '导入失败'}`)
        continue
      }
      for (const parsed of parsedTracks) {
        try {
          const payload = {
            name: parsed.name || file.name.replace(/\.gpx$/i, ''),
            activityType: parsed.activityType || 'other',
            note: parsed.note.slice(0, 1000),
            startedAt: toIso(parsed.points[0].recordedAt),
            endedAt: toIso(parsed.points.at(-1).recordedAt),
            points: parsed.points.map(point => ({ ...point, recordedAt: toIso(point.recordedAt) }))
          }
          await addTrajectory(payload)
          succeeded++
        } catch (error) {
          failures.push(`${file.name} / ${parsed.name}：${error.message || '导入失败'}`)
        }
      }
    }
    await loadRows()
    if (failures.length) ElMessage.warning(`成功导入 ${succeeded}/${totalItems} 条轨迹。失败：${failures.join('；')}`)
    else ElMessage.success(`已分别导入 ${succeeded} 条轨迹`)
  } finally { importing.value = false }
}

async function parseGpxFile(file, separateTracks = false) {
  if (file.size > 50 * 1024 * 1024) throw new Error('GPX 文件不能超过 50 MB')
  const documentXml = new DOMParser().parseFromString(await file.text(), 'application/xml')
  if (documentXml.querySelector('parsererror') || documentXml.documentElement.localName !== 'gpx') throw new Error('GPX 文件格式无效')
  const tracks = Array.from(documentXml.getElementsByTagNameNS('*', 'trk'))
  if (separateTracks) return tracks.length > 1
    ? tracks.map((track, index) => parseGpxTrack(documentXml, file, [track], index, tracks.length))
    : [parseGpxTrack(documentXml, file, tracks, 0, tracks.length)]
  return parseGpxTrack(documentXml, file, tracks, 0, tracks.length)
}

function parseGpxTrack(documentXml, file, tracks, trackIndex, trackCount) {
  const segmentNodes = tracks.flatMap(track => Array.from(track.children).filter(node => node.localName === 'trkseg')
    .map(node => Array.from(node.children).filter(child => child.localName === 'trkpt')))
    .filter(segment => segment.length >= 2)
  if (!segmentNodes.length && tracks.length === 0) {
    const routePoints = Array.from(documentXml.getElementsByTagNameNS('*', 'rtept'))
    if (routePoints.length) segmentNodes.push(routePoints)
  }
  const rawNodes = segmentNodes.flat()
  if (rawNodes.length < 2 || rawNodes.length > 200000) throw new Error('GPX 轨迹点数量须为 2 到 200,000')
  const parsedTimes = rawNodes.map(node => new Date(node.getElementsByTagNameNS('*', 'time')[0]?.textContent || ''))
  const firstTimedIndex = parsedTimes.findIndex(date => Number.isFinite(date.getTime()))
  const firstTimedValue = firstTimedIndex < 0 ? Date.now() : parsedTimes[firstTimedIndex].getTime()
  let lastTime = new Date(firstTimedValue - 5000 * (firstTimedIndex + 1))
  const points = []
  let rawIndex = 0
  segmentNodes.forEach((nodes, segmentIndex) => nodes.forEach(node => {
    const longitude = Number(node.getAttribute('lon')), latitude = Number(node.getAttribute('lat'))
    const parsedTime = parsedTimes[rawIndex++]
    if (!Number.isFinite(longitude) || !Number.isFinite(latitude) || Math.abs(longitude) > 180 || Math.abs(latitude) > 90) return
    const elevation = Number(node.getElementsByTagNameNS('*', 'ele')[0]?.textContent)
    const recorded = Number.isFinite(parsedTime.getTime()) && parsedTime >= lastTime ? parsedTime : new Date(lastTime.getTime() + 5000)
    lastTime = recorded
    points.push({ sequence: points.length + 1, segmentNo: segmentIndex + 1, recordedAt: localDateTime(recorded), longitude, latitude, altitudeM: Number.isFinite(elevation) ? elevation : 0, accuracyM: null, speedMps: null })
  }))
  if (points.length < 2) throw new Error('GPX 中没有可用的轨迹点')
  const textOf = (parent, name) => parent?.getElementsByTagNameNS('*', name)[0]?.textContent?.trim() || ''
  const track = tracks[0]
  const baseName = file.name.replace(/\.gpx$/i, '')
  const metadataName = textOf(documentXml, 'name')
  const name = textOf(track, 'name') || (trackCount > 1 ? `${metadataName || baseName} - ${trackIndex + 1}` : metadataName || baseName)
  const note = textOf(track, 'desc') || textOf(documentXml, 'desc')
  const rawType = textOf(track, 'type')
  return {
    name,
    note,
    activityType: parseActivityType(rawType),
    points,
    hasMissingValues: parsedTimes.some(date => !Number.isFinite(date.getTime())) || rawNodes.some(node => !Number.isFinite(Number(node.getElementsByTagNameNS('*', 'ele')[0]?.textContent)))
  }
}

function parseActivityType(value) {
  const type = String(value || '').trim().toLowerCase()
  if (/步行|徒步|walking|walk|hike/.test(type)) return 'walk'
  if (/跑步|跑|running|run/.test(type)) return 'run'
  if (/骑行|骑|自行车|cycling|cycle|bike/.test(type)) return 'bike'
  if (/公共交通|公交|地铁|铁路|高铁|火车|transit|bus|train|subway|rail/.test(type)) return 'transit'
  if (/驾车|打车|出租|网约车|驾驶|driving|drive|car|taxi/.test(type)) return 'drive'
  return type ? 'other' : ''
}

async function exportSelected() {
  await exportRows(selected.value, '所选轨迹')
}

async function exportCurrentPage() {
  await exportRows(rows.value, '当前页轨迹')
}

async function exportRows(items, label) {
  if (!items.length) return
  exporting.value = true
  try {
    const records = []
    for (const item of items) records.push((await getTrajectoryById(item.trajectoryId)).data)
    exportGpxRecords(records, `LifeLog-${label}-${new Date().toISOString().slice(0, 10)}.gpx`)
    ElMessage.success(`已导出 ${records.length} 条轨迹`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '轨迹导出失败')
  } finally { exporting.value = false }
}

function exportGpxRecords(records, filename) {
  const namespace = 'http://www.topografix.com/GPX/1/1'
  const xml = document.implementation.createDocument(namespace, 'gpx')
  const root = xml.documentElement
  root.setAttribute('version', '1.1')
  root.setAttribute('creator', 'LifeLog')
  const addText = (parent, name, value) => {
    if (value == null || value === '') return
    const node = xml.createElementNS(namespace, name)
    node.textContent = String(value)
    parent.appendChild(node)
  }
  for (const record of records) {
    const track = xml.createElementNS(namespace, 'trk')
    addText(track, 'name', record.name || 'GPS 轨迹')
    addText(track, 'type', activityLabel(record.activityType))
    addText(track, 'desc', record.note)
    const groups = new Map()
    ;[...(record.points || [])].sort((a, b) => a.sequence - b.sequence).forEach(point => {
      const key = point.segmentNo || 1
      if (!groups.has(key)) groups.set(key, [])
      groups.get(key).push(point)
    })
    for (const segmentPoints of groups.values()) {
      const segment = xml.createElementNS(namespace, 'trkseg')
      for (const point of segmentPoints) {
        const trackPoint = xml.createElementNS(namespace, 'trkpt')
        trackPoint.setAttribute('lat', String(point.latitude))
        trackPoint.setAttribute('lon', String(point.longitude))
        addText(trackPoint, 'ele', point.altitudeM)
        const recordedAt = point.recordedAt ? new Date(point.recordedAt) : null
        if (recordedAt && Number.isFinite(recordedAt.getTime())) addText(trackPoint, 'time', recordedAt.toISOString())
        segment.appendChild(trackPoint)
      }
      track.appendChild(segment)
    }
    root.appendChild(track)
  }
  const blob = new Blob([new XMLSerializer().serializeToString(xml)], { type: 'application/gpx+xml;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = safeFileName(filename)
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function safeFileName(value) { return String(value).replace(/[\\/:*?"<>|]/g, '-').trim() || 'GPS轨迹.gpx' }
function drawRoute() { map?.getSource('trajectory-edit')?.setData(routeFeatureCollection()) }
function fitRoute() {
  if (!map || !form.points.length) return
  const bounds = new maplibregl.LngLatBounds()
  form.points.forEach(point => bounds.extend([Number(point.longitude), Number(point.latitude)]))
  if (bounds.isEmpty()) return
  if (form.points.length === 1) map.jumpTo({ center: bounds.getCenter(), zoom: 14 })
  else map.fitBounds(bounds, { padding: 48, maxZoom: 15, duration: 0 })
}
function selectPointOffset(offset) {
  const nextSequence = selectedPointSequence.value + offset
  const point = form.points.find(item => item.sequence === nextSequence)
  if (!point) return
  selectedPointSequence.value = nextSequence
  map?.easeTo({ center: [Number(point.longitude), Number(point.latitude)], duration: 250 })
}
async function save() {
  if (!form.name.trim() || !form.startedAt || form.points.length < 2) { ElMessage.warning('请填写名称、开始时间并至少添加两个采样点'); return }
  saving.value = true
  try {
    const payload = { ...form, name: form.name.trim(), startedAt: toIso(form.startedAt), endedAt: form.endedAt ? toIso(form.endedAt) : null, points: form.points.map((point, index) => ({ ...point, sequence: index + 1, recordedAt: toIso(point.recordedAt) })) }
    if (form.trajectoryId) await updateTrajectory(form.trajectoryId, payload); else await addTrajectory(payload)
    ElMessage.success('轨迹已保存'); allowNavigation = true; await router.push('/travel/admin/trajectory')
  } catch (error) { ElMessage.error(error?.response?.data?.message || '轨迹保存失败') }
  finally { saving.value = false }
}
async function requestClose() {
  if (dirty()) { try { await ElMessageBox.confirm('当前轨迹有未保存的修改，确定离开吗？', '离开编辑', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }) } catch { return } }
  allowNavigation = true; router.push('/travel/admin/trajectory')
}
onBeforeRouteLeave(async () => {
  if (!editing.value || allowNavigation || !dirty()) { allowNavigation = false; return true }
  try { await ElMessageBox.confirm('当前轨迹有未保存的修改，确定离开吗？', '离开编辑', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }); return true }
  catch { return false }
})
function dirty() { return routeSnapshot !== snapshot() }
function snapshot() { return JSON.stringify({ name: form.name, activityType: form.activityType, startedAt: form.startedAt, endedAt: form.endedAt, note: form.note, points: form.points }) }
function openCreate() { router.push({ name: 'TravelTrajectoryEdit' }) }
function openEdit(row) { router.push({ name: 'TravelTrajectoryEdit', params: { id: row.trajectoryId } }) }
async function removeOne(row) { await removeRows([row.trajectoryId], `确定删除“${row.name}”吗？`) }
async function removeSelected() { await removeRows(selected.value.map(row => row.trajectoryId), `确定删除选中的 ${selected.value.length} 条轨迹吗？`) }
async function removeRows(ids, message) {
  if (!ids.length) return
  try { await ElMessageBox.confirm(message, '删除轨迹', { type: 'warning' }) } catch { return }
  deleting.value = true
  try { await deleteTrajectories(ids); ElMessage.success('轨迹已删除'); selected.value = []; await loadRows() }
  catch { ElMessage.error('删除失败') }
  finally { deleting.value = false }
}
function changePageSize() { page.value = 1; loadRows() }
function handleSortChange({ prop, order }) { sortState.value = { prop: prop || 'startedAt', order: order === 'ascending' ? 'asc' : 'desc' }; page.value = 1; loadRows() }
function shiftTrajectoryTimes(value) {
  const nextStart = value || form.startedAt
  if (!lastStartTime || !nextStart) { lastStartTime = nextStart; return }
  const delta = new Date(nextStart).getTime() - new Date(lastStartTime).getTime()
  lastStartTime = nextStart
  if (!Number.isFinite(delta) || delta === 0) return
  form.points.forEach(point => { point.recordedAt = localDateTime(new Date(new Date(point.recordedAt).getTime() + delta)) })
  if (form.endedAt) form.endedAt = localDateTime(new Date(new Date(form.endedAt).getTime() + delta))
}
function activityLabel(value) { return activityOptions.find(item => item.value === value)?.label || '其他' }
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
function formatDuration(value) { if (value == null) return '—'; const hours = Math.floor(value / 3600), minutes = Math.floor(value % 3600 / 60); return hours ? `${hours}小时${minutes}分` : `${minutes}分钟` }
function formatDistance(value) { const meters = Number(value); if (!Number.isFinite(meters)) return '—'; return meters >= 1000 ? `${(meters / 1000).toFixed(2)} km` : `${meters.toFixed(0)} m` }
function localDateTime(value) { if (!value) return ''; const date = new Date(value), pad = part => String(part).padStart(2, '0'); return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}` }
function toIso(value) { return value ? new Date(value).toISOString() : null }
function calculateDistance(points) {
  let distance = 0
  for (let index = 1; index < points.length; index++) { const a = points[index - 1], b = points[index], rad = Math.PI / 180, dLat = (b.latitude - a.latitude) * rad, dLon = (b.longitude - a.longitude) * rad, h = Math.sin(dLat / 2) ** 2 + Math.cos(a.latitude * rad) * Math.cos(b.latitude * rad) * Math.sin(dLon / 2) ** 2; distance += 6371008.8 * 2 * Math.asin(Math.sqrt(Math.min(1, h))) }
  return distance
}
</script>

<style scoped>
.trajectory-admin { display:flex; flex-direction:column; color:#263b40; }
.trajectory-editor { display:flex; flex-direction:column; gap:14px; color:#263b40; }
.page-header,.editor-header,.map-heading,.points-heading { display:flex; align-items:center; justify-content:space-between; gap:14px; }
.page-header { margin-bottom:12px; }.page-header h1,.editor-header h1 { margin:0 0 6px; color:#1d3035; font-size:24px; font-weight:720; line-height:1.25; }.page-header p,.map-hint { margin:0; color:#65777c; font-size:13px; }
.header-actions,.map-actions,.point-navigation { display:flex; align-items:center; flex-wrap:wrap; gap:8px; }.gpx-input { display:none; }
.query-filters { display:flex; align-items:center; justify-content:flex-start; flex-wrap:wrap; gap:10px; min-height:38px; }.keyword-filter { width:220px; }.activity-filter { width:150px; }.date-filter { width:220px; }
.count { color:#65777c; font-size:13px; white-space:nowrap; }.query-card { margin-bottom:0; border:1px solid #d9e3e4; border-radius:6px; }.query-card :deep(.el-card__body) { padding:10px 18px !important; }.action-bar,.bulk-toolbar { display:flex; align-items:center; flex-wrap:wrap; gap:8px; padding:12px; border:1px solid #d9e3e4; border-radius:7px; background:#fff; box-shadow:0 2px 8px rgba(29,48,53,.035); }.action-bar { margin:0 0 15px; }.bulk-toolbar { margin:0 0 12px; color:#65777c; font-size:13px; }.bulk-toolbar .el-button { margin-left:auto; }.pagination-bar { display:flex; justify-content:center; margin-top:20px; padding-bottom:12px; }
.editor-header { padding-bottom:12px; border-bottom:1px solid #e2eaea; }.editor-header h1 { margin-left:12px; }.editor-header>div:first-child { display:flex; align-items:center; }.editor-fields,.map-section,.points-section { padding:16px; border:1px solid #e2eaea; border-radius:6px; background:#fff; }
.metadata-grid { display:grid; grid-template-columns:1.4fr .8fr 1.2fr 1.2fr; gap:14px; }.metadata-grid :deep(.el-form-item) { margin-bottom:10px; }.map-heading,.points-heading { margin-bottom:10px; }.map-heading strong,.points-heading strong { display:block; font-size:14px; }.map-heading span,.points-heading span { margin-left:8px; color:#7e8e91; font-size:12px; }
.time-adjust-hint { display:block; margin-top:5px; color:#7e8e91; font-size:12px; line-height:1.4; }
.point-workspace { display:grid; grid-template-columns:minmax(0,1.5fr) minmax(300px,.8fr); align-items:stretch; gap:14px; }.point-workspace>.map-section,.point-workspace>.points-section { min-width:0; }
.editor-map { height:min(62vh,680px); min-height:420px; border:1px solid #dce6e7; border-radius:5px; background:#e8eff0; }.map-hint { margin-top:8px; }.point-fields { display:grid; grid-template-columns:1fr; gap:0; }.point-fields :deep(.el-form-item) { margin-bottom:12px; }.point-fields :deep(.el-input-number) { width:100%; }.points-section :deep(.el-empty) { padding:10px 0; }
@media(max-width:900px) { .metadata-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.point-workspace { grid-template-columns:minmax(0,1.2fr) minmax(260px,.8fr); }.editor-map { height:55vh; min-height:360px; } }
@media(max-width:700px) { .page-header,.editor-header,.points-heading { align-items:flex-start; flex-direction:column; }.header-actions,.map-actions,.point-navigation { width:100%; }.map-actions>* { flex:1; }.query-filters { align-items:stretch; flex-direction:column; gap:10px; }.keyword-filter,.activity-filter,.date-filter,.query-filters :deep(.el-input),.query-filters :deep(.el-select),.query-filters :deep(.el-date-editor),.query-filters :deep(.el-button) { width:100% !important; }.action-bar,.bulk-toolbar { align-items:stretch; flex-direction:column; }.action-bar :deep(.el-button),.bulk-toolbar :deep(.el-button) { width:100%; margin:0; }.pagination-bar { justify-content:flex-start; overflow-x:auto; }.pagination-bar :deep(.el-pagination) { flex-wrap:nowrap; min-width:max-content; }.metadata-grid,.point-workspace { grid-template-columns:1fr; gap:12px; }.metadata-grid { gap:0; }.editor-fields,.map-section,.points-section { padding:12px; }.editor-map { height:45vh; min-height:280px; }.point-fields { grid-template-columns:repeat(2,minmax(0,1fr)); gap:0 12px; } }
</style>
