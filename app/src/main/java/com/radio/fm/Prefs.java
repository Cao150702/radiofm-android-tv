package com.radio.fm;

import android.content.Context;
import android.content.SharedPreferences;

/** 设置项的读写集中在这里，避免各处硬编码 key */
public final class Prefs {

    private static final String FILE = "radio_prefs";

    public static final String KEY_SLEEP_MINUTES   = "sleep_minutes";      // 定时关闭：0=关
    public static final String KEY_ALARM_ENABLED   = "alarm_enabled";
    public static final String KEY_ALARM_HOUR      = "alarm_hour";
    public static final String KEY_ALARM_MINUTE    = "alarm_minute";
    public static final String KEY_ALARM_STATION   = "alarm_station";      // 电台名
    public static final String KEY_ALARM_URL       = "alarm_url";
    public static final String KEY_RECORD_ENABLED  = "record_enabled";
    public static final String KEY_LAST_STATION    = "last_station_url";
    public static final String KEY_LAST_NAME       = "last_station_name";
    public static final String KEY_SHOW_ICY        = "show_icy";
    public static final String KEY_BOOT_RESUME     = "boot_resume";

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public int getInt(String k, int def) { return sp.getInt(k, def); }
    public void setInt(String k, int v) { sp.edit().putInt(k, v).apply(); }

    public boolean getBool(String k, boolean def) { return sp.getBoolean(k, def); }
    public void setBool(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }

    public String getString(String k, String def) { return sp.getString(k, def); }
    public void setString(String k, String v) { sp.edit().putString(k, v).apply(); }

    // ---- 定时关闭 ----
    public int getSleepMinutes() { return getInt(KEY_SLEEP_MINUTES, 0); }
    public void setSleepMinutes(int m) { setInt(KEY_SLEEP_MINUTES, m); }

    // ---- 定时唤醒 ----
    public boolean isAlarmEnabled() { return getBool(KEY_ALARM_ENABLED, false); }
    public int getAlarmHour() { return getInt(KEY_ALARM_HOUR, 7); }
    public int getAlarmMinute() { return getInt(KEY_ALARM_MINUTE, 0); }
    public String getAlarmStationName() { return getString(KEY_ALARM_STATION, ""); }
    public String getAlarmUrl() { return getString(KEY_ALARM_URL, ""); }

    public void setAlarm(boolean on, int hour, int minute, String name, String url) {
        sp.edit()
          .putBoolean(KEY_ALARM_ENABLED, on)
          .putInt(KEY_ALARM_HOUR, hour)
          .putInt(KEY_ALARM_MINUTE, minute)
          .putString(KEY_ALARM_STATION, name == null ? "" : name)
          .putString(KEY_ALARM_URL, url == null ? "" : url)
          .apply();
    }

    // ---- 录音 ----
    public boolean isRecordEnabled() { return getBool(KEY_RECORD_ENABLED, false); }
    public void setRecordEnabled(boolean b) { setBool(KEY_RECORD_ENABLED, b); }

    // ---- 曲目信息 ----
    public boolean isShowIcy() { return getBool(KEY_SHOW_ICY, true); }
    public void setShowIcy(boolean b) { setBool(KEY_SHOW_ICY, b); }

    // ---- 开机恢复 ----
    public boolean isBootResume() { return getBool(KEY_BOOT_RESUME, false); }
    public void setBootResume(boolean b) { setBool(KEY_BOOT_RESUME, b); }
}
