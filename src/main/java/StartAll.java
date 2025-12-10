import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class StartAll {
    // 存储所有子进程的引用
    private static final List<Process> childProcesses = new ArrayList<>();

    public static void main(String[] args) {

         // Create a ScheduledExecutorService for Boss
         ScheduledExecutorService bossScheduler = Executors.newSingleThreadScheduledExecutor();

        // 定义Boss任务
        Runnable bossTask = () -> {
            try {
                log.info("正在执行 Boss 任务，线程名称: {}", Thread.currentThread().getName());
                executeTask("boss.Boss");
                log.info("Boss 任务已完成，完成时间: {}", java.time.LocalDateTime.now());
            } catch (Exception e) {
                log.error("Boss 任务执行过程中发生错误: {}", e.getMessage(), e);
            }
        };

        // 创建一个统一的线程池来执行所有任务
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        // 定义Liepin任务
        Runnable liepinTask = () -> {
            try {
                log.info("正在执行 Liepin 任务，线程名称: {}", Thread.currentThread().getName());
                executeTask("liepin.Liepin");
                log.info("Liepin 任务已完成，完成时间: {}", java.time.LocalDateTime.now());
            } catch (Exception e) {
                log.error("Liepin 任务执行过程中发生错误: {}", e.getMessage(), e);
            }
        };

        // 定义Job51任务
        Runnable job51Task = () -> {
            try {
                log.info("正在执行 Job51 任务，线程名称: {}", Thread.currentThread().getName());
                executeTask("job51.Job51");
                log.info("Job51 任务已完成，完成时间: {}", java.time.LocalDateTime.now());
            } catch (Exception e) {
                log.error("Job51 任务执行过程中发生错误: {}", e.getMessage(), e);
            }
        };

        // 提交所有任务到线程池执行
//        executorService.submit(liepinTask);
//        executorService.submit(job51Task);

        // 添加关闭钩子，优雅地关闭线程池和子进程
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭线程池和子进程...");
            
            // 关闭所有子进程
            synchronized (childProcesses) {
                for (Process process : childProcesses) {
                    if (process != null && process.isAlive()) {
                        process.destroyForcibly();
                    }
                }
                childProcesses.clear();
            }
            
            executorService.shutdown();
            bossScheduler.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("强制关闭线程池...");
                    executorService.shutdownNow();
                    bossScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭线程池时发生错误: {}", e.getMessage(), e);
                executorService.shutdownNow();
                bossScheduler.shutdownNow();
            }
        }));
    }

    /**
     * 使用独立进程运行指定的类
     *
     * @param className 要执行的类名
     * @throws Exception 如果发生错误
     */
    private static void executeTask(String className) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
            "java", "-cp", System.getProperty("java.class.path"), className
        );
        processBuilder.inheritIO(); // 将子进程的输入/输出重定向到当前进程
        Process process = processBuilder.start();
        
        // 将进程添加到管理列表中
        synchronized (childProcesses) {
            childProcesses.add(process);
        }
        
        int exitCode = process.waitFor();
        
        // 进程结束后从列表中移除
        synchronized (childProcesses) {
            childProcesses.remove(process);
        }
        
        if (exitCode != 0) {
            throw new RuntimeException(className + " 执行失败，退出代码: " + exitCode);
        }
    }
}
