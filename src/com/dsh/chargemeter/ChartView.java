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
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path areaPath = new Path();
    private final float[] xs = new float[MAX_POINTS];
    private final float[] ys = new float[MAX_POINTS];

    private float maxScale = 20f;
    private int cLine = Color.parseColor("#4D6BFE");
    private int cGrid = Color.parseColor("#EAECF0");
    private int cText = Color.parseColor("#8A8F99");
    private int cFill = Color.parseColor("#4D6BFE");

    public ChartView(Context c) {
        this(c, null);
    }

    public ChartView(Context c, AttributeSet attrs) {
        super(c, attrs);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2.4f));
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setColor(cLine);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(7));
        glowPaint.setStrokeCap(Paint.Cap.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
        gridPaint.setColor(cGrid);

        textPaint.setColor(cText);
        textPaint.setTextSize(dp(9));
        textPaint.setTextAlign(Paint.Align.RIGHT);

        dotPaint.setColor(cLine);
    }

    void setTheme(int line, int grid, int text, int fill) {
        cLine = line;
        cGrid = grid;
        cText = text;
        cFill = fill;
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
        maxScale = Math.max(5f, max * 1.2f);
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
        float left = dp(30);
        float right = w - dp(4);
        float top = dp(8);
        float bottom = h - dp(16);

        // 横向网格 + Y 轴刻度标签
        for (int i = 0; i <= 4; i++) {
            float y = top + (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, gridPaint);
            float label = maxScale * (4 - i) / 4f;
            String s = i == 0 ? String.format("%.0f", maxScale) : String.format("%.0f", label);
            canvas.drawText(s, left - dp(4), y + dp(3), textPaint);
        }
        canvas.drawText("W", left - dp(4), top - dp(3), textPaint);

        if (points.size() < 2) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("正在采样…", (left + right) / 2f, (top + bottom) / 2f, textPaint);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            return;
        }

        int n = points.size();
        float step = (right - left) / (MAX_POINTS - 1);
        float startX = right - step * (n - 1);

        for (int i = 0; i < n; i++) {
            xs[i] = startX + step * i;
            ys[i] = bottom - (bottom - top) * Math.min(1f, points.get(i) / maxScale);
        }

        // 平滑曲线（二次贝塞尔，中点连接）
        linePath.reset();
        areaPath.reset();
        linePath.moveTo(xs[0], ys[0]);
        areaPath.moveTo(xs[0], bottom);
        areaPath.lineTo(xs[0], ys[0]);
        for (int i = 1; i < n - 1; i++) {
            float xc = (xs[i] + xs[i + 1]) / 2f;
            float yc = (ys[i] + ys[i + 1]) / 2f;
            linePath.quadTo(xs[i], ys[i], xc, yc);
            areaPath.quadTo(xs[i], ys[i], xc, yc);
        }
        linePath.lineTo(xs[n - 1], ys[n - 1]);
        areaPath.lineTo(xs[n - 1], ys[n - 1]);
        areaPath.lineTo(xs[n - 1], bottom);
        areaPath.close();

        // 面积渐变填充
        fillPaint.setShader(new LinearGradient(0, top, 0, bottom,
                new int[]{cFill & 0x00FFFFFF | 0x33000000, cFill & 0x00FFFFFF | 0x05000000},
                null, Shader.TileMode.CLAMP));
        canvas.drawPath(areaPath, fillPaint);
        fillPaint.setShader(null);

        // 发光底 + 实线
        glowPaint.setColor(cLine & 0x00FFFFFF | 0x2A000000);
        canvas.drawPath(linePath, glowPaint);
        canvas.drawPath(linePath, linePaint);

        // 末端圆点
        canvas.drawCircle(xs[n - 1], ys[n - 1], dp(3.6f), dotPaint);
        dotPaint.setColor(Color.WHITE);
        canvas.drawCircle(xs[n - 1], ys[n - 1], dp(1.6f), dotPaint);
        dotPaint.setColor(cLine);
    }
}
