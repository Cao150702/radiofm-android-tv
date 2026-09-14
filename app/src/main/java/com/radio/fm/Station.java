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
