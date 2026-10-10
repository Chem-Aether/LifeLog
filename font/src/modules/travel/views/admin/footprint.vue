<template>
  <div v-loading="loading || saving || deleting" class="footprint-page">
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
        <span class="count">共 {{ filteredRows.length }} 条</span>
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
      :data="pageRows"
      row-key="footprintId"
      :default-sort="{ prop: 'visitDate', order: 'descending' }"
      stripe
      empty-text="暂无足迹记录"
      @sort-change="handleSortChange"
      @selection-change="selectedRows = $event"
    >
      <el-table-column type="selection" width="48" />
      <el-table-column prop="regionName" label="点亮地区" min-width="220" sortable="custom" />
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
          <TravelRecordExportButton v-if="scope.row.footprintId" type="footprint" :record-id="scope.row.footprintId" />
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
        :total="sortedRows.length"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handlePageSizeChange"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="form.footprintId ? '编辑足迹' : '新增足迹'"
      width="min(920px, 94vw)"
      destroy-on-close
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
    >
      <el-form label-width="90px">
        <el-form-item label="地图选点">
          <FootprintMapPicker
            :longitude="form.longitude"
            :latitude="form.latitude"
            @pick="handleMapPick"
            @clear="clearMapPoint"
          />
        </el-form-item>
        <el-form-item label="当前地区">
          <div class="region-result">
            <span>{{ selectedRegionName || '选点后自动识别' }}</span>
          </div>
        </el-form-item>
        <div class="field-grid">
          <el-form-item label="记录性质" required>
            <el-radio-group v-model="form.visitType">
              <el-radio-button value="travel">旅行地点</el-radio-button>
              <el-radio-button value="transit">途经城市</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="地点名称" required>
            <el-input v-model="form.placeName" placeholder="选择 POI 后自动填写，也可手动输入" maxlength="100" />
          </el-form-item>
          <el-form-item label="到访日期">
            <el-date-picker
              v-model="form.visitDate"
              type="date"
              format="YYYY/M/D"
              value-format="YYYY-MM-DD"
              editable
              placeholder="选择日期"
              style="width: 100%"
            />
          </el-form-item>
        </div>
        <el-form-item label="旅行心得">
          <el-input
            v-model="form.note"
            type="textarea"
            :rows="4"
            placeholder="记录这次旅行的见闻、感受或特别回忆"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="旅行封面">
          <div class="cover-editor">
            <img v-if="coverPreviewUrl" :src="coverPreviewUrl" alt="足迹封面预览" />
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              :disabled="uploadingCover"
              accept=".jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif"
              :on-change="handleCoverChange"
            >
              <el-button :loading="uploadingCover">{{ form.mediaId ? '更换封面' : '上传封面' }}</el-button>
            </el-upload>
            <el-button v-if="form.mediaId" link type="danger" :disabled="uploadingCover" @click="form.mediaId = ''">移除封面</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving || uploadingCover" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="uploadingCover" @click="save">保存并点亮</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addFootprint, deleteFootprint, deleteFootprints, getFootprints, updateFootprint } from '@/modules/travel/apis/travel.js'
import { mediaUrl, uploadTravelMedia } from '@/modules/travel/apis/media.js'
import FootprintMapPicker from '@/modules/travel/components/FootprintMapPicker.vue'
import TravelDataTools from '@/modules/travel/components/TravelDataTools.vue'
import TravelRecordExportButton from '@/modules/travel/components/TravelRecordExportButton.vue'

const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const uploadingCover = ref(false)
const deleting = ref(false)
const selectedRows = ref([])
let loadSequence = 0
const tableRef = ref(null)
const regionFilter = ref('')
const placeFilter = ref('')
const visitTypeFilter = ref('all')
const currentPage = ref(1)
const pageSize = ref(10)
const sortState = reactive({ prop: 'visitDate', order: 'descending' })
const dialogVisible = ref(false)
const selectedRegionName = ref('')
const editSnapshot = ref(null)
const form = reactive({
  footprintId: null,
  placeName: '',
  visitType: 'travel',
  visitDate: null,
  longitude: null,
  latitude: null,
  note: '',
  mediaId: ''
})
const coverPreviewUrl = computed(() => mediaUrl(form.mediaId))

