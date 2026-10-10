import { travelRequest as request } from '@/utils/request.js'

// 列表（支持分页/条件查询）
export const getTrainList = (params = {}) =>
    request({
        url: '/travel/trains',
        method: 'GET',
        params
    }).then(res => {
    // 永远分页：后端应返回 Result{ data: IPage{ records, total, ... } }
    const payload = (res && Object.prototype.hasOwnProperty.call(res, 'data')) ? res.data : res

    if (payload && Array.isArray(payload.records)) {
        return { data: payload.records, page: payload }
    }

    // 兜底：避免页面 map 报错
    return { data: [], page: { total: 0, records: [] } }
    })

// 获取车票基础信息（不含途经站明细）
export const getTrainTicket = (trainId) => {
    return request({
        url: `/travel/trains/${trainId}`,
        method: 'GET'
    })
}

// 获取某个车票的途经站明细（展开时用）
export const addTrainTicket = (data) => {
    return request({
        url: '/travel/trains',
        method: 'POST',
        data
    })
}

// 更新车票（支持同时保存途经站最终态）
export const updateTrainTicket = (data) => {
    return request({
        url: `/travel/trains/${data.trainId}`,
        method: 'PUT',
        data
    })
}

export const deleteTrainTicket = (trainId) => {
    return request({
        url: `/travel/trains/${trainId}`,
        method: 'DELETE'
    })
}

export const deleteTrainTickets = (ids) => request({
    url: '/travel/trains/batch',
    method: 'DELETE',
    data: ids
})

export const sampleTrainRoute = (data) => {
    return request({
        url: '/travel/trains/route/sample',
        method: 'POST',
        data
    })
}
