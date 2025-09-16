// Boss直聘配置应用主脚本
class BossConfigApp {
    constructor() {
        this.config = {};
        this.isRunning = false;
        this.taskStates = {
            loginTaskId: null,
            collectTaskId: null,
            filterTaskId: null,
            deliverTaskId: null
        };
        this.init();
    }

    init() {
        this.initializeTooltips();
        this.bindEvents();
        this.loadSavedConfig();
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

        // 新的4个步骤按钮
        document.getElementById('loginBtn')?.addEventListener('click', () => {
            this.handleLogin();
        });

        document.getElementById('collectBtn')?.addEventListener('click', () => {
            this.handleCollect();
        });

        document.getElementById('filterBtn')?.addEventListener('click', () => {
            this.handleFilter();
        });

        document.getElementById('deliverBtn')?.addEventListener('click', () => {
            this.handleDeliver();
        });

        document.getElementById('resetTasksBtn')?.addEventListener('click', () => {
            this.resetTaskFlow();
        });

        // 表单验证
        this.bindFormValidation();

        // 高级配置动态添加
        this.bindAdvancedConfig();

        // 实时配置保存
        this.bindAutoSave();

        // 岗位明细：绑定搜索与标签切换加载
        this.bindJobDetailsEvents();

        // 求职配置视图：绑定标签切换加载与刷新
        this.bindBossConfigViewEvents();
    }

    // 表单验证
    bindFormValidation() {
        // 基础必填字段（始终必填）
        const alwaysRequiredFields = [
            'keywordsField',
            'cityCodeField',
            'experienceComboBox',
            'jobTypeComboBox',
            'salaryComboBox',
            'degreeComboBox',
            'sayHiTextArea'
        ];

        // 条件必填字段（根据发送图片简历勾选状态决定）
        const conditionalRequiredFields = [
            'resumeImagePathField'
        ];

        // 绑定基础必填字段验证
        alwaysRequiredFields.forEach(fieldId => {
            const field = document.getElementById(fieldId);
            if (field) {
                field.addEventListener('blur', () => {
                    // 选择框使用专门的验证方法
                    if (field.tagName === 'SELECT') {
                        this.validateSelectField(field);
                    } else {
                        this.validateField(field);
                    }
                });
            }
        });

        // 绑定条件必填字段验证
        conditionalRequiredFields.forEach(fieldId => {
            const field = document.getElementById(fieldId);
            if (field) {
                field.addEventListener('blur', () => {
                    this.validateConditionalField(field);
                });
            }
        });

        // 绑定发送图片简历复选框变化事件
        const sendImgResumeCheckBox = document.getElementById('sendImgResumeCheckBox');
        if (sendImgResumeCheckBox) {
            sendImgResumeCheckBox.addEventListener('change', () => {
                this.handleSendImgResumeChange();
            });
        }

        // 薪资范围验证
        const minSalary = document.getElementById('minSalaryField');
        const maxSalary = document.getElementById('maxSalaryField');
        
        if (minSalary && maxSalary) {
            [minSalary, maxSalary].forEach(field => {
                field.addEventListener('input', () => {
                    // 只允许正整数，范围 0-100
                    const raw = field.value.replace(/[^0-9]/g, '');
                    field.value = raw;
                    this.validateSalaryRange();
                });
                field.addEventListener('blur', () => {
                    // 失焦时归一化到 0-100 区间
                    let v = parseInt(field.value, 10);
                    if (isNaN(v)) v = 0;
                    if (v < 0) v = 0;
                    if (v > 100) v = 100;
                    field.value = String(v);
                    this.validateSalaryRange();
                });
                field.setAttribute('min', '0');
                field.setAttribute('max', '100');
                field.setAttribute('step', '1');
                field.setAttribute('inputmode', 'numeric');
            });
        }

        // 等待时间验证
        const waitTimeField = document.getElementById('waitTimeField');
        if (waitTimeField) {
            waitTimeField.addEventListener('input', () => {
                this.validateWaitTime();
            });
        }
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

    // =====================
    // 岗位明细（列表/搜索/分页）
    // =====================
    bindJobDetailsEvents() {
        const self = this;
        // 首次进入记录页时加载
        document.getElementById('boss-records-tab')?.addEventListener('shown.bs.tab', function () {
            self.loadJobDetails('Boss直聘', 'boss', 0);
        });
        document.getElementById('zhilian-records-tab')?.addEventListener('shown.bs.tab', function () {
            self.loadJobDetails('智联招聘', 'zhilian', 0);
        });
        document.getElementById('job51-records-tab')?.addEventListener('shown.bs.tab', function () {
            self.loadJobDetails('51job', 'job51', 0);
        });

        // 搜索按钮
        document.getElementById('bossRecordSearchBtn')?.addEventListener('click', () => {
            this.loadJobDetails('Boss直聘', 'boss', 0);
        });
        document.getElementById('zhilianRecordSearchBtn')?.addEventListener('click', () => {
            this.loadJobDetails('智联招聘', 'zhilian', 0);
        });
        document.getElementById('job51RecordSearchBtn')?.addEventListener('click', () => {
            this.loadJobDetails('51job', 'job51', 0);
        });
    }

    loadJobDetails(platformText, platformKey, pageIndex = 0) {
        const keywordInputIdMap = {
            boss: 'bossRecordKeyword',
            zhilian: 'zhilianRecordKeyword',
            job51: 'job51RecordKeyword'
        };
        const tbodyIdMap = {
            boss: 'bossRecordTbody',
            zhilian: 'zhilianRecordTbody',
            job51: 'job51RecordTbody'
        };
        const pagerIdMap = {
            boss: 'bossRecordPagination',
            zhilian: 'zhilianRecordPagination',
            job51: 'job51RecordPagination'
        };

        const keyword = document.getElementById(keywordInputIdMap[platformKey])?.value?.trim() || '';
        const params = new URLSearchParams();
        if (platformText) params.set('platform', platformText);
        if (keyword) params.set('keyword', keyword);
        params.set('page', String(pageIndex));
        params.set('size', '10');

        // 占位提示
        const tbody = document.getElementById(tbodyIdMap[platformKey]);
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">加载中...</td></tr>';
        }

        fetch(`/api/jobs?${params.toString()}`)
            .then(res => res.json())
            .then(page => {
                this.renderJobDetailsTable(platformKey, page);
                this.renderPagination(platformKey, page);
            })
            .catch(() => {
                if (tbody) {
                    tbody.innerHTML = '<tr><td colspan="7" class="text-center text-danger">加载失败</td></tr>';
                }
            });
    }

