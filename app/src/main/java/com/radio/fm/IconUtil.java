package com.radio.fm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

/**
 * 图标处理工具。
 *
 * 目前只有一件事：**把白底变透明**。
 *
 * 为什么在运行时做而不是事先处理成 PNG：
 *   · 事先处理要往仓库里塞一张位图（几十 KB），而这个项目一直是
 *     "APK 越小越好"（零第三方依赖、纯矢量图标）。
 *   · 运行时处理只要几毫秒，换图也不用重新导出各种密度。
 *
 * 为什么用**洪水填充**而不是"把白色像素都变透明"：
 *   后者会把图标内部本来就是白色的部分（频谱窗、高光）一起挖空，出现窟窿。
 *   从四边往里填，只吃掉与边缘连通的白色，也就是真正的"背景"。
 *
 * 用扫描线推进而非逐像素递归：图标边长几百像素，递归会爆栈。
 * 扫描线按行推进，栈里存的是"某行上的一个种子"，深度可控。
 */
public final class IconUtil {

    private static final String TAG = "IconUtil";

    private IconUtil() {}

    /** 判定为"白"：三通道都 >= 235 且彼此相差 <= 12（灰白，不是偏色） */
    private static final int WHITE_MIN = 235;
    private static final int CHANNEL_TOLERANCE = 12;

    /**
     * 把位图四边连通的白色背景变透明，返回新位图。
     * 失败时返回 null，调用方沿用原图标即可 —— 不是致命错误。
     */
    public static Bitmap makeBackgroundTransparent(Context ctx, int resId) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;      // 按原始像素处理，缩放会造出半白边
            Bitmap src = BitmapFactory.decodeResource(ctx.getResources(), resId, o);
            if (src == null) return null;

            final int w = src.getWidth(), h = src.getHeight();
            if (w <= 0 || h <= 0) return null;

            final int[] px = new int[w * h];
            src.getPixels(px, 0, w, 0, 0, w, h);
            final boolean[] queued = new boolean[w * h];   // 已入过队/已处理

            final int[] stack = new int[w * h];
            int sp = 0;

            // ---- 种子：四条边上所有白色像素 ----
            for (int x = 0; x < w; x++) {
                sp = seed(px, queued, stack, sp, x);                 // 顶行
                sp = seed(px, queued, stack, sp, (h - 1) * w + x);   // 底行
            }
            for (int y = 0; y < h; y++) {
                sp = seed(px, queued, stack, sp, y * w);             // 左列
                sp = seed(px, queued, stack, sp, y * w + (w - 1));   // 右列
            }

            // ---- 扫描线填充 ----
            while (sp > 0) {
                final int idx = stack[--sp];
                final int y = idx / w;
                final int rowStart = y * w;

                // 向左右扩到本行连续白色段的边界
                int left = idx;
                while (left > rowStart && !queued[left - 1] && isWhite(px[left - 1])) {
                    left--;
                    queued[left] = true;
                }
                int right = idx;
                final int rowEnd = rowStart + w - 1;
                while (right < rowEnd && !queued[right + 1] && isWhite(px[right + 1])) {
                    right++;
                    queued[right] = true;
                }

                // 整段变透明，并把上下两行相邻的白色当作新种子
                for (int i = left; i <= right; i++) {
                    px[i] = px[i] & 0x00FFFFFF;      // alpha → 0
                    if (y > 0)     sp = seed(px, queued, stack, sp, i - w);
                    if (y < h - 1) sp = seed(px, queued, stack, sp, i + w);
                }
            }

            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            out.setPixels(px, 0, w, 0, 0, w, h);
            return out;
        } catch (Exception e) {
            Log.w(TAG, "图标去白底失败，沿用原图标", e);
            return null;
        }
    }

    /** 若该像素是白色且没入过队，就压栈 */
    private static int seed(int[] px, boolean[] queued, int[] stack, int sp, int idx) {
        if (idx >= 0 && idx < px.length && !queued[idx] && isWhite(px[idx])) {
            queued[idx] = true;
            if (sp < stack.length) stack[sp++] = idx;
        }
        return sp;
    }

    private static boolean isWhite(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        int max = r > g ? (r > b ? r : b) : (g > b ? g : b);
        int min = r < g ? (r < b ? r : b) : (g < b ? g : b);
        return min >= WHITE_MIN && (max - min) <= CHANNEL_TOLERANCE;
    }
}
