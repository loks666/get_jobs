package getjobs.modules.job51.service.impl;

import com.microsoft.playwright.Page;
import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.common.dto.ConfigDTO;
import getjobs.modules.boss.dto.JobDTO;
import getjobs.modules.job51.service.Job51ElementLocators;
import getjobs.service.RecruitmentService;
import getjobs.utils.PlaywrightUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 51job招聘服务实现
 *
 * @author loks666
 * 项目链接: <a href=
 * "https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
@Service
public class Job51RecruitmentServiceImpl implements RecruitmentService {

    private static final String HOME_URL = RecruitmentPlatformEnum.JOB_51.getHomeUrl();
    private static final String LOGIN_URL = "https://login.51job.com/login.php";
    private static final String SEARCH_JOB_URL = "https://we.51job.com/pc/search?";

    @Override
    public RecruitmentPlatformEnum getPlatform() {
        return RecruitmentPlatformEnum.JOB_51;
    }

    @Override
    public boolean login(ConfigDTO config) {
        log.info("开始51job登录检查");

        try {
            // 使用Playwright打开网站
            Page page = PlaywrightUtil.getPageObject();
            page.navigate(HOME_URL);

            // 检查是否需要登录
            if (isLoginRequired()) {
                log.info("需要登录，开始登录流程");
                return login();
            } else {
                log.info("51job已登录");
                return true;
            }
        } catch (Exception e) {
            log.error("51job登录失败", e);
            return false;
        }
    }

    @Override
    public List<JobDTO> collectJobs(ConfigDTO config) {
        log.info("开始执行51job岗位采集操作");
        try {
            Page page = PlaywrightUtil.getPageObject();
            config.getCityCodeCodes().forEach(cityCode -> {
                // 构造完整的搜索条件
                String searchParams = buildSearchParams(cityCode, config);
                String fullUrl = SEARCH_JOB_URL + searchParams;
                log.info("访问搜索URL: {}", fullUrl);
                page.navigate(fullUrl);

                // 等待页面完全加载，确保分页元素可用
                waitForPageLoad(page);

                int pageNumber = 1;
                while (Job51ElementLocators.clickPageNumber(page, pageNumber)) {
                    log.info("正在处理第{}页数据", pageNumber);

                    // 添加3-5秒随机延迟，避免过快点击分页
                    PlaywrightUtil.randomSleep(3, 5);

                    // 在点击分页后也需要等待页面加载
                    waitForPageContentLoad(page);
                    pageNumber++;
                }
            });

            log.info("51job岗位采集功能待实现");
            return List.of(); // 暂时返回空列表，等待具体实现

        } catch (Exception e) {
            log.error("51job岗位采集失败", e);
            return List.of();
        }
    }

    @Override
    public List<JobDTO> collectRecommendJobs(ConfigDTO config) {
        // TODO: 实现51job推荐岗位采集逻辑
        return List.of();
    }

    @Override
    public List<JobDTO> filterJobs(List<JobDTO> jobDTOS, ConfigDTO config) {
        log.info("开始执行51job岗位过滤操作，待过滤岗位数量: {}", jobDTOS.size());
        try {
            // TODO: 实现51job岗位过滤逻辑
            // 这里需要根据51job的岗位特点和过滤规则来实现过滤
            // 可以参考Boss直聘的过滤逻辑，但需要适配51job的岗位结构

            log.info("51job岗位过滤功能待实现");
            return jobDTOS; // 暂时返回原始列表，等待具体实现

        } catch (Exception e) {
            log.error("51job岗位过滤失败", e);
            return jobDTOS;
        }
    }

    @Override
    public int deliverJobs(List<JobDTO> jobDTOS, ConfigDTO config) {
        log.info("开始执行51job岗位投递操作，待投递岗位数量: {}", jobDTOS.size());

        // 在新标签页中打开岗位详情
        try (Page jobPage = PlaywrightUtil.getPageObject().context().newPage()) {

            AtomicInteger count = new AtomicInteger();

            try {
                jobDTOS.forEach(jobDTO -> {

                    jobPage.navigate(jobDTO.getHref());
                    // 执行投递
                    Job51ElementLocators.clickApplyJobButton(jobPage);
                    count.getAndIncrement();

                    // 添加3-5秒随机延迟，避免投递过快
                    PlaywrightUtil.randomSleep(3, 5);

                });

                return count.get(); // 暂时返回0，等待具体实现

            } catch (Exception e) {
                log.error("51job岗位投递失败", e);
                return count.get();
            }
        }
    }

    @Override
    public boolean isDeliveryLimitReached() {
        // TODO: 实现51job投递限制检查逻辑
        // 这里需要检查是否达到51job的投递限制
        log.info("51job投递限制检查功能待实现");
        return false; // 暂时返回false，等待具体实现
    }

