package com.getjobs.worker.boss;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 岗位详情 → 已沟通确认框 → 聊天页。不在此处输入或发送消息。 */
@Slf4j
final class BossChatNavigator implements AutoCloseable {
    static final String INPUT = "div#chat-input.chat-input[contenteditable='true'], textarea.input-area";
    // 2026-10-21 从真实 Boss 页面 DOM 采集确认：两个沟通入口都可能出现此通知框。
    static final String GREET_CONFIRM = ".greet-boss-dialog:visible .greet-boss-footer a.sure-btn";
    // 先定位可见的“留在此页”，再取同时包含两个按钮的最近共同祖先。
    // 排除 body/html，不能退化为全页面点击同名的岗位按钮。
    static final String STAY = ":text-is('留在此页'):visible";
    static final String CONFIRM_CONTAINER =
            "xpath=ancestor::*[not(self::body or self::html) and .//*[normalize-space(.)='继续沟通']][1]";

    record Chat(Page page, Locator input, boolean repeat) {}

    private final Page origin;
    private final List<Page> popups = new ArrayList<>();
    private final Consumer<Page> popupListener = popups::add;

    BossChatNavigator(Page origin) {
        this.origin = origin;
        origin.onPopup(popupListener);
    }

    Chat open(Locator button, boolean repeat, boolean allowRepeat, Supplier<Boolean> stop, long timeoutMs) {
        if (stop != null && Boolean.TRUE.equals(stop.get())) return null;
        if (repeat && !allowRepeat) return null;
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        String entryUrl = origin.url();
        try {
            button.click(new Locator.ClickOptions().setTimeout(Math.min(timeoutMs, 5_000)));
        } catch (PlaywrightException navigationError) {
            log.debug("点击岗位按钮时发生页面切换，继续检查聊天入口：{}", navigationError.getMessage());
        }
        // 弹框只在点击后的1秒内检测；聊天页面加载仍使用独立的总超时。
        long confirmationDeadline = Math.min(deadline, System.nanoTime() + 1_000_000_000L);
        Page confirmationPage = null;
        String confirmationUrl = null;
        boolean confirmed = false;
        while (System.nanoTime() < deadline) {
            if (stop != null && Boolean.TRUE.equals(stop.get())) return null;
            List<Page> candidates = new ArrayList<>(popups);
            if (candidates.stream().allMatch(Page::isClosed)) candidates.add(origin);
            for (Page candidate : candidates) {
                if (candidate.isClosed()) continue;
                try {
                    Locator confirmation = !confirmed && System.nanoTime() < confirmationDeadline
                            ? findConfirmation(candidate) : null;
                    if (confirmation != null) {
                        // “已向BOSS发送消息”也是首次打招呼的结果通知，不能据此改成旧会话。
                        if (!confirmed) {
                            confirmationPage = candidate;
                            confirmationUrl = candidate.url();
                            // 每次只点击一次；弹框仍在或跳转未完成时继续等待，不能发送。
                            confirmed = true;
                            long remainingMs = (confirmationDeadline - System.nanoTime()) / 1_000_000L;
                            if (remainingMs <= 0) continue;
                            try {
                                confirmation.click(new Locator.ClickOptions().setTimeout(remainingMs));
                            } catch (PlaywrightException clickError) {
                                log.warn("弹框确认未在1秒检测窗口内完成，跳过当前岗位");
                                return null;
                            }
                            log.info("已点击已沟通确认框内的【继续沟通】，等待聊天页跳转");
                        }
                        continue;
                    }
                    if (confirmed && candidate == confirmationPage && candidate.url().equals(confirmationUrl)) {
                        continue;
                    }
                    if (repeat && candidate == origin && candidate.url().equals(entryUrl)) continue;
                    // 每轮新建定位器，避免导航后继续使用旧 frame 的输入框。
                    Locator input = candidate.locator(INPUT).first();
                    if (input.isVisible() && input.isEditable(new Locator.IsEditableOptions().setTimeout(500))) {
                        return new Chat(candidate, input, repeat);
                    }
                } catch (PlaywrightException navigationError) {
                    log.debug("聊天页正在切换，继续等待：{}", navigationError.getMessage());
                }
            }
            Page pump = !origin.isClosed() ? origin : popups.stream().filter(p -> !p.isClosed()).findFirst().orElse(null);
            if (pump == null) return null;
            try {
                pump.waitForTimeout(100);
            } catch (PlaywrightException navigationError) {
                log.debug("等待聊天页时页面已切换：{}", navigationError.getMessage());
            }
        }
        log.warn("等待聊天页超时，未发送消息；请检查是否存在未识别的弹框");
        return null;
    }

    private Locator findConfirmation(Page page) {
        Locator greetConfirm = page.locator(GREET_CONFIRM);
        if (greetConfirm.count() == 1 && greetConfirm.isVisible()
                && "继续沟通".equals(greetConfirm.textContent().trim())) return greetConfirm;
        Locator stays = page.locator(STAY);
        for (int i = 0; i < stays.count(); i++) {
            Locator container = stays.nth(i).locator(CONFIRM_CONTAINER);
            Locator confirm = container.getByText("继续沟通", new Locator.GetByTextOptions().setExact(true));
            if (confirm.count() == 1 && confirm.isVisible()) return confirm;
        }
        return null;
    }

    @Override
    public void close() {
        origin.offPopup(popupListener);
        // 只清理由当前岗位点击产生的新窗口，不影响其他招聘平台标签页。
        for (Page popup : popups) closePage(popup);
        closePage(origin);
    }

    private void closePage(Page page) {
        try {
            if (!page.isClosed()) page.close();
        } catch (PlaywrightException error) {
            log.debug("关闭本轮聊天页失败：{}", error.getMessage());
        }
    }
}
