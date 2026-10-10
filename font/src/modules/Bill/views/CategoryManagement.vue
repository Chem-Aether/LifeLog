<template>
  <main class="category-page">
    <header class="page-header">
      <div><h1>分类管理</h1><p>维护分类名称、层级和图标，一级和二级分类均可用于记账</p></div>
      <el-button type="primary" @click="openCreate">新增分类</el-button>
    </header>
    <section class="summary-strip">
      <div><span>一级分类</span><strong>{{ rootCount }}</strong></div>
      <div><span>全部分类</span><strong>{{ categories.length }}</strong></div>
    </section>
    <section class="category-panel" v-loading="loading">
      <div class="panel-heading"><div><h2>分类层级</h2><span>共 {{ categories.length }} 项</span></div><el-button text :loading="loading" @click="loadCategories">刷新</el-button></div>
      <el-table :data="categoryTree" row-key="id" default-expand-all :tree-props="{ children: 'children' }" empty-text="暂无分类" class="category-table">
        <el-table-column prop="name" label="分类名称" min-width="240"><template #default="{ row }"><span class="category-name-cell"><CategoryIcon :value="row.icon" /><span :class="['category-name', { 'is-root': row.parentId == null }]">{{ row.name }}</span></span></template></el-table-column>
        <el-table-column prop="icon" label="图标" width="90" align="center"><template #default="{ row }"><span class="category-icon"><CategoryIcon :value="row.icon" /></span></template></el-table-column>
        <el-table-column label="层级" width="130"><template #default="{ row }"><span :class="['level-tag', row.parentId == null ? 'root' : 'child']">{{ row.parentId == null ? '一级分类' : '二级分类' }}</span></template></el-table-column>
        <el-table-column prop="parentName" label="所属一级分类" min-width="180"><template #default="{ row }">{{ row.parentName || '—' }}</template></el-table-column>
        <el-table-column label="操作" width="140" align="right" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="danger" @click="removeCategory(row)">删除</el-button></template></el-table-column>
      </el-table>
    </section>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑分类' : '新增分类'" width="min(500px, 94vw)" destroy-on-close :close-on-click-modal="false">
      <el-form label-width="96px" class="category-form">
        <template v-if="!editingId">
          <el-form-item label="分类层级" required><el-radio-group v-model="form.level" @change="onLevelChange"><el-radio-button value="root">一级分类</el-radio-button><el-radio-button value="child">二级分类</el-radio-button></el-radio-group></el-form-item>
          <el-form-item v-if="form.level === 'child'" label="所属一级" required><el-select v-model="form.parentId" placeholder="选择所属一级分类" class="full-width"><el-option v-for="parent in rootCategories" :key="parent.id" :label="parent.name" :value="parent.id" /></el-select></el-form-item>
        </template>
        <el-form-item label="分类名称" required><el-input v-model="form.name" maxlength="100" placeholder="输入分类名称" @keyup.enter="saveCategory" /></el-form-item>
        <el-form-item label="图标内容" required>
          <div class="icon-editor">
            <el-input v-model="form.icon" type="textarea" :rows="3" maxlength="32000" show-word-limit placeholder="直接输入 Emoji，或粘贴 SVG 文本" />
            <div class="icon-editor-tools">
              <el-upload accept=".svg,image/svg+xml" :auto-upload="false" :show-file-list="false" :on-change="loadSvgFile"><el-button>上传 SVG 文本</el-button></el-upload>
              <span>上传内容会直接填入并保存到 icon 字段</span>
            </div>
            <div class="icon-preview"><span>预览</span><CategoryIcon :value="form.icon" /></div>
            <div class="emoji-picker" aria-label="Emoji 快捷选择">
              <button v-for="emoji in categoryIconOptions" :key="emoji" type="button" :class="['icon-option', { selected: form.icon === emoji }]" :aria-label="`选择${emoji}`" :title="emoji" @click="form.icon = emoji">{{ emoji }}</button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveCategory">{{ editingId ? '保存修改' : '保存' }}</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createBillCategory, deleteBillCategory, getBillCategories, updateBillCategory } from '@/modules/Bill/apis/bill.js'
import CategoryIcon from '../components/CategoryIcon.vue'
import { categoryIconOptions } from '@/modules/Bill/config/categoryIcons.js'

const categories = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({ level: 'child', parentId: null, name: '', icon: '🏷️' })
const rootCategories = computed(() => categories.value.filter(category => category.parentId == null))
const rootCount = computed(() => rootCategories.value.length)
const categoryTree = computed(() => rootCategories.value.map(parent => ({
  ...parent,
  children: categories.value.filter(category => category.parentId === parent.id),
})))

async function loadCategories() {
  loading.value = true
  try { categories.value = await getBillCategories() }
  catch (error) { ElMessage.error(error?.response?.data?.msg || '分类加载失败') }
  finally { loading.value = false }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { level: 'child', parentId: rootCategories.value[0]?.id ?? null, name: '', icon: '🏷️' })
  dialogVisible.value = true
}

function openEdit(category) {
  editingId.value = category.id
  Object.assign(form, { level: category.parentId == null ? 'root' : 'child', parentId: category.parentId, name: category.name, icon: category.icon || '🏷️' })
  dialogVisible.value = true
}

