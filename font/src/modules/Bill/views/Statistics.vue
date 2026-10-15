<template>
  <main class="statistics-page" v-loading="loading">
    <header class="page-header">
      <div class="heading-copy">
        <h1>收支统计</h1>
        <nav class="period-path" aria-label="统计时间层级">
          <button :class="{ active: viewLevel === 'year' }" @click="goYear">{{ year }} 年</button>
          <template v-if="selectedMonth"><span>›</span><button :class="{ active: viewLevel === 'month' }" @click="goMonth">{{ monthLabel }}</button></template>
          <template v-if="selectedDay"><span>›</span><span class="active">{{ dayLabel }}</span></template>
        </nav>
      </div>
      <div v-if="viewLevel === 'year'" class="year-control">
        <span>统计年度</span>
        <el-select v-model="year" style="width:120px" @change="selectYear">
          <el-option v-for="item in years" :key="item" :label="`${item} 年`" :value="item" />
        </el-select>
      </div>
      <div v-else class="period-control">
        <el-button circle :aria-label="`上一${viewLevel === 'month' ? '月' : '日'}`" @click="shiftPeriod(-1)">‹</el-button>
        <strong>{{ periodHeading }}</strong>
        <el-button circle aria-label="下一时段" :disabled="isFuturePeriod(1)" @click="shiftPeriod(1)">›</el-button>
      </div>
    </header>

    <section class="summary-grid">
      <article class="summary-tile income-tile"><span>{{ periodUnit }}收入</span><strong>¥ {{ money(totals.income) }}</strong><small>自外部流入</small></article>
      <article class="summary-tile expense-tile"><span>{{ periodUnit }}支出</span><strong>¥ {{ money(totals.expense) }}</strong><small>流向外部</small></article>
      <article class="summary-tile net-tile"><span>{{ periodUnit }}净流水</span><strong :class="totals.net >= 0 ? 'positive' : 'negative'">{{ totals.net >= 0 ? '+' : '' }}¥ {{ money(totals.net) }}</strong><small>收入减支出</small></article>
    </section>

    <section v-show="viewLevel === 'year'" class="panel heatmap-panel">
      <div class="panel-heading">
          <div><h2>全年每日净流水</h2><p>点击日期进入所属月份 · 颜色按年度分位归一</p></div>
        <div class="heatmap-legend"><span>支出较多</span><i class="loss"/><i class="neutral"/><i class="profit"/><span>收入较多</span></div>
      </div>
      <div ref="calendarEl" class="calendar-chart" />
    </section>

    <section v-show="viewLevel !== 'day'" class="panel trend-panel">
      <div class="panel-heading">
        <div><h2>{{ viewLevel === 'year' ? '月度收支趋势' : '每日收支趋势' }}</h2><p>{{ viewLevel === 'year' ? '点击月份进入月度统计' : '点击日期查看当天流水' }} · 柱形为收入/支出，折线为净流水</p></div>
      </div>
      <div ref="trendEl" class="trend-chart" />
    </section>

    <section v-show="viewLevel !== 'day'" class="detail-chart-grid">
      <article class="panel account-statistics-panel">
        <div class="panel-heading">
          <div><h2>分账户收支</h2><p>{{ periodHeading }} · 仅统计对外收入与支出，不含账户间转账</p></div>
        </div>
        <div ref="accountEl" class="account-chart" :style="{ height: `${accountChartHeight}px` }" />
      </article>
      <article class="panel category-panel">
        <div class="panel-heading">
          <div><h2>{{ selectedExpenseParent ? `支出分类 · ${selectedExpenseParent.name}` : categoryDirection === 'expense' ? '支出分类' : '收入分类' }}</h2><p>{{ selectedExpenseParent ? '按二级分类汇总' : categoryDirection === 'expense' ? '按一级分类汇总，点击分类查看明细' : '按具体分类汇总' }}</p></div>
          <div class="category-actions">
            <el-button v-if="selectedExpenseParent" text type="primary" @click="backToExpenseCategories">返回一级</el-button>
            <el-radio-group v-model="categoryDirection" size="small">
              <el-radio-button value="expense">支出</el-radio-button>
              <el-radio-button value="income">收入</el-radio-button>
            </el-radio-group>
          </div>
        </div>
        <div ref="categoryEl" class="category-chart" />
      </article>
    </section>

    <section v-if="viewLevel === 'day'" class="panel detail-panel">
      <div class="panel-heading">
        <div><h2>{{ dayLabel }}流水</h2><p>共 {{ detailTotal }} 条收支记录</p></div>
        <el-button v-if="selectedRange" text type="primary" @click="openFilteredBills">查看账单列表</el-button>
      </div>
      <el-table v-if="selectedRange" :data="detailRecords" stripe empty-text="该时段没有账单" class="detail-table">
        <el-table-column prop="occurredAt" label="日期" width="160"><template #default="{ row }">{{ formatDate(row.occurredAt) }}</template></el-table-column>
        <el-table-column prop="direction" label="方向" width="100"><template #default="{ row }"><el-tag :class="`tag-${row.direction}`" effect="plain">{{ directionName(row.direction) }}</el-tag></template></el-table-column>
        <el-table-column prop="category" label="分类" width="140"><template #default="{ row }">{{ row.category || '未分类' }}</template></el-table-column>
        <el-table-column prop="description" label="摘要" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ row.description || row.counterparty || '-' }}</template></el-table-column>
        <el-table-column prop="amount" label="金额" width="140" align="right"><template #default="{ row }"><strong :class="row.direction">{{ row.direction === 'income' ? '+' : row.direction === 'expense' ? '-' : '' }}¥ {{ money(row.amount) }}</strong></template></el-table-column>
      </el-table>
      <div v-if="selectedRange && detailTotal" class="detail-pagination">
        <el-pagination v-model:current-page="detailPage" v-model:page-size="detailPageSize" :page-sizes="[10, 20, 50]" :total="detailTotal" layout="total, sizes, prev, pager, next" background @current-change="loadDetailPage" @size-change="changeDetailPageSize" />
      </div>
      <el-empty v-if="!selectedRange" description="选择日期或月份，查看对应账单" :image-size="68" />
    </section>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { getAccountStatistics, getBillCategories, getBillRecordPage, getBillSummary, getBillTimeline, getCategoryStatistics } from '@/modules/Bill/apis/bill.js'
