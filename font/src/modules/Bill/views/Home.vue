<template>
  <main class="overview">
    <header class="page-header">
      <div><h1>账单首页</h1><p>{{ monthLabel }} · 收支与账户概览</p></div>
      <div class="header-actions">
        <div class="month-navigation" aria-label="切换统计月份">
          <el-button text circle aria-label="上个月" @click="shiftMonth(-1)"><el-icon><ArrowLeft /></el-icon></el-button>
          <strong>{{ monthLabel }}</strong>
          <el-button text circle aria-label="下个月" @click="shiftMonth(1)"><el-icon><ArrowRight /></el-icon></el-button>
        </div>
        <el-button type="primary" @click="router.push('/bill/query')"><el-icon><Plus /></el-icon>记一笔</el-button>
      </div>
    </header>

    <section class="top-grid">
      <article class="panel monthly-panel">
        <div class="monthly-copy">
          <span class="section-kicker">本月净收支</span>
          <strong :class="['monthly-total', totalFlow < 0 ? 'is-negative' : 'is-positive']">¥ {{ money(totalFlow) }}</strong>
          <div class="monthly-meta">
            <span><i class="dot income-dot" />收入 <b>¥ {{ money(summary.income) }}</b></span>
            <span><i class="dot expense-dot" />支出 <b>¥ {{ money(summary.expense) }}</b></span>
            <span><i class="dot transfer-dot" />转账 <b>¥ {{ money(summary.transfer) }}</b></span>
          </div>
          <el-button text class="details-link" @click="router.push('/bill/query')">查看账单 <el-icon><ArrowRight /></el-icon></el-button>
        </div>
        <div ref="flowChartEl" class="flow-chart" aria-label="本月收入支出转账比例图" />
      </article>

      <article class="panel assets-panel">
        <div class="panel-heading"><h2>资产概要</h2><span class="account-count">{{ accounts.length }} 个账户</span></div>
        <div class="asset-total"><span>账户总余额</span><strong>¥ {{ money(totalBalance) }}</strong></div>
        <div v-if="accounts.length" class="account-list">
          <div v-for="(account, index) in accounts.slice(0, 4)" :key="account.id" class="account-row">
            <span class="account-icon" :class="`account-tone-${index % 4}`"><el-icon><Wallet /></el-icon></span>
            <span class="account-name">{{ account.name }}<small v-if="account.code">{{ maskAccountNumber(account.code) }}</small></span>
            <strong>¥ {{ money(account.balance) }}</strong>
          </div>
        </div>
        <el-empty v-else description="添加账户后显示资产" :image-size="54" />
        <router-link to="/bill/account" class="panel-link">管理账户 <el-icon><ArrowRight /></el-icon></router-link>
      </article>
    </section>

    <section class="lower-grid">
      <div class="left-stack">
        <div class="category-grid">
          <article class="panel category-panel">
            <div class="panel-heading"><h2>支出分类</h2><span class="category-total">¥ {{ money(summary.expense) }}</span></div>
            <div class="category-content">
              <div ref="expenseChartEl" class="category-chart" aria-label="本月支出分类图" />
              <div class="category-legend">
                <div v-for="(item, index) in expenseLegend" :key="item.name"><span :style="{ background: palette[index % palette.length] }" />{{ item.name }}<b>{{ money(item.value) }}</b></div>
                <el-empty v-if="!expenseLegend.length" description="暂无分类支出" :image-size="40" />
              </div>
            </div>
          </article>
          <article class="panel category-panel">
            <div class="panel-heading"><h2>收入分类</h2><span class="category-total">¥ {{ money(summary.income) }}</span></div>
            <div class="category-content">
              <div ref="incomeChartEl" class="category-chart" aria-label="本月收入分类图" />
              <div class="category-legend">
                <div v-for="(item, index) in incomeLegend" :key="item.name"><span :style="{ background: palette[(index + 2) % palette.length] }" />{{ item.name }}<b>{{ money(item.value) }}</b></div>
                <el-empty v-if="!incomeLegend.length" description="暂无分类收入" :image-size="40" />
              </div>
            </div>
          </article>
        </div>

        <article class="panel recent-panel">
          <div class="panel-heading"><h2>最近账单</h2><router-link to="/bill/query" class="panel-link">全部账单 <el-icon><ArrowRight /></el-icon></router-link></div>
          <el-table :data="recent" stripe size="small" empty-text="暂无账单" class="recent-table">
            <el-table-column prop="occurredAt" label="日期" width="130"><template #default="{ row }">{{ formatDate(row.occurredAt) }}</template></el-table-column>
            <el-table-column prop="category" label="分类" width="105"><template #default="{ row }"><span class="category-pill">{{ row.category || '未分类' }}</span></template></el-table-column>
            <el-table-column prop="description" label="摘要" min-width="140" show-overflow-tooltip />
            <el-table-column prop="amount" label="金额" width="115" align="right"><template #default="{ row }"><strong :class="row.direction">{{ row.direction === 'income' ? '+' : row.direction === 'expense' ? '-' : '' }}{{ money(row.amount) }}</strong></template></el-table-column>
          </el-table>
        </article>
      </div>

      <article class="panel daily-panel">
        <div class="panel-heading"><h2>每日收支</h2><span class="daily-caption">{{ monthLabel }}</span></div>
        <div ref="dailyChartEl" class="daily-chart" aria-label="本月每日收入与支出柱状图" />
      </article>
    </section>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Plus, Wallet } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getAccounts, getBillRecords, getBillSummary, getCategoryStatistics } from '@/modules/Bill/apis/bill.js'
