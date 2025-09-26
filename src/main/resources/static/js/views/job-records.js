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
                self.loadJobDetails('zhilian', 'zhilian', 0);
            });
            document.getElementById('job51-records-tab')?.addEventListener('shown.bs.tab', function () {
                self.loadJobDetails('51job', 'job51', 0);
            });

            document.getElementById('bossRecordSearchBtn')?.addEventListener('click', () => {
                this.loadJobDetails('Boss直聘', 'boss', 0);
            });
            document.getElementById('zhilianRecordSearchBtn')?.addEventListener('click', () => {
                this.loadJobDetails('zhilian', 'zhilian', 0);
            });
            document.getElementById('job51RecordSearchBtn')?.addEventListener('click', () => {
                this.loadJobDetails('51job', 'job51', 0);
            });
            document.getElementById('job51RecordRefreshBtn')?.addEventListener('click', () => {
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
            const colSpan = platformKey === 'job51' ? '14' : '7'; // 51job使用14列，其他使用7列
            if (tbody) {
                tbody.innerHTML = `<tr><td colspan="${colSpan}" class="text-center text-muted">加载中...</td></tr>`;
            }

            fetch(`/api/jobs?${params.toString()}`)
                .then(res => res.json())
                .then(page => {
                    this.renderJobDetailsTable(platformKey, page);
                    this.renderPagination(platformKey, page);
                })
                .catch(() => {
                    if (tbody) {
                        const colSpan = platformKey === 'job51' ? '14' : '7';
                        tbody.innerHTML = `<tr><td colspan="${colSpan}" class="text-center text-danger">加载失败</td></tr>`;
                    }
                });
        }

        renderJobDetailsTable(platformKey, page) {
            const tbodyIdMap = { boss: 'bossRecordTbody', zhilian: 'zhilianRecordTbody', job51: 'job51RecordTbody' };
            const tbody = document.getElementById(tbodyIdMap[platformKey]);
            if (!tbody) {
                return;
            }

            const content = Array.isArray(page?.content) ? page.content : [];
            if (content.length === 0) {
                const colSpan = platformKey === 'job51' ? '14' : '7';
                tbody.innerHTML = `<tr><td colspan="${colSpan}" class="text-center text-muted">暂无数据</td></tr>`;
                return;
            }

            const statusBadge = (status) => {
                const map = { 0: { text: '待处理', cls: 'secondary' }, 1: { text: '已处理', cls: 'success' }, 2: { text: '已忽略', cls: 'warning' } };
                const it = map[status] || { text: '未知', cls: 'dark' };
                return `<span class="badge bg-${it.cls}">${it.text}</span>`;
            };

            const rows = content.map((j, index) => {
                
                const escapeHtml = (str) => String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
                const fmt = (input) => { try { if (!input) return ''; const d = new Date(input); if (isNaN(d.getTime())) return ''; const pad = (n) => (n < 10 ? '0' + n : '' + n); return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`; } catch (_) { return ''; } };
                const truncateText = (text, maxLength) => { if (!text || text.length <= maxLength) return text; return text.substring(0, maxLength) + '...'; };
                const parseArray = (str) => { try { return Array.isArray(str) ? str : (str ? JSON.parse(str) : []); } catch { return str ? str.split(',').map(s => s.trim()) : []; } };
                
                if (platformKey === 'job51') {
                    // 51job的详细字段显示 - 基于完整的API对象结构
                    const jobTitle = escapeHtml(j.jobTitle || '未知岗位');
                    const companyName = escapeHtml(j.companyName || '未知公司');
                    const workCity = escapeHtml(j.workCity || '未知城市');
                    const workArea = escapeHtml(j.workArea || '');
                    const businessDistrict = escapeHtml(j.businessDistrict || '');
                    const salaryDesc = escapeHtml(j.salaryDesc || '面议');
                    const deliverTime = fmt(j.createdAt || j.updatedAt);
                    const state = statusBadge(j.status || 0);
                    const hrName = escapeHtml(j.hrName || '未知HR');
                    const hrTitle = escapeHtml(j.hrTitle || '');
                    const hrActiveTime = escapeHtml(j.hrActiveTime || j.hrActiveTimeDesc || '未知');
                    const jobExperience = escapeHtml(j.jobExperience || '经验不限');
                    const jobDegree = escapeHtml(j.jobDegree || '学历不限');
                    const jobDescription = escapeHtml(truncateText(j.jobDescription || '暂无描述', 100));
                    const skills = parseArray(j.skills || j.jobLabels || '[]');
                    const companyStage = escapeHtml(j.companyStage || '');
                    const companyScale = escapeHtml(j.companyScale || '');
                    const companyIndustry = escapeHtml(j.companyIndustry || '');
                    const companyTag = escapeHtml(j.companyTag || '');
                    const filterReason = escapeHtml(j.filterReason || '');
                    
                    // 新增字段解析
                    const encryptJobId = escapeHtml(j.encryptJobId || '');
                    const encryptHrId = escapeHtml(j.encryptHrId || '');
                    const encryptCompanyId = escapeHtml(j.encryptCompanyId || '');
                    const platform = escapeHtml(j.platform || '51job');
                    const industryCode = j.industryCode || null;
                    const jobType = j.jobType || null;
                    const isDeleted = j.isDeleted || false;
                    const longitude = j.longitude || null;
                    const latitude = j.latitude || null;
                    const hrCertLevel = escapeHtml(j.hrCertLevel || '');
                    const jobRequirements = escapeHtml(truncateText(j.jobRequirements || '', 80));
                    const remark = escapeHtml(j.remark || '');
                    const securityId = escapeHtml(j.securityId || '');
                    
                    return `
                        <tr data-job-id="${j.id}">
                            <!-- 基本信息 -->
                            <td>
                                <div class="job-title mb-1">
                                    <a href="${j.jobUrl || '#'}" target="_blank" class="text-decoration-none fw-bold">${jobTitle}</a>
                                </div>
                                <div class="job-id mb-1">
                                    <small class="text-muted">ID: ${j.id || '-'}</small>
                                </div>
                                <div class="encrypt-ids">
                                    ${encryptJobId ? `<small class="text-muted d-block">JobID: ${encryptJobId}</small>` : ''}
                                    ${encryptHrId ? `<small class="text-muted d-block">HrID: ${encryptHrId}</small>` : ''}
                                </div>
                                <div class="status-badges mt-1">
                                    ${j.isFavorite ? '<span class="badge bg-danger me-1">★</span>' : ''}
                                    ${j.isOptimal ? '<span class="badge bg-warning me-1">优</span>' : ''}
                                    ${j.isContacted ? '<span class="badge bg-info me-1">联</span>' : ''}
                                    ${isDeleted ? '<span class="badge bg-dark me-1">删</span>' : ''}
                                </div>
                            </td>
                            <!-- 岗位详情 -->
                            <td>
                                <div class="job-description mb-2">${jobDescription}</div>
                                ${jobRequirements ? `<div class="job-requirements mb-2"><strong>要求:</strong> ${jobRequirements}</div>` : ''}
                                <div class="job-type-info">
                                    ${jobType !== null ? `<span class="badge bg-primary me-1">类型: ${jobType}</span>` : ''}
                                    ${j.jobPositionName ? `<div class="mt-1"><small class="text-muted">职位: ${escapeHtml(j.jobPositionName)}</small></div>` : ''}
                                </div>
                                ${j.isProxyJob ? '<div class="mt-1"><span class="badge bg-secondary">代理职位</span></div>' : ''}
                            </td>
                            <!-- 公司信息 -->
                            <td>
                                <div class="company-name mb-1 fw-bold">${companyName}</div>
                                <div class="company-details">
                                    ${companyIndustry ? `<div><i class="bi bi-building me-1"></i>${companyIndustry}</div>` : ''}
                                    ${companyScale ? `<div><i class="bi bi-people me-1"></i>${companyScale}</div>` : ''}
                                    ${companyStage ? `<div><i class="bi bi-graph-up me-1"></i>${companyStage}</div>` : ''}
                                </div>
                                ${j.companyLogo ? `<div class="company-logo mt-1"><img src="${j.companyLogo}" alt="${companyName}" style="width: 32px; height: 32px; border-radius: 4px;"></div>` : ''}
                                ${encryptCompanyId ? `<div class="mt-1"><small class="text-muted">CompanyID: ${encryptCompanyId}</small></div>` : ''}
                                ${industryCode ? `<div class="mt-1"><small class="text-muted">行业代码: ${industryCode}</small></div>` : ''}
                            </td>
                            <!-- 工作地点 -->
                            <td>
                                <div class="location-city fw-bold">${workCity}</div>
                                ${workArea ? `<div class="location-area"><i class="bi bi-geo-alt me-1"></i>${workArea}</div>` : ''}
                                ${businessDistrict ? `<div class="business-district"><i class="bi bi-building me-1"></i>${businessDistrict}</div>` : ''}
                                ${longitude && latitude ? `<div class="coordinates mt-1"><small class="text-muted">坐标: ${longitude.toFixed(4)}, ${latitude.toFixed(4)}</small></div>` : ''}
                                ${j.cityCode ? `<div class="mt-1"><small class="text-muted">城市代码: ${j.cityCode}</small></div>` : ''}
                            </td>
                            <!-- 薪资条件 -->
                            <td>
                                <div class="salary-desc fw-bold text-success mb-1">${salaryDesc}</div>
                                <div class="work-schedule">
                                    ${j.leastMonthDesc ? `<div><i class="bi bi-calendar me-1"></i>${escapeHtml(j.leastMonthDesc)}</div>` : ''}
                                    ${j.daysPerWeekDesc ? `<div><i class="bi bi-clock me-1"></i>${escapeHtml(j.daysPerWeekDesc)}</div>` : ''}
                                </div>
                                ${j.jobPayTypeDesc ? `<div class="pay-type mt-1"><small class="text-muted">${escapeHtml(j.jobPayTypeDesc)}</small></div>` : ''}
                            </td>
                            <!-- 职位要求 -->
                            <td>
                                <div class="requirements mb-1">
                                    <div><i class="bi bi-clock me-1"></i><strong>经验:</strong> ${jobExperience}</div>
                                    <div><i class="bi bi-mortarboard me-1"></i><strong>学历:</strong> ${jobDegree}</div>
                                </div>
                                ${j.jobExperienceName ? `<div class="experience-name mt-1"><small class="text-muted">经验类型: ${escapeHtml(j.jobExperienceName)}</small></div>` : ''}
                                ${j.jobDegreeName ? `<div class="degree-name mt-1"><small class="text-muted">学历类型: ${escapeHtml(j.jobDegreeName)}</small></div>` : ''}
                            </td>
                            <!-- 技能标签 -->
                            <td>
                                <div class="skills-container mb-1">
                                    ${skills.slice(0, 3).map(skill => `<span class="badge bg-secondary me-1 mb-1">${escapeHtml(skill)}</span>`).join('')}
                                    ${skills.length > 3 ? `<span class="badge bg-light text-dark">+${skills.length - 3}</span>` : ''}
                                </div>
                                ${parseArray(j.jobLabels || '').length > 0 ? `<div class="job-labels">
                                    ${parseArray(j.jobLabels || '').slice(0, 2).map(label => `<span class="badge bg-outline-primary me-1 mb-1">${escapeHtml(label)}</span>`).join('')}
                                </div>` : ''}
                                ${skills.length > 0 ? `<div class="skill-count mt-1"><small class="text-muted">共${skills.length}项技能</small></div>` : ''}
                            </td>
                            <!-- HR信息 -->
                            <td>
                                <div class="hr-info mb-1">
                                    ${j.hrAvatar ? `<img src="${j.hrAvatar}" class="hr-avatar me-2" alt="${hrName}" style="width: 24px; height: 24px; border-radius: 50%;">` : ''}
                                    <span class="hr-name fw-bold">${hrName}</span>
                                </div>
                                <div class="hr-details">
                                    ${hrTitle ? `<div><i class="bi bi-person-badge me-1"></i>${hrTitle}</div>` : ''}
                                    <div class="hr-status">
                                        <span class="${j.hrOnline ? 'text-success' : 'text-muted'}">
                                            <i class="bi bi-circle-fill"></i>
                                            ${j.hrOnline ? '在线' : '离线'}
                                        </span>
                                    </div>
                                    ${hrActiveTime !== '未知' ? `<div><i class="bi bi-clock me-1"></i>${hrActiveTime}</div>` : ''}
                                    ${hrCertLevel ? `<div class="cert-level mt-1"><small class="text-muted">认证: ${hrCertLevel}</small></div>` : ''}
                                </div>
                            </td>
                            <!-- 公司标签 -->
                            <td>
                                <div class="company-tags">
                                    ${companyTag ? parseArray(companyTag).slice(0, 3).map(tag => `<span class="badge bg-success me-1 mb-1">${escapeHtml(tag)}</span>`).join('') : ''}
                                    ${companyTag && parseArray(companyTag).length > 3 ? `<span class="badge bg-light text-dark">+${parseArray(companyTag).length - 3}</span>` : ''}
                                </div>
                                ${j.welfareList ? `<div class="welfare-list mt-1">
                                    ${parseArray(j.welfareList).slice(0, 2).map(welfare => `<span class="badge bg-outline-success me-1 mb-1">${escapeHtml(welfare)}</span>`).join('')}
                                </div>` : ''}
                                ${companyTag ? `<div class="tag-count mt-1"><small class="text-muted">共${parseArray(companyTag).length}个标签</small></div>` : '<small class="text-muted">暂无标签</small>'}
                            </td>
                            <!-- 职位状态 -->
                            <td>
                                <div class="status-badge mb-1">${state}</div>
                                <div class="status-flags">
                                    ${j.isGoldHunter ? '<span class="badge bg-warning me-1">金猎</span>' : ''}
                                    ${j.isShielded ? '<span class="badge bg-dark me-1">屏蔽</span>' : ''}
                                    ${j.isOutland ? '<span class="badge bg-info me-1">海外</span>' : ''}
                                    ${j.showTopPosition ? '<span class="badge bg-primary me-1">置顶</span>' : ''}
                                </div>
                                ${j.jobValidStatus ? `<div class="valid-status mt-1"><small class="text-muted">有效性: ${j.jobValidStatus}</small></div>` : ''}
                                ${j.jobInvalidStatus ? `<div class="invalid-status mt-1"><small class="text-danger">失效: ${j.jobInvalidStatus}</small></div>` : ''}
                                ${filterReason ? `<div class="filter-reason mt-1" title="${filterReason}"><small class="text-warning">过滤: ${truncateText(filterReason, 20)}</small></div>` : ''}
                            </td>
                            <!-- 平台信息 -->
                            <td>
                                <div class="platform-info mb-1">
                                    <span class="badge bg-primary">${platform}</span>
                                </div>
                                ${j.searchId ? `<div class="search-id"><small class="text-muted">SearchID: ${escapeHtml(j.searchId)}</small></div>` : ''}
                                ${j.itemId ? `<div class="item-id"><small class="text-muted">ItemID: ${escapeHtml(j.itemId)}</small></div>` : ''}
                                ${j.expectId ? `<div class="expect-id"><small class="text-muted">ExpectID: ${escapeHtml(j.expectId)}</small></div>` : ''}
                                ${securityId ? `<div class="security-id"><small class="text-muted">SecurityID: ${securityId}</small></div>` : ''}
                                ${j.atsDirectPost ? '<div class="ats-info mt-1"><span class="badge bg-info">ATS直投</span></div>' : ''}
                            </td>
                            <!-- 时间信息 -->
                            <td>
                                <div class="time-info">
                                    <div class="created-time mb-1">
                                        <strong>创建:</strong><br>
                                        <small>${deliverTime || '-'}</small>
                                    </div>
                                    ${j.updatedAt ? `<div class="updated-time">
                                        <strong>更新:</strong><br>
                                        <small>${fmt(j.updatedAt)}</small>
                                    </div>` : ''}
                                </div>
                            </td>
                            <!-- 扩展信息 -->
                            <td>
                                <div class="extended-info">
                                    ${remark ? `<div class="remark mb-1"><strong>备注:</strong> ${truncateText(remark, 50)}</div>` : ''}
                                    ${j.jobAddress ? `<div class="job-address mb-1"><i class="bi bi-geo-alt me-1"></i>${escapeHtml(truncateText(j.jobAddress, 40))}</div>` : ''}
                                    ${j.anonymousStatus ? `<div class="anonymous"><span class="badge bg-secondary">匿名状态: ${j.anonymousStatus}</span></div>` : ''}
                                    ${j.proxyType ? `<div class="proxy-type mt-1"><small class="text-muted">代理类型: ${j.proxyType}</small></div>` : ''}
                                    ${j.jobDetailType ? `<div class="detail-type mt-1"><small class="text-muted">详情类型: ${j.jobDetailType}</small></div>` : ''}
                                </div>
                            </td>
                            <!-- 操作 -->
                            <td>
                                <div class="action-buttons d-flex flex-column gap-1">
                                    <button class="btn btn-outline-primary btn-sm" onclick="window.open('${j.jobUrl || '#'}', '_blank')" title="查看详情">
                                        <i class="bi bi-eye"></i>
                                    </button>
                                    <button class="btn btn-outline-info btn-sm" onclick="navigator.clipboard.writeText('${j.jobUrl || ''}')" title="复制链接">
                                        <i class="bi bi-link-45deg"></i>
                                    </button>
                                    <button class="btn btn-outline-success btn-sm" title="收藏">
                                        <i class="bi bi-heart${j.isFavorite ? '-fill' : ''}"></i>
                                    </button>
                                    <button class="btn btn-outline-warning btn-sm" title="公司信息">
                                        <i class="bi bi-building"></i>
                                    </button>
                                    <button class="btn btn-outline-secondary btn-sm" title="更多操作">
                                        <i class="bi bi-three-dots"></i>
                                    </button>
                                </div>
                            </td>
                        </tr>
                    `;
                } else {
                    // 其他平台保持原有的简单显示
                    const jobTitle = escapeHtml(j.jobTitle || '-');
                    const companyName = escapeHtml(j.companyName || '-');
                    // 修复字段映射问题 - 尝试多个可能的字段名
                    const city = escapeHtml(j.workCity || j.workLocation || j.city || '-');
                    const salary = escapeHtml(j.salaryDesc || j.salaryRange || j.salary || '-');
                    const deliverTime = fmt(j.createdAt || j.updatedAt);
                    const state = statusBadge(j.status);
                    const hrActive = escapeHtml(j.hrActiveTime || j.hrActiveTimeDesc || j.bossActiveTimeDesc || '-');
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
                }
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


