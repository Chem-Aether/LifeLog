<template>
  <div v-loading="loading || saving || deleting" class="flight-manage-page">
    <div class="page-title">航空出行记录管理</div>

    <!-- 🔍 查询条件栏 -->
    <el-card class="query-card" shadow="hover">
      <el-form :model="queryForm" inline size="default">
        <el-form-item label="航班号">
          <el-input v-model="queryForm.flightNo" placeholder="请输入航班号" clearable />
        </el-form-item>
  <el-form-item label="起飞机场">
          <el-autocomplete
              v-model="queryForm.departureAirportNameDisplay"
              :fetch-suggestions="queryAirport"
              placeholder="起飞机场"
              @select="(s) => handleAirportSelect(s, 'queryDeparture')"
          />
        </el-form-item>
        <el-form-item label="到达机场">
          <el-autocomplete
              v-model="queryForm.arrivalAirportNameDisplay"
              :fetch-suggestions="queryAirport"
              placeholder="到达机场"
              @select="(s) => handleAirportSelect(s, 'queryArrival')"
          />
        </el-form-item>

        <el-form-item label="起飞时间">
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
          <el-button :disabled="saving || deleting || loading || editIndex !== -1" @click="refresh">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <div class="tool-bar">
      <el-button type="primary" :disabled="editIndex !== -1" @click="handleAdd">+ 新增机票</el-button>
      <TravelDataTools type="flight" :disabled="editIndex !== -1 || saving || deleting" @imported="loadData" />
    </div>

    <div class="table-section">
    <div class="bulk-toolbar">
        <span>已选择 {{ selectedRows.length }} 条（当前页）</span>
        <el-button type="danger" plain :disabled="!selectedRows.length || deleting" :loading="deleting" @click="deleteSelected">批量删除</el-button>
      </div>
      <el-table ref="tableRef" :data="pageData" stripe @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="flightNo" label="航班号" min-width="110" sortable />
        <el-table-column prop="airline" label="航空公司" min-width="120" sortable show-overflow-tooltip />
        <el-table-column prop="aircraftType" label="机型" min-width="110" sortable show-overflow-tooltip />
        <el-table-column prop="aircraftRegistration" label="飞机注册号" min-width="125" sortable />
        <el-table-column prop="departureAirportName" label="出发机场" min-width="170" sortable show-overflow-tooltip>
          <template #default="scope">{{ formatAirport(scope.row, 'departure') }}</template>
        </el-table-column>
        <el-table-column prop="departureTerminal" label="出发航站楼" min-width="110" sortable />
        <el-table-column prop="boardingMethod" label="登机方式" min-width="105" sortable />
        <el-table-column prop="departureTime" label="起飞时间" min-width="155" sortable :formatter="formatTableDateTime" />
        <el-table-column prop="arrivalAirportName" label="到达机场" min-width="170" sortable show-overflow-tooltip>
          <template #default="scope">{{ formatAirport(scope.row, 'arrival') }}</template>
        </el-table-column>
        <el-table-column prop="arrivalTerminal" label="到达航站楼" min-width="110" sortable />
        <el-table-column prop="deboardingMethod" label="下机方式" min-width="105" sortable />
        <el-table-column prop="arrivalTime" label="到达时间" min-width="155" sortable :formatter="formatTableDateTime" />
        <el-table-column label="经停机场" min-width="150" show-overflow-tooltip>
          <template #default="scope">{{ formatStopovers(scope.row.stopovers) }}</template>
        </el-table-column>
        <el-table-column prop="seatNo" label="座位号" width="95" sortable />
        <el-table-column prop="distanceKm" label="里程(km)" width="110" sortable />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="scope">
            <TravelRecordExportButton v-if="scope.row.flightId" type="flight" :record-id="scope.row.flightId" />
            <el-button link type="primary" @click="handleEdit(scope.row, scope.$index)">编辑</el-button>
            <el-button link type="danger" :disabled="deleting" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editItem?.flightId ? '编辑机票记录' : '新增机票记录'" width="min(900px, 94vw)" destroy-on-close class="record-dialog" @closed="cancelEdit">
      <el-form v-if="editItem" :model="editItem" label-position="top" class="record-form">
        <div class="grid-form">
          <el-form-item label="航班号"><el-input v-model="editItem.flightNo" /></el-form-item>
          <el-form-item label="航空公司"><el-input v-model="editItem.airline" /></el-form-item>
          <el-form-item label="座位号"><el-input v-model="editItem.seatNo" /></el-form-item>
          <el-form-item label="机号"><el-input v-model="editItem.aircraftRegistration" /></el-form-item>
          <el-form-item label="机型"><el-input v-model="editItem.aircraftType" /></el-form-item>
          <el-form-item label="飞行距离(km)"><el-input-number v-model="editItem.distanceKm" :min="0" :precision="1" /></el-form-item>
          <el-form-item label="经停机场"><el-autocomplete v-model="editItem.stopoverDisplay" :fetch-suggestions="queryAirport" placeholder="无则不填" @select="s => handleAirportSelect(s, 'formStopover')" /></el-form-item>
          <el-form-item label="起飞机场"><el-autocomplete v-model="editItem.departureAirportNameDisplay" :fetch-suggestions="queryAirport" @select="s => handleAirportSelect(s, 'formDeparture')" /></el-form-item>
          <el-form-item label="起飞机场航站楼"><el-input v-model="editItem.departureTerminal" /></el-form-item>
          <el-form-item label="登机方式"><el-select v-model="editItem.boardingMethod" clearable filterable allow-create placeholder="未记录"><el-option label="廊桥" value="廊桥" /><el-option label="摆渡车" value="摆渡车" /><el-option label="远机位步行" value="远机位步行" /><el-option label="其他" value="其他" /></el-select></el-form-item>
          <el-form-item label="起飞时间"><el-date-picker v-model="editItem.departureTime" type="datetime" format="YYYY/M/D HH:mm" value-format="YYYY-MM-DDTHH:mm" editable /></el-form-item>
          <el-form-item label="到达机场"><el-autocomplete v-model="editItem.arrivalAirportNameDisplay" :fetch-suggestions="queryAirport" @select="s => handleAirportSelect(s, 'formArrival')" /></el-form-item>
          <el-form-item label="到达机场航站楼"><el-input v-model="editItem.arrivalTerminal" /></el-form-item>
          <el-form-item label="下机方式"><el-select v-model="editItem.deboardingMethod" clearable filterable allow-create placeholder="未记录"><el-option label="廊桥" value="廊桥" /><el-option label="摆渡车" value="摆渡车" /><el-option label="远机位步行" value="远机位步行" /><el-option label="其他" value="其他" /></el-select></el-form-item>
          <el-form-item label="到达时间"><el-date-picker v-model="editItem.arrivalTime" type="datetime" format="YYYY/M/D HH:mm" value-format="YYYY-MM-DDTHH:mm" editable /></el-form-item>
          <el-form-item label="备注" class="wide-field"><el-input v-model="editItem.note" type="textarea" :rows="2" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button></template>
    </el-dialog>

    <!-- 分页 -->
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFlightList, deleteFlightTicketById, deleteFlightTickets, insertFlightTicket, updateFlightTicketById } from '@/modules/travel/apis/flightTickets.js'
import { useResourceSearch } from '@/modules/travel/composables/useResourceSearch.js'
import TravelDataTools from '@/modules/travel/components/TravelDataTools.vue'
import TravelRecordExportButton from '@/modules/travel/components/TravelRecordExportButton.vue'