    renderJobDetailsTable(platformKey, page) {
        const tbodyIdMap = {
            boss: 'bossRecordTbody',
            zhilian: 'zhilianRecordTbody',
            job51: 'job51RecordTbody'
        };
        const tbody = document.getElementById(tbodyIdMap[platformKey]);
        if (!tbody) return;

        const content = Array.isArray(page?.content) ? page.content : [];
        if (content.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">暂无数据</td></tr>';
            return;
        }

        const statusBadge = (status) => {
            const map = {
                0: { text: '待处理', cls: 'secondary' },
                1: { text: '已处理', cls: 'success' },
                2: { text: '已忽略', cls: 'warning' }
            };
            const it = map[status] || { text: '未知', cls: 'dark' };
            return `<span class="badge bg-${it.cls}">${it.text}</span>`;
        };

        const rows = content.map(j => {
            const jobTitle = this.escapeHtml(j.jobTitle || '-');
            const companyName = this.escapeHtml(j.companyName || '-');
            const city = this.escapeHtml(j.workLocation || '-');
            const salary = this.escapeHtml(j.salaryRange || '-');
            const deliverTime = this.formatDateTime(j.createdAt || j.updatedAt); // BaseEntity 可能包含
            const state = statusBadge(j.status);
            const hrActive = this.escapeHtml(j.hrActiveTime || '-');
            return `
                <tr>
                    <td><a href="${j.jobUrl || '#'}" target="_blank">${jobTitle}</a></td>
                    <td>${companyName}</td>
                    <td>${city}</td>
                    <td>${salary}</td>
                    <td>${deliverTime || '-'}</td>
                    <td>${state}</td>
                    <td>${hrActive}</td>
                </tr>
            `;
        }).join('');

        tbody.innerHTML = rows;
    }

