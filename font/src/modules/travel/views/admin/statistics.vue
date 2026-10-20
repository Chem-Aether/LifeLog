<template>
  <main class="travel-statistics" v-loading="loading">
    <header class="page-header">
      <div>
        <span class="eyebrow">TRAVEL INSIGHTS</span>
        <h1>旅行统计</h1>
        <p>从每一段航程、每一次停靠和每个到访地点，回看你的旅行轨迹。</p>
      </div>
      <el-select v-model="selectedYear" aria-label="统计年份" class="year-filter">
        <el-option label="全部年份" value="all" />
        <el-option v-for="year in years" :key="year" :label="`${year} 年`" :value="year" />
      </el-select>
    </header>

    <section class="overview-section">
      <header class="section-heading"><div><span class="eyebrow">PLACES & MOMENTS</span><h2>足迹与城市</h2></div><p>回看去过的城市与地点</p></header>
      <div class="metric-grid" aria-label="足迹关键指标">
        <article class="metric metric-days"><span>出行天数</span><strong>{{ stats.days }}</strong><small>有记录的日期</small></article>
        <article class="metric metric-places"><span>到访城市</span><strong>{{ stats.cities }}</strong><small>已标注城市</small></article>
        <article class="metric metric-photos"><span>足迹地点</span><strong>{{ stats.places }}</strong><small>{{ stats.photos }} 张旅行照片</small></article>
      </div>
      <div class="chart-grid">
        <article class="panel city-panel">
          <header class="panel-heading"><div><h3>常去城市</h3><p>按足迹记录次数排序</p></div></header>
          <div ref="cityEl" class="chart rank-chart" aria-label="到访城市排行榜" />
        </article>
      </div>
    </section>

    <section class="calendar-section">
      <header class="section-heading"><div><span class="eyebrow">TRAVEL CALENDAR</span><h2>出行日历</h2></div><p>每日出行记录</p></header>
      <article class="panel calendar-panel">
        <div class="calendar-legend" aria-label="日历图例">
          <span><i class="legend-footprint" />足迹</span><span><i class="legend-train" />铁路</span><span><i class="legend-flight" />航空</span><span><i class="legend-mixed" />同日多类</span><span class="legend-intensity">颜色越深，记录越多</span>
        </div>
        <div v-if="calendarMonths.length" class="calendar-month-grid">
          <section v-for="month in calendarMonths" :key="month.label" class="calendar-month">
            <h3>{{ month.label }}</h3>
            <div class="month-weekdays"><span v-for="day in weekdayLabels" :key="day">{{ day }}</span></div>
            <div class="month-days">
              <span v-for="(day, index) in month.days" :key="day?.date || `${month.label}-${index}`" :class="calendarDayClass(day)" :style="calendarDayStyle(day)" :title="calendarDayTitle(day)" :aria-label="calendarDayTitle(day)" />
            </div>
          </section>
        </div>
        <p v-else class="calendar-empty">暂无可展示的出行记录</p>
      </article>
      <header class="section-heading preference-heading"><div><span class="eyebrow">TRAVEL PREFERENCES</span><h2>出行偏好</h2></div><p>按航班起飞日与铁路发车日统计</p></header>
      <div class="preference-grid">
        <article class="panel">
          <header class="panel-heading"><div><h3>季节偏好</h3><p>春夏秋冬出行次数</p></div></header>
          <div ref="seasonEl" class="chart preference-chart" aria-label="春夏秋冬出行偏好雷达图" />
        </article>
        <article class="panel">
          <header class="panel-heading"><div><h3>月份偏好</h3><p>一月至十二月出行次数</p></div></header>
          <div ref="monthEl" class="chart preference-chart" aria-label="一月至十二月出行偏好雷达图" />
        </article>
        <article class="panel">
          <header class="panel-heading"><div><h3>星期偏好</h3><p>周一至周日出行次数</p></div></header>
          <div ref="weekdayEl" class="chart preference-chart" aria-label="星期出行偏好雷达图" />
        </article>
      </div>
    </section>

    <section class="compass-section">
      <div class="footprint-insights-grid">
        <article class="compass-panel">
          <header class="panel-heading"><div><h3>足迹方位</h3><p>按地点坐标寻找旅行版图的四个方向</p></div></header>
          <div class="compass-card">
            <div class="compass-layout">
              <div class="compass-point direction-north"><span class="direction-label">北 · 最北</span><strong>{{ directions.north.placeName }}</strong><small>{{ directions.north.cityName }} · {{ directions.north.coordinate }}</small></div>
              <div class="compass-point direction-east"><span class="direction-label">东 · 最东</span><strong>{{ directions.east.placeName }}</strong><small>{{ directions.east.cityName }} · {{ directions.east.coordinate }}</small></div>
              <div class="compass-point direction-south"><span class="direction-label">南 · 最南</span><strong>{{ directions.south.placeName }}</strong><small>{{ directions.south.cityName }} · {{ directions.south.coordinate }}</small></div>
              <div class="compass-point direction-west"><span class="direction-label">西 · 最西</span><strong>{{ directions.west.placeName }}</strong><small>{{ directions.west.cityName }} · {{ directions.west.coordinate }}</small></div>
              <div class="compass-center"><span class="compass-rose"><i></i></span><strong>{{ directions.count }}</strong><small>个有坐标足迹</small></div>
            </div>
          </div>
        </article>
        <article class="panel category-panel">
          <header class="panel-heading"><div><h3>地点类型</h3><p>旅行地点分类记录次数</p></div></header>
          <div ref="locationTypeEl" class="chart category-chart" aria-label="地点类型统计" />
        </article>
      </div>
    </section>

    <header class="section-heading transport-heading"><div><span class="eyebrow">TRANSPORT BREAKDOWN</span><h2>交通方式分析</h2></div></header>
    <section class="transport-grid">
      <article class="transport-column rail-column">
        <header class="transport-title"><div><span class="transport-icon rail-icon">铁</span><div><h2>铁路</h2><p>列车、车型与常到车站</p></div></div></header>
        <div class="transport-metrics">
          <div><span>总里程</span><strong>{{ stats.trainKm }}</strong></div>
          <div><span>总次数</span><strong>{{ stats.trainCount }}</strong></div>
          <div><span>总时长</span><strong>{{ stats.trainDuration }}</strong></div>
          <div><span>经停站</span><strong>{{ stats.stationCount }}</strong></div>
        </div>
        <div class="transport-records">
          <article v-for="record in personalRecords.train" :key="record.key" class="transport-record">
            <span>{{ record.label }}</span><strong>{{ record.value }}</strong><small>{{ record.route }}</small><small>{{ record.date }}</small>
          </article>
        </div>
        <div class="transport-charts">
          <section class="transport-chart"><h3>铁路类型</h3><div ref="trainTypeEl" class="chart transport-rank" aria-label="铁路类型统计" /></section>
          <section class="transport-chart"><h3>车型系列</h3><div ref="trainModelEl" class="chart transport-rank" aria-label="列车车型系列统计" /></section>
          <section class="transport-chart cloud-section"><h3>常去车站 <small>按记录频次</small></h3><div class="word-cloud" aria-label="常去车站词云"><span v-for="item in stationCloud" :key="item.name" :title="`${item.name} · ${item.value} 次`" :style="item.style">{{ item.name }}</span><em v-if="!stationCloud.length">暂无车站记录</em></div></section>
        </div>
      </article>

      <article class="transport-column air-column">
        <header class="transport-title"><div><span class="transport-icon air-icon">航</span><div><h2>航空</h2><p>航司、机型与常到机场</p></div></div></header>
        <div class="transport-metrics">
          <div><span>总航程</span><strong>{{ stats.flightKm }}</strong></div>
          <div><span>总次数</span><strong>{{ stats.flightCount }}</strong></div>
          <div><span>总航时</span><strong>{{ stats.flightDuration }}</strong></div>
          <div><span>到访机场</span><strong>{{ stats.airportCount }}</strong></div>
        </div>
        <div class="transport-records">
          <article v-for="record in personalRecords.flight" :key="record.key" class="transport-record">
            <span>{{ record.label }}</span><strong>{{ record.value }}</strong><small>{{ record.route }}</small><small>{{ record.date }}<template v-if="record.note"> · {{ record.note }}</template></small>
          </article>
        </div>
        <div class="transport-charts">
          <section class="transport-chart"><h3>航空公司（航司代码）</h3><div ref="airlineEl" class="chart transport-rank" aria-label="航空公司统计" /></section>
          <section class="transport-chart"><h3>机型系列</h3><div ref="aircraftEl" class="chart transport-rank" aria-label="飞机机型系列统计" /></section>
          <section class="transport-chart cloud-section"><h3>常去机场 <small>按记录频次</small></h3><div class="word-cloud" aria-label="常去机场词云"><span v-for="item in airportCloud" :key="item.name" :title="`${item.name} · ${item.value} 次`" :style="item.style">{{ item.name }}</span><em v-if="!airportCloud.length">暂无机场记录</em></div></section>
        </div>
      </article>
    </section>
    <p class="data-note">统计基于当前已录入的航班、铁路行程与非途经足迹；缺失日期、城市或机型的记录不会计入对应维度。</p>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { getFootprints } from '@/modules/travel/apis/travel.js'
