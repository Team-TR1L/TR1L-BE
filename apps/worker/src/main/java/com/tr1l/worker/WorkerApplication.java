package com.tr1l.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.tr1l.worker", "com.tr1l.billing", "com.tr1l.util"})
@ConfigurationPropertiesScan(basePackages = "com.tr1l.worker.config")
public class WorkerApplication {
    public static void main(String[] args) {
        SpringApplication.exit(SpringApplication.run(WorkerApplication.class,args));
    }
}
