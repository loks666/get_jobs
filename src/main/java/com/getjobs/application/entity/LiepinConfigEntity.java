package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("liepin_config")
public class LiepinConfigEntity {
    @TableId(type = IdType.AUTO)
    /** 主键ID */
    private Long id;

    /** 搜索关键词 */
    private String keywords;

    /** 城市（名称或代码） */
    private String city;

    /** 薪资代码或范围 */
    private String salaryCode;

    /** 招聘者活跃（pubTime 代码值：空=不限，1=一天内，3=三天内，7=一周内，30=一月内） */
    private String pubTime;

    /** 每批投递份数（0=不限） */
    private Integer batchSize;

    /** 每次投递间隔最小秒数 */
    private Integer applyIntervalMin;

    /** 每次投递间隔最大秒数 */
    private Integer applyIntervalMax;

    /** 批次完成后等待最小秒数 */
    private Integer batchWaitMin;

    /** 批次完成后等待最大秒数 */
    private Integer batchWaitMax;

    /** 批次完成后动作：continue=等待后继续，stop=停止任务 */
    private String batchAction;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
