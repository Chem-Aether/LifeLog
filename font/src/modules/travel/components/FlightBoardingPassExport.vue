<template>
  <span class="export-control">
    <el-button link type="info" :loading="loading" @click="openPreview">导出图片</el-button>
    <el-dialog v-model="visible" title="登机牌图片预览" width="min(1080px, calc(100vw - 28px))" class="flight-export-dialog" append-to-body destroy-on-close>
      <div class="ticket-style-switch">
        <span>票面颜色</span>
        <button v-for="color in presets" :key="color" type="button" :aria-label="`使用${color.name}主题色`" :title="color.name" :style="{ backgroundColor: color.value }" :class="{ selected: accent === color.value }" @click="setColor(color.value)" />
        <el-color-picker v-model="accent" color-format="hex" aria-label="自定义票面颜色" @change="refreshPreview" />
        <label class="passenger-name-input">
          <span>姓名</span>
          <el-input v-model="passengerName" maxlength="24" placeholder="输入姓名" @input="refreshPreview" />
        </label>
      </div>
      <div class="preview-frame">
        <el-skeleton v-if="loading" animated :rows="5" />
        <img v-else-if="imageUrl" :src="imageUrl" alt="登机牌纪念图片" />
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
import { searchAirports } from '@/modules/travel/apis/sysResource.js'

const props = defineProps({
  recordId: { type: [String, Number], required: true },
  record: { type: Object, required: true },
})

const presets = [
  { name: '靛蓝', value: '#6471b8' }, { name: '青绿', value: '#168a91' },
  { name: '海蓝', value: '#3279a8' }, { name: '珊瑚', value: '#c36e52' }, { name: '松绿', value: '#536f62' },
]
const visible = ref(false)
const loading = ref(false)
const imageUrl = ref('')
const accent = ref(presets[0].value)
const passengerName = ref('')
const ticketNumber = ref('')
const identificationCode = ref('')
const airportIata = ref({ departure: '', arrival: '' })

function randomDigits(length) {
  const values = new Uint8Array(length)
  crypto.getRandomValues(values)
  return Array.from(values, value => value % 10).join('')
}

function escapeXml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' })[char])
}

function text(x, y, value, size, color = '#18252b', weight = 500, anchor = 'start') {
  if (!value) return ''
  return `<text x="${x}" y="${y}" fill="${color}" font-size="${size}" font-weight="${weight}" text-anchor="${anchor}" font-family="Arial, 'Microsoft YaHei', sans-serif">${escapeXml(value)}</text>`
}

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ')
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}

function formatCabinClass(value) {
  const raw = String(value ?? '').trim()
  const match = raw.match(/[（(]([^()（）]*?)[）)]/)
  return match ? match[1].trim() : raw
}

async function findIata(icao, name) {
  const keyword = String(icao || name || '').trim()
  if (!keyword) return ''
  try {
    const response = await searchAirports(keyword, 20)
    const airports = Array.isArray(response?.data) ? response.data : []
    const match = airports.find(airport => icao && airport.icao?.toUpperCase() === icao.toUpperCase())
      || airports.find(airport => name && airport.name === name)
      || airports[0]
    return match?.iata || ''
  } catch {
    return ''
  }
}

function airportLabel(name, iata) {
  return [name, iata].filter(Boolean).join(' · ')
}

