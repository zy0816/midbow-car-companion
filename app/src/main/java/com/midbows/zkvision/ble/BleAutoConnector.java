package com.midbows.zkvision.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

import com.midbows.zkvision.util.RobotLog;

/**
 * BLE 自动连接协调器：扫描到目标设备即连接，两端都就绪后停止扫描。
 *
 * <p>把「发现即连接」的策略从 UI 中剥离，service 后台即可自治运行。
 * 可选转发一个 UI 监听器用于界面展示连接状态（{@link BleManager} 仅支持单监听）。
 *
 * <p><b>抗车机睡眠/唤醒</b>：车机睡眠会断蓝牙、唤醒后蓝牙重开。仅靠断线回调重连不够（旧
 * scanner 句柄失效、双连后已停扫无法重新发现）。故此处加两道自愈：
 * (1) 监听蓝牙开关广播，{@code STATE_ON} 即重新扫描；
 * (2) 看门狗定时巡检，只要未双连且蓝牙可用就持续重扫，直到两端都连上。
 */
public final class BleAutoConnector implements BleManager.BleStateListener {

    private static final String TAG = "BleAutoConnector";
    /** 看门狗巡检周期：未双连时定时重扫兜底，覆盖漏收断线/蓝牙广播的情况。 */
    private static final long WATCHDOG_INTERVAL_MS = 10000;

    /** 运动板（重）连成功通知，用于让行为引擎在重连后复位机器人状态。 */
    public interface MotionReadyListener {
        void onMotionLinkReady();
    }

    private final Context context;
    private final BleManager ble;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BleManager.BleStateListener uiListener;
    private MotionReadyListener motionReadyListener;
    private boolean running;

    public BleAutoConnector(Context context, BleManager ble) {
        this.context = context.getApplicationContext();
        this.ble = ble;
    }

    /** 设置可选的 UI 转发监听器。 */
    public void setUiListener(BleManager.BleStateListener listener) {
        this.uiListener = listener;
    }

    /** 设置运动板就绪监听器（行为引擎用于重连后复位律动状态）。 */
    public void setMotionReadyListener(MotionReadyListener listener) {
        this.motionReadyListener = listener;
    }

    public void start() {
        running = true;
        ble.setListener(this);
        context.registerReceiver(btStateReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        ble.startScan();
        handler.removeCallbacks(watchdog);
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(watchdog);
        try {
            context.unregisterReceiver(btStateReceiver);
        } catch (IllegalArgumentException ignored) {
            // 未注册（重复 stop）时忽略。
        }
        ble.stopScan();
    }

    /** 看门狗：未双连且蓝牙可用就持续重扫，直到两端都连上；车机睡眠唤醒后的兜底自愈。 */
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            if (!bothConnected() && ble.isBluetoothEnabled()) {
                // 强制重启扫描而非仅 startScan：车机睡眠会令系统悄停扫描但 scanning 标记残留，
                // 只判 isScanning() 会被脱节的标记骗过而永不重扫。restartScan 先停再开自愈。
                RobotLog.d(TAG, "看门狗：未双连，强制重启扫描");
                ble.restartScan();
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    /** 蓝牙开关广播：车机睡眠关蓝牙、唤醒开蓝牙；开了立即重扫重连。 */
    private final BroadcastReceiver btStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_ON) {
                RobotLog.d(TAG, "蓝牙已开启，强制重启扫描");
                ble.restartScan();
            } else if (state == BluetoothAdapter.STATE_OFF
                    || state == BluetoothAdapter.STATE_TURNING_OFF) {
                // 不能只停扫描：BT 关时 GATT 常不回调断开，端点状态会卡在「已连/连接中」，
                // 唤醒后扫到设备也被状态守卫挡住永不重连。强制复位两端到断开态（等同杀进程重置）。
                RobotLog.d(TAG, "蓝牙关闭，复位链路等待重开重连");
                ble.resetForBluetoothOff();
            }
        }
    };

    @Override
    public void onDeviceFound(BluetoothDevice device, int type) {
        if (ble.getConnectionState(type) == BluetoothProfile.STATE_DISCONNECTED) {
            RobotLog.d(TAG, "发现设备，连接 type=" + type);
            ble.connect(device, type);
        }
        if (uiListener != null) {
            uiListener.onDeviceFound(device, type);
        }
    }

    @Override
    public void onConnectionStateChanged(int type, int state) {
        if (bothConnected()) {
            ble.stopScan();
        } else if (running && ble.isBluetoothEnabled()) {
            // 任一端掉线就立即恢复扫描，便于重新发现并重连（断线回调比看门狗更及时）。
            ble.startScan();
        }
        if (uiListener != null) {
            uiListener.onConnectionStateChanged(type, state);
        }
    }

    @Override
    public void onServicesDiscovered(int type, boolean success) {
        // 运动板服务发现成功＝可下发指令：通知行为引擎复位，纠正断连期间遗留的自维持律动。
        if (type == BleManager.TYPE_MOTION && success && motionReadyListener != null) {
            motionReadyListener.onMotionLinkReady();
        }
        if (uiListener != null) {
            uiListener.onServicesDiscovered(type, success);
        }
    }

    @Override
    public void onDataReceived(int type, byte[] data) {
        if (uiListener != null) {
            uiListener.onDataReceived(type, data);
        }
    }

    private boolean bothConnected() {
        return ble.getConnectionState(BleManager.TYPE_MOTION) == BluetoothProfile.STATE_CONNECTED
                && ble.getConnectionState(BleManager.TYPE_EYES) == BluetoothProfile.STATE_CONNECTED;
    }
}
