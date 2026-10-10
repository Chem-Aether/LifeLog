import { travelRequest as request } from '@/utils/request.js'


const yearParams = (year) => year === 'all' ? {} : { year }

export const getTickets = (mode, year = 'all') => {
    return request({
        url: '/travel/tickets',
        method: 'get',
        params: { mode, ...yearParams(year) }
    })
}


export const getTicketSummary = (mode, year = 'all') => {
    return request({
        url: '/travel/tickets/summary',
        method: 'get',
        params: { mode, ...yearParams(year) }
    })
}

export const getFootprints = (year = 'all') => request({
    url: '/travel/footprints',
    method: 'get',
    params: yearParams(year)
})

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

export const exportTravelRecord = (type, id, format) => request({
  url: '/travel/export',
  method: 'get',
  params: { type, id, ...(format ? { format } : {}) },
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
