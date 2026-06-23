package com.midbows.zkvision.signal;

import android.content.Context;

import com.ecarx.xui.adaptapi.car.sensor.ISensor;
import com.midbows.zkvision.behavior.BehaviorEngine;
import com.midbows.zkvision.data.SettingsManager;

/**
 * 冷热反应：跟随车内温度传感器（非空调设定）。
 *
 * <p>监听 {@link ISensor#SENSOR_TYPE_TEMPERATURE_INDOOR}，经 {@link CabinTempReaction} 判定
 * 冷/正常/热。仅在档位变化时上报，冷时哆嗦、热时灼红。
 */
final class CabinTempMonitor extends AbstractSignalSource {

    private static final String TAG = "CabinTempMonitor";

    private Object token;
    private CabinTempReaction last;

    CabinTempMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        last = null;
        token = EcarxCarManager.getInstance().watchSensorValue(
                context, ISensor.SENSOR_TYPE_TEMPERATURE_INDOOR, (type, value) -> {
                    // 阈值实时取自用户设置，未设置回退出厂值；改完即生效、无需重连。
                    SettingsManager s = SettingsManager.getInstance(context);
                    CabinTempReaction r = CabinTempReaction.classify(
                            value,
                            s.getFloat(SettingsManager.KEY_COLD_BELOW, CarThresholds.COLD_BELOW_C),
                            s.getFloat(SettingsManager.KEY_HOT_ABOVE, CarThresholds.HOT_ABOVE_C));
                    if (r == last) {
                        return;
                    }
                    last = r;
                    switch (r) {
                        case COLD:
                            engine.onCabinCold();
                            break;
                        case HOT:
                            engine.onCabinHot();
                            break;
                        default:
                            engine.onCabinNormal();
                            break;
                    }
                });
    }

    @Override
    protected void doStop() {
        EcarxCarManager.getInstance().unwatch(token);
    }
}
