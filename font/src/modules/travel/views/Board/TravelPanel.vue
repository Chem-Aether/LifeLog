<template>
  <section class="travel-panel">
    <header class="panel-header">
      <div><span>{{ mode === 'flight' ? 'FLIGHT LOG' : 'RAIL LOG' }}</span><h2>{{ selectedTicket ? '行程详情' : modeTitle }}</h2></div>
      <el-button circle text aria-label="关闭侧栏" @click="$emit('close')">×</el-button>
    </header>

    <div v-if="selectedTicket" class="panel-scroll detail-view">
      <el-button text class="back-button" @click="selectedTicket = null">← 返回全部行程</el-button>
      <article class="detail-route">
        <strong>{{ selectedTicket.From || '-' }}</strong>
        <div><b>{{ selectedTicket.Number || '未填写班次' }}</b><i></i><small>{{ selectedTicket.time || '时长未知' }}</small></div>
        <strong>{{ selectedTicket.To || '-' }}</strong>
      </article>
      <el-descriptions :column="1" border class="detail-fields">
        <el-descriptions-item v-for="item in selectedTicket.more || []" :key="item.label" :label="item.label">
          {{ item.value || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <section v-if="selectedTicket.routeStations?.length" class="station-route">
        <h3>途经站点</h3>
        <el-timeline>
          <el-timeline-item v-for="station in selectedTicket.routeStations" :key="station.name">{{ station.name }}</el-timeline-item>
        </el-timeline>
      </section>
    </div>

    <div v-else class="panel-scroll" v-loading="loading" element-loading-background="rgba(5, 17, 28, .72)">
      <div class="metric-grid">
        <div v-for="item in dashboard" :key="item.label" class="metric-item"><strong>{{ item.value }}</strong><span>{{ item.label }}</span></div>
      </div>
      <section v-if="statistics.length" class="distribution">
        <h3>{{ mode === 'flight' ? '航空公司分布' : '列车类型分布' }}</h3>
        <div v-for="item in statistics" :key="item.name" class="distribution-row">
          <span>{{ item.name || '其他' }}</span><i><b :style="{ width: `${barWidth(item.value)}%` }"></b></i><strong>{{ item.value }}</strong>
        </div>
      </section>
      <div class="ticket-heading"><h3>{{ mode === 'flight' ? '机票记录' : '车票记录' }}</h3><span>{{ filteredTickets.length }} 条</span></div>
      <el-empty v-if="!loading && !filteredTickets.length" description="暂无行程记录" :image-size="72" />
      <div class="ticket-list">
        <button v-for="ticket in filteredTickets" :key="ticketKey(ticket)" type="button" class="ticket-card" @click="selectedTicket = ticket">
          <span class="ticket-accent"></span>
          <span class="ticket-route"><strong>{{ ticket.From || '-' }}</strong><i></i><strong>{{ ticket.To || '-' }}</strong></span>
          <span class="ticket-meta"><b>{{ ticket.Number || '未填写班次' }}</b><span>{{ ticket.time || '时长未知' }}</span></span>
          <span class="ticket-date">{{ primaryTime(ticket) }}</span>
        </button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { getTickets, getTicketSummary } from '@/modules/travel/apis/travel.js'

const props = defineProps({
  mode: { type: String, required: true },
  routeSelection: { type: Object, default: null },
  selectedYear: { type: [String, Number], default: 'all' }
})
defineEmits(['close'])
const loading = ref(false)
const tickets = ref([])
const dashboard = ref([])
const statistics = ref([])
const selectedTicket = ref(null)
let requestId = 0

const modeTitle = computed(() => props.mode === 'flight' ? '航空行程' : '铁路行程')
const maximumStatistic = computed(() => Math.max(1, ...statistics.value.map(item => Number(item.value) || 0)))
const ticketKey = ticket => `${props.mode}-${ticket.flightId ?? ticket.trainId ?? ticket.Number}-${ticket.From}-${ticket.To}`
const primaryTime = ticket => ticket.more?.find(item => item.label === (props.mode === 'flight' ? '起飞时间' : '发车时间'))?.value || ''
const filteredTickets = computed(() => String(props.selectedYear) === 'all'
  ? tickets.value
  : tickets.value.filter(ticket => String(primaryTime(ticket)).startsWith(String(props.selectedYear))))
const barWidth = value => Math.max(6, (Number(value) || 0) / maximumStatistic.value * 100)

function matchesSelection(ticket, selection) {
  if (!selection || selection.type !== props.mode) return false
  const ticketId = props.mode === 'flight' ? ticket.flightId : ticket.trainId
  if (selection.id != null && ticketId != null) return String(selection.id) === String(ticketId)
  return selection.number && String(ticket.Number) === String(selection.number)
}

function applyRouteSelection() {
  if (!props.routeSelection) return
  const match = tickets.value.find(ticket => matchesSelection(ticket, props.routeSelection))
  if (match) selectedTicket.value = match
}

async function loadPanel() {
  if (!['flight', 'train'].includes(props.mode)) return
  const currentRequest = ++requestId
  loading.value = true
  selectedTicket.value = null
  try {
    const [ticketResult, summaryResult] = await Promise.all([
      getTickets(props.mode, props.selectedYear),
      getTicketSummary(props.mode, props.selectedYear)
    ])
    if (currentRequest !== requestId) return
    tickets.value = ticketResult.data || []
    dashboard.value = summaryResult.data?.metrics || []
    statistics.value = summaryResult.data?.distribution || []
    applyRouteSelection()
  } catch {
    if (currentRequest !== requestId) return
    tickets.value = []
    dashboard.value = []
    statistics.value = []
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}

watch(() => props.mode, loadPanel, { immediate: true })
watch(() => props.routeSelection, applyRouteSelection, { deep: true })
watch(() => props.selectedYear, loadPanel)
</script>

<style scoped>
.travel-panel { height: 100%; color: #dcecef; }
.panel-header { height: 78px; padding: 0 18px 0 22px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid rgba(142, 210, 220, .14); box-sizing: border-box; }
.panel-header span { color: #50cce0; font-size: 10px; }
.panel-header h2 { margin: 3px 0 0; font-size: 20px; letter-spacing: 0; }
.panel-header :deep(.el-button) { color: #aac4ca; font-size: 24px; }
.panel-scroll { height: calc(100% - 78px); overflow-y: auto; padding: 18px; box-sizing: border-box; scrollbar-width: thin; scrollbar-color: rgba(93, 174, 188, .48) transparent; }
.metric-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.metric-item { min-height: 72px; padding: 13px 14px; box-sizing: border-box; border: 1px solid rgba(127, 207, 219, .13); background: rgba(20, 49, 64, .42); border-radius: 5px; }
.metric-item strong { display: block; overflow-wrap: anywhere; color: #f4fbfc; font-size: 19px; }
.metric-item span { display: block; margin-top: 5px; color: #7899a2; font-size: 12px; }
.distribution { margin-top: 20px; }
.distribution h3, .ticket-heading h3, .station-route h3 { margin: 0; font-size: 14px; }
.distribution-row { display: grid; grid-template-columns: 72px 1fr 28px; align-items: center; gap: 9px; margin-top: 11px; color: #91abb2; font-size: 12px; }
.distribution-row > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.distribution-row i { height: 5px; overflow: hidden; background: rgba(105, 157, 168, .15); border-radius: 2px; }
.distribution-row b { display: block; height: 100%; background: #3dc2d8; border-radius: 2px; }
.distribution-row strong { color: #cce1e5; text-align: right; }
.ticket-heading { display: flex; align-items: center; justify-content: space-between; margin: 24px 0 10px; }
.ticket-heading span { color: #708e97; font-size: 12px; }
.ticket-list { display: grid; gap: 9px; }
.ticket-card { position: relative; width: 100%; min-height: 102px; padding: 15px 15px 13px 18px; overflow: hidden; border: 1px solid rgba(128, 200, 211, .14); border-radius: 5px; background: rgba(10, 31, 44, .62); color: inherit; text-align: left; cursor: pointer; transition: border-color .18s, background .18s, transform .18s; }
.ticket-card:hover { border-color: rgba(73, 201, 221, .58); background: rgba(20, 52, 68, .78); transform: translateY(-1px); }
.ticket-accent { position: absolute; inset: 0 auto 0 0; width: 3px; background: #42c8dd; }
.ticket-route { display: grid; grid-template-columns: minmax(0, 1fr) 48px minmax(0, 1fr); align-items: center; gap: 8px; }
.ticket-route strong { overflow: hidden; color: #f2f8fa; font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }
.ticket-route strong:last-child { text-align: right; }
.ticket-route i { height: 1px; position: relative; background: #62828a; }
.ticket-route i::after { content: ''; position: absolute; top: -3px; right: 0; border-width: 3px 0 3px 6px; border-style: solid; border-color: transparent transparent transparent #62828a; }
.ticket-meta { display: flex; justify-content: space-between; margin-top: 12px; color: #7899a2; font-size: 12px; }
.ticket-meta b { color: #50cce0; }
.ticket-date { display: block; margin-top: 4px; color: #607e87; font-size: 11px; }
.back-button { margin: -8px 0 12px -10px; color: #62cfdf; }
.detail-route { display: grid; grid-template-columns: minmax(0, 1fr) 100px minmax(0, 1fr); align-items: center; gap: 10px; min-height: 104px; padding: 18px 12px; border: 1px solid rgba(97, 199, 216, .2); border-radius: 5px; background: rgba(16, 44, 59, .52); }
.detail-route > strong { overflow: hidden; color: #f3fafb; font-size: 20px; text-overflow: ellipsis; white-space: nowrap; }
.detail-route > strong:last-child { text-align: right; }
.detail-route div { text-align: center; }
.detail-route b, .detail-route small { display: block; }
.detail-route b { color: #53cbdf; font-size: 13px; }
.detail-route i { display: block; height: 1px; margin: 8px 0; background: #62828a; }
.detail-route small { color: #77959d; }
.detail-fields { margin-top: 14px; }
.detail-fields :deep(.el-descriptions__body), .detail-fields :deep(.el-descriptions__table) { background: transparent; }
.detail-fields :deep(.el-descriptions__label.el-descriptions__cell.is-bordered-label) { width: 104px; background: rgba(17, 45, 59, .64); color: #7f9ea6; }
.detail-fields :deep(.el-descriptions__content.el-descriptions__cell.is-bordered-content) { background: rgba(8, 26, 38, .48); color: #d9e8eb; }
.detail-fields :deep(.el-descriptions__cell) { border-color: rgba(113, 184, 195, .16) !important; }
.station-route { margin-top: 20px; }
.station-route :deep(.el-timeline) { margin-top: 16px; padding-left: 6px; }
.station-route :deep(.el-timeline-item__content) { color: #bad0d5; }
.station-route :deep(.el-timeline-item__node) { background: #42c8dd; }
</style>
