package com.ununn.aidome;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource("classpath:application.yml")
public class AidomeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AidomeApplication.class, args);
    }

}
