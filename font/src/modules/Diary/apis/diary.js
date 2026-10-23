import { diaryRequest } from '@/utils/request.js'
import { resolveMediaUrl } from '@/utils/media.js'

const request = config => diaryRequest(config).then(result => result.data)

const contentType = url => ({ jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp', gif: 'image/gif', mp4: 'video/mp4', m4v: 'video/mp4', mov: 'video/quicktime', webm: 'video/webm', '3gp': 'video/3gpp', avi: 'video/x-msvideo' })[url?.split('?')[0]?.split('.').pop()?.toLowerCase()] || 'application/octet-stream'
const attachMediaTypes = entry => ({ ...entry, mediaTypes: Object.fromEntries((entry.mediaUrls || []).map(url => [url, contentType(url)])) })

export const getDiaryEntries = async (params, { includeMediaTypes = true } = {}) => {
  const result = await request({ url: '/diary/entries', method: 'get', params })
  if (!includeMediaTypes) return result
  return { ...result, items: (result.items || []).map(attachMediaTypes) }
}
export const getDiaryEntry = async id => {
  const entry = await request({ url: `/diary/entries/${id}`, method: 'get' })
  return attachMediaTypes(entry)
}
export const createDiaryEntry = data => request({ url: '/diary/entries', method: 'post', data })
export const updateDiaryEntry = (id, data) => request({ url: `/diary/entries/${id}`, method: 'put', data })
export const deleteDiaryEntry = id => request({ url: `/diary/entries/${id}`, method: 'delete' })
export const getDiaryYears = () => request({ url: '/diary/years', method: 'get' })
export const getDiarySummary = year => request({ url: '/diary/summary', method: 'get', params: { year } })

export const uploadDiaryMedia = file => {
  const data = new FormData()
  data.append('file', file)
  data.append('category', 'diary')
  return diaryRequest({ url: '/api/media', method: 'post', data, timeout: 0 })
}

export const uploadDiaryVideo = file => {
  const data = new FormData()
  data.append('file', file)
  data.append('category', 'diary')
  data.append('media_type', 'video')
  return diaryRequest({ url: '/api/media', method: 'post', data, timeout: 0 })
}


export const deleteDiaryMedia = url => diaryRequest({ url: '/api/media', method: 'delete', params: { url } })
export const diaryMediaUrl = resolveMediaUrl
