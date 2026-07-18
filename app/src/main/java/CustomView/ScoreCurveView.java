package CustomView;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 整局评分曲线（专业行情图风格）：
 *  - 自适应网格 + 右侧刻度轴（centipawns，+红优 / -黑优）
 *  - 均势基准线（虚线高亮）
 *  - Catmull-Rom 平滑曲线，按优势分色（红优暖色 / 黑优冷色）
 *  - 纵向渐变填充 + 末点发光高亮 + 实时评分胶囊标签
 * 数据来自 ChessInfo.evalHistory，每步落子追加一点（最右端为最新），曲线随对局逐步向右延伸。
 * 作为底部提示盒的背景层：提示盒浮于其上覆盖，提示消失后曲线重新露出。
 */
public class ScoreCurveView extends View {
    private List<Integer> scores = new ArrayList<>();

    private Paint bgPaint;
    private Paint gridPaint;
    private Paint zeroPaint;
    private Paint redPaint;
    private Paint blackPaint;
    private Paint fillPaint;
    private Paint glowPaint;
    private Paint dotPaint;
    private Paint badgeBgPaint;
    private Paint badgeTextPaint;
    private Paint placeholderPaint;

    private float density;
    private int cornerRadius;
    private Path clipPath;
    private Path smoothPath;
    private Path segPath;

    // 暖色（红优）/ 冷色（黑优）
    private static final int RED_ADV = Color.rgb(244, 102, 96);   // 暖红
    private static final int BLACK_ADV = Color.rgb(86, 156, 214);  // 冷蓝

    public ScoreCurveView(Context context) {
        super(context);
        init();
    }

    public ScoreCurveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        cornerRadius = (int) (8f * density);

        bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setAntiAlias(true);

        // 次级网格线
        gridPaint = new Paint();
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setColor(Color.argb(55, 170, 200, 220));
        gridPaint.setStrokeWidth(1.3f * density);
        gridPaint.setAntiAlias(true);

