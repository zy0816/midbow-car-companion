package com.midbows.zkvision.ui;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.midbows.zkvision.R;
import com.midbows.zkvision.behavior.EyeMood;
import com.midbows.zkvision.behavior.RobotChoreographer;
import com.midbows.zkvision.ble.BleManager;
import com.midbows.zkvision.ble.RobotController;
import com.midbows.zkvision.data.SettingsManager;
import com.midbows.zkvision.protocol.MotionProtocol;
import com.midbows.zkvision.service.RobotService;
import com.midbows.zkvision.signal.CarStateProbe;
import com.midbows.zkvision.util.RobotLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 车载机器人控制台：连接状态、信号开关、测试台、实时日志。横屏 + 大触控适配车机。
 */
public final class MainActivity extends AppCompatActivity implements RobotLog.Sink {

    private static final int REQ_PERMS = 1;
    private static final int CONNECTED_COLOR = 0xFF34C759;
    private static final int DISCONNECTED_COLOR = 0xFFB0B4BA;
    private static final long STATUS_POLL_MS = 1000;
    private static final int LOG_MAX_CHARS = 8000;

    private BleManager ble;
    private RobotController robot;
    private RobotChoreographer choreo;
    private SettingsManager settings;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private View dotMotion;
    private View dotEyes;
    private TextView stateMotion;
    private TextView stateEyes;
    private MaterialButton btnBindMotion;
    private MaterialButton btnBindEyes;
    private TextView tvLog;
    private ScrollView scrollLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ble = BleManager.getInstance(this);
        robot = new RobotController(ble);
        choreo = new RobotChoreographer(robot);
        settings = SettingsManager.getInstance(this);

        bindViews();
        bindSignalSwitches();
        bindTestPad();

