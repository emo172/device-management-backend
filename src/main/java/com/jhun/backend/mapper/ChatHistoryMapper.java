package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.ChatHistory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AI 对话历史数据访问接口。
 */
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

    /**
     * 查询某个用户的全部 AI 历史。
     * <p>
     * 输入条件仅接受当前业务侧已确认的用户 ID，返回对象为该用户全部历史实体列表；
     * 调用方必须自行保证这是“当前登录用户本人”的 ID，不能把该方法当作跨用户管理查询接口使用。
     *
     * @param userId 待查询用户的主键 ID
     * @return 指定用户名下的 AI 历史实体列表
     */
    List<ChatHistory> findByUserId(@Param("userId") String userId);

    /**
     * 查询用户拥有的一条 AI 历史详情。
     * <p>
     * 该方法通过 `userId + historyId` 双条件返回单条历史实体，专门用于本人历史隔离校验；
     * 若记录不属于该用户或记录不存在，应返回空结果而不是放宽到只按历史 ID 查询。
     *
     * @param userId 当前登录用户的主键 ID
     * @param historyId 待查询历史记录的主键 ID
     * @return 当前用户拥有的历史实体，不存在时返回空
     */
    ChatHistory findOwnedById(@Param("userId") String userId, @Param("historyId") String historyId);
}
