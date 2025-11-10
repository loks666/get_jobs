export type SSEListener = { name: string; handler: (event: MessageEvent) => void };

export type BackoffConfig = {
  initialDelayMs?: number;
  maxDelayMs?: number;
  factor?: number;
  jitter?: boolean;
};

export type SSEBackoffOptions = {
  listeners: SSEListener[];
  onOpen?: () => void;
  onError?: (error: any, attempt: number, delayMs: number) => void;
  backoff?: BackoffConfig;
};

export type SSEBackoffClient = {
  close: () => void;
};

/**
 * 创建带指数退避的 EventSource 客户端，自动重连并在页面卸载时清理。
 */
export function createSSEWithBackoff(url: string, options: SSEBackoffOptions): SSEBackoffClient {
  const cfg: Required<BackoffConfig> = {
    initialDelayMs: options.backoff?.initialDelayMs ?? 1000,
    maxDelayMs: options.backoff?.maxDelayMs ?? 30000,
    factor: options.backoff?.factor ?? 1.7,
    jitter: options.backoff?.jitter ?? true,
  };

  let es: EventSource | null = null;
  let attempt = 0;
  let closed = false;
  let reconnectTimer: number | null = null;

  const computeDelay = (n: number) => {
    const base = Math.min(cfg.initialDelayMs * Math.pow(cfg.factor, Math.max(0, n - 1)), cfg.maxDelayMs);
    if (!cfg.jitter) return base;
    const jitter = base * 0.3 * Math.random();
    return Math.floor(base - jitter);
  };

  const cleanupReconnect = () => {
    if (reconnectTimer != null) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
  };

  const connect = () => {
    if (closed) return;
    try {
      es = new EventSource(url);
      // 注册事件监听
      options.listeners.forEach((l) => es?.addEventListener(l.name, l.handler));

      es.onopen = () => {
        attempt = 0; // 重置重试计数
        options.onOpen?.();
      };

      es.onerror = (error) => {
        // 显式关闭当前连接，计算退避延迟后重连
        try { es?.close(); } catch {}
        es = null;
        if (closed) return;
        const nextAttempt = attempt + 1;
        const delay = computeDelay(nextAttempt);
        options.onError?.(error, nextAttempt, delay);
        attempt = nextAttempt;
        cleanupReconnect();
        reconnectTimer = window.setTimeout(() => {
          connect();
        }, delay);
      };
    } catch (err) {
      if (closed) return;
      const nextAttempt = attempt + 1;
      const delay = computeDelay(nextAttempt);
      options.onError?.(err, nextAttempt, delay);
      attempt = nextAttempt;
      cleanupReconnect();
      reconnectTimer = window.setTimeout(() => {
        connect();
      }, delay);
    }
  };

  // 在客户端环境下启动连接
  if (typeof window !== 'undefined' && typeof EventSource !== 'undefined') {
    connect();
  }

  const handleBeforeUnload = () => {
    try { es?.close(); } catch {}
    cleanupReconnect();
    closed = true;
  };

  if (typeof window !== 'undefined') {
    window.addEventListener('beforeunload', handleBeforeUnload);
  }

  return {
    close() {
      if (typeof window !== 'undefined') {
        window.removeEventListener('beforeunload', handleBeforeUnload);
      }
      handleBeforeUnload();
    },
  };
}