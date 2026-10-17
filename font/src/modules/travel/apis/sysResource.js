import { mapRequest } from '@/utils/request.js'

const regionBoundaryCache = new Map()
const pendingBoundaryRequests = new Map()

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
