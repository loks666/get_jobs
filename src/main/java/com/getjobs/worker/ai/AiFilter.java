package com.getjobs.worker.ai;

import lombok.Data;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Data
public class AiFilter {

    /**
     * ai检测结果
     */
    private Boolean result;

    /**
     * 如果匹配，则返回的打招呼语
     */
    private String message;

    public AiFilter(Boolean result) {
        this.result = result;
    }

    public AiFilter(Boolean result, String message) {
        this.result = result;
        this.message = message;
    }

}
