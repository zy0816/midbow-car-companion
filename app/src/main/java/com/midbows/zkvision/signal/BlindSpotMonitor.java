package com.midbows.zkvision.signal;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import com.midbows.zkvision.behavior.BehaviorEngine;
import com.midbows.zkvision.util.RobotLog;

/**
 * 盲区扭头：跟随车机盲区监测（LCA）告警，区分左右侧扭头方向。
 *
 * <p><b>真实信号源</b>：盲区告警的功能开关设置项停车恒读 0xFF，<b>不是</b>实时告警。真实的实时盲区在
 * 整车 VHAL 厂商属性（左右各一，盲区动画状态），经 {@link VhalReader} 反射
 * {@code CarPropertyManager.getIntProperty} 读取。后台线程定时轮询（binder 同步调用不能放主线程），
 * 任一侧由「无告警」跳变为「有告警」即朝该侧探头。原始值落 W 日志便于实车标定非零语义。
 * 属性 ID 与具体车型相关，集中在下方常量，移植到其它车只需改这两个值。
 */
final class BlindSpotMonitor extends AbstractSignalSource {

    private static final String TAG = "BlindSpotMonitor";

    /** 整车 VHAL 盲区动画属性（GLOBAL，areaId=0）；与具体车型相关。 */
    private static final int PROP_LCA_LEFT = 0x21403d08;
    private static final int PROP_LCA_RIGHT = 0x21403d09;
    private static final int AREA_GLOBAL = 0;

    /** 轮询周期：盲区来车是瞬时事件，需较快采样；200ms 兼顾灵敏与开销。 */
    private static final long POLL_INTERVAL_MS = 200;

    private HandlerThread pollThread;
    private Handler handler;
    private VhalReader vhal;

    /** 上次是否处于告警态（去重，仅跳变上报）。 */
    private boolean leftActive;
    private boolean rightActive;
    /** 上次落日志的原始值，仅变化时记 W，避免刷屏。 */
    private int lastLeftRaw = Integer.MIN_VALUE;
    private int lastRightRaw = Integer.MIN_VALUE;

    BlindSpotMonitor(Context context, BehaviorEngine engine) {
        super(context, engine);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Override
    protected void doStart() {
        leftActive = false;
        rightActive = false;
        lastLeftRaw = Integer.MIN_VALUE;
        lastRightRaw = Integer.MIN_VALUE;
        vhal = VhalReader.getInstance(context);
        pollThread = new HandlerThread("BlindSpotPoll");
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
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            int left = vhal.getInt(PROP_LCA_LEFT, AREA_GLOBAL);
            int right = vhal.getInt(PROP_LCA_RIGHT, AREA_GLOBAL);
            handleSide(true, left);
            handleSide(false, right);
            if (handler != null) {
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    /** 处理单侧：读不到则跳过；非零视为告警；跳变到告警时朝该侧探头。 */
    private void handleSide(boolean left, int raw) {
        if (raw == VhalReader.UNAVAILABLE) {
            return;
        }
        int lastRaw = left ? lastLeftRaw : lastRightRaw;
        if (raw != lastRaw) {
            if (left) {
                lastLeftRaw = raw;
            } else {
                lastRightRaw = raw;
            }
            RobotLog.w(TAG, "盲区原始值 " + (left ? "左" : "右") + "=0x" + Integer.toHexString(raw));
        }
        boolean active = raw != 0;
        boolean wasActive = left ? leftActive : rightActive;
        if (active == wasActive) {
            return;
        }
        if (left) {
            leftActive = active;
        } else {
            rightActive = active;
        }
        if (active) {
            engine.onBlindSpot(left);
        }
    }
}
