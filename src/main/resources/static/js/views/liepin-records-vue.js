// 猎聘岗位明细Vue应用
(function () {
    if (!window.Views) window.Views = {};

    class LiepinRecordsVue {
        constructor() {
            this.app = null;
            this.init();
        }

        init() {
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
                    async loadJobs(page = 0) {
                        this.loading = true;
                        this.error = null;
                        
                        try {
                            const params = new URLSearchParams();
                            params.set('platform', 'liepin');
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

                    searchJobs() {
                        this.loadJobs(0);
                    },

                    refreshData() {
                        this.loadJobs(this.currentPage);
                    },

                    goToPage(page) {
                        if (page >= 0 && page < this.totalPages) {
                            this.loadJobs(page);
                        }
                    },

                    parseArray(str) {
                        if (!str) return [];
                        try {
                            if (typeof str === 'string') {
                                return JSON.parse(str);
                            }
                            return Array.isArray(str) ? str : [];
                        } catch (e) {
                            return [];
                        }
                    },

                    truncateText(text, maxLength = 50) {
                        if (!text) return '';
                        if (text.length <= maxLength) return text;
                        return text.substring(0, maxLength) + '...';
                    },

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

                    viewJobDetail(job) {
                        if (job.jobUrl) {
                            window.open(job.jobUrl, '_blank');
                        } else {
                            this.showAlert('岗位链接不可用');
                        }
                    },

                    async copyJobUrl(job) {
                        if (job.jobUrl) {
                            try {
                                await navigator.clipboard.writeText(job.jobUrl);
                                this.showAlert('链接已复制到剪贴板');
                            } catch (e) {
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
                            this.showAlert('操作失败，请稍后重试');
                        }
                    },

                    viewCompanyInfo(job) {
                        if (job.companyName) {
                            this.showCompanyModal(job);
                        } else {
                            this.showAlert('暂无公司详细信息');
                        }
                    },

                    showCompanyModal(job) {
                        const modalHtml = `
                            <div class="modal fade" id="companyModalLiepin" tabindex="-1" aria-labelledby="companyModalLabelLiepin" aria-hidden="true">
                                <div class="modal-dialog modal-lg">
                                    <div class="modal-content">
                                        <div class="modal-header">
                                            <h5 class="modal-title" id="companyModalLabelLiepin">
                                                <i class="bi bi-building me-2"></i>${job.companyName}
                                            </h5>
                                            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                                        </div>
                                        <div class="modal-body">
                                            ...
                                        </div>
                                        <div class="modal-footer">
                                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">关闭</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        `;

                        const existingModal = document.getElementById('companyModalLiepin');
                        if (existingModal) {
                            existingModal.remove();
                        }

                        document.body.insertAdjacentHTML('beforeend', modalHtml);

                        const modal = new bootstrap.Modal(document.getElementById('companyModalLiepin'));
                        modal.show();

                        document.getElementById('companyModalLiepin').addEventListener('hidden.bs.modal', function() {
                            this.remove();
                        });
                    },

                    showAlert(message) {
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
                    const liepinRecordsTab = document.getElementById('liepin-records-tab');
                    if (liepinRecordsTab) {
                        liepinRecordsTab.addEventListener('shown.bs.tab', () => {
                            this.loadJobs(0);
                        });
                    }
                    
                    const searchBtn = document.getElementById('liepinRecordSearchBtn');
                    if (searchBtn) {
                        searchBtn.addEventListener('click', () => {
                            const keywordInput = document.getElementById('liepinRecordKeyword');
                            if (keywordInput) {
                                this.searchKeyword = keywordInput.value;
                                this.searchJobs();
                            }
                        });
                    }

                    const refreshBtn = document.getElementById('liepinRecordRefreshBtn');
                    if (refreshBtn) {
                        refreshBtn.addEventListener('click', () => {
                            this.refreshData();
                        });
                    }

                    const keywordInput = document.getElementById('liepinRecordKeyword');
                    if (keywordInput) {
                        keywordInput.addEventListener('keypress', (e) => {
                            if (e.key === 'Enter') {
                                this.searchKeyword = keywordInput.value;
                                this.searchJobs();
                            }
                        });
                    }
                    
                    // 初始加载
                    const pane = document.getElementById('liepin-records-pane');
                    if(pane && pane.classList.contains('active')){
                        this.loadJobs(0);
                    }
                }
            });

            const mountElement = document.getElementById('liepinRecordsVueApp');
            if (mountElement) {
                this.app.mount(mountElement);
            }
        }

        destroy() {
            if (this.app) {
                this.app.unmount();
                this.app = null;
            }
        }
    }

    window.Views.LiepinRecordsVue = LiepinRecordsVue;
})();
