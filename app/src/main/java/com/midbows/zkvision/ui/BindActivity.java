package com.midbows.zkvision.ui;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.midbows.zkvision.ble.BleManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备绑定（开源支持）：扫描周围<b>全部</b> BLE 设备，用户手动指派哪台是运动板、哪台是氛围灯。
 *
 * <p>本 app 准备开源，别人的机器人广播名/MAC 与本机不同，硬编码名前缀（MIDBOW1S/ET-ROBOT-01）
 * 不通用。绑定后 {@link BleManager} 优先按 MAC 连接，未绑定时回退名前缀。固件级写/通知特征 UUID
 * 跨单元一致，只需把 MAC 映射对即可。界面在代码中构建。
 */
@SuppressLint("MissingPermission")
public final class BindActivity extends AppCompatActivity implements BleManager.RawScanListener {

    private static final int PAD = dp(16);
    private static final int GAP = dp(8);
    private static final int BTN_H = dp(56);

    private BleManager ble;
    private TextView bindingLabel;
    private LinearLayout deviceList;
    /** 已展示设备：MAC → 行视图，去重并原位刷新 RSSI。 */
    private final Map<String, TextView> rows = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ble = BleManager.getInstance(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = vbox();
        root.setPadding(PAD, PAD, PAD, PAD);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(backBar());
        root.addView(title("设备绑定"));
        root.addView(hint("扫描周围全部蓝牙设备，把你的机器人指派为「机器人本体」或「氛围灯」。"
                + "未绑定时按出厂名前缀自动识别。"));

        bindingLabel = new TextView(this);
        bindingLabel.setTextSize(15);
        bindingLabel.setPadding(0, dp(12), 0, 0);
        root.addView(bindingLabel);
        refreshBindingLabel();

        root.addView(buttonRow(
                btn("开始扫描", () -> ble.startScan()),
                btn("停止扫描", () -> ble.stopScan()),
                btn("清除机器人本体", () -> {
                    ble.bindDevice(BleManager.TYPE_MOTION, "");
                    refreshBindingLabel();
                    toast("已清除机器人本体绑定");
                }),
                btn("清除氛围灯", () -> {
                    ble.bindDevice(BleManager.TYPE_EYES, "");
                    refreshBindingLabel();
                    toast("已清除氛围灯绑定");
                })));

        root.addView(section("扫描到的设备"));
        deviceList = vbox();
        root.addView(deviceList);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ble.setRawScanListener(this);
        ble.startScan();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ble.setRawScanListener(null);
    }

    // 原始扫描回调（已在主线程派发）。
    @Override
    public void onRawDevice(BluetoothDevice device, String name, int rssi) {
        if (device == null || device.getAddress() == null) {
            return;
        }
        String mac = device.getAddress();
        String shown = (name == null || name.isEmpty() ? "(无名)" : name)
                + "\n" + mac + "   " + rssi + " dBm";
        TextView existing = rows.get(mac);
        if (existing != null) {
            existing.setText(shown);
            return;
        }
        // 新设备：一行信息 + 两个指派按钮。
        LinearLayout rowBox = vbox();
        rowBox.setPadding(0, GAP, 0, 0);

        TextView info = new TextView(this);
        info.setTextSize(14);
        info.setText(shown);
        rowBox.addView(info);

        rowBox.addView(buttonRow(
                btn("设为机器人本体", () -> {
                    ble.bindDevice(BleManager.TYPE_MOTION, mac);
                    refreshBindingLabel();
                    toast("已绑定机器人本体 → " + mac);
                }),
                btn("设为氛围灯", () -> {
                    ble.bindDevice(BleManager.TYPE_EYES, mac);
                    refreshBindingLabel();
                    toast("已绑定氛围灯 → " + mac);
                })));

        rows.put(mac, info);
        deviceList.addView(rowBox);
    }

    private void refreshBindingLabel() {
        String motion = ble.getBoundMac(BleManager.TYPE_MOTION);
        String light = ble.getBoundMac(BleManager.TYPE_EYES);
        bindingLabel.setText("当前绑定：\n机器人本体 = "
                + (motion.isEmpty() ? "未绑定（按名前缀 MIDBOW1S）" : motion)
                + "\n氛围灯 = "
                + (light.isEmpty() ? "未绑定（按名前缀 ET-ROBOT-01）" : light));
    }

    // ---------------- 小工具 ----------------

    private View backBar() {
        LinearLayout bar = hbox();
        View b = btn("← 返回", this::finish);
        bar.addView(b, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, BTN_H));
        return bar;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private View buttonRow(View... buttons) {
        LinearLayout row = hbox();
        for (View b : buttons) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, BTN_H, 1f);
            lp.rightMargin = GAP;
            row.addView(b, lp);
        }
        return row;
    }

    private View btn(String text, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(15);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, GAP, 0, 0);
        return l;
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(24);
        return t;
    }

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(19);
        t.setPadding(0, dp(20), 0, dp(4));
        return t;
    }

    private TextView hint(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(Color.GRAY);
        t.setPadding(0, dp(4), 0, 0);
        return t;
    }

    private static int dp(int v) {
        return Math.round(v * android.content.res.Resources.getSystem()
                .getDisplayMetrics().density);
    }
}
