package com.edu.orderservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Failure implements Type {
    private final String msg;
    private final String reason;
    private final boolean retryable;
}