import { maskAccountNumber } from '../utils/accountNumber.js'

const router = useRouter()
const palette = ['#5488d8', '#59aa8b', '#e6b44f', '#8a79c7', '#e47a72', '#54aeb5', '#7e9b62']
const summary = ref({ income: 0, expense: 0, transfer: 0 })
const accounts = ref([])
const expenseCategories = ref([])
const incomeCategories = ref([])
const recent = ref([])
const allRecords = ref([])
const flowChartEl = ref(null)
const expenseChartEl = ref(null)
const incomeChartEl = ref(null)
const dailyChartEl = ref(null)
let charts = []
const now = new Date()
const selectedMonth = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
const monthParts = computed(() => selectedMonth.value.split('-').map(Number))
const monthLabel = computed(() => {
  const [selectedYear, selectedMonthNumber] = monthParts.value
  return `${selectedYear}年${selectedMonthNumber}月`
})
const monthStart = computed(() => `${selectedMonth.value}-01T00:00:00`)
const totalBalance = computed(() => accounts.value.reduce((total, account) => total + Number(account.balance || 0), 0))
const totalFlow = computed(() => Number(summary.value.income || 0) - Number(summary.value.expense || 0))
const maxExpense = computed(() => Math.max(1, ...expenseCategories.value.map(item => Number(item.total_amount))))
const expenseLegend = computed(() => legendItems(expenseCategories.value))
const incomeLegend = computed(() => legendItems(incomeCategories.value))
const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const formatDate = value => String(value || '').replace('T', ' ').slice(0, 16)

function shiftMonth(offset) {
  const [year, month] = monthParts.value
  const date = new Date(year, month - 1 + offset, 1)
  selectedMonth.value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function signedLog(value) { return Math.sign(value) * Math.log10(1 + Math.abs(value)) }
function signedExp(value) { return Math.sign(value) * (10 ** Math.abs(value) - 1) }
function compactAmount(value) {
  const amount = Math.round(Math.abs(value))
  return `${value < 0 ? '-' : ''}${amount >= 10000 ? `${(amount / 10000).toFixed(0)}万` : amount}`
}

function legendItems(rows) {
  const top = rows.slice(0, 4).map(row => ({ name: row.category, value: Number(row.total_amount) }))
  const rest = rows.slice(4).reduce((total, row) => total + Number(row.total_amount), 0)
  if (rest > 0) top.push({ name: '其他', value: rest })
  return top
}

function donutOption(data, colors, centerText) {
  return {
    color: colors,
    tooltip: {
      trigger: 'item',
      confine: true,
      position: (point, params, dom, rect, size) => [
        Math.max(8, Math.min(point[0] + 12, size.viewSize[0] - size.contentSize[0] - 8)),
        Math.max(8, Math.min(point[1] + 12, size.viewSize[1] - size.contentSize[1] - 8)),
      ],
      formatter: '{b}: ¥{c} ({d}%)',
    },
    graphic: [{ type: 'text', left: 'center', top: 'middle', style: { text: centerText, textAlign: 'center', fill: '#61717b', fontSize: 11, lineHeight: 18 } }],
    series: [{ type: 'pie', radius: ['54%', '76%'], center: ['50%', '50%'], avoidLabelOverlap: true, itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 4 }, label: { show: false }, labelLine: { show: false }, emphasis: { scaleSize: 5, label: { show: false }, labelLine: { show: false } }, data }],
  }
}

