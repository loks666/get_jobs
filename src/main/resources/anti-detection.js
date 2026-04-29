(() => {
  'use strict';

  const nativeFunctionToString = Function.prototype.toString;
  const nativeSourceMap = new WeakMap();
  const registerNativeSource = (fn, source) => {
    try { nativeSourceMap.set(fn, source); } catch (_) {}
  };

  Object.defineProperty(Function.prototype, 'toString', {
    configurable: true,
    writable: true,
    value: function toString() {
      if (nativeSourceMap.has(this)) return nativeSourceMap.get(this);
      return nativeFunctionToString.call(this);
    },
  });
  registerNativeSource(Function.prototype.toString, nativeFunctionToString.toString());

  const nativeGetter = (obj, prop) => {
    try {
      const desc = Object.getOwnPropertyDescriptor(obj, prop);
      return desc && desc.get;
    } catch (_) {
      return null;
    }
  };

  const defineGetter = (obj, prop, getter) => {
    try {
      Object.defineProperty(obj, prop, { configurable: true, enumerable: true, get: getter });
      registerNativeSource(getter, `function get ${prop}() { [native code] }`);
    } catch (_) {}
  };

  const stealthify = (obj, prop, handler) => {
    const original = obj && obj[prop];
    if (typeof original !== 'function') return;
    const wrapped = function (...args) { return handler.call(this, original, args); };
    try { Object.defineProperty(wrapped, 'name', { value: prop, configurable: true }); } catch (_) {}
    try { Object.setPrototypeOf(wrapped, Object.getPrototypeOf(original)); } catch (_) {}
    registerNativeSource(wrapped, nativeFunctionToString.call(original));
    const desc = Object.getOwnPropertyDescriptor(obj, prop) || { configurable: true, writable: true };
    try { Object.defineProperty(obj, prop, { ...desc, value: wrapped }); } catch (_) {}
  };

  // webdriver / automation flags
  defineGetter(Navigator.prototype, 'webdriver', function webdriver() { return false; });
  defineGetter(Navigator.prototype, 'plugins', function plugins() { return [1, 2, 3, 4, 5]; });
  defineGetter(Navigator.prototype, 'languages', function languages() { return ['zh-CN', 'zh', 'en-US', 'en']; });
  defineGetter(Navigator.prototype, 'platform', function platform() { return 'MacIntel'; });

  try { delete window.__playwright; } catch (_) {}
  try { delete window.__pw_manual; } catch (_) {}
  try { delete window.__PW_inspect; } catch (_) {}

  // Boss/Chrome DevTools detection often relies on CDP serializing Error.stack or DOM getters.
  const nativeErrorStackGetter = nativeGetter(Error.prototype, 'stack');
  if (nativeErrorStackGetter) {
    defineGetter(Error.prototype, 'stack', function stack() {
      try { return nativeErrorStackGetter.call(this); } catch (_) { return ''; }
    });
  }

  const sanitizeConsoleArgs = (args) => args.map((arg) => {
    if (arg instanceof Error) return { name: arg.name, message: arg.message };
    if (arg && typeof arg === 'object') return String(arg);
    return arg;
  });
  ['log', 'debug', 'info', 'warn', 'error', 'dir', 'table'].forEach((name) => {
    stealthify(console, name, (original, args) => original.apply(console, sanitizeConsoleArgs(args)));
  });

  // Boss uses disable-devtool-like timing checks. The key hooks discussed in
  // loks666/get_jobs#250 are console.table and performance.now.
  const navStart = (performance && performance.timing && performance.timing.navigationStart) || Date.now();
  const noopTable = function table() {};
  registerNativeSource(noopTable, 'function table() { [native code] }');
  try {
    Object.defineProperty(console, 'table', {
      configurable: true,
      writable: true,
      value: noopTable,
    });
  } catch (_) {}

  const hookedPerformanceNow = function now() {
    return Date.now() - navStart;
  };
  registerNativeSource(hookedPerformanceNow, 'function now() { [native code] }');
  try {
    Object.defineProperty(performance, 'now', {
      configurable: true,
      writable: true,
      value: hookedPerformanceNow,
    });
  } catch (_) {}

  // Permissions API consistency
  if (navigator.permissions && navigator.permissions.query) {
    stealthify(navigator.permissions, 'query', (original, args) => {
      const params = args && args[0];
      if (params && params.name === 'notifications') {
        return Promise.resolve({ state: Notification.permission });
      }
      return original.apply(navigator.permissions, args);
    });
  }

  // Chrome runtime shape
  if (!window.chrome) {
    try { Object.defineProperty(window, 'chrome', { configurable: true, value: {} }); } catch (_) {}
  }
  if (window.chrome && !window.chrome.runtime) {
    try { Object.defineProperty(window.chrome, 'runtime', { configurable: true, value: {} }); } catch (_) {}
  }

  // Propagate the two critical hooks to same-origin iframes accessed through contentWindow.
  try {
    const descriptor = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, 'contentWindow');
    if (descriptor && descriptor.get) {
      const originalContentWindowGetter = descriptor.get;
      const hookedContentWindowGetter = function contentWindow() {
        const iframeWindow = originalContentWindowGetter.call(this);
        try {
          if (iframeWindow && iframeWindow.console) iframeWindow.console.table = noopTable;
          if (iframeWindow && iframeWindow.performance) iframeWindow.performance.now = hookedPerformanceNow;
        } catch (_) {}
        return iframeWindow;
      };
      registerNativeSource(hookedContentWindowGetter, 'function get contentWindow() { [native code] }');
      Object.defineProperty(HTMLIFrameElement.prototype, 'contentWindow', {
        configurable: true,
        get: hookedContentWindowGetter,
      });
    }
  } catch (_) {}

  registerNativeSource(registerNativeSource, 'function registerNativeSource() { [native code] }');
})();
