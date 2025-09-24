/**
 * 51job岗位明细Vue组件
 */
const { createApp } = Vue;

// 创建51job岗位明细Vue应用
const Job51RecordsApp = createApp({
    data() {
        return {
            loading: false,
            error: null,
            records: [],
            totalElements: 0,
            totalPages: 0,
            currentPage: 0,
            pageSize: 10,
            searchKeyword: '',
            
            // 状态映射
            statusMap: {
                0: { text: '待投递', class: 'badge bg-secondary' },
                1: { text: '已投递', class: 'badge bg-primary' },
                2: { text: '已沟通', class: 'badge bg-info' },
                3: { text: '面试中', class: 'badge bg-warning' },
                4: { text: '已录用', class: 'badge bg-success' },
                5: { text: '已拒绝', class: 'badge bg-danger' },
                6: { text: '已过期', class: 'badge bg-dark' }
            },
            
            // 工作类型映射
            jobTypeMap: {
                0: '全职',
                1: '兼职',
                2: '实习',
                3: '合同工'
            }
        };
    },
    
    computed: {
        // 可见页码
        visiblePages() {
            const start = Math.max(0, this.currentPage - 2);
            const end = Math.min(this.totalPages, start + 5);
            return Array.from({ length: end - start }, (_, i) => start + i);
        }
    },
    
    mounted() {
        this.loadRecords();
        
        // 绑定搜索功能
        const searchBtn = document.getElementById('job51RecordSearchBtn');
        const refreshBtn = document.getElementById('job51RecordRefreshBtn');
        const searchInput = document.getElementById('job51RecordKeyword');
        
        if (searchBtn) {
            searchBtn.addEventListener('click', () => {
                this.searchKeyword = searchInput?.value || '';
                this.currentPage = 0;
                this.loadRecords();
            });
        }
        
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => {
                this.searchKeyword = '';
                if (searchInput) searchInput.value = '';
                this.currentPage = 0;
                this.loadRecords();
            });
        }
        
        if (searchInput) {
            searchInput.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') {
                    this.searchKeyword = e.target.value;
                    this.currentPage = 0;
                    this.loadRecords();
                }
            });
        }
    },
    
    methods: {
        // 加载岗位记录
        async loadRecords() {
            this.loading = true;
            this.error = null;
            
            try {
                const params = new URLSearchParams({
                    page: this.currentPage,
                    size: this.pageSize,
                    sort: 'createdAt,desc'
                });
                
                if (this.searchKeyword) {
                    params.append('keyword', this.searchKeyword);
                }
                
                const response = await fetch(`/api/jobs?platform=51job&${params}`);
                
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                
                const data = await response.json();
                
                this.records = data.content || [];
                this.totalElements = data.totalElements || 0;
                this.totalPages = data.totalPages || 0;
                this.currentPage = data.number || 0;
                
            } catch (error) {
                console.error('加载51job岗位记录失败:', error);
                this.error = '加载数据失败，请稍后重试';
                this.records = [];
            } finally {
                this.loading = false;
            }
        },
        
        // 分页跳转
        goToPage(page) {
            if (page >= 0 && page < this.totalPages && page !== this.currentPage) {
                this.currentPage = page;
                this.loadRecords();
            }
        },
        
        // 格式化日期时间
        formatDateTime(dateStr) {
            if (!dateStr) return '-';
            try {
                const date = new Date(dateStr);
                return date.toLocaleString('zh-CN', {
                    year: 'numeric',
                    month: '2-digit',
                    day: '2-digit',
                    hour: '2-digit',
                    minute: '2-digit'
                });
            } catch (e) {
                return dateStr;
            }
        },
        
        // 解析数组字符串
        parseArray(arrayStr) {
            if (!arrayStr) return [];
            if (Array.isArray(arrayStr)) return arrayStr;
            try {
                return JSON.parse(arrayStr);
            } catch (e) {
                // 如果不是JSON格式，尝试按逗号分割
                return arrayStr.split(',').map(item => item.trim()).filter(item => item);
            }
        },
        
        // 截断文本
        truncateText(text, maxLength = 100) {
            if (!text) return '';
            return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
        },
        
        // 获取状态显示信息
        getStatusInfo(status) {
            return this.statusMap[status] || { text: '未知', class: 'badge bg-secondary' };
        },
        
        // 获取工作类型文本
        getJobTypeText(jobType) {
            return this.jobTypeMap[jobType] || '未知';
        },
        
        // 查看职位详情
        viewJobDetail(job) {
            if (job.jobUrl) {
                window.open(job.jobUrl, '_blank');
            } else {
                this.showAlert('暂无职位链接');
            }
        },
        
        // 复制职位链接
        async copyJobUrl(job) {
            if (!job.jobUrl) {
                this.showAlert('暂无职位链接');
                return;
            }
            
            try {
                await navigator.clipboard.writeText(job.jobUrl);
                this.showToast('链接已复制到剪贴板');
            } catch (error) {
                console.error('复制失败:', error);
                this.showAlert('复制失败，请手动复制链接');
            }
        },
        
        // 收藏/取消收藏
        async toggleFavorite(job) {
            try {
                // 这里可以实现收藏功能的API调用
                // 目前先只在前端更新状态，后续可以添加后端API
                job.isFavorite = !job.isFavorite;
                this.showToast(job.isFavorite ? '已收藏' : '已取消收藏');
            } catch (error) {
                console.error('收藏操作失败:', error);
                this.showAlert('操作失败，请稍后重试');
            }
        },
        
        // 查看公司信息
        viewCompanyInfo(job) {
            // 可以在这里实现公司信息弹窗或跳转
            if (job.companyName) {
                this.showAlert(`公司名称：${job.companyName}\n行业：${job.companyIndustry || '未知'}\n规模：${job.companyScale || '未知'}\n性质：${job.companyStage || '未知'}`);
            }
        },
        
        // 显示Toast消息
        showToast(message, type = 'success') {
            const toastEl = document.getElementById('globalToast');
            const toastBody = document.getElementById('globalToastBody');
            
            if (toastEl && toastBody) {
                toastBody.textContent = message;
                toastEl.className = `toast align-items-center text-bg-${type} border-0`;
                
                const toast = new bootstrap.Toast(toastEl);
                toast.show();
            }
        },
        
        // 显示Alert对话框
        showAlert(message) {
            const modalEl = document.getElementById('alertModal');
            const modalBody = document.getElementById('alertModalBody');
            
            if (modalEl && modalBody) {
                modalBody.textContent = message;
                const modal = new bootstrap.Modal(modalEl);
                modal.show();
            } else {
                alert(message);
            }
        }
    }
});

// 等待DOM加载完成后挂载Vue应用
document.addEventListener('DOMContentLoaded', function() {
    const container = document.getElementById('job51RecordsVueApp');
    if (container) {
        Job51RecordsApp.mount('#job51RecordsVueApp');
    }
});
