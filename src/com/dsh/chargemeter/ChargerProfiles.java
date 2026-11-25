package com.dsh.chargemeter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 充电器档案（指纹识别）。
 * 系统不开放充电器厂商/Vendor ID，所以用「插上后的功率特征」建档：
 * 同一台充电器每次的最大功率高度稳定，据此匹配并回显用户起的名字。
 */
public class ChargerProfiles extends SQLiteOpenHelper {

    private static final String DB = "chargers.db";
    private static final int VER = 1;

    public ChargerProfiles(Context c) {
        super(c, DB, null, VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE chargers (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT, max_w REAL, seen INTEGER, last_ts INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
    }

    public static class Row {
        public long id;
        public String name;
        public double maxW;
        public int seen;
        public long lastTs;
    }

    public List<Row> all() {
        List<Row> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().query("chargers", null, null, null, null, null,
                    "last_ts DESC");
            while (c != null && c.moveToNext()) {
                Row r = new Row();
                r.id = c.getLong(c.getColumnIndexOrThrow("_id"));
                r.name = c.getString(c.getColumnIndexOrThrow("name"));
                r.maxW = c.getDouble(c.getColumnIndexOrThrow("max_w"));
                r.seen = c.getInt(c.getColumnIndexOrThrow("seen"));
                r.lastTs = c.getLong(c.getColumnIndexOrThrow("last_ts"));
                out.add(r);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /** 按最大功率匹配（±18%），命中则累加次数 */
    public Row match(double maxW) {
        if (maxW <= 0) return null;
        for (Row r : all()) {
            if (r.maxW <= 0) continue;
            double diff = Math.abs(r.maxW - maxW) / r.maxW;
            if (diff <= 0.18) {
                ContentValues v = new ContentValues();
                v.put("seen", r.seen + 1);
                v.put("last_ts", System.currentTimeMillis());
                getWritableDatabase().update("chargers", v, "_id=?",
                        new String[]{String.valueOf(r.id)});
                r.seen++;
                return r;
            }
        }
        return null;
    }

    public Row add(String name, double maxW) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("max_w", maxW);
        v.put("seen", 1);
        v.put("last_ts", System.currentTimeMillis());
        long id = getWritableDatabase().insert("chargers", null, v);
        Row r = new Row();
        r.id = id;
        r.name = name;
        r.maxW = maxW;
        r.seen = 1;
        r.lastTs = System.currentTimeMillis();
        return r;
    }

    public void rename(long id, String name) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        getWritableDatabase().update("chargers", v, "_id=?", new String[]{String.valueOf(id)});
    }

    public void remove(long id) {
        getWritableDatabase().delete("chargers", "_id=?", new String[]{String.valueOf(id)});
    }

    public int count() {
        return all().size();
    }
}