import { enrichFootprintRegions } from '@/modules/travel/apis/sysResource.js'
import { getAllFlights } from '@/modules/travel/apis/flightTickets.js'
import { getAllTrains } from '@/modules/travel/apis/trainTickets.js'
import { toFlightTrip, toTrainTrip } from '@/modules/travel/utils/tripView.js'
import { getFootprintRegion } from '@/modules/travel/utils/footprintRegion.js'
import { footprintCategoryOptions, getFootprintCategory } from '@/modules/travel/stores/footprintCategories.js'
import { summarizeFlights, summarizeTrains } from '@/modules/travel/utils/travelMetrics.js'
import { getCssThemeColor, getCssThemePalette } from '@/utils/cssTheme.js'

const selectedYear = ref('all')
const loading = ref(false)
const tickets = ref([])
const footprints = ref([])
const cityEl = ref(null)
const locationTypeEl = ref(null)
const aircraftEl = ref(null)
const trainModelEl = ref(null)
const seasonEl = ref(null)
const monthEl = ref(null)
const weekdayEl = ref(null)
const trainTypeEl = ref(null)
const airlineEl = ref(null)
const charts = []
const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']
const travelPalette = getCssThemePalette('travel', 5)

const ticketDate = ticket => detail(ticket, ticket.ticketType === 'flight' ? '起飞时间' : '发车时间')
const years = computed(() => [...new Set([
  ...tickets.value.map(ticket => ticketDate(ticket).slice(0, 4)),
  ...footprints.value.map(item => String(item.visitDate || '').slice(0, 4))
].filter(year => /^\d{4}$/.test(year)))].sort((a, b) => b.localeCompare(a)))
const activeTickets = computed(() => selectedYear.value === 'all'
  ? tickets.value
  : tickets.value.filter(ticket => ticketDate(ticket).startsWith(selectedYear.value)))
const flightSummary = computed(() => summarizeFlights(activeTickets.value
  .filter(ticket => ticket.ticketType === 'flight').map(ticket => ticket.record)))
const trainSummary = computed(() => summarizeTrains(activeTickets.value
  .filter(ticket => ticket.ticketType === 'train').map(ticket => ticket.record)))
