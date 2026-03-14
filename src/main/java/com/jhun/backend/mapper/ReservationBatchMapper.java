package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.ReservationBatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预约批次数据访问接口。
 */
@Mapper
public interface ReservationBatchMapper extends BaseMapper<ReservationBatch> {
}
