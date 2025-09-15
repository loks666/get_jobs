// 岗位明细列表模块（导出类，不自动初始化）
(function () {
    if (!window.Views) window.Views = {};

    class JobRecords {
        constructor() {
            this.bindEvents();
        }

        bindEvents() {
            const self = this;
            document.getElementById('boss-records-tab')?.addEventListener('shown.bs.tab', function () {
                self.loadJobDetails('Boss直聘', 'boss', 0);
            });
            document.getElementById('zhilian-records-tab')?.addEventListener('shown.bs.tab', function () {
                self.loadJobDetails('智联招聘', 'zhilian', 0);
            });
            document.getElementById('job51-records-tab')?.addEventListener('shown.bs.tab', function () {
                self.loadJobDetails('51job', 'job51', 0);
            });

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
            const keywordInputIdMap = { boss: 'bossRecordKeyword', zhilian: 'zhilianRecordKeyword', job51: 'job51RecordKeyword' };
            const tbodyIdMap = { boss: 'bossRecordTbody', zhilian: 'zhilianRecordTbody', job51: 'job51RecordTbody' };
            const pagerIdMap = { boss: 'bossRecordPagination', zhilian: 'zhilianRecordPagination', job51: 'job51RecordPagination' };

            const keyword = document.getElementById(keywordInputIdMap[platformKey])?.value?.trim() || '';
            const params = new URLSearchParams();
            if (platformText) params.set('platform', platformText);
            if (keyword) params.set('keyword', keyword);
            params.set('page', String(pageIndex));
            params.set('size', '10');

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
            const tbodyIdMap = { boss: 'bossRecordTbody', zhilian: 'zhilianRecordTbody', job51: 'job51RecordTbody' };
            const tbody = document.getElementById(tbodyIdMap[platformKey]);
            if (!tbody) return;

            const content = Array.isArray(page?.content) ? page.content : [];
            if (content.length === 0) {
                tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">暂无数据</td></tr>';
                return;
            }

            const statusBadge = (status) => {
                const map = { 0: { text: '待处理', cls: 'secondary' }, 1: { text: '已处理', cls: 'success' }, 2: { text: '已忽略', cls: 'warning' } };
                const it = map[status] || { text: '未知', cls: 'dark' };
                return `<span class="badge bg-${it.cls}">${it.text}</span>`;
            };

            const rows = content.map(j => {
                const escapeHtml = (str) => String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
                const fmt = (input) => { try { if (!input) return ''; const d = new Date(input); if (isNaN(d.getTime())) return ''; const pad = (n) => (n < 10 ? '0' + n : '' + n); return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`; } catch (_) { return ''; } };
                const jobTitle = escapeHtml(j.jobTitle || '-');
                const companyName = escapeHtml(j.companyName || '-');
                const city = escapeHtml(j.workLocation || '-');
                const salary = escapeHtml(j.salaryRange || '-');
                const deliverTime = fmt(j.createdAt || j.updatedAt);
                const state = statusBadge(j.status);
                const hrActive = escapeHtml(j.hrActiveTime || '-');
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
            const pagerIdMap = { boss: 'bossRecordPagination', zhilian: 'zhilianRecordPagination', job51: 'job51RecordPagination' };
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
    }

    // 导出由 app.js 统一初始化
    window.Views.JobRecords = JobRecords;
})();


