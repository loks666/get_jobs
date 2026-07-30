(() => {
  "use strict";

  const nativeFunctionToString = Function.prototype.toString;
  const nativeSourceMap = new WeakMap();
  const registerNativeSource = (fn, source) => {
    try {
      nativeSourceMap.set(fn, source);
    } catch (_) {}
  };

  Object.defineProperty(Function.prototype, "toString", {
    configurable: true,
    writable: true,
    value: function toString() {
      return nativeSourceMap.get(this) || nativeFunctionToString.call(this);
    },
  });
  registerNativeSource(
    Function.prototype.toString,
    nativeFunctionToString.toString(),
  );

  const stealthify = (obj, prop, handler) => {
    const original = obj[prop];
    if (typeof original !== "function") return;

    const wrapped = function (...args) {
      return handler.call(this, original, args);
    };
    Object.defineProperty(wrapped, "name", {
      configurable: true,
      value: prop,
    });
    registerNativeSource(wrapped, nativeFunctionToString.call(original));
    Object.defineProperty(obj, prop, {
      ...Object.getOwnPropertyDescriptor(obj, prop),
      value: wrapped,
    });
  };

  const webdriverGetter = function webdriver() {
    return undefined;
  };
  registerNativeSource(
    webdriverGetter,
    "function get webdriver() { [native code] }",
  );
  try {
    Object.defineProperty(Navigator.prototype, "webdriver", {
      configurable: true,
      get: webdriverGetter,
    });
  } catch (_) {}

  const sanitizeConsoleArgs = (args) =>
    args.map((arg) => (arg && typeof arg === "object" ? {} : arg));
  ["log", "debug", "info", "warn", "error", "dir"].forEach((name) => {
    stealthify(console, name, (original, args) =>
      original.apply(console, sanitizeConsoleArgs(args)),
    );
  });

  const noopTable = function table() {};
  registerNativeSource(noopTable, "function table() { [native code] }");
  try {
    Object.defineProperty(console, "table", {
      configurable: true,
      writable: true,
      value: noopTable,
    });
  } catch (_) {}

  const performancePrototype = Object.getPrototypeOf(performance);
  const performanceNowDescriptor = Object.getOwnPropertyDescriptor(
    performancePrototype,
    "now",
  );
  if (typeof performanceNowDescriptor?.value === "function") {
    const nativePerformanceNow = performanceNowDescriptor.value;
    const timeOrigin = Number.isFinite(performance.timeOrigin)
      ? performance.timeOrigin
      : Date.now() - nativePerformanceNow.call(performance);
    let lastNow = Date.now() - timeOrigin;
    const monotonicNow = function now() {
      const current = Date.now() - timeOrigin;
      lastNow = current > lastNow ? current : lastNow + 0.000001;
      return lastNow;
    };
    registerNativeSource(
      monotonicNow,
      nativeFunctionToString.call(nativePerformanceNow),
    );
    try {
      Object.defineProperty(performancePrototype, "now", {
        ...performanceNowDescriptor,
        value: monotonicNow,
      });
    } catch (_) {}

    const contentWindowDescriptor = Object.getOwnPropertyDescriptor(
      HTMLIFrameElement.prototype,
      "contentWindow",
    );
    if (typeof contentWindowDescriptor?.get === "function") {
      const nativeContentWindowGetter = contentWindowDescriptor.get;
      const contentWindowGetter = function contentWindow() {
        const iframeWindow = nativeContentWindowGetter.call(this);
        try {
          Object.defineProperty(iframeWindow.console, "table", {
            configurable: true,
            writable: true,
            value: noopTable,
          });
          const iframePerformancePrototype = Object.getPrototypeOf(
            iframeWindow.performance,
          );
          const iframeNowDescriptor = Object.getOwnPropertyDescriptor(
            iframePerformancePrototype,
            "now",
          );
          if (typeof iframeNowDescriptor?.value === "function") {
            Object.defineProperty(iframePerformancePrototype, "now", {
              ...iframeNowDescriptor,
              value: monotonicNow,
            });
          }
          Object.defineProperty(
            iframeWindow.Function.prototype,
            "toString",
            Object.getOwnPropertyDescriptor(Function.prototype, "toString"),
          );
        } catch (_) {}
        return iframeWindow;
      };
      registerNativeSource(
        contentWindowGetter,
        nativeFunctionToString.call(nativeContentWindowGetter),
      );
      try {
        Object.defineProperty(HTMLIFrameElement.prototype, "contentWindow", {
          ...contentWindowDescriptor,
          get: contentWindowGetter,
        });
      } catch (_) {}
    }
  }

  registerNativeSource(
    registerNativeSource,
    "function registerNativeSource() { [native code] }",
  );
})();
