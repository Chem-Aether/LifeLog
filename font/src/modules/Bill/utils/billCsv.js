const headers = ['交易时间', '方向', '金额', '付款账户ID', '付款账户', '收款账户ID', '收款账户', '交易对象', '分类ID', '分类', '摘要', '备注']

export function createBillCsvTemplate() {
  return `\uFEFF${headers.join(',')}\r\n`
}

export function parseBillCsv(text, accounts, categories) {
  const rows = parseRows(text.replace(/^\uFEFF/, ''))
  if (!rows.length) throw new Error('CSV 文件为空')
  const columns = rows[0].map(value => value.trim())
  const positions = Object.fromEntries(headers.map(header => [header, columns.indexOf(header)]))
  for (const required of ['交易时间', '方向', '金额']) {
    if (positions[required] < 0) throw new Error(`缺少必要列：${required}`)
  }
  const errors = []
  const records = []
  const accountById = new Map(accounts.map(account => [String(account.id), account]))
  const accountByName = new Map()
  accounts.forEach(account => {
    const matches = accountByName.get(account.name) || []
    matches.push(account)
    accountByName.set(account.name, matches)
  })
  const categoryById = new Map(categories.map(category => [String(category.id), category]))
  const categoryByName = new Map()
  categories.forEach(category => {
    const matches = categoryByName.get(category.name) || []
    matches.push(category)
    categoryByName.set(category.name, matches)
  })

  rows.slice(1).forEach((cells, index) => {
    const line = index + 2
    if (cells.every(value => !value.trim())) return
    const get = name => positions[name] < 0 ? '' : (cells[positions[name]] || '').trim()
    const directionValue = get('方向')
    const direction = ({ 收入: 'income', 支出: 'expense', 转账: 'transfer', 内部转账: 'transfer', income: 'income', expense: 'expense', transfer: 'transfer' })[directionValue]
    const occurredAt = get('交易时间').replace(' ', 'T')
    const amount = Number(get('金额').replace(/[,￥¥]/g, ''))
    const resolveAccount = (idHeader, nameHeader) => {
      const id = get(idHeader)
      if (id) return accountById.has(id) ? Number(id) : null
      const name = get(nameHeader)
      if (!name) return null
      const match = accountByName.get(name)
      return match?.length === 1 ? Number(match[0].id) : null
    }
    const fromAccountId = resolveAccount('付款账户ID', '付款账户')
    const toAccountId = resolveAccount('收款账户ID', '收款账户')
    const categoryIdText = get('分类ID')
    const categoryName = get('分类')
    const namedCategories = categoryByName.get(categoryName)
    const categoryId = categoryIdText
      ? (categoryById.has(categoryIdText) ? Number(categoryIdText) : null)
      : (namedCategories?.length === 1 ? Number(namedCategories[0].id) : null)
    const rowErrors = []
    if (!/^\d{4}-\d{1,2}-\d{1,2}T\d{1,2}:\d{2}(:\d{2})?$/.test(occurredAt)) rowErrors.push('交易时间格式应为 YYYY-MM-DD HH:mm:ss')
    if (!Number.isFinite(amount) || amount <= 0) rowErrors.push('金额必须为大于零的数字')
    if (!direction) rowErrors.push('方向需为收入、支出或转账')
    if (direction !== 'income' && !fromAccountId) rowErrors.push('付款账户无效或无法唯一匹配')
    if (direction !== 'expense' && !toAccountId) rowErrors.push('收款账户无效或无法唯一匹配')
    if (direction === 'income' && fromAccountId) rowErrors.push('收入记录不应填写付款账户')
    if (direction === 'expense' && toAccountId) rowErrors.push('支出记录不应填写收款账户')
    if (fromAccountId && toAccountId && fromAccountId === toAccountId) rowErrors.push('付款和收款账户不能相同')
    if ((categoryName || categoryIdText) && !categoryId) rowErrors.push('分类无效或名称不唯一，请填写有效分类ID')
    if (rowErrors.length) {
      errors.push(`第${line}行：${rowErrors.join('；')}`)
      return
    }
    records.push({
      occurredAt: occurredAt.length === 16 ? `${occurredAt}:00` : occurredAt,
      amount,
      fromAccountId,
      toAccountId,
      counterparty: get('交易对象'),
      description: get('摘要'),
      categoryId,
      remark: get('备注'),
    })
  })
  if (!records.length && !errors.length) errors.push('文件中没有可导入的数据行')
  return { records, errors }
}

function parseRows(text) {
  const rows = []
  let row = []
  let field = ''
  let quoted = false
  for (let i = 0; i < text.length; i++) {
    const char = text[i]
    if (quoted) {
      if (char === '"' && text[i + 1] === '"') { field += '"'; i++ }
      else if (char === '"') quoted = false
      else field += char
    } else if (char === '"') quoted = true
    else if (char === ',') { row.push(field); field = '' }
    else if (char === '\n') {
      row.push(field.replace(/\r$/, ''))
      rows.push(row)
      row = []
      field = ''
    } else field += char
  }
  if (quoted) throw new Error('CSV 文件包含未闭合的引号')
  if (field || row.length) { row.push(field.replace(/\r$/, '')); rows.push(row) }
  return rows
}
