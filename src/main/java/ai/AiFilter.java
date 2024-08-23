package ai;

import lombok.Data;

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
