package com.midbows.zkvision.signal;

import android.content.Context;

import com.ecarx.xui.adaptapi.car.sensor.ITireSensor;
import com.midbows.zkvision.behavior.BehaviorEngine;

/**
 * 胎压报警：跟随车机 TPMS 系统态。
 *
 * <p>监听 {@link ITireSensor#TIRE_TPMS_SYS_STATES}，经 {@link TireAlarm} 判定是否告警。
 * 仅在「无告警→告警」跳变时摇头示警。
 */
final class TireMonitor extends AbstractSignalSource {

    private static final String TAG = "TireMonitor";

    private Object token;
    private boolean warning;

    TireMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        warning = false;
        token = EcarxCarManager.getInstance().watchSensorEvent(
                context, ITireSensor.TIRE_TPMS_SYS_STATES, (type, event) -> {
                    boolean warn = TireAlarm.isWarning(event);
                    if (warn && !warning) {
                        engine.onTireWarning();
                    }
                    warning = warn;
                });
    }

    @Override
    protected void doStop() {
        EcarxCarManager.getInstance().unwatch(token);
    }
}
