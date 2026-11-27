package com.dsh.chargemeter;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.content.pm.PackageManager;
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
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
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
            new Theme("OriginOS 原子", 0xFFF4F5F7, 0xFFFFFFFF, 0xFFEAECF0,
                    0xFF1B1E23, 0xFF8A8F99, 0xFF4D6BFE, 0xFF4D6BFE, false),
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
    private TextView countdownText;
    private BarView bar;
    private ChartView chart;

    private BatteryManager bm;
    private SharedPreferences cfg;
    private int cellCount = 1;
    private int themeId = 0;
    private double ratio = 1.0;
    private double peak;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<TextView, String> cardLabels = new HashMap<>();
    private static final int WINDOW = 5;
    private ValueAnimator powerAnim;
    private double displayedWatts = 0;
    private double lastAmps = 0;
    private String lastVoltsText = "-- V";
    private boolean lowPowerHinted = false;
    private boolean watchServiceOn = false;
    private final CellDetector cellDetector = new CellDetector();
    private ChargeHistory history;
    private long sessionId = -1;
    private long sessionStartCounter = 0;
    private double sessionSumWatts = 0;
    private int sessionSamples = 0;
    private long lastHistoryWrite = 0;
    private boolean cellsManual = false;
    private boolean cellsAutoDone = false;
    private int pendingCells = 0;
    private int pendingCellsCount = 0;
    private boolean firstPowerFrame = true;
    private ChargerProfiles chargers;
    private boolean chargerSession = false;
    private long chargerStartTs = 0;
    private double chargerMaxW = 0;
    private String chargerName = null;


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
        cellCount = cellsManual ? cfg.getInt("cells", recommendedCells()) : recommendedCells();
        ratio = cfg.getFloat("ratio", 1.0f);
        cellsManual = cfg.getBoolean("cells_manual", false);
        cellsAutoDone = cfg.getBoolean("cells_auto", false);
        history = new ChargeHistory(this);
        chargers = new ChargerProfiles(this);
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
        stateChip.setPadding(dp(10), dp(7), dp(12), dp(7));
        stateDot = new View(this);
        stateChip.addView(stateDot, new LinearLayout.LayoutParams(dp(6), dp(6)));
        stateLabel = new TextView(this);
        stateLabel.setTextSize(10);
        stateLabel.setMaxLines(1);
        stateLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        stateLabel.setTypeface(MONO, Typeface.BOLD);
        stateLabel.setLetterSpacing(0.06f);
        stateLabel.setPadding(dp(6), 0, 0, 0);
        stateChip.addView(stateLabel);
        head.addView(stateChip);

        TextView themeBtn = new TextView(this);
        themeBtn.setText("风格");
        themeBtn.setTextSize(12);
        themeBtn.setTextColor(th.a1);
        themeBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        themeBtn.setBackground(panelStroke(dp(30)));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(7);
        themeBtn.setLayoutParams(tlp);
        themeBtn.setOnClickListener(v -> pickTheme());
        head.addView(themeBtn);

        TextView setBtn = new TextView(this);
        setBtn.setText("校准");
        setBtn.setTextSize(12);
        setBtn.setTextColor(th.a1);
        setBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        setBtn.setBackground(panelStroke(dp(30)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(7);
        setBtn.setLayoutParams(slp);
        setBtn.setOnClickListener(v -> showSettings());
        head.addView(setBtn);
        root.addView(head);

        // ===== 主读数区 =====
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp2.topMargin = dp(30);
        hero.setLayoutParams(hlp2);

        powerCaption = new TextView(this);
        powerCaption.setText("充入功率");
        powerCaption.setTextColor(th.dim);
        powerCaption.setTextSize(12);
        powerCaption.setMaxLines(1);
        powerCaption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        powerCaption.setLetterSpacing(0.14f);
        powerCaption.setGravity(Gravity.CENTER);
        hero.addView(powerCaption);

        LinearLayout powerRow = new LinearLayout(this);
        powerRow.setOrientation(LinearLayout.HORIZONTAL);
        powerRow.setGravity(Gravity.BOTTOM);
        powerRow.setPadding(0, dp(6), 0, 0);

        GestureDetector gd = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        startActivity(new Intent(MainActivity.this, MeterActivity.class));
                        return true;
                    }
                });
        powerText = new TextView(this);
        powerText.setText("--");
        powerText.setTextColor(th.a2);
        powerText.setTextSize(56);
        powerText.setTypeface(Typeface.DEFAULT_BOLD);
        powerText.setIncludeFontPadding(false);
        powerText.setOnTouchListener((v, ev) -> {
            gd.onTouchEvent(ev);
            return true;
        });
        powerText.setOnLongClickListener(v -> {
            String txt = String.format("%.2f W · %.2f A · %s",
                    displayedWatts, lastAmps, lastVoltsText);
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("power", txt));
                Toast.makeText(this, "已复制：" + txt, Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
            }
            return true;
        });
        powerRow.addView(powerText);

        powerUnit = new TextView(this);
        powerUnit.setText("W");
        powerUnit.setTextColor(th.dim);
        powerUnit.setTextSize(20);
        powerUnit.setPadding(dp(6), 0, 0, dp(14));
        powerRow.addView(powerUnit);
        hero.addView(powerRow);

        tierText = new TextView(this);
        tierText.setText("");
        tierText.setTextSize(10);
        tierText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tierText.setLetterSpacing(0.1f);
        tierText.setGravity(Gravity.CENTER);
        tierText.setPadding(0, dp(2), 0, 0);
        hero.addView(tierText);

        countdownText = new TextView(this);
        countdownText.setTextColor(th.a1);
        countdownText.setTextSize(11);
        countdownText.setGravity(Gravity.CENTER);
        countdownText.setPadding(dp(8), dp(8), dp(8), 0);
        countdownText.setMaxLines(1);
        countdownText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        hero.addView(countdownText);
        root.addView(hero);

        bar = new BarView(this);
        bar.setTheme(th.a1, th.a2, th.dark ? th.line : 0xFFE5E5EA);
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        blp2.topMargin = dp(18);
        blp2.leftMargin = dp(2);
        blp2.rightMargin = dp(2);
        bar.setLayoutParams(blp2);
        root.addView(bar);

        factorText = new TextView(this);
        factorText.setTextColor(th.dim);
        factorText.setTextSize(10);
        factorText.setGravity(Gravity.CENTER);
        factorText.setPadding(dp(8), dp(10), dp(8), 0);
        factorText.setMaxLines(1);
        factorText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(factorText);

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
        chartCard.setBackground(panelStroke(0));
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
        chartTitle.setTextSize(14);
        chartTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chartTitle.setMaxLines(1);
        chartTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chartHead.addView(chartTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView chartHint = new TextView(this);
        chartHint.setText("最近 2 分钟");
        chartHint.setTextColor(th.dim);
        chartHint.setTextSize(11);
        chartHint.setMaxLines(1);
        chartHint.setEllipsize(android.text.TextUtils.TruncateAt.END);
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
        levelText.setPadding(dp(8), dp(14), dp(8), 0);
        levelText.setMaxLines(1);
        levelText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(levelText);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(18);
        actionRow.setLayoutParams(alp);

        TextView detailBtn = actionButton("详细信息");
        detailBtn.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, DetailActivity.class)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        actionRow.addView(detailBtn, dlp);

        TextView aboutBtn = actionButton("开源");
        aboutBtn.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AboutActivity.class)));
        LinearLayout.LayoutParams alpb = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        alpb.leftMargin = dp(10);
        actionRow.addView(aboutBtn, alpb);

        root.addView(actionRow);

        rawText = new TextView(this);
        rawText.setTextSize(9);
        rawText.setTextColor(th.dark ? (th.dim & 0x00FFFFFF) | 0x99000000 : 0xFFA1A1A6);
        rawText.setTypeface(MONO);
        rawText.setGravity(Gravity.CENTER);
        rawText.setPadding(dp(8), dp(6), dp(8), 0);
        rawText.setMaxLines(1);
        rawText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(rawText);

        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 201);
        }



        try {
            Display d = getWindowManager().getDefaultDisplay();
            screenHz = d.getRefreshRate();
        } catch (Exception ignored) {
        }

    }

    private Dialog themedDialog(View content) {
        Dialog d = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.dimAmount = 0.62f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        return d;
    }

    private LinearLayout dialogPanel() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.dark ? th.panel : 0xFFFFFFFF);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), th.line);
        l.setBackground(bg);
        l.setPadding(dp(18), dp(18), dp(18), dp(14));
        return l;
    }

    private TextView dialogTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(th.text);
        t.setTextSize(18);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(4), 0, 0, dp(10));
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(th.line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        v.setLayoutParams(lp);
        return v;
    }

    private TextView actionButton(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.panel);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), th.line);
        b.setBackground(bg);
        b.setTextColor(th.a1);
        return b;
    }

    private void pickTheme() {
        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle("选择风格"));

        for (int i = 0; i < THEMES.length; i++) {
            final int idx = i;
            final Theme t = THEMES[i];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(11), dp(8), dp(11));

            View swatch = new View(this);
            GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{t.bg, t.a1, t.a2});
            sg.setCornerRadius(dp(7));
            sg.setStroke(dp(1), 0x33FFFFFF);
            swatch.setBackground(sg);
            row.addView(swatch, new LinearLayout.LayoutParams(dp(40), dp(26)));

            TextView name = new TextView(this);
            name.setText(t.name);
            name.setTextColor(th.text);
            name.setTextSize(15);
            name.setPadding(dp(14), 0, 0, 0);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(name, nlp);

            if (idx == themeId) {
                TextView ck = new TextView(this);
                ck.setText("✓");
                ck.setTextSize(16);
                ck.setTypeface(MONO, Typeface.BOLD);
                ck.setTextColor(th.a2);
                ck.setPadding(dp(8), 0, dp(4), 0);
                row.addView(ck);
            }

            GradientDrawable rbg = new GradientDrawable();
            rbg.setColor(idx == themeId ? (th.a1 & 0x00FFFFFF) | 0x14000000 : Color.TRANSPARENT);
            rbg.setCornerRadius(dp(12));
            row.setBackground(rbg);
            row.setOnClickListener(v -> {
                if (idx != themeId) {
                    cfg.edit().putInt("theme", idx).apply();
                    recreate();
                } else {
                    dismissDialogAndRecreate();
                }
            });

            panel.addView(row);
            if (i < THEMES.length - 1) panel.addView(divider());
        }

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextSize(14);
        cancel.setTextColor(th.dim);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(14), dp(14), dp(14), dp(6));
        panel.addView(cancel);

        final Dialog dlg = themedDialog(panel);
        cancel.setOnClickListener(v -> dlg.dismiss());
        dlg.show();
    }

    private void dismissDialogAndRecreate() {
        recreate();
    }

    private GradientDrawable panelStroke(int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(th.panel);
        d.setCornerRadius(dp(radiusDp));
        if (th.dark) {
            d.setStroke(dp(1), th.line);
        }
        return d;
    }

    private void showSettings() {
        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle("校准"));

        if (isHuaweiFamily()) {
            TextView warn = new TextView(this);
            warn.setText("华为/荣耀机型充电协议较特殊，读数偏差大时请用下方「自动校准」");
            warn.setTextColor(0xFFFF9F0A);
            warn.setTextSize(11);
            warn.setPadding(dp(4), 0, 0, dp(8));
            panel.addView(warn);
        }

        TextView tip = new TextView(this);
        tip.setText("推荐 " + recommendedCells() + " 电芯 · " + Build.MANUFACTURER + " " + Build.MODEL);
        tip.setTextColor(th.a1);
        tip.setTextSize(12);
        tip.setPadding(dp(4), 0, 0, dp(12));
        panel.addView(tip);

        TextView l1 = new TextView(this);
        l1.setText("电芯数");
        l1.setTextColor(th.dim);
        l1.setTextSize(12);
        l1.setPadding(dp(4), 0, 0, dp(6));
        panel.addView(l1);

        EditText e1 = new EditText(this);
        e1.setInputType(InputType.TYPE_CLASS_NUMBER);
        e1.setText(String.valueOf(cellCount == 1 ? recommendedCells() : cellCount));
        styleInput(e1);
        panel.addView(e1);

        TextView l2 = new TextView(this);
        l2.setText("校准倍率");
        l2.setTextColor(th.dim);
        l2.setTextSize(12);
        l2.setPadding(dp(4), dp(14), 0, dp(6));
        panel.addView(l2);

        EditText e2 = new EditText(this);
        e2.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e2.setText(String.valueOf(ratio));
        styleInput(e2);
        panel.addView(e2);

        TextView hint = new TextView(this);
        hint.setText("1~10，用来对齐手机自己显示的瓦数");
        hint.setTextColor(th.dim);
        hint.setTextSize(10);
        hint.setPadding(dp(4), dp(6), 0, 0);
        panel.addView(hint);

        TextView autoBtn = new TextView(this);
        autoBtn.setText("自动校准（对比手机显示的瓦数）");
        autoBtn.setTextColor(th.a1);
        autoBtn.setTextSize(12);
        autoBtn.setPadding(dp(4), dp(16), 0, 0);
        autoBtn.setOnClickListener(v -> showAutoCalibrate());
        panel.addView(autoBtn);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(18), 0, 0);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRow.setLayoutParams(blp);

        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        TextView cancel = pillButton("取消", false);
        btnRow.addView(cancel, half);

        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = dp(10);
        TextView save = pillButton("保存", true);
        btnRow.addView(save, half2);
        panel.addView(btnRow);

        final Dialog dlg = themedDialog(panel);
        cancel.setOnClickListener(v -> dlg.dismiss());
        save.setOnClickListener(v -> {
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
            cfg.edit().putInt("cells", cellCount).putFloat("ratio", (float) ratio)
                    .putBoolean("cells_manual", true).apply();
            cellsManual = true;
            peak = 0;
            chart.clear();
            tick();
            dlg.dismiss();
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        });
        dlg.show();
    }

    private void styleInput(EditText e) {
        e.setTextColor(th.text);
        e.setTextSize(17);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.dark ? 0xFF101A26 : 0xFFF2F2F7);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), th.line);
        e.setBackground(bg);
    }

    private TextView pillButton(String text, boolean primary) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(11), dp(16), dp(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        if (primary) {
            bg.setColor(th.a1);
            b.setTextColor(th.dark ? 0xFF06121A : Color.WHITE);
        } else {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(dp(1), th.line);
            b.setTextColor(th.dim);
        }
        b.setBackground(bg);
        return b;
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

    public static int recommendedCells() {
        String b = (Build.BRAND + " " + Build.MANUFACTURER + " " + Build.MODEL).toLowerCase();
        // 国产快充机型（67W 以上基本是双电芯串联）
        if (b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")
                || b.contains("oppo") || b.contains("oneplus") || b.contains("realme")
                || b.contains("vivo") || b.contains("iqoo")) {
            return 2;
        }
        if (b.contains("honor")) return 2;
        // 三星 / 谷歌 / 索尼 / 摩托 等以单电芯为主
        return 1;
    }

    private boolean isHuaweiFamily() {
        String b = (Build.BRAND + " " + Build.MANUFACTURER).toLowerCase();
        return b.contains("huawei") || b.contains("harmony");
    }

    /** 自动校准：让用户把手机系统显示的瓦数填进来，反推校准倍率 */
    private void showAutoCalibrate() {
        LinearLayout panel = dialogPanel();
        panel.addView(dialogTitle("自动校准"));

        TextView cur = new TextView(this);
        cur.setText(String.format("当前读数：%.2f W（%d 电芯 · 倍率 %.2f）",
                displayedWatts, cellCount, ratio));
        cur.setTextColor(th.a1);
        cur.setTextSize(13);
        cur.setPadding(dp(4), 0, 0, dp(14));
        panel.addView(cur);

        TextView l1 = new TextView(this);
        l1.setText("手机系统/充电器上显示的瓦数");
        l1.setTextColor(th.dim);
        l1.setTextSize(12);
        l1.setPadding(dp(4), 0, 0, dp(6));
        panel.addView(l1);

        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setHint("例如 67");
        styleInput(e);
        panel.addView(e);

        TextView hint = new TextView(this);
        hint.setText("填 0 或留空则保持当前倍率");
        hint.setTextColor(th.dim);
        hint.setTextSize(10);
        hint.setPadding(dp(4), dp(6), 0, 0);
        panel.addView(hint);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(18);
        btnRow.setLayoutParams(blp);

        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        TextView cancel = pillButton("取消", false);
        btnRow.addView(cancel, half);

        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = dp(10);
        TextView ok = pillButton("开始校准", true);
        btnRow.addView(ok, half2);
        panel.addView(btnRow);

        final Dialog dlg = themedDialog(panel);
        cancel.setOnClickListener(v -> dlg.dismiss());
        ok.setOnClickListener(v -> {
            double target;
            try {
                target = Double.parseDouble(e.getText().toString().trim());
            } catch (Exception ex) {
                dlg.dismiss();
                return;
            }
            if (target > 0 && displayedWatts > 0.5) {
                double newRatio = ratio * (target / displayedWatts);
                if (newRatio > 0.1 && newRatio < 10) {
                    ratio = newRatio;
                    cfg.edit().putFloat("ratio", (float) ratio)
                            .putBoolean("cells_manual", true).apply();
                    cellsManual = true;
                    peak = 0;
                    chart.clear();
                    tick();
                    Toast.makeText(this, String.format("校准完成：倍率 %.2f", ratio),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "数值差距太大，请检查输入", Toast.LENGTH_SHORT).show();
                }
            }
            dlg.dismiss();
        });
        dlg.show();
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
        tv.setPadding(dp(12), dp(10), dp(12), dp(10));
        tv.setMinimumHeight(dp(78));
        tv.setBackground(panelStroke(dp(20)));
        tv.setLineSpacing(dp(5), 1f);
        tv.setMaxLines(2);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cardLabels.put(tv, label);
        setCard(tv, value);

        if (label.contains("电流")) currentText = tv;
        else if (label.contains("电压")) voltageText = tv;
        else if (label.contains("温度")) tempText = tv;
        else {
            peakText = tv;
            tv.setOnClickListener(v -> {
                peak = 0;
                setCard(tv, "--");
                Toast.makeText(this, "峰值已清零", Toast.LENGTH_SHORT).show();
            });
        }
        return tv;
    }

    private void setCard(TextView tv, String value) {
        setCard(tv, value, null);
    }

    /** label(小) / value(大) / sub(可选小字，如单芯电压) */
    private void setCard(TextView tv, String value, String sub) {
        String label = cardLabels.get(tv);
        if (label == null) label = "";
        boolean hasSub = sub != null && !sub.isEmpty();
        String full = label + "\n" + value + (hasSub ? "\n" + sub : "");
        int labelEnd = label.length();
        int valueStart = labelEnd + 1;
        int valueEnd = valueStart + value.length();
        SpannableString ss = new SpannableString(full);
        ss.setSpan(new AbsoluteSizeSpan(10, true), 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(th.dim), 0, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new AbsoluteSizeSpan(16, true), valueStart, valueEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(th.text), valueStart, valueEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (hasSub) {
            ss.setSpan(new AbsoluteSizeSpan(9, true), valueEnd + 1, full.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new ForegroundColorSpan(th.dim), valueEnd + 1, full.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tv.setTypeface(MONO);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setText(ss);
    }

    private void updateCard(TextView tv, String value) {
        if (tv != null) setCard(tv, value);
    }

    private void updateCard(TextView tv, String value, String sub) {
        if (tv != null) setCard(tv, value, sub);
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
        if (powerAnim != null) powerAnim.cancel();
        if (bar != null) bar.stopAnim();
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

        long counter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        long raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        boolean currentSupported = raw != Long.MIN_VALUE;
        if (!currentSupported) raw = 0;
        double instantAmps = raw / 1_000_000.0;
        double instantWatts = Math.abs(instantAmps) * (voltageMv / 1000.0) * cellCount * ratio;
        if (instantWatts > 400) {
            instantAmps = instantAmps / 1000.0;
        }
        double amps = instantAmps;
        double voltsMv = voltageMv;
        double packVolts = voltsMv / 1000.0 * cellCount;
        double watts = Math.abs(amps) * packVolts * ratio;

        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING;
        boolean feeding = amps > 0.02;
        boolean valid = currentSupported && voltageMv >= 2500 && voltageMv <= 5000;

        displayedWatts = watts;
        powerText.setText(valid ? String.format("%.2f", watts) : "--");
        powerText.setTextColor(feeding ? th.a2 : th.a1);
        if (th.dark) {
            powerText.setShadowLayer(dp(12), 0, 0, (feeding ? th.a2 : th.a1) & 0x88FFFFFF);
        } else {
            powerText.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        }
        powerUnit.setTextColor(th.dim);
        if (powerCaption != null) {
            powerCaption.setText((feeding ? "充入功率" : "放电功率")
                    + (chargerName != null ? " · " + chargerName : ""));
        }

        lastAmps = amps;
        lastVoltsText = String.format("%.2f V", packVolts);
        updateCard(currentText, String.format("%.2f A", amps));
        String voltSub = null;
        if (valid) {
            voltSub = cellCount > 1
                    ? String.format("单芯 %.2f V · %d 串", voltsMv / 1000.0, cellCount)
                    : "单电芯";
        }
        updateCard(voltageText, valid ? String.format("%.2f V", packVolts) : "--", voltSub);
        updateCard(tempText, tempTenths > 0 ? String.format("%.1f °C", tempTenths / 10.0) : "--");

        if (feeding && valid && watts > peak) {
            peak = watts;
        }
        updateCard(peakText, peak > 0 ? String.format("%.2f W", peak) : "--");

        double gaugeMax = Math.max(20, peak * 1.15);
        bar.setRatio(valid ? (float) (watts / gaugeMax) : 0f);
        if (feeding && valid) {
            chart.push((float) watts);
        }

        updateCountdown(level, voltageMv, watts, feeding, valid);

        levelText.setText("电量 " + (level >= 0 ? level + "%" : "--")
                + String.format(" · %d 电芯%s · 倍率 %.2f", cellCount,
                        cellsManual ? "" : (cellsAutoDone ? "(自动)" : "(识别中)"), ratio)
                + (screenHz > 0 ? String.format(" · %.0fHz", screenHz) : ""));

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

        updateCharger(plugged, watts);
        if (charging && plugged != 0) {
            updateCellsAuto(counter, amps);
        }
        updateHistory(charging, plugged, level, watts, counter);
        maybeStartWatch(charging, plugged);

        if (!currentSupported) {
            rawText.setText("该机型未开放实时电流");
        } else if (!valid) {
            rawText.setText(String.format("原始 %d 微安 · %d 毫伏 · 电压异常，请校准", raw, voltageMv));
        } else {
            rawText.setText(String.format("原始 %d 微安 · %.3f 安 · %d 毫伏 · 每秒刷新", raw, amps, voltageMv));
        }
    }

    private void updateCountdown(int level, int voltageMv, double watts, boolean feeding, boolean valid) {
        if (countdownText == null) return;
        if (!feeding || !valid || level <= 0 || level >= 100) {
            countdownText.setText(feeding && level >= 100 ? "已充满" : "");
            return;
        }
        long counter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        if (counter <= 0 || watts < 1) {
            countdownText.setText("");
            return;
        }
        double fullMah = (counter / 1000.0) / (level / 100.0);
        double remainMah = fullMah - counter / 1000.0;
        double packVoltage = voltageMv / 1000.0 * cellCount;
        double remainWh = remainMah / 1000.0 * packVoltage * ratio;
        double hours = remainWh / watts;
        long minutes = Math.round(hours * 60);
        if (minutes < 1 || minutes > 600) {
            countdownText.setText("");
            return;
        }
        long h = minutes / 60;
        long m = minutes % 60;
        countdownText.setText(h > 0
                ? String.format("距充满约 %d 小时 %d 分", h, m)
                : String.format("距充满约 %d 分钟", m));
    }

    private void updateCharger(int plugged, double watts) {
        long now = System.currentTimeMillis();
        if (plugged != 0) {
            if (!chargerSession) {
                chargerSession = true;
                chargerStartTs = now;
                chargerMaxW = watts;
                chargerName = null;
            } else {
                chargerMaxW = Math.max(chargerMaxW, watts);
                if (chargerName == null && now - chargerStartTs > 45000 && chargerMaxW > 3) {
                    ChargerProfiles.Row hit = chargers.match(chargerMaxW);
                    if (hit != null) {
                        chargerName = hit.name;
                        Toast.makeText(this, "识别到充电器：" + hit.name
                                + String.format("（%.1f W）", hit.maxW), Toast.LENGTH_LONG).show();
                    } else {
                        ChargerProfiles.Row added = chargers.add(
                                "充电器 " + (chargers.count() + 1), chargerMaxW);
                        chargerName = added.name;
                        Toast.makeText(this, String.format("新充电器已记录：%.1f W，可在关于页改名",
                                chargerMaxW), Toast.LENGTH_LONG).show();
                    }
                }
            }
        } else {
            chargerSession = false;
            chargerMaxW = 0;
            chargerName = null;
        }
    }

    private void updateCellsAuto(long counter, double amps) {
        if (cellsManual) return;
        int detected = cellDetector.add(counter, amps, System.currentTimeMillis());
        if (detected <= 0) return;
        if (detected == pendingCells) {
            pendingCellsCount++;
        } else {
            pendingCells = detected;
            pendingCellsCount = 1;
        }
        // 连续两次识别一致才采纳，避免 counter 粒度造成的误判把双电芯写成单电芯
        if (pendingCellsCount >= 2 && detected != cellCount) {
            cellCount = detected;
            cellsAutoDone = true;
            // 倍率是基于旧电芯数校准出来的，电芯数变了就复位，避免功率被放大/缩小
            ratio = 1.0;
            cfg.edit().putInt("cells", cellCount).putFloat("ratio", 1.0f)
                    .putBoolean("cells_auto", true).apply();
            peak = 0;
            chart.clear();
        } else if (pendingCellsCount >= 2) {
            cellsAutoDone = true;
        }
    }

    private void updateHistory(boolean charging, int plugged, int level, double watts, long counter) {
        long now = System.currentTimeMillis();
        if (charging && plugged != 0) {
            if (sessionId < 0) {
                // App 被系统回收重启时，续接 3 分钟内尚未结束的会话，避免同一次充电出现多条记录
                sessionId = history.findResumable(3 * 60 * 1000L);
                sessionStartCounter = counter;
                sessionSumWatts = 0;
                sessionSamples = 0;
                if (sessionId < 0) {
                    sessionId = history.start(level, watts, cellCount);
                }
                lastHistoryWrite = now;
            } else {
                sessionSumWatts += watts;
                sessionSamples++;
                if (now - lastHistoryWrite > 5000) {
                    lastHistoryWrite = now;
                    double wh = whFromCounter(counter);
                    history.update(sessionId, level, sessionSamples > 0
                            ? sessionSumWatts / sessionSamples : watts, wh);
                }
            }
        } else if (sessionId >= 0) {
            double wh = whFromCounter(counter);
            history.update(sessionId, level, sessionSamples > 0
                    ? sessionSumWatts / sessionSamples : 0, wh);
            sessionId = -1;
        }
    }

    private double whFromCounter(long counter) {
        if (counter <= 0 || sessionStartCounter <= 0 || counter < sessionStartCounter) return 0;
        double deltaAh = (counter - sessionStartCounter) / 1_000_000.0;
        double packVoltage = 4.0;
        try {
            if (lastVoltsText != null) {
                double v = Double.parseDouble(lastVoltsText.replace("V", "").trim());
                if (v > 0.5 && v < 20) packVoltage = v;
            }
        } catch (Exception ignored) {
        }
        return deltaAh * packVoltage;
    }

    private void maybeStartWatch(boolean charging, int plugged) {
        boolean enabled = cfg.getBoolean("notify_full", true);
        if (charging && plugged != 0 && enabled) {
            if (!watchServiceOn) {
                try {
                    startForegroundService(new Intent(this, ChargeWatchService.class));
                    watchServiceOn = true;
                } catch (Exception ignored) {
                }
            }
        } else if (plugged == 0 || !enabled) {
            if (watchServiceOn) {
                try {
                    stopService(new Intent(this, ChargeWatchService.class));
                } catch (Exception ignored) {
                }
                watchServiceOn = false;
            }
        }
    }

    private void diagnose(boolean charging, boolean feeding, double watts, int level) {
        if (!charging || !feeding) {
            lowPowerHinted = false;
            return;
        }
        if (!lowPowerHinted && watts > 0.3 && watts < 10 && level < 80) {
            lowPowerHinted = true;
            Toast.makeText(this,
                    "功率只有 " + String.format("%.1f", watts) + " W，可能线材或充电器不支持快充",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void broadcastPower(double amps, double watts, int level) {
        try {
            Intent i = new Intent("com.dsh.chargemeter.POWER");
            i.setPackage(getPackageName());
            i.putExtra("watts", watts);
            i.putExtra("amps", amps);
            i.putExtra("level", level);
            i.putExtra("timestamp", System.currentTimeMillis());
            sendBroadcast(i);
        } catch (Exception ignored) {
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
