// liepin配置表单管理类
class LiepinConfigForm {
    constructor() {
        this.config = {};
        this.isRunning = false;
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
        formElements.forEach(element => {
            element.addEventListener('change', () => {
                this.saveConfig();
            });
        });
    }

    saveConfig() {
        const getMultiSelectValues = (selectId) => {
            const el = document.getElementById(selectId);
            if (!el) return '';
            return Array.from(el.selectedOptions).map(o => o.value).filter(Boolean).join(',');
        };

        this.config = {
            keywords: document.getElementById('liepinKeywordsField')?.value || '',
            cityCode: getMultiSelectValues('liepinCityCodeField'),
            experience: document.getElementById('liepinExperienceComboBox')?.value || '',
            jobType: document.getElementById('liepinJobTypeComboBox')?.value || '',
            salary: document.getElementById('liepinSalaryComboBox')?.value || '',
            degree: document.getElementById('liepinDegreeComboBox')?.value || '',
            scale: document.getElementById('liepinScaleComboBox')?.value || '',
            companyNature: document.getElementById('liepinCompanyNatureComboBox')?.value || '',
            recruiterActivity: document.getElementById('liepinRecruiterActivityComboBox')?.value || '',
            blacklistFilter: document.getElementById('liepinBlacklistFilterCheckBox')?.checked || false,
            blacklistKeywords: document.getElementById('liepinBlacklistKeywordsTextArea')?.value || '',
            enableAIJobMatch: document.getElementById('liepinEnableAIJobMatchCheckBox')?.checked || false
        };

        localStorage.setItem('liepinConfig', JSON.stringify(this.config));

        try {
            fetch('/api/config/liepin', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(this.config)
            }).then(() => {}).catch(() => {});
        } catch (e) {}
    }

    async loadSavedConfig() {
        try {
            const res = await fetch('/api/config/liepin');
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const data = await res.json();
            if (data && typeof data === 'object' && Object.keys(data).length) {
                this.config = data;
                this.populateForm();
                localStorage.setItem('liepinConfig', JSON.stringify(this.config));
                return;
            }
        } catch (err) {
            console.warn('Liepin backend config read failed: ' + (err?.message || 'Unknown error'));
        }

        const savedConfig = localStorage.getItem('liepinConfig');
        if (savedConfig) {
            try {
                this.config = JSON.parse(savedConfig);
                this.populateForm();
            } catch (error) {
                console.warn('Liepin local config corrupted, cleared: ' + error.message);
                localStorage.removeItem('liepinConfig');
            }
        }
    }

    populateForm() {
        Object.keys(this.config).forEach(key => {
            const element = document.getElementById(this.getFieldId(key));
            if (element) {
                if (element.type === 'checkbox') {
                    element.checked = this.config[key];
                } else {
                    let value = this.config[key];
                    if (Array.isArray(value)) {
                        value = value.join(',');
                    }
                    element.value = value || '';
                }
            }
        });

        try {
            let cityCodeStr = Array.isArray(this.config.cityCode) ? this.config.cityCode.join(',') : (this.config.cityCode || '');
            const codes = cityCodeStr.split(',').map(s => s.trim()).filter(Boolean);
            const citySelect = document.getElementById('liepinCityCodeField');
            if (citySelect && codes.length) {
                Array.from(citySelect.options).forEach(opt => {
                    opt.selected = codes.includes(opt.value);
                });
                this.updateCitySummary();
            }
        } catch (_) {}
    }

    getFieldId(key) {
        const fieldMap = {
            keywords: 'liepinKeywordsField',
            cityCode: 'liepinCityCodeField',
            experience: 'liepinExperienceComboBox',
            jobType: 'liepinJobTypeComboBox',
            salary: 'liepinSalaryComboBox',
            degree: 'liepinDegreeComboBox',
            scale: 'liepinScaleComboBox',
            companyNature: 'liepinCompanyNatureComboBox',
            recruiterActivity: 'liepinRecruiterActivityComboBox',
            blacklistFilter: 'liepinBlacklistFilterCheckBox',
            blacklistKeywords: 'liepinBlacklistKeywordsTextArea',
            enableAIJobMatch: 'liepinEnableAIJobMatchCheckBox'
        };
        return fieldMap[key] || `liepin${key.charAt(0).toUpperCase() + key.slice(1)}Field`;
    }

    async loadDataSequentially() {
        try {
            await this.loadLiepinDicts();
            await this.waitForDOMReady();
            await this.loadSavedConfig();
        } catch (error) {
            console.error('Liepin data loading failed:', error);
        }
    }

    async waitForDOMReady() {
        return new Promise(resolve => setTimeout(resolve, 100));
    }

    async loadLiepinDicts() {
        try {
            const res = await fetch('/dicts/LIEPIN');
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const data = await res.json();
            if (!data || !Array.isArray(data.groups)) {
                console.warn('Liepin dict data structure incorrect:', data);
                return;
            }

            const groupMap = new Map();
            data.groups.forEach(g => groupMap.set(g.key, g.items || []));

            this.renderCitySelection(groupMap.get('cityList') || []);
            this.fillSelect('liepinExperienceComboBox', groupMap.get('experienceList'));
            this.fillSelect('liepinJobTypeComboBox', groupMap.get('jobTypeList'));
            this.fillSelect('liepinSalaryComboBox', groupMap.get('salaryList'));
            this.fillSelect('liepinDegreeComboBox', groupMap.get('degreeList'));
            this.fillSelect('liepinScaleComboBox', groupMap.get('scaleList'));
            this.fillSelect('liepinCompanyNatureComboBox', groupMap.get('companyNatureList'));
            this.fillSelect('liepinRecruiterActivityComboBox', groupMap.get('recruiterActivityList'));

        } catch (e) {
            console.warn('Loading Liepin dicts failed:', e?.message || e);
            throw e;
        }
    }

    renderCitySelection(cityItems) {
        const citySelect = document.getElementById('liepinCityCodeField');
        const citySearch = document.getElementById('liepinCitySearchField');
        const cityListContainer = document.getElementById('liepinCityDropdownList');

        const renderCityOptions = (list) => {
            if (!citySelect) return;
            const selected = new Set(Array.from(citySelect.selectedOptions).map(o => o.value));
            citySelect.innerHTML = '';
            list.forEach(it => {
                const value = it.code ?? '';
                const opt = document.createElement('option');
                opt.value = value;
                opt.textContent = `${it.name ?? ''}${it.code ? ' (' + it.code + ')' : ''}`;
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
                    item.innerHTML = `<input class="form-check-input" type="checkbox" value="${value}" id="${id}" ${selected.has(value) ? 'checked' : ''}><label class="form-check-label small" for="${id}">${label}</label>`;
                    item.querySelector('input[type="checkbox"]').addEventListener('change', (e) => {
                        const option = Array.from(citySelect.options).find(o => o.value === e.target.value);
                        if (option) option.selected = e.target.checked;
                        this.updateCitySummary();
                    });
                    cityListContainer.appendChild(item);
                });
            }
            this.updateCitySummary();
        };

        renderCityOptions(cityItems);
        citySearch?.addEventListener('input', () => {
            const kw = citySearch.value.trim().toLowerCase();
            const filtered = kw ? cityItems.filter(it => String(it.name || '').toLowerCase().includes(kw) || String(it.code || '').toLowerCase().includes(kw)) : cityItems;
            renderCityOptions(filtered);
        });
    }

    fillSelect(selectId, items) {
        if (!Array.isArray(items) || items.length === 0) return;
        const sel = document.getElementById(selectId);
        if (!sel) return;
        const first = sel.querySelector('option');
        sel.innerHTML = '';
        if (first && first.value === '') sel.appendChild(first);
        items.forEach(it => {
            const opt = document.createElement('option');
            opt.value = it.code ?? it.name ?? '';
            opt.textContent = it.name ?? String(it.code ?? '');
            sel.appendChild(opt);
        });
    }

    updateCitySummary() {
        const citySelect = document.getElementById('liepinCityCodeField');
        const cityDropdownBtn = document.getElementById('liepinCityDropdownBtn');
        const citySummary = document.getElementById('liepinCitySelectionSummary');
        if (!citySelect || !cityDropdownBtn || !citySummary) return;

        const values = Array.from(citySelect.selectedOptions).map(o => o.textContent);
        if (values.length === 0) {
            cityDropdownBtn.textContent = '选择城市';
            citySummary.textContent = '未选择';
        } else if (values.length <= 2) {
            const text = values.join('、');
            cityDropdownBtn.textContent = text;
            citySummary.textContent = `已选 ${values.length} 项：${text}`;
        } else {
            cityDropdownBtn.textContent = `已选 ${values.length} 项`;
            citySummary.textContent = `已选 ${values.length} 项`;
        }
    }

    handleSaveConfig() {
        this.saveConfig();
        this.showToast('猎聘配置已保存');
    }

    handleBackupData() {
        const backupBtn = document.getElementById('liepinBackupDataBtn');
        if (backupBtn) {
            backupBtn.disabled = true;
            backupBtn.innerHTML = '<i class="bi bi-hourglass-split me-2"></i>备份中...';
        }
        fetch('/api/backup/export', { method: 'POST' })
            .then(res => res.json())
            .then(data => {
                this.showToast(data.success ? '数据库备份成功' : '数据库备份失败: ' + data.message, data.success ? 'success' : 'danger');
            })
            .catch(error => this.showToast('数据库备份失败: ' + error.message, 'danger'))
            .finally(() => {
                if (backupBtn) {
                    backupBtn.disabled = false;
                    backupBtn.innerHTML = '<i class="bi bi-database me-2"></i>数据库备份';
                }
            });
    }

    async handleLogin() {
        this.updateButtonState('liepinLoginBtn', 'liepinLoginStatus', '执行中...', true);
        try {
            const response = await fetch('/api/liepin/task/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(this.getCurrentConfig())
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
        } catch (error) {
            this.updateButtonState('liepinLoginBtn', 'liepinLoginStatus', '登录失败', false);
            this.showToast('登录接口调用失败: ' + error.message, 'danger');
        }
    }

    async handleCollect() {
        if (!this.taskStates.loginTaskId) {
            this.showAlertModal('操作提示', '请先完成登录步骤');
            return;
        }
        this.updateButtonState('liepinCollectBtn', 'liepinCollectStatus', '采集中...', true);
        try {
            const response = await fetch('/api/liepin/task/collect', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(this.getCurrentConfig())
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
        } catch (error) {
            this.updateButtonState('liepinCollectBtn', 'liepinCollectStatus', '采集失败', false);
            this.showToast('采集接口调用失败: ' + error.message, 'danger');
        }
    }

    async handleFilter() {
        if (!this.taskStates.loginTaskId) {
            this.showAlertModal('操作提示', '请先完成登录步骤');
            return;
        }
        this.updateButtonState('liepinFilterBtn', 'liepinFilterStatus', '过滤中...', true);
        try {
            const request = { collectTaskId: this.taskStates.collectTaskId, config: this.getCurrentConfig() };
            const response = await fetch('/api/liepin/task/filter', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            });
            const result = await response.json();
            if (result.success) {
                this.taskStates.filterTaskId = result.taskId;
                this.updateButtonState('liepinFilterBtn', 'liepinFilterStatus', `过滤完成(${result.originalCount}→${result.filteredCount})`, false);
                this.showToast(`过滤完成，从 ${result.originalCount} 个职位中筛选出 ${result.filteredCount} 个！`);
            } else {
                this.updateButtonState('liepinFilterBtn', 'liepinFilterStatus', '过滤失败', false);
                this.showToast(result.message || '过滤失败', 'danger');
            }
        } catch (error) {
            this.updateButtonState('liepinFilterBtn', 'liepinFilterStatus', '过滤失败', false);
            this.showToast('过滤接口调用失败: ' + error.message, 'danger');
        }
    }

    handleApply() {
        if (!this.taskStates.loginTaskId) {
            this.showAlertModal('操作提示', '请先完成登录步骤');
            return;
        }
        this.showConfirmModal('投递确认', '是否执行实际投递？', () => this.executeApply(true), () => this.executeApply(false));
    }

    async executeApply(enableActualDelivery) {
        this.updateButtonState('liepinApplyBtn', 'liepinApplyStatus', '投递中...', true);
        try {
            const request = { filterTaskId: this.taskStates.filterTaskId, config: this.getCurrentConfig(), enableActualDelivery };
            const response = await fetch('/api/liepin/task/deliver', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            });
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
        } catch (error) {
            this.updateButtonState('liepinApplyBtn', 'liepinApplyStatus', '投递失败', false);
            this.showToast('投递接口调用失败: ' + error.message, 'danger');
        }
    }

    getCurrentConfig() {
        const getMultiSelectValues = (selectId) => Array.from(document.getElementById(selectId)?.selectedOptions || []).map(o => o.value).filter(Boolean).join(',');
        return {
            keywords: document.getElementById('liepinKeywordsField')?.value || '',
            cityCode: getMultiSelectValues('liepinCityCodeField'),
            experience: document.getElementById('liepinExperienceComboBox')?.value || '',
            jobType: document.getElementById('liepinJobTypeComboBox')?.value || '',
            salary: document.getElementById('liepinSalaryComboBox')?.value || '',
            degree: document.getElementById('liepinDegreeComboBox')?.value || '',
            scale: document.getElementById('liepinScaleComboBox')?.value || '',
            companyNature: document.getElementById('liepinCompanyNatureComboBox')?.value || '',
            recruiterActivity: document.getElementById('liepinRecruiterActivityComboBox')?.value || '',
            blacklistFilter: document.getElementById('liepinBlacklistFilterCheckBox')?.checked || false,
            blacklistKeywords: document.getElementById('liepinBlacklistKeywordsTextArea')?.value || '',
            enableAIJobMatch: document.getElementById('liepinEnableAIJobMatchCheckBox')?.checked || false
        };
    }

    resetTaskFlow() {
        this.showConfirmModal('重置确认', '确定要重置任务流程吗？', () => {
            this.taskStates = { loginTaskId: null, collectTaskId: null, filterTaskId: null, applyTaskId: null };
            this.updateButtonState('liepinLoginBtn', 'liepinLoginStatus', '待执行', false);
            this.updateButtonState('liepinCollectBtn', 'liepinCollectStatus', '等待登录', true);
            this.updateButtonState('liepinFilterBtn', 'liepinFilterStatus', '等待登录', true);
            this.updateButtonState('liepinApplyBtn', 'liepinApplyStatus', '等待登录', true);
            this.showToast('任务流程已重置', 'info');
        });
    }

    updateButtonState(buttonId, statusId, statusText, isLoading) {
        const button = document.getElementById(buttonId);
        const status = document.getElementById(statusId);
        if (button) button.disabled = isLoading;
        if (status) {
            status.textContent = statusText;
            status.className = `badge ms-2 ${isLoading ? 'bg-warning text-dark' : 'bg-success text-white'}`;
        }
    }

    enableNextStep(buttonId, statusId, statusText) {
        const button = document.getElementById(buttonId);
        const status = document.getElementById(statusId);
        if (button) button.disabled = false;
        if (status) {
            status.textContent = statusText;
            status.className = 'badge bg-info text-white ms-2';
        }
    }

    showToast(message, variant = 'success') {
        const toastEl = document.getElementById('globalToast');
        const bodyEl = document.getElementById('globalToastBody');
        if (!toastEl || !bodyEl) return;
        bodyEl.textContent = message;
        toastEl.className = `toast align-items-center text-bg-${variant} border-0`;
        bootstrap.Toast.getOrCreateInstance(toastEl).show();
    }

    showConfirmModal(title, message, onConfirm, onCancel) {
        const modalEl = document.getElementById('confirmModal');
        const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
        document.getElementById('confirmModalLabel').textContent = title;
        document.getElementById('confirmModalBody').textContent = message;
        const okBtn = document.getElementById('confirmModalOk');
        const newOkBtn = okBtn.cloneNode(true);
        okBtn.parentNode.replaceChild(newOkBtn, okBtn);
        newOkBtn.addEventListener('click', () => {
            modal.hide();
            onConfirm?.();
        }, { once: true });
        modalEl.addEventListener('hidden.bs.modal', () => onCancel?.(), { once: true });
        modal.show();
    }

    showAlertModal(title, message) {
        const modal = bootstrap.Modal.getOrCreateInstance(document.getElementById('alertModal'));
        document.getElementById('alertModalLabel').textContent = title;
        document.getElementById('alertModalBody').textContent = message;
        modal.show();
    }
}

window.LiepinConfigForm = LiepinConfigForm;
