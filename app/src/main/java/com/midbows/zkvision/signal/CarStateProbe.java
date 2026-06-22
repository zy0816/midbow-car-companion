package com.midbows.zkvision.signal;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import com.ecarx.xui.adaptapi.FunctionStatus;
import com.ecarx.xui.adaptapi.car.hev.ICharging;
import com.ecarx.xui.adaptapi.car.sensor.ISensor;
import com.ecarx.xui.adaptapi.car.sensor.ITireSensor;
import com.ecarx.xui.adaptapi.car.vehicle.IADAS;
import com.ecarx.xui.adaptapi.car.vehicle.IAmbienceLight;
import com.ecarx.xui.adaptapi.car.vehicle.IBcm;
import com.ecarx.xui.adaptapi.car.vehicle.IDriveMode;
import com.midbows.zkvision.util.RobotLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 车态探测：一次性遍历全部联动信号，打印「是否支持 + 当前值」到日志面板。
 *
 * <p>用于实车一次性标定——核对各功能/传感是否存在、当前取值，回填阈值/编码（盲区左右、
 * 充电/转向灯值语义等）。只读不写，通过 {@link RobotLog} 输出，不影响行为引擎。
 */
public final class CarStateProbe {

    private static final String TAG = "CarStateProbe";
    private static final long READ_DELAY_MS = 1500;

    private enum Kind { FUNCTION, SENSOR_VALUE, SENSOR_EVENT }

    private static final class Entry {
        final String name;
        final int id;
        final Kind kind;

        Entry(String name, int id, Kind kind) {
            this.name = name;
            this.id = id;
            this.kind = kind;
        }
    }

    private final Context context;
    /**
     * 探测在后台线程跑。{@code readFunction/readSensor*} 都是对车控服务的同步 binder 调用，
     * 这里一次要连读 ~24 个，放主线程会卡死 UI；必须后台。读完用 RobotLog 输出（其内部转主线程刷 UI）。
     */
    private final HandlerThread thread = new HandlerThread("CarStateProbe");
    private final Handler handler;

