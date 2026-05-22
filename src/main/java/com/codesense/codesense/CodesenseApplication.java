package com.codesense.codesense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching   // enables Redis caching with @Cacheable
@EnableAsync     // enables async ingestion with @Async
public class CodesenseApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodesenseApplication.class, args);
    }
}