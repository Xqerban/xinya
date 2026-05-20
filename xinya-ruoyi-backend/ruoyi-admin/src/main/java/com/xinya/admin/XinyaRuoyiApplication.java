package com.xinya.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.xinya.admin",
        "com.xinya.business",
        "com.xinya.framework",
        "com.xinya.common"
})
public class XinyaRuoyiApplication {
    public static void main(String[] args) {
        SpringApplication.run(XinyaRuoyiApplication.class, args);
    }
}
