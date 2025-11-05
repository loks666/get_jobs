package com.getjobs.worker.service;

import com.getjobs.worker.dto.JobProgressMessage;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 求职平台服务接口
 * 定义所有求职平台服务的统一接口
 */
public interface JobPlatformService {

    /**
     * 执行投递任务
     *
     * @param progressCallback 进度回调函数，用于实时推送任务进度
     */
    void executeDelivery(Consumer<JobProgressMessage> progressCallback);

    /**
     * 停止当前投递任务
     */
    void stopDelivery();

    /**
     * 获取当前任务状态
     *
     * @return 状态信息，包含isRunning、platform等字段
     */
    Map<String, Object> getStatus();

    /**
     * 获取平台名称
     *
     * @return 平台标识符 (boss, liepin, job51, zhilian)
     */
    String getPlatformName();

    /**
     * 检查是否正在运行
     *
     * @return true表示任务正在执行中
     */
    boolean isRunning();
}
