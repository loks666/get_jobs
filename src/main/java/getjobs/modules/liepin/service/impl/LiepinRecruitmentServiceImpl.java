package getjobs.modules.liepin.service.impl;

import com.microsoft.playwright.Page;
import getjobs.common.dto.ConfigDTO;
import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.modules.boss.dto.JobDTO;
import getjobs.service.RecruitmentService;
import getjobs.utils.PlaywrightUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class LiepinRecruitmentServiceImpl implements RecruitmentService {

    private static final String HOME_URL = RecruitmentPlatformEnum.LIEPIN.getHomeUrl();
    private static final String SEARCH_JOB_URL = "https://www.liepin.com/zhaopin/?";

    @Override
    public RecruitmentPlatformEnum getPlatform() {
        return RecruitmentPlatformEnum.LIEPIN;
    }

    @Override
    public boolean login(ConfigDTO config) {
        log.info("开始猎聘登录检查");
        try {
            Page page = PlaywrightUtil.getPageObject();
            page.navigate(HOME_URL);
            // 这里的登录检查逻辑需要根据猎聘的页面元素进行调整
            // if (LiepinElementLocators.isLoginRequired(page)) {
            //     log.info("需要登录，开始登录流程");
            //     return performLogin();
            // } else {
            //     log.info("猎聘已登录");
            //     return true;
            // }
            return performLogin(); // 暂时总是执行登录
        } catch (Exception e) {
            log.error("猎聘登录失败", e);
            return false;
        }
    }

    @Override
    public List<JobDTO> collectJobs(ConfigDTO config) {
        log.info("开始猎聘岗位采集");
        List<JobDTO> allJobDTOS = new ArrayList<>();
        try {
            for (String cityCode : config.getCityCodeCodes()) {
                for (String keyword : config.getKeywordsList()) {
                    List<JobDTO> jobDTOS = collectJobsByCity(cityCode, keyword, config);
                    allJobDTOS.addAll(jobDTOS);
                }
            }
            log.info("猎聘岗位采集完成，共采集{}个岗位", allJobDTOS.size());
            return allJobDTOS;
        } catch (Exception e) {
            log.error("猎聘岗位采集失败", e);
            return allJobDTOS;
        }
    }

    @Override
    public List<JobDTO> collectRecommendJobs(ConfigDTO config) {
        return List.of();
    }

    @Override
    public List<JobDTO> filterJobs(List<JobDTO> jobDTOS, ConfigDTO config) {
        return jobDTOS; // 暂时不过滤
    }

    @Override
    public int deliverJobs(List<JobDTO> jobDTOS, ConfigDTO config) {
        log.info("开始执行猎聘岗位投递操作，待投递岗位数量: {}", jobDTOS.size());
        AtomicInteger successCount = new AtomicInteger(0);
        try (Page jobPage = PlaywrightUtil.getPageObject().context().newPage()) {
            for (JobDTO jobDTO : jobDTOS) {
                try {
                    log.info("正在投递岗位: {}", jobDTO.getJobName());
                    jobPage.navigate(jobDTO.getHref());
                    jobPage.waitForLoadState();
                    // 投递逻辑需要根据猎聘的页面元素进行调整
                    // if (LiepinElementLocators.clickApplyButton(jobPage)) {
                    //     log.info("岗位投递成功: {}", jobDTO.getJobName());
                    //     successCount.getAndIncrement();
                    // } else {
                    //     log.warn("岗位投递失败或已投递: {}", jobDTO.getJobName());
                    // }
                    PlaywrightUtil.randomSleep(3, 5);
                } catch (Exception e) {
                    log.error("投递岗位 {} 时发生异常: {}", jobDTO.getJobName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("猎聘岗位投递过程中发生严重错误", e);
        }
        log.info("猎聘岗位投递完成，成功投递 {} 个岗位", successCount.get());
        return successCount.get();
    }

    @Override
    public boolean isDeliveryLimitReached() {
        return false;
    }

    @Override
    public void saveData(String dataPath) {
        log.info("猎聘数据保存功能待实现");
    }

    private List<JobDTO> collectJobsByCity(String cityCode, String keyword, ConfigDTO config) {
        String searchUrl = buildSearchUrl(cityCode, keyword, config);
        log.info("开始采集，城市: {}，关键词: {}，URL: {}", cityCode, keyword, searchUrl);
        List<JobDTO> jobDTOS = new ArrayList<>();
        Page page = PlaywrightUtil.getPageObject();
        try {
            page.navigate(searchUrl);
            page.waitForLoadState();
            // 采集逻辑需要根据猎聘的页面元素进行调整
            // jobDTOS.addAll(LiepinElementLocators.extractJobs(page));
        } catch (Exception e) {
            log.error("采集城市: {}, 关键词: {} 的职位失败", cityCode, keyword, e);
        }
        log.info("城市: {}, 关键词: {} 的职位采集完成，共{}个职位", cityCode, keyword, jobDTOS.size());
        return jobDTOS;
    }

    private String buildSearchUrl(String cityCode, String keyword, ConfigDTO config) {
        StringBuilder url = new StringBuilder(SEARCH_JOB_URL);
        try {
            url.append("key=").append(URLEncoder.encode(keyword, StandardCharsets.UTF_8));
            if (cityCode != null && !cityCode.trim().isEmpty()) {
                url.append("&city=").append(cityCode);
            }
            // 其他参数...
        } catch (Exception e) {
            log.error("构建搜索URL失败", e);
        }
        return url.toString();
    }

    private boolean performLogin() {
        Page page = PlaywrightUtil.getPageObject();
        try {
            page.navigate(HOME_URL);
            log.info("等待用户手动登录...");
            // 登录逻辑需要根据猎聘的页面元素进行调整
            // while (!LiepinElementLocators.isUserLoggedIn(page)) {
            //     PlaywrightUtil.sleep(2);
            // }
            log.info("登录成功");
            return true;
        } catch (Exception e) {
            log.error("登录过程中发生错误", e);
            return false;
        }
    }
}
