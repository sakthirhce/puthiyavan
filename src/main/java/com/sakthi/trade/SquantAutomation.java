package com.sakthi.trade;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class SquantAutomation {
    public static void main(String[] args) {
        try {
            SpringApplication.run(SquantAutomation.class, args);
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }


}
