package com.dsh.chargemeter;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 详细数据 / 高级功能页 */
public class DetailActivity extends Activity {

    private static final Typeface MONO = Typeface.create("monospace", Typeface.NORMAL);

    private MainActivity.Theme th;
    private TextView liveText;
    private TextView statText;
    private TextView totalText;
    private ChargeHistory history;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences cfg = getSharedPreferences("cfg", Context.MODE_PRIVATE);
        int themeId = cfg.getInt("theme", 0);
        if (themeId < 0 || themeId >= MainActivity.THEMES.length) themeId = 0;
        th = MainActivity.THEMES[themeId];
        history = new ChargeHistory(this);

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
        title.setText("详细数据");
        title.setTextColor(th.text);
        title.setTextSize(22);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(head);

        // 实时电池
        LinearLayout live = card();
        live.addView(cardTitle("实时电池"));
        liveText = body();
        live.addView(liveText);
        root.addView(live);

        // 本次充电
        LinearLayout stat = card();
        stat.addView(cardTitle("最近一次充电"));
        statText = body();
        stat.addView(statText);
        root.addView(stat);

        // 累计统计
        LinearLayout total = card();
        total.addView(cardTitle("累计统计"));
        totalText = body();
        total.addView(totalText);
        total.addView(exportButton());
        root.addView(total);

        // 术语说明
        LinearLayout note = card();
        note.addView(cardTitle("说明"));
        TextView noteBody = body();
        noteBody.setText("· 电压：系统上报的是「单芯电压」，双电芯机型的包电压 = 单芯 × 2\n"
                + "· 电芯数：优先由充电曲线自动识别，也可在「校准」手动指定\n"
                + "· 功率：|电流| × 包电压 × 倍率，为电池侧读数，非充电器输入功率\n"
                + "· 健康：系统 BatteryService 上报的状态（良好 / 过热 / 老化等）");
        noteBody.setTextSize(11);
        note.addView(noteBody);
        root.addView(note);

