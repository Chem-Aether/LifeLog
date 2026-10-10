<template>
  <main class="account-page">
    <header class="page-header"><div><h1>账户</h1><p>管理自有资金账户与当前余额</p></div><el-button type="primary" @click="openCreate">新增账户</el-button></header>
    <section class="account-grid" v-loading="loading">
      <article v-for="account in accounts" :key="account.id" class="account-card" role="link" tabindex="0" @click="openDetail(account)" @keydown.enter="openDetail(account)">
        <div class="account-head"><div><h2>{{ account.name }}</h2><span>{{ accountNumberLabel(account.code) }}</span></div><el-dropdown trigger="click" @click.stop><el-button text>更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="openEdit(account)">编辑账户</el-dropdown-item><el-dropdown-item divided @click="remove(account)">删除账户</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div>
        <div class="balance-label">当前余额</div><strong class="balance">¥ {{ money(account.balance) }}</strong>
        <div class="account-footer">查看账户流水 <el-icon><ArrowRight /></el-icon></div>
      </article>
      <el-empty v-if="!loading && !accounts.length" description="还没有账户" />
    </section>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑账户' : '新增账户'" width="min(460px, 94vw)" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="账户名称" required><el-input v-model="form.name" maxlength="100" placeholder="如：现金、银行卡、电子钱包" /></el-form-item>
        <el-form-item label="银行卡号"><el-input v-model="form.code" type="password" show-password maxlength="40" placeholder="输入完整卡号或尾号，可选" /></el-form-item>
        <el-form-item label="期初余额"><el-input-number v-model="form.openingBalance" :precision="2" :step="100" controls-position="right" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createAccount, deleteAccount, getAccounts, updateAccount } from '@/modules/Bill/apis/bill.js'
import { accountNumberLabel } from '../utils/accountNumber.js'

const accounts = ref([])
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({ name: '', code: '', openingBalance: 0 })
const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
function openDetail(account) { router.push(`/bill/account/${account.id}`) }

async function load() {
  loading.value = true
  try { accounts.value = await getAccounts() }
  catch (error) { ElMessage.error(error?.response?.data?.msg || '账户加载失败') }
  finally { loading.value = false }
}
function openCreate() { editingId.value = null; Object.assign(form, { name: '', code: '', openingBalance: 0 }); dialogVisible.value = true }
function openEdit(account) { editingId.value = account.id; Object.assign(form, { name: account.name, code: account.code || '', openingBalance: Number(account.openingBalance || 0) }); dialogVisible.value = true }
async function save() {
  if (!form.name.trim()) return ElMessage.warning('请输入账户名称')
  saving.value = true
  try {
    const payload = { ...form, name: form.name.trim(), code: form.code.trim() }
    if (editingId.value) await updateAccount(editingId.value, payload)
    else await createAccount(payload)
    dialogVisible.value = false
    ElMessage.success('账户已保存')
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
onMounted(load)
</script>

<style scoped>
.account-page { display:flex; flex-direction:column; gap:18px; color:#293b49; }.page-header { display:flex; justify-content:space-between; align-items:center; }.page-header h1 { margin:0; font-size:24px; }.page-header p { margin:6px 0 0; color:#7c8b95; font-size:13px; }
.account-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(250px,1fr)); gap:14px; align-content:start; }.account-card { position:relative; overflow:hidden; padding:18px; background:#fff; border:1px solid #e4e9ee; border-radius:9px; box-shadow:0 2px 8px rgba(38,55,70,.025); cursor:pointer; transition:transform .16s ease,box-shadow .16s ease; }.account-card:hover { transform:translateY(-2px); box-shadow:0 7px 18px rgba(38,55,70,.08); }.account-card:focus-visible { outline:2px solid #258b88; outline-offset:2px; }.account-card::before { position:absolute; inset:0 0 auto; height:3px; content:''; background:#4c83d4; }.account-card:nth-child(4n + 2)::before { background:#59aa8b; }.account-card:nth-child(4n + 3)::before { background:#e6b44f; }.account-card:nth-child(4n + 4)::before { background:#8a79c7; }.account-head { display:flex; justify-content:space-between; align-items:flex-start; }.account-head h2 { margin:0 0 6px; font-size:16px; }.account-head span,.balance-label { color:#7c8b95; font-size:12px; }.balance-label { margin-top:24px; }.balance { display:block; margin-top:6px; color:#334957; font-size:24px; font-variant-numeric:tabular-nums; }.account-footer { display:flex; align-items:center; gap:4px; margin-top:16px; color:#41819c; font-size:12px; }
</style>
