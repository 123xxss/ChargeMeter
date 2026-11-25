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

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateOngoing();
            check();
            handler.postDelayed(this, 5000);
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
            double watts = 0;
            if (raw != Long.MIN_VALUE && raw != 0) {
                double amps = raw / 1_000_000.0;
                watts = Math.abs(amps) * (voltage / 1000.0);
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
        } catch (Exception ignored) {
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
        if (full && !notified) {
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
