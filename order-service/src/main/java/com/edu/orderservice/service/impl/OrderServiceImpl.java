package com.edu.orderservice.service.impl;

import com.edu.orderservice.chaos.ChaosFaultInjector;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.edu.orderservice.config.OrderMetrics;
import com.edu.orderservice.dto.AddressDTO;
import com.edu.orderservice.model.Failure;
import com.edu.orderservice.model.Order;
import com.edu.orderservice.model.Type;
import com.edu.orderservice.repository.OrderRepository;
import com.edu.orderservice.service.OrderService;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private OrderMetrics orderMetrics;

    private static final String SERVICE_NAME = "order-service";
    private static final ConcurrentHashMap<String, Timer.Sample> activeTimers = new ConcurrentHashMap<>();
    private static final String ADDRESS_SERVICE_URL = "http://localhost:9093/addresses/";

    // ---------------------------------------------------------------------
    // CHAOS CONFIGURATION (feature-flag driven)
    // ---------------------------------------------------------------------
//    @Value("${chaos.enabled:false}")
//    private boolean chaosEnabled;
//
//    @Value("${chaos.latency-ms:0}")
//    private long chaosLatencyMs;
//
//    @Value("${chaos.timeout-probability:0}")
//    private double timeoutProbability;
//
//    @Value("${chaos.connection-error-probability:0}")
//    private double connectionErrorProbability;
//
//    @Value("${chaos.http-500-probability:0}")
//    private double http500Probability;

    @Autowired
    private ChaosFaultInjector chaosFaultInjector;

    // ---------------------------------------------------------------------
    // IMPORTANT: Annotation order matters in Spring AOP
    //
    // In Spring AOP, the annotation declared FIRST is the OUTER interceptor,
    // and the annotation declared LAST is the INNER interceptor.
    //
    // Current order (as declared below):
    //
    //   @Retry           → OUTER layer
    //   @CircuitBreaker  → INNER layer
    //
    // Effective execution flow:
    //
    // Controller
    //   → Retry
    //       → CircuitBreaker
    //           → Method execution (repository + HTTP call)
    //
    // ---------------------------------------------------------------------
    // BEHAVIOR DETAILS
    // ---------------------------------------------------------------------
    //
    // 1) Retry behavior
    //
    // - Retry wraps the ENTIRE method execution, not only the HTTP call.
    // - Any exception thrown inside this method can trigger a retry,
    //   including:
    //     - Database / repository errors
    //     - HTTP client errors
    //     - Serialization / mapping errors
    //     - Runtime exceptions
    // - A retry happens ONLY if the exception matches `retry-exceptions`
    //   and does NOT match `ignore-exceptions`.
    // - With max-attempts = 2:
    //     - 1 initial attempt
    //     - 1 retry attempt
    //
    // ---------------------------------------------------------------------
    // 2) Circuit Breaker behavior
    //
    // - CircuitBreaker decides whether a call is allowed to execute.
    // - Possible states:
    //     - CLOSED    → Calls are allowed and monitored
    //     - OPEN      → Calls fail fast (method body is NOT executed)
    //     - HALF_OPEN → Limited test calls are allowed
    //
    // - When the circuit is OPEN:
    //     - No repository access
    //     - No HTTP call
    //     - A CallNotPermittedException is thrown immediately
    //
    // - When the circuit is CLOSED or HALF_OPEN:
    //     - The method executes normally
    //     - Failures are recorded to calculate the failure rate
    //
    // ---------------------------------------------------------------------
    // 3) IMPORTANT DISTINCTION (COMMON MISCONCEPTION)
    //
    // - CircuitBreaker fallback is NOT executed only when the circuit is OPEN.
    // - It is triggered for ANY exception recorded by the Circuit Breaker.
    // - Therefore, fallback execution does NOT reliably indicate
    //   an OPEN circuit.
    //
    // - The ONLY reliable way to detect an OPEN circuit is by checking for:
    //     CallNotPermittedException
    //
    // ---------------------------------------------------------------------
    // 4) Why this annotation order is used
    //
    // - Retry handles transient failures first (timeouts, network glitches).
    // - CircuitBreaker records the FINAL outcome AFTER retries.
    // - This prevents the circuit from opening too aggressively due to
    //   short-lived or recoverable failures.
    // - When the circuit is OPEN, calls fail fast and retries are skipped,
    //   avoiding unnecessary load.
    //
    // ---------------------------------------------------------------------
    // 5) Best practices when combining Retry + CircuitBreaker
    //
    // - Prefer a SINGLE fallback (Retry fallback).
    // - Avoid using a CircuitBreaker fallback together with Retry.
    // - Inside the Retry fallback, explicitly distinguish between:
    //     - CallNotPermittedException → Circuit is OPEN
    //     - Other exceptions         → External service failure after retries
    //
    // ---------------------------------------------------------------------
    @RateLimiter(name = SERVICE_NAME, fallbackMethod = "rateLimitFallback")
    @Bulkhead(name = SERVICE_NAME, type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "bulkheadFallback")
    @Retry(name = SERVICE_NAME, fallbackMethod = "retryFallbackMethod")
    @CircuitBreaker(name = SERVICE_NAME)
    public Type getOrderByPostCode(String orderNumber) {
        Timer.Sample timer = orderMetrics.startOrderProcessingTimer();
        activeTimers.put(orderNumber, timer);
        orderMetrics.incrementOrdersProcessed();

        log.error(">>> METHOD BODY ENTERED <<< orderNumber={}", orderNumber);

        Order order = orderRepository.findByOrderNumber(orderNumber)

                .orElseThrow(() -> {
                    log.error("Order not found. orderNumber={}", orderNumber);
                    Timer.Sample activeTimer = activeTimers.remove(orderNumber);
                    if (activeTimer != null) {
                        orderMetrics.recordOrderProcessingDuration(activeTimer);
                    }
                    orderMetrics.incrementOrdersFailed("ORDER_NOT_FOUND");
                    return new RuntimeException("Order Not Found: " + orderNumber);
                });

        String postalCode = order.getPostalCode();
        orderMetrics.incrementOrdersByPostalCode(postalCode);

        // -----------------------------------------------------------------
        // CHAOS INJECTION (before external call)
        // -----------------------------------------------------------------
        //chaosMonkey();

        // 🔥 FAULT INJECTION
        chaosFaultInjector.inject();

        // -----------------------------------------------------------------
        // External service call log
        // -----------------------------------------------------------------
        log.debug("Calling Address Service. url={}{}", ADDRESS_SERVICE_URL, postalCode);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AddressDTO> entity = new HttpEntity<>(null, headers);

        ResponseEntity<AddressDTO> response = restTemplate.exchange(
                ADDRESS_SERVICE_URL + postalCode,
                HttpMethod.GET,
                entity,
                AddressDTO.class
        );

        AddressDTO addressDTO = response.getBody();

        if (addressDTO != null) {
            order.setShippingState(addressDTO.getState());
            order.setShippingCity(addressDTO.getCity());

            // -----------------------------------------------------------------
            // Success log
            // -----------------------------------------------------------------
            log.info(
                    "Successfully retrieved address. orderNumber={}, city={}, state={}",
                    orderNumber,
                    addressDTO.getCity(),
                    addressDTO.getState()
            );
        } else {
            log.warn(
                    "Address service returned empty body. orderNumber={}, postalCode={}",
                    orderNumber,
                    postalCode
            );
        }

        orderMetrics.incrementOrdersSuccessful();
        Timer.Sample activeTimer = activeTimers.remove(orderNumber);
        if (activeTimer != null) {
            orderMetrics.recordOrderProcessingDuration(activeTimer);
        }
        return order;
    }

    // ---------------------------------------------------------------------
    // CHAOS ENGINEERING CORE
    // ---------------------------------------------------------------------
