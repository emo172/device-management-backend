package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.PromptTemplate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Prompt 模板数据访问接口。
 */
@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {

    /**
     * 查询模板列表，供系统管理员后台维护使用。
     * <p>
     * 返回对象为模板实体列表，调用方可直接映射为后台列表响应；
     * 该方法仅适用于模板管理和巡检场景，不应被当作前台用户可访问的公开查询接口。
     *
     * @return 全量模板实体列表
     */
    List<PromptTemplate> findAllTemplates();

    /**
     * 查询某个类型下全部启用模板。
     * <p>
     * 该方法专门服务于运行时冲突检测：正常情况下结果应为 0 或 1 条；
     * 若返回多条，则说明数据库已经存在脏数据，调用方必须 fail-fast，而不是静默挑选一条继续运行。
     *
     * @param type 模板类型
     * @return 指定类型下全部启用模板实体列表
     */
    List<PromptTemplate> findActiveByType(@Param("type") String type);

    /**
     * 按模板代码查询模板。
     * <p>
     * 该方法主要用于创建、更新前的唯一性校验，输入条件为模板代码，输出对象为命中的模板实体；
     * 若无记录则返回空，调用方需自行决定是创建新记录还是继续更新。
     *
     * @param code 模板代码
     * @return 对应代码的模板实体，不存在时返回空
     */
    PromptTemplate findByCode(@Param("code") String code);

    /**
     * 按模板名称查询模板。
     * <p>
     * 该方法与代码查询类似，主要用于后台写操作前的唯一性校验；
     * 使用限制是只能用于识别名称冲突，不能替代模板详情查询。
     *
     * @param name 模板名称
     * @return 对应名称的模板实体，不存在时返回空
     */
    PromptTemplate findByName(@Param("name") String name);
}
