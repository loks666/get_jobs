package com.getjobs.worker.liepin;

import com.getjobs.worker.utils.PlaywrightUtil;
import com.getjobs.application.entity.AiEntity;
import com.getjobs.application.service.AiService;
import com.getjobs.application.service.BossResumeImageService;
import com.getjobs.application.service.BossService;
import com.getjobs.application.service.ConfigService;
import com.getjobs.application.service.LiepinService;
import com.getjobs.worker.boss.BossConfig;
import com.getjobs.application.entity.LiepinEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

// 移除保存页面源码相关的导入
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static com.getjobs.worker.liepin.Locators.*;


/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
@Component
@Scope("prototype")
public class Liepin {
    static {
        // 在类加载时就设置日志文件名，确保Logger初始化时能获取到正确的属性
        System.setProperty("log.name", "liepin");
    }

    private int maxPage = 50;
    private final List<String> resultList = new ArrayList<>();
    private final List<LiepinEntity> lastApiEntities = new ArrayList<>();
    private boolean monitoringRegistered = false;
    @Setter
    private LiepinConfig config;
    @Getter
    private Date startDate;
    @Setter
    private Page page;
    @Autowired
    private LiepinService liepinService;
    @Autowired
    private BossResumeImageService bossResumeImageService;
    @Autowired
    private AiService aiService;
    @Autowired
    private ConfigService configService;
    @Autowired
    private BossService bossService;

    public interface ProgressCallback {
        void onProgress(String message, Integer current, Integer total);
    }

    @Setter
    private ProgressCallback progressCallback;
    @Setter
    private Supplier<Boolean> shouldStopCallback;

