package com.radio.fm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 接收定时关闭 / 定时唤醒 / 开机广播。
 *
 * 唤醒场景下不能只 startService —— Android 8+ 在后台启动服务会抛
 * IllegalStateException。所以 8.0+ 用 startForegroundService，
 * 由 RadioService.onCreate 负责及时调用 startForeground。
 */
public class TimerReceiver extends BroadcastReceiver {

    private static final String TAG = "TimerReceiver";
    private static final int REQ_BOOT = 1003;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        Log.i(TAG, "收到广播: " + action);

        Prefs prefs = new Prefs(context);

        if (TimerScheduler.ACTION_SLEEP.equals(action)) {
            prefs.setSleepMinutes(0);              // 一次性，触发后清零
            context.startService(new Intent(context, RadioService.class)
                    .setAction(RadioService.ACTION_STOP));
            return;
        }

        if (TimerScheduler.ACTION_ALARM.equals(action)) {
            String url = prefs.getAlarmUrl();
            String name = prefs.getAlarmStationName();
            if (url == null || url.length() == 0) {
                Log.w(TAG, "定时唤醒已触发，但没有配置电台");
                return;
            }
            Intent svc = new Intent(context, RadioService.class)
                    .setAction(RadioService.ACTION_PLAY)
                    .putExtra(RadioService.EXTRA_URL, url)
                    .putExtra(RadioService.EXTRA_NAME, name);
            startServiceCompat(context, svc);
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // 重启后 AlarmManager 里的闹钟全部丢失，必须重新注册
            if (prefs.isAlarmEnabled()) {
                TimerScheduler.scheduleAlarm(context, prefs.getAlarmHour(),
                        prefs.getAlarmMinute(), true);
            }
            if (prefs.isBootResume() && prefs.getAlarmUrl().length() > 0) {
                Log.i(TAG, "开机恢复上次电台");
                startServiceCompat(context, new Intent(context, RadioService.class)
                        .setAction(RadioService.ACTION_PLAY)
                        .putExtra(RadioService.EXTRA_URL, prefs.getAlarmUrl())
                        .putExtra(RadioService.EXTRA_NAME, prefs.getAlarmStationName()));
            }
        }
    }

    /** Android 8.0+ 后台启动普通服务会崩，必须用 startForegroundService */
    private static void startServiceCompat(Context ctx, Intent svc) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            ctx.startForegroundService(svc);
        } else {
            ctx.startService(svc);
        }
    }
}
