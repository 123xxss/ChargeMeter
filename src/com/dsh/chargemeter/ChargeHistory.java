package com.dsh.chargemeter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 充电历史：每次插上充电器后的摘要，存在本地 SQLite，不联网 */
public class ChargeHistory extends SQLiteOpenHelper {

    private static final String DB = "history.db";
    private static final int VER = 1;

    public ChargeHistory(Context c) {
        super(c, DB, null, VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "start_ts INTEGER, end_ts INTEGER," +
                "level_start INTEGER, level_end INTEGER," +
                "avg_watts REAL, max_watts REAL, wh REAL, cells INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
    }

    public long start(int level, double watts, int cells) {
        ContentValues v = new ContentValues();
        long now = System.currentTimeMillis();
        v.put("start_ts", now);
        v.put("end_ts", now);
        v.put("level_start", level);
        v.put("level_end", level);
        v.put("avg_watts", watts);
        v.put("max_watts", watts);
        v.put("wh", 0);
        v.put("cells", cells);
        return getWritableDatabase().insert("sessions", null, v);
    }

    public void update(long id, int level, double watts, double wh) {
        ContentValues v = new ContentValues();
        v.put("end_ts", System.currentTimeMillis());
        v.put("level_end", level);
        v.put("avg_watts", watts);
        v.put("wh", wh);
        getWritableDatabase().update("sessions", v, "_id=?", new String[]{String.valueOf(id)});
    }

    public void updateMax(long id, double maxWatts) {
        ContentValues v = new ContentValues();
        v.put("max_watts", maxWatts);
        getWritableDatabase().update("sessions", v, "_id=?", new String[]{String.valueOf(id)});
    }

    /** 找最近一条还在进行中的会话（end_ts 距现在 3 分钟以内），用于 App 重启后续接 */
    public long findResumable(long withinMs) {
        Cursor c = null;
        try {
            c = getReadableDatabase().query("sessions", new String[]{"_id", "end_ts"},
                    null, null, null, null, "end_ts DESC", "1");
            if (c != null && c.moveToNext()) {
                long id = c.getLong(0);
                long endTs = c.getLong(1);
                if (System.currentTimeMillis() - endTs < withinMs) return id;
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return -1;
    }

    public static class Row {
        public long startTs, endTs;
        public int levelStart, levelEnd, cells;
        public double avgWatts, maxWatts, wh;
    }

    public List<Row> recent(int limit) {
        List<Row> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().query("sessions", null, null, null, null, null,
                    "start_ts DESC", String.valueOf(limit));
            while (c != null && c.moveToNext()) {
                Row r = new Row();
                r.startTs = c.getLong(c.getColumnIndexOrThrow("start_ts"));
                r.endTs = c.getLong(c.getColumnIndexOrThrow("end_ts"));
                r.levelStart = c.getInt(c.getColumnIndexOrThrow("level_start"));
                r.levelEnd = c.getInt(c.getColumnIndexOrThrow("level_end"));
                r.avgWatts = c.getDouble(c.getColumnIndexOrThrow("avg_watts"));
                r.maxWatts = c.getDouble(c.getColumnIndexOrThrow("max_watts"));
                r.wh = c.getDouble(c.getColumnIndexOrThrow("wh"));
                r.cells = c.getInt(c.getColumnIndexOrThrow("cells"));
                out.add(r);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    public static String format(Row r) {
        SimpleDateFormat f = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        long mins = Math.max(1, (r.endTs - r.startTs) / 60000);
        return f.format(new Date(r.startTs))
                + String.format("  %d%%→%d%%  %d 分钟", r.levelStart, r.levelEnd, mins)
                + String.format("\n平均 %.1f W · 峰值 %.1f W · 充入 %.1f Wh", r.avgWatts, r.maxWatts, r.wh);
    }
}
