package com.getjobs.worker.manager;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossAntiDetectionScriptTest {
    @Test
    void hidesKnownBossDetectionSignalsAtRuntime() throws IOException {
        String script;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("anti-detection.js")) {
            assertNotNull(input);
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true)
            );
            try {
                BrowserContext context = browser.newContext();
                context.addInitScript(script);
                Page page = context.newPage();

                @SuppressWarnings("unchecked")
                Map<String, Object> signals = (Map<String, Object>) page.evaluate("""
                        () => {
                          let consoleGetterCalled = false;
                          const consoleProbe = {};
                          Object.defineProperty(consoleProbe, "value", {
                            enumerable: true,
                            get() {
                              consoleGetterCalled = true;
                              return "detected";
                            },
                          });
                          console.table(consoleProbe);

                          const wallStart = Date.now();
                          const performanceStart = performance.now();
                          for (let index = 0; index < 100000; index += 1) {
                            performance.now();
                          }
                          const wallElapsed = Date.now() - wallStart;
                          const performanceElapsed = performance.now() - performanceStart;

                          const iframe = document.createElement("iframe");
                          document.body.appendChild(iframe);
                          let iframeConsoleGetterCalled = false;
                          const iframeConsoleProbe = {};
                          Object.defineProperty(iframeConsoleProbe, "value", {
                            enumerable: true,
                            get() {
                              iframeConsoleGetterCalled = true;
                              return "detected";
                            },
                          });
                          iframe.contentWindow.console.table(iframeConsoleProbe);

                          return {
                            webdriverHidden: navigator.webdriver === undefined,
                            consoleTableSafe: !consoleGetterCalled,
                            iframeConsoleTableSafe: !iframeConsoleGetterCalled,
                            iframePerformanceNowNative:
                              iframe.contentWindow.Function.prototype.toString
                                .call(iframe.contentWindow.performance.now)
                                .includes("[native code]"),
                            performanceNowOwn: Object.prototype.hasOwnProperty.call(
                              performance,
                              "now",
                            ),
                            performanceNowNative: Function.prototype.toString
                              .call(performance.now)
                              .includes("[native code]"),
                            elapsedDifference: Math.abs(
                              performanceElapsed - wallElapsed,
                            ),
                          };
                        }
                        """);

                assertTrue((Boolean) signals.get("webdriverHidden"));
                assertTrue((Boolean) signals.get("consoleTableSafe"));
                assertTrue((Boolean) signals.get("iframeConsoleTableSafe"));
                assertTrue((Boolean) signals.get("iframePerformanceNowNative"));
                assertFalse((Boolean) signals.get("performanceNowOwn"));
                assertTrue((Boolean) signals.get("performanceNowNative"));
                assertTrue(((Number) signals.get("elapsedDifference")).doubleValue() < 100);
            } finally {
                browser.close();
            }
        }
    }

    @Test
    void injectsScriptOnlyOnBossHosts() throws IOException {
        String script;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("anti-detection.js")) {
            assertNotNull(input);
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true)
            );
            try {
                BrowserContext context = browser.newContext();
                context.addInitScript(PlaywrightManager.wrapBossInitScript(script));
                context.route("**/*", route -> route.fulfill(
                        new com.microsoft.playwright.Route.FulfillOptions()
                                .setContentType("text/html")
                                .setBody("<html><body>offline</body></html>")
                ));

                for (String host : List.of(
                        "www.zhipin.com",
                        "www.liepin.com",
                        "www.51job.com",
                        "www.zhaopin.com"
                )) {
                    Page page = context.newPage();
                    page.navigate("https://" + host + "/");
                    boolean injected = (Boolean) page.evaluate(
                            "() => window.__bossAntiDetectInjected === true"
                    );
                    assertEquals(host.endsWith(".zhipin.com"), injected, host);
                    page.close();
                }
            } finally {
                browser.close();
            }
        }
    }
}
