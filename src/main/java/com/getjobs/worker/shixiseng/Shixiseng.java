package com.getjobs.worker.shixiseng;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getjobs.application.entity.AiEntity;
import com.getjobs.application.entity.ShixisengEntity;
import com.getjobs.application.service.AiService;
import com.getjobs.application.service.BossService;
import com.getjobs.application.service.ConfigService;
import com.getjobs.application.service.ShixisengService;
import com.getjobs.worker.boss.BossConfig;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@Component
@Scope("prototype")
public class Shixiseng {

    public interface ProgressCallback {
        void onProgress(String message, Integer current, Integer total);
    }

    @Setter
    private Page page;
    @Setter
    private ShixisengConfig config;
    @Setter
    private ProgressCallback progressCallback;
    @Setter
    private Supplier<Boolean> shouldStopCallback;

    @Autowired
    private ShixisengService shixisengService;
    @Autowired
    private BossService bossService;
    @Autowired
    private AiService aiService;
    @Autowired
    private ConfigService configService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private int deliveredCount = 0;

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

    public int execute() {
        deliveredCount = 0;
        if (config == null || config.getKeywords() == null || config.getKeywords().isEmpty()) {
            info("未配置实习僧搜索关键词，跳过投递");
            return 0;
        }
        for (String keyword : config.getKeywords()) {
            if (shouldStop()) {
                info("收到停止指令，结束实习僧投递");
                break;
            }
            submitKeyword(keyword);
        }
        return deliveredCount;
    }

    private void submitKeyword(String keyword) {
        String cleanKeyword = keyword == null ? "" : keyword.replace("\"", "").trim();
        if (cleanKeyword.isEmpty()) return;

        int maxPage = 1;
        String firstUrl = buildSearchUrl(cleanKeyword, 1);
        page.navigate(firstUrl);
        try {
            page.waitForSelector("div.intern-wrap.intern-item[data-intern-id]",
                    new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(10000));
        } catch (Exception e) {
            info("【" + cleanKeyword + "】未找到岗位列表，跳过");
            return;
        }

        try {
            Locator pagerNums = page.locator(".el-pagination ul.el-pager li.number");
            int cnt = pagerNums.count();
            if (cnt > 0) {
                String lastText = pagerNums.nth(cnt - 1).textContent();
                if (lastText != null && !lastText.isBlank()) {
                    maxPage = Math.min(20, Integer.parseInt(lastText.trim()));
                }
            }
        } catch (Exception ignored) {
            maxPage = 1;
        }

        for (int p = 1; p <= maxPage; p++) {
            if (shouldStop()) {
                info("收到停止指令，结束分页循环");
                return;
            }
            if (p > 1) {
                page.navigate(buildSearchUrl(cleanKeyword, p));
                try {
                    page.waitForSelector("div.intern-wrap.intern-item[data-intern-id]",
                            new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(10000));
                } catch (Exception e) {
                    break;
                }
            }

            info(String.format("正在扫描实习僧【%s】第【%d/%d】页...", cleanKeyword, p, maxPage));
            List<String> uuids = extractUuidsFromPage();
            if (uuids.isEmpty()) break;

            processPageJobs(cleanKeyword, uuids);
        }
        info(String.format("【%s】关键词投递完成！", cleanKeyword));
    }

    private String buildSearchUrl(String keyword, int pageNum) {
        String encodedKw = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String city = (config.getCity() != null && !config.getCity().isBlank()) ? config.getCity().trim() : "全国";
        String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
        return "https://www.shixiseng.com/interns?keyword=" + encodedKw + "&city=" + encodedCity + "&page=" + pageNum;
    }

    private List<String> extractUuidsFromPage() {
        List<String> uuids = new ArrayList<>();
        Locator cards = page.locator("div.intern-wrap.intern-item[data-intern-id]");
        int count = cards.count();
        for (int i = 0; i < count; i++) {
            try {
                String uuid = cards.nth(i).getAttribute("data-intern-id");
                if (uuid != null && !uuid.isBlank() && !uuids.contains(uuid)) {
                    uuids.add(uuid.trim());
                }
            } catch (Exception ignored) {}
        }
        return uuids;
    }

