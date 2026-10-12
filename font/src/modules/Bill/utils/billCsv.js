const mappingAliases = {
  occurredAt: ['交易时间', '交易日期', '记账时间', '记账日期', '发生时间', '发生日期', '支付时间', '付款时间', '订单时间', '日期', '时间', 'transactiondate', 'datetime', 'date'],
  amount: ['交易金额', '收支金额', '发生金额', '金额', '交易额', '支出金额', '收入金额', 'amount', 'transactionamount'],
  direction: ['交易类型', '收支类型', '收支方向', '资金方向', '类型', '方向', '收支', 'direction', 'type'],
  fromAccount: ['出账账号', '出账账户', '付款账号', '付款账户id', '付款账户', '支出账户', '转出账户', '付款银行卡', '付款卡号', '付款方账户', 'fromaccount', 'payeraccount'],
  toAccount: ['入账账户id', '收款账户id', '入账账号id', '收款账号id', '转入账户id', '入账账户', '收款账户', '转入账户', '收款银行卡', '收款卡号', 'toaccount', 'payeeaccount'],
  counterparty: ['交易对象', '交易对方', '对方名称', '对方户名', '对方账户', '商户名称', '商户', '收款方', '付款方', '入账账号', '收款账号', 'counterparty', 'merchant'],
  description: ['摘要', '商品名称', '商品', '商品信息', '消费说明', '交易说明', '用途', '描述', '备注说明', '入账账号', 'description', 'subject'],
  category: ['分类id', '分类', '账单分类', '交易分类', '类别', 'category'],
  remark: ['备注', '附言', '备注信息', 'remark'],
}

export function decodeBillCsv(buffer) {
  const bytes = new Uint8Array(buffer)
  if (bytes[0] === 0xff && bytes[1] === 0xfe) return new TextDecoder('utf-16le').decode(bytes)
  if (bytes[0] === 0xfe && bytes[1] === 0xff) return new TextDecoder('utf-16be').decode(bytes)
  try { return new TextDecoder('utf-8', { fatal: true }).decode(bytes) }
  catch { return new TextDecoder('gb18030').decode(bytes) }
}

export function parseBillCsv(text) {
  const source = String(text || '').replace(/^\uFEFF/, '')
  const delimiter = detectDelimiter(source)
  const parsed = parseRows(source, delimiter).filter(row => row.some(value => value.trim()))
  if (!parsed.length) throw new Error('CSV 文件为空')
  const headers = parsed[0].map(value => value.trim())
  if (headers.every(value => !value)) throw new Error('未识别到表头，请确认文件首行为列名')
  return { headers, rows: parsed.slice(1), delimiter }
}

export function suggestBillCsvMapping(headers) {
  const used = new Set()
  const mapping = {}
  for (const [field, aliases] of Object.entries(mappingAliases)) {
    const normalizedAliases = aliases.map(normalizeHeader)
    let index = headers.findIndex((header, i) => !used.has(i) && normalizedAliases.includes(normalizeHeader(header)))
    if (index < 0) index = headers.findIndex((header, i) => !used.has(i) && normalizedAliases.some(alias => alias.length > 2 && normalizeHeader(header).includes(alias)))
    mapping[field] = index
    if (index >= 0) used.add(index)
  }
  return mapping
}

export function normalizeBillDate(value) {
  const text = String(value || '').trim()
  if (!text) return ''
  const match = text.match(/^(\d{4})[./年-](\d{1,2})[./月-](\d{1,2})(?:日)?(?:\s+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?$/)
  if (match) {
    const [, year, month, day, hour = '0', minute = '0', second = '0'] = match
    return `${year}-${pad(month)}-${pad(day)}T${pad(hour)}:${pad(minute)}:${pad(second)}`
  }
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(text)) return text.length === 16 ? `${text}:00` : text.slice(0, 19)
  return ''
}

export function parseBillAmount(value) {
  let text = String(value ?? '').trim().replace(/[\s,￥¥元]/g, '')
  if (!text) return null
  const negative = /^\(.*\)$/.test(text)
  text = text.replace(/[()]/g, '')
  const amount = Number(text)
  if (!Number.isFinite(amount)) return null
  return negative ? -Math.abs(amount) : amount
}

export function resolveBillAccount(value, accounts) {
  const source = normalizeAccount(value)
  if (!source) return { id: null, ambiguous: false }
  const exact = accounts.filter(account => {
    const name = normalizeAccount(account.name)
    const code = normalizeAccount(account.code)
    const sourceDigits = source.replace(/\D/g, '')
    const codeDigits = code.replace(/\D/g, '')
    return source === name || source === normalizeAccount(account.id) || (code && (source === code || source.endsWith(code)))
      || (sourceDigits.length >= 4 && codeDigits.length >= 4 && sourceDigits.slice(-4) === codeDigits.slice(-4))
  })
  if (exact.length === 1) return { id: Number(exact[0].id), ambiguous: false }
  if (exact.length > 1) return { id: null, ambiguous: true }
  const relaxed = accounts.filter(account => {
    const name = normalizeAccount(account.name)
    return name.length > 1 && (source.startsWith(name) || name.startsWith(source))
  })
  return relaxed.length === 1 ? { id: Number(relaxed[0].id), ambiguous: false } : { id: null, ambiguous: relaxed.length > 1 }
}

export function resolveBillCategory(value, categories) {
  const name = normalizeHeader(value)
  if (!name) return { id: null, ambiguous: false }
  if (/^\d+$/.test(name)) {
    const byId = categories.find(category => String(category.id) === name)
    if (byId) return { id: Number(byId.id), ambiguous: false }
  }
  const matches = categories.filter(category => normalizeHeader(category.name) === name)
  return matches.length === 1
    ? { id: Number(matches[0].id), ambiguous: false }
    : { id: null, ambiguous: matches.length > 1 }
}

export function createBillCsvTemplate() {
  const headers = ['交易时间', '方向', '金额', '付款账户ID', '付款账户', '收款账户ID', '收款账户', '交易对象', '分类ID', '分类', '摘要', '备注']
  return `\uFEFF${headers.join(',')}\r\n`
}

function detectDelimiter(text) {
  const line = text.split(/\r?\n/, 1)[0] || ''
  const counts = [',', '\t', ';'].map(delimiter => countOutsideQuotes(line, delimiter))
  return [',', '\t', ';'][counts.indexOf(Math.max(...counts))]
}

function countOutsideQuotes(text, delimiter) {
  let count = 0
  let quoted = false
  for (let i = 0; i < text.length; i++) {
    if (text[i] === '"' && text[i + 1] === '"' && quoted) i++
    else if (text[i] === '"') quoted = !quoted
    else if (!quoted && text[i] === delimiter) count++
  }
  return count
}

function parseRows(text, delimiter) {
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
    else if (char === delimiter) { row.push(field); field = '' }
    else if (char === '\n') { row.push(field.replace(/\r$/, '')); rows.push(row); row = []; field = '' }
    else field += char
  }
  if (quoted) throw new Error('CSV 文件包含未闭合的引号')
  if (field || row.length) { row.push(field.replace(/\r$/, '')); rows.push(row) }
  return rows
}

function normalizeHeader(value) { return String(value || '').toLowerCase().replace(/[\s_\-()[\]（）【】.／/]/g, '') }
function normalizeAccount(value) { return String(value || '').toLowerCase().replace(/\s/g, '').replace(/[()（）\[\]【】\-]/g, '') }
function pad(value) { return String(value).padStart(2, '0') }
