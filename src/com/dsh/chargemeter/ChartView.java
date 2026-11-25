package com.dsh.chargemeter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class ChartView extends View {

    private static final int MAX_POINTS = 120;

    private final List<Float> points = new ArrayList<>();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path areaPath = new Path();
    private float maxScale = 20f;
    private int cLine = Color.parseColor("#00FFA3");
    private int cGrid = Color.parseColor("#14212E");
    private int cText = Color.parseColor("#3A5468");
    private int cFill = Color.parseColor("#5500FFA3");

    public ChartView(Context c) {
        this(c, null);
    }

    public ChartView(Context c, AttributeSet attrs) {
        super(c, attrs);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2.4f));
        linePaint.setColor(Color.parseColor("#00FFA3"));
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
        gridPaint.setColor(Color.parseColor("#14212E"));

        textPaint.setColor(Color.parseColor("#3A5468"));
        textPaint.setTextSize(dp(10));

        dotPaint.setColor(Color.parseColor("#00FFA3"));
    }

    void setTheme(int line, int grid, int text, int fill) {
        cLine = line; cGrid = grid; cText = text; cFill = fill;
        linePaint.setColor(line);
        gridPaint.setColor(grid);
        textPaint.setColor(text);
        dotPaint.setColor(line);
        invalidate();
    }

    void push(float value) {
        points.add(value);
        while (points.size() > MAX_POINTS) {
            points.remove(0);
        }
        float max = 1f;
        for (float v : points) {
            if (v > max) max = v;
        }
        maxScale = Math.max(5f, max * 1.25f);
        invalidate();
    }

    void clear() {
        points.clear();
        invalidate();
    }

    private float dp(float v) {
        return getResources().getDisplayMetrics().density * v;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float left = dp(4);
        float right = w - dp(4);
        float top = dp(14);
        float bottom = h - dp(6);

        for (int i = 0; i <= 4; i++) {
            float y = top + (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, gridPaint);
        }
        canvas.drawText(String.format("%.0f W", maxScale), left + dp(2), top - dp(4), textPaint);

        if (points.size() < 2) {
            textPaint.setColor(Color.parseColor("#3A5468"));
            textPaint.setTextSize(dp(11));
            canvas.drawText("正在采样…", left + dp(2), (top + bottom) / 2f, textPaint);
            textPaint.setColor(Color.parseColor("#3A5468"));
            textPaint.setTextSize(dp(10));
            return;
        }

        int n = points.size();
        float step = (right - left) / (MAX_POINTS - 1);
        float startX = right - step * (n - 1);

        linePath.reset();
        areaPath.reset();
        for (int i = 0; i < n; i++) {
            float x = startX + step * i;
            float v = points.get(i);
            float y = bottom - (bottom - top) * Math.min(1f, v / maxScale);
            if (i == 0) {
                linePath.moveTo(x, y);
                areaPath.moveTo(x, bottom);
                areaPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                areaPath.lineTo(x, y);
            }
        }
        areaPath.lineTo(startX + step * (n - 1), bottom);
        areaPath.close();

        fillPaint.setShader(new LinearGradient(0, top, 0, bottom,
                new int[]{cFill, cFill & 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
        canvas.drawPath(areaPath, fillPaint);
        fillPaint.setShader(null);

        linePaint.setStrokeWidth(dp(6));
        linePaint.setColor((cLine & 0x00FFFFFF) | 0x33000000);
        canvas.drawPath(linePath, linePaint);
        linePaint.setStrokeWidth(dp(2.2f));
        linePaint.setColor(cLine);
        canvas.drawPath(linePath, linePaint);

        float lastY = bottom - (bottom - top) * Math.min(1f, points.get(n - 1) / maxScale);
        canvas.drawCircle(startX + step * (n - 1), lastY, dp(3.4f), dotPaint);
    }
}
