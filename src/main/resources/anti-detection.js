(() => {
  "use strict";

  const defineGetter = (obj, prop, getter) => {
    try {
      Object.defineProperty(obj, prop, { get: getter, configurable: true });
    } catch (_) {}
  };

  /* -------------------------------------------------------
   * 1. 核心：隐藏 navigator.webdriver（自动化头号特征）
   *    Boss 检测到 webdriver===true 会把登录页直接回退
   * ----------------------------------------------------- */
  try {
    Object.defineProperty(Navigator.prototype, "webdriver", {
      get: () => undefined,
      configurable: true,
    });
  } catch (_) {}
  try {
    Object.defineProperty(navigator, "webdriver", {
      get: () => undefined,
      configurable: true,
    });
  } catch (_) {}
  try { delete Navigator.prototype.webdriver; } catch (_) {}

  /* -------------------------------------------------------
   * 2. window.chrome 运行时桩（真实 Chrome 才有）
   * ----------------------------------------------------- */
  try {
    window.chrome = window.chrome || {};
    window.chrome.runtime = window.chrome.runtime || {};
    window.chrome.app = window.chrome.app || {
      isInstalled: false,
      InstallState: { DISABLED: "disabled", INSTALLED: "installed", NOT_INSTALLED: "not_installed" },
      RunningState: { CANNOT_RUN: "cannot_run", READY_TO_RUN: "ready_to_run", RUNNING: "running" },
    };
    window.chrome.csi = window.chrome.csi || function () { return {}; };
    window.chrome.loadTimes = window.chrome.loadTimes || function () { return {}; };
  } catch (_) {}

  /* -------------------------------------------------------
   * 3. navigator.languages
   * ----------------------------------------------------- */
  defineGetter(navigator, "languages", () => ["zh-CN", "zh"]);

  /* -------------------------------------------------------
   * 4. navigator.plugins / mimeTypes 非空（无头/自动化常为空）
   * ----------------------------------------------------- */
  try {
    const makePlugin = (name, filename, description) => ({ name, filename, description, length: 1 });
    const pluginData = [
      makePlugin("PDF Viewer", "internal-pdf-viewer", "Portable Document Format"),
      makePlugin("Chrome PDF Viewer", "internal-pdf-viewer", "Portable Document Format"),
      makePlugin("Chromium PDF Viewer", "internal-pdf-viewer", "Portable Document Format"),
    ];
    defineGetter(navigator, "plugins", () => {
      const arr = pluginData.slice();
      arr.item = (i) => arr[i];
      arr.namedItem = (n) => arr.find((p) => p.name === n) || null;
      arr.refresh = () => {};
      return arr;
    });
    defineGetter(navigator, "mimeTypes", () => {
      const arr = [{ type: "application/pdf", suffixes: "pdf", description: "" }];
      arr.item = (i) => arr[i];
      arr.namedItem = (n) => arr.find((m) => m.type === n) || null;
      return arr;
    });
  } catch (_) {}

  /* -------------------------------------------------------
   * 5. permissions.query 一致性（notifications）
   * ----------------------------------------------------- */
  try {
    if (navigator.permissions && navigator.permissions.query) {
      const originalQuery = navigator.permissions.query.bind(navigator.permissions);
      navigator.permissions.query = (parameters) => {
        if (parameters && parameters.name === "notifications") {
          return Promise.resolve({
            state: (typeof Notification !== "undefined" && Notification.permission) || "default",
            onchange: null,
          });
        }
        return originalQuery(parameters);
      };
    }
  } catch (_) {}

  /* -------------------------------------------------------
   * 6. WebGL vendor / renderer 伪装（避免暴露 SwiftShader 等）
   * ----------------------------------------------------- */
  try {
    const patchGL = (proto) => {
      if (!proto || !proto.getParameter) return;
      const original = proto.getParameter;
      proto.getParameter = function (parameter) {
        if (parameter === 37445) return "Intel Inc.";                 // UNMASKED_VENDOR_WEBGL
        if (parameter === 37446) return "Intel Iris OpenGL Engine";    // UNMASKED_RENDERER_WEBGL
        return original.call(this, parameter);
      };
    };
    if (window.WebGLRenderingContext) patchGL(WebGLRenderingContext.prototype);
    if (window.WebGL2RenderingContext) patchGL(WebGL2RenderingContext.prototype);
  } catch (_) {}

  /* -------------------------------------------------------
   * 7. 合理化硬件特征
   * ----------------------------------------------------- */
  try { defineGetter(navigator, "hardwareConcurrency", () => 8); } catch (_) {}
  try { defineGetter(navigator, "deviceMemory", () => 8); } catch (_) {}

  /* -------------------------------------------------------
   * 8. 伪装 Function.prototype.toString，使上面的 patch 看起来像原生
   * ----------------------------------------------------- */
  try {
    const nativeFunctionToString = Function.prototype.toString;
    const nativeSourceMap = new WeakMap();
    Object.defineProperty(Function.prototype, "toString", {
      configurable: true,
      writable: true,
      value: function toString() {
        if (nativeSourceMap.has(this)) return nativeSourceMap.get(this);
        return nativeFunctionToString.call(this);
      },
    });
  } catch (_) {}

  /* -------------------------------------------------------
   * 9. console 降噪：避免 DevTools/CDP 展开对象（保留原有行为）
   * ----------------------------------------------------- */
  try {
    const filterConsoleArgs = (args) =>
      args.map((arg) => (arg && typeof arg === "object" ? {} : arg));
    ["log", "debug", "info", "warn", "error", "dir", "table"].forEach((name) => {
      const original = console[name];
      if (typeof original === "function") {
        console[name] = function (...args) {
          return original.apply(console, filterConsoleArgs(args));
        };
      }
    });
  } catch (_) {}
})();