const activeFootprints = computed(() => {
  const records = footprints.value.filter(item => item.visitType !== 'transit')
  return selectedYear.value === 'all' ? records : records.filter(item => String(item.visitDate || '').startsWith(selectedYear.value))
})
const directions = computed(() => {
  const records = activeFootprints.value.filter(validCoordinates)
  const selectExtreme = (field, compare) => records.reduce((extreme, item) => {
    if (!extreme || compare(Number(item[field]), Number(extreme[field]))) return item
    return extreme
  }, null)
  const format = item => item ? {
    placeName: item.placeName || '未命名地点',
    cityName: getFootprintRegion(item).cityName || '城市未标注',
    coordinate: `${Number(item.longitude).toFixed(2)}, ${Number(item.latitude).toFixed(2)}`
  } : { placeName: '暂无坐标记录', cityName: '', coordinate: '' }
  return {
    count: records.length.toLocaleString('zh-CN'),
    north: format(selectExtreme('latitude', (value, current) => value > current)),
    south: format(selectExtreme('latitude', (value, current) => value < current)),
    east: format(selectExtreme('longitude', (value, current) => value > current)),
    west: format(selectExtreme('longitude', (value, current) => value < current))
  }
})
const periodLabel = computed(() => selectedYear.value === 'all' ? '全部年份' : `${selectedYear.value} 年`)
const stats = computed(() => {
  const flights = activeTickets.value.filter(item => item.ticketType === 'flight')
  const trains = activeTickets.value.filter(item => item.ticketType === 'train')
  const stations = new Set(trains.flatMap(ticket => ticketStationNames(ticket)))
  const airports = new Set(flights.flatMap(ticket => [ticket.From, ticket.To].map(normalizeName).filter(Boolean)))
  const cities = new Set(activeFootprints.value.map(item => getFootprintRegion(item).cityName.trim()).filter(Boolean))
  const days = new Set([
    ...activeTickets.value.map(item => dateKey(ticketDate(item))),
    ...activeFootprints.value.map(item => dateKey(item.visitDate))
  ].filter(Boolean))
  return {
    days: days.size.toLocaleString('zh-CN'),
    cities: cities.size.toLocaleString('zh-CN'),
    places: activeFootprints.value.length.toLocaleString('zh-CN'),
    photos: activeFootprints.value.reduce((sum, item) => sum + (Array.isArray(item.mediaIds) ? item.mediaIds.length : 0), 0).toLocaleString('zh-CN'),
    flightCount: flights.length,
    trainCount: trains.length,
    flightKm: metricNumber(flightSummary.value, '航程'),
    trainKm: metricNumber(trainSummary.value, '里程'),
    flightDuration: metricText(flightSummary.value, '航时'),
    trainDuration: metricText(trainSummary.value, '时长'),
    stationCount: stations.size.toLocaleString('zh-CN'),
    airportCount: airports.size.toLocaleString('zh-CN')
  }
})
const calendarYear = computed(() => selectedYear.value === 'all' ? (years.value[0] || '') : String(selectedYear.value))