function boardingPassSvg() {
  const row = props.record
  const departure = row.departureAirportName || row.departureAirportNameDisplay || ''
  const arrival = row.arrivalAirportName || row.arrivalAirportNameDisplay || ''
  const departureLabel = airportLabel(departure, airportIata.value.departure)
  const arrivalLabel = airportLabel(arrival, airportIata.value.arrival)
  const flightNo = row.flightNo || ''
  const date = row.departureTime ? new Date(row.departureTime) : null
  const dateText = date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short' }).format(date).toUpperCase() : ''
  const timeText = date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(date) : ''
  const seat = row.seatNo || ''
  const passengerPinyin = passengerName.value
    ? pinyin(passengerName.value, { toneType: 'none', surname: 'head', separator: ' ' }).toUpperCase()
    : ''
  let barcodeX = 690
  const bars = Array.from({ length: 34 }, (_, index) => {
    const token = `${props.recordId}${flightNo}${index}`
    const width = 2 + ((token.charCodeAt(index % token.length) * (index + 3)) % 5)
    const markup = `<rect x="${barcodeX}" y="362" width="${width}" height="60" fill="#17242b"/>`
    barcodeX += width + 2 + (index % 3)
    return markup
  }).join('')
  const stubs = [972, 1228].map(left => `
    ${text(left + 24, 230, 'FLIGHT', 14, '#87939a', 700)}
    ${text(left + 24, 273, flightNo, 29, '#18252b', 650)}
    ${text(left + 24, 321, 'DATE', 13, '#87939a', 600)}
    ${text(left + 24, 360, dateText, 24, '#18252b', 650)}
    ${text(left + 24, 410, 'DESTINATION', 13, '#87939a', 600)}
    ${text(left + 24, 450, arrivalLabel, 21, '#18252b', 600)}
    ${text(left + 24, 500, 'SEAT', 13, '#87939a', 600)}
    ${text(left + 24, 541, seat, 27, '#18252b', 650)}
    <path d="M${left + 20} 565h205" stroke="#d5dade" stroke-width="2" stroke-dasharray="5 6"/>
    ${text(left + 24, 594, 'SERIAL NO.', 12, '#7c898f')}
    ${text(left + 24, 621, ticketNumber.value.slice(-3), 20, '#18252b', 650)}`).join('')

  return `<svg xmlns="http://www.w3.org/2000/svg" width="1500" height="760" viewBox="0 0 1500 760">
    <defs><clipPath id="ticket-frame-clip"><rect x="24" y="24" width="1452" height="712" rx="22"/></clipPath></defs>
    <rect width="1500" height="760" rx="28" fill="#eef1f3"/><rect x="24" y="24" width="1452" height="712" rx="22" fill="#fff" stroke="#d9dfe2" stroke-width="2"/>
    <path d="M46 24h1408a22 22 0 0 1 22 22v130H24V46a22 22 0 0 1 22-22Z" fill="${escapeXml(accent.value)}"/>
    ${text(68, 83, 'LIFELOG TRAVEL', 22, '#fff', 700)}
    ${text(68, 129, 'FLIGHT MEMORY PASS', 34, '#fff', 750)}${text(1430, 124, '纪念登机牌 · 非登机凭证', 19, '#fff', 650, 'end')}
    <path d="M948 176v468M1204 176v468" stroke="#cbd3d7" stroke-width="2" stroke-dasharray="5 8"/><path d="M948 176v468" stroke="${escapeXml(accent.value)}" stroke-width="3" stroke-dasharray="4 8" opacity=".55"/>
    <rect x="72" y="195" width="246" height="112" rx="8" fill="${escapeXml(accent.value)}" opacity=".08" stroke="${escapeXml(accent.value)}" stroke-width="2"/><rect x="337" y="195" width="274" height="112" rx="8" fill="#fff" stroke="${escapeXml(accent.value)}" stroke-width="2"/><rect x="630" y="195" width="278" height="112" rx="8" fill="#fff" stroke="${escapeXml(accent.value)}" stroke-width="2"/>
    ${text(92, 226, '航班 FLIGHT', 15, '#7e8b92', 600)}${text(92, 267, flightNo, 29, '#18252b', 700)}${text(92, 295, dateText, 17, '#53636a', 600)}
    ${text(357, 226, '起飞时间 DEPARTURE', 14, '#7e8b92', 600)}${text(587, 234, timeText, 26, '#18252b', 650, 'end')}${text(357, 279, '航站楼 TERMINAL', 14, '#7e8b92', 600)}${text(587, 286, row.departureTerminal || '', 21, '#18252b', 650, 'end')}
    ${text(650, 226, '舱位 CLASS', 14, '#7e8b92', 600)}${text(888, 234, formatCabinClass(row.cabinClass), 22, '#18252b', 650, 'end')}${text(650, 279, '座位号 SEAT', 14, '#7e8b92', 600)}${text(888, 286, seat, 22, '#18252b', 650, 'end')}
    ${text(75, 354, '出发机场 DEPARTURE', 14, '#89969c', 600)}${text(75, 391, departureLabel, 22, '#26363d', 650)}
    ${text(390, 354, '到达机场 ARRIVAL', 14, '#89969c', 600)}${text(390, 391, arrivalLabel, 22, '#26363d', 650)}
    ${text(75, 448, '起飞时间 DEPARTURE', 14, '#89969c', 600)}${text(75, 483, timeText, 22, '#26363d', 600)}
    ${text(390, 448, '到达时间 ARRIVAL', 14, '#89969c', 600)}${text(390, 483, row.arrivalTime ? formatDate(row.arrivalTime) : '', 22, '#26363d', 600)}
    ${text(75, 527, '姓名 NAME', 14, '#89969c', 600)}${text(75, 559, passengerName.value, 20, '#26363d', 650)}${text(75, 582, passengerPinyin, 14, '#52666e', 600)}
    ${text(390, 527, '票号 TICKET NO.', 14, '#89969c', 600)}${text(390, 559, ticketNumber.value, 19, '#26363d', 650)}
    ${bars}${text(805, 442, '', 12, '#87939a', 500, 'middle')}
    <rect x="660" y="462" width="282" height="76" rx="3" fill="#f8fbfb" stroke="${escapeXml(accent.value)}" stroke-width="2.5" stroke-dasharray="9 5"/>
    ${text(674, 492, '登机口可能变更，请及时关注', 16, '#2b454c', 650)}${text(674, 519, '广播或航显信息', 16, '#2b454c', 650)}
    <path d="M58 640h862" stroke="#d8dfe2" stroke-width="2"/>
    ${text(62, 666, '重要提示：航班起飞前15分钟停止登机，请您通过安检后，再次确认登机口信息！请勿折叠！', 12, '#25363d', 600)}
    <rect x="24" y="680" width="1452" height="56" fill="${escapeXml(accent.value)}" opacity=".9" clip-path="url(#ticket-frame-clip)"/>
    <rect x="24" y="24" width="1452" height="712" rx="22" fill="none" stroke="#d9dfe2" stroke-width="2"/>
    ${text(62, 714, '个人旅行记录 · 非真实交通凭证', 14, '#fff', 650)}
    ${stubs}
  </svg>`
}

