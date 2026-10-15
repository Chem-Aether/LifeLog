<template>
  <div v-if="isEditing" v-loading="saving || routeSampling" class="travel-edit-page">
    <header class="edit-header"><div><el-button text @click="requestClose">返回铁路记录</el-button><h1>{{ editItem?.trainId ? '编辑铁路行程' : '新增铁路行程' }}</h1></div><div class="edit-actions"><el-button :disabled="saving" @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button></div></header>
    <section class="edit-panel">
      <el-form v-if="editItem" :model="editItem" label-position="top" class="record-form">
        <div class="grid-form">
          <el-form-item label="车次"><el-input v-model="editItem.trainNo" @input="matchTrainType(editItem, $event)" /></el-form-item>
          <el-form-item label="列车类型"><el-select v-model="editItem.trainType" filterable allow-create default-first-option clearable placeholder="选择或输入列车类型"><el-option v-for="trainType in trainTypes" :key="trainType.label" :label="trainType.label" :value="trainType.label" /></el-select></el-form-item>
          <el-form-item label="车型"><el-input v-model="editItem.trainModel" /></el-form-item>
          <el-form-item label="始发站"><el-autocomplete v-model="editItem.originStationName" :fetch-suggestions="queryStation" /></el-form-item>
          <el-form-item label="发站"><el-autocomplete v-model="editItem.startStationName" :fetch-suggestions="queryStation" @select="s => applyStation(editItem, s, 'start')" /></el-form-item>
          <el-form-item label="发车时间"><el-date-picker v-model="editItem.departureTime" type="datetime" format="YYYY/M/D HH:mm" value-format="YYYY-MM-DDTHH:mm" editable /></el-form-item>
          <el-form-item label="终点站"><el-autocomplete v-model="editItem.terminalStationName" :fetch-suggestions="queryStation" /></el-form-item>
          <el-form-item label="到站"><el-autocomplete v-model="editItem.endStationName" :fetch-suggestions="queryStation" @select="s => applyStation(editItem, s, 'end')" /></el-form-item>
          <el-form-item label="到达时间"><el-date-picker v-model="editItem.arrivalTime" type="datetime" format="YYYY/M/D HH:mm" value-format="YYYY-MM-DDTHH:mm" editable /></el-form-item>
          <el-form-item label="车厢号"><el-input v-model="editItem.carriageNo" /></el-form-item><el-form-item label="座位号"><el-input v-model="editItem.seatNo" /></el-form-item>
          <el-form-item label="座位等级"><el-select v-model="editItem.seatType" filterable allow-create default-first-option clearable placeholder="选择或输入座位等级" @change="rememberSeatType"><el-option v-for="seatType in seatTypeOptions" :key="seatType" :label="seatType" :value="seatType" /></el-select></el-form-item>
          <el-form-item label="里程(km)"><el-input-number v-model="editItem.mileageKm" :min="0" :precision="2" /></el-form-item><el-form-item label="备注" class="wide-field"><el-input v-model="editItem.note" type="textarea" :rows="2" /></el-form-item>
        </div>
        <el-divider content-position="left">途经站与轨迹</el-divider>
        <draggable v-model="editItem.waypoints" item-key="sequence" @end="waypointsChanged(editItem.waypoints, editItem)" handle=".drag-handle" class="waypoint-list"><template #item="{ element, index }"><div class="waypoint-row"><span class="drag-handle">☰</span><span class="waypoint-order">{{ index + 1 }}</span><el-autocomplete v-model="element.stationName" :fetch-suggestions="queryStation" @select="s => applyWaypoint(editItem, element, s)" /><el-button link type="danger" @click="delStation(editItem, element)">移除</el-button></div></template></draggable>
        <div class="add-waypoint"><el-autocomplete v-model="tempStationName" :fetch-suggestions="queryStation" placeholder="搜索途经站" @select="s => tempStationSuggestion = s" /><el-button @click="confirmAddStation(editItem)">添加站点</el-button></div>
        <div class="route-actions"><el-input v-model="editItem.routeSource" placeholder="轨迹来源" /><el-button :loading="routeSampling" @click="sampleRoute(editItem, 'auto')">按车次获取</el-button><el-button :loading="routeSampling" @click="sampleRoute(editItem, 'manual')">按途经站生成</el-button><el-button v-if="editItem.routeGeoJson" type="primary" plain @click="editSavedRoute">编辑轨迹区间</el-button><el-button v-if="editItem.routeGeoJson" type="danger" link @click="clearRoute(editItem)">移除轨迹</el-button><el-tag v-if="editItem.routeGeoJson" type="success">轨迹已载入</el-tag></div>
      </el-form>
    </section>
    <TrainRoutePreview v-model="routePreviewVisible" :route-geo-json="pendingRoute.routeGeoJson" :stations="pendingRoute.stations" @confirm="applyRouteSelection" />
  </div>
  <div v-else v-loading="saving || deleting || routeSampling" class="train-manage-page">
    <div class="page-title">铁路出行记录管理</div>

    <!-- 查询 -->
    <el-card class="query-card" shadow="hover">
      <el-form :model="queryForm" inline size="default">
        <el-form-item label="车次">
          <el-input v-model="queryForm.trainNo" placeholder="G123" clearable />
        </el-form-item>
        <el-form-item label="发站">
          <el-autocomplete
              v-model="queryForm.startStationName"
              :fetch-suggestions="queryStation"
              placeholder="发站"
          />
        </el-form-item>
        <el-form-item label="到站">
          <el-autocomplete
              v-model="queryForm.endStationName"
              :fetch-suggestions="queryStation"
              placeholder="到站"
          />
        </el-form-item>

        <el-form-item label="发车时间">
          <el-date-picker
              v-model="queryForm.departureTimeRange"
              type="datetimerange"
              format="YYYY/M/D HH:mm"
              value-format="YYYY-MM-DDTHH:mm"
              editable
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="editIndex !== -1 || loading" @click="doQuery">查询</el-button>
          <el-button :disabled="editIndex !== -1 || loading" @click="resetQuery">重置</el-button>
          <el-button :disabled="saving || deleting || loading || editIndex !== -1" @click="handleRefresh">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="tool-bar">
      <el-button type="primary" :disabled="editIndex !== -1 || loading" @click="handleAdd">+ 新增行程</el-button>
      <TravelDataTools type="train" :disabled="loading || editIndex !== -1 || saving || deleting" @imported="loadData" />
    </div>

    <el-skeleton v-if="loading" rows="8" />

    <div v-else-if="trainList.length === 0" class="empty-tip">
      <el-empty description="暂无记录" />
    </div>

    <div class="table-section">
    <div class="bulk-toolbar">
        <span>已选择 {{ selectedRows.length }} 条（当前页）</span>
        <el-button type="danger" plain :disabled="!selectedRows.length || deleting" :loading="deleting" @click="deleteSelected">批量删除</el-button>
      </div>
      <el-table ref="tableRef" v-loading="loading" :data="pageData" stripe @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="trainNo" label="车次" width="95" sortable />
        <el-table-column prop="trainType" label="列车类型" min-width="145" sortable>
          <template #default="scope">
            <el-tag v-if="scope.row.trainType" class="train-type-tag" :class="trainTypeClass(scope.row.trainType)" effect="light" round>{{ scope.row.trainType }}</el-tag>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="trainModel" label="车型" min-width="110" sortable show-overflow-tooltip />
        <el-table-column prop="startStationName" label="发站" min-width="145" sortable show-overflow-tooltip>
          <template #default="scope">{{ scope.row.startStationName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="endStationName" label="到站" min-width="145" sortable show-overflow-tooltip>
          <template #default="scope">{{ scope.row.endStationName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="originStationName" label="始发站点" min-width="120" sortable show-overflow-tooltip />
        <el-table-column prop="terminalStationName" label="终到站点" min-width="120" sortable show-overflow-tooltip />
        <el-table-column prop="departureTime" label="发车时间" min-width="155" sortable :formatter="formatTableDateTime" />
        <el-table-column prop="arrivalTime" label="到达时间" min-width="155" sortable :formatter="formatTableDateTime" />
        <el-table-column prop="carriageNo" label="车厢号" width="95" sortable />
        <el-table-column prop="seatNo" label="座位号" width="95" sortable />
        <el-table-column prop="seatType" label="座位等级" min-width="110" sortable />
        <el-table-column prop="mileageKm" label="里程(km)" width="115" sortable />
        <el-table-column label="途经站" min-width="190" show-overflow-tooltip>
          <template #default="scope">{{ formatWaypoints(scope.row.waypoints) }}</template>
        </el-table-column>
        <el-table-column prop="routeGeoJson" label="轨迹" width="95">
          <template #default="scope"><el-tag v-if="scope.row.routeGeoJson" type="success">已记录</el-tag><span v-else class="muted">无</span></template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="scope">
            <div class="table-actions">
              <TravelRecordExportButton v-if="scope.row.trainId" type="train" :record-id="scope.row.trainId" :record="scope.row" />
              <el-button link type="primary" @click="handleEdit(scope.row, scope.$index)">编辑</el-button>
              <el-button link type="danger" :disabled="deleting" @click="handleDelete(scope.$index)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="pagination-bar">
      <el-pagination
        :disabled="editIndex !== -1 || saving || deleting || loading"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="pageInfo.total || 0"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="loadData"
        @size-change="handlePageSizeChange"
      />
    </div>
    <TrainRoutePreview
        v-model="routePreviewVisible"
        :route-geo-json="pendingRoute.routeGeoJson"
        :stations="pendingRoute.stations"
        @confirm="applyRouteSelection"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import draggable from 'vuedraggable'
