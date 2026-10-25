package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * The weather WeatherView reads, acted out in the top-left corner of Home behind the clock: sunlight with slow rays
 * from the corner, a cool glow with twinkling stars at night, drifting clouds, rain, snow, storm flashes or fog. It
 * fades out towards the middle of the screen so the shelf and the dock stay clear, and draws nothing until there is
 * a reading. Like WeatherView it animates only while Home is on screen.
 */
public final class WeatherScene extends View implements WeatherView.Listener {

    private static final long FRAME_MS = 33;
    private static final int RAIN = 70, FLAKES = 50, STARS = 22;

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    /** Per particle: x and y as a share of the corner's reach, speed, size. Fixed, so the scene never jumps. */
    private final float[] px = new float[RAIN], py = new float[RAIN], ps = new float[RAIN], pz = new float[RAIN];

    private WeatherView.Sky sky;
    private boolean day = true;
    /** How far the scene reaches from the corner, in px; the glows are made for it in onSizeChanged. */
    private float reach;
    private RadialGradient warm, cool, grey, flash;
    private Bitmap cloud;
    private PorterDuffColorFilter tint;
    private int tintColor;
    private final RectF dst = new RectF();

    public WeatherScene(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        Random r = new Random(7);
        for (int i = 0; i < RAIN; i++) {
            // Bunched towards the corner: the square of a uniform number leans to 0.
            float a = r.nextFloat();
            px[i] = a * a * 1.1f;
            py[i] = r.nextFloat();
            ps[i] = 0.7f + r.nextFloat() * 0.6f;
            pz[i] = r.nextFloat();
        }
        setWillNotDraw(false);
    }

    @Override
    public void onWeather(WeatherView.Sky sky, boolean day) {
        this.sky = sky;
        this.day = day;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        reach = Math.max(w * 0.55f, h * 0.75f);
        warm = glow(0x8CFFD27A, 0x33FFB347);
        cool = glow(0x4D7C93FF, 0x1A5B6CFF);
        grey = glow(0x40C8D0DC, 0x14A0A8B4);
        flash = glow(0xB3FFFFFF, 0x40D8E4FF);
        // Drawn small and stretched: the blur hides the scaling, and it stays about 100 KB.
        if (cloud == null && w > 0) cloud = softCloud(Math.round(160 * density));
    }

    private RadialGradient glow(int inner, int mid) {
        return new RadialGradient(0, 0, reach, new int[]{inner, mid, 0x00000000}, new float[]{0f, 0.4f, 1f},
                Shader.TileMode.CLAMP);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (sky == null || reach <= 0) return;
        float t = SystemClock.uptimeMillis() / 1000f;
        switch (sky) {
            case CLEAR:
                if (day) sunlight(c, t, 1f);
                else night(c, t);
                break;
            case PARTLY:
                if (day) sunlight(c, t, 0.7f);
                else night(c, t);
                clouds(c, t, 2, 0x38FFFFFF);
                break;
            case CLOUDY:
                glowAt(c, grey, 1f);
                clouds(c, t, 3, 0x30E6EAF0);
                break;
            case FOG:
                glowAt(c, grey, 1f);
                fog(c, t);
                break;
            case RAIN:
                glowAt(c, grey, 0.8f);
                clouds(c, t, 2, 0x26C8D0DC);
                rain(c, t);
                break;
            case SNOW:
                glowAt(c, grey, 0.8f);
                snow(c, t);
                break;
            case STORM:
                glowAt(c, grey, 0.9f);
                clouds(c, t, 2, 0x2690A0B4);
                rain(c, t);
                float p = t % 4.2f;
                if (p < 0.1f || p > 0.22f && p < 0.3f) glowAt(c, flash, 1f);
                break;
        }
        if (getWindowVisibility() == VISIBLE) postInvalidateDelayed(FRAME_MS);
    }

    /** The share of full strength at (x, y): 1 in the corner, 0 at the scene's reach. */
    private float fade(float x, float y) {
        return Math.max(0f, 1f - (float) Math.hypot(x, y) / reach);
    }

    /** Falling particles stay in the corner: they reach 60% as far as the glow and fade out faster. */
    private float corner(float x, float y) {
        float f = Math.max(0f, 1f - (float) Math.hypot(x, y) / (reach * 0.6f));
        return f * f;
    }

    private void glowAt(Canvas c, RadialGradient shader, float alpha) {
        paint.setShader(shader);
        paint.setAlpha(Math.round(255 * alpha));
        c.drawCircle(0, 0, reach, paint);
        paint.setShader(null);
        paint.setAlpha(255);
    }

    /** A warm glow from the corner that breathes, and seven soft rays that sway a few degrees. */
    private void sunlight(Canvas c, float t, float strength) {
        glowAt(c, warm, strength * (0.85f + 0.15f * (float) Math.sin(t * 0.9f)));
        paint.setShader(warm);
        float sway = 4f * (float) Math.sin(t * 0.35f);
        for (int i = 0; i < 7; i++) {
            double mid = Math.toRadians(8 + i * 12.5 + sway);
            double half = Math.toRadians(2.2 + 1.2 * Math.sin(t * 0.7 + i));
            path.reset();
            path.moveTo(0, 0);
            path.lineTo((float) Math.cos(mid - half) * reach, (float) Math.sin(mid - half) * reach);
            path.lineTo((float) Math.cos(mid + half) * reach, (float) Math.sin(mid + half) * reach);
            path.close();
            float a = 0.35f + 0.3f * (0.5f + 0.5f * (float) Math.sin(t * 0.8f + i * 1.7f));
            paint.setAlpha(Math.round(255 * a * strength));
            c.drawPath(path, paint);
        }
        paint.setShader(null);
        paint.setAlpha(255);
    }

