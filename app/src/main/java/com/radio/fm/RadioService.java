package com.radio.fm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放服务：后台播放 + 通知栏控制 + 断流重连 + 可选边播边录 + ICY 曲目。
 *
 * 几个老机器上的关键点：
 *  - MediaPlayer 播网络流出错是常态，必须自动重试下一个备用地址
 *  - AudioManager 的来电/耳机拔出要正确处理，不然电话来了电台还在响
 *  - 用 WifiLock + WakeLock，否则熄屏后 WiFi 降频会断流
 */
public class RadioService extends Service implements MediaPlayer.OnErrorListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener {

    private static final String TAG = "RadioService";

    public static final String ACTION_PLAY = "com.radio.fm.PLAY";
    public static final String ACTION_STOP = "com.radio.fm.STOP";
    public static final String ACTION_TOGGLE = "com.radio.fm.TOGGLE";
    public static final String ACTION_NEXT = "com.radio.fm.NEXT";
    public static final String ACTION_PREV = "com.radio.fm.PREV";
    public static final String ACTION_RECORD = "com.radio.fm.RECORD";

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_URLS = "urls";    // 备用地址数组
    public static final String EXTRA_RECORD = "record";

    private static final String CHANNEL_ID = "radio_playback";
    private static final int NOTIF_ID = 1;

    private static final int MAX_RETRY_PER_URL = 2;

    // ---- 对外状态 ----
    public static final int STATE_IDLE = 0;
    public static final int STATE_PREPARING = 1;
    public static final int STATE_PLAYING = 2;
    public static final int STATE_ERROR = 3;

    private MediaPlayer player;
    private final IBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String currentName = "";
    private String[] currentUrls = new String[0];
    private int urlIndex = 0;
    private int retryCount = 0;
    private int state = STATE_IDLE;
    private String lastError = "";
    private String icyTitle = "";

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private AudioManager audioManager;
    private boolean pausedByFocusLoss = false;

    private StreamRecorder recorder;
    private Prefs prefs;
    private Thread icyThread;

    private final List<Listener> listeners = new ArrayList<Listener>();

    public interface Listener {
        void onStateChanged(int state, String stationName, String icyTitle);
        void onError(String message);
    }

    public class LocalBinder extends Binder {
        public RadioService getService() { return RadioService.this; }
    }

