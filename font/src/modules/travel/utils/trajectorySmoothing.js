const MAX_POINTS = 4000
const MAX_CORNER_STEP_M = 150
const METERS_PER_DEGREE = 111195

function nearby(a, b) {
  if (!Number.isFinite(a?.[0]) || !Number.isFinite(a?.[1]) || !Number.isFinite(b?.[0]) || !Number.isFinite(b?.[1])) return false
  const latitude = (a[1] + b[1]) * Math.PI / 360
  const dx = (a[0] - b[0]) * Math.cos(latitude) * METERS_PER_DEGREE
  const dy = (a[1] - b[1]) * METERS_PER_DEGREE
  return dx * dx + dy * dy <= MAX_CORNER_STEP_M * MAX_CORNER_STEP_M
}

function between(a, b) {
  return a.map((value, index) => Number.isFinite(b[index]) ? value * 0.75 + b[index] * 0.25 : value)
}

export function smoothTrajectoryLine(coordinates) {
  if (!Array.isArray(coordinates) || coordinates.length < 3 || coordinates.length > MAX_POINTS) return coordinates
  const result = [coordinates[0]]
  for (let index = 1; index < coordinates.length - 1; index++) {
    const previous = coordinates[index - 1]
    const current = coordinates[index]
    const next = coordinates[index + 1]
    if (nearby(previous, current) && nearby(current, next)) {
      result.push(between(current, previous), between(current, next))
    } else result.push(current)
  }
  result.push(coordinates.at(-1))
  return result
}

export function smoothTrajectoryGeometry(geometry) {
  if (geometry?.type === 'LineString') {
    return { ...geometry, coordinates: smoothTrajectoryLine(geometry.coordinates) }
  }
  if (geometry?.type === 'MultiLineString') {
    return { ...geometry, coordinates: geometry.coordinates.map(smoothTrajectoryLine) }
  }
  return geometry
}
