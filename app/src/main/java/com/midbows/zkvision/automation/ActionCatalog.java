package com.midbows.zkvision.automation;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义场景规则可选「机器人动作」目录。
 *
 * <p>每个 {@link ActionDef#key} 是稳定字符串，与 {@code BehaviorEngine.runAction(key, rgb)} 的
 * 分支一一对应（执行映射在 BehaviorEngine，本目录只提供给编辑页的可选项与是否需要选色）。
 */
public final class ActionCatalog {

    public static final String NOD = "nod";
    public static final String SHAKE = "shake";
    public static final String TURN_LEFT = "turn_left";
    public static final String TURN_RIGHT = "turn_right";
    public static final String HOLD_LEFT = "hold_left";
    public static final String HOLD_RIGHT = "hold_right";
    public static final String CENTER = "center";
    public static final String LEAN_BACK = "lean_back";
    public static final String LEAN_FWD = "lean_fwd";
    public static final String PEEK_LEFT = "peek_left";
    public static final String PEEK_RIGHT = "peek_right";
    public static final String SHIVER = "shiver";
    public static final String RECOIL = "recoil";
    public static final String WOBBLE = "wobble";
    public static final String GROOVE_ON = "groove_on";
    public static final String GROOVE_OFF = "groove_off";
    public static final String SET_COLOR = "set_color";
    public static final String FLASH_COLOR = "flash_color";
    public static final String RESET_BASE = "reset_base";

    /** 表情动作键前缀：{@code expr_<两位十六进制>}，BehaviorEngine 解析后走 FE55 表情字节。 */
    public static final String EXPR_PREFIX = "expr_";

    /** 表情动作键 → 中文名（厂家 FE55 表情表，18 个已知）。 */
    private static final String[][] EXPRESSIONS = {
            {"06", "生气"}, {"07", "笑一个"}, {"08", "比个心"}, {"09", "眨眼睛"},
            {"0A", "开心"}, {"0B", "难过"}, {"0C", "再眨眼睛"}, {"0D", "装酷"},
            {"0E", "坏笑"}, {"0F", "放烟花"}, {"10", "石头剪刀布"}, {"11", "害怕"},
            {"12", "晕头转向"}, {"1A", "害羞"}, {"23", "情人节快乐"}, {"24", "玫瑰花"},
            {"26", "吃月饼"},
    };

    public static final class ActionDef {
        public final String key;
        public final String name;
        public final boolean needsColor;

        ActionDef(String key, String name, boolean needsColor) {
            this.key = key;
            this.name = name;
            this.needsColor = needsColor;
        }
    }

    private static final List<ActionDef> ALL = build();

    private ActionCatalog() {
    }

    public static List<ActionDef> all() {
        return ALL;
    }

    public static ActionDef byKey(String key) {
        for (ActionDef d : ALL) {
            if (d.key.equals(key)) {
                return d;
            }
        }
        return null;
    }

    private static List<ActionDef> build() {
        List<ActionDef> l = new ArrayList<>();
        l.add(new ActionDef(NOD, "点头", false));
        l.add(new ActionDef(SHAKE, "摇头", false));
        l.add(new ActionDef(TURN_LEFT, "左转一下", false));
        l.add(new ActionDef(TURN_RIGHT, "右转一下", false));
        l.add(new ActionDef(HOLD_LEFT, "转向左侧保持", false));
        l.add(new ActionDef(HOLD_RIGHT, "转向右侧保持", false));
        l.add(new ActionDef(CENTER, "居中复位", false));
        l.add(new ActionDef(LEAN_BACK, "后仰", false));
        l.add(new ActionDef(LEAN_FWD, "前倾", false));
        l.add(new ActionDef(PEEK_LEFT, "左探头", false));
        l.add(new ActionDef(PEEK_RIGHT, "右探头", false));
        l.add(new ActionDef(SHIVER, "哆嗦", false));
        l.add(new ActionDef(RECOIL, "受惊后仰", false));
        l.add(new ActionDef(WOBBLE, "摇头晃脑", false));
        l.add(new ActionDef(GROOVE_ON, "律动开", false));
        l.add(new ActionDef(GROOVE_OFF, "律动关", false));
        l.add(new ActionDef(SET_COLOR, "设底色", true));
        l.add(new ActionDef(FLASH_COLOR, "闪一下色", true));
        l.add(new ActionDef(RESET_BASE, "恢复默认底色", false));
        for (String[] e : EXPRESSIONS) {
            l.add(new ActionDef(EXPR_PREFIX + e[0], "表情·" + e[1], false));
        }
        return l;
    }
}
