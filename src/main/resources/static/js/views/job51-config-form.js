// 51job配置表单管理类
class Job51ConfigForm {
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
        // 先加载字典数据，再加载配置数据，确保下拉框已准备好
        this.loadDataSequentially();
    }

    // 初始化工具提示
    initializeTooltips() {
        const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
        tooltipTriggerList.map(function (tooltipTriggerEl) {
            return new bootstrap.Tooltip(tooltipTriggerEl);
        });
    }

    // 绑定事件
    bindEvents() {
        // 保存配置按钮
        const saveConfigBtn = document.getElementById('job51SaveConfigBtn');
        if (saveConfigBtn) {
            saveConfigBtn.addEventListener('click', () => {
                this.handleSaveConfig();
            });
        }

        // 数据库备份按钮
        const backupDataBtn = document.getElementById('job51BackupDataBtn');
        if (backupDataBtn) {
            backupDataBtn.addEventListener('click', () => {
                this.handleBackupData();
            });
        }

        // 任务执行按钮
        document.getElementById('job51LoginBtn')?.addEventListener('click', () => {
            this.handleLogin();
        });

        // 手动确认登录按钮
        const job51LoginManualBtn = document.getElementById('job51LoginManualBtn');
        if (job51LoginManualBtn) {
            job51LoginManualBtn.addEventListener('click', () => {
                console.log('51job手动登录按钮被点击');
                this.handleManualLogin();
            });
            console.log('已绑定51job手动登录按钮事件');
        } else {
            console.warn('未找到51job手动登录按钮元素');
        }

        document.getElementById('job51CollectBtn')?.addEventListener('click', () => {
            this.handleCollect();
        });

        document.getElementById('job51FilterBtn')?.addEventListener('click', () => {
            this.handleFilter();
        });

        document.getElementById('job51ApplyBtn')?.addEventListener('click', () => {
            this.handleApply();
        });

        document.getElementById('job51ResetTasksBtn')?.addEventListener('click', () => {
            this.resetTaskFlow();
        });

        // 表单验证
        this.bindFormValidation();

        // 自动保存
        this.bindAutoSave();
    }

    // 表单验证
    bindFormValidation() {
        const requiredFields = [
            'job51KeywordsField',
            'job51CityCodeField',
            'job51ExperienceComboBox',
            'job51JobTypeComboBox',
            'job51SalaryComboBox',
            'job51DegreeComboBox'
        ];

        requiredFields.forEach(fieldId => {
            const field = document.getElementById(fieldId);
            if (field) {
                field.addEventListener('blur', () => {
                    if (field.tagName === 'SELECT') {
                        this.validateSelectField(field);
                    } else {
                        this.validateField(field);
                    }
                });
            }
        });
    }

    // 验证单个字段
    validateField(field) {
        const value = field.value.trim();
        const isValid = value.length > 0;
        this.updateFieldValidation(field, isValid);
        return isValid;
    }

    // 验证选择框字段
    validateSelectField(field) {
        const value = field.value;
        const isValid = value && value !== '';
        this.updateFieldValidation(field, isValid);
        return isValid;
    }

    // 更新字段验证状态
    updateFieldValidation(field, isValid) {
        if (isValid) {
            field.classList.remove('is-invalid');
            field.classList.add('is-valid');
        } else {
            field.classList.remove('is-valid');
            field.classList.add('is-invalid');
        }
    }

    // 自动保存配置
    bindAutoSave() {
        const formElements = document.querySelectorAll('#job51-config-pane input, #job51-config-pane select, #job51-config-pane textarea');
        formElements.forEach(element => {
            element.addEventListener('change', () => {
                this.saveConfig();
            });
        });
    }

    // 保存配置
    saveConfig() {
        const getMultiSelectValues = (selectId) => {
            const el = document.getElementById(selectId);
            if (!el) return '';
            return Array.from(el.selectedOptions).map(o => o.value).filter(Boolean).join(',');
        };

        this.config = {
            // 搜索条件
            keywords: document.getElementById('job51KeywordsField')?.value || '',
            industry: getMultiSelectValues('job51IndustryField'),
            cityCode: getMultiSelectValues('job51CityCodeField'),
            
            // 职位要求
            experience: document.getElementById('job51ExperienceComboBox')?.value || '',
            jobType: document.getElementById('job51JobTypeComboBox')?.value || '',
            salary: document.getElementById('job51SalaryComboBox')?.value || '',
            degree: document.getElementById('job51DegreeComboBox')?.value || '',
            scale: document.getElementById('job51ScaleComboBox')?.value || '',
            companyNature: document.getElementById('job51CompanyNatureComboBox')?.value || '',
            
            
            // 功能开关
            autoApply: document.getElementById('job51AutoApplyCheckBox')?.checked || false,
            blacklistFilter: document.getElementById('job51BlacklistFilterCheckBox')?.checked || false,
            blacklistKeywords: document.getElementById('job51BlacklistKeywordsTextArea')?.value || '',
            
            // AI配置
            enableAIJobMatch: document.getElementById('job51EnableAIJobMatchCheckBox')?.checked || false
        };

        localStorage.setItem('job51Config', JSON.stringify(this.config));

        // 同步保存到后端
        try {
            fetch('/api/config/job51', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(this.config)
            }).then(() => {}).catch(() => {});
        } catch (e) {}
    }

    // 加载保存的配置（优先后端，其次本地缓存）
    async loadSavedConfig() {
        try {
            console.log('开始加载51job保存的配置...');
            const res = await fetch('/api/config/job51');
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
            
            console.log('51job后端配置数据:', data);
            if (data && typeof data === 'object' && Object.keys(data).length) {
                this.config = data;
                this.populateForm();
                localStorage.setItem('job51Config', JSON.stringify(this.config));
                console.log('51job配置已从后端加载并回填表单');
                return;
            }
            
            // 如果后端没有数据，尝试本地缓存
            const savedConfig = localStorage.getItem('job51Config');
            if (savedConfig) {
                try {
                    this.config = JSON.parse(savedConfig);
                    this.populateForm();
                    console.log('51job配置已从本地缓存加载并回填表单');
                } catch (error) {
                    console.warn('51job本地配置损坏，已清理：' + error.message);
                    localStorage.removeItem('job51Config');
                }
            }
        } catch (err) {
            console.warn('51job后端配置读取失败：' + (err?.message || '未知错误'));
            // 尝试本地缓存
            const savedConfig = localStorage.getItem('job51Config');
            if (savedConfig) {
                try {
                    this.config = JSON.parse(savedConfig);
                    this.populateForm();
                    console.log('51job配置已从本地缓存加载并回填表单');
                } catch (error) {
                    console.warn('51job本地配置损坏，已清理：' + error.message);
                    localStorage.removeItem('job51Config');
                }
            }
        }
    }

    // 填充表单
    populateForm() {
        Object.keys(this.config).forEach(key => {
            const element = document.getElementById(this.getFieldId(key));
            if (element) {
                if (element.type === 'checkbox') {
                    element.checked = this.config[key];
                } else {
                    // 处理可能的数组字段转换为逗号分隔字符串
                    let value = this.config[key];
                    if (Array.isArray(value)) {
                        value = value.join(',');
                        console.log(`51job: 数组字段 ${key} 转换为字符串:`, this.config[key], '->', value);
                    }
                    element.value = value || '';
                }
            }
        });

        // 回填城市多选
        try {
            // 处理数组格式（从后端返回）或字符串格式（从本地缓存）
            let cityCodeStr = '';
            if (Array.isArray(this.config.cityCode)) {
                cityCodeStr = this.config.cityCode.join(',');
            } else {
                cityCodeStr = this.config.cityCode || '';
            }
            
            const codes = cityCodeStr.split(',').map(s => s.trim()).filter(Boolean);
            const citySelect = document.getElementById('job51CityCodeField');
            if (citySelect && codes.length) {
                console.log('51job回填城市选择，原始数据:', this.config.cityCode, '处理后:', codes);
                Array.from(citySelect.options).forEach(opt => {
                    opt.selected = codes.includes(opt.value);
                });
                
                // 同步下拉复选框
                const cityListContainer = document.getElementById('job51CityDropdownList');
                const cityDropdownBtn = document.getElementById('job51CityDropdownBtn');
                const citySummary = document.getElementById('job51CitySelectionSummary');
                const selectedSet = new Set(codes);
                if (cityListContainer) {
                    cityListContainer.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
                        checkbox.checked = selectedSet.has(checkbox.value);
                    });
                }
                
                // 更新按钮文案和摘要
                if (cityDropdownBtn && citySummary) {
                    const values = Array.from(citySelect.selectedOptions).map(o => o.textContent || '').filter(Boolean);
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
            }
        } catch (_) {}

        // 回填行业多选
        try {
            // 处理数组格式（从后端返回）或字符串格式（从本地缓存）
            let industryStr = '';
            if (Array.isArray(this.config.industry)) {
                industryStr = this.config.industry.join(',');
            } else {
                industryStr = this.config.industry || '';
            }
            
            const codes = industryStr.split(',').map(s => s.trim()).filter(Boolean);
            const industrySelect = document.getElementById('job51IndustryField');
            if (industrySelect && codes.length) {
                console.log('51job回填行业选择，原始数据:', this.config.industry, '处理后:', codes);
                Array.from(industrySelect.options).forEach(opt => {
                    opt.selected = codes.includes(opt.value);
                });
            }
        } catch (_) {}
    }

    // 获取字段ID
    getFieldId(key) {
        const fieldMap = {
            keywords: 'job51KeywordsField',
            industry: 'job51IndustryField',
            cityCode: 'job51CityCodeField',
            experience: 'job51ExperienceComboBox',
            jobType: 'job51JobTypeComboBox',
            salary: 'job51SalaryComboBox',
            degree: 'job51DegreeComboBox',
            scale: 'job51ScaleComboBox',
            companyNature: 'job51CompanyNatureComboBox',
            autoApply: 'job51AutoApplyCheckBox',
            blacklistFilter: 'job51BlacklistFilterCheckBox',
            blacklistKeywords: 'job51BlacklistKeywordsTextArea',
            enableAIJobMatch: 'job51EnableAIJobMatchCheckBox'
        };
        return fieldMap[key] || key;
    }

    // 按顺序加载数据：先字典，后配置
    async loadDataSequentially() {
        try {
            console.log('开始按顺序加载51job数据：字典 -> 配置');
            // 先加载字典数据
            await this.loadJob51Dicts();
            console.log('51job字典数据加载完成，开始加载配置数据');
            // 再加载配置数据
            await this.loadSavedConfig();
            console.log('51job配置数据加载完成');
        } catch (error) {
            console.error('51job数据加载失败:', error);
        }
    }

    // 加载51job字典数据
    async loadJob51Dicts() {
        try {
            console.log('开始加载51job字典数据...');
            const res = await fetch('/dicts/JOB_51');
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const data = await res.json();
            console.log('接收到51job字典数据:', data);
            
            if (!data || !Array.isArray(data.groups)) {
                console.warn('51job字典数据结构不正确:', data);
                return;
            }

            const groupMap = new Map();
            data.groups.forEach(g => {
                console.log(`处理51job字典组: ${g.key}, 项目数量: ${Array.isArray(g.items) ? g.items.length : 0}`);
                groupMap.set(g.key, Array.isArray(g.items) ? g.items : []);
            });

            // 渲染城市选择
            const cityItems = groupMap.get('cityList') || [];
            console.log('51job城市数据:', cityItems);
            this.renderCitySelection(cityItems);

            // 渲染其他选择框
            console.log('开始渲染51job其他下拉框...');
            this.fillSelect('job51IndustryField', groupMap.get('industryList'));
            this.fillSelect('job51ExperienceComboBox', groupMap.get('experienceList'));
            this.fillSelect('job51JobTypeComboBox', groupMap.get('jobTypeList'));
            this.fillSelect('job51SalaryComboBox', groupMap.get('salaryList'));
            this.fillSelect('job51DegreeComboBox', groupMap.get('degreeList'));
            this.fillSelect('job51ScaleComboBox', groupMap.get('scaleList'));
            this.fillSelect('job51CompanyNatureComboBox', groupMap.get('companyNatureList'));
            console.log('51job所有下拉框渲染完成');

        } catch (e) {
            console.warn('加载51job字典失败：', e?.message || e);
            throw e; // 重新抛出错误，让调用者知道字典加载失败
        }
    }

    // 渲染城市选择
    renderCitySelection(cityItems) {
        const citySelect = document.getElementById('job51CityCodeField');
        const citySearch = document.getElementById('job51CitySearchField');
        const cityListContainer = document.getElementById('job51CityDropdownList');
        const cityDropdownBtn = document.getElementById('job51CityDropdownBtn');
        const citySummary = document.getElementById('job51CitySelectionSummary');

        const updateCitySummary = () => {
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
        };

        const renderCityOptions = (list) => {
            if (!citySelect) return;
            
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
                    const id = `job51_city_chk_${value}`.replace(/[^a-zA-Z0-9_\-]/g, '_');
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

    // 通用方法：将字典渲染到 select
    fillSelect(selectId, items) {
        const sel = document.getElementById(selectId);
        if (!sel || !Array.isArray(items)) return;
        
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
    }

    // 更新城市选择摘要
    updateCitySummary() {
        const citySelect = document.getElementById('job51CityCodeField');
        const cityDropdownBtn = document.getElementById('job51CityDropdownBtn');
        const citySummary = document.getElementById('job51CitySelectionSummary');
        
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

    // 处理保存配置
    handleSaveConfig() {
        this.saveConfig();
        this.showToast('51job配置已保存');
    }

    // 处理数据库备份
    handleBackupData() {
        const backupBtn = document.getElementById('job51BackupDataBtn');
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
                this.showToast('数据库备份失败: ' + data.message, 'danger');
            }
        })
        .catch(error => {
            console.error('备份请求失败:', error);
            this.showToast('数据库备份失败: ' + error.message, 'danger');
        })
        .finally(() => {
            if (backupBtn) {
                backupBtn.disabled = false;
                backupBtn.innerHTML = '<i class="bi bi-database me-2"></i>数据库备份';
            }
        });
    }

    // 处理登录
    async handleLogin() {
        if (!this.validateRequiredFields()) {
            this.showAlertModal('验证失败', '请先完善必填项');
            return;
        }

        this.updateButtonState('job51LoginBtn', 'job51LoginStatus', '执行中...', true);
        
        try {
            const config = this.getCurrentConfig();
            const response = await fetch('/api/job51/task/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });

            const result = await response.json();
            
            if (result.success) {
                this.taskStates.loginTaskId = result.taskId;
                this.updateButtonState('job51LoginBtn', 'job51LoginStatus', '登录成功', false);
                this.enableNextStep('job51CollectBtn', 'job51CollectStatus', '可开始采集');
                this.enableNextStep('job51FilterBtn', 'job51FilterStatus', '可开始过滤');
                this.enableNextStep('job51ApplyBtn', 'job51ApplyStatus', '可开始投递');
                this.showToast('51job登录成功！');
            } else {
                this.updateButtonState('job51LoginBtn', 'job51LoginStatus', '登录失败', false);
                this.showToast(result.message || '登录失败', 'danger');
            }
        } catch (error) {
            this.updateButtonState('job51LoginBtn', 'job51LoginStatus', '登录失败', false);
            this.showToast('登录接口调用失败: ' + error.message, 'danger');
        }
    }

    // 手动确认登录
    handleManualLogin() {
        console.log('51job手动登录方法被调用');
        
        // 模拟登录成功的状态
        this.taskStates.loginTaskId = 'manual_login_' + Date.now();
        console.log('设置taskId:', this.taskStates.loginTaskId);
        
        this.updateButtonState('job51LoginBtn', 'job51LoginStatus', '登录成功', false);
        console.log('更新51job登录按钮状态为登录成功');
        
        // 启用后续步骤按钮
        this.enableNextStep('job51CollectBtn', 'job51CollectStatus', '可开始采集');
        this.enableNextStep('job51FilterBtn', 'job51FilterStatus', '可开始过滤');  
        this.enableNextStep('job51ApplyBtn', 'job51ApplyStatus', '可开始投递');
        console.log('启用51job后续步骤按钮');
        
        this.showToast('已手动标记为登录状态', 'success');
        console.log('51job手动登录处理完成');
    }

    // 处理采集
    async handleCollect() {
        if (!this.taskStates.loginTaskId) {
            this.showAlertModal('操作提示', '请先完成登录步骤');
            return;
        }

        this.updateButtonState('job51CollectBtn', 'job51CollectStatus', '采集中...', true);
        
        try {
            const config = this.getCurrentConfig();
            const response = await fetch('/api/job51/task/collect', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });

            const result = await response.json();
            
            if (result.success) {
                this.taskStates.collectTaskId = result.taskId;
                this.updateButtonState('job51CollectBtn', 'job51CollectStatus', 
                    `采集完成(${result.jobCount}个职位)`, false);
                this.showToast(`采集完成，共找到 ${result.jobCount} 个职位！`);
            } else {
                this.updateButtonState('job51CollectBtn', 'job51CollectStatus', '采集失败', false);
                this.showToast(result.message || '采集失败', 'danger');
            }
        } catch (error) {
            this.updateButtonState('job51CollectBtn', 'job51CollectStatus', '采集失败', false);
            this.showToast('采集接口调用失败: ' + error.message, 'danger');
        }
    }

    // 处理过滤
    async handleFilter() {
        if (!this.taskStates.loginTaskId) {
            this.showAlertModal('操作提示', '请先完成登录步骤');
            return;
        }

        this.updateButtonState('job51FilterBtn', 'job51FilterStatus', '过滤中...', true);
        
        try {
            const config = this.getCurrentConfig();
            const request = {
                collectTaskId: this.taskStates.collectTaskId,
                config: config
            };

            const response = await fetch('/api/job51/task/filter', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            });

            const result = await response.json();
            
            if (result.success) {
                this.taskStates.filterTaskId = result.taskId;
                this.updateButtonState('job51FilterBtn', 'job51FilterStatus', 
                    `过滤完成(${result.originalCount}→${result.filteredCount})`, false);
                this.showToast(`过滤完成，从 ${result.originalCount} 个职位中筛选出 ${result.filteredCount} 个！`);
            } else {
                this.updateButtonState('job51FilterBtn', 'job51FilterStatus', '过滤失败', false);
                this.showToast(result.message || '过滤失败', 'danger');
            }
        } catch (error) {
            this.updateButtonState('job51FilterBtn', 'job51FilterStatus', '过滤失败', false);
            this.showToast('过滤接口调用失败: ' + error.message, 'danger');
        }
    }

    // 处理投递
    async handleApply() {
        if (!this.taskStates.loginTaskId) {
            this.showAlertModal('操作提示', '请先完成登录步骤');
            return;
        }

        this.showConfirmModal(
            '投递确认',
            '是否执行实际投递？\n点击"确定"将真实投递简历\n点击"取消"将仅模拟投递',
            () => this.executeApply(true),
            () => this.executeApply(false)
        );
    }

    // 执行投递
    async executeApply(enableActualDelivery) {
        this.updateButtonState('job51ApplyBtn', 'job51ApplyStatus', '投递中...', true);
        
        try {
            const config = this.getCurrentConfig();
            const request = {
                filterTaskId: this.taskStates.filterTaskId,
                config: config,
                enableActualDelivery: enableActualDelivery
            };

            const response = await fetch('/api/job51/task/deliver', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            });

            const result = await response.json();
            
            if (result.success) {
                this.taskStates.applyTaskId = result.taskId;
                const deliveryType = result.actualDelivery ? '实际投递' : '模拟投递';
                this.updateButtonState('job51ApplyBtn', 'job51ApplyStatus', 
                    `${deliveryType}完成(${result.appliedCount}/${result.totalCount})`, false);
                this.showToast(`${deliveryType}完成！处理了 ${result.appliedCount} 个职位`);
            } else {
                this.updateButtonState('job51ApplyBtn', 'job51ApplyStatus', '投递失败', false);
                this.showToast(result.message || '投递失败', 'danger');
            }
        } catch (error) {
            this.updateButtonState('job51ApplyBtn', 'job51ApplyStatus', '投递失败', false);
            this.showToast('投递接口调用失败: ' + error.message, 'danger');
        }
    }

    // 获取当前配置
    getCurrentConfig() {
        const getMultiSelectValues = (selectId) => {
            const el = document.getElementById(selectId);
            if (!el) return '';
            return Array.from(el.selectedOptions).map(o => o.value).filter(Boolean).join(',');
        };
        
        return {
            keywords: document.getElementById('job51KeywordsField')?.value || '',
            industry: getMultiSelectValues('job51IndustryField'),
            cityCode: getMultiSelectValues('job51CityCodeField'),
            experience: document.getElementById('job51ExperienceComboBox')?.value || '',
            jobType: document.getElementById('job51JobTypeComboBox')?.value || '',
            salary: document.getElementById('job51SalaryComboBox')?.value || '',
            degree: document.getElementById('job51DegreeComboBox')?.value || '',
            scale: document.getElementById('job51ScaleComboBox')?.value || '',
            companyNature: document.getElementById('job51CompanyNatureComboBox')?.value || '',
            autoApply: document.getElementById('job51AutoApplyCheckBox')?.checked || false,
            blacklistFilter: document.getElementById('job51BlacklistFilterCheckBox')?.checked || false,
            blacklistKeywords: document.getElementById('job51BlacklistKeywordsTextArea')?.value || '',
            enableAIJobMatch: document.getElementById('job51EnableAIJobMatchCheckBox')?.checked || false
        };
    }

    // 验证必填字段
    validateRequiredFields() {
        const requiredFields = [
            'job51KeywordsField',
            'job51CityCodeField',
            'job51ExperienceComboBox',
            'job51JobTypeComboBox',
            'job51SalaryComboBox',
            'job51DegreeComboBox'
        ];

        let isValid = true;
        
        requiredFields.forEach(fieldId => {
            const field = document.getElementById(fieldId);
            if (field) {
                if (field.tagName === 'SELECT') {
                    if (!this.validateSelectField(field)) {
                        isValid = false;
                    }
                } else {
                    if (!this.validateField(field)) {
                        isValid = false;
                    }
                }
            }
        });

        return isValid;
    }

    // 更新按钮状态
    updateButtonState(buttonId, statusId, statusText, isLoading) {
        const button = document.getElementById(buttonId);
        const status = document.getElementById(statusId);
        
        if (button) {
            button.disabled = isLoading;
        }
        
        if (status) {
            status.textContent = statusText;
            status.className = isLoading ? 'badge bg-warning text-dark ms-2' : 'badge bg-success text-white ms-2';
        }
    }

    // 启用下一步按钮
    enableNextStep(buttonId, statusId, statusText) {
        const button = document.getElementById(buttonId);
        const status = document.getElementById(statusId);
        
        if (button) {
            button.disabled = false;
        }
        
        if (status) {
            status.textContent = statusText;
            status.className = 'badge bg-info text-white ms-2';
        }
    }

    // 重置任务流程
    resetTaskFlow() {
        this.showConfirmModal(
            '重置确认',
            '确定要重置任务流程吗？这将清除所有任务状态。',
            () => {
                this.taskStates = {
                    loginTaskId: null,
                    collectTaskId: null,
                    filterTaskId: null,
                    applyTaskId: null
                };

                this.updateButtonState('job51LoginBtn', 'job51LoginStatus', '待执行', false);
                this.updateButtonState('job51CollectBtn', 'job51CollectStatus', '等待登录', true);
                this.updateButtonState('job51FilterBtn', 'job51FilterStatus', '等待登录', true);
                this.updateButtonState('job51ApplyBtn', 'job51ApplyStatus', '等待登录', true);

                document.getElementById('job51CollectBtn').disabled = true;
                document.getElementById('job51FilterBtn').disabled = true;
                document.getElementById('job51ApplyBtn').disabled = true;

                this.showToast('任务流程已重置', 'info');
            }
        );
    }

    // 显示全局Toast
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

    // 显示确认对话框
    showConfirmModal(title, message, onConfirm, onCancel) {
        const modal = new bootstrap.Modal(document.getElementById('confirmModal'));
        document.getElementById('confirmModalLabel').textContent = title;
        document.getElementById('confirmModalBody').textContent = message;
        
        const okBtn = document.getElementById('confirmModalOk');
        const newOkBtn = okBtn.cloneNode(true);
        okBtn.parentNode.replaceChild(newOkBtn, okBtn);
        
        newOkBtn.addEventListener('click', () => {
            modal.hide();
            if (onConfirm) onConfirm();
        });
        
        modal._element.addEventListener('hidden.bs.modal', () => {
            if (onCancel) onCancel();
        }, { once: true });
        
        modal.show();
    }

    // 显示提示对话框
    showAlertModal(title, message) {
        const modal = new bootstrap.Modal(document.getElementById('alertModal'));
        document.getElementById('alertModalLabel').textContent = title;
        document.getElementById('alertModalBody').textContent = message;
        modal.show();
    }
}

// 导出到全局
window.Job51ConfigForm = Job51ConfigForm;
