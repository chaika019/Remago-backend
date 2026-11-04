package com.adrotation.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AdCrmApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdCrmApplication.class, args);
    }
}
