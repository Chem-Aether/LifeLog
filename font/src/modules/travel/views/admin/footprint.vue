<template>
  <div v-if="isEditing" v-loading="saving || uploadingCover" class="travel-edit-page">
    <header class="edit-header"><div><el-button text @click="requestClose">返回足迹管理</el-button><h1>{{ form.footprintId ? '编辑足迹' : '新增足迹' }}</h1></div><div class="edit-actions"><el-button :disabled="saving || uploadingCover" @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" :disabled="uploadingCover" @click="save">保存并点亮</el-button></div></header>
    <section class="edit-panel"><el-form label-width="90px"><el-form-item label="地图选点"><FootprintMapPicker :longitude="form.longitude" :latitude="form.latitude" @pick="handleMapPick" @clear="clearMapPoint" /></el-form-item><el-form-item label="当前地区"><div class="region-result"><span>{{ selectedRegionName || '选点后自动识别' }}</span></div></el-form-item><div class="field-grid"><el-form-item label="记录性质" required><el-radio-group v-model="form.visitType"><el-radio-button value="travel">旅行地点</el-radio-button><el-radio-button value="transit">途经城市</el-radio-button></el-radio-group></el-form-item><el-form-item label="地点名称" required><el-input v-model="form.placeName" placeholder="选择 POI 后自动填写，也可手动输入" maxlength="100" /></el-form-item><el-form-item label="到访日期"><el-date-picker v-model="form.visitDate" type="date" format="YYYY/M/D" value-format="YYYY-MM-DD" editable placeholder="选择日期" style="width:100%" /></el-form-item></div><el-form-item label="旅行心得"><el-input v-model="form.note" type="textarea" :rows="4" placeholder="记录这次旅行的见闻、感受或特别回忆" maxlength="1000" show-word-limit /></el-form-item><el-form-item label="旅行图片"><MediaUploadEditor v-model="coverItems" :upload="uploadTravelMedia" :before-remove="beforeRemoveCover" :url-for="mediaUrl" :multiple="true" :max-count="9" :disabled="saving" @busy-change="uploadingCover = $event" /></el-form-item></el-form></section>
  </div>
  <div v-else v-loading="loading || saving || deleting" class="footprint-page">
    <div class="page-header">
      <div>
        <h1>足迹与点亮地区</h1>
        <p>保存去过或途经的地点，行政区根据坐标实时识别。</p>
      </div>
    </div>

    <el-card class="query-card" shadow="never">
      <div class="query-filters">
        <el-input v-model="regionFilter" clearable placeholder="按地区搜索，如南京" style="width: 220px" />
        <el-input v-model="placeFilter" clearable placeholder="按地点名称搜索" style="width: 220px" />
        <el-select v-model="visitTypeFilter" aria-label="记录类型筛选" style="width: 150px">
          <el-option label="全部类型" value="all" />
          <el-option label="旅行地点" value="travel" />
          <el-option label="途经城市" value="transit" />
        </el-select>
        <el-button :disabled="loading || saving || deleting" @click="loadRows">刷新</el-button>
        <span class="count">共 {{ total }} 条</span>
      </div>
    </el-card>

    <div class="action-bar">
      <el-button type="primary" :disabled="saving || deleting" @click="openCreate">+ 新增足迹</el-button>
      <TravelDataTools type="footprint" :disabled="loading || saving || deleting" @imported="loadRows" />
    </div>

    <div class="bulk-toolbar">
      <span>已选择 {{ selectedRows.length }} 条（当前页）</span>
      <el-button type="danger" plain :disabled="!selectedRows.length || deleting" :loading="deleting" @click="deleteSelected">批量删除</el-button>
    </div>

    <el-table
      ref="tableRef"
      :data="rows"
      row-key="footprintId"
      :default-sort="{ prop: 'visitDate', order: 'descending' }"
      stripe
      empty-text="暂无足迹记录"
      @sort-change="handleSortChange"
      @selection-change="selectedRows = $event"
    >
      <el-table-column type="selection" width="48" />
      <el-table-column prop="regionName" label="点亮地区" min-width="220" />
      <el-table-column prop="placeName" label="地点名称" min-width="180" sortable="custom" />
      <el-table-column prop="visitType" label="记录性质" width="110" sortable="custom">
        <template #default="scope">
          <el-tag :type="scope.row.visitType === 'transit' ? 'info' : 'success'">
            {{ scope.row.visitType === 'transit' ? '途经城市' : '旅行地点' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="visitDate" label="到访日期" width="140" sortable="custom" />
      <el-table-column label="坐标" min-width="190">
        <template #default="scope">
          <span v-if="scope.row.longitude != null">
            {{ Number(scope.row.longitude).toFixed(5) }}, {{ Number(scope.row.latitude).toFixed(5) }}
          </span>
          <span v-else class="muted">未选点</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="scope">
          <el-button link type="primary" :disabled="saving || deleting" @click="openEdit(scope.row)">编辑</el-button>
          <el-button link type="danger" :disabled="deleting" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="loadRows"
        @size-change="handlePageSizeChange"
      />
    </div>

  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addFootprint, deleteFootprint, deleteFootprints, getFootprintById, getFootprintsPage, updateFootprint } from '@/modules/travel/apis/travel.js'
import { deleteTravelMedia, mediaUrl, uploadTravelMedia } from '@/modules/travel/apis/media.js'
import MediaUploadEditor from '@/components/MediaUploadEditor.vue'
import FootprintMapPicker from '@/modules/travel/components/FootprintMapPicker.vue'
import TravelDataTools from '@/modules/travel/components/TravelDataTools.vue'

const rows = ref([])
const total = ref(0)
const route = useRoute()
const router = useRouter()
const isEditing = computed(() => route.name === 'TravelFootprintEdit')
let skipLeaveGuard = false
const loading = ref(false)
const saving = ref(false)
const uploadingCover = ref(false)
const deleting = ref(false)
const originalMediaIds = ref([])
const pendingMediaDeletion = ref(new Set())
const selectedRows = ref([])
let loadSequence = 0
const tableRef = ref(null)
const regionFilter = ref('')
const placeFilter = ref('')
const visitTypeFilter = ref('all')
const currentPage = ref(1)
const pageSize = ref(10)
const sortState = reactive({ prop: 'visitDate', order: 'descending' })
const selectedRegionName = ref('')
const editSnapshot = ref(null)
let lastEditId = String(route.params.id || '')
const form = reactive({
  footprintId: null,
  placeName: '',
  visitType: 'travel',
  visitDate: null,
  longitude: null,
  latitude: null,
  note: '',
  mediaIds: []
})
const coverItems = computed({
  get: () => form.mediaIds.map((mediaId, index) => ({ mediaId, url: mediaUrl(mediaId), contentType: 'image/jpeg', name: `足迹图片 ${index + 1}` })),
  set: items => { form.mediaIds = items.map(item => item.mediaId) },
})

let filterTimer
watch([regionFilter, placeFilter, visitTypeFilter], () => {
  currentPage.value = 1
  clearSelection()
  clearTimeout(filterTimer)
  filterTimer = setTimeout(loadRows, 250)
})
watch(currentPage, clearSelection)

onMounted(async () => {
  await loadRows()
  if (!isEditing.value) return
  const row = route.params.id ? await getEditRecord(route.params.id) : null
  if (route.params.id && !row) { ElMessage.error('足迹记录不存在'); router.replace('/travel/admin/footprint'); return }
  if (row) openEditData(row)
  else { resetForm(); editSnapshot.value = snapshotForm() }
})

watch(() => route.fullPath, async () => {
  if (!isEditing.value) { pendingMediaDeletion.value = new Set(); lastEditId = ''; return }
  const currentEditId = String(route.params.id || '')
  if (currentEditId !== lastEditId) pendingMediaDeletion.value = new Set()
  lastEditId = currentEditId
  await loadRows()
  const row = route.params.id ? await getEditRecord(route.params.id) : null
  if (route.params.id && !row) { ElMessage.error('足迹记录不存在'); router.replace('/travel/admin/footprint'); return }
  if (row) openEditData(row)
  else { resetForm(); editSnapshot.value = snapshotForm() }
})

async function loadRows() {
  const sequence = ++loadSequence
  loading.value = true
  try {
    const res = await getFootprintsPage({
      region: regionFilter.value.trim() || undefined,
      keyword: placeFilter.value.trim() || undefined,
      visitType: visitTypeFilter.value === 'all' ? undefined : visitTypeFilter.value,
      page: currentPage.value,
      pageSize: pageSize.value,
      sortBy: sortState.prop || 'visitDate',
      sortOrder: sortState.order === 'ascending' ? 'asc' : 'desc',
    })
    if (sequence !== loadSequence) return
    const result = res.data || {}
    rows.value = result.records || []
    total.value = Number(result.total || 0)
    clearSelection()
  } catch (error) {
    if (sequence === loadSequence) ElMessage.error(error?.response?.data?.message || '足迹记录加载失败')
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

async function getEditRecord(id) {
  try { return (await getFootprintById(id)).data }
  catch { return null }
}

function handleSortChange({ prop, order }) {
  sortState.prop = prop || ''
  sortState.order = order || ''
  currentPage.value = 1
  clearSelection()
  loadRows()
}

function handlePageSizeChange() {
  currentPage.value = 1
  clearSelection()
  loadRows()
}

function clearSelection() {
  selectedRows.value = []
  tableRef.value?.clearSelection()
}

function resetForm() {
  Object.assign(form, {
    footprintId: null,
    placeName: '',
    visitType: 'travel',
    visitDate: null,
    longitude: null,
    latitude: null,
    note: '',
    mediaIds: []
  })
  selectedRegionName.value = ''
  originalMediaIds.value = []
  pendingMediaDeletion.value = new Set()
  editSnapshot.value = null
}

function openCreate() {
  router.push({ name: 'TravelFootprintEdit' })
}

function openEdit(row) {
  router.push({ name: 'TravelFootprintEdit', params: { id: row.footprintId } })
}

function openEditData(row) {
  Object.assign(form, {
    footprintId: row.footprintId,
    placeName: row.placeName,
    visitType: row.visitType || 'travel',
    visitDate: row.visitDate || null,
    longitude: row.longitude == null ? null : Number(row.longitude),
    latitude: row.latitude == null ? null : Number(row.latitude),
    note: row.note || '',
    mediaIds: Array.isArray(row.mediaIds) ? [...row.mediaIds] : []
  })
  originalMediaIds.value = [...form.mediaIds]
  pendingMediaDeletion.value = new Set()
  editSnapshot.value = JSON.stringify({
    placeName: form.placeName.trim(),
    visitType: form.visitType,
    visitDate: form.visitDate,
    longitude: form.longitude,
    latitude: form.latitude,
    note: form.note.trim() || null,
    mediaIds: form.mediaIds
  })
  selectedRegionName.value = row.regionName || ''
}

async function beforeRemoveCover(mediaId) {
  if (!mediaId) return
  if (form.footprintId && originalMediaIds.value.includes(mediaId)) {
    pendingMediaDeletion.value = new Set([...pendingMediaDeletion.value, mediaId])
    return
  }
  await deleteTravelMedia(mediaId)
}

function hasUnsavedChanges() {
  return isEditing.value && editSnapshot.value !== snapshotForm()
}
function snapshotForm() { return JSON.stringify({ placeName: form.placeName.trim(), visitType: form.visitType, visitDate: form.visitDate || null, longitude: form.longitude, latitude: form.latitude, note: form.note.trim() || null, mediaIds: form.mediaIds }) }
async function requestClose() {
  if (saving.value || uploadingCover.value) return
  if (hasUnsavedChanges()) { try { await ElMessageBox.confirm('当前内容尚未保存，确定放弃修改并离开吗？', '未保存的修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }) } catch { return } }
  skipLeaveGuard = true
  await router.push('/travel/admin/footprint')
}
onBeforeRouteLeave(async () => {
  if (skipLeaveGuard) { skipLeaveGuard = false; return true }
  if (!hasUnsavedChanges() || saving.value || uploadingCover.value) return true
  try { await ElMessageBox.confirm('当前内容尚未保存，确定放弃修改并离开吗？', '未保存的修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }); return true } catch { return false }
})

async function handleMapPick(point) {
  form.longitude = point.longitude
  form.latitude = point.latitude
  if (point.suggestedName && !form.placeName.trim()) {
    form.placeName = point.suggestedName
  }
  selectedRegionName.value = point.fullName || point.districtName || ''
}

function clearMapPoint() {
  form.longitude = null
  form.latitude = null
  selectedRegionName.value = ''
}

async function save() {
  if (saving.value) return
  if (!form.placeName.trim()) return ElMessage.warning('请输入地点名称')

  const payload = {
    placeName: form.placeName.trim(),
    visitType: form.visitType,
    visitDate: form.visitDate || null,
    longitude: form.longitude,
    latitude: form.latitude,
    note: form.note.trim() || null,
    mediaIds: form.mediaIds
  }
  if (form.footprintId && editSnapshot.value === JSON.stringify(payload) && pendingMediaDeletion.value.size === 0) {
    skipLeaveGuard = true
    await router.push('/travel/admin/footprint')
    ElMessage.info('没有修改内容')
    return
  }
  saving.value = true
  try {
    if (form.footprintId) await updateFootprint(form.footprintId, payload)
    else {
      await addFootprint(payload)
      currentPage.value = 1
    }
    for (const mediaId of pendingMediaDeletion.value) {
      try {
        await deleteTravelMedia(mediaId)
        const remaining = new Set(pendingMediaDeletion.value)
        remaining.delete(mediaId)
        pendingMediaDeletion.value = remaining
      } catch {
        editSnapshot.value = snapshotForm()
        ElMessage.warning('足迹已保存，但图片文件删除失败；再次保存可重试')
        return
      }
    }
    skipLeaveGuard = true
    await router.push('/travel/admin/footprint')
    ElMessage.success('保存成功，地图点亮数据已更新')
    await loadRows()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存失败，请检查填写内容')
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  if (deleting.value || saving.value) return
  try {
    await ElMessageBox.confirm(`确定删除“${row.placeName}”吗？`, '删除足迹', { type: 'warning' })
    deleting.value = true
    await deleteFootprint(row.footprintId)
    ElMessage.success('删除成功')
    if (rows.value.length === 1 && currentPage.value > 1) currentPage.value -= 1
    await loadRows()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('删除失败')
  } finally {
    deleting.value = false
  }
}

async function deleteSelected() {
  if (deleting.value || saving.value) return
  const selected = [...selectedRows.value]
  if (!selected.length) return
  try {
    await ElMessageBox.confirm(`确定删除当前页选中的 ${selected.length} 条足迹记录吗？此操作不可撤销。`, '批量删除', { type: 'warning' })
  } catch { return }
  deleting.value = true
  try {
    const result = await deleteFootprints(selected.map(row => row.footprintId))
    const deleted = Number(result?.data ?? result) || 0
    clearSelection()
    if (selected.length === rows.value.length && currentPage.value > 1) currentPage.value -= 1
    await loadRows()
    if (deleted === selected.length) ElMessage.success(`已删除 ${deleted} 条足迹记录`)
    else ElMessage.warning(`成功删除 ${deleted} 条，另有 ${selected.length - deleted} 条已不存在`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.response?.data?.msg || '批量删除失败，记录未变更')
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.travel-edit-page { display:flex; flex-direction:column; gap:16px; min-height:100%; color:#1d3035; }
.edit-header { display:flex; align-items:center; justify-content:space-between; gap:16px; padding:0 0 14px; border-bottom:1px solid #e5ebec; }
.edit-header h1 { margin:6px 0 0; font-size:22px; font-weight:700; }
.edit-actions { display:flex; align-items:center; gap:8px; }
.edit-panel { padding:20px; background:#fff; border:1px solid #e5ebec; border-radius:7px; }
.footprint-page { width: 100%; margin: 0 auto; }
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 12px; }
.query-card { border-color: #d9e3e4; }
.query-card :deep(.el-card__body) { padding: 10px 18px !important; }
.action-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 0 0 15px; padding: 12px; border: 1px solid #d9e3e4; border-radius: 7px; background: #fff; box-shadow: 0 2px 8px rgba(29,48,53,.035); }
h1 { margin: 0 0 6px; color: #1d3035; font-size: 24px; font-weight: 720; line-height: 1.25; }
p { margin: 0; color: #65777c; font-size: 13px; }
.query-filters { display: flex; align-items: center; justify-content: flex-start; flex-wrap: wrap; gap: 10px; min-height: 38px; }
.bulk-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 0 0 12px; padding: 12px; border: 1px solid #d9e3e4; border-radius: 7px; background: #fff; color: #65777c; font-size: 13px; box-shadow: 0 2px 8px rgba(29,48,53,.035); }
.count { color: #65777c; font-size: 13px; white-space: nowrap; }
.muted { color: #89999d; }
.footprint-page :deep(.picker-shell) { width: 100%; }
.region-result { width: 100%; min-height: 34px; padding: 0 10px; display: flex; align-items: center; justify-content: space-between; background: #f3f7f7; border: 1px solid #d9e3e4; border-radius: 5px; color: #2b3d42; }
.field-grid { display: grid; grid-template-columns: 1fr 1fr; column-gap: 18px; }
.field-grid :deep(.el-form-item:first-child) { grid-column: 1 / -1; }
.more-fields { margin: 2px 0 0; border-top: 0; }
.more-fields :deep(.el-collapse-item__header) { height: 36px; color: #667085; border-bottom: 0; }
.more-fields :deep(.el-collapse-item__wrap) { border-bottom: 0; }
.more-fields :deep(.el-form-item) { margin-bottom: 8px; }
.region-option { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pagination-bar { display: flex; justify-content: center; margin-top: 20px; padding-bottom: 12px; }
@media (max-width: 700px) {
  .page-header { align-items: stretch; flex-direction: column; }
  .action-bar { align-items: stretch; flex-direction: column; }
  .query-filters { align-items: stretch; justify-content: flex-start; flex-direction: column; gap: 10px; }
  .query-filters :deep(.el-input) { width: 100% !important; }
  .query-filters :deep(.el-select) { width: 100% !important; }
  .bulk-toolbar { align-items: stretch; flex-direction: column; }
  .bulk-toolbar :deep(.el-button) { margin: 0; }
  .action-bar > :deep(.el-button), .action-bar > :deep(.travel-data-tools) { width: 100%; margin: 0; }
  .action-bar :deep(.travel-data-tools) { align-items: stretch; flex-direction: column; }
  .action-bar :deep(.travel-data-tools .el-button) { width: 100%; margin: 0; }
  .pagination-bar { justify-content: flex-start; overflow-x: auto; }
  .pagination-bar :deep(.el-pagination) { flex-wrap: nowrap; min-width: max-content; }
  .field-grid { grid-template-columns: 1fr; }
  .field-grid :deep(.el-form-item:first-child) { grid-column: auto; }
  .more-fields { margin-left: 0; }
}
</style>