    // ================= 生命周期 =================

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        Tls12.install();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RadioFM:playback");
        wakeLock.setReferenceCounted(false);

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "RadioFM:wifi");
            wifiLock.setReferenceCounted(false);
        }

        createChannel();

        // 8.0+ 被 startForegroundService 拉起时，必须立刻进前台，否则 5 秒后 ANR
        startForeground(NOTIF_ID, buildNotification("准备中", "", false));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }
        String action = intent.getAction();

        if (ACTION_PLAY.equals(action)) {
            String url = intent.getStringExtra(EXTRA_URL);
            String name = intent.getStringExtra(EXTRA_NAME);
            String[] urls = intent.getStringArrayExtra(EXTRA_URLS);
            if (urls == null || urls.length == 0) {
                urls = url != null ? new String[]{url} : new String[0];
            }
            if (urls.length > 0) {
                play(urls, name == null ? "" : name);
            }
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
            stopSelf();
        } else if (ACTION_TOGGLE.equals(action)) {
            toggle();
        } else if (ACTION_NEXT.equals(action)) {
            nextUrl();
        } else if (ACTION_PREV.equals(action)) {
            prevUrl();
        } else if (ACTION_RECORD.equals(action)) {
            setRecording(intent.getBooleanExtra(EXTRA_RECORD, false));
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        stopPlayback();
        stopForegroundCompat();
        super.onDestroy();
    }

    // ================= 播放控制 =================

    public void play(String[] urls, String name) {
        releasePlayer();
        currentUrls = urls;
        urlIndex = 0;
        retryCount = 0;
        currentName = name;
        lastError = "";

        prefs.setString(Prefs.KEY_LAST_STATION, urls[0]);
        prefs.setString(Prefs.KEY_LAST_NAME, name);

        acquireLocks();
        startCurrent();
    }

    private void startCurrent() {
        if (currentUrls.length == 0) return;
        final String url = currentUrls[urlIndex];
        Log.i(TAG, "尝试播放 [" + urlIndex + "/" + (currentUrls.length - 1) + "] " + url);

        setState(STATE_PREPARING, null);
        releasePlayer();

        player = new MediaPlayer();
        player.setOnPreparedListener(this);
        player.setOnErrorListener(this);
        player.setOnInfoListener(this);
        player.setOnBufferingUpdateListener(this);

        try {
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(url);
            player.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "setDataSource 失败", e);
            onPlaybackFailed("地址无效: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        retryCount = 0;
        mp.start();
        setState(STATE_PLAYING, null);
        startIcyPolling();
        startRecordingIfEnabled();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "MediaPlayer 错误 what=" + what + " extra=" + extra);
        String hint;
        switch (extra) {
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED: hint = "格式不支持"; break;
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:   hint = "连接超时(-110)"; break;
            case -1004: hint = "服务器拒绝连接"; break;   // IO 错误
            case -1007: hint = "流地址已失效"; break;
            case -1101: hint = "网络太慢，缓冲不足"; break;
            default:    hint = "播放失败(" + extra + ")";
        }
        onPlaybackFailed(hint);
        return true;   // 已处理，不让系统再弹框
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            setState(STATE_PREPARING, null);
        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            setState(STATE_PLAYING, null);
        }
        return false;
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        // 直播流这个值通常不可靠，忽略
    }

    /** 一个地址失败 → 重试 → 换下一个备用地址 */
    private void onPlaybackFailed(String reason) {
        lastError = reason;
        retryCount++;

        if (retryCount <= MAX_RETRY_PER_URL) {
            Log.i(TAG, "重试同一地址 (" + retryCount + "/" + MAX_RETRY_PER_URL + ")");
            handler.postDelayed(new Runnable() {
                @Override public void run() { startCurrent(); }
            }, 1200);
            return;
        }

        retryCount = 0;
        if (urlIndex + 1 < currentUrls.length) {
            urlIndex++;
            Log.i(TAG, "切换备用地址 " + urlIndex);
            handler.postDelayed(new Runnable() {
                @Override public void run() { startCurrent(); }
            }, 800);
            return;
        }

        // 所有地址都失败
        urlIndex = 0;
        setState(STATE_ERROR, reason);
        notifyError(currentName + " 播放失败：" + reason);
        releaseLocks();
    }

    public void nextUrl() {
        if (currentUrls.length <= 1) return;
        urlIndex = (urlIndex + 1) % currentUrls.length;
        retryCount = 0;
        startCurrent();
    }

    public void prevUrl() {
        if (currentUrls.length <= 1) return;
        urlIndex = (urlIndex - 1 + currentUrls.length) % currentUrls.length;
        retryCount = 0;
        startCurrent();
    }

    public void pause() {
        if (player != null && state == STATE_PLAYING) {
            player.pause();
            setState(STATE_IDLE, null);
            releaseLocks();
        }
    }

    public void resume() {
        if (player != null && state == STATE_IDLE) {
            player.start();
            setState(STATE_PLAYING, null);
            acquireLocks();
        } else if (currentUrls.length > 0) {
            startCurrent();
        }
    }

    public void toggle() {
        if (state == STATE_PLAYING || state == STATE_PREPARING) {
            pause();
        } else if (player != null) {
            resume();
        }
    }

    public void stopPlayback() {
        stopIcyPolling();
        stopRecording();
        releasePlayer();
        setState(STATE_IDLE, null);
        releaseLocks();
        currentName = "";
        icyTitle = "";
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.reset(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    // ================= 状态与通知 =================

    private void setState(int s, String err) {
        state = s;
        updateNotification();
        for (Listener l : new ArrayList<Listener>(listeners)) {
            l.onStateChanged(s, currentName, icyTitle);
        }
    }

    public int getState() { return state; }
    public String getCurrentName() { return currentName; }
    public String getIcyTitle() { return icyTitle; }
    public String getLastError() { return lastError; }
    public boolean isPlaying() { return state == STATE_PLAYING; }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    private void notifyError(String msg) {
        for (Listener l : new ArrayList<Listener>(listeners)) l.onError(msg);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                        "电台播放", NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification(String title, String text, boolean playing) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }

        b.setSmallIcon(android.R.drawable.ic_media_play)
         .setContentTitle(title == null || title.length() == 0 ? "网络收音机" : title)
         .setContentText(text == null ? "" : text)
         .setContentIntent(contentPi)
         .setOngoing(playing)
         .setOnlyAlertOnce(true);

        // 播放/暂停
        Intent toggle = new Intent(this, RadioService.class).setAction(ACTION_TOGGLE);
        b.addAction(android.R.drawable.ic_media_pause, playing ? "暂停" : "播放",
                PendingIntent.getService(this, 10, toggle,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // 停止
        Intent stop = new Intent(this, RadioService.class).setAction(ACTION_STOP);
        b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止",
                PendingIntent.getService(this, 11, stop,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        return b.build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        String title = currentName.length() == 0 ? "网络收音机" : currentName;
        String text;
        boolean playing = (state == STATE_PLAYING);

        switch (state) {
            case STATE_PREPARING: text = "缓冲中…"; break;
            case STATE_PLAYING:
                text = icyTitle.length() > 0 ? icyTitle : "正在播放";
                break;
            case STATE_ERROR:     text = lastError.length() > 0 ? lastError : "播放失败"; break;
            default:              text = "已停止"; break;
        }
        try {
            nm.notify(NOTIF_ID, buildNotification(title, text, playing));
        } catch (Exception e) {
            Log.w(TAG, "更新通知失败", e);
        }
    }

    @SuppressWarnings("deprecation")
    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    // ================= 锁 =================

    private void acquireLocks() {
        try {
            // 加超时上限：万一服务被系统异常回收，锁最多 4 小时后自动释放，
            // 不至于把用户电池耗干
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(4 * 60 * 60 * 1000L);
            }
            if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
        } catch (Exception e) {
            Log.w(TAG, "获取锁失败", e);
        }
    }

    private void releaseLocks() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception e) {
            Log.w(TAG, "释放锁失败", e);
        }
    }

    // ================= ICY 曲目 =================

    private void startIcyPolling() {
        stopIcyPolling();
        if (!prefs.isShowIcy()) return;

        icyThread = new Thread(new Runnable() {
            @Override public void run() {
                while (state == STATE_PLAYING) {
                    final String url = currentUrls.length > urlIndex ? currentUrls[urlIndex] : null;
                    if (url == null) break;

                    final String title = IcyMetadata.fetch(url, 8000);
                    if (title != null && title.length() > 0 && !title.equals(icyTitle)) {
                        handler.post(new Runnable() {
                            @Override public void run() {
                                icyTitle = title;
                                updateNotification();
                                for (Listener l : new ArrayList<Listener>(listeners)) {
                                    l.onStateChanged(state, currentName, icyTitle);
                                }
                            }
                        });
                    }
                    // 30 秒查一次就够，别频繁开连接
                    try { Thread.sleep(30000); } catch (InterruptedException e) { break; }
                }
            }
        }, "icy-poller");
        icyThread.setDaemon(true);
        icyThread.start();
    }

    private void stopIcyPolling() {
        if (icyThread != null) {
            icyThread.interrupt();
            icyThread = null;
        }
    }

    // ================= 录音 =================

    /**
     * 录音功能未启用（用户不需要）。
     * StreamRecorder 代码保留，接回来时把下面的 return 去掉即可。
     */
    private void startRecordingIfEnabled() {
        return;   // 录音已停用
    }

    private void stopRecording() {
        if (recorder != null) {
            recorder.stop();
            recorder = null;
        }
    }

    public StreamRecorder getRecorder() { return recorder; }

    public void setRecording(boolean on) {
        prefs.setRecordEnabled(on);
        if (on) {
            startRecordingIfEnabled();
        } else {
            stopRecording();
        }
    }
}
