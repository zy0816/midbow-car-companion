package com.midbows.zkvision.signal;

import android.content.Context;

import com.midbows.zkvision.behavior.BehaviorEngine;
import com.midbows.zkvision.util.RobotLog;

/**
 * 信号源公共基类：统一运行态守卫 + 启停日志。
 *
 * <p>把所有信号源共用的「幂等 start/stop + 日志」模板收敛到此处，
 * 子类只实现真正的接入/释放（{@link #doStart()}/{@link #doStop()}）。
 * 广播型用 {@link BroadcastSignalSource}，Binder/系统属性型直接继承本类，避免重复样板。
 */
public abstract class AbstractSignalSource implements SignalSource {

    protected final Context context;
    protected final BehaviorEngine engine;
    private boolean running;

    protected AbstractSignalSource(Context context, BehaviorEngine engine) {
        this.context = context;
        this.engine = engine;
    }

    /** 日志 TAG。 */
    protected abstract String tag();

    /** 实际接入（注册监听/绑定服务/挂载采集等）。 */
    protected abstract void doStart();

    /** 实际释放。 */
    protected abstract void doStop();

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        RobotLog.d(tag(), "启动");
        doStart();
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        RobotLog.d(tag(), "停止");
        doStop();
    }

    protected boolean isRunning() {
        return running;
    }
}
