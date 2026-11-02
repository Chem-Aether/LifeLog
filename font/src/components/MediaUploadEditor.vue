<template>
  <section class="media-editor" :class="{ disabled }">
    <draggable v-model="sortedItems" item-key="url" class="media-grid" handle=".sort-handle" :disabled="!multiple || disabled || busy" :animation="150">
      <template #item="{ element: item, index }">
        <article class="media-item">
          <video v-if="item.contentType?.startsWith('video/')" :src="resolveMediaUrl(item.url)" controls preload="metadata" />
          <img v-else :src="resolveMediaUrl(item.url)" :alt="item.name || '已上传图片'" />
          <button v-if="multiple && modelValue.length > 1" class="sort-handle" type="button" :aria-label="`调整${item.name || '媒体'}的顺序`" :disabled="disabled || busy" title="拖动排序，方向键调整" @keydown.left.prevent="moveItem(index, -1)" @keydown.up.prevent="moveItem(index, -1)" @keydown.right.prevent="moveItem(index, 1)" @keydown.down.prevent="moveItem(index, 1)"><el-icon><Rank /></el-icon></button>
          <button v-if="!item.contentType?.startsWith('video/') && item.contentType !== 'image/gif'" class="edit-item" type="button" :aria-label="`编辑${item.name || '图片'}`" :disabled="disabled || busy" title="编辑图片" @click="editItem(index)"><el-icon><Edit /></el-icon></button>
          <button class="remove-item" type="button" :aria-label="`移除${item.name || '媒体'}`" :disabled="disabled || busy" @click="removeItem(index)"><el-icon><Close /></el-icon></button>
        </article>
      </template>
      <template #footer>
        <button v-if="modelValue.length < maxCount || !multiple" class="add-item" type="button" :disabled="disabled || busy" @click="fileInput?.click()">
          <el-icon v-if="busy" class="is-loading"><Loading /></el-icon><el-icon v-else><Plus /></el-icon>
          <span>{{ busy ? '处理中' : modelValue.length ? multiple ? '继续添加' : '更换图片' : '添加图片' }}</span>
        </button>
      </template>
    </draggable>
    <p class="editor-hint">图片默认原图上传，可手动编辑裁切、旋转和缩放<template v-if="allowVideo">；视频直接上传</template></p>
    <input ref="fileInput" class="file-input" type="file" :accept="accept" :multiple="multiple" :disabled="disabled || busy" @change="selectFiles">

    <el-dialog v-model="dialogVisible" title="编辑图片" width="min(760px, calc(100vw - 28px))" class="crop-dialog" :close-on-click-modal="false" @close="handleDialogClose" @closed="onDialogClosed">
      <div class="crop-workspace" @pointermove="moveDrag" @pointerup="stopDrag" @pointercancel="stopDrag">
        <canvas ref="canvasEl" class="crop-canvas" width="800" height="500" />
        <div class="crop-frame" :style="frameStyle" @pointerdown="startDrag">
          <button v-for="handle in resizeHandles" :key="handle" type="button" class="crop-handle" :class="`handle-${handle}`" :aria-label="`调整裁切框${handleLabels[handle]}`" :title="`调整裁切框${handleLabels[handle]}`" @pointerdown.stop="startResize($event, handle)" />
        </div>
      </div>
      <div class="crop-tools">
        <label class="ratio-control"><span>裁切比例</span><el-select v-model="aspectChoice" aria-label="裁切比例" @change="changeAspect"><el-option label="原图" value="original" /><el-option label="自由" value="free" /><el-option label="1 : 1" value="1:1" /><el-option label="4 : 3" value="4:3" /><el-option label="3 : 2" value="3:2" /><el-option label="16 : 9" value="16:9" /></el-select></label>
        <div class="zoom-control"><span>缩放</span><el-slider v-model="zoom" :min="1" :max="3" :step="0.01" :show-tooltip="false" @input="changeZoom" /></div>
        <el-button circle aria-label="向左旋转" title="向左旋转" @click="rotate(-1)"><el-icon><RefreshLeft /></el-icon></el-button>
        <el-button circle aria-label="向右旋转" title="向右旋转" @click="rotate(1)"><el-icon><RefreshRight /></el-icon></el-button>
        <el-button circle aria-label="重置图片" title="重置图片" :disabled="!modified" @click="resetEditor"><el-icon><Refresh /></el-icon></el-button>
      </div>
      <template #footer><el-button @click="cancelImage">取消</el-button><el-button type="primary" :loading="uploading" :disabled="!modified" @click="confirmImage">保存图片</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Edit, Loading, Plus, Rank, Refresh, RefreshLeft, RefreshRight } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'
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
const sortedItems = computed({
  get: () => props.modelValue,
  set: items => emit('update:modelValue', items),
})
function moveItem(index, direction) {
  if (!props.multiple || props.disabled || busy.value) return
  const target = index + direction
  if (target < 0 || target >= props.modelValue.length) return
  const items = [...props.modelValue]
  ;[items[index], items[target]] = [items[target], items[index]]
  emit('update:modelValue', items)
}
const fileInput = ref(null)
const canvasEl = ref(null)
const dialogVisible = ref(false)
const uploading = ref(false)
const busy = ref(false)
const queue = ref([])
const activeFile = ref(null)
const editingIndex = ref(null)
const sourceCanvas = ref(null)
const initialCanvas = ref(null)
const modified = ref(false)
const aspectChoice = ref('original')
const zoom = ref(1)
const cropRect = ref({ x: 0, y: 0, width: 0, height: 0 })
const imageOffset = ref({ x: 0, y: 0 })
const handleLabels = { nw: '左上角', ne: '右上角', sw: '左下角', se: '右下角', n: '上边', e: '右边', s: '下边', w: '左边' }
const resizeHandles = computed(() => aspectChoice.value === 'free'
  ? ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w']
  : ['nw', 'ne', 'se', 'sw'])
