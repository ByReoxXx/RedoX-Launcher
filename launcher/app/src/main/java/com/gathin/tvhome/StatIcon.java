package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

/**
 * A small line icon in place of a stats label, drawn in the text's colour at about its cap height: a chip with pins
 * for CPU, a memory module for RAM, a drum for storage. Drawn in place of one placeholder character.
 */
final class StatIcon extends ReplacementSpan {

    static final int CPU = 0, MEMORY = 1, STORAGE = 2;

    private final int kind;
    private final float density;
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    StatIcon(int kind, float density) {
        this.kind = kind;
        this.density = density;
        pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setStrokeJoin(Paint.Join.ROUND);
    }

    private float size(Paint paint) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        return (fm.descent - fm.ascent) * 0.72f;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        float s = size(paint);
        return Math.round((kind == MEMORY ? s * 1.5f : s) + 6 * density);
    }

    @Override
    public void draw(@NonNull Canvas c, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                     @NonNull Paint paint) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        float s = size(paint);
        float mid = y + (fm.ascent + fm.descent) / 2f;
        float w = 1.6f * density;
        pen.setColor(paint.getColor());
        pen.setStrokeWidth(w);
        if (kind == CPU) {
            // A chip: an outlined square, a filled core, and three pins on every side.
            float body = s * 0.62f, left = x + (s - body) / 2f, t = mid - body / 2f;
            pen.setStyle(Paint.Style.STROKE);
            rect.set(left, t, left + body, t + body);
            c.drawRoundRect(rect, w, w, pen);
            pen.setStyle(Paint.Style.FILL);
            float core = body * 0.38f;
            rect.set(left + (body - core) / 2f, mid - core / 2f, left + (body + core) / 2f, mid + core / 2f);
            c.drawRect(rect, pen);
            pen.setStyle(Paint.Style.STROKE);
            float pin = (s - body) / 2f;
            for (int i = 1; i <= 3; i++) {
                float at = body * i / 4f;
                c.drawLine(left + at, t, left + at, t - pin, pen);
                c.drawLine(left + at, t + body, left + at, t + body + pin, pen);
                c.drawLine(left, t + at, left - pin, t + at, pen);
                c.drawLine(left + body, t + at, left + body + pin, t + at, pen);
            }
        } else if (kind == MEMORY) {
            // A memory module: a wide board with three chips on it and contacts along its lower edge.
            float bw = s * 1.5f, bh = s * 0.6f, t = mid - bh / 2f - s * 0.08f;
            pen.setStyle(Paint.Style.STROKE);
            rect.set(x, t, x + bw, t + bh);
            c.drawRoundRect(rect, w, w, pen);
            pen.setStyle(Paint.Style.FILL);
            float chip = bw * 0.2f, gap = (bw - 3 * chip) / 4f;
            for (int i = 0; i < 3; i++) {
                float cl = x + gap + i * (chip + gap);
                rect.set(cl, t + bh * 0.25f, cl + chip, t + bh * 0.75f);
                c.drawRect(rect, pen);
            }
            pen.setStyle(Paint.Style.STROKE);
            for (int i = 1; i <= 5; i++) {
                float at = x + bw * i / 6f;
                c.drawLine(at, t + bh, at, t + bh + s * 0.2f, pen);
            }
        } else {
            // A drum (the usual sign for stored data): an ellipse on top, straight sides, two bands below.
            float dw = s * 0.8f, left = x + (s - dw) / 2f, eh = s * 0.28f;
            float t = mid - s / 2f + eh / 2f, b = mid + s / 2f - eh / 2f;
            pen.setStyle(Paint.Style.STROKE);
            rect.set(left, t - eh / 2f, left + dw, t + eh / 2f);
            c.drawOval(rect, pen);
            c.drawLine(left, t, left, b, pen);
            c.drawLine(left + dw, t, left + dw, b, pen);
            for (float band : new float[]{(t + b) / 2f, b}) {
                rect.set(left, band - eh / 2f, left + dw, band + eh / 2f);
                c.drawArc(rect, 0, 180, false, pen);
            }
        }
    }
}
