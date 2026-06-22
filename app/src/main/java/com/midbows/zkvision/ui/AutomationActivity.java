package com.midbows.zkvision.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

        if (!rule.isBuiltin()) {
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
