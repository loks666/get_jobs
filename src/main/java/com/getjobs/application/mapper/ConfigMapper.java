package com.getjobs.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.getjobs.application.entity.ConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置Mapper接口
 */
@Mapper
public interface ConfigMapper extends BaseMapper<ConfigEntity> {
}