function paintCharts() {
  charts.forEach(chart => chart.dispose())
  charts = []
  const create = element => {
    if (!element) return null
    const chart = echarts.init(element)
    charts.push(chart)
    return chart
  }
  const flow = create(flowChartEl.value)
  flow?.setOption(donutOption([
    { name: '收入', value: Number(summary.value.income) },
    { name: '支出', value: Number(summary.value.expense) },
    { name: '内部转账', value: Number(summary.value.transfer) },
  ], ['#4c83d4', '#d96973', '#a8c844'], '本月\n总收支'))

  const expenseChart = create(expenseChartEl.value)
  expenseChart?.setOption(donutOption(expenseLegend.value, palette, '支出\n分类'))
  const incomeChart = create(incomeChartEl.value)
  incomeChart?.setOption(donutOption(incomeLegend.value, palette.slice(2).concat(palette.slice(0, 2)), '收入\n分类'))

  const [year, monthNumber] = monthParts.value
  const dayCount = new Date(year, monthNumber, 0).getDate()
  const days = Array.from({ length: dayCount }, (_, index) => String(index + 1))
  const incomes = Array(dayCount).fill(0)
  const expenses = Array(dayCount).fill(0)
  for (const record of allRecords.value) {
    const date = String(record.occurredAt || '').slice(0, 10)
    if (!date.startsWith(selectedMonth.value)) continue
    const day = Number(date.slice(8, 10)) - 1
    if (day < 0 || day >= dayCount) continue
    if (record.direction === 'income') incomes[day] += Number(record.amount)
    if (record.direction === 'expense') expenses[day] -= Number(record.amount)
  }
  const daily = create(dailyChartEl.value)
  daily?.setOption({
    color: ['#4c83d4', '#d96973'],
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      formatter: params => {
        const rows = params.filter(item => item.data?.raw !== 0)
        if (!rows.length) return `${params[0]?.axisValue ?? ''}日<br/>无收支`
        return `${params[0].axisValue}日<br/>${rows.map(item => `${item.marker}${item.seriesName}：¥ ${money(item.data.raw)}`).join('<br/>')}`
      },
    },
    legend: { bottom: 2, itemWidth: 12, itemHeight: 8, textStyle: { color: '#667680', fontSize: 12 } },
    grid: { top: 20, left: 12, right: 18, bottom: 42, containLabel: true },
    xAxis: { type: 'category', data: days, axisTick: { show: false }, axisLine: { lineStyle: { color: '#dfe6e9' } }, axisLabel: { color: '#7d8991', fontSize: 10, interval: dayCount > 27 ? 2 : dayCount > 18 ? 1 : 0, hideOverlap: true } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf1f2', type: 'dashed' } }, axisLabel: { color: '#8a969c', fontSize: 10, formatter: value => value === 0 ? '0' : compactAmount(signedExp(value)) } },
    series: [
      { name: '收入', type: 'bar', data: incomes.map(value => ({ value: signedLog(value), raw: value })), barMaxWidth: 13, itemStyle: { borderRadius: [4, 4, 0, 0] } },
      { name: '支出', type: 'bar', data: expenses.map(value => ({ value: signedLog(value), raw: value })), barMaxWidth: 13, itemStyle: { borderRadius: [0, 0, 4, 4] } },
    ],
  })
  charts.forEach(chart => chart.resize())
}

async function loadMonth() {
  try {
    const [year, monthNumber] = monthParts.value
    const end = `${selectedMonth.value}-${String(new Date(year, monthNumber, 0).getDate()).padStart(2, '0')}T23:59:59`
    const range = { startTime: monthStart.value, endTime: end }
    const [summaryData, expenseData, incomeData] = await Promise.all([
      getBillSummary(range),
      getCategoryStatistics({ ...range, direction: 'expense' }),
      getCategoryStatistics({ ...range, direction: 'income', detailed: true }),
    ])
    summary.value = summaryData
    expenseCategories.value = expenseData
    incomeCategories.value = incomeData
    await nextTick()
    paintCharts()
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '总览加载失败') }
}
async function load() {
  try {
    const [accountsData, recordsData] = await Promise.all([getAccounts(), getBillRecords()])
    accounts.value = accountsData
    allRecords.value = recordsData
    recent.value = recordsData.slice(0, 4)
    const latestMonth = recordsData.reduce((latest, record) => {
      const month = String(record.occurredAt || '').slice(0, 7)
      return /^\d{4}-\d{2}$/.test(month) && month > latest ? month : latest
    }, '')
    if (/^\d{4}-\d{2}$/.test(latestMonth) && latestMonth !== selectedMonth.value) selectedMonth.value = latestMonth
    else await loadMonth()
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '总览加载失败') }
}
function resizeCharts() { charts.forEach(chart => chart.resize()) }
watch(selectedMonth, loadMonth)
onMounted(async () => { await load(); window.addEventListener('resize', resizeCharts) })
onBeforeUnmount(() => { window.removeEventListener('resize', resizeCharts); charts.forEach(chart => chart.dispose()); charts = [] })
</script>

