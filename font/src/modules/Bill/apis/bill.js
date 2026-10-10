import { billRequest } from '@/utils/request.js'

const request = config => billRequest(config).then(result => result.data)

export const getAccounts = () => request({ url: '/bill/accounts', method: 'get' })
export const createAccount = data => request({ url: '/bill/accounts', method: 'post', data })
export const updateAccount = (id, data) => request({ url: `/bill/accounts/${id}`, method: 'put', data })
export const deleteAccount = id => request({ url: `/bill/accounts/${id}`, method: 'delete' })
export const getBillCategories = () => request({ url: '/bill/categories', method: 'get' })
export const createBillCategory = data => request({ url: '/bill/categories', method: 'post', data })
export const updateBillCategory = (id, data) => request({ url: `/bill/categories/${id}`, method: 'put', data })
export const deleteBillCategory = id => request({ url: `/bill/categories/${id}`, method: 'delete' })
export const getAccountRecords = (id, params) => request({ url: `/bill/accounts/${id}/records`, method: 'get', params })

export const getBillRecords = params => request({ url: '/bill/records', method: 'get', params })
export const importBillRecords = records => request({ url: '/bill/records/import', method: 'post', data: { records }, timeout: 120000 })
export const exportBillRecords = params => billRequest({ url: '/bill/records/export', method: 'get', params, responseType: 'blob', fullResponse: true, timeout: 120000 })
export const createBillRecord = data => request({ url: '/bill/records', method: 'post', data })
export const updateBillRecord = (id, data) => request({ url: `/bill/records/${id}`, method: 'put', data })
export const deleteBillRecord = id => request({ url: `/bill/records/${id}`, method: 'delete' })
export const getBillSummary = params => request({ url: '/bill/statistics/summary', method: 'get', params })
export const getAccountStatistics = params => request({ url: '/bill/statistics/accounts', method: 'get', params })
export const getCategoryStatistics = params => request({ url: '/bill/statistics/categories', method: 'get', params })
export const getBillTimeline = year => request({ url: '/bill/statistics/timeline', method: 'get', params: { year } })
