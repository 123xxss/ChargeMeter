package com.dsh.chargemeter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 仪表常亮模式：全屏黑底大数字，当充电器显示屏用 */
public class MeterActivity extends Activity {

    private TextView powerText;
    private TextView levelText;
    private TextView subText;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int cellCount = 1;
    private double ratio = 1.0;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        SharedPreferences cfg = getSharedPreferences("cfg", Context.MODE_PRIVATE);
        cellCount = cfg.contains("cells") ? cfg.getInt("cells", 1) : 1;
        ratio = cfg.getFloat("ratio", 1.0f);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        root.setOnClickListener(v -> finish());

        levelText = new TextView(this);
        levelText.setTextColor(0xFF00FFA3);
        levelText.setTextSize(16);
        levelText.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        levelText.setGravity(Gravity.CENTER);
        root.addView(levelText);

        powerText = new TextView(this);
        powerText.setTextColor(0xFF00E5FF);
        powerText.setTextSize(120);
        powerText.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        powerText.setIncludeFontPadding(false);
        powerText.setShadowLayer(dp(24), 0, 0, 0x9900E5FF);
        powerText.setGravity(Gravity.CENTER);
        root.addView(powerText);

        subText = new TextView(this);
        subText.setTextColor(0xFF4E6B80);
        subText.setTextSize(13);
        subText.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        subText.setGravity(Gravity.CENTER);
        root.addView(subText);

        setContentView(root);
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

    private void update() {
        Intent it = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = -1;
        int voltageMv = 0;
        if (it != null) {
            level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            voltageMv = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        }
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        long raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        double amps = raw == Long.MIN_VALUE ? 0 : raw / 1_000_000.0;
        double watts = Math.abs(amps) * (voltageMv / 1000.0) * cellCount * ratio;
        if (watts > 400) watts /= 1000.0;

        powerText.setText(String.format("%.2f", watts));
        powerText.setTextColor(amps > 0.02 ? 0xFF00FFA3 : 0xFF00E5FF);
        levelText.setText((level >= 0 ? level + "%" : "--") + "  ·  "
                + (amps > 0.02 ? "充入" : "放电"));
        subText.setText("点击退出 · 屏幕常亮 · " + cellCount + " 电芯");
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
