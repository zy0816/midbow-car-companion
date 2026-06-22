package com.midbows.zkvision.signal;

import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;

import com.midbows.zkvision.behavior.BehaviorEngine;

/**
 * 昼夜犯困：夜间变暗变慢、白天明亮。
 *
 * <p>本车 ecarx 的 {@code SENSOR_TYPE_DAY_NIGHT} 传感器实测 notavailable，
 * 故改用车机系统的深色/浅色模式（{@link Configuration#UI_MODE_NIGHT_MASK}）判断昼夜——
 * 车机自动深浅切换即对应系统夜间模式。通过 {@link ComponentCallbacks#onConfigurationChanged}
 * 监听切换，启动时先推一次当前状态。仅在昼夜变化时上报。
 */
final class DayNightMonitor extends AbstractSignalSource {

    private static final String TAG = "DayNightMonitor";

    private ComponentCallbacks callback;
    private Boolean lastNight;

    DayNightMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        lastNight = null;
        emit(context.getResources().getConfiguration());
        callback = new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                emit(newConfig);
            }

            @Override
            public void onLowMemory() {
            }
        };
        context.registerComponentCallbacks(callback);
    }

    private void emit(Configuration cfg) {
        boolean night = (cfg.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (Boolean.valueOf(night).equals(lastNight)) {
            return;
        }
        lastNight = night;
        engine.onDayNight(night);
    }

    @Override
    protected void doStop() {
        if (callback != null) {
            context.unregisterComponentCallbacks(callback);
            callback = null;
        }
    }
}
