package getjobs.modules.zhilian.service.impl;

import com.microsoft.playwright.Page;
import getjobs.common.dto.ConfigDTO;
import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.modules.boss.dto.JobDTO;
import getjobs.modules.zhilian.service.ZhiLianElementLocators;
import getjobs.service.RecruitmentService;
import getjobs.utils.PlaywrightUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 智联招聘服务实现类
 * 
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
@Service
public class ZhiLianRecruitmentServiceImpl implements RecruitmentService {

    private static final String HOME_URL = RecruitmentPlatformEnum.ZHILIAN_ZHAOPIN.getHomeUrl();
    private static final String LOGIN_URL = "https://passport.zhaopin.com/login";
    private static final String SEARCH_JOB_URL = "https://www.zhaopin.com/sou?";
    // https://www.zhaopin.com/sou?el=4&we=0510&et=2&sl=15001,25000&jl=763&kw=java
    @Override
    public RecruitmentPlatformEnum getPlatform() {
        return RecruitmentPlatformEnum.ZHILIAN_ZHAOPIN;
    }

    @Override
    public boolean login(ConfigDTO config) {
        log.info("开始智联招聘登录检查");
        
        try {
            // 使用Playwright打开网站
            Page page = PlaywrightUtil.getPageObject();
            page.navigate(HOME_URL);
            
            // 检查是否需要登录
            if (ZhiLianElementLocators.isLoginRequired(page)) {
                log.info("需要登录，开始登录流程");
                return performLogin();
            } else {
                log.info("智联招聘已登录");
                return true;
            }
        } catch (Exception e) {
            log.error("智联招聘登录失败", e);
            return false;
        }
    }

