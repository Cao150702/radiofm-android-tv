package com.radio.fm;

import android.content.Context;
import android.util.Log;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/** 从网址拉取 M3U/PLS 播放列表并交给主界面导入 */
public class PlaylistLoader {

    private static final String TAG = "PlaylistLoader";
    private final Context ctx;
    private final MainActivity activity;

    public PlaylistLoader(MainActivity a) {
        this.activity = a;
        this.ctx = a;
    }

    public void load(final String url) {
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setInstanceFollowRedirects(true);
                    if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                        Tls12.applyTo((javax.net.ssl.HttpsURLConnection) conn);
                    }
                    InputStream in = conn.getInputStream();
                    String text = IcyMetadata.readAll(in);
                    final Map<String, String> entries = IcyMetadata.parsePlaylist(text, url);
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() { activity.applyPlaylist(entries); }
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "加载播放列表失败", e);
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            android.widget.Toast.makeText(ctx,
                                    "加载失败：" + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                        }
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }, "playlist-loader").start();
    }
}
