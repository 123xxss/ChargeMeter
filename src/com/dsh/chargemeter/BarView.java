package com.dsh.chargemeter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** 水平功率条：替代环形仪表，更现代的读数呈现 */
public class BarView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float ratio = 0f;
    private float shown = 0f;
    private int c1, c2, cTrack;
    private ValueAnimator anim;

    public BarView(Context c) {
        this(c, null);
    }

    public BarView(Context c, AttributeSet a) {
        super(c, a);
        track.setStyle(Paint.Style.FILL);
        fill.setStyle(Paint.Style.FILL);
        c1 = 0xFF00E5FF;
        c2 = 0xFF00FFA3;
        cTrack = 0xFF16283A;
        track.setColor(cTrack);
    }

    void setTheme(int accent1, int accent2, int trackColor) {
        c1 = accent1;
        c2 = accent2;
        cTrack = trackColor;
        track.setColor(trackColor);
        invalidate();
    }

    void setRatio(float r) {
        r = Math.max(0f, Math.min(1f, r));
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(shown, r);
        anim.setDuration(420);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    void setRatioInstant(float r) {
        shown = Math.max(0f, Math.min(1f, r));
        invalidate();
    }

    void stopAnim() {
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;

        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, track);

        float fw = Math.max(h, w * shown);
        if (fw > h) {
            fill.setShader(new LinearGradient(0, 0, fw, 0,
                    new int[]{c1, c2}, null, Shader.TileMode.CLAMP));
            rect.set(0, 0, fw, h);
            canvas.drawRoundRect(rect, r, r, fill);
            fill.setShader(null);
        } else if (shown > 0.01f) {
            canvas.drawCircle(h / 2f, h / 2f, h / 2f, track);
        }
    }
}
