package com.xinya.ops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class XinyaOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(XinyaOpsApplication.class, args);
    }
}
