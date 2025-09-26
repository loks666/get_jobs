// Boss直聘配置应用主脚本
class BossConfigApp {
    constructor() {
        this.config = {};
        this.isRunning = false;
        this.isDataLoading = false; // 添加数据加载状态标识
        this.init();
    }

    init() {
        CommonUtils.initializeTooltips();
        this.bindEvents();
        // 先加载字典数据，再加载配置数据，确保下拉框已准备好
        this.loadDataSequentially();
    }

    // 绑定事件
    bindEvents() {
        // 保存配置按钮
        const saveConfigBtn = document.getElementById('saveConfigBtn');
        if (saveConfigBtn) {
            saveConfigBtn.addEventListener('click', () => {
                this.handleSaveOnly();
            });
            console.log('已绑定保存配置按钮事件');
        } else {
            console.warn('未找到保存配置按钮元素');
        }

        // 数据库备份按钮
        const backupDataBtn = document.getElementById('backupDataBtn');
        if (backupDataBtn) {
            backupDataBtn.addEventListener('click', () => {
                this.handleBackupData();
            });
            console.log('已绑定数据库备份按钮事件');
        } else {
            console.warn('未找到数据库备份按钮元素');
        }

        // 求职配置视图：绑定标签切换加载与刷新
        this.bindBossConfigViewEvents();
    }


    // =====================
    // 求职配置只读视图
    // =====================
    bindBossConfigViewEvents() {
        const self = this;
        document.getElementById('boss-current-tab')?.addEventListener('shown.bs.tab', function () {
            console.log('求职配置tab已显示');
            // 切换到"求职配置"页签时，触发 Vue 视图加载
            if (window.bossConfigView && typeof window.bossConfigView.load === 'function') {
                console.log('调用Vue应用的load方法');
                window.bossConfigView.load(false);
            } else {
                console.log('Vue应用未找到，检查初始化');
                // 兼容：若 Vue 尚未初始化，保底旧渲染（基本不会走到）
                self.loadBossConfigView();
            }
        });
    }

    loadBossConfigView(force = false) {
        // 已废弃：现在使用Vue应用来处理配置展示
        console.log('loadBossConfigView方法已废弃，现在使用Vue应用');
    }

    renderBossConfigView(container, data) {
        // 已废弃：现在使用Vue应用来处理配置展示
        console.log('renderBossConfigView方法已废弃，现在使用Vue应用');
    }



    // 按顺序加载数据：先字典，后配置
    async loadDataSequentially() {
        this.isDataLoading = true; // 设置数据加载状态
        try {
            console.log('App.js: 开始按顺序加载数据：等待字典事件 -> 等待配置事件');
            // 优先等待由 boss-config-form.js 分发的字典加载完成事件，最短等待，避免重复请求
            await new Promise((resolve) => {
                let resolved = false;
                const handler = () => {
                    if (resolved) return;
                    resolved = true;
                    window.removeEventListener('bossDictDataLoaded', handler);
                    resolve();
                };
                window.addEventListener('bossDictDataLoaded', handler, { once: true });
                // 超时快速继续，避免阻塞
                setTimeout(() => {
                    if (resolved) return;
                    window.removeEventListener('bossDictDataLoaded', handler);
                    resolve();
                }, 1500);
            });
            
            console.log('App.js: 等待boss-config-form.js加载配置数据');
            await this.waitForBossConfigLoaded();
            console.log('App.js: 配置数据加载完成');
        } catch (error) {
            console.error('App.js: 数据加载失败:', error);
        } finally {
            this.isDataLoading = false; // 数据加载完成，清除加载状态
        }
    }

    // 等待boss-config-form.js加载配置数据完成，避免重复请求
    async waitForBossConfigLoaded() {
        return new Promise((resolve) => {
            let resolved = false;
            const handler = (event) => {
                if (resolved) return;
                resolved = true;
                try {
                    // 从boss-config-form.js获取已加载的配置数据
                    const configData = event.detail && event.detail.config ? event.detail.config : {};
                    if (configData && Object.keys(configData).length > 0) {
                        console.log('App.js: 从boss-config-form.js获取配置数据:', configData);
                        this.config = configData;
                        this.populateForm();
                    } else {
                        console.log('App.js: boss-config-form.js未提供配置数据，尝试本地缓存');
                        this.loadFromLocalStorage();
                    }
                } catch (error) {
                    console.warn('App.js: 处理配置数据时出错:', error);
                    this.loadFromLocalStorage();
                } finally {
                    window.removeEventListener('bossConfigLoaded', handler);
                    resolve();
                }
            };
            
            window.addEventListener('bossConfigLoaded', handler, { once: true });
            
            // 超时回退到本地缓存
            setTimeout(() => {
                if (resolved) return;
                resolved = true;
                window.removeEventListener('bossConfigLoaded', handler);
                console.log('App.js: 等待配置事件超时，使用本地缓存');
                this.loadFromLocalStorage();
                resolve();
            }, 2000);
        });
    }

