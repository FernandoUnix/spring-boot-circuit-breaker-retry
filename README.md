# Circuit Breaker Pattern with Retry in Spring Boot

A comprehensive example demonstrating how to implement **Circuit Breaker** and **Retry** patterns together in Spring Boot using Resilience4j. This project shows best practices for building resilient microservices that can gracefully handle failures in external dependencies.

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Key Concepts](#key-concepts)
- [Configuration](#configuration)
- [Getting Started](#getting-started)
- [Testing Scenarios](#testing-scenarios)
- [Monitoring](#monitoring)
- [Best Practices](#best-practices)
- [Understanding the Code](#understanding-the-code)
- [References](#references)
- [Acknowledgments](#acknowledgments)

## 🎯 Overview

This project consists of two Spring Boot microservices:

1. **Order Service** (`order-service`) - Port `1010`
   - Manages orders and retrieves shipping addresses
   - Implements Circuit Breaker + Retry patterns
   - Calls Address Service to fetch address details

2. **Address Service** (`address-service`) - Port `9090`
   - Provides address information by postal code
   - Simulates an external dependency that can fail

### Why This Pattern?

In distributed systems, external services can fail due to:
- Network timeouts
- Service overload
- Temporary unavailability
- Infrastructure issues

**Circuit Breaker** prevents cascading failures by "opening" when a service is down, while **Retry** handles transient failures automatically. Together, they provide robust resilience.

## 🏗️ Architecture

```
┌─────────────────┐
│   Client/User   │
└────────┬────────┘
         │
         │ HTTP Request
         ▼
┌─────────────────────────────────────┐
│      Order Service (Port 1010)     │
│  ┌───────────────────────────────┐ │
│  │  @Retry (OUTER)               │ │
│  │    └─ @CircuitBreaker (INNER) │ │
│  │         └─ HTTP Call          │ │
│  └───────────────────────────────┘ │
└────────┬────────────────────────────┘
         │
         │ HTTP Request
         ▼
┌─────────────────────────────────────┐
│   Address Service (Port 9090)     │
│   (External Dependency)           │
└────────────────────────────────────┘
```

## 🔑 Key Concepts

### Circuit Breaker States

The Circuit Breaker has three states:

1. **CLOSED** ✅
   - Normal operation
   - All calls are allowed
   - Failures are recorded and counted
   - Transitions to OPEN if failure rate exceeds threshold

2. **OPEN** 🔴
   - Service is considered down
   - All calls are **immediately rejected** (fail-fast)
   - No HTTP calls are made (saves resources)
   - Throws `CallNotPermittedException`
   - Automatically transitions to HALF_OPEN after wait duration

3. **HALF_OPEN** 🟡
   - Testing if service has recovered
   - Limited number of test calls allowed
   - If test calls succeed → transitions to CLOSED
   - If test calls fail → transitions back to OPEN

### Retry Pattern

- Automatically retries failed operations
- Handles **transient failures** (network glitches, timeouts)
- Uses exponential backoff to avoid overwhelming the service
- Only retries specific exception types (configurable)

### Why Combine Them?

```
Request Flow:
1. Retry (OUTER) intercepts first
   └─> 2. CircuitBreaker (INNER) checks state
       └─> 3. If CLOSED: Execute method
       └─> 4. If OPEN: Throw CallNotPermittedException immediately
   └─> 5. If exception occurs: Retry attempts again (up to max-attempts)
   └─> 6. After retries exhausted: Fallback is called
```

**Benefits:**
- Retry handles transient failures before Circuit Breaker sees them
- Circuit Breaker only records **final** outcomes (after retries)
- Prevents Circuit Breaker from opening too aggressively
- When Circuit Breaker is OPEN, retries are skipped (fail-fast)

## ⚙️ Configuration

### Retry Configuration

```yaml
resilience4j:
  retry:
    instances:
      order-service:
        max-attempts: 2                    # 1 initial + 1 retry
        wait-duration: 1s                  # Base wait time
        enable-exponential-backoff: true   # Exponential backoff enabled
        exponential-backoff-multiplier: 2  # Wait time doubles each retry
        exponential-max-wait-duration: 5s  # Maximum wait cap
        
        # Only retry on technical/transient failures
        retry-exceptions:
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
          - java.net.ConnectException
          - java.util.concurrent.TimeoutException
        
        # Don't retry on business logic errors
        ignore-exceptions:
          - java.lang.IllegalArgumentException
```

**Retry Behavior:**
- **Attempt 1**: Immediate execution
- **Attempt 2** (if attempt 1 fails): Wait 1s, then retry
- **Attempt 3** (if attempt 2 fails): Wait 2s, then retry (if max-attempts > 2)
- Maximum wait between retries: 5 seconds

### Circuit Breaker Configuration

```yaml
resilience4j:
  circuitbreaker:
    instances:
      order-service:
        sliding-window-type: TIME_BASED    # Evaluate last 30 seconds
        sliding-window-size: 30            # 30-second window
        failure-rate-threshold: 50         # Open if 50%+ failures
        minimum-number-of-calls: 5          # Need 5 calls before evaluating
        wait-duration-in-open-state: 5s    # Stay OPEN for 5 seconds
        permitted-number-of-calls-in-half-open-state: 3  # 3 test calls
        automatic-transition-from-open-to-half-open-enabled: true
        
        # Only record technical failures
        record-exceptions:
          - java.net.ConnectException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
        
        # Ignore business/client errors
        ignore-exceptions:
          - java.lang.IllegalArgumentException
          - org.springframework.web.client.HttpClientErrorException
```

**Circuit Breaker Behavior:**
- Monitors calls in the **last 30 seconds** (TIME_BASED window)
- Requires **minimum 5 calls** before evaluating failure rate
- Opens circuit if **50% or more** calls fail
- Stays OPEN for **5 seconds**, then transitions to HALF_OPEN
- Allows **3 test calls** in HALF_OPEN state

## 🚀 Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- IDE (IntelliJ IDEA, Eclipse, or VS Code)

### Running the Application

#### 1. Start Address Service

```bash
cd address-service
./mvnw spring-boot:run
# Or on Windows:
mvnw.cmd spring-boot:run
```

Address Service will start on **http://localhost:9090**

#### 2. Start Order Service

```bash
cd order-service
./mvnw spring-boot:run
# Or on Windows:
mvnw.cmd spring-boot:run
```

Order Service will start on **http://localhost:1010**

### Testing the API

#### Get Order with Address

```bash
curl "http://localhost:1010/orders?orderNumber=0c70c0c2"
```

**Expected Response (Success):**
```json
{
  "orderNumber": "0c70c0c2",
  "postalCode": "1000001",
  "shippingCity": "Tokyo",
  "shippingState": "Tokyo",
  "orderDate": "2024-01-15"
}
```

**Expected Response (Failure):**
```json
{
  "msg": "Address service failed after retry attempts: Connection refused: connect"
}
```

## 🧪 Testing Scenarios

### Scenario 1: Normal Operation (Circuit CLOSED)

1. Both services running
2. Make a request: `GET /orders?orderNumber=0c70c0c2`
3. **Result**: Success, address retrieved

**What happens:**
- Circuit Breaker: CLOSED ✅
- Retry: Not needed (first attempt succeeds)
- Response: Order with address details

### Scenario 2: Transient Failure (Retry Handles It)

1. Stop Address Service temporarily
2. Make a request: `GET /orders?orderNumber=0c70c0c2`
3. Start Address Service within 1-2 seconds
4. **Result**: Retry succeeds on second attempt

**What happens:**
- Circuit Breaker: CLOSED ✅
- Retry: First attempt fails → waits 1s → retries → succeeds
- Response: Order with address details (after retry)

### Scenario 3: Service Down (Circuit Opens)

1. Stop Address Service completely
2. Make **5+ requests** rapidly (to trigger circuit opening)
3. **Result**: Circuit Breaker opens after 50% failure rate

**What happens:**
- Circuit Breaker: CLOSED → OPEN 🔴
- After 5+ failures: Circuit opens
- Subsequent requests: Fail-fast with `CallNotPermittedException`
- No HTTP calls made (saves resources)
- Response: "Circuit breaker is OPEN"

### Scenario 4: Service Recovery (Circuit Closes)

1. Circuit is OPEN
2. Wait 5 seconds (wait-duration-in-open-state)
3. Start Address Service
4. Circuit transitions to HALF_OPEN
5. Make a request
6. **Result**: Test call succeeds, circuit closes

**What happens:**
- Circuit Breaker: OPEN → HALF_OPEN → CLOSED ✅
- Test calls in HALF_OPEN state
- If successful: Circuit closes
- Response: Order with address details

## 📊 Monitoring

### Health Check Endpoint

Check Circuit Breaker status:

```bash
curl http://localhost:1010/actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "order-service": {
          "status": "UP",
          "details": {
            "failureRate": "0.0%",
            "failureRateThreshold": "50.0%",
            "state": "CLOSED",
            "bufferedCalls": 10,
            "failedCalls": 0,
            "notPermittedCalls": 0
          }
        }
      }
    }
  }
}
```

### Key Metrics

- **state**: Current circuit state (CLOSED, OPEN, HALF_OPEN)
- **failureRate**: Percentage of failed calls
- **bufferedCalls**: Total calls in the sliding window
- **failedCalls**: Number of failed calls
- **notPermittedCalls**: Calls rejected when circuit was OPEN

### Logging

The application uses SLF4J logging. Check logs to see:

- Retry attempts: `Starting getOrderByPostCode. orderNumber=...`
- Success: `Successfully retrieved address...`
- Circuit Breaker OPEN: `Circuit breaker OPEN for Address Service...`
- Retry exhausted: `Retries exhausted for Address Service...`

## 💡 Best Practices

### 1. Annotation Order Matters

```java
@Retry(name = SERVICE_NAME, fallbackMethod = "retryFallbackMethod")
@CircuitBreaker(name = SERVICE_NAME)
public Type getOrderByPostCode(String orderNumber) {
    // Method implementation
}
```

**Why this order?**
- `@Retry` (OUTER) handles retries first
- `@CircuitBreaker` (INNER) records final outcomes
- Prevents circuit from opening too aggressively

### 2. Single Fallback Strategy

Use **only** Retry fallback, not Circuit Breaker fallback:

```java
private Type retryFallbackMethod(String orderNumber, Exception e) {
    if (e instanceof CallNotPermittedException) {
        // Circuit is OPEN
        return new Failure("Circuit breaker is OPEN");
    }
    // Retries exhausted
    return new Failure("Service failed after retries");
}
```

### 3. Exception Classification

**Retry on:**
- Network errors (`ConnectException`, `ResourceAccessException`)
- Timeouts (`TimeoutException`)
- Server errors (`HttpServerErrorException`)

**Don't retry on:**
- Client errors (`HttpClientErrorException` - 4xx)
- Business logic errors (`IllegalArgumentException`)

### 4. Configuration Tuning

**For High-Volume Services:**
- Increase `minimum-number-of-calls` (e.g., 10-20)
- Use TIME_BASED sliding window
- Lower `failure-rate-threshold` (e.g., 30-40%)

**For Low-Volume Services:**
- Use COUNT_BASED sliding window
- Lower `minimum-number-of-calls` (e.g., 3-5)
- Higher `failure-rate-threshold` (e.g., 60-70%)

### 5. Monitoring and Alerting

- Monitor Circuit Breaker state transitions
- Alert when circuit stays OPEN for extended periods
- Track retry success rates
- Monitor `notPermittedCalls` (indicates circuit is blocking requests)

## 🔍 Understanding the Code

### Key Implementation Details

#### 1. Service Method

```java
@Retry(name = SERVICE_NAME, fallbackMethod = "retryFallbackMethod")
@CircuitBreaker(name = SERVICE_NAME)
public Type getOrderByPostCode(String orderNumber) {
    // 1. Fetch order from database
    Order order = orderRepository.findByOrderNumber(orderNumber)
        .orElseThrow(() -> new RuntimeException("Order Not Found"));
    
    // 2. Call external Address Service
    ResponseEntity<AddressDTO> response = restTemplate.exchange(
        ADDRESS_SERVICE_URL + order.getPostalCode(),
        HttpMethod.GET,
        entity,
        AddressDTO.class
    );
    
    // 3. Map response and return
    return order;
}
```

**Important:** No try-catch blocks! Exceptions must propagate naturally for Circuit Breaker and Retry to work correctly.

#### 2. Fallback Method

```java
private Type retryFallbackMethod(String orderNumber, Exception e) {
    // Check if circuit is OPEN
    if (e instanceof CallNotPermittedException) {
        return new Failure("Circuit breaker is OPEN");
    }
    
    // Retries exhausted
    return new Failure("Service failed after retries: " + e.getMessage());
}
```

**Key Points:**
- Only way to detect OPEN circuit: `CallNotPermittedException`
- Fallback is called after ALL retry attempts are exhausted
- Distinguish between circuit OPEN vs. service failure

## 📚 References

- **Resilience4j Documentation**: https://resilience4j.readme.io/
- **Circuit Breaker Pattern**: https://martinfowler.com/bliki/CircuitBreaker.html
- **Spring Cloud Circuit Breaker**: https://spring.io/projects/spring-cloud-circuitbreaker

## 🙏 Acknowledgments

This example was based on and inspired by the excellent article:
- **Circuit Breaker Pattern in Spring Boot** by [@truongbui95](https://medium.com/@truongbui95): https://medium.com/@truongbui95/circuit-breaker-pattern-in-spring-boot-d2d258b75042

The original article provided the foundation for this implementation, which has been extended with:
- Retry pattern integration
- Enhanced configuration examples

## 🤝 Contributing

Feel free to submit issues, fork the repository, and create pull requests for any improvements.

## 📝 License

This project is for educational purposes.

---

**Built with ❤️ using Spring Boot and Resilience4j**
