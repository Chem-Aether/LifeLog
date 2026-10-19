import { spiderRequest } from '@/utils/request.js'

export const getTrainTimetable = ({ trainNo }) => spiderRequest.get(`/trains/${encodeURIComponent(trainNo)}`, {
  timeout: 30000,
})
