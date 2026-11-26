package com.dsh.chargemeter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * 充电监测：充电时前台常驻，电量到 100% 提醒一次后自停。
 * 只在用户开启「充满提醒」时由 MainActivity 启动。
 */
public class ChargeWatchService extends Service {

    static final String CH_ONGOING = "watch";
    static final String CH_FULL = "full";
    static final int ID_ONGOING = 1001;
    static final int ID_FULL = 1002;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean notified = false;
    private long chargerStart = 0;
    private double chargerMax = 0;
    private boolean chargerDone = false;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateOngoing();
            check();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        startForeground(ID_ONGOING, ongoingNotification());
        handler.post(ticker);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel a = new NotificationChannel(CH_ONGOING, "充电监测",
                NotificationManager.IMPORTANCE_MIN);
        a.setShowBadge(false);
        nm.createNotificationChannel(a);

        NotificationChannel b = new NotificationChannel(CH_FULL, "充满提醒",
                NotificationManager.IMPORTANCE_HIGH);
        b.enableVibration(true);
        b.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build());
        nm.createNotificationChannel(b);
    }

    private PendingIntent openApp() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification ongoingNotification() {
        return new Notification.Builder(this, CH_ONGOING)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("充电监测中")
                .setContentText("充满后会提醒你")
                .setOngoing(true)
                .setContentIntent(openApp())
                .build();
    }

    private void updateOngoing() {
        if (notified) return;
        try {
            Intent it = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (it == null) return;
            int level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int voltage = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000);
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            long raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            android.content.SharedPreferences cfg = getSharedPreferences("cfg", MODE_PRIVATE);
            int cells = cfg.contains("cells") ? cfg.getInt("cells", 1) : 1;
            float ratio = cfg.getFloat("ratio", 1.0f);
            double watts = 0;
            if (raw != Long.MIN_VALUE && raw != 0) {
                double amps = raw / 1_000_000.0;
                watts = Math.abs(amps) * (voltage / 1000.0) * cells * ratio;
                if (watts > 400) watts /= 1000.0;
            }
            Notification n = new Notification.Builder(this, CH_ONGOING)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(String.format("%.1f W · %d%%", watts, level))
                    .setContentText("充电监测中 · 充满后提醒你")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(openApp())
                    .build();
            getSystemService(NotificationManager.class).notify(ID_ONGOING, n);
            trackCharger(it, watts);
        } catch (Exception ignored) {
        }
    }

    /** 后台也能给充电器建档：插上 45 秒后按峰值功率匹配或新建 */
    private void trackCharger(Intent it, double watts) {
        int plugged = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        long now = System.currentTimeMillis();
        if (plugged == 0) {
            chargerStart = 0;
            chargerMax = 0;
            chargerDone = false;
            return;
        }
        if (chargerStart == 0) {
            chargerStart = now;
            chargerMax = watts;
            chargerDone = false;
            return;
        }
        chargerMax = Math.max(chargerMax, watts);
        if (!chargerDone && now - chargerStart > 45000 && chargerMax > 3) {
            chargerDone = true;
            try {
                ChargerProfiles profiles = new ChargerProfiles(this);
                if (profiles.match(chargerMax) == null) {
                    profiles.add("充电器 " + (profiles.count() + 1), chargerMax);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void check() {
        Intent it = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (it == null) return;
        int level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);

        if (plugged == 0) {
            stopSelf();
            return;
        }
        boolean full = level >= 100 || status == BatteryManager.BATTERY_STATUS_FULL;
        long lastNotify = getSharedPreferences("cfg", MODE_PRIVATE)
                .getLong("last_full_notify", 0L);
        boolean recentlyNotified = System.currentTimeMillis() - lastNotify < 12L * 3600 * 1000;
        if (full && !notified && !recentlyNotified) {
            getSharedPreferences("cfg", MODE_PRIVATE).edit()
                    .putLong("last_full_notify", System.currentTimeMillis()).apply();
            notified = true;
            NotificationManager nm = getSystemService(NotificationManager.class);
            Notification n = new Notification.Builder(this, CH_FULL)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("电池已充满")
                    .setContentText("可以拔掉充电器了")
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setContentIntent(openApp())
                    .build();
            try {
                nm.notify(ID_FULL, n);
            } catch (Exception ignored) {
            }
            stopForeground(true);
            stopSelf();
        }
    }
}
