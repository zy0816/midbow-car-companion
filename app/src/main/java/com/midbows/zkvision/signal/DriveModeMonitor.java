package com.midbows.zkvision.signal;

import android.content.Context;

import com.ecarx.xui.adaptapi.car.vehicle.IDriveMode;
import com.midbows.zkvision.behavior.BehaviorEngine;

/**
 * 驾驶模式人格：底色与节奏跟随车机驾驶模式。
 *
 * <p>监听 {@link IDriveMode#DM_FUNC_DRIVE_MODE_SELECT}，经 {@link DrivePersonality} 映射为
 * 人格（颜色 + 是否活泼）。仅在人格变化时上报。
 */
final class DriveModeMonitor extends AbstractSignalSource {

    private static final String TAG = "DriveModeMonitor";

    private Object token;
    private DrivePersonality last;

    DriveModeMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        last = null;
        token = EcarxCarManager.getInstance().watchFunctionValue(
                context, IDriveMode.DM_FUNC_DRIVE_MODE_SELECT, (id, zone, value) -> {
                    DrivePersonality p = DrivePersonality.of(value);
                    if (p == last) {
                        return;
                    }
                    last = p;
                    engine.onDrivePersonality(p.r, p.g, p.b, p.brightness, p.energetic);
                });
    }

    @Override
    protected void doStop() {
        EcarxCarManager.getInstance().unwatch(token);
    }
}
