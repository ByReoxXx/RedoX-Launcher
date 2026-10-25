package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextClock;

/**
 * Home's clock, still a TextClock (format, size and font come from the layout), drawn by hand so that:
 * - at each minute only the digits that changed roll: the old one slides up and fades, the new one rises from below;
 * - its colour follows the focused app (setAccent): the app's hue, kept bright and only half saturated, so it reads
 *   on any backdrop;
 * - a dark shadow under it and a soft glow in the app's colour keep it clear over the aurora and the weather scene.
 * Nothing animates between minutes; a frame is drawn only while a roll or a colour change runs.
 */
public final class RollingClock extends TextClock {

    private static final long ROLL_MS = 650, TINT_MS = 500;

    private final float density;
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private String shown = "", previous = "";
    private float roll = 1f;
    private ValueAnimator rollAnim, tintAnim;
    private int color = Color.WHITE, accent;

    public RollingClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
    }

    /** The focused app's colour; 0 goes back to white. */
    void setAccent(int newAccent) {
        if (newAccent == accent && tintAnim == null) return;
        accent = newAccent;
        int target = newAccent == 0 ? Color.WHITE : bright(newAccent);
        if (tintAnim != null) tintAnim.cancel();
        int from = color;
        tintAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(TINT_MS);
        tintAnim.addUpdateListener(a -> {
            color = (int) argb.evaluate(a.getAnimatedFraction(), from, target);
            invalidate();
        });
        tintAnim.start();
    }

    /** The colour's hue at full brightness and 55% saturation: clearly the app's colour, never dark. */
    static int bright(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(hsv[1], 0.55f);
        hsv[2] = 1f;
        return Color.HSVToColor(hsv);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (density == 0f) return;   // TextView sets its first text before this class's fields exist
        String next = text.toString();
        if (next.equals(shown)) return;
        previous = shown;
        shown = next;
        if (previous.isEmpty() || !isAttachedToWindow()) {
            roll = 1f;
            return;
        }
        if (rollAnim != null) rollAnim.cancel();
        rollAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(ROLL_MS);
        rollAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        rollAnim.addUpdateListener(a -> {
            roll = (float) a.getAnimatedValue();
            invalidate();
        });
        rollAnim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (rollAnim != null) rollAnim.end();
        if (tintAnim != null) tintAnim.end();
    }

    @Override
    protected void onDraw(Canvas c) {
        Layout layout = getLayout();
        if (layout == null || shown.isEmpty()) return;
        TextPaint p = getPaint();
        float x = getTotalPaddingLeft() + layout.getLineLeft(0);
        float y = getTotalPaddingTop() + layout.getLineBaseline(0);
        float lift = getTextSize() * 0.55f;
        boolean rolling = roll < 1f && previous.length() == shown.length();
        for (int pass = 0; pass < 2; pass++) {
            // First a dark shadow for contrast, then the text again with a glow in the app's colour.
            if (pass == 0) p.setShadowLayer(10 * density, 0, 3 * density, 0xD9000000);
            else p.setShadowLayer(22 * density, 0, 0, accent == 0 ? 0x80FFFFFF : (bright(accent) & 0xFFFFFF) | 0xCC000000);
            for (int i = 0; i < shown.length(); i++) {
                float cx = x + p.measureText(shown, 0, i);
                String now = shown.substring(i, i + 1);
                if (rolling && previous.charAt(i) != shown.charAt(i)) {
                    float ox = x + p.measureText(previous, 0, i);
                    draw(c, p, previous.substring(i, i + 1), ox, y - lift * roll, 1f - roll);
                    draw(c, p, now, cx, y + lift * (1f - roll), roll);
                } else {
                    draw(c, p, now, cx, y, 1f);
                }
            }
        }
        p.clearShadowLayer();
    }

    private void draw(Canvas c, TextPaint p, String s, float x, float y, float alpha) {
        if (alpha <= 0.01f) return;
        p.setColor(color);
        p.setAlpha(Math.round(255 * alpha));
        c.drawText(s, x, y, p);
    }
}
