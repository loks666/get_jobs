package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("job51_option")
public class Job51OptionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 选项类型：jobArea, salary 等 */
    private String type;

    /** 选项名称 */
    private String name;

    /** 选项代码 */
    private String code;

    /** 显示排序（数值越小越靠前） */
    private Integer sortOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}