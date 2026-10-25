package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

/**
 * A small five-step meter after a stats value: as many steps lit as the load fills, in the load's colour (green,
 * amber, red), the rest faint. Drawn in place of one placeholder character.
 */
final class StatMeter extends ReplacementSpan {

    static final int GREEN = 0xFF34D399, AMBER = 0xFFFBBF24, RED = 0xFFF87171;
    private static final int STEPS = 5;

    private final int lit;
    private final int color;
    private final float density;
    private final RectF rect = new RectF();

    /** @param percent how full, 0 to 100 */
    StatMeter(int percent, float density) {
        this.lit = Math.max(1, Math.min(STEPS, (int) Math.ceil(percent * STEPS / 100f)));
        this.color = colorFor(percent);
        this.density = density;
    }

    /** Green under 50%, amber from 50%, red from 80%. */
    static int colorFor(int percent) {
        return percent >= 80 ? RED : percent >= 50 ? AMBER : GREEN;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        return Math.round((6 + STEPS * 8 - 3) * density);
    }

    @Override
    public void draw(@NonNull Canvas c, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                     @NonNull Paint paint) {
        int old = paint.getColor();
        Paint.FontMetrics fm = paint.getFontMetrics();
        float h = (fm.descent - fm.ascent) * 0.62f;
        float mid = y + (fm.ascent + fm.descent) / 2f;
        float w = 5 * density, gap = 3 * density;
        float left = x + 6 * density;
        for (int i = 0; i < STEPS; i++) {
            // Each step a little taller than the last, like a signal meter.
            float sh = h * (0.55f + 0.45f * i / (STEPS - 1));
            rect.set(left, mid + h / 2f - sh, left + w, mid + h / 2f);
            paint.setColor(i < lit ? color : 0x33FFFFFF);
            c.drawRoundRect(rect, 1.5f * density, 1.5f * density, paint);
            left += w + gap;
        }
        paint.setColor(old);
    }
}
