package com.lifelog.travel.flight;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Objects;

/** 处理航班记录的筛选、校验和持久化。 */
@Service
public class FlightRecordService {
    private final FlightRecordMapper mapper;

    /**
     * 创建航班业务服务。
     * @param mapper 航班数据访问组件
     */
    public FlightRecordService(FlightRecordMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 按筛选参数分页查询航班记录。
     * @param dto 查询条件
     * @return 航班分页对象
     */
    public Page<FlightRecord> list(FlightRecordQueryDTO dto) {
        LambdaQueryWrapper<FlightRecord> query = new LambdaQueryWrapper<>();
        query.like(StringUtils.hasText(dto.getFlightNo()), FlightRecord::getFlightNo, dto.getFlightNo());
        query.like(StringUtils.hasText(dto.getDepartureIcao()), FlightRecord::getDepartureIcao, dto.getDepartureIcao());
        query.like(StringUtils.hasText(dto.getArrivalIcao()), FlightRecord::getArrivalIcao, dto.getArrivalIcao());
        query.ge(dto.getDepartureTimeStart() != null, FlightRecord::getDepartureTime, dto.getDepartureTimeStart());
        query.le(dto.getDepartureTimeEnd() != null, FlightRecord::getDepartureTime, dto.getDepartureTimeEnd());
        query.orderByDesc(FlightRecord::getDepartureTime);
        long page = dto.getPageNum() == null || dto.getPageNum() < 1 ? 1 : dto.getPageNum();
        long size = dto.getPageSize() == null || dto.getPageSize() < 1 ? 10 : Math.min(dto.getPageSize(), 100);
        return mapper.selectPage(new Page<>(page, size), query);
    }

    /** 按 ID 查询航班记录。 */
    public FlightRecord get(Long id) {
        FlightRecord record = mapper.selectById(id);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "航班记录不存在");
        }
        return record;
    }

    /**
     * 校验并创建航班记录。
     * @param record 航班数据
     * @return 已保存的航班记录
     */
    @Transactional
    public FlightRecord save(FlightRecord record) {
        validate(record);
        normalizeStopovers(record);
        record.setFlightId(null);
        mapper.insert(record);
        return record;
    }

    /**
     * 校验并更新航班记录。
     * @param id 航班记录 ID
     * @param record 更新数据
     * @return 更新后的航班记录
     * @throws ResponseStatusException 记录不存在或内容无效时抛出
     */
    @Transactional
    public FlightRecord update(Long id, FlightRecord record) {
        if (mapper.selectById(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "航班记录不存在");
        validate(record);
        normalizeStopovers(record);
        record.setFlightId(id);
        mapper.updateById(record);
        return mapper.selectById(id);
    }

    /**
     * 删除航班记录。
     * @param id 航班记录 ID
     * @throws ResponseStatusException 记录不存在时抛出
     */
    @Transactional
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "航班记录不存在");
        }
    }

    /**
     * 批量删除有效航班记录。
     * @param ids 航班记录 ID 列表
     * @return 删除记录数
     */
    @Transactional
    public int deleteBatch(List<Long> ids) {
        List<Long> validIds = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        if (validIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效的航班记录");
        }
        return mapper.delete(new LambdaQueryWrapper<FlightRecord>().in(FlightRecord::getFlightId, validIds));
    }

    /** 确保经停机场列表始终为非空集合。 */
    private static void normalizeStopovers(FlightRecord record) {
        if (record.getStopovers() == null) record.setStopovers(List.of());
    }

    /**
     * 校验航班号、起降机场和时间先后关系。
     * @param record 待校验的航班记录
     * @throws ResponseStatusException 必填信息缺失或时间不合法时抛出
     */
    private void validate(FlightRecord record) {
        if (record == null || !StringUtils.hasText(record.getFlightNo()) || !StringUtils.hasText(record.getDepartureIcao())
                || !StringUtils.hasText(record.getArrivalIcao()) || record.getDepartureTime() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "航班号、起降机场和起飞时间不能为空");
        if (record.getArrivalTime() != null && record.getArrivalTime().isBefore(record.getDepartureTime()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "到达时间不能早于起飞时间");
    }
}