        TextView tip = new TextView(this);
        tip.setText("高级功能：数据可导出 CSV，供测充电器 / 对比曲线使用");
        tip.setTextColor(th.dim);
        tip.setTextSize(10);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(18), 0, 0);
        root.addView(tip);

        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        refresh();
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

    private TextView cardTitle(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(th.dim);
        v.setTextSize(11);
        v.setLetterSpacing(0.06f);
        return v;
    }

    private TextView body() {
        TextView v = new TextView(this);
        v.setTextColor(th.text);
        v.setTextSize(12);
        v.setTypeface(MONO);
        v.setLineSpacing(dp(6), 1f);
        v.setPadding(0, dp(8), 0, 0);
        return v;
    }

    private View exportButton() {
        TextView b = new TextView(this);
        b.setText("导出全部记录（CSV）");
        b.setTextColor(th.a1);
        b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(11), dp(14), dp(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), th.a1);
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> exportCsv());
        return b;
    }

    private String healthText(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "良好";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "过热";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "损坏";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "电压过高";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "故障";
            case BatteryManager.BATTERY_HEALTH_COLD: return "过冷";
            default: return "未知";
        }
    }

    private String plugText(int p) {
        switch (p) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "交流充电器";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "无线";
            default: return "未连接";
        }
    }

    private String statusText(int s) {
        switch (s) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "放电中";
            case BatteryManager.BATTERY_STATUS_FULL: return "已充满";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "未充电";
            default: return "未知";
        }
    }

    private void refresh() {
        Intent it = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        SharedPreferences cfg = getSharedPreferences("cfg", Context.MODE_PRIVATE);
        int cells = cfg.contains("cells") ? cfg.getInt("cells", 1) : 1;
        double ratio = cfg.getFloat("ratio", 1.0f);

        int level = -1, voltage = 0, temp = 0, health = 0, status = 0, plugged = 0;
        String tech = "";
        if (it != null) {
            level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            voltage = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            health = it.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
            status = it.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            plugged = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            tech = it.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        }
        long raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        long counter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        double amps = raw == Long.MIN_VALUE ? 0 : raw / 1_000_000.0;
        double watts = Math.abs(amps) * (voltage / 1000.0) * cells * ratio;
        if (watts > 400) watts /= 1000.0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("设备    %s %s%n", Build.MANUFACTURER, Build.MODEL));
        sb.append(String.format("系统    Android %s (API %d)%n", Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
        sb.append(String.format("电量    %s%n", level >= 0 ? level + "%" : "--"));
        sb.append(String.format("功率    %.2f W（%d 电芯 · 倍率 %.2f）%n", watts, cells, ratio));
        sb.append(String.format("电流    %s%n", raw == Long.MIN_VALUE ? "不支持" : String.format("%.3f A", amps)));
        if (cells > 1) {
            sb.append(String.format("电压    单芯 %d mV · 包 %d mV%n", voltage, voltage * cells));
        } else {
            sb.append(String.format("电压    %d mV%n", voltage));
        }
        sb.append(String.format("温度    %s%n", temp > 0 ? String.format("%.1f °C", temp / 10.0) : "--"));
        sb.append(String.format("健康    %s%n", healthText(health)));
        sb.append(String.format("状态    %s%n", statusText(status)));
        sb.append(String.format("电源    %s%n", plugText(plugged)));
        sb.append(String.format("材质    %s%n", tech == null || tech.isEmpty() ? "--" : tech));
        if (counter > 0) {
            sb.append(String.format("充入    %.0f mAh", counter / 1000.0));
            if (level > 5) {
                sb.append(String.format("（满电约 %.0f mAh）", counter / 1000.0 / (level / 100.0)));
            }
        }
        liveText.setText(sb.toString().trim());

        List<ChargeHistory.Row> rows = history.recent(1);
        if (rows.isEmpty()) {
            statText.setText("本次还没有记录（插上充电器后自动开始）");
            statText.setTextColor(th.dim);
        } else {
            ChargeHistory.Row r = rows.get(0);
            SimpleDateFormat f = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
            long mins = Math.max(1, (r.endTs - r.startTs) / 60000);
            statText.setTextColor(th.text);
            statText.setText(String.format("开始    %s%n时长    %d 分钟%n电量    %d%% → %d%%%n平均    %.1f W%n峰值    %.1f W%n充入    %.2f Wh",
                    f.format(new Date(r.startTs)), mins, r.levelStart, r.levelEnd,
                    r.avgWatts, r.maxWatts, r.wh));
        }

        List<ChargeHistory.Row> all = history.recent(200);
        if (all.isEmpty()) {
            totalText.setText("暂无数据");
            totalText.setTextColor(th.dim);
        } else {
            double sumWh = 0, maxW = 0;
            int sessions = all.size();
            for (ChargeHistory.Row r : all) {
                sumWh += r.wh;
                if (r.maxWatts > maxW) maxW = r.maxWatts;
            }
            totalText.setTextColor(th.text);
            totalText.setText(String.format("充电次数 %d 次%n累计充入 %.1f Wh%n历史峰值 %.1f W", sessions, sumWh, maxW));
        }
    }

    private void exportCsv() {
        List<ChargeHistory.Row> rows = history.recent(500);
        if (rows.isEmpty()) {
            Toast.makeText(this, "还没有记录可导出", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder(
                "start,end,level_start,level_end,avg_w,max_w,wh,cells,ratio\n");
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        SharedPreferences cfg = getSharedPreferences("cfg", Context.MODE_PRIVATE);
        int cells = cfg.contains("cells") ? cfg.getInt("cells", 1) : 1;
        double ratio = cfg.getFloat("ratio", 1.0f);
        int exported = 0;
        for (ChargeHistory.Row r : rows) {
            if (r.endTs - r.startTs < 30000) continue;
            exported++;
            sb.append(f.format(new Date(r.startTs))).append(',')
              .append(f.format(new Date(r.endTs))).append(',')
              .append(r.levelStart).append(',')
              .append(r.levelEnd).append(',')
              .append(String.format(Locale.US, "%.2f", r.avgWatts)).append(',')
              .append(String.format(Locale.US, "%.2f", r.maxWatts)).append(',')
              .append(String.format(Locale.US, "%.3f", r.wh)).append(',')
              .append(cells).append(',')
              .append(String.format(Locale.US, "%.2f", ratio)).append('\n');
        }
        if (exported == 0) {
            Toast.makeText(this, "记录时长都太短，暂无可导出的完整记录", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME,
                    "chargemeter_" + System.currentTimeMillis() + ".csv");
            v.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("无法创建文件");
            OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(sb.toString().getBytes("UTF-8"));
            os.close();
            Toast.makeText(this, "已导出到 下载/ 目录", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败：" + e.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
        }
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

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
