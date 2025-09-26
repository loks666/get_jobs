// 智联招聘配置表单管理类
class ZhilianConfigForm {
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
        const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
        tooltipTriggerList.map(function (tooltipTriggerEl) {
            return new bootstrap.Tooltip(tooltipTriggerEl);
        });
    }

    bindEvents() {
        document.getElementById('zhilianSaveConfigBtn')?.addEventListener('click', () => this.handleSaveConfig());
        document.getElementById('zhilianBackupDataBtn')?.addEventListener('click', () => this.handleBackupData());

        document.getElementById('zhilianLoginBtn')?.addEventListener('click', () => this.handleLogin());
        document.getElementById('zhilianLoginManualBtn')?.addEventListener('click', () => this.handleManualLogin());
        document.getElementById('zhilianCollectBtn')?.addEventListener('click', () => this.handleCollect());
        document.getElementById('zhilianFilterBtn')?.addEventListener('click', () => this.handleFilter());
        document.getElementById('zhilianApplyBtn')?.addEventListener('click', () => this.handleApply());
        document.getElementById('zhilianResetTasksBtn')?.addEventListener('click', () => this.resetTaskFlow());

        this.bindFormValidation();
        this.bindAutoSave();
    }

    bindFormValidation() {
        const requiredFields = [
            'zhilianKeywordsField',
            'zhilianCityCodeField',
            'zhilianExperienceComboBox',
            'zhilianJobTypeComboBox',
            'zhilianSalaryComboBox',
            'zhilianDegreeComboBox'
        ];
        requiredFields.forEach(id => {
            const field = document.getElementById(id);
            if (!field) return;
            field.addEventListener('blur', () => {
                if (field.tagName === 'SELECT') this.validateSelectField(field);
                else this.validateField(field);
            });
        });
    }

    validateField(field) {
        const value = (field.value || '').trim();
        const ok = value.length > 0;
        this.updateFieldValidation(field, ok);
        return ok;
    }

    validateSelectField(field) {
        const ok = !!field.value;
        this.updateFieldValidation(field, ok);
        return ok;
    }

    updateFieldValidation(field, isValid) {
        if (isValid) {
            field.classList.remove('is-invalid');
            field.classList.add('is-valid');
        } else {
            field.classList.remove('is-valid');
            field.classList.add('is-invalid');
        }
    }

    bindAutoSave() {
        const formElements = document.querySelectorAll('#zhilian-config-pane input, #zhilian-config-pane select, #zhilian-config-pane textarea');
        formElements.forEach(el => el.addEventListener('change', () => this.saveConfig()));
    }

    getMultiValues(selectId) {
        const el = document.getElementById(selectId);
        if (!el) return '';
        return Array.from(el.selectedOptions).map(o => o.value).filter(Boolean).join(',');
    }

    getCurrentConfig() {
        return {
            // 搜索条件
            keywords: document.getElementById('zhilianKeywordsField')?.value || '',
            industry: this.getMultiValues('zhilianIndustryField'),
            cityCode: this.getMultiValues('zhilianCityCodeField'),

            // 职位要求
            experience: document.getElementById('zhilianExperienceComboBox')?.value || '',
            jobType: document.getElementById('zhilianJobTypeComboBox')?.value || '',
            salary: document.getElementById('zhilianSalaryComboBox')?.value || '',
            degree: document.getElementById('zhilianDegreeComboBox')?.value || '',
            scale: document.getElementById('zhilianScaleComboBox')?.value || '',
            companyNature: document.getElementById('zhilianCompanyNatureComboBox')?.value || '',

            // 功能开关
            blacklistFilter: document.getElementById('zhilianBlacklistFilterCheckBox')?.checked || false,
            blacklistKeywords: document.getElementById('zhilianBlacklistKeywordsTextArea')?.value || '',

            // AI配置
            enableAIJobMatch: document.getElementById('zhilianEnableAIJobMatchCheckBox')?.checked || false
        };
    }

    saveConfig() {
        this.config = this.getCurrentConfig();
        localStorage.setItem('zhilianConfig', JSON.stringify(this.config));
        try {
            fetch('/api/config/zhilian', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(this.config)
            }).then(() => {}).catch(() => {});
        } catch (_) {}
    }

    async loadSavedConfig() {
        try {
            const res = await fetch('/api/config/zhilian');
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
                localStorage.setItem('zhilianConfig', JSON.stringify(this.config));
                return;
            }
            const saved = localStorage.getItem('zhilianConfig');
            if (saved) {
                try {
                    this.config = JSON.parse(saved);
                    this.populateForm();
                } catch (e) {
                    localStorage.removeItem('zhilianConfig');
                }
            }
        } catch (_) {
            const saved = localStorage.getItem('zhilianConfig');
            if (saved) {
                try {
                    this.config = JSON.parse(saved);
                    this.populateForm();
                } catch (e) {
                    localStorage.removeItem('zhilianConfig');
                }
            }
        }
    }

    getFieldId(key) {
        const map = {
            keywords: 'zhilianKeywordsField',
            industry: 'zhilianIndustryField',
            cityCode: 'zhilianCityCodeField',
            experience: 'zhilianExperienceComboBox',
            jobType: 'zhilianJobTypeComboBox',
            salary: 'zhilianSalaryComboBox',
            degree: 'zhilianDegreeComboBox',
            scale: 'zhilianScaleComboBox',
            companyNature: 'zhilianCompanyNatureComboBox',
            blacklistFilter: 'zhilianBlacklistFilterCheckBox',
            blacklistKeywords: 'zhilianBlacklistKeywordsTextArea',
            enableAIJobMatch: 'zhilianEnableAIJobMatchCheckBox'
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

        // 回填城市多选（隐藏select）并同步dropdown显示
        try {
            let cityStr = '';
            if (Array.isArray(this.config.cityCode)) cityStr = this.config.cityCode.join(',');
            else cityStr = this.config.cityCode || '';
            const codes = cityStr.split(',').map(s => s.trim()).filter(Boolean);
            const citySelect = document.getElementById('zhilianCityCodeField');
            if (citySelect && codes.length) {
                Array.from(citySelect.options).forEach(opt => { opt.selected = codes.includes(opt.value); });
                const list = document.getElementById('zhilianCityDropdownList');
                const btn = document.getElementById('zhilianCityDropdownBtn');
                const summary = document.getElementById('zhilianCitySelectionSummary');
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
            console.log('智联招聘: 开始按顺序加载数据：字典 -> 配置');
            
            // 先加载字典数据
            await this.loadZhilianDicts();
            console.log('智联招聘: 字典数据加载完成，开始加载配置数据');
            
            // 等待DOM元素完全渲染
            await this.waitForDOMReady();
            
            // 再加载配置数据
            await this.loadSavedConfig();
            console.log('智联招聘: 配置数据加载完成');
        } catch (e) {
            console.warn('智联数据加载失败:', e?.message || e);
        }
    }

    // 等待DOM元素完全准备就绪
    async waitForDOMReady() {
        return new Promise((resolve) => {
            // 等待一个事件循环，确保所有DOM操作完成
            setTimeout(() => {
                console.log('智联招聘: DOM元素准备就绪');
                resolve();
            }, 100);
        });
    }

    async loadZhilianDicts() {
        const res = await fetch('/dicts/ZHILIAN_ZHAOPIN');
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const data = await res.json();
        if (!data || !Array.isArray(data.groups)) return;
        const groupMap = new Map();
        data.groups.forEach(g => groupMap.set(g.key, Array.isArray(g.items) ? g.items : []));

        // 渲染城市
        this.renderCitySelection(groupMap.get('cityList') || []);

        // 渲染其他下拉
        this.fillSelect('zhilianIndustryField', groupMap.get('industryList'));
        this.fillSelect('zhilianExperienceComboBox', groupMap.get('experienceList'));
        this.fillSelect('zhilianJobTypeComboBox', groupMap.get('jobTypeList'));
        this.fillSelect('zhilianSalaryComboBox', groupMap.get('salaryList'));
        this.fillSelect('zhilianDegreeComboBox', groupMap.get('degreeList'));
        this.fillSelect('zhilianScaleComboBox', groupMap.get('scaleList'));
        this.fillSelect('zhilianCompanyNatureComboBox', groupMap.get('companyNatureList'));
    }

    renderCitySelection(cityItems) {
        const citySelect = document.getElementById('zhilianCityCodeField');
        const citySearch = document.getElementById('zhilianCitySearchField');
        const cityListContainer = document.getElementById('zhilianCityDropdownList');
        const cityDropdownBtn = document.getElementById('zhilianCityDropdownBtn');
        const citySummary = document.getElementById('zhilianCitySelectionSummary');

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
                    const id = `zhilian_city_chk_${value}`.replace(/[^a-zA-Z0-9_\-]/g, '_');
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

    fillSelect(selectId, items) {
        console.log(`智联招聘: 填充下拉框 ${selectId}，数据项数量:`, Array.isArray(items) ? items.length : 0);
        
        if (!Array.isArray(items) || items.length === 0) {
            console.warn(`智联招聘: 下拉框 ${selectId} 的数据无效:`, items);
            return;
        }

        const sel = document.getElementById(selectId);
        if (!sel) {
            console.warn(`智联招聘: 未找到下拉框元素: ${selectId}`);
            // 延迟重试
            setTimeout(() => {
                const retrySel = document.getElementById(selectId);
                if (retrySel) {
                    console.log(`智联招聘: 重试填充下拉框 ${selectId}`);
                    this.fillSelect(selectId, items);
                }
            }, 500);
            return;
        }
        
        try {
            // 保留第一项"请选择"，其余重建
            const first = sel.querySelector('option');
            sel.innerHTML = '';
            if (first && first.value === '') sel.appendChild(first);
            
            items.forEach(it => {
                const opt = document.createElement('option');
                opt.value = it.code ?? it.name ?? '';
                opt.textContent = it.name ?? String(it.code ?? '');
                sel.appendChild(opt);
            });
            
            console.log(`智联招聘: 下拉框 ${selectId} 填充完成，共 ${items.length} 项`);
            
        } catch (error) {
            console.error(`智联招聘: 填充下拉框 ${selectId} 时出错:`, error);
        }
    }

    handleSaveConfig() {
        this.saveConfig();
        this.showToast('智联配置已保存');
    }

    handleBackupData() {
        const btn = document.getElementById('zhilianBackupDataBtn');
        if (btn) { btn.disabled = true; btn.innerHTML = '<i class="bi bi-hourglass-split me-2"></i>备份中...'; }
        fetch('/api/backup/export', { method: 'POST', headers: { 'Content-Type': 'application/json' } })
            .then(r => r.json())
            .then(d => { if (d.success) this.showToast('数据库备份成功', 'success'); else this.showToast('数据库备份失败: ' + d.message, 'danger'); })
            .catch(e => { this.showToast('数据库备份失败: ' + e.message, 'danger'); })
            .finally(() => { if (btn) { btn.disabled = false; btn.innerHTML = '<i class="bi bi-database me-2"></i>数据库备份'; } });
    }

    async handleLogin() {
        if (!this.validateRequiredFields()) { this.showAlertModal('验证失败', '请先完善必填项'); return; }
        this.updateButtonState('zhilianLoginBtn', 'zhilianLoginStatus', '执行中...', true);
        try {
            const response = await fetch('/api/zhilian/task/login', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(this.getCurrentConfig())
            });
            const result = await response.json();
            if (result.success) {
                this.taskStates.loginTaskId = result.taskId;
                this.updateButtonState('zhilianLoginBtn', 'zhilianLoginStatus', '登录成功', false);
                this.enableNextStep('zhilianCollectBtn', 'zhilianCollectStatus', '可开始采集');
                this.enableNextStep('zhilianFilterBtn', 'zhilianFilterStatus', '可开始过滤');
                this.enableNextStep('zhilianApplyBtn', 'zhilianApplyStatus', '可开始投递');
                this.showToast('智联登录成功！');
            } else {
                this.updateButtonState('zhilianLoginBtn', 'zhilianLoginStatus', '登录失败', false);
                this.showToast(result.message || '登录失败', 'danger');
            }
        } catch (e) {
            this.updateButtonState('zhilianLoginBtn', 'zhilianLoginStatus', '登录失败', false);
            this.showToast('登录接口调用失败: ' + e.message, 'danger');
        }
    }

    handleManualLogin() {
        this.taskStates.loginTaskId = 'manual_login_' + Date.now();
        this.updateButtonState('zhilianLoginBtn', 'zhilianLoginStatus', '登录成功', false);
        this.enableNextStep('zhilianCollectBtn', 'zhilianCollectStatus', '可开始采集');
        this.enableNextStep('zhilianFilterBtn', 'zhilianFilterStatus', '可开始过滤');
        this.enableNextStep('zhilianApplyBtn', 'zhilianApplyStatus', '可开始投递');
        this.showToast('已手动标记为登录状态', 'success');
    }

    async handleCollect() {
        if (!this.taskStates.loginTaskId) { this.showAlertModal('操作提示', '请先完成登录步骤'); return; }
        this.updateButtonState('zhilianCollectBtn', 'zhilianCollectStatus', '采集中...', true);
        try {
            const response = await fetch('/api/zhilian/task/collect', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(this.getCurrentConfig())
            });
            const result = await response.json();
            if (result.success) {
                this.taskStates.collectTaskId = result.taskId;
                this.updateButtonState('zhilianCollectBtn', 'zhilianCollectStatus', `采集完成(${result.jobCount}个职位)`, false);
                this.showToast(`采集完成，共找到 ${result.jobCount} 个职位！`);
            } else {
                this.updateButtonState('zhilianCollectBtn', 'zhilianCollectStatus', '采集失败', false);
                this.showToast(result.message || '采集失败', 'danger');
            }
        } catch (e) {
            this.updateButtonState('zhilianCollectBtn', 'zhilianCollectStatus', '采集失败', false);
            this.showToast('采集接口调用失败: ' + e.message, 'danger');
        }
    }

    async handleFilter() {
        if (!this.taskStates.loginTaskId) { this.showAlertModal('操作提示', '请先完成登录步骤'); return; }
        this.updateButtonState('zhilianFilterBtn', 'zhilianFilterStatus', '过滤中...', true);
        try {
            const request = { collectTaskId: this.taskStates.collectTaskId, config: this.getCurrentConfig() };
            const response = await fetch('/api/zhilian/task/filter', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
            const result = await response.json();
            if (result.success) {
                this.taskStates.filterTaskId = result.taskId;
                this.updateButtonState('zhilianFilterBtn', 'zhilianFilterStatus', `过滤完成(${result.originalCount}→${result.filteredCount})`, false);
                this.showToast(`过滤完成，从 ${result.originalCount} 个职位中筛选出 ${result.filteredCount} 个！`);
            } else {
                this.updateButtonState('zhilianFilterBtn', 'zhilianFilterStatus', '过滤失败', false);
                this.showToast(result.message || '过滤失败', 'danger');
            }
        } catch (e) {
            this.updateButtonState('zhilianFilterBtn', 'zhilianFilterStatus', '过滤失败', false);
            this.showToast('过滤接口调用失败: ' + e.message, 'danger');
        }
    }

    async handleApply() {
        if (!this.taskStates.loginTaskId) { this.showAlertModal('操作提示', '请先完成登录步骤'); return; }
        this.showConfirmModal('投递确认', '是否执行实际投递？\n点击"确定"将真实投递简历\n点击"取消"将仅模拟投递', () => this.executeApply(true), () => this.executeApply(false));
    }

    async executeApply(enableActualDelivery) {
        this.updateButtonState('zhilianApplyBtn', 'zhilianApplyStatus', '投递中...', true);
        try {
            const request = { filterTaskId: this.taskStates.filterTaskId, config: this.getCurrentConfig(), enableActualDelivery };
            const response = await fetch('/api/zhilian/task/deliver', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
            const result = await response.json();
            if (result.success) {
                this.taskStates.applyTaskId = result.taskId;
                const deliveryType = result.actualDelivery ? '实际投递' : '模拟投递';
                this.updateButtonState('zhilianApplyBtn', 'zhilianApplyStatus', `${deliveryType}完成(${result.appliedCount}/${result.totalCount})`, false);
                this.showToast(`${deliveryType}完成！处理了 ${result.appliedCount} 个职位`);
            } else {
                this.updateButtonState('zhilianApplyBtn', 'zhilianApplyStatus', '投递失败', false);
                this.showToast(result.message || '投递失败', 'danger');
            }
        } catch (e) {
            this.updateButtonState('zhilianApplyBtn', 'zhilianApplyStatus', '投递失败', false);
            this.showToast('投递接口调用失败: ' + e.message, 'danger');
        }
    }

    validateRequiredFields() {
        const required = [
            'zhilianKeywordsField',
            'zhilianCityCodeField',
            'zhilianExperienceComboBox',
            'zhilianJobTypeComboBox',
            'zhilianSalaryComboBox',
            'zhilianDegreeComboBox'
        ];
        let ok = true;
        required.forEach(id => {
            const field = document.getElementById(id);
            if (!field) return;
            if (field.tagName === 'SELECT') { if (!this.validateSelectField(field)) ok = false; }
            else { if (!this.validateField(field)) ok = false; }
        });
        return ok;
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
            this.updateButtonState('zhilianLoginBtn', 'zhilianLoginStatus', '待执行', false);
            this.updateButtonState('zhilianCollectBtn', 'zhilianCollectStatus', '等待登录', true);
            this.updateButtonState('zhilianFilterBtn', 'zhilianFilterStatus', '等待登录', true);
            this.updateButtonState('zhilianApplyBtn', 'zhilianApplyStatus', '等待登录', true);
            const cb = id => { const el = document.getElementById(id); if (el) el.disabled = true; };
            cb('zhilianCollectBtn'); cb('zhilianFilterBtn'); cb('zhilianApplyBtn');
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

// 导出到全局
window.ZhilianConfigForm = ZhilianConfigForm;


