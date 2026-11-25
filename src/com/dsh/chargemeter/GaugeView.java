package com.dsh.chargemeter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class GaugeView extends View {

    private static final float START = 135f;
    private static final float SWEEP = 270f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float ratio = 0f;
    private float smooth = 0f;
    private ValueAnimator animator;
    private float stroke = 10f;

    private int cTrack = Color.parseColor("#131E2A");
    private int c1 = Color.parseColor("#00E5FF");
    private int c2 = Color.parseColor("#5B8CFF");
    private int c3 = Color.parseColor("#B14CFF");
    private int cDot = Color.parseColor("#00E5FF");
    private int cTick = Color.parseColor("#22384C");

    public GaugeView(Context c) {
        this(c, null);
    }

    public GaugeView(Context c, AttributeSet attrs) {
        super(c, attrs);
        stroke = dp(9);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(cTrack);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(stroke * 2.6f);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(dp(1.2f));
        tickPaint.setColor(cTick);

        dotPaint.setStyle(Paint.Style.FILL);
    }

    void setTheme(int track, int accent1, int accent2, int accent3, int dot, int tick, int glow) {
        cTrack = track;
        c1 = accent1;
        c2 = accent2;
        c3 = accent3;
        cDot = dot;
        cTick = tick;
        trackPaint.setColor(track);
        tickPaint.setColor(tick);
        glowPaint.setColor(glow);
        dotPaint.setColor(dot);
        rebuildShader();
        invalidate();
    }

    private void rebuildShader() {
        if (oval.width() <= 0) return;
        arcPaint.setShader(new LinearGradient(oval.left, oval.bottom,
                oval.right, oval.top,
                new int[]{c1, c2, c3},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    private float dp(float v) {
        return getResources().getDisplayMetrics().density * v;
    }

    void setRatio(float r) {
        ratio = Math.max(0f, Math.min(1f, r));
        if (animator != null && animator.isRunning()) return;
        smooth = ratio;
        invalidate();
    }

    /** 平滑过渡到目标比例（600ms 缓动），用于让仪表的弧线“游动”而不是跳变 */
    void animateTo(float r) {
        float target = Math.max(0f, Math.min(1f, r));
        if (Math.abs(target - smooth) < 0.002f) return;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(smooth, target);
        animator.setDuration(600);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            smooth = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    void stopAnim() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float pad = stroke * 2.2f + dp(14);
        float size = Math.min(w, h) - pad * 2;
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;
        oval.set(left, top, left + size, top + size);
        rebuildShader();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (int i = 0; i <= 36; i++) {
            double ang = Math.toRadians(START + SWEEP * i / 36f);
            float r1 = oval.width() / 2f + stroke * 1.6f;
            float r2 = r1 + (i % 3 == 0 ? dp(9) : dp(4));
            float cx = oval.centerX();
            float cy = oval.centerY();
            canvas.drawLine(cx + (float) (r1 * Math.cos(ang)), cy + (float) (r1 * Math.sin(ang)),
                    cx + (float) (r2 * Math.cos(ang)), cy + (float) (r2 * Math.sin(ang)), tickPaint);
        }

        canvas.drawArc(oval, START, SWEEP, false, trackPaint);

        if (smooth > 0.002f) {
            canvas.drawArc(oval, START, SWEEP * smooth, false, glowPaint);
            canvas.drawArc(oval, START, SWEEP * smooth, false, arcPaint);

            double ang = Math.toRadians(START + SWEEP * smooth);
            float r = oval.width() / 2f;
            float px = oval.centerX() + (float) (r * Math.cos(ang));
            float py = oval.centerY() + (float) (r * Math.sin(ang));
            dotPaint.setColor((cDot & 0x00FFFFFF) | 0x33000000);
            canvas.drawCircle(px, py, dp(11), dotPaint);
            dotPaint.setColor(cDot);
            canvas.drawCircle(px, py, dp(5.5f), dotPaint);
            dotPaint.setColor(Color.WHITE);
            canvas.drawCircle(px, py, dp(2.4f), dotPaint);
            dotPaint.setColor(cDot);
        }
    }
}
