<template>
  <main class="bill-page">
    <template v-if="isEditing">
      <header class="edit-page-header"><div><el-button text @click="requestClose">返回账单</el-button><h1>{{ editingId ? '编辑账单' : '新增账单' }}</h1></div><div><el-button :disabled="saving" @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></div></header>
      <section class="edit-page-panel"><el-form label-width="96px" class="edit-form">
        <el-form-item label="资金方向" required><el-radio-group v-model="draft.direction" @change="changeDirection"><el-radio-button value="expense">支出</el-radio-button><el-radio-button value="income">收入</el-radio-button><el-radio-button value="transfer">转账</el-radio-button></el-radio-group></el-form-item>
        <div class="account-flow" :class="draft.direction"><el-form-item v-if="draft.direction !== 'income'" label="出账账户" required><el-select v-model="draft.fromAccountId" clearable filterable placeholder="选择自己的账户" class="full-width"><el-option v-for="a in accounts" :key="a.id" :label="accountFilterLabel(a)" :value="a.id" /></el-select></el-form-item><el-form-item v-if="draft.direction !== 'expense'" label="入账账户" required><el-select v-model="draft.toAccountId" clearable filterable placeholder="选择自己的账户" class="full-width"><el-option v-for="a in accounts" :key="a.id" :label="accountFilterLabel(a)" :value="a.id" /></el-select></el-form-item></div>
        <el-form-item label="交易对象"><el-input v-model="draft.counterparty" placeholder="商户、付款方或收款方，可选" maxlength="200" /></el-form-item><el-form-item label="金额" required><el-input-number v-model="draft.amount" :min="0.01" :precision="2" :step="1" controls-position="right" class="full-width" /></el-form-item><el-form-item label="交易时间" required><el-date-picker v-model="draft.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY/M/D HH:mm" class="full-width" /></el-form-item><el-form-item label="分类"><CategoryTreePicker v-model="draft.categoryId" :categories="categories" placeholder="选择一级或二级分类" /></el-form-item><el-form-item label="摘要"><el-input v-model="draft.description" placeholder="例如：午餐、工资、账户充值" maxlength="500" /></el-form-item><el-form-item label="备注"><el-input v-model="draft.remark" type="textarea" :rows="3" maxlength="2000" /></el-form-item>
      </el-form></section>
    </template>
    <template v-else>
    <header class="page-header">
      <div><h1>账单</h1><p>按账户流向记录收入、支出与内部转账</p></div>
      <div class="header-actions">
        <el-button class="data-action" @click="openImport">导入账单</el-button>
        <el-button class="data-action" @click="exportCurrent">导出当前筛选</el-button>
        <el-button class="data-action" @click="exportAll">导出全部</el-button>
        <el-button type="primary" @click="openCreate">新增账单</el-button>
      </div>
    </header>

    <section class="filter-panel">
      <el-select v-model="filters.direction" clearable placeholder="全部方向" class="filter-control">
        <el-option label="收入" value="income" /><el-option label="支出" value="expense" /><el-option label="内部转账" value="transfer" />
      </el-select>
      <el-select v-model="filters.accountId" clearable filterable placeholder="全部账户" class="filter-control account-filter">
        <el-option v-for="account in accounts" :key="account.id" :label="accountFilterLabel(account)" :value="account.id" />
      </el-select>
      <CategoryTreePicker v-model="filters.categoryId" :categories="categories" placeholder="全部类别" popover-width="320" class="filter-control category-filter" />
      <el-date-picker v-model="filters.range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" class="date-filter" />
      <el-input v-model="filters.keyword" clearable placeholder="搜索对方、摘要或备注" class="keyword-filter" @keyup.enter="loadRecords" />
      <el-button type="primary" :loading="loading" @click="loadRecords">查询</el-button>
      <el-button :disabled="loading" @click="resetFilters">重置</el-button>
      <span class="record-count">共 <strong>{{ total }}</strong> 条记录</span>
    </section>

    <section class="table-panel" v-loading="loading || deleting">
      <div class="bulk-toolbar"><span>已选择 {{ selectedRows.length }} 条（当前页）</span><el-button type="danger" plain :disabled="!selectedRows.length || deleting" :loading="deleting" @click="deleteSelected">批量删除</el-button></div>
      <BillRecordTable ref="tableRef" :rows="records" :accounts="accounts" :show-actions="true" :show-selection="true" :default-sort="tableSort" @edit="openEdit" @delete="remove" @sort-change="handleSort" @selection-change="selectedRows = $event" />
      <div class="pagination" v-if="total">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper" background @current-change="loadPage" @size-change="changePageSize" />
      </div>
    </section>

    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createBillRecord, deleteBillRecord, deleteBillRecords, exportBillRecords, getAccounts, getBillCategories, getBillRecord, getBillRecordPage, updateBillRecord } from '@/modules/Bill/apis/bill.js'
