package zhilian;

import lombok.extern.slf4j.Slf4j;
import utils.JobUtils;
import utils.Platform;

@Slf4j
public class ZhilianScheduled {

    public static void main(String[] args) {
        JobUtils.runScheduled(Platform.ZHILIAN);
    }

    public static void postJobs() {
        safeRun(() -> ZhiLian.main(null));
    }

    private static void safeRun(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("safeRun异常：{}", e.getMessage(), e);
        }
    }
}
