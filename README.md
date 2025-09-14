### 启动工作

#### 补全`application-gpt.yml`配置
api-key: ${OPENAI_API_KEY} -> api-key: ${OPENAI_API_KEY:xxx}
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:xxx}
```