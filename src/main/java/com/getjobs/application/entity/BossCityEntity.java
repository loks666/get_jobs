package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("boss_city")
public class BossCityEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Integer code;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
