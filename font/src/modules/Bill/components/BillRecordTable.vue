<template>
  <el-table
    :data="rows"
    :max-height="maxHeight || undefined"
    stripe
    row-key="id"
    empty-text="暂无账单记录"
    class="bill-record-table"
    @sort-change="$emit('sort-change', $event)"
  >
    <el-table-column prop="amount" label="金额" width="130" align="right" sortable="custom">
      <template #default="{ row }">
        <strong :class="row.direction">{{ amountSign(row.direction) }}{{ money(row.amount) }}</strong>
      </template>
    </el-table-column>
    <el-table-column prop="description" label="描述" min-width="180" sortable="custom" show-overflow-tooltip>
      <template #default="{ row }">{{ row.description || '-' }}</template>
    </el-table-column>
    <el-table-column prop="category" label="分类" width="150" sortable="custom">
      <template #default="{ row }"><span class="category-cell"><CategoryIcon :value="row.categoryIcon" />{{ categoryLabel(row) }}</span></template>
    </el-table-column>
    <el-table-column prop="direction" label="收支" width="100" sortable="custom">
      <template #default="{ row }">
        <el-tag :class="`tag-${row.direction}`" effect="plain">{{ directionName(row.direction) }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="occurredAt" label="日期" width="165" sortable="custom">
      <template #default="{ row }">{{ formatDate(row.occurredAt) }}</template>
    </el-table-column>
    <el-table-column prop="payer" label="付款账户" min-width="145" sortable="custom">
      <template #default="{ row }">
        <el-tooltip :content="payer(row)" placement="top" :show-after="300">
          <span class="account-name">{{ payer(row) }}</span>
        </el-tooltip>
      </template>
    </el-table-column>
    <el-table-column prop="payee" label="收款账户" min-width="145" sortable="custom">
      <template #default="{ row }">
        <el-tooltip :content="payee(row)" placement="top" :show-after="300">
          <span class="account-name">{{ payee(row) }}</span>
        </el-tooltip>
      </template>
    </el-table-column>
    <el-table-column v-if="showActions" label="操作" width="120" fixed="right" align="center">
      <template #default="{ row }">
        <el-button link type="primary" @click="$emit('edit', row)">编辑</el-button>
        <el-button link type="danger" @click="$emit('delete', row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import CategoryIcon from './CategoryIcon.vue'

const props = defineProps({
  rows: { type: Array, default: () => [] },
  accounts: { type: Array, default: () => [] },
  showActions: { type: Boolean, default: false },
  maxHeight: { type: String, default: null },
})

defineEmits(['edit', 'delete', 'sort-change'])

const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const amountSign = direction => direction === 'income' ? '+' : direction === 'expense' ? '-' : ''
const directionName = value => ({ income: '收入', expense: '支出', transfer: '内部转账' })[value] || '未知'
const formatDate = value => value ? String(value).replace('T', ' ').slice(0, 16) : '-'
const categoryLabel = row => row.category != null && row.category !== '' ? row.category : '未分类'

function accountCode(row, side) {
  const rowCode = side === 'from' ? row.fromAccountCode : row.toAccountCode
  const accountId = side === 'from' ? row.fromAccountId : row.toAccountId
  return rowCode || props.accounts.find(account => String(account.id) === String(accountId))?.code
}

function accountLabel(name, code) {
  if (!name) return ''
  const suffix = String(code || '').replace(/[\s-]/g, '').slice(-4)
  return suffix ? `${name}（${suffix}）` : name
}

const payer = row => accountLabel(row.fromAccountName, accountCode(row, 'from')) || row.counterparty || '外部'
const payee = row => accountLabel(row.toAccountName, accountCode(row, 'to')) || row.counterparty || '外部'
</script>
