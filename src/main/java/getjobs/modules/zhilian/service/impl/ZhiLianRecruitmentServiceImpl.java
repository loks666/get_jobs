package getjobs.modules.zhilian.service.impl;

import getjobs.common.dto.ConfigDTO;
import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.modules.boss.dto.JobDTO;
import getjobs.service.RecruitmentService;

import java.util.List;

public class ZhiLianRecruitmentServiceImpl implements RecruitmentService {
    @Override
    public RecruitmentPlatformEnum getPlatform() {
        return RecruitmentPlatformEnum.ZHILIAN_ZHAOPIN;
    }

    @Override
    public boolean login(ConfigDTO config) {
        return false;
    }

    @Override
    public List<JobDTO> collectJobs(ConfigDTO config) {
        return List.of();
    }

    @Override
    public List<JobDTO> collectRecommendJobs(ConfigDTO config) {
        return List.of();
    }

    @Override
    public List<JobDTO> filterJobs(List<JobDTO> jobDTOS, ConfigDTO config) {
        return List.of();
    }

    @Override
    public int deliverJobs(List<JobDTO> jobDTOS, ConfigDTO config) {
        return 0;
    }

    @Override
    public boolean isDeliveryLimitReached() {
        return false;
    }

    @Override
    public void saveData(String dataPath) {

    }
}
