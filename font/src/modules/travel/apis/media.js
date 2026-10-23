import { travelRequest } from '@/utils/request.js'
import { resolveMediaUrl } from '@/utils/media.js'

export const uploadTravelMedia = file => {
  const data = new FormData()
  data.append('file', file)
  data.append('category', 'travel')
  return travelRequest({ url: '/api/media', method: 'post', data, timeout: 0 })
}

export const deleteTravelMedia = url => travelRequest({
  url: '/api/media',
  method: 'delete',
  params: { url },
})

export const mediaUrl = resolveMediaUrl
