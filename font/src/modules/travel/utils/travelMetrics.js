const minutesBetween = (departure, arrival) => {
  if (!departure || !arrival) return 0
  const value = Math.floor((new Date(arrival) - new Date(departure)) / 60000)
  return Number.isFinite(value) ? Math.max(0, value) : 0
}

const duration = minutes => `${Math.floor(minutes / 60)}h${minutes % 60}m`
const distribution = counts => [...counts.entries()]
  .map(([name, value]) => ({ name, value }))
  .sort((left, right) => right.value - left.value || left.name.localeCompare(right.name, 'zh-CN'))

function summary(metrics, counts) {
  return { metrics, distribution: distribution(counts) }
}

export function summarizeFlights(records) {
  const airports = new Set()
  const airlines = new Map()
  let distance = 0
  let minutes = 0
  for (const record of records) {
    if (record.departureIcao) airports.add(record.departureIcao)
    if (record.arrivalIcao) airports.add(record.arrivalIcao)
    if (record.distanceKm != null) distance += Number(record.distanceKm) || 0
    minutes += minutesBetween(record.departureTime, record.arrivalTime)
    const airline = String(record.flightNo || '').slice(0, 2).toUpperCase() || '其他'
    airlines.set(airline, (airlines.get(airline) || 0) + 1)
  }
  return summary([
    { label: '航程', value: `${Math.round(distance)} km` }, { label: '航时', value: duration(minutes) },
    { label: '航次', value: records.length }, { label: '航点', value: airports.size }
  ], airlines)
}

export function summarizeTrains(records) {
  const stations = new Set()
  const trainTypes = new Map()
  let distance = 0
  let minutes = 0
  for (const record of records) {
    for (const name of [record.startStationName, record.endStationName, ...(record.waypoints || []).map(item => item.stationName)]) {
      if (name) stations.add(String(name).trim())
    }
    if (record.mileageKm != null) distance += Number(record.mileageKm) || 0
    minutes += minutesBetween(record.departureTime, record.arrivalTime)
    const type = record.trainType || String(record.trainNo || '').slice(0, 1).toUpperCase() || '其他'
    trainTypes.set(type, (trainTypes.get(type) || 0) + 1)
  }
  return summary([
    { label: '里程', value: `${Math.round(distance)} km` }, { label: '时长', value: duration(minutes) },
    { label: '次数', value: records.length }, { label: '车站', value: stations.size }
  ], trainTypes)
}
