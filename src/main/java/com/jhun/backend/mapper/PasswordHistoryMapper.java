package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.PasswordHistory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 密码历史数据访问接口。
 */
@Mapper
public interface PasswordHistoryMapper extends BaseMapper<PasswordHistory> {

    /**
     * 查询用户的全部密码历史。
     *
     * @param userId 用户 ID
     * @return 历史密码列表
     */
    List<PasswordHistory> findByUserId(@Param("userId") String userId);
}
