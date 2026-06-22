package com.midbows.zkvision.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.midbows.zkvision.behavior.BehaviorEngine;
import com.midbows.zkvision.ble.BleAutoConnector;
import com.midbows.zkvision.ble.BleManager;
import com.midbows.zkvision.ble.RobotController;
import com.midbows.zkvision.data.SettingsManager;
import com.midbows.zkvision.signal.DoorMonitor;
import com.midbows.zkvision.signal.LogcatVoiceMonitor;
import com.midbows.zkvision.signal.MusicMonitor;
import com.midbows.zkvision.signal.NavMonitor;
import com.midbows.zkvision.signal.RuleEngine;
import com.midbows.zkvision.signal.SignalSource;
import com.midbows.zkvision.ui.MainActivity;
import com.midbows.zkvision.util.RobotLog;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 前台服务：构建对象图（BLE → 控制器 → 行为引擎 → 信号源）并按设置启停信号源。
 *
 * <p>对象装配集中在此处，各层依赖单向、互不反向依赖。
 */
public final class RobotService extends Service {

    private static final String TAG = "RobotService";
    private static final String CHANNEL_ID = "zkvision_service";
    private static final int NOTIFICATION_ID = 1001;

    private BleAutoConnector autoConnector;
    private BehaviorEngine behaviorEngine;

    /** 设置开关 key → 对应信号源。 */
    private final Map<String, SignalSource> sources = new LinkedHashMap<>();

    private final BroadcastReceiver configReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SettingsManager.ACTION_CONFIG_CHANGED.equals(intent.getAction())) {
                applySettings();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        RobotLog.d(TAG, "服务创建");

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        BleManager ble = BleManager.getInstance(this);
        RobotController robot = new RobotController(ble);
        behaviorEngine = new BehaviorEngine(robot);
        behaviorEngine.start();

        autoConnector = new BleAutoConnector(this, ble);
        autoConnector.setMotionReadyListener(behaviorEngine::onMotionLinkReady);
        autoConnector.start();

        sources.put(SettingsManager.KEY_VOICE, new LogcatVoiceMonitor(this, behaviorEngine));
        sources.put(SettingsManager.KEY_MUSIC, new MusicMonitor(this, behaviorEngine));
        sources.put(SettingsManager.KEY_DOOR, new DoorMonitor(this, behaviorEngine));
        sources.put(SettingsManager.KEY_NAV, new NavMonitor(this, behaviorEngine));
        sources.put(SettingsManager.KEY_CARSTATE, new RuleEngine(this, behaviorEngine));

        applySettings();

        registerReceiver(configReceiver, new IntentFilter(SettingsManager.ACTION_CONFIG_CHANGED));
    }

    private void applySettings() {
        SettingsManager settings = SettingsManager.getInstance(this);
        for (Map.Entry<String, SignalSource> e : sources.entrySet()) {
            if (settings.isEnabled(e.getKey())) {
                e.getValue().start();
            } else {
                e.getValue().stop();
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "车载机器人服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, flags);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("车载机器人")
                .setContentText("正在监听车机事件")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(pi)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        RobotLog.d(TAG, "服务销毁");
        unregisterReceiver(configReceiver);
        for (SignalSource s : sources.values()) {
            s.stop();
        }
        behaviorEngine.stop();
        autoConnector.stop();
    }
}
