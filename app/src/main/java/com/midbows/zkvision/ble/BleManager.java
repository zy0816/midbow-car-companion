package com.midbows.zkvision.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import com.midbows.zkvision.data.SettingsManager;
import com.midbows.zkvision.util.HexUtil;
import com.midbows.zkvision.util.RobotLog;

import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 双端点 BLE 连接管理：扫描、连接、特征匹配、写队列与流控、断线重连。
 *
 * <p>只负责字节收发，不理解协议语义；高层动作由 RobotController 经 protocol 层构帧后调用。
 *
 * <p>修正旧脚手架两处特征匹配 bug：
 * 运动板写特征应为 {@code ae10}（通知 {@code ae05}），
 * 眼睛板写特征应为 {@code c02e69c2}（通知 {@code d914e6b6}）。
 */
@SuppressLint("MissingPermission")
public final class BleManager {

    private static final String TAG = "BleManager";
    private static final long RECONNECT_DELAY_MS = 5000;
    private static final long WRITE_RETRY_DELAY_MS = 30;
    /** 写出后等待 onCharacteristicWrite 回调的超时；超时即视为丢失，兜底驱动队列，防止死锁。 */
    private static final long WRITE_TIMEOUT_MS = 500;
    /** 单帧写入最多尝试次数，超过即丢弃，避免对死链路无限重试刷屏。 */
    private static final int MAX_WRITE_ATTEMPTS = 3;
    /** 某端点连续写失败达到此数即判定为僵尸链路，主动断开触发重连自愈。 */
    private static final int ZOMBIE_LINK_FAILURES = 8;
    /**
     * 暂存指令最长存活：端点未就绪时仍把指令入队缓存（仿竞品「断链暂停不丢弃」），连上后补发；
     * 但超过此时长仍未送达即丢弃，避免长时间断连后唤醒一次性灌入大量过期瞬时指令（旧动作回放）。
     */
    private static final long STALE_WRITE_MS = 3000;
    /** 写队列封顶：长时间未就绪时防止无限堆积，超限丢最旧。 */
    private static final int WRITE_QUEUE_CAP = 32;
    /**
     * 连接建立超时：connectGatt 发起后若在此时限内仍未就绪（既没到 CONNECTED+发现写特征，
     * 也没回 DISCONNECTED），即判定卡死并强制释放重连。
     *
     * <p><b>这是「锁车失联后再也连不上、只有杀进程重开才行」的根因修复之二。</b>车机锁/解锁时
     * 蓝牙被系统高频开关，协议栈半死不活时 {@code connectGatt} 常<b>永不回调</b>
     * {@code onConnectionStateChange}，端点便永远卡在「连接中」；而重连/重发现都要求状态为
     * DISCONNECTED，于是谁也救不了它——只有杀进程重建单例才恢复。本超时把卡死的连接强制拉回
     * DISCONNECTED 并重连，等效杀进程但自动化。
     */
    private static final long CONNECT_TIMEOUT_MS = 12000;

    private static final int MSG_WRITE_NEXT = 0;
    private static final int MSG_WRITE_TIMEOUT = 1;
    private static final int MSG_WRITE_DONE = 2;

    public static final int TYPE_MOTION = 1; // 运动板（出厂名 MIDBOW1S，可绑定其它）
    public static final int TYPE_EYES = 2;   // 氛围灯（出厂名 ET-ROBOT-01，可绑定其它）

    public interface BleStateListener {
        void onDeviceFound(BluetoothDevice device, int type);

        void onConnectionStateChanged(int type, int state);

        void onServicesDiscovered(int type, boolean success);

        void onDataReceived(int type, byte[] data);
    }

    /**
     * 绑定页用的原始扫描回调：上报<b>所有</b>扫到的 BLE 设备（含未识别的），供用户手动指派
     * 哪台是运动板 / 哪台是氛围灯。普通运行不需要它，仅绑定 UI 打开时注册。
     */
    public interface RawScanListener {
        void onRawDevice(BluetoothDevice device, String name, int rssi);
    }

    private static BleManager instance;

    private final Context context;
    private final BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;

