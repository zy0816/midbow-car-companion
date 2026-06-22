package com.midbows.zkvision.signal;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import com.ecarx.xui.adaptapi.car.sensor.ISensor;
import com.midbows.zkvision.behavior.BehaviorEngine;
import com.midbows.zkvision.util.RobotLog;

/**
 * 急加速/急刹反应：监听纵向加速度传感量。
 *
 * <p>监听 {@link ISensor#SENSOR_TYPE_SPEED_LON_ACCELERATION}，经 {@link AccelReaction}
 * 判定后触发后仰（急加速）/前倾（急刹）。仅在状态跳变时上报，避免连续抖动重复触发。
 *
 * <p><b>v5.4 修复「加速无效」</b>：纵向加速度多为<b>只读传感量</b>，注册监听后车机
 * <b>不主动推送</b> {@code onSensorValueChanged}（{@link ISensor#getSensorLatestValue} 能读到值，
 * 但回调不来），导致原先纯回调实现永不触发。改为<b>回调 + 后台定时轮询</b>双通道：轮询主力
 * （仿氛围灯 v4.4），在后台线程读最新值判定；并把变化的原始值落 W 级日志，便于实车标定阈值/符号。
 */
final class AccelMonitor extends AbstractSignalSource {

    private static final String TAG = "AccelMonitor";

    /** 轮询周期：急加速/急刹是瞬时事件，需较快采样才抓得住；250ms 兼顾灵敏与开销。 */
    private static final long POLL_INTERVAL_MS = 250;

    private Object token;
    private volatile AccelReaction last = AccelReaction.NONE;
    /** 上次落日志的原始值（取整 0.1），仅明显变化时记 W 日志，避免刷屏。 */
    private volatile float lastLoggedValue = Float.NaN;

    /**
     * 轮询专用后台线程。{@code getSensorLatestValue} 是对车控服务的同步 binder 调用，
     * 车机繁忙会阻塞调用线程；放主线程会卡 UI，必须后台读。
     */
    private HandlerThread pollThread;
    private Handler handler;

    AccelMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        last = AccelReaction.NONE;
        lastLoggedValue = Float.NaN;
        // 回调通道：若车机确实推送加速度变化则即时响应。
        token = EcarxCarManager.getInstance().watchSensorValue(
                context, ISensor.SENSOR_TYPE_SPEED_LON_ACCELERATION,
                (type, value) -> classifyAndDispatch(value, "回调"));
        // 轮询通道（主力）：只读传感量靠定时读最新值兜底。后台线程跑，不卡 UI。
        pollThread = new HandlerThread("AccelPoll");
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
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            float value = EcarxCarManager.getInstance()
                    .readSensorValue(ISensor.SENSOR_TYPE_SPEED_LON_ACCELERATION);
            if (!Float.isNaN(value)) {
                classifyAndDispatch(value, "轮询");
            }
            if (handler != null) {
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    /** 判定并在状态跳变时上报；变化明显的原始值落 W 日志供实车标定。 */
    private void classifyAndDispatch(float value, String src) {
        // 标定日志：与上次相差 ≥0.5 m/s² 才记，避免静止微抖刷屏。
        if (Float.isNaN(lastLoggedValue) || Math.abs(value - lastLoggedValue) >= 0.5f) {
            lastLoggedValue = value;
            RobotLog.w(TAG, "纵向加速度[" + src + "]=" + value + " m/s²");
        }
        AccelReaction r = AccelReaction.classify(
                value, CarThresholds.HARD_ACCEL_MS2, CarThresholds.HARD_BRAKE_MS2);
        if (r == last) {
            return;
        }
        last = r;
        if (r == AccelReaction.ACCEL) {
            engine.onHardAccel();
        } else if (r == AccelReaction.BRAKE) {
            engine.onHardBrake();
        }
    }
}
