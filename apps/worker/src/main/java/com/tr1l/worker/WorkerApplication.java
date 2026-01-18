package com.tr1l.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.tr1l.worker","com.tr1l.billing","com.tr1l.util"})
public class WorkerApplication {
     public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class);
    }
}
