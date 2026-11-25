package com.dsh.chargemeter;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class PowerTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    @Override
    public void onClick() {
        super.onClick();
        refresh();
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(i);
        }
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        long raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        float watts = 0f;
        boolean ok = raw != Long.MIN_VALUE && raw != 0;
        if (ok) {
            double amps = raw / 1_000_000.0;
            if (Math.abs(amps) > 30) amps = amps / 1000.0;
            int voltage = 4000;
            try {
                android.content.Intent it = registerReceiver(null,
                        new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (it != null) voltage = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000);
            } catch (Exception ignored) {
            }
            watts = (float) (Math.abs(amps) * (voltage / 1000.0));
            if (watts > 400) watts = watts / 1000f;
        }
        if (ok) {
            t.setLabel(watts >= 10 ? String.format("%.0f W", watts) : String.format("%.1f W", watts));
            t.setState(Tile.STATE_ACTIVE);
        } else {
            t.setLabel("充电功率");
            t.setState(Tile.STATE_INACTIVE);
        }
        t.updateTile();
    }
}
