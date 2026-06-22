package com.midbows.zkvision.signal;

import java.util.Arrays;
import java.util.List;

/**
 * 把多个信号源聚合为一个：start/stop 扇出到全部子源。
 *
 * <p>用于把「车辆状态联动」的多个 ecarx 监听器收束到单一开关下统一启停，
 * 避免每个联动各开一个设置项。
 */
final class CompositeSignalSource implements SignalSource {

    private final List<SignalSource> children;

    CompositeSignalSource(SignalSource... children) {
        this.children = Arrays.asList(children);
    }

    @Override
    public void start() {
        for (SignalSource s : children) {
            s.start();
        }
    }

    @Override
    public void stop() {
        for (SignalSource s : children) {
            s.stop();
        }
    }
}
