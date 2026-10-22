package com.getjobs.worker.boss;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 仅使用路由拦截的模拟网页，不连接招聘网站，也不发送任何消息。 */
@EnabledIfEnvironmentVariable(named = "BOSS_CHAT_DOM_TEST", matches = "true")
class BossChatNavigatorDomTest {
    static Playwright playwright;
    static Browser browser;
    BrowserContext context;
    Page page;

    @BeforeAll static void launch() {
        var environment = new java.util.HashMap<>(System.getenv());
        environment.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(environment));
        String chrome = System.getenv("BOSS_TEST_CHROME");
        var options = new BrowserType.LaunchOptions().setHeadless(true);
        if (chrome != null) options.setExecutablePath(Path.of(chrome));
        browser = playwright.chromium().launch(options);
    }
    @AfterAll static void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
    @BeforeEach void setup() { context = browser.newContext(); page = context.newPage(); }
    @AfterEach void cleanup() { context.close(); }

    void fixture(boolean popup, boolean showModal, boolean navigate, boolean showInput) {
        String target = popup ? "window.open('/chat', '_blank')" : "location.href='/chat'";
        String go = navigate ? "setTimeout(()=>{" + target + "},200)" : "";
        String modal = """
                <div id="confirm-dialog" class="greet-boss-dialog">
                  <div class="greet-boss-layer"></div><div class="greet-boss-container">
                  <div class="greet-boss-header"><h3>已向BOSS发送消息</h3></div>
                  <div class="greet-boss-body"><p class="greet-con">测试消息</p></div>
                  <div class="greet-boss-footer"><a href="javascript:;" class="default-btn cancel-btn">留在此页</a>
                    <a href="javascript:;" class="default-btn sure-btn" id="continue" onclick="document.querySelector('#confirm-dialog').remove();%s">继续沟通</a>
                  </div></div>
                </div>
                """.formatted(go);
        // 弹框确认后，原页面下方也有可见输入框；必须等新聊天页，不能使用它。
        String onclick = showModal
                ? "setTimeout(()=>{document.querySelector('#modal').innerHTML=" + quote(modal)
                    + ";document.querySelector('#old-input').style.display='block'},100)"
                : go;
        String job = "<button id='job-button' onclick=\"" + onclick.replace("&", "&amp;").replace("\"", "&quot;")
                + "\">继续沟通</button><div id='modal'></div>"
                + "<textarea class='input-area' id='old-input' style='display:none'></textarea>";
        String chat = showInput
                ? "<p>新的聊天页面</p><div id='mount'></div><script>setTimeout(()=>{document.querySelector('#mount').innerHTML=\"<textarea class='input-area' id='new-input'></textarea>\"},150)</script>"
                : "<p>聊天页还没加载出输入框</p>";
        context.route("http://getjobs.test/**", route -> route.fulfill(new Route.FulfillOptions()
                .setContentType("text/html; charset=utf-8")
                .setBody(route.request().url().endsWith("/chat") ? chat : job)));
        page.navigate("http://getjobs.test/job");
    }

    static String quote(String text) {
        return org.json.JSONObject.quote(text);
    }

    @Test void firstContactCanStillUseInlineChatWithoutNavigation() {
        page.setContent("<button id='start' onclick=\"document.querySelector('#mount').innerHTML=&#34;&lt;textarea class='input-area'&gt;&lt;/textarea&gt;&#34;\">立即沟通</button><div id='mount'></div>");
        try (var navigator = new BossChatNavigator(page)) {
            var chat = navigator.open(page.locator("#start"), false, false, null, 3_000);
            assertNotNull(chat);
            assertFalse(chat.repeat());
            assertEquals(page, chat.page());
        }
    }

    @ParameterizedTest
    @CsvSource({"false,true", "true,true", "false,false", "true,false"})
    void entersReadyChatInSameTabOrPopup(boolean popup, boolean modal) {
        fixture(popup, modal, true, true);
        try (var navigator = new BossChatNavigator(page)) {
            var chat = navigator.open(page.locator("#job-button"), true, true, null, 5_000);
            assertNotNull(chat);
            assertTrue(chat.repeat());
            assertEquals("http://getjobs.test/chat", chat.page().url());
            assertEquals("new-input", chat.input().getAttribute("id"));
            assertEquals(popup, chat.page() != page);
        }
        assertTrue(context.pages().isEmpty());
    }

    @Test void confirmationDoesNotChangeFirstContactIntoRepeatChat() {
        fixture(false, true, true, true);
        try (var navigator = new BossChatNavigator(page)) {
            var chat = navigator.open(page.locator("#job-button"), false, true, null, 5_000);
            assertNotNull(chat);
            assertFalse(chat.repeat());
        }
    }

    @Test void disabledSwitchDoesNotConfirmModal() {
        fixture(false, true, true, true);
        try (var navigator = new BossChatNavigator(page)) {
            assertNull(navigator.open(page.locator("#job-button"), true, false, null, 3_000));
            assertEquals(0, page.locator("#confirm-dialog").count());
            assertEquals("http://getjobs.test/job", page.url());
        }
    }

    @Test void existingChatMustLeaveJobPageEvenIfAnInputIsVisible() {
        page.setContent("<button id='start'>继续沟通</button><textarea class='input-area'></textarea>");
        try (var navigator = new BossChatNavigator(page)) {
            assertNull(navigator.open(page.locator("#start"), true, true, null, 400));
        }
    }

    @Test void stopsLookingForModalAfterOneSecond() {
        page.setContent("""
                <button id="start" onclick="setTimeout(()=>{document.querySelector('#mount').innerHTML=
                '<div class=greet-boss-dialog><div class=greet-boss-footer><a class=sure-btn onclick=window.confirmClicked=true>继续沟通</a></div></div>'},1400)">立即沟通</button>
                <div id="mount"></div>
                """);
        try (var navigator = new BossChatNavigator(page)) {
            assertNull(navigator.open(page.locator("#start"), false, true, null, 2_100));
            assertEquals(1, page.locator(".greet-boss-dialog").count());
            assertNotEquals(true, page.evaluate("() => window.confirmClicked === true"));
        }
    }

    @Test void slowChatInputCanLoadAfterModalDetectionWindowEnds() {
        context.route("http://getjobs.test/slow-chat", route -> route.fulfill(new Route.FulfillOptions()
                .setContentType("text/html")
                .setBody("<div id='mount'></div><script>setTimeout(()=>{document.querySelector('#mount').innerHTML=\"<textarea class='input-area'></textarea>\"},1400)</script>")));
        page.setContent("<button id='start' onclick=\"location.href='http://getjobs.test/slow-chat'\">继续沟通</button>");
        try (var navigator = new BossChatNavigator(page)) {
            assertNotNull(navigator.open(page.locator("#start"), true, true, null, 3_000));
        }
    }

    @Test void doesNotUseOldInputWhenNavigationFails() {
        fixture(false, true, false, true);
        try (var navigator = new BossChatNavigator(page)) {
            assertNull(navigator.open(page.locator("#job-button"), true, true, null, 800));
        }
    }

    @Test void noInputOrStopDoesNotReturnReadyChat() {
        fixture(true, true, true, false);
        try (var navigator = new BossChatNavigator(page)) {
            assertNull(navigator.open(page.locator("#job-button"), true, true, null, 1_000));
        }
        assertTrue(context.pages().isEmpty());
        page = context.newPage();
        fixture(false, true, true, true);
        try (var navigator = new BossChatNavigator(page)) {
            assertNull(navigator.open(page.locator("#job-button"), true, true, () -> true, 1_000));
        }
    }
}