const calendarMonths = computed(() => {
  if (!calendarYear.value) return []
  const year = Number(calendarYear.value)
  const counts = new Map()
  const add = (date, type) => {
    if (!date.startsWith(calendarYear.value)) return
    if (!counts.has(date)) counts.set(date, { footprint: 0, train: 0, flight: 0 })
    counts.get(date)[type] += 1
  }
  activeTickets.value.forEach(ticket => {
    const type = ticket.ticketType === 'flight' ? 'flight' : 'train'
    add(dateKey(ticketDate(ticket)), type)
  })
  activeFootprints.value.forEach(item => add(dateKey(item.visitDate), 'footprint'))
  return Array.from({ length: 12 }, (_, monthIndex) => {
    const first = new Date(year, monthIndex, 1, 12)
    const offset = (first.getDay() + 6) % 7
    const dayCount = new Date(year, monthIndex + 1, 0).getDate()
    const days = Array(offset).fill(null)
    for (let day = 1; day <= dayCount; day += 1) {
      const date = `${calendarYear.value}-${String(monthIndex + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
      const activity = counts.get(date) || { footprint: 0, train: 0, flight: 0 }
      const types = Object.keys(activity).filter(type => activity[type] > 0)
      days.push({ date, ...activity, total: Object.values(activity).reduce((sum, count) => sum + count, 0), types })
    }
    while (days.length % 7) days.push(null)
    return { label: `${monthIndex + 1} 月`, days }
  })
})
const personalRecords = computed(() => {
  const flights = activeTickets.value.filter(ticket => ticket.ticketType === 'flight')
  const trains = activeTickets.value.filter(ticket => ticket.ticketType === 'train')
  const makeRecord = (key, label, record, formatter, note = '') => ({
    key,
    label,
    value: record ? formatter(record.value) : '暂无数据',
    route: record ? `${record.ticket.From || '未知起点'} → ${record.ticket.To || '未知终点'}` : '缺少可计算的行程记录',
    date: record ? dateKey(ticketDate(record.ticket)) : '',
    note: record ? note : ''
  })
  const best = (list, measure) => list
    .map(ticket => ({ ticket, value: measure(ticket) }))
    .filter(record => Number.isFinite(record.value) && record.value > 0)
    .sort((a, b) => b.value - a.value)[0]
  const distanceFormatter = value => `${Math.round(value).toLocaleString('zh-CN')} km`
  const durationFormatter = value => formatDuration(value)
  return {
    train: [
      makeRecord('train-distance', '距离之最', best(trains, ticketDistance), distanceFormatter),
      makeRecord('train-duration', '时长之最', best(trains, durationMinutes), durationFormatter)
    ],
    flight: [
      makeRecord('flight-distance', '距离之最', best(flights, flightDistance), distanceFormatter),
      makeRecord('flight-duration', '时长之最', best(flights, durationMinutes), durationFormatter)
    ]
  }
})

function detail(ticket, label) {
  return ticket?.more?.find(item => item.label === label)?.value || ''
}

function durationMinutes(ticket) {
  const value = String(ticket.time || '')
  const hours = Number(value.match(/(\d+)\s*h/i)?.[1] || 0)
  const minutes = Number(value.match(/(\d+)\s*m/i)?.[1] || 0)
  return hours * 60 + minutes
}

function formatDuration(minutes) {
  const hours = Math.floor(minutes / 60)
  const remainder = minutes % 60
  return hours ? `${hours} 小时 ${remainder} 分` : `${remainder} 分钟`
}

function ticketDistance(ticket) {
  if (ticket.ticketType === 'train') {
    const value = detail(ticket, '里程')
    return Number(String(value).replace(/[^\d.]/g, '')) || 0
  }
  return flightDistance(ticket)
}

function flightDistance(ticket) {
  const recordedDistance = Number(ticket.record?.distanceKm)
  if (Number.isFinite(recordedDistance) && recordedDistance > 0) return recordedDistance
  if ([ticket.fromLongitude, ticket.fromLatitude, ticket.toLongitude, ticket.toLatitude].some(value => value == null || value === '')) return 0
  const longitude1 = Number(ticket.fromLongitude)
  const latitude1 = Number(ticket.fromLatitude)
  const longitude2 = Number(ticket.toLongitude)
  const latitude2 = Number(ticket.toLatitude)
  if (![longitude1, latitude1, longitude2, latitude2].every(Number.isFinite)) return 0
  const radians = value => value * Math.PI / 180
  const latitudeDelta = radians(latitude2 - latitude1)
  const longitudeDelta = radians(longitude2 - longitude1)
  const value = Math.min(1, Math.sin(latitudeDelta / 2) ** 2
    + Math.cos(radians(latitude1)) * Math.cos(radians(latitude2)) * Math.sin(longitudeDelta / 2) ** 2
  )
  return 6371 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value))
}

function calendarDayClass(day) {
  if (!day) return 'calendar-day is-empty'
  if (!day.total) return 'calendar-day'
  const kind = day.types.length > 1 ? 'mixed' : day.types[0]
  return `calendar-day is-${kind} level-${Math.min(4, day.total)}`
}

function calendarDayStyle(day) {
  if (!day || day.types.length < 2) return undefined
  const colors = { flight: getCssThemeColor('--travel-chart-2'), train: getCssThemeColor('--travel-chart-3'), footprint: getCssThemeColor('--travel-chart-1') }
  const stops = day.types.map((type, index) => `${colors[type]} ${Math.round(index / day.types.length * 100)}%`)
  stops.push(`${colors[day.types[day.types.length - 1]]} 100%`)
  return { background: `linear-gradient(135deg, ${stops.join(', ')})` }
}

function calendarDayTitle(day) {
  if (!day) return ''
  const details = [
    day.flight ? `航空 ${day.flight}` : '',
    day.train ? `铁路 ${day.train}` : '',
    day.footprint ? `足迹 ${day.footprint}` : ''
  ].filter(Boolean)
  return details.length ? `${day.date} · ${details.join(' · ')}` : `${day.date} · 无记录`
}

function normalizeName(value) {
  return String(value || '').trim()
}

function validCoordinates(item) {
  const longitude = Number(item.longitude)
  const latitude = Number(item.latitude)
  return item.longitude != null && item.longitude !== ''
    && item.latitude != null && item.latitude !== ''
    && Number.isFinite(longitude) && Number.isFinite(latitude)
    && longitude >= -180 && longitude <= 180
    && latitude >= -90 && latitude <= 90
}

function ticketStationNames(ticket) {
  const names = [ticket.From, ticket.To, ...(Array.isArray(ticket.routeStations) ? ticket.routeStations.map(item => item.name) : [])]
  return [...new Set(names.map(normalizeName).filter(Boolean))]
}

function modelFamily(value) {
  return String(value || '').split(/[-‐‑‒–—]/, 1)[0].replace(/\s+/g, '').toLocaleUpperCase() || '未填写型号'
}

function dateKey(value) {
  const match = String(value || '').match(/^(\d{4})[/-](\d{1,2})[/-](\d{1,2})/)
  return match ? `${match[1]}-${String(match[2]).padStart(2, '0')}-${String(match[3]).padStart(2, '0')}` : ''
}

function metricText(summary, label) {
  return summary?.metrics?.find(item => item.label === label)?.value || '0h0m'
}

function metricNumber(summary, label) {
  const value = metricText(summary, label)
  const number = Number(String(value).replace(/[\s,]/g, '').match(/[\d.]+/)?.[0] || 0)
  return `${Math.round(number).toLocaleString('zh-CN')} km`
}

function currentRows() {
  const events = activeTickets.value.map(ticket => ({
    type: ticket.ticketType,
    date: dateKey(ticketDate(ticket)),
    ticket
  })).filter(item => item.date)
  const places = activeFootprints.value.map(item => ({
    city: getFootprintRegion(item).cityName.trim(),
    locationType: item.locationType
  }))
  return { events, places }
}

const stationCloud = computed(() => makeWordCloud(activeTickets.value
  .filter(ticket => ticket.ticketType === 'train')
  .flatMap(ticketStationNames)))
const airportCloud = computed(() => makeWordCloud(activeTickets.value
  .filter(ticket => ticket.ticketType === 'flight')
  .flatMap(ticket => [...new Set([ticket.From, ticket.To].map(normalizeName).filter(Boolean))])))

function makeWordCloud(names) {
  const counts = new Map()
  names.forEach(name => counts.set(name, (counts.get(name) || 0) + 1))
  const rows = groupTop(counts, 28)
  const max = Math.max(1, ...rows.map(item => item.value))
  const colors = travelPalette
  return rows.map((item, index) => ({
    ...item,
    style: {
      fontSize: `${12 + Math.round(Math.sqrt(item.value / max) * 9)}px`,
      color: colors[index % colors.length],
      fontWeight: item.value === max ? 700 : 550
    }
  }))
}

function groupTop(entries, limit = 8) {
  return [...entries.entries()].map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value || a.name.localeCompare(b.name, 'zh-CN'))
    .slice(0, limit)
}

function makeChart(element, option) {
  if (!element) return
  let chart = echarts.getInstanceByDom(element)
  if (!chart) {
    chart = echarts.init(element)
    charts.push(chart)
  }
  chart.setOption(option, true)
}

function baseOption() {
  return {
    animationDuration: 500,
    textStyle: { color: '#71818a', fontFamily: 'inherit' },
    tooltip: { trigger: 'axis', backgroundColor: '#fff', borderColor: '#dce6e6', textStyle: { color: '#344b50' }, confine: true }
  }
}

function renderRank(element, data, color, unit = '次') {
  const rows = data.slice().reverse()
  makeChart(element, {
    ...baseOption(),
    tooltip: { ...baseOption().tooltip, trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: params => `${params[0]?.name}<br/>${params[0]?.value ?? 0} ${unit}` },
    grid: { top: 5, right: 35, bottom: 8, left: 8, containLabel: true },
    xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf1f2' } }, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'category', data: rows.map(item => item.name), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { width: 110, overflow: 'truncate', fontSize: 11, color: '#52666d' } },
    series: [{
      type: 'bar',
      data: rows.map(item => ({
        value: item.value,
        itemStyle: { color: Array.isArray(color) ? color[stablePaletteIndex(item.name, color.length)] : color }
      })),
      barMaxWidth: 17,
      itemStyle: { borderRadius: [0, 3, 3, 0] },
      label: { show: true, position: 'right', color: '#63767d', fontSize: 10 }
    }]
  })
}

function stablePaletteIndex(value, length) {
  let hash = 0
  for (const character of String(value)) hash = (hash * 31 + character.codePointAt(0)) >>> 0
  return hash % length
}

function renderPreferenceRadar(element, indicators, values, colors, palette) {
  const maximum = Math.max(1, ...values)
  const indicator = indicators.map(name => ({ name, max: maximum }))
  const axisStyles = Object.fromEntries(Object.entries(colors).map(([name, color], index) => [name, `axis${index}`]))
  const rich = Object.fromEntries(Object.entries(colors).map(([name, color]) => [axisStyles[name], { color, fontSize: 11, fontWeight: 600 }]))
  const gradient = new echarts.graphic.LinearGradient(0, 0, 1, 1, palette.map((color, index) => ({
    offset: index / (palette.length - 1),
    color
  })))
  makeChart(element, {
    ...baseOption(),
    tooltip: {
      ...baseOption().tooltip,
      trigger: 'item',
      formatter: params => `${params.name}<br/>${indicators.map((name, index) => `${name}：${params.value?.[index] ?? 0} 次`).join('<br/>')}`
    },
    radar: {
      center: ['50%', '55%'],
      radius: '65%',
      indicator,
      splitNumber: 4,
      axisName: {
        formatter: name => axisStyles[name] ? `{${axisStyles[name]}|${name}}` : name,
        rich: { ...rich, default: { color: '#71818a', fontSize: 10 } }
      },
      axisLine: { lineStyle: { color: '#dce5e7' } },
      splitLine: { lineStyle: { color: '#e8eef0' } },
      splitArea: { areaStyle: { color: ['#fff', '#f8fafb'] } }
    },
    series: [{
      type: 'radar',
      data: [{
        name: '出行次数', value: values,
        lineStyle: { color: gradient, width: 2.5 },
        itemStyle: { color: palette[0], borderColor: '#fff', borderWidth: 1.5 },
        areaStyle: { color: gradient, opacity: 0.18 },
        symbol: 'circle', symbolSize: 5
      }]
    }]
  })
}

function renderTravelPreferences(events) {
  const seasonCounts = [0, 0, 0, 0]
  const monthCounts = Array(12).fill(0)
  const weekdayCounts = Array(7).fill(0)
  for (const item of events) {
    if (!item.date) continue
    const date = new Date(`${item.date}T12:00:00`)
    const month = date.getMonth()
    if (month >= 0 && month <= 11) seasonCounts[[3, 3, 0, 0, 0, 1, 1, 1, 2, 2, 2, 3][month]] += 1
    if (month >= 0 && month <= 11) monthCounts[month] += 1
    weekdayCounts[(date.getDay() + 6) % 7] += 1
  }
  const seasons = ['--travel-season-spring', '--travel-season-summer', '--travel-season-autumn', '--travel-season-winter'].map(name => getCssThemeColor(name))
  renderPreferenceRadar(seasonEl.value, ['春', '夏', '秋', '冬'], seasonCounts, {
    春: seasons[0], 夏: seasons[1], 秋: seasons[2], 冬: seasons[3]
  }, seasons)
  renderPreferenceRadar(monthEl.value, Array.from({ length: 12 }, (_, index) => `${index + 1}月`), monthCounts, {}, seasons)
  renderPreferenceRadar(weekdayEl.value, ['周一', '周二', '周三', '周四', '周五', '周六', '周日'], weekdayCounts, {}, [travelPalette[1], travelPalette[0]])
}

function paintCharts() {
  if (!cityEl.value) return
  const { events, places } = currentRows()
  const cityCounts = new Map()
  const locationTypeCounts = new Map()
  const aircraftCounts = new Map()
  const trainModelCounts = new Map()
  for (const item of places) {
    const city = item.city || '未标注城市'
    cityCounts.set(city, (cityCounts.get(city) || 0) + 1)
    const category = getFootprintCategory(item.locationType) || getFootprintCategory('other_place')
    locationTypeCounts.set(category.value, (locationTypeCounts.get(category.value) || 0) + 1)
  }
  for (const event of events) {
    if (event.type === 'flight') {
      const model = modelFamily(detail(event.ticket, '机型'))
      aircraftCounts.set(model, (aircraftCounts.get(model) || 0) + 1)
    } else {
      const value = detail(event.ticket, '类型/车型')
      const [, ...modelParts] = value.split(' / ')
      const modelValue = modelParts.join(' / ')
      const model = modelFamily(modelValue)
      trainModelCounts.set(model, (trainModelCounts.get(model) || 0) + 1)
    }
  }
  renderRank(cityEl.value, groupTop(cityCounts), travelPalette)
  renderLocationTypeStats(locationTypeEl.value, locationTypeCounts)
  renderTravelPreferences(events)
  const trainTypeCounts = new Map((trainSummary.value?.distribution || []).map(item => [item.name || '其他', Number(item.value) || 0]))
  const airlineCounts = new Map((flightSummary.value?.distribution || []).map(item => [item.name || '其他', Number(item.value) || 0]))
  renderRank(trainTypeEl.value, groupTop(trainTypeCounts), '#d39643')
  renderRank(trainModelEl.value, groupTop(trainModelCounts), '#7a8f62')
  renderRank(airlineEl.value, groupTop(airlineCounts), '#258b88')
  renderRank(aircraftEl.value, groupTop(aircraftCounts), '#4f8fc9')
}

function renderLocationTypeStats(element, counts) {
  const ranked = groupTop(counts, Number.MAX_SAFE_INTEGER)
  const visible = ranked.slice(0, 8).map(item => {
    const category = getFootprintCategory(item.name)
    const categoryIndex = footprintCategoryOptions.findIndex(option => option.value === item.name)
    return {
      ...item,
      name: category?.label || '其他地点',
      color: travelPalette[categoryIndex < 0 ? 0 : categoryIndex % travelPalette.length]
    }
  })
  const remaining = ranked.slice(8).reduce((total, item) => total + item.value, 0)
  if (remaining) visible.push({ name: '其余类型', value: remaining, color: '#87949a' })
  const rows = visible.slice().reverse()
  makeChart(element, {
    ...baseOption(),
    tooltip: { ...baseOption().tooltip, trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: params => `${params[0]?.name}<br/>${params[0]?.value ?? 0} 个地点` },
    grid: { top: 5, right: 42, bottom: 8, left: 8, containLabel: true },
    xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf1f2' } }, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'category', data: rows.map(item => item.name), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { width: 90, overflow: 'truncate', fontSize: 10, color: '#52666d' } },
    series: [{ type: 'bar', data: rows.map(item => ({ value: item.value, itemStyle: { color: item.color } })), barMaxWidth: 16, itemStyle: { borderRadius: [0, 3, 3, 0] }, label: { show: true, position: 'right', color: '#63767d', fontSize: 10 } }]
  })
}

async function loadData() {
  loading.value = true
  const [flightResult, trainResult, footprintResult] = await Promise.allSettled([
    getAllFlights(), getAllTrains(), getFootprints()
  ])
  const flights = flightResult.status === 'fulfilled' ? flightResult.value : []
  const trains = trainResult.status === 'fulfilled' ? trainResult.value : []
  tickets.value = [...flights.map(toFlightTrip), ...trains.map(toTrainTrip)]
  if (flightResult.status === 'rejected' || trainResult.status === 'rejected') ElMessage.error('旅行记录加载失败')
  if (footprintResult.status === 'fulfilled') {
    footprints.value = await enrichFootprintRegions(footprintResult.value.data || [])
  }
  else ElMessage.error('足迹统计加载失败')
  await nextTick()
  paintCharts()
  loading.value = false
}

function resizeCharts() { charts.forEach(chart => chart.resize()) }

watch(selectedYear, async () => {
  await nextTick()
  paintCharts()
})

onMounted(async () => {
  await loadData()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  charts.forEach(chart => chart.dispose())
  charts.length = 0
})
</script>

<style scoped>
.travel-statistics { --ink:#293b49; --muted:#7c8b95; --line:#e3e9ec; --accent:#258b88; min-height:calc(100vh - 52px); color:var(--ink); }
.page-header { display:flex; align-items:flex-end; justify-content:space-between; gap:18px; margin-bottom:16px; }
.page-header h1 { margin:4px 0 5px; padding:0; border:0; color:var(--ink); font-size:24px; font-weight:720; line-height:1.25; }
.page-header h1::before { display:none; }
.page-header p { margin:0; color:var(--muted); font-size:12px; line-height:1.5; }
.eyebrow { color:var(--accent); font-size:9px; font-weight:700; letter-spacing:1px; }
.year-filter { width:132px; flex:0 0 auto; }
.section-heading { display:flex; align-items:flex-end; justify-content:space-between; gap:12px; margin:18px 1px 10px; }
.section-heading h2 { margin:3px 0 0; color:#334951; font-size:17px; font-weight:700; }
.section-heading > p { margin:0; color:#91a0a4; font-size:11px; text-align:right; }
.overview-section > .section-heading { margin-top:0; }
.metric-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:9px; margin-bottom:12px; }
.metric { position:relative; display:flex; min-width:0; min-height:104px; flex-direction:column; justify-content:center; gap:6px; padding:13px 15px; overflow:hidden; border:1px solid var(--line); border-top:2px solid var(--metric-color); border-radius:6px; background:#fff; }
.metric::after { position:absolute; right:-13px; bottom:-22px; width:66px; height:66px; border:1px solid color-mix(in srgb,var(--metric-color),transparent 82%); border-radius:50%; content:""; }
.metric > span { color:#7d8d94; font-size:11px; }
.metric strong { overflow:hidden; color:#344b50; font-size:23px; font-variant-numeric:tabular-nums; text-overflow:ellipsis; white-space:nowrap; }
.metric small { color:#99a5a8; font-size:10px; }
.metric-days { --metric-color:#319889; }.metric-places { --metric-color:#5a91c1; }.metric-photos { --metric-color:#d39a4d; }.metric-flights { --metric-color:#4f8fc9; }.metric-rail { --metric-color:#829a67; }
.chart-grid { display:grid; grid-template-columns:minmax(0,1fr); gap:11px; }
.preference-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:11px; margin-top:11px; }
.panel { min-width:0; padding:14px 16px 11px; border:1px solid var(--line); border-radius:6px; background:#fff; }
.panel-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; }
.panel-heading h2,.panel-heading h3 { margin:0; color:#334951; font-size:14px; font-weight:680; }
.panel-heading p { margin:4px 0 0; color:#91a0a4; font-size:10px; }
.panel-mark { color:#d8e2e3; font-size:10px; font-variant-numeric:tabular-nums; }
.chart { width:100%; height:238px; }
.preference-chart { height:220px; }
.calendar-section { margin-top:22px; }
.calendar-panel { padding:15px 16px; }
.calendar-legend { display:flex; justify-content:flex-end; flex-wrap:wrap; gap:7px 14px; margin-bottom:14px; }
.calendar-legend > span { display:inline-flex; align-items:center; gap:5px; color:#7c8b95; font-size:10px; }
.calendar-legend .legend-intensity { color:#a0abad; }
.calendar-legend i { width:10px; height:10px; border-radius:2px; }
.legend-footprint { background:#319889; }.legend-train { background:#d39643; }.legend-flight { background:#4f8fc9; }.legend-mixed { background:linear-gradient(135deg,#4f8fc9,#d39643,#319889); }
.calendar-month-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:18px 20px; }
.calendar-month { min-width:0; }
.calendar-month h3 { margin:0 0 7px; color:#52666d; font-size:11px; font-weight:650; }
.month-weekdays,.month-days { display:grid; grid-template-columns:repeat(7,minmax(0,1fr)); justify-items:center; gap:3px; }
.month-weekdays { margin-bottom:4px; }
.month-weekdays span { color:#a0abad; font-size:8px; line-height:1.2; }
.calendar-day { width:100%; max-width:16px; aspect-ratio:1; border-radius:3px; background:#f0f3f4; }
.calendar-day.is-empty { visibility:hidden; }
.calendar-day.is-footprint.level-1 { background:#dcedea; }.calendar-day.is-footprint.level-2 { background:#abd5ce; }.calendar-day.is-footprint.level-3 { background:#70b8ad; }.calendar-day.is-footprint.level-4 { background:#319889; }
.calendar-day.is-train.level-1 { background:#f5e9d6; }.calendar-day.is-train.level-2 { background:#e9cb9c; }.calendar-day.is-train.level-3 { background:#dfae63; }.calendar-day.is-train.level-4 { background:#d39643; }
.calendar-day.is-flight.level-1 { background:#e0edf5; }.calendar-day.is-flight.level-2 { background:#b8d4e5; }.calendar-day.is-flight.level-3 { background:#83b5d4; }.calendar-day.is-flight.level-4 { background:#4f8fc9; }
.calendar-empty { margin:25px 0; color:#9aa6a9; font-size:12px; text-align:center; }
.compass-section { margin-top:22px; }
.footprint-insights-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); align-items:stretch; gap:11px; }
.compass-panel { min-width:0; padding:14px 16px 11px; border:1px solid var(--line); border-radius:6px; background:#fff; }
.compass-card { padding:8px 2px 0; }
.category-panel { min-width:0; }
.category-chart { height:238px; }
.compass-layout { display:grid; max-width:820px; grid-template-columns:minmax(0,1fr) 112px minmax(0,1fr); grid-template-rows:auto minmax(82px,auto) auto; grid-template-areas:". north ." "west center east" ". south ."; align-items:center; gap:8px 16px; margin:auto; }
.compass-point { display:flex; min-width:0; flex-direction:column; gap:3px; padding:5px 8px; }
.compass-point strong,.compass-point small { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.compass-point strong { color:#344b50; font-size:13px; font-weight:650; }
.compass-point small { color:#91a0a4; font-size:10px; }
.direction-label { color:#258b88; font-size:10px; font-weight:700; }
.direction-north { grid-area:north; align-items:center; text-align:center; }.direction-east { grid-area:east; }.direction-south { grid-area:south; align-items:center; text-align:center; }.direction-west { grid-area:west; align-items:flex-end; text-align:right; }
.direction-north .direction-label { color:#4f8fc9; }.direction-east .direction-label { color:#4f9b65; }.direction-south .direction-label { color:#df8650; }.direction-west .direction-label { color:#c99a3e; }
.compass-center { display:flex; grid-area:center; flex-direction:column; align-items:center; gap:2px; color:#71818a; }
.compass-center > strong { color:#344b50; font-size:16px; font-variant-numeric:tabular-nums; }
.compass-center > small { font-size:9px; white-space:nowrap; }
.compass-rose { position:relative; display:grid; width:54px; height:54px; place-items:center; border:1px solid #dce7e9; border-radius:50%; background:radial-gradient(circle, #fff 54%, #f5f9f9 55%); }
.compass-rose::before,.compass-rose::after { position:absolute; left:50%; width:1px; height:36px; background:#dce7e9; content:""; transform:translateX(-50%); }
.compass-rose::before { top:8px; }.compass-rose::after { top:50%; width:36px; height:1px; transform:translateY(-50%); }
.compass-rose i { z-index:1; width:0; height:0; border-right:5px solid transparent; border-bottom:16px solid #258b88; border-left:5px solid transparent; }
.transport-heading { margin-top:22px; }
.transport-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); align-items:start; gap:12px; }
.transport-column { min-width:0; overflow:hidden; border:1px solid var(--line); border-radius:6px; background:#fff; }
.transport-title { padding:15px 16px 12px; border-bottom:1px solid #edf1f2; }
.transport-title > div { display:flex; align-items:center; gap:11px; }
.transport-icon { display:grid; width:36px; height:36px; flex:0 0 auto; place-items:center; border-radius:5px; color:#fff; font-size:14px; font-weight:700; }
.rail-icon { background:#d39643; }.air-icon { background:#4f8fc9; }
.transport-title h2 { margin:0; color:#334951; font-size:16px; }
.transport-title p { margin:3px 0 0; color:#91a0a4; font-size:10px; }
.transport-metrics { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); padding:10px 12px; border-bottom:1px solid #edf1f2; }
.transport-metrics > div { display:flex; min-width:0; flex-direction:column; gap:5px; padding:3px 8px; border-right:1px solid #edf1f2; }
.transport-metrics > div:last-child { border:0; }
.transport-metrics span { overflow:hidden; color:#91a0a4; font-size:10px; text-overflow:ellipsis; white-space:nowrap; }
.transport-metrics strong { overflow:hidden; color:#344b50; font-size:15px; font-variant-numeric:tabular-nums; text-overflow:ellipsis; white-space:nowrap; }
.transport-records { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); padding:9px 12px; border-bottom:1px solid #edf1f2; }
.transport-record { display:flex; min-width:0; flex-direction:column; gap:4px; padding:4px 8px; }
.transport-record + .transport-record { border-left:1px solid #edf1f2; }
.transport-record > span { color:#91a0a4; font-size:10px; }
.transport-record > strong { overflow:hidden; color:#344b50; font-size:16px; font-variant-numeric:tabular-nums; text-overflow:ellipsis; white-space:nowrap; }
.rail-column .transport-record > strong { color:#ba8437; }.air-column .transport-record > strong { color:#4380ad; }
.transport-record > small { overflow:hidden; color:#829198; font-size:9px; text-overflow:ellipsis; white-space:nowrap; }
.transport-record > small:last-child { color:#a0abad; }
.transport-charts { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px 8px; padding:12px; }
.transport-chart { min-width:0; }
.transport-chart h3 { display:flex; align-items:baseline; gap:6px; margin:0; color:#52666d; font-size:12px; font-weight:650; }
.transport-chart h3 small { color:#9aa6a9; font-size:9px; font-weight:400; }
.transport-rank { height:205px; }
.cloud-section { grid-column:1 / -1; }
.word-cloud { display:flex; min-height:150px; align-content:center; align-items:center; justify-content:center; flex-wrap:wrap; gap:9px 13px; padding:12px 4px 4px; }
.word-cloud span { max-width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; cursor:default; }
.word-cloud em { color:#a1adaf; font-size:11px; font-style:normal; }
.data-note { margin:12px 1px 0; color:#9aa6a9; font-size:10px; line-height:1.5; }
@media(max-width:1100px) { .preference-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.transport-metrics { grid-template-columns:repeat(2,minmax(0,1fr)); row-gap:8px; }.transport-metrics > div:nth-child(2) { border:0; } }
@media(max-width:760px) {
  .travel-statistics { min-height:calc(100vh - 90px); }
  .page-header { align-items:flex-start; flex-direction:column; gap:11px; }
  .year-filter { width:100%; }
  .section-heading { align-items:flex-start; flex-direction:column; gap:4px; }
  .section-heading > p { text-align:left; }
  .metric-grid { grid-template-columns:repeat(3,minmax(0,1fr)); gap:6px; }
  .metric { min-height:84px; padding:9px 8px; }
  .metric strong { font-size:18px; }
  .metric > span { font-size:10px; }
  .metric small { font-size:9px; }
  .chart-grid,.preference-grid { grid-template-columns:minmax(0,1fr); gap:8px; }
  .panel { padding:13px 12px 9px; }
  .chart { height:220px; }
  .preference-chart { height:205px; }
  .calendar-panel { padding:12px 10px; }
  .calendar-legend { justify-content:flex-start; gap:7px 10px; }
  .calendar-month-grid { grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px 10px; }
  .calendar-day { max-width:13px; }
  .transport-records { padding:8px 5px; }
  .transport-record { padding:4px 5px; }
  .transport-record > strong { font-size:13px; }
  .transport-record > small { font-size:8px; }
  .compass-card { padding:10px 6px; }
  .footprint-insights-grid { grid-template-columns:minmax(0,1fr); gap:8px; }
  .compass-layout { grid-template-columns:minmax(0,1fr) 56px minmax(0,1fr); grid-template-rows:auto minmax(68px,auto) auto; gap:6px 3px; }
  .compass-point { gap:2px; padding:4px 2px; }
  .compass-point strong { font-size:11px; }
  .compass-point small { font-size:8px; }
  .direction-label { font-size:9px; }
  .compass-rose { width:42px; height:42px; }
  .compass-rose::before { top:6px; height:29px; }
  .compass-rose::after { width:29px; }
  .compass-rose i { border-right-width:4px; border-bottom-width:12px; border-left-width:4px; }
  .compass-center > strong { font-size:14px; }
  .compass-center > small { font-size:8px; }
  .transport-grid { grid-template-columns:minmax(0,1fr); gap:10px; }
  .transport-metrics { grid-template-columns:repeat(4,minmax(0,1fr)); padding:9px 5px; }
  .transport-metrics > div { padding:3px 5px; }
  .transport-metrics > div:nth-child(2) { border-right:1px solid #edf1f2; }
  .transport-metrics > div:nth-child(4) { border:0; }
  .transport-metrics strong { font-size:12px; }
  .transport-charts { gap:8px 4px; padding:10px 8px; }
  .transport-rank { height:190px; }
}
</style>