const { queryAirport } = useResourceSearch()
const formatTableDateTime = (_row, _column, value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-'
const formatAirport = (row, side) => {
  const name = row[`${side}AirportName`]
  const icao = row[`${side}Icao`]
  return [name, icao ? `(${icao})` : ''].filter(Boolean).join(' ') || '-'
}
const formatStopovers = stopovers => Array.isArray(stopovers)
  ? stopovers.map(item => item?.name || item?.icao).filter(Boolean).join('、') || '-'
  : '-'

// 选择后：输入框显示中文(ICAO)，但真实提交字段保存 ICAO
// type: queryDeparture | queryArrival | formDeparture | formArrival | formStopover
const handleAirportSelect = (suggestion, type) => {
  const code = suggestion?.icao
  if (!code) return

  const display = suggestion?.value || `${suggestion?.name || ''} (${code})`
  const name = suggestion?.name || ''

  if (type === 'queryDeparture') {
  queryForm.departureIcao = code
    queryForm.departureAirportNameDisplay = display
  }
  if (type === 'queryArrival') {
  queryForm.arrivalIcao = code
    queryForm.arrivalAirportNameDisplay = display
  }

  const item = editItem.value
  if (!item) return

  if (type === 'formDeparture') {
  // 提交后端：ICAO
  item.departureIcao = code
  // 展示/入库中文名
  item.departureAirportName = name
    item.departureAirportNameDisplay = display
  }
  if (type === 'formArrival') {
  item.arrivalIcao = code
  item.arrivalAirportName = name
    item.arrivalAirportNameDisplay = display
  }
  if (type === 'formStopover') {
  // 表结构：经停机场只存中文
    item.stopovers = [{ sequence: 1, icao: code, name }]
    item.stopoverDisplay = display
  }
}

// ===================== 数据 =====================
const flightList = ref([])
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
let loadSequence = 0
const selectedRows = ref([])
const tableRef = ref(null)
const dialogVisible = ref(false)
const editItem = ref(null)

// 后端分页信息（total/size/current/records...）
const pageInfo = ref({ total: 0 })

onMounted(async () => {
  await loadData()
})

// 用 ICAO 回填显示文本（列表加载/刷新时）
const fillAirportDisplays = (list = []) => {
  return (list || []).map(item => {
  // 入库字段：airport 是中文名，icao 是码；展示两者组合
  item.departureAirportNameDisplay = item.departureAirportName && item.departureIcao ? `${item.departureAirportName} (${item.departureIcao})` : (item.departureAirportName || item.departureIcao || '')
  item.arrivalAirportNameDisplay = item.arrivalAirportName && item.arrivalIcao ? `${item.arrivalAirportName} (${item.arrivalIcao})` : (item.arrivalAirportName || item.arrivalIcao || '')

  // 经停机场表里只有中文，展示直接用中文即可
  const stopover = item.stopovers?.[0]
  item.stopoverDisplay = stopover ? `${stopover.name || ''}${stopover.icao ? ` (${stopover.icao})` : ''}` : ''
    return item
  })
}

const loadData = async () => {
  const sequence = ++loadSequence
  loading.value = true
  selectedRows.value = []
  tableRef.value?.clearSelection()
  try {
  const departureTimeStart = Array.isArray(queryForm.departureTimeRange)
    ? queryForm.departureTimeRange[0]
    : null
  const departureTimeEnd = Array.isArray(queryForm.departureTimeRange)
    ? queryForm.departureTimeRange[1]
    : null

  const res = await getFlightList({
    pageNum: currentPage.value,
    pageSize: pageSize.value,
    flightNo: queryForm.flightNo,
  departureIcao: queryForm.departureIcao,
  arrivalIcao: queryForm.arrivalIcao,
    departureTimeStart,
    departureTimeEnd
  })
  if (sequence !== loadSequence) return
  flightList.value = fillAirportDisplays(res.data || [])
  pageInfo.value = res.page || { total: (res.data || []).length }
  } catch (error) {
    if (sequence === loadSequence) ElMessage.error(error?.response?.data?.message || '航班记录加载失败')
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

// ===================== 查询 =====================
const queryForm = reactive({
  flightNo: '',
  // 提交后端用（ICAO）
  departureIcao: '',
  arrivalIcao: '',

  // 输入框展示用（中文 + ICAO）
  departureAirportNameDisplay: '',
  arrivalAirportNameDisplay: '',
  // [start, end]
  departureTimeRange: null
})

const doQuery = async () => {
  currentPage.value = 1
  await loadData()
}
const resetQuery = async () => {
  queryForm.flightNo = ''
  queryForm.departureIcao = ''
  queryForm.arrivalIcao = ''
  queryForm.departureAirportNameDisplay = ''
  queryForm.arrivalAirportNameDisplay = ''
  queryForm.departureTimeRange = null
  currentPage.value = 1
  await loadData()
}

// ===================== 分页 =====================
const currentPage = ref(1)
const pageSize = ref(10)

// 后端分页：当前页数据就是 flightList
const pageData = computed(() => flightList.value)

// ===================== 编辑 / 新增 统一逻辑 =====================
const editIndex = ref(-1)
const editBackup = ref(null)
// 进入编辑
const handleEdit = (item, idx) => {
  if (editIndex.value !== -1 || saving.value) return
  editIndex.value = idx
  editBackup.value = JSON.parse(JSON.stringify(item))
  editItem.value = JSON.parse(JSON.stringify(item))
  dialogVisible.value = true
}

// 取消
const cancelEdit = () => {
  editIndex.value = -1
  editBackup.value = null
  editItem.value = null
}

// 保存
const saveEdit = async () => {
  if (saving.value) return
  const item = editItem.value
  if (!item) return
  if (!item.flightNo?.trim() || !item.departureIcao || !item.arrivalIcao || !item.departureTime) {
    ElMessage.warning('请填写航班号、起降机场和起飞时间')
    return
  }
  if (item.arrivalTime && new Date(item.arrivalTime).getTime() < new Date(item.departureTime).getTime()) {
    ElMessage.warning('到达时间不能早于起飞时间')
    return
  }

  // 无变更：直接退出编辑，不请求后端
  if (editBackup.value) {
    const now = JSON.stringify(item)
    const old = JSON.stringify(editBackup.value)
    if (now === old) {
      editIndex.value = -1
      editBackup.value = null
      dialogVisible.value = false
      ElMessage.info(item.flightId ? '没有修改内容' : '空白记录已取消')
      return
    }
  }

  saving.value = true
  try {
    // 修改已有数据
    if (item.flightId) {
      await updateFlightTicketById(item)
      ElMessage.success('修改成功')
    }

    // 新增数据
    else {
      await insertFlightTicket(item)
      ElMessage.success('新增成功')
    }

    // 退出编辑状态
    editIndex.value = -1
    editBackup.value = null
    editItem.value = null
    dialogVisible.value = false

    if (!item.flightId) currentPage.value = 1
    await loadData()
  } catch (err) {
    ElMessage.error(err?.response?.data?.message || err?.response?.data?.msg || '保存失败，请检查填写内容')
  } finally {
    saving.value = false
  }
}

// 新增 = 推入空数据
const handleAdd = async () => {
  if (editIndex.value !== -1 || saving.value) return
  if (currentPage.value !== 1) {
    currentPage.value = 1
    await loadData()
  }
  const newItem = {
    flightNo: '', aircraftRegistration: '', aircraftType: '', airline: '',
    departureAirportName: '', departureAirportNameDisplay: '', departureTerminal: '', boardingMethod: '', departureIcao: '',
    departureTime: '', distanceKm: 0,
  arrivalAirportName: '', arrivalAirportNameDisplay: '', arrivalTerminal: '', deboardingMethod: '', arrivalIcao: '',
    arrivalTime: '', stopovers: [], seatNo: '', note: '',
  }
  newItem.stopoverDisplay = ''
  editIndex.value = 0
  editBackup.value = JSON.parse(JSON.stringify(newItem))
  editItem.value = newItem
  dialogVisible.value = true
}

// ===================== 删除 =====================
const handleDelete = async (item) => {
  if (deleting.value || saving.value) return
  try {
    await ElMessageBox.confirm(`确定删除航班 ${item.flightNo || ''} 吗？`, '删除航班', { type: 'warning' })
    deleting.value = true
    await deleteFlightTicketById(item.flightId)
    ElMessage.success('删除成功')
    if (pageData.value.length === 1 && currentPage.value > 1) currentPage.value -= 1
    await loadData()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(err?.response?.data?.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

// ===================== 刷新 =====================
const refresh = async () => {
  await loadData()
  ElMessage.success('刷新成功')
}

const clearTableSelection = () => {
  selectedRows.value = []
  tableRef.value?.clearSelection()
}

const handlePageSizeChange = () => {
  currentPage.value = 1
  loadData()
}

const deleteSelected = async () => {
  if (deleting.value || saving.value) return
  const selected = [...selectedRows.value]
  if (!selected.length) return
  try {
    await ElMessageBox.confirm(`确定删除当前页选中的 ${selected.length} 条机票记录吗？此操作不可撤销。`, '批量删除', { type: 'warning' })
  } catch { return }
  deleting.value = true
  try {
    const result = await deleteFlightTickets(selected.map(row => row.flightId))
    const deleted = Number(result?.data ?? result) || 0
    clearTableSelection()
    const remaining = Math.max(0, (pageInfo.value.total || 0) - deleted)
    if (currentPage.value > 1 && remaining <= (currentPage.value - 1) * pageSize.value) currentPage.value -= 1
    await loadData()
    if (deleted === selected.length) ElMessage.success(`已删除 ${deleted} 条机票记录`)
    else ElMessage.warning(`成功删除 ${deleted} 条，另有 ${selected.length - deleted} 条已不存在`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.response?.data?.msg || '批量删除失败，记录未变更')
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.flight-manage-page {
  width: 100%;
  margin: 0 auto;
}
.page-title {
  margin-bottom: 18px;
  color: #1d3035;
  font-size: 24px;
  font-weight: 720;
  line-height: 1.25;
}
.query-card {
  margin-bottom: 16px;
}
.tool-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 15px;
  padding: 12px;
  background: #fff;
  border: 1px solid #e5ebec;
  border-radius: 6px;
}
.bulk-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 0 0 12px; color: #667085; font-size: 13px; }
.pagination-bar { display: flex; justify-content: center; margin-top: 20px; padding-bottom: 12px; }
.record-form .grid-form { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0 16px; }
.record-form :deep(.el-form-item) { margin-bottom: 10px; }
.record-form :deep(.el-form-item__content > .el-input), .record-form :deep(.el-form-item__content > .el-autocomplete), .record-form :deep(.el-form-item__content > .el-date-editor), .record-form :deep(.el-form-item__content > .el-select), .record-form :deep(.el-form-item__content > .el-input-number) { width: 100%; }
.wide-field { grid-column: 1 / -1; }
@media (max-width: 760px) {
  .page-title { font-size: 21px; margin-bottom: 16px; }
  .tool-bar { align-items: stretch; flex-direction: column; }
  .tool-bar > :deep(.el-button), .tool-bar > :deep(.travel-data-tools) { width: 100%; margin: 0; }
  .pagination-bar { justify-content: flex-start; overflow-x: auto; }
  .pagination-bar :deep(.el-pagination) { flex-wrap: nowrap; min-width: max-content; }
}
</style>
