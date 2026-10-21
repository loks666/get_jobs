package com.getjobs.worker.zhilian;

import com.getjobs.application.entity.AiEntity;
import com.getjobs.application.entity.ZhilianJobDataEntity;
import com.getjobs.application.service.AiService;
import com.getjobs.application.service.ZhilianService;
import com.getjobs.worker.utils.Job;
import com.getjobs.worker.utils.JobUtils;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * 智联招聘自动投递 - Playwright版本
 */
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class ZhiLian {

    @Setter
    private Page page;

    @Setter
    private ZhilianConfig config;

    @Setter
    private ProgressCallback progressCallback;

    @Setter
    private Supplier<Boolean> shouldStopCallback;

    private final List<Job> resultList = new ArrayList<>();
    private boolean isLimit = false;
    private int maxPage = 500;

    private static final String HOME_URL = "https://www.zhaopin.com/sou/";

    private final ZhilianService zhilianService;

    private final AiService aiService;

    /**
     * 没有自定义提示词时的兜底问法：只要 true / false 两个答案。
     * 与 Boss 不同，智联配置里没有"招呼语"字段，所以这里只有 4 个占位符。
     */
    private static final String DEFAULT_AI_PROMPT =
            "我目前在找工作，%s，我期望的岗位方向是【%s】，目前我需要投递的岗位名称是【%s】，"
                    + "这个岗位的要求是【%s】。如果这个岗位和我的期望与经历基本符合，请只回复 true；"
                    + "如果完全不相干，请只回复 false。不要输出任何其他内容。";

    /**
     * 渝东北、渝东南各区县：离重庆主城太远，用户明确不投。
     * （例如开州、万州、黔江、武隆等；主城与渝西不在其中）
     */
    private static final List<String> EXCLUDED_REGIONS = Arrays.asList(
            // 渝东北
            "万州", "梁平", "城口", "丰都", "垫江", "忠县", "开州", "云阳", "奉节", "巫山", "巫溪",
            // 渝东南
            "黔江", "武隆", "石柱", "秀山", "酉阳", "彭水"
    );

    /**
     * 岗位是否落在渝东北/渝东南。传入岗位标题、地点、公司名等多个文本，
     * 任一包含目标区县名即判定为排除（地区信息可能写在任一字段里）。
     */
    private boolean isExcludedRegion(String... texts) {
        if (texts == null) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        for (String t : texts) {
            if (t != null) {
                sb.append(t);
            }
        }
        if (sb.length() == 0) {
            return false;
        }
        String all = sb.toString();
        for (String region : EXCLUDED_REGIONS) {
            if (all.contains(region)) {
                return true;
            }
        }
        return false;
    }

    private static class PageJob {
        int index;
        String jobId;
        String jobTitle;
        String companyName;

        PageJob(int index, String jobId, String jobTitle, String companyName) {
            this.index = index;
            this.jobId = jobId;
            this.jobTitle = jobTitle;
            this.companyName = companyName;
        }
    }

    /**
     * 进度回调接口
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void accept(String message, Integer current, Integer total);
    }

    /**
     * 准备工作：加载配置、初始化数据
     */
    public void prepare() {
        log.info("智联招聘准备工作开始...");
        resultList.clear();
        isLimit = false;
        log.info("智联招聘准备工作完成");
    }

    /**
     * 执行投递任务
     * @return 投递数量
     */
    public int execute() {
        log.info("智联招聘投递任务开始...");
        long startTime = System.currentTimeMillis();

        try {
            // 解析本次要投递的城市列表
            List<String> cityCodes = (config.getCityCode() == null || config.getCityCode().isEmpty())
                    ? Collections.singletonList("0")
                    : config.getCityCode();
            if (cityCodes.size() > 1) {
                log.info("智联本次将依次投递 {} 个城市", cityCodes.size());
            }

            // 外层遍历城市，内层遍历关键词
            boolean stopped = false;
            for (String cityCode : cityCodes) {
                if (stopped) break;
                for (String keyword : config.getKeywords()) {
                    if (shouldStop() || isLimit) {
                        sendProgress("用户取消投递或已达上限", null, null);
                        stopped = true;
                        break;
                    }

                    deliverByKeyword(keyword, cityCode);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            String message = String.format("智联招聘投递完成，共投递%d个岗位，用时%s",
                resultList.size(), formatDuration(duration));
            log.info(message);
            sendProgress(message, null, null);

            if (!resultList.isEmpty()) {
                log.info("新投递公司如下:");
                resultList.forEach(job -> log.info(job.toString()));
            } else {
                log.info("未投递新的岗位...");
            }

        } catch (Exception e) {
            log.error("智联招聘投递过程出现异常", e);
            sendProgress("投递出现异常: " + e.getMessage(), null, null);
        }

        return resultList.size();
    }

    /**
     * 按关键词投递
     */
    private void deliverByKeyword(String keyword, String cityCode) {
        if (isLimit) {
            return;
        }

        try {
            log.info("开始投递关键词: {}", keyword);
            sendProgress("正在搜索关键词: " + keyword, null, null);

            // 智联已改版：
            // 1) 搜索页从 /sou/jl{code}/p{n} 迁到 /jobs/?jl={code}&kw={keyword}
            // 2) 布局从"每张卡片自带投递按钮"改成"左列表 + 右详情面板"两栏，
            //    必须先点卡片选中，再到右侧面板点"立即投递"。
            // 因此直接构造新 URL，不再依赖往搜索框里输入关键词。
            String searchUrl = buildSearchUrl(keyword, cityCode);
            page.navigate(searchUrl);
            PlaywrightUtil.sleep(3);

            // 等待岗位列表加载（改版后卡片容器为 div.job-card）
            try {
                page.waitForSelector("div.job-card",
                    new Page.WaitForSelectorOptions().setTimeout(15_000));
            } catch (Exception e) {
                // 超时时必须留下现场信息，否则只能看到"共投递0个"却无从判断原因
                // （常见：页面停在登录页 / 被风控拦截 / 搜索未真正触发）。
                String curUrl;
                try { curUrl = page.url(); } catch (Exception ignored) { curUrl = "<读取失败>"; }
                String title;
                try { title = page.title(); } catch (Exception ignored) { title = "<读取失败>"; }
                log.warn("等待岗位列表超时，跳过当前关键词。请求URL={} 当前URL={} 标题={}",
                        searchUrl, curUrl, title);
                return;
            }

            // 改版后列表页没有"下一页"按钮了，改为滚动到底部触发加载更多。
            // 每次滚动只处理新增出来的卡片（老卡片仍在 DOM 中，必须跳过）。
            int processed = 0;
            int round = 1;
            while (round <= maxPage) {
                if (shouldStop() || isLimit) {
                    sendProgress("用户取消投递或已达上限", null, null);
                    return;
                }

                log.info("开始投递【{}】关键词，第【{}】轮...", keyword, round);
                sendProgress(String.format("正在投递第%d轮", round), round, maxPage);

                int handled = deliverCurrentPage(keyword, processed);
                if (handled <= 0) {
                    break;
                }
                processed += handled;

                PlaywrightUtil.sleep(2);

                if (!loadMoreJobs()) {
                    log.info("没有更多岗位，结束加载");
                    break;
                }
                round++;
            }

            log.info("关键词【{}】投递完成", keyword);
        } catch (Exception e) {
            log.error("投递关键词【{}】时出现异常", keyword, e);
        }
    }

    /**
    /**
     * 投递列表中新增的职位（从 startIndex 开始处理）。
     *
     * 智联改版后是"左列表 + 右详情面板"的两栏结构：
     * 必须先点击左侧 div.job-card 选中岗位，右侧面板才会加载该岗位详情，
     * 之后才能点到 button.job-detail-summary__apply（立即投递）。
     * 旧版"每张卡片自带投递按钮"的写法已经完全失效。
     *
     * @return 本次处理掉的卡片数量；<=0 表示应结束本关键词
     */
    private int deliverCurrentPage(String keyword, int startIndex) {
        try {
            page.waitForSelector("div.job-card",
                    new Page.WaitForSelectorOptions().setTimeout(15000));

            if (checkIsLimit()) {
                sendProgress("用户取消投递或已达上限", null, null);
                return -1;
            }

            Locator cards = page.locator("div.job-card");
            int count = cards.count();
            if (startIndex >= count) {
                return 0;
            }
            log.info("检测到列表岗位数量: {}，本次从第 {} 个开始处理", count, startIndex);

            for (int i = startIndex; i < count; i++) {
                if (shouldStop() || isLimit) {
                    sendProgress("用户取消投递或已达上限", null, null);
                    return -1;
                }

                // 上一次投递可能留下了招呼语浮层，先清掉再点卡片
                closeBlockingOverlays();

                Locator card = cards.nth(i);
                String jobTitle = trimOrNull(safeGetText(card, "span.vue-clamp__text"));
                String salary = trimOrNull(safeGetText(card, "span.job-card__salary"));
                String location = trimOrNull(safeGetText(card, "div.job-card__location"));
                String companyName = trimOrNull(safeGetText(card, "a.job-card__company-name"));

                if (jobTitle == null) {
                    continue;
                }

                // 渝东北、渝东南离主城太远，明确不投。
                // 放在去重之前：纯字符串判断最省，不必浪费后续的点卡片、落库和 AI 请求。
                if (isExcludedRegion(jobTitle, location, companyName)) {
                    log.info("岗位位于渝东北/渝东南，跳过：title={}，location={}，company={}",
                            jobTitle, location, companyName);
                    continue;
                }

                // 库里已有记录说明投过，跳过，避免重复打扰
                boolean exists = false;
                try {
                    exists = companyName != null
                            && zhilianService.existsByTitleAndCompany(jobTitle, companyName);
                } catch (Exception e) {
                    log.debug("查询岗位是否已存在失败: {}", e.getMessage());
                }
                if (exists) {
                    log.info("岗位已投递过，跳过：title={}，company={}", jobTitle, companyName);
                    continue;
                }

                // 选中该岗位：点击卡片 → 右侧详情面板加载对应详情。
                // 页面上会不断冒出各种浮层（招呼语弹窗、沟通抽屉、附件选择弹窗…），
                // 它们会 intercept pointer events，让 Playwright 的真实点击一直超时。
                // 因此先尝试真实点击，失败则退回 JS 派发 click 事件绕开遮挡。
                try {
                    card.scrollIntoViewIfNeeded();
                } catch (Exception ignored) {}
                boolean selected = false;
                try {
                    card.click(new Locator.ClickOptions().setTimeout(3000));
                    selected = true;
                } catch (Exception e) {
                    try {
                        card.evaluate("e => e.click()");
                        selected = true;
                    } catch (Exception e2) {
                        log.warn("点击岗位卡片失败，跳过：title={}，原因={}", jobTitle, e.getMessage());
                    }
                }
                if (!selected) {
                    continue;
                }
                PlaywrightUtil.sleep(1);

                // 卡片上已经没有岗位链接了，jobId 只能从详情面板的职位详情链接里取
                String jobLink = readDetailJobLink();
                String jobId = extractJobIdFromLink(jobLink);

                // ★ AI 把关：智联的关键词匹配极松（搜"宠物"能冒出超市配送、AI训练师），
                // 少了这一层会把完全不相干的岗位投出去，反而污染求职记录。
                // 必须在落库和点击投递之前判断，否则库里会留下一堆"未投递"的垃圾数据。
                String jd = readJobDescription();
                if (!aiApprove(keyword, jobTitle, jd, companyName, salary)) {
                    try {
                        ZhilianJobDataEntity skipped = new ZhilianJobDataEntity();
                        skipped.setJobId(jobId == null ? "" : jobId);
                        skipped.setJobTitle(jobTitle);
                        skipped.setJobLink(jobLink);
                        skipped.setSalary(salary);
                        skipped.setLocation(location);
                        skipped.setCompanyName(companyName);
                        skipped.setDeliveryStatus("不匹配");
                        zhilianService.insertJob(skipped);
                    } catch (Exception ex) {
                        log.debug("保存AI过滤记录失败: {}", ex.getMessage());
                    }
                    continue;
                }

                try {
                    ZhilianJobDataEntity entity = new ZhilianJobDataEntity();
                    entity.setJobId(jobId == null ? "" : jobId);
                    entity.setJobTitle(jobTitle);
                    entity.setJobLink(jobLink);
                    entity.setSalary(salary);
                    entity.setLocation(location);
                    entity.setCompanyName(companyName);
                    entity.setDeliveryStatus("未投递");
                    zhilianService.insertJob(entity);
                } catch (Exception ex) {
                    log.warn("保存岗位数据失败: {}", ex.getMessage());
                }

                // 点击右侧详情面板的"立即投递"
                Locator applyBtn = page.locator("button.job-detail-summary__apply");
                if (applyBtn.count() == 0) {
                    log.info("岗位【{}】未找到立即投递按钮，跳过", jobTitle);
                    continue;
                }
                try {
                    // 点击前注册监听器，统一关闭由当前页面打开的新窗口（弹出页）
                    java.util.function.Consumer<Page> closer = (Page newPage) -> {
                        try {
                            if (newPage.opener() == page) {
                                try { newPage.waitForLoadState(); } catch (Exception ignored) {}
                                try { PlaywrightUtil.sleep(200); } catch (Exception ignored) {}
                                try { newPage.close(); } catch (Exception ignored) {}
                            }
                        } catch (Exception ignored) {}
                    };
                    page.context().onPage(closer);
                    try {
                        try {
                            applyBtn.first().click(new Locator.ClickOptions().setTimeout(3000));
                        } catch (Exception ce) {
                            // 同样可能被浮层挡住，退回 JS 点击
                            applyBtn.first().evaluate("e => e.click()");
                        }
                    } finally {
                        try { page.context().offPage(closer); } catch (Exception ignored) {}
                    }
                    PlaywrightUtil.sleep(1);

                    // ★ 智联点「立即投递」只是弹出「请选择要投递的简历」，
                    //   必须再点一次「投递简历」才真正投递出去。
                    //   少了这一步，简历选择框一直挂着、投递从未发生，
                    //   而代码照样落库报"已投递" —— 之前那一批假投递就是这么来的。
                    if (!confirmAttachmentAndDeliver()) {
                        log.warn("投递未能确认成功，不计入结果：title={}，company={}", jobTitle, companyName);
                        closeBlockingOverlays();
                        continue;
                    }
                    // 投递成功后弹出招呼语浮层，必须关掉，否则后续卡片点不动
                    closeBlockingOverlays();

                    try {
                        if (jobId != null && !jobId.isEmpty()) {
                            zhilianService.markDeliveredByJobId(jobId);
                        } else if (companyName != null) {
                            zhilianService.markDeliveredByTitleAndCompany(jobTitle, companyName);
                        }
                    } catch (Exception ex) {
                        log.warn("更新投递状态失败: {}", ex.getMessage());
                    }

                    // 计入本次投递结果。旧实现只在"相似职位"弹窗里 add，
                    // 主流程投出去的岗位根本没被统计 —— 这也是"永远显示0"的元凶之一。
                    Job job = new Job();
                    job.setJobName(jobTitle);
                    job.setSalary(salary);
                    job.setCompanyName(companyName);
                    job.setJobInfo(location);
                    resultList.add(job);

                    log.info("已投递：title={}，company={}，salary={}", jobTitle, companyName, salary);
                } catch (Exception clickEx) {
                    log.warn("投递失败，继续下一个岗位: {}", clickEx.getMessage());
                }

                if (checkIsLimit()) {
                    sendProgress("用户取消投递或已达上限", null, null);
                    return -1;
                }
            }

            return count - startIndex;
        } catch (Exception e) {
            log.error("投递当前页面失败", e);
            try {
                saveCurrentPageHtml();
                log.info("已保存当前页面到 src/main/java/com/getjobs/worker/zhilian/page.html 以便排查");
            } catch (Exception saveEx) {
                log.warn("保存当前页面HTML失败: {}", saveEx.getMessage());
            }
            return -1;
        }
    }

    /**
     * 读取右侧详情面板里的职位描述（JD）。
     * 改版后正文容器是 div.job-description；取不到时逐级退回更外层的容器。
     * 读不到 JD 时 AI 只能凭职位名判断，因此允许返回 null 由调用方处理。
     */
    private String readJobDescription() {
        String[] selectors = {
                "div.job-description",
                "div.job-detail-card__body",
                "div.job-detail-modules__body"
        };
        for (String sel : selectors) {
            try {
                Locator loc = page.locator(sel);
                if (loc.count() == 0) {
                    continue;
                }
                String text = trimOrNull(loc.first().innerText());
                if (text != null && text.length() >= 20) {
                    // 截断，避免长 JD 把 token 打满、拖慢每次判断
                    return text.length() > 1500 ? text.substring(0, 1500) : text;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * 用 AI 判断岗位是否与求职方向匹配。
     *
     * 智联的关键词匹配非常松 —— 搜"宠物"会冒出超市分拣、AI训练师这类岗位，
     * Boss 侧靠 generateAiMessage() 返回 false 来拦截，智联原本完全没有这一层，
     * 结果就是投到什么算什么。这里补齐同样的把关。
     *
     * 失败策略是"保守跳过"：AI 请求异常或返回空时宁可不投，
     * 因为乱投对求职形象的损害远大于少投几个。
     *
     * @return true=可以投递；false=应跳过
     */
    private boolean aiApprove(String keyword, String jobTitle, String jd, String companyName, String salary) {
        if (aiService == null) {
            return true;
        }

        AiEntity aiConfig;
        try {
            aiConfig = aiService.getAiConfig();
        } catch (Exception e) {
            log.warn("读取AI配置失败，保守跳过该岗位: title={}，原因={}", jobTitle, e.getMessage());
            return false;
        }
        // 没填自我介绍说明用户没启用 AI，保持原行为（不过滤）
        if (aiConfig == null || aiConfig.getIntroduce() == null || aiConfig.getIntroduce().isBlank()) {
            return true;
        }

        String prompt = (aiConfig.getPrompt() != null && !aiConfig.getPrompt().isBlank())
                ? aiConfig.getPrompt()
                : DEFAULT_AI_PROMPT;

        String requestMessage;
        try {
            // 库里的提示词是 Boss 用的 5 占位符版本（含招呼语），智联没有招呼语，第 5 位传空串
            requestMessage = String.format(prompt, aiConfig.getIntroduce(), keyword, jobTitle,
                    jd == null ? "" : jd, "");
        } catch (Exception e) {
            log.warn("AI提示词格式化失败，改用默认提示词: {}", e.getMessage());
            requestMessage = String.format(DEFAULT_AI_PROMPT, aiConfig.getIntroduce(), keyword, jobTitle,
                    jd == null ? "" : jd);
        }

        try {
            String result = aiService.sendRequest(requestMessage);
            if (result == null || result.isBlank()) {
                log.warn("AI返回为空，保守跳过该岗位: title={}", jobTitle);
                return false;
            }
            if (isAiRejectResponse(result)) {
                log.info("AI判定不匹配，跳过：title={}，company={}，salary={}", jobTitle, companyName, salary);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("AI请求失败，保守跳过该岗位: title={}，原因={}", jobTitle, e.getMessage());
            return false;
        }
    }

    /**
     * 只把独立的 false 识别为拒绝，避免正常文本里偶然出现 false 字样时误杀。
     */
    private boolean isAiRejectResponse(String result) {
        return result != null
                && result.trim().matches("(?i)^[*`'\"\\s]*false[*`'\"。.!！\\s]*$");
    }

    /**
     * 构造智联改版后的搜索 URL：/jobs/?jl={城市码}&kw={关键词}
     */
    private String buildSearchUrl(String keyword, String cityCode) {
        String encoded;
        try {
            encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        } catch (Exception e) {
            encoded = keyword;
        }
        return "https://www.zhaopin.com/jobs/?jl=" + cityCode + "&pageMode=search&kw=" + encoded;
    }

    /**
     * 滚动到底部触发加载更多；返回是否真的新增了卡片。
     */
    private boolean loadMoreJobs() {
        try {
            int before = page.locator("div.job-card").count();
            page.evaluate("() => { const el = document.scrollingElement || document.body;"
                    + " if (el) el.scrollTop = el.scrollHeight; }");
            PlaywrightUtil.sleep(3);
            int after = page.locator("div.job-card").count();
            if (after > before) {
                log.info("滚动加载新增 {} 个岗位（{} -> {}）", after - before, before, after);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.debug("滚动加载更多岗位失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 清理会挡住列表点击的浮层。
     *
     * 实测有两个：
     * 1) banner-drawer：右侧沟通抽屉，z-index 2000，投递后可能自动弹出并盖住整个列表，
     *    是"点卡片一直超时"的真正元凶；
     * 2) deliver-greeting-modal：投递后的招呼语浮层，点「留在此页」即可关掉
     *    （自动投递不需要真的去跟 HR 沟通）。
     *
     * 注意：这两个元素即使处于隐藏状态也常驻 DOM 里，必须先用 isVisible() 判断，
     * 否则会去点一个不可见元素而白白超时。
     */
    /**
     * 智联的投递是两段式的：
     *   1) 点「立即投递」→ 弹出「请选择要投递的简历」(.a-job-apply-workflow--attachment-select)
     *   2) 再点「投递简历」(a.a-attachment-select__action-btn__delivery) → 才真正投递
     * 只做第 1 步等于什么都没发生。
     *
     * @return true 表示已点过确认且页面校验通过
     */
    private boolean confirmAttachmentAndDeliver() {
        try {
            page.waitForSelector(".a-job-apply-workflow--attachment-select",
                    new Page.WaitForSelectorOptions().setTimeout(8000));
        } catch (Exception e) {
            log.debug("未出现「选择简历」弹窗，按无弹窗流程继续校验");
        }

        Locator delivery = page.locator("a.a-attachment-select__action-btn__delivery");
        if (delivery.count() == 0) {
            // 没有确认按钮：要么已经投过（按钮已变），要么这一步压根没弹出来
            return verifyDelivered();
        }

        boolean clicked = false;
        try {
            delivery.first().click(new Locator.ClickOptions().setTimeout(3000));
            clicked = true;
        } catch (Exception e) {
            try {
                delivery.first().evaluate("e => e.click()");
                clicked = true;
            } catch (Exception e2) {
                log.warn("点击「投递简历」失败: {}", e.getMessage());
            }
        }
        if (!clicked) {
            return false;
        }
        PlaywrightUtil.sleep(2);
        return verifyDelivered();
    }

    /**
     * 校验投递是否真的成功 —— 光点按钮不算数。
     * 判据（实测得出）：
     *   1) 「立即投递」按钮文案变成「继续沟通」/「已投递」之类；
     *   2) 出现 deliver-greeting-modal（"已向对方发送简历和打招呼语"）。
     * 两条都不满足就判定失败，绝不落库、绝不计数。
     */
    private boolean verifyDelivered() {
        for (int i = 0; i < 8; i++) {
            try {
                Locator apply = page.locator("button.job-detail-summary__apply");
                if (apply.count() > 0) {
                    String text = trimOrNull(apply.first().textContent());
                    if (text != null && !text.contains("立即投递")) {
                        return true;
                    }
                }
                if (page.locator(".deliver-greeting-modal").count() > 0) {
                    return true;
                }
            } catch (Exception ignored) {
            }
            PlaywrightUtil.sleep(1);
        }
        return false;
    }

    private void closeBlockingOverlays() {
        // 上一次投递若卡在「选择简历」弹窗（未点确认），先关掉，否则会挡住后续所有点击
        try {
            Locator selClose = page.locator(".a-job-apply-workflow--attachment-select .a-dialog__close");
            if (selClose.count() > 0 && selClose.first().isVisible()) {
                selClose.first().click(new Locator.ClickOptions().setTimeout(2000));
                PlaywrightUtil.sleep(1);
            }
        } catch (Exception e) {
            log.debug("关闭选择简历弹窗失败: {}", e.getMessage());
        }

        try {
            Locator drawerClose = page.locator("button.banner-drawer__close");
            if (drawerClose.count() > 0 && drawerClose.first().isVisible()) {
                drawerClose.first().click(new Locator.ClickOptions().setTimeout(3000));
                PlaywrightUtil.sleep(1);
            }
        } catch (Exception e) {
            log.debug("关闭沟通抽屉失败: {}", e.getMessage());
        }

        try {
            Locator stay = page.locator("button.deliver-greeting-modal__btn--secondary");
            if (stay.count() > 0 && stay.first().isVisible()) {
                stay.first().click(new Locator.ClickOptions().setTimeout(3000));
                PlaywrightUtil.sleep(1);
                return;
            }
            Locator close = page.locator("button.deliver-greeting-modal__close");
            if (close.count() > 0 && close.first().isVisible()) {
                close.first().click(new Locator.ClickOptions().setTimeout(3000));
                PlaywrightUtil.sleep(1);
            }
        } catch (Exception e) {
            log.debug("关闭招呼语弹窗失败: {}", e.getMessage());
        }
    }

    /**
     * 从右侧详情面板读取职位详情链接（卡片上已经没有岗位链接了）。
     */
    private String readDetailJobLink() {
        try {
            Locator a = page.locator("div.job-detail-panel a[href*='jobdetail']").first();
            if (a.count() == 0) {
                return null;
            }
            return a.getAttribute("href");
        } catch (Exception e) {
            return null;
        }
    }

    private String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }


    /**
     * 处理投递弹窗
     */
    private void handleDeliveryDialog() {
        try {
            // 获取所有页面
            List<Page> pages = page.context().pages();
            if (pages.size() < 2) {
                log.warn("未检测到投递弹窗页面");
                return;
            }

            // 切换到最新打开的页面（投递弹窗）
            Page dialogPage = pages.get(pages.size() - 1);

            try {
                // 检查投递结果（CSS选择器）
                Locator deliverResult = dialogPage.locator("div.deliver-dialog");
                if (deliverResult.count() > 0) {
                    String text = deliverResult.textContent();
                    if (text != null && text.contains("申请成功")) {
                        log.info("岗位申请成功！");
                    }
                }
            } catch (Exception e) {
                log.debug("读取投递结果失败: {}", e.getMessage());
            }

            // 关闭弹窗
            try {
                Locator closeButton = dialogPage.locator("img[title='close-icon']");
                if (closeButton.count() > 0) {
                    closeButton.click();
                    PlaywrightUtil.sleep(1);
                }
            } catch (Exception e) {
                log.debug("关闭投递弹窗失败: {}", e.getMessage());
                if (checkIsLimit()) {
                    return;
                }
            }

            // 投递相似职位
            deliverSimilarJobs(dialogPage);

            // 关闭弹窗页面
            try {
                dialogPage.close();
            } catch (Exception e) {
                log.debug("关闭弹窗页面失败: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("处理投递弹窗失败", e);
        }
    }

    /**
     * 投递相似职位
     */
    private void deliverSimilarJobs(Page dialogPage) {
        try {
            // 全选相似职位
            Locator selectAllCheckbox = dialogPage.locator("div.applied-select-all input");
            if (selectAllCheckbox.count() > 0 && !selectAllCheckbox.isChecked()) {
                selectAllCheckbox.click();
                PlaywrightUtil.sleep(1);
            }

            // 获取相似职位列表
            Locator jobs = dialogPage.locator("div.recommend-job");
            int jobCount = jobs.count();

            if (jobCount == 0) {
                log.info("没有匹配到相似职位");
                return;
            }

            // 记录相似职位信息
            for (int i = 0; i < jobCount; i++) {
                try {
                    Locator jobElement = jobs.nth(i);
                    String jobName = safeGetText(jobElement, ".recommend-job__position");
                    String salary = safeGetText(jobElement, "span.recommend-job__demand__salary");
                    String years = safeGetText(jobElement, "span.recommend-job__demand__experience").replace("\n", " ");
                    String education = safeGetText(jobElement, "span.recommend-job__demand__educational").replace("\n", " ");
                    String companyName = safeGetText(jobElement, ".recommend-job__cname");
                    String companyTag = safeGetText(jobElement, ".recommend-job__demand__cinfo").replace("\n", " ");

                    Job job = new Job();
                    job.setJobName(jobName);
                    job.setSalary(salary);
                    job.setCompanyTag(companyTag);
                    job.setCompanyName(companyName);
                    job.setJobInfo(years + "·" + education);

                    log.info("投递【{}】公司【{}】岗位，薪资【{}】，要求【{}·{}】，规模【{}】",
                        companyName, jobName, salary, years, education, companyTag);
                    resultList.add(job);
                } catch (Exception e) {
                    log.debug("记录相似职位信息失败: {}", e.getMessage());
                }
            }

            // 点击投递按钮
            Locator postButton = dialogPage.locator("div.applied-select-all button");
            if (postButton.count() > 0) {
                postButton.click();
                PlaywrightUtil.sleep(2);
                log.info("相似职位投递成功！");
            }

        } catch (Exception e) {
            log.error("投递相似职位异常: {}", e.getMessage());
        }
    }

    /**
     * 检查是否达到投递上限
     */
    private boolean checkIsLimit() {
        try {
            PlaywrightUtil.sleep(1);
            Locator result = page.locator("//div[@class='a-job-apply-workflow']");
            if (result.count() > 0) {
                String text = result.textContent();
                if (text != null && text.contains("达到上限")) {
                    log.info("今日投递已达上限！");
                    isLimit = true;
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 设置最大页数（已废弃：使用“下一页不可点击”+最多50页）
     */
    private void setMaxPages() {
        // 保留方法避免旧调用报错，但不再依赖输入框改页码
        maxPage = 50;
    }

    /**
     * 构建基础搜索URL（不含关键词，由搜索框触发）
     */
    private String buildBaseUrl(int pageNum, String cityCode) {
        StringBuilder url = new StringBuilder(HOME_URL);
        url.append("jl").append(cityCode).append("/");
        url.append("p").append(pageNum).append("?");
        url.append(JobUtils.appendParam("sl", config.getSalary()));
        return url.toString();
    }

    /**
     * 查找搜索关键词输入框（多候选选择器，提高鲁棒性）
     */
    private Locator findKeywordInput() {
        String[] candidates = new String[] {
            "input[placeholder*='职位']",
            "input[placeholder*='公司']",
            "input[name='kw']",
            "input[type='text']",
            "input[class*='search'], input[class*='sou'], input[class*='input']"
        };
        for (String sel : candidates) {
            try {
                Locator lc = page.locator(sel);
                if (lc != null && lc.count() > 0) {
                    return lc.first();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 判断“下一页”是否不可点击
     */
    private boolean isNextDisabled() {
        try {
            Locator nextBtn = page.locator("a.soupager__btn:has-text(\"下一页\")");
            if (nextBtn.count() == 0) return true;
            String cls = null;
            try { cls = nextBtn.first().getAttribute("class"); } catch (Exception ignored) {}
            String disabledAttr = null;
            try { disabledAttr = nextBtn.first().getAttribute("disabled"); } catch (Exception ignored) {}
            if (cls != null && cls.contains("soupager__btn--disable")) return true;
            return disabledAttr != null && ("disabled".equalsIgnoreCase(disabledAttr) || "true".equalsIgnoreCase(disabledAttr));
        } catch (Exception e) {
            return false;
        }
    }

    private String extractJobIdFromLink(String link) {
        if (link == null) return null;
        try {
            int i = link.indexOf("jobdetail/");
            int j = link.lastIndexOf(".htm");
            if (i >= 0 && j > i) {
                return link.substring(i + "jobdetail/".length(), j);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 安全获取文本内容
     */
    private String safeGetText(Locator parent, String selector) {
        try {
            Locator element = parent.locator(selector);
            if (element.count() > 0) {
                return element.textContent();
            }
        } catch (Exception e) {
            log.debug("获取文本失败: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 格式化时长
     */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, seconds % 60);
        } else {
            return String.format("%d秒", seconds);
        }
    }

    /**
     * 将当前页面内容保存到项目内 page.html，覆盖原文件
     */
    private void saveCurrentPageHtml() {
        try {
            String html = page.content();
            java.nio.file.Path path = java.nio.file.Paths.get("src/main/java/com/getjobs/worker/zhilian/page.html");
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.write(path, html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("写入 page.html 失败", e);
        }
    }

    /**
     * 发送进度消息
     */
    private void sendProgress(String message, Integer current, Integer total) {
        if (progressCallback != null) {
            progressCallback.accept(message, current, total);
        }
    }

    /**
     * 检查是否应该停止
     */
    private boolean shouldStop() {
        return shouldStopCallback != null && shouldStopCallback.get();
    }
}