let dragState = null
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
    initialCanvas.value = canvas
    modified.value = false
    originalAspect.value = canvas.width / canvas.height
    setCropRect('original')
    dialogVisible.value = true
    await nextTick()
    drawCanvas()
  } catch (error) {
    activeFile.value = null
    editingIndex.value = null
    sourceCanvas.value = null
    initialCanvas.value = null
    throw new Error('图片无法读取，请换一张图片重试', { cause: error })
  }
}

function aspectValue(value = aspectChoice.value) {
  if (value === 'original') return originalAspect.value
  const [width, height] = value.split(':').map(Number)
  return width / height
}
function setCropRect(value) {
  const imageScale = Math.min((viewport.width - 24) / sourceCanvas.value.width, (viewport.height - 24) / sourceCanvas.value.height)
  const imageWidth = sourceCanvas.value.width * imageScale
  const imageHeight = sourceCanvas.value.height * imageScale
  const aspect = value === 'free' ? imageWidth / imageHeight : aspectValue(value)
  const width = Math.min(imageWidth, imageHeight * aspect)
  const height = width / aspect
  cropRect.value = { x: (viewport.width - width) / 2, y: (viewport.height - height) / 2, width, height }
}
function changeAspect(value) { setCropRect(value); clampImage(); modified.value = true; drawCanvas() }
function changeZoom() { clampImage(); modified.value = true; drawCanvas() }
function resetEditor() {
  sourceCanvas.value = initialCanvas.value
  originalAspect.value = sourceCanvas.value.width / sourceCanvas.value.height
  aspectChoice.value = 'original'
  zoom.value = 1
  imageOffset.value = { x: 0, y: 0 }
  setCropRect('original')
  modified.value = false
  drawCanvas()
}
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
  const centeredX = (viewport.width - image.width) / 2
  const centeredY = (viewport.height - image.height) / 2
  imageOffset.value = {
    x: Math.max(cropRect.value.x + cropRect.value.width - image.width - centeredX,
      Math.min(cropRect.value.x - centeredX, imageOffset.value.x)),
    y: Math.max(cropRect.value.y + cropRect.value.height - image.height - centeredY,
      Math.min(cropRect.value.y - centeredY, imageOffset.value.y)),
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
function cropBounds() {
  const image = layoutImage()
  return {
    left: Math.max(0, image.x), top: Math.max(0, image.y),
    right: Math.min(viewport.width, image.x + image.width),
    bottom: Math.min(viewport.height, image.y + image.height),
  }
}
function clamp(value, min, max) { return Math.max(min, Math.min(max, value)) }
function startDrag(event) {
  if (event.button !== 0) return
  dragState = { mode: 'move', point: pointerPosition(event), rect: { ...cropRect.value } }
  event.currentTarget.setPointerCapture(event.pointerId)
}
function startResize(event, handle) {
  if (event.button !== 0) return
  dragState = { mode: handle, point: pointerPosition(event), rect: { ...cropRect.value } }
  event.currentTarget.setPointerCapture(event.pointerId)
}
function resizeCrop(handle, rect, dx, dy, bounds) {
  const minSize = 40
  const left = rect.x
  const top = rect.y
  const right = left + rect.width
  const bottom = top + rect.height
  if (aspectChoice.value !== 'free') {
    const horizontal = handle.includes('w') ? -1 : 1
    const vertical = handle.includes('n') ? -1 : 1
    const anchorX = horizontal < 0 ? right : left
    const anchorY = vertical < 0 ? bottom : top
    const aspect = aspectValue()
    const widthFromX = rect.width + horizontal * dx
    const widthFromY = (rect.height + vertical * dy) * aspect
    const maxWidth = Math.min(horizontal < 0 ? anchorX - bounds.left : bounds.right - anchorX,
      (vertical < 0 ? anchorY - bounds.top : bounds.bottom - anchorY) * aspect)
    const width = clamp(Math.abs(dx) >= Math.abs(dy * aspect) ? widthFromX : widthFromY,
      Math.min(maxWidth, Math.max(minSize, minSize * aspect)), maxWidth)
    const height = width / aspect
    return { x: horizontal < 0 ? anchorX - width : anchorX, y: vertical < 0 ? anchorY - height : anchorY, width, height }
  }
  const nextLeft = handle.includes('w') ? clamp(left + dx, bounds.left, right - minSize) : left
  const nextRight = handle.includes('e') ? clamp(right + dx, left + minSize, bounds.right) : right
  const nextTop = handle.includes('n') ? clamp(top + dy, bounds.top, bottom - minSize) : top
  const nextBottom = handle.includes('s') ? clamp(bottom + dy, top + minSize, bounds.bottom) : bottom
  return { x: nextLeft, y: nextTop, width: nextRight - nextLeft, height: nextBottom - nextTop }
}
function moveDrag(event) {
  if (!dragState) return
  const point = pointerPosition(event)
  const dx = point.x - dragState.point.x
  const dy = point.y - dragState.point.y
  const bounds = cropBounds()
  const rect = dragState.rect
  const next = dragState.mode === 'move'
    ? { ...rect, x: clamp(rect.x + dx, bounds.left, bounds.right - rect.width),
        y: clamp(rect.y + dy, bounds.top, bounds.bottom - rect.height) }
    : resizeCrop(dragState.mode, rect, dx, dy, bounds)
  if (next.x !== rect.x || next.y !== rect.y || next.width !== rect.width || next.height !== rect.height) modified.value = true
  cropRect.value = next
  drawCanvas()
}
function stopDrag() { dragState = null }
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
  modified.value = true
  drawCanvas()
}

