package com.sakthi.trade;


import com.sakthi.trade.zerodha.account.User;
import com.sakthi.trade.zerodha.account.UserList;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//import org.telegram.telegrambots.ApiContextInitializer;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class SquantAutomation {
    public static void main(String[] args) {
        try {
        //    ApiContextInitializer.init();
            SpringApplication.run(SquantAutomation.class, args);
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    /*
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/optionExpDate").allowedOrigins("http://localhost:3001");
            }
        };
    }*/

}
