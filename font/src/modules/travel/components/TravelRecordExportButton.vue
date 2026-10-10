<template>
  <el-button link type="info" :loading="loading" @click="download">导出</el-button>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportTravelRecord } from '@/modules/travel/apis/travel.js'

const props = defineProps({
  type: { type: String, required: true },
  recordId: { type: [String, Number], required: true }
})
const loading = ref(false)

async function download() {
  loading.value = true
  try {
    const format = props.type === 'train' || props.type === 'footprint' ? 'gpkg' : 'xlsx'
    const response = await exportTravelRecord(props.type, props.recordId, format)
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `旅行记录-${props.type}-${props.recordId}.${format}`
    anchor.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch {
    ElMessage.error('单条记录导出失败')
  } finally {
    loading.value = false
  }
}
</script>
