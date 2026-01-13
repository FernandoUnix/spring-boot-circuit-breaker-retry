package com.edu.orderservice.service.impl;

import com.edu.orderservice.chaos.ChaosFaultInjector;
import com.edu.orderservice.model.Failure;
import com.edu.orderservice.model.Order;
import com.edu.orderservice.model.Type;
import com.edu.orderservice.repository.OrderRepository;
import com.edu.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderServiceImplTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ChaosFaultInjector chaosFaultInjector;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setup() {
        orderRepository.deleteAll();
        orderRepository.save(
                Order.builder()
                        .id(1)
                        .orderNumber("ORDER-1")
                        .postalCode("12345")
                        .shippingState("TX")
                        .shippingCity("Texas")
                        .build()
        );

        chaosFaultInjector.setEnabled(false);
        chaosFaultInjector.setTimeout(false);
        chaosFaultInjector.setConnectionError(false);
        chaosFaultInjector.setHttp500(false);
        chaosFaultInjector.setLatency(false);
        chaosFaultInjector.setLatencyMS(0);

        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void shouldReturnOrderSuccessfully() {
        // Given
        mockServer.expect(requestTo("http://localhost:9090/addresses/12345"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":1,\"postalCode\":\"12345\",\"state\":\"TX\",\"city\":\"Austin\"}"));

        // When
        Type result = orderService.getOrderByPostCode("ORDER-1");

        // Then
        assertThat(result).isInstanceOf(Order.class);
        Order order = (Order) result;
        assertThat(order.getOrderNumber()).isEqualTo("ORDER-1");
        assertThat(order.getPostalCode()).isEqualTo("12345");
        assertThat(order.getShippingState()).isEqualTo("TX");
        assertThat(order.getShippingCity()).isEqualTo("Austin");
        mockServer.verify();
    }

    @Test
    void shouldReturnRetryExhaustedWhenConnectionErrorIsInjected() {
        // Given
        chaosFaultInjector.setEnabled(true);
        chaosFaultInjector.setConnectionError(true);

        // When
        Type result = orderService.getOrderByPostCode("ORDER-1");

        // Then
        assertThat(result).isInstanceOf(Failure.class);
        Failure failure = (Failure) result;
        assertThat(failure.getReason()).isEqualTo("RETRY_EXHAUSTED");
    }

    @Test
    void shouldReturnCircuitOpenWhenCircuitBreakerIsOpen() throws InterruptedException {
        // Given
        chaosFaultInjector.setEnabled(true);
        chaosFaultInjector.setConnectionError(true);
        orderService.getOrderByPostCode("ORDER-1");
        orderService.getOrderByPostCode("ORDER-1");

        Thread.sleep(1100);

        chaosFaultInjector.setEnabled(false);
        chaosFaultInjector.setConnectionError(false);

        // When
        Type result = orderService.getOrderByPostCode("ORDER-1");

        // Then
        assertThat(result).isInstanceOf(Failure.class);
        Failure failure = (Failure) result;
        assertThat(failure.getReason()).isEqualTo("CIRCUIT_OPEN");
    }

    @Test
    void shouldReturnBulkheadWhenBulkheadIsFull() throws InterruptedException {
        // Given
        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            mockServer.expect(requestTo("http://localhost:9090/addresses/12345"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"id\":1,\"postalCode\":\"12345\",\"state\":\"TX\",\"city\":\"Austin\"}"));
        }

        // When
        Type[] results = new Type[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    results[index] = orderService.getOrderByPostCode("ORDER-1");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        boolean foundBulkheadFailure = false;
        for (Type result : results) {
            if (result instanceof Failure failure && "BULKHEAD_FULL".equals(failure.getReason())) {
                foundBulkheadFailure = true;
                break;
            }
        }
        assertThat(foundBulkheadFailure).isTrue();
    }

    @Test
    void shouldReturnRateLimitWhenRateLimiterIsExceeded() throws InterruptedException {
        // Given
        int numberOfCalls = 5;

        for (int i = 0; i < numberOfCalls; i++) {
            mockServer.expect(requestTo("http://localhost:9090/addresses/12345"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"id\":1,\"postalCode\":\"12345\",\"state\":\"TX\",\"city\":\"Austin\"}"));
        }

        // When
        Type[] results = new Type[numberOfCalls];
        for (int i = 0; i < numberOfCalls; i++) {
            results[i] = orderService.getOrderByPostCode("ORDER-1");
        }

        // Then
        boolean foundRateLimitFailure = false;
        for (Type result : results) {
            if (result instanceof Failure failure && "RATE_LIMIT".equals(failure.getReason())) {
                foundRateLimitFailure = true;
                break;
            }
        }
        assertThat(foundRateLimitFailure).isTrue();
    }
}
