package com.radio.fm;

/**
 * 一个电台。urls 是同一电台的多个备用流地址 —— 按顺序尝试，
 * 前一个连不上就自动试下一个。这比只有一个地址抗造得多。
 */
public class Station {

    public final String name;
    public final String[] urls;
    public final String genre;      // 分类，用于分组显示
    public final String region;     // 地区
    public boolean favorite;
    public String lastError;        // 最近一次失败原因，UI 用来解释"为什么点了没声"

    public Station(String name, String genre, String region, String... urls) {
        this.name = name;
        this.genre = genre == null ? "其他" : genre;
        this.region = region == null ? "" : region;
        this.urls = urls;
    }

    public String primaryUrl() {
        return urls.length > 0 ? urls[0] : null;
    }

    /**
     * 是否 HLS 流（.m3u8）—— Android 4.4 的 MediaPlayer 播不了。
     *
     * **从地址推导，不靠单独字段记。**
     * 曾经用 region="安卓9" 兼职标记这件事，结果后来把 28 个省级卫视的
     * region 从省份改到「中央」时，标记没跟着走 —— 那些台在 4.4 上就
     * 混进了「全部频道」，点了不出声。内容分类变、技术标记漂，是设计问题；
     * 从地址推导则不可能不一致。
     */
    public boolean isHls() {
        for (String u : urls) {
            if (u != null && u.contains(".m3u8")) return true;
        }
        return false;
    }

    /** 列表里的副标题：地区 · 分类 · 音质 */
    public String subtitle() {
        StringBuilder sb = new StringBuilder();
        if (region.length() > 0) sb.append(region).append(" · ");
        sb.append(genre);
        String u = primaryUrl();
        if (u != null) {
            sb.append(" · ").append(u.startsWith("https") ? "HTTPS" : "HTTP");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return name;
    }
}
