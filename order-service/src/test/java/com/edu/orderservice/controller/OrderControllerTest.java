package com.edu.orderservice.controller;

import com.edu.orderservice.chaos.ChaosFaultInjector;
import com.edu.orderservice.model.Order;
import com.edu.orderservice.repository.OrderRepository;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.internal.RetryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ChaosFaultInjector chaos;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setup() {
        mockServer = MockRestServiceServer.createServer(restTemplate);

        orderRepository.deleteAll();
        orderRepository.save(
                new Order(1, "ORDER-1", "12345", null, null)
        );

        // RESET CHAOS //Not using chaos anymore
//        chaos.setEnabled(false);
//        chaos.setTimeout(false);
//        chaos.setConnectionError(false);
//        chaos.setHttp500(false);
//        chaos.setLatency(false);
    }

    // ------------------------------------------------------------------
    // ✅ START SUCCESS
    // ------------------------------------------------------------------
    @Test
    void shouldReturn200WhenAddressServiceIsOk() throws Exception {

        mockServer.expect(requestTo("http://localhost:9090/addresses/12345"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                            {
                              "id":1,
                              "postalCode":"12345",
                              "state":"TX",
                              "city":"Austin"
                            }
                        """));

        mockMvc.perform(get("/orders")
                        .param("orderNumber", "ORDER-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingState").value("TX"))
                .andExpect(jsonPath("$.shippingCity").value("Austin"));
    }

    // ------------------------------------------------------------------
    // ✅ END SUCCESS
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // ❌ START RETRY
    // ------------------------------------------------------------------
    @Test
    void shouldFailIfRetryAttemptsExceedThree() throws Exception {

        // --------------------------------------------------
        // Arrange
        // --------------------------------------------------
        AtomicInteger callCount = new AtomicInteger(0);

        mockServer.expect(ExpectedCount.times(3),
                        requestTo("http://localhost:9090/addresses/12345"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {

                    callCount.incrementAndGet();

                    // Todas as tentativas falham
                    throw new ResourceAccessException("Connection refused");
                });

        long start = System.currentTimeMillis();

        // --------------------------------------------------
        // Act
        // --------------------------------------------------
        mockMvc.perform(get("/orders")
                        .param("orderNumber", "ORDER-1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.reason").value("RETRY_EXHAUSTED"));

        long duration = System.currentTimeMillis() - start;

        // --------------------------------------------------
        // Assert
        // --------------------------------------------------

        // 1 tentativa inicial + 2 retries
        assertThat(callCount.get()).isEqualTo(3);

        // Delay mínimo esperado:
        // 1s (retry 1) + 2s (retry 2) = ~3s
        assertThat(duration)
                .isGreaterThanOrEqualTo(3000)
                .isLessThan(4500); // margem de segurança para CI

        mockServer.verify();
    }

    @Test
    void shouldRetryOnceAndSucceedOnSecondAttempt() throws Exception {

        AtomicInteger callCount = new AtomicInteger();

        mockServer.expect(ExpectedCount.manyTimes(),
                        requestTo("http://localhost:9090/addresses/12345"))
                .andRespond(request -> {
                    int currentCall = callCount.incrementAndGet();

                    // 1ª tentativa → falha
                    if (currentCall == 1) {
                        return withServerError().createResponse(request);
                    }

                    // 2ª tentativa → sucesso
                    return withSuccess(
                            "{\"status\":\"OK\",\"data\":\"SUCCESS\"}",
                            MediaType.APPLICATION_JSON
                    ).createResponse(request);
                });

        long start = System.currentTimeMillis();

        mockMvc.perform(get("/orders")
                        .param("orderNumber", "ORDER-1"))
                .andExpect(status().isOk());

        long duration = System.currentTimeMillis() - start;

        // ✔ valida retry aplicado
        assertThat(callCount.get()).isEqualTo(2);

        // ✔ valida delay mínimo de 1s entre tentativas
        assertThat(duration).isGreaterThanOrEqualTo(1000);

        mockServer.verify();
    }

    @Test
    void shouldRetryTwiceAndSucceedOnThirdAttempt() throws Exception {

        // --------------------------------------------------
        // Arrange
        // --------------------------------------------------
        AtomicInteger callCount = new AtomicInteger(0);

        mockServer.expect(ExpectedCount.times(3),
                        requestTo("http://localhost:9090/addresses/12345"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {

                    int attempt = callCount.incrementAndGet();

                    // 1ª e 2ª tentativas → falha
                    if (attempt <= 2) {
                        throw new ResourceAccessException("Connection refused");
                    }

                    // 3ª tentativa → sucesso
                    return withSuccess()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("""
                        {
                          "id": 1,
                          "postalCode": "12345",
                          "state": "TX",
                          "city": "Austin"
                        }
                        """)
                            .createResponse(request);
                });

        long start = System.currentTimeMillis();

        // --------------------------------------------------
        // Act
        // --------------------------------------------------
        mockMvc.perform(get("/orders")
                        .param("orderNumber", "ORDER-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingState").value("TX"))
                .andExpect(jsonPath("$.shippingCity").value("Austin"));

        long duration = System.currentTimeMillis() - start;

        // --------------------------------------------------
        // Assert
        // --------------------------------------------------

        // 3 execuções (1 original + 2 retries)
        assertThat(callCount.get()).isEqualTo(3);

        // Delay mínimo esperado:
        // 1s (retry 1) + 2s (retry 2) = ~3s
        assertThat(duration)
                .isGreaterThanOrEqualTo(3000)
                .isLessThan(4500); // margem para CI

        mockServer.verify();
    }

    // ------------------------------------------------------------------
    // ❌ END RETRY
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // ❌ CIRCUIT_OPEN → 503
    // ------------------------------------------------------------------
    @Test
    void shouldReturn503WhenCircuitIsOpen() throws Exception {

        chaos.setEnabled(true);
        chaos.setConnectionError(true);

        // 🔥 força falhas suficientes para abrir o circuito
        for (int i = 0; i < 6; i++) {
            try {
                mockMvc.perform(get("/orders")
                        .param("orderNumber", "ORDER-1"));
            } catch (Exception ignored) {}
        }

        mockMvc.perform(get("/orders")
                        .param("orderNumber", "ORDER-1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("CIRCUIT_OPEN"));
    }

    // ------------------------------------------------------------------
    // ❌ START BULKHEAD → 429
    // ------------------------------------------------------------------

    @Test
    void shouldReturn429WhenBulkheadIsFull() throws Exception {

        // ------------------------------------------------------------
        // Arrange
        // ------------------------------------------------------------

        int bulkheadLimit = 2;

        CountDownLatch enteredLatch = new CountDownLatch(bulkheadLimit);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        mockServer.expect(ExpectedCount.manyTimes(),
                        requestTo("http://localhost:9090/addresses/12345"))
                .andRespond(request -> {

                    // sinaliza que a chamada entrou no método protegido
                    enteredLatch.countDown();

                    // mantém a thread ocupando o bulkhead
                    try {
                        releaseLatch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    return withSuccess("""
                {
                  "id": 1,
                  "postalCode": "12345",
                  "state": "TX",
                  "city": "Austin"
                }
            """, MediaType.APPLICATION_JSON).createResponse(request);
                });

        Runnable call = () -> {
            try {
                mockMvc.perform(get("/orders")
                                .param("orderNumber", "ORDER-1"))
                        .andExpect(status().isOk());
            } catch (Exception ignored) {}
        };

        // ------------------------------------------------------------
        // Act — dispara chamadas concorrentes
        // ------------------------------------------------------------

        Thread t1 = new Thread(call);
        Thread t2 = new Thread(call);

        t1.start();
        t2.start();

        // garante que o bulkhead está totalmente ocupado
        assertTrue(
                enteredLatch.await(2, TimeUnit.SECONDS),
                "Bulkhead was not fully occupied in time"
        );

        // ------------------------------------------------------------
        // Assert — 4ª chamada deve falhar
        // ------------------------------------------------------------

        mockMvc.perform(get("/orders")
                        .param("orderNumber", "ORDER-1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reason").value("BULKHEAD_FULL"));

        // ------------------------------------------------------------
        // Cleanup
        // ------------------------------------------------------------

        releaseLatch.countDown();

        t1.join();
        t2.join();
    }

    @Test
    void shouldAllowSecondCallWhenOnlyOneThreadIsOccupied() throws Exception {

        //bulkheadLimit = 2;

        CountDownLatch enteredLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        mockServer.expect(ExpectedCount.manyTimes(),
                        requestTo("http://localhost:9090/addresses/12345"))
                .andRespond(request -> {

                    enteredLatch.countDown();

                    try {
                        releaseLatch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    return withSuccess("""
                    {
                      "id": 1,
                      "postalCode": "12345",
                      "state": "TX",
                      "city": "Austin"
                    }
                """, MediaType.APPLICATION_JSON).createResponse(request);
                });

        Runnable call = () -> {
            try {
                mockMvc.perform(get("/orders")
                                .param("orderNumber", "ORDER-1"))
                        .andExpect(status().isOk());
            } catch (Exception ignored) {}
        };

        // ------------------------------------------------------------
        // Act — ocupa apenas 1 slot do bulkhead
        // ------------------------------------------------------------

        Thread t1 = new Thread(call);
        t1.start();

        assertTrue(
                enteredLatch.await(2, TimeUnit.SECONDS),
                "First call did not enter bulkhead"
        );

        // ------------------------------------------------------------
        // Assert — segunda chamada ainda deve passar
        // ------------------------------------------------------------

        mockMvc.perform(get("/orders")
                        .param("orderNumber", "ORDER-1"))
                .andExpect(status().isOk());

        // ------------------------------------------------------------
        // Cleanup
        // ------------------------------------------------------------

        releaseLatch.countDown();
        t1.join();
    }

    // ------------------------------------------------------------------
    // ❌ START BULKHEAD → 429
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // ❌ RATE_LIMIT → 429
    // ------------------------------------------------------------------
    @Test
    void shouldReturn429WhenRateLimitIsExceeded() throws Exception {

        for (int i = 0; i < 15; i++) {
            try {
                mockMvc.perform(get("/orders")
                        .param("orderNumber", "ORDER-1"));
            } catch (Exception ignored) {}
        }

        mockMvc.perform(get("/orders")
                        .param("orderNumber", "ORDER-1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reason").value("RATE_LIMIT"));
    }
}