    @Override
    public List<JobDTO> collectJobs(ConfigDTO config) {
        log.info("开始智联招聘岗位采集");
        List<JobDTO> allJobDTOS = new ArrayList<>();
        
        try {
            // 按城市和关键词搜索岗位
            for (String cityCode : config.getCityCodeCodes()) {
                for (String keyword : config.getKeywordsList()) {
                    List<JobDTO> jobDTOS = collectJobsByCity(cityCode, keyword, config);
                    allJobDTOS.addAll(jobDTOS);
                }
            }
            
            log.info("智联招聘岗位采集完成，共采集{}个岗位", allJobDTOS.size());
            return allJobDTOS;
        } catch (Exception e) {
            log.error("智联招聘岗位采集失败", e);
            return allJobDTOS;
        }
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
        log.info("开始保存智联招聘数据到路径: {}", dataPath);
        try {
            // TODO: 实现智联招聘数据保存逻辑
            // 这里需要保存智联招聘相关的数据，如登录状态、采集的岗位信息等
            
            log.info("智联招聘数据保存功能待实现");
            
        } catch (Exception e) {
            log.error("智联招聘数据保存失败", e);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 按城市采集岗位
     */
    private List<JobDTO> collectJobsByCity(String cityCode, String keyword, ConfigDTO config) {
        String searchUrl = buildSearchUrl(cityCode, keyword, config);
        log.info("开始采集，城市: {}，关键词: {}，URL: {}", cityCode, keyword, searchUrl);
        
        List<JobDTO> jobDTOS = new ArrayList<>();
        Page page = PlaywrightUtil.getPageObject();
        
        try {
            page.navigate(searchUrl);
            
            // 等待页面加载
            page.waitForLoadState();
            
            // 检查是否存在职位
            if (ZhiLianElementLocators.hasJobList(page)) {
                // 滚动加载所有职位
                int totalJobs = ZhiLianElementLocators.scrollLoadMoreJobs(page);
                log.info("页面职位加载完成，总计: {}", totalJobs);
                
                // 解析页面中的所有职位
                List<JobDTO> pageJobs = ZhiLianElementLocators.parseJobsFromPage(page);
                jobDTOS.addAll(pageJobs);
                
                // 点击所有职位卡片以获取更多详情
                ZhiLianElementLocators.clickAllJobCards(page, 1000);
                
                // 处理分页 - 采集更多页面的职位
                int currentPage = 1;
                int maxPages = 10; // 限制最大页数
                
                while (currentPage < maxPages && ZhiLianElementLocators.clickNextPage(page)) {
                    currentPage++;
                    log.info("正在采集第{}页", currentPage);
                    
                    // 等待页面加载
                    PlaywrightUtil.sleep(3);
                    
                    if (ZhiLianElementLocators.hasJobList(page)) {
                        // 滚动加载当前页职位
                        ZhiLianElementLocators.scrollLoadMoreJobs(page);
                        
                        // 解析当前页职位
                        List<JobDTO> currentPageJobs = ZhiLianElementLocators.parseJobsFromPage(page);
                        jobDTOS.addAll(currentPageJobs);
                        
                        // 点击职位卡片
                        ZhiLianElementLocators.clickAllJobCards(page, 1000);
                    }
                    
                    // 页面间隔
                    PlaywrightUtil.sleep(2);
                }
            } else {
                log.warn("未找到职位列表，可能需要登录或页面加载失败");
            }
            
        } catch (Exception e) {
            log.error("采集城市: {}, 关键词: {} 的职位失败", cityCode, keyword, e);
        }
        
        log.info("城市: {}, 关键词: {} 的职位采集完成，共{}个职位", cityCode, keyword, jobDTOS.size());
        return jobDTOS;
    }

    /**
     * 构建搜索URL
     * 基于URL: https://www.zhaopin.com/sou?el=4&we=0510&et=2&sl=15001,25000&jl=763&kw=java
     * 
     * @param cityCode 城市代码
     * @param keyword 关键词
     * @param config 配置信息
     * @return 完整的搜索URL
     */
    private String buildSearchUrl(String cityCode, String keyword, ConfigDTO config) {
        StringBuilder url = new StringBuilder(SEARCH_JOB_URL);
        
        try {
            // 必需参数
            // 关键词需要URL编码
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            url.append("kw=").append(encodedKeyword);
            
            // 城市参数 jl
            if (cityCode != null && !cityCode.trim().isEmpty()) {
                url.append("&jl=").append(cityCode);
            }
            
            // 学历要求 el (Education Level)
            // el=4 表示本科, 1=不限, 2=高中, 3=大专, 4=本科, 5=硕士, 6=博士
            if (config.getDegree() != null && !config.getDegree().trim().isEmpty()) {
                url.append("&el=").append(config.getDegree());
            }
            
            // 职位类型 et (Employment Type)  
            // et=2 表示全职, 1=全职, 2=兼职, 3=实习
            if (config.getJobType() != null && !config.getJobType().trim().isEmpty()) {
                url.append("&et=").append(config.getJobType());
            }
            
            // 公司性质 ct (Company Type)
            if (config.getCompanyType() != null && !config.getCompanyType().trim().isEmpty()) {
                url.append("&ct=").append(config.getCompanyType());
            }
            
            // 公司规模 cs (Company Size)
            if (config.getScale() != null && !config.getScale().trim().isEmpty()) {
                url.append("&cs=").append(config.getScale());
            }
            
            // 薪资范围 sl (Salary Level)
            // 格式: sl=15001,25000
            if (config.getSalary() != null && !config.getSalary().trim().isEmpty()) {
                url.append("&sl=").append(config.getSalary());
            }
            
            // 工作经验 we (Work Experience)
            // we=0510 表示5-10年
            if (config.getExperience() != null && !config.getExperience().trim().isEmpty()) {
                url.append("&we=").append(config.getExperience());
            }
            
            // 行业类型 in
            if (config.getIndustry() != null && !config.getIndustry().trim().isEmpty()) {
                url.append("&in=").append(URLEncoder.encode(config.getIndustry(), StandardCharsets.UTF_8));
            }
            
        } catch (Exception e) {
            log.error("构建搜索URL失败", e);
            // 返回基础URL
            return SEARCH_JOB_URL + "kw=" + keyword;
        }
        
        String finalUrl = url.toString();
        log.debug("构建的搜索URL: {}", finalUrl);
        return finalUrl;
    }

    /**
     * 执行登录操作
     */
    private boolean performLogin() {
        Page page = PlaywrightUtil.getPageObject();
        
        try {
            page.navigate(LOGIN_URL);
            PlaywrightUtil.sleep(3);
            
            log.info("等待用户手动登录...");
            log.info("请在浏览器中完成登录操作");
            
            boolean loginSuccess = false;


            while (!loginSuccess) {
                try {
                    // 检查登录状态
                    if (!ZhiLianElementLocators.isUserLoggedIn(page)) {
                        loginSuccess = true;
                        log.info("登录成功");
                    }
                } catch (Exception e) {
                    log.debug("登录状态检查异常: {}", e.getMessage());
                }
                
                PlaywrightUtil.sleep(2);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("登录过程中发生错误", e);
            return false;
        }
    }

    /**
     * 等待用户输入或超时
     */
    private boolean waitForUserInputOrTimeout(Scanner scanner) {
        long end = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < end) {
            try {
                if (System.in.available() > 0) {
                    scanner.nextLine();
                    return true;
                }
            } catch (IOException e) {
                // 忽略异常
            }
            PlaywrightUtil.sleep(1);
        }
        return false;
    }
}
