package com.midbows.zkvision.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.midbows.zkvision.ble.BleManager;
import com.midbows.zkvision.ble.RobotController;
import com.midbows.zkvision.protocol.EyesProtocol;

/**
 * 设备控制台：复刻微信小程序的<b>全部</b>直接控制功能，使其可脱离小程序使用。
 *
 * <p>分两大区：运动板（FE55）与氛围灯（a1，机器人底部氛围灯，非眼睛）。界面在代码中构建，
 * 避免上百个资源 ID 的易错匹配。出于安全<b>不</b>含 OTA 文件传输（刷砖风险），其余命令照搬小程序。
 */
public final class ConsoleActivity extends AppCompatActivity {

    private static final int PAD = dp(16);
    private static final int GAP = dp(8);
    private static final int BTN_H = dp(64);

    private RobotController robot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        robot = new RobotController(BleManager.getInstance(this));

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = vbox();
        root.setPadding(PAD, PAD, PAD, PAD);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(title("设备控制台"));
        root.addView(hint("照搬小程序的直接控制；不含 OTA 文件传输（刷砖风险）。"));

        buildMotionSection(root);
        buildLightSection(root);
    }

    // ---------------- 运动板（FE55） ----------------

    private void buildMotionSection(LinearLayout root) {
        root.addView(section("运动板"));

        root.addView(buttonRow(
                btn("上", () -> robot.look(com.midbows.zkvision.protocol.MotionProtocol.DIR_UP)),
                btn("下", () -> robot.look(com.midbows.zkvision.protocol.MotionProtocol.DIR_DOWN)),
                btn("左", () -> robot.look(com.midbows.zkvision.protocol.MotionProtocol.DIR_LEFT)),
                btn("右", () -> robot.look(com.midbows.zkvision.protocol.MotionProtocol.DIR_RIGHT)),
                btn("中/停", robot::center)));

        root.addView(buttonRow(
                btn("电机通电", robot::motorOn),
                btn("电机断电", robot::motorOff),
                btn("声音开", () -> robot.soundEnabled(true)),
                btn("声音关", () -> robot.soundEnabled(false))));

        root.addView(buttonRow(
                btn("唤醒音开", () -> robot.wakeSoundEnabled(true)),
                btn("唤醒音关", () -> robot.wakeSoundEnabled(false)),
                btn("律动加速", robot::rhythmFaster),
                btn("律动减速", robot::rhythmSlower)));

        root.addView(buttonRow(
                btn("自适应律动开", () -> robot.selfAdaptiveRhythm(true)),
                btn("自适应律动关", () -> robot.selfAdaptiveRhythm(false)),
                btn("声源定位", robot::modeLocalize),
                btn("专用词", robot::modeKeyword)));

        root.addView(buttonRow(
                btn("特殊指令", robot::specialDefault),
                btn("快捷指令", robot::shortcut),
                btn("查询动作池", robot::randomPoolQuery)));

        // 随机动作池：5 位掩码，每位一个开关，组合后下发。
        final int[] mask = {0x1F};
        LinearLayout maskRow = hbox();
        final TextView maskLabel = new TextView(this);
        maskLabel.setText(maskText(mask[0]));
        for (int i = 0; i < 5; i++) {
            final int bit = 1 << i;
            Button b = makeButton("动作" + (i + 1));
            b.setOnClickListener(v -> {
                mask[0] ^= bit;
                maskLabel.setText(maskText(mask[0]));
                robot.randomPoolSet(mask[0]);
            });
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, BTN_H, 1f);
            lp.rightMargin = GAP;
            maskRow.addView(b, lp);
        }
        root.addView(maskRow);
        maskLabel.setPadding(0, GAP, 0, 0);
        root.addView(maskLabel);
    }

    private String maskText(int mask) {
        return "随机动作池掩码：0x" + Integer.toHexString(mask & 0x1F).toUpperCase()
                + "（点上方动作位切换）";
    }

    // ---------------- 氛围灯（a1） ----------------

    private void buildLightSection(LinearLayout root) {
        root.addView(section("氛围灯（机器人底部氛围灯）"));

        root.addView(buttonRow(
                colorBtn("红", 0xFF0000),
                colorBtn("绿", 0x00FF00),
                colorBtn("蓝", 0x0000FF),
                colorBtn("黄", 0xFFFF00)));
        root.addView(buttonRow(
                colorBtn("青", 0x00FFFF),
                colorBtn("紫", 0xFF00FF),
                colorBtn("白", 0xFFFFFF),
                colorBtn("暖白", 0xFFB070)));

        // 亮度
        root.addView(label("亮度"));
        root.addView(seekRow(255, 200, v -> robot.setAmbientBrightness(v)));

        root.addView(buttonRow(
                btn("自动亮度开", () -> robot.ambientAutoBrightness(true)),
                btn("自动亮度关", () -> robot.ambientAutoBrightness(false)),
                btn("同步时间", robot::ambientSyncTime),
                btn("状态查询", robot::ambientStatusQuery)));

        root.addView(buttonRow(
                btn("校准", robot::ambientCalibrate),
                btn("重启", robot::ambientReboot),
                btn("复位", robot::ambientReset),
                btn("激活", robot::ambientActivate)));

        // 灵敏度：6 项，各一条滑杆。range 见 EyesProtocol。
        root.addView(section("灵敏度"));
        addSensitivity(root, "左转(3~60)", EyesProtocol.SENS_TURN_LEFT, 60, 30);
        addSensitivity(root, "右转(3~60)", EyesProtocol.SENS_TURN_RIGHT, 60, 30);
        addSensitivity(root, "加速(0~60)", EyesProtocol.SENS_ACCEL, 60, 30);
        addSensitivity(root, "刹车(0~60)", EyesProtocol.SENS_BRAKE, 60, 30);
        addSensitivity(root, "静音(1~999)", EyesProtocol.SENS_MUTE, 999, 500);
        addSensitivity(root, "响度(1~999)", EyesProtocol.SENS_LOUDNESS, 999, 500);
    }

    private void addSensitivity(LinearLayout root, String name, int param, int max, int def) {
        root.addView(label(name));
        root.addView(seekRow(max, def, v -> robot.setSensitivity(param, v)));
    }

    private View colorBtn(String name, int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return btn(name, () -> robot.setEyeColor(r, g, b, 200));
    }

    // ---------------- 滑杆行（SeekBar + 实时值 + 松手下发） ----------------

    private interface IntSink {
        void apply(int value);
    }

    private View seekRow(int max, int def, IntSink sink) {
        LinearLayout row = hbox();
        final TextView valueLabel = new TextView(this);
        valueLabel.setText(String.valueOf(def));
        valueLabel.setMinWidth(dp(56));
        valueLabel.setGravity(Gravity.CENTER);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        bar.setProgress(def);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueLabel.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sink.apply(seekBar.getProgress());
            }
        });
        LinearLayout.LayoutParams barLp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        barLp.gravity = Gravity.CENTER_VERTICAL;
        row.addView(bar, barLp);
        row.addView(valueLabel);
        return row;
    }

    // ---------------- 小工具 ----------------

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
        Button b = makeButton(text);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(16);
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
        l.setPadding(0, GAP, 0, 0);
        l.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setPadding(0, GAP, 0, 0);
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
