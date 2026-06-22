package com.midbows.zkvision.signal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.midbows.zkvision.behavior.BehaviorEngine;
import com.midbows.zkvision.util.RobotLog;

/**
 * 基于 BroadcastReceiver 的信号源公共基类。
 *
 * <p>在 {@link AbstractSignalSource} 的运行态守卫之上，统一处理注册/注销，并把 Intent
 * 全部 Extra 打到日志（辅助实车勘探）。子类只声明要监听的 action 与处理逻辑。
 */
public abstract class BroadcastSignalSource extends AbstractSignalSource {

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            dumpExtras(intent);
            onAction(intent.getAction(), intent);
        }
    };

    protected BroadcastSignalSource(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    /** 声明要监听的 action。 */
    protected abstract void fillFilter(IntentFilter filter);

    /** 处理收到的广播。 */
    protected abstract void onAction(String action, Intent intent);

    /** 额外初始化钩子（如 AudioFocus、Visualizer、Car API）。 */
    protected void onStart() {
    }

    /** 额外释放钩子。 */
    protected void onStop() {
    }

    @Override
    protected final void doStart() {
        IntentFilter filter = new IntentFilter();
        fillFilter(filter);
        context.registerReceiver(receiver, filter);
        onStart();
    }

    @Override
    protected final void doStop() {
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // 未注册时忽略
        }
        onStop();
    }

    private void dumpExtras(Intent intent) {
        RobotLog.d(tag(), "广播 " + intent.getAction());
        Bundle b = intent.getExtras();
        if (b != null) {
            for (String key : b.keySet()) {
                RobotLog.d(tag(), "  " + key + " = " + b.get(key));
            }
        }
    }
}
