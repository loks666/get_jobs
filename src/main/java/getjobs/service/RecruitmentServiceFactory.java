package getjobs.service;

import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.modules.boss.service.impl.BossRecruitmentServiceImpl;
import getjobs.modules.job51.service.impl.Job51RecruitmentServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 招聘服务工厂类
 * 负责创建和管理不同招聘平台的服务实例
 * 支持Spring依赖注入
 * 
 * @author loks666
 *         项目链接: <a href=
 *         "https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
@Component
public class RecruitmentServiceFactory {

    private final Map<RecruitmentPlatformEnum, RecruitmentService> serviceMap = new HashMap<>();

    private final BossRecruitmentServiceImpl bossRecruitmentService;
    private final Job51RecruitmentServiceImpl job51RecruitmentService;

    public RecruitmentServiceFactory(BossRecruitmentServiceImpl bossRecruitmentService,
            Job51RecruitmentServiceImpl job51RecruitmentService) {
        this.bossRecruitmentService = bossRecruitmentService;
        this.job51RecruitmentService = job51RecruitmentService;
    }

    @PostConstruct
    public void initServices() {
        // 初始化各平台服务
        serviceMap.put(RecruitmentPlatformEnum.BOSS_ZHIPIN, bossRecruitmentService);
        serviceMap.put(RecruitmentPlatformEnum.JOB_51, job51RecruitmentService);
        // TODO: 后续可以添加其他平台的实现
        // serviceMap.put(RecruitmentPlatformEnum.ZHILIAN_ZHAOPIN,
        // zhilianRecruitmentService);

        log.info("招聘服务工厂初始化完成，支持平台: {}", serviceMap.keySet());
    }

    /**
     * 根据平台枚举获取对应的招聘服务
     * 
     * @param platform 招聘平台枚举
     * @return 招聘服务实例
     */
    public RecruitmentService getService(RecruitmentPlatformEnum platform) {
        RecruitmentService service = serviceMap.get(platform);
        if (service == null) {
            log.error("暂不支持的招聘平台: {}", platform.getPlatformName());
            throw new UnsupportedOperationException("暂不支持的招聘平台: " + platform.getPlatformName());
        }
        return service;
    }

    /**
     * 根据平台代码获取对应的招聘服务
     * 
     * @param platformCode 平台代码
     * @return 招聘服务实例
     */
    public RecruitmentService getService(String platformCode) {
        RecruitmentPlatformEnum platform = RecruitmentPlatformEnum.getByCode(platformCode);
        if (platform == null) {
            log.error("未找到平台代码对应的招聘平台: {}", platformCode);
            throw new IllegalArgumentException("未找到平台代码对应的招聘平台: " + platformCode);
        }
        return getService(platform);
    }

    /**
     * 获取所有支持的招聘平台
     * 
     * @return 支持的招聘平台数组
     */
    public RecruitmentPlatformEnum[] getSupportedPlatforms() {
        return serviceMap.keySet().toArray(new RecruitmentPlatformEnum[0]);
    }

    /**
     * 检查是否支持指定平台
     * 
     * @param platform 招聘平台枚举
     * @return 是否支持
     */
    public boolean isSupported(RecruitmentPlatformEnum platform) {
        return serviceMap.containsKey(platform);
    }

    /**
     * 检查是否支持指定平台代码
     * 
     * @param platformCode 平台代码
     * @return 是否支持
     */
    public boolean isSupported(String platformCode) {
        RecruitmentPlatformEnum platform = RecruitmentPlatformEnum.getByCode(platformCode);
        return platform != null && isSupported(platform);
    }
}
