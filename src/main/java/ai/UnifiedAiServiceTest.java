package ai;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * UnifiedAiService 测试类
 * 验证统一AI服务的各项功能
 * 
 * @author loks666
 */
@Slf4j
public class UnifiedAiServiceTest {
    
    public static void main(String[] args) {
        testUnifiedAiService();
    }
    
    public static void testUnifiedAiService() {
        log.info("开始测试 UnifiedAiService...");
        
        // 获取服务实例
        UnifiedAiService aiService = UnifiedAiService.getInstance();
        
        // 1. 测试服务健康检查
        testHealthCheck(aiService);
        
        // 2. 测试国企检查
        testStateOwnedEnterprise(aiService);
        
        // 3. 测试批量国企检查
        testBatchStateOwnedEnterprise(aiService);
        
        // 4. 测试职位匹配
        testJobMatch(aiService);
        
        // 5. 测试通用AI请求
        testGeneralAiRequest(aiService);
        
        // 6. 测试服务统计信息
        testServiceStats(aiService);
        
        log.info("UnifiedAiService 测试完成!");
    }
    
    private static void testHealthCheck(UnifiedAiService aiService) {
        log.info("=== 测试服务健康检查 ===");
        try {
            boolean isHealthy = aiService.healthCheck();
            log.info("服务健康状态: {}", isHealthy ? "正常" : "异常");
        } catch (Exception e) {
            log.error("健康检查测试失败: {}", e.getMessage(), e);
        }
    }
    
    private static void testStateOwnedEnterprise(UnifiedAiService aiService) {
        log.info("=== 测试国企检查 ===");
        String[] testCompanies = {
            "中国石油天然气集团有限公司",
            "阿里巴巴集团",
            "华为技术有限公司",
            "腾讯科技有限公司"
        };
        
        for (String companyName : testCompanies) {
            try {
                log.info("检查公司: {}", companyName);
                UnifiedAiService.StateOwnedResponse response = aiService.checkStateOwnedEnterprise(
                    companyName, UnifiedAiService.AiPlatform.DEEPSEEK);
                
                if (response.isSuccess()) {
                    log.info("检查结果: {} - {}", companyName, 
                        response.getData() ? "国有企业" : "非国有企业");
                } else {
                    log.warn("检查失败: {} - {}", companyName, response.getMessage());
                }
            } catch (Exception e) {
                log.error("检查公司 {} 时发生异常: {}", companyName, e.getMessage(), e);
            }
        }
    }
    
    private static void testBatchStateOwnedEnterprise(UnifiedAiService aiService) {
        log.info("=== 测试批量国企检查 ===");
        try {
            List<String> companies = Arrays.asList(
                "中国移动通信集团有限公司",
                "百度在线网络技术有限公司",
                "字节跳动科技有限公司"
            );
            
            log.info("批量检查公司: {}", companies);
            Map<String, UnifiedAiService.StateOwnedResponse> results = 
                aiService.batchCheckStateOwnedEnterprise(companies, UnifiedAiService.AiPlatform.DEEPSEEK);
            
            results.forEach((company, response) -> {
                if (response.isSuccess()) {
                    log.info("批量检查结果: {} - {}", company, 
                        response.getData() ? "国有企业" : "非国有企业");
                } else {
                    log.warn("批量检查失败: {} - {}", company, response.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("批量国企检查测试失败: {}", e.getMessage(), e);
        }
    }
    
    private static void testJobMatch(UnifiedAiService aiService) {
        log.info("=== 测试职位匹配检测 ===");
        try {
            String keyword = "Java开发";
            String jobName = "高级Java开发工程师";
            String jobDescription = "负责Java后端开发，熟悉Spring Boot、MySQL、Redis等技术栈，" +
                "有微服务架构经验，能够独立承担模块开发任务。";
            
            log.info("职位匹配测试 - 关键词: {}, 职位: {}", keyword, jobName);
            UnifiedAiService.JobMatchResult result = aiService.checkJobMatch(keyword, jobName, jobDescription);
            
            log.info("匹配结果: {}", result.isMatched() ? "匹配" : "不匹配");
            if (result.getMessage() != null && !result.getMessage().isEmpty()) {
                log.info("AI回复: {}", result.getMessage());
            }
        } catch (Exception e) {
            log.error("职位匹配测试失败: {}", e.getMessage(), e);
        }
    }
    
    private static void testGeneralAiRequest(UnifiedAiService aiService) {
        log.info("=== 测试通用AI请求 ===");
        try {
            String testQuestion = "请简单介绍一下Java编程语言的特点";
            log.info("发送AI请求: {}", testQuestion);
            
            String response = aiService.sendAiRequest(testQuestion);
            if (response != null && !response.trim().isEmpty()) {
                log.info("AI回复: {}", response.length() > 100 ? 
                    response.substring(0, 100) + "..." : response);
            } else {
                log.warn("AI未返回有效响应");
            }
        } catch (Exception e) {
            log.error("通用AI请求测试失败: {}", e.getMessage(), e);
        }
    }
    
    private static void testServiceStats(UnifiedAiService aiService) {
        log.info("=== 测试服务统计信息 ===");
        try {
            String stats = aiService.getServiceStats();
            log.info("服务统计: {}", stats);
        } catch (Exception e) {
            log.error("服务统计信息测试失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 模拟异常情况测试
     */
    public static void testErrorScenarios() {
        log.info("=== 测试异常情况 ===");
        UnifiedAiService aiService = UnifiedAiService.getInstance();
        
        // 测试空参数
        try {
            UnifiedAiService.StateOwnedResponse response = aiService.checkStateOwnedEnterprise(
                "", UnifiedAiService.AiPlatform.DEEPSEEK);
            log.info("空公司名测试结果: success={}", response.isSuccess());
        } catch (Exception e) {
            log.info("空公司名测试捕获异常: {}", e.getMessage());
        }
        
        // 测试null参数
        try {
            UnifiedAiService.StateOwnedResponse response = aiService.checkStateOwnedEnterprise(
                null, UnifiedAiService.AiPlatform.DEEPSEEK);
            log.info("null公司名测试结果: success={}", response.isSuccess());
        } catch (Exception e) {
            log.info("null公司名测试捕获异常: {}", e.getMessage());
        }
    }
} 