package com.radio.fm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从直播流的 ICY 元数据里读"正在播放"的曲目名。
 *
 * 原理：连接时发 Icy-MetaData:1，服务器会回 icy-metaint:N，
 * 表示每 N 字节音频数据后面跟一个元数据块，块首字节是长度/16。
 * 元数据里是 StreamTitle='...'; 所以要边下载边按 N 字节切片找。
 *
 * 注意这会把音频数据拉下来（不播放），所以拿到标题后要立刻断开重连播放，
 * 否则等于在后台偷偷下载整个流。
 */
public final class IcyMetadata {

    private static final int MAX_META_BYTES = 4080;   // 元数据块上限 255*16
    private static final int MAX_SCAN_BYTES = 96 * 1024;  // 最多扫 96KB 就放弃

    private IcyMetadata() {}

    /** 结果：曲目名，或 null（该流不支持 / 扫描超时 / 出错） */
    public static String fetch(String url, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Icy-MetaData", "1");
            conn.setRequestProperty("User-Agent", "WinampMPEG/5.09");
            conn.setInstanceFollowRedirects(true);

            if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                Tls12.applyTo((javax.net.ssl.HttpsURLConnection) conn);
            }

            int metaInt = conn.getHeaderFieldInt("icy-metaint", 0);
            if (metaInt <= 0) return null;   // 该流不带元数据

            InputStream in = conn.getInputStream();
            long remaining = metaInt;
            long total = 0;
            byte[] buf = new byte[4096];

            while (total < MAX_SCAN_BYTES) {
                // 跳过 metaInt 字节音频
                while (remaining > 0) {
                    int toRead = (int) Math.min(buf.length, remaining);
                    int n = in.read(buf, 0, toRead);
                    if (n < 0) return null;
                    remaining -= n;
                    total += n;
                    if (total >= MAX_SCAN_BYTES) return null;
                }
                // 读元数据块长度字节
                int lenByte = in.read();
                if (lenByte < 0) return null;
                total++;
                int metaLen = lenByte * 16;
                if (metaLen == 0) {
                    remaining = metaInt;      // 本次无标题，继续下一段
                    continue;
                }
                if (metaLen > MAX_META_BYTES) return null;
                byte[] meta = new byte[metaLen];
                int got = 0;
                while (got < metaLen) {
                    int n = in.read(meta, got, metaLen - got);
                    if (n < 0) break;
                    got += n;
                    total += n;
                }
                String title = parseTitle(new String(meta, 0, got, "UTF-8"));
                if (title != null && title.length() > 0) return title;
                remaining = metaInt;
            }
            return null;

        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();   // 必须断开，别继续吃流量
        }
    }

    /** 从 StreamTitle='xxx'; 里抠出 xxx */
    private static String parseTitle(String meta) {
        int i = meta.indexOf("StreamTitle='");
        if (i < 0) return null;
        int start = i + "StreamTitle='".length();
        int end = meta.indexOf("';", start);
        if (end < 0) end = meta.indexOf('\'', start);
        if (end <= start) return null;
        String t = meta.substring(start, end).trim();
        return t.length() == 0 ? null : t;
    }

    /** 解析 M3U/PLS 播放列表，返回 名称 -> 地址 */
    public static Map<String, String> parsePlaylist(String content, String baseUrl) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        String[] lines = content.split("\r?\n");
        String pendingName = null;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.length() == 0 || line.startsWith("#")) {
                // #EXTINF:-1,电台名
                if (line.startsWith("#EXTINF:")) {
                    int comma = line.indexOf(',');
                    if (comma >= 0 && comma + 1 < line.length()) {
                        pendingName = line.substring(comma + 1).trim();
                    }
                }
                continue;
            }
            // PLS 格式: File1=http://...
            if (line.regionMatches(true, 0, "File", 0, 4) && line.indexOf('=') > 0) {
                int eq = line.indexOf('=');
                String key = line.substring(0, eq);
                String val = line.substring(eq + 1).trim();
                String num = key.replaceAll("[^0-9]", "");
                if (val.length() > 0) {
                    out.put("频道 " + (num.length() > 0 ? num : String.valueOf(out.size() + 1)), val);
                }
                continue;
            }
            if (line.regionMatches(true, 0, "Title", 0, 5)) continue;

            // 裸地址行
            if (line.startsWith("http://") || line.startsWith("https://") || line.startsWith("mms://")) {
                String name = pendingName != null ? pendingName : ("电台 " + (out.size() + 1));
                out.put(name, line);
                pendingName = null;
            }
        }
        return out;
    }

    public static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
            if (sb.length() > 512 * 1024) break;   // 播放列表不该这么大
        }
        return sb.toString();
    }
}
