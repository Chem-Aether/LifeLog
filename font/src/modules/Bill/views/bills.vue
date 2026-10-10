<template>
  <main class="bill-page">
    <header class="page-header">
      <div><h1>账单</h1><p>按账户流向记录收入、支出与内部转账</p></div>
      <div class="header-actions">
        <el-button class="data-action" @click="openImport">导入 CSV</el-button>
        <el-button class="data-action" @click="exportCurrent">导出当前筛选</el-button>
        <el-button class="data-action" @click="exportAll">导出全部</el-button>
        <el-button type="primary" @click="openCreate">新增账单</el-button>
      </div>
    </header>

    <section class="filter-panel">
      <el-select v-model="filters.direction" clearable placeholder="全部方向" class="filter-control">
        <el-option label="收入" value="income" /><el-option label="支出" value="expense" /><el-option label="内部转账" value="transfer" />
      </el-select>
      <el-select v-model="filters.accountId" clearable filterable placeholder="全部账户" class="filter-control">
        <el-option v-for="account in accounts" :key="account.id" :label="account.name" :value="account.id" />
      </el-select>
      <el-cascader v-model="filters.categoryId" :options="categoryOptions" :props="categoryPickerProps" clearable filterable placeholder="全部类别" class="filter-control category-filter">
        <template #default="{ data }"><span class="category-option"><CategoryIcon :value="data.icon" />{{ data.label }}</span></template>
      </el-cascader>
      <el-date-picker v-model="filters.range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" class="date-filter" />
      <el-input v-model="filters.keyword" clearable placeholder="搜索对方、摘要或备注" class="keyword-filter" @keyup.enter="loadRecords" />
      <el-button type="primary" :loading="loading" @click="loadRecords">查询</el-button>
      <el-button :disabled="loading" @click="resetFilters">重置</el-button>
      <span class="record-count">共 <strong>{{ records.length }}</strong> 条记录</span>
    </section>

    <section class="table-panel" v-loading="loading">
      <BillRecordTable :rows="pageRecords" :accounts="accounts" :show-actions="true" @edit="openEdit" @delete="remove" @sort-change="handleSort" />
      <div class="pagination" v-if="records.length">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="records.length" layout="total, sizes, prev, pager, next, jumper" background />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑账单' : '新增账单'" width="min(620px, 94vw)" destroy-on-close :close-on-click-modal="false">
      <el-form label-width="96px" class="edit-form">
        <el-form-item label="资金方向" required>
          <el-radio-group v-model="draft.direction" @change="changeDirection">
            <el-radio-button value="expense">支出</el-radio-button><el-radio-button value="income">收入</el-radio-button><el-radio-button value="transfer">转账</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <div class="account-flow" :class="draft.direction">
          <el-form-item v-if="draft.direction !== 'income'" label="出账账户" required>
            <el-select v-model="draft.fromAccountId" clearable filterable placeholder="选择自己的账户" class="full-width">
              <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="draft.direction !== 'expense'" label="入账账户" required>
            <el-select v-model="draft.toAccountId" clearable filterable placeholder="选择自己的账户" class="full-width">
              <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="交易对象"><el-input v-model="draft.counterparty" placeholder="商户、付款方或收款方，可选" maxlength="200" /></el-form-item>
        <el-form-item label="金额" required><el-input-number v-model="draft.amount" :min="0.01" :precision="2" :step="1" controls-position="right" class="full-width" /></el-form-item>
        <el-form-item label="交易时间" required><el-date-picker v-model="draft.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY/M/D HH:mm" class="full-width" /></el-form-item>
        <el-form-item label="分类"><el-cascader v-model="draft.categoryId" :options="categoryOptions" :props="categoryPickerProps" clearable filterable placeholder="选择分类（可选一级或二级）" class="full-width"><template #default="{ data }"><span class="category-option"><CategoryIcon :value="data.icon" />{{ data.label }}</span></template></el-cascader></el-form-item>
        <el-form-item label="摘要"><el-input v-model="draft.description" placeholder="例如：午餐、工资、账户充值" maxlength="500" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="draft.remark" type="textarea" :rows="3" maxlength="2000" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="导入账单 CSV" width="min(620px, 94vw)" :close-on-click-modal="false" @closed="clearImport">
      <div class="import-content">
        <p>导入采用全量校验：文件中任一行有误时不会写入任何记录。导出的 CSV 可直接重新导入。</p>
        <div class="import-actions">
          <el-button @click="downloadTemplate">下载 CSV 模板</el-button>
          <el-upload accept=".csv,text/csv" :auto-upload="false" :show-file-list="false" :on-change="handleImportFile">
            <el-button type="primary">选择 CSV 文件</el-button>
          </el-upload>
        </div>
        <div v-if="importFileName" class="import-summary">
          <strong>{{ importFileName }}</strong>
          <span v-if="!importErrors.length">校验通过，可导入 {{ importRecords.length }} 条</span>
          <span v-else class="import-error-count">发现 {{ importErrors.length }} 个问题</span>
        </div>
        <ul v-if="importErrors.length" class="import-errors">
          <li v-for="error in importErrors.slice(0, 12)" :key="error">{{ error }}</li>
          <li v-if="importErrors.length > 12">其余 {{ importErrors.length - 12 }} 个问题未显示</li>
        </ul>
      </div>
      <template #footer><el-button @click="importVisible = false">取消</el-button><el-button type="primary" :disabled="!importRecords.length || importErrors.length" :loading="importing" @click="submitImport">确认导入</el-button></template>
    </el-dialog>

  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createBillRecord, deleteBillRecord, exportBillRecords, getAccounts, getBillCategories, getBillRecords, importBillRecords as sendBillImport, updateBillRecord } from '@/modules/Bill/apis/bill.js'
