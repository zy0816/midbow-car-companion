package com.midbows.zkvision.signal;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import com.ecarx.xui.adaptapi.car.vehicle.IAmbienceLight;
import com.midbows.zkvision.behavior.BehaviorEngine;
import com.midbows.zkvision.util.RobotLog;

/**
 * 氛围灯同步：机器人底部氛围灯跟随车机氛围灯颜色。
 *
 * <p>实车标定结论：真实当前色在 {@link IAmbienceLight#SETTING_FUNC_AMBIENCE_LIGHT_MAINZONES_COLOR_SET}
 * (0x2a040100)，取值为 Android 打包色 {@code 0xAARRGGBB}；旧的 {@code SETTING_FUNC_MOOD_LIGHT_COLOR}
 * (0x2a060200) 在本车永远返回 0xFF 无效，故弃用。该值是「设置项」，变化不一定经回调推送，
 * 因此以<b>定时读取</b>为主（{@code getFunctionValue}），值变化即同步，同时保留回调（若车机确实推送则即时响应）。
 * 经 {@link AmbientColorMap} 解包为 RGB 后交 {@link BehaviorEngine} 作为静息底色。
 */
final class AmbientLightMonitor extends AbstractSignalSource {

    private static final String TAG = "AmbientLightMonitor";

    /** 轮询周期：氛围灯是设置项、变化不频繁，3s 足够跟手又不占资源。 */
    private static final long POLL_INTERVAL_MS = 3000;

    /**
     * 轮询专用后台线程。<b>关键</b>：{@code readFunction} 是对车控服务的同步 binder 调用，
     * 一旦车机服务繁忙就会阻塞调用线程；若放主线程轮询会直接卡死整个 UI（v4.4 的死机根因）。
     * 必须在后台线程读，绝不可在主线程。
     */
    private HandlerThread pollThread;
    private Handler handler;
    private Object token;
    /** 上次已同步的车机原始值，去重：只有值真正变化才下发，避免重复刷氛围灯。轮询/回调两线程访问故 volatile。 */
    private volatile int lastValue = Integer.MIN_VALUE;

    AmbientLightMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        // 回调通道：若车机确实推送设置项变化则即时响应。
        token = EcarxCarManager.getInstance().watchFunctionValue(
                context, IAmbienceLight.SETTING_FUNC_AMBIENCE_LIGHT_MAINZONES_COLOR_SET,
                (id, zone, value) -> apply(value, "回调"));
        // 轮询通道（主力）：设置项变化通常不经回调，靠定时读当前值兜底同步。在后台线程跑，不卡 UI。
        pollThread = new HandlerThread("AmbientPoll");
        pollThread.start();
        handler = new Handler(pollThread.getLooper());
        handler.post(pollRunnable);
    }

    @Override
    protected void doStop() {
        if (handler != null) {
            handler.removeCallbacks(pollRunnable);
            handler = null;
        }
        if (pollThread != null) {
            pollThread.quitSafely();
            pollThread = null;
        }
        EcarxCarManager.getInstance().unwatch(token);
        lastValue = Integer.MIN_VALUE;
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            int value = EcarxCarManager.getInstance()
                    .readFunction(IAmbienceLight.SETTING_FUNC_AMBIENCE_LIGHT_MAINZONES_COLOR_SET);
            if (value != Integer.MIN_VALUE) {
                apply(value, "轮询");
            }
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    /** 把车机氛围灯原始值映射后下发；与上次值相同则跳过，避免重复刷。 */
    private void apply(int value, String src) {
        if (value == lastValue) {
            return;
        }
        lastValue = value;
        RobotLog.d(TAG, "氛围灯值[" + src + "]=0x" + Integer.toHexString(value) + " (" + value + ")");
        int[] rgb = AmbientColorMap.toRgb(value);
        if (rgb == null) {
            engine.onAmbientOff();
        } else {
            engine.onAmbientColor(rgb[0], rgb[1], rgb[2]);
        }
    }
}
