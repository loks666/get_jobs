package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shixiseng_config")
public class ShixisengConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 搜索关键词 JSON 数组 */
    private String keywords;

    /** 目标城市（如 广州、全国） */
    private String city;

    /** 是否启用 AI 岗位匹配过滤 (1=启用, 0=关闭) */
    private Integer enableAi;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
