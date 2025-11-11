package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * zhilian_data 表实体
 */
@Data
@TableName("zhilian_data")
public class ZhilianJobDataEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("job_id")
    private String jobId;

    @TableField("job_title")
    private String jobTitle;

    @TableField("job_link")
    private String jobLink;

    @TableField("salary")
    private String salary;

    @TableField("location")
    private String location;

    @TableField("experience")
    private String experience;

    @TableField("degree")
    private String degree;

    @TableField("company_name")
    private String companyName;

    @TableField("delivery_status")
    private String deliveryStatus; // 未投递 / 已投递 / 已过滤 / 投递失败

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}