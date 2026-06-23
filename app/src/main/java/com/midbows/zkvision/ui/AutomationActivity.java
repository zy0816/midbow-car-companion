package com.midbows.zkvision.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.midbows.zkvision.R;
import com.midbows.zkvision.automation.ActionCatalog;
import com.midbows.zkvision.automation.AutomationRule;
import com.midbows.zkvision.automation.BuiltinCatalog;
import com.midbows.zkvision.automation.RuleStore;
import com.midbows.zkvision.automation.TriggerCatalog;
import com.midbows.zkvision.data.SettingsManager;
import com.midbows.zkvision.signal.CarThresholds;

/**
 * 场景助手列表页：展示全部规则（预置 + 自定义），支持开关、删除、点击编辑、新建。
 *
 * <p>规则数量有限（十余条），列表用程序化构建的卡片填充 {@code ruleList}，避免引入 RecyclerView。
 * 任何变更经 {@link RuleStore} 落盘并广播，{@code RuleEngine} 实时重建监听。
 */
public final class AutomationActivity extends AppCompatActivity {

    private LinearLayout ruleList;
    private RuleStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_automation);
        store = RuleStore.getInstance(this);
        ruleList = findViewById(R.id.ruleList);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnNewRule).setOnClickListener(v ->
                startActivity(new Intent(this, RuleEditorActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        ruleList.removeAllViews();
        for (AutomationRule rule : store.getRules()) {
            ruleList.addView(buildRow(rule));
        }
    }

    private View buildRow(AutomationRule rule) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(12);
        card.setLayoutParams(cardLp);
        card.setRadius(dp(16));
        card.setCardElevation(0);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(16), dp(20), dp(16));
        row.setMinimumHeight(dp(76));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(textsLp);

        TextView title = new TextView(this);
        title.setText(rule.name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        TextView sub = new TextView(this);
        sub.setText(describe(rule));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sub.setAlpha(0.7f);

        texts.addView(title);
        texts.addView(sub);

        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setChecked(rule.enabled);
        sw.setOnCheckedChangeListener((b, checked) -> store.setEnabled(rule.id, checked));

        row.addView(texts);
        row.addView(sw);
        card.addView(row);

        if (rule.isBuiltin()) {
            // 预置场景点击进入详情：阈值型（急加速/冷热）可现场调参，其余仅展示触发→动作。
            texts.setOnClickListener(v -> showBuiltinDetail(rule));
        } else {
            texts.setOnClickListener(v -> {
                Intent i = new Intent(this, RuleEditorActivity.class);
                i.putExtra(RuleEditorActivity.EXTRA_RULE_ID, rule.id);
                startActivity(i);
            });
        }
        card.setOnLongClickListener(v -> {
            confirmDelete(rule);
            return true;
        });
        return card;
    }

    /**
     * 预置场景详情：阈值型预置（急加速/急刹、座舱冷热）给出可调参数并落 {@link SettingsManager}，
     * 监听器在判定时实时取值、改完即生效；其余预置仅展示触发→动作文案与开关说明。
     */
    private void showBuiltinDetail(AutomationRule rule) {
        BuiltinCatalog.BuiltinDef d = BuiltinCatalog.byKey(rule.builtinKey);
        String title = d == null ? rule.name : d.name;

        if ("accel".equals(rule.builtinKey)) {
            SettingsManager s = SettingsManager.getInstance(this);
            EditText accel = numberField(s.getFloat(
                    SettingsManager.KEY_ACCEL_HARD, CarThresholds.HARD_ACCEL));
            EditText brake = numberField(s.getFloat(
                    SettingsManager.KEY_BRAKE_HARD, CarThresholds.HARD_BRAKE));
            LinearLayout box = thresholdBox(
                    "纵向加速度为传感器原生量（实测静止约 0.0x，疑似 g 而非 m/s²）。\n超过阈值触发急加速后仰 / 急刹前倾。",
                    "急加速阈值（约 g）", accel, "急刹阈值（绝对值，约 g）", brake);
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setView(box)
                    .setPositiveButton("保存", (dlg, w) -> {
                        saveFloat(s, SettingsManager.KEY_ACCEL_HARD, accel, CarThresholds.HARD_ACCEL);
                        saveFloat(s, SettingsManager.KEY_BRAKE_HARD, brake, CarThresholds.HARD_BRAKE);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        if ("cabintemp".equals(rule.builtinKey)) {
            SettingsManager s = SettingsManager.getInstance(this);
            EditText cold = numberField(s.getFloat(
                    SettingsManager.KEY_COLD_BELOW, CarThresholds.COLD_BELOW_C));
            EditText hot = numberField(s.getFloat(
                    SettingsManager.KEY_HOT_ABOVE, CarThresholds.HOT_ABOVE_C));
            LinearLayout box = thresholdBox(
                    "跟随车内温度传感器（℃）。低于「冷」阈值哆嗦，高于「热」阈值灼红。",
                    "冷阈值（℃）", cold, "热阈值（℃）", hot);
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setView(box)
                    .setPositiveButton("保存", (dlg, w) -> {
                        saveFloat(s, SettingsManager.KEY_COLD_BELOW, cold, CarThresholds.COLD_BELOW_C);
                        saveFloat(s, SettingsManager.KEY_HOT_ABOVE, hot, CarThresholds.HOT_ABOVE_C);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        // 非阈值型预置：仅展示触发→动作，说明可用开关启停、长按删除。
        String msg = d == null
                ? "预置场景。"
                : "触发：" + d.triggerText + "\n动作：" + d.actionText
                        + "\n\n此预置无可调参数，可用右侧开关启停、长按删除。";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("知道了", null)
                .show();
    }

    private EditText numberField(float value) {
        EditText e = new EditText(this);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setText(String.valueOf(value));
        return e;
    }

    private LinearLayout thresholdBox(String hint, String label1, EditText e1,
                                      String label2, EditText e2) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), dp(8));
        TextView tip = new TextView(this);
        tip.setText(hint);
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tip.setAlpha(0.7f);
        box.addView(tip);
        TextView l1 = new TextView(this);
        l1.setText(label1);
        l1.setPadding(0, dp(16), 0, 0);
        box.addView(l1);
        box.addView(e1);
        TextView l2 = new TextView(this);
        l2.setText(label2);
        l2.setPadding(0, dp(12), 0, 0);
        box.addView(l2);
        box.addView(e2);
        return box;
    }

    private void saveFloat(SettingsManager s, String key, EditText field, float def) {
        try {
            s.setFloat(key, Float.parseFloat(field.getText().toString().trim()));
        } catch (NumberFormatException ignored) {
            s.setFloat(key, def);
        }
    }

    private void confirmDelete(AutomationRule rule) {
        new AlertDialog.Builder(this)
                .setTitle("删除规则")
                .setMessage("确定删除「" + rule.name + "」？")
                .setPositiveButton("删除", (d, w) -> {
                    store.delete(rule.id);
                    refresh();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 列表副标题：触发 → 动作。 */
    private String describe(AutomationRule rule) {
        if (rule.isBuiltin()) {
            BuiltinCatalog.BuiltinDef d = BuiltinCatalog.byKey(rule.builtinKey);
            if (d != null) {
                return d.triggerText + " → " + d.actionText;
            }
            return "预置场景";
        }
        TriggerCatalog.TriggerDef t = TriggerCatalog.byKey(rule.triggerKey);
        ActionCatalog.ActionDef a = ActionCatalog.byKey(rule.actionKey);
        String trigger = t == null ? "?" : t.name + " " + rule.comparator.label + " " + valueText(t, rule);
        String action = a == null ? "?" : a.name;
        return trigger + " → " + action;
    }

    private String valueText(TriggerCatalog.TriggerDef t, AutomationRule rule) {
        if (t.valueKind != TriggerCatalog.ValueKind.NUMBER && !t.options.isEmpty()) {
            for (TriggerCatalog.Option o : t.options) {
                if (o.value == (int) rule.value) {
                    return o.label;
                }
            }
        }
        if (t.floatValue) {
            return String.valueOf(rule.value);
        }
        return String.valueOf((int) rule.value);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
