package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Realistic 3-4 layer Aurora Borealis (Northern Lights) effect for the top half of the screen,
 * blending emerald green, cyan, purple, and the selected app's ambient color with undulating sine waves.
 */
public final class AuroraBackgroundView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float phase = 0f;
    private ValueAnimator animator;
    private int accentColor = 0xFF10B981; // default emerald green

    public AuroraBackgroundView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.FILL);
        startAnimation();
    }

    public void setAccentColor(int color) {
        if (color != 0) {
            this.accentColor = color;
            invalidate();
        }
    }

    private void startAnimation() {
        animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        animator.setDuration(9000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight() / 2; // top half of screen
        if (w <= 0 || h <= 0) return;

        // Draw multi-layer aurora light curtains
        drawAuroraLayer(canvas, w, h, 0, 0.65f, 0xFF10B981, 0x5506B6D4, 0x008B5CF6, phase);
        drawAuroraLayer(canvas, w, h, 1, 0.5f, accentColor, 0x6610B981, 0x0006B6D4, phase * 1.2f + 1.2f);
        drawAuroraLayer(canvas, w, h, 2, 0.35f, 0xFF8B5CF6, 0x5510B981, 0x0010B981, phase * 0.8f + 2.4f);
        drawAuroraLayer(canvas, w, h, 3, 0.25f, 0xFF06B6D4, 0x448B5CF6, 0x0006B6D4, phase * 1.5f + 0.5f);
    }

    private void drawAuroraLayer(Canvas canvas, int w, int h, int index, float alphaMultiplier, int colorTop, int colorMid, int colorBot, float p) {
        path.reset();
        path.moveTo(0, 0);

        float slice = w / 6f;
        float topY = h * (0.15f + index * 0.08f);
        path.lineTo(0, topY);

        for (int i = 0; i <= 6; i++) {
            float x = i * slice;
            float wave1 = (float) Math.sin(p + i * 0.75f + index * 1.5f);
            float wave2 = (float) Math.cos(p * 0.6f - i * 0.4f);
            float y = topY + (wave1 * 40f) + (wave2 * 30f) + (index * 25f);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                float prevX = (i - 1) * slice;
                float prevWave1 = (float) Math.sin(p + (i - 1) * 0.75f + index * 1.5f);
                float prevWave2 = (float) Math.cos(p * 0.6f - (i - 1) * 0.4f);
                float prevY = topY + (prevWave1 * 40f) + (prevWave2 * 30f) + (index * 25f);
                float ctrlX = (prevX + x) / 2f;
                path.cubicTo(ctrlX, prevY, ctrlX, y, x, y);
            }
        }

        path.lineTo(w, 0);
        path.close();

        LinearGradient shader = new LinearGradient(0, 0, 0, h,
                new int[]{colorTop, colorMid, colorBot},
                new float[]{0.0f, 0.45f, 1.0f},
                Shader.TileMode.CLAMP);
        paint.setShader(shader);
        paint.setAlpha((int) (255 * alphaMultiplier));
        canvas.drawPath(path, paint);
    }
}
