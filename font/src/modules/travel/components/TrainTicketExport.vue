<template>
  <span class="export-control">
    <el-button link type="info" :loading="loading" @click="openPreview">导出图片</el-button>
    <el-dialog v-model="visible" title="车票图片预览" width="min(1080px, calc(100vw - 28px))" class="train-export-dialog" append-to-body destroy-on-close>
      <div class="ticket-style-switch">
        <el-radio-group v-model="ticketColor" aria-label="车票底色" @change="refreshPreview">
          <el-radio-button value="blue">蓝色车票</el-radio-button>
          <el-radio-button value="red">红色车票</el-radio-button>
        </el-radio-group>
        <label class="ticket-input"><span>姓名</span><el-input v-model="passengerName" maxlength="20" placeholder="输入乘车人" @input="refreshPreview" /></label>
        <label class="ticket-input"><span>身份证号</span><el-input v-model="identityInput" maxlength="18" placeholder="输入18位身份证号，票面自动隐藏中间位" @input="refreshPreview" /></label>
        <label class="ticket-input"><span>票价</span><el-input-number v-model="price" :min="0" :precision="2" :step="10" controls-position="right" @change="refreshPreview" /></label>
      </div>
      <div class="preview-frame">
        <el-skeleton v-if="loading" animated :rows="5" />
        <img v-else-if="imageUrl" :src="imageUrl" alt="车票纪念图片" />
        <el-empty v-else description="图片生成失败" />
      </div>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" :disabled="!imageUrl || loading" @click="downloadImage">下载 PNG</el-button>
      </template>
    </el-dialog>
  </span>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pinyin } from 'pinyin-pro'

const props = defineProps({
  recordId: { type: [String, Number], required: true },
  record: { type: Object, required: true },
})

const visible = ref(false)
const loading = ref(false)
const imageUrl = ref('')
const ticketColor = ref('blue')
const passengerName = ref('')
const price = ref(0)
const ticketNumber = ref('')
const identityCode = ref('')
const identityInput = ref('')
const machineCode = ref('')

function randomDigits(length) {
  const values = new Uint8Array(length)
  crypto.getRandomValues(values)
  return Array.from(values, value => value % 10).join('')
}

function drawText(context, value, x, y, size, { align = 'left', weight = 400, color = '#102329', family = '"Times New Roman"', spacing = 0 } = {}) {
  if (!value) return
  context.textAlign = align
  context.textBaseline = 'middle'
  context.fillStyle = color
  context.font = `${weight} ${size}px ${family}`
  const content = String(value)
  if (!spacing) {
    context.fillText(content, x, y)
    return
  }
  let cursor = x
  for (const character of content) {
    context.fillText(character, cursor, y)
    cursor += context.measureText(character).width + spacing
  }
}

function drawTextWithWidth(context, value, x, y, targetWidth, color) {
  if (!value) return
  context.fillStyle = color
  context.textAlign = 'left'
  const text = String(value)
  const naturalWidth = context.measureText(text).width
  if (naturalWidth > targetWidth || text.length < 2) {
    context.fillText(text, x, y)
    return
  }
  const gap = (targetWidth - naturalWidth) / (text.length - 1)
  let cursor = x
  for (const character of text) {
    context.fillText(character, cursor, y)
    cursor += context.measureText(character).width + gap
  }
}

function stationPinyin(name, provided) {
  if (provided) return provided
  const value = pinyin(name, { toneType: 'none', separator: '' })
  return value ? value[0].toUpperCase() + value.slice(1) : ''
}

function maskedIdentity(value) {
  const identity = String(value ?? '').trim()
  if (identity.length <= 14) return identity
  return `${identity.slice(0, 10)}****${identity.slice(-4)}`
}

function drawDecorativeCode(context, x, y, size, seedText) {
  const count = 25
  const cell = size / count
  let seed = [...String(seedText)].reduce((value, character) => (value * 31 + character.charCodeAt(0)) >>> 0, 17)
  const next = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 0x100000000 }
  const inFinder = (column, row, left, top) => column >= left && column < left + 7 && row >= top && row < top + 7
  const finderPixel = (column, row, left, top) => {
    const dx = column - left
    const dy = row - top
    return dx === 0 || dx === 6 || dy === 0 || dy === 6 || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4)
  }
  context.fillStyle = '#111'
  for (let row = 0; row < count; row += 1) {
    for (let column = 0; column < count; column += 1) {
      const finder = [[0, 0], [count - 7, 0], [0, count - 7]].find(([left, top]) => inFinder(column, row, left, top))
      if (finder ? finderPixel(column, row, finder[0], finder[1]) : next() > 0.52) {
        context.fillRect(x + column * cell, y + row * cell, Math.ceil(cell), Math.ceil(cell))
      }
    }
  }
}

