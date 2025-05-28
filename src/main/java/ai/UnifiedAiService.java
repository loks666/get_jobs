package ai;

import boss.BossConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.HttpUtils;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

/**
 * 统一AI服务类
 * 管理所有AI接口调用，包括：
 * 1. 职位匹配检测
 * 2. 国企判定
 * 3. 求职问候语生成
 * 4. 其他AI相关功能
 * 
 * @author loks666
 */
@Slf4j
public class UnifiedAiService {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static volatile UnifiedAiService instance;
    
    // AI平台类型枚举
    public enum AiPlatform {
        DEEPSEEK("deepseek"),
        OPENAI("openai"),
        CHATGPT("chatgpt");
        
        private final String code;
        
        AiPlatform(String code) {
            this.code = code;
        }
        
        public String getCode() {
            return code;
        }
    }
    
    // 响应实体类
    public static class StateOwnedResponse {
        private boolean success;
        private boolean data;
        private String message;
        
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public boolean getData() {
            return data;
        }
        
        public void setData(boolean data) {
            this.data = data;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
    
    // AI职位匹配结果
    public static class JobMatchResult {
        private boolean matched;
        private String message;
        private double confidence;
        
        public JobMatchResult(boolean matched) {
            this.matched = matched;
        }
        
        public JobMatchResult(boolean matched, String message) {
            this.matched = matched;
            this.message = message;
        }
        
        public JobMatchResult(boolean matched, String message, double confidence) {
            this.matched = matched;
            this.message = message;
            this.confidence = confidence;
        }
        
        public boolean isMatched() {
            return matched;
        }
        
        public String getMessage() {
            return message;
        }
        
        public double getConfidence() {
            return confidence;
        }
    }
    
    // 求职问候语请求DTO
    public static class JobGreetingRequest {
        private String jobDescription;
        private String resume;
        private Integer greetingCount = 5;
        private String style = "professional";
        private String model = "deepseek-chat";
        private Double temperature = 0.8;
        
        public JobGreetingRequest() {}
        
        public JobGreetingRequest(String jobDescription, String resume) {
            this.jobDescription = jobDescription;
            this.resume = resume;
        }
        
        public JobGreetingRequest(String jobDescription, String resume, Integer greetingCount, String style) {
            this.jobDescription = jobDescription;
            this.resume = resume;
            this.greetingCount = greetingCount;
            this.style = style;
        }
        
        // Getters and Setters
        public String getJobDescription() { return jobDescription; }
        public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
        
        public String getResume() { return resume; }
        public void setResume(String resume) { this.resume = resume; }
        
        public Integer getGreetingCount() { return greetingCount; }
        public void setGreetingCount(Integer greetingCount) { this.greetingCount = greetingCount; }
        
        public String getStyle() { return style; }
        public void setStyle(String style) { this.style = style; }
        
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        
        public Double getTemperature() { return temperature; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }
    }
    
    // 求职问候语响应DTO
    public static class JobGreetingResponse {
        private boolean success;
        private String message;
        private JobGreetingData data;
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public JobGreetingData getData() { return data; }
        public void setData(JobGreetingData data) { this.data = data; }
        
        public static class JobGreetingData {
            private String id;
            private List<GreetingItem> greetings;
            private MatchAnalysis matchAnalysis;
            private String model;
            private LocalDateTime createTime;
            private Integer tokenUsage;
            
            // Getters and Setters
            public String getId() { return id; }
            public void setId(String id) { this.id = id; }
            
            public List<GreetingItem> getGreetings() { return greetings; }
            public void setGreetings(List<GreetingItem> greetings) { this.greetings = greetings; }
            
            public MatchAnalysis getMatchAnalysis() { return matchAnalysis; }
            public void setMatchAnalysis(MatchAnalysis matchAnalysis) { this.matchAnalysis = matchAnalysis; }
            
            public String getModel() { return model; }
            public void setModel(String model) { this.model = model; }
            
            public LocalDateTime getCreateTime() { return createTime; }
            public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
            
            public Integer getTokenUsage() { return tokenUsage; }
            public void setTokenUsage(Integer tokenUsage) { this.tokenUsage = tokenUsage; }
        }
        
        public static class GreetingItem {
            private String content;
            private List<String> highlightedSkills;
            private List<String> matchedRequirements;
            private String recommendedScene;
            
            // Getters and Setters
            public String getContent() { return content; }
            public void setContent(String content) { this.content = content; }
            
            public List<String> getHighlightedSkills() { return highlightedSkills; }
            public void setHighlightedSkills(List<String> highlightedSkills) { this.highlightedSkills = highlightedSkills; }
            
            public List<String> getMatchedRequirements() { return matchedRequirements; }
            public void setMatchedRequirements(List<String> matchedRequirements) { this.matchedRequirements = matchedRequirements; }
            
            public String getRecommendedScene() { return recommendedScene; }
            public void setRecommendedScene(String recommendedScene) { this.recommendedScene = recommendedScene; }
        }
        
        public static class MatchAnalysis {
            private Integer overallMatchRate;
            private List<String> matchedSkills;
            private List<String> missingSkills;
            private List<String> strengths;
            private List<String> suggestions;
            
            // Getters and Setters
            public Integer getOverallMatchRate() { return overallMatchRate; }
            public void setOverallMatchRate(Integer overallMatchRate) { this.overallMatchRate = overallMatchRate; }
            
            public List<String> getMatchedSkills() { return matchedSkills; }
            public void setMatchedSkills(List<String> matchedSkills) { this.matchedSkills = matchedSkills; }
            
            public List<String> getMissingSkills() { return missingSkills; }
            public void setMissingSkills(List<String> missingSkills) { this.missingSkills = missingSkills; }
            
            public List<String> getStrengths() { return strengths; }
            public void setStrengths(List<String> strengths) { this.strengths = strengths; }
            
            public List<String> getSuggestions() { return suggestions; }
            public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
        }
    }
    
    private UnifiedAiService() {
        // 私有构造函数
    }
    
    /**
     * 获取单例实例
     */
    public static UnifiedAiService getInstance() {
        if (instance == null) {
            synchronized (UnifiedAiService.class) {
                if (instance == null) {
                    instance = new UnifiedAiService();
                }
            }
        }
        return instance;
    }
    
    /**
     * 检查公司是否为国有企业
     * 
     * @param companyName 公司名称
     * @param platform AI平台，默认为deepseek
     * @return 检查结果
     */
    public StateOwnedResponse checkStateOwnedEnterprise(String companyName, AiPlatform platform) {
        return checkStateOwnedEnterprise(companyName, platform, null);
    }
    
    /**
     * 检查公司是否为国有企业
     * 
     * @param companyName 公司名称
     * @param platform AI平台
     * @param baseUrl 自定义API域名，如果为空则使用配置中的域名
     * @return 检查结果
     */
    public StateOwnedResponse checkStateOwnedEnterprise(String companyName, AiPlatform platform, String baseUrl) {
        StateOwnedResponse response = new StateOwnedResponse();
        
        try {
            BossConfig config = BossConfig.getInstance();
            String apiDomain = baseUrl != null ? baseUrl : config.getApiDomain();
            
            if (apiDomain == null || apiDomain.trim().isEmpty()) {
                log.error("API域名未配置");
                response.setSuccess(false);
                response.setMessage("API域名未配置");
                return response;
            }
            
            String url = String.format("%s/pure-admin-service/gpt/isStateOwnedEnterprise/%s?companyName=%s",
                    apiDomain, platform.getCode(), URLEncoder.encode(companyName, StandardCharsets.UTF_8));
            
            log.info("调用国企检查接口: {}", url);
            String responseBody = HttpUtils.get(url);
            
            StateOwnedResponse apiResponse = OBJECT_MAPPER.readValue(responseBody, StateOwnedResponse.class);
            
            if (apiResponse.isSuccess()) {
                log.info("公司 {} 国企检查结果: {}", companyName, apiResponse.getData() ? "是国有企业" : "不是国有企业");
            } else {
                log.warn("公司 {} 国企检查失败: {}", companyName, apiResponse.getMessage());
            }
            
            return apiResponse;
            
        } catch (Exception e) {
            log.error("检查公司 {} 是否为国企时发生错误: {}", companyName, e.getMessage(), e);
            response.setSuccess(false);
            response.setMessage("检查失败: " + e.getMessage());
            return response;
        }
    }
    
    /**
     * 使用AI检测职位是否匹配
     * 
     * @param keyword 搜索关键词
     * @param jobName 职位名称
     * @param jobDescription 职位描述
     * @return 匹配结果
     */
    public JobMatchResult checkJobMatch(String keyword, String jobName, String jobDescription) {
        try {
            AiConfig aiConfig = AiConfig.init();
            BossConfig config = BossConfig.getInstance();
            
            String requestMessage = String.format(aiConfig.getPrompt(), 
                    aiConfig.getIntroduce(), keyword, jobName, jobDescription, config.getSayHi());
            
            log.info("使用AI检测职位匹配: 关键词={}, 职位={}", keyword, jobName);
            String result = AiService.sendRequest(requestMessage);
            
            if (result.contains("false")) {
                log.info("AI判定职位不匹配: {}", jobName);
                return new JobMatchResult(false, "AI判定不匹配");
            } else {
                log.info("AI判定职位匹配: {}", jobName);
                return new JobMatchResult(true, result);
            }
            
        } catch (Exception e) {
            log.error("AI职位匹配检测失败: {}", e.getMessage(), e);
            return new JobMatchResult(false, "AI检测失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送通用AI请求
     * 
     * @param content 请求内容
     * @param timeoutSeconds 超时时间（秒）
     * @return AI响应内容
     */
    public String sendAiRequest(String content, int timeoutSeconds) {
        try {
            log.info("发送AI请求，内容长度: {}", content.length());
            return AiService.sendRequest(content);
        } catch (Exception e) {
            log.error("AI请求失败: {}", e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 发送通用AI请求（使用默认超时时间）
     * 
     * @param content 请求内容
     * @return AI响应内容
     */
    public String sendAiRequest(String content) {
        return sendAiRequest(content, 60);
    }
    
    /**
     * 批量检查多个公司是否为国企
     * 
     * @param companyNames 公司名称列表
     * @param platform AI平台
     * @return 检查结果映射
     */
    public java.util.Map<String, StateOwnedResponse> batchCheckStateOwnedEnterprise(
            java.util.List<String> companyNames, AiPlatform platform) {
        
        java.util.Map<String, StateOwnedResponse> results = new java.util.concurrent.ConcurrentHashMap<>();
        
        // 使用线程池并发处理
        ExecutorService executor = Executors.newFixedThreadPool(5);
        java.util.List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();
        
        for (String companyName : companyNames) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                StateOwnedResponse result = checkStateOwnedEnterprise(companyName, platform);
                results.put(companyName, result);
            }, executor);
            futures.add(future);
        }
        
        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        
        return results;
    }
    
    /**
     * 健康检查 - 测试AI服务是否可用
     * 
     * @return 服务是否可用
     */
    public boolean healthCheck() {
        try {
            String testResponse = sendAiRequest("测试", 10);
            return testResponse != null && !testResponse.trim().isEmpty();
        } catch (Exception e) {
            log.error("AI服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取服务统计信息
     * 
     * @return 统计信息
     */
    public String getServiceStats() {
        return String.format("UnifiedAiService - 实例: %s, 健康状态: %s", 
                instance != null ? "已初始化" : "未初始化",
                healthCheck() ? "正常" : "异常");
    }
    
    /**
     * 生成求职问候语
     * 
     * @param jobDescription 岗位描述
     * @param resume 个人简历
     * @param platform AI平台
     * @return 问候语生成结果
     */
    public JobGreetingResponse generateJobGreeting(String jobDescription, String resume, AiPlatform platform) {
        return generateJobGreeting(jobDescription, resume, platform, null, null, null);
    }
    
    /**
     * 生成求职问候语
     * 
     * @param jobDescription 岗位描述
     * @param resume 个人简历
     * @param platform AI平台
     * @param greetingCount 生成问候语数量
     * @param style 问候语风格
     * @return 问候语生成结果
     */
    public JobGreetingResponse generateJobGreeting(String jobDescription, String resume, AiPlatform platform, 
            Integer greetingCount, String style) {
        return generateJobGreeting(jobDescription, resume, platform, greetingCount, style, null);
    }
    
    /**
     * 生成求职问候语
     * 
     * @param jobDescription 岗位描述
     * @param resume 个人简历
     * @param platform AI平台
     * @param greetingCount 生成问候语数量
     * @param style 问候语风格
     * @param baseUrl 自定义API域名，如果为空则使用配置中的域名
     * @return 问候语生成结果
     */
    public JobGreetingResponse generateJobGreeting(String jobDescription, String resume, AiPlatform platform, 
            Integer greetingCount, String style, String baseUrl) {
        
        JobGreetingResponse response = new JobGreetingResponse();
        
        try {
            BossConfig config = BossConfig.getInstance();
            String apiDomain = baseUrl != null ? baseUrl : config.getApiDomain();
            
            if (apiDomain == null || apiDomain.trim().isEmpty()) {
                log.error("API域名未配置");
                response.setSuccess(false);
                response.setMessage("API域名未配置");
                return response;
            }
            
            // 构建请求对象
            JobGreetingRequest request = new JobGreetingRequest(jobDescription, resume);
            if (greetingCount != null) {
                request.setGreetingCount(greetingCount);
            }
            if (style != null && !style.trim().isEmpty()) {
                request.setStyle(style);
            }
            
            String url = String.format("%s/pure-admin-service/gpt/generate-job-greeting/%s",
                    apiDomain, platform.getCode());
            
            log.info("调用求职问候语生成接口: {}, 平台: {}", url, platform.getCode());
            log.debug("请求参数 - 岗位描述长度: {}, 简历长度: {}, 生成数量: {}, 风格: {}", 
                    jobDescription.length(), resume.length(), request.getGreetingCount(), request.getStyle());
            
            String responseBody = HttpUtils.postJson(url, request);
            
            JobGreetingResponse apiResponse = OBJECT_MAPPER.readValue(responseBody, JobGreetingResponse.class);
            
            if (apiResponse.isSuccess()) {
                log.info("求职问候语生成成功，返回 {} 条问候语", 
                        apiResponse.getData() != null && apiResponse.getData().getGreetings() != null ? 
                        apiResponse.getData().getGreetings().size() : 0);
            } else {
                log.warn("求职问候语生成失败: {}", apiResponse.getMessage());
            }
            
            return apiResponse;
            
        } catch (Exception e) {
            log.error("生成求职问候语时发生错误: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setMessage("生成失败: " + e.getMessage());
            return response;
        }
    }
    
    /**
     * 使用默认平台生成求职问候语（DeepSeek）
     * 
     * @param jobDescription 岗位描述
     * @param resume 个人简历
     * @return 问候语生成结果
     */
    public JobGreetingResponse generateJobGreeting(String jobDescription, String resume) {
        return generateJobGreeting(jobDescription, resume, AiPlatform.DEEPSEEK);
    }
} 