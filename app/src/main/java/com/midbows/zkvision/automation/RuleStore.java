package com.midbows.zkvision.automation;

import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 场景助手规则持久化（JSON 存 SharedPreferences）。全项目唯一规则读写入口。
 *
 * <p>首次启动注入全部内置预置规则（开箱即用）。任何增删改后发 {@link #ACTION_RULES_CHANGED}
 * 广播，{@code RuleEngine} 收到即重建监听。
 */
public final class RuleStore {

    public static final String ACTION_RULES_CHANGED = "com.midbows.zkvision.ACTION_RULES_CHANGED";

    private static final String PREFS = "zkvision_rules";
    private static final String KEY_RULES = "rules";
    private static final String KEY_SEEDED = "seeded";
    /** 已注入过的内置预置键集合（JSON 数组）。用于升级后只补新增的预置，且不复活用户删过的。 */
    private static final String KEY_SEEDED_KEYS = "seeded_keys";

    private final Context context;
    private final android.content.SharedPreferences prefs;
    private static RuleStore instance;

    public static synchronized RuleStore getInstance(Context context) {
        if (instance == null) {
            instance = new RuleStore(context.getApplicationContext());
        }
        return instance;
    }

    private RuleStore(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        seedIfNeeded();
    }

    private void seedIfNeeded() {
        List<String> seededKeys = readSeededKeys();
        // 旧版迁移：已 seeded 但无 seeded_keys，则以当前已存的预置键作为「已注入」基线，避免重复。
        if (seededKeys.isEmpty() && prefs.getBoolean(KEY_SEEDED, false)) {
            for (AutomationRule r : getRules()) {
                if (r.isBuiltin() && !seededKeys.contains(r.builtinKey)) {
                    seededKeys.add(r.builtinKey);
                }
            }
        }
        List<AutomationRule> list = getRules();
        boolean changed = false;
        for (BuiltinCatalog.BuiltinDef d : BuiltinCatalog.all()) {
            if (seededKeys.contains(d.key)) {
                continue;
            }
            AutomationRule r = new AutomationRule();
            r.id = UUID.randomUUID().toString();
            r.name = d.name;
            r.builtinKey = d.key;
            r.enabled = true;
            list.add(r);
            seededKeys.add(d.key);
            changed = true;
        }
        if (changed) {
            writeList(list);
        }
        prefs.edit()
                .putBoolean(KEY_SEEDED, true)
                .putString(KEY_SEEDED_KEYS, new JSONArray(seededKeys).toString())
                .apply();
    }

    private List<String> readSeededKeys() {
        List<String> keys = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_SEEDED_KEYS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                keys.add(arr.getString(i));
            }
        } catch (JSONException ignored) {
        }
        return keys;
    }

    public synchronized List<AutomationRule> getRules() {
        List<AutomationRule> list = new ArrayList<>();
        String json = prefs.getString(KEY_RULES, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(AutomationRule.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    public AutomationRule getById(String id) {
        for (AutomationRule r : getRules()) {
            if (r.id.equals(id)) {
                return r;
            }
        }
        return null;
    }

    /** 新增或按 id 覆盖更新；随后广播。 */
    public synchronized void saveRule(AutomationRule rule) {
        if (rule.id == null) {
            rule.id = UUID.randomUUID().toString();
        }
        List<AutomationRule> list = getRules();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(rule.id)) {
                list.set(i, rule);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            list.add(rule);
        }
        writeList(list);
        notifyChanged();
    }

    public synchronized void setEnabled(String id, boolean enabled) {
        List<AutomationRule> list = getRules();
        for (AutomationRule r : list) {
            if (r.id.equals(id)) {
                r.enabled = enabled;
                break;
            }
        }
        writeList(list);
        notifyChanged();
    }

    public synchronized void delete(String id) {
        List<AutomationRule> list = getRules();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        writeList(list);
        notifyChanged();
    }

    private void writeList(List<AutomationRule> list) {
        JSONArray arr = new JSONArray();
        for (AutomationRule r : list) {
            try {
                arr.put(r.toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(KEY_RULES, arr.toString()).apply();
    }

    private void notifyChanged() {
        context.sendBroadcast(new Intent(ACTION_RULES_CHANGED).setPackage(context.getPackageName()));
    }
}
