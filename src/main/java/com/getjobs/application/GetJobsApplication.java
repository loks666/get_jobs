package com.getjobs.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * GetJobs应用程序启动类
 * 自动化求职平台的主入口
 * 
 * @author GetJobs
 * @version 0.0.1-SNAPSHOT
 */
@SpringBootApplication(scanBasePackages = "com.getjobs")
@EnableScheduling
@EnableAsync
public class GetJobsApplication {
    public static void main(String[] args) {
        SpringApplication.run(GetJobsApplication.class, args);
    }
}