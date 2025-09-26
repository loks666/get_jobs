// 猎聘配置表单管理类
class LiepinConfigForm {
    constructor() {
        this.config = {};
        this.taskStates = {
            loginTaskId: null,
            collectTaskId: null,
            filterTaskId: null,
            applyTaskId: null
        };
        this.init();
    }

    init() {
        this.initializeTooltips();
        this.bindEvents();
        this.loadDataSequentially();
    }

    initializeTooltips() {
        const tooltipTriggerList = [].slice.call(document.querySelectorAll('#liepin-pane [data-bs-toggle="tooltip"]'));
        tooltipTriggerList.map(function (tooltipTriggerEl) {
            return new bootstrap.Tooltip(tooltipTriggerEl);
        });
    }

    bindEvents() {
        document.getElementById('liepinSaveConfigBtn')?.addEventListener('click', () => this.handleSaveConfig());
        document.getElementById('liepinBackupDataBtn')?.addEventListener('click', () => this.handleBackupData());

        document.getElementById('liepinLoginBtn')?.addEventListener('click', () => this.handleLogin());
        document.getElementById('liepinCollectBtn')?.addEventListener('click', () => this.handleCollect());
        document.getElementById('liepinFilterBtn')?.addEventListener('click', () => this.handleFilter());
        document.getElementById('liepinApplyBtn')?.addEventListener('click', () => this.handleApply());
        document.getElementById('liepinResetTasksBtn')?.addEventListener('click', () => this.resetTaskFlow());

        this.bindAutoSave();
    }

    bindAutoSave() {
        const formElements = document.querySelectorAll('#liepin-config-pane input, #liepin-config-pane select, #liepin-config-pane textarea');
        formElements.forEach(el => el.addEventListener('change', () => this.saveConfig()));
    }

    getMultiValues(selectId) {
        const el = document.getElementById(selectId);
        if (!el) return '';
        return Array.from(el.selectedOptions).map(o => o.value).filter(Boolean).join(',');
    }

    getCurrentConfig() {
        return {
            keywords: document.getElementById('liepinKeywordsField')?.value || '',
            cityCode: this.getMultiValues('liepinCityCodeField'),
        };
    }

