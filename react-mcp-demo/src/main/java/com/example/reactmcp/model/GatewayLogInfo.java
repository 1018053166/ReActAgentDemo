package com.example.reactmcp.model;

/**
 * 网关日志信息
 */
public class GatewayLogInfo {
    private String service;
    private String timeRange;
    private boolean circuitOpen;
    private String rateLimit;
    private int errors;

    public GatewayLogInfo(String service, String timeRange, boolean circuitOpen, String rateLimit, int errors) {
        this.service = service;
        this.timeRange = timeRange;
        this.circuitOpen = circuitOpen;
        this.rateLimit = rateLimit;
        this.errors = errors;
    }

    @Override
    public String toString() {
        return String.format("service=%s, timeRange=%s, circuitOpen=%s, rateLimit=%s, errors=%d",
                service, timeRange, circuitOpen, rateLimit, errors);
    }
}
