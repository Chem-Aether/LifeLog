<template>
  <el-popover v-model:visible="visible" trigger="click" placement="bottom-start" :width="popoverWidth" :teleported="true">
    <template #reference>
      <button type="button" class="category-trigger" :class="{ 'is-open': visible, 'is-placeholder': !selectedLabel }" :aria-expanded="visible">
        <span class="trigger-label">{{ selectedLabel || placeholder }}</span>
        <el-button v-if="modelValue != null && modelValue !== ''" text class="clear-button" aria-label="清除分类" @click.stop="clearSelection"><el-icon><CircleClose /></el-icon></el-button>
        <el-icon v-else class="trigger-arrow"><ArrowDown /></el-icon>
      </button>
    </template>
    <div class="category-picker">
      <el-input v-model="keyword" clearable size="small" placeholder="搜索分类" class="category-search" @input="treeRef?.filter(keyword)" />
      <el-tree
        ref="treeRef"
        :data="treeData"
        node-key="id"
        :props="treeProps"
        :current-node-key="modelValue"
        :expand-on-click-node="false"
        :filter-node-method="filterNode"
        highlight-current
        @node-click="selectNode"
      >
        <template #default="{ data }">
          <span class="category-node"><CategoryIcon :value="data.icon" /><span>{{ data.name }}</span><small>{{ data.parentId == null ? '一级' : '二级' }}</small></span>
        </template>
      </el-tree>
      <el-empty v-if="!treeData.length" description="暂无分类" :image-size="48" />
    </div>
  </el-popover>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ArrowDown, CircleClose } from '@element-plus/icons-vue'
import CategoryIcon from './CategoryIcon.vue'

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  categories: { type: Array, default: () => [] },
  placeholder: { type: String, default: '选择分类' },
  popoverWidth: { type: [Number, String], default: 320 },
})
const emit = defineEmits(['update:modelValue'])
const visible = ref(false)
const keyword = ref('')
const treeRef = ref(null)
const treeProps = { label: 'name', children: 'children' }
const treeData = computed(() => props.categories.filter(item => item.parentId == null).map(parent => ({
  ...parent,
  children: props.categories.filter(item => item.parentId === parent.id),
})))
const selectedLabel = computed(() => {
  const selected = props.categories.find(item => String(item.id) === String(props.modelValue))
  if (!selected) return ''
  if (selected.parentId == null) return selected.name
  const parent = props.categories.find(item => item.id === selected.parentId)
  return parent ? `${parent.name} / ${selected.name}` : selected.name
})

function filterNode(value, data) {
  if (!value) return true
  const parent = data.parentId == null ? '' : props.categories.find(item => item.id === data.parentId)?.name || ''
  return `${parent} ${data.name}`.toLowerCase().includes(value.trim().toLowerCase())
}
function selectNode(data) {
  emit('update:modelValue', data.id)
  visible.value = false
  keyword.value = ''
}
function clearSelection() {
  emit('update:modelValue', null)
}
watch(visible, open => {
  if (!open) keyword.value = ''
})
</script>

<style scoped>
.category-trigger { display:flex; width:100%; min-height:34px; align-items:center; gap:8px; padding:0 11px; border:1px solid #d5e0e1; border-radius:5px; background:#fff; color:#344b50; text-align:left; cursor:pointer; }
.category-trigger:hover,.category-trigger.is-open { border-color:#087f8c; }
.trigger-label { flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.is-placeholder .trigger-label,.trigger-arrow { color:#a8b2b5; }
.trigger-arrow { flex:0 0 auto; transition:transform .16s ease; }.is-open .trigger-arrow { transform:rotate(180deg); }
.clear-button { min-height:22px; height:22px; padding:0 2px; color:#a8b2b5; }.clear-button:hover { color:#6c7d82; }
.category-picker { max-height:340px; overflow:auto; }.category-search { position:sticky; z-index:1; top:0; margin-bottom:8px; background:#fff; }
.category-node { display:flex; min-width:0; align-items:center; gap:8px; }.category-node > span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.category-node small { margin-left:auto; padding-left:8px; color:#8b989f; font-size:11px; }
.category-picker :deep(.el-tree-node__content) { height:34px; border-radius:4px; }.category-picker :deep(.el-tree-node__content:hover) { background:#eff7f6; }.category-picker :deep(.el-tree--highlight-current .el-tree-node.is-current > .el-tree-node__content) { background:#e8f4f3; color:#087f8c; }
</style>
