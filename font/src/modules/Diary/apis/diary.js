import { diaryRequest, mediaRequest, MEDIA_BASE_URL } from '@/utils/request.js'

const request = config => diaryRequest(config).then(result => result.data)

export const getDiaryMediaTypes = async mediaIds => {
  const ids = [...new Set(mediaIds || [])]
  const types = {}
  for (let index = 0; index < ids.length; index += 200) {
    const result = await mediaRequest({
      url: '/api/media/types', method: 'get', params: { ids: ids.slice(index, index + 200).join(',') },
    })
    Object.assign(types, result.data ?? result)
  }
  return types
}

const attachMediaTypes = async entry => ({ ...entry, mediaTypes: await getDiaryMediaTypes(entry.mediaIds) })

export const getDiaryEntries = async (params, { includeMediaTypes = true } = {}) => {
  const result = await request({ url: '/diary/entries', method: 'get', params })
  if (!includeMediaTypes) return result
  let mediaTypes = {}
  try { mediaTypes = await getDiaryMediaTypes((result.items || []).flatMap(entry => entry.mediaIds || [])) }
  catch { /* Keep diary text available if the media service is offline. */ }
  return { ...result, items: (result.items || []).map(entry => ({ ...entry, mediaTypes })) }
}
export const getDiaryEntry = async id => {
  const entry = await request({ url: `/diary/entries/${id}`, method: 'get' })
  try { return await attachMediaTypes(entry) }
  catch { return { ...entry, mediaTypes: {} } }
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
  return mediaRequest({ url: '/api/media', method: 'post', data, timeout: 0 })
}

export const uploadDiaryVideo = file => {
  const data = new FormData()
  data.append('file', file)
  data.append('category', 'diary')
  data.append('media_type', 'video')
  return mediaRequest({ url: '/api/media', method: 'post', data, timeout: 0 })
}


export const diaryMediaUrl = mediaId => mediaId
  ? `${MEDIA_BASE_URL}/media/${encodeURIComponent(mediaId)}`
  : ''
