package com.getjobs.application.service;

import com.getjobs.worker.utils.Bot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

/**
 * 企业微信「消息推送」通知服务。
 *
 * <p>原先 {@link Bot} 的 sendMessage 在全项目没有任何调用点，属于死代码 ——
 * 即使在页面上填了 HOOK_URL，任务跑完也不会有任何消息。本服务把投递过程中的
 * 关键节点统一收拢到这里，再交给 Bot 发送。</p>
 *
 * <p>推送的内容遵循「只推送值得看的消息」原则：</p>
 * <ul>
 *   <li>任务完成汇总（投递数、耗时）—— 一定会推</li>
 *   <li>需要人工介入的异常（未登录、日投递上限、验证码、任务失败）—— 一定会推</li>
 *   <li>任务开始 —— 默认不推，避免和紧随其后的完成消息连着刷屏，可用 BOT_NOTIFY_START 开启</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Bot bot;
    private final ConfigService configService;

    /**
     * 投递任务完成。
     *
     * @param platform   平台名称，如「Boss直聘」
     * @param count      本次投递数量
     * @param durationMs 耗时（毫秒）
     */
    public void deliveryFinished(String platform, int count, long durationMs) {
        StringBuilder sb = new StringBuilder();
        if (count <= 0) {
            // 0 投递几乎总是异常信号，不能按"顺利完成"来报，
            // 否则用户会以为程序正常、只是恰好没有新岗位。
            sb.append("【getJobs·需处理】").append(platform).append(" 投递完成，但数量为 0\n");
            sb.append("常见原因：登录已失效（需重新扫码）、搜索页结构变化、关键词无匹配结果。\n");
        } else {
            sb.append("【get_jobs】").append(platform).append(" 投递完成\n");
        }
        sb.append("本次投递：").append(count).append(" 个\n");
        sb.append("耗时：").append(formatDuration(durationMs)).append("\n");
        sb.append("时间：").append(LocalDateTime.now().format(CLOCK));
        send(sb.toString());
    }

    /**
     * 需要人工介入的异常。例如未登录、触发风控验证码、达到日投递上限。
     */
    public void needsAttention(String platform, String title, String detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("【getJobs·需处理】").append(platform).append("\n");
        sb.append(title).append("\n");
        if (detail != null && !detail.isBlank()) {
            sb.append("详情：").append(clip(detail, 300)).append("\n");
        }
        sb.append("时间：").append(LocalDateTime.now().format(CLOCK));
        send(sb.toString());
    }

    /**
     * 投递任务失败（异常中断）。
     */
    public void deliveryFailed(String platform, String reason) {
        needsAttention(platform, "投递任务执行失败", reason);
    }

    /**
     * 投递任务开始。默认关闭，需在环境变量配置页把 BOT_NOTIFY_START 设为 1 才会推送。
     */
    public void deliveryStarted(String platform, Collection<String> keywords, Collection<String> cities) {
        if (!isStartNotifyEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【getJobs】").append(platform).append(" 开始投递\n");
        if (keywords != null && !keywords.isEmpty()) {
            sb.append("关键词：").append(join(keywords)).append("\n");
        }
        if (cities != null && !cities.isEmpty()) {
            sb.append("城市：").append(join(cities)).append("\n");
        }
        sb.append("时间：").append(LocalDateTime.now().format(CLOCK));
        send(sb.toString());
    }

    /**
     * 发送一条自定义消息（用于「测试推送」按钮验证配置是否正确）。
     */
    public void sendTestMessage(String extra) {
        StringBuilder sb = new StringBuilder();
        sb.append("【getJobs】推送测试\n");
        sb.append("如果你在群里看到这条消息，说明 Webhook 配置成功。\n");
        if (extra != null && !extra.isBlank()) {
            sb.append(extra).append("\n");
        }
        sb.append("时间：").append(LocalDateTime.now().format(CLOCK));
        send(sb.toString());
    }

    /**
     * 是否配置了可用的 Webhook。注意这里只用于给用户提示，
     * 真正的开关判断在 Bot 内部（每次发送前会重新读取配置）。
     */
    public boolean isConfigured() {
        try {
            String url = configService.getConfigValue("HOOK_URL");
            return url != null && !url.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isStartNotifyEnabled() {
        try {
            String v = configService.getConfigValue("BOT_NOTIFY_START");
            return "1".equals(v) || "true".equalsIgnoreCase(v);
        } catch (Exception e) {
            return false;
        }
    }

    private void send(String message) {
        try {
            // Bot 会在发送前重新读取 HOOK_URL / BOT_IS_SEND，
            // 未配置或开关关闭时会自行跳过，这里无需再判断。
            Bot.sendMessageByTime(message);
            log.info("已推送企业微信通知");
        } catch (Exception e) {
            // 推送失败绝不能影响投递主流程
            log.warn("推送企业微信通知失败: {}", e.getMessage());
        }
    }

    private static String join(Collection<String> items) {
        if (items == null) return "";
        List<String> list = items.stream()
                .filter(s -> s != null && !s.isBlank())
                .limit(10)
                .toList();
        if (list.isEmpty()) return "";
        String joined = String.join("、", list);
        return items.size() > 10 ? joined + " 等" + items.size() + "项" : joined;
    }

    private static String clip(String s, int max) {
        String v = s.replaceAll("\\s+", " ").trim();
        return v.length() <= max ? v : v.substring(0, max) + "...";
    }

    private static String formatDuration(long ms) {
        if (ms <= 0) return "不足 1 秒";
        long sec = ms / 1000;
        if (sec < 60) return sec + " 秒";
        long min = sec / 60;
        long rest = sec % 60;
        if (min < 60) return min + " 分 " + rest + " 秒";
        return (min / 60) + " 小时 " + (min % 60) + " 分";
    }
}
