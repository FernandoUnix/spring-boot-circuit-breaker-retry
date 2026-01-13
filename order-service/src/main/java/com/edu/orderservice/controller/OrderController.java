package com.edu.orderservice.controller;

import com.edu.orderservice.model.Failure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu.orderservice.model.Type;
import com.edu.orderservice.service.OrderService;

@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping
    public ResponseEntity<?> getByOrderNumber(@RequestParam("orderNumber") String orderNumber) {

        System.err.println(">>> CONTROLLER HIT <<< orderNumber=" + orderNumber);

        Type result = orderService.getOrderByPostCode(orderNumber);

        if (result instanceof Failure failure) {

            return switch (failure.getReason()) {
                case "CIRCUIT_OPEN" ->
                        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(failure);

                case "BULKHEAD_FULL" ->
                        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(failure);

                case "RATE_LIMIT" ->
                        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(failure);

                case "RETRY_EXHAUSTED" ->
                        ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(failure);

                default ->
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(failure);
            };
        }

        return ResponseEntity.ok(result);
    }
}
