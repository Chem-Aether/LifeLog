<template>
  <el-dialog :model-value="modelValue" title="批量导入轨迹" width="min(760px, 94vw)" destroy-on-close
    :close-on-click-modal="!parsing && !importing" @update:model-value="emit('update:modelValue', $event)">
    <div class="import-summary">
      <span>选择多个 CSV / GPX 文件；每个 CSV 生成一条轨迹，GPX 中的多条轨迹分别导入。</span>
      <el-button link type="primary" @click="downloadTrajectoryCsvTemplate">下载 CSV 模板</el-button>
    </div>
    <el-upload ref="uploadRef" accept=".csv,.gpx" multiple :limit="20" :auto-upload="false" :show-file-list="false"
      :disabled="parsing || importing" :on-change="handleSelection" :on-exceed="showLimitMessage">
      <el-button :disabled="parsing || importing">选择文件</el-button>
      <template #tip><span class="upload-tip">最多选择 20 个文件，单个文件不超过 50 MB</span></template>
    </el-upload>

    <div v-if="files.length" class="file-summary">
      <span>{{ files.length }} 个文件</span>
      <span>{{ validRecords.length }} 条可导入轨迹</span>
      <span>{{ pointTotal.toLocaleString() }} 个采样点</span>
      <el-button link type="danger" :disabled="parsing || importing" @click="clearFiles">清空</el-button>
    </div>
    <el-table v-if="files.length" :data="files" size="small" max-height="280" row-key="uid">
      <el-table-column prop="name" label="文件" min-width="180" show-overflow-tooltip />
      <el-table-column label="解析结果" min-width="180">
        <template #default="{ row }">
          <span v-if="row.error" class="file-error">{{ row.error }}</span>
          <span v-else-if="row.records">{{ row.records.length }} 条轨迹 · {{ row.pointCount.toLocaleString() }} 点</span>
          <span v-else class="muted">待解析</span>
        </template>
      </el-table-column>
    </el-table>
    <el-alert v-if="pointTotal > MAX_BATCH_POINTS" type="warning" :closable="false"
      title="采样点总数超过单次导入上限 500,000，请分批选择文件。" />
    <el-alert v-if="validRecords.length > MAX_BATCH_RECORDS" type="warning" :closable="false"
      title="轨迹数量超过单次导入上限 100 条，请分批选择文件。" />
    <el-alert v-if="files.some(file => file.error)" class="parse-alert" type="warning" :closable="false"
      title="部分文件解析失败；成功解析的文件仍可导入，失败文件不会写入。" />

    <template #footer>
      <el-button :disabled="parsing || importing" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button :disabled="!files.length || parsing || importing" :loading="parsing" @click="parseFiles">{{ parsing ? `解析中 ${parseProgress}/${files.length}` : '解析文件' }}</el-button>
      <el-button type="primary" :disabled="!validRecords.length || validRecords.length > MAX_BATCH_RECORDS || pointTotal > MAX_BATCH_POINTS || parsing || importing"
        :loading="importing" @click="submitImport">导入 {{ validRecords.length }} 条</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, shallowRef, triggerRef, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addTrajectories } from '@/modules/travel/apis/travel.js'
import { downloadTrajectoryCsvTemplate, parseTrajectoryFile } from '@/modules/travel/utils/trajectoryImport.js'

const MAX_BATCH_POINTS = 500000
const MAX_BATCH_RECORDS = 100
const props = defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue', 'imported'])
const uploadRef = ref(null)
const files = shallowRef([])
const parsing = ref(false)
const importing = ref(false)
const parseProgress = ref(0)
const validRecords = computed(() => files.value.flatMap(file => file.records || []))
const pointTotal = computed(() => validRecords.value.reduce((total, record) => total + record.points.length, 0))

watch(() => props.modelValue, open => { if (open) clearFiles() })

function handleSelection(_file, list) {
  files.value = list.map(item => ({ uid: item.uid, name: item.name, raw: item.raw, records: null, error: '' }))
}

function showLimitMessage() { ElMessage.warning('最多选择 20 个文件') }

async function parseFiles() {
  parsing.value = true
  parseProgress.value = 0
  try {
    for (const file of files.value) {
      file.records = null
      file.error = ''
      try {
        file.records = await parseTrajectoryFile(file.raw, true)
        file.pointCount = file.records.reduce((total, record) => total + record.points.length, 0)
      } catch (error) { file.error = error.message || '文件解析失败' }
      parseProgress.value++
      triggerRef(files)
    }
    if (!validRecords.value.length) ElMessage.warning('没有可导入的轨迹，请检查文件内容')
  } finally { parsing.value = false }
}

async function submitImport() {
  if (!validRecords.value.length || validRecords.value.length > MAX_BATCH_RECORDS || pointTotal.value > MAX_BATCH_POINTS) return
  importing.value = true
  try {
    const payload = validRecords.value.map(({ name, activityType, note, points }) => ({
      name, activityType, note,
      startedAt: new Date(points[0].recordedAt).toISOString(),
      endedAt: new Date(points.at(-1).recordedAt).toISOString(),
      points: points.map((point, index) => ({ ...point, sequence: index + 1, recordedAt: new Date(point.recordedAt).toISOString() }))
    }))
    const response = await addTrajectories(payload)
    const imported = response.data?.totalImported ?? payload.length
    const failures = files.value.filter(file => file.error).map(file => `${file.name}：${file.error}`)
    emit('imported')
    emit('update:modelValue', false)
    if (failures.length) {
      await ElMessageBox.alert(`成功导入 ${imported} 条轨迹。以下文件未导入：\n${failures.join('\n')}`, '批量导入完成', { type: 'warning' })
    } else ElMessage.success(`成功导入 ${imported} 条轨迹`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.response?.data?.msg || '批量导入失败，未写入任何轨迹')
  } finally { importing.value = false }
}

function clearFiles() {
  files.value = []
  uploadRef.value?.clearFiles()
}
</script>

<style scoped>
.import-summary, .file-summary { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.import-summary { justify-content: space-between; margin-bottom: 14px; color: var(--el-text-color-secondary); }
.upload-tip { margin-left: 12px; color: var(--el-text-color-secondary); font-size: 12px; }
.file-summary { margin: 16px 0 8px; color: var(--el-text-color-regular); font-size: 13px; }
.file-summary :last-child { margin-left: auto; }
.file-error { color: var(--el-color-danger); }
.muted { color: var(--el-text-color-secondary); }
.parse-alert { margin-top: 12px; }
</style>