        // 均势（0）基准线：虚线、更亮更粗
        zeroPaint = new Paint();
        zeroPaint.setStyle(Paint.Style.STROKE);
        zeroPaint.setColor(Color.argb(200, 150, 185, 210));
        zeroPaint.setStrokeWidth(1.6f * density);
        zeroPaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{5f * density, 4f * density}, 0));
        zeroPaint.setAntiAlias(true);

        redPaint = new Paint();
        redPaint.setStyle(Paint.Style.STROKE);
        redPaint.setStrokeWidth(1.3f * density);
        redPaint.setAntiAlias(true);
        redPaint.setStrokeJoin(Paint.Join.ROUND);
        redPaint.setStrokeCap(Paint.Cap.ROUND);
        redPaint.setColor(RED_ADV);

        blackPaint = new Paint();
        blackPaint.setStyle(Paint.Style.STROKE);
        blackPaint.setStrokeWidth(1.3f * density);
        blackPaint.setAntiAlias(true);
        blackPaint.setStrokeJoin(Paint.Join.ROUND);
        blackPaint.setStrokeCap(Paint.Cap.ROUND);
        blackPaint.setColor(BLACK_ADV);

        fillPaint = new Paint();
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAntiAlias(true);

        glowPaint = new Paint();
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setAntiAlias(true);

        dotPaint = new Paint();
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setAntiAlias(true);

        // 实时评分胶囊标签
        badgeBgPaint = new Paint();
        badgeBgPaint.setStyle(Paint.Style.FILL);
        badgeBgPaint.setAntiAlias(true);
        badgeBgPaint.setColor(RED_ADV);

        badgeTextPaint = new Paint();
        badgeTextPaint.setTextSize(10f * density);
        badgeTextPaint.setColor(Color.WHITE);
        badgeTextPaint.setAntiAlias(true);
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);

        placeholderPaint = new Paint();
        placeholderPaint.setTextSize(11f * density);
        placeholderPaint.setColor(Color.argb(140, 200, 220, 235));
        placeholderPaint.setAntiAlias(true);
        placeholderPaint.setTextAlign(Paint.Align.CENTER);

        clipPath = new Path();
        smoothPath = new Path();
        segPath = new Path();
    }

    /** 设置整局评分序列（红优为正、黑优为负） */
    public void setScores(List<Integer> s) {
        scores = new ArrayList<>(s != null ? s : java.util.Collections.<Integer>emptyList());
        postInvalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clipPath.reset();
        clipPath.addRoundRect(new RectF(0, 0, w, h), cornerRadius, cornerRadius, Path.Direction.CW);
    }

    /** 评分格式化：mate（>=10000）显示 M，否则带符号整数 */
    private String fmt(int v) {
        if (Math.abs(v) >= 10000) {
            return (v > 0 ? "+M" : "-M") + (Math.abs(v) / 100);
        }
        return (v > 0 ? "+" : "") + v;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.save();
        canvas.clipPath(clipPath);

        // 背景（圆角卡片底，上深下浅渐变）
        android.graphics.LinearGradient bgGrad = new android.graphics.LinearGradient(
                0, 0, 0, h, Color.parseColor("#1B2733"), Color.parseColor("#121A24"), Shader.TileMode.CLAMP);
        bgPaint.setShader(bgGrad);
        canvas.drawRoundRect(new RectF(0, 0, w, h), cornerRadius, cornerRadius, bgPaint);
        bgPaint.setShader(null);
        // 顶部高光描边
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setColor(Color.argb(55, 120, 160, 190));
        bgPaint.setStrokeWidth(1f * density);
        canvas.drawRoundRect(new RectF(0.5f * density, 0.5f * density,
                w - 0.5f * density, h - 0.5f * density), cornerRadius, cornerRadius, bgPaint);
        bgPaint.setStyle(Paint.Style.FILL);

        float padX = 8f * density;
        float padTop = 7f * density;
        float padBottom = 7f * density;
        float plotW = w - 2f * padX;            // 不预留刻度轴，曲线占满宽度
        float plotH = h - padTop - padBottom;
        float midY = padTop + plotH / 2f;

        int n = scores.size();

        // 纵轴缩放：纯由数据驱动，最大坐标为「当前绝对值最大 + 10%」(×1.10)，不写死任何常量
        float maxAbs = 0f;
        for (int v : scores) {
            float a = Math.abs(v);
            if (a > maxAbs) maxAbs = a;
        }
        maxAbs = maxAbs * 1.10f;
        if (maxAbs < 1f) maxAbs = 1f;           // 极小值保护，避免除零/退化

        if (n == 0) {
            placeholderPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("评分曲线", w / 2f, midY + 4f * density, placeholderPaint);
            canvas.restore();
            return;
        }

        // 网格线：+max / +mid / 0 / -mid / -max（纯参考线，不显示数字）
        float[] gridVals = {maxAbs, maxAbs / 2f, 0, -maxAbs / 2f, -maxAbs};
        for (float gv : gridVals) {
            float gy = midY - (gv / maxAbs) * (plotH / 2f);
            if (Math.abs(gv) < 1f) {
                canvas.drawLine(padX, gy, padX + plotW, gy, zeroPaint);
            } else {
                canvas.drawLine(padX, gy, padX + plotW, gy, gridPaint);
            }
        }

        // 计算各点坐标
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            float x = (n == 1) ? (padX + plotW / 2f) : (padX + plotW * i / (n - 1));
            float v = scores.get(i);
            float norm = Math.max(-1f, Math.min(1f, v / maxAbs));
            xs[i] = x;
            ys[i] = midY - norm * (plotH / 2f);
        }

        // 竖线网格：每步（每个数据点）一根，与曲线点对齐。
        // 过长对局点过密时自动降低透明度；每约 24dp 选一根「主竖线」略加强，避免糊成一片。
        float stepSpacing = (n > 1) ? (plotW / (n - 1)) : plotW;
        int majorEvery = Math.max(1, (int) Math.ceil((24f * density) / Math.max(stepSpacing, 1f)));
        int gridBaseAlpha = gridPaint.getAlpha();
        for (int i = 0; i < n; i++) {
            boolean major = (i % majorEvery == 0) || (i == n - 1);
            gridPaint.setAlpha(major ? 90 : 40);
            canvas.drawLine(xs[i], padTop, xs[i], h - padBottom, gridPaint);
        }
        gridPaint.setAlpha(gridBaseAlpha);

        // 1) 渐变填充：沿平滑曲线闭合到底边，颜色随当前优势方变化
        int top = (scores.get(n - 1) >= 0) ? RED_ADV : BLACK_ADV;
        int topA = Color.argb(75, Color.red(top), Color.green(top), Color.blue(top));
        int botA = Color.argb(0, Color.red(top), Color.green(top), Color.blue(top));
        android.graphics.LinearGradient fillGrad = new android.graphics.LinearGradient(
                0, padTop, 0, h - padBottom, topA, botA, Shader.TileMode.CLAMP);
        fillPaint.setShader(fillGrad);
        buildSmoothPath(smoothPath, xs, ys, n);
        Path fillPath = new Path(smoothPath);
        fillPath.lineTo(xs[n - 1], h - padBottom);
        fillPath.lineTo(xs[0], h - padBottom);
        fillPath.close();
        canvas.drawPath(fillPath, fillPaint);
        fillPaint.setShader(null);

        // 2) 平滑曲线分段着色（按段中点符号：红优暖色 / 黑优冷色）
        for (int i = 0; i < n - 1; i++) {
            boolean redSeg = ((scores.get(i) + scores.get(i + 1)) / 2f) >= 0;
            Paint seg = redSeg ? redPaint : blackPaint;
            float c1x, c1y, c2x, c2y;
            int i0 = Math.max(0, i - 1);
            int i3 = Math.min(n - 1, i + 2);
            c1x = xs[i] + (xs[i + 1] - xs[i0]) / 6f;
            c1y = ys[i] + (ys[i + 1] - ys[i0]) / 6f;
            c2x = xs[i + 1] - (xs[i3] - xs[i]) / 6f;
            c2y = ys[i + 1] - (ys[i3] - ys[i]) / 6f;
            segPath.reset();
            segPath.moveTo(xs[i], ys[i]);
            segPath.cubicTo(c1x, c1y, c2x, c2y, xs[i + 1], ys[i + 1]);
            canvas.drawPath(segPath, seg);
        }

        // 3) 末点发光高亮
        int dotColor = (scores.get(n - 1) >= 0) ? RED_ADV : BLACK_ADV;
        glowPaint.setColor(Color.argb(65, Color.red(dotColor), Color.green(dotColor), Color.blue(dotColor)));
        canvas.drawCircle(xs[n - 1], ys[n - 1], 7f * density, glowPaint);
        dotPaint.setColor(dotColor);
        canvas.drawCircle(xs[n - 1], ys[n - 1], 3.4f * density, dotPaint);
        dotPaint.setColor(Color.WHITE);
        canvas.drawCircle(xs[n - 1], ys[n - 1], 1.4f * density, dotPaint);

        // 4) 实时评分胶囊标签（专业行情图常见的当前值提示）
        String label = fmt(scores.get(n - 1));
        float tw = badgeTextPaint.measureText(label);
        float bw = tw + 12f * density;
        float bh = 15f * density;
        float bx = xs[n - 1];
        // 水平：靠右则左移，靠左则右移，避免出界
        if (bx + bw / 2f > padX + plotW) bx = padX + plotW - bw / 2f;
        if (bx - bw / 2f < padX) bx = padX + bw / 2f;
        float by = ys[n - 1] - bh - 5f * density;
        if (by < padTop + 1f) by = ys[n - 1] + 5f * density; // 顶部空间不足则放下方
        badgeBgPaint.setColor(dotColor);
        canvas.drawRoundRect(new RectF(bx - bw / 2f, by, bx + bw / 2f, by + bh),
                7f * density, 7f * density, badgeBgPaint);
        canvas.drawText(label, bx, by + bh / 2f + 3.5f * density, badgeTextPaint);

        canvas.restore();
    }

    /** 由离散点生成平滑曲线路径（Catmull-Rom 转三次贝塞尔） */
    private void buildSmoothPath(Path path, float[] xs, float[] ys, int n) {
        path.reset();
        if (n == 0) return;
        path.moveTo(xs[0], ys[0]);
        if (n == 1) return;
        for (int i = 0; i < n - 1; i++) {
            int i0 = Math.max(0, i - 1);
            int i3 = Math.min(n - 1, i + 2);
            float c1x = xs[i] + (xs[i + 1] - xs[i0]) / 6f;
            float c1y = ys[i] + (ys[i + 1] - ys[i0]) / 6f;
            float c2x = xs[i + 1] - (xs[i3] - xs[i]) / 6f;
            float c2y = ys[i + 1] - (ys[i3] - ys[i]) / 6f;
            path.cubicTo(c1x, c1y, c2x, c2y, xs[i + 1], ys[i + 1]);
        }
    }
}
