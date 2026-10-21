package com.getjobs.application.controller;

import com.getjobs.application.entity.CookieEntity;
import com.getjobs.application.entity.ZhilianConfigEntity;
import com.getjobs.application.service.CookieService;
import com.getjobs.application.service.ZhilianService;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.service.ZhilianJobService;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 智联招聘控制器（整合版）
 * 提供智联招聘平台配置管理、Cookie管理和登录状态控制的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/zhilian")
@CrossOrigin(origins = "*")
public class ZhilianController {

    @Autowired
    private ZhilianService zhilianService;

    @Autowired
    private PlaywrightManager playwrightManager;

    @Autowired
    private CookieService cookieService;

    @Autowired
    private ZhilianJobService zhilianJobService;

    // ==================== 配置管理相关接口 ====================

    /**
     * 获取 智联 配置与可选项
     */
    @GetMapping("/config")
    public Map<String, Object> getAllZhilianConfig() {
        Map<String, Object> result = new HashMap<>();

        ZhilianConfigEntity config = zhilianService.getFirstConfig();
        if (config == null) {
            config = new ZhilianConfigEntity();
        }

        Map<String, List<Map<String, String>>> options = new HashMap<>();
        options.put("city", zhilianService.getOptionsByType("city").stream().map(e -> {
            Map<String, String> m = new HashMap<>();
            m.put("name", e.getName());
            m.put("code", e.getCode());
            return m;
        }).collect(Collectors.toList()));
        // 智联薪资目前不枚举，前端可用文本输入或简单选择"不限"

        result.put("config", config);
        result.put("options", options);
        return result;
    }

    /**
     * 更新 智联 配置（选择性更新第一条记录）
     */
    @PutMapping("/config")
    public ZhilianConfigEntity updateConfig(@RequestBody ZhilianConfigEntity config) {
        // 城市：支持多城市投递。前端传来代码列表 -> 统一保存成『中文名称的括号列表』
        if (config != null && config.getCityCode() != null) {
            config.setCityCode(zhilianService.normalizeCityListToNames(config.getCityCode()));
        }
        return zhilianService.updateConfig(config);
    }

    /**
     * 返回城市选项列表
     */
    @GetMapping("/config/options/city")
    public List<Map<String, String>> getCityOptions() {
        return zhilianService.getOptionsByType("city").stream().map(e -> {
            Map<String, String> m = new HashMap<>();
            m.put("name", e.getName());
            m.put("code", e.getCode());
            return m;
        }).collect(Collectors.toList());
    }

    // ==================== 登录和认证相关接口 ====================

