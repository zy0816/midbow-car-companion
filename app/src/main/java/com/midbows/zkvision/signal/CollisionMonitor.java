package com.midbows.zkvision.signal;

import android.content.Context;

import com.ecarx.xui.adaptapi.car.base.ICarFunction;
import com.ecarx.xui.adaptapi.car.vehicle.IADAS;
import com.midbows.zkvision.behavior.BehaviorEngine;

/**
 * 碰撞惊吓：跟随车机前向碰撞预警。
 *
 * <p>监听 {@link IADAS#SETTING_FUNC_FORWARD_COLLISION_WARN}，值变为
 * {@link ICarFunction#COMMON_VALUE_ON} 视为预警，触发受惊后仰（最高优先级）。
 * 按「灭→亮」跳变去抖。
 */
final class CollisionMonitor extends AbstractSignalSource {

    private static final String TAG = "CollisionMonitor";

    private Object token;
    private boolean warning;

    CollisionMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        warning = false;
        token = EcarxCarManager.getInstance().watchFunctionValue(
                context, IADAS.SETTING_FUNC_FORWARD_COLLISION_WARN, (id, zone, value) -> {
                    boolean warn = value == ICarFunction.COMMON_VALUE_ON;
                    if (warn && !warning) {
                        engine.onCollisionWarn();
                    }
                    warning = warn;
                });
    }

    @Override
    protected void doStop() {
        EcarxCarManager.getInstance().unwatch(token);
    }
}
