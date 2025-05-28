# UnifiedAiService 使用说明

## 概述

`UnifiedAiService` 是一个统一的AI服务管理类，用于管理所有AI接口调用，包括：
- 职位匹配检测
- 国企判定
- 其他AI相关功能

## 主要功能

### 1. 国企检查

```java
// 获取服务实例
UnifiedAiService aiService = UnifiedAiService.getInstance();

// 检查单个公司是否为国企
UnifiedAiService.StateOwnedResponse response = aiService.checkStateOwnedEnterprise(
    "华为技术有限公司", 
    UnifiedAiService.AiPlatform.DEEPSEEK
);

if (response.isSuccess() && response.getData()) {
    System.out.println("该公司是国有企业");
} else {
    System.out.println("该公司不是国有企业");
}

// 批量检查多个公司
List<String> companies = Arrays.asList("华为", "阿里巴巴", "腾讯");
Map<String, UnifiedAiService.StateOwnedResponse> results = 
    aiService.batchCheckStateOwnedEnterprise(companies, UnifiedAiService.AiPlatform.DEEPSEEK);
```

### 2. 职位匹配检测

```java
// AI职位匹配检测
UnifiedAiService.JobMatchResult matchResult = aiService.checkJobMatch(
    "Java开发", 
    "高级Java工程师", 
    "负责Java后端开发，熟悉Spring框架..."
);

if (matchResult.isMatched()) {
    System.out.println("职位匹配成功: " + matchResult.getMessage());
} else {
    System.out.println("职位不匹配");
}
```

### 3. 通用AI请求

```java
// 发送通用AI请求
String response = aiService.sendAiRequest("请帮我分析这个职位描述...");
System.out.println("AI回复: " + response);

// 带超时时间的请求
String response2 = aiService.sendAiRequest("复杂的分析请求...", 120); // 120秒超时
```

### 4. 服务监控

```java
// 健康检查
boolean isHealthy = aiService.healthCheck();
System.out.println("服务状态: " + (isHealthy ? "正常" : "异常"));

// 获取服务统计信息
String stats = aiService.getServiceStats();
System.out.println(stats);
```

## 支持的AI平台

- `DEEPSEEK`: DeepSeek平台
- `OPENAI`: OpenAI平台
- `CHATGPT`: ChatGPT平台

## 配置说明

确保在 `BossConfig` 中配置了正确的API域名：

```java
config.setApiDomain("https://your-api-domain.com");
```

## 异常处理

所有方法都包含完善的异常处理：

```java
try {
    UnifiedAiService.StateOwnedResponse response = aiService.checkStateOwnedEnterprise(
        companyName, UnifiedAiService.AiPlatform.DEEPSEEK);
    // 处理成功响应
} catch (Exception e) {
    log.error("AI服务调用失败: {}", e.getMessage(), e);
    // 处理异常情况
}
```

## 线程安全

`UnifiedAiService` 使用单例模式，并且是线程安全的，可以在多线程环境中安全使用。

## 最佳实践

1. **使用单例**: 始终通过 `getInstance()` 获取服务实例
2. **异常处理**: 总是包含适当的异常处理逻辑
3. **超时设置**: 对于可能耗时较长的操作，设置合适的超时时间
4. **批量操作**: 对于多个相似操作，优先使用批量方法以提高效率
5. **健康检查**: 在关键业务流程前进行服务健康检查

## 迁移指南

### 从原有代码迁移

**旧代码:**
```java
String platform = "deepseek";
String baseUrl = config.getApiDomain();
String url = String.format("%s/pure-admin-service/gpt/isStateOwnedEnterprise/%s?companyName=%s",
        baseUrl, platform, URLEncoder.encode(companyName, "UTF-8"));
String response = HttpUtils.get(url);
StateOwnedResponse stateOwnedResponse = OBJECT_MAPPER.readValue(response, StateOwnedResponse.class);
```

**新代码:**
```java
UnifiedAiService aiService = UnifiedAiService.getInstance();
UnifiedAiService.StateOwnedResponse response = aiService.checkStateOwnedEnterprise(
        companyName, UnifiedAiService.AiPlatform.DEEPSEEK);
```

### AI职位匹配迁移

**旧代码:**
```java
AiConfig aiConfig = AiConfig.init();
String requestMessage = String.format(aiConfig.getPrompt(), aiConfig.getIntroduce(), keyword, jobName, jd, config.getSayHi());
String result = AiService.sendRequest(requestMessage);
return result.contains("false") ? new AiFilter(false) : new AiFilter(true, result);
```

**新代码:**
```java
UnifiedAiService aiService = UnifiedAiService.getInstance();
UnifiedAiService.JobMatchResult result = aiService.checkJobMatch(keyword, jobName, jd);
return result.isMatched() ? new AiFilter(true, result.getMessage()) : new AiFilter(false);
``` 