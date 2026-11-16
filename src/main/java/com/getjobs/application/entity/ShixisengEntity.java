package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shixiseng_data")
public class ShixisengEntity {
    @TableId
    private String jobId;           // uuid e.g. inn_co4bscna08aq
    private String jobTitle;
    private String jobLink;
    private String jobSalaryText;
    private String jobArea;
    private String jobEduReq;
    private String jobExpReq;       // 每周天数/实习月数
    private String compName;
    private String compIndustry;
    private String compScale;
    private Integer delivered;      // 0 未投递, 1 已投递
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