    private final BleEndpoint motion =
            new BleEndpoint(TYPE_MOTION, "MIDBOW1S", "ae10", "ae05");
    private final BleEndpoint eyes =
            new BleEndpoint(TYPE_EYES, "ET-ROBOT-01", "c02e69c2", "d914e6b6");

    private final Deque<WriteTask> writeQueue = new ConcurrentLinkedDeque<>();
    private final HandlerThread writeThread;
    private final Handler writeHandler;
    /** 已写出、正在等待 onCharacteristicWrite 回调（或超时）的那一笔；只在 writeThread 访问。 */
    private WriteTask pendingTask;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BleStateListener listener;
    /** 绑定页打开时注册，接收全部原始扫描结果；为空表示无人监听。 */
    private volatile RawScanListener rawScanListener;

    /** 两端点均未就绪时丢弃指令的告警节流：上次告警时刻，避免没连上时疯狂刷屏占资源。 */
    private long lastNotReadyWarnMs;
    private static final long NOT_READY_WARN_INTERVAL_MS = 5000;

    /** 已记日志的未识别设备地址；同一地址只记一次，避免扫描回调每秒刷屏。stopScan 时清空。 */
    private final java.util.Set<String> loggedUnknownAddrs = new java.util.HashSet<>();

    private static final class WriteTask {
        final BleEndpoint endpoint;
        final byte[] data;
        /** 入队时刻，用于时效淘汰：超 STALE_WRITE_MS 仍未送达则丢弃。 */
        final long createdMs = SystemClock.uptimeMillis();
        int attempts;

        WriteTask(BleEndpoint endpoint, byte[] data) {
            this.endpoint = endpoint;
            this.data = data;
        }
    }

    public static synchronized BleManager getInstance(Context context) {
        if (instance == null) {
            instance = new BleManager(context.getApplicationContext());
        }
        return instance;
    }

