<template>
  <div class="travel-data-tools">
    <template v-if="type === 'trajectory'">
      <el-button type="primary" :disabled="disabled" @click="emit('create')">新增轨迹</el-button>
      <el-button :disabled="disabled || importLoading" :loading="importLoading" @click="emit('import')">导入</el-button>
      <el-button type="primary" :disabled="disabled || exportDisabled || exportLoading" @click="openExportDialog">导出</el-button>
    </template>
    <template v-else>
      <el-button :disabled="disabled || Boolean(busy)" @click="importDialogVisible = true">{{ type === 'footprint' ? '导入 CSV' : '导入' }}</el-button>
      <el-button type="primary" :disabled="disabled || Boolean(busy)" @click="exportDialogVisible = true">导出</el-button>
    </template>

    <el-dialog v-model="importDialogVisible" title="导入数据" width="min(520px, 92vw)" destroy-on-close @closed="resetImport">
      <el-form label-width="90px">
        <el-form-item label="导入方式">
          <el-select v-model="importMode" :disabled="Boolean(busy)" style="width: 100%">
            <el-option label="新增导入" value="append" />
            <el-option label="按ID更新" value="update" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件格式">
          <div class="format-row">
            <el-tag>{{ formatLabel(importExtension) }}</el-tag>
            <el-button link type="primary" :loading="busy === 'template'" :disabled="Boolean(busy)" @click="downloadTemplate">下载导入模板</el-button>
          </div>
        </el-form-item>
        <el-form-item label="数据文件">
          <el-upload
            ref="uploadRef"
            :accept="fileExtension"
            :auto-upload="false"
            :disabled="Boolean(busy)"
            :on-change="selectFile"
            :on-remove="() => selectedFile = null"
          >
            <el-button :disabled="Boolean(busy)">选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="Boolean(busy)" @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedFile || Boolean(busy) || importLoading" :loading="busy === 'import' || importLoading" @click="submitImport">开始导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="exportDialogVisible" title="导出数据" width="min(520px, 92vw)" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item v-if="type === 'trajectory'" label="导出范围">
          <el-radio-group v-model="exportScope">
            <el-radio-button value="selected" :disabled="!exportSelectedCount">所选轨迹</el-radio-button>
            <el-radio-button value="page" :disabled="!exportPageCount">当前页</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-else label="导出范围">
          <span>全部{{ categoryLabel }}记录</span>
        </el-form-item>
        <el-form-item label="文件格式">
          <el-radio-group v-if="type !== 'trajectory'" v-model="exportFormat" :disabled="Boolean(busy)">
            <el-radio-button v-for="format in exportFormats" :key="format.value" :value="format.value">{{ format.label }}</el-radio-button>
          </el-radio-group>
          <el-tag v-else>GPX (.gpx)</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="Boolean(busy)" @click="exportDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy === 'export' || exportLoading" :disabled="Boolean(busy) || exportLoading" @click="submitExport">导出文件</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportTravelData, exportTravelTemplate, importTravelData } from '@/modules/travel/apis/travel.js'

const props = defineProps({
  type: { type: String, required: true, validator: value => ['flight', 'train', 'footprint', 'trajectory'].includes(value) },
  disabled: { type: Boolean, default: false },
  importLoading: { type: Boolean, default: false },
  exportLoading: { type: Boolean, default: false },
  exportDisabled: { type: Boolean, default: false },
  exportSelectedCount: { type: Number, default: 0 },
  exportPageCount: { type: Number, default: 0 }
})
const emit = defineEmits(['imported', 'create', 'import', 'export'])
const busy = ref('')
const importMode = ref('append')
const importDialogVisible = ref(false)
const exportDialogVisible = ref(false)
const selectedFile = ref(null)
const uploadRef = ref(null)
const exportScope = ref('page')
const categoryLabel = props.type === 'flight' ? '机票' : props.type === 'train' ? '铁路行程' : '足迹'
const fileExtension = props.type === 'train' ? '.gpkg' : props.type === 'footprint' ? '.csv' : props.type === 'trajectory' ? '.csv,.gpx' : '.xlsx'
const importExtension = fileExtension.slice(1)
const exportFormats = props.type === 'footprint'
  ? [{ value: 'xlsx', label: 'Excel (.xlsx)' }, { value: 'gpkg', label: 'GeoPackage (.gpkg)' }]
  : [{ value: importExtension, label: formatLabel(importExtension) }]
const exportFormat = ref(exportFormats[0].value)

function formatLabel(format) {
  if (format === 'gpkg') return 'GeoPackage (.gpkg)'
  if (format === 'csv') return 'CSV (.csv)'
  return 'Excel (.xlsx)'
}

function saveBlob(response, fallbackName) {
  const blob = response.data
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fallbackName
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

async function exportAll() {
  busy.value = 'export'
  try {
    saveBlob(await exportTravelData(props.type, exportFormat.value), `${categoryLabel}.${exportFormat.value}`)
    exportDialogVisible.value = false
  } catch {
    ElMessage.error('导出失败')
  } finally {
    busy.value = ''
  }
}

async function downloadTemplate() {
  busy.value = 'template'
  try {
    saveBlob(await exportTravelTemplate(props.type), `${categoryLabel}导入模板.${importExtension}`)
  } catch {
    ElMessage.error('模板下载失败')
  } finally {
    busy.value = ''
  }
}

function openExportDialog() {
  exportScope.value = props.exportSelectedCount ? 'selected' : 'page'
  exportDialogVisible.value = true
}

function submitImport() {
  importSelected()
}

function submitExport() {
  if (props.type === 'trajectory') {
    emit('export', exportScope.value)
    exportDialogVisible.value = false
    return
  }
  exportAll()
}

function selectFile(uploadFile) {
  const file = uploadFile.raw
  if (!file) return
  if (!file.name.toLowerCase().endsWith(fileExtension)) {
    ElMessage.warning(`请选择 ${fileExtension} 格式文件`)
    uploadRef.value?.clearFiles()
    return
  }
  selectedFile.value = file
}

async function importSelected() {
  const file = selectedFile.value
  if (!file) return ElMessage.warning('请先选择导入文件')
  busy.value = 'import'
  try {
    const report = await importTravelData(file, props.type, importMode.value)
    importDialogVisible.value = false
    emit('imported')
    const errors = report.errors || []
    const summary = `成功导入 ${report.totalImported || 0} 条${categoryLabel}记录。`
    if (errors.length) {
      const shownErrors = errors.slice(0, 30).map(escapeHtml).join('<br>')
      const more = errors.length > 30 ? `<br>另有 ${errors.length - 30} 条错误未显示。` : ''
      await ElMessageBox.alert(`${summary}<br><br>${shownErrors}${more}`, '导入完成，部分行未导入', { dangerouslyUseHTMLString: true, type: 'warning' })
    } else {
      ElMessage.success(summary)
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.response?.data?.msg || '导入失败，请检查文件格式')
  } finally {
    busy.value = ''
    resetImport()
  }
}

function resetImport() {
  selectedFile.value = null
  uploadRef.value?.clearFiles()
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character])
}
</script>

<style scoped>
.travel-data-tools { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.format-row { display: flex; align-items: center; gap: 12px; }
</style>
