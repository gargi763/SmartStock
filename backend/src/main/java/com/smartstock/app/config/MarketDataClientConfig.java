package com.smartstock.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class MarketDataClientConfig {

    @Bean
    public RestClient alphaVantageRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(8));

        return RestClient.builder()
                .baseUrl("https://www.alphavantage.co/query")
                .requestFactory(requestFactory)
                .build();
    }
}
