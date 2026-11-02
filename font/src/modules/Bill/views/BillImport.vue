<template>
  <main class="bill-import-page">
    <header class="import-header">
      <div class="title-group"><el-button text class="back-button" :disabled="reading || importing" @click="requestBack"><el-icon><ArrowLeft /></el-icon>返回账单</el-button><h1>导入账单</h1></div>
    </header>

    <section v-if="fileName" class="file-strip">
      <div class="file-info"><el-icon><Document /></el-icon><div><strong>{{ fileName }}</strong><span>{{ sourceRows.length }} 行账单</span></div></div>
      <div class="file-actions">
        <el-select v-if="sheets.length > 1" v-model="activeSheet" class="sheet-select" aria-label="选择工作表" :disabled="reading || importing" @change="applySheet"><el-option v-for="sheet in sheets" :key="sheet.name" :label="sheet.name" :value="sheet.name" /></el-select>
        <el-upload accept=".csv,.xlsx" :auto-upload="false" :show-file-list="false" :disabled="reading || importing || referenceLoading || referenceError" :on-change="selectFile"><el-button :loading="reading" :disabled="importing || referenceLoading || referenceError">更换文件</el-button></el-upload>
        <el-button text type="danger" :disabled="reading || importing" @click="clearFile">移除</el-button>
      </div>
    </section>
    <section v-else class="empty-upload"><el-icon><Upload /></el-icon><strong>导入账单文件</strong><span>CSV 或 Excel (.xlsx)</span><el-button v-if="referenceError" @click="loadReferenceData">重新加载账户</el-button><el-upload v-else accept=".csv,.xlsx" :auto-upload="false" :show-file-list="false" :disabled="reading || referenceLoading" :on-change="selectFile"><el-button type="primary" :loading="reading || referenceLoading">选择文件</el-button></el-upload></section>

    <template v-if="sourceRows.length">
      <section class="review-strip">
        <div class="review-counts"><strong>{{ validCount }} 条可导入</strong><span v-if="invalidCount" class="invalid-stat">{{ invalidCount }} 条待处理</span><span v-if="excludedCount">{{ excludedCount }} 条已排除</span></div>
        <el-button text @click="mappingOpen = !mappingOpen">{{ mappingOpen ? '收起列对应' : '调整列对应' }}</el-button>
      </section>

      <section v-if="mappingOpen" class="mapping-section">
        <div class="section-heading"><div><h2>列对应</h2><p v-if="missingRequiredColumns">请选择交易时间和金额对应的列。</p></div><el-button text @click="resetMapping">恢复自动识别</el-button></div>
        <div class="mapping-grid">
          <label v-for="field in mappingFields" :key="field.key" class="mapping-field"><span>{{ field.label }}<small v-if="field.required">必填</small></span><el-select v-model="mapping[field.key]" clearable placeholder="不导入此列" @change="updateMapping(field.key)"><el-option v-for="(header, index) in headers" :key="index" :label="columnLabel(header, index)" :value="index" /></el-select></label>
        </div>
      </section>

      <section class="preview-section">
        <div class="section-heading preview-heading"><h2>账单预览</h2><el-checkbox v-model="problemsOnly">只看问题行</el-checkbox></div>
        <div class="preview-table-wrap">
          <el-table :data="visibleRows" row-key="line" :row-class-name="rowClassName" height="min(62vh, 680px)" :empty-text="problemsOnly ? '没有需要处理的记录' : '没有可预览的记录'">
            <el-table-column label="导入" width="62" fixed="left"><template #default="{ row }"><el-checkbox v-model="row.include" /></template></el-table-column>
            <el-table-column label="日期时间" min-width="166"><template #default="{ row }"><el-input v-model="row.occurredAt" placeholder="YYYY-MM-DD HH:mm" /></template></el-table-column>
            <el-table-column label="方向" width="105"><template #default="{ row }"><el-select v-model="row.direction"><el-option label="支出" value="expense" /><el-option label="收入" value="income" /><el-option label="转账" value="transfer" /></el-select></template></el-table-column>
            <el-table-column label="金额" min-width="185"><template #default="{ row }"><el-input-number v-model="row.amount" :min="0.01" :precision="2" :step="1" :controls="false" /></template></el-table-column>
            <el-table-column label="出账账户" min-width="170"><template #default="{ row }"><el-select v-model="row.fromAccountId" clearable filterable placeholder="选择账户" @change="syncDirection(row)"><el-option v-for="account in accounts" :key="account.id" :label="accountLabel(account)" :value="Number(account.id)" /></el-select></template></el-table-column>
            <el-table-column label="入账账户" min-width="170"><template #default="{ row }"><el-select v-model="row.toAccountId" clearable filterable placeholder="选择账户" @change="syncDirection(row)"><el-option v-for="account in accounts" :key="account.id" :label="accountLabel(account)" :value="Number(account.id)" /></el-select></template></el-table-column>
            <el-table-column label="交易对象" min-width="150"><template #default="{ row }"><el-input v-model="row.counterparty" placeholder="商户或对方" /></template></el-table-column>
            <el-table-column label="商品 / 摘要" min-width="160"><template #default="{ row }"><el-input v-model="row.description" placeholder="可选" /></template></el-table-column>
            <el-table-column label="分类" min-width="150"><template #default="{ row }"><el-select v-model="row.categoryId" clearable filterable placeholder="不分类" @change="row.invalidCategory = false"><el-option v-for="category in categories" :key="category.id" :label="category.name" :value="Number(category.id)" /></el-select></template></el-table-column>
            <el-table-column label="校验" min-width="210" fixed="right"><template #default="{ row }"><span v-if="rowErrors(row).length" class="row-error">{{ rowErrors(row).join('；') }}</span><span v-else class="row-valid">可导入</span></template></el-table-column>
          </el-table>
        </div>
        <el-pagination v-if="filteredRows.length > pageSize" v-model:current-page="page" :page-size="pageSize" :total="filteredRows.length" layout="total, prev, pager, next" background />
      </section>

      <footer class="import-footer"><span>{{ includedCount }} 条已选择 <em v-if="invalidCount">· {{ invalidCount }} 条需处理</em><em v-if="validCount > MAX_IMPORT_RECORDS"> · 单次最多 {{ MAX_IMPORT_RECORDS }} 条</em></span><div><el-button v-if="invalidCount" :disabled="importing" @click="excludeInvalidRows">排除问题行</el-button><el-button type="primary" :disabled="!validCount || validCount > MAX_IMPORT_RECORDS || importing || reading" :loading="importing" @click="submitImport">导入 {{ validCount }} 条</el-button></div></footer>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Document, Upload } from '@element-plus/icons-vue'
