package boss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Boss直聘日志工具类
 * 提供统一的日志输出格式和上下文管理
 */
public class BossLogger {
    private static final Logger log = LoggerFactory.getLogger(Boss.class);
    
    // 日志上下文键
    private static final String CONTEXT_KEYWORD = "keyword";
    private static final String CONTEXT_COMPANY = "company";
    private static final String CONTEXT_JOB = "job";
    private static final String CONTEXT_OPERATION = "operation";
    
    /**
     * 设置搜索上下文
     */
    public static void setSearchContext(String keyword, String cityCode) {
        MDC.put(CONTEXT_KEYWORD, keyword);
        MDC.put("cityCode", cityCode);
        MDC.put(CONTEXT_OPERATION, "search");
    }
    
    /**
     * 设置职位处理上下文
     */
    public static void setJobContext(String company, String jobName) {
        MDC.put(CONTEXT_COMPANY, company);
        MDC.put(CONTEXT_JOB, jobName);
        MDC.put(CONTEXT_OPERATION, "job_process");
    }
    
    /**
     * 清除上下文
     */
    public static void clearContext() {
        MDC.clear();
    }
    
    /**
     * 应用启动日志
     */
    public static void logApplicationStart() {
        log.info("=== Boss直聘自动投递启动 ===");
    }
    
    /**
     * 应用完成日志
     */
    public static void logApplicationComplete(int totalChats, long duration) {
        log.info("=== Boss投递完成，共发起{}个聊天，用时{}ms ===", totalChats, duration);
    }
    
    /**
     * 搜索开始日志
     */
    public static void logSearchStart(String keyword, String url) {
        log.info("开始搜索关键词[{}] URL: {}", keyword, url);
    }
    
    /**
     * 页面加载状态日志
     */
    public static void logPageLoad(int jobCount, String status) {
        log.debug("页面加载状态: {} - 当前岗位数量: {}", status, jobCount);
    }
    
    /**
     * 职位过滤日志
     */
    public static void logJobFiltered(String reason, String company, String jobName, Object... params) {
        log.debug("职位已过滤 - 原因: {} - 公司: {} - 岗位: {} - 详情: {}", 
                reason, company, jobName, params.length > 0 ? params[0] : "");
    }
    
    /**
     * 职位投递成功日志
     */
    public static void logJobSuccess(String company, String jobName, String recruiter, String position) {
        log.info("✅ 投递成功 - 公司: {} - 岗位: {} - 招聘官: {}", company, jobName, recruiter);
    }
    
    /**
     * HR活跃状态日志
     */
    public static void logHRStatus(String companyHR, String status, boolean isActive) {
        if (isActive) {
            log.debug("HR活跃检查 - {} 状态: {}", companyHR, status);
        } else {
            log.debug("HR不活跃已过滤 - {} 状态: {}", companyHR, status);
        }
    }
    
    /**
     * 登录状态日志
     */
    public static void logLoginStatus(String status) {
        log.info("登录状态: {}", status);
    }
    
    /**
     * AI处理日志
     */
    public static void logAIProcess(String operation, boolean success, String message) {
        if (success) {
            log.debug("AI处理成功 - 操作: {} - 结果: {}", operation, message);
        } else {
            log.warn("AI处理失败 - 操作: {} - 原因: {}", operation, message);
        }
    }
    
    /**
     * 错误日志 - 业务异常
     */
    public static void logBusinessError(String operation, String message, Object... params) {
        log.warn("业务异常 - 操作: {} - 原因: {} - 参数: {}", operation, message, params);
    }
    
    /**
     * 错误日志 - 系统异常
     */
    public static void logSystemError(String operation, Exception e) {
        log.error("系统异常 - 操作: {} - 错误: {}", operation, e.getMessage(), e);
    }
    
    /**
     * 性能日志
     */
    public static void logPerformance(String operation, long duration) {
        if (duration > 5000) { // 超过5秒记录
            log.warn("性能警告 - 操作: {} 耗时: {}ms", operation, duration);
        } else {
            log.debug("性能统计 - 操作: {} 耗时: {}ms", operation, duration);
        }
    }
    
    /**
     * 数据统计日志
     */
    public static void logStatistics(String type, int count, Object... details) {
        log.info("统计数据 - 类型: {} - 数量: {} - 详情: {}", type, count, 
                details.length > 0 ? details[0] : "");
    }
    
    /**
     * 配置日志
     */
    public static void logConfig(String configName, Object value) {
        log.debug("配置项 - {}: {}", configName, value);
    }
    
    /**
     * 文件操作日志
     */
    public static void logFileOperation(String operation, String filePath, boolean success) {
        if (success) {
            log.debug("文件操作成功 - 操作: {} - 文件: {}", operation, filePath);
        } else {
            log.error("文件操作失败 - 操作: {} - 文件: {}", operation, filePath);
        }
    }
} 