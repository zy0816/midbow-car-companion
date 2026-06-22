package com.midbows.zkvision.automation;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置预置场景目录：把项目里调好的车机联动作为「开箱即用」的预置规则。
 *
 * <p>每个 {@link BuiltinDef#key} 对应 {@code RuleEngine} 里一个调好的监听器（复用其去抖/标定/
 * 时序逻辑，零回归）。预置规则的触发/动作以 {@link #triggerText}/{@link #actionText} 文案展示，
 * 用户可开关、可删除；要做更细的自定义则新建自定义规则。氛围灯同步、座舱冷热是「持续同步」型，
 * 也以预置项形式管理但内部走各自专用监听器。
 */
public final class BuiltinCatalog {

    public static final class BuiltinDef {
        public final String key;
        public final String name;
        public final String triggerText;
        public final String actionText;

        BuiltinDef(String key, String name, String triggerText, String actionText) {
            this.key = key;
            this.name = name;
            this.triggerText = triggerText;
            this.actionText = actionText;
        }
    }

    private static final List<BuiltinDef> ALL = build();

    private BuiltinCatalog() {
    }

    public static List<BuiltinDef> all() {
        return ALL;
    }

    public static BuiltinDef byKey(String key) {
        for (BuiltinDef d : ALL) {
            if (d.key.equals(key)) {
                return d;
            }
        }
        return null;
    }

    private static List<BuiltinDef> build() {
        List<BuiltinDef> l = new ArrayList<>();
        l.add(new BuiltinDef("collision", "碰撞受惊", "前向碰撞预警", "受惊后仰 + 红闪"));
        l.add(new BuiltinDef("blindspot", "盲区扭头", "盲区告警(左/右)", "朝告警侧探头 + 警示黄"));
        l.add(new BuiltinDef("turnsignal", "转向灯转头", "打转向灯", "朝转向侧转头并保持"));
        l.add(new BuiltinDef("accel", "急加速急刹", "纵向加速度超阈值", "急加速后仰 / 急刹前倾"));
        l.add(new BuiltinDef("charging", "充电睡觉", "开始/停止充电、充满", "睡眠暗暖 / 充满点头庆祝"));
        l.add(new BuiltinDef("daynight", "昼夜情绪", "系统昼夜切换", "夜间犯困暗紫 / 白天明亮"));
        l.add(new BuiltinDef("tire", "胎压示警", "TPMS 告警", "摇头示警 + 红"));
        l.add(new BuiltinDef("drivemode", "驾驶模式人格", "切换驾驶模式", "底色随模式变化"));
        l.add(new BuiltinDef("ambient", "氛围灯同步", "车机氛围灯颜色变化", "底部灯跟随车机同色"));
        l.add(new BuiltinDef("cabintemp", "冷热反应", "车内温度过冷/过热", "冰蓝哆嗦 / 灼红"));
        return l;
    }
}