import BillRecordTable from '../components/BillRecordTable.vue'
import CategoryIcon from '../components/CategoryIcon.vue'
import { createBillCsvTemplate, parseBillCsv } from '../utils/billCsv.js'

const records = ref([])
const router = useRouter()
const route = useRoute()
const accounts = ref([])
const categories = ref([])
const categoryOptions = computed(() => categories.value.filter(item => item.parentId == null).map(parent => ({
  value: parent.id,
  label: parent.name,
  icon: parent.icon,
  children: categories.value.filter(item => item.parentId === parent.id).map(item => ({ value: item.id, label: item.name, icon: item.icon })),
})))
const categoryPickerProps = { emitPath: false, checkStrictly: true }
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const importVisible = ref(false)
const importing = ref(false)
const importFileName = ref('')
const importRecords = ref([])
const importErrors = ref([])
const editingId = ref(null)
const page = ref(1)
const pageSize = ref(20)
const sortState = reactive({ prop: '', order: '' })
const filters = reactive({ direction: '', accountId: null, categoryId: null, range: [], keyword: '' })
const draft = reactive(emptyDraft())
const sortedRecords = computed(() => {
  if (!sortState.prop || !sortState.order) return records.value
  const sign = sortState.order === 'ascending' ? 1 : -1
  return [...records.value].sort((a, b) => {
    const left = sortValue(a, sortState.prop)
    const right = sortValue(b, sortState.prop)
    const comparison = typeof left === 'number' && typeof right === 'number'
      ? left - right
      : String(left).localeCompare(String(right), 'zh-CN', { numeric: true })
    return comparison * sign
  })
})
const pageRecords = computed(() => sortedRecords.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))

function emptyDraft() {
  return { direction: 'expense', fromAccountId: null, toAccountId: null, amount: null, occurredAt: localDateTime(), counterparty: '', description: '', categoryId: null, remark: '' }
}
function localDateTime() {
  const d = new Date(); const pad = value => String(value).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}
