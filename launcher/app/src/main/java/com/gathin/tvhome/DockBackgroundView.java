package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Draws a fixed dock background frame (frosted glass or solid band with border) for single-row horizontal dock. */
public class DockBackgroundView extends View {
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float shaderTop = Float.NaN, shaderBottom = Float.NaN;
    private final boolean lightGlass;
    private final boolean translucent;
    private final int opacity;
    private final float density;

    DockBackgroundView(Context context, boolean translucent, int opacity, boolean lightGlass, float density) {
        super(context);
        this.lightGlass = lightGlass;
        this.translucent = translucent;
        this.opacity = opacity;
        this.density = density;
        band.setColor(translucent ? 0x8C121014 : (Math.round(opacity * 2.55f) << 24) | 0x141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(lightGlass ? 0x38FFFFFF : translucent ? 0x26FFFFFF : 0x1FFFFFFF);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float pad = 14 * density;
        rect.set(pad, pad, getWidth() - pad, getHeight() - pad);
        float radius = 26 * density;
        if (lightGlass && (rect.top != shaderTop || rect.bottom != shaderBottom)) {
            band.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x24FFFFFF, 0x0DFFFFFF,
                    Shader.TileMode.CLAMP));
            shaderTop = rect.top;
            shaderBottom = rect.bottom;
        }
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
