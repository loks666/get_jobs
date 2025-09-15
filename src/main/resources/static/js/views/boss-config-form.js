// Boss 配置表单模块（导出类，不自动初始化）
(function () {
    if (!window.Views) window.Views = {};

    class BossConfigApp {
        constructor() {
            this.config = {};
            this.isRunning = false;
            this.init();
        }

        init() {
            this.initializeTooltips();
            this.bindEvents();
            this.loadSavedConfig();
        }

        initializeTooltips() {
            const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
            tooltipTriggerList.map(function (tooltipTriggerEl) {
                return new bootstrap.Tooltip(tooltipTriggerEl);
            });
        }

        bindEvents() {
            document.getElementById('saveConfigBtn')?.addEventListener('click', () => {
                this.handleSaveOnly();
            });
            document.getElementById('backupDataBtn')?.addEventListener('click', () => {
                this.handleBackupData();
            });
            document.getElementById('startDeliveryBtn')?.addEventListener('click', () => {
                this.handleStartOnly();
            });
            this.bindFormValidation();
            this.bindAdvancedConfig();
            this.bindAutoSave();
        }

        bindFormValidation() {
            const requiredFields = [
                'keywordsField',
                'cityCodeField',
                'resumeImagePathField',
                'sayHiTextArea'
            ];
            requiredFields.forEach(fieldId => {
                const field = document.getElementById(fieldId);
                if (field) {
                    field.addEventListener('blur', () => {
                        this.validateField(field);
                    });
                }
            });
            const minSalary = document.getElementById('minSalaryField');
            const maxSalary = document.getElementById('maxSalaryField');
            if (minSalary && maxSalary) {
                [minSalary, maxSalary].forEach(field => {
                    field.addEventListener('input', () => {
                        this.validateSalaryRange();
                    });
                });
            }
            const waitTimeField = document.getElementById('waitTimeField');
            if (waitTimeField) {
                waitTimeField.addEventListener('input', () => {
                    this.validateWaitTime();
                });
            }
        }

        validateField(field) {
            const value = field.value.trim();
            const isValid = value.length > 0;
            this.updateFieldValidation(field, isValid);
            return isValid;
        }

        validateSalaryRange() {
            const minSalary = document.getElementById('minSalaryField');
            const maxSalary = document.getElementById('maxSalaryField');
            const minValue = parseInt(minSalary.value) || 0;
            const maxValue = parseInt(maxSalary.value) || 0;
            const isValid = minValue > 0 && maxValue > 0 && minValue <= maxValue;
            this.updateFieldValidation(minSalary, isValid);
            this.updateFieldValidation(maxSalary, isValid);
            return isValid;
        }

        validateWaitTime() {
            const waitTimeField = document.getElementById('waitTimeField');
            const value = parseInt(waitTimeField.value) || 0;
            const isValid = value >= 1 && value <= 300;
            this.updateFieldValidation(waitTimeField, isValid);
            return isValid;
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

        bindAdvancedConfig() {
            this.bindCityCodeConfig();
            this.bindHRStatusConfig();
        }

        bindCityCodeConfig() {
            const container = document.getElementById('customCityCodeContainer');
            if (!container) return;
            const sampleCityCodes = [
                { city: '北京', code: '101010100' },
                { city: '上海', code: '101020100' },
                { city: '深圳', code: '101280600' },
                { city: '广州', code: '101280100' }
            ];
            sampleCityCodes.forEach(item => {
                this.addCityCodeItem(container, item.city, item.code);
            });
            const addBtn = document.createElement('button');
            addBtn.className = 'btn btn-sm btn-outline-primary mt-2';
            addBtn.innerHTML = '<i class="bi bi-plus-circle me-1"></i>添加城市代码';
            addBtn.onclick = () => this.showAddCityCodeModal();
            container.appendChild(addBtn);
        }

        addCityCodeItem(container, city, code) {
            const item = document.createElement('div');
            item.className = 'd-flex justify-content-between align-items-center mb-2 p-2 bg-white rounded border';
            item.innerHTML = `
                <span class="fw-semibold">${city} - ${code}</span>
                <button class="btn btn-sm btn-outline-danger" onclick="this.parentElement.remove()">
                    <i class="bi bi-trash"></i>
                </button>
            `;
            container.appendChild(item);
        }

        showAddCityCodeModal() {
            const city = prompt('请输入城市名称:');
            const code = prompt('请输入城市代码:');
            if (city && code) {
                const container = document.getElementById('customCityCodeContainer');
                this.addCityCodeItem(container, city, code);
            }
        }

        bindHRStatusConfig() {
            const container = document.getElementById('deadStatusContainer');
            if (!container) return;
            const sampleStatuses = [ '长期未活跃', '已离职', '账号异常', '不回复消息' ];
            sampleStatuses.forEach(status => {
                this.addHRStatusItem(container, status);
            });
            const addBtn = document.createElement('button');
            addBtn.className = 'btn btn-sm btn-outline-warning mt-2';
            addBtn.innerHTML = '<i class="bi bi-plus-circle me-1"></i>添加HR状态';
            addBtn.onclick = () => this.showAddHRStatusModal();
            container.appendChild(addBtn);
        }

        addHRStatusItem(container, status) {
            const item = document.createElement('div');
            item.className = 'd-flex justify-content-between align-items-center mb-2 p-2 bg-white rounded border';
            item.innerHTML = `
                <span class="fw-semibold">${status}</span>
                <button class="btn btn-sm btn-outline-danger" onclick="this.parentElement.remove()">
                    <i class="bi bi-trash"></i>
                </button>
            `;
            container.appendChild(item);
        }

        showAddHRStatusModal() {
            const status = prompt('请输入HR状态描述:');
            if (status) {
                const container = document.getElementById('deadStatusContainer');
                this.addHRStatusItem(container, status);
            }
        }

        bindAutoSave() {
            const formElements = document.querySelectorAll('input, select, textarea');
            formElements.forEach(element => {
                element.addEventListener('change', () => {
                    this.saveConfig();
                });
            });
        }

        saveConfig() {
            this.config = {
                keywords: document.getElementById('keywordsField').value,
                industry: document.getElementById('industryField').value,
                cityCode: document.getElementById('cityCodeField').value,
                experience: document.getElementById('experienceComboBox').value,
                jobType: document.getElementById('jobTypeComboBox').value,
                salary: document.getElementById('salaryComboBox').value,
                degree: document.getElementById('degreeComboBox').value,
                scale: document.getElementById('scaleComboBox').value,
                stage: document.getElementById('stageComboBox').value,
                minSalary: document.getElementById('minSalaryField').value,
                maxSalary: document.getElementById('maxSalaryField').value,
                resumeImagePath: document.getElementById('resumeImagePathField').value,
                resumeContent: document.getElementById('resumeContentTextArea').value,
                sayHi: document.getElementById('sayHiTextArea').value,
                filterDeadHR: document.getElementById('filterDeadHRCheckBox').checked,
                sendImgResume: document.getElementById('sendImgResumeCheckBox').checked,
                keyFilter: document.getElementById('keyFilterCheckBox').checked,
                recommendJobs: document.getElementById('recommendJobsCheckBox').checked,
                enableAIJobMatchDetection: document.getElementById('enableAIJobMatchDetectionCheckBox').checked,
                enableAIGreeting: document.getElementById('enableAIGreetingCheckBox').checked,
                checkStateOwned: document.getElementById('checkStateOwnedCheckBox').checked,
                waitTime: document.getElementById('waitTimeField').value
            };
            localStorage.setItem('bossConfig', JSON.stringify(this.config));
            try {
                fetch('/api/config/boss', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(this.config)
                }).then(() => {}).catch(() => {});
            } catch (e) {}
        }

        loadSavedConfig() {
            fetch('/api/config/boss')
                .then(async res => {
                    if (!res.ok) throw new Error('HTTP ' + res.status);
                    const ct = res.headers.get('content-type') || '';
                    if (ct.includes('application/json')) {
                        return res.json();
                    } else {
                        const text = await res.text();
                        const snippet = (text || '').slice(0, 80);
                        throw new Error('返回非JSON：' + snippet);
                    }
                })
                .then(data => {
                    if (data && typeof data === 'object' && Object.keys(data).length) {
                        this.config = data;
                        this.populateForm();
                        localStorage.setItem('bossConfig', JSON.stringify(this.config));
                        return;
                    }
                    const savedConfig = localStorage.getItem('bossConfig');
                    if (savedConfig) {
                        try {
                            this.config = JSON.parse(savedConfig);
                            this.populateForm();
                        } catch (error) {
                            console.warn('本地配置损坏，已清理：' + error.message);
                            localStorage.removeItem('bossConfig');
                        }
                    }
                })
                .catch((err) => {
                    console.warn('后端配置读取失败：' + (err?.message || '未知错误'));
                    const savedConfig = localStorage.getItem('bossConfig');
                    if (savedConfig) {
                        try {
                            this.config = JSON.parse(savedConfig);
                            this.populateForm();
                        } catch (error) {
                            console.warn('本地配置损坏，已清理：' + error.message);
                            localStorage.removeItem('bossConfig');
                        }
                    }
                });
        }

        populateForm() {
            Object.keys(this.config).forEach(key => {
                const element = document.getElementById(this.getFieldId(key));
                if (element) {
                    if (element.type === 'checkbox') {
                        element.checked = this.config[key];
                    } else {
                        element.value = this.config[key];
                    }
                }
            });
        }

        getFieldId(key) {
            const fieldMap = {
                keywords: 'keywordsField',
                industry: 'industryField',
                cityCode: 'cityCodeField',
                experience: 'experienceComboBox',
                jobType: 'jobTypeComboBox',
                salary: 'salaryComboBox',
                degree: 'degreeComboBox',
                scale: 'scaleComboBox',
                stage: 'stageComboBox',
                minSalary: 'minSalaryField',
                maxSalary: 'maxSalaryField',
                resumeImagePath: 'resumeImagePathField',
                resumeContent: 'resumeContentTextArea',
                sayHi: 'sayHiTextArea',
                filterDeadHR: 'filterDeadHRCheckBox',
                sendImgResume: 'sendImgResumeCheckBox',
                keyFilter: 'keyFilterCheckBox',
                recommendJobs: 'recommendJobsCheckBox',
                enableAIJobMatchDetection: 'enableAIJobMatchDetectionCheckBox',
                enableAIGreeting: 'enableAIGreetingCheckBox',
                checkStateOwned: 'checkStateOwnedCheckBox',
                waitTime: 'waitTimeField'
            };
            return fieldMap[key] || key;
        }

        handleSaveOnly() {
            this.saveConfig();
            try {
                const toastEl = document.getElementById('globalToast');
                const bodyEl = document.getElementById('globalToastBody');
                if (toastEl && bodyEl) {
                    bodyEl.textContent = '配置已保存';
                    const toast = bootstrap.Toast.getOrCreateInstance(toastEl, { delay: 2000 });
                    toast.show();
                }
            } catch (_) {}
        }

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
                    this.showToast('数据库备份成功', 'success');
                    console.log('备份路径:', data.backupPath);
                } else {
                    this.showToast('数据库备份失败: ' + data.message, 'error');
                }
            })
            .catch(error => {
                console.error('备份请求失败:', error);
                this.showToast('数据库备份失败: ' + error.message, 'error');
            })
            .finally(() => {
                if (backupBtn) {
                    backupBtn.disabled = false;
                    backupBtn.innerHTML = '<i class="bi bi-database me-2"></i>数据库备份';
                }
            });
        }

        showToast(message, type = 'success') {
            try {
                const toastEl = document.getElementById('globalToast');
                const bodyEl = document.getElementById('globalToastBody');
                if (toastEl && bodyEl) {
                    bodyEl.textContent = message;
                    toastEl.className = `toast align-items-center text-bg-${type === 'success' ? 'success' : 'danger'} border-0`;
                    const toast = bootstrap.Toast.getOrCreateInstance(toastEl, { delay: 3000 });
                    toast.show();
                }
            } catch (error) {
                console.error('显示提示失败:', error);
            }
        }

        handleStartOnly() {
            if (this.isRunning) {
                alert('任务已在运行中...');
                return;
            }
            if (!this.validateRequiredFields()) {
                alert('请先完善必填项再开始执行');
                return;
            }
            this.startExecution();
        }

        validateRequiredFields() {
            const requiredFields = [
                'keywordsField',
                'cityCodeField',
                'resumeImagePathField',
                'sayHiTextArea'
            ];
            let isValid = true;
            requiredFields.forEach(fieldId => {
                const field = document.getElementById(fieldId);
                if (field && !this.validateField(field)) {
                    isValid = false;
                }
            });
            return isValid && this.validateSalaryRange() && this.validateWaitTime();
        }

        startExecution() {
            this.isRunning = true;
            const startBtn = document.getElementById('startDeliveryBtn');
            if (startBtn) {
                startBtn.disabled = true;
                startBtn.innerHTML = '<i class="bi bi-hourglass-split me-2"></i>执行中...';
                startBtn.classList.add('loading');
            }
            this.simulateExecution();
        }

        simulateExecution() {
            const steps = [
                { message: '正在启动浏览器...', delay: 2000 },
                { message: '正在登录Boss直聘...', delay: 3000 },
                { message: '正在设置搜索条件...', delay: 2000 },
                { message: '正在筛选职位...', delay: 4000 },
                { message: '正在投递简历...', delay: 5000 },
                { message: '正在发送打招呼消息...', delay: 3000 },
                { message: '任务执行完成！', delay: 1000 }
            ];
            let currentStep = 0;
            const executeStep = () => {
                if (currentStep < steps.length) {
                    const step = steps[currentStep];
                    console.log(step.message);
                    currentStep++;
                    setTimeout(executeStep, step.delay);
                } else {
                    this.finishExecution();
                }
            };
            executeStep();
        }

        finishExecution() {
            this.isRunning = false;
            const startBtn = document.getElementById('startDeliveryBtn');
            if (startBtn) {
                startBtn.disabled = false;
                startBtn.innerHTML = '<i class="bi bi-rocket-takeoff me-2"></i>开始执行投递';
                startBtn.classList.remove('loading');
                startBtn.classList.add('success-flash');
                setTimeout(() => startBtn.classList.remove('success-flash'), 600);
            }
        }
    }

    // 导出为全局可用类，由 app.js 统一初始化
    window.Views.BossConfigForm = BossConfigApp;
})();