import TrainRoutePreview from '@/modules/travel/components/TrainRoutePreview.vue'
import TravelDataTools from '@/modules/travel/components/TravelDataTools.vue'
import TravelRecordExportButton from '@/modules/travel/components/TravelRecordExportButton.vue'
import {
  getTrainList,
  addTrainTicket,
  updateTrainTicket,
  deleteTrainTicket,
  deleteTrainTickets,
  sampleTrainRoute
} from '@/modules/travel/apis/trainTickets.js'
import { useResourceSearch } from '@/modules/travel/composables/useResourceSearch.js'
import { defaultSeatTypes, trainTypes } from '@/modules/travel/stores/trainOptions.js'

const { queryTrainStation: queryStation } = useResourceSearch()
const route = useRoute()
const router = useRouter()
const isEditing = computed(() => route.name === 'TravelTrainTicketEdit')
const loading = ref(true)
const saving = ref(false)
const deleting = ref(false)
let loadSequence = 0
const trainList = ref([])
const matchTrainType = (item, trainNo) => {
  const matchedType = trainTypes.find(type => type.prefix === trainNo?.trim()?.[0]?.toUpperCase())
  if (matchedType && (!item.trainType || trainTypes.some(type => type.label === item.trainType))) item.trainType = matchedType.label
}
const trainTypeClass = value => trainTypes.find(type => type.label === value)?.className || 'type-custom'
const formatWaypoints = waypoints => Array.isArray(waypoints)
  ? waypoints.map(point => point?.stationName).filter(Boolean).join('、') || '-'
  : '-'
