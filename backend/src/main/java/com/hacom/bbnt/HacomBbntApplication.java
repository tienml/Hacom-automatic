package com.hacom.bbnt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HacomBbntApplication {
    public static void main(String[] args) {
        SpringApplication.run(HacomBbntApplication.class, args);
    }
}
