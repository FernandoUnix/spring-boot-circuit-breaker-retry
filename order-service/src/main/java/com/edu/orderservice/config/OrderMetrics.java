package com.edu.orderservice.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter ordersProcessedTotal;
    private final Counter ordersSuccessfulTotal;
    private final Timer orderProcessingDuration;

    public OrderMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.ordersProcessedTotal = Counter.builder("order.processed.total")
                .description("Total number of orders processed")
                .tag("application", "order-service")
                .register(meterRegistry);

        this.ordersSuccessfulTotal = Counter.builder("order.successful.total")
                .description("Total number of successful orders")
                .tag("application", "order-service")
                .register(meterRegistry);

        this.orderProcessingDuration = Timer.builder("order.processing.duration")
                .description("Time taken to process an order")
                .tag("application", "order-service")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    public void incrementOrdersProcessed() {
        ordersProcessedTotal.increment();
    }

    public void incrementOrdersSuccessful() {
        ordersSuccessfulTotal.increment();
    }

    public void incrementOrdersFailed(String reason) {
        Counter.builder("order.failed.total")
                .description("Total number of failed orders")
                .tag("application", "order-service")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void incrementOrdersByPostalCode(String postalCode) {
        Counter.builder("order.by.postal.code")
                .description("Orders processed by postal code")
                .tag("application", "order-service")
                .tag("postal_code", postalCode)
                .register(meterRegistry)
                .increment();
    }

    public Timer.Sample startOrderProcessingTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordOrderProcessingDuration(Timer.Sample sample) {
        sample.stop(orderProcessingDuration);
    }
}

