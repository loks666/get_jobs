package com.getjobs.application.controller;

import com.getjobs.application.entity.AiEntity;
import com.getjobs.application.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI配置控制器
 * 提供AI配置管理的REST API接口
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
@Slf4j
public class AiConfigController {


    @Autowired
    private AiService aiService;

    /**
     * 获取AI配置
     * @return AI配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getAiConfig() {
        Map<String, Object> response = new HashMap<>();

        try {
            AiEntity aiEntity = aiService.getAiConfig();

            response.put("success", true);
            response.put("data", aiEntity);
            response.put("message", "获取AI配置成功");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取AI配置失败", e);
            response.put("success", false);
            response.put("message", "获取AI配置失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 保存或更新AI配置
     * @param requestBody 请求体包含introduce和prompt
     * @return 保存结果
     */
    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> saveAiConfig(@RequestBody Map<String, String> requestBody) {
        Map<String, Object> response = new HashMap<>();

        try {
            String introduce = requestBody.get("introduce");
            String prompt = requestBody.get("prompt");

            if (introduce == null || prompt == null) {
                response.put("success", false);
                response.put("message", "参数不完整，introduce和prompt不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            AiEntity aiEntity = aiService.saveOrUpdateAiConfig(introduce, prompt);

            response.put("success", true);
            response.put("data", aiEntity);
            response.put("message", "保存AI配置成功");

            log.info("保存AI配置成功，ID: {}", aiEntity.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("保存AI配置失败", e);
            response.put("success", false);
            response.put("message", "保存AI配置失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 健康检查接口
     * @return 服务状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("service", "AiConfigController");
        response.put("status", "healthy");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * AI 文本生成测试接口（GET）
     * 示例：/api/ai/chat?content=你好，帮我写一句简洁的问候语
     */
    @GetMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestParam(name = "content") String content) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (content == null || content.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "content 参数不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            String reply = aiService.sendRequest(content.trim());
            response.put("success", true);
            response.put("data", reply);
            response.put("message", "AI 请求成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("AI 请求失败", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
