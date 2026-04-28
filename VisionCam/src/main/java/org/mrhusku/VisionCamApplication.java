package org.mrhusku;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class VisionCamApplication {

    public static void main(String[] args) {
        SpringApplication.run(VisionCamApplication.class, args);
    }

    // conf conectivitate
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}