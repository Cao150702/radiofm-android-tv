package com.radio.fm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;

/**
 * 动态频谱条 —— 播放时随声音起伏，暂停/缓冲/停止时静止。
 *
 * ── 为什么是「合成动画」而不是真实 FFT ──
 * 真正取波形要走 MediaPlayer.getAudioSessionId() + Visualizer，但这条链在
 * 电视盒子上很不可靠：很多盒子的音频输出走 HDMI 且由厂商定制，
 * Visualizer 拿到的是空数据（getWaveForm 返回全 0），真做了就是一根直条 ——
 * 比不做还难看。而这个应用的主力场景正是电视/盒子。
 *
 * 所以这里按"声音的样子"合成动画：每根柱条 = 若干正弦波叠加，
 * 各自的频率/相位不同，整体看起来像在随音乐律动，且**零兼容风险**。
 * 代价是它不对应真实音频 —— 但用户要的是"看起来在工作"，
 * 这一点合成动画完全做得到，而且比真频谱稳定得多。
 *
 * 「播放才动、暂停就停」这个联动是**真的** —— 由 RadioService 的实际状态驱动，
 * 不是自己瞎跑。所以"暂停时静止"是真信息，不是装饰。
 */
public class SpectrumView extends View {

    private static final int BARS = 28;

    /** 每帧推进的相位（弧度）。120ms 一帧 —— 这个数字决定整体快慢 */
    private static final float PHASE_STEP = 0.34f;

    private static final int IDLE_MSG = 1;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();

    /** 每根柱条当前的显示高度（0..1），做平滑用 —— 直接跳变会很生硬 */
    private final float[] levels = new float[BARS];
    /** 每根柱条的目标高度，动画向它逼近 */
    private final float[] targets = new float[BARS];
    /** 每根柱条的相位偏移，让它们不同步 */
    private final float[] phases = new float[BARS];
    /** 每根柱条起伏的快慢倍率 */
    private final float[] speeds = new float[BARS];

    private float tick = 0f;
    private boolean active = false;

    public SpectrumView(Context c) { super(c); init(); }
    public SpectrumView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        // 深色底 —— 原来那条白底在木纹面板上太跳，且柱子需要深底才显得亮
        bgPaint.setColor(0xFF2A1A14);

        for (int i = 0; i < BARS; i++) {
            // 用下标做确定性的"随机"，避免 Random 每次重建布局都变
            phases[i] = (float) (i * 1.7 % (Math.PI * 2));
            speeds[i] = 0.7f + (i % 5) * 0.16f;
        }
    }

    /**
     * 由外部（MainActivity）按播放状态调用。
     *
     * @param playing 正在播放 → 动；其余（缓冲/暂停/停止/出错）→ 静止
     */
    public void setActive(boolean playing) {
        if (this.active == playing) return;
        this.active = playing;

        if (playing) {
            removeMessages();
            step();                 // 立刻出一帧，别等 120ms 才有反应
            invalidate();
            schedule();
        } else {
            // 停下来时让柱子**淡出**而不是硬切 —— 但必须继续刷帧，
            // 否则只画一帧就停在半空，看着像卡死（这是最初的 bug）。
            // 淡到接近 0 就彻底停，之后一帧都不再画。
            java.util.Arrays.fill(targets, 0f);
            removeMessages();
            step();
            invalidate();
            if (!faded()) schedule();
        }
    }

    /** 所有柱条都基本归零了吗 */
    private boolean faded() {
        for (float l : levels) if (l > 0.02f) return false;
        return true;
    }

    private final Handler handler = new Handler() {
        @Override public void handleMessage(Message msg) {
            if (msg.what != IDLE_MSG) return;
            step();
            invalidate();          // 这一小块整块重绘，开销可忽略
            // 播放中一直刷；停下后刷到淡完为止，然后彻底静默
            if (active || !faded()) schedule();
        }
    };

    private void schedule() {
        handler.sendEmptyMessageDelayed(IDLE_MSG, 120);
    }

    private void removeMessages() {
        handler.removeMessages(IDLE_MSG);
    }

    /** 推进一帧：算目标高度，再让显示高度向它平滑逼近 */
    private void step() {
        tick += PHASE_STEP;
        for (int i = 0; i < BARS; i++) {
            if (active) {
                // 三个不同周期的正弦叠加 → 看起来不那么"机械"
                float v = (float) (
                        0.50
                        + 0.28 * Math.sin(tick * speeds[i] + phases[i])
                        + 0.16 * Math.sin(tick * speeds[i] * 2.3 + phases[i] * 1.7)
                        + 0.06 * Math.sin(tick * speeds[i] * 4.1 + phases[i] * 0.5));
                // 中间高两头低，像真的频谱包络
                float shape = 1f - 0.55f * Math.abs(i - (BARS - 1) / 2f) / ((BARS - 1) / 2f);
                targets[i] = clamp(v * shape, 0.06f, 1f);
            } else {
                targets[i] = 0f;
            }
            // 平滑逼近：上升比下降稍快，观感更"跟手"
            float d = targets[i] - levels[i];
            levels[i] += d * (d > 0 ? 0.45f : 0.28f);
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.drawRect(0, 0, w, h, bgPaint);

        // 柱条：等宽 + 等间距
        float slot = (float) w / BARS;
        float barW = slot * 0.62f;
        float gap = (slot - barW) / 2f;
        // 顶部留一点余量，最高的柱子不贴边
        float maxH = h * 0.88f;
        float baseY = h * 0.94f;

        // 按高度着色：矮=绿，中=黄，高=红。每根柱子单独设色，
        // 所以这里逐条 createLinearGradient（28 根，开销可忽略）
        for (int i = 0; i < BARS; i++) {
            float lv = levels[i];
            if (lv <= 0.01f) continue;
            float bh = maxH * lv;
            float left = i * slot + gap;
            float right = left + barW;
            float top = baseY - bh;

            int color;
            if (lv < 0.55f)      color = 0xFF43A047;   // 绿
            else if (lv < 0.80f) color = 0xFFFDD835;   // 黄
            else                 color = 0xFFE53935;   // 红

            barPaint.setShader(new LinearGradient(0, top, 0, baseY,
                    color, blend(color, 0xFF2A1A14, 0.55f), Shader.TileMode.CLAMP));
            canvas.drawRect(left, top, right, baseY, barPaint);
        }
        barPaint.setShader(null);

        // 基线：一条暗线，静止时也有个"仪器感"
        Paint line = barPaint;
        line.setColor(0xFF5D4037);
        canvas.drawRect(0, baseY, w, baseY + Math.max(1f, h * 0.02f), line);
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return Color.rgb(r, g, bl);
    }

    @Override
    protected void onDetachedFromWindow() {
        // 视图销毁时必须停掉 handler，否则 Activity 关了它还在跑
        removeMessages();
        active = false;
        super.onDetachedFromWindow();
    }
}
