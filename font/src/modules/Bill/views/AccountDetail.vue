<template>
  <main class="detail-page" v-loading="accountLoading">
    <header class="page-header">
      <div><el-button text class="back-button" @click="router.push('/bill/account')"><el-icon><ArrowLeft /></el-icon>账户列表</el-button><h1>{{ account?.name || '账户详情' }}</h1></div>
      <el-button v-if="account" @click="openEdit"><el-icon><Edit /></el-icon>编辑账户</el-button>
    </header>

    <section v-if="account" class="account-summary">
      <div class="account-identity"><span>账户信息</span><strong>{{ account.name }}</strong><small>{{ accountNumberLabel(account.code) }}</small></div>
      <div class="account-balance"><span>当前余额</span><strong>¥ {{ money(account.balance) }}</strong></div>
    </section>

    <section v-if="account" class="records-panel" v-loading="recordsLoading">
      <div class="section-heading"><div><h2>账户流水</h2><span>共 {{ total.toLocaleString('zh-CN') }} 条</span></div></div>
      <BillRecordTable :rows="records" :accounts="[account]" :show-actions="false" @sort-change="handleSort" />
      <div class="pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper" background @current-change="loadRecords" @size-change="changePageSize" />
      </div>
    </section>
    <el-empty v-else-if="!accountLoading" description="账户不存在或已删除"><el-button @click="router.push('/bill/account')">返回账户列表</el-button></el-empty>

    <el-dialog v-model="dialogVisible" title="编辑账户" width="min(460px, 94vw)" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="账户名称" required><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="银行卡号"><el-input v-model="form.code" type="password" show-password maxlength="40" placeholder="输入完整卡号或尾号，可选" /></el-form-item>
        <el-form-item label="期初余额"><el-input-number v-model="form.openingBalance" :precision="2" :step="100" controls-position="right" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Edit } from '@element-plus/icons-vue'
import { getAccounts, getAccountRecords, updateAccount } from '@/modules/Bill/apis/bill.js'
import BillRecordTable from '../components/BillRecordTable.vue'
import { accountNumberLabel } from '../utils/accountNumber.js'

const route = useRoute()
const router = useRouter()
const account = ref(null)
const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const sortBy = ref('occurredAt')
const sortOrder = ref('desc')
const accountLoading = ref(false)
const recordsLoading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({ name: '', code: '', openingBalance: 0 })
const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const formatDate = value => value ? String(value).replace('T', ' ').slice(0, 16) : '-'
const directionName = direction => ({ income: '收入', expense: '支出', transfer: '内部转账' })[direction] || '未知'

async function loadAccount() {
  accountLoading.value = true
  try {
    const accounts = await getAccounts()
    account.value = accounts.find(item => String(item.id) === String(route.params.id)) || null
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || '账户加载失败')
  } finally { accountLoading.value = false }
}
async function loadRecords() {
  if (!account.value) return
  recordsLoading.value = true
  try {
    const result = await getAccountRecords(account.value.id, { page: page.value, pageSize: pageSize.value, sortBy: sortBy.value, sortOrder: sortOrder.value })
    records.value = result.records
    total.value = result.total
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '账户流水加载失败') }
  finally { recordsLoading.value = false }
}
function changePageSize() { page.value = 1; loadRecords() }
function handleSort({ prop, order }) {
  sortBy.value = order ? prop : 'occurredAt'
  sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  page.value = 1
  loadRecords()
}
function openEdit() {
  Object.assign(form, { name: account.value.name, code: account.value.code || '', openingBalance: Number(account.value.openingBalance || 0) })
  dialogVisible.value = true
}
async function save() {
  if (!form.name.trim()) return ElMessage.warning('请输入账户名称')
  saving.value = true
  try {
    await updateAccount(account.value.id, { ...form, name: form.name.trim(), code: form.code.trim() })
    dialogVisible.value = false
    ElMessage.success('账户信息已更新')
    await loadAccount()
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '账户更新失败') }
  finally { saving.value = false }
}

onMounted(async () => {
  await loadAccount()
  await loadRecords()
})
</script>

<style scoped>
.detail-page { display:flex; flex-direction:column; gap:16px; color:#293b49; }.page-header { display:flex; align-items:center; justify-content:space-between; }.page-header h1 { margin:4px 0 0; font-size:24px; }.back-button { padding:0; color:#65818c; }.account-summary,.records-panel { background:#fff; border:1px solid #e4e9ee; border-radius:9px; box-shadow:0 2px 8px rgba(38,55,70,.025); }.account-summary { display:flex; align-items:center; justify-content:space-between; gap:20px; padding:22px 24px; }.account-identity,.account-balance { display:flex; flex-direction:column; gap:6px; }.account-identity span,.account-balance span { color:#7c8b95; font-size:12px; }.account-identity strong { color:#293b49; font-size:20px; }.account-identity small { color:#87949b; font-size:12px; }.account-balance { align-items:flex-end; }.account-balance strong { color:#258b88; font-size:26px; font-variant-numeric:tabular-nums; }.records-panel { padding:18px; }.section-heading { display:flex; align-items:center; justify-content:space-between; margin-bottom:14px; }.section-heading > div { display:flex; align-items:baseline; gap:10px; }.section-heading h2 { margin:0; font-size:17px; }.section-heading span { color:#89969d; font-size:12px; }.flow-arrow { margin:0 9px; color:#9aabad; }.income { color:#288d76; }.expense { color:#d76069; }.transfer { color:#537fbd; }.tag-income { color:#288d76; border-color:#b9ded2; background:#eff8f4; }.tag-expense { color:#c95e66; border-color:#efc6c8; background:#fff4f4; }.tag-transfer { color:#537fbd; border-color:#c5d5eb; background:#f2f6fc; }.pagination { display:flex; justify-content:flex-end; padding-top:16px; }
.detail-page :deep(.el-pagination.is-background .el-pager li.is-active) { background-color:#258b88; }
@media(max-width:760px) { .account-summary { align-items:flex-start; flex-direction:column; padding:18px; }.account-balance { align-items:flex-start; }.account-balance strong { font-size:23px; }.records-panel { padding:12px; }.pagination { justify-content:center; overflow-x:auto; }.pagination :deep(.el-pagination) { flex-wrap:wrap; justify-content:center; height:auto; gap:6px; }.page-header h1 { font-size:21px; } }
</style>
