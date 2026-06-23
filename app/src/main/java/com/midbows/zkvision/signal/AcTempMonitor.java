package com.midbows.zkvision.signal;

import android.content.Context;

import com.ecarx.xui.adaptapi.car.hvac.IHvac;
import com.midbows.zkvision.behavior.BehaviorEngine;

/**
 * 空调互动：监听空调设定温度（{@link IHvac#HVAC_FUNC_TEMP}，浮点自定义值，如 22.5℃）。
 *
 * <p>设定温度走 ecarx 车身功能的「自定义浮点值」回调。比上一次调高→点头+开心表情，调低→哆嗦+装酷表情，
 * 给「人调空调，机器人有反应」的互动感。首次回调只记基线不触发；小于 {@link #EPSILON} 的抖动忽略。
 */
final class AcTempMonitor extends AbstractSignalSource {

    private static final String TAG = "AcTempMonitor";
    /** 温度抖动死区：仅 ≥ 此变化才算一次「调高/调低」。 */
    private static final float EPSILON = 0.4f;

    private Object token;
    private float last = Float.NaN;

    AcTempMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        last = Float.NaN;
        token = EcarxCarManager.getInstance().watchFunctionFloatValue(
                context, IHvac.HVAC_FUNC_TEMP, (functionId, zone, value) -> {
                    if (Float.isNaN(value)) {
                        return;
                    }
                    if (Float.isNaN(last)) {
                        last = value;
                        return;
                    }
                    float delta = value - last;
                    if (delta >= EPSILON) {
                        last = value;
                        engine.onAcWarmer();
                    } else if (delta <= -EPSILON) {
                        last = value;
                        engine.onAcCooler();
                    }
                });
    }

    @Override
    protected void doStop() {
        EcarxCarManager.getInstance().unwatch(token);
    }
}