    private void processPageJobs(String keyword, List<String> uuids) {
        Set<String> blackJobs = bossService.getBlackJobs();
        Set<String> blackCompanies = bossService.getBlackCompanies();
        boolean enableAi = Boolean.TRUE.equals(config.getEnableAi());

        for (String uuid : uuids) {
            if (shouldStop()) {
                info("收到停止指令，结束岗位遍历");
                return;
            }
            if (shixisengService.isDelivered(uuid)) {
                continue;
            }

            try {
                String detailJson = (String) page.evaluate(
                        "async (u) => { const r = await fetch(u, { credentials: 'include' }); return await r.text(); }",
                        "https://www.shixiseng.com/app/intern/info?uuid=" + uuid
                );
                if (detailJson == null || detailJson.isBlank()) continue;

                JsonNode root = objectMapper.readTree(detailJson);
                JsonNode msg = root.path("msg");
                if (msg.isMissingNode() || msg.isNull()) continue;

                String jobTitle = msg.path("iname").asText("").trim();
                String compName = msg.path("cname").asText("").trim();
                String degree = msg.path("degree").asText("").trim();
                String city = msg.path("city").asText("").trim();
                String industry = msg.path("industry").asText("").trim();
                String scale = msg.path("scale").asText("").trim();
                String minsal = msg.path("minsal").asText("");
                String maxsal = msg.path("maxsal").asText("");
                String day = msg.path("day").asText("");
                String month = msg.path("month").asText("");
                String jd = msg.path("info").asText("").trim();
                boolean overdue = msg.path("overdue").asBoolean(false);
                boolean apiDelivered = msg.path("delivered").asBoolean(false);

                ShixisengEntity snapshot = new ShixisengEntity();
                snapshot.setJobId(uuid);
                snapshot.setJobTitle(jobTitle);
                snapshot.setJobLink("https://www.shixiseng.com/intern/" + uuid);
                snapshot.setJobSalaryText((!minsal.isEmpty() || !maxsal.isEmpty()) ? (minsal + "-" + maxsal + "元/天") : "");
                snapshot.setJobArea(city);
                snapshot.setJobEduReq(degree);
                snapshot.setJobExpReq((!day.isEmpty() ? day + "天/周" : "") + (!month.isEmpty() ? " · " + month + "个月" : ""));
                snapshot.setCompName(compName);
                snapshot.setCompIndustry(industry);
                snapshot.setCompScale(scale);
                snapshot.setDelivered(apiDelivered ? 1 : 0);
                shixisengService.saveOrUpdateSnapshot(snapshot);

                if (overdue) {
                    log.info("实习僧跳过已过期岗位: {} ({})", jobTitle, compName);
                    continue;
                }
                if (apiDelivered) {
                    shixisengService.markDelivered(uuid);
                    continue;
                }

                if (blackJobs != null && blackJobs.stream().anyMatch(b -> b != null && !b.isBlank() && jobTitle.toLowerCase().contains(b.toLowerCase()))) {
                    log.info("实习僧跳过黑名单岗位: {} ({})", jobTitle, compName);
                    continue;
                }
                if (blackCompanies != null && blackCompanies.stream().anyMatch(b -> b != null && !b.isBlank() && compName.toLowerCase().contains(b.toLowerCase()))) {
                    log.info("实习僧跳过黑名单公司: {} ({})", compName, jobTitle);
                    continue;
                }
                if (degree.contains("硕士") || degree.contains("博士")) {
                    log.info("实习僧跳过学历要求不符岗位: {} ({}) [{}]", jobTitle, compName, degree);
                    continue;
                }

                if (enableAi && !isAiMatch(keyword, jobTitle, compName, city, degree, snapshot.getJobSalaryText(), snapshot.getJobExpReq(), jd)) {
                    log.info("实习僧 AI 判定岗位不匹配，跳过投递: {} - {}", compName, jobTitle);
                    continue;
                }

                // 打开详情页并点击“投个简历”
                if (applyOnDetailPage(uuid, jobTitle, compName)) {
                    deliveredCount++;
                    shixisengService.markDelivered(uuid);
                    info(String.format("实习僧投递成功 | %s | %s (%s)", compName, jobTitle, snapshot.getJobSalaryText()));
                }
            } catch (Exception e) {
                log.warn("处理实习僧岗位失败 uuid={}: {}", uuid, e.getMessage());
            }
        }
    }

