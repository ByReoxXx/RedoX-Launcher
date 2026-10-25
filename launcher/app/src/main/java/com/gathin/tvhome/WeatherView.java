package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * The weather now, beside the clock: a small animated sign (sun, moon, clouds, rain, snow, storm or fog) and the
 * temperature. Read from Open-Meteo (no key, no account) for config.xml's latitude and longitude, every 30 minutes
 * while Home is on screen and 5 minutes after a failure; nothing runs or animates while Home is hidden. Without a
 * latitude and longitude it stays gone.
 */
public final class WeatherView extends View {

    enum Sky { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

    /** Told each reading (WeatherScene acts it out in the corner). */
    interface Listener {
        void onWeather(Sky sky, boolean day);
    }

    private Listener listener;

    private static final long REFRESH_MS = 30 * 60_000L, RETRY_MS = 5 * 60_000L;
    /** About 30 frames a second: smooth enough for drifting clouds and drops, half the work of every vsync. */
    private static final long FRAME_MS = 33;

    /** The last reading, kept across recreate() so Home shows it at once. */
    private static Sky lastSky;
    private static boolean lastDay = true;
    private static int lastTemp;
    private static long lastFetch;

    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path cut = new Path();
    private final RectF rect = new RectF();
    private String lat = "", lon = "";
    private boolean shown;
    private String temp = "";

    private final Runnable fetch = this::fetch;

    public WeatherView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setColor(0xF2FFFFFF);
        text.setTextSize(26 * getResources().getDisplayMetrics().scaledDensity);
        setVisibility(GONE);
    }

    /** Where to read the weather for; empty values turn it off. */
    void setLocation(String latitude, String longitude) {
        lat = latitude.trim();
        lon = longitude.trim();
        if (!lat.isEmpty() && !lon.isEmpty() && lastSky != null) show();
    }

    void setListener(Listener l) {
        listener = l;
        if (l != null && lastSky != null && !lat.isEmpty() && !lon.isEmpty()) l.onWeather(lastSky, lastDay);
    }

    void setTypeface(Typeface face) {
        text.setTypeface(face);
        requestLayout();
    }

