package boss;

import lombok.Data;

@Data
public class AIFilter {

    /**
     * ai检测结果
     */
    private Boolean result;

    /**
     * 如果匹配，则返回的打招呼语
     */
    private String message;

    public AIFilter() {
    }

    public AIFilter(Boolean result) {
        this.result = result;
    }

    public AIFilter(Boolean result, String message) {
        this.result = result;
        this.message = message;
    }

}
