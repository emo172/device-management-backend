package com.jhun.backend.mapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 基础设施占位 Mapper。
 * <p>
 * 当前仓库尚未落地具体业务 Mapper 时，通过该接口维持 MyBatis Mapper 扫描链路可用，
 * 避免基础设施阶段出现“未扫描到任何 Mapper”的启动告警；后续接入真实数据访问对象后可逐步替换。
 */
@Mapper
public interface InfrastructureMapper {
}
