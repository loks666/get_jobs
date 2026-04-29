package com.getjobs.application.controller;

import com.getjobs.application.service.ResumeOptimizationService;
import com.getjobs.application.service.ResumeOptimizationService.ResumeOptimizationRequest;
import com.getjobs.application.service.ResumeOptimizationService.ResumeOptimizationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/resume")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ResumeOptimizationController {
    private final ResumeOptimizationService resumeOptimizationService;

    @GetMapping("/boss/current")
    public ResponseEntity<Map<String, Object>> getBossCurrentResume() {
        Map<String, Object> response = new HashMap<>();
        try {
            String resumeText = resumeOptimizationService.fetchBossResumeText();
            response.put("success", true);
            response.put("message", "Boss在线简历读取成功");
            response.put("data", Map.of("resumeText", resumeText, "source", "boss"));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("读取Boss在线简历失败", e);
            response.put("success", false);
            response.put("message", "读取Boss在线简历失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/optimize")
    public ResponseEntity<Map<String, Object>> optimize(@RequestBody ResumeOptimizationRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            ResumeOptimizationResponse data = resumeOptimizationService.optimize(request);
            response.put("success", true);
            response.put("message", "简历优化完成");
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("简历优化失败", e);
            response.put("success", false);
            response.put("message", "简历优化失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
