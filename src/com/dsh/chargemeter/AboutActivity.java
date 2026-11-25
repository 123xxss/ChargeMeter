package com.dsh.chargemeter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AboutActivity extends Activity {

    private static final String REPO = "https://github.com/123xxss/ChargeMeter";
    private static final String ISSUES = REPO + "/issues";
    private static final Typeface MONO = Typeface.create("monospace", Typeface.NORMAL);

    private MainActivity.Theme th;
    private TextView capacityText;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateBattery();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences cfg = getSharedPreferences("cfg", Context.MODE_PRIVATE);
        int themeId = cfg.getInt("theme", 0);
        if (themeId < 0 || themeId >= MainActivity.THEMES.length) themeId = 0;
        th = MainActivity.THEMES[themeId];

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
        root.setPadding(dp(18), statusBar + dp(10), dp(18), dp(28));

        // 顶栏
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(30);
        back.setTextColor(th.a1);
        back.setPadding(0, 0, dp(10), dp(4));
        back.setOnClickListener(v -> finish());
        head.addView(back);

        TextView title = new TextView(this);
        title.setText("关于");
        title.setTextColor(th.text);
        title.setTextSize(24);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(head);

        // 应用信息卡
        LinearLayout appCard = card();
        TextView name = new TextView(this);
        name.setText("充电功率");
        name.setTextColor(th.text);
        name.setTextSize(22);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        appCard.addView(name);

        TextView ver = new TextView(this);
        ver.setText("版本 " + versionName() + " · MIT License");
        ver.setTextColor(th.dim);
        ver.setTextSize(12);
        ver.setPadding(0, dp(4), 0, 0);
        appCard.addView(ver);

        TextView desc = new TextView(this);
        desc.setText("实时读取电池侧充放电功率，无广告、无联网权限、不收集任何数据。");
        desc.setTextColor(th.dim);
        desc.setTextSize(13);
        desc.setPadding(0, dp(10), 0, 0);
        desc.setLineSpacing(dp(4), 1f);
        appCard.addView(desc);
        root.addView(appCard);

        // 开源地址（醒目）
        LinearLayout repoCard = card();
        TextView repoLabel = new TextView(this);
        repoLabel.setText("开源地址");
        repoLabel.setTextColor(th.dim);
        repoLabel.setTextSize(11);
        repoLabel.setLetterSpacing(0.06f);
        repoCard.addView(repoLabel);

        TextView repoUrl = new TextView(this);
        repoUrl.setText("github.com/123xxss/ChargeMeter");
        repoUrl.setTextColor(th.a1);
        repoUrl.setTextSize(15);
        repoUrl.setTypeface(MONO, Typeface.BOLD);
        repoUrl.setPadding(0, dp(8), 0, dp(2));
        repoCard.addView(repoUrl);

        TextView repoHint = new TextView(this);
        repoHint.setText("点击打开 · 欢迎 Star / 提 Issue / 提交 PR");
        repoHint.setTextColor(th.dim);
        repoHint.setTextSize(11);
        repoHint.setPadding(0, dp(6), 0, 0);
        repoCard.addView(repoHint);
        repoCard.setOnClickListener(v -> openUrl(REPO));
        root.addView(repoCard);

        // 电池信息卡
        LinearLayout batCard = card();
        TextView batTitle = new TextView(this);
        batTitle.setText("电池信息");
        batTitle.setTextColor(th.dim);
        batTitle.setTextSize(11);
        batCard.addView(batTitle);

        capacityText = new TextView(this);
        capacityText.setTextColor(th.text);
        capacityText.setTextSize(14);
        capacityText.setTypeface(MONO);
        capacityText.setPadding(0, dp(8), 0, 0);
        capacityText.setLineSpacing(dp(6), 1f);
        batCard.addView(capacityText);
        root.addView(batCard);

        // 充电历史
        LinearLayout hisCard = card();
        TextView hisTitle = new TextView(this);
        hisTitle.setText("最近充电记录");
        hisTitle.setTextColor(th.dim);
        hisTitle.setTextSize(11);
        hisCard.addView(hisTitle);

        TextView hisBody = new TextView(this);
        hisBody.setTextColor(th.text);
        hisBody.setTextSize(12);
        hisBody.setTypeface(MONO);
        hisBody.setLineSpacing(dp(5), 1f);
        hisBody.setPadding(0, dp(8), 0, 0);
        try {
            ChargeHistory his = new ChargeHistory(this);
            java.util.List<ChargeHistory.Row> rows = his.recent(5);
            if (rows.isEmpty()) {
                hisBody.setText("还没有记录，插上充电器后会自动记录");
                hisBody.setTextColor(th.dim);
            } else {
                StringBuilder sb = new StringBuilder();
                for (ChargeHistory.Row r : rows) {
                    sb.append(ChargeHistory.format(r)).append("\n\n");
                }
                hisBody.setText(sb.toString().trim());
            }
        } catch (Exception e) {
            hisBody.setText("读取失败");
        }
        hisCard.addView(hisBody);
        root.addView(hisCard);

        // 链接卡
        LinearLayout linkCard = card();
        linkCard.addView(linkRow("反馈问题（GitHub Issues）", ISSUES));
        linkCard.addView(separator());
        linkCard.addView(linkRow("项目主页", REPO + "#readme"));
        linkCard.addView(separator());
        linkCard.addView(linkRow("开源许可证（MIT）", REPO + "/blob/main/LICENSE"));
        root.addView(linkCard);

        TextView foot = new TextView(this);
        foot.setText("数据来源：Android BatteryService\n功率为电池侧数值，非充电器输入功率");
        foot.setTextColor(th.dim);
        foot.setTextSize(10);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(20), 0, 0);
        foot.setLineSpacing(dp(4), 1f);
        root.addView(foot);

        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "5.3";
        }
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.dark ? th.panel : 0xFFFFFFFF);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), th.line);
        l.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        l.setLayoutParams(lp);
        return l;
    }

    private View separator() {
        View v = new View(this);
        v.setBackgroundColor(th.line);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private View linkRow(String text, final String url) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(th.a1);
        tv.setTextSize(13);
        tv.setPadding(0, dp(13), 0, dp(13));
        tv.setOnClickListener(v -> openUrl(url));
        return tv;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private void updateBattery() {
        android.os.BatteryManager bm =
                (android.os.BatteryManager) getSystemService(BATTERY_SERVICE);
        long counter = bm.getLongProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        Intent it = registerReceiver(null,
                new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = -1;
        int temp = 0;
        String tech = "";
        int voltage = 0;
        if (it != null) {
            level = it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            temp = it.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0);
            voltage = it.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0);
            tech = it.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("电量  ").append(level >= 0 ? level + "%" : "--").append('\n');
        sb.append("电压  ").append(voltage > 0 ? voltage + " mV" : "--").append('\n');
        sb.append("温度  ").append(temp > 0 ? String.format("%.1f °C", temp / 10.0) : "--").append('\n');
        sb.append("材质  ").append(tech == null || tech.isEmpty() ? "--" : tech).append('\n');
        if (counter > 0) {
            sb.append("充入  ").append(String.format("%.0f mAh", counter / 1000.0));
            if (level > 5 && level <= 100) {
                double full = counter / (level / 100.0);
                sb.append(String.format("（满电约 %.0f mAh）", full / 1000.0));
            }
        } else {
            sb.append("充入  --");
        }
        capacityText.setText(sb.toString());
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
