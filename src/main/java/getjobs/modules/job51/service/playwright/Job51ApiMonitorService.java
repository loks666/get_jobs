package getjobs.modules.job51.service.playwright;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import getjobs.modules.job51.dto.Job51ApiResponse;
import getjobs.repository.entity.JobEntity;
import getjobs.repository.JobRepository;
import getjobs.utils.Job51DataConverter;
import getjobs.utils.PlaywrightUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 51Job接口监控服务
 * 负责监听和记录51Job相关的API请求和响应
 * 
 * @author getjobs
 * @since v2.1.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Job51ApiMonitorService {

    private final JobRepository jobRepository;
    private final Job51DataConverter dataConverter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 全局接口调用频率限制：记录最后一次调用时间
    private static volatile long lastCallTime = 0L;

    /**
     * 初始化监控服务
     * 设置51Job搜索接口监听器
     */
    @PostConstruct
    public void init() {
        setupJob51ApiMonitor();
    }

    /**
     * 设置51Job接口监听器
     * 监听所有相关的API响应并处理职位数据
     */
    public void setupJob51ApiMonitor() {
        try {
            Page page = PlaywrightUtil.getPageObject();

            // 监听51Job职位搜索接口的响应
            setupResponseMonitor(page);

            log.info("51Job API监控服务初始化完成");
        } catch (Exception e) {
            log.error("51Job API监控服务初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 设置响应监控
     */
    private void setupResponseMonitor(Page page) {
        page.onResponse(response -> {
            String url = response.url();

            // 监听51Job职位搜索接口响应
            if (url.contains("/api/job/search-pc")) {
                handleJob51SearchResponse(response);
            }
            // 可以在此添加其他51Job相关接口的监听
        });
    }

    /**
     * 处理51Job职位搜索响应
     */
    private void handleJob51SearchResponse(Response response) {
        log.info("=== 51Job职位搜索响应拦截 ===");
        log.info("响应状态: {}", response.status());
        log.info("响应URL: {}", response.url());
        log.info("响应头: {}", response.headers());

        try {
            String body = response.text();
            log.info("响应体长度: {} 字符", body.length());
            log.debug("响应体内容: {}", body);

            // 尝试解析JSON并美化输出
            formatJsonResponse(body);

            // 解析并保存职位数据
            parseAndSaveJob51Data(body, "51Job职位搜索");

        } catch (PlaywrightException e) {
            log.error("读取51Job响应体失败: {}", e.getMessage());
        }
        log.info("==========================");
    }

    /**
     * 格式化JSON响应
     */
    private void formatJsonResponse(String body) {
        try {
            Object jsonObject = objectMapper.readValue(body, Object.class);
            String formattedJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
            log.debug("格式化JSON响应: {}", formattedJson);
        } catch (Exception e) {
            log.debug("响应体不是有效的JSON格式: {}", e.getMessage());
        }
    }

    /**
     * 解析并保存51Job职位数据
     * 
     * @param body   响应体JSON字符串
     * @param source 数据来源描述
     */
    @Transactional
    public void parseAndSaveJob51Data(String body, String source) {
        try {
            // 解析51Job API响应
            Job51ApiResponse response = objectMapper.readValue(body, Job51ApiResponse.class);

            if (!"1".equals(response.getStatus())) {
                log.warn("51Job API响应错误，status: {}, message: {}", response.getStatus(), response.getMessage());
                return;
            }

            if (response.getResultbody() == null || 
                response.getResultbody().getJob() == null || 
                response.getResultbody().getJob().getItems() == null) {
                log.warn("51Job API响应中没有职位数据");
                return;
            }

            List<Job51ApiResponse.Job51JobItem> jobItems = response.getResultbody().getJob().getItems();
            log.info("从{}获取到 {} 个职位数据", source, jobItems.size());

            // 过滤有效的职位数据
            List<Job51ApiResponse.Job51JobItem> validJobItems = jobItems.stream()
                    .filter(dataConverter::isValidJobData)
                    .toList();

            if (validJobItems.isEmpty()) {
                log.warn("没有有效的职位数据，来源: {}", source);
                return;
            }

            log.info("过滤后有效职位数据: {} 个", validJobItems.size());

            // 转换为JobEntity并保存
            List<JobEntity> jobEntities = validJobItems.stream()
                    .map(dataConverter::convertToJobEntity)
                    .filter(Objects::nonNull)
                    .toList();

            if (!jobEntities.isEmpty()) {
                // 检查是否已存在相同的职位（基于encryptJobId）
                List<JobEntity> newJobs = jobEntities.stream()
                        .filter(entity -> !isJobExists(entity.getEncryptJobId()))
                        .collect(Collectors.toList());

                if (!newJobs.isEmpty()) {
                    jobRepository.saveAll(newJobs);
                    log.info("成功保存 {} 个新职位到数据库，来源: {}", newJobs.size(), source);

                    // 打印保存的职位信息
                    newJobs.forEach(job -> log.info("保存职位: {} - {} - {}", 
                        job.getJobTitle(), job.getCompanyName(), job.getSalaryDesc()));
                } else {
                    log.info("所有职位都已存在，跳过保存，来源: {}", source);
                }
            } else {
                log.warn("没有有效的职位数据可以保存，来源: {}", source);
            }

        } catch (Exception e) {
            log.error("解析并保存51Job职位数据失败，来源: {}", source, e);
        }
    }

    /**
     * 检查职位是否已存在
     * 
     * @param encryptJobId 职位ID
     * @return 是否存在
     */
    private boolean isJobExists(String encryptJobId) {
        if (encryptJobId == null || encryptJobId.trim().isEmpty()) {
            return false;
        }

        try {
            return jobRepository.existsByEncryptJobId(encryptJobId);
        } catch (Exception e) {
            log.warn("检查职位是否存在时发生错误: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 手动启动监控（如果需要重新启动）
     */
    public void startMonitoring() {
        log.info("手动启动51Job API监控服务");
        setupJob51ApiMonitor();
    }

    /**
     * 检查监控服务状态
     */
    public boolean isMonitoringActive() {
        try {
            BrowserContext ctx = PlaywrightUtil.getContext();
            return ctx != null;
        } catch (Exception e) {
            log.warn("检查51Job监控服务状态失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 刷新51Job页面来获取新的session
     */
    public boolean refreshSession() {
        try {
            log.info("开始刷新51Job session");

            // 获取Playwright上下文
            BrowserContext context = PlaywrightUtil.getContext();
            if (context == null) {
                log.error("无法获取Playwright上下文，session刷新失败");
                return false;
            }

            // 创建新页面访问51job.com
            Page refreshPage = context.newPage();

            try {
                // 设置用户代理，模拟真实浏览器访问
                refreshPage.setExtraHTTPHeaders(java.util.Map.of(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8",
                        "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8",
                        "Accept-Encoding", "gzip, deflate, br",
                        "Cache-Control", "no-cache",
                        "Pragma", "no-cache"));

                // 访问51job.com主页
                log.info("正在访问 https://www.51job.com/");
                refreshPage.navigate("https://www.51job.com/");

                // 等待页面加载完成
                refreshPage.waitForLoadState();
                refreshPage.waitForTimeout(3000); // 等待3秒确保页面完全加载

                // 模拟用户行为：滚动页面
                refreshPage.evaluate("window.scrollTo(0, document.body.scrollHeight / 2)");
                refreshPage.waitForTimeout(1000);

                // 模拟点击页面（不实际点击任何元素，只是模拟用户活动）
                refreshPage.hover("body");
                refreshPage.waitForTimeout(1000);

                log.info("成功访问51job.com，session刷新完成");
                return true;

            } finally {
                // 关闭页面
                if (refreshPage != null) {
                    refreshPage.close();
                }
            }

        } catch (Exception e) {
            log.error("访问51job.com刷新session时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }


    /**
     * 获取监控统计信息
     */
    public java.util.Map<String, Object> getMonitoringStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        try {
            // 统计数据库中51job职位数量
            long totalJobs = jobRepository.countByPlatform("51job");
            stats.put("totalJobs", totalJobs);
            stats.put("platform", "51job");
            stats.put("isActive", isMonitoringActive());
            stats.put("lastCallTime", lastCallTime);
            
        } catch (Exception e) {
            log.error("获取监控统计信息失败: {}", e.getMessage(), e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
}