function money(value) { return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function sortValue(row, prop) {
  if (prop === 'amount') return Number(row.amount || 0)
  if (prop === 'payer') return `${row.fromAccountName || row.counterparty || ''}${row.fromAccountName && row.fromAccountCode ? `（${String(row.fromAccountCode).replace(/\D/g, '').slice(-4)}）` : ''}`
  if (prop === 'payee') return `${row.toAccountName || row.counterparty || ''}${row.toAccountName && row.toAccountCode ? `（${String(row.toAccountCode).replace(/\D/g, '').slice(-4)}）` : ''}`
  return row[prop] || ''
}
function handleSort({ prop, order }) { sortState.prop = prop || ''; sortState.order = order || ''; page.value = 1 }
function queryParams() {
  const params = { direction: filters.direction || undefined, accountId: filters.accountId || undefined, categoryId: filters.categoryId || undefined, keyword: filters.keyword.trim() || undefined }
  if (filters.range?.length === 2) {
    params.startTime = `${filters.range[0]}T00:00:00`
    params.endTime = `${filters.range[1]}T23:59:59`
  }
  return params
}
async function loadRecords() {
  loading.value = true
  page.value = 1
  try {
    records.value = await getBillRecords(queryParams())
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '账单加载失败')
  } finally { loading.value = false }
}
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
function downloadTemplate() {
  downloadBlob(new Blob([createBillCsvTemplate()], { type: 'text/csv;charset=utf-8' }), '账单导入模板.csv')
}
function openImport() {
  importVisible.value = true
}
async function handleImportFile(uploadFile) {
  importFileName.value = uploadFile.name
  importRecords.value = []
  importErrors.value = []
  try {
    const { records: parsed, errors } = parseBillCsv(await uploadFile.raw.text(), accounts.value, categories.value)
    importRecords.value = parsed
    importErrors.value = errors
  } catch (error) { importErrors.value = [error.message || 'CSV 解析失败'] }
}
function clearImport() {
  importFileName.value = ''
  importRecords.value = []
  importErrors.value = []
}
async function submitImport() {
  importing.value = true
  try {
    const count = await sendBillImport(importRecords.value)
    ElMessage.success(`成功导入 ${count} 条账单`)
    importVisible.value = false
    await loadRecords()
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '导入失败，未写入数据') }
  finally { importing.value = false }
}
function changeDirection() {
  draft.fromAccountId = null
  draft.toAccountId = null
}
function openCreate() {
  editingId.value = null
  Object.assign(draft, emptyDraft())
  dialogVisible.value = true
}
function openEdit(row) {
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
  dialogVisible.value = true
}
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
    dialogVisible.value = false
    await loadRecords()
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '账单保存失败') }
  finally { saving.value = false }
}
async function remove(row) {
  try {
    await ElMessageBox.confirm('删除后账户余额统计会自动重算，确定删除这条账单吗？', '删除账单', { type: 'warning' })
    await deleteBillRecord(row.id)
    ElMessage.success('账单已删除')
    await loadRecords()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.response?.data?.msg || '删除失败')
  }
}
watch(pageSize, () => { page.value = 1 })
onMounted(async () => {
  if (route.query.startTime && route.query.endTime) {
    filters.range = [String(route.query.startTime).slice(0, 10), String(route.query.endTime).slice(0, 10)]
  }
  await Promise.all([loadAccounts(), loadCategories(), loadRecords()])
})
</script>

<style scoped>
.bill-page { display: flex; flex-direction: column; gap: 16px; color: #293b49; }
.page-header { display: flex; align-items: center; justify-content: space-between; }
.header-actions { display:flex; align-items:center; gap:8px; flex-wrap:wrap; justify-content:flex-end; }
.header-actions :deep(.data-action) { --el-button-text-color:#258b88; --el-button-bg-color:#f1f8f7; --el-button-border-color:#c7e0de; --el-button-hover-text-color:#1f7775; --el-button-hover-bg-color:#e5f2f1; --el-button-hover-border-color:#8fc4c1; --el-button-active-text-color:#176d6b; --el-button-active-bg-color:#d8ebea; --el-button-active-border-color:#79b6b2; }
.page-header h1 { margin: 0; font-size: 24px; }
.page-header p { margin: 6px 0 0; color: #7c8b95; font-size: 13px; }
.record-count { margin-left:auto; color:#85929a; font-size:12px; white-space:nowrap; }.record-count strong { color:#435863; font-weight:600; font-variant-numeric:tabular-nums; }
.filter-panel,.table-panel { padding: 16px; background: #fff; border: 1px solid #e4e9ee; border-radius: 9px; box-shadow:0 2px 8px rgba(38,55,70,.025); }
.filter-panel { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.filter-control { width: 170px; }.category-filter { width: 190px; }.date-filter { width: 270px; }.keyword-filter { width: 220px; }
.category-option { display:inline-flex; align-items:center; gap:7px; }.category-option > span { font-size:16px; }
.pagination { display: flex; justify-content: flex-end; padding-top: 16px; }
.bill-page :deep(.el-pagination.is-background .el-pager li.is-active) { background-color:#258b88; }
.edit-form { max-width: 540px; }
.account-flow { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; width: 100%; }
.account-flow :deep(.el-form-item) { min-width: 0; }
.full-width { width: 100%; }
.import-content > p { margin:0 0 14px; color:#71818a; line-height:1.6; }.import-actions { display:flex; align-items:center; gap:10px; }.import-summary { display:flex; flex-direction:column; gap:6px; margin-top:16px; padding:12px; background:#f5f8f9; border-radius:6px; color:#60737d; }.import-summary strong { color:#344b57; }.import-error-count { color:#c95e66; }.import-errors { max-height:190px; overflow:auto; margin:12px 0 0; padding:12px 12px 12px 32px; color:#bd515b; background:#fff6f6; border-radius:6px; line-height:1.7; }
@media (max-width: 760px) { .page-header { align-items:flex-start; flex-direction:column; gap:12px; }.header-actions { justify-content:flex-start; }.filter-control,.keyword-filter,.date-filter { width: 100%; }.filter-panel :deep(.el-button) { flex: 1; }.record-count { width:100%; margin:2px 0 0; text-align:right; }.account-flow { grid-template-columns: 1fr; gap: 0; } }
</style>