async function renderImage() {
  const background = new Image()
  background.src = `${import.meta.env.BASE_URL}travel/${ticketColor.value}Ticket.png`
  await background.decode()

  const scale = 2
  const width = background.naturalWidth
  const height = background.naturalHeight
  const canvas = document.createElement('canvas')
  canvas.width = width * scale
  canvas.height = height * scale
  const context = canvas.getContext('2d')
  context.scale(scale, scale)
  context.drawImage(background, 0, 0)

  const row = props.record
  const dx = ticketColor.value === 'red' ? 10 : 0
  const dy = ticketColor.value === 'red' ? 20 : 0
  const start = row.startStationName || ''
  const end = row.endStationName || ''
  const trainNo = row.trainNo || ''
  const ink = '#111'
  const departure = row.departureTime ? new Date(row.departureTime) : null
  context.textBaseline = 'middle'
  context.fillStyle = ink
  drawText(context, ticketNumber.value, 45 + dx, 50 + dy, 40, { family: 'Arial', spacing: 10, color: '#b32421' })
  drawText(context, row.checkInRoom || row.waitingRoom || '', 760 + dx, 40 + dy, 30, { align: 'right', color: ink })

  context.font = '400 50px "Times New Roman"'
  context.textAlign = 'left'
  const drawStation = (name, englishName, left, isEnd = false) => {
    context.font = '400 50px "Times New Roman"'
    context.fillStyle = ink
    context.textAlign = 'left'
    let stationX = left + dx
    const stationY = 105 + dy
    const widthAdjust = Math.max(0, name.length - 3)
    const textX = name.length < 4 ? stationX : stationX - widthAdjust * (isEnd ? 20 : 15)
    context.textAlign = 'left'
    context.fillStyle = ink
    drawTextWithWidth(context, name, textX, stationY, name.length < 4 ? 160 : 170 + widthAdjust * 20, ink)
    stationX += name.length < 4 ? 165 : 165 + widthAdjust * (isEnd ? 25 : 33)
    drawText(context, '站', stationX, stationY, 32, { color: ink })
    const pyX = isEnd ? left + dx + 90 : Math.max(left + dx + 90, context.measureText(name).width / 2 + 100 + dx)
    drawText(context, stationPinyin(name, englishName), pyX, stationY + 40, 30, { align: 'center', color: ink })
  }
  drawStation(start, row.originStationNameEn || row.startStationNameEn || '', 60)
  drawStation(end, row.endStationNameEn || row.terminalStationNameEn || '', 550, true)
  drawText(context, trainNo, 400 + dx, 105 + dy, 46, { align: 'center', color: ink })
  context.strokeStyle = ink
  context.lineWidth = 2
  context.setLineDash([])
  context.beginPath()
  context.moveTo(345 + dx, 135 + dy)
  context.lineTo(465 + dx, 135 + dy)
  context.lineTo(450 + dx, 130 + dy)
  context.stroke()

  if (departure && !Number.isNaN(departure.getTime())) {
    let cursor = 40 + dx
    const writePart = (value, size) => {
      context.font = `400 ${size}px "Times New Roman"`
      context.textAlign = 'left'
      context.fillText(value, cursor, 190 + dy)
      cursor += context.measureText(value).width + 3
    }
    writePart(String(departure.getFullYear()), 38)
    writePart('年', 24)
    writePart(String(departure.getMonth() + 1).padStart(2, '0'), 38)
    writePart('月', 24)
    writePart(String(departure.getDate()).padStart(2, '0'), 38)
    writePart('日', 24)
    cursor += 10
    writePart(`${String(departure.getHours()).padStart(2, '0')}:${String(departure.getMinutes()).padStart(2, '0')}`, 38)
    writePart('开', 24)
  }

  let seatX = 510 + dx
  const writeSeat = (value, size) => {
    context.font = `400 ${size}px "Times New Roman"`
    context.textAlign = 'left'
    context.fillText(value, seatX, 190 + dy)
    seatX += context.measureText(value).width + 3
  }
  writeSeat(row.carriageNo || '', 40)
  writeSeat('车', 24)
  writeSeat(row.seatNo || '', 40)
  writeSeat('号', 24)
  writeSeat(row.seatPosition || '', 24)

  let priceX = 40 + dx
  context.font = '400 36px "Times New Roman"'
  context.textAlign = 'left'
  const priceText = `￥${Number(price.value || 0).toFixed(1)}`
  context.fillText(priceText, priceX, 235 + dy)
  priceX += context.measureText(priceText).width + 3
  drawText(context, '元', priceX, 235 + dy, 24, { color: ink })
  drawText(context, row.seatType || '', 620 + dx, 235 + dy, 30, { align: 'center', color: ink })
  const lowerBlockShift = 40
  drawText(context, maskedIdentity(identityInput.value), 40 + dx, 345 + dy - lowerBlockShift, 35, { color: ink })
  drawText(context, passengerName.value, 400 + dx, 345 + dy - lowerBlockShift, 32, { color: ink })

  context.setLineDash([10, 5])
  context.lineWidth = 2
  context.strokeStyle = ink
  context.strokeRect(80 + dx, 365 + dy - lowerBlockShift, 460, 70)
  context.setLineDash([])
  drawText(context, '报销凭证 遗失不补', 300 + dx, 385 + dy - lowerBlockShift, 24, { align: 'center', color: ink })
  drawText(context, '退票改签时须交回车站', 300 + dx, 415 + dy - lowerBlockShift, 24, { align: 'center', color: ink })
  const qrSize = ticketColor.value === 'blue' ? 128 : 136
  drawDecorativeCode(context, 600, ticketColor.value === 'blue' ? 300 : 360, qrSize, `${ticketNumber.value}-${identityCode.value}`)
  drawText(context, machineCode.value, 40 + dx, 475 + dy - lowerBlockShift, 27, { color: ink })

  const blob = await new Promise((resolve, reject) => canvas.toBlob(value => value ? resolve(value) : reject(new Error('PNG生成失败')), 'image/png'))
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = URL.createObjectURL(blob)
}

