// 公共工具方法和基础类
class CommonUtils {
    // 工具：简易转义
    static escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // 工具：时间格式化（ISO 或时间戳）
    static formatDateTime(input) {
        try {
            if (!input) return '';
            const d = new Date(input);
            if (isNaN(d.getTime())) return '';
            const pad = (n) => (n < 10 ? '0' + n : '' + n);
            return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
        } catch (_) { return ''; }
    }

    // 显示全局Toast
    static showToast(message, variant = 'success') {
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

    // 显示确认对话框
    static showConfirmModal(title, message, onConfirm, onCancel) {
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
    static showAlertModal(title, message) {
        const modal = new bootstrap.Modal(document.getElementById('alertModal'));
        document.getElementById('alertModalLabel').textContent = title;
        document.getElementById('alertModalBody').textContent = message;
        modal.show();
    }

    // 显示输入对话框
    static showInputModal(title, label1, placeholder1, label2, placeholder2, onConfirm) {
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

    // 初始化工具提示
    static initializeTooltips() {
        const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
        tooltipTriggerList.map(function (tooltipTriggerEl) {
            return new bootstrap.Tooltip(tooltipTriggerEl);
        });
    }
}

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
                        CommonUtils.showAlertModal('导入成功', '配置已导入');
                    }
                } catch (error) {
                    if (window.bossConfigApp) {
                        CommonUtils.showAlertModal('导入失败', '配置导入失败: ' + error.message);
                    }
                }
            };
            reader.readAsText(file);
        }
    };
    
    input.click();
}