import BillRecordTable from '../components/BillRecordTable.vue'
import CategoryTreePicker from '../components/CategoryTreePicker.vue'

const records = ref([])
const router = useRouter()
const route = useRoute()
const isEditing = computed(() => route.name === 'BillRecordEdit')
const LIST_STATE_KEY = 'billRecordListState'
const restoredList = route.path === '/bill/query' ? takeListState() : null
const accounts = ref([])
const categories = ref([])
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const editingId = ref(null)
const tableRef = ref(null)
const selectedRows = ref([])
const page = ref(restoredList?.page || 1)
const pageSize = ref(restoredList?.pageSize || 20)
const total = ref(0)
const sortState = reactive({ prop: restoredList?.sort?.prop || '', order: restoredList?.sort?.order || '' })
const tableSort = computed(() => sortState.prop && sortState.order ? { prop: sortState.prop, order: sortState.order } : {})
const filters = reactive({ direction: '', accountId: null, categoryId: null, range: [], keyword: '', ...restoredList?.filters })
if (route.query.startTime && route.query.endTime) {
  filters.range = [String(route.query.startTime).slice(0, 10), String(route.query.endTime).slice(0, 10)]
}
const draft = reactive(emptyDraft())
const editSnapshot = ref('')
let skipLeaveGuard = false

function takeListState() {
  const saved = sessionStorage.getItem(LIST_STATE_KEY)
  sessionStorage.removeItem(LIST_STATE_KEY)
  if (!saved || (route.query.startTime && route.query.endTime)) return null
  try { return JSON.parse(saved) } catch { return null }
}

function rememberListState() {
  sessionStorage.setItem(LIST_STATE_KEY, JSON.stringify({
    filters: { ...filters, range: [...(filters.range || [])] },
    page: page.value, pageSize: pageSize.value, sort: { ...sortState },
  }))
}

function emptyDraft() {
  return { direction: 'expense', fromAccountId: null, toAccountId: null, amount: null, occurredAt: localDateTime(), counterparty: '', description: '', categoryId: null, remark: '' }
}
function localDateTime() {
  const d = new Date(); const pad = value => String(value).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}