import { getCssThemeColor, getCssThemePalette } from '@/utils/cssTheme.js'

const router = useRouter()
const currentYear = new Date().getFullYear()
const years = Array.from({ length: 12 }, (_, index) => currentYear - index)
const year = ref(currentYear)
const viewLevel = ref('year')
const selectedMonth = ref('')
const selectedDay = ref('')
const categoryDirection = ref('expense')
const loading = ref(false)
const daily = ref([])
const monthly = ref([])
const periodSummary = ref({ income: 0, expense: 0, transfer: 0 })
const categoryStats = ref([])
const categories = ref([])
const accountStats = ref([])
const selectedExpenseParent = ref(null)
const detailRecords = ref([])
const detailTotal = ref(0)
const detailPage = ref(1)
const detailPageSize = ref(20)
const detailRange = ref(null)
const selectedRange = ref(null)
const calendarEl = ref(null)
const trendEl = ref(null)
const categoryEl = ref(null)
const accountEl = ref(null)
let calendarChart
let trendChart
let categoryChart
let accountChart

const palette = getCssThemePalette('bill', 9)
const dailyMap = computed(() => new Map(daily.value.map(item => [item.period, item])))
const monthLabel = computed(() => selectedMonth.value ? `${Number(selectedMonth.value.slice(5))} 月` : '')
const dayLabel = computed(() => selectedDay.value ? `${Number(selectedMonth.value.slice(5))} 月 ${Number(selectedDay.value.slice(8))} 日` : '')
const periodHeading = computed(() => viewLevel.value === 'year' ? `${year.value} 年` : viewLevel.value === 'month' ? `${year.value} 年 ${monthLabel.value}` : `${year.value} 年 ${dayLabel.value}`)
const periodUnit = computed(() => ({ year: '年度', month: '月度', day: '当日' })[viewLevel.value])
const totals = computed(() => {
  const income = Number(periodSummary.value.income || 0)
  const expense = Number(periodSummary.value.expense || 0)
  return { income, expense, net: income - expense }
})
const activeAccountStats = computed(() => accountStats.value
  .filter(item => Number(item.income || 0) > 0 || Number(item.expense || 0) > 0)
  .sort((left, right) => Number(right.income || 0) + Number(right.expense || 0) - Number(left.income || 0) - Number(left.expense || 0)))
