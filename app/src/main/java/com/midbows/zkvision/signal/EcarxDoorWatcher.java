package com.midbows.zkvision.signal;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.ecarx.xui.adaptapi.FunctionStatus;
import com.ecarx.xui.adaptapi.car.base.ICarFunction;
import com.ecarx.xui.adaptapi.car.vehicle.IBcm;
import com.midbows.zkvision.util.RobotLog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 通过 ecarx 车身功能监听「车门打开」作为上车问候触发。
 *
 * <p>监听 {@link IBcm#BCM_FUNC_DOOR}，当某个车门值变为
 * {@link IBcm#DOOR_OPEN} 即视为有人上车。这是车机原生暴露的真实门状态。
 * 多门同开由 {@link com.midbows.zkvision.behavior.BehaviorEngine}
 * 的 WELCOME 优先级仲裁去重，本类只按「关→开」跳变上报，避免重复触发。
 */
final class EcarxDoorWatcher implements ICarFunction.IFunctionValueWatcher {

    private static final String TAG = "EcarxDoorWatcher";

    /** 同一车门两次触发的最小间隔；门开度信号会在阈值附近抖动，必须用冷却时间兜底。 */
    private static final long RETRIGGER_COOLDOWN_MS = 8000;
    /** 「全部关闭」确认延时：避免门开度在阈值附近瞬时跳到关又跳回开时误判已关。 */
    private static final long CLOSE_SETTLE_MS = 1200;

    interface DoorListener {
        /** @param zone 车门 zone（VehicleAreaSeat 位掩码：1=主驾 4=副驾）。 */
        void onDoorOpen(int zone);

        /** 所有车门均已关闭（迎宾应回正）。 */
        void onAllDoorsClosed();
    }

    private final Context context;
    private final DoorListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** 当前处于「开」状态的车门 zone，用于「关→开」跳变去抖。 */
    private final Set<Integer> openZones = new HashSet<>();
    /** 各 zone 上次触发时刻，用于冷却去抖（门开度抖动会反复跨阈值）。 */
    private final Map<Integer, Long> lastTriggerMs = new HashMap<>();

    /** 延时确认「全部关闭」：若期间又有门打开会被取消，避免抖动误回正。 */
    private final Runnable confirmAllClosed = new Runnable() {
        @Override
        public void run() {
            if (openZones.isEmpty()) {
                RobotLog.d(TAG, "车门全部关闭（确认）");
                listener.onAllDoorsClosed();
            }
        }
    };

    EcarxDoorWatcher(Context context, DoorListener listener) {
        this.context = context;
        this.listener = listener;
    }

    void start() {
        EcarxCarManager mgr = EcarxCarManager.getInstance();
        mgr.watchFunction(IBcm.BCM_FUNC_DOOR, this);
        mgr.ensureConnected(context);
        RobotLog.d(TAG, "已挂载 BCM_FUNC_DOOR 车门监听");
    }

    void stop() {
        EcarxCarManager.getInstance().unwatchFunction(this);
        handler.removeCallbacks(confirmAllClosed);
        openZones.clear();
        lastTriggerMs.clear();
    }

    @Override
    public void onFunctionValueChanged(int functionId, int zone, int value) {
        if (functionId != IBcm.BCM_FUNC_DOOR) {
            return;
        }
        RobotLog.d(TAG, "门信号 zone=" + zone + " value=" + value);
        // 关键：门开度会报 1 以外的中间值(开门途中)；只有明确 DOOR_CLOSE(0) 才算关闭，
        // 其余一律视为「开/未关」，避免开门过程中的中间值被误判成已关而提前回正。
        boolean open = value != IBcm.DOOR_CLOSE;
        if (open) {
            // 有门开，撤销待确认的「全部关闭」回正。
            handler.removeCallbacks(confirmAllClosed);
            // 先按「关→开」跳变去抖：门保持开期间的重复上报直接忽略。
            if (!openZones.add(zone)) {
                return;
            }
            // 再用冷却兜底：门开度在阈值附近快速关→开抖动时，跳变去抖会失效，靠时间窗压制。
            long now = SystemClock.uptimeMillis();
            Long last = lastTriggerMs.get(zone);
            if (last != null && now - last < RETRIGGER_COOLDOWN_MS) {
                return;
            }
            lastTriggerMs.put(zone, now);
            RobotLog.d(TAG, "车门打开 zone=" + zone + "，判定上车");
            listener.onDoorOpen(zone);
        } else {
            // 某门关闭；待所有门都关闭后延时确认再回正（防抖动误判）。
            if (openZones.remove(zone) && openZones.isEmpty()) {
                handler.removeCallbacks(confirmAllClosed);
                handler.postDelayed(confirmAllClosed, CLOSE_SETTLE_MS);
            }
        }
    }

    @Override
    public void onFunctionChanged(int functionId) {
        // 不关心
    }

    @Override
    public void onCustomizeFunctionValueChanged(int functionId, int zone, float value) {
        // 不关心
    }

    @Override
    public void onSupportedFunctionValueChanged(int functionId, int[] supportedValues) {
        // 不关心
    }

    @Override
    public void onSupportedFunctionStatusChanged(int functionId, int zone, FunctionStatus status) {
        // 不关心
    }
}
