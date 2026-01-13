package com.edu.orderservice.chaos;

import com.edu.orderservice.service.impl.OrderServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

@Component
@ConfigurationProperties(prefix = "fault")
public class ChaosFaultInjector {

    private static final Logger log = LoggerFactory.getLogger(ChaosFaultInjector.class);

    private boolean enabled;
    private boolean timeout;
    private boolean connectionError;
    private boolean http500;
    private boolean latency;
    public long latencyMS;

    public void inject() {
        if (!enabled) {
            return;
        }

        if (latency && latencyMS > 0) {
            try {
                log.warn("Injecting latency {}ms", latencyMS);
                Thread.sleep(latencyMS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (timeout) {
            log.warn("CHAOS: Injecting TIMEOUT");
            throw new ResourceAccessException("Injected timeout fault");
        }

        if (connectionError) {
            log.warn("CHAOS: Injecting CONNECTION FAILURE");
            throw new ResourceAccessException("Injected connection error");
        }

        if (http500) {
            log.warn("CHAOS: Injecting HTTP 500");
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public boolean isLatency() {
        return latency;
    }

    public void setLatency(boolean latency) {
        this.latency = latency;
    }

    public long getLatencyMS() {
        return latencyMS;
    }

    public void setLatencyMS(long latencyMS) {
        this.latencyMS = latencyMS;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isTimeout() { return timeout; }
    public void setTimeout(boolean timeout) { this.timeout = timeout; }

    public boolean isConnectionError() { return connectionError; }
    public void setConnectionError(boolean connectionError) { this.connectionError = connectionError; }

    public boolean isHttp500() { return http500; }
    public void setHttp500(boolean http500) { this.http500 = http500; }
}