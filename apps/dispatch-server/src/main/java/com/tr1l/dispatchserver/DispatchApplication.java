package com.tr1l.dispatchserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.tr1l.dispatch"})
@EnableScheduling
public class DispatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(DispatchApplication.class);
    }
}