    /**
     * 检查登录状态
     * @return 登录状态信息
     */
    @GetMapping("/login-status")
    public ResponseEntity<Map<String, Object>> checkLoginStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean isLoggedIn = playwrightManager.isLoggedIn("zhilian");
            response.put("success", true);
            response.put("isLoggedIn", isLoggedIn);
            response.put("message", isLoggedIn ? "已登录" : "未登录");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("检查登录状态失败", e);
            response.put("success", false);
            response.put("message", "检查登录状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 诊断用：导航到智联搜索页并导出真实 DOM 结构中各选择器命中数量。
     * 站点改版导致选择器失效时，用它可以快速定位新结构，而不必靠猜。
     * 用法：GET /api/zhilian/debug/dom?kw=宠物&jl=551
     */
    @GetMapping("/debug/dom")
    public ResponseEntity<Map<String, Object>> debugDom(
            @RequestParam(name = "kw", defaultValue = "宠物") String kw,
            @RequestParam(name = "jl", defaultValue = "551") String jl) {
        Map<String, Object> r = new HashMap<>();
        try {
            String url = "https://www.zhaopin.com/jobs/?jl=" + jl
                    + "&pageMode=search&kw=" + java.net.URLEncoder.encode(kw, java.nio.charset.StandardCharsets.UTF_8);
            r.put("navigateTo", url);

            // 浏览器被手动关掉后字段仍非空，直接操作会一路 TargetClosedError，先自愈
            playwrightManager.ensureReady();

            // Playwright 对象只能在专用线程上操作，必须走 callOnPlaywright，
            // 否则会抛 "Object doesn't exist"。
            Map<String, Object> probe = playwrightManager.callOnPlaywright(() -> {
                Map<String, Object> m = new HashMap<>();
                Page page = playwrightManager.getZhilianPage();
                if (page == null) {
                    m.put("error", "zhilianPage 未初始化");
                    return m;
                }
                page.navigate(url);
                PlaywrightUtil.sleep(6);
                m.put("url", page.url());
                m.put("title", page.title());

                String[] sels = {
                    "div.job-card", "div.joblist-box__item", "div.job-list-panel", "div.positionlist",
                    "a.job-card__name", "a.jobinfo__name",
                    "span.job-card__salary", "p.jobinfo__salary",
                    "a.job-card__company-name", "div.companyinfo__name",
                    "span.job-card__location", "div.job-card__location",
                    "a.soupager__btn", "button.collect-and-apply__btn"
                };
                Map<String, Integer> counts = new HashMap<>();
                for (String s : sels) {
                    int c;
                    try { c = page.locator(s).count(); } catch (Exception e) { c = -1; }
                    counts.put(s, c);
                }
                m.put("counts", counts);

                try {
                    if (page.locator("div.job-card").count() > 0) {
                        Object html = page.locator("div.job-card").first().evaluate("e => e.outerHTML");
                        String s = String.valueOf(html);
                        m.put("firstCardHtml", s.length() > 6000 ? s.substring(0, 6000) : s);
                    }
                } catch (Exception e) {
                    m.put("firstCardHtmlError", String.valueOf(e));
                }

                try {
                    Object btns = page.evaluate("() => Array.from(document.querySelectorAll('button,a'))"
                            + ".filter(b => /投递|申请/.test(b.textContent || ''))"
                            + ".slice(0, 15).map(b => b.tagName + '.' + b.className + ' | ' + (b.textContent||'').trim())");
                    m.put("applyButtons", btns);
                } catch (Exception e) {
                    m.put("applyButtonsError", String.valueOf(e));
                }
                return m;
            });
            r.putAll(probe);
            return ResponseEntity.ok(r);
        } catch (Exception e) {
            log.error("诊断智联 DOM 失败", e);
            r.put("error", String.valueOf(e));
            return ResponseEntity.ok(r);
        }
    }

    /**
     * 诊断用：在智联页面上下文里执行一段 JS 并返回结果，用于快速摸清改版后的 DOM 结构。
     * 例如：GET /api/zhilian/debug/eval?js=()=>document.querySelectorAll('button').length
     * 注意：仅本地自用的排障工具，不要对外暴露。
     */
    @GetMapping("/debug/eval")
    public ResponseEntity<Map<String, Object>> debugEval(@RequestParam(name = "js") String js) {
        Map<String, Object> r = new HashMap<>();
        try {
            // 浏览器被手动关掉后字段仍非空，直接 evaluate 会抛 TargetClosedError，先自愈
            playwrightManager.ensureReady();

            Object out = playwrightManager.callOnPlaywright(() -> {
                Page page = playwrightManager.getZhilianPage();
                if (page == null) {
                    return "zhilianPage 未初始化";
                }
                return page.evaluate(js);
            });
            r.put("result", out);
            return ResponseEntity.ok(r);
        } catch (Exception e) {
            log.error("在智联页面执行 JS 失败", e);
            r.put("error", String.valueOf(e));
            return ResponseEntity.ok(r);
        }
    }

    /**
     * 触发智联招聘登录：在未登录时点击二维码入口，等待扫码
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> triggerZhilianLogin() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.triggerZhilianLogin();
            response.put("success", true);
            response.put("message", "已尝试打开智联二维码登录入口，请在浏览器扫码登录");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("触发智联登录失败", e);
            response.put("success", false);
            response.put("message", "触发登录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 退出登录：清空数据库Cookie并清理运行中的上下文Cookie
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logoutZhilian() {
        Map<String, Object> response = new HashMap<>();
        try {
            // 更新登录状态为未登录并触发SSE通知
            playwrightManager.setLoginStatus("zhilian", false);

            // 清空数据库中 智联招聘 平台的所有 Cookie 值
            cookieService.clearCookieByPlatform("zhilian", "manual logout");

            // 清理运行中的上下文Cookie
            try {
                playwrightManager.clearZhilianCookies();
            } catch (Exception e) {
                log.warn("清理智联招聘上下文Cookie时发生异常，但不影响退出流程: {}", e.getMessage());
            }

            response.put("success", true);
            response.put("message", "智联招聘已退出登录，数据库Cookie和上下文Cookie均已清理");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("退出登录失败", e);
            response.put("success", false);
            response.put("message", "退出登录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== Cookie管理相关接口 ====================

    /**
     * 调试接口：读取数据库中的 智联招聘 Cookie 记录
     */
    @GetMapping("/cookie")
    public ResponseEntity<Map<String, Object>> getZhilianCookieRecord() {
        Map<String, Object> response = new HashMap<>();
        try {
            CookieEntity cookie = cookieService.getCookieByPlatform("zhilian");
            Map<String, Object> data = new HashMap<>();
            if (cookie != null) {
                data.put("platform", cookie.getPlatform());
                data.put("cookie_value", cookie.getCookieValue());
                data.put("remark", cookie.getRemark());
                data.put("created_at", cookie.getCreatedAt());
                data.put("updated_at", cookie.getUpdatedAt());
            } else {
                data.put("platform", "zhilian");
                data.put("cookie_value", null);
                data.put("message", "未找到智联招聘Cookie记录");
            }
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "读取Cookie记录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 调试接口：主动保存当前上下文中的 智联招聘 Cookie 到数据库
     */
    @PostMapping("/save-cookie")
    public ResponseEntity<Map<String, Object>> saveZhilianCookie() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.saveZhilianCookiesToDb("manual save");
            response.put("success", true);
            response.put("message", "已主动保存智联招聘Cookie到数据库");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "保存智联招聘Cookie失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== 数据分析与列表 ====================

    /** 投递统计（Dashboard） */
    @GetMapping("/stats")
    public ZhilianService.StatsResponse stats(
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "experience", required = false) String experience,
            @RequestParam(value = "degree", required = false) String degree,
            @RequestParam(value = "minK", required = false) Double minK,
            @RequestParam(value = "maxK", required = false) Double maxK,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        java.util.List<String> statusList = null;
        if (statuses != null && !statuses.trim().isEmpty()) {
            statusList = java.util.Arrays.stream(statuses.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        }
        return zhilianService.getZhilianStats(statusList, location, experience, degree, minK, maxK, keyword);
    }

    /** 岗位列表（分页 + 筛选） */
    @GetMapping("/list")
    public ZhilianService.PagedResult list(
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "experience", required = false) String experience,
            @RequestParam(value = "degree", required = false) String degree,
            @RequestParam(value = "minK", required = false) Double minK,
            @RequestParam(value = "maxK", required = false) Double maxK,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size
    ) {
        java.util.List<String> statusList = null;
        if (statuses != null && !statuses.trim().isEmpty()) {
            statusList = java.util.Arrays.stream(statuses.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        }
        return zhilianService.listZhilianJobs(statusList, location, experience, degree, minK, maxK, keyword, page, size);
    }

    // ==================== 任务管理相关接口 ====================

    /**
     * 启动智联招聘自动投递任务
     * @return 响应结果
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startZhilianJob() {
        Map<String, Object> response = new HashMap<>();

        try {
            // 未登录则不允许启动
            if (!playwrightManager.isLoggedIn("zhilian")) {
                response.put("success", false);
                response.put("message", "请先登录智联招聘");
                response.put("status", "not_logged_in");
                return ResponseEntity.badRequest().body(response);
            }

            // 检查是否已有任务在运行
            if (zhilianJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "智联招聘任务已在运行中，请等待当前任务完成");
                response.put("status", "running");
                return ResponseEntity.badRequest().body(response);
            }

            // 异步启动新任务
            CompletableFuture.runAsync(() -> {
                zhilianJobService.executeDelivery(progressMessage -> {
                    log.info("[{}] {}", progressMessage.getPlatform(), progressMessage.getMessage());
                });
            });

            response.put("success", true);
            response.put("message", "智联招聘任务启动成功");
            response.put("status", "started");

            log.info("通过API启动智联招聘任务成功");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("启动智联招聘任务失败", e);
            response.put("success", false);
            response.put("message", "启动智联招聘任务失败: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 停止智联招聘任务
     * @return 响应结果
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopZhilianJob() {
        Map<String, Object> response = new HashMap<>();

        try {
            if (!zhilianJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "没有正在运行的智联招聘任务");
                return ResponseEntity.badRequest().body(response);
            }

            zhilianJobService.stopDelivery();

            response.put("success", true);
            response.put("message", "智联招聘任务停止请求已发送");

            log.info("通过API停止智联招聘任务");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("停止智联招聘任务失败", e);
            response.put("success", false);
            response.put("message", "停止智联招聘任务失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取当前运行状态
     * @return 当前状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCurrentStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Object> status = zhilianJobService.getStatus();
            response.put("success", true);
            response.putAll(status);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取当前状态失败", e);
            response.put("success", false);
            response.put("message", "获取状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== 健康检查接口 ====================

    /**
     * 健康检查接口
     * @return 服务状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("service", "ZhilianController");
        response.put("status", "healthy");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    // ==================== 辅助方法 ====================

    // 旧枚举构建方法已移除，改为从数据库读取
}
