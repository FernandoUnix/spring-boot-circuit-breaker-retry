package com.edu.orderservice.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.edu.orderservice.dto.AddressDTO;
import com.edu.orderservice.model.Failure;
import com.edu.orderservice.model.Order;
import com.edu.orderservice.model.Type;
import com.edu.orderservice.repository.OrderRepository;
import com.edu.orderservice.service.OrderService;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Autowired

    private OrderRepository orderRepository;

    @Autowired
    private RestTemplate restTemplate;

    private static final String SERVICE_NAME = "order-service";
    private static final String ADDRESS_SERVICE_URL = "http://localhost:9090/addresses/";

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
    @Retry(name = SERVICE_NAME, fallbackMethod = "retryFallbackMethod")
    @CircuitBreaker(name = SERVICE_NAME)
    public Type getOrderByPostCode(String orderNumber) {

        // -----------------------------------------------------------------
        // Method entry log (important for tracing retries)
        // -----------------------------------------------------------------
        log.info("Starting getOrderByPostCode. orderNumber={}", orderNumber);

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> {
                    log.error("Order not found. orderNumber={}", orderNumber);
                    return new RuntimeException("Order Not Found: " + orderNumber);
                });

        String postalCode = order.getPostalCode();

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

        return order;
    }

    // ---------------------------------------------------------------------
    // Retry fallback (called AFTER all retry attempts are exhausted)
    // ---------------------------------------------------------------------
    private Type retryFallbackMethod(String orderNumber, Exception e) {

        // -----------------------------------------------------------------
        // Circuit Breaker OPEN (fail-fast scenario)
        // -----------------------------------------------------------------
        if (e instanceof CallNotPermittedException) {
            log.error(
                    "Circuit breaker OPEN for Address Service. orderNumber={}",
                    orderNumber
            );
            return new Failure(
                    "Address service is unavailable - Circuit breaker is OPEN"
            );
        }

        // -----------------------------------------------------------------
        // Retry exhausted (external service instability)
        // -----------------------------------------------------------------
        log.warn(
                "Retries exhausted for Address Service. orderNumber={}, reason={}",
                orderNumber,
                e.getClass().getSimpleName(),
                e
        );

        return new Failure(
                "Address service failed after retry attempts: " + e.getMessage()
        );
    }
}