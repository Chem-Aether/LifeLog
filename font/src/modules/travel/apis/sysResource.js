import { mapRequest } from '@/utils/request.js'

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

export const getRegionBoundaries = ids => mapRequest.post('/api/regions/boundaries', { ids })
