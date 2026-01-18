package com.tr1l.deliveryserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.tr1l")
public class DeliveryApplication {
     public static void main(String[] args) {
        SpringApplication.run(DeliveryApplication.class);
    }
}