import { getAccounts, getBillCategories, importBillRecords } from '@/modules/Bill/apis/bill.js'
import { decodeBillCsv, inferBillDirection, normalizeBillDate, parseBillAmount, parseBillCsv, resolveBillAccount, resolveBillCategory, suggestBillCsvMapping } from '../utils/billCsv.js'

const router = useRouter()
const MAX_IMPORT_RECORDS = 10000
const accounts = ref([])
const categories = ref([])
const mappingFields = [
  { key: 'occurredAt', label: '交易日期', required: true }, { key: 'amount', label: '交易金额', required: true },
  { key: 'direction', label: '收支方向' }, { key: 'fromAccount', label: '出账账户' }, { key: 'toAccount', label: '入账账户' },
  { key: 'counterparty', label: '交易对象' }, { key: 'description', label: '商品 / 摘要' }, { key: 'category', label: '分类' }, { key: 'remark', label: '备注' },
]
const fileName = ref('')
const sheets = ref([])
const activeSheet = ref('')
const headers = ref([])
const sourceRows = ref([])
const previewRows = ref([])
const mapping = reactive(emptyMapping())
const importing = ref(false)
const reading = ref(false)
const referenceLoading = ref(true)
const referenceError = ref(false)
const mappingOpen = ref(false)
const problemsOnly = ref(false)
const page = ref(1)
const pageSize = 100
const leaveAllowed = ref(false)
const categoryMappingTouched = ref(false)
const filteredRows = computed(() => problemsOnly.value ? previewRows.value.filter(row => row.include && rowErrors(row).length) : previewRows.value)
const visibleRows = computed(() => filteredRows.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const includedCount = computed(() => previewRows.value.filter(row => row.include).length)
const invalidCount = computed(() => previewRows.value.filter(row => row.include && rowErrors(row).length).length)
const validCount = computed(() => previewRows.value.filter(row => row.include && !rowErrors(row).length).length)
const excludedCount = computed(() => previewRows.value.length - includedCount.value)
const missingRequiredColumns = computed(() => mapping.occurredAt < 0 || mapping.amount < 0)

function emptyMapping() { return Object.fromEntries(mappingFields.map(field => [field.key, -1])) }
function columnLabel(header, index) {
  const same = headers.value.slice(0, index + 1).filter(value => value === header).length
  const total = headers.value.filter(value => value === header).length
  return `${header || `未命名列 ${index + 1}`}${total > 1 ? `（${same}）` : ''}`
}
function accountLabel(account) { return `${account.name}${account.code ? ` · ${String(account.code).slice(-4)}` : ''}` }
function cell(cells, key) {
  const index = Number(mapping[key])
  return index >= 0 ? String(cells[index] || '').trim() : ''
}
function buildPreview() {
  page.value = 1
  previewRows.value = sourceRows.value.map(({ cells, line }) => {
    const signedAmount = parseBillAmount(cell(cells, 'amount'))
    const directionText = cell(cells, 'direction').toLowerCase()
    const fromValue = cell(cells, 'fromAccount')
    const toValue = cell(cells, 'toAccount')
    const explicitDirection = directionText.includes('转') || directionText === 'transfer' ? 'transfer'
      : directionText.includes('收入') || directionText.includes('入账') || directionText === 'income' ? 'income'
        : directionText.includes('支出') || directionText.includes('消费') || directionText === 'expense' ? 'expense'
          : ''
    const accountDirection = fromValue && toValue ? 'transfer' : fromValue ? 'expense' : toValue ? 'income' : ''
    const fallbackDirection = explicitDirection || accountDirection || (signedAmount == null ? '' : signedAmount < 0 ? 'expense' : 'income')
    const fromMatch = resolveBillAccount(fromValue, accounts.value)
    const toMatch = resolveBillAccount(toValue, accounts.value)
    const categoryMatch = resolveBillCategory(cell(cells, 'category'), categories.value)
    let fromAccountId = fromMatch.id
    let toAccountId = toMatch.id
    if (fallbackDirection === 'income' && !toAccountId && fromAccountId) { toAccountId = fromAccountId; fromAccountId = null }
    const direction = inferBillDirection(fromAccountId, toAccountId, fallbackDirection)
    return {
      line, include: true, occurredAt: normalizeBillDate(cell(cells, 'occurredAt')),
      direction, amount: signedAmount == null ? null : Math.abs(signedAmount), fromAccountId, toAccountId,
      counterparty: cell(cells, 'counterparty'), description: cell(cells, 'description'),
      categoryId: categoryMatch.id, invalidCategory: Boolean(cell(cells, 'category') && !categoryMatch.id),
      remark: cell(cells, 'remark'),
    }
  })
}
function syncDirection(row) { row.direction = inferBillDirection(row.fromAccountId, row.toAccountId, row.direction) }
function updateMapping(key) {
  if (key === 'category') categoryMappingTouched.value = true
  buildPreview()
}
function autoMapCategory() {
  if (categoryMappingTouched.value || mapping.category >= 0 || !sourceRows.value.length || !categories.value.length) return
  const names = new Set(categories.value.map(category => normalizeCell(category.name)).filter(Boolean))
  const score = index => sourceRows.value.reduce((count, row) => count + (names.has(normalizeCell(row.cells[index])) ? 1 : 0), 0)
  let bestIndex = -1
  let bestScore = 0
  headers.value.forEach((_, index) => {
    const value = score(index)
    if (value > bestScore) { bestIndex = index; bestScore = value }
  })
  if (bestIndex >= 0) mapping.category = bestIndex
}
function normalizeCell(value) { return String(value || '').trim().toLocaleLowerCase() }
function resetMapping() { categoryMappingTouched.value = false; Object.assign(mapping, suggestBillCsvMapping(headers.value)); autoMapCategory(); buildPreview() }
function rowErrors(row) {
  const errors = []
  if (!row.occurredAt || !normalizeBillDate(row.occurredAt)) errors.push('日期无效')
  if (!Number.isFinite(Number(row.amount)) || Number(row.amount) <= 0) errors.push('金额需大于 0')
  if (!row.direction) errors.push('方向无效')
  if (row.direction !== 'income' && !row.fromAccountId) errors.push('请选择出账账户')
  if (row.direction !== 'expense' && !row.toAccountId) errors.push('请选择入账账户')
  if (row.fromAccountId && row.fromAccountId === row.toAccountId) errors.push('出入账户不能相同')
  if (row.invalidCategory && !row.categoryId) errors.push('分类未匹配，请重新选择或清空')
  return errors
}
function rowClassName({ row }) { return row.include && rowErrors(row).length ? 'import-row-invalid' : '' }
function excludeInvalidRows() { previewRows.value.forEach(row => { if (rowErrors(row).length) row.include = false }) }
function recordsToImport() {
  return previewRows.value.filter(row => row.include && !rowErrors(row).length).map(row => ({
    occurredAt: normalizeBillDate(row.occurredAt), amount: Number(row.amount),
    fromAccountId: row.direction === 'income' ? null : row.fromAccountId,
    toAccountId: row.direction === 'expense' ? null : row.toAccountId,
    counterparty: row.counterparty, description: row.description, categoryId: row.categoryId || null, remark: row.remark,
  }))
}
async function selectFile(uploadFile) {
  if (!uploadFile.raw || reading.value || importing.value || referenceLoading.value || referenceError.value) return
  reading.value = true
  try {
    if (uploadFile.raw.size > 20 * 1024 * 1024) throw new Error('文件不能超过 20 MB')
    const extension = uploadFile.name.split('.').pop()?.toLowerCase()
    let parsedSheets
    if (extension === 'csv') {
      const parsed = parseBillCsv(decodeBillCsv(await uploadFile.raw.arrayBuffer()))
      parsedSheets = [{ name: 'CSV', headers: parsed.headers, rows: parsed.rows.map((cells, index) => ({ cells, line: parsed.headerIndex + index + 2 })) }]
    } else if (extension === 'xlsx') {
      const { parseBillExcel } = await import('../utils/billExcel.js')
      parsedSheets = await parseBillExcel(uploadFile.raw)
    } else throw new Error('请选择 CSV 或 Excel (.xlsx) 文件')
    if (!parsedSheets.length || !parsedSheets.some(sheet => sheet.rows.length)) throw new Error('文件中没有账单数据行')
    fileName.value = uploadFile.name
    sheets.value = parsedSheets
    activeSheet.value = parsedSheets.find(sheet => {
      const suggested = suggestBillCsvMapping(sheet.headers)
      return suggested.occurredAt >= 0 && suggested.amount >= 0
    })?.name || parsedSheets[0].name
    applySheet()
  } catch (error) { ElMessage.error(error.message || '文件解析失败') }
  finally { reading.value = false }
}
function applySheet() {
  const sheet = sheets.value.find(item => item.name === activeSheet.value)
  if (!sheet) return
  headers.value = sheet.headers
  sourceRows.value = sheet.rows
  categoryMappingTouched.value = false
  problemsOnly.value = false
  Object.assign(mapping, suggestBillCsvMapping(sheet.headers))
  autoMapCategory()
  buildPreview()
  mappingOpen.value = missingRequiredColumns.value
}
function resetFile() { fileName.value = ''; sheets.value = []; activeSheet.value = ''; headers.value = []; sourceRows.value = []; previewRows.value = []; categoryMappingTouched.value = false; mappingOpen.value = false; problemsOnly.value = false; Object.assign(mapping, emptyMapping()) }
async function clearFile() {
  try { await ElMessageBox.confirm('移除文件会清除当前校对结果，确定继续吗？', '移除导入文件', { type: 'warning', confirmButtonText: '移除文件', cancelButtonText: '保留文件' }); resetFile() }
  catch { /* keep the current import */ }
}
async function submitImport() {
  const records = recordsToImport()
  if (!records.length || records.length > MAX_IMPORT_RECORDS || reading.value) return
  if (invalidCount.value) {
    try {
      await ElMessageBox.confirm(`还有 ${invalidCount.value} 条问题行，跳过后导入其余 ${records.length} 条账单？`, '确认导入', {
        type: 'warning', confirmButtonText: '跳过并导入', cancelButtonText: '继续校对',
      })
    } catch { return }
  }
  importing.value = true
  try {
    const count = await importBillRecords(records)
    leaveAllowed.value = true
    ElMessage.success(`成功导入 ${count} 条账单`)
    await router.push('/bill/query')
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '导入失败，未写入数据') }
  finally { importing.value = false }
}
async function requestBack() {
  if (reading.value || importing.value) return
  if (sourceRows.value.length) {
    try { await ElMessageBox.confirm('离开后当前文件和校对结果将被清除，确定返回吗？', '放弃本次导入', { type: 'warning', confirmButtonText: '放弃并返回', cancelButtonText: '继续导入' }) }
    catch { return }
  }
  leaveAllowed.value = true
  await router.push('/bill/query')
}
onBeforeRouteLeave(async () => {
  if (leaveAllowed.value) return true
  if (reading.value || importing.value) return false
  if (!sourceRows.value.length) return true
  try { await ElMessageBox.confirm('离开后当前文件和校对结果将被清除，确定离开吗？', '放弃本次导入', { type: 'warning', confirmButtonText: '放弃并离开', cancelButtonText: '继续导入' }); return true }
  catch { return false }
})
watch(problemsOnly, () => { page.value = 1 })
watch(filteredRows, () => { page.value = Math.min(page.value, Math.max(1, Math.ceil(filteredRows.value.length / pageSize))) })
async function loadReferenceData() {
  referenceLoading.value = true
  referenceError.value = false
  try {
    [accounts.value, categories.value] = await Promise.all([getAccounts(), getBillCategories()])
    if (sourceRows.value.length) { autoMapCategory(); buildPreview() }
  } catch { referenceError.value = true; ElMessage.error('账户或分类加载失败，请重试') }
  finally { referenceLoading.value = false }
}
onMounted(loadReferenceData)
</script>

