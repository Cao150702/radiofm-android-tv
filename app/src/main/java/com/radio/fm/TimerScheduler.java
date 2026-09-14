package com.radio.fm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

/**
 * 定时关闭 + 定时唤醒的闹钟调度。
 *
 * 这里用 AlarmManager 而不是 Handler/Service 计时：
 * 老机器上应用随时可能被系统杀掉，只有 AlarmManager 能在被杀之后照样唤醒。
 */
public final class TimerScheduler {

    private static final String TAG = "TimerScheduler";

    public static final String ACTION_SLEEP = "com.radio.fm.ACTION_SLEEP";
    public static final String ACTION_ALARM = "com.radio.fm.ACTION_ALARM";

    private static final int REQ_SLEEP = 1001;
    private static final int REQ_ALARM = 1002;

    private TimerScheduler() {}

    private static PendingIntent pi(Context ctx, String action, int req) {
        Intent i = new Intent(ctx, TimerReceiver.class);
        i.setAction(action);
        return PendingIntent.getBroadcast(ctx, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ---------------- 定时关闭 ----------------

    public static void scheduleSleep(Context ctx, int minutes) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        cancelSleep(ctx);
        if (minutes <= 0) return;

        long at = System.currentTimeMillis() + minutes * 60_000L;
        PendingIntent p = pi(ctx, ACTION_SLEEP, REQ_SLEEP);
        setCompat(am, at, p);
        Log.i(TAG, "定时关闭已设置: " + minutes + " 分钟后");
    }

    public static void cancelSleep(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi(ctx, ACTION_SLEEP, REQ_SLEEP));
    }

    // ---------------- 定时唤醒 ----------------

    public static void scheduleAlarm(Context ctx, int hour, int minute, boolean enabled) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        cancelAlarm(ctx);
        if (!enabled) return;

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_YEAR, 1);      // 今天已过点，顺延到明天
        }

        PendingIntent p = pi(ctx, ACTION_ALARM, REQ_ALARM);
        am.setRepeating(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), AlarmManager.INTERVAL_DAY, p);
        Log.i(TAG, "定时唤醒已设置: 每天 " + hour + ":" + String.format("%02d", minute));
    }

    public static void cancelAlarm(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi(ctx, ACTION_ALARM, REQ_ALARM));
    }

    /**
     * setExactAndAllowWhileIdle 是 API 23+ 才有的，老机器用 setExact。
     * 不用 setWindow 之类的新 API，保证 4.4 能跑。
     */
    @SuppressWarnings("deprecation")
    private static void setCompat(AlarmManager am, long at, PendingIntent p) {
        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, at, p);
        }
    }
}
