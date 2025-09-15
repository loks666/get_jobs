// Boss 求职配置只读视图（导出创建函数，由 app.js 调用）
(function () {
    if (!window.Views) window.Views = {};

    function createBossConfigReadonlyApp() {
        const { createApp } = window.Vue || {};
        if (!createApp) {
            console.error('Vue.js未加载');
            return null;
        }

        const mountElement = document.getElementById('bossConfigApp');
        if (!mountElement) {
            console.error('找不到挂载元素 #bossConfigApp');
            return null;
        }

        const app = createApp({
            data() {
                return {
                    config: {},
                    loading: false,
                    error: '',
                    initialized: false
                };
            },
            computed: {
                hasData() {
                    return this.config && Object.keys(this.config).length > 0;
                }
            },
            methods: {
                async load(force = false) {
                    if (this.loading && !force) return;
                    
                    this.loading = true;
                    this.error = '';

                    try {
                        const res = await fetch('/api/config/boss', {
                            headers: {
                                'Accept': 'application/json',
                                'Cache-Control': force ? 'no-cache' : 'default'
                            }
                        });

                        if (!res.ok) {
                            throw new Error(`请求失败: HTTP ${res.status}`);
                        }

                        const data = await res.json();
                        this.config = data || {};
                        
                    } catch (err) {
                        console.error('加载配置失败:', err);
                        this.error = err?.message || '加载失败，请稍后重试';
                        this.config = {};
                    } finally {
                        this.loading = false;
                    }
                },

                // 格式化工具方法
                safe(v) {
                    if (v == null) return '';
                    return Array.isArray(v) ? v.join('、') : String(v);
                },

                join(v) {
                    if (v == null) return '';
                    return Array.isArray(v) ? v.join('、') : String(v);
                },

                formatDateTime(input) {
                    if (!input) return '';
                    try {
                        const d = new Date(input);
                        if (isNaN(d.getTime())) return '';
                        
                        const pad = n => String(n).padStart(2, '0');
                        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
                    } catch (err) {
                        console.warn('日期格式化失败:', err);
                        return '';
                    }
                },

                formatKVList(obj) {
                    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return '';
                    return Object.entries(obj)
                        .map(([k, v]) => `${k} - ${v}`)
                        .join('<br>');
                },

                // Tab 切换处理
                handleTabShow() {
                    if (!this.initialized) {
                        this.load(true);
                        this.initialized = true;
                    } else {
                        this.load(false);
                    }
                }
            },
            mounted() {
                // 确保Bootstrap已加载
                if (typeof bootstrap === 'undefined') {
                    console.error('Bootstrap未加载');
                    return;
                }

                // 设置tab事件监听
                const tabElement = document.getElementById('boss-current-tab');
                if (tabElement) {
                    tabElement.addEventListener('shown.bs.tab', this.handleTabShow);
                }

                // 如果当前tab是激活状态，立即加载数据
                const pane = document.getElementById('boss-current-pane');
                if (pane?.classList.contains('active') && pane?.classList.contains('show')) {
                    this.handleTabShow();
                }
            },
            unmounted() {
                // 清理事件监听
                const tabElement = document.getElementById('boss-current-tab');
                if (tabElement) {
                    tabElement.removeEventListener('shown.bs.tab', this.handleTabShow);
                }
            }
        });

        return app.mount('#bossConfigApp');
    }

    window.Views.createBossConfigReadonlyApp = createBossConfigReadonlyApp;
})();


