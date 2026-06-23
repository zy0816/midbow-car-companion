package com.midbows.zkvision.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.midbows.zkvision.R;
import com.midbows.zkvision.automation.ActionCatalog;
import com.midbows.zkvision.automation.AutomationRule;
import com.midbows.zkvision.automation.Comparator;
import com.midbows.zkvision.automation.RuleStore;
import com.midbows.zkvision.automation.TriggerCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义规则编辑页：选触发源 + 条件 + 目标值，选机器人动作（必要时配色），保存/删除。
 *
 * <p>仅编辑自定义规则；预置规则在列表页只做开关/删除。通过 {@link #EXTRA_RULE_ID} 区分新建/编辑。
 */
public final class RuleEditorActivity extends AppCompatActivity {

    public static final String EXTRA_RULE_ID = "rule_id";

    /** 预置配色（与机器人氛围灯一致的低饱和度色板）。 */
    private static final int[] SWATCHES = {
            0x00B4FF, 0x34C759, 0xFF9F43, 0xFF5A3C, 0xE056C8, 0x5B6BFF, 0xFFFFFF, 0xFFD23F
    };

    private final List<TriggerCatalog.TriggerDef> triggers = TriggerCatalog.all();
    private final List<ActionCatalog.ActionDef> actions = ActionCatalog.all();

    private EditText etName;
    private Spinner spTrigger;
    private Spinner spComparator;
    private Spinner spEnumValue;
    private EditText etNumberValue;
    private Spinner spAction;
    private View colorRow;
    private View colorPreview;
    private LinearLayout colorSwatches;

    private RuleStore store;
    private AutomationRule rule;
    private int selectedColor = 0x00B4FF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rule_editor);
        store = RuleStore.getInstance(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        String id = getIntent().getStringExtra(EXTRA_RULE_ID);
        rule = id == null ? null : store.getById(id);
        boolean isNew = rule == null;
        if (isNew) {
            rule = new AutomationRule();
        }
        selectedColor = rule.color & 0xFFFFFF;

        bindViews();
        setupTriggerSpinner();
        setupComparatorSpinner();
        setupActionSpinner();
        buildSwatches();

        applyExisting();

        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnDelete).setOnClickListener(v -> {
            if (!isNew) {
                store.delete(rule.id);
            }
            finish();
        });
    }

    private void bindViews() {
        etName = findViewById(R.id.etName);
        spTrigger = findViewById(R.id.spTrigger);
        spComparator = findViewById(R.id.spComparator);
        spEnumValue = findViewById(R.id.spEnumValue);
        etNumberValue = findViewById(R.id.etNumberValue);
        spAction = findViewById(R.id.spAction);
        colorRow = findViewById(R.id.colorRow);
        colorPreview = findViewById(R.id.colorPreview);
        colorSwatches = findViewById(R.id.colorSwatches);
    }

    private void setupTriggerSpinner() {
        List<String> names = new ArrayList<>();
        for (TriggerCatalog.TriggerDef d : triggers) {
            names.add(d.name);
        }
        spTrigger.setAdapter(adapter(names));
        spTrigger.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long idd) {
                onTriggerChanged(triggers.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupComparatorSpinner() {
        List<String> names = new ArrayList<>();
        for (Comparator c : Comparator.values()) {
            names.add(c.label);
        }
        spComparator.setAdapter(adapter(names));
    }

    private void setupActionSpinner() {
        List<String> names = new ArrayList<>();
        for (ActionCatalog.ActionDef d : actions) {
            names.add(d.name);
        }
        spAction.setAdapter(adapter(names));
        spAction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long idd) {
                colorRow.setVisibility(actions.get(position).needsColor ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /** 触发源切换：按取值形态显示开关/枚举下拉或数值输入（值控件复位为默认）。 */
    private void onTriggerChanged(TriggerCatalog.TriggerDef def) {
        if (def.valueKind == TriggerCatalog.ValueKind.NUMBER) {
            spEnumValue.setVisibility(View.GONE);
            etNumberValue.setVisibility(View.VISIBLE);
        } else {
            etNumberValue.setVisibility(View.GONE);
            spEnumValue.setVisibility(View.VISIBLE);
            List<String> labels = new ArrayList<>();
            for (TriggerCatalog.Option o : def.options) {
                labels.add(o.label);
            }
            spEnumValue.setAdapter(adapter(labels));
        }
    }

    private void buildSwatches() {
        for (int rgb : SWATCHES) {
            View sw = new View(this);
            int size = dp(36);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(dp(8));
            sw.setLayoutParams(lp);
            sw.setBackgroundResource(R.drawable.dot);
            sw.getBackground().mutate().setTint(0xFF000000 | rgb);
            sw.setOnClickListener(v -> setColor(rgb));
            colorSwatches.addView(sw);
        }
        setColor(selectedColor);
    }

    private void setColor(int rgb) {
        selectedColor = rgb & 0xFFFFFF;
        colorPreview.getBackground().mutate().setTint(0xFF000000 | selectedColor);
    }

    /** 回填已存在规则的字段。目标值用 post 在 Spinner 选中回调全部结算后落位，避免时序丢选中。 */
    private void applyExisting() {
        etName.setText(rule.name);
        spComparator.setSelection(rule.comparator.ordinal());
        int actionIdx = indexOfAction(rule.actionKey);
        spAction.setSelection(actionIdx);
        colorRow.setVisibility(actions.get(actionIdx).needsColor ? View.VISIBLE : View.GONE);

        spTrigger.setSelection(indexOfTrigger(rule.triggerKey));
        if (rule.triggerKey == null) {
            return;
        }
        final double value = rule.value;
        spTrigger.post(() -> restoreValue(value));
    }

    /** 把原始目标值写入当前显示的值控件（枚举下拉或数值输入）。 */
    private void restoreValue(double value) {
        TriggerCatalog.TriggerDef def = triggers.get(spTrigger.getSelectedItemPosition());
        if (def.valueKind == TriggerCatalog.ValueKind.NUMBER) {
            etNumberValue.setText(def.floatValue
                    ? String.valueOf(value) : String.valueOf((int) value));
        } else {
            for (int i = 0; i < def.options.size(); i++) {
                if (def.options.get(i).value == (int) value) {
                    spEnumValue.setSelection(i);
                    return;
                }
            }
        }
    }

    private void save() {
        String name = etName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            toast("请填写规则名称");
            return;
        }
        TriggerCatalog.TriggerDef def = triggers.get(spTrigger.getSelectedItemPosition());
        ActionCatalog.ActionDef act = actions.get(spAction.getSelectedItemPosition());

        double value;
        if (def.valueKind == TriggerCatalog.ValueKind.NUMBER) {
            String raw = etNumberValue.getText().toString().trim();
            try {
                value = Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                toast("请填写有效数值");
                return;
            }
        } else {
            int pos = spEnumValue.getSelectedItemPosition();
            value = def.options.isEmpty() ? 0 : def.options.get(pos).value;
        }

        rule.name = name;
        rule.builtinKey = null;
        rule.triggerKey = def.key;
        rule.comparator = Comparator.values()[spComparator.getSelectedItemPosition()];
        rule.value = value;
        rule.actionKey = act.key;
        rule.color = act.needsColor ? selectedColor : rule.color;
        store.saveRule(rule);
        finish();
    }

    private int indexOfTrigger(String key) {
        for (int i = 0; i < triggers.size(); i++) {
            if (triggers.get(i).key.equals(key)) {
                return i;
            }
        }
        return 0;
    }

    private int indexOfAction(String key) {
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).key.equals(key)) {
                return i;
            }
        }
        return 0;
    }

    private ArrayAdapter<String> adapter(List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