    renderPagination(platformKey, page) {
        const pagerIdMap = {
            boss: 'bossRecordPagination',
            zhilian: 'zhilianRecordPagination',
            job51: 'job51RecordPagination'
        };
        const pager = document.getElementById(pagerIdMap[platformKey]);
        if (!pager) return;

        const totalPages = page?.totalPages ?? 0;
        const number = page?.number ?? 0;
        const disabledPrev = number <= 0 ? ' disabled' : '';
        const disabledNext = number >= totalPages - 1 ? ' disabled' : '';

        const pageItems = [];
        for (let i = 0; i < totalPages; i++) {
            const active = i === number ? ' active' : '';
            pageItems.push(`<li class="page-item${active}"><a class="page-link" href="#" data-page="${i}">${i + 1}</a></li>`);
        }

        pager.innerHTML = `
            <li class="page-item${disabledPrev}"><a class="page-link" href="#" data-page="${number - 1}">上一页</a></li>
            ${pageItems.join('')}
            <li class="page-item${disabledNext}"><a class="page-link" href="#" data-page="${number + 1}">下一页</a></li>
        `;

        // 绑定点击
        pager.querySelectorAll('a.page-link').forEach(a => {
            a.addEventListener('click', (e) => {
                e.preventDefault();
                const pageTo = parseInt(a.getAttribute('data-page'));
                if (!isNaN(pageTo) && pageTo >= 0 && pageTo < totalPages) {
                    const platformText = platformKey === 'boss' ? 'Boss直聘' : platformKey === 'zhilian' ? '智联招聘' : '51job';
                    this.loadJobDetails(platformText, platformKey, pageTo);
                }
            });
        });
    }