    saveConfig() {
        this.config = this.getCurrentConfig();
        localStorage.setItem('liepinConfig', JSON.stringify(this.config));
        try {
            fetch('/api/config/liepin', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(this.config)
            }).then(() => {}).catch(() => {});
        } catch (_) {}
    }

    async loadSavedConfig() {
        try {
            const res = await fetch('/api/config/liepin');
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const ct = res.headers.get('content-type') || '';
            let data;
            if (ct.includes('application/json')) data = await res.json();
            else {
                const text = await res.text();
                const snippet = (text || '').slice(0, 80);
                throw new Error('返回非JSON：' + snippet);
            }
            if (data && typeof data === 'object' && Object.keys(data).length) {
                this.config = data;
                this.populateForm();
                localStorage.setItem('liepinConfig', JSON.stringify(this.config));
                return;
            }
            const saved = localStorage.getItem('liepinConfig');
            if (saved) {
                try {
                    this.config = JSON.parse(saved);
                    this.populateForm();
                } catch (e) {
                    localStorage.removeItem('liepinConfig');
                }
            }
        } catch (_) {
            const saved = localStorage.getItem('liepinConfig');
            if (saved) {
                try {
                    this.config = JSON.parse(saved);
                    this.populateForm();
                } catch (e) {
                    localStorage.removeItem('liepinConfig');
                }
            }
        }
    }

    getFieldId(key) {
        const map = {
            keywords: 'liepinKeywordsField',
            cityCode: 'liepinCityCodeField',
        };
        return map[key] || key;
    }

    populateForm() {
        Object.keys(this.config).forEach(key => {
            const el = document.getElementById(this.getFieldId(key));
            if (!el) return;
            if (el.type === 'checkbox') el.checked = !!this.config[key];
            else {
                let value = this.config[key];
                if (Array.isArray(value)) value = value.join(',');
                el.value = value || '';
            }
        });

        try {
            let cityStr = '';
            if (Array.isArray(this.config.cityCode)) cityStr = this.config.cityCode.join(',');
            else cityStr = this.config.cityCode || '';
            const codes = cityStr.split(',').map(s => s.trim()).filter(Boolean);
            const citySelect = document.getElementById('liepinCityCodeField');
            if (citySelect && codes.length) {
                Array.from(citySelect.options).forEach(opt => { opt.selected = codes.includes(opt.value); });
                const list = document.getElementById('liepinCityDropdownList');
                const btn = document.getElementById('liepinCityDropdownBtn');
                const summary = document.getElementById('liepinCitySelectionSummary');
                const set = new Set(codes);
                if (list) list.querySelectorAll('input[type="checkbox"]').forEach(chk => chk.checked = set.has(chk.value));
                if (btn && summary) {
                    const values = Array.from(citySelect.selectedOptions).map(o => o.textContent || '').filter(Boolean);
                    if (values.length === 0) { btn.textContent = '选择城市'; summary.textContent = '未选择'; }
                    else if (values.length <= 2) { const text = values.join('、'); btn.textContent = text; summary.textContent = `已选 ${values.length} 项：${text}`; }
                    else { btn.textContent = `已选 ${values.length} 项`; summary.textContent = `已选 ${values.length} 项`; }
                }
            }
        } catch (_) {}
    }

    async loadDataSequentially() {
        try {
            await this.loadLiepinDicts();
            await this.waitForDOMReady();
            await this.loadSavedConfig();
        } catch (e) {
            console.warn('猎聘数据加载失败:', e?.message || e);
        }
    }

    async waitForDOMReady() {
        return new Promise((resolve) => {
            setTimeout(() => {
                resolve();
            }, 100);
        });
    }

    async loadLiepinDicts() {
        const res = await fetch('/dicts/LIEPIN');
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const data = await res.json();
        if (!data || !Array.isArray(data.groups)) return;
        const groupMap = new Map();
        data.groups.forEach(g => groupMap.set(g.key, Array.isArray(g.items) ? g.items : []));
        this.renderCitySelection(groupMap.get('cityList') || []);
    }

    renderCitySelection(cityItems) {
        const citySelect = document.getElementById('liepinCityCodeField');
        const citySearch = document.getElementById('liepinCitySearchField');
        const cityListContainer = document.getElementById('liepinCityDropdownList');
        const cityDropdownBtn = document.getElementById('liepinCityDropdownBtn');
        const citySummary = document.getElementById('liepinCitySelectionSummary');

        const updateCitySummary = () => {
            if (!citySelect || !cityDropdownBtn || !citySummary) return;
            const values = Array.from(citySelect.selectedOptions).map(o => o.textContent);
            if (values.length === 0) { cityDropdownBtn.textContent = '选择城市'; citySummary.textContent = '未选择'; }
            else if (values.length <= 2) { const text = values.join('、'); cityDropdownBtn.textContent = text; citySummary.textContent = `已选 ${values.length} 项：${text}`; }
            else { cityDropdownBtn.textContent = `已选 ${values.length} 项`; citySummary.textContent = `已选 ${values.length} 项`; }
        };

        const renderCityOptions = (list) => {
            if (!citySelect) return;
            const selected = new Set(Array.from(citySelect.selectedOptions).map(o => o.value));
            citySelect.innerHTML = '';
            list.forEach(it => {
                const value = it.code ?? '';
                const label = `${it.name ?? ''}${it.code ? ' (' + it.code + ')' : ''}`;
                const opt = document.createElement('option');
                opt.value = value;
                opt.textContent = label;
                if (selected.has(value)) opt.selected = true;
                citySelect.appendChild(opt);
            });
            if (cityListContainer) {
                cityListContainer.innerHTML = '';
                list.forEach(it => {
                    const value = it.code ?? '';
                    const label = `${it.name ?? ''}${it.code ? ' (' + it.code + ')' : ''}`;
                    const item = document.createElement('div');
                    item.className = 'form-check mb-1';
                    const id = `liepin_city_chk_${value}`.replace(/[^a-zA-Z0-9_\-]/g, '_');
                    item.innerHTML = `
                        <input class="form-check-input" type="checkbox" value="${value}" id="${id}" ${selected.has(value) ? 'checked' : ''}>
                        <label class="form-check-label small" for="${id}">${label}</label>
                    `;
                    const checkbox = item.querySelector('input[type="checkbox"]');
                    checkbox.addEventListener('change', () => {
                        const option = Array.from(citySelect.options).find(o => o.value === value);
                        if (option) option.selected = checkbox.checked;
                        updateCitySummary();
                    });
                    cityListContainer.appendChild(item);
                });
            }
            updateCitySummary();
        };

        renderCityOptions(cityItems);
        if (citySearch) {
            citySearch.addEventListener('input', () => {
                const kw = (citySearch.value || '').trim().toLowerCase();
                if (!kw) { renderCityOptions(cityItems); return; }
                const filtered = cityItems.filter(it => String(it.name || '').toLowerCase().includes(kw) || String(it.code || '').toLowerCase().includes(kw));
                renderCityOptions(filtered);
            });
        }
    }

    handleSaveConfig() {
        this.saveConfig();
        this.showToast('猎聘配置已保存');
    }

    handleBackupData() {
        const btn = document.getElementById('liepinBackupDataBtn');
        if (btn) { btn.disabled = true; btn.innerHTML = '<i class="bi bi-hourglass-split me-2"></i>备份中...'; }
        fetch('/api/backup/export', { method: 'POST', headers: { 'Content-Type': 'application/json' } })
            .then(r => r.json())
            .then(d => { if (d.success) this.showToast('数据库备份成功', 'success'); else this.showToast('数据库备份失败: ' + d.message, 'danger'); })
            .catch(e => { this.showToast('数据库备份失败: ' + e.message, 'danger'); })
            .finally(() => { if (btn) { btn.disabled = false; btn.innerHTML = '<i class="bi bi-database me-2"></i>数据库备份'; } });
    }

    async handleLogin() {
        this.updateButtonState('liepinLoginBtn', 'liepinLoginStatus', '执行中...', true);
        try {
            const response = await fetch('/api/liepin/task/login', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(this.getCurrentConfig())
            });
            const result = await response.json();
            if (result.success) {
                this.taskStates.loginTaskId = result.taskId;
                this.updateButtonState('liepinLoginBtn', 'liepinLoginStatus', '登录成功', false);
                this.enableNextStep('liepinCollectBtn', 'liepinCollectStatus', '可开始采集');
                this.enableNextStep('liepinFilterBtn', 'liepinFilterStatus', '可开始过滤');
                this.enableNextStep('liepinApplyBtn', 'liepinApplyStatus', '可开始投递');
                this.showToast('猎聘登录成功！');
            } else {
                this.updateButtonState('liepinLoginBtn', 'liepinLoginStatus', '登录失败', false);
                this.showToast(result.message || '登录失败', 'danger');
            }
        } catch (e) {
            this.updateButtonState('liepinLoginBtn', 'liepinLoginStatus', '登录失败', false);
            this.showToast('登录接口调用失败: ' + e.message, 'danger');
        }
    }

    async handleCollect() {
        if (!this.taskStates.loginTaskId) { this.showAlertModal('操作提示', '请先完成登录步骤'); return; }
        this.updateButtonState('liepinCollectBtn', 'liepinCollectStatus', '采集中...', true);
        try {
            const response = await fetch('/api/liepin/task/collect', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(this.getCurrentConfig())
            });
            const result = await response.json();
            if (result.success) {
                this.taskStates.collectTaskId = result.taskId;
                this.updateButtonState('liepinCollectBtn', 'liepinCollectStatus', `采集完成(${result.jobCount}个职位)`, false);
                this.showToast(`采集完成，共找到 ${result.jobCount} 个职位！`);
            } else {
                this.updateButtonState('liepinCollectBtn', 'liepinCollectStatus', '采集失败', false);
                this.showToast(result.message || '采集失败', 'danger');
            }
        } catch (e) {
            this.updateButtonState('liepinCollectBtn', 'liepinCollectStatus', '采集失败', false);
            this.showToast('采集接口调用失败: ' + e.message, 'danger');
        }
    }

    async handleFilter() {
        if (!this.taskStates.loginTaskId) { this.showAlertModal('操作提示', '请先完成登录步骤'); return; }
        this.updateButtonState('liepinFilterBtn', 'liepinFilterStatus', '过滤中...', true);
        try {
            const request = { collectTaskId: this.taskStates.collectTaskId, config: this.getCurrentConfig() };
            const response = await fetch('/api/liepin/task/filter', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
            const result = await response.json();
            if (result.success) {
                this.taskStates.filterTaskId = result.taskId;
                this.updateButtonState('liepinFilterBtn', 'liepinFilterStatus', `过滤完成(${result.originalCount}→${result.filteredCount})`, false);
                this.showToast(`过滤完成，从 ${result.originalCount} 个职位中筛选出 ${result.filteredCount} 个！`);
            } else {
                this.updateButtonState('liepinFilterBtn', 'liepinFilterStatus', '过滤失败', false);
                this.showToast(result.message || '过滤失败', 'danger');
            }
        } catch (e) {
            this.updateButtonState('liepinFilterBtn', 'liepinFilterStatus', '过滤失败', false);
            this.showToast('过滤接口调用失败: ' + e.message, 'danger');
        }
    }

    async handleApply() {
        if (!this.taskStates.loginTaskId) { this.showAlertModal('操作提示', '请先完成登录步骤'); return; }
        this.showConfirmModal('投递确认', '是否执行实际投递？\n点击"确定"将真实投递简历\n点击"取消"将仅模拟投递', () => this.executeApply(true), () => this.executeApply(false));
    }

    async executeApply(enableActualDelivery) {
        this.updateButtonState('liepinApplyBtn', 'liepinApplyStatus', '投递中...', true);
        try {
            const request = { filterTaskId: this.taskStates.filterTaskId, config: this.getCurrentConfig(), enableActualDelivery };
            const response = await fetch('/api/liepin/task/deliver', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
            const result = await response.json();
            if (result.success) {
                this.taskStates.applyTaskId = result.taskId;
                const deliveryType = result.actualDelivery ? '实际投递' : '模拟投递';
                this.updateButtonState('liepinApplyBtn', 'liepinApplyStatus', `${deliveryType}完成(${result.appliedCount}/${result.totalCount})`, false);
                this.showToast(`${deliveryType}完成！处理了 ${result.appliedCount} 个职位`);
            } else {
                this.updateButtonState('liepinApplyBtn', 'liepinApplyStatus', '投递失败', false);
                this.showToast(result.message || '投递失败', 'danger');
            }
        } catch (e) {
            this.updateButtonState('liepinApplyBtn', 'liepinApplyStatus', '投递失败', false);
            this.showToast('投递接口调用失败: ' + e.message, 'danger');
        }
    }

    updateButtonState(buttonId, statusId, statusText, isLoading) {
        const button = document.getElementById(buttonId);
        const status = document.getElementById(statusId);
        if (button) button.disabled = isLoading;
        if (status) {
            status.textContent = statusText;
            status.className = isLoading ? 'badge bg-warning text-dark ms-2' : 'badge bg-success text-white ms-2';
        }
    }

    enableNextStep(buttonId, statusId, statusText) {
        const button = document.getElementById(buttonId);
        const status = document.getElementById(statusId);
        if (button) button.disabled = false;
        if (status) { status.textContent = statusText; status.className = 'badge bg-info text-white ms-2'; }
    }

    resetTaskFlow() {
        this.showConfirmModal('重置确认', '确定要重置任务流程吗？这将清除所有任务状态。', () => {
            this.taskStates = { loginTaskId: null, collectTaskId: null, filterTaskId: null, applyTaskId: null };
            this.updateButtonState('liepinLoginBtn', 'liepinLoginStatus', '待执行', false);
            this.updateButtonState('liepinCollectBtn', 'liepinCollectStatus', '等待登录', true);
            this.updateButtonState('liepinFilterBtn', 'liepinFilterStatus', '等待登录', true);
            this.updateButtonState('liepinApplyBtn', 'liepinApplyStatus', '等待登录', true);
            const cb = id => { const el = document.getElementById(id); if (el) el.disabled = true; };
            cb('liepinCollectBtn'); cb('liepinFilterBtn'); cb('liepinApplyBtn');
            this.showToast('任务流程已重置', 'info');
        });
    }

    showToast(message, variant = 'success') {
        try {
            const toastEl = document.getElementById('globalToast');
            const bodyEl = document.getElementById('globalToastBody');
            if (!toastEl || !bodyEl) return;
            bodyEl.textContent = message || '操作成功';
            const variants = ['success', 'danger', 'warning', 'info', 'primary', 'secondary', 'dark'];
            variants.forEach(v => toastEl.classList.remove(`text-bg-${v}`));
            toastEl.classList.add(`text-bg-${variant}`);
            const toast = bootstrap.Toast.getOrCreateInstance(toastEl, { delay: 2000 });
            toast.show();
        } catch (_) {}
    }

    showConfirmModal(title, message, onConfirm, onCancel) {
        const modal = new bootstrap.Modal(document.getElementById('confirmModal'));
        document.getElementById('confirmModalLabel').textContent = title;
        document.getElementById('confirmModalBody').textContent = message;
        const okBtn = document.getElementById('confirmModalOk');
        const newOkBtn = okBtn.cloneNode(true);
        okBtn.parentNode.replaceChild(newOkBtn, okBtn);
        newOkBtn.addEventListener('click', () => { modal.hide(); if (onConfirm) onConfirm(); });
        modal._element.addEventListener('hidden.bs.modal', () => { if (onCancel) onCancel(); }, { once: true });
        modal.show();
    }

    showAlertModal(title, message) {
        const modal = new bootstrap.Modal(document.getElementById('alertModal'));
        document.getElementById('alertModalLabel').textContent = title;
        document.getElementById('alertModalBody').textContent = message;
        modal.show();
    }
}

if (!window.Views) {
    window.Views = {};
}
window.Views.LiepinConfigForm = LiepinConfigForm;
