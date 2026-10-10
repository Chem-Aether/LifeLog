export function maskAccountNumber(value) {
  const number = String(value ?? '').replace(/[\s-]/g, '')
  if (!number) return ''
  if (/^\d{9,}$/.test(number)) {
    return `${number.slice(0, 4)}${'*'.repeat(Math.max(4, number.length - 8))}${number.slice(-4)}`
  }
  return number.slice(-4)
}

export function accountNumberLabel(value) {
  const masked = maskAccountNumber(value)
  if (!masked) return '未设置卡号'
  const number = String(value ?? '').replace(/[\s-]/g, '')
  return /^\d{9,}$/.test(number) ? masked : `尾号 ${masked}`
}
