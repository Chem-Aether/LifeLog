<template>
  <main class="bill-import-page">
    <header class="import-header">
      <div class="title-group"><el-button text class="back-button" @click="requestBack"><el-icon><ArrowLeft /></el-icon>返回账单</el-button><h1>导入账单 CSV</h1><p>自动识别常见列名与账户，导入前可检查、修改或排除每一行。</p></div>
      <div class="header-actions"><el-button @click="downloadTemplate"><el-icon><Download /></el-icon>下载标准模板</el-button><el-upload accept=".csv,text/csv" :auto-upload="false" :show-file-list="false" :on-change="selectFile"><el-button type="primary"><el-icon><Upload /></el-icon>选择 CSV 文件</el-button></el-upload></div>
    </header>

    <section v-if="sourceRows.length" class="file-strip">
      <div class="file-info"><el-icon><Document /></el-icon><div><strong>{{ fileName }}</strong><span>{{ sourceRows.length }} 行 · {{ headers.length }} 列 · {{ validCount }} 行可导入</span></div></div>
      <el-button text type="danger" @click="clearFile">移除文件</el-button>
    </section>
    <section v-else class="empty-upload"><el-icon><Upload /></el-icon><strong>选择一份账单 CSV</strong><span>支持常见银行、支付平台导出文件，也可直接使用标准模板。</span><div><el-upload accept=".csv,text/csv" :auto-upload="false" :show-file-list="false" :on-change="selectFile"><el-button type="primary">选择文件</el-button></el-upload><el-button @click="downloadTemplate">下载标准模板</el-button></div></section>

    <template v-if="sourceRows.length">
      <section class="mapping-section">
        <div class="section-heading"><div><h2>列映射</h2><p>已按列名自动匹配，可手动调整。未填写方向时，根据金额正负推断收入或支出。</p></div><el-button text @click="resetMapping">重新自动识别</el-button></div>
        <div class="mapping-grid">
          <label v-for="field in mappingFields" :key="field.key" class="mapping-field"><span>{{ field.label }}<small v-if="field.required">必填</small></span><el-select v-model="mapping[field.key]" clearable placeholder="不导入此列" @change="updateMapping(field.key)"><el-option v-for="(header, index) in headers" :key="index" :label="columnLabel(header, index)" :value="index" /></el-select></label>
        </div>
      </section>

      <section class="preview-section">
        <div class="section-heading preview-heading"><div><h2>数据预览</h2><p>账户与分类可逐行校正；取消勾选的行不会导入。标红行需修正或排除。</p></div><div class="preview-stats"><span>{{ includedCount }} 行待导入</span><span v-if="invalidCount" class="invalid-stat">{{ invalidCount }} 行有问题</span></div></div>
        <div class="preview-table-wrap">
          <el-table :data="visibleRows" row-key="line" :row-class-name="rowClassName" height="min(62vh, 680px)" empty-text="没有可预览的记录">
            <el-table-column label="导入" width="62" fixed="left"><template #default="{ row }"><el-checkbox v-model="row.include" /></template></el-table-column>
            <el-table-column label="日期时间" min-width="166"><template #default="{ row }"><el-input v-model="row.occurredAt" placeholder="YYYY-MM-DD HH:mm" /></template></el-table-column>
            <el-table-column label="方向 / 金额" min-width="190"><template #default="{ row }"><div class="amount-cell"><el-select v-model="row.direction" class="direction-select"><el-option label="支出" value="expense" /><el-option label="收入" value="income" /><el-option label="转账" value="transfer" /></el-select><el-input-number v-model="row.amount" :min="0.01" :precision="2" :step="1" controls-position="right" /></div></template></el-table-column>
            <el-table-column label="出账账户" min-width="170"><template #default="{ row }"><el-select v-model="row.fromAccountId" clearable filterable placeholder="选择账户"><el-option v-for="account in accounts" :key="account.id" :label="accountLabel(account)" :value="Number(account.id)" /></el-select></template></el-table-column>
            <el-table-column label="入账账户" min-width="170"><template #default="{ row }"><el-select v-model="row.toAccountId" clearable filterable placeholder="选择账户"><el-option v-for="account in accounts" :key="account.id" :label="accountLabel(account)" :value="Number(account.id)" /></el-select></template></el-table-column>
            <el-table-column label="交易对象" min-width="150"><template #default="{ row }"><el-input v-model="row.counterparty" placeholder="商户或对方" /></template></el-table-column>
            <el-table-column label="商品 / 摘要" min-width="160"><template #default="{ row }"><el-input v-model="row.description" placeholder="可选" /></template></el-table-column>
            <el-table-column label="分类" min-width="150"><template #default="{ row }"><el-select v-model="row.categoryId" clearable filterable placeholder="不分类" @change="row.invalidCategory = false"><el-option v-for="category in categories" :key="category.id" :label="category.name" :value="Number(category.id)" /></el-select></template></el-table-column>
            <el-table-column label="校验" min-width="210" fixed="right"><template #default="{ row }"><span v-if="rowErrors(row).length" class="row-error">{{ rowErrors(row).join('；') }}</span><span v-else class="row-valid">可导入</span></template></el-table-column>
          </el-table>
        </div>
        <el-pagination v-if="previewRows.length > pageSize" v-model:current-page="page" :page-size="pageSize" :total="previewRows.length" layout="total, prev, pager, next" background />
      </section>

      <footer class="import-footer"><span>{{ validCount }} 条有效 <em v-if="invalidCount">· {{ invalidCount }} 条需修正或排除</em></span><div><el-button @click="requestBack">取消</el-button><el-button type="primary" :disabled="!validCount || invalidCount > 0 || importing" :loading="importing" @click="submitImport">确认导入 {{ validCount }} 条</el-button></div></footer>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Document, Download, Upload } from '@element-plus/icons-vue'
