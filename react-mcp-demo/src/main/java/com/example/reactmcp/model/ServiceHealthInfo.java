package com.example.reactmcp.model;

/**
 * 服务健康信息
 */
public class ServiceHealthInfo {
    private String service;
    private int instances;
    private int healthy;
    private int unhealthy;

    public ServiceHealthInfo(String service, int instances, int healthy, int unhealthy) {
        this.service = service;
        this.instances = instances;
        this.healthy = healthy;
        this.unhealthy = unhealthy;
    }

    // Getters
    public String getService() {
        return service;
    }

    public int getInstances() {
        return instances;
    }

    public int getHealthy() {
        return healthy;
    }

    public int getUnhealthy() {
        return unhealthy;
    }

    @Override
    public String toString() {
        return String.format("{\"service\":\"%s\",\"instances\":%d,\"healthy\":%d,\"unhealthy\":%d}",
                service, instances, healthy, unhealthy);
    }
}
