package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("boss_option")
public class BossOptionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 选项类型：city, industry, experience, jobType, salary, degree, scale, stage
     */
    private String type;

    /**
     * 选项名称
     */
    private String name;

    /**
     * 选项代码
     */
    private String code;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
