import { mapRequest } from '@/utils/request.js'

export const searchTrainStations = (keyword, limit = 20) => mapRequest.get('/api/railway/stations/search', {
  params: { keyword, limit },
})
