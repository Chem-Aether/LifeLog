import { mapRequest } from '@/utils/request.js'

export const searchAirports = (keyword, limit = 20) => mapRequest.get('/api/airports/search', {
  params: { keyword, limit },
})

export const forwardGeocode = (keyword, type = 'all', limit = 20) => mapRequest.get('/api/geocode/forward', {
  params: { q: keyword, type, limit },
})

export const reverseGeocode = (longitude, latitude) => mapRequest.get('/api/geocode/reverse', {
  params: { longitude, latitude },
})

export const getRegionBoundaries = codes => mapRequest.post('/api/regions/boundaries/by-codes', { codes })