    @Override
    public void saveData(String dataPath) {
        log.info("开始保存51job数据到路径: {}", dataPath);
        try {
            // TODO: 实现51job数据保存逻辑
            // 这里需要保存51job相关的数据，如登录状态、采集的岗位信息等

            log.info("51job数据保存功能待实现");

        } catch (Exception e) {
            log.error("51job数据保存失败", e);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建完整的搜索参数字符串
     * 基于URL: https://we.51job.com/pc/search?jobArea=030200&keyword=java&salary=16000-20000&workYear=05&degree=04&companySize=03&companyType=04
     *
     * @param cityCode 城市代码
     * @param config   配置信息
     * @return 完整的搜索参数字符串
     */
    private String buildSearchParams(String cityCode, ConfigDTO config) {
        StringBuilder params = new StringBuilder();

        try {
            // 必需参数
            params.append("jobArea=").append(cityCode);

            // 关键词需要URL编码
            String keywords = config.getKeywords() != null ? config.getKeywords().trim() : "";
            params.append("&keyword=").append(URLEncoder.encode(keywords, StandardCharsets.UTF_8));

            // 可选参数 - 薪资范围 (如: 16000-20000)
            appendParameterIfNotEmpty(params, "salary", config.getSalary());

            // 可选参数 - 工作年限 (如: 05表示5年以上)
            appendParameterIfNotEmpty(params, "workYear", config.getExperience());

            // 可选参数 - 学历要求 (如: 04表示本科)
            appendParameterIfNotEmpty(params, "degree", config.getDegree());

            // 可选参数 - 公司规模 (如: 03表示100-499人)
            appendParameterIfNotEmpty(params, "companySize", config.getScale());

            // 可选参数 - 公司类型 (如: 04表示民营公司)
            appendParameterIfNotEmpty(params, "companyType", config.getCompanyType());

            // 其他可能的参数
            // industrytype - 行业类型
            appendParameterIfNotEmpty(params, "industrytype", config.getIndustry());

            // jobtype - 职位类型 
            appendParameterIfNotEmpty(params, "jobtype", config.getJobType());

        } catch (Exception e) {
            log.error("构建搜索参数失败", e);
            // 返回基础参数
            return "jobArea=" + cityCode + "&keyword=" +
                    (config.getKeywords() != null ? config.getKeywords() : "");
        }

        log.debug("构建的搜索参数: {}", params.toString());
        return params.toString();
    }

    /**
     * 添加参数到StringBuilder（如果参数值不为空）
     *
     * @param params     参数构建器
     * @param paramName  参数名
     * @param paramValue 参数值
     */
    private void appendParameterIfNotEmpty(StringBuilder params, String paramName, String paramValue) {
        if (paramValue != null && !paramValue.trim().isEmpty()) {
            try {
                params.append("&").append(paramName).append("=")
                        .append(URLEncoder.encode(paramValue.trim(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.warn("编码参数失败: {}={}", paramName, paramValue, e);
                // 如果编码失败，使用原始值
                params.append("&").append(paramName).append("=").append(paramValue.trim());
            }
        }
    }

    /**
     * 检查是否需要登录
     */
    private boolean isLoginRequired() {
        try {
            Page page = PlaywrightUtil.getPageObject();

            // 检查是否存在登录按钮
            if (Job51ElementLocators.hasLoginElement(page)) {
                log.debug("检测到登录按钮，需要登录");
                return true; // 需要登录
            }

            return false;
        } catch (Exception e) {
            log.error("登录状态检查出错", e);
            return true; // 出错时默认需要登录
        }
    }

    /**
     * 登录检查
     */
    @SneakyThrows
    private boolean login() {
        Page page = PlaywrightUtil.getPageObject();
        page.navigate(LOGIN_URL);
        PlaywrightUtil.sleep(3);

        try {
            // 检查是否已经登录
            if (Job51ElementLocators.isUserLoggedIn(page)) {
                log.info("检测到已登录状态");
                return true;
            }
        } catch (Exception ignored) {
        }

        log.info("等待用户手动登录...");

        boolean login = false;

        while (!login) {
            try {
                // 判断登录页登录元素是否还存在
                if (Job51ElementLocators.hasPasswordLoginElement(page)) {
                    login = true;
                    log.info("登录成功");
                }
            } catch (Exception e) {
                // 登录检查异常，继续等待
                log.debug("登录状态检查异常，继续等待: {}", e.getMessage());
            }

            // 等待一段时间后再次检查
            PlaywrightUtil.sleep(3);
        }

        return true;
    }

    /**
     * 等待页面完全加载
     * 等待分页元素和职位列表元素出现，确保页面内容完全加载
     */
    private void waitForPageLoad(Page page) {
        try {
            log.info("等待页面加载完成...");

            // 等待DOM加载完成
            page.waitForLoadState();

            // 等待分页元素出现（最多等待10秒）
            try {
                page.waitForSelector("ul.el-pager", new Page.WaitForSelectorOptions().setTimeout(10000));
                log.info("分页元素已加载");
            } catch (Exception e) {
                log.warn("分页元素加载超时，可能页面没有分页或加载失败: {}", e.getMessage());
            }


            // 额外等待一段时间，确保JS完全执行完毕
            PlaywrightUtil.sleep(2);

        } catch (Exception e) {
            log.error("等待页面加载时发生错误: {}", e.getMessage());
            // 发生错误时也等待一段时间
            PlaywrightUtil.sleep(3);
        }
    }

    /**
     * 等待页面内容加载
     * 在点击分页后等待新页面内容加载完成
     */
    private void waitForPageContentLoad(Page page) {
        try {
            log.debug("等待页面内容更新...");

            // 等待网络空闲，确保数据加载完成
            page.waitForLoadState();

            // 等待职位列表内容更新
            try {
                page.waitForSelector("div.joblist .joblist-item", new Page.WaitForSelectorOptions().setTimeout(5000));
                log.debug("职位列表内容已更新");
            } catch (Exception e) {
                log.warn("职位列表内容更新超时: {}", e.getMessage());
            }

            // 短暂等待确保页面稳定
            PlaywrightUtil.sleep(1);

        } catch (Exception e) {
            log.error("等待页面内容加载时发生错误: {}", e.getMessage());
            // 发生错误时也等待一段时间
            PlaywrightUtil.sleep(2);
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
