package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Realistic 3-4 layer Aurora Borealis (Northern Lights) effect for the top half of the screen,
 * blending emerald green, cyan, purple, and the selected app's ambient color with undulating sine waves.
 *
 * About 30 frames a second, and only while Home is on screen and not paused (MainActivity pauses it when Home
 * sits idle). The gradients are made once per size and accent, not every frame. setSpeed slows it (night mode);
 * time advances by the frames drawn, so a change of speed or a pause never makes the curtains jump.
 */
public final class AuroraBackgroundView extends View {

    private static final long FRAME_MS = 33;
    /** One full swing of the curtains, as before: 2π in 9 seconds. */
    private static final float RADIANS_PER_SECOND = (float) (Math.PI * 2 / 9);

    private static final float[] ALPHA = {0.65f, 0.5f, 0.35f, 0.25f};
    private static final int[][] COLORS = {
            {0xFF10B981, 0x5506B6D4, 0x008B5CF6},
            {0, 0x6610B981, 0x0006B6D4},   // the first colour is the accent
            {0xFF8B5CF6, 0x5510B981, 0x0010B981},
            {0xFF06B6D4, 0x448B5CF6, 0x0006B6D4},
    };
    private static final float[] STOPS = {0.0f, 0.45f, 1.0f};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final LinearGradient[] shaders = new LinearGradient[4];
    private int shaderHeight = -1;
    private int accentColor = 0xFF10B981; // default emerald green
    private float phase;
    private long lastFrame;
    private float speed = 1f;
    private boolean paused;

    public AuroraBackgroundView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setAccentColor(int color) {
        if (color != 0 && color != accentColor) {
            this.accentColor = color;
            shaders[1] = null;
            invalidate();
        }
    }

    /** Frozen on its current frame (Home idle), or running again. */
    void setPaused(boolean pause) {
        if (pause == paused) return;
        paused = pause;
        lastFrame = 0;
        invalidate();
    }

    /** 1 is the normal pace; night mode slows it. */
    void setSpeed(float s) {
        speed = s;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        lastFrame = 0;   // no catch-up jump after Home was hidden
        if (visibility == VISIBLE) invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight() / 2; // top half of screen
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.uptimeMillis();
        if (!paused && lastFrame > 0) phase += (now - lastFrame) / 1000f * RADIANS_PER_SECOND * speed;
        lastFrame = now;

        if (h != shaderHeight) {
            shaderHeight = h;
            java.util.Arrays.fill(shaders, null);
        }
        for (int i = 0; i < shaders.length; i++) {
            if (shaders[i] != null) continue;
            int[] colors = COLORS[i].clone();
            if (i == 1) colors[0] = accentColor;
            shaders[i] = new LinearGradient(0, 0, 0, h, colors, STOPS, Shader.TileMode.CLAMP);
        }

        // Draw multi-layer aurora light curtains
        drawAuroraLayer(canvas, w, h, 0, phase);
        drawAuroraLayer(canvas, w, h, 1, phase * 1.2f + 1.2f);
        drawAuroraLayer(canvas, w, h, 2, phase * 0.8f + 2.4f);
        drawAuroraLayer(canvas, w, h, 3, phase * 1.5f + 0.5f);

        if (!paused && getWindowVisibility() == VISIBLE) postInvalidateDelayed(FRAME_MS);
    }

    private void drawAuroraLayer(Canvas canvas, int w, int h, int index, float p) {
        path.reset();
        float slice = w / 6f;
        float topY = h * (0.15f + index * 0.08f);
        float prevY = 0;
        for (int i = 0; i <= 6; i++) {
            float x = i * slice;
            float y = topY + (float) Math.sin(p + i * 0.75f + index * 1.5f) * 40f
                    + (float) Math.cos(p * 0.6f - i * 0.4f) * 30f + index * 25f;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                float ctrlX = x - slice / 2f;
                path.cubicTo(ctrlX, prevY, ctrlX, y, x, y);
            }
            prevY = y;
        }
        // Closed straight back from the top-right corner to the curve's start, as the curtains have always been drawn.
        path.lineTo(w, 0);
        path.close();

        paint.setShader(shaders[index]);
        paint.setAlpha((int) (255 * ALPHA[index]));
        canvas.drawPath(path, paint);
    }
}