    // 从本地缓存加载配置
    loadFromLocalStorage() {
        try {
            const savedConfig = localStorage.getItem('bossConfig');
            if (savedConfig) {
                this.config = JSON.parse(savedConfig);
                console.log('App.js: 从本地缓存加载配置完成');
            }
        } catch (error) {
            console.warn('App.js: 本地缓存配置损坏，已清理:', error.message);
            localStorage.removeItem('bossConfig');
        }
    }


    // 处理数据库备份
    handleBackupData() {
        const backupBtn = document.getElementById('backupDataBtn');
        if (backupBtn) {
            backupBtn.disabled = true;
            backupBtn.innerHTML = '<i class="bi bi-hourglass-split me-2"></i>备份中...';
        }

        fetch('/api/backup/export', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                CommonUtils.showToast('数据库备份成功', 'success');
                console.log('备份路径:', data.backupPath);
            } else {
                CommonUtils.showToast('数据库备份失败: ' + data.message, 'danger');
            }
        })
        .catch(error => {
            console.error('备份请求失败:', error);
            CommonUtils.showToast('数据库备份失败: ' + error.message, 'danger');
        })
        .finally(() => {
            if (backupBtn) {
                backupBtn.disabled = false;
                backupBtn.innerHTML = '<i class="bi bi-database me-2"></i>数据库备份';
            }
        });
    }



}

