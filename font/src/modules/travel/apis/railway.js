import { mapRequest } from '@/utils/request.js'

export const searchTrainStations = (keyword, limit = 20) => mapRequest.get('/api/railway/stations/search', {
  params: { keyword, limit },
})

export const sampleRailwayRoute = async ({ trainCode, stations }) => {
  const response = await mapRequest.post('/api/railway/routes/sample', { trainCode, stations }, {
    timeout: 120000,
  })
  const result = response?.data || {}
  return {
    ...result,
    routeGeoJson: result.geometry ? JSON.stringify(result.geometry) : '',
    source: 'railway-network',
  }
}
