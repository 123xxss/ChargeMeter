package com.dsh.chargemeter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.widget.RemoteViews;

public class PowerWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        RemoteViews views = build(context);
        for (int id : ids) {
            mgr.updateAppWidget(id, views);
        }
    }

    static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_power);
        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
        long raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        Intent it = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = -1, voltage = 4000;
        if (it != null) {
            level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            voltage = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000);
        }
        double watts = 0;
        if (raw != Long.MIN_VALUE && raw != 0) {
            double amps = raw / 1_000_000.0;
            watts = Math.abs(amps) * (voltage / 1000.0);
            if (watts > 400) watts /= 1000.0;
        }
        v.setTextViewText(R.id.w_power, String.format("%.1f W", watts));
        v.setTextViewText(R.id.w_level, (level >= 0 ? level + "%" : "--") + " · 充电功率");

        Intent open = new Intent(ctx, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.w_power, pi);
        return v;
    }

    /** 供 App 内部主动刷新（例如每秒采样后） */
    static void refreshAll(Context ctx) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, PowerWidget.class));
            if (ids.length > 0) {
                RemoteViews views = build(ctx);
                for (int id : ids) mgr.updateAppWidget(id, views);
            }
        } catch (Exception ignored) {
        }
    }
}