function onLevelChange(level) {
  if (level === 'root') form.parentId = null
  else if (!rootCategories.value.some(category => category.id === form.parentId)) form.parentId = rootCategories.value[0]?.id ?? null
}

async function loadSvgFile(file) {
  if (!file.raw) return
  if (file.size > 32000) return ElMessage.warning('SVG 文件不能超过 32 KB')
  try {
    form.icon = await file.raw.text()
    ElMessage.success('SVG 内容已载入，可在文本框中查看或修改')
  } catch { ElMessage.error('读取 SVG 文件失败') }
}

async function saveCategory() {
  const name = form.name.trim()
  if (!name) return ElMessage.warning('请输入分类名称')
  if (form.level === 'child' && !form.parentId) return ElMessage.warning('请选择所属一级分类')
  saving.value = true
  try {
    if (editingId.value) await updateBillCategory(editingId.value, { name, icon: form.icon })
    else await createBillCategory({ name, parentId: form.level === 'child' ? form.parentId : null, icon: form.icon })
    await loadCategories()
    dialogVisible.value = false
    ElMessage.success(editingId.value ? '分类已更新' : '分类已添加')
  } catch (error) { ElMessage.error(error?.response?.data?.msg || (editingId.value ? '分类更新失败' : '分类添加失败')) }
  finally { saving.value = false }
}

async function removeCategory(category) {
  try {
    await ElMessageBox.confirm(`确定删除分类“${category.name}”吗？仍被账单或子分类使用的分类无法删除。`, '删除分类', { type: 'warning' })
    await deleteBillCategory(category.id)
    await loadCategories()
    ElMessage.success('分类已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.response?.data?.msg || '分类删除失败')
  }
}

onMounted(loadCategories)
</script>

<style scoped>
.category-page { display:flex; flex-direction:column; gap:16px; color:#293b49; }
.page-header { display:flex; align-items:center; justify-content:space-between; gap:16px; }
.page-header h1 { margin:0; font-size:24px; }
.page-header p { margin:6px 0 0; color:#7c8b95; font-size:13px; }
.summary-strip,.category-panel { background:#fff; border:1px solid #e4e9ee; border-radius:9px; box-shadow:0 2px 8px rgba(38,55,70,.025); }
.summary-strip { display:flex; align-items:center; min-height:82px; padding:0 22px; }
.summary-strip > div { display:flex; flex-direction:column; gap:5px; min-width:150px; padding:0 24px; border-left:1px solid #edf0f2; }
.summary-strip > div:first-child { padding-left:0; border-left:0; }
.summary-strip span,.panel-heading span { color:#7c8b95; font-size:12px; }
.summary-strip strong { color:#258b88; font-size:21px; font-variant-numeric:tabular-nums; }
.category-panel { padding:18px; }
.panel-heading { display:flex; align-items:center; justify-content:space-between; margin-bottom:14px; }
.panel-heading > div { display:flex; align-items:baseline; gap:10px; }
.panel-heading h2 { margin:0; font-size:17px; }
.category-table { --el-table-header-bg-color:#f5f7f9; --el-table-row-hover-bg-color:#f4f9f8; --el-table-border-color:#edf0f2; --el-table-header-text-color:#667984; color:#344957; }
.category-table :deep(th.el-table__cell) { font-size:12px; font-weight:600; }
.category-name-cell { display:inline-flex; align-items:center; gap:10px; }.category-name { color:#536873; }
.category-name.is-root { color:#293b49; font-weight:600; }
.category-icon { font-size:20px; line-height:1; }
.level-tag { display:inline-flex; align-items:center; min-height:24px; padding:0 9px; border:1px solid; border-radius:5px; font-size:12px; }
.level-tag.root { color:#258b88; border-color:#b9dedb; background:#eff8f7; }
.level-tag.child { color:#637b89; border-color:#d9e2e7; background:#f5f7f9; }
.category-form { max-width:440px; }
.icon-editor { width:100%; }.icon-editor-tools { display:flex; align-items:center; gap:10px; margin-top:8px; color:#86949b; font-size:11px; }.icon-preview { display:flex; align-items:center; gap:14px; min-height:52px; margin:12px 0; padding:8px 12px; border:1px solid #e4e9ee; border-radius:6px; background:#f7f9fa; color:#7c8b95; font-size:12px; }.icon-preview :deep(.category-svg-icon),.icon-preview :deep(.category-emoji-icon) { color:#258b88; font-size:24px; }
.emoji-picker { display:grid; grid-template-columns:repeat(8,32px); gap:5px; max-height:120px; overflow:auto; padding:2px; }.icon-option { display:grid; place-items:center; width:32px; height:32px; padding:0; border:1px solid #e1e8eb; border-radius:6px; background:#fff; font-size:18px; cursor:pointer; }.icon-option:hover { border-color:#8dc5c1; background:#f4faf9; }.icon-option.selected { border-color:#258b88; background:#eaf5f4; box-shadow:0 0 0 1px #258b88 inset; }
.full-width { width:100%; }
@media(max-width:700px) {
  .page-header { align-items:flex-start; }
  .page-header h1 { font-size:21px; }
  .summary-strip { min-height:72px; padding:0 14px; }
  .summary-strip > div { min-width:0; flex:1; padding:0 16px; }
  .category-panel { padding:12px; }
  .emoji-picker { grid-template-columns:repeat(7,32px); }
}
</style>
