export async function parseTrajectoryFile(file, separateTracks = false) {
  if (file.size > 50 * 1024 * 1024) throw new Error('文件不能超过 50 MB')
  if (!/\.(csv|gpx)$/i.test(file.name)) throw new Error('仅支持 CSV 或 GPX 文件')
  const records = file.name.toLowerCase().endsWith('.csv')
    ? [await parseCsvFile(file)]
    : await parseGpxFile(file, separateTracks)
  records.forEach(validateSegments)
  return records
}

export function downloadTrajectoryCsvTemplate() {
  const header = 'dataTime,locType,longitude,latitude,heading,accuracy,speed,distance,isBackForeground,stepType,altitude\r\n'
  const blob = new Blob(['\uFEFF', header], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = 'GPS轨迹导入模板.csv'
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

async function parseCsvFile(file) {
  const rows = parseCsv(await readCsvText(file))
  if (rows.length < 2) throw new Error('CSV 文件没有轨迹采样点')
  const headers = rows[0].map(value => value.trim().replace(/^\uFEFF/, ''))
  const columns = new Map(headers.map((name, index) => [name, index]))
  const missing = ['dataTime', 'longitude', 'latitude'].filter(name => !columns.has(name))
  if (missing.length) throw new Error(`CSV 缺少必需列：${missing.join('、')}`)
  const valueAt = (row, name) => String(row[columns.get(name)] ?? '').trim()
  const points = []
  let segmentNo = 1
  let previousTime = null
  for (let rowIndex = 1; rowIndex < rows.length; rowIndex++) {
    const row = rows[rowIndex]
    if (row.every(value => !String(value || '').trim())) continue
    const line = rowIndex + 1
    const rawTime = valueAt(row, 'dataTime')
    const timestamp = Number(rawTime)
    const date = Number.isFinite(timestamp)
      ? new Date(timestamp < 1e12 ? timestamp * 1000 : timestamp)
      : new Date(rawTime.includes('T') ? rawTime : rawTime.replace(' ', 'T'))
    if (!rawTime || !Number.isFinite(date.getTime())) throw new Error(`第 ${line} 行 dataTime 无效`)
    if (previousTime != null && date.getTime() < previousTime) throw new Error(`第 ${line} 行时间早于上一采样点`)
    if (previousTime != null && date.getTime() - previousTime > 30 * 60 * 1000) segmentNo++
    previousTime = date.getTime()
    const longitude = Number(valueAt(row, 'longitude'))
    const latitude = Number(valueAt(row, 'latitude'))
    if (!Number.isFinite(longitude) || Math.abs(longitude) > 180 || !Number.isFinite(latitude) || Math.abs(latitude) > 90) {
      throw new Error(`第 ${line} 行经纬度无效`)
    }
    const optionalNumber = name => {
      const raw = valueAt(row, name)
      if (!raw) return null
      const value = Number(raw)
      if (!Number.isFinite(value)) throw new Error(`第 ${line} 行${name}无效`)
      return value
    }
    points.push({ sequence: points.length + 1, segmentNo, recordedAt: localDateTime(date), longitude, latitude,
      altitudeM: optionalNumber('altitude') ?? 0, accuracyM: optionalNumber('accuracy'), speedMps: optionalNumber('speed') })
  }
  if (points.length < 2 || points.length > 200000) throw new Error('CSV 轨迹点数量须为 2 到 200,000')
  return { name: file.name.replace(/\.csv$/i, ''), activityType: 'walk', note: '', points }
}

async function readCsvText(file) {
  const bytes = await file.arrayBuffer()
  try { return new TextDecoder('utf-8', { fatal: true }).decode(bytes).replace(/^\uFEFF/, '') }
  catch { return new TextDecoder('gb18030').decode(bytes) }
}

function parseCsv(text) {
  const rows = []
  let row = [], field = '', quoted = false
  for (let index = 0; index < text.length; index++) {
    const char = text[index]
    if (quoted) {
      if (char === '"' && text[index + 1] === '"') { field += '"'; index++ }
      else if (char === '"') quoted = false
      else field += char
    } else if (char === '"' && !field) quoted = true
    else if (char === ',') { row.push(field); field = '' }
    else if (char === '\n' || char === '\r') {
      if (char === '\r' && text[index + 1] === '\n') index++
      row.push(field); rows.push(row); row = []; field = ''
    } else field += char
  }
  if (quoted) throw new Error('CSV 文件存在未闭合的引号')
  if (row.length || field) { row.push(field); rows.push(row) }
  return rows
}

async function parseGpxFile(file, separateTracks) {
  const documentXml = new DOMParser().parseFromString(await file.text(), 'application/xml')
  if (documentXml.querySelector('parsererror') || documentXml.documentElement.localName !== 'gpx') throw new Error('GPX 文件格式无效')
  const tracks = Array.from(documentXml.getElementsByTagNameNS('*', 'trk'))
  if (separateTracks && tracks.length > 1) return tracks.map((track, index) => parseGpxTrack(documentXml, file, [track], index, tracks.length))
  return [parseGpxTrack(documentXml, file, tracks, 0, tracks.length)]
}

function parseGpxTrack(documentXml, file, tracks, trackIndex, trackCount) {
  const segments = tracks.flatMap(track => Array.from(track.children).filter(node => node.localName === 'trkseg')
    .map(node => Array.from(node.children).filter(child => child.localName === 'trkpt'))).filter(segment => segment.length >= 2)
  if (!segments.length && !tracks.length) {
    const route = Array.from(documentXml.getElementsByTagNameNS('*', 'rtept'))
    if (route.length) segments.push(route)
  }
  const rawNodes = segments.flat()
  if (rawNodes.length < 2 || rawNodes.length > 200000) throw new Error('GPX 轨迹点数量须为 2 到 200,000')
  const times = rawNodes.map(node => new Date(node.getElementsByTagNameNS('*', 'time')[0]?.textContent || ''))
  const firstTimedIndex = times.findIndex(date => Number.isFinite(date.getTime()))
  let lastTime = new Date((firstTimedIndex < 0 ? Date.now() : times[firstTimedIndex].getTime()) - 5000 * (firstTimedIndex + 1))
  let index = 0
  const points = []
  segments.forEach((nodes, segmentIndex) => nodes.forEach(node => {
    const longitude = Number(node.getAttribute('lon')), latitude = Number(node.getAttribute('lat'))
    const parsedTime = times[index++]
    if (!Number.isFinite(longitude) || !Number.isFinite(latitude) || Math.abs(longitude) > 180 || Math.abs(latitude) > 90) return
    const elevation = Number(node.getElementsByTagNameNS('*', 'ele')[0]?.textContent)
    const recorded = Number.isFinite(parsedTime.getTime()) && parsedTime >= lastTime ? parsedTime : new Date(lastTime.getTime() + 5000)
    lastTime = recorded
    points.push({ sequence: points.length + 1, segmentNo: segmentIndex + 1, recordedAt: localDateTime(recorded), longitude, latitude,
      altitudeM: Number.isFinite(elevation) ? elevation : 0, accuracyM: null, speedMps: null })
  }))
  if (points.length < 2) throw new Error('GPX 中没有可用的轨迹点')
  const textOf = (parent, name) => parent?.getElementsByTagNameNS('*', name)[0]?.textContent?.trim() || ''
  const track = tracks[0]
  const baseName = file.name.replace(/\.gpx$/i, '')
  const metadataName = textOf(documentXml, 'name')
  return {
    name: textOf(track, 'name') || (trackCount > 1 ? `${metadataName || baseName} - ${trackIndex + 1}` : metadataName || baseName),
    note: textOf(track, 'desc') || textOf(documentXml, 'desc'),
    activityType: parseActivityType(textOf(track, 'type')),
    points,
    hasMissingValues: times.some(date => !Number.isFinite(date.getTime()))
      || rawNodes.some(node => !Number.isFinite(Number(node.getElementsByTagNameNS('*', 'ele')[0]?.textContent)))
  }
}

function validateSegments(record) {
  const counts = new Map()
  record.points.forEach(point => counts.set(point.segmentNo, (counts.get(point.segmentNo) || 0) + 1))
  if ([...counts.values()].some(count => count < 2)) throw new Error('每个轨迹分段至少需要两个采样点')
}

function parseActivityType(value) {
  const type = String(value || '').trim().toLowerCase()
  if (['walk', 'bike', 'drive', 'transit', 'train', 'plane', 'ship', 'other'].includes(type)) return type
  if (/步行|徒步|walking|walk|hike|跑步|跑|running|run/.test(type)) return 'walk'
  if (/骑行|骑|自行车|cycling|cycle|bike/.test(type)) return 'bike'
  if (/飞机|航空|flight|plane|air/.test(type)) return 'plane'
  if (/船舶|轮船|渡船|ship|boat/.test(type)) return 'ship'
  if (/火车|铁路|高铁|train|rail/.test(type)) return 'train'
  if (/公共交通|公交|地铁|transit|bus|subway/.test(type)) return 'transit'
  if (/驾车|打车|出租|网约车|驾驶|driving|drive|car|taxi/.test(type)) return 'drive'
  return type ? 'other' : ''
}

function localDateTime(value) {
  const date = new Date(value), pad = part => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