    /** Home shown or hidden. The view itself is GONE until the first reading, so this follows the window instead. */
    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        removeCallbacks(fetch);
        if (visibility != VISIBLE || lat.isEmpty() || lon.isEmpty()) return;
        long age = SystemClock.elapsedRealtime() - lastFetch;
        if (lastSky == null || age >= REFRESH_MS) fetch();
        else postDelayed(fetch, REFRESH_MS - age);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(fetch);
    }

    private void fetch() {
        final String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day";
        new Thread(() -> {
            boolean ok = false;
            try {
                JSONObject now = new JSONObject(read(url)).getJSONObject("current");
                int code = now.getInt("weather_code");
                double t = now.getDouble("temperature_2m");
                boolean day = now.optInt("is_day", 1) == 1;
                post(() -> {
                    lastSky = skyFor(code);
                    lastDay = day;
                    lastTemp = (int) Math.round(t);
                    lastFetch = SystemClock.elapsedRealtime();
                    show();
                });
                ok = true;
            } catch (Exception ignored) {
                // Offline or the service is down: keep the last reading and try again soon.
            }
            final long next = ok ? REFRESH_MS : RETRY_MS;
            post(() -> {
                removeCallbacks(fetch);
                if (getWindowVisibility() == VISIBLE) postDelayed(fetch, next);
            });
        }, "weather").start();
    }

    private static String read(String address) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    /** WMO weather interpretation codes, as Open-Meteo gives them. */
    static Sky skyFor(int code) {
        if (code <= 1) return Sky.CLEAR;
        if (code == 2) return Sky.PARTLY;
        if (code == 3) return Sky.CLOUDY;
        if (code == 45 || code == 48) return Sky.FOG;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return Sky.SNOW;
        if (code >= 95) return Sky.STORM;
        return Sky.RAIN;   // drizzle 51-57, rain 61-67, showers 80-82
    }

    private void show() {
        temp = String.format(Locale.ROOT, "%d°", lastTemp);
        setContentDescription(temp);
        setVisibility(VISIBLE);
        requestLayout();
        invalidate();
        if (listener != null) listener.onWeather(lastSky, lastDay);
    }

    private int iconPx() {
        return Math.round(46 * density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = iconPx() + Math.round(6 * density) + Math.round(text.measureText(temp.isEmpty() ? "00°" : temp));
        setMeasuredDimension(w, iconPx());
    }

    @Override
    protected void onDraw(Canvas c) {
        if (lastSky == null) return;
        float s = iconPx();
        float t = SystemClock.uptimeMillis() / 1000f;
        float cx = s / 2f, cy = s / 2f;
        switch (lastSky) {
            case CLEAR:
                if (lastDay) sun(c, cx, cy, s * 0.20f, t);
                else moon(c, cx, cy, s * 0.22f, t);
                break;
            case PARTLY:
                if (lastDay) sun(c, cx + s * 0.14f, cy - s * 0.12f, s * 0.15f, t);
                else moon(c, cx + s * 0.14f, cy - s * 0.12f, s * 0.15f, t);
                cloud(c, cx - s * 0.04f + drift(t, s * 0.04f), cy + s * 0.10f, s * 0.70f, 0xFFF2F4F8);
                break;
            case CLOUDY:
                cloud(c, cx + s * 0.12f - drift(t, s * 0.03f), cy - s * 0.10f, s * 0.55f, 0xFFB8C0CC);
                cloud(c, cx - s * 0.05f + drift(t, s * 0.05f), cy + s * 0.08f, s * 0.72f, 0xFFE6EAF0);
                break;
            case FOG:
                fog(c, s, t);
                break;
            case RAIN:
                drops(c, s, t, false);
                cloud(c, cx, cy - s * 0.14f, s * 0.78f, 0xFFD5DBE4);
                break;
            case SNOW:
                drops(c, s, t, true);
                cloud(c, cx, cy - s * 0.14f, s * 0.78f, 0xFFE6EAF0);
                break;
            case STORM:
                drops(c, s, t, false);
                bolt(c, s, t);
                cloud(c, cx, cy - s * 0.14f, s * 0.78f, 0xFF9AA3B2);
                break;
        }
        text.setTextAlign(Paint.Align.LEFT);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(temp, s + 6 * density, cy - (fm.ascent + fm.descent) / 2f, text);
        if (getWindowVisibility() == VISIBLE) postInvalidateDelayed(FRAME_MS);
    }

    private static float drift(float t, float amount) {
        return (float) Math.sin(t * 0.8f) * amount;
    }

    /** A warm disc with eight rays that turn slowly and breathe. */
    private void sun(Canvas c, float cx, float cy, float r, float t) {
        fill.setColor(0x40FFC93C);
        c.drawCircle(cx, cy, r * (1.45f + 0.1f * (float) Math.sin(t * 2)), fill);
        stroke.setColor(0xFFFFC93C);
        stroke.setStrokeWidth(r * 0.22f);
        float turn = t * 25f;
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(turn + i * 45);
            float len = r * (0.45f + 0.12f * (float) Math.sin(t * 3 + i));
            float in = r * 1.35f;
            c.drawLine(cx + (float) Math.cos(a) * in, cy + (float) Math.sin(a) * in,
                    cx + (float) Math.cos(a) * (in + len), cy + (float) Math.sin(a) * (in + len), stroke);
        }
        fill.setColor(0xFFFFD45C);
        c.drawCircle(cx, cy, r, fill);
    }

    /** A crescent with two stars that twinkle. */
    private void moon(Canvas c, float cx, float cy, float r, float t) {
        path.reset();
        path.addCircle(cx, cy, r, Path.Direction.CW);
        cut.reset();
        cut.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW);
        path.op(cut, Path.Op.DIFFERENCE);
        fill.setColor(0xFFF3EBC8);
        c.drawPath(path, fill);
        for (int i = 0; i < 2; i++) {
            float a = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 2.2f + i * 2.1f));
            fill.setColor(((int) (a * 255) << 24) | 0xFFFFFF);
            c.drawCircle(cx + r * (i == 0 ? 1.15f : 0.75f), cy + r * (i == 0 ? -0.95f : 0.15f), r * 0.11f, fill);
        }
    }

    /** Three overlapping puffs on a flat base, w wide, centred on (cx, cy). */
    private void cloud(Canvas c, float cx, float cy, float w, int color) {
        fill.setColor(color);
        float r = w * 0.2f;
        c.drawCircle(cx - w * 0.22f, cy + r * 0.25f, r * 0.95f, fill);
        c.drawCircle(cx + w * 0.02f, cy - r * 0.35f, r * 1.3f, fill);
        c.drawCircle(cx + w * 0.25f, cy + r * 0.3f, r * 0.9f, fill);
        rect.set(cx - w * 0.42f, cy + r * 0.1f, cx + w * 0.42f, cy + r * 1.15f);
        c.drawRoundRect(rect, r, r, fill);
    }

    /** Rain streaks or snowflakes falling from under the cloud, each on its own phase. */
    private void drops(Canvas c, float s, float t, boolean snow) {
        float top = s * 0.45f, fall = s * 0.5f;
        for (int i = 0; i < 4; i++) {
            float p = (t * (snow ? 0.45f : 1.4f) + i * 0.27f) % 1f;
            float x = s * (0.24f + i * 0.17f) - p * (snow ? 0 : s * 0.06f);
            float y = top + p * fall;
            int alpha = (int) (255 * Math.min(1f, (1f - p) * 2.5f));
            if (snow) {
                x += (float) Math.sin(t * 2 + i) * s * 0.04f;
                fill.setColor((alpha << 24) | 0xFFFFFF);
                c.drawCircle(x, y, s * 0.045f, fill);
            } else {
                stroke.setColor((alpha << 24) | 0x7CC4FF);
                stroke.setStrokeWidth(s * 0.045f);
                c.drawLine(x, y, x - s * 0.04f, y + s * 0.13f, stroke);
            }
        }
    }

    /** A bolt that flashes twice every 2.6 seconds. */
    private void bolt(Canvas c, float s, float t) {
        float p = t % 2.6f;
        if (!(p < 0.12f || p > 0.24f && p < 0.34f)) return;
        path.reset();
        path.moveTo(s * 0.52f, s * 0.42f);
        path.lineTo(s * 0.38f, s * 0.68f);
        path.lineTo(s * 0.50f, s * 0.68f);
        path.lineTo(s * 0.42f, s * 0.92f);
        path.lineTo(s * 0.66f, s * 0.60f);
        path.lineTo(s * 0.54f, s * 0.60f);
        path.close();
        fill.setColor(0xFFFFE066);
        c.drawPath(path, fill);
    }

    /** Four soft bands sliding to and fro. */
    private void fog(Canvas c, float s, float t) {
        stroke.setStrokeWidth(s * 0.07f);
        for (int i = 0; i < 4; i++) {
            float shift = (float) Math.sin(t * 0.9f + i * 1.3f) * s * 0.08f;
            float y = s * (0.28f + i * 0.15f);
            stroke.setColor(i % 2 == 0 ? 0xE6DDE3EA : 0x99DDE3EA);
            c.drawLine(s * 0.14f + shift, y, s * 0.86f + shift, y, stroke);
        }
    }
}
