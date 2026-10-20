import { mapRequest } from '@/utils/request.js'

const regionBoundaryCache = new Map()
const pendingBoundaryRequests = new Map()
const regionPathCache = new Map()
const pendingPathRequests = new Map()

export const searchAirports = (keyword, limit = 20) => mapRequest.get('/api/airports/search', {
  params: { keyword, limit },
})

export const getAirportsByIcao = codes => mapRequest.post('/api/airports/by-codes', { codes })

export const searchPoiCatalog = (keyword, limit = 20) => mapRequest.get('/api/pois/search', {
  params: { q: keyword, limit },
})

export const reverseGeocode = (longitude, latitude) => mapRequest.get('/api/regions/reverse', {
  params: { longitude, latitude },
})

export async function getRegionBoundaries(ids) {
  const normalizedIds = [...new Set((ids || []).filter(id => id != null).map(String))]
  if (normalizedIds.length > 500) {
    const collections = await Promise.all(Array.from({ length: Math.ceil(normalizedIds.length / 500) }, (_, index) =>
      getRegionBoundaries(normalizedIds.slice(index * 500, (index + 1) * 500))))
    return { type: 'FeatureCollection', features: collections.flatMap(collection => collection.features) }
  }
  const missingIds = normalizedIds.filter(id => !regionBoundaryCache.has(id))
  const requestKey = [...missingIds].sort().join('|')

  if (missingIds.length && !pendingBoundaryRequests.has(requestKey)) {
    const request = mapRequest.post('/api/regions/boundaries', { ids: missingIds })
      .then(collection => {
        const featuresById = new Map((collection?.features || []).map(feature => [
          String(feature.properties?.id || feature.id || ''), feature
        ]))
        missingIds.forEach(id => regionBoundaryCache.set(id, featuresById.get(id) || null))
      })
      .finally(() => pendingBoundaryRequests.delete(requestKey))
    pendingBoundaryRequests.set(requestKey, request)
  }

  if (missingIds.length) await pendingBoundaryRequests.get(requestKey)
  return {
    type: 'FeatureCollection',
    features: normalizedIds.map(id => regionBoundaryCache.get(id)).filter(Boolean)
  }
}

export async function getRegionPaths(ids) {
  const normalizedIds = [...new Set((ids || []).filter(id => id != null).map(String))]
  if (normalizedIds.length > 500) {
    const batches = await Promise.all(Array.from({ length: Math.ceil(normalizedIds.length / 500) }, (_, index) =>
      getRegionPaths(normalizedIds.slice(index * 500, (index + 1) * 500))))
    return Object.assign({}, ...batches)
  }
  const missingIds = normalizedIds.filter(id => !regionPathCache.has(id))
  const requestKey = [...missingIds].sort().join('|')

  if (missingIds.length && !pendingPathRequests.has(requestKey)) {
    const request = mapRequest.post('/api/regions/paths', { ids: missingIds })
      .then(response => {
        const pathsById = response?.data || {}
        missingIds.forEach(id => regionPathCache.set(id, pathsById[id]?.path || []))
      })
      .finally(() => pendingPathRequests.delete(requestKey))
    pendingPathRequests.set(requestKey, request)
  }

  if (missingIds.length) await pendingPathRequests.get(requestKey)
  return Object.fromEntries(normalizedIds.map(id => [id, { path: regionPathCache.get(id) || [] }]))
}

export async function enrichFootprintRegions(records) {
  const items = Array.isArray(records) ? records : []
  const pathsById = await getRegionPaths(items.map(item => item.regionId))
  return items.map(item => ({
    ...item,
    regionPaths: pathsById[String(item.regionId)]?.path || [],
  }))
}
