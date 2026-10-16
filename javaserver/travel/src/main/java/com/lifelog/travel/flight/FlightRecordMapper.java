package com.lifelog.travel.flight;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 提供航班记录的 MyBatis-Plus 数据访问操作。 */
@Mapper
public interface FlightRecordMapper extends BaseMapper<FlightRecord> {
}
