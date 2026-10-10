export const defaultSeatTypes = Object.freeze([
  '二等座', '二等包座', '一等座', '优选一等座', '商务座', '特等座',
  '高级软卧', '软卧', '动卧', '一等卧', '软座', '硬座', '无座', '硬卧', '二等卧'
])

export const trainTypes = Object.freeze([
  { prefix: 'G', label: '高速动车', className: 'type-g' },
  { prefix: 'D', label: '动车', className: 'type-d' },
  { prefix: 'C', label: '城际动车', className: 'type-c' },
  { prefix: 'K', label: '快速旅客列车', className: 'type-k' },
  { prefix: 'Z', label: '直达特快列车', className: 'type-z' },
  { prefix: 'T', label: '特快旅客列车', className: 'type-t' },
  { prefix: 'L', label: '临时旅客列车', className: 'type-l' },
  { prefix: 'S', label: '市郊列车', className: 'type-s' },
  { prefix: 'Y', label: '旅游列车', className: 'type-y' },
  { prefix: 'F', label: '折返列车', className: 'type-f' }
])