//    private void chaosMonkey() {
//
//        if (!chaosEnabled) {
//            return;
//        }
//
//        double r = Math.random();
//
//        if (chaosLatencyMs > 0) {
//            try {
//                log.warn("CHAOS: Injecting latency {}ms", chaosLatencyMs);
//                Thread.sleep(chaosLatencyMs);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            }
//        }
//
//        if (r < timeoutProbability) {
//            log.warn("CHAOS: Injecting TIMEOUT");
//            throw new ResourceAccessException("Chaos timeout");
//        }
//
//        if (r < timeoutProbability + connectionErrorProbability) {
//            log.warn("CHAOS: Injecting CONNECTION FAILURE");
//            throw new ResourceAccessException("Chaos connection failure");
//        }
//
//        if (r < timeoutProbability + connectionErrorProbability + http500Probability) {
//            log.warn("CHAOS: Injecting HTTP 500");
//            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
//        }
//    }
    // ---------------------------------------------------------------------
    // Retry fallback (called AFTER all retry attempts are exhausted)
    // ---------------------------------------------------------------------
    private Type retryFallbackMethod(String orderNumber, Exception e) {
        Timer.Sample activeTimer = activeTimers.remove(orderNumber);
        if (activeTimer != null) {
            orderMetrics.recordOrderProcessingDuration(activeTimer);
        }

        if (e instanceof CallNotPermittedException) {
            log.error(
                    "Circuit breaker OPEN for Address Service. orderNumber={}",
                    orderNumber
            );
            orderMetrics.incrementOrdersFailed("CIRCUIT_OPEN");
            return new Failure(
                    "Address service is unavailable - Circuit breaker is OPEN",
                    "CIRCUIT_OPEN",
                    true
            );
        }

        log.warn(
                "Retries exhausted for Address Service. orderNumber={}, reason={}",
                orderNumber,
                e.getClass().getSimpleName(),
                e
        );

        orderMetrics.incrementOrdersFailed("RETRY_EXHAUSTED");
        return new Failure(
                "Address service failed after retry attempts: " + e.getMessage(),
                "RETRY_EXHAUSTED",
                false
        );
    }

//    private Type circuitFallback(
//            String orderNumber,
//            CallNotPermittedException ex
//    ) {
//        log.error("CIRCUIT OPEN. orderNumber={}", orderNumber);
//        return new Failure("CIRCUIT_OPEN", "Service temporarily unavailable", true);
//    }


    private Type bulkheadFallback(
            String orderNumber,
            BulkheadFullException ex
    ) {
        Timer.Sample activeTimer = activeTimers.remove(orderNumber);
        if (activeTimer != null) {
            orderMetrics.recordOrderProcessingDuration(activeTimer);
        }
        log.warn("BULKHEAD FULL. orderNumber={}", orderNumber);
        orderMetrics.incrementOrdersFailed("BULKHEAD_FULL");
        return new Failure("Service overloaded", "BULKHEAD_FULL", true);
    }


    private Type rateLimitFallback(String orderNumber, RequestNotPermitted e) {
        Timer.Sample activeTimer = activeTimers.remove(orderNumber);
        if (activeTimer != null) {
            orderMetrics.recordOrderProcessingDuration(activeTimer);
        }
        log.error(
                ">>> RATE LIMITER FALLBACK <<< orderNumber={}, exception={}",
                orderNumber,
                e.getClass().getName(),
                e
        );

        orderMetrics.incrementOrdersFailed("RATE_LIMIT");
        return new Failure(
                "Too many requests. Please try again later.",
                "RATE_LIMIT",
                false
        );
    }
}