    public void prepare() {
        this.startDate = new Date();
        this.resultList.clear();

        // 监控猎聘接口请求与返回，输出被拦截的URL（精确匹配PC搜索相关接口）
        if (page != null && !monitoringRegistered) {
            // 请求拦截日志（仅搜索岗位接口）
            page.onRequest(req -> {
                try {
                    String url = req.url();
                    if (url != null && url.contains("com.liepin.searchfront4c.pc-search-job")
                            && !url.contains("com.liepin.searchfront4c.pc-search-job-cond-init")) {
                        // log.info("[拦截][请求] {}", url);
                    }
                } catch (Exception ignored) {}
            });
            page.onResponse((Response response) -> {
                try {
                    String url = response.url();
                    if (url != null && response.status() == 200 &&
                            url.contains("com.liepin.searchfront4c.pc-search-job") &&
                            !url.contains("com.liepin.searchfront4c.pc-search-job-cond-init")) {
                        log.info("[拦截][响应] {}", url);
                        String contentType = null;
                        try { contentType = response.headers().get("content-type"); } catch (Exception ignored) {}
                        if (contentType == null || contentType.contains("application/json")) {
                            String text = response.text();
                            if (text != null && !text.isEmpty()) {
                                // 尝试解析，若结构不符合则在方法内直接返回
                                parseAndPersistLiepinData(text);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("监控猎聘接口响应时发生错误: {}", e.getMessage());
                }
            });
            monitoringRegistered = true;
        }
    }

    public int execute() {
        if (page == null) {
            throw new IllegalStateException("Liepin.page 未设置");
        }
        if (config == null) {
            throw new IllegalStateException("Liepin.config 未设置");
        }

        // 在开始执行前确保已注册接口监听
        prepare();

        List<String> keywords = config.getKeywords();
        if (keywords == null || keywords.isEmpty()) {
            log.warn("未配置关键词，执行结束");
            return 0;
        }

        for (String keyword : keywords) {
            if (shouldStop()) {
                info("收到停止指令，提前结束关键词循环");
                break;
            }
            submit(keyword);
        }
        return resultList.size();
    }

    // ========== 解析接口JSON并保存到数据库 ==========
    private void parseAndPersistLiepinData(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            // 兼容两种结构：data.data.jobCardList 或 data.jobCardList
            JsonNode cardList = root.path("data").path("data").path("jobCardList");
            if (!cardList.isArray()) {
                cardList = root.path("data").path("jobCardList");
            }
            if (!cardList.isArray()) {
                return;
            }
            lastApiEntities.clear();
            for (JsonNode item : cardList) {
                JsonNode job = item.path("job");
                JsonNode comp = item.path("comp");
                JsonNode recruiter = item.path("recruiter");

                Long jobId = readLong(job.path("jobId"));
                if (jobId == null) {
                    continue;
                }

                LiepinEntity entity = new LiepinEntity();
                entity.setJobId(jobId);
                entity.setJobTitle(readText(job.path("title")));
                entity.setJobLink(readText(job.path("link")));
                entity.setJobSalaryText(readText(job.path("salary")));
                entity.setJobArea(readText(job.path("dq")));
                entity.setJobEduReq(readText(job.path("requireEduLevel")));
                entity.setJobExpReq(readText(job.path("requireWorkYears")));
                entity.setJobPublishTime(readText(job.path("refreshTime")));

                entity.setCompId(readLong(comp.path("compId")));
                entity.setCompName(readText(comp.path("compName")));
                entity.setCompIndustry(readText(comp.path("compIndustry")));
                entity.setCompScale(readText(comp.path("compScale")));

                entity.setHrId(readText(recruiter.path("recruiterId")));
                entity.setHrName(readText(recruiter.path("recruiterName")));
                entity.setHrTitle(readText(recruiter.path("recruiterTitle")));
                entity.setHrImId(readText(recruiter.path("imId")));

                // 缓存到内存供页面投递显示使用（避免从页面读取文本）
                lastApiEntities.add(entity);
            }
            // 批量持久化：仅不存在时插入，默认 delivered=0
            try {
                liepinService.insertSnapshotsIfNotExistsBatch(lastApiEntities);
            } catch (Exception e) {
                log.warn("批量保存猎聘岗位数据失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("解析猎聘JSON失败: {}", e.getMessage());
        }
    }

    private String readText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String v = node.asText();
        return (v == null || v.isEmpty()) ? null : v;
    }

    private Long readLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            if (node.isNumber()) {
                long v = node.asLong();
                return v == 0 ? null : v;
            }
            if (node.isTextual()) {
                String t = node.asText();
                if (t == null || t.isEmpty()) return null;
                long v = Long.parseLong(t.trim());
                return v == 0 ? null : v;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String safeText(String s) {
        if (s == null) return null;
        return s.replaceAll("\n", " ").replaceAll("【 ", "[").replaceAll(" 】", "]");
    }

    private String evaluateAndBuildGreeting(String keyword, String jobName, String companyName, LiepinEntity apiEntity, Locator currentJobCard) {
        try {
            BossConfig bossCfg = configService.getBossConfig();
            String fallbackGreeting = (bossCfg != null && bossCfg.getSayHi() != null && !bossCfg.getSayHi().isBlank())
                    ? bossCfg.getSayHi()
                    : "您好！我对贵司的岗位非常感兴趣，可随时到岗，期待有机会沟通！";

            StringBuilder reqInfo = new StringBuilder();
            reqInfo.append("公司：").append(companyName);
            if (apiEntity != null) {
                if (apiEntity.getCompIndustry() != null && !apiEntity.getCompIndustry().isBlank()) {
                    reqInfo.append("（行业：").append(apiEntity.getCompIndustry()).append("）");
                }
                if (apiEntity.getJobArea() != null && !apiEntity.getJobArea().isBlank()) {
                    reqInfo.append(" | 地点：").append(apiEntity.getJobArea());
                }
                if (apiEntity.getJobExpReq() != null && !apiEntity.getJobExpReq().isBlank()) {
                    reqInfo.append(" | 经验要求：").append(apiEntity.getJobExpReq());
                }
                if (apiEntity.getJobEduReq() != null && !apiEntity.getJobEduReq().isBlank()) {
                    reqInfo.append(" | 学历要求：").append(apiEntity.getJobEduReq());
                }
                if (apiEntity.getJobSalaryText() != null && !apiEntity.getJobSalaryText().isBlank()) {
                    reqInfo.append(" | 薪资：").append(apiEntity.getJobSalaryText());
                }
            }
            try {
                String cardText = currentJobCard.textContent();
                if (cardText != null && !cardText.isBlank()) {
                    String compact = cardText.replaceAll("\\s+", " ").trim();
                    if (compact.length() > 260) compact = compact.substring(0, 260);
                    reqInfo.append(" | 卡片详情：").append(compact);
                }
            } catch (Exception ignored) {}

            boolean enableAi = bossCfg != null && Boolean.TRUE.equals(bossCfg.getEnableAI());
            if (!enableAi) {
                return fallbackGreeting;
            }
            AiEntity aiConfig = aiService.getAiConfig();
            String introduce = (aiConfig != null && aiConfig.getIntroduce() != null) ? aiConfig.getIntroduce() : "";
            String prompt = (aiConfig != null) ? aiConfig.getPrompt() : null;
            if (prompt != null && !prompt.isBlank()) {
                String reqMsg = String.format(prompt, introduce, keyword == null ? "" : keyword, jobName, reqInfo.toString(), fallbackGreeting);
                String aiReply = aiService.sendRequest(reqMsg);
                if (aiReply != null && aiReply.trim().matches("(?i)^[*`'\"\\s]*false[*`'\"。.!！\\s]*$")) {
                    return null;
                }
                if (aiReply != null && !aiReply.isBlank()) {
                    return aiReply.trim();
                }
            }
            return fallbackGreeting;
        } catch (Exception aiErr) {
            log.warn("猎聘 AI 生成招呼语失败，使用默认招呼语: {}", aiErr.getMessage());
            BossConfig bossCfg = configService.getBossConfig();
            return (bossCfg != null && bossCfg.getSayHi() != null && !bossCfg.getSayHi().isBlank())
                    ? bossCfg.getSayHi()
                    : "您好！我对贵司的岗位非常感兴趣，可随时到岗，期待有机会沟通！";
        }
    }

    private void sendGreetingInChat(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        try {
            String[] inputSelectors = {
                "[class*='__im_basic__'] textarea",
                "[class*='__im_basic__'] [contenteditable='true']",
                "textarea",
                "div[contenteditable='true']"
            };
            for (String sel : inputSelectors) {
                Locator box = page.locator(sel);
                if (box.count() > 0 && box.last().isVisible()) {
                    box.last().click();
                    box.last().fill(message);
                    PlaywrightUtil.sleep(1);
                    page.keyboard().press("Enter");
                    PlaywrightUtil.sleep(1);
                    log.info("猎聘已自动发送定制招呼语: {}", message);
                    return;
                }
            }
            for (com.microsoft.playwright.Frame frame : page.frames()) {
                for (String sel : inputSelectors) {
                    Locator box = frame.locator(sel);
                    if (box.count() > 0 && box.last().isVisible()) {
                        box.last().click();
                        box.last().fill(message);
                        PlaywrightUtil.sleep(1);
                        page.keyboard().press("Enter");
                        PlaywrightUtil.sleep(1);
                        log.info("猎聘(iframe)已自动发送定制招呼语: {}", message);
                        return;
                    }
                }
            }
            log.warn("猎聘聊天窗口未找到文本输入框，跳过自定义招呼语发送");
        } catch (Exception e) {
            log.warn("猎聘发送招呼语失败（不影响投递）: {}", e.getMessage());
        }
    }

    private void clickSendResumeButtonInChat() {
        try {
            String[] selectors = {
                "button:has-text('发简历')",
                "a:has-text('发简历')",
                "span:text-is('发简历')",
                "div:text-is('发简历')"
            };
            for (String sel : selectors) {
                Locator btn = page.locator(sel);
                if (btn.count() > 0 && btn.last().isVisible()) {
                    btn.last().click();
                    PlaywrightUtil.sleep(1);
                    confirmResumeSendModalIfPresent();
                    log.info("猎聘已自动点击【发简历】发送内置简历");
                    return;
                }
            }
            for (com.microsoft.playwright.Frame frame : page.frames()) {
                for (String sel : selectors) {
                    Locator btn = frame.locator(sel);
                    if (btn.count() > 0 && btn.last().isVisible()) {
                        btn.last().click();
                        PlaywrightUtil.sleep(1);
                        confirmResumeSendModalIfPresent();
                        log.info("猎聘(iframe)已自动点击【发简历】发送内置简历");
                        return;
                    }
                }
            }
            log.warn("猎聘聊天窗口未找到【发简历】按钮，跳过内置简历发送");
        } catch (Exception e) {
            log.warn("猎聘点击【发简历】失败（不影响打招呼）: {}", e.getMessage());
        }
    }

    private void confirmResumeSendModalIfPresent() {
        try {
            String[] confirmSelectors = {
                "button:has-text('立即投递')",
                ".ant-modal button:has-text('立即投递')",
                ".ant-modal button:has-text('确定')",
                ".ant-modal button:has-text('发送')",
                "button:text-is('确认发送')",
                "button:text-is('确定')"
            };
            for (int attempt = 0; attempt < 3; attempt++) {
                for (String sel : confirmSelectors) {
                    Locator ok = page.locator(sel);
                    if (ok.count() > 0 && ok.last().isVisible()) {
                        ok.last().click();
                        PlaywrightUtil.sleep(1);
                        log.info("猎聘已点击确认弹窗按钮: {}", sel);
                        return;
                    }
                }
                for (com.microsoft.playwright.Frame frame : page.frames()) {
                    for (String sel : confirmSelectors) {
                        Locator ok = frame.locator(sel);
                        if (ok.count() > 0 && ok.last().isVisible()) {
                            ok.last().click();
                            PlaywrightUtil.sleep(1);
                            log.info("猎聘(iframe)已点击确认弹窗按钮: {}", sel);
                            return;
                        }
                    }
                }
                Thread.sleep(500);
            }
        } catch (Exception ignored) {}
    }

    private void sendResumeImageInChat() {
        try {
            BossConfig bossCfg = configService.getBossConfig();
            if (bossCfg != null && Boolean.FALSE.equals(bossCfg.getSendImgResume())) {
                return;
            }
            Optional<Path> resumeImage = bossResumeImageService.getCurrentImagePath();
            if (resumeImage.isEmpty()) {
                return;
            }
            Path imagePath = resumeImage.get();
            String[] selectors = {
                "input[type='file'][accept*='image']",
                "input[type='file']"
            };
            for (String sel : selectors) {
                Locator fileInput = page.locator(sel);
                if (fileInput.count() > 0) {
                    fileInput.last().setInputFiles(imagePath);
                    PlaywrightUtil.sleep(2);
                    log.info("猎聘已自动发送图片简历: {}", imagePath.getFileName());
                    return;
                }
            }
            for (com.microsoft.playwright.Frame frame : page.frames()) {
                for (String sel : selectors) {
                    Locator fileInput = frame.locator(sel);
                    if (fileInput.count() > 0) {
                        fileInput.last().setInputFiles(imagePath);
                        PlaywrightUtil.sleep(2);
                        log.info("猎聘(iframe)已自动发送图片简历: {}", imagePath.getFileName());
                        return;
                    }
                }
            }
            log.warn("猎聘聊天窗口未找到图片上传控件，跳过图片发送");
        } catch (Exception e) {
            log.warn("猎聘发送图片简历失败（不影响投递）: {}", e.getMessage());
        }
    }

    private boolean shouldStop() {
        return shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get());
    }

    private void info(String msg) {
        if (progressCallback != null) {
            progressCallback.onProgress(msg, null, null);
        } else {
            log.info(msg);
        }
    }

    private void submit(String keyword) {
        // 清洗关键词：去掉前后引号与多余空白
        String cleanKeyword = keyword == null ? "" : keyword.replace("\"", "").trim();
        maxPage = 50;
        lastApiEntities.clear();
        page.navigate(getSearchUrl() + "&key=" + cleanKeyword);
        
        // 等待分页元素加载（若结果不足一页无分页栏，则默认按 1 页处理）
        try {
            page.waitForSelector(PAGINATION_BOX, new Page.WaitForSelectorOptions().setTimeout(10000));
            Locator paginationBox = page.locator(PAGINATION_BOX);
            Locator lis = paginationBox.locator("li.ant-pagination-item");
            setMaxPage(lis);
        } catch (Exception e) {
            log.warn("未检测到猎聘分页栏，默认按 1 页继续投递: {}", e.getMessage());
            maxPage = 1;
        }
        
        for (int i = 0; i < maxPage; i++) {
            if (shouldStop()) {
                info("收到停止指令，结束分页循环");
                return;
            }
            try {
                // 尝试关闭订阅弹窗
                Locator closeBtn = page.locator(SUBSCRIBE_CLOSE_BTN);
                if (closeBtn.count() > 0) {
                    closeBtn.click();
                }
            } catch (Exception ignored) {
            }
            
        // 等待岗位卡片挂载（不要求可见，避免因遮挡造成超时）
        page.waitForSelector(
            JOB_CARDS,
            new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.ATTACHED)
                .setTimeout(15000)
        );
            // 额外等待一次接口响应，确保 lastApiEntities 刷新（精确匹配PC搜索接口）
            try {
                page.waitForResponse(r -> {
                    try {
                        String u = r.url();
                        return u != null && u.contains("com.liepin.searchfront4c.pc-search-job") && r.status() == 200;
                    } catch (Exception ignored) { return false; }
                }, () -> {});
            } catch (Exception ignored) {}
            info(String.format("正在投递【%s】第【%d】页...", cleanKeyword, i + 1));
            submitJob(cleanKeyword);
            info(String.format("已投递第【%d】页所有的岗位...", i + 1));
            
            // 查找下一页按钮（AntD v5 结构）
            Locator paginationBox = page.locator(PAGINATION_BOX);
            Locator nextLi = paginationBox.locator(NEXT_PAGE);
            if (nextLi.count() > 0) {
                String cls = nextLi.first().getAttribute("class");
                boolean disabled = cls != null && cls.contains("ant-pagination-disabled");
                if (!disabled) {
                    Locator btn = nextLi.first().locator("button.ant-pagination-item-link");
                    if (btn.count() > 0) {
                        btn.first().click();
                    } else {
                        nextLi.first().click();
                    }
                    PlaywrightUtil.sleep(2);
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        info(String.format("【%s】关键词投递完成！", cleanKeyword));
    }

    private String getSearchUrl() {
        String baseUrl = "https://www.liepin.com/zhaopin/?";
        StringBuilder sb = new StringBuilder(baseUrl);
        // 直接拼接参数，参数为空则忽略
        if (config.getCityCode() != null && !config.getCityCode().isEmpty()) {
            sb.append("city=").append(config.getCityCode()).append("&");
            sb.append("dq=").append(config.getCityCode()).append("&");
        }
        if (config.getSalary() != null && !config.getSalary().isEmpty()) {
            sb.append("salary=").append(config.getSalary()).append("&");
        }
        sb.append("currentPage=0");
        return sb.toString();
    }

    private void setMaxPage(Locator lis) {
        try {
            int count = lis.count();
            if (count >= 1) {
                String pageText = lis.nth(count - 1).textContent();
                if (pageText != null) {
                    int page = Integer.parseInt(pageText.trim());
                    if (page >= 1) {
                        maxPage = page;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void submitJob(String currentKeyword) {
        Set<String> blackJobs = bossService.getBlackJobs();
        Set<String> blackCompanies = bossService.getBlackCompanies();
        Set<String> blackRecruiters = bossService.getBlackRecruiters();
        // 获取hr数量
        Locator jobCards = page.locator(JOB_CARDS);
        int count = jobCards.count();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (shouldStop()) {
                info("收到停止指令，结束卡片遍历");
                return;
            }
            // 获取当前岗位卡片（用于后续操作与缺省展示）
            Locator currentJobCard = page.locator(JOB_CARDS).nth(i);
            // 从接口数据获取展示所需字段，若接口数据缺失则使用缺省占位，仍尝试打招呼
            String jobName = null;
            String companyName = null;
            String salary = null;
            String recruiterName = null;
            LiepinEntity currentApiEntity = null;
            if (i < lastApiEntities.size()) {
                currentApiEntity = lastApiEntities.get(i);
                jobName = safeText(currentApiEntity.getJobTitle());
                companyName = safeText(currentApiEntity.getCompName());
                salary = safeText(currentApiEntity.getJobSalaryText());
                recruiterName = safeText(currentApiEntity.getHrName());
            }
            if (recruiterName == null) recruiterName = "HR";
            if (jobName == null) {
                try {
                    Locator titleLoc = currentJobCard.locator("[class*='job-title'], [class*='jobTitle']").first();
                    if (titleLoc.count() > 0) {
                        jobName = safeText(titleLoc.textContent());
                    }
                } catch (Exception ignored) {}
            }
            if (jobName == null) jobName = "岗位";
            if (companyName == null) companyName = "公司";
            if (salary == null) salary = "";

            final String checkJob = jobName;
            final String checkComp = companyName;
            final String checkRec = recruiterName;
            if (blackJobs != null && blackJobs.stream().anyMatch(b -> b != null && !b.isBlank() && checkJob.toLowerCase().contains(b.toLowerCase()))) {
                log.info("猎聘跳过黑名单岗位: {} ({})", checkJob, checkComp);
                continue;
            }
            if (blackCompanies != null && blackCompanies.stream().anyMatch(b -> b != null && !b.isBlank() && checkComp.toLowerCase().contains(b.toLowerCase()))) {
                log.info("猎聘跳过黑名单公司: {} ({})", checkComp, checkJob);
                continue;
            }
            if (blackRecruiters != null && blackRecruiters.stream().anyMatch(b -> b != null && !b.isBlank() && checkRec.toLowerCase().contains(b.toLowerCase()))) {
                log.info("猎聘跳过黑名单HR: {} ({} - {})", checkRec, checkComp, checkJob);
                continue;
            }
            if (currentApiEntity != null) {
                String expReq = currentApiEntity.getJobExpReq();
                if (expReq != null && expReq.matches(".*\\d+.*年.*") && !expReq.contains("不限") && !expReq.contains("应届") && !expReq.contains("在校")) {
                    log.info("猎聘跳过经验要求不符岗位: {} ({}) [经验要求: {}]", checkJob, checkComp, expReq);
                    continue;
                }
                String eduReq = currentApiEntity.getJobEduReq();
                if (eduReq != null && (eduReq.contains("硕士") || eduReq.contains("博士"))) {
                    log.info("猎聘跳过学历要求不符岗位: {} ({}) [学历要求: {}]", checkJob, checkComp, eduReq);
                    continue;
                }
            }
            
            try {
                // 使用JavaScript滚动到卡片位置，更稳定
                try {
                    // 先滚动到卡片位置
                    page.evaluate("(element) => element.scrollIntoView({behavior: 'instant', block: 'center'})", currentJobCard.elementHandle());
                    // PlaywrightUtil.sleep(1); // 等待滚动完成
                    
                    // 再次确保元素在视窗中
                    page.evaluate("(element) => { const rect = element.getBoundingClientRect(); if (rect.top < 0 || rect.bottom > window.innerHeight) { element.scrollIntoView({behavior: 'instant', block: 'center'}); } }", currentJobCard.elementHandle());
                    // PlaywrightUtil.sleep(1);
                } catch (Exception scrollError) {
                    log.warn("JavaScript滚动失败，尝试页面滚动: {}", scrollError.getMessage());
                    // 备用方案：滚动页面到大概位置
                    page.evaluate("window.scrollBy(0, " + (i * 200) + ")");
                    // PlaywrightUtil.sleep(1);
                }
                
                // 查找HR区域 - 尝试多种可能的HR标签选择器
                Locator hrArea = null;
                String[] hrSelectors = {
                    ".recruiter-info-box",  // 根据页面源码，这是主要的HR区域类名
                    ".recruiter-info, .hr-info, .contact-info",
                    "[class*='recruiter'], [class*='hr-'], [class*='contact']",
                    ".job-card-footer, .card-footer",
                    ".job-bottom, .bottom-info"
                };
                
                for (String selector : hrSelectors) {
                    Locator tempHrArea = currentJobCard.locator(selector);
                    if (tempHrArea.count() > 0) {
                        hrArea = tempHrArea.first();
                        log.debug("找到HR区域，使用选择器: {}", selector);
                        break;
                    }
                }
                
                // 如果找不到特定的HR区域，使用整个卡片
                if (hrArea == null) {
                    log.debug("未找到特定HR区域，使用整个岗位卡片");
                    hrArea = currentJobCard;
                }
                
                // 鼠标悬停到HR区域，触发按钮显示 - 简化悬停逻辑
                boolean hoverSuccess = false;
                int hoverRetries = 3;
                for (int retry = 0; retry < hoverRetries; retry++) {
                    try {
                        // 检查HR区域是否可见，如果不可见则跳过悬停
                        if (!hrArea.isVisible()) {
                            log.debug("HR区域不可见，跳过悬停操作");
                            hoverSuccess = true; // 设为成功，继续后续流程
                            break;
                        }
                        
                        // 直接悬停，不再进行复杂的微调
                        hrArea.hover(new Locator.HoverOptions().setTimeout(5000));
                        hoverSuccess = true;
                        break;
                    } catch (Exception hoverError) {
                        log.warn("第{}次悬停失败: {}", retry + 1, hoverError.getMessage());
                        if (retry < hoverRetries - 1) {
                            // 重试前重新滚动确保元素可见
                            try {
                                page.evaluate("(element) => element.scrollIntoView({behavior: 'instant', block: 'center'})", currentJobCard.elementHandle());
                                Thread.sleep(500); // 等待滚动完成
                            } catch (Exception e) {
                                log.warn("重试前滚动失败: {}", e.getMessage());
                            }
                        }
                    }
                }
                
                if (!hoverSuccess) {
                    log.warn("悬停操作失败，但继续查找按钮");
                    // 不再跳过，而是继续查找按钮，因为有些按钮可能不需要悬停就能显示
                }
                
                // PlaywrightUtil.sleep(1); // 等待按钮显示
                
                // 获取hr名字
                // 已从接口获取 HR 名字，无需再从页面读
                
            } catch (Exception e) {
                log.error("处理岗位卡片失败: {}", e.getMessage());
                continue;
            }
            
            // 查找聊一聊按钮
            Locator button = null;
            String buttonText = "";
            try {
                // 在当前岗位卡片中查找按钮，尝试多种选择器
                
                String[] buttonSelectors = {
                    "button.ant-btn.ant-btn-primary.ant-btn-round",
                    "button.ant-btn.ant-btn-round.ant-btn-primary", 
                    "button[class*='ant-btn'][class*='primary']",
                    "button[class*='ant-btn'][class*='round']",
                    "button[class*='chat'], button[class*='talk']",
                    ".chat-btn, .talk-btn, .contact-btn",
                    "button:has-text('聊一聊')",
                    "button" // 最后尝试所有按钮
                };
                
                for (String selector : buttonSelectors) {
                    try {
                        Locator tempButtons = currentJobCard.locator(selector);
                        int buttonCount = tempButtons.count();
                        log.debug("选择器 '{}' 找到 {} 个按钮", selector, buttonCount);
                        
                        for (int j = 0; j < buttonCount; j++) {
                            Locator tempButton = tempButtons.nth(j);
                            try {
                                if (tempButton.isVisible()) {
                                    String text = tempButton.textContent();
                                    log.debug("按钮文本: '{}'", text);
                                    if (text != null && !text.trim().isEmpty()) {
                                        button = tempButton;
                                        buttonText = text.trim();
                                        // 只关注"聊一聊"按钮
                                        if (text.contains("聊一聊")) {
                                            log.debug("找到目标按钮: '{}'", text);
                                            break;
                                        }
                                    }
                                }
                            } catch (Exception ignore) {
                                log.debug("获取按钮文本失败: {}", ignore.getMessage());
                            }
                        }
                        
                        if (button != null && buttonText.contains("聊一聊")) {
                            break;
                        }
                    } catch (Exception e) {
                        log.debug("选择器 '{}' 查找失败: {}", selector, e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                log.error("查找按钮失败: {}", e.getMessage());
                // 不再保存页面源码
                continue;
            }
            
            // 提取 jobId（用于更新投递状态）
            Long jobIdForUpdate = null;
            if (i < lastApiEntities.size()) {
                jobIdForUpdate = lastApiEntities.get(i).getJobId();
            }
            if (jobIdForUpdate == null) {
                jobIdForUpdate = extractJobIdFromCard(currentJobCard);
            }

            // 检查按钮文本并点击
            if (button != null && buttonText.contains("聊一聊")) {
                String greetingToSend = evaluateAndBuildGreeting(currentKeyword, jobName, companyName, currentApiEntity, currentJobCard);
                if (greetingToSend == null) {
                    log.info("猎聘 AI 判定岗位不匹配，跳过投递: {} - {}", companyName, jobName);
                    continue;
                }
                try {
                    try {
                        if (currentJobCard != null && currentJobCard.isVisible()) {
                            currentJobCard.hover(new Locator.HoverOptions().setTimeout(2000));
                        }
                    } catch (Exception ignored) {}
                    // 在点击按钮前进行鼠标微调，先向右移动2像素，再向左移动2像素
                    try {
                        var boundingBox = button.boundingBox(new Locator.BoundingBoxOptions().setTimeout(3000));
                        if (boundingBox != null) {
                            double centerX = boundingBox.x + boundingBox.width / 2;
                            double centerY = boundingBox.y + boundingBox.height / 2;
                            
                            // 先移动到按钮中心
                            page.mouse().move(centerX, centerY);
                            Thread.sleep(50);
                            
                            // 向右移动2像素
                            page.mouse().move(centerX + 2, centerY);
                            Thread.sleep(50);
                            
                            // 向左移动2像素（回到中心再向左2像素）
                            page.mouse().move(centerX - 2, centerY);
                            Thread.sleep(50);
                            
                            // 回到中心位置
                            page.mouse().move(centerX, centerY);
                            Thread.sleep(50);
                            
                            log.debug("完成鼠标微调，准备点击按钮");
                        }
                    } catch (Exception moveError) {
                        log.warn("鼠标微调失败，直接点击按钮: {}", moveError.getMessage());
                    }
                    
                    button.click(new Locator.ClickOptions().setTimeout(5000));
                    // PlaywrightUtil.sleep(1); // 等待点击响应
                    
                    // 等待聊天窗口加载后，自动发送定制打招呼语，再关闭窗口
                    try {
                        try {
                            page.waitForSelector(CHAT_HEADER + ", textarea, div[contenteditable='true'], iframe[src*='im'], [class*='im-']",
                                    new Page.WaitForSelectorOptions().setTimeout(5000));
                        } catch (Exception waitErr) {
                            log.debug("等待猎聘聊天容器超时，仍尝试查找输入框: {}", waitErr.getMessage());
                        }
                        PlaywrightUtil.sleep(1);
                        sendGreetingInChat(greetingToSend);
                        clickSendResumeButtonInChat();

                        // 直接关闭聊天窗口
                        Locator close = page.locator(CHAT_CLOSE + ", [class*='im'] [class*='close'], .ant-modal-close");
                        if (close.count() > 0 && close.first().isVisible()) {
                            PlaywrightUtil.sleep(1);
                            close.first().click();
                        }
                        
                        resultList.add(sb.append("【").append(companyName).append(" ").append(jobName).append(" ").append(salary).append(" ").append(recruiterName).append(" ").append("】").toString());
                        sb.setLength(0);
                        // 点击成功后标记为已投递
                        if (jobIdForUpdate != null) {
                            liepinService.markDelivered(jobIdForUpdate);
                        }
                        
                    } catch (Exception e) {
                        log.warn("关闭聊天窗口失败，但投递可能已成功: {}", e.getMessage());
                        // 即使关闭失败，也认为投递成功
                        resultList.add(sb.append("【").append(companyName).append(" ").append(jobName).append(" ").append(salary).append(" ").append(recruiterName).append(" ").append("】").toString());
                        sb.setLength(0);
                        if (jobIdForUpdate != null) {
                            liepinService.markDelivered(jobIdForUpdate);
                        }
                    }
                    
                } catch (Exception e) {
                    log.error("点击按钮失败: {}", e.getMessage());
                }
            } else {
                // 如果按钮是“继续聊”，视为已投递
                if (button != null && buttonText.contains("继续聊") && jobIdForUpdate != null) {
                    liepinService.markDelivered(jobIdForUpdate);
                }
                if (button != null) {
                    log.debug("跳过岗位（按钮文本不匹配）: 【{}】的【{}·{}】岗位，按钮文本: '{}'", companyName, jobName, salary, buttonText);
                } else {
                    // 不再保存页面源码
                }
            }
        }
    }

    // 从岗位卡片的 data 属性中提取 jobId（兼容 lastApiEntities 缺失场景）
    private Long extractJobIdFromCard(Locator card) {
        try {
            String ext = card.getAttribute("data-tlg-ext");
            if (ext != null && !ext.isEmpty()) {
                try {
                    String decoded = java.net.URLDecoder.decode(ext, java.nio.charset.StandardCharsets.UTF_8);
                    com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(decoded);
                    String jobIdStr = node.path("jobId").asText(null);
                    if (jobIdStr != null && !jobIdStr.isEmpty()) {
                        return Long.parseLong(jobIdStr);
                    }
                } catch (Exception ignore) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\\\\"jobId\\\\\":\\\\\"(\\d+)\\\\\"").matcher(ext);
                    if (m.find()) {
                        return Long.parseLong(m.group(1));
                    }
                }
            }
            String scm = card.getAttribute("data-tlg-scm");
            if (scm != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("jobId=(\\d+)").matcher(scm);
                if (m.find()) {
                    return Long.parseLong(m.group(1));
                }
            }
        } catch (Exception ignore) {}
        return null;
    }
}
