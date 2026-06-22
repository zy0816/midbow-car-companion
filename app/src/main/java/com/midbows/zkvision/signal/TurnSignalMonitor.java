package com.midbows.zkvision.signal;

import android.content.Context;

import com.ecarx.xui.adaptapi.car.base.ICarFunction;
import com.ecarx.xui.adaptapi.car.vehicle.IBcm;
import com.midbows.zkvision.behavior.BehaviorEngine;

/**
 * 转向灯探头：打转向灯时朝该侧轻探。
 *
 * <p>监听 {@link IBcm#BCM_FUNC_LIGHT_LEFT_TRUN_SIGNAL}/{@code RIGHT}，值变为
 * {@link ICarFunction#COMMON_VALUE_ON} 视为该侧转向灯亮。按「灭→亮」跳变去抖。
 */
final class TurnSignalMonitor extends AbstractSignalSource {

    private static final String TAG = "TurnSignalMonitor";

    private Object leftToken;
    private Object rightToken;
    private boolean leftOn;
    private boolean rightOn;

    TurnSignalMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        leftOn = false;
        rightOn = false;
        EcarxCarManager mgr = EcarxCarManager.getInstance();
        leftToken = mgr.watchFunctionValue(
                context, IBcm.BCM_FUNC_LIGHT_LEFT_TRUN_SIGNAL,
                (id, zone, value) -> handle(true, value == ICarFunction.COMMON_VALUE_ON));
        rightToken = mgr.watchFunctionValue(
                context, IBcm.BCM_FUNC_LIGHT_RIGHT_TRUN_SIGNAL,
                (id, zone, value) -> handle(false, value == ICarFunction.COMMON_VALUE_ON));
    }

    private void handle(boolean left, boolean on) {
        if (left) {
            if (on && !leftOn) {
                engine.onTurnSignal(true);
            }
            leftOn = on;
        } else {
            if (on && !rightOn) {
                engine.onTurnSignal(false);
            }
            rightOn = on;
        }
    }

    @Override
    protected void doStop() {
        EcarxCarManager mgr = EcarxCarManager.getInstance();
        mgr.unwatch(leftToken);
        mgr.unwatch(rightToken);
    }
}
