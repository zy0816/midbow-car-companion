package com.midbows.zkvision.signal;

/**
 * 信号源统一生命周期接口。每个车机信号监听器实现本接口，
 * 由 service 层统一 start/stop，便于按开关增减来源（可扩展）。
 */
public interface SignalSource {
    void start();

    void stop();
}
