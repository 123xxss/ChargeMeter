package com.dsh.chargemeter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    static final class Theme {
        final String name;
        final int bg, panel, line, text, dim, a1, a2;
        final boolean dark;
        Theme(String n, int bg, int panel, int line, int text, int dim, int a1, int a2, boolean dark) {
            this.name = n; this.bg = bg; this.panel = panel; this.line = line;
            this.text = text; this.dim = dim; this.a1 = a1; this.a2 = a2; this.dark = dark;
        }
    }

    static final Theme[] THEMES = {
            new Theme("科幻青", 0xFF06090F, 0xFF0A121B, 0xFF16283A,
                    0xFFE6F7FF, 0xFF5C7A90, 0xFF00E5FF, 0xFF00FFA3, true),
            new Theme("极简白", 0xFFF2F2F7, 0xFFFFFFFF, 0xFFE5E5EA,
                    0xFF1C1C1E, 0xFF8E8E93, 0xFF007AFF, 0xFF34C759, false),
            new Theme("赛博紫", 0xFF0B0614, 0xFF150B23, 0xFF2A1B3D,
                    0xFFF0E6FF, 0xFF8B7BA5, 0xFFB14CFF, 0xFFFF3D9A, true),
            new Theme("终端绿", 0xFF000000, 0xFF04120A, 0xFF12301B,
                    0xFFCCFFDD, 0xFF4E8F63, 0xFF33FF66, 0xFF99FF33, true),
            new Theme("熔岩橙", 0xFF120806, 0xFF1E0F0A, 0xFF3A2018,
                    0xFFFFEDE4, 0xFFA67C6A, 0xFFFF7A45, 0xFFFFD166, true),
    };

    private Theme th = THEMES[0];

    private static final Typeface MONO = Typeface.create("monospace", Typeface.NORMAL);

    private LinearLayout stateChip;
    private View stateDot;
    private TextView stateLabel;
    private TextView tierText;
    private TextView powerText;
    private TextView powerUnit;
    private TextView powerCaption;
    private TextView currentText;
    private TextView voltageText;
    private TextView tempText;
    private TextView peakText;
    private TextView levelText;
    private TextView rawText;
    private TextView factorText;
    private GaugeView gauge;
    private ChartView chart;

    private BatteryManager bm;
    private SharedPreferences cfg;
    private int cellCount = 1;
    private int themeId = 0;
    private double ratio = 1.0;
    private double peak;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<TextView, String> cardLabels = new HashMap<>();

    private float screenHz;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        cfg = getSharedPreferences("cfg", Context.MODE_PRIVATE);
        cellCount = cfg.contains("cells") ? cfg.getInt("cells", 1) : recommendedCells();
        ratio = cfg.getFloat("ratio", 1.0f);
        themeId = cfg.getInt("theme", 0);
        if (themeId < 0 || themeId >= THEMES.length) themeId = 0;
        th = THEMES[themeId];

        getWindow().setStatusBarColor(th.bg);
        getWindow().setNavigationBarColor(th.bg);
        if (!th.dark) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(th.bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(th.bg);
        int statusBar = dp(24);
        int sid = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (sid > 0) statusBar = getResources().getDimensionPixelSize(sid);
        root.setPadding(dp(18), statusBar + dp(10), dp(18), dp(22));

        // ===== 顶部 =====
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("充电监视");
        title.setTextColor(th.a1);
        title.setTextSize(13);
        title.setLetterSpacing(0.08f);
        title.setTypeface(MONO, Typeface.BOLD);
        titleBox.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("实时电池功率");
        subtitle.setTextColor(th.dim);
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(3), 0, 0);
        titleBox.addView(subtitle);
        head.addView(titleBox, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        stateChip = new LinearLayout(this);
        stateChip.setOrientation(LinearLayout.HORIZONTAL);
        stateChip.setGravity(Gravity.CENTER_VERTICAL);
        stateChip.setPadding(dp(9), dp(5), dp(11), dp(5));
        stateDot = new View(this);
        stateChip.addView(stateDot, new LinearLayout.LayoutParams(dp(6), dp(6)));
        stateLabel = new TextView(this);
        stateLabel.setTextSize(10);
        stateLabel.setTypeface(MONO, Typeface.BOLD);
        stateLabel.setLetterSpacing(0.06f);
        stateLabel.setPadding(dp(6), 0, 0, 0);
        stateChip.addView(stateLabel);
        head.addView(stateChip);

        TextView themeBtn = new TextView(this);
        themeBtn.setText("风格");
        themeBtn.setTextSize(11);
        themeBtn.setTextColor(th.a1);
        themeBtn.setPadding(dp(11), dp(5), dp(11), dp(5));
        themeBtn.setBackground(panelStroke(dp(30)));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(7);
        themeBtn.setLayoutParams(tlp);
        themeBtn.setOnClickListener(v -> pickTheme());
        head.addView(themeBtn);

        TextView setBtn = new TextView(this);
        setBtn.setText("校准");
        setBtn.setTextSize(11);
        setBtn.setTextColor(th.a1);
        setBtn.setPadding(dp(11), dp(5), dp(11), dp(5));
        setBtn.setBackground(panelStroke(dp(30)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(7);
        setBtn.setLayoutParams(slp);
        setBtn.setOnClickListener(v -> showSettings());
        head.addView(setBtn);
        root.addView(head);

        // ===== 仪表盘 =====
        FrameLayout gaugeBox = new FrameLayout(this);
        LinearLayout.LayoutParams gbp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(256));
        gbp.topMargin = dp(4);
        gaugeBox.setLayoutParams(gbp);

        gauge = new GaugeView(this);
        gauge.setTheme(th.dark ? th.line : 0xFFE5E5EA, th.a1, th.a2, th.a2,
                th.a2, th.dark ? th.line : 0xFFD1D1D6, (th.a2 & 0x00FFFFFF) | 0x26000000);
        gaugeBox.addView(gauge, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        clp.topMargin = dp(10);

        powerCaption = new TextView(this);
        powerCaption.setText("充入功率");
        powerCaption.setTextColor(th.dim);
        powerCaption.setTextSize(11);
        powerCaption.setLetterSpacing(0.16f);
        powerCaption.setGravity(Gravity.CENTER);
        center.addView(powerCaption);

        LinearLayout powerRow = new LinearLayout(this);
        powerRow.setOrientation(LinearLayout.HORIZONTAL);
        powerRow.setGravity(Gravity.BOTTOM);
        powerRow.setPadding(0, dp(2), 0, 0);

        powerText = new TextView(this);
        powerText.setText("--");
        powerText.setTextColor(th.a2);
        powerText.setTextSize(46);
        powerText.setTypeface(MONO, Typeface.BOLD);
        powerText.setIncludeFontPadding(false);
        powerRow.addView(powerText);

        powerUnit = new TextView(this);
        powerUnit.setText(" W");
        powerUnit.setTextColor(th.dim);
        powerUnit.setTextSize(17);
        powerUnit.setTypeface(MONO);
        powerUnit.setPadding(0, 0, 0, dp(8));
        powerRow.addView(powerUnit);
        center.addView(powerRow);

        tierText = new TextView(this);
        tierText.setText("");
        tierText.setTextSize(11);
        tierText.setTypeface(MONO, Typeface.BOLD);
        tierText.setLetterSpacing(0.12f);
        tierText.setGravity(Gravity.CENTER);
        tierText.setPadding(0, dp(6), 0, 0);
        center.addView(tierText);

        factorText = new TextView(this);
        factorText.setTextColor(th.dim);
        factorText.setTextSize(10);
        factorText.setGravity(Gravity.CENTER);
        factorText.setPadding(0, dp(4), 0, 0);
        center.addView(factorText);

        gaugeBox.addView(center, clp);
        root.addView(gaugeBox);

        // ===== 指标 =====
        LinearLayout grid1 = row();
        grid1.addView(card("电流", "--"), weight());
        grid1.addView(card("电压", "--"), weight());
        root.addView(grid1);

        LinearLayout grid2 = row();
        grid2.addView(card("温度", "--"), weight());
        grid2.addView(card("峰值", "--"), weight());
        root.addView(grid2);

        // ===== 曲线 =====
        LinearLayout chartCard = new LinearLayout(this);
        chartCard.setOrientation(LinearLayout.VERTICAL);
        chartCard.setPadding(dp(16), dp(14), dp(16), dp(10));
        chartCard.setBackground(panelStroke(dp(12)));
        LinearLayout.LayoutParams cclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cclp.topMargin = dp(12);
        chartCard.setLayoutParams(cclp);

        LinearLayout chartHead = new LinearLayout(this);
        chartHead.setOrientation(LinearLayout.HORIZONTAL);
        chartHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView chartTitle = new TextView(this);
        chartTitle.setText("功率曲线");
        chartTitle.setTextColor(th.text);
        chartTitle.setTextSize(11);
        chartTitle.setLetterSpacing(0.04f);
        chartTitle.setTypeface(MONO, Typeface.BOLD);
        chartHead.addView(chartTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView chartHint = new TextView(this);
        chartHint.setText("最近 2 分钟");
        chartHint.setTextColor(th.dim);
        chartHint.setTextSize(10);
        chartHead.addView(chartHint);
        chartCard.addView(chartHead);

        chart = new ChartView(this);
        chart.setTheme(th.a2, th.dark ? th.line : 0xFFE5E5EA, th.dim,
                (th.a2 & 0x00FFFFFF) | 0x55000000);
        chartCard.addView(chart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
        root.addView(chartCard);

        levelText = new TextView(this);
        levelText.setText("--");
        levelText.setTextColor(th.dim);
        levelText.setTextSize(10);
        levelText.setGravity(Gravity.CENTER);
        levelText.setPadding(0, dp(14), 0, 0);
        root.addView(levelText);

        rawText = new TextView(this);
        rawText.setTextSize(9);
        rawText.setTextColor(th.dark ? (th.dim & 0x00FFFFFF) | 0x99000000 : 0xFFA1A1A6);
        rawText.setTypeface(MONO);
        rawText.setGravity(Gravity.CENTER);
        rawText.setPadding(0, dp(6), 0, 0);
        root.addView(rawText);

        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        try {
            Display d = getWindowManager().getDefaultDisplay();
            screenHz = d.getRefreshRate();
        } catch (Exception ignored) {
        }

    }

    private void pickTheme() {
        String[] names = new String[THEMES.length];
        for (int i = 0; i < THEMES.length; i++) {
            names[i] = (i == themeId ? "✓ " : "") + THEMES[i].name;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setTitle("选择风格")
                .setItems(names, (d, which) -> {
                    if (which == themeId) return;
                    cfg.edit().putInt("theme", which).apply();
                    recreate();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private GradientDrawable panelStroke(int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(th.panel);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), th.line);
        return d;
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(12), dp(22), dp(0));

        TextView tip = new TextView(this);
        tip.setText("推荐 " + recommendedCells() + " 电芯 · " + Build.MANUFACTURER + " " + Build.MODEL);
        tip.setTextColor(th.a1);
        tip.setTextSize(12);
        box.addView(tip);

        TextView t1 = new TextView(this);
        t1.setText("电芯数");
        t1.setTextColor(th.dim);
        t1.setTextSize(12);
        t1.setPadding(0, dp(8), 0, 0);
        box.addView(t1);

        EditText e1 = new EditText(this);
        e1.setInputType(InputType.TYPE_CLASS_NUMBER);
        e1.setText(String.valueOf(cellCount == 1 ? recommendedCells() : cellCount));
        box.addView(e1);

        TextView t2 = new TextView(this);
        t2.setText("校准倍率（1~10，用来对齐系统显示）");
        t2.setTextColor(th.dim);
        t2.setTextSize(12);
        t2.setPadding(0, dp(8), 0, 0);
        box.addView(t2);

        EditText e2 = new EditText(this);
        e2.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e2.setText(String.valueOf(ratio));
        box.addView(e2);

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setTitle("校准")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        int c = Integer.parseInt(e1.getText().toString().trim());
                        if (c < 1 || c > 4) c = 1;
                        cellCount = c;
                    } catch (Exception ignored) {
                    }
                    try {
                        double r = Double.parseDouble(e2.getText().toString().trim());
                        if (r <= 0 || r > 10) r = 1.0;
                        ratio = r;
                    } catch (Exception ignored) {
                    }
                    cfg.edit().putInt("cells", cellCount).putFloat("ratio", (float) ratio).apply();
                    peak = 0;
                    chart.clear();
                    tick();
                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String techText(String tech) {
        if (tech == null || tech.isEmpty()) return "";
        if (tech.equalsIgnoreCase("Li-ion")) return "锂离子电池";
        if (tech.equalsIgnoreCase("Li-poly")) return "聚合物锂电池";
        if (tech.equalsIgnoreCase("LiFe")) return "磷酸铁锂";
        if (tech.equalsIgnoreCase("NiMH")) return "镍氢电池";
        if (tech.equalsIgnoreCase("NiCd")) return "镍镉电池";
        return tech;
    }

    private int recommendedCells() {
        String b = (Build.BRAND + " " + Build.MANUFACTURER + " " + Build.MODEL).toLowerCase();
        if (b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")
                || b.contains("oppo") || b.contains("oneplus") || b.contains("realme")
                || b.contains("vivo") || b.contains("iqoo")
                || b.contains("honor") || b.contains("huawei")) {
            return 2;
        }
        return 1;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        l.setLayoutParams(lp);
        return l;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(5);
        lp.leftMargin = dp(5);
        return lp;
    }

    private View card(String label, String value) {
        TextView tv = new TextView(this);
        tv.setPadding(dp(13), dp(10), dp(13), dp(10));
        tv.setMinimumHeight(dp(74));
        tv.setBackground(panelStroke(dp(10)));
        tv.setLineSpacing(dp(7), 1f);
        cardLabels.put(tv, label);
        setCard(tv, value);

        if (label.contains("电流")) currentText = tv;
        else if (label.contains("电压")) voltageText = tv;
        else if (label.contains("温度")) tempText = tv;
        else peakText = tv;
        return tv;
    }

    private void setCard(TextView tv, String value) {
        String label = cardLabels.get(tv);
        if (label == null) label = "";
        String full = label + "\n" + value;
        SpannableString ss = new SpannableString(full);
        int cut = label.length();
        ss.setSpan(new AbsoluteSizeSpan(10, true), 0, cut, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(th.dim), 0, cut, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(Typeface.BOLD), 0, cut, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(19, true), cut + 1, full.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(th.text), cut + 1, full.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setTypeface(MONO);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setText(ss);
    }

    private void updateCard(TextView tv, String value) {
        if (tv != null) setCard(tv, value);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private void tick() {
        Intent it = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = -1;
        int voltageMv = 0;
        int tempTenths = 0;
        int status = BatteryManager.BATTERY_STATUS_UNKNOWN;
        int plugged = 0;
        String tech = "";
        if (it != null) {
            level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            voltageMv = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            tempTenths = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            status = it.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            plugged = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            tech = it.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        }

        long raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        boolean currentSupported = raw != Long.MIN_VALUE;
        if (!currentSupported) raw = 0;
        double amps = raw / 1_000_000.0;
        double cells = voltageMv / 1000.0 * cellCount;
        double watts = Math.abs(amps) * cells * ratio;
        if (watts > 400) {
            amps = amps / 1000.0;
            watts = watts / 1000.0;
        }

        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING;
        boolean feeding = amps > 0.02;
        boolean valid = currentSupported && voltageMv >= 2500 && voltageMv <= 5000;

        powerText.setText(valid ? String.format("%.2f", watts) : "--");
        powerText.setTextColor(feeding ? th.a2 : th.a1);
        if (th.dark) {
            powerText.setShadowLayer(dp(12), 0, 0, (feeding ? th.a2 : th.a1) & 0x88FFFFFF);
        } else {
            powerText.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        }
        powerUnit.setTextColor(th.dim);
        if (powerCaption != null) {
            powerCaption.setText(feeding ? "充入功率" : "放电功率");
        }

        updateCard(currentText, String.format("%.2f A", amps));
        updateCard(voltageText, valid ? String.format("%.2f V", cells) : "--");
        updateCard(tempText, tempTenths > 0 ? String.format("%.1f °C", tempTenths / 10.0) : "--");

        if (feeding && valid && watts > peak) {
            peak = watts;
        }
        updateCard(peakText, peak > 0 ? String.format("%.2f W", peak) : "--");

        double gaugeMax = Math.max(20, peak * 1.15);
        gauge.setRatio(valid ? (float) (watts / gaugeMax) : 0f);
        chart.push(feeding && valid ? (float) watts : 0f);

        levelText.setText("电量 " + (level >= 0 ? level + "%" : "--")
                + (techText(tech).isEmpty() ? "" : " · " + techText(tech))
                + String.format(" · %d 电芯 · 倍率 %.2f", cellCount, ratio)
                + (screenHz > 0 ? String.format(" · 刷新率 %.0fHz", screenHz) : ""));

        String chip;
        int chipColor;
        if (status == BatteryManager.BATTERY_STATUS_CHARGING && !feeding) {
            chip = "已充满";
            chipColor = th.a1;
        } else if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
            chip = plugged == BatteryManager.BATTERY_PLUGGED_USB ? "数据线充电"
                    : plugged == BatteryManager.BATTERY_PLUGGED_AC ? "充电中"
                    : plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ? "无线充电" : "充电中";
            chipColor = th.a2;
        } else if (status == BatteryManager.BATTERY_STATUS_FULL) {
            chip = "已充满";
            chipColor = th.a1;
        } else if (plugged != 0) {
            chip = "已接电源";
            chipColor = th.a1;
        } else {
            chip = "未充电";
            chipColor = th.dim;
        }
        stateLabel.setText(chip);
        stateLabel.setTextColor(chipColor);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(chipColor);
        stateDot.setBackground(dotBg);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor((chipColor & 0x00FFFFFF) | 0x14000000);
        bg.setCornerRadius(dp(30));
        bg.setStroke(dp(1), (chipColor & 0x00FFFFFF) | 0x33000000);
        stateChip.setBackground(bg);

        String tier;
        int tierColor;
        if (!feeding || !valid) {
            tier = "";
            tierColor = th.dim;
        } else if (watts < 10) {
            tier = "涓流充电";
            tierColor = th.dim;
        } else if (watts < 30) {
            tier = "标准充电";
            tierColor = th.a1;
        } else if (watts < 60) {
            tier = "快速充电";
            tierColor = th.a2;
        } else {
            tier = "超快充电";
            tierColor = th.a2;
        }
        tierText.setText(tier);
        tierText.setTextColor(tierColor);

        if (!currentSupported) {
            rawText.setText("该机型未开放实时电流");
        } else if (!valid) {
            rawText.setText(String.format("原始 %d 微安 · %d 毫伏 · 电压异常，请校准", raw, voltageMv));
        } else {
            rawText.setText(String.format("原始 %d 微安 · %.3f 安 · %d 毫伏 · 每秒刷新", raw, amps, voltageMv));
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
