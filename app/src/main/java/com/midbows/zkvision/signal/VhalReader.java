package com.midbows.zkvision.signal;

import android.content.Context;

import com.midbows.zkvision.util.RobotLog;

import java.lang.reflect.Method;

/**
 * 整车 VHAL 厂商属性读取（反射 {@code android.car.CarPropertyManager}）。
 *
 * <p>盲区（LCA）等实时信号在整车 VHAL 上以厂商属性暴露，标准 {@code android.car.Car} →
 * {@code CarPropertyManager.getIntProperty(propId, areaId)} 即可读到。本项目不依赖 {@code android.car}
 * （非 SDK，编译期不可见），故全程<b>反射</b>调用；非车机环境（单测/普通机）静默降级。
 *
 * <p>读取厂商属性需 {@code CAR_VENDOR_EXTENSION}（signature|privileged）权限——由平台签名 +
 * {@code sharedUserId=android.uid.system} 满足。{@code createCar(Context)} 为<b>阻塞</b>连接，
 * 必须在后台线程调用，切勿放主线程。
 *
 * <p>属性 ID 与具体车型 VHAL 相关，集中定义在调用方常量里；本读取器与车型无关，只负责反射读 INT32。
 */
final class VhalReader {

    private static final String TAG = "VhalReader";

    /** 读取失败/不可用的哨兵值。 */
    static final int UNAVAILABLE = Integer.MIN_VALUE;

    private static VhalReader instance;

    private final Context appContext;
    private Object car;            // android.car.Car
    private Object propertyManager; // android.car.hardware.property.CarPropertyManager
    private Method getIntProperty; // (int propId, int areaId) -> int
    private boolean tried;         // 已尝试连接（无论成败），避免反复抛异常刷屏
    private boolean available;

    private VhalReader(Context context) {
        this.appContext = context.getApplicationContext();
    }

    static synchronized VhalReader getInstance(Context context) {
        if (instance == null) {
            instance = new VhalReader(context);
        }
        return instance;
    }

    /** 懒连接 car-service 并解析 CarPropertyManager.getIntProperty。阻塞，须后台线程调用。 */
    private synchronized void ensureConnected() {
        if (tried) {
            return;
        }
        tried = true;
        try {
            Class<?> carClass = Class.forName("android.car.Car");
            // createCar(Context) 阻塞直到连上（旧 API，足够用）。
            Method createCar = carClass.getMethod("createCar", Context.class);
            car = createCar.invoke(null, appContext);
            if (car == null) {
                RobotLog.d(TAG, "createCar 返回 null，VHAL 不可用");
                return;
            }
            // getCarManager("property") -> CarPropertyManager
            Method getCarManager = carClass.getMethod("getCarManager", String.class);
            propertyManager = getCarManager.invoke(car, "property");
            if (propertyManager == null) {
                RobotLog.d(TAG, "getCarManager(property) 返回 null");
                return;
            }
            getIntProperty = propertyManager.getClass()
                    .getMethod("getIntProperty", int.class, int.class);
            available = true;
            RobotLog.d(TAG, "VHAL 已就绪");
        } catch (Throwable t) {
            RobotLog.d(TAG, "VHAL 接入失败，降级: " + t.getMessage());
        }
    }

    boolean isAvailable() {
        return available;
    }

    /**
     * 读厂商 INT32 属性。失败返回 {@link #UNAVAILABLE}。
     * 个别属性在某状态下读会抛 {@code PropertyNotAvailable}，按不可用处理即可。
     */
    int getInt(int propId, int areaId) {
        ensureConnected();
        if (!available) {
            return UNAVAILABLE;
        }
        try {
            Object v = getIntProperty.invoke(propertyManager, propId, areaId);
            return v instanceof Integer ? (Integer) v : UNAVAILABLE;
        } catch (Throwable t) {
            return UNAVAILABLE;
        }
    }
}