<style scoped>
.bill-import-page { display:grid; gap:16px; padding-bottom:74px; color:#293b49; }
.import-header { padding-bottom:14px; border-bottom:1px solid #e3e9ec; }
.title-group h1 { margin:6px 0; font-size:24px; }
.back-button { padding:0; color:#667780; }
.back-button .el-icon { margin-right:5px; }
.file-strip,.review-strip,.mapping-section,.preview-section,.empty-upload { border:1px solid #e3e9ec; border-radius:7px; background:#fff; }
.file-strip,.review-strip { display:flex; align-items:center; justify-content:space-between; gap:16px; padding:14px 16px; }
.file-info,.file-actions,.review-counts { display:flex; align-items:center; gap:12px; min-width:0; }
.file-info>.el-icon { flex:none; color:var(--el-color-primary); font-size:22px; }
.file-info strong,.file-info span { display:block; }
.file-info strong { overflow:hidden; color:#344957; font-size:13px; text-overflow:ellipsis; white-space:nowrap; }
.file-info span { margin-top:4px; color:#89979d; font-size:11px; }
.file-info>div { min-width:0; }
.file-actions { flex:none; gap:8px; }
.sheet-select { width:160px; }
.empty-upload { display:grid; min-height:230px; align-content:center; justify-items:center; gap:12px; padding:24px; text-align:center; }
.empty-upload>.el-icon { color:var(--el-color-primary); font-size:30px; }
.empty-upload strong { font-size:16px; }
.empty-upload>span { color:#7c8b95; font-size:13px; }
.review-counts { color:#697d85; font-size:12px; }
.review-counts strong { color:#293b49; font-size:14px; }
.invalid-stat,.row-error { color:#c95e66; }
.mapping-section,.preview-section { padding:16px; }
.section-heading { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-bottom:14px; }
.section-heading h2 { margin:0; font-size:16px; }
.section-heading p { margin:5px 0 0; color:#c95e66; font-size:12px; }
.mapping-grid { display:grid; grid-template-columns:repeat(3,minmax(180px,1fr)); gap:12px; }
.mapping-field { display:grid; min-width:0; gap:6px; }
.mapping-field>span { display:flex; align-items:center; gap:6px; color:#53676f; font-size:12px; }
.mapping-field small { color:#c95e66; font-size:10px; }
.preview-section { min-width:0; }
.preview-table-wrap { overflow-x:auto; }
.preview-table-wrap :deep(.el-input),.preview-table-wrap :deep(.el-select),.preview-table-wrap :deep(.el-input-number) { width:100%; }
.row-error,.row-valid { font-size:11px; line-height:1.45; }
.row-valid { color:#288d76; }
.preview-section :deep(.el-pagination) { justify-content:flex-end; margin-top:14px; }
.preview-section :deep(.el-table .import-row-invalid td) { background:#fff8f7; }
.import-footer { position:sticky; z-index:5; bottom:0; display:flex; align-items:center; justify-content:space-between; gap:16px; margin:0 -8px; padding:12px 8px; border-top:1px solid #e3e9ec; background:rgba(255,255,255,.96); backdrop-filter:blur(10px); }
.import-footer>span { color:#53676f; font-size:12px; }
.import-footer em { color:#c95e66; font-style:normal; }
.import-footer>div { display:flex; gap:8px; }
@media(max-width:900px) { .mapping-grid { grid-template-columns:repeat(2,minmax(150px,1fr)); } }
@media(max-width:760px) {
  .title-group h1 { font-size:21px; }
  .file-strip { align-items:flex-start; flex-wrap:wrap; }
  .file-actions { width:100%; justify-content:flex-end; }
  .review-strip { flex-wrap:wrap; }
  .mapping-grid { grid-template-columns:1fr; }
  .import-footer { margin:0 -13px; padding:12px 13px; }
  .import-footer>span { max-width:36%; line-height:1.5; }
  .import-footer>div { flex:1; justify-content:flex-end; }
  .import-footer :deep(.el-button) { padding:0 10px; }
}
</style>
