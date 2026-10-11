<template>
  <main class="account-page">
    <template v-if="isEditing">
      <header class="edit-page-header"><div><el-button text @click="requestClose">返回账户列表</el-button><h1>{{ editingId ? '编辑账户' : '新增账户' }}</h1></div><div><el-button :disabled="saving" @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></div></header>
      <section class="edit-page-panel"><el-form label-width="100px"><el-form-item label="账户名称" required><el-input v-model="form.name" maxlength="100" placeholder="如：现金、银行卡、电子钱包" /></el-form-item><el-form-item label="银行卡号"><el-input v-model="form.code" type="password" show-password maxlength="40" placeholder="输入完整卡号或尾号，可选" /></el-form-item><el-form-item label="期初余额"><el-input-number v-model="form.openingBalance" :precision="2" :step="100" controls-position="right" style="width:100%" /></el-form-item></el-form></section>
    </template>
    <template v-else>
    <header class="page-header"><div><h1>账户</h1><p>管理自有资金账户与当前余额</p></div><el-button type="primary" @click="openCreate">新增账户</el-button></header>
    <section class="account-grid" v-loading="loading">
      <article v-for="account in accounts" :key="account.id" class="account-card" role="link" tabindex="0" @click="openDetail(account)" @keydown.enter="openDetail(account)">
        <div class="account-head"><div><h2>{{ account.name }}</h2><span>{{ accountNumberLabel(account.code) }}</span></div><el-dropdown trigger="click" @click.stop><el-button text>更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="openEdit(account)">编辑账户</el-dropdown-item><el-dropdown-item divided @click="remove(account)">删除账户</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div>
        <div class="balance-label">当前余额</div><strong class="balance">¥ {{ money(account.balance) }}</strong>
        <div class="account-footer">查看账户流水 <el-icon><ArrowRight /></el-icon></div>
      </article>
      <el-empty v-if="!loading && !accounts.length" description="还没有账户" />
    </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createAccount, deleteAccount, getAccounts, updateAccount } from '@/modules/Bill/apis/bill.js'
import { accountNumberLabel } from '../utils/accountNumber.js'

const accounts = ref([])
const router = useRouter()
const route = useRoute()
const isEditing = computed(() => route.name === 'BillAccountEdit')
const loading = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = reactive({ name: '', code: '', openingBalance: 0 })
const editSnapshot = ref('')
let skipLeaveGuard = false
const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
function openDetail(account) { router.push(`/bill/account/${account.id}`) }

async function load() {
  loading.value = true
  try { accounts.value = await getAccounts() }
  catch (error) { ElMessage.error(error?.response?.data?.msg || '账户加载失败') }
  finally { loading.value = false }
}
function openCreate() { router.push({ name: 'BillAccountEdit' }) }
function openEdit(account) { router.push({ name: 'BillAccountEdit', params: { id: account.id } }) }
function populateForm(account) { editingId.value = account.id; Object.assign(form, { name: account.name, code: account.code || '', openingBalance: Number(account.openingBalance || 0) }) }
function hasUnsavedChanges() { return isEditing.value && JSON.stringify(form) !== editSnapshot.value }
async function requestClose() {
  if (saving.value) return
  if (hasUnsavedChanges()) { try { await ElMessageBox.confirm('当前内容尚未保存，确定放弃修改并离开吗？', '未保存的修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }) } catch { return } }
  skipLeaveGuard = true
  await router.push('/bill/account')
}
onBeforeRouteLeave(async () => {
  if (skipLeaveGuard) { skipLeaveGuard = false; return true }
  if (!hasUnsavedChanges() || saving.value) return true
  try { await ElMessageBox.confirm('当前内容尚未保存，确定放弃修改并离开吗？', '未保存的修改', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }); return true } catch { return false }
})
async function save() {
  if (!form.name.trim()) return ElMessage.warning('请输入账户名称')
  saving.value = true
  try {
    const payload = { ...form, name: form.name.trim(), code: form.code.trim() }
    if (editingId.value) await updateAccount(editingId.value, payload)
    else await createAccount(payload)
    ElMessage.success('账户已保存')
    if (isEditing.value) { skipLeaveGuard = true; await router.push('/bill/account') }
    await load()
  } catch (error) { ElMessage.error(error?.response?.data?.msg || '账户保存失败') }
  finally { saving.value = false }
}
async function remove(account) {
  try {
    await ElMessageBox.confirm(`确定删除“${account.name}”吗？已被账单使用的账户不能删除。`, '删除账户', { type: 'warning' })
    await deleteAccount(account.id)
    ElMessage.success('账户已删除')
    await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.response?.data?.msg || '账户删除失败') }
}
onMounted(async () => {
  await load()
  if (isEditing.value) {
    const account = accounts.value.find(item => String(item.id) === String(route.params.id))
    if (route.params.id && !account) { ElMessage.error('账户不存在'); router.replace('/bill/account'); return }
    if (account) populateForm(account)
    else { editingId.value = null; Object.assign(form, { name: '', code: '', openingBalance: 0 }) }
    editSnapshot.value = JSON.stringify(form)
  }
})
</script>

<style scoped>
.edit-page-header { display:flex; align-items:center; justify-content:space-between; gap:16px; padding-bottom:14px; border-bottom:1px solid #e4e9ee; }
.edit-page-header h1 { margin:6px 0 0; font-size:22px; }
.edit-page-panel { max-width:700px; padding:20px; background:#fff; border:1px solid #e4e9ee; border-radius:7px; }
.account-page { display:flex; flex-direction:column; gap:18px; color:#293b49; }.page-header { display:flex; justify-content:space-between; align-items:center; }.page-header h1 { margin:0; font-size:24px; }.page-header p { margin:6px 0 0; color:#7c8b95; font-size:13px; }
.account-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(250px,1fr)); gap:14px; align-content:start; }.account-card { position:relative; overflow:hidden; padding:18px; background:#fff; border:1px solid #e4e9ee; border-radius:9px; box-shadow:0 2px 8px rgba(38,55,70,.025); cursor:pointer; transition:transform .16s ease,box-shadow .16s ease; }.account-card:hover { transform:translateY(-2px); box-shadow:0 7px 18px rgba(38,55,70,.08); }.account-card:focus-visible { outline:2px solid #258b88; outline-offset:2px; }.account-card::before { position:absolute; inset:0 0 auto; height:3px; content:''; background:#4c83d4; }.account-card:nth-child(4n + 2)::before { background:#59aa8b; }.account-card:nth-child(4n + 3)::before { background:#e6b44f; }.account-card:nth-child(4n + 4)::before { background:#8a79c7; }.account-head { display:flex; justify-content:space-between; align-items:flex-start; }.account-head h2 { margin:0 0 6px; font-size:16px; }.account-head span,.balance-label { color:#7c8b95; font-size:12px; }.balance-label { margin-top:24px; }.balance { display:block; margin-top:6px; color:#334957; font-size:24px; font-variant-numeric:tabular-nums; }.account-footer { display:flex; align-items:center; gap:4px; margin-top:16px; color:#41819c; font-size:12px; }
</style>