const filteredRows = computed(() => {
  const regionKeyword = regionFilter.value.trim().toLowerCase()
  const placeKeyword = placeFilter.value.trim().toLowerCase()
  const selectedType = visitTypeFilter.value
  return rows.value.filter(item => {
    const region = `${item.regionName || ''} ${item.regionCode || ''}`.toLowerCase()
    const place = (item.placeName || '').toLowerCase()
    return (!regionKeyword || region.includes(regionKeyword))
      && (!placeKeyword || place.includes(placeKeyword))
      && (selectedType === 'all' || (item.visitType || 'travel') === selectedType)
  })
})

const sortedRows = computed(() => {
  if (!sortState.prop || !sortState.order) return filteredRows.value
  const direction = sortState.order === 'ascending' ? 1 : -1
  return [...filteredRows.value].sort((left, right) => {
    const a = left[sortState.prop]
    const b = right[sortState.prop]
    if (a == null || a === '') return b == null || b === '' ? 0 : 1
    if (b == null || b === '') return -1
    return String(a).localeCompare(String(b), 'zh-CN', { numeric: true }) * direction
  })
})

const pageRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return sortedRows.value.slice(start, start + pageSize.value)
})

watch([regionFilter, placeFilter, visitTypeFilter], () => {
  currentPage.value = 1
  clearSelection()
})
watch(currentPage, clearSelection)

watch(() => sortedRows.value.length, total => {
  const lastPage = Math.max(1, Math.ceil(total / pageSize.value))
  if (currentPage.value > lastPage) currentPage.value = lastPage
})

onMounted(loadRows)

async function loadRows() {
  const sequence = ++loadSequence
  loading.value = true
  try {
    const res = await getFootprints()
    if (sequence !== loadSequence) return
    rows.value = res.data || []
    clearSelection()
  } catch (error) {
    if (sequence === loadSequence) ElMessage.error(error?.response?.data?.message || '足迹记录加载失败')
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function handleSortChange({ prop, order }) {
  sortState.prop = prop || ''
  sortState.order = order || ''
  currentPage.value = 1
  clearSelection()
}

function handlePageSizeChange() {
  currentPage.value = 1
  clearSelection()
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
    mediaId: ''
  })
  selectedRegionName.value = ''
  editSnapshot.value = null
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    footprintId: row.footprintId,
    placeName: row.placeName,
    visitType: row.visitType || 'travel',
    visitDate: row.visitDate || null,
    longitude: row.longitude == null ? null : Number(row.longitude),
    latitude: row.latitude == null ? null : Number(row.latitude),
    note: row.note || '',
    mediaId: row.mediaId || ''
  })
  editSnapshot.value = JSON.stringify({
    placeName: form.placeName.trim(),
    visitType: form.visitType,
    visitDate: form.visitDate,
    longitude: form.longitude,
    latitude: form.latitude,
    note: form.note.trim() || null,
    mediaId: form.mediaId || null
  })
  selectedRegionName.value = row.regionName || ''
  dialogVisible.value = true
}

async function handleMapPick(point) {
  form.longitude = point.longitude
  form.latitude = point.latitude
  if (point.suggestedName && !form.placeName.trim()) {
    form.placeName = point.suggestedName
  }
  selectedRegionName.value = point.fullName || point.districtName || ''
}

async function handleCoverChange(uploadFile) {
  if (!uploadFile.raw || uploadingCover.value) return
  uploadingCover.value = true
  try {
    const result = await uploadTravelMedia(uploadFile.raw)
    form.mediaId = result.media_id
    ElMessage.success('封面上传成功')
  } catch (error) {
    ElMessage.error(error?.response?.data?.detail || '封面上传失败')
  } finally {
    uploadingCover.value = false
  }
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
    mediaId: form.mediaId || null
  }
  if (form.footprintId && editSnapshot.value === JSON.stringify(payload)) {
    dialogVisible.value = false
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
    dialogVisible.value = false
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
.cover-editor { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.cover-editor img { width: 120px; height: 76px; object-fit: cover; border: 1px solid #d9e3e4; border-radius: 5px; }
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
