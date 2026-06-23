package com.midbows.zkvision.signal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.midbows.zkvision.automation.AutomationRule;
import com.midbows.zkvision.automation.RuleStore;
import com.midbows.zkvision.automation.TriggerCatalog;
import com.midbows.zkvision.behavior.BehaviorEngine;
import com.midbows.zkvision.util.RobotLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景助手执行引擎：把 {@link RuleStore} 里启用的规则装配成实际监听并执行动作。
 *
 * <p>预置规则复用 {@code signal} 包里调好的内置监听器（碰撞/盲区/转向灯/急加速急刹/充电/昼夜/
 * 胎压/驾驶模式/氛围灯同步/冷热），零回归；自定义规则按 {@link TriggerCatalog} 订阅 ecarx
 * 功能/传感，回调值用规则的比较运算符判定，「假→真」跳变沿触发 {@link BehaviorEngine#runAction}。
 *
 * <p>规则增删改后由 {@link RuleStore#ACTION_RULES_CHANGED} 广播驱动本引擎重建监听。
 * 作为单一 {@link SignalSource} 挂在「车辆状态联动」总开关下，由 {@code RobotService} 统一启停。
 */
public final class RuleEngine implements SignalSource {

    private static final String TAG = "RuleEngine";

    private final Context context;
    private final BehaviorEngine engine;

    private boolean running;
    private final List<SignalSource> activeBuiltins = new ArrayList<>();
    private final List<Object> customTokens = new ArrayList<>();

    private final BroadcastReceiver rulesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (RuleStore.ACTION_RULES_CHANGED.equals(intent.getAction())) {
                RobotLog.d(TAG, "规则变化，重建监听");
                rebuild();
            }
        }
    };

    public RuleEngine(Context context, BehaviorEngine engine) {
        this.context = context.getApplicationContext();
        this.engine = engine;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        RobotLog.d(TAG, "启动");
        context.registerReceiver(rulesReceiver, new IntentFilter(RuleStore.ACTION_RULES_CHANGED));
        build();
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        RobotLog.d(TAG, "停止");
        try {
            context.unregisterReceiver(rulesReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        teardown();
    }

    private synchronized void rebuild() {
        if (!running) {
            return;
        }
        teardown();
        build();
    }

    private void build() {
        for (AutomationRule rule : RuleStore.getInstance(context).getRules()) {
            if (!rule.enabled) {
                continue;
            }
            if (rule.isBuiltin()) {
                SignalSource s = createBuiltin(rule.builtinKey);
                if (s != null) {
                    s.start();
                    activeBuiltins.add(s);
                }
            } else {
                subscribeCustom(rule);
            }
        }
        RobotLog.d(TAG, "已装配 内置=" + activeBuiltins.size() + " 自定义=" + customTokens.size());
    }

    private void teardown() {
        for (SignalSource s : activeBuiltins) {
            s.stop();
        }
        activeBuiltins.clear();
        EcarxCarManager mgr = EcarxCarManager.getInstance();
        for (Object token : customTokens) {
            mgr.unwatch(token);
        }
        customTokens.clear();
    }

    /** 内置预置键 → 调好的监听器实例。 */
    private SignalSource createBuiltin(String key) {
        switch (key) {
            case "collision":
                return new CollisionMonitor(context, engine);
            case "blindspot":
                return new BlindSpotMonitor(context, engine);
            case "turnsignal":
                return new TurnSignalMonitor(context, engine);
            case "accel":
                return new AccelMonitor(context, engine);
            case "charging":
                return new ChargingMonitor(context, engine);
            case "daynight":
                return new DayNightMonitor(context, engine);
            case "tire":
                return new TireMonitor(context, engine);
            case "drivemode":
                return new DriveModeMonitor(context, engine);
            case "ambient":
                return new AmbientLightMonitor(context, engine);
            case "cabintemp":
                return new CabinTempMonitor(context, engine);
            case "actemp":
                return new AcTempMonitor(context, engine);
            default:
                RobotLog.d(TAG, "未知预置键: " + key);
                return null;
        }
    }

    /** 自定义规则：订阅触发源，跳变沿触发动作。 */
    private void subscribeCustom(AutomationRule rule) {
        TriggerCatalog.TriggerDef def = TriggerCatalog.byKey(rule.triggerKey);
        if (def == null || rule.actionKey == null) {
            RobotLog.d(TAG, "自定义规则触发/动作无效，跳过: " + rule.name);
            return;
        }
        final boolean[] lastMatch = {false};
        EcarxCarManager mgr = EcarxCarManager.getInstance();
        Object token;
        switch (def.kind) {
            case FUNCTION:
                token = mgr.watchFunctionValue(context, def.id,
                        (id, zone, value) -> evaluate(rule, lastMatch, value, false));
                break;
            case SENSOR_EVENT:
                token = mgr.watchSensorEvent(context, def.id,
                        (type, event) -> evaluate(rule, lastMatch, event, false));
                break;
            case SENSOR_VALUE:
                token = mgr.watchSensorValue(context, def.id,
                        (type, value) -> evaluateFloat(rule, lastMatch, value));
                break;
            default:
                return;
        }
        customTokens.add(token);
        RobotLog.d(TAG, "自定义规则已挂载: " + rule.name);
    }

    private void evaluate(AutomationRule rule, boolean[] lastMatch, int value, boolean unused) {
        boolean match = rule.comparator.matchInt(value, (int) rule.value);
        fireOnRisingEdge(rule, lastMatch, match);
    }

    private void evaluateFloat(AutomationRule rule, boolean[] lastMatch, float value) {
        boolean match = rule.comparator.matchFloat(value, (float) rule.value);
        fireOnRisingEdge(rule, lastMatch, match);
    }

    private void fireOnRisingEdge(AutomationRule rule, boolean[] lastMatch, boolean match) {
        if (match && !lastMatch[0]) {
            RobotLog.d(TAG, "规则命中: " + rule.name + " → " + rule.actionKey);
            engine.runAction(rule.actionKey, rule.color);
        }
        lastMatch[0] = match;
    }
}
