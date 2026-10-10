import { diaryRequest, mediaRequest, MEDIA_BASE_URL } from '@/utils/request.js'

const request = config => diaryRequest(config).then(result => result.data)

export const getDiaryEntries = params => request({ url: '/diary/entries', method: 'get', params })
export const getDiaryEntry = id => request({ url: `/diary/entries/${id}`, method: 'get' })
export const createDiaryEntry = data => request({ url: '/diary/entries', method: 'post', data })
export const updateDiaryEntry = (id, data) => request({ url: `/diary/entries/${id}`, method: 'put', data })
export const deleteDiaryEntry = id => request({ url: `/diary/entries/${id}`, method: 'delete' })
export const getDiaryYears = () => request({ url: '/diary/years', method: 'get' })
export const getDiarySummary = year => request({ url: '/diary/summary', method: 'get', params: { year } })

export const uploadDiaryMedia = file => {
  const data = new FormData()
  data.append('file', file)
  data.append('category', 'diary')
  return mediaRequest({ url: '/api/media', method: 'post', data, timeout: 0 })
}

export const diaryMediaUrl = mediaId => mediaId
  ? `${MEDIA_BASE_URL}/media/${encodeURIComponent(mediaId)}`
  : ''
