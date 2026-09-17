package com.getjobs.application.controller;

import com.getjobs.application.service.ChatGptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/chatgpt")
@CrossOrigin(origins = {"http://localhost:6866", "http://127.0.0.1:6866", "http://localhost:9527", "http://127.0.0.1:9527"})
@RequiredArgsConstructor
public class ChatGptController {
    private final ChatGptService chatGpt;

    @GetMapping("/status") public Map<String, Object> status() { return Map.of("success", true, "data", chatGpt.status()); }
    @GetMapping("/models") public Map<String, Object> models() { return Map.of("success", true, "data", chatGpt.models()); }
    @PostMapping("/login") public Map<String, Object> login() { return Map.of("success", true, "data", chatGpt.login()); }
    @PostMapping("/cancel") public Map<String, Object> cancel() { chatGpt.cancelLogin(); return Map.of("success", true); }
    @PostMapping("/logout") public Map<String, Object> logout() { chatGpt.logout(); return Map.of("success", true); }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> error(IllegalStateException e) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
    }
}
