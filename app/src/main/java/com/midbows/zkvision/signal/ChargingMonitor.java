package com.midbows.zkvision.signal;

import android.content.Context;

import com.ecarx.xui.adaptapi.car.base.ICarFunction;
import com.ecarx.xui.adaptapi.car.hev.ICharging;
import com.midbows.zkvision.behavior.BehaviorEngine;

/**
 * 充电睡觉：充电时进入睡眠，充满即醒来庆祝。
 *
 * <p>监听 {@link ICharging#CHARGE_FUNC_CHARGING}（充电中）与 {@link ICharging#CHARGE_FUNC_CHARGING_SOC}
 * （电量百分比）。开始充电→睡眠；SOC 达到 {@link CarThresholds#FULL_SOC_PCT}→醒来庆祝（仅触发一次）。
 */
final class ChargingMonitor extends AbstractSignalSource {

    private static final String TAG = "ChargingMonitor";

    private Object chargingToken;
    private Object socToken;
    private boolean charging;
    private boolean fullFired;

    ChargingMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        charging = false;
        fullFired = false;
        EcarxCarManager mgr = EcarxCarManager.getInstance();
        chargingToken = mgr.watchFunctionValue(
                context, ICharging.CHARGE_FUNC_CHARGING, (id, zone, value) -> {
                    boolean on = value == ICarFunction.COMMON_VALUE_ON;
                    if (on == charging) {
                        return;
                    }
                    charging = on;
                    engine.onCharging(on);
                });
        socToken = mgr.watchFunctionValue(
                context, ICharging.CHARGE_FUNC_CHARGING_SOC, (id, zone, value) -> {
                    if (value >= CarThresholds.FULL_SOC_PCT) {
                        if (charging && !fullFired) {
                            fullFired = true;
                            engine.onChargeFull();
                        }
                    } else {
                        fullFired = false;
                    }
                });
    }

    @Override
    protected void doStop() {
        EcarxCarManager mgr = EcarxCarManager.getInstance();
        mgr.unwatch(chargingToken);
        mgr.unwatch(socToken);
    }
}