// 页面加载完成后初始化应用（原配置表单/记录页逻辑）
document.addEventListener('DOMContentLoaded', () => {
    // 先初始化Boss配置表单（负责字典加载与事件分发）
    if (window.Views && window.Views.BossConfigForm) {
        window.bossConfigFormApp = new window.Views.BossConfigForm();
    }
    
    // 再初始化主应用（会等待字典事件）
    window.bossConfigApp = new BossConfigApp();
    
    // 初始化智联配置表单
    if (window.ZhilianConfigForm) {
        window.zhilianConfigApp = new ZhilianConfigForm();
    }
    
    // 初始化51job配置表单
    if (window.Job51ConfigForm) {
        window.job51ConfigApp = new Job51ConfigForm();
    }

    // 初始化猎聘配置表单
    if (window.Views && window.Views.LiepinConfigForm) {
        window.liepinConfigApp = new window.Views.LiepinConfigForm();
    }

    // 初始化猎聘相关功能
    if (document.getElementById('liepin-config-pane')) {
        console.log('Initializing Liepin module');
        initLiepinConfigForm();
        initLiepinRecordsVue();
    }

    // Boss字典加载逻辑已移至 Views.BossConfigForm 中

    // =============================
    // 初始化"求职配置"Vue 视图
    // =============================
    try {
        const { createApp } = window.Vue || {};
        if (createApp && document.getElementById('bossConfigApp')) {
            const app = createApp({
                data() {
                    return {
                        config: {},
                        loading: false,
                        error: '',
                        cityNameByCode: {},
                    };
                },
                computed: {
                    hasData() {
                        // 检查config是否存在且不为空对象
                        if (!this.config || typeof this.config !== 'object') {
                            console.log('hasData: config不存在或不是对象');
                            return false;
                        }
                        const keys = Object.keys(this.config);
                        console.log('hasData: config键数量=', keys.length, '键:', keys);
                        // 只要有任何有效数据就认为有数据，不需要检查所有字段都有值
                        return keys.length > 0;
                    }
                },
                methods: {
                    async loadDict() {
                        // 优先复用 boss-config-form.js 分发的字典事件，避免重复请求
                        await new Promise((resolve) => {
                            let resolved = false;
                            const handler = (e) => {
                                if (resolved) return;
                                resolved = true;
                                try {
                                    const detail = e && e.detail ? e.detail : {};
                                    const groupMap = detail.groupMap;
                                    // groupMap 可能是 Map，也可能是普通对象（容错处理）
                                    let cityItems = [];
                                    if (groupMap && typeof groupMap.get === 'function') {
                                        cityItems = groupMap.get('cityList') || [];
                                    } else if (groupMap && typeof groupMap === 'object') {
                                        cityItems = groupMap['cityList'] || [];
                                    }
                                    const map = {};
                                    (Array.isArray(cityItems) ? cityItems : []).forEach(it => {
                                        if (it && it.code) map[String(it.code)] = it.name || String(it.code);
                                    });
                                    this.cityNameByCode = map;
                                } catch (_) {
                                    this.cityNameByCode = {};
                                }
                                window.removeEventListener('bossDictDataLoaded', handler);
                                resolve();
                            };
                            window.addEventListener('bossDictDataLoaded', handler, { once: true });
                            // 超时快速回退到本地fetch，避免阻塞
                            setTimeout(async () => {
                                if (resolved) return;
                                window.removeEventListener('bossDictDataLoaded', handler);
                                try {
                                    const res = await fetch('/dicts/BOSS_ZHIPIN');
                                    if (res.ok) {
                                        const data = await res.json();
                                        const cityGroup = (data && Array.isArray(data.groups)) ? data.groups.find(g => g.key === 'cityList') : null;
                                        const map = {};
                                        (Array.isArray(cityGroup?.items) ? cityGroup.items : []).forEach(it => {
                                            if (it && it.code) map[String(it.code)] = it.name || String(it.code);
                                        });
                                        this.cityNameByCode = map;
                                    } else {
                                        this.cityNameByCode = {};
                                    }
                                } catch (_) {
                                    this.cityNameByCode = {};
                                } finally {
                                    resolved = true;
                                    resolve();
                                }
                            }, 800);
                        });
                    },
                    async load(force = false) {
                        console.log('开始加载配置数据');
                        this.loading = true;
                        this.error = '';
                        try {
                            // 预先加载城市字典（不阻塞后续渲染）
                            await this.loadDict();
                            const res = await fetch('/api/config/boss');
                            console.log('API响应状态:', res.status);
                            if (!res.ok) throw new Error('HTTP ' + res.status);
                            const ct = res.headers.get('content-type') || '';
                            console.log('Content-Type:', ct);
                            const text = await res.text();
                            console.log('响应文本长度:', text.length);
                            // 仅当明确为 JSON 时解析；否则直接报错并给出片段提示，避免 JSON.parse 抛出 SyntaxError
                            if (!ct.includes('application/json')) {
                                const snippet = (text || '').slice(0, 120);
                                // 明确判断 HTML 场景
                                if (/^\s*<!DOCTYPE|^\s*<html/i.test(text)) {
                                    throw new Error('返回非JSON(HTML)：' + snippet);
                                }
                                throw new Error('返回非JSON：' + snippet);
                            }
                            let data = {};
                            try {
                                data = JSON.parse(text);
                                console.log('解析后的数据:', data);
                                console.log('数据键数量:', Object.keys(data).length);
                            } catch (e) {
                                throw new Error('JSON解析失败：' + (e?.message || 'SyntaxError'));
                            }
                            this.config = (data && typeof data === 'object') ? data : {};
                            console.log('设置到config的数据:', this.config);
                            console.log('hasData计算结果:', this.hasData);
                        } catch (err) {
                            console.error('加载配置失败:', err);
                            this.error = err?.message || '未知错误';
                            this.config = {};
                        } finally {
                            this.loading = false;
                        }
                    },
                    mapCityCodesToNames(value) {
                        if (!value) return '';
                        if (Array.isArray(value)) {
                            return value.map(v => this.cityNameByCode[String(v)] || String(v)).join('、');
                        }
                        const codes = String(value).split(',').map(s => s.trim()).filter(Boolean);
                        if (codes.length === 0) return '';
                        return codes.map(c => this.cityNameByCode[c] || c).join('、');
                    },
                    safe(v) {
                        if (Array.isArray(v)) return v.join('、');
                        return (v ?? '').toString();
                    },
                    join(v) {
                        if (Array.isArray(v)) return v.join('、');
                        return v ?? '';
                    },
                    formatDateTime(input) {
                        try {
                            if (!input) return '';
                            const d = new Date(input);
                            if (isNaN(d.getTime())) return '';
                            const pad = (n) => (n < 10 ? '0' + n : '' + n);
                            return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
                        } catch (_) { return ''; }
                    },
                    formatKVList(obj) {
                        if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return '';
                        const entries = Object.entries(obj);
                        if (!entries.length) return '';
                        return entries.map(([k, v]) => `${k} - ${v}`).join('<br>');
                    }
                },
                mounted() {
                    console.log('Vue应用已挂载');
                    // 如果一进来就处于该页签，可立即加载
                    const pane = document.getElementById('boss-current-pane');
                    if (pane && pane.classList.contains('show') && pane.classList.contains('active')) {
                        console.log('当前页签已激活，立即加载数据');
                        this.load(false);
                    }
                }
            });
            window.bossConfigView = app.mount('#bossConfigApp');
        }
    } catch (_) {}

    // =============================
    // 初始化"岗位明细"Vue 视图
    // =============================
    try {
        if (window.Views && window.Views.BossRecordsVue) {
            window.bossRecordsVue = new window.Views.BossRecordsVue();
        }
    } catch (error) {
        console.error('初始化Boss岗位明细Vue应用失败:', error);
    }

    try {
        if (window.Views && window.Views.ZhilianRecordsVue) {
            window.zhilianRecordsVue = new window.Views.ZhilianRecordsVue();
        }
    } catch (error) {
        console.error('初始化智联岗位明细Vue应用失败:', error);
    }

    try {
        if (window.Views && window.Views.LiepinRecordsVue) {
            window.liepinRecordsVue = new window.Views.LiepinRecordsVue();
        }
    } catch (error) {
        console.error('初始化猎聘岗位明细Vue应用失败:', error);
    }
});

