package com.midbows.zkvision.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 机器人用户设置（各信号源开关）。全项目唯一设置读写入口。
 */
public final class SettingsManager {

    public static final String ACTION_CONFIG_CHANGED = "com.midbows.zkvision.ACTION_CONFIG_CHANGED";

    private static final String PREFS = "zkvision_settings";

    public static final String KEY_VOICE = "monitor_voice";
    public static final String KEY_MUSIC = "monitor_music";
    public static final String KEY_DOOR = "monitor_door";
    public static final String KEY_NAV = "monitor_nav";
    /** 车辆状态联动总开关（氛围灯/加速/座位/驾驶模式/昼夜/冷热/碰撞/盲区/转向灯/胎压/充电）。 */
    public static final String KEY_CARSTATE = "monitor_carstate";

    /**
     * 设备绑定 MAC：运动板 / 氛围灯。开源后别人的机器人广播名/MAC 与本机不同，
     * 用户在「绑定」页扫描全部 BLE 后手动指派，存其 MAC 到此；BleManager 优先按 MAC 匹配，
     * 未绑定时回退到出厂名前缀（MIDBOW1S / ET-ROBOT-01）。空串=未绑定。
     */
    public static final String KEY_BIND_MOTION_MAC = "bind_motion_mac";
    public static final String KEY_BIND_LIGHT_MAC = "bind_light_mac";

    private final SharedPreferences prefs;
    private static SettingsManager instance;

    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context.getApplicationContext());
        }
        return instance;
    }

    private SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isEnabled(String key) {
        return prefs.getBoolean(key, true);
    }

    public void setEnabled(String key, boolean enabled) {
        prefs.edit().putBoolean(key, enabled).apply();
    }

    /** 读绑定 MAC；未绑定返回空串。 */
    public String getBoundMac(String key) {
        return prefs.getString(key, "");
    }

    /** 存绑定 MAC；传 null/空串清除绑定（回退名前缀）。 */
    public void setBoundMac(String key, String mac) {
        prefs.edit().putString(key, mac == null ? "" : mac).apply();
    }
}
