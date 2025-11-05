package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("boss_config")
public class BossConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer debugger;
    private Integer waitTime;
    private String keywords;
    private String cityCode;
    private String industry;
    private String jobType;
    private String experience;
    private String degree;
    private String salary;
    private String scale;
    private String stage;
    private String sayHi;
    private Integer expectedSalaryMin;
    private Integer expectedSalaryMax;
    private Integer enableAi;
    private Integer sendImgResume;
    private Integer filterDeadHr;
    private String deadStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
