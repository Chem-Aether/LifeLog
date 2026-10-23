<template>
  <section class="media-editor" :class="{ disabled }">
    <div class="media-grid">
      <article v-for="(item, index) in modelValue" :key="item.url" class="media-item">
        <video v-if="item.contentType?.startsWith('video/')" :src="resolveMediaUrl(item.url)" controls preload="metadata" />
        <img v-else :src="resolveMediaUrl(item.url)" :alt="item.name || '已上传图片'" />
        <button v-if="!item.contentType?.startsWith('video/')" class="edit-item" type="button" :aria-label="`编辑${item.name || '图片'}`" :disabled="disabled || busy" title="编辑图片" @click="editItem(index)"><el-icon><Edit /></el-icon></button>
        <button class="remove-item" type="button" :aria-label="`移除${item.name || '媒体'}`" :disabled="disabled || busy" @click="removeItem(index)"><el-icon><Close /></el-icon></button>
      </article>
      <button v-if="modelValue.length < maxCount || !multiple" class="add-item" type="button" :disabled="disabled || busy" @click="fileInput?.click()">
        <el-icon v-if="busy" class="is-loading"><Loading /></el-icon><el-icon v-else><Plus /></el-icon>
        <span>{{ busy ? '处理中' : modelValue.length ? multiple ? '继续添加' : '更换图片' : '添加图片' }}</span>
      </button>
    </div>
    <p class="editor-hint">图片默认原图上传，可手动编辑裁切、旋转和缩放<template v-if="allowVideo">；视频直接上传</template></p>
    <input ref="fileInput" class="file-input" type="file" :accept="accept" :multiple="multiple" :disabled="disabled || busy" @change="selectFiles">

    <el-dialog v-model="dialogVisible" title="编辑图片" width="min(760px, calc(100vw - 28px))" class="crop-dialog" :close-on-click-modal="false" @close="handleDialogClose" @closed="onDialogClosed">
      <div class="crop-workspace" @pointerdown="startDrag" @pointermove="moveDrag" @pointerup="stopDrag" @pointercancel="stopDrag">
        <canvas ref="canvasEl" class="crop-canvas" width="800" height="500" />
        <div class="crop-frame" :style="frameStyle"><span /><span /><span /><span /></div>
      </div>
      <div class="crop-tools">
        <label class="ratio-control"><span>裁切比例</span><el-select v-model="aspectChoice" aria-label="裁切比例" @change="changeAspect"><el-option label="原图" value="original" /><el-option label="1 : 1" value="1:1" /><el-option label="4 : 3" value="4:3" /><el-option label="3 : 2" value="3:2" /><el-option label="16 : 9" value="16:9" /></el-select></label>
        <div class="zoom-control"><span>缩放</span><el-slider v-model="zoom" :min="1" :max="3" :step="0.01" :show-tooltip="false" @input="drawCanvas" /></div>
        <el-button circle aria-label="向左旋转" title="向左旋转" @click="rotate(-1)"><el-icon><RefreshLeft /></el-icon></el-button>
        <el-button circle aria-label="向右旋转" title="向右旋转" @click="rotate(1)"><el-icon><RefreshRight /></el-icon></el-button>
      </div>
      <template #footer><el-button @click="cancelImage">取消</el-button><el-button type="primary" :loading="uploading" @click="confirmImage">保存并替换</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Close, Edit, Loading, Plus, RefreshLeft, RefreshRight } from '@element-plus/icons-vue'
import { resolveMediaUrl } from '@/utils/media.js'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  upload: { type: Function, required: true },
  beforeRemove: { type: Function, default: null },
  multiple: { type: Boolean, default: true },
  allowVideo: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  maxCount: { type: Number, default: 100 },
})
const emit = defineEmits(['update:modelValue', 'busy-change'])
const fileInput = ref(null)
const canvasEl = ref(null)
const dialogVisible = ref(false)
const uploading = ref(false)
const busy = ref(false)
const queue = ref([])
const activeFile = ref(null)
const editingIndex = ref(null)
const sourceCanvas = ref(null)
const aspectChoice = ref('original')
const zoom = ref(1)
const cropRect = ref({ x: 0, y: 0, width: 0, height: 0 })
const imageOffset = ref({ x: 0, y: 0 })
const draggingFrame = ref(false)
const lastPointer = ref({ x: 0, y: 0 })
let afterDialogClose = null
const viewport = { width: 800, height: 500 }
const originalAspect = ref(1)
let renderedImage = { x: 0, y: 0, scale: 1, width: 0, height: 0 }

