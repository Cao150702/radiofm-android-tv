package com.radio.fm;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 边播边录 / 定时录音。
 *
 * 为什么不用 MediaRecorder：它录的是麦克风或内部音源，抓不了网络流。
 * 所以要自己开一条 HTTP 连接把流字节原样写文件 —— 存下来的是原始
 * MP3/AAC 流，可以直接用任何播放器播。
 *
 * 注意这会占用第二条连接（一条播放一条录制），服务器看到两个客户端。
 *
 * ⚠️ 目前未启用：用户不需要录音功能，已从设置界面移除。
 * 代码保留在此，需要时接回 RadioService.startRecordingIfEnabled() 即可。
 */
public class StreamRecorder {

    private static final String TAG = "StreamRecorder";

    private volatile boolean running;
    private Thread thread;
    private File outputFile;
    private long bytesWritten;
    private String stationName;
    private Listener listener;

    public interface Listener {
        void onRecordProgress(long bytes, long seconds);
        void onRecordFinished(File file, long bytes);
        void onRecordError(String message);
    }

    public void setListener(Listener l) { this.listener = l; }

    public boolean isRecording() { return running; }
    public File getOutputFile() { return outputFile; }
    public long getBytesWritten() { return bytesWritten; }
    public String getStationName() { return stationName; }

    public File getRecordDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), "RadioFM/recordings");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * @param url      流地址
     * @param name     电台名，用于文件名
     * @param maxMillis 最长录制时长，0 = 不限（由调用方 stop()）
     */
    public void start(final String url, final String name, final long maxMillis) {
        if (running) return;
        running = true;
        stationName = name;

        thread = new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection conn = null;
                OutputStream out = null;
                try {
                    String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                    String safe = name == null ? "radio" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
                    outputFile = new File(getRecordDir(), safe + "_" + stamp + ".mp3");

                    conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
                    conn.setRequestProperty("User-Agent", "WinampMPEG/5.09");
                    conn.setInstanceFollowRedirects(true);
                    if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                        Tls12.applyTo((javax.net.ssl.HttpsURLConnection) conn);
                    }

                    InputStream in = conn.getInputStream();
                    out = new FileOutputStream(outputFile);

                    byte[] buf = new byte[8192];
                    long startAt = System.currentTimeMillis();
                    long lastReport = 0;
                    int n;
                    while (running && (n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        bytesWritten += n;

                        long now = System.currentTimeMillis();
                        if (now - lastReport > 1000) {
                            lastReport = now;
                            final long b = bytesWritten;
                            final long sec = (now - startAt) / 1000;
                            if (listener != null) {
                                listener.onRecordProgress(b, sec);
                            }
                        }
                        if (maxMillis > 0 && now - startAt >= maxMillis) {
                            Log.i(TAG, "达到最长录制时长，停止");
                            break;
                        }
                    }
                    out.flush();

                    if (listener != null) listener.onRecordFinished(outputFile, bytesWritten);

                } catch (Exception e) {
                    Log.e(TAG, "录制失败", e);
                    if (listener != null) listener.onRecordError(e.toString());
                } finally {
                    running = false;
                    try { if (out != null) out.close(); } catch (Exception ignored) {}
                    if (conn != null) conn.disconnect();
                }
            }
        }, "stream-recorder");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            // 读阻塞最多 20 秒（readTimeout），给它一点时间收尾
            try { thread.join(3000); } catch (InterruptedException ignored) {}
            thread = null;
        }
    }
}
