// 智联招聘岗位明细Vue应用
(function () {
    if (!window.Views) window.Views = {};

    class ZhilianRecordsVue {
        constructor() {
            this.app = null;
            this.init();
        }

        init() {
            // 等待DOM加载完成
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', () => this.createVueApp());
            } else {
                this.createVueApp();
            }
        }

        createVueApp() {
            const { createApp } = Vue;
            
            this.app = createApp({
                data() {
                    return {
                        loading: false,
                        error: null,
                        searchKeyword: '',
                        records: [],
                        currentPage: 0,
                        pageSize: 10,
                        totalElements: 0,
                        totalPages: 0,
                        numberOfElements: 0,
                        first: true,
                        last: false,
                        empty: true
                    }
                },
                computed: {
                    visiblePages() {
                        const pages = [];
                        const start = Math.max(0, this.currentPage - 2);
                        const end = Math.min(this.totalPages - 1, this.currentPage + 2);
                        
                        for (let i = start; i <= end; i++) {
                            pages.push(i);
                        }
                        return pages;
                    }
                },
                methods: {
                    // 加载岗位数据
                    async loadJobs(page = 0) {
                        this.loading = true;
                        this.error = null;
                        
                        try {
                            const params = new URLSearchParams();
                            params.set('platform', 'zhilian');
                            if (this.searchKeyword.trim()) {
                                params.set('keyword', this.searchKeyword.trim());
                            }
                            params.set('page', page.toString());
                            params.set('size', this.pageSize.toString());

                            const response = await fetch(`/api/jobs?${params.toString()}`);
                            if (!response.ok) {
                                throw new Error(`HTTP error! status: ${response.status}`);
                            }
                            
                            const data = await response.json();
                            
                            this.records = data.content || [];
                            this.currentPage = data.number || 0;
                            this.totalElements = data.totalElements || 0;
                            this.totalPages = data.totalPages || 0;
                            this.numberOfElements = data.numberOfElements || 0;
                            this.first = data.first || false;
                            this.last = data.last || false;
                            this.empty = data.empty || true;
                            
                        } catch (error) {
                            console.error('加载岗位数据失败:', error);
                            this.error = '加载数据失败，请稍后重试';
                            this.records = [];
                        } finally {
                            this.loading = false;
                        }
                    },

                    // 搜索岗位
                    searchJobs() {
                        this.loadJobs(0);
                    },

                    // 刷新数据
                    refreshData() {
                        this.loadJobs(this.currentPage);
                    },

                    // 分页跳转
                    goToPage(page) {
                        if (page >= 0 && page < this.totalPages) {
                            this.loadJobs(page);
                        }
                    },

                    // 解析数组字符串
                    parseArray(str) {
                        if (!str) return [];
                        try {
                            if (typeof str === 'string') {
                                return JSON.parse(str);
                            }
                            return Array.isArray(str) ? str : [];
                        } catch (e) {
                            console.warn('解析数组失败:', str, e);
                            return [];
                        }
                    },

                    // 截断文本
                    truncateText(text, maxLength = 50) {
                        if (!text) return '';
                        if (text.length <= maxLength) return text;
                        return text.substring(0, maxLength) + '...';
                    },

                    // 格式化日期时间
                    formatDateTime(dateStr) {
                        if (!dateStr) return '-';
                        try {
                            const date = new Date(dateStr);
                            if (isNaN(date.getTime())) return '-';
                            
                            const year = date.getFullYear();
                            const month = String(date.getMonth() + 1).padStart(2, '0');
                            const day = String(date.getDate()).padStart(2, '0');
                            const hours = String(date.getHours()).padStart(2, '0');
                            const minutes = String(date.getMinutes()).padStart(2, '0');
                            
                            return `${year}-${month}-${day} ${hours}:${minutes}`;
                        } catch (e) {
                            return '-';
                        }
                    },

                    // 获取状态信息
                    getStatusInfo(status) {
                        const statusMap = {
                            0: { text: '待处理', class: 'badge bg-secondary' },
                            1: { text: '待处理', class: 'badge bg-secondary' },
                            2: { text: '已过滤', class: 'badge bg-warning' },
                            3: { text: '投递成功', class: 'badge bg-success' },
                            4: { text: '投递失败', class: 'badge bg-danger' }
                        };
                        return statusMap[status] || { text: '未知', class: 'badge bg-dark' };
                    },

                    // 获取工作类型文本
                    getJobTypeText(jobType) {
                        const typeMap = {
                            0: '全职',
                            1: '兼职',
                            2: '实习',
                            3: '合同工',
                            4: '外包'
                        };
                        return typeMap[jobType] || '未知';
                    },

                    // 查看岗位详情
                    viewJobDetail(job) {
                        if (job.jobUrl) {
                            window.open(job.jobUrl, '_blank');
                        } else {
                            this.showAlert('岗位链接不可用');
                        }
                    },

                    // 复制岗位链接
                    async copyJobUrl(job) {
                        if (job.jobUrl) {
                            try {
                                await navigator.clipboard.writeText(job.jobUrl);
                                this.showAlert('链接已复制到剪贴板');
                            } catch (e) {
                                // 降级方案
                                const textArea = document.createElement('textarea');
                                textArea.value = job.jobUrl;
                                document.body.appendChild(textArea);
                                textArea.select();
                                document.execCommand('copy');
                                document.body.removeChild(textArea);
                                this.showAlert('链接已复制到剪贴板');
                            }
                        } else {
                            this.showAlert('岗位链接不可用');
                        }
                    },

                    // 切换收藏状态
                    async toggleFavorite(job) {
                        try {
                            const response = await fetch(`/api/jobs/${job.id}/favorite`, {
                                method: 'PUT',
                                headers: {
                                    'Content-Type': 'application/json'
                                },
                                body: JSON.stringify({
                                    isFavorite: !job.isFavorite
                                })
                            });

                            if (response.ok) {
                                job.isFavorite = !job.isFavorite;
                                this.showAlert(job.isFavorite ? '已添加到收藏' : '已取消收藏');
                            } else {
                                this.showAlert('操作失败，请稍后重试');
                            }
                        } catch (error) {
                            console.error('切换收藏状态失败:', error);
                            this.showAlert('操作失败，请稍后重试');
                        }
                    },

                    // 查看公司信息
                    viewCompanyInfo(job) {
                        if (job.companyName) {
                            this.showCompanyModal(job);
                        } else {
                            this.showAlert('暂无公司详细信息');
                        }
                    },

                    // 显示公司信息模态框
                    showCompanyModal(job) {
                        // 创建模态框HTML
                        const modalHtml = `
                            <div class="modal fade" id="companyModal" tabindex="-1" aria-labelledby="companyModalLabel" aria-hidden="true">
                                <div class="modal-dialog modal-lg">
                                    <div class="modal-content">
                                        <div class="modal-header">
                                            <h5 class="modal-title" id="companyModalLabel">
                                                <i class="bi bi-building me-2"></i>${job.companyName}
                                            </h5>
                                            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                                        </div>
                                        <div class="modal-body">
                                            <div class="row">
                                                <div class="col-md-4">
                                                    <div class="company-logo-large mb-3">
                                                        <img src="${job.companyLogo || ''}" class="img-fluid rounded" alt="${job.companyName}" 
                                                             style="max-height: 120px;">
                                                    </div>
                                                    <div class="company-basic-info">
                                                        <h6><i class="bi bi-building me-2"></i>基本信息</h6>
                                                        <ul class="list-unstyled">
                                                            <li><strong>行业:</strong> ${job.companyIndustry || '-'}</li>
                                                            <li><strong>规模:</strong> ${job.companyScale || '-'}</li>
                                                            <li><strong>阶段:</strong> ${job.companyStage || '-'}</li>
                                                            <li><strong>城市:</strong> ${job.workCity || '-'}</li>
                                                        </ul>
                                                    </div>
                                                </div>
                                                <div class="col-md-8">
                                                    <div class="company-intro">
                                                        <h6><i class="bi bi-info-circle me-2"></i>公司介绍</h6>
                                                        <div class="intro-content" style="max-height: 300px; overflow-y: auto;">
                                                            <p>${job.companyDescription || '暂无公司介绍'}</p>
                                                        </div>
                                                    </div>
                                                    <div class="company-welfare mt-3">
                                                        <h6><i class="bi bi-gift me-2"></i>公司福利</h6>
                                                        <div class="welfare-tags">
                                                            ${this.parseArray(job.welfareList).map(welfare => 
                                                                `<span class="badge bg-success me-1 mb-1">${welfare}</span>`
                                                            ).join('')}
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                        <div class="modal-footer">
                                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">关闭</button>
                                            <button type="button" class="btn btn-primary" onclick="window.open('${job.jobUrl || '#'}', '_blank')">
                                                <i class="bi bi-eye me-1"></i>查看岗位详情
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        `;

                        // 移除已存在的模态框
                        const existingModal = document.getElementById('companyModal');
                        if (existingModal) {
                            existingModal.remove();
                        }

                        // 添加新模态框到页面
                        document.body.insertAdjacentHTML('beforeend', modalHtml);

                        // 显示模态框
                        const modal = new bootstrap.Modal(document.getElementById('companyModal'));
                        modal.show();

                        // 模态框关闭后移除
                        document.getElementById('companyModal').addEventListener('hidden.bs.modal', function() {
                            this.remove();
                        });
                    },

                    // 显示提示信息
                    showAlert(message) {
                        // 使用Bootstrap的toast组件
                        const toastElement = document.getElementById('globalToast');
                        const toastBody = document.getElementById('globalToastBody');
                        if (toastElement && toastBody) {
                            toastBody.textContent = message;
                            const toast = new bootstrap.Toast(toastElement);
                            toast.show();
                        } else {
                            alert(message);
                        }
                    }
                },
                mounted() {
                    // 监听标签页切换事件
                    const zhilianRecordsTab = document.getElementById('zhilian-records-tab');
                    if (zhilianRecordsTab) {
                        zhilianRecordsTab.addEventListener('shown.bs.tab', () => {
                            this.loadJobs(0);
                        });
                    }
                    
                    // 绑定搜索按钮事件
                    const searchBtn = document.getElementById('zhilianRecordSearchBtn');
                    if (searchBtn) {
                        searchBtn.addEventListener('click', () => {
                            const keywordInput = document.getElementById('zhilianRecordKeyword');
                            if (keywordInput) {
                                this.searchKeyword = keywordInput.value;
                                this.searchJobs();
                            }
                        });
                    }

                    // 绑定刷新按钮事件
                    const refreshBtn = document.getElementById('zhilianRecordRefreshBtn');
                    if (refreshBtn) {
                        refreshBtn.addEventListener('click', () => {
                            this.refreshData();
                        });
                    }

                    // 绑定回车键搜索
                    const keywordInput = document.getElementById('zhilianRecordKeyword');
                    if (keywordInput) {
                        keywordInput.addEventListener('keypress', (e) => {
                            if (e.key === 'Enter') {
                                this.searchKeyword = keywordInput.value;
                                this.searchJobs();
                            }
                        });
                    }
                    
                    // 初始加载数据
                    this.loadJobs(0);
                }
            });

            // 挂载到指定元素
            const mountElement = document.getElementById('zhilianRecordsVueApp');
            if (mountElement) {
                const rootInstance = this.app.mount(mountElement);
                // 暴露根组件实例，便于外部按钮调用其方法
                window.zhilianRecordsRoot = rootInstance;
            }
        }

        // 销毁应用
        destroy() {
            if (this.app) {
                this.app.unmount();
                this.app = null;
            }
        }
    }

    // 导出类
    window.Views.ZhilianRecordsVue = ZhilianRecordsVue;
})();
