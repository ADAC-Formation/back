package com.adac.portail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PortailAdacApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortailAdacApplication.class, args);
    }
}