function accountFilterLabel(account) {
  const suffix = String(account.code || '').replace(/[\s-]/g, '').slice(-4)
  return suffix ? `${account.name}（${suffix}）` : account.name
}
function handleSort({ prop, order }) {
  if (sortState.prop === (prop || '') && sortState.order === (order || '')) return
  sortState.prop = prop || ''
  sortState.order = order || ''
  page.value = 1
  loadPage()
}
function queryParams() {
  const params = { direction: filters.direction || undefined, accountId: filters.accountId || undefined, categoryId: filters.categoryId || undefined, keyword: filters.keyword.trim() || undefined }
  if (filters.range?.length === 2) {
    params.startTime = `${filters.range[0]}T00:00:00`
    params.endTime = `${filters.range[1]}T23:59:59`
  }
  return params
}
async function loadRecords() {
  page.value = 1
  await loadPage()
}
async function loadPage() {
  loading.value = true
  try {
    const result = await getBillRecordPage({
      ...queryParams(), page: page.value, pageSize: pageSize.value,
      sortBy: sortState.prop || 'occurredAt', sortOrder: sortState.order === 'ascending' ? 'asc' : 'desc'
    })
    tableRef.value?.clearSelection()
    selectedRows.value = []
    records.value = result.records || []
    total.value = Number(result.total || 0)
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '账单加载失败')
  } finally { loading.value = false }
}
function changePageSize() { page.value = 1; loadPage() }
async function loadAccounts() {
  try { accounts.value = await getAccounts() } catch { ElMessage.error('账户加载失败') }
}
async function loadCategories() {
  try { categories.value = await getBillCategories() } catch { ElMessage.error('分类加载失败') }
}
function resetFilters() {
  Object.assign(filters, { direction: '', accountId: null, categoryId: null, range: [], keyword: '' })
  loadRecords()
}
function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
async function exportCurrent() {
  try {
    const response = await exportBillRecords(queryParams())
    downloadBlob(response.data, '账单-当前筛选.csv')
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '账单导出失败') }
}
async function exportAll() {
  try {
    const response = await exportBillRecords()
    downloadBlob(response.data, '账单-全部.csv')
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '账单导出失败') }
}
function openImport() {
  router.push({ name: 'BillRecordImport' })
}
function changeDirection() {
  draft.fromAccountId = null
  draft.toAccountId = null
}
function openCreate() {
  router.push({ name: 'BillRecordEdit' })
}
function openEdit(row) {
  if (deleting.value) return
  router.push({ name: 'BillRecordEdit', params: { id: row.id } })
}
function populateDraft(row) {
  editingId.value = row.id
  Object.assign(draft, {
    direction: row.direction,
    fromAccountId: row.fromAccountId,
    toAccountId: row.toAccountId,
    amount: Number(row.amount),
    occurredAt: String(row.occurredAt).replace(' ', 'T').slice(0, 19),
    counterparty: row.counterparty || '',
    description: row.description || '',
    categoryId: row.categoryId || null,
    remark: row.remark || '',
  })
}
function hasUnsavedChanges() { return isEditing.value && JSON.stringify(draft) !== editSnapshot.value }
async function requestClose() {
  if (saving.value) return
  if (hasUnsavedChanges()) {
    try { await ElMessageBox.confirm('当前内容尚未保存，确定放弃修改并离开吗？', '未保存的修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }) }
    catch { return }
  }
  skipLeaveGuard = true
  await router.push('/bill/query')
}
onBeforeRouteLeave(async to => {
  if (!isEditing.value) {
    if (to.name === 'BillRecordEdit') rememberListState()
    return true
  }
  if (skipLeaveGuard) skipLeaveGuard = false
  else if (hasUnsavedChanges() && !saving.value) {
    try { await ElMessageBox.confirm('当前内容尚未保存，确定放弃修改并离开吗？', '未保存的修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }) }
    catch { return false }
  }
  if (to.path !== '/bill/query') sessionStorage.removeItem(LIST_STATE_KEY)
  return true
})
async function save() {
  const needsFrom = draft.direction !== 'income'
  const needsTo = draft.direction !== 'expense'
  if ((needsFrom && !draft.fromAccountId) || (needsTo && !draft.toAccountId)) return ElMessage.warning('请选择资金流向中的自有账户')
  if (!draft.amount || !draft.occurredAt) return ElMessage.warning('请填写金额和交易时间')
  if (draft.direction === 'transfer' && draft.fromAccountId === draft.toAccountId) return ElMessage.warning('转出与转入账户不能相同')
  const payload = {
    occurredAt: draft.occurredAt,
    amount: draft.amount,
    fromAccountId: needsFrom ? draft.fromAccountId : null,
    toAccountId: needsTo ? draft.toAccountId : null,
    counterparty: draft.counterparty,
    description: draft.description,
    categoryId: draft.categoryId || null,
    remark: draft.remark,
  }
  saving.value = true
  try {
    if (editingId.value) await updateBillRecord(editingId.value, payload)
    else await createBillRecord(payload)
    ElMessage.success('账单已保存')
    skipLeaveGuard = true
    await router.push('/bill/query')
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '账单保存失败') }
  finally { saving.value = false }
}
async function remove(row) {
  if (deleting.value) return
  try {
    await ElMessageBox.confirm('删除后账户余额统计会自动重算，确定删除这条账单吗？', '删除账单', { type: 'warning' })
    await deleteBillRecord(row.id)
    ElMessage.success('账单已删除')
    if (records.value.length === 1 && page.value > 1) page.value -= 1
    await loadPage()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.response?.data?.msg || '删除失败')
  }
}
async function deleteSelected() {
  if (deleting.value || !selectedRows.value.length) return
  const selected = [...selectedRows.value]
  try {
    await ElMessageBox.confirm(`确定删除当前页选中的 ${selected.length} 条账单吗？此操作不可撤销。`, '批量删除', { type: 'warning' })
  } catch { return }
  deleting.value = true
  try {
    const deleted = Number(await deleteBillRecords(selected.map(row => row.id))) || 0
    await loadPage()
    if (!records.value.length && page.value > 1) {
      page.value = Math.max(1, Math.ceil(total.value / pageSize.value))
      if (total.value) await loadPage()
    }
    if (deleted === selected.length) ElMessage.success(`已删除 ${deleted} 条账单`)
    else ElMessage.warning(`已删除 ${deleted} 条，另有 ${selected.length - deleted} 条已不存在`)
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '批量删除失败') }
  finally { deleting.value = false }
}
onMounted(async () => {
  await Promise.all([loadAccounts(), loadCategories(), isEditing.value ? Promise.resolve() : loadPage()])
  if (isEditing.value) {
    let row = null
    if (route.params.id) {
      try { row = await getBillRecord(route.params.id) }
      catch { ElMessage.error('账单不存在'); router.replace('/bill/query'); return }
    }
    if (row) populateDraft(row)
    else { editingId.value = null; Object.assign(draft, emptyDraft()) }
    editSnapshot.value = JSON.stringify(draft)
  }
})
</script>

<style scoped>
.edit-page-header { display:flex; align-items:center; justify-content:space-between; gap:16px; padding-bottom:14px; border-bottom:1px solid #e4e9ee; }
.edit-page-header h1 { margin:6px 0 0; color:#293b49; font-size:22px; }
.edit-page-panel { max-width:760px; padding:20px; background:#fff; border:1px solid #e4e9ee; border-radius:7px; }
.bill-page { display: flex; flex-direction: column; gap: 16px; color: #293b49; }
.page-header { display: flex; align-items: center; justify-content: space-between; }
.header-actions { display:flex; align-items:center; gap:8px; flex-wrap:wrap; justify-content:flex-end; }
.header-actions :deep(.data-action) { --el-button-text-color:#258b88; --el-button-bg-color:#f1f8f7; --el-button-border-color:#c7e0de; --el-button-hover-text-color:#1f7775; --el-button-hover-bg-color:#e5f2f1; --el-button-hover-border-color:#8fc4c1; --el-button-active-text-color:#176d6b; --el-button-active-bg-color:#d8ebea; --el-button-active-border-color:#79b6b2; }
.page-header h1 { margin: 0; font-size: 24px; }
.page-header p { margin: 6px 0 0; color: #7c8b95; font-size: 13px; }
.record-count { margin-left:auto; color:#85929a; font-size:12px; white-space:nowrap; }.record-count strong { color:#435863; font-weight:600; font-variant-numeric:tabular-nums; }
.filter-panel,.table-panel { padding: 16px; background: #fff; border: 1px solid #e4e9ee; border-radius: 9px; box-shadow:0 2px 8px rgba(38,55,70,.025); }
.filter-panel { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.bulk-toolbar { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:12px; color:#697d85; font-size:12px; }
.filter-control { width: 170px; }.account-filter { width: 205px; }.category-filter { width: 190px; }.date-filter { width: 270px; }.keyword-filter { width: 220px; }
.category-option { display:inline-flex; align-items:center; gap:7px; }.category-option > span { font-size:16px; }
.pagination { display: flex; justify-content: flex-end; padding-top: 16px; }
.bill-page :deep(.el-pagination.is-background .el-pager li.is-active) { background-color:#258b88; }
.edit-form { max-width: 540px; }
.account-flow { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; width: 100%; }
.account-flow :deep(.el-form-item) { min-width: 0; }
.full-width { width: 100%; }
@media (max-width: 760px) { .page-header { align-items:flex-start; flex-direction:column; gap:12px; }.header-actions { justify-content:flex-start; }.filter-control,.keyword-filter,.date-filter { width: 100%; }.filter-panel :deep(.el-button) { flex: 1; }.record-count { width:100%; margin:2px 0 0; text-align:right; }.account-flow { grid-template-columns: 1fr; gap: 0; } }
</style>
