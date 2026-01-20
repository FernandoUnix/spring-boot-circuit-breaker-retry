package com.edu.orderservice.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public OrderMetrics orderMetrics(MeterRegistry meterRegistry) {
        return new OrderMetrics(meterRegistry);
    }
}

