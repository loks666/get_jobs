// Boss 配置表单模块（导出类，不自动初始化）
(function () {
    if (!window.Views) window.Views = {};

    class BossConfigApp {
        constructor() {
            this.config = {};
            this.isRunning = false;
            this.dictDataLoaded = false; // 字典数据加载状态标志
            this.init();
        }

        init() {
            this.initializeTooltips();
            this.bindEvents();
            // 先加载字典数据，再加载配置数据，确保下拉框已准备好
            this.loadDataSequentially();
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

        // 按顺序加载数据：先字典，后配置
        async loadDataSequentially() {
            try {
                console.log('BossConfigForm: 开始按顺序加载数据：字典 -> 配置');
                
                // 先加载字典数据
                await this.loadBossDicts();
                console.log('BossConfigForm: 字典数据加载完成，开始加载配置数据');
                
                // 等待DOM元素完全渲染
                await this.waitForDOMReady();
                
                // 再加载配置数据
                await this.loadSavedConfig();
                console.log('BossConfigForm: 配置数据加载完成');
                
            } catch (error) {
                console.error('BossConfigForm: 数据加载失败:', error);
            }
        }

        // 等待DOM元素完全准备就绪
        async waitForDOMReady() {
            return new Promise((resolve) => {
                // 等待一个事件循环，确保所有DOM操作完成
                setTimeout(() => {
                    console.log('BossConfigForm: DOM元素准备就绪');
                    resolve();
                }, 100);
            });
        }

        // 加载保存的配置
        async loadSavedConfig() {
            try {
                console.log('BossConfigForm: 开始加载配置数据...');
                
                const res = await fetch('/api/config/boss');
                if (!res.ok) throw new Error('HTTP ' + res.status);
                const ct = res.headers.get('content-type') || '';
                let data;
                if (ct.includes('application/json')) {
                    data = await res.json();
                } else {
                    const text = await res.text();
                    const snippet = (text || '').slice(0, 80);
                    throw new Error('返回非JSON：' + snippet);
                }
                
                if (data && typeof data === 'object' && Object.keys(data).length) {
                    this.config = data;
                    console.log('BossConfigForm: 从后端加载到配置数据:', this.config);
                    
                    // 确保字典数据已加载后再填充表单
                    await this.waitForDictDataReady();
                    this.populateForm();
                    
                    localStorage.setItem('bossConfig', JSON.stringify(this.config));
                    return;
                }
                
                // 如果后端没有数据，尝试本地缓存
                const savedConfig = localStorage.getItem('bossConfig');
                if (savedConfig) {
                    try {
                        this.config = JSON.parse(savedConfig);
                        console.log('BossConfigForm: 从本地缓存加载到配置数据:', this.config);
                        
                        // 确保字典数据已加载后再填充表单
                        await this.waitForDictDataReady();
                        this.populateForm();
                    } catch (error) {
                        console.warn('本地配置损坏，已清理：' + error.message);
                        localStorage.removeItem('bossConfig');
                    }
                }
            } catch (err) {
                console.warn('后端配置读取失败：' + (err?.message || '未知错误'));
                // 尝试本地缓存
                const savedConfig = localStorage.getItem('bossConfig');
                if (savedConfig) {
                    try {
                        this.config = JSON.parse(savedConfig);
                        console.log('BossConfigForm: 从本地缓存加载到配置数据（异常情况）:', this.config);
                        
                        // 确保字典数据已加载后再填充表单
                        await this.waitForDictDataReady();
                        this.populateForm();
                    } catch (error) {
                        console.warn('本地配置损坏，已清理：' + error.message);
                        localStorage.removeItem('bossConfig');
                    }
                }
            }
        }

        // 等待字典数据准备就绪
        async waitForDictDataReady() {
            return new Promise(async (resolve) => {
                // 首先等待字典数据加载事件
                if (!this.dictDataLoaded) {
                    console.log('BossConfigForm: 等待字典数据加载完成...');
                    
                    // 监听字典数据加载完成事件
                    const handleDictLoaded = () => {
                        console.log('BossConfigForm: 收到字典数据加载完成事件');
                        window.removeEventListener('bossDictDataLoaded', handleDictLoaded);
                        // 继续等待DOM完全渲染
                        this.waitForAllDictDataReady().then(resolve);
                    };
                    
                    window.addEventListener('bossDictDataLoaded', handleDictLoaded);
                    
                    // 设置超时，避免无限等待
                    setTimeout(() => {
                        console.warn('BossConfigForm: 等待字典数据超时，强制继续');
                        window.removeEventListener('bossDictDataLoaded', handleDictLoaded);
                        this.waitForAllDictDataReady().then(resolve);
                    }, 5000);
                } else {
                    // 字典数据已加载，等待DOM完全渲染
                    console.log('BossConfigForm: 字典数据已就绪，等待DOM完全渲染');
                    await this.waitForAllDictDataReady();
                    resolve();
                }
            });
        }

        populateForm() {
            console.log('BossConfigForm: 开始填充表单，配置数据:', this.config);
            
            // 先处理普通字段
            Object.keys(this.config).forEach(key => {
                const element = document.getElementById(this.getFieldId(key));
                if (element) {
                    if (element.type === 'checkbox') {
                        element.checked = this.config[key];
                        console.log(`BossConfigForm: 设置复选框 ${key} = ${this.config[key]}`);
                    } else {
                        // 处理可能的数组字段转换为逗号分隔字符串
                        let value = this.config[key];
                        if (Array.isArray(value)) {
                            value = value.join(',');
                            console.log(`BossConfigForm: 数组字段 ${key} 转换为字符串:`, this.config[key], '->', value);
                        }
                        element.value = value || '';
                        console.log(`BossConfigForm: 设置字段 ${key} = ${value}`);
                    }
                }
            });

            // 特殊处理城市选择器
            this.populateCitySelector();
            
            // 特殊处理期望薪资字段
            this.populateExpectedSalary();
            
            // 特殊处理其他下拉框
            this.populateSelectBoxes();
        }

        // 填充城市选择器
        populateCitySelector() {
            const cityCode = this.config.cityCode;
            if (!cityCode) return;
            
            // 处理数组格式（从后端返回）或字符串格式（从本地缓存）
            let cityCodeStr = '';
            if (Array.isArray(cityCode)) {
                cityCodeStr = cityCode.join(',');
            } else {
                cityCodeStr = cityCode;
            }
            
            console.log('BossConfigForm: 填充城市选择器，原始城市代码:', cityCode, '处理后:', cityCodeStr);
            
            const citySelect = document.getElementById('cityCodeField');
            const cityDropdownBtn = document.getElementById('cityDropdownBtn');
            const citySummary = document.getElementById('citySelectionSummary');
            
            if (!citySelect) {
                console.warn('BossConfigForm: 未找到城市选择器元素');
                return;
            }

            // 解析城市代码（支持逗号分隔的多个城市）
            const codes = cityCodeStr.split(',').map(s => s.trim()).filter(Boolean);
            console.log('BossConfigForm: 解析的城市代码:', codes);

            // 设置隐藏select的选中状态
            Array.from(citySelect.options).forEach(opt => {
                opt.selected = codes.includes(opt.value);
            });

            // 更新下拉框显示状态
            this.updateCityDropdownDisplay();

            // 更新城市摘要
            if (typeof this.updateCitySummary === 'function') {
                this.updateCitySummary();
            } else {
                this.updateCitySummaryFallback();
            }
        }

        // 更新城市下拉框显示状态
        updateCityDropdownDisplay() {
            const citySelect = document.getElementById('cityCodeField');
            const cityListContainer = document.getElementById('cityDropdownList');
            
            if (!citySelect || !cityListContainer) return;

            // 更新checkbox状态
            const checkboxes = cityListContainer.querySelectorAll('input[type="checkbox"]');
            checkboxes.forEach(checkbox => {
                const option = Array.from(citySelect.options).find(o => o.value === checkbox.value);
                if (option) {
                    checkbox.checked = option.selected;
                }
            });
        }

        // 更新城市摘要显示
        updateCitySummary() {
            const cityDropdownBtn = document.getElementById('cityDropdownBtn');
            const citySummary = document.getElementById('citySelectionSummary');
            const citySelect = document.getElementById('cityCodeField');
            
            if (!cityDropdownBtn || !citySummary || !citySelect) return;

            const selectedOptions = Array.from(citySelect.selectedOptions);
            const values = selectedOptions.map(o => o.textContent);
            
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

        // 备用的城市摘要更新方法
        updateCitySummaryFallback() {
            const cityDropdownBtn = document.getElementById('cityDropdownBtn');
            const citySummary = document.getElementById('citySelectionSummary');
            const citySelect = document.getElementById('cityCodeField');
            
            if (!cityDropdownBtn || !citySummary || !citySelect) return;

            const selectedOptions = Array.from(citySelect.selectedOptions);
            const values = selectedOptions.map(o => o.textContent);
            
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

        // 填充期望薪资字段
        populateExpectedSalary() {
            const expectedSalary = this.config.expectedSalary;
            if (Array.isArray(expectedSalary) && expectedSalary.length >= 2) {
                const minSalaryField = document.getElementById('minSalaryField');
                const maxSalaryField = document.getElementById('maxSalaryField');
                
                if (minSalaryField && maxSalaryField) {
                    minSalaryField.value = expectedSalary[0] || '';
                    maxSalaryField.value = expectedSalary[1] || '';
                    console.log(`BossConfigForm: 期望薪资回填: ${expectedSalary[0]} ~ ${expectedSalary[1]}`);
                }
            }
        }

        // 填充其他下拉框
        populateSelectBoxes() {
            const selectFields = [
                'experienceComboBox',
                'jobTypeComboBox', 
                'salaryComboBox',
                'degreeComboBox',
                'scaleComboBox',
                'stageComboBox'
            ];

            selectFields.forEach(fieldId => {
                const element = document.getElementById(fieldId);
                const configKey = this.getConfigKeyFromFieldId(fieldId);
                
                if (element && this.config[configKey]) {
                    let value = this.config[configKey];
                    
                    // 处理数组格式，取第一个元素（下拉框只能选一个值）
                    if (Array.isArray(value)) {
                        value = value.length > 0 ? value[0] : '';
                        console.log(`BossConfigForm: 下拉框 ${fieldId} 数组转换:`, this.config[configKey], '->', value);
                    }
                    
                    console.log(`BossConfigForm: 设置下拉框 ${fieldId} = ${value}`);
                    
                    // 查找匹配的选项
                    const option = Array.from(element.options).find(opt => opt.value === value);
                    if (option) {
                        element.value = value;
                        console.log(`BossConfigForm: 成功设置下拉框 ${fieldId}`);
                    } else {
                        console.warn(`BossConfigForm: 下拉框 ${fieldId} 中未找到值 ${value} 对应的选项`);
                    }
                }
            });
        }

        // 从字段ID获取配置键名
        getConfigKeyFromFieldId(fieldId) {
            const fieldMap = {
                'experienceComboBox': 'experience',
                'jobTypeComboBox': 'jobType',
                'salaryComboBox': 'salary',
                'degreeComboBox': 'degree',
                'scaleComboBox': 'scale',
                'stageComboBox': 'stage'
            };
            return fieldMap[fieldId] || fieldId;
        }

        // 检查字典数据是否完全加载
        isDictDataReady() {
            const requiredSelects = [
                'experienceComboBox',
                'jobTypeComboBox',
                'salaryComboBox',
                'degreeComboBox',
                'scaleComboBox',
                'stageComboBox',
                'cityCodeField'
            ];

            for (const selectId of requiredSelects) {
                const select = document.getElementById(selectId);
                if (!select || select.options.length <= 1) {
                    console.log(`BossConfigForm: 下拉框 ${selectId} 尚未完全加载`);
                    return false;
                }
            }

            console.log('BossConfigForm: 所有字典数据已完全加载');
            return true;
        }

        // 等待所有字典数据完全加载
        async waitForAllDictDataReady() {
            return new Promise((resolve) => {
                if (this.isDictDataReady()) {
                    resolve();
                    return;
                }

                console.log('BossConfigForm: 等待所有字典数据完全加载...');
                
                let attempts = 0;
                const maxAttempts = 50; // 最多等待5秒
                
                const checkInterval = setInterval(() => {
                    attempts++;
                    
                    if (this.isDictDataReady()) {
                        clearInterval(checkInterval);
                        console.log('BossConfigForm: 所有字典数据加载完成');
                        resolve();
                    } else if (attempts >= maxAttempts) {
                        clearInterval(checkInterval);
                        console.warn('BossConfigForm: 等待字典数据超时，强制继续');
                        resolve();
                    }
                }, 100);
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
            return isValid && this.validateSalaryRange() ;
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

        // 加载Boss字典数据
        async loadBossDicts() {
            // 等待DOM元素准备就绪
            await this.waitForDOMElements();
            
            try {
                console.log('BossConfigForm: 开始加载Boss字典数据...');
                const res = await fetch('/dicts/BOSS_ZHIPIN');
                if (!res.ok) throw new Error('HTTP ' + res.status);
                const data = await res.json();
                console.log('BossConfigForm: 接收到字典数据:', data);
                
                if (!data || !Array.isArray(data.groups)) {
                    console.warn('BossConfigForm: 字典数据结构不正确:', data);
                    return;
                }

                const groupMap = new Map();
                data.groups.forEach(g => {
                    console.log(`BossConfigForm: 处理字典组: ${g.key}, 项目数量: ${Array.isArray(g.items) ? g.items.length : 0}`);
                    groupMap.set(g.key, Array.isArray(g.items) ? g.items : []);
                });

                // 渲染城市选择器
                this.renderCitySelector(groupMap.get('cityList') || []);
                
                // 渲染其他下拉框
                this.fillSelect('experienceComboBox', groupMap.get('experienceList'));
                this.fillSelect('salaryComboBox', groupMap.get('salaryList'));
                this.fillSelect('degreeComboBox', groupMap.get('degreeList'));
                this.fillSelect('scaleComboBox', groupMap.get('scaleList'));
                this.fillSelect('stageComboBox', groupMap.get('stageList'));
                this.fillSelect('jobTypeComboBox', groupMap.get('jobTypeList'));
                
                console.log('BossConfigForm: 字典数据加载完成');
                
                // 标记字典数据已加载完成
                this.dictDataLoaded = true;
                
                // 触发自定义事件，通知其他组件字典数据已就绪
                window.dispatchEvent(new CustomEvent('bossDictDataLoaded', {
                    detail: { groupMap: groupMap }
                }));
                
            } catch (e) {
                console.warn('BossConfigForm: 加载Boss字典失败：', e?.message || e);
                // 如果失败，延迟重试
                setTimeout(() => this.loadBossDicts(), 2000);
            }
        }

        // 等待DOM元素准备就绪
        async waitForDOMElements() {
            const requiredElements = [
                'cityCodeField',
                'experienceComboBox',
                'salaryComboBox',
                'degreeComboBox',
                'scaleComboBox',
                'stageComboBox',
                'jobTypeComboBox'
            ];

            return new Promise((resolve) => {
                let attempts = 0;
                const maxAttempts = 50; // 最多等待5秒

                const checkElements = () => {
                    attempts++;
                    const missingElements = requiredElements.filter(id => !document.getElementById(id));
                    
                    if (missingElements.length === 0) {
                        console.log('BossConfigForm: 所有DOM元素已准备就绪');
                        resolve();
                    } else if (attempts >= maxAttempts) {
                        console.warn('BossConfigForm: 等待DOM元素超时，缺失元素:', missingElements);
                        resolve(); // 即使超时也继续执行
                    } else {
                        console.log(`BossConfigForm: 等待DOM元素准备就绪，缺失: ${missingElements.join(', ')} (${attempts}/${maxAttempts})`);
                        setTimeout(checkElements, 100);
                    }
                };

                checkElements();
            });
        }

        // 渲染城市选择器
        renderCitySelector(cityItems) {
            console.log('BossConfigForm: 渲染城市选择器，城市数量:', cityItems.length);
            
            const citySelect = document.getElementById('cityCodeField');
            const citySearch = document.getElementById('citySearchField');
            const cityListContainer = document.getElementById('cityDropdownList');
            const cityDropdownBtn = document.getElementById('cityDropdownBtn');
            const citySummary = document.getElementById('citySelectionSummary');
            
            if (!citySelect) {
                console.warn('BossConfigForm: 未找到城市选择器元素');
                return;
            }

            // 更新城市摘要显示
            const updateCitySummary = () => {
                if (!cityDropdownBtn || !citySummary) return;
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
            };

            // 将updateCitySummary方法绑定到实例，供其他方法调用
            this.updateCitySummary = updateCitySummary;

            // 渲染城市选项
            const renderCityOptions = (list) => {
                // 保留当前已选
                const selected = new Set(Array.from(citySelect.selectedOptions).map(o => o.value));

                // 重建隐藏select
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

                // 重建dropdown列表
                if (cityListContainer) {
                    cityListContainer.innerHTML = '';
                    list.forEach(it => {
                        const value = it.code ?? '';
                        const label = `${it.name ?? ''}${it.code ? ' (' + it.code + ')' : ''}`;

                        const item = document.createElement('div');
                        item.className = 'form-check mb-1';
                        const id = `city_chk_${value}`.replace(/[^a-zA-Z0-9_\-]/g, '_');
                        item.innerHTML = `
                            <input class="form-check-input" type="checkbox" value="${value}" id="${id}" ${selected.has(value) ? 'checked' : ''}>
                            <label class="form-check-label small" for="${id}">${label}</label>
                        `;
                        const checkbox = item.querySelector('input[type="checkbox"]');
                        checkbox.addEventListener('change', () => {
                            // 同步到隐藏select
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
            
            // 绑定搜索功能
            if (citySearch) {
                citySearch.addEventListener('input', () => {
                    const kw = citySearch.value.trim().toLowerCase();
                    if (!kw) {
                        renderCityOptions(cityItems);
                        return;
                    }
                    const filtered = cityItems.filter(it =>
                        String(it.name || '').toLowerCase().includes(kw) ||
                        String(it.code || '').toLowerCase().includes(kw)
                    );
                    renderCityOptions(filtered);
                });
            }
        }

        // 填充下拉框
        fillSelect(selectId, items) {
            console.log(`BossConfigForm: 填充下拉框 ${selectId}，数据项数量:`, Array.isArray(items) ? items.length : 0);
            
            if (!Array.isArray(items) || items.length === 0) {
                console.warn(`BossConfigForm: 下拉框 ${selectId} 的数据无效:`, items);
                return;
            }

            const sel = document.getElementById(selectId);
            if (!sel) {
                console.warn(`BossConfigForm: 未找到下拉框元素: ${selectId}`);
                // 延迟重试
                setTimeout(() => {
                    const retrySel = document.getElementById(selectId);
                    if (retrySel) {
                        console.log(`BossConfigForm: 重试填充下拉框 ${selectId}`);
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
                
                console.log(`BossConfigForm: 下拉框 ${selectId} 填充完成，共 ${items.length} 项`);
                
                // 触发change事件，通知其他组件
                sel.dispatchEvent(new Event('change', { bubbles: true }));
                
            } catch (error) {
                console.error(`BossConfigForm: 填充下拉框 ${selectId} 时出错:`, error);
            }
        }
    }

    // 导出为全局可用类，由 app.js 统一初始化
    window.Views.BossConfigForm = BossConfigApp;
})();


