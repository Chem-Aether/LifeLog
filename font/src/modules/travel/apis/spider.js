import { spiderRequest } from '@/utils/request.js'

export const getTrainRouteStations = ({ trainNo, date }) => spiderRequest.get('/stations', {
  params: {
    train_code: trainNo,
    date: date.replaceAll('-', ''),
  },
  timeout: 30000,
})