    /** A cool glow and stars that twinkle each on its own beat. */
    private void night(Canvas c, float t) {
        glowAt(c, cool, 1f);
        for (int i = 0; i < STARS; i++) {
            float x = px[i] * reach * 0.8f, y = py[i] * reach * 0.6f;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (1.2f + pz[i]) + i * 2.3f);
            int a = Math.round(255 * fade(x, y) * (0.3f + 0.7f * tw));
            paint.setColor((a << 24) | 0xFFFFFF);
            c.drawCircle(x, y, (1f + pz[i] * 1.5f) * density, paint);
        }
    }

    /**
     * Large soft clouds drifting slowly across the corner and back: one blurred white cloud, drawn once in
     * onSizeChanged, scaled and tinted per cloud. A blur every frame would cost a weak TV GPU far more.
     */
    private void clouds(Canvas c, float t, int count, int color) {
        if (cloud == null) return;
        int alpha = color >>> 24;
        if (tint == null || tintColor != color) {
            tintColor = color;
            tint = new PorterDuffColorFilter(color | 0xFF000000, PorterDuff.Mode.SRC_IN);
        }
        paint.setColorFilter(tint);
        for (int i = 0; i < count; i++) {
            float w = reach * (0.55f - i * 0.1f);
            float h = w * cloud.getHeight() / cloud.getWidth();
            float x = reach * (0.14f + i * 0.3f) + (float) Math.sin(t * 0.12f + i * 2f) * reach * 0.08f;
            float y = reach * (0.04f + i * 0.1f);
            int a = Math.round(alpha * 1.8f * fade(x, y));
            if (a <= 0) continue;
            paint.setAlpha(Math.min(255, a));
            dst.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2);
            c.drawBitmap(cloud, null, dst, paint);
        }
        paint.setColorFilter(null);
        paint.setAlpha(255);
    }

    /** Three overlapping puffs on a flat base, white, with a soft blurred edge. */
    private static Bitmap softCloud(int w) {
        int h = Math.round(w * 0.6f), pad = Math.round(w * 0.12f);
        Bitmap bmp = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        p.setMaskFilter(new BlurMaskFilter(pad * 0.8f, BlurMaskFilter.Blur.NORMAL));
        float x = pad + w / 2f, y = pad + h * 0.45f, r = w * 0.22f;
        Path path = new Path();
        path.addCircle(x - w * 0.24f, y + r * 0.3f, r, Path.Direction.CW);
        path.addCircle(x, y - r * 0.3f, r * 1.35f, Path.Direction.CW);
        path.addCircle(x + w * 0.26f, y + r * 0.35f, r * 0.95f, Path.Direction.CW);
        path.addRoundRect(x - w * 0.45f, y + r * 0.1f, x + w * 0.45f, y + r * 1.25f, r, r, Path.Direction.CW);
        c.drawPath(path, p);
        return bmp;
    }

    /** Slanted streaks, faster near the front, fading with distance from the corner. */
    private void rain(Canvas c, float t) {
        float fall = reach * 0.6f;
        line.setStrokeWidth(1.6f * density);
        for (int i = 0; i < RAIN; i++) {
            float p = (t * 0.9f * ps[i] + py[i]) % 1f;
            float y = -reach * 0.05f + p * fall;
            float x = px[i] * reach * 0.6f - p * reach * 0.08f;
            float len = (14 + 16 * pz[i]) * density;
            int a = Math.round(230 * corner(x, y) * (0.5f + 0.5f * pz[i]));
            if (a <= 0) continue;
            line.setColor((a << 24) | 0xA8D4FF);
            c.drawLine(x, y, x - len * 0.25f, y + len, line);
        }
    }

    private void snow(Canvas c, float t) {
        float fall = reach * 0.6f;
        for (int i = 0; i < FLAKES; i++) {
            float p = (t * 0.12f * ps[i] + py[i]) % 1f;
            float y = -reach * 0.03f + p * fall;
            float x = px[i] * reach * 0.6f + (float) Math.sin(t * 0.8f + i) * 10 * density;
            int a = Math.round(240 * corner(x, y));
            if (a <= 0) continue;
            paint.setColor((a << 24) | 0xFFFFFF);
            c.drawCircle(x, y, (1.5f + 2f * pz[i]) * density, paint);
        }
    }

    /** Three wide bands sliding to and fro, thinning out away from the corner. */
    private void fog(Canvas c, float t) {
        for (int i = 0; i < 3; i++) {
            float y = reach * (0.12f + i * 0.14f);
            float shift = (float) Math.sin(t * 0.25f + i * 1.9f) * reach * 0.1f;
            line.setStrokeWidth(reach * 0.06f);
            line.setColor(((int) (255 * 0.18f * fade(reach * 0.3f, y)) << 24) | 0xDDE3EA);
            c.drawLine(-reach * 0.1f + shift, y, reach * 0.75f + shift, y, line);
        }
    }
}