const accept = computed(() => props.allowVideo
  ? 'image/jpeg,image/png,image/webp,image/gif,video/mp4,.mp4'
  : 'image/jpeg,image/png,image/webp,image/gif')
const frameStyle = computed(() => ({
  left: `${cropRect.value.x / viewport.width * 100}%`,
  top: `${cropRect.value.y / viewport.height * 100}%`,
  width: `${cropRect.value.width / viewport.width * 100}%`,
  height: `${cropRect.value.height / viewport.height * 100}%`,
}))

function setBusy(value) { busy.value = value; emit('busy-change', value) }
function selectFiles(event) {
  const files = [...(event.target.files || [])]
  event.target.value = ''
  const available = props.multiple ? props.maxCount - props.modelValue.length : 1
  if (files.length > available) ElMessage.warning(`最多添加 ${props.maxCount} 个媒体`)
  queue.value = files.slice(0, Math.max(0, available))
  if (queue.value.length) { setBusy(true); processNext() }
}

async function processNext() {
  const file = queue.value.shift()
  if (!file) { activeFile.value = null; uploading.value = false; setBusy(false); return }
  const isVideo = file.type.startsWith('video/') || /\.mp4$/i.test(file.name)
  if (isVideo) {
    if (!props.allowVideo || !/\.mp4$/i.test(file.name)) {
      ElMessage.warning('目前仅支持 MP4 视频')
      return processNext()
    }
    uploading.value = true
    try { await uploadFile(file, 'video') }
    catch (error) { showUploadError(error) }
    finally { uploading.value = false }
    return processNext()
  }
  const imageType = file.type || ({ jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp', gif: 'image/gif' }[file.name.split('.').pop()?.toLowerCase()] || '')
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(imageType)) {
    ElMessage.warning('请选择 JPEG、PNG、WebP 或 GIF 图片')
    return processNext()
  }
  uploading.value = true
  try { await uploadFile(file, 'image') }
  catch (error) { showUploadError(error) }
  finally { uploading.value = false }
  return processNext()
}

async function editItem(index) {
  if (busy.value || props.disabled) return
  const item = props.modelValue[index]
  if (!item) return
  setBusy(true)
  try {
    const response = await fetch(resolveMediaUrl(item.url))
    if (!response.ok) throw new Error('图片读取失败，请稍后重试')
    const blob = await response.blob()
    const type = blob.type || item.contentType || 'image/jpeg'
    const extension = ({ 'image/jpeg': '.jpg', 'image/png': '.png', 'image/webp': '.webp', 'image/gif': '.gif' })[type] || '.jpg'
    const baseName = (item.name || 'image').replace(/\.[^.]+$/, '')
    const file = new File([blob], `${baseName}${extension}`, { type })
    await openImageEditor(file, index)
  } catch (error) {
    setBusy(false)
    ElMessage.error(error?.message || '图片读取失败')
  }
}

async function openImageEditor(file, index) {
  activeFile.value = file
  editingIndex.value = index
  aspectChoice.value = 'original'
  zoom.value = 1
  imageOffset.value = { x: 0, y: 0 }
  try {
    const bitmap = await createImageBitmap(file)
    const canvas = document.createElement('canvas')
    canvas.width = bitmap.width
    canvas.height = bitmap.height
    canvas.getContext('2d').drawImage(bitmap, 0, 0)
    bitmap.close()
    sourceCanvas.value = canvas
    originalAspect.value = canvas.width / canvas.height
    setCropRect('original')
    dialogVisible.value = true
    await nextTick()
    drawCanvas()
  } catch (error) {
    activeFile.value = null
    editingIndex.value = null
    sourceCanvas.value = null
    throw new Error('图片无法读取，请换一张图片重试', { cause: error })
  }
}

function aspectValue(value = aspectChoice.value) {
  if (value === 'original') return originalAspect.value
  const [width, height] = value.split(':').map(Number)
  return width / height
}
function setCropRect(value) {
  const aspect = aspectValue(value)
  const imageScale = Math.min((viewport.width - 24) / sourceCanvas.value.width, (viewport.height - 24) / sourceCanvas.value.height)
  const imageWidth = sourceCanvas.value.width * imageScale
  const imageHeight = sourceCanvas.value.height * imageScale
  const width = Math.min(imageWidth * 0.88, imageHeight * 0.88 * aspect, viewport.width - 44, (viewport.height - 44) * aspect)
  const height = width / aspect
  cropRect.value = { x: (viewport.width - width) / 2, y: (viewport.height - height) / 2, width, height }
}
function changeAspect(value) { setCropRect(value); clampImage(); drawCanvas() }
function layoutImage() {
  const image = sourceCanvas.value
  if (!image) return null
  const scale = Math.min((viewport.width - 24) / image.width, (viewport.height - 24) / image.height) * zoom.value
  const width = image.width * scale
  const height = image.height * scale
  return { scale, width, height, x: (viewport.width - width) / 2 + imageOffset.value.x, y: (viewport.height - height) / 2 + imageOffset.value.y }
}
function clampImage() {
  const image = layoutImage()
  if (!image) return
  imageOffset.value = {
    x: Math.min(cropRect.value.x, Math.max(cropRect.value.x + cropRect.value.width - image.width, imageOffset.value.x)),
    y: Math.min(cropRect.value.y, Math.max(cropRect.value.y + cropRect.value.height - image.height, imageOffset.value.y)),
  }
}
function drawCanvas() {
  const canvas = canvasEl.value
  const image = layoutImage()
  if (!canvas || !image) return
  const context = canvas.getContext('2d')
  context.clearRect(0, 0, viewport.width, viewport.height)
  context.fillStyle = '#202927'
  context.fillRect(0, 0, viewport.width, viewport.height)
  renderedImage = image
  context.drawImage(sourceCanvas.value, image.x, image.y, image.width, image.height)
  const { x, y, width, height } = cropRect.value
  context.fillStyle = 'rgba(10, 18, 16, .58)'
  context.fillRect(0, 0, viewport.width, y)
  context.fillRect(0, y + height, viewport.width, viewport.height - y - height)
  context.fillRect(0, y, x, height)
  context.fillRect(x + width, y, viewport.width - x - width, height)
  context.strokeStyle = '#fff'
  context.lineWidth = 2
  context.strokeRect(x, y, width, height)
  context.strokeStyle = 'rgba(255,255,255,.48)'
  context.lineWidth = 1
  for (let i = 1; i < 3; i += 1) {
    context.beginPath(); context.moveTo(x + width * i / 3, y); context.lineTo(x + width * i / 3, y + height); context.stroke()
    context.beginPath(); context.moveTo(x, y + height * i / 3); context.lineTo(x + width, y + height * i / 3); context.stroke()
  }
}
function pointerPosition(event) {
  const rect = canvasEl.value.getBoundingClientRect()
  return { x: (event.clientX - rect.left) * viewport.width / rect.width, y: (event.clientY - rect.top) * viewport.height / rect.height }
}
function startDrag(event) {
  draggingFrame.value = true
  lastPointer.value = pointerPosition(event)
  event.currentTarget.setPointerCapture(event.pointerId)
}
function moveDrag(event) {
  if (!draggingFrame.value) return
  const point = pointerPosition(event)
  const deltaX = point.x - lastPointer.value.x
  const deltaY = point.y - lastPointer.value.y
  lastPointer.value = point
  const image = layoutImage()
  const { width, height } = cropRect.value
  const minX = Math.max(0, image.x)
  const minY = Math.max(0, image.y)
  const maxX = Math.min(viewport.width - width, image.x + image.width - width)
  const maxY = Math.min(viewport.height - height, image.y + image.height - height)
  cropRect.value = {
    ...cropRect.value,
    x: Math.max(minX, Math.min(maxX, cropRect.value.x + deltaX)),
    y: Math.max(minY, Math.min(maxY, cropRect.value.y + deltaY)),
  }
  drawCanvas()
}
function stopDrag() { draggingFrame.value = false }
function rotate(direction) {
  const source = sourceCanvas.value
  const rotated = document.createElement('canvas')
  rotated.width = source.height
  rotated.height = source.width
  const context = rotated.getContext('2d')
  context.translate(rotated.width / 2, rotated.height / 2)
  context.rotate(direction * Math.PI / 2)
  context.drawImage(source, -source.width / 2, -source.height / 2)
  sourceCanvas.value = rotated
  originalAspect.value = rotated.width / rotated.height
  imageOffset.value = { x: 0, y: 0 }
  setCropRect(aspectChoice.value)
  clampImage()
  drawCanvas()
}

async function confirmImage() {
  if (!sourceCanvas.value || !activeFile.value) return
  uploading.value = true
  try {
    const source = sourceCanvas.value
    const { x, y, width, height } = cropRect.value
    const scale = renderedImage.scale
    const sx = Math.max(0, (x - renderedImage.x) / scale)
    const sy = Math.max(0, (y - renderedImage.y) / scale)
    const sw = Math.min(source.width - sx, width / scale)
    const sh = Math.min(source.height - sy, height / scale)
    const output = document.createElement('canvas')
    const outputScale = Math.min(1, 4096 / Math.max(sw, sh))
    output.width = Math.max(1, Math.round(sw * outputScale))
    output.height = Math.max(1, Math.round(sh * outputScale))
    const context = output.getContext('2d')
    const sourceType = activeFile.value.type || ({ png: 'image/png', webp: 'image/webp' }[activeFile.value.name.split('.').pop()?.toLowerCase()] || 'image/jpeg')
    const outputType = sourceType === 'image/png' || sourceType === 'image/webp' ? sourceType : 'image/jpeg'
    if (outputType === 'image/jpeg') { context.fillStyle = '#fff'; context.fillRect(0, 0, output.width, output.height) }
    context.drawImage(source, sx, sy, sw, sh, 0, 0, output.width, output.height)
    const blob = await new Promise(resolve => output.toBlob(resolve, outputType, 0.92))
    if (!blob) throw new Error('无法生成裁切图片')
    const extension = outputType === 'image/png' ? '.png' : outputType === 'image/webp' ? '.webp' : '.jpg'
    const file = new File([blob], `${activeFile.value.name.replace(/\.[^.]+$/, '')}-edited${extension}`, { type: outputType })
    const originalFile = activeFile.value
    const replacementIndex = editingIndex.value
    activeFile.value = null
    closeDialogAfter(async () => {
      try { await uploadFile(file, 'image', originalFile.name, replacementIndex) }
      catch (error) { showUploadError(error) }
      finally { uploading.value = false; sourceCanvas.value = null; editingIndex.value = null; await processNext() }
    })
  } catch (error) {
    uploading.value = false
    showUploadError(error)
    sourceCanvas.value = null
    activeFile.value = null
    editingIndex.value = null
    closeDialogAfter(processNext)
  }
}
function cancelImage() {
  activeFile.value = null
  sourceCanvas.value = null
  editingIndex.value = null
  closeDialogAfter(processNext)
}
function handleDialogClose() {
  if (activeFile.value) {
    activeFile.value = null
    sourceCanvas.value = null
    editingIndex.value = null
    afterDialogClose = processNext
  }
}
function closeDialogAfter(callback) { afterDialogClose = callback; dialogVisible.value = false }
function onDialogClosed() { const callback = afterDialogClose; afterDialogClose = null; callback?.() }
async function uploadFile(file, mediaType, originalName = file.name, replacementIndex = null) {
  const result = await props.upload(file, mediaType)
  const url = result.url || result.data?.url
  if (!url) throw new Error('对象存储未返回媒体 URL')
  const item = {
    url,
    contentType: result.content_type || (mediaType === 'video' ? 'video/mp4' : file.type),
    name: originalName,
  }
  const replacedItem = replacementIndex == null
    ? props.multiple ? null : props.modelValue[0]
    : props.modelValue[replacementIndex]
  if (replacedItem && replacedItem.url !== url) await props.beforeRemove?.(replacedItem.url)
  const next = replacementIndex == null
    ? props.multiple ? [...props.modelValue, item] : [item]
    : props.modelValue.map((current, index) => index === replacementIndex ? item : current)
  emit('update:modelValue', next)
  ElMessage.success(mediaType === 'video' ? '视频上传成功' : '图片上传成功')
}
async function removeItem(index) {
  const item = props.modelValue[index]
  if (!item || props.disabled || busy.value) return
  if (props.beforeRemove) {
    setBusy(true)
    try { await props.beforeRemove(item.url) }
    catch (error) { showUploadError(error); setBusy(false); return }
    setBusy(false)
  }
  emit('update:modelValue', props.modelValue.filter((_, itemIndex) => itemIndex !== index))
}
function showUploadError(error) {
  ElMessage.error(error?.response?.data?.detail || error?.message || '媒体上传失败')
}
</script>

<style scoped>
.media-editor { min-width:0; }.media-grid { display:flex; flex-wrap:wrap; gap:9px; }.media-item,.add-item { position:relative; display:grid; width:88px; height:88px; flex:0 0 auto; place-items:center; overflow:hidden; border:1px solid #dfe7e5; border-radius:5px; background:#f5f8f6; }.media-item img,.media-item video { display:block; width:100%; height:100%; object-fit:cover; }.remove-item,.edit-item { position:absolute; top:4px; display:grid; width:24px; height:24px; place-items:center; border:0; border-radius:50%; background:rgba(27,42,39,.72); color:#fff; cursor:pointer; }.remove-item { right:4px; }.edit-item { right:32px; }.add-item { gap:5px; align-content:center; border-style:dashed; color:#64877a; font:inherit; font-size:10px; cursor:pointer; }.add-item .el-icon { font-size:19px; }.add-item:disabled,.remove-item:disabled,.edit-item:disabled { cursor:not-allowed; opacity:.55; }.editor-hint { margin:7px 0 0; color:#94a19b; font-size:10px; }.file-input { display:none; }
.crop-tools { display:grid; grid-template-columns:150px minmax(140px,1fr) 36px 36px; align-items:center; gap:12px; margin-top:14px; }.ratio-control,.zoom-control { display:grid; grid-template-columns:auto minmax(80px,1fr); align-items:center; gap:8px; color:#73817b; font-size:10px; }.ratio-control :deep(.el-select) { width:94px; }.zoom-control :deep(.el-slider) { margin:0 5px; }.crop-tools :deep(.el-button) { width:34px; height:34px; margin:0; }
.crop-workspace { position:relative; width:100%; overflow:hidden; border-radius:5px; background:#202927; touch-action:none; cursor:move; }.crop-canvas { display:block; width:100%; height:auto; aspect-ratio:8/5; pointer-events:none; }.crop-frame { position:absolute; border:1px solid transparent; pointer-events:none; }.crop-frame span { position:absolute; width:13px; height:13px; border-color:#fff; border-style:solid; }.crop-frame span:nth-child(1) { top:-1px; left:-1px; border-width:2px 0 0 2px; }.crop-frame span:nth-child(2) { top:-1px; right:-1px; border-width:2px 2px 0 0; }.crop-frame span:nth-child(3) { right:-1px; bottom:-1px; border-width:0 2px 2px 0; }.crop-frame span:nth-child(4) { bottom:-1px; left:-1px; border-width:0 0 2px 2px; }
:deep(.crop-dialog .el-dialog) { overflow:hidden; border-radius:7px; }.disabled { opacity:.75; }
@media(max-width:540px) { .crop-tools { grid-template-columns:minmax(0,1fr) 36px 36px; gap:8px; }.ratio-control { grid-column:1/-1; grid-template-columns:70px 1fr; }.zoom-control { grid-column:1; } }
</style>