    private BleManager(Context context) {
        this.context = context;
        BluetoothManager manager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.adapter = manager != null ? manager.getAdapter() : null;

        // 载入已保存的绑定 MAC（开源支持）：有绑定则按 MAC 连，无则回退出厂名前缀。
        SettingsManager settings = SettingsManager.getInstance(context);
        motion.boundMac = settings.getBoundMac(SettingsManager.KEY_BIND_MOTION_MAC);
        eyes.boundMac = settings.getBoundMac(SettingsManager.KEY_BIND_LIGHT_MAC);

        writeThread = new HandlerThread("BleWriteThread");
        writeThread.start();
        writeHandler = new Handler(writeThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_WRITE_NEXT:
                        processNextWrite();
                        break;
                    case MSG_WRITE_TIMEOUT:
                        onWriteTimeout();
                        break;
                    case MSG_WRITE_DONE:
                        onWriteDone(msg.arg1);
                        break;
                    default:
                        break;
                }
            }
        };
    }

    public void setListener(BleStateListener listener) {
        this.listener = listener;
    }

    /** 绑定页注册原始扫描回调；传 null 注销。 */
    public void setRawScanListener(RawScanListener l) {
        this.rawScanListener = l;
    }

    public String getBoundMac(int type) {
        BleEndpoint ep = endpoint(type);
        return ep != null && ep.boundMac != null ? ep.boundMac : "";
    }

    /**
     * 绑定/解绑某端点到指定 MAC（开源支持）。持久化到设置，断开当前链路并重扫，
     * 让新绑定设备按 MAC 重新连上。传 null/空串=解绑回退名前缀。
     */
    public synchronized void bindDevice(int type, String mac) {
        BleEndpoint ep = endpoint(type);
        if (ep == null) {
            return;
        }
        String norm = mac == null ? "" : mac;
        ep.boundMac = norm;
        SettingsManager settings = SettingsManager.getInstance(context);
        settings.setBoundMac(type == TYPE_MOTION
                ? SettingsManager.KEY_BIND_MOTION_MAC : SettingsManager.KEY_BIND_LIGHT_MAC, norm);
        RobotLog.d(TAG, "绑定 type=" + type + " -> " + (norm.isEmpty() ? "(清除/名前缀)" : norm));
        // 旧链路可能连的是别的设备，硬复位后重扫按新归属规则重连。
        resetEndpoint(ep);
        ep.device = null;
        restartScan();
    }

    public boolean isBluetoothEnabled() {
        return adapter != null && adapter.isEnabled();
    }

    private BleEndpoint endpoint(int type) {
        if (type == TYPE_MOTION) {
            return motion;
        }
        if (type == TYPE_EYES) {
            return eyes;
        }
        return null;
    }

    // ---------------- 扫描 ----------------

    /** 是否正在扫描；防止重复 startScan（Android 30s 内 5 次 start 会被限频静默失效）。 */
    private boolean scanning;

    public void startScan() {
        if (!isBluetoothEnabled()) {
            return;
        }
        // BT 关过又开（车机睡眠唤醒）后旧 scanner 句柄会失效，重新取一次。
        scanner = adapter.getBluetoothLeScanner();
        if (scanner != null && !scanning) {
            scanning = true;
            RobotLog.d(TAG, "开始扫描 BLE");
            scanner.startScan(scanCallback);
        }
    }

    public void stopScan() {
        if (scanner != null && scanning && isBluetoothEnabled()) {
            scanner.stopScan(scanCallback);
        }
        scanning = false;
        loggedUnknownAddrs.clear();
    }

    public boolean isScanning() {
        return scanning;
    }

    /**
     * 强制重启扫描，纠正 scanning 标记与系统实际扫描状态脱节。
     *
     * <p>车机睡眠时系统会悄悄停掉 BLE 扫描，但我们的 {@code scanning} 标记仍为 true，
     * 看门狗便误以为还在扫而永不重扫，唤醒后再也连不上。看门狗定时调用本方法兜底：
     * 先停（清掉可能残留的系统级扫描），复位标记，再真正重新开扫。10s 一次远低于
     * Android「30s 内 5 次」限频。
     */
    public void restartScan() {
        if (!isBluetoothEnabled()) {
            return;
        }
        if (scanner != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception ignored) {
                // 扫描器在睡眠中已失效时 stop 可能抛异常，忽略后照常重开。
            }
        }
        scanning = false;
        startScan();
    }

    /**
     * 蓝牙被关闭（车机睡眠）时，强制把两端链路复位到断开态。
     *
     * <p><b>这是「锁车失联后再也连不上、只有杀进程重开才行」的根因修复。</b>
     * 蓝牙适配器关闭时，GATT 往往<b>不会</b>回调 {@code onConnectionStateChange(DISCONNECTED)}，
     * 端点状态便永远卡在「已连」（或重连在 BT 关期间发起卡在「连接中」）。而
     * {@code onDeviceFound} 仅在状态为 DISCONNECTED 时才发起连接，于是唤醒后即便扫到设备也
     * 被卡死的状态挡住、永不重连——只有杀进程重开（重建单例＝全新断开态）才恢复。
     *
     * <p>本方法主动 {@code disconnect()+close()} 释放旧句柄并把状态复位为 DISCONNECTED，
     * 效果等同杀进程重置，但<b>不</b>安排重连（BT 已关连也连不上）；待蓝牙重开后由扫描自然重连。
     */
    public synchronized void resetForBluetoothOff() {
        RobotLog.d(TAG, "蓝牙关闭，强制复位两端链路（等重开后重连）");
        stopScan();
        resetEndpoint(motion);
        resetEndpoint(eyes);
    }

    /** 硬复位单个端点到断开态：关旧 GATT、清特征、复位状态与写队列；不安排重连。 */
    private void resetEndpoint(final BleEndpoint ep) {
        cancelConnectWatchdog(ep);
        final BluetoothGatt g = ep.gatt;
        ep.gatt = null;
        ep.clearChars();
        ep.consecutiveWriteFailures = 0;
        if (g != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        g.disconnect();
                    } catch (Exception ignored) {
                    }
                    try {
                        g.close();
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        if (ep.state != BluetoothProfile.STATE_DISCONNECTED) {
            ep.state = BluetoothProfile.STATE_DISCONNECTED;
            notifyStateChange(ep.type, ep.state);
        }
        purgeWrites(ep);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            // 绑定页打开时，把每一台扫到的设备（含未识别）原样上报，供用户手动指派。
            RawScanListener raw = rawScanListener;
            if (raw != null && (name != null || device.getAddress() != null)) {
                final RawScanListener r = raw;
                final BluetoothDevice d = device;
                final String n = name;
                final int rssi = result.getRssi();
                mainHandler.post(() -> r.onRawDevice(d, n, rssi));
            }
            // 归属判定：已绑定按 MAC，未绑定回退名前缀。
            if (motion.matches(device, name)) {
                notifyDeviceFound(device, TYPE_MOTION);
            } else if (eyes.matches(device, name)) {
                notifyDeviceFound(device, TYPE_EYES);
            } else if (name != null && loggedUnknownAddrs.add(device.getAddress())) {
                // 暂未识别的 BLE 设备落日志，便于发现「蓝牙按钮」的真实名字/地址后再接入；
                // 同一地址只记一次，避免扫描回调每秒重复刷屏淹没日志。
                RobotLog.d(TAG, "发现未识别 BLE 设备 name=" + name + " addr=" + device.getAddress());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            // 扫描注册失败（车机锁/解锁频繁开关蓝牙后，协议栈常返回 APPLICATION_REGISTRATION_FAILED）。
            // 必须把 scanning 复位为 false，否则标记残留为 true 会让后续 startScan 直接跳过、永不重扫，
            // 表现为系统层「0 BLE apps registered」却再也连不上。复位后由看门狗 10s 周期重试。
            RobotLog.w(TAG, "扫描启动失败 errorCode=" + errorCode + "，复位扫描标记待重试");
            scanning = false;
        }
    };

    // ---------------- 连接 ----------------

    public synchronized void connect(BluetoothDevice device, int type) {
        BleEndpoint ep = endpoint(type);
        if (ep == null) {
            return;
        }
        // 蓝牙未开（车机睡眠中）时连也连不上，且会把状态推到「连接中」卡死，唤醒后扫到设备也
        // 因状态非 DISCONNECTED 而不再重连。此时直接跳过，保持 DISCONNECTED 等 BT 重开后重扫重连。
        if (!isBluetoothEnabled()) {
            RobotLog.d(TAG, "蓝牙未开启，跳过连接 type=" + type);
            return;
        }
        if (ep.gatt != null) {
            ep.gatt.close();
        }
        ep.device = device;
        ep.state = BluetoothProfile.STATE_CONNECTING;
        ep.consecutiveWriteFailures = 0;
        notifyStateChange(type, ep.state);
        RobotLog.d(TAG, "连接端点 " + type + ": " + device.getAddress());
        ep.gatt = device.connectGatt(context, false, new EndpointCallback(ep));
        armConnectWatchdog(ep);
    }

    /** 武装连接看门狗：超时仍未就绪即强制释放重连，自愈「连接中卡死永不回调」。 */
    private void armConnectWatchdog(final BleEndpoint ep) {
        cancelConnectWatchdog(ep);
        ep.connectWatchdog = new Runnable() {
            @Override
            public void run() {
                ep.connectWatchdog = null;
                if (!ep.isReady() && ep.gatt != null) {
                    RobotLog.w(TAG, "type=" + ep.type + " 连接超时仍未就绪，强制释放重连");
                    forceTeardown(ep);
                }
            }
        };
        mainHandler.postDelayed(ep.connectWatchdog, CONNECT_TIMEOUT_MS);
    }

    /** 撤销连接看门狗（到达就绪/断开终态或复位时调用）。 */
    private void cancelConnectWatchdog(BleEndpoint ep) {
        if (ep.connectWatchdog != null) {
            mainHandler.removeCallbacks(ep.connectWatchdog);
            ep.connectWatchdog = null;
        }
    }

    public synchronized void disconnect(int type) {
        BleEndpoint ep = endpoint(type);
        if (ep != null && ep.gatt != null) {
            ep.gatt.disconnect();
        }
    }

    public int getConnectionState(int type) {
        BleEndpoint ep = endpoint(type);
        return ep != null ? ep.state : BluetoothProfile.STATE_DISCONNECTED;
    }

    public BluetoothDevice getConnectedDevice(int type) {
        BleEndpoint ep = endpoint(type);
        return ep != null ? ep.device : null;
    }

    // ---------------- 发送 ----------------

    public void sendMotion(byte[] data) {
        send(TYPE_MOTION, data);
    }

    public void sendEyes(byte[] data) {
        send(TYPE_EYES, data);
    }

    /** 运动板与眼睛板是否都已就绪。要求两端都连上才发指令，避免单端在线时半截动作。 */
    public boolean bothReady() {
        return motion.isReady() && eyes.isReady();
    }

    public void send(int type, byte[] data) {
        BleEndpoint ep = endpoint(type);
        if (ep == null) {
            return;
        }
        // 仿竞品「断链暂停不丢弃」：即便端点暂未就绪也先入队缓存，连上后由 processNextWrite 补发。
        // 队列封顶丢最旧 + 入队时效淘汰（STALE_WRITE_MS），避免长断连后唤醒灌入大量过期瞬时指令。
        // 按端点各自就绪各自发：哪端连上就能控哪端，一端僵死不锁死另一端。
        while (writeQueue.size() >= WRITE_QUEUE_CAP) {
            writeQueue.poll();
        }
        writeQueue.offer(new WriteTask(ep, data));
        // 总是踢一脚；processNextWrite 自带「在途写判重」(pendingTask)，多余的消息无害。
        writeHandler.sendEmptyMessage(MSG_WRITE_NEXT);
        if (!ep.isReady()) {
            // 未就绪只是暂存待补发，不丢弃；节流告警（最多每 5s 一条），杜绝未连接时刷屏。
            long now = SystemClock.uptimeMillis();
            if (now - lastNotReadyWarnMs >= NOT_READY_WARN_INTERVAL_MS) {
                lastNotReadyWarnMs = now;
                RobotLog.w(TAG, "端点未就绪（运动=" + motion.isReady()
                        + " 眼睛=" + eyes.isReady() + "），暂存指令待连上补发 type=" + type);
            }
        }
    }

    /**
     * 取下一笔可发送的指令并写出。若已有在途写（pendingTask）则等回调/超时，绝不并发写。仅 writeThread 调用。
     *
     * <p>扫描队列取<b>首个所属端点已就绪</b>的指令发送：未就绪但仍新鲜的暂留队列待端点连上补发；
     * 超 {@link #STALE_WRITE_MS} 仍未送达的则丢弃（防唤醒后回放过期瞬时动作）。这样某端点未就绪
     * 不会阻塞另一已就绪端点的指令。
     */
    private void processNextWrite() {
        if (pendingTask != null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        WriteTask task = null;
        for (Iterator<WriteTask> it = writeQueue.iterator(); it.hasNext(); ) {
            WriteTask t = it.next();
            if (t.endpoint.isReady()) {
                task = t;
                it.remove();
                break;
            }
            if (now - t.createdMs > STALE_WRITE_MS) {
                it.remove(); // 端点迟迟未就绪，指令过期，丢弃避免日后灌旧动作。
            }
            // 否则保留：端点尚未就绪但指令仍新鲜，等连上后补发。
        }
        if (task == null) {
            // 当前无「就绪端点」的指令可发；待 send()/重连再次踢队列。
            return;
        }

        BleEndpoint ep = task.endpoint;
        BluetoothGattCharacteristic c = ep.writeChar;
        int props = c.getProperties();
        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
            // 仿竞品：优先带应答写。ATT 层确认送达，onCharacteristicWrite 的 status 真实可信，
            // 配合失败重试才能真正「不丢命令」；仅在特征不支持带应答时才退回无应答。
            c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        } else {
            c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        }
        c.setValue(task.data);
        boolean ok = ep.gatt.writeCharacteristic(c);
        if (ok) {
            // 写已提交。挂起等回调；同时上超时看门狗，回调若丢失则超时兜底，杜绝队列死锁。
            pendingTask = task;
            writeHandler.sendEmptyMessageDelayed(MSG_WRITE_TIMEOUT, WRITE_TIMEOUT_MS);
            RobotLog.d(TAG, "写 type=" + ep.type + " " + HexUtil.toHex(task.data));
            return;
        }

        // 提交失败（协议栈忙/链路异常）：重试或丢弃，并累计失败用于僵尸判定。
        handleWriteFailure(ep, task, "提交失败");
        writeHandler.sendEmptyMessageDelayed(MSG_WRITE_NEXT, WRITE_RETRY_DELAY_MS);
    }

    /** onCharacteristicWrite 回调（带 status）。取消超时，按结果推进队列。仅 writeThread 调用。 */
    private void onWriteDone(int status) {
        writeHandler.removeMessages(MSG_WRITE_TIMEOUT);
        WriteTask task = pendingTask;
        pendingTask = null;
        if (task != null) {
            BleEndpoint ep = task.endpoint;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                ep.consecutiveWriteFailures = 0;
            } else {
                handleWriteFailure(ep, task, "回调status=" + status);
            }
        }
        writeHandler.sendEmptyMessage(MSG_WRITE_NEXT);
    }

    /** 写出后回调迟迟不来：视为本笔失败，兜底推进队列，防止写队列永久卡死。仅 writeThread 调用。 */
    private void onWriteTimeout() {
        WriteTask task = pendingTask;
        pendingTask = null;
        if (task != null) {
            handleWriteFailure(task.endpoint, task, "回调超时");
        }
        writeHandler.sendEmptyMessage(MSG_WRITE_NEXT);
    }

    /** 统一的写失败处理：累计失败计数、按上限重试或丢弃、达阈值判定僵尸链路强制断开重连。 */
    private void handleWriteFailure(BleEndpoint ep, WriteTask task, String reason) {
        ep.consecutiveWriteFailures++;
        task.attempts++;
        if (task.attempts < MAX_WRITE_ATTEMPTS) {
            writeQueue.offerFirst(task);
            RobotLog.d(TAG, "写 type=" + ep.type + " " + HexUtil.toHex(task.data)
                    + " " + reason + "(重试 " + task.attempts + ")");
        } else {
            RobotLog.w(TAG, "写 type=" + ep.type + " " + HexUtil.toHex(task.data)
                    + " " + reason + "，超限丢弃");
        }
        if (ep.consecutiveWriteFailures >= ZOMBIE_LINK_FAILURES && ep.gatt != null) {
            RobotLog.w(TAG, "type=" + ep.type + " 连续写失败，判定僵尸链路，强制释放重连");
            ep.consecutiveWriteFailures = 0;
            forceTeardown(ep);
        }
    }

    /**
     * 硬释放一个端点的链路并安排重连。
     *
     * <p>僵尸链路（GATT 显示已连、物理已断）只调 {@code disconnect()} 往往收不到
     * DISCONNECTED 回调，于是状态永远卡在「已连」、{@code close()} 永不执行——连接槽不释放，
     * 机器人侧仍认为被车机占用而停止广播，谁也连不上，形成死锁。故此处不依赖回调，主动
     * {@code disconnect()+close()} 真正释放，复位状态后安排重连。
     */
    private void forceTeardown(final BleEndpoint ep) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                cancelConnectWatchdog(ep);
                BluetoothGatt g = ep.gatt;
                if (g != null) {
                    g.disconnect();
                    g.close();
                    ep.gatt = null;
                }
                ep.clearChars();
                ep.state = BluetoothProfile.STATE_DISCONNECTED;
                notifyStateChange(ep.type, ep.state);
                purgeWrites(ep);
                scheduleReconnect(ep);
            }
        });
    }

    /**
     * 端点掉线：清掉其在途写与超时看门狗，但<b>保留</b>排队指令（仿竞品「断链暂停不丢弃」）。
     *
     * <p>中断的在途写若仍新鲜则放回队首，端点重连后由 {@link #processNextWrite} 补发；积压的
     * 排队指令不再整批清空，交给 {@link #STALE_WRITE_MS} 时效淘汰过期项。这样锁车/睡眠等
     * 短暂断连窗口里发出的指令不会蒸发，连上即补发，根治「发命令机器人没反应」。
     */
    private void purgeWrites(final BleEndpoint ep) {
        writeHandler.post(new Runnable() {
            @Override
            public void run() {
                writeHandler.removeMessages(MSG_WRITE_TIMEOUT);
                if (pendingTask != null && pendingTask.endpoint == ep) {
                    WriteTask interrupted = pendingTask;
                    pendingTask = null;
                    if (SystemClock.uptimeMillis() - interrupted.createdMs <= STALE_WRITE_MS) {
                        writeQueue.offerFirst(interrupted);
                    }
                }
                writeHandler.sendEmptyMessage(MSG_WRITE_NEXT);
            }
        });
    }

    // ---------------- GATT 回调（单一实现，参数化端点） ----------------

    private final class EndpointCallback extends BluetoothGattCallback {
        private final BleEndpoint ep;

        EndpointCallback(BleEndpoint ep) {
            this.ep = ep;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            ep.state = newState;
            notifyStateChange(ep.type, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                cancelConnectWatchdog(ep);
                ep.clearChars();
                // 必须 close() 真正释放连接槽，否则机器人侧仍认为被车机占用、不再广播，无法重连。
                gatt.close();
                if (ep.gatt == gatt) {
                    ep.gatt = null;
                }
                purgeWrites(ep);
                scheduleReconnect(ep);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            boolean ok = status == BluetoothGatt.GATT_SUCCESS;
            if (ok) {
                findCharacteristics(gatt, ep);
            }
            notifyServicesDiscovered(ep.type, ok && ep.writeChar != null);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            writeHandler.obtainMessage(MSG_WRITE_DONE, status, 0).sendToTarget();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            notifyDataReceived(ep.type, characteristic.getValue());
        }
    }

    private void findCharacteristics(BluetoothGatt gatt, BleEndpoint ep) {
        for (BluetoothGattService service : gatt.getServices()) {
            for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                String uuid = c.getUuid().toString().toLowerCase();
                if (uuid.contains(ep.writeUuidFrag)) {
                    ep.writeChar = c;
                    // 已拿到写特征＝端点真正就绪，撤销连接看门狗，避免误判超时。
                    cancelConnectWatchdog(ep);
                    RobotLog.d(TAG, "type=" + ep.type + " 写特征 " + uuid
                            + " props=" + c.getProperties());
                    // 端点就绪即踢一脚写队列：把断链期间暂存的指令补发出去。
                    writeHandler.sendEmptyMessage(MSG_WRITE_NEXT);
                } else if (uuid.contains(ep.notifyUuidFrag)) {
                    ep.notifyChar = c;
                    gatt.setCharacteristicNotification(c, true);
                    RobotLog.d(TAG, "type=" + ep.type + " 通知特征 " + uuid);
                }
            }
        }
    }

    private void scheduleReconnect(final BleEndpoint ep) {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // 仅在蓝牙开启时重连；BT 关（睡眠）期间重连只会把状态卡到「连接中」，
                // 留 DISCONNECTED 给 BT 重开后的扫描重连更稳。
                if (ep.device != null && ep.state == BluetoothProfile.STATE_DISCONNECTED
                        && isBluetoothEnabled()) {
                    RobotLog.d(TAG, "自动重连 type=" + ep.type);
                    connect(ep.device, ep.type);
                }
            }
        }, RECONNECT_DELAY_MS);
    }

    // ---------------- 主线程回调分发 ----------------

    private void notifyDeviceFound(final BluetoothDevice device, final int type) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onDeviceFound(device, type);
                }
            }
        });
    }

    private void notifyStateChange(final int type, final int state) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onConnectionStateChanged(type, state);
                }
            }
        });
    }

    private void notifyServicesDiscovered(final int type, final boolean success) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onServicesDiscovered(type, success);
                }
            }
        });
    }

    private void notifyDataReceived(final int type, final byte[] data) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onDataReceived(type, data);
                }
            }
        });
    }
}