    public CarStateProbe(Context context) {
        this.context = context.getApplicationContext();
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    /** 启动探测：触发连接，延时后读取并打印一遍。 */
    public void run() {
        EcarxCarManager mgr = EcarxCarManager.getInstance();
        mgr.ensureConnected(context);
        RobotLog.d(TAG, "车态探测开始，等待连接…");
        handler.postDelayed(this::dump, READ_DELAY_MS);
    }

    private void dump() {
        EcarxCarManager mgr = EcarxCarManager.getInstance();
        if (!mgr.isConnected()) {
            RobotLog.d(TAG, "ecarx 未连接，无法探测（非车机环境？）");
            return;
        }
        RobotLog.d(TAG, "==== 车态探测结果 ====");
        for (Entry e : entries()) {
            if (e.kind == Kind.FUNCTION) {
                FunctionStatus s = mgr.isFunctionSupported(e.id);
                int v = mgr.readFunction(e.id);
                RobotLog.d(TAG, e.name + " [功能 " + e.id + "] 支持=" + s + " 值=" + fmtInt(v));
            } else if (e.kind == Kind.SENSOR_VALUE) {
                FunctionStatus s = mgr.isSensorSupported(e.id);
                float v = mgr.readSensorValue(e.id);
                RobotLog.d(TAG, e.name + " [传感 " + e.id + "] 支持=" + s + " 值=" + v);
            } else {
                FunctionStatus s = mgr.isSensorSupported(e.id);
                int v = mgr.readSensorEvent(e.id);
                RobotLog.d(TAG, e.name + " [传感态 " + e.id + "] 支持=" + s + " 值=" + fmtInt(v));
            }
        }
        RobotLog.d(TAG, "==== 探测结束 ====");
    }

    private static String fmtInt(int v) {
        return v == Integer.MIN_VALUE ? "N/A" : v + " (0x" + Integer.toHexString(v) + ")";
    }

    private static List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        // 氛围灯一族：MOOD_LIGHT_COLOR 实车读出 255(0xFF) 无效，说明真实「当前色」可能在别的 funcId
        // （色随驾驶模式/动态时无单色，或色值在 *_COLOR_SET 里）。一次性全读出来，定位哪个返回有效色。
        list.add(new Entry("氛围灯-MOOD色", IAmbienceLight.SETTING_FUNC_MOOD_LIGHT_COLOR, Kind.FUNCTION));
        list.add(new Entry("氛围灯-MOOD开关", IAmbienceLight.SETTING_FUNC_MOOD_LIGHT, Kind.FUNCTION));
        list.add(new Entry("氛围灯-MOOD模式", IAmbienceLight.SETTING_FUNC_MOOD_LIGHT_MODE, Kind.FUNCTION));
        list.add(new Entry("氛围灯-总开关", IAmbienceLight.SETTING_FUNC_AMBIENCE_LIGHT_ALL_ONOFF, Kind.FUNCTION));
        list.add(new Entry("氛围灯-COLOR_SET", IAmbienceLight.SETTING_FUNC_AMBIENCE_LIGHT_COLOR_SET, Kind.FUNCTION));
        list.add(new Entry("氛围灯-主色", IAmbienceLight.SETTING_FUNC_AMBIENCE_LIGHT_MAINCOLOR, Kind.FUNCTION));
        list.add(new Entry("氛围灯-主区色", IAmbienceLight.SETTING_FUNC_AMBIENCE_LIGHT_MAINZONES_COLOR_SET, Kind.FUNCTION));
        list.add(new Entry("氛围灯-底区色", IAmbienceLight.SETTING_FUNC_AMBIENCE_LIGHT_BOTZONES_COLOR_SET, Kind.FUNCTION));
        list.add(new Entry("驾驶模式", IDriveMode.DM_FUNC_DRIVE_MODE_SELECT, Kind.FUNCTION));
        list.add(new Entry("前向碰撞预警", IADAS.SETTING_FUNC_FORWARD_COLLISION_WARN, Kind.FUNCTION));
        list.add(new Entry("盲区监测告警", IADAS.SETTING_FUNC_BLIND_SPOT_DETECTION_WARNING, Kind.FUNCTION));
        list.add(new Entry("左转向灯", IBcm.BCM_FUNC_LIGHT_LEFT_TRUN_SIGNAL, Kind.FUNCTION));
        list.add(new Entry("右转向灯", IBcm.BCM_FUNC_LIGHT_RIGHT_TRUN_SIGNAL, Kind.FUNCTION));
        list.add(new Entry("车门", IBcm.BCM_FUNC_DOOR, Kind.FUNCTION));
        list.add(new Entry("充电中", ICharging.CHARGE_FUNC_CHARGING, Kind.FUNCTION));
        list.add(new Entry("充电SOC", ICharging.CHARGE_FUNC_CHARGING_SOC, Kind.FUNCTION));
        list.add(new Entry("纵向加速度", ISensor.SENSOR_TYPE_SPEED_LON_ACCELERATION, Kind.SENSOR_VALUE));
        list.add(new Entry("车内温度", ISensor.SENSOR_TYPE_TEMPERATURE_INDOOR, Kind.SENSOR_VALUE));
        list.add(new Entry("主驾占用", ISensor.SENSOR_TYPE_SEAT_OCCUPATION_STATUS_DRIVER, Kind.SENSOR_EVENT));
        list.add(new Entry("副驾占用", ISensor.SENSOR_TYPE_SEAT_OCCUPATION_STATUS_PASSENGER, Kind.SENSOR_EVENT));
        list.add(new Entry("昼夜", ISensor.SENSOR_TYPE_DAY_NIGHT, Kind.SENSOR_EVENT));
        list.add(new Entry("挡位", ISensor.SENSOR_TYPE_GEAR, Kind.SENSOR_EVENT));
        list.add(new Entry("胎压系统态", ITireSensor.TIRE_TPMS_SYS_STATES, Kind.SENSOR_EVENT));
        return list;
    }
}