    private boolean isAiMatch(String keyword, String jobTitle, String compName, String city, String degree, String salary, String expReq, String jd) {
        try {
            BossConfig bossCfg = configService.getBossConfig();
            String fallbackGreeting = (bossCfg != null && bossCfg.getSayHi() != null) ? bossCfg.getSayHi() : "";
            AiEntity aiConfig = aiService.getAiConfig();
            String introduce = (aiConfig != null && aiConfig.getIntroduce() != null) ? aiConfig.getIntroduce() : "";
            String prompt = (aiConfig != null) ? aiConfig.getPrompt() : null;
            if (prompt == null || prompt.isBlank()) return true;

            String compactJd = jd.length() > 700 ? jd.substring(0, 700) : jd;
            String fullInfo = String.format("公司：%s | 城市：%s | 学历：%s | 薪资：%s | 实习要求：%s | JD：%s",
                    compName, city, degree, salary, expReq, compactJd);
            String reqMsg = String.format(prompt, introduce, keyword, jobTitle, fullInfo, fallbackGreeting);
            String reply = aiService.sendRequest(reqMsg);
            if (reply != null && reply.trim().matches("(?i)^[*`'\"\\s]*false[*`'\"。.!！\\s]*$")) {
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("实习僧 AI 筛选异常，默认放行: {}", e.getMessage());
            return true;
        }
    }

    private boolean applyOnDetailPage(String uuid, String jobTitle, String compName) {
        try {
            page.navigate("https://www.shixiseng.com/intern/" + uuid);
            Locator btn = page.locator(".resume_apply").first();
            btn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            String btnText = btn.textContent();
            if (btnText == null) btnText = "";
            btnText = btnText.trim();

            if (btnText.contains("已投") || btnText.contains("过期") || btnText.contains("停招")) {
                log.info("实习僧岗位状态为[{}]，跳过: {} ({})", btnText, jobTitle, compName);
                if (btnText.contains("已投")) {
                    shixisengService.markDelivered(uuid);
                }
                return false;
            }

            btn.click(new Locator.ClickOptions().setTimeout(5000));
            PlaywrightUtil.sleep(1);

            // 处理可能出现的“选择简历/确认投递”弹窗
            String[] confirmSelectors = {
                ".deliver-dialog-box button:has-text('投递')",
                ".deliver-dialog-box button:has-text('确定')",
                ".deliver-dialog-box button:has-text('确认')",
                ".el-dialog button:has-text('投递简历')",
                ".el-dialog button:has-text('确认投递')",
                ".el-dialog button:has-text('立即投递')"
            };
            for (String sel : confirmSelectors) {
                Locator okBtn = page.locator(sel);
                if (okBtn.count() > 0 && okBtn.first().isVisible()) {
                    okBtn.first().click(new Locator.ClickOptions().setTimeout(3000));
                    PlaywrightUtil.sleep(2);
                    break;
                }
            }

            String afterText = "";
            try {
                afterText = btn.textContent();
            } catch (Exception ignored) {}
            if (afterText != null && afterText.contains("已投")) {
                return true;
            }
            // 若弹窗已经关闭或按钮状态变化，也记录成功
            return true;
        } catch (Exception e) {
            log.warn("实习僧详情页投递失败 uuid={}: {}", uuid, e.getMessage());
            return false;
        }
    }
}
