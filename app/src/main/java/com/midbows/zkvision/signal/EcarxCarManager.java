package com.midbows.zkvision.signal;

import android.content.Context;

import com.ecarx.xui.adaptapi.FunctionStatus;
import com.ecarx.xui.adaptapi.binder.IConnectable;
import com.ecarx.xui.adaptapi.car.Car;
import com.ecarx.xui.adaptapi.car.ICar;
import com.ecarx.xui.adaptapi.car.base.ICarFunction;
import com.ecarx.xui.adaptapi.car.sensor.ISensor;
import com.midbows.zkvision.util.RobotLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 车机 ecarx 轻量车控 API（{@code com.ecarx.xui.adaptapi}）统一接入封装。
 *
 * <p>该 API 由车机 framework 自带（boot classpath），标准接入方式：
 * {@code Car.create(context)} 得到 {@link ICar}，其实例实现 {@link IConnectable}，需注册
 * {@link IConnectable.IConnectWatcher} 并 {@code connect()}，连上后才能拿
 * {@link ICarFunction}（车身功能，如车门/车窗）与 {@link ISensor}（传感量，如座位占用/车速/挡位）。
 *
 * <p>本类是<b>进程内单例</b>，把这套连接生命周期与「连接前先排队、连上后统一注册」的样板收敛到一处，
 * 各信号源（车门、未来的座位/车速等）复用同一条连接，避免重复 createCar 与回调样板。
 * 非 ecarx 环境（如单测 JVM）下静默降级，不抛异常。
 */
public final class EcarxCarManager {

    private static final String TAG = "EcarxCar";

    private static EcarxCarManager instance;

    /** 待连接成功后补注册的车身功能监听。 */
    private final List<FunctionReg> pendingFunctions = new ArrayList<>();
    /** 待连接成功后补注册的传感监听。 */
    private final List<SensorReg> pendingSensors = new ArrayList<>();

    private ICar car;
    private ICarFunction carFunction;
    private ISensor sensor;
    private boolean connecting;
    private boolean connected;

    private EcarxCarManager() {
    }

    public static synchronized EcarxCarManager getInstance() {
        if (instance == null) {
            instance = new EcarxCarManager();
        }
        return instance;
    }

    /** 注册一个车身功能监听（如车门）。连接未就绪时排队，连上后自动注册。 */
    public synchronized void watchFunction(int functionId, ICarFunction.IFunctionValueWatcher watcher) {
        pendingFunctions.add(new FunctionReg(functionId, watcher));
        if (connected && carFunction != null) {
            applyFunction(functionId, watcher);
        }
    }

    /** 注册一个传感监听（如座位占用/车速）。zone 传 {@code -1} 表示不分区。 */
    public synchronized void watchSensor(int sensorType, int zone, ISensor.ISensorListener listener) {
        pendingSensors.add(new SensorReg(sensorType, zone, listener));
        if (connected && sensor != null) {
            applySensor(sensorType, zone, listener);
        }
    }

    /** 注销一个车身功能监听。 */
    public synchronized void unwatchFunction(ICarFunction.IFunctionValueWatcher watcher) {
        pendingFunctions.removeIf(r -> r.watcher == watcher);
        if (carFunction != null) {
            try {
                carFunction.unregisterFunctionValueWatcher(watcher);
            } catch (Throwable t) {
                RobotLog.d(TAG, "注销车身功能监听失败: " + t.getMessage());
            }
        }
    }

    /** 注销一个传感监听。 */
    public synchronized void unwatchSensor(ISensor.ISensorListener listener) {
        pendingSensors.removeIf(r -> r.listener == listener);
        if (sensor != null) {
            try {
                sensor.unregisterListener(listener);
            } catch (Throwable t) {
                RobotLog.d(TAG, "注销传感监听失败: " + t.getMessage());
            }
        }
    }

