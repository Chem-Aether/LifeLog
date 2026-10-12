import { MEDIA_BASE_URL, mediaRequest } from '@/utils/request.js'

export const uploadTravelMedia = file => {
  const data = new FormData()
  data.append('file', file)
  data.append('category', 'travel')
  return mediaRequest({ url: '/api/media', method: 'post', data, timeout: 0 })
}

export const deleteTravelMedia = mediaId => mediaRequest({
  url: `/api/media/${encodeURIComponent(mediaId)}`,
  method: 'delete',
})

export const mediaUrl = mediaId => mediaId
  ? `${MEDIA_BASE_URL}/media/${encodeURIComponent(mediaId)}`
  : ''