const formatTableDateTime = (_row, _column, value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-'
const seatTypeStorageKey = 'travel.train.seatTypes'
const seatTypeOptions = ref(defaultSeatTypes)
try {
  const savedSeatTypes = JSON.parse(localStorage.getItem(seatTypeStorageKey) || '[]')
  if (Array.isArray(savedSeatTypes)) {
    const savedNames = savedSeatTypes.map(value => typeof value === 'string' ? value.trim() : '').filter(Boolean)
    seatTypeOptions.value = [...new Set([...defaultSeatTypes, ...savedNames])]
  }
} catch {}
const rememberSeatType = value => {
  const name = typeof value === 'string' ? value.trim() : ''
  if (!name) return
  if (seatTypeOptions.value.includes(name)) return
  seatTypeOptions.value = [...seatTypeOptions.value, name]
  try { localStorage.setItem(seatTypeStorageKey, JSON.stringify(seatTypeOptions.value)) } catch {}
}
const selectedRows = ref([])
const tableRef = ref(null)

// 后端分页信息（total/size/current/records...）
const pageInfo = ref({ total: 0 })

const currentPage = ref(1)
const pageSize = ref(10)
const editIndex = ref(-1)
const editItem = ref(null)
const tempStationName = ref('')
const tempStationSuggestion = ref(null)
const routePreviewVisible = ref(false)
const pendingRoute = reactive({ item: null, routeGeoJson: '', stations: [] })
const routeSampling = ref(false)
let skipLeaveGuard = false

// 备份编辑前的数据，用于取消编辑时回滚
const editBackup = ref(null)

const queryForm = reactive({
  trainNo: '',
  startStationName: '',
  endStationName: '',
  // [start, end]
  departureTimeRange: null
})

onMounted(async () => {
  if (isEditing.value) {
    currentPage.value = Math.max(1, Number(route.query.page) || 1)
    await loadData()
    const row = route.params.id ? trainList.value.find(item => String(item.trainId) === String(route.params.id)) : null
    if (route.params.id && !row) { ElMessage.error('铁路记录不存在'); router.replace('/travel/admin/trainTicket'); return }
    beginEdit(row, row ? trainList.value.indexOf(row) : 0)
    return
  }
  await loadData()
})

watch(() => route.fullPath, async () => {
  if (!isEditing.value) { cancelEdit(); return }
  currentPage.value = Math.max(1, Number(route.query.page) || 1)
  await loadData()
  const row = route.params.id ? trainList.value.find(item => String(item.trainId) === String(route.params.id)) : null
  if (route.params.id && !row) { ElMessage.error('铁路记录不存在'); router.replace('/travel/admin/trainTicket'); return }
  beginEdit(row, row ? trainList.value.indexOf(row) : 0)
})

const loadData = async () => {
  const sequence = ++loadSequence
  loading.value = true
  selectedRows.value = []
  tableRef.value?.clearSelection()

  const departureTimeStart = Array.isArray(queryForm.departureTimeRange)
    ? queryForm.departureTimeRange[0]
    : null
  const departureTimeEnd = Array.isArray(queryForm.departureTimeRange)
    ? queryForm.departureTimeRange[1]
    : null

  try {
  const res = await getTrainList({
    pageNum: currentPage.value,
    pageSize: pageSize.value,
    trainNo: queryForm.trainNo,
    startStationName: queryForm.startStationName,
    endStationName: queryForm.endStationName,
    departureTimeStart,
    departureTimeEnd
  })
  if (sequence !== loadSequence) return
  trainList.value = (res.data || []).map(i => {
    i.waypoints = i.waypoints || []
    return i
  })
  trainList.value.forEach(item => rememberSeatType(item.seatType))
  pageInfo.value = res.page || { total: (res.data || []).length }
  } catch (error) {
    if (sequence === loadSequence) ElMessage.error(error?.response?.data?.message || error?.response?.data?.msg || '铁路记录加载失败')
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

// 后端分页：当前页数据就是 trainList
const pageData = computed(() => trainList.value)

const handleEdit = (item, idx) => {
  if (editIndex.value !== -1 || saving.value) return
  router.push({ name: 'TravelTrainTicketEdit', params: { id: item.trainId }, query: { page: currentPage.value } })
}
const beginEdit = (item, idx) => {
  editIndex.value = idx
  const source = item ? JSON.parse(JSON.stringify(item)) : { trainId: null, trainNo: '', trainType: '', trainModel: '', startStationName: '', endStationName: '', originStationName: '', terminalStationName: '', departureTime: '', arrivalTime: '', carriageNo: '', seatNo: '', seatType: '二等座', mileageKm: null, waypoints: [], routeGeoJson: null, routeSource: '', note: '' }
  editBackup.value = JSON.parse(JSON.stringify(source))
  editItem.value = source
  editItem.value.waypoints ||= []
}
const hasUnsavedChanges = () => JSON.stringify(editItem.value) !== JSON.stringify(editBackup.value)
const requestClose = async () => {
  if (saving.value) return
  if (hasUnsavedChanges()) {
    try { await ElMessageBox.confirm('当前内容尚未保存，确定放弃修改并离开吗？', '未保存的修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }) }
    catch { return }
  }
  skipLeaveGuard = true
  await router.push('/travel/admin/trainTicket')
}
onBeforeRouteLeave(async () => {
  if (skipLeaveGuard) { skipLeaveGuard = false; return true }
  if (!isEditing.value || saving.value || !hasUnsavedChanges()) return true
  try { await ElMessageBox.confirm('当前内容尚未保存，确定放弃修改并离开吗？', '未保存的修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }); return true }
  catch { return false }
})
const cancelEdit = () => {
  editIndex.value = -1
  editBackup.value = null
  editItem.value = null
}
const saveEdit = async () => {
  if (saving.value) return
  const item = editItem.value
  if (!item) return

  if (!item.trainNo?.trim() || !item.startStationName?.trim() || !item.endStationName?.trim() || !item.departureTime) {
    ElMessage.warning('请填写车次、发站、到站和发车时间')
    return
  }
  if (item.arrivalTime && new Date(item.arrivalTime).getTime() < new Date(item.departureTime).getTime()) {
    ElMessage.warning('到达时间不能早于发车时间')
    return
  }
  if (item.routeGeoJson) {
    try {
      const geometry = JSON.parse(item.routeGeoJson)
      if (!['LineString', 'MultiLineString'].includes(geometry.type) || !Array.isArray(geometry.coordinates) || geometry.coordinates.length === 0) {
        throw new Error()
      }
    } catch {
      ElMessage.warning('轨迹数据无效，请重新采样')
      return
    }
  }

  // 无变更：直接退出编辑，不请求后端
  if (editBackup.value) {
    const now = JSON.stringify(item)
    const old = JSON.stringify(editBackup.value)
    if (now === old) {
      editIndex.value = -1
      editBackup.value = null
      skipLeaveGuard = true
      router.push('/travel/admin/trainTicket')
      ElMessage.info(item.trainId ? '没有修改内容' : '空白记录已取消')
      return
    }
  }

  saving.value = true
  try {
    if (item.trainId) {
      await updateTrainTicket(item)
    } else {
      await addTrainTicket(item)
    }

    editIndex.value = -1
    editBackup.value = null
    editItem.value = null
    skipLeaveGuard = true
    router.push('/travel/admin/trainTicket')
    ElMessage.success('保存成功')
    if (!item.trainId) currentPage.value = 1
    await loadData()
  } catch (e) {
    const message = e?.response?.data?.detail
        || e?.response?.data?.message
        || e?.response?.data?.msg
        || e?.response?.data?.error
        || '保存失败'
    ElMessage.error(message)
  } finally {
    saving.value = false
  }
}

const handleAdd = async () => {
  if (editIndex.value !== -1 || saving.value) return
  router.push({ name: 'TravelTrainTicketEdit' })
}

const renumber = (list) => {
  list.forEach((s, i) => s.sequence = i + 1)
}

const sampleRoute = async (item, mode) => {
  const trainNo = item.trainNo?.trim()
  if (mode === 'auto' && !trainNo) {
    ElMessage.warning('请先填写车次')
    return
  }
  const stationNames = (item.waypoints || []).map(station => station.stationName?.trim()).filter(Boolean)
  if (mode === 'manual' && stationNames.length < 2) {
    ElMessage.warning('请先按顺序填写至少两个途经站')
    return
  }
  const now = new Date()
  const date = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  routeSampling.value = true
  try {
    const res = await sampleTrainRoute({ mode, trainNo: trainNo || '', date, stations: stationNames })
    const data = res.data || {}
    const timetable = new Map((data.timetable || []).map(station => [station.name, station]))
    pendingRoute.item = item
    pendingRoute.routeGeoJson = data.routeGeoJson || ''
    pendingRoute.stations = (data.stations || []).map(station => {
      const schedule = timetable.get(station.stationName) || {}
      return { ...station, arrivalTime: schedule.arrive || null, departureTime: schedule.depart || null }
    })
    pendingRoute.trainType = data.trainType || ''
    pendingRoute.routeSource = data.source || 'railway-network'
    routePreviewVisible.value = true
    if (data.unmatchedStations?.length) ElMessage.warning(`铁路网未匹配：${data.unmatchedStations.join('、')}`)
  } catch (error) {
    const message = error?.response?.data?.msg || error?.response?.data?.detail || '轨迹采样失败'
    ElMessage.error(message)
  } finally {
    routeSampling.value = false
  }
}
const editSavedRoute = () => {
  if (!editItem.value?.routeGeoJson) return
  pendingRoute.item = editItem.value
  pendingRoute.routeGeoJson = editItem.value.routeGeoJson
  pendingRoute.stations = [...(editItem.value.waypoints || [])]
  pendingRoute.trainType = editItem.value.trainType || ''
  pendingRoute.routeSource = editItem.value.routeSource || 'manual-edit'
  routePreviewVisible.value = true
}
const applyRouteSelection = ({ routeGeoJson, waypoints, routeOrigin, routeTerminal }) => {
  if (!pendingRoute.item) return
  pendingRoute.item.routeGeoJson = routeGeoJson
  pendingRoute.item.waypoints = waypoints
  pendingRoute.item.mileageKm = null
  const first = waypoints[0]
  const last = waypoints.at(-1)
  pendingRoute.item.startStationName = first.stationName
  pendingRoute.item.endStationName = last.stationName
  pendingRoute.item.originStationName = routeOrigin.stationName
  pendingRoute.item.terminalStationName = routeTerminal.stationName
  pendingRoute.item.trainType = pendingRoute.trainType || pendingRoute.item.trainType
  pendingRoute.item.routeSource = pendingRoute.routeSource
  pendingRoute.item.departureTime = first.departureTime || first.arrivalTime || pendingRoute.item.departureTime
  pendingRoute.item.arrivalTime = last.arrivalTime || last.departureTime || pendingRoute.item.arrivalTime
  routePreviewVisible.value = false
  ElMessage.success(`已保留所选区间，并补全 ${waypoints.length} 个途经站`)
}
const applyStation = (item, station, type) => {
  item[`${type}StationName`] = station.name
}
const applyWaypoint = (item, waypoint, station) => {
  Object.assign(waypoint, {
    stationName: station.name,
    longitude: station.longitude,
    latitude: station.latitude
  })
  item.mileageKm = null
}
const waypointsChanged = (list, item) => {
  renumber(list)
  item.mileageKm = null
}
const confirmAddStation = (item) => {
  const name = tempStationName.value?.trim()
  if (!name) {
    ElMessage.warning('请选择站点')
    return
  }
  const selected = tempStationSuggestion.value?.name === name ? tempStationSuggestion.value : {}
  item.waypoints.push({
    stationName: name,
    longitude: selected.longitude ?? null,
    latitude: selected.latitude ?? null,
    sequence: item.waypoints.length + 1
  })
  item.mileageKm = null
  tempStationName.value = ''
  tempStationSuggestion.value = null
}
const delStation = (item, el) => {
  item.waypoints.splice(item.waypoints.indexOf(el), 1)
  waypointsChanged(item.waypoints, item)
}
const clearRoute = (item) => { item.routeGeoJson = null; item.mileageKm = null }

const handleDelete = async (idx) => {
  if (deleting.value || saving.value) return
  const item = trainList.value[idx]
  if (!item) return
  if (!item.trainId) {
    trainList.value.splice(idx, 1)
    editIndex.value = -1
    editBackup.value = null
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除 ${item.trainNo} ${item.startStationName} 至 ${item.endStationName} 的行程吗？`, '删除行程', { type: 'warning' })
    deleting.value = true
    await deleteTrainTicket(item.trainId)
    if (pageData.value.length === 1 && currentPage.value > 1) currentPage.value -= 1
    await loadData()
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.response?.data?.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

const handleRefresh = async () => {
  await loadData()
  ElMessage.success('刷新成功')
}
const doQuery = async () => {
  currentPage.value = 1
  await loadData()
}
const resetQuery = async () => {
  Object.assign(queryForm, { trainNo: '', startStationName: '', endStationName: '', departureTimeRange: null })
  currentPage.value = 1
  await loadData()
}

const clearTableSelection = () => {
  selectedRows.value = []
  tableRef.value?.clearSelection()
}

const deleteSelected = async () => {
  if (deleting.value || saving.value) return
  const selected = [...selectedRows.value]
  if (!selected.length) return
  try {
    await ElMessageBox.confirm(`确定删除当前页选中的 ${selected.length} 条铁路行程吗？此操作不可撤销。`, '批量删除', { type: 'warning' })
  } catch { return }
  deleting.value = true
  try {
    const result = await deleteTrainTickets(selected.map(row => row.trainId))
    const deleted = Number(result?.data ?? result) || 0
    clearTableSelection()
    const remaining = Math.max(0, (pageInfo.value.total || 0) - deleted)
    if (currentPage.value > 1 && remaining <= (currentPage.value - 1) * pageSize.value) currentPage.value -= 1
    await loadData()
    if (deleted === selected.length) ElMessage.success(`已删除 ${deleted} 条铁路行程`)
    else ElMessage.warning(`成功删除 ${deleted} 条，另有 ${selected.length - deleted} 条已不存在`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.response?.data?.msg || '批量删除失败，记录未变更')
  } finally {
    deleting.value = false
  }
}

const handlePageSizeChange = () => {
  currentPage.value = 1
  loadData()
}
</script>

<style scoped>
.table-actions { display:inline-flex; align-items:center; gap:8px; white-space:nowrap; }
.table-actions :deep(.el-button) { margin:0; font-size:14px; line-height:1; }
.travel-edit-page { display:flex; flex-direction:column; gap:16px; min-height:100%; color:#1d3035; }
.edit-header { display:flex; align-items:center; justify-content:space-between; gap:16px; padding:0 0 14px; border-bottom:1px solid #e5ebec; }
.edit-header h1 { margin:6px 0 0; font-size:22px; font-weight:700; }
.edit-actions { display:flex; align-items:center; gap:8px; }
.edit-panel { padding:20px; background:#fff; border:1px solid #e5ebec; border-radius:7px; }
.train-manage-page { width: 100%; margin: 0 auto; }
.page-title { margin-bottom: 18px; color: #1d3035; font-size: 24px; font-weight: 720; line-height: 1.25; }
.query-card { margin-bottom: 16px; }
.tool-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 15px; padding: 12px; background: #fff; border: 1px solid #e5ebec; border-radius: 6px; }
.bulk-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 0 0 12px; color: #667085; font-size: 13px; }
.pagination-bar { display: flex; justify-content: center; margin-top: 20px; padding-bottom: 12px; }
.empty-tip { padding: 40px 0; text-align: center; }
.record-form .grid-form { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0 16px; }
.train-type-tag { border: 1px solid; font-weight: 600; }
.train-type-tag.type-g { color: #08786f; background: #e3f4f0; border-color: #b6e5d9; }
.train-type-tag.type-d { color: #3867a8; background: #eaf1fb; border-color: #c8d7ef; }
.train-type-tag.type-c { color: #157b8a; background: #e3f4f7; border-color: #bde5ec; }
.train-type-tag.type-k { color: #a65d16; background: #fff3e4; border-color: #f3d3aa; }
.train-type-tag.type-z { color: #a83f60; background: #fbe9ee; border-color: #edc2d0; }
.train-type-tag.type-t { color: #495f91; background: #edf0fa; border-color: #d0d6ee; }
.train-type-tag.type-l { color: #765281; background: #f3ebf5; border-color: #e0cfe6; }
.train-type-tag.type-s { color: #4f7f3c; background: #eaf4e7; border-color: #cee2c7; }
.train-type-tag.type-y { color: #92701d; background: #fbf1df; border-color: #ecddb5; }
.train-type-tag.type-f { color: #536a78; background: #e9eef1; border-color: #d0dbe0; }
.train-type-tag.type-custom { color: #506b72; background: #eef3f4; border-color: #d5e0e2; }
.record-form .grid-form { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0 16px; }
.record-form :deep(.el-form-item) { margin-bottom: 10px; }
.record-form :deep(.el-form-item__content > .el-input), .record-form :deep(.el-form-item__content > .el-autocomplete), .record-form :deep(.el-form-item__content > .el-date-editor), .record-form :deep(.el-form-item__content > .el-select), .record-form :deep(.el-form-item__content > .el-input-number) { width: 100%; }
.wide-field { grid-column: 1 / -1; }
.waypoint-list { display: grid; gap: 7px; max-height: 220px; overflow: auto; }
.waypoint-row { display: grid; grid-template-columns: 22px 30px minmax(0, 1fr) 50px; align-items: center; gap: 8px; }
.drag-handle { color: #718187; cursor: grab; }
.waypoint-order { color: #718187; text-align: center; font-size: 13px; }
.add-waypoint, .route-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 12px; }
.add-waypoint :deep(.el-autocomplete) { flex: 1; min-width: 180px; }
.route-actions > :deep(.el-input) { flex: 1 1 150px; max-width: 240px; }
@media (max-width: 760px) {
  .page-title { font-size: 21px; margin-bottom: 16px; }
  .tool-bar { align-items: stretch; flex-direction: column; }
  .tool-bar > :deep(.el-button), .tool-bar > :deep(.travel-data-tools) { width: 100%; margin: 0; }
  .tool-bar :deep(.el-radio-group) { width: 100%; }
  .tool-bar :deep(.el-radio-button) { flex: 1; }
  .grid-form, .record-form .grid-form { grid-template-columns: 1fr; }
  .waypoint-row { grid-template-columns: 20px 24px minmax(0, 1fr) 42px; }
  .record-form .grid-form { grid-template-columns: 1fr; }
  .pagination-bar { justify-content: flex-start; overflow-x: auto; }
  .pagination-bar :deep(.el-pagination) { flex-wrap: nowrap; min-width: max-content; }
}

</style>
