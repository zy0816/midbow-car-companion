package com.midbows.zkvision.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.midbows.zkvision.ble.BleManager;
import com.midbows.zkvision.ble.RobotController;
import com.midbows.zkvision.protocol.EyesProtocol;

import java.util.function.Consumer;

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

        root.addView(backBar());
        root.addView(title("设备控制台"));
        root.addView(hint("照搬小程序的直接控制；不含 OTA 文件传输（刷砖风险）。"
                + "蓝牙为单向下发，提示仅表示命令已发出。"));

        buildMotionSection(root);
        buildExpressionSection(root);
        buildLightSection(root);
    }

    // ---------------- 表情（FE55 表情字节，眼屏） ----------------

    /** 厂家已确认的 18 个表情：{hex, 名称}。0x13 音乐仅动作无表情，单列出来标注。 */
    private static final String[][] EXPRESSIONS = {
            {"06", "生气"}, {"07", "笑一个"}, {"08", "比个心"}, {"09", "眨眼睛"},
            {"0A", "开心"}, {"0B", "难过"}, {"0C", "再眨眼睛"}, {"0D", "装酷"},
            {"0E", "坏笑"}, {"0F", "放烟花"}, {"10", "石头剪刀布"}, {"11", "害怕"},
            {"12", "晕头转向"}, {"13", "音乐(仅动作)"}, {"1A", "害羞"}, {"23", "情人节快乐"},
            {"24", "玫瑰花"}, {"26", "吃月饼"},
    };

    private void buildExpressionSection(LinearLayout root) {
        root.addView(section("表情"));
        root.addView(hint("眼屏表情，经运动板下发（FE 55 10 [字节] 55 FE）。"));

        LinearLayout row = null;
        for (int i = 0; i < EXPRESSIONS.length; i++) {
            if (i % 4 == 0) {
                row = hbox();
                root.addView(row);
            }
            final byte cmd = (byte) Integer.parseInt(EXPRESSIONS[i][0], 16);
            final String name = EXPRESSIONS[i][1];
            Button b = makeButton(name);
            b.setOnClickListener(v -> {
                robot.playExpression(cmd);
                toast("已发送表情：" + name);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, BTN_H, 1f);
            lp.rightMargin = GAP;
            row.addView(b, lp);
        }

        // 字节探索器：扫描「好热/好冷」等未公开表情字节（对照小程序「特殊指令」）。
        root.addView(section("表情探索（实车扫未公开字节）"));
        root.addView(hint("输入 1 字节十六进制（如 14）发 FE5510[XX]55FE，看眼屏出什么；"
                + "或用「上/下一字节」逐个扫，记下好热/好冷等对应字节。"
                + "已知：0x25=摇头晃脑(动作非表情)、0x26=吃月饼。"));

        final EditText hexInput = new EditText(this);
        hexInput.setInputType(InputType.TYPE_CLASS_TEXT);
        hexInput.setText("14");
        hexInput.setTextSize(16);

        Runnable sendHex = () -> {
            String s = hexInput.getText().toString().trim();
            try {
                int v = Integer.parseInt(s, 16) & 0xFF;
                hexInput.setText(String.format("%02X", v));
                robot.playExpression((byte) v);
                toast("已发送字节：0x" + String.format("%02X", v));
            } catch (NumberFormatException e) {
                toast("请输入有效的十六进制字节（00~FF）");
            }
        };

        LinearLayout sendRow = hbox();
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(0, BTN_H, 2f);
        inLp.rightMargin = GAP;
        sendRow.addView(hexInput, inLp);
        Button sendBtn = makeButton("发送");
        sendBtn.setOnClickListener(v -> sendHex.run());
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, BTN_H, 1f);
        sendRow.addView(sendBtn, sLp);
        root.addView(sendRow);

        root.addView(buttonRow(
                stepBtn("上一字节", hexInput, -1),
                stepBtn("下一字节", hexInput, +1)));
    }

    /** 字节步进按钮：把输入框的十六进制值 ±1 后立即发送，便于逐个扫描。 */
    private View stepBtn(String text, EditText hexInput, int delta) {
        Button b = makeButton(text);
        b.setOnClickListener(v -> {
            int cur;
            try {
                cur = Integer.parseInt(hexInput.getText().toString().trim(), 16);
            } catch (NumberFormatException e) {
                cur = 0;
            }
            int next = (cur + delta) & 0xFF;
            hexInput.setText(String.format("%02X", next));
            robot.playExpression((byte) next);
            toast("已发送字节：0x" + String.format("%02X", next));
        });
        return b;
    }

    // ---------------- 运动板（FE55） ----------------

    private void buildMotionSection(LinearLayout root) {
        root.addView(section("机器人本体"));

        root.addView(buttonRow(
                btn("上", () -> robot.look(com.midbows.zkvision.protocol.MotionProtocol.DIR_UP)),
                btn("下", () -> robot.look(com.midbows.zkvision.protocol.MotionProtocol.DIR_DOWN)),
                btn("左", () -> robot.look(com.midbows.zkvision.protocol.MotionProtocol.DIR_LEFT)),
                btn("右", () -> robot.look(com.midbows.zkvision.protocol.MotionProtocol.DIR_RIGHT)),
                btn("中/停", robot::center)));

        // 本质是开关的项改用单个 Switch（去掉「开/关」双按钮）。设备无状态回读，初值取常见默认。
        root.addView(switchRow(
                sw("电机供电", true, on -> { if (on) robot.motorOn(); else robot.motorOff(); }),
                sw("声音", true, robot::soundEnabled),
                sw("唤醒音", true, robot::wakeSoundEnabled),
                sw("自适应律动", false, robot::selfAdaptiveRhythm)));

        root.addView(buttonRow(
                btn("律动加速", robot::rhythmFaster),
                btn("律动减速", robot::rhythmSlower),
                btn("声源定位", robot::modeLocalize),
                btn("专用词", robot::modeKeyword)));

        root.addView(buttonRow(
                btn("特殊指令", robot::specialDefault),
                btn("快捷指令", robot::shortcut),
                btn("摇头晃脑", robot::wobble),
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
                toast("已发送：" + seekBar.getProgress());
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
        b.setOnClickListener(v -> {
            action.run();
            toast("已发送：" + text);
        });
        return b;
    }

    // ---------------- 返回键 / 开关行 / 提示 ----------------

    /** 二级页统一返回键：蓝牙单向下发，提示仅表示命令已发出。 */
    private View backBar() {
        Button b = makeButton("← 返回");
        b.setOnClickListener(v -> finish());
        LinearLayout bar = hbox();
        bar.addView(b, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    /** 本质是开关的项：单个 Switch（替代「开/关」双按钮）。设备无状态回读，初值取常见默认。 */
    private MaterialSwitch sw(String name, boolean initial, Consumer<Boolean> sink) {
        MaterialSwitch s = new MaterialSwitch(this);
        s.setText(name);
        s.setTextSize(16);
        s.setChecked(initial);
        s.setOnCheckedChangeListener((button, checked) -> {
            sink.accept(checked);
            toast(name + (checked ? "：开" : "：关"));
        });
        return s;
    }

    private View switchRow(View... switches) {
        LinearLayout col = vbox();
        col.setPadding(0, GAP, 0, 0);
        for (View s : switches) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = GAP;
            col.addView(s, lp);
        }
        return col;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
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
