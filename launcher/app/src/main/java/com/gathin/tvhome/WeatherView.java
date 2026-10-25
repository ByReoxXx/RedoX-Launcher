package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * The temperature now, beside the clock, with today's daytime high (a small sun) over tonight's low (a small moon)
 * after it; WeatherScene acts the weather out in the corner. Read from Open-Meteo (no key, no account) for
 * config.xml's latitude and longitude, every 30 minutes while Home is on screen and 5 minutes after a failure; nothing runs or animates while Home is hidden. Without a
 * latitude and longitude it stays gone.
 */
public final class WeatherView extends View {

    enum Sky { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

    /** Told each reading (WeatherScene acts it out in the corner). */
    interface Listener {
        void onWeather(Sky sky, boolean day);
    }

    private Listener listener;
    /** Shown and hidden with the temperature: the line before it. */
    private View divider;

    private static final long REFRESH_MS = 30 * 60_000L, RETRY_MS = 5 * 60_000L;

    /** The last reading, kept across recreate() so Home shows it at once. */
    private static Sky lastSky;
    private static boolean lastDay = true;
    private static int lastTemp;
    /** Today's high, and the low from 20:00 to 06:00 (the night now, before 06:00); MIN_VALUE while unknown. */
    private static int lastHigh = Integer.MIN_VALUE, lastLow = Integer.MIN_VALUE;
    private static long lastFetch;

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path moon = new Path(), cut = new Path();
    private final float density;
    private String lat = "", lon = "";
    private String temp = "", high = "", low = "";

    private final Runnable fetch = this::fetch;

    public WeatherView(Context context, AttributeSet attrs) {
        super(context, attrs);
        text.setColor(0xF2FFFFFF);
        float sp = getResources().getDisplayMetrics().scaledDensity;
        density = getResources().getDisplayMetrics().density;
        text.setTextSize(26 * sp);
        small.setColor(0xCCFFFFFF);
        small.setTextSize(13 * sp);
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

    void setDivider(View v) {
        divider = v;
        if (getVisibility() == VISIBLE) v.setVisibility(VISIBLE);
    }

    void setTypeface(Typeface face) {
        text.setTypeface(face);
        small.setTypeface(face);
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
                + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max&hourly=temperature_2m"
                + "&forecast_days=2&timezone=auto";
        new Thread(() -> {
            boolean ok = false;
            try {
                JSONObject root = new JSONObject(read(url));
                JSONObject now = root.getJSONObject("current");
                int code = now.getInt("weather_code");
                double t = now.getDouble("temperature_2m");
                boolean day = now.optInt("is_day", 1) == 1;
                int hi = (int) Math.round(root.getJSONObject("daily").getJSONArray("temperature_2m_max").getDouble(0));
                // Local times (timezone=auto) from today 00:00: the night is 20:00 to 06:00, or the one still on.
                int hour = Integer.parseInt(now.getString("time").substring(11, 13));
                JSONArray hourly = root.getJSONObject("hourly").getJSONArray("temperature_2m");
                double night = Double.MAX_VALUE;
                for (int i = hour < 6 ? 0 : 20; i <= (hour < 6 ? 6 : 30) && i < hourly.length(); i++) {
                    night = Math.min(night, hourly.getDouble(i));
                }
                int lo = night == Double.MAX_VALUE ? Integer.MIN_VALUE : (int) Math.round(night);
                post(() -> {
                    lastSky = skyFor(code);
                    lastDay = day;
                    lastTemp = (int) Math.round(t);
                    lastHigh = hi;
                    lastLow = lo;
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
        high = lastHigh == Integer.MIN_VALUE ? "" : String.format(Locale.ROOT, "%d°", lastHigh);
        low = lastLow == Integer.MIN_VALUE ? "" : String.format(Locale.ROOT, "%d°", lastLow);
        setContentDescription(high.isEmpty() ? temp : temp + ", " + high + " / " + low);
        setVisibility(VISIBLE);
        if (divider != null) divider.setVisibility(VISIBLE);
        requestLayout();
        invalidate();
        if (listener != null) listener.onWeather(lastSky, lastDay);
    }

    /** The gap after the temperature, and the sun or moon before each small figure, with its gap. */
    private float gapPx() { return 12 * density; }
    private float glyphPx() { return 15 * density; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Paint.FontMetrics fm = text.getFontMetrics();
        float w = text.measureText(temp.isEmpty() ? "00°" : temp);
        if (!high.isEmpty()) w += gapPx() + glyphPx() + Math.max(small.measureText(high), small.measureText(low));
        setMeasuredDimension(Math.round(w), Math.round(fm.descent - fm.ascent));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (lastSky == null) return;
        c.drawText(temp, 0, -text.getFontMetrics().ascent, text);
        if (high.isEmpty()) return;
        // Two small lines in the height of the temperature: the day's high over the night's low.
        float x = text.measureText(temp) + gapPx();
        Paint.FontMetrics fm = small.getFontMetrics();
        float line = getHeight() / 2f;
        float r = 3.6f * density;
        for (int i = 0; i < 2; i++) {
            float mid = line * i + line / 2f;
            if (i == 0) {
                glyph.setColor(0xFFFFC93C);
                c.drawCircle(x + r + density, mid, r, glyph);
            } else {
                moon.reset();
                moon.addCircle(x + r + density, mid, r * 1.1f, Path.Direction.CW);
                cut.reset();
                cut.addCircle(x + r + density + r * 0.6f, mid - r * 0.4f, r * 0.95f, Path.Direction.CW);
                moon.op(cut, Path.Op.DIFFERENCE);
                glyph.setColor(0xFFE8E2C8);
                c.drawPath(moon, glyph);
            }
            c.drawText(i == 0 ? high : low, x + glyphPx(), mid - (fm.ascent + fm.descent) / 2f, small);
        }
    }
}