const accountChartHeight = computed(() => Math.max(310, activeAccountStats.value.length * 44 + 76))

function money(value) { return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function directionName(value) { return ({ income: '收入', expense: '支出', transfer: '转账' })[value] || '未知' }
function formatDate(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-' }
function localIsoDate(date) {
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
function dailyRows() {
  const count = new Date(year.value, 1, 0).getDate() + Array.from({ length: 11 }, (_, index) => new Date(year.value, index + 2, 0).getDate()).reduce((sum, value) => sum + value, 0)
  return Array.from({ length: count }, (_, index) => {
    const date = localIsoDate(new Date(year.value, 0, index + 1))
    const item = dailyMap.value.get(date) || {}
    const income = Number(item.income || 0)
    const expense = Number(item.expense || 0)
    return { date, income, expense, net: income - expense }
  })
}
function monthlyRows() {
  const source = new Map(monthly.value.map(item => [item.period, item]))
  return Array.from({ length: 12 }, (_, index) => {
    const period = `${year.value}-${String(index + 1).padStart(2, '0')}`
    const item = source.get(period) || {}
    return { period, income: Number(item.income || 0), expense: Number(item.expense || 0) }
  })
}
function monthDailyRows() {
  if (!selectedMonth.value) return []
  const [targetYear, targetMonth] = selectedMonth.value.split('-').map(Number)
  const count = new Date(targetYear, targetMonth, 0).getDate()
  return Array.from({ length: count }, (_, index) => {
    const date = `${selectedMonth.value}-${String(index + 1).padStart(2, '0')}`
    const item = dailyMap.value.get(date) || {}
    return { period: date, income: Number(item.income || 0), expense: Number(item.expense || 0) }
  })
}
function chartTheme() {
  return { textStyle: { color: '#617581', fontFamily: 'inherit' }, animationDuration: 450, animationDurationUpdate: 300 }
}
function renderCalendar() {
  if (!calendarChart) return
  const rows = dailyRows()
  const magnitudes = rows.map(item => Math.abs(item.net)).filter(value => value > 0).sort((left, right) => left - right)
  const values = rows.map(item => {
    let normalized = 0
    if (item.net !== 0 && magnitudes.length) {
      let low = 0
      let high = magnitudes.length
      while (low < high) {
        const middle = (low + high) >>> 1
        if (magnitudes[middle] <= Math.abs(item.net)) low = middle + 1
        else high = middle
      }
      const percentile = low / magnitudes.length
      normalized = Math.sign(item.net) * (0.25 + 0.75 * percentile)
    }
    return { value: [item.date, normalized], itemStyle: item.date === selectedRange.value?.day ? { borderColor: '#293b49', borderWidth: 2 } : undefined }
  })
  calendarChart.setOption({
    ...chartTheme(),
    tooltip: { formatter: ({ data }) => {
      const date = data.value[0]
      const item = dailyMap.value.get(date) || {}
      const net = Number(item.income || 0) - Number(item.expense || 0)
      return `<strong>${date}</strong><br/>收入：¥ ${money(item.income)}<br/>支出：¥ ${money(item.expense)}<br/>净流水：<b style="color:${net < 0 ? '#d76069' : '#258b88'}">${net < 0 ? '-' : '+'}¥ ${money(Math.abs(net))}</b>`
    } },
    visualMap: { min: -1, max: 1, calculable: false, orient: 'horizontal', left: 'center', bottom: 0, itemWidth: 12, itemHeight: 10, text: ['净流入', '净流出'], textStyle: { color: '#819099', fontSize: 11 }, inRange: { color: [getCssThemeColor('--bill-loss'), getCssThemeColor('--bill-neutral'), getCssThemeColor('--bill-profit')] } },
    calendar: { top: 34, left: 42, right: 18, bottom: 38, range: String(year.value), cellSize: ['auto', 14], itemStyle: { color: '#f1f4f5', borderWidth: 3, borderColor: '#fff', borderRadius: 3 }, yearLabel: { show: false }, monthLabel: { color: '#62747e', nameMap: 'cn', fontSize: 11 }, dayLabel: { firstDay: 1, color: '#829098', nameMap: 'cn', fontSize: 10 }, splitLine: { show: false } },
    series: [{ type: 'heatmap', coordinateSystem: 'calendar', data: values }],
  }, true)
}
function renderTrend() {
  if (!trendChart) return
  const items = viewLevel.value === 'year' ? monthlyRows() : monthDailyRows()
  const labels = items.map(item => viewLevel.value === 'year' ? `${Number(item.period.slice(5))}月` : `${Number(item.period.slice(8))}日`)
  const net = items.map(item => Number(item.income || 0) - Number(item.expense || 0))
  trendChart.setOption({
    ...chartTheme(),
    color: [palette[0], palette[4], getCssThemeColor('--bill-accent')],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: params => {
      const item = items[params[0]?.dataIndex]
      if (!item) return ''
      const period = item.period || item.date
      const balance = Number(item.income || 0) - Number(item.expense || 0)
      return `<strong>${period}</strong><br/>收入：¥ ${money(item.income)}<br/>支出：¥ ${money(item.expense)}<br/>净流水：${balance < 0 ? '-' : '+'}¥ ${money(Math.abs(balance))}`
    } },
    legend: { top: 0, right: 4, textStyle: { color: '#72838c', fontSize: 11 }, data: ['收入', '支出', '净流水'] },
    grid: { top: 42, left: 58, right: 44, bottom: 34 },
    xAxis: { type: 'category', data: labels, boundaryGap: true, axisLine: { lineStyle: { color: '#dce3e6' } }, axisTick: { show: false }, axisLabel: { color: '#87959c', fontSize: 10, interval: items.length > 20 ? 3 : 0, hideOverlap: true } },
    yAxis: [{ type: 'value', axisLabel: { color: '#87959c', fontSize: 10, formatter: value => compactMoney(value) }, splitLine: { lineStyle: { color: '#edf1f2', type: 'dashed' } } }, { type: 'value', show: false }],
    series: [
      { name: '收入', type: 'bar', data: items.map(item => Number(item.income || 0)), barMaxWidth: 18, itemStyle: { color: palette[0], borderRadius: [3, 3, 0, 0] } },
      { name: '支出', type: 'bar', data: items.map(item => Number(item.expense || 0)), barMaxWidth: 18, itemStyle: { color: palette[4], borderRadius: [3, 3, 0, 0] } },
      { name: '净流水', type: 'line', yAxisIndex: 1, data: net, symbol: 'circle', symbolSize: 6, lineStyle: { width: 2, color: getCssThemeColor('--bill-accent') }, itemStyle: { color: getCssThemeColor('--bill-accent') } },
    ],
  }, true)
}
function renderCategories() {
  if (!categoryChart) return
  const data = categoryStats.value.map(item => ({ name: item.category, value: Number(item.total_amount || 0) }))
  categoryChart.setOption({
    ...chartTheme(),
    color: palette,
    tooltip: {
      trigger: 'item',
      confine: true,
      position: (point, params, dom, rect, size) => [
        Math.max(8, Math.min(point[0] + 12, size.viewSize[0] - size.contentSize[0] - 8)),
        Math.max(8, Math.min(point[1] + 12, size.viewSize[1] - size.contentSize[1] - 8)),
      ],
      formatter: params => `${params.marker}${params.name}<br/>金额：¥ ${money(params.value)}<br/>占比：${params.percent}%`,
    },
    legend: { type: 'scroll', orient: 'vertical', right: 0, top: 'middle', width: '38%', textStyle: { color: '#72838c', fontSize: 11 } },
    series: [{ type: 'pie', radius: ['48%', '72%'], center: ['32%', '52%'], avoidLabelOverlap: true, itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 5 }, label: { show: false }, labelLine: { show: false }, emphasis: { scale: true, scaleSize: 8, label: { show: false }, labelLine: { show: false } }, data }],
    graphic: data.length ? [{ type: 'text', left: '22%', top: '45%', style: { text: categoryDirection.value === 'expense' ? '支出' : '收入', fill: '#89969d', fontSize: 12, textAlign: 'center' } }] : [{ type: 'text', left: 'center', top: 'middle', style: { text: '暂无分类数据', fill: '#a4afb4', fontSize: 13, textAlign: 'center' } }],
  }, true)
}
function renderAccountStats() {
  if (!accountChart) return
  const rows = activeAccountStats.value
  const labels = rows.map(item => {
    const number = String(item.code || '').replace(/\D/g, '')
    const suffix = number.slice(-4)
    return suffix ? `${item.name}（${suffix}）` : item.name
  })
  accountChart.setOption({
    ...chartTheme(),
    color: [palette[0], palette[4]],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: params => {
        const index = params[0]?.dataIndex
        const row = rows[index]
        if (!row) return ''
        return `<strong>${labels[index]}</strong><br/>收入：¥ ${money(row.income)}<br/>支出：¥ ${money(row.expense)}`
      },
    },
    legend: { top: 0, right: 8, textStyle: { color: '#72838c', fontSize: 11 } },
    grid: { top: 34, left: 12, right: 34, bottom: 22, containLabel: true },
    xAxis: { type: 'value', min: 0, axisLabel: { color: '#87959c', fontSize: 10, formatter: compactMoney }, splitLine: { lineStyle: { color: '#edf1f2', type: 'dashed' } } },
    yAxis: { type: 'category', data: labels, inverse: true, axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: '#617581', fontSize: 11, width: 180, overflow: 'truncate' } },
    graphic: rows.length ? [] : [{ type: 'text', left: 'center', top: 'middle', style: { text: '该年度暂无账户收支', fill: '#a4afb4', fontSize: 13, textAlign: 'center' } }],
    series: [
      { name: '收入', type: 'bar', data: rows.map(item => Number(item.income || 0)), barMaxWidth: 14, itemStyle: { borderRadius: [0, 4, 4, 0] } },
      { name: '支出', type: 'bar', data: rows.map(item => Number(item.expense || 0)), barMaxWidth: 14, itemStyle: { borderRadius: [0, 4, 4, 0] } },
    ],
  }, true)
}
function backToExpenseCategories() {
  selectedExpenseParent.value = null
  loadCategoryStats()
}
function compactMoney(value) {
  const abs = Math.abs(Number(value || 0))
  if (abs >= 10000) return `${(value / 10000).toFixed(1)}万`
  return Number(value).toLocaleString('zh-CN')
}
function initCharts() {
  calendarChart = echarts.init(calendarEl.value)
  trendChart = echarts.init(trendEl.value)
  categoryChart = echarts.init(categoryEl.value)
  accountChart = echarts.init(accountEl.value)
  calendarChart.on('click', params => {
    const date = Array.isArray(params.data?.value) ? params.data.value[0] : null
    if (date) selectMonth(date.slice(0, 7))
  })
  trendChart.on('click', params => {
    const items = viewLevel.value === 'year' ? monthlyRows() : monthDailyRows()
    const item = items[params.dataIndex]
    if (!item) return
    if (viewLevel.value === 'year') selectMonth(item.period)
    else selectDay(item.period)
  })
  categoryChart.on('click', params => {
    if (categoryDirection.value !== 'expense' || selectedExpenseParent.value) return
    const parent = categories.value.find(item => item.parentId == null && item.name === params.name)
    if (!parent || !categories.value.some(item => String(item.parentId) === String(parent.id))) return
    selectedExpenseParent.value = parent
    loadCategoryStats(parent.id)
  })
  renderCalendar(); renderTrend(); renderCategories(); renderAccountStats()
}
function getPeriodRange() {
  if (viewLevel.value === 'day') return { startTime: `${selectedDay.value}T00:00:00`, endTime: `${selectedDay.value}T23:59:59` }
  if (viewLevel.value === 'month') {
    const [targetYear, targetMonth] = selectedMonth.value.split('-').map(Number)
    const lastDay = new Date(targetYear, targetMonth, 0).getDate()
    return { startTime: `${selectedMonth.value}-01T00:00:00`, endTime: `${selectedMonth.value}-${String(lastDay).padStart(2, '0')}T23:59:59` }
  }
  return { startTime: `${year.value}-01-01T00:00:00`, endTime: `${year.value}-12-31T23:59:59` }
}
async function loadStatistics() {
  loading.value = true
  try {
    const range = getPeriodRange()
    const [timeline, stats, categoryList, accountRows, summary] = await Promise.all([
      getBillTimeline(year.value),
      getCategoryStatistics({ ...range, direction: categoryDirection.value, detailed: categoryDirection.value === 'income' }),
      categories.value.length ? Promise.resolve(categories.value) : getBillCategories(),
      getAccountStatistics(range),
      getBillSummary(range),
    ])
    daily.value = timeline.daily || []
    monthly.value = timeline.monthly || []
    categoryStats.value = stats || []
    categories.value = categoryList || []
    accountStats.value = accountRows || []
    periodSummary.value = summary || { income: 0, expense: 0, transfer: 0 }
    selectedRange.value = viewLevel.value === 'day' ? { day: selectedDay.value } : null
    await nextTick()
    renderCalendar(); renderTrend(); renderCategories(); renderAccountStats(); resizeCharts()
    if (viewLevel.value === 'day') await loadDetails(range.startTime, range.endTime)
    else { detailRecords.value = []; detailTotal.value = 0 }
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '统计数据加载失败') }
  finally { loading.value = false }
}
async function loadCategoryStats(parentCategoryId = null) {
  try {
    categoryStats.value = await getCategoryStatistics({
      ...getPeriodRange(),
      direction: categoryDirection.value,
      detailed: categoryDirection.value === 'income' || parentCategoryId != null,
      parentCategoryId: parentCategoryId ?? undefined,
    })
    renderCategories()
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '分类统计加载失败') }
}
async function selectDay(date) {
  year.value = Number(date.slice(0, 4))
  selectedMonth.value = date.slice(0, 7)
  selectedDay.value = date
  viewLevel.value = 'day'
  selectedExpenseParent.value = null
  selectedRange.value = { day: date }
  await loadStatistics()
}
async function selectMonth(period) {
  year.value = Number(period.slice(0, 4))
  selectedMonth.value = period
  selectedDay.value = ''
  viewLevel.value = 'month'
  selectedExpenseParent.value = null
  selectedRange.value = null
  await loadStatistics()
}
function selectYear(value = year.value) {
  year.value = Number(value)
  goYear()
}
function goYear() {
  viewLevel.value = 'year'
  selectedMonth.value = ''
  selectedDay.value = ''
  selectedExpenseParent.value = null
  selectedRange.value = null
  loadStatistics()
}
function goMonth() {
  if (!selectedMonth.value) return
  viewLevel.value = 'month'
  selectedDay.value = ''
  selectedExpenseParent.value = null
  selectedRange.value = null
  loadStatistics()
}
function shiftPeriod(offset) {
  if (viewLevel.value === 'month') {
    const [targetYear, targetMonth] = selectedMonth.value.split('-').map(Number)
    const shifted = new Date(targetYear, targetMonth - 1 + offset, 1)
    selectMonth(`${shifted.getFullYear()}-${String(shifted.getMonth() + 1).padStart(2, '0')}`)
    return
  }
  const shifted = new Date(`${selectedDay.value}T12:00:00`)
  shifted.setDate(shifted.getDate() + offset)
  selectDay(localIsoDate(shifted))
}
function isFuturePeriod(offset) {
  const today = localIsoDate(new Date())
  if (viewLevel.value === 'month') {
    const [targetYear, targetMonth] = selectedMonth.value.split('-').map(Number)
    const shifted = new Date(targetYear, targetMonth - 1 + offset, 1)
    return `${shifted.getFullYear()}-${String(shifted.getMonth() + 1).padStart(2, '0')}` > today.slice(0, 7)
  }
  const shifted = new Date(`${selectedDay.value}T12:00:00`)
  shifted.setDate(shifted.getDate() + offset)
  return localIsoDate(shifted) > today
}
async function loadDetails(startTime, endTime) {
  try {
    detailPage.value = 1
    detailRange.value = { startTime, endTime }
    await loadDetailPage()
  }
  catch (error) { ElMessage.error(error?.response?.data?.msg || '明细加载失败') }
}
async function loadDetailPage() {
  if (!detailRange.value) return
  try {
    const result = await getBillRecordPage({
      ...detailRange.value, page: detailPage.value, pageSize: detailPageSize.value,
      sortBy: 'occurredAt', sortOrder: 'desc', excludeTransfer: true
    })
    detailRecords.value = result.records || []
    detailTotal.value = Number(result.total || 0)
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '明细加载失败') }
}
function changeDetailPageSize() { detailPage.value = 1; loadDetailPage() }
function openFilteredBills() {
  if (!selectedRange.value) return
  const { day, month } = selectedRange.value
  const start = day ? `${day}T00:00:00` : `${month}-01T00:00:00`
  let end
  if (day) end = `${day}T23:59:59`
  else {
    const [targetYear, targetMonth] = month.split('-').map(Number)
    end = `${month}-${String(new Date(targetYear, targetMonth, 0).getDate()).padStart(2, '0')}T23:59:59`
  }
  router.push({ path: '/bill/query', query: { startTime: start, endTime: end } })
}
function resizeCharts() { calendarChart?.resize(); trendChart?.resize(); categoryChart?.resize(); accountChart?.resize() }
watch(categoryDirection, () => {
  selectedExpenseParent.value = null
  loadCategoryStats()
})
onMounted(async () => {
  await nextTick()
  initCharts()
  window.addEventListener('resize', resizeCharts)
  await loadStatistics()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  calendarChart?.dispose(); trendChart?.dispose(); categoryChart?.dispose(); accountChart?.dispose()
})
</script>