function initLiepinConfigForm() {
    const saveBtn = document.getElementById('liepinSaveConfigBtn');
    const loginBtn = document.getElementById('liepinLoginBtn');
    const collectBtn = document.getElementById('liepinCollectBtn');
    const filterBtn = document.getElementById('liepinFilterBtn');
    const applyBtn = document.getElementById('liepinApplyBtn');

    saveBtn.addEventListener('click', () => {
        const config = {
            keywords: document.getElementById('liepinKeywordsField').value,
            cityCode: Array.from(document.getElementById('liepinCityCodeField').selectedOptions).map(opt => opt.value)
        };
        fetch('/api/config/liepin', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        }).then(() => CommonUtils.showToast('配置已保存', 'success'));
    });

    loginBtn.addEventListener('click', () => {
        fetch('/api/liepin/task/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({}) })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    CommonUtils.showToast('登录成功', 'success');
                    collectBtn.disabled = false;
                    filterBtn.disabled = false;
                    applyBtn.disabled = false;
                } else {
                    CommonUtils.showToast('登录失败: ' + data.message, 'danger');
                }
            });
    });

    collectBtn.addEventListener('click', () => {
        const config = {
            keywords: document.getElementById('liepinKeywordsField').value,
            cityCode: Array.from(document.getElementById('liepinCityCodeField').selectedOptions).map(opt => opt.value)
        };
        fetch('/api/liepin/task/collect', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(config) })
            .then(res => res.json())
            .then(data => CommonUtils.showToast(`采集到 ${data.jobCount} 个岗位`, 'info'));
    });

    filterBtn.addEventListener('click', () => {
        const config = {
            keywords: document.getElementById('liepinKeywordsField').value,
            cityCode: Array.from(document.getElementById('liepinCityCodeField').selectedOptions).map(opt => opt.value)
        };
        fetch('/api/liepin/task/filter', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ config }) })
            .then(res => res.json())
            .then(data => CommonUtils.showToast(`过滤后剩余 ${data.filteredCount} 个岗位`, 'info'));
    });

    applyBtn.addEventListener('click', () => {
        const config = {
            keywords: document.getElementById('liepinKeywordsField').value,
            cityCode: Array.from(document.getElementById('liepinCityCodeField').selectedOptions).map(opt => opt.value)
        };
        fetch('/api/liepin/task/deliver', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ config, enableActualDelivery: true }) })
            .then(res => res.json())
            .then(data => CommonUtils.showToast(`投递了 ${data.deliveredCount} 个岗位`, 'success'));
    });
}

function initLiepinRecordsVue() {
    // Vue app for liepin records
}

// 注意：不要重复创建Vue应用，已在上面的DOMContentLoaded中创建
