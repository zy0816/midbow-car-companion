package com.midbows.zkvision.automation;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 一条场景助手规则：当[触发]满足时机器人执行[动作]。
 *
 * <p>两类：预置（{@link #builtinKey} 非空，复用调好的内置监听器，触发/动作为固定文案）与
 * 自定义（{@link #builtinKey} 为空，用 {@link #triggerKey}+{@link #comparator}+{@link #value}
 * 描述触发，用 {@link #actionKey}+{@link #color} 描述动作）。可 JSON 序列化存储。
 */
public final class AutomationRule {

    public String id;
    public String name;
    public boolean enabled = true;

    /** 预置规则键（{@link BuiltinCatalog}）；自定义规则为 null。 */
    public String builtinKey;

    // —— 自定义规则字段 ——
    public String triggerKey;
    public Comparator comparator = Comparator.EQ;
    /** 触发比较值（整型功能/传感取整，连续量为浮点）。 */
    public double value;
    public String actionKey;
    /** 动作颜色 0xRRGGBB（仅设色/闪色动作用）。 */
    public int color = 0x00B4FF;

    public boolean isBuiltin() {
        return builtinKey != null;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("enabled", enabled);
        if (builtinKey != null) {
            o.put("builtinKey", builtinKey);
        } else {
            o.put("triggerKey", triggerKey);
            o.put("comparator", comparator.name());
            o.put("value", value);
            o.put("actionKey", actionKey);
            o.put("color", color);
        }
        return o;
    }

    public static AutomationRule fromJson(JSONObject o) {
        AutomationRule r = new AutomationRule();
        r.id = o.optString("id");
        r.name = o.optString("name");
        r.enabled = o.optBoolean("enabled", true);
        r.builtinKey = o.has("builtinKey") ? o.optString("builtinKey") : null;
        if (r.builtinKey == null) {
            r.triggerKey = o.optString("triggerKey", null);
            try {
                r.comparator = Comparator.valueOf(o.optString("comparator", "EQ"));
            } catch (IllegalArgumentException e) {
                r.comparator = Comparator.EQ;
            }
            r.value = o.optDouble("value", 0);
            r.actionKey = o.optString("actionKey", null);
            r.color = o.optInt("color", 0x00B4FF);
        }
        return r;
    }
}