        requestRuntimePermissions();
    }

    private void bindViews() {
        dotMotion = findViewById(R.id.dotMotion);
        dotEyes = findViewById(R.id.dotEyes);
        stateMotion = findViewById(R.id.stateMotion);
        stateEyes = findViewById(R.id.stateEyes);
        btnBindMotion = findViewById(R.id.btnBindMotion);
        btnBindEyes = findViewById(R.id.btnBindEyes);
        tvLog = findViewById(R.id.tvLog);
        scrollLog = findViewById(R.id.scrollLog);
        btnBindMotion.setOnClickListener(v -> bindConnected(BleManager.TYPE_MOTION, "机器人本体"));
        btnBindEyes.setOnClickListener(v -> bindConnected(BleManager.TYPE_EYES, "氛围灯"));
        findViewById(R.id.btnScan).setOnClickListener(v -> ble.startScan());
        findViewById(R.id.btnAutomation).setOnClickListener(v ->
                startActivity(new Intent(this, AutomationActivity.class)));
        findViewById(R.id.btnConsole).setOnClickListener(v ->
                startActivity(new Intent(this, ConsoleActivity.class)));
        findViewById(R.id.btnBind).setOnClickListener(v ->
                startActivity(new Intent(this, BindActivity.class)));
    }

    private void bindSignalSwitches() {
        bindSwitch(R.id.swVoice, SettingsManager.KEY_VOICE);
        bindSwitch(R.id.swMusic, SettingsManager.KEY_MUSIC);
        bindSwitch(R.id.swDoor, SettingsManager.KEY_DOOR);
        bindSwitch(R.id.swNav, SettingsManager.KEY_NAV);
        bindSwitch(R.id.swCarState, SettingsManager.KEY_CARSTATE);
    }

    private void bindSwitch(int id, String key) {
        MaterialSwitch sw = findViewById(id);
        sw.setChecked(settings.isEnabled(key));
        sw.setOnCheckedChangeListener((button, checked) -> {
            settings.setEnabled(key, checked);
            sendBroadcast(new Intent(SettingsManager.ACTION_CONFIG_CHANGED));
        });
    }

    private void bindTestPad() {
        findViewById(R.id.btnLeft).setOnClickListener(v -> choreo.turn(true));
        findViewById(R.id.btnRight).setOnClickListener(v -> choreo.turn(false));
        findViewById(R.id.btnCenter).setOnClickListener(v -> choreo.center());
        findViewById(R.id.btnNod).setOnClickListener(v -> choreo.nod());
        findViewById(R.id.btnShake).setOnClickListener(v -> choreo.shake());
        findViewById(R.id.btnMotorOn).setOnClickListener(v -> robot.motorOn());
        findViewById(R.id.btnMotorOff).setOnClickListener(v -> robot.motorOff());
        findViewById(R.id.btnProbe).setOnClickListener(v -> new CarStateProbe(this).run());

        bindMood(R.id.moodHappy, EyeMood.HAPPY);
        bindMood(R.id.moodListen, EyeMood.LISTENING);
        bindMood(R.id.moodThink, EyeMood.THINKING);
        bindMood(R.id.moodSpeak, EyeMood.SPEAKING);
        bindMood(R.id.moodAlert, EyeMood.ALERT);
        bindMood(R.id.moodMusic, EyeMood.MUSIC);
        bindMood(R.id.moodOff, EyeMood.OFF);
    }

    private void bindMood(int id, EyeMood mood) {
        findViewById(id).setOnClickListener(v -> choreo.mood(mood));
    }

    // ---------------- 运行时权限 ----------------

    private void requestRuntimePermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfMissing(needed, Manifest.permission.BLUETOOTH_SCAN);
            addIfMissing(needed, Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            addIfMissing(needed, Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(needed, Manifest.permission.POST_NOTIFICATIONS);
        }
        if (needed.isEmpty()) {
            startEngine();
        } else {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERMS);
        }
    }

    private void addIfMissing(List<String> list, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
            list.add(permission);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            startEngine();
        }
    }

    private void startEngine() {
        Intent intent = new Intent(this, RobotService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    // ---------------- 状态轮询 ----------------

    private final Runnable statusPoll = new Runnable() {
        @Override
        public void run() {
            updateDot(dotMotion, stateMotion, ble.getConnectionState(BleManager.TYPE_MOTION));
            updateDot(dotEyes, stateEyes, ble.getConnectionState(BleManager.TYPE_EYES));
            updateBindButton(btnBindMotion, BleManager.TYPE_MOTION);
            updateBindButton(btnBindEyes, BleManager.TYPE_EYES);
            handler.postDelayed(this, STATUS_POLL_MS);
        }
    };

    private void updateDot(View dot, TextView label, int state) {
        boolean connected = state == BluetoothProfile.STATE_CONNECTED;
        dot.getBackground().mutate().setTint(connected ? CONNECTED_COLOR : DISCONNECTED_COLOR);
        label.setText(connected ? "已连接" : "未连接");
        label.setTextColor(connected ? CONNECTED_COLOR : DISCONNECTED_COLOR);
    }

    /** 已连接才可绑定；当前设备即已绑定设备时显示「已绑定」并禁用。 */
    private void updateBindButton(MaterialButton btn, int type) {
        BluetoothDevice dev = ble.getConnectedDevice(type);
        boolean connected = ble.getConnectionState(type) == BluetoothProfile.STATE_CONNECTED
                && dev != null;
        if (!connected) {
            btn.setEnabled(false);
            btn.setText("绑定此设备");
            return;
        }
        if (dev.getAddress().equals(ble.getBoundMac(type))) {
            btn.setEnabled(false);
            btn.setText("已绑定");
        } else {
            btn.setEnabled(true);
            btn.setText("绑定此设备");
        }
    }

    /** 把当前已连接的设备 MAC 绑定为该端点（一键绑定，免去扫描指派）。 */
    private void bindConnected(int type, String name) {
        BluetoothDevice dev = ble.getConnectedDevice(type);
        if (dev == null || ble.getConnectionState(type) != BluetoothProfile.STATE_CONNECTED) {
            Toast.makeText(this, "请先连接" + name + "再绑定", Toast.LENGTH_SHORT).show();
            return;
        }
        ble.bindDevice(type, dev.getAddress());
        Toast.makeText(this, "已绑定 " + name + " → " + dev.getAddress(), Toast.LENGTH_SHORT).show();
    }

    // ---------------- 日志 ----------------

    @Override
    public void onLog(String line) {
        tvLog.append(line + "\n");
        if (tvLog.length() > LOG_MAX_CHARS) {
            CharSequence text = tvLog.getText();
            tvLog.setText(text.subSequence(text.length() - LOG_MAX_CHARS / 2, text.length()));
        }
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    protected void onResume() {
        super.onResume();
        RobotLog.addSink(this);
        handler.post(statusPoll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        RobotLog.removeSink(this);
        handler.removeCallbacks(statusPoll);
    }
}