async function renderImage() {
  const svgBlob = new Blob([boardingPassSvg()], { type: 'image/svg+xml;charset=utf-8' })
  const svgUrl = URL.createObjectURL(svgBlob)
  try {
    const image = new Image()
    image.src = svgUrl
    await image.decode()
    const canvas = document.createElement('canvas')
    canvas.width = 1500
    canvas.height = 760
    canvas.getContext('2d').drawImage(image, 0, 0)
    const blob = await new Promise((resolve, reject) => canvas.toBlob(value => value ? resolve(value) : reject(new Error('PNG生成失败')), 'image/png'))
    if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = URL.createObjectURL(blob)
  } finally { URL.revokeObjectURL(svgUrl) }
}

async function openPreview() {
  visible.value = true
  loading.value = true
  ticketNumber.value = randomDigits(13)
  identificationCode.value = randomDigits(18)
  const [departureIata, arrivalIata] = await Promise.all([
    findIata(props.record.departureIcao, props.record.departureAirportName),
    findIata(props.record.arrivalIcao, props.record.arrivalAirportName),
  ])
  airportIata.value = { departure: departureIata, arrival: arrivalIata }
  await refreshPreview()
}
async function refreshPreview() {
  if (!visible.value) return
  loading.value = true
  try { await renderImage() } catch { ElMessage.error('图片生成失败，请重试') } finally { loading.value = false }
}
function setColor(color) { accent.value = color; refreshPreview() }
function downloadImage() {
  if (!imageUrl.value) return
  const link = document.createElement('a')
  link.href = imageUrl.value
  link.download = `登机牌纪念图-${props.recordId}.png`
  link.click()
}
onBeforeUnmount(() => { if (imageUrl.value) URL.revokeObjectURL(imageUrl.value) })
</script>

<style scoped>
.ticket-style-switch { display:flex; align-items:center; gap:10px; margin-bottom:14px; }
.export-control { display:inline-flex; }
.ticket-style-switch > span { color:#697a81; font-size:13px; }
.passenger-name-input { display:flex; align-items:center; gap:8px; margin-left:auto; color:#697a81; font-size:13px; }
.passenger-name-input :deep(.el-input) { width:190px; }
.ticket-style-switch > button { width:25px; height:25px; border:2px solid #fff; border-radius:50%; box-shadow:0 0 0 1px #cdd7da; cursor:pointer; }
.ticket-style-switch > button.selected { box-shadow:0 0 0 2px #49666e; }
.preview-frame { display:flex; width:100%; max-width:100%; max-height:calc(100dvh - 270px); align-items:center; justify-content:center; overflow:hidden; box-sizing:border-box; border:1px solid #e3eaec; border-radius:7px; background:#f5f8f8; }
.preview-frame img { display:block; width:auto; height:auto; max-width:100%; max-height:calc(100dvh - 275px); object-fit:contain; }
.flight-export-dialog :deep(.el-dialog__body) { max-height:calc(100dvh - 170px); overflow-x:hidden; overflow-y:auto; }
@media(max-width:600px) { .ticket-style-switch { flex-wrap:wrap; }.passenger-name-input { width:100%; margin-left:0; }.passenger-name-input :deep(.el-input) { flex:1; width:auto; }.preview-frame { max-height:calc(100dvh - 290px); }.preview-frame img { max-height:calc(100dvh - 295px); } }
</style>