import { getAccounts, getBillCategories, importBillRecords } from '@/modules/Bill/apis/bill.js'
import { createBillCsvTemplate, decodeBillCsv, normalizeBillDate, parseBillAmount, parseBillCsv, resolveBillAccount, resolveBillCategory, suggestBillCsvMapping } from '../utils/billCsv.js'

const router = useRouter()
const accounts = ref([])
const categories = ref([])
const mappingFields = [
  { key: 'occurredAt', label: '交易日期', required: true }, { key: 'amount', label: '交易金额', required: true },
  { key: 'direction', label: '收支方向' }, { key: 'fromAccount', label: '出账账户' }, { key: 'toAccount', label: '入账账户' },
  { key: 'counterparty', label: '交易对象' }, { key: 'description', label: '商品 / 摘要' }, { key: 'category', label: '分类' }, { key: 'remark', label: '备注' },
]
const fileName = ref('')
const headers = ref([])
const sourceRows = ref([])
const previewRows = ref([])
const mapping = reactive(emptyMapping())
const importing = ref(false)
const page = ref(1)
const pageSize = 100
const leaveAllowed = ref(false)
const categoryMappingTouched = ref(false)
const visibleRows = computed(() => previewRows.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const includedCount = computed(() => previewRows.value.filter(row => row.include).length)
const invalidCount = computed(() => previewRows.value.filter(row => row.include && rowErrors(row).length).length)
const validCount = computed(() => previewRows.value.filter(row => row.include && !rowErrors(row).length).length)

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
    const direction = directionText.includes('转') || directionText === 'transfer' ? 'transfer'
      : directionText.includes('收入') || directionText.includes('入账') || directionText === 'income' ? 'income'
        : directionText.includes('支出') || directionText.includes('消费') || directionText === 'expense' ? 'expense'
          : signedAmount == null ? '' : signedAmount < 0 ? 'expense' : 'income'
    const fromValue = cell(cells, 'fromAccount')
    const toValue = cell(cells, 'toAccount')
    const fromMatch = resolveBillAccount(fromValue, accounts.value)
    const toMatch = resolveBillAccount(toValue, accounts.value)
    const categoryMatch = resolveBillCategory(cell(cells, 'category'), categories.value)
    let fromAccountId = fromMatch.id
    let toAccountId = toMatch.id
    if (direction === 'income' && !toAccountId && fromAccountId) { toAccountId = fromAccountId; fromAccountId = null }
    return {
      line, include: true, occurredAt: normalizeBillDate(cell(cells, 'occurredAt')),
      direction, amount: signedAmount == null ? null : Math.abs(signedAmount), fromAccountId, toAccountId,
      counterparty: cell(cells, 'counterparty'), description: cell(cells, 'description'),
      categoryId: categoryMatch.id, invalidCategory: Boolean(cell(cells, 'category') && !categoryMatch.id),
      remark: cell(cells, 'remark'),
    }
  })
}
function updateMapping(key) {
  if (key === 'category') categoryMappingTouched.value = true
  buildPreview()
}
function autoMapCategory() {
  if (categoryMappingTouched.value || !sourceRows.value.length || !categories.value.length) return
  const names = new Set(categories.value.map(category => normalizeCell(category.name)).filter(Boolean))
  const score = index => sourceRows.value.reduce((count, row) => count + (names.has(normalizeCell(row.cells[index])) ? 1 : 0), 0)
  const currentScore = mapping.category >= 0 ? score(Number(mapping.category)) : 0
  let bestIndex = -1
  let bestScore = currentScore
  headers.value.forEach((_, index) => {
    const value = score(index)
    if (value > bestScore) { bestIndex = index; bestScore = value }
  })
  if (bestIndex >= 0) { mapping.category = bestIndex; buildPreview() }
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
function recordsToImport() {
  return previewRows.value.filter(row => row.include && !rowErrors(row).length).map(row => ({
    occurredAt: normalizeBillDate(row.occurredAt), amount: Number(row.amount),
    fromAccountId: row.direction === 'income' ? null : row.fromAccountId,
    toAccountId: row.direction === 'expense' ? null : row.toAccountId,
    counterparty: row.counterparty, description: row.description, categoryId: row.categoryId || null, remark: row.remark,
  }))
}
async function selectFile(uploadFile) {
  if (!uploadFile.raw) return
  fileName.value = uploadFile.name
  page.value = 1
  try {
    const buffer = await uploadFile.raw.arrayBuffer()
    const parsed = parseBillCsv(decodeBillCsv(buffer))
    if (!parsed.rows.length) throw new Error('文件中没有账单数据行')
    headers.value = parsed.headers
    sourceRows.value = parsed.rows.map((cells, index) => ({ cells, line: index + 2 }))
    categoryMappingTouched.value = false
    Object.assign(mapping, suggestBillCsvMapping(parsed.headers))
    autoMapCategory()
    buildPreview()
  } catch (error) { resetFile(); ElMessage.error(error.message || 'CSV 解析失败') }
}
function resetFile() { fileName.value = ''; headers.value = []; sourceRows.value = []; previewRows.value = []; categoryMappingTouched.value = false; Object.assign(mapping, emptyMapping()) }
async function clearFile() {
  try { await ElMessageBox.confirm('移除文件会清除所有列映射和校对结果，确定继续吗？', '移除导入文件', { type: 'warning', confirmButtonText: '移除文件', cancelButtonText: '保留文件' }); resetFile() }
  catch { /* keep the current import */ }
}
function downloadTemplate() {
  const url = URL.createObjectURL(new Blob([createBillCsvTemplate()], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a'); link.href = url; link.download = '账单导入模板.csv'; link.click(); URL.revokeObjectURL(url)
}
async function submitImport() {
  const records = recordsToImport()
  if (!records.length || invalidCount.value) return
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
  if (sourceRows.value.length) {
    try { await ElMessageBox.confirm('离开后当前文件和校对结果将被清除，确定返回吗？', '放弃本次导入', { type: 'warning', confirmButtonText: '放弃并返回', cancelButtonText: '继续导入' }) }
    catch { return }
  }
  leaveAllowed.value = true
  await router.push('/bill/query')
}
onBeforeRouteLeave(async () => {
  if (leaveAllowed.value || !sourceRows.value.length || importing.value) return true
  try { await ElMessageBox.confirm('离开后当前文件和校对结果将被清除，确定离开吗？', '放弃本次导入', { type: 'warning', confirmButtonText: '放弃并离开', cancelButtonText: '继续导入' }); return true }
  catch { return false }
})
watch(previewRows, () => { page.value = Math.min(page.value, Math.max(1, Math.ceil(previewRows.value.length / pageSize))) })
onMounted(async () => {
  try { [accounts.value, categories.value] = await Promise.all([getAccounts(), getBillCategories()]); autoMapCategory(); buildPreview() }
  catch { ElMessage.error('账户或分类加载失败，请检查后再导入') }
})
</script>

<style scoped>
.bill-import-page { display:grid; gap:16px; padding-bottom:74px; color:#293b49; }
.import-header,.section-heading,.file-strip,.import-footer { display:flex; align-items:center; justify-content:space-between; gap:16px; }
.import-header { align-items:flex-start; padding-bottom:14px; border-bottom:1px solid #e3e9ec; }.title-group h1 { margin:6px 0; font-size:24px; }.title-group p,.section-heading p { margin:0; color:#7c8b95; font-size:13px; line-height:1.5; }.back-button { padding:0; color:#667780; }.back-button .el-icon { margin-right:5px; }.header-actions { display:flex; align-items:center; gap:8px; }.header-actions :deep(.el-button--primary),.import-footer :deep(.el-button--primary) { --el-button-bg-color:#258b88; --el-button-border-color:#258b88; --el-button-hover-bg-color:#1f7775; --el-button-hover-border-color:#1f7775; }
.file-strip,.mapping-section,.preview-section,.empty-upload { padding:16px; border:1px solid #e3e9ec; border-radius:7px; background:#fff; }.file-info { display:flex; align-items:center; gap:12px; color:#258b88; }.file-info>.el-icon { font-size:22px; }.file-info strong,.file-info span { display:block; }.file-info strong { color:#344957; font-size:13px; }.file-info span { margin-top:4px; color:#89979d; font-size:11px; }
.empty-upload { display:grid; min-height:260px; align-content:center; justify-items:center; gap:12px; text-align:center; }.empty-upload>.el-icon { color:#258b88; font-size:30px; }.empty-upload strong { font-size:16px; }.empty-upload>span { color:#7c8b95; font-size:13px; }.empty-upload>div { display:flex; gap:8px; margin-top:6px; }
.section-heading { align-items:flex-start; margin-bottom:14px; }.section-heading h2 { margin:0 0 5px; font-size:16px; }.mapping-grid { display:grid; grid-template-columns:repeat(3,minmax(180px,1fr)); gap:12px; }.mapping-field { display:grid; min-width:0; gap:6px; }.mapping-field>span { display:flex; align-items:center; gap:6px; color:#53676f; font-size:12px; }.mapping-field small { color:#c95e66; font-size:10px; }
.preview-section { min-width:0; }.preview-heading { align-items:center; }.preview-stats { display:flex; flex:0 0 auto; gap:10px; color:#60747d; font-size:12px; }.invalid-stat,.row-error { color:#c95e66; }.preview-table-wrap { overflow-x:auto; }.preview-table-wrap :deep(.el-input),.preview-table-wrap :deep(.el-select),.amount-cell :deep(.el-input-number) { width:100%; }.amount-cell { display:flex; gap:6px; }.amount-cell .direction-select { width:82px; flex:0 0 82px; }.row-error { font-size:11px; line-height:1.45; }.row-valid { color:#288d76; font-size:11px; }.preview-section :deep(.el-pagination) { justify-content:flex-end; margin-top:14px; }.preview-section :deep(.el-table .import-row-invalid td) { background:#fff8f7; }
.import-footer { position:sticky; z-index:5; bottom:0; margin:0 -8px; padding:12px 8px; border-top:1px solid #e3e9ec; background:rgba(255,255,255,.96); backdrop-filter:blur(10px); }.import-footer>span { color:#53676f; font-size:12px; }.import-footer em { color:#c95e66; font-style:normal; }.import-footer>div { display:flex; gap:8px; }
@media(max-width:900px) { .mapping-grid { grid-template-columns:repeat(2,minmax(150px,1fr)); } }
@media(max-width:760px) { .import-header,.section-heading { align-items:flex-start; flex-direction:column; }.header-actions { width:100%; }.title-group h1 { font-size:21px; }.mapping-grid { grid-template-columns:1fr; }.preview-heading { gap:8px; }.file-strip { align-items:flex-start; }.import-footer { margin:0 -13px; padding:12px 13px; }.import-footer>span { max-width:38%; line-height:1.5; }.import-footer>div { flex:1; justify-content:flex-end; }.import-footer :deep(.el-button) { padding:0 10px; } }
</style>
