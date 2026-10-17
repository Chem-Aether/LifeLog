import { travelRequest as request } from '@/utils/request.js'


export const getFootprints = (params = {}) => request({ url: '/travel/footprints', method: 'get', params })
export const getFootprintById = id => request({ url: `/travel/footprints/${id}`, method: 'get' })

export const exportTravelData = (type, format) => request({
  url: '/travel/export',
  method: 'get',
  params: type ? { type, ...(format ? { format } : {}) } : undefined,
  responseType: 'blob',
  timeout: 0,
  fullResponse: true
})

export const exportTravelTemplate = (type) => request({
  url: '/travel/export/template',
  method: 'get',
  params: { type },
  responseType: 'blob',
  timeout: 0,
  fullResponse: true
})

export const importTravelData = (file, type, mode = 'append') => {
  const data = new FormData()
  data.append('file', file)
  return request({ url: '/travel/import', method: 'post', params: { type, mode }, data, timeout: 0 })
}

export const addFootprint = (data) => request({
    url: '/travel/footprints',
    method: 'post',
    data
})

export const updateFootprint = (id, data) => request({
    url: `/travel/footprints/${id}`,
    method: 'put',
    data
})

export const deleteFootprint = (id) => request({
    url: `/travel/footprints/${id}`,
    method: 'delete'
})

export const deleteFootprints = (ids) => request({
    url: '/travel/footprints/batch',
    method: 'delete',
    data: ids
})
