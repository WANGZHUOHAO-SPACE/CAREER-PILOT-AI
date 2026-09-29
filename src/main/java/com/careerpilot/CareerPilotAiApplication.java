package com.careerpilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({"com.careerpilot.mapper", "com.careerpilot.observability"})
public class CareerPilotAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerPilotAiApplication.class, args);
    }
}
