const text = value => value == null ? '' : String(value)
const dateTime = value => value ? String(value).replace('T', ' ').slice(0, 16) : ''

function duration(departure, arrival) {
  if (!departure || !arrival) return ''
  const minutes = Math.max(0, Math.floor((new Date(arrival) - new Date(departure)) / 60000))
  return `${Math.floor(minutes / 60)}h${minutes % 60}m`
}

const details = entries => entries.map(([label, value]) => ({ label, value: text(value) }))

export function toFlightTrip(record, airports = {}) {
  const fromAirport = airports[record.departureIcao]
  const toAirport = airports[record.arrivalIcao]
  return {
    record,
    ticketType: 'flight',
    flightId: record.flightId,
    Number: record.flightNo,
    From: record.departureAirportName || fromAirport?.name || record.departureIcao,
    To: record.arrivalAirportName || toAirport?.name || record.arrivalIcao,
    departureTime: record.departureTime,
    time: duration(record.departureTime, record.arrivalTime),
    fromLongitude: fromAirport?.longitude,
    fromLatitude: fromAirport?.latitude,
    toLongitude: toAirport?.longitude,
    toLatitude: toAirport?.latitude,
    distanceKm: record.distanceKm,
    more: details([
      ['航空公司', record.airline], ['起飞时间', dateTime(record.departureTime)],
      ['出发航站楼', record.departureTerminal], ['出发机场代码', record.departureIcao],
      ['登机方式', record.boardingMethod], ['到达时间', dateTime(record.arrivalTime)],
      ['到达航站楼', record.arrivalTerminal], ['到达机场代码', record.arrivalIcao],
      ['下机方式', record.deboardingMethod], ['机型', record.aircraftType],
      ['飞机注册号', record.aircraftRegistration], ['经停机场', formatStopovers(record.stopovers)],
      ['座位', record.seatNo], ['里程', record.distanceKm == null ? '' : `${record.distanceKm} km`],
      ['备注', record.note]
    ])
  }
}

export function toTrainTrip(record) {
  const waypoints = Array.isArray(record.waypoints) ? record.waypoints : []
  const station = item => ({ name: item.stationName, longitude: item.longitude, latitude: item.latitude })
  const trainType = record.trainType || ''
  const trainModel = record.trainModel || ''
  return {
    record,
    ticketType: 'train',
    trainId: record.trainId,
    Number: record.trainNo,
    From: record.startStationName,
    To: record.endStationName,
    departureTime: record.departureTime,
    time: duration(record.departureTime, record.arrivalTime),
    routeGeoJson: record.routeGeoJson,
    routeStations: waypoints.map(station),
    more: details([
      ['发车时间', dateTime(record.departureTime)], ['到达时间', dateTime(record.arrivalTime)],
      ['列车类型', trainType], ['车型', trainModel],
      ['类型/车型', trainType && trainModel ? `${trainType} / ${trainModel}` : trainType || trainModel],
      ['始发站', record.originStationName], ['终到站', record.terminalStationName],
      ['里程', record.mileageKm == null ? '' : `${record.mileageKm} km`], ['席别', record.seatType],
      ['车厢', record.carriageNo], ['座位', record.seatNo], ['备注', record.note]
    ])
  }
}

function formatStopovers(stopovers) {
  if (!Array.isArray(stopovers)) return ''
  return stopovers.map(item => {
    const name = text(item?.name || item?.airportName)
    const icao = text(item?.icao)
    return name && icao ? `${name} (${icao})` : name || icao
  }).filter(Boolean).join('、')
}