<style scoped>
.overview { --ink:#263746; --muted:#778692; --line:#e5eaee; display:flex; flex-direction:column; gap:12px; max-width:1600px; margin:0 auto; color:var(--ink); }
.page-header { display:flex; align-items:center; justify-content:space-between; min-height:48px; }
.page-header h1 { margin:0; font-size:22px; font-weight:700; }
.page-header p { margin:4px 0 0; color:var(--muted); font-size:12px; }
.header-actions { display:flex; align-items:center; gap:16px; }
.month-navigation { display:flex; align-items:center; gap:6px; }
.month-navigation strong { min-width:126px; color:#218782; font-size:22px; text-align:center; font-variant-numeric:tabular-nums; }
.month-navigation :deep(.el-button) { width:32px; height:32px; color:#58727c; }
.top-grid { display:grid; grid-template-columns:minmax(0,1.08fr) minmax(360px,.92fr); gap:12px; }
.panel { min-width:0; padding:14px 16px; background:#fff; border:1px solid var(--line); border-radius:9px; box-shadow:0 2px 8px rgba(38,55,70,.025); }
.monthly-panel { min-height:180px; display:grid; grid-template-columns:minmax(0,1fr) 190px; align-items:center; padding-left:20px; }
.monthly-copy { display:flex; flex-direction:column; align-items:flex-start; }
.section-kicker { display:block; margin-bottom:3px; color:#8a989f; font-size:11px; }
.monthly-total { margin-top:2px; font-size:32px; line-height:1.15; font-variant-numeric:tabular-nums; }
.monthly-total.is-positive { color:#258b88; }
.monthly-total.is-negative { color:#d76069; }
.monthly-meta { display:flex; flex-wrap:wrap; gap:18px; margin-top:10px; color:#788790; font-size:12px; }
.monthly-meta span { display:flex; align-items:center; gap:6px; }
.monthly-meta b { color:#364957; font-weight:600; }
.dot { width:8px; height:8px; border-radius:50%; }
.income-dot { background:#4c83d4; }
.expense-dot { background:#d96973; }
.transfer-dot { background:#a8c844; }
.details-link { margin:6px 0 0 -8px; color:#397aaf; }
.flow-chart { width:180px; height:155px; }
.assets-panel { min-height:180px; }
.panel-heading { display:flex; justify-content:space-between; align-items:center; }
.panel-heading h2 { margin:0; font-size:15px; }
.account-count,.daily-caption { color:var(--muted); font-size:12px; }
.asset-total { display:flex; align-items:baseline; justify-content:space-between; margin:8px 0; padding-bottom:7px; border-bottom:1px solid #edf0f2; }
.asset-total span { color:var(--muted); font-size:12px; }
.asset-total strong { color:#334958; font-size:17px; font-variant-numeric:tabular-nums; }
.account-list { display:grid; grid-template-columns:1fr 1fr; gap:6px; }
.account-row { display:flex; min-width:0; align-items:center; gap:7px; padding:6px; border-radius:7px; background:#f5f7fb; }
.account-icon { display:grid; flex:none; place-items:center; width:26px; height:26px; border-radius:7px; }
.account-tone-0 { color:#527fd0; background:#e8eefb; }
.account-tone-1 { color:#258c81; background:#e5f3ef; }
.account-tone-2 { color:#c28b2c; background:#fbf2df; }
.account-tone-3 { color:#8772bc; background:#f0ecf8; }
.account-name { display:flex; min-width:0; flex:1; flex-direction:column; gap:2px; overflow:hidden; color:#344957; font-size:11px; text-overflow:ellipsis; white-space:nowrap; }
.account-name small { color:#9aa5aa; font-size:9px; }
.account-row strong { flex:none; color:#334957; font-size:10px; font-variant-numeric:tabular-nums; }
.panel-link { display:inline-flex; align-items:center; gap:3px; color:#41819c; font-size:12px; text-decoration:none; }
.assets-panel > .panel-link { margin-top:6px; }
.lower-grid { display:grid; grid-template-columns:minmax(0,1.08fr) minmax(360px,.92fr); align-items:stretch; gap:12px; }
.left-stack { display:flex; min-width:0; min-height:0; flex-direction:column; gap:12px; }
.category-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
.category-panel { min-height:188px; }
.category-total { color:#677b87; font-size:12px; font-variant-numeric:tabular-nums; }
.category-content { display:grid; grid-template-columns:minmax(100px,.9fr) minmax(100px,1.1fr); align-items:center; gap:6px; margin-top:5px; }
.category-chart { width:100%; height:128px; }
.category-legend { display:flex; flex-direction:column; gap:6px; }
.category-legend > div:not(.el-empty) { display:grid; grid-template-columns:8px minmax(0,1fr) auto; align-items:center; gap:6px; color:#657681; font-size:10px; }
.category-legend span { width:8px; height:8px; border-radius:50%; }
.category-legend b { color:#334957; font-size:10px; font-weight:600; font-variant-numeric:tabular-nums; }
.category-legend :deep(.el-empty) { padding:0; }
.category-legend :deep(.el-empty__description p) { font-size:11px; }
.recent-panel { flex:1; min-height:0; padding-bottom:8px; }
.recent-table { margin-top:6px; }
.category-pill { display:inline-block; max-width:90px; overflow:hidden; color:#687b86; font-size:11px; text-overflow:ellipsis; white-space:nowrap; }
.recent-table :deep(.el-table__inner-wrapper::before) { display:none; }
.recent-table :deep(.el-table th.el-table__cell) { background:#f6f8f9; color:#73828c; font-size:11px; font-weight:600; }
.recent-table :deep(.el-table td.el-table__cell) { color:#3c4f5c; }
.recent-table :deep(.el-table th.el-table__cell),.recent-table :deep(.el-table td.el-table__cell) { padding:6px 0; }
.recent-table :deep(.el-table .cell) { padding:0 8px; }
.daily-panel { display:flex; min-height:0; flex-direction:column; }
.daily-chart { flex:1; min-height:0; margin-top:4px; }
.income { color:#288d76; }
.expense { color:#d76069; }
.transfer { color:#537fbd; }
@media(max-height:820px) and (min-width:1101px) {
  .overview { gap:8px; }
  .page-header { min-height:42px; }
  .page-header h1 { font-size:20px; }
  .page-header p { margin-top:2px; font-size:11px; }
  .top-grid,.lower-grid,.left-stack,.category-grid { gap:8px; }
  .monthly-panel,.assets-panel { min-height:158px; }
  .monthly-panel { grid-template-columns:minmax(0,1fr) 160px; padding-left:16px; }
  .monthly-total { font-size:28px; }
  .monthly-meta { margin-top:6px; gap:12px; font-size:11px; }
  .details-link { margin-top:2px; }
  .flow-chart { width:150px; height:130px; }
  .asset-total { margin:5px 0; padding-bottom:5px; }
  .account-list { gap:4px; }
  .account-row { padding:4px 5px; }
  .account-icon { width:22px; height:22px; }
  .assets-panel > .panel-link { margin-top:3px; }
  .category-panel { min-height:158px; }
  .category-content { margin-top:2px; }
  .category-chart { height:105px; }
  .category-legend { gap:4px; }
  .recent-panel { padding-top:10px; }
  .recent-table { margin-top:3px; }
  .recent-table :deep(.el-table th.el-table__cell),.recent-table :deep(.el-table td.el-table__cell) { padding:3px 0; }
}
@media(max-width:1100px) {
  .top-grid,.lower-grid { grid-template-columns:1fr; }
  .daily-panel { min-height:340px; }
  .daily-chart { min-height:280px; }
  .assets-panel { min-height:auto; }
}
@media(max-width:680px) {
  .overview { gap:12px; }
  .page-header { align-items:flex-start; gap:10px; }
  .page-header h1 { font-size:21px; }
  .header-actions { flex-direction:column; align-items:flex-end; gap:5px; }
  .month-navigation { gap:2px; }
  .month-navigation strong { min-width:112px; font-size:19px; }
  .panel { padding:13px; }
  .top-grid { gap:12px; }
  .monthly-panel { grid-template-columns:minmax(0,1fr) 135px; min-height:170px; padding-left:14px; }
  .monthly-total { font-size:25px; }
  .monthly-meta { flex-direction:column; gap:5px; margin-top:7px; }
  .flow-chart { width:135px; height:130px; }
  .account-list { grid-template-columns:1fr 1fr; }
  .category-grid { grid-template-columns:1fr; gap:12px; }
  .category-panel { min-height:190px; }
  .category-content { grid-template-columns:1fr 1fr; }
  .category-chart { height:135px; }
  .daily-panel { min-height:320px; }
  .daily-chart { min-height:260px; }
  .recent-panel { overflow:hidden; }
  .recent-table { width:100%; }
}
</style>
