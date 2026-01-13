package com.edu.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestConfig {
    @Bean
    public RestTemplate restTemplate() {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // Time to establish the connection
        //factory.setConnectTimeout(2_000); // 2 seconds

        // Time waiting for data after connection is established
        //factory.setReadTimeout(3_000); // 3 seconds

        return new RestTemplate(factory);
    }
}
