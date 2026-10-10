package com.lifelog.travel.flight;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifelog.travel.flight.FlightRecordQueryDTO;
import com.lifelog.travel.flight.FlightRecord;
import com.lifelog.travel.flight.FlightRecordMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class FlightRecordService {
    private final FlightRecordMapper mapper;
    private final ObjectMapper objectMapper;
    public FlightRecordService(FlightRecordMapper mapper, ObjectMapper objectMapper) { this.mapper = mapper; this.objectMapper = objectMapper; }

    public Object list(FlightRecordQueryDTO dto) {
        LambdaQueryWrapper<FlightRecord> query = new LambdaQueryWrapper<>();
        query.like(StringUtils.hasText(dto.getFlightNo()), FlightRecord::getFlightNo, dto.getFlightNo());
        query.like(StringUtils.hasText(dto.getDepartureIcao()), FlightRecord::getDepartureIcao, dto.getDepartureIcao());
        query.like(StringUtils.hasText(dto.getArrivalIcao()), FlightRecord::getArrivalIcao, dto.getArrivalIcao());
        query.ge(dto.getDepartureTimeStart() != null, FlightRecord::getDepartureTime, dto.getDepartureTimeStart());
        query.le(dto.getDepartureTimeEnd() != null, FlightRecord::getDepartureTime, dto.getDepartureTimeEnd());
        query.orderByDesc(FlightRecord::getDepartureTime);
        long page = dto.getPageNum() == null || dto.getPageNum() < 1 ? 1 : dto.getPageNum();
        long size = dto.getPageSize() == null || dto.getPageSize() < 1 ? 10 : Math.min(dto.getPageSize(), 100);
        Page<FlightRecord> result = mapper.selectPage(new Page<>(page, size), query);
        result.getRecords().forEach(this::hydrate);
        return result;
    }

    public FlightRecord save(FlightRecord record) { validate(record); prepare(record); record.setFlightId(null); mapper.insert(record); return record; }
    public FlightRecord update(Long id, FlightRecord record) {
        if (mapper.selectById(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "航班记录不存在");
        validate(record); prepare(record); record.setFlightId(id); mapper.updateById(record); return hydrate(mapper.selectById(id));
    }
    public void delete(Long id) { if (mapper.deleteById(id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "航班记录不存在"); }
    public int deleteBatch(List<Long> ids) {
        List<Long> validIds = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        if (validIds.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效的航班记录");
        return mapper.delete(new LambdaQueryWrapper<FlightRecord>().in(FlightRecord::getFlightId, validIds));
    }

    public FlightRecord hydrate(FlightRecord record) {
        if (record == null) return null;
        try {
            record.setStopovers(objectMapper.readValue(record.getStopoversJson(), objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)));
            return record;
        } catch (JsonProcessingException e) { throw new IllegalStateException("经停机场数据损坏", e); }
    }
    private void prepare(FlightRecord record) {
        if (record.getStopovers() == null) record.setStopovers(List.of());
        try { record.setStopoversJson(objectMapper.writeValueAsString(record.getStopovers())); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("经停机场格式无效", e); }
    }
    private void validate(FlightRecord record) {
        if (record == null || !StringUtils.hasText(record.getFlightNo()) || !StringUtils.hasText(record.getDepartureIcao())
                || !StringUtils.hasText(record.getArrivalIcao()) || record.getDepartureTime() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "航班号、起降机场和起飞时间不能为空");
        if (record.getArrivalTime() != null && record.getArrivalTime().isBefore(record.getDepartureTime()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "到达时间不能早于起飞时间");
    }
}
