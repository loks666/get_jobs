package com.getjobs.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.getjobs.application.entity.BlacklistEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Boss黑名单Mapper
 */
@Mapper
public interface BlacklistMapper extends BaseMapper<BlacklistEntity> {
}
