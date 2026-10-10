import { travelRequest as request } from '@/utils/request.js'

// 获取机票列表
export const getFlightList = (params = {}) => {
    return request({
        url: '/travel/flights',
        method: 'GET',
        params: {
            // 分页
            pageNum: params.pageNum,
            pageSize: params.pageSize,

            // 查询条件
            flightNo: params.flightNo,
            // 用 ICAO 做查询条件（与表结构一致）
            departureIcao: params.departureIcao,
            arrivalIcao: params.arrivalIcao,

            // 时间范围
            // 后端按起飞时间范围查询
            departureTimeStart: params.departureTimeStart,
            departureTimeEnd: params.departureTimeEnd
        }
    }).then(res => {
    // 永远分页：后端应返回 Result{ data: IPage{ records, total, ... } }
    const payload = (res && Object.prototype.hasOwnProperty.call(res, 'data')) ? res.data : res

    if (payload && Array.isArray(payload.records)) {
        return { data: payload.records, page: payload }
    }

    return { data: [], page: { total: 0, records: [] } }
    })
}

// 公共字段
const flightTicketFields = (params) => ({
    flightNo: params.flightNo,
    airline: params.airline,
    aircraftRegistration: params.aircraftRegistration,
    aircraftType: params.aircraftType,

    // 表字段：中文名 + ICAO
    departureAirportName: params.departureAirportName,
    departureTerminal: params.departureTerminal,
    boardingMethod: params.boardingMethod,
    departureIcao: params.departureIcao,
    departureTime: params.departureTime,

    arrivalAirportName: params.arrivalAirportName,
    arrivalTerminal: params.arrivalTerminal,
    deboardingMethod: params.deboardingMethod,
    arrivalIcao: params.arrivalIcao,
    arrivalTime: params.arrivalTime,

    // 表字段：经停机场只存中文
    stopovers: params.stopovers || [],
    distanceKm: params.distanceKm,
    seatNo: params.seatNo,
    note: params.note
})

// 新增机票
export const insertFlightTicket = (params = {}) => {
    return request({
        url: '/travel/flights',
        method: 'POST',
        data: flightTicketFields(params)
    })
}

// 根据 ID 修改
export const updateFlightTicketById = (params = {}) => {
    return request({
        url: `/travel/flights/${params.flightId}`,
        method: 'PUT',
        data: {
            flightId: params.flightId,
            ...flightTicketFields(params)
        }
    })
}

// 根据 ID 修改
export const deleteFlightTicketById = (id) => {
    return request({
        url: `/travel/flights/${id}`,
        method: 'DELETE'
    })
}


export const deleteFlightTickets = (ids) => request({
    url: '/travel/flights/batch',
    method: 'DELETE',
    data: ids
})
