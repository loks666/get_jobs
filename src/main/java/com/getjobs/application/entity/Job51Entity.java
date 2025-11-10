package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 51job 岗位快照数据实体
 * 保存从 51job 搜索接口返回的有价值字段
 */
@Data
@TableName("job51_data")
public class Job51Entity {
    // 岗位字段
    @TableId
    private Long jobId;
    private String jobTitle;
    private String jobLink;
    private String jobSalaryText;
    private String jobArea;
    private String jobEduReq;
    private String jobExpReq;
    private String jobPublishTime;

    // 公司/HR字段
    private Long compId;
    private String compName;
    private String compIndustry;
    private String compScale;
    private String hrId;
    private String hrName;
    private String hrTitle;

    // 状态与时间戳
    private Integer delivered; // 0=未投递 1=已投递
    private String createTime;
    private String updateTime;
}