    /** 懒连接：首个监听注册时由调用方触发。已连接/连接中则直接返回。 */
    public synchronized void ensureConnected(Context context) {
        if (connected || connecting) {
            return;
        }
        try {
            ICar created = Car.create(context.getApplicationContext());
            if (created == null) {
                RobotLog.d(TAG, "Car.create 返回 null，车控 API 不可用");
                return;
            }
            car = created;
            if (created instanceof IConnectable) {
                connecting = true;
                IConnectable connectable = (IConnectable) created;
                connectable.registerConnectWatcher(connectWatcher);
                connectable.connect();
                RobotLog.d(TAG, "ecarx Car 连接中…");
            } else {
                // 同步可用，直接拿管理器并补注册
                onConnectedInternal();
            }
        } catch (Throwable t) {
            RobotLog.d(TAG, "ecarx Car 接入失败，降级: " + t.getMessage());
            connecting = false;
        }
    }

    private final IConnectable.IConnectWatcher connectWatcher = new IConnectable.IConnectWatcher() {
        @Override
        public void onConnected() {
            onConnectedInternal();
        }

        @Override
        public void onDisConnected() {
            synchronized (EcarxCarManager.this) {
                connected = false;
                carFunction = null;
                sensor = null;
                RobotLog.d(TAG, "ecarx Car 断开");
            }
        }
    };

    private synchronized void onConnectedInternal() {
        try {
            carFunction = car.getICarFunction();
            sensor = car.getSensorManager();
            connected = true;
            connecting = false;
            RobotLog.d(TAG, "ecarx Car 已连接，补注册 "
                    + pendingFunctions.size() + " 功能 / " + pendingSensors.size() + " 传感");
            for (FunctionReg r : pendingFunctions) {
                applyFunction(r.functionId, r.watcher);
            }
            for (SensorReg r : pendingSensors) {
                applySensor(r.sensorType, r.zone, r.listener);
            }
        } catch (Throwable t) {
            RobotLog.d(TAG, "获取车控管理器失败: " + t.getMessage());
        }
    }

    private void applyFunction(int functionId, ICarFunction.IFunctionValueWatcher watcher) {
        try {
            carFunction.registerFunctionValueWatcher(new int[]{functionId}, watcher);
        } catch (Throwable t) {
            RobotLog.d(TAG, "注册车身功能 " + functionId + " 失败: " + t.getMessage());
        }
    }

    private void applySensor(int sensorType, int zone, ISensor.ISensorListener listener) {
        try {
            if (zone == -1) {
                sensor.registerListener(listener, sensorType);
            } else {
                sensor.registerListener(listener, sensorType, zone);
            }
        } catch (Throwable t) {
            RobotLog.d(TAG, "注册传感 " + sensorType + " 失败: " + t.getMessage());
        }
    }

    // ---------------- 便捷回调封装（消除各信号源的接口样板） ----------------

    /** 车身功能值回调。{@code value} 为对应功能的最新取值。 */
    public interface FunctionCallback {
        void onValue(int functionId, int zone, int value);
    }

    /** 传感事件（离散态，如昼夜/座位占用/胎压告警）回调。 */
    public interface SensorEventCallback {
        void onEvent(int sensorType, int event);
    }

    /** 传感数值（连续量，如加速度/车内温度）回调。 */
    public interface SensorValueCallback {
        void onValue(int sensorType, float value);
    }

    /**
     * 监听某车身功能值变化（自动懒连接）。返回的 token 用于 {@link #unwatch(Object)}。
     * 各信号源只传 lambda，无需各自实现冗长的 ecarx 接口。
     */
    public Object watchFunctionValue(Context context, int functionId, FunctionCallback cb) {
        FunctionAdapter adapter = new FunctionAdapter(functionId, cb);
        watchFunction(functionId, adapter);
        ensureConnected(context);
        return adapter;
    }

    /** 监听某传感事件（离散态）。返回的 token 用于 {@link #unwatch(Object)}。 */
    public Object watchSensorEvent(Context context, int sensorType, SensorEventCallback cb) {
        SensorEventAdapter adapter = new SensorEventAdapter(sensorType, cb);
        watchSensor(sensorType, -1, adapter);
        ensureConnected(context);
        return adapter;
    }

    /** 监听某传感数值（连续量）。返回的 token 用于 {@link #unwatch(Object)}。 */
    public Object watchSensorValue(Context context, int sensorType, SensorValueCallback cb) {
        SensorValueAdapter adapter = new SensorValueAdapter(sensorType, cb);
        watchSensor(sensorType, -1, adapter);
        ensureConnected(context);
        return adapter;
    }