<style scoped>
.statistics-page { display:flex; flex-direction:column; gap:14px; color:#293b49; }
.page-header { display:flex; align-items:center; justify-content:space-between; gap:16px; min-height:52px; }
.page-header h1 { margin:0; font-size:23px; }.heading-copy { display:flex; align-items:center; gap:18px; }.period-path { display:flex; align-items:center; gap:8px; color:#9aa5aa; font-size:13px; }.period-path button { padding:4px 0; color:#74858e; background:none; border:0; cursor:pointer; }.period-path button.active,.period-path .active { color:#258b88; font-weight:600; }.period-path button:hover { color:#176f6d; }
.year-control,.period-control { display:flex; align-items:center; gap:10px; color:#75868e; font-size:13px; }.period-control strong { min-width:132px; color:#258b88; text-align:center; font-size:16px; font-variant-numeric:tabular-nums; }.period-control :deep(.el-button) { width:30px; height:30px; color:#58727c; }
.summary-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; }
.summary-tile,.panel { background:#fff; border:1px solid #e4e9ee; border-radius:9px; box-shadow:0 2px 8px rgba(38,55,70,.025); }
.summary-tile { display:flex; min-width:0; flex-direction:column; gap:7px; padding:16px 18px; border-top:3px solid #258b88; }
.summary-tile > span { color:#74858e; font-size:12px; }.summary-tile strong { overflow:hidden; color:#344957; font-size:22px; font-variant-numeric:tabular-nums; text-overflow:ellipsis; white-space:nowrap; }.summary-tile small { color:#98a3a8; font-size:11px; }
.income-tile { border-top-color:#528bd4; }.expense-tile { border-top-color:#e27670; }.net-tile { border-top-color:#258b88; }
.positive,.income { color:#258b88!important; }.negative,.expense { color:#d76069!important; }.transfer { color:#7183bf!important; }
.panel { padding:16px 18px; min-width:0; }.panel-heading { display:flex; align-items:center; justify-content:space-between; gap:12px; }.panel-heading h2 { margin:0; font-size:16px; }.panel-heading p { margin:5px 0 0; color:#8a979d; font-size:11px; }
.heatmap-legend { display:flex; align-items:center; gap:5px; color:#87959c; font-size:10px; }.heatmap-legend i { width:12px; height:12px; border-radius:3px; }.heatmap-legend .loss { background:#d96870; }.heatmap-legend .neutral { background:#f2f4f6; border:1px solid #e4e9ee; }.heatmap-legend .profit { background:#54a58d; }.detail-pagination { display:flex; justify-content:flex-end; margin-top:14px; overflow-x:auto; }
.calendar-chart { height:190px; margin-top:8px; }.trend-chart,.category-chart { width:100%; height:310px; margin-top:8px; }.account-chart { width:100%; min-height:310px; margin-top:8px; }.detail-chart-grid { display:grid; grid-template-columns:minmax(0,1fr) minmax(0,1fr); gap:16px; align-items:stretch; }.category-actions { display:flex; align-items:center; gap:10px; flex-shrink:0; }.detail-panel { min-height:190px; }.detail-table { margin-top:12px; }.tag-income { color:#288d76; border-color:#b9ded2; background:#eff8f4; }.tag-expense { color:#c95e66; border-color:#efc6c8; background:#fff4f4; }.tag-transfer { color:#537fbd; border-color:#c5d5eb; background:#f2f6fc; }
.statistics-page :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { background:#258b88; border-color:#258b88; box-shadow:-1px 0 0 0 #258b88; }
@media(max-width:1000px) { .summary-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.detail-chart-grid { grid-template-columns:1fr; } }
@media(max-width:620px) { .page-header { align-items:flex-start; flex-direction:column; }.heading-copy { align-items:flex-start; flex-direction:column; gap:6px; }.period-control { align-self:flex-end; }.summary-grid { gap:8px; }.summary-tile { padding:13px; }.summary-tile strong { font-size:18px; }.panel { padding:14px 12px; }.calendar-chart { height:165px; }.trend-chart,.category-chart { height:280px; }.panel-heading { align-items:flex-start; }.category-actions { gap:4px; }.category-actions :deep(.el-button) { padding:6px; }.heatmap-legend { align-self:flex-end; }.panel-heading :deep(.el-radio-group) { flex-shrink:0; } }
</style>