    // 工具：简易转义
    escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // 工具：时间格式化（ISO 或时间戳）
    formatDateTime(input) {
        try {
            if (!input) return '';
            const d = new Date(input);
            if (isNaN(d.getTime())) return '';
            const pad = (n) => (n < 10 ? '0' + n : '' + n);
            return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
        } catch (_) { return ''; }
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

    // 验证条件必填字段
    validateConditionalField(field) {
        const sendImgResumeCheckBox = document.getElementById('sendImgResumeCheckBox');
        const isSendImgResumeChecked = sendImgResumeCheckBox ? sendImgResumeCheckBox.checked : false;
        
        // 如果发送图片简历未勾选，则简历图片路径不是必填
        if (!isSendImgResumeChecked) {
            this.updateFieldValidation(field, true); // 始终有效
            return true;
        }
        
        // 如果发送图片简历已勾选，则简历图片路径是必填
        const value = field.value.trim();
        const isValid = value.length > 0;
        
        this.updateFieldValidation(field, isValid);
        return isValid;
    }

    // 处理发送图片简历复选框变化
    handleSendImgResumeChange() {
        const sendImgResumeCheckBox = document.getElementById('sendImgResumeCheckBox');
        const resumeImagePathField = document.getElementById('resumeImagePathField');
        
        if (!sendImgResumeCheckBox || !resumeImagePathField) return;
        
        const isChecked = sendImgResumeCheckBox.checked;
        
        if (isChecked) {
            // 勾选时，验证简历图片路径是否已填写
            this.validateConditionalField(resumeImagePathField);
        } else {
            // 未勾选时，清除验证状态
            resumeImagePathField.classList.remove('is-valid', 'is-invalid');
        }
    }

    // 验证薪资范围
    validateSalaryRange() {
        const minSalary = document.getElementById('minSalaryField');
        const maxSalary = document.getElementById('maxSalaryField');
        
        const minValue = Math.min(100, Math.max(0, parseInt(minSalary.value, 10) || 0));
        const maxValue = Math.min(100, Math.max(0, parseInt(maxSalary.value, 10) || 0));
        
        const isValid = Number.isInteger(minValue) && Number.isInteger(maxValue) && minValue >= 0 && maxValue >= 0 && minValue <= maxValue;
        
        this.updateFieldValidation(minSalary, isValid);
        this.updateFieldValidation(maxSalary, isValid);
        
        return isValid;
    }

    // 验证等待时间
    validateWaitTime() {
        const waitTimeField = document.getElementById('waitTimeField');
        const value = parseInt(waitTimeField.value) || 0;
        
        const isValid = value >= 1 && value <= 300;
        this.updateFieldValidation(waitTimeField, isValid);
        
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

    // 高级配置绑定
    bindAdvancedConfig() {
        // 城市代码配置
        this.bindCityCodeConfig();
        
        // HR状态配置
        this.bindHRStatusConfig();
    }

    // 城市代码配置
    bindCityCodeConfig() {
        const container = document.getElementById('customCityCodeContainer');
        if (!container) return;

        // 添加示例城市代码
        const sampleCityCodes = [
            { city: '北京', code: '101010100' },
            { city: '上海', code: '101020100' },
            { city: '深圳', code: '101280600' },
            { city: '广州', code: '101280100' }
        ];

        sampleCityCodes.forEach(item => {
            this.addCityCodeItem(container, item.city, item.code);
        });

        // 添加按钮
        const addBtn = document.createElement('button');
        addBtn.className = 'btn btn-sm btn-outline-primary mt-2';
        addBtn.innerHTML = '<i class="bi bi-plus-circle me-1"></i>添加城市代码';
        addBtn.onclick = () => this.showAddCityCodeModal();
        container.appendChild(addBtn);
    }

    // 添加城市代码项
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

    // 显示添加城市代码模态框
    showAddCityCodeModal() {
        this.showInputModal(
            '添加城市代码',
            '城市名称',
            '请输入城市名称',
            '城市代码',
            '请输入城市代码',
            (city, code) => {
                if (city && code) {
                    const container = document.getElementById('customCityCodeContainer');
                    this.addCityCodeItem(container, city, code);
                }
            }
        );
    }

    // HR状态配置
    bindHRStatusConfig() {
        const container = document.getElementById('deadStatusContainer');
        if (!container) return;

        // 添加示例HR状态
        const sampleStatuses = [
            '长期未活跃',
            '已离职',
            '账号异常',
            '不回复消息'
        ];

        sampleStatuses.forEach(status => {
            this.addHRStatusItem(container, status);
        });

        // 添加按钮
        const addBtn = document.createElement('button');
        addBtn.className = 'btn btn-sm btn-outline-warning mt-2';
        addBtn.innerHTML = '<i class="bi bi-plus-circle me-1"></i>添加HR状态';
        addBtn.onclick = () => this.showAddHRStatusModal();
        container.appendChild(addBtn);
    }

    // 添加HR状态项
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

    // 显示添加HR状态模态框
    showAddHRStatusModal() {
        this.showInputModal(
            '添加HR状态',
            'HR状态描述',
            '请输入HR状态描述',
            null,
            null,
            (status) => {
                if (status) {
                    const container = document.getElementById('deadStatusContainer');
                    this.addHRStatusItem(container, status);
                }
            }
        );
    }

    // 自动保存配置
    bindAutoSave() {
        const formElements = document.querySelectorAll('input, select, textarea');
        formElements.forEach(element => {
            element.addEventListener('change', () => {
                this.saveConfig();
            });
        });
    }

    // 保存配置
    saveConfig() {
        this.config = {
            // 搜索条件
            keywords: document.getElementById('keywordsField').value,
            industry: document.getElementById('industryField').value,
            cityCode: document.getElementById('cityCodeField').value,
            
            // 职位要求
            experience: document.getElementById('experienceComboBox').value,
            jobType: document.getElementById('jobTypeComboBox').value,
            salary: document.getElementById('salaryComboBox').value,
            degree: document.getElementById('degreeComboBox').value,
            scale: document.getElementById('scaleComboBox').value,
            stage: document.getElementById('stageComboBox').value,
            expectedPosition: document.getElementById('expectedPositionField').value,
            minSalary: document.getElementById('minSalaryField').value,
            maxSalary: document.getElementById('maxSalaryField').value,
            
            // 简历配置
            resumeImagePath: document.getElementById('resumeImagePathField').value,
            resumeContent: document.getElementById('resumeContentTextArea').value,
            sayHi: document.getElementById('sayHiTextArea').value,
            
            // 功能开关
            filterDeadHR: document.getElementById('filterDeadHRCheckBox').checked,
            sendImgResume: document.getElementById('sendImgResumeCheckBox').checked,
            keyFilter: document.getElementById('keyFilterCheckBox').checked,
            recommendJobs: document.getElementById('recommendJobsCheckBox').checked,
            
            // AI配置
            enableAIJobMatchDetection: document.getElementById('enableAIJobMatchDetectionCheckBox').checked,
            enableAIGreeting: document.getElementById('enableAIGreetingCheckBox').checked,
            checkStateOwned: document.getElementById('checkStateOwnedCheckBox').checked,
            
            // 系统参数
            waitTime: document.getElementById('waitTimeField').value
        };

        localStorage.setItem('bossConfig', JSON.stringify(this.config));

        // 同步保存到后端
        try {
            fetch('/api/config/boss', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(this.config)
            }).then(() => {}).catch(() => {});
        } catch (e) {}
    }

    // 加载保存的配置（优先后端，其次本地缓存）
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

    // 填充表单
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

    // 获取字段ID
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
            expectedPosition: 'expectedPositionField',
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

    // 处理保存并开始（兼容旧逻辑，仍保留）
    handleSaveAndStart() {
        if (this.isRunning) {
            alert('任务已在运行中...');
            return;
        }

        // 验证必填字段
        if (!this.validateRequiredFields()) {
            alert('请填写所有必填字段');
            return;
        }

        // 保存配置
        this.saveConfig();

        // 开始执行
        this.startExecution();
    }

    // 仅保存配置
    handleSaveOnly() {
        // 验证必填字段（保存时可选：放宽要求，这里仍做基本校验提示但允许保存）
        this.saveConfig();
        this.showToast('配置已保存');
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

    // =====================
    // 新的4个步骤API调用方法
    // =====================

    // 步骤1: 登录
    async handleLogin() {
        if (!this.validateRequiredFields()) {
            this.showAlertModal('验证失败', '请先完善必填项');
            return;
        }

        this.updateButtonState('loginBtn', 'loginStatus', '执行中...', true);
        
        try {
            const config = this.getCurrentConfig();
            const response = await fetch('/api/boss/task/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });

            const result = await response.json();
            
            if (result.success) {
                this.taskStates.loginTaskId = result.taskId;
                this.updateButtonState('loginBtn', 'loginStatus', '登录成功', false);
                // 登录成功后，同时启用步骤2、3、4
                this.enableNextStep('collectBtn', 'collectStatus', '可开始采集');
                this.enableNextStep('filterBtn', 'filterStatus', '可开始过滤');
                this.enableNextStep('deliverBtn', 'deliverStatus', '可开始投递');
                this.showToast('登录成功！');
            } else {
                this.updateButtonState('loginBtn', 'loginStatus', '登录失败', false);
                this.showToast(result.message || '登录失败', 'danger');
            }
        } catch (error) {
            this.updateButtonState('loginBtn', 'loginStatus', '登录失败', false);
            this.showToast('登录接口调用失败: ' + error.message, 'danger');
        }
    }

    // 步骤2: 采集岗位
    async handleCollect() {
        if (!this.taskStates.loginTaskId) {
            this.showAlertModal('操作提示', '请先完成登录步骤');
            return;
        }

        this.updateButtonState('collectBtn', 'collectStatus', '采集中...', true);
        
        try {
            const config = this.getCurrentConfig();
            const response = await fetch('/api/boss/task/collect', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });

            const result = await response.json();
            
            if (result.success) {
                this.taskStates.collectTaskId = result.taskId;
                this.updateButtonState('collectBtn', 'collectStatus', `采集完成(${result.jobCount}个岗位)`, false);
                this.showToast(`采集完成，共找到 ${result.jobCount} 个岗位！`);
            } else {
                this.updateButtonState('collectBtn', 'collectStatus', '采集失败', false);
                this.showToast(result.message || '采集失败', 'danger');
            }
        } catch (error) {
            this.updateButtonState('collectBtn', 'collectStatus', '采集失败', false);
            this.showToast('采集接口调用失败: ' + error.message, 'danger');
        }
    }

    // 步骤3: 过滤岗位
    async handleFilter() {
        if (!this.taskStates.loginTaskId) {
            this.showAlertModal('操作提示', '请先完成登录步骤');
            return;
        }

        this.updateButtonState('filterBtn', 'filterStatus', '过滤中...', true);
        
        try {
            const config = this.getCurrentConfig();
            const request = {
                collectTaskId: this.taskStates.collectTaskId,
                config: config
            };

            const response = await fetch('/api/boss/task/filter', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            });

            const result = await response.json();
            
            if (result.success) {
                this.taskStates.filterTaskId = result.taskId;
                this.updateButtonState('filterBtn', 'filterStatus', 
                    `过滤完成(${result.originalCount}→${result.filteredCount})`, false);
                this.showToast(`过滤完成，从 ${result.originalCount} 个岗位中筛选出 ${result.filteredCount} 个！`);
            } else {
                this.updateButtonState('filterBtn', 'filterStatus', '过滤失败', false);
                this.showToast(result.message || '过滤失败', 'danger');
            }
        } catch (error) {
            this.updateButtonState('filterBtn', 'filterStatus', '过滤失败', false);
            this.showToast('过滤接口调用失败: ' + error.message, 'danger');
        }
    }

    // 步骤4: 投递岗位
    async handleDeliver() {
        if (!this.taskStates.loginTaskId) {
            this.showAlertModal('操作提示', '请先完成登录步骤');
            return;
        }

        // 询问是否实际投递
        this.showConfirmModal(
            '投递确认',
            '是否执行实际投递？\n点击"确定"将真实投递简历\n点击"取消"将仅模拟投递',
            () => this.executeDeliver(true),
            () => this.executeDeliver(false)
        );
    }

    // 执行投递
    async executeDeliver(enableActualDelivery) {

        this.updateButtonState('deliverBtn', 'deliverStatus', '投递中...', true);
        
        try {
            const config = this.getCurrentConfig();
            const request = {
                filterTaskId: this.taskStates.filterTaskId,
                config: config,
                enableActualDelivery: enableActualDelivery
            };

            const response = await fetch('/api/boss/task/deliver', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request)
            });

            const result = await response.json();
            
            if (result.success) {
                this.taskStates.deliverTaskId = result.taskId;
                const deliveryType = result.actualDelivery ? '实际投递' : '模拟投递';
                this.updateButtonState('deliverBtn', 'deliverStatus', 
                    `${deliveryType}完成(${result.deliveredCount}/${result.totalCount})`, false);
                this.showToast(`${deliveryType}完成！处理了 ${result.deliveredCount} 个岗位`);
            } else {
                this.updateButtonState('deliverBtn', 'deliverStatus', '投递失败', false);
                this.showToast(result.message || '投递失败', 'danger');
            }
        } catch (error) {
            this.updateButtonState('deliverBtn', 'deliverStatus', '投递失败', false);
            this.showToast('投递接口调用失败: ' + error.message, 'danger');
        }
    }

    // 获取当前配置
    getCurrentConfig() {
        return {
            // 搜索条件
            keywords: document.getElementById('keywordsField').value,
            industry: document.getElementById('industryField').value,
            cityCode: document.getElementById('cityCodeField').value,
            
            // 职位要求
            experience: document.getElementById('experienceComboBox').value,
            jobType: document.getElementById('jobTypeComboBox').value,
            salary: document.getElementById('salaryComboBox').value,
            degree: document.getElementById('degreeComboBox').value,
            scale: document.getElementById('scaleComboBox').value,
            stage: document.getElementById('stageComboBox').value,
            minSalary: document.getElementById('minSalaryField').value,
            maxSalary: document.getElementById('maxSalaryField').value,
            
            // 简历配置
            resumeImagePath: document.getElementById('resumeImagePathField').value,
            resumeContent: document.getElementById('resumeContentTextArea').value,
            sayHi: document.getElementById('sayHiTextArea').value,
            
            // 功能开关
            filterDeadHR: document.getElementById('filterDeadHRCheckBox').checked,
            sendImgResume: document.getElementById('sendImgResumeCheckBox').checked,
            keyFilter: document.getElementById('keyFilterCheckBox').checked,
            recommendJobs: document.getElementById('recommendJobsCheckBox').checked,
            
            // AI配置
            enableAIJobMatchDetection: document.getElementById('enableAIJobMatchDetectionCheckBox').checked,
            enableAIGreeting: document.getElementById('enableAIGreetingCheckBox').checked,
            checkStateOwned: document.getElementById('checkStateOwnedCheckBox').checked,
            
            // 系统参数
            waitTime: document.getElementById('waitTimeField').value
        };
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
                // 重置任务状态
                this.taskStates = {
                    loginTaskId: null,
                    collectTaskId: null,
                    filterTaskId: null,
                    deliverTaskId: null
                };

                // 重置按钮状态
                this.updateButtonState('loginBtn', 'loginStatus', '待执行', false);
                this.updateButtonState('collectBtn', 'collectStatus', '等待登录', true);
                this.updateButtonState('filterBtn', 'filterStatus', '等待登录', true);
                this.updateButtonState('deliverBtn', 'deliverStatus', '等待登录', true);

                // 禁用后续步骤按钮
                document.getElementById('collectBtn').disabled = true;
                document.getElementById('filterBtn').disabled = true;
                document.getElementById('deliverBtn').disabled = true;

                this.showToast('任务流程已重置', 'info');
            }
        );
    }

    // 仅开始执行（保留兼容性）
    handleStartOnly() {
        this.showAlertModal('功能提示', '请使用新的分步执行功能：登录 → 采集 → 过滤 → 投递');
    }

    // 验证必填字段
    validateRequiredFields() {
        // 基础必填字段
        const alwaysRequiredFields = [
            'keywordsField',
            'cityCodeField',
            'experienceComboBox',
            'jobTypeComboBox',
            'salaryComboBox',
            'degreeComboBox',
            'sayHiTextArea'
        ];

        // 条件必填字段
        const conditionalRequiredFields = [
            'resumeImagePathField'
        ];

        let isValid = true;
        
        // 验证基础必填字段
        alwaysRequiredFields.forEach(fieldId => {
            const field = document.getElementById(fieldId);
            if (field) {
                // 选择框使用专门的验证方法
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

        // 验证条件必填字段
        conditionalRequiredFields.forEach(fieldId => {
            const field = document.getElementById(fieldId);
            if (field && !this.validateConditionalField(field)) {
                isValid = false;
            }
        });

        return isValid && this.validateSalaryRange() && this.validateWaitTime();
    }

    // 开始执行（已废弃，使用新的分步执行）
    startExecution() {
        this.showAlertModal('功能提示', '请使用新的分步执行功能：登录 → 采集 → 过滤 → 投递');
    }

    // 模拟执行过程
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

    // 完成执行（已废弃，使用新的分步执行）
    finishExecution() {
        this.isRunning = false;
        this.showToast('任务执行完成！');
    }

    // 显示全局Toast
    showToast(message, variant = 'success') {
        try {
            const toastEl = document.getElementById('globalToast');
            const bodyEl = document.getElementById('globalToastBody');
            if (!toastEl || !bodyEl) return;
            bodyEl.textContent = message || '操作成功';
            // 根据 variant 调整背景色
            const variants = ['success', 'danger', 'warning', 'info', 'primary', 'secondary', 'dark'];
            variants.forEach(v => toastEl.classList.remove(`text-bg-${v}`));
            toastEl.classList.add(`text-bg-${variant}`);
            const toast = bootstrap.Toast.getOrCreateInstance(toastEl, { delay: 2000 });
            toast.show();
        } catch (_) {}
    }

    // =====================
    // Bootstrap 模态框辅助方法
    // =====================

    // 显示确认对话框
    showConfirmModal(title, message, onConfirm, onCancel) {
        const modal = new bootstrap.Modal(document.getElementById('confirmModal'));
        document.getElementById('confirmModalLabel').textContent = title;
        document.getElementById('confirmModalBody').textContent = message;
        
        // 移除之前的事件监听器
        const okBtn = document.getElementById('confirmModalOk');
        const newOkBtn = okBtn.cloneNode(true);
        okBtn.parentNode.replaceChild(newOkBtn, okBtn);
        
        // 添加新的事件监听器
        newOkBtn.addEventListener('click', () => {
            modal.hide();
            if (onConfirm) onConfirm();
        });
        
        // 绑定取消事件
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

    // 显示输入对话框
    showInputModal(title, label1, placeholder1, label2, placeholder2, onConfirm) {
        const modal = new bootstrap.Modal(document.getElementById('inputModal'));
        const field2Container = document.getElementById('inputModalField2Container');
        
        // 设置标题
        document.getElementById('inputModalLabel').textContent = title;
        
        // 设置第一个输入框
        document.getElementById('inputModalLabel1').textContent = label1;
        const field1 = document.getElementById('inputModalField1');
        field1.placeholder = placeholder1;
        field1.value = '';
        
        // 设置第二个输入框（如果提供）
        if (label2 && placeholder2) {
            field2Container.style.display = 'block';
            document.getElementById('inputModalLabel2').textContent = label2;
            const field2 = document.getElementById('inputModalField2');
            field2.placeholder = placeholder2;
            field2.value = '';
        } else {
            field2Container.style.display = 'none';
        }
        
        // 移除之前的事件监听器
        const okBtn = document.getElementById('inputModalOk');
        const newOkBtn = okBtn.cloneNode(true);
        okBtn.parentNode.replaceChild(newOkBtn, okBtn);
        
        // 添加新的事件监听器
        newOkBtn.addEventListener('click', () => {
            const value1 = field1.value.trim();
            const value2 = field2Container.style.display !== 'none' ? 
                document.getElementById('inputModalField2').value.trim() : null;
            
            modal.hide();
            if (onConfirm) onConfirm(value1, value2);
        });
        
        modal.show();
    }


}

// 页面加载完成后初始化应用（原配置表单/记录页逻辑）
document.addEventListener('DOMContentLoaded', () => {
    window.bossConfigApp = new BossConfigApp();

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
                    async load(force = false) {
                        console.log('开始加载配置数据');
                        this.loading = true;
                        this.error = '';
                        try {
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
        console.error('初始化岗位明细Vue应用失败:', error);
    }
});

// 注意：不要重复创建Vue应用，已在上面的DOMContentLoaded中创建

// 导出配置功能
function exportConfig() {
    const config = (window.bossConfigApp && window.bossConfigApp.config) || {};
    const dataStr = JSON.stringify(config, null, 2);
    const dataBlob = new Blob([dataStr], { type: 'application/json' });
    const url = URL.createObjectURL(dataBlob);
    
    const link = document.createElement('a');
    link.href = url;
    link.download = 'boss-config.json';
    link.click();
    
    URL.revokeObjectURL(url);
}

// 导入配置功能
function importConfig() {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    
    input.onchange = (event) => {
        const file = event.target.files[0];
        if (file) {
            const reader = new FileReader();
            reader.onload = (e) => {
                try {
                    const config = JSON.parse(e.target.result);
                    if (window.bossConfigApp) {
                        window.bossConfigApp.config = config;
                        if (typeof window.bossConfigApp.populateForm === 'function') {
                            window.bossConfigApp.populateForm();
                        }
                        window.bossConfigApp.showAlertModal('导入成功', '配置已导入');
                    }
                } catch (error) {
                    if (window.bossConfigApp) {
                        window.bossConfigApp.showAlertModal('导入失败', '配置导入失败: ' + error.message);
                    }
                }
            };
            reader.readAsText(file);
        }
    };
    
    input.click();
}