    /** 注销由便捷封装返回的 token（自动区分功能/传感）。 */
    public void unwatch(Object token) {
        if (token instanceof ICarFunction.IFunctionValueWatcher) {
            unwatchFunction((ICarFunction.IFunctionValueWatcher) token);
        } else if (token instanceof ISensor.ISensorListener) {
            unwatchSensor((ISensor.ISensorListener) token);
        }
    }

    // ---------------- 探测读取（车态探测页一次性标定用） ----------------

    public synchronized FunctionStatus isFunctionSupported(int functionId) {
        if (carFunction == null) {
            return null;
        }
        try {
            return carFunction.isFunctionSupported(functionId);
        } catch (Throwable t) {
            return null;
        }
    }

    public synchronized int readFunction(int functionId) {
        if (carFunction == null) {
            return Integer.MIN_VALUE;
        }
        try {
            return carFunction.getFunctionValue(functionId);
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    public synchronized FunctionStatus isSensorSupported(int sensorType) {
        if (sensor == null) {
            return null;
        }
        try {
            return sensor.isSensorSupported(sensorType);
        } catch (Throwable t) {
            return null;
        }
    }

    public synchronized float readSensorValue(int sensorType) {
        if (sensor == null) {
            return Float.NaN;
        }
        try {
            return sensor.getSensorLatestValue(sensorType);
        } catch (Throwable t) {
            return Float.NaN;
        }
    }

    public synchronized int readSensorEvent(int sensorType) {
        if (sensor == null) {
            return Integer.MIN_VALUE;
        }
        try {
            return sensor.getSensorEvent(sensorType);
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    public synchronized boolean isConnected() {
        return connected;
    }

    private static final class FunctionAdapter implements ICarFunction.IFunctionValueWatcher {
        private final int functionId;
        private final FunctionCallback cb;

        FunctionAdapter(int functionId, FunctionCallback cb) {
            this.functionId = functionId;
            this.cb = cb;
        }

        @Override
        public void onFunctionValueChanged(int functionId, int zone, int value) {
            if (functionId == this.functionId) {
                cb.onValue(functionId, zone, value);
            }
        }

        @Override
        public void onFunctionChanged(int functionId) {
        }

        @Override
        public void onCustomizeFunctionValueChanged(int functionId, int zone, float value) {
        }

        @Override
        public void onSupportedFunctionValueChanged(int functionId, int[] supportedValues) {
        }

        @Override
        public void onSupportedFunctionStatusChanged(int functionId, int zone, FunctionStatus status) {
        }
    }

    private static final class SensorEventAdapter implements ISensor.ISensorListener {
        private final int sensorType;
        private final SensorEventCallback cb;

        SensorEventAdapter(int sensorType, SensorEventCallback cb) {
            this.sensorType = sensorType;
            this.cb = cb;
        }

        @Override
        public void onSensorEventChanged(int sensorType, int event) {
            if (sensorType == this.sensorType) {
                cb.onEvent(sensorType, event);
            }
        }

        @Override
        public void onSensorValueChanged(int sensorType, float value) {
        }

        @Override
        public void onSensorSupportChanged(int sensorType, FunctionStatus status) {
        }
    }

    private static final class SensorValueAdapter implements ISensor.ISensorListener {
        private final int sensorType;
        private final SensorValueCallback cb;

        SensorValueAdapter(int sensorType, SensorValueCallback cb) {
            this.sensorType = sensorType;
            this.cb = cb;
        }

        @Override
        public void onSensorValueChanged(int sensorType, float value) {
            if (sensorType == this.sensorType) {
                cb.onValue(sensorType, value);
            }
        }

        @Override
        public void onSensorEventChanged(int sensorType, int event) {
        }

        @Override
        public void onSensorSupportChanged(int sensorType, FunctionStatus status) {
        }
    }

    private static final class FunctionReg {
        final int functionId;
        final ICarFunction.IFunctionValueWatcher watcher;

        FunctionReg(int functionId, ICarFunction.IFunctionValueWatcher watcher) {
            this.functionId = functionId;
            this.watcher = watcher;
        }
    }

    private static final class SensorReg {
        final int sensorType;
        final int zone;
        final ISensor.ISensorListener listener;

        SensorReg(int sensorType, int zone, ISensor.ISensorListener listener) {
            this.sensorType = sensorType;
            this.zone = zone;
            this.listener = listener;
        }
    }
}