async function confirmImage() {
  if (!sourceCanvas.value || !activeFile.value || !modified.value) return
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
      finally { uploading.value = false; sourceCanvas.value = null; initialCanvas.value = null; editingIndex.value = null; await processNext() }
    })
  } catch (error) {
    uploading.value = false
    showUploadError(error)
    sourceCanvas.value = null
    initialCanvas.value = null
    activeFile.value = null
    editingIndex.value = null
    closeDialogAfter(processNext)
  }
}
function cancelImage() {
  activeFile.value = null
  sourceCanvas.value = null
  initialCanvas.value = null
  editingIndex.value = null
  closeDialogAfter(processNext)
}
function handleDialogClose() {
  if (activeFile.value) {
    activeFile.value = null
    sourceCanvas.value = null
    initialCanvas.value = null
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
  if (replacedItem && replacedItem.url !== url) {
    try { await props.beforeRemove?.(replacedItem.url) }
    catch (error) {
      try { await props.beforeRemove?.(url) } catch { /* The original error is more useful to the caller. */ }
      throw error
    }
  }
  const next = replacementIndex == null
    ? props.multiple ? [...props.modelValue, item] : [item]
    : props.modelValue.map((current, index) => index === replacementIndex ? item : current)
  emit('update:modelValue', next)
  ElMessage.success(mediaType === 'video' ? '视频上传成功' : '图片上传成功')
}
async function removeItem(index) {
  const item = props.modelValue[index]
  if (!item || props.disabled || busy.value) return
  const isVideo = item.contentType?.startsWith('video/')
  setBusy(true)
  try {
    await ElMessageBox.confirm(`确定移除这${isVideo ? '段视频' : '张图片'}吗？`, '移除媒体', {
      type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消',
    })
  } catch { setBusy(false); return }
  try { await props.beforeRemove?.(item.url) }
  catch (error) { showUploadError(error); setBusy(false); return }
  emit('update:modelValue', props.modelValue.filter((_, itemIndex) => itemIndex !== index))
  ElMessage.success('已从当前记录移除')
  setBusy(false)
}
function showUploadError(error) {
  const message = error?.response?.data?.detail
    || (error?.response?.status === 502 ? '媒体存储暂时无响应，请稍后重试' : null)
    || (error?.code === 'ECONNABORTED' ? '上传超时，请稍后重试' : null)
    || error?.message || '媒体上传失败'
  ElMessage.error(message)
}
</script>

<style scoped>
.media-editor { min-width:0; }.media-grid { display:flex; flex-wrap:wrap; gap:9px; }.media-item,.add-item { position:relative; display:grid; width:88px; height:88px; flex:0 0 auto; place-items:center; overflow:hidden; border:1px solid #dfe7e5; border-radius:5px; background:#f5f8f6; }.media-item img,.media-item video { display:block; width:100%; height:100%; object-fit:cover; }.remove-item,.edit-item,.sort-handle { position:absolute; top:4px; display:grid; width:24px; height:24px; place-items:center; border:0; border-radius:50%; background:rgba(27,42,39,.72); color:#fff; cursor:pointer; }.remove-item { right:4px; }.edit-item { right:32px; }.sort-handle { left:4px; cursor:grab; touch-action:none; }.sort-handle:active { cursor:grabbing; }.media-item.sortable-ghost { opacity:.35; }.add-item { gap:5px; align-content:center; border-style:dashed; color:#64877a; font:inherit; font-size:10px; cursor:pointer; }.add-item .el-icon { font-size:19px; }.add-item:disabled,.remove-item:disabled,.edit-item:disabled,.sort-handle:disabled { cursor:not-allowed; opacity:.55; }.editor-hint { margin:7px 0 0; color:#94a19b; font-size:10px; }.file-input { display:none; }
.crop-tools { display:grid; grid-template-columns:150px minmax(140px,1fr) repeat(3,36px); align-items:center; gap:12px; margin-top:14px; }.ratio-control,.zoom-control { display:grid; grid-template-columns:auto minmax(80px,1fr); align-items:center; gap:8px; color:#73817b; font-size:10px; }.ratio-control :deep(.el-select) { width:94px; }.zoom-control :deep(.el-slider) { margin:0 5px; }.crop-tools :deep(.el-button) { width:34px; height:34px; margin:0; }
.crop-workspace { position:relative; width:100%; overflow:hidden; border-radius:5px; background:#202927; touch-action:none; }.crop-canvas { display:block; width:100%; height:auto; aspect-ratio:8/5; pointer-events:none; }.crop-frame { position:absolute; border:1px solid rgba(255,255,255,.92); cursor:move; touch-action:none; }.crop-handle { position:absolute; z-index:1; width:17px; height:17px; padding:0; border:2px solid #fff; border-radius:3px; background:#258b88; box-shadow:0 1px 6px rgba(0,0,0,.45); touch-action:none; }.handle-nw { top:0; left:0; transform:translate(-50%,-50%); cursor:nwse-resize; }.handle-ne { top:0; right:0; transform:translate(50%,-50%); cursor:nesw-resize; }.handle-sw { bottom:0; left:0; transform:translate(-50%,50%); cursor:nesw-resize; }.handle-se { bottom:0; right:0; transform:translate(50%,50%); cursor:nwse-resize; }.handle-n { top:0; left:50%; transform:translate(-50%,-50%); cursor:ns-resize; }.handle-s { bottom:0; left:50%; transform:translate(-50%,50%); cursor:ns-resize; }.handle-e { top:50%; right:0; transform:translate(50%,-50%); cursor:ew-resize; }.handle-w { top:50%; left:0; transform:translate(-50%,-50%); cursor:ew-resize; }
:deep(.crop-dialog .el-dialog) { overflow:hidden; border-radius:7px; }.disabled { opacity:.75; }
@media(max-width:540px) { .crop-tools { grid-template-columns:minmax(0,1fr) repeat(3,36px); gap:8px; }.ratio-control { grid-column:1/-1; grid-template-columns:70px 1fr; }.zoom-control { grid-column:1; } }
</style>