async function openPreview() {
  visible.value = true
  passengerName.value = ''
  price.value = Number(props.record.price ?? props.record.ticketPrice ?? 0) || 0
  ticketNumber.value = randomDigits(13)
  identityCode.value = randomDigits(12)
  identityInput.value = randomDigits(18)
  machineCode.value = `${randomDigits(14)}L${randomDigits(6)} JM`
  await refreshPreview()
}
async function refreshPreview() {
  if (!visible.value) return
  loading.value = true
  try { await renderImage() } catch { ElMessage.error('图片生成失败，请重试') } finally { loading.value = false }
}
function downloadImage() {
  if (!imageUrl.value) return
  const link = document.createElement('a')
  link.href = imageUrl.value
  link.download = `车票纪念图-${props.recordId}.png`
  link.click()
}
onBeforeUnmount(() => { if (imageUrl.value) URL.revokeObjectURL(imageUrl.value) })
</script>

<style scoped>
.ticket-style-switch { display:flex; align-items:center; flex-wrap:wrap; gap:12px; margin-bottom:14px; }
.ticket-input { display:flex; align-items:center; gap:8px; color:#697a81; font-size:13px; }
.ticket-input :deep(.el-input) { width:180px; }
.ticket-input :deep(.el-input-number) { width:150px; }
.export-control { display:inline-flex; }
.preview-frame { display:flex; width:100%; max-width:100%; max-height:calc(100dvh - 270px); align-items:center; justify-content:center; overflow:hidden; box-sizing:border-box; border:1px solid #e3eaec; border-radius:7px; background:#f5f8f8; }
.preview-frame img { display:block; width:auto; height:auto; max-width:100%; max-height:calc(100dvh - 275px); object-fit:contain; }
.train-export-dialog :deep(.el-dialog__body) { max-height:calc(100dvh - 170px); overflow-x:hidden; overflow-y:auto; }
@media(max-width:600px) { .preview-frame { max-height:calc(100dvh - 245px); }.preview-frame img { max-height:calc(100dvh - 250px); } }
</style>
