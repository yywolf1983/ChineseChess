package CustomView;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 模式选择弹窗中的「双方阵营示意」图标：左侧红方、右侧黑方。
 * 形状区分人/电脑，颜色区分阵营（红/黑）与电脑（蓝）。
 */
public class ModeComboView extends View {
    private boolean leftAI = false;
    private boolean rightAI = false;
    private int leftColor = Color.rgb(200, 40, 40);
    private int rightColor = Color.rgb(40, 40, 40);
    private final Paint paint = new Paint();

    public ModeComboView(Context context) {
        super(context);
        init();
    }

    public ModeComboView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ModeComboView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setSides(boolean leftAI, int leftColor, boolean rightAI, int rightColor) {
        this.leftAI = leftAI;
        this.leftColor = leftColor;
        this.rightAI = rightAI;
        this.rightColor = rightColor;
        invalidate();
    }

    private void drawGlyph(Canvas canvas, float cx, float cy, float size, boolean isAI, int color) {
        paint.setColor(color);
        float r = size / 2f;
        if (!isAI) {
            // 玩家：头部 + 肩膀弧线
            canvas.drawCircle(cx, cy - r * 0.32f, r * 0.4f, paint);
            android.graphics.RectF body = new android.graphics.RectF(
                    cx - r * 0.62f, cy + r * 0.05f, cx + r * 0.62f, cy + r * 1.0f);
            canvas.drawArc(body, 18, 144, false, paint);
        } else {
            // 机器人：天线 + 方形头 + 眼睛 + 身体
            // 天线
            canvas.drawLine(cx, cy - r * 0.82f, cx, cy - r * 0.6f, paint);
            canvas.drawCircle(cx, cy - r * 0.92f, r * 0.11f, paint);
            // 头部
            android.graphics.RectF head = new android.graphics.RectF(
                    cx - r * 0.58f, cy - r * 0.6f, cx + r * 0.58f, cy + r * 0.06f);
            canvas.drawRoundRect(head, r * 0.2f, r * 0.2f, paint);
            // 眼睛（填充）
            Paint.Style prevStyle = paint.getStyle();
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx - r * 0.24f, cy - r * 0.27f, r * 0.1f, paint);
            canvas.drawCircle(cx + r * 0.24f, cy - r * 0.27f, r * 0.1f, paint);
            paint.setStyle(prevStyle);
            // 身体
            android.graphics.RectF body = new android.graphics.RectF(
                    cx - r * 0.44f, cy + r * 0.16f, cx + r * 0.44f, cy + r * 0.62f);
            canvas.drawRoundRect(body, r * 0.16f, r * 0.16f, paint);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float cy = h / 2f;
        float size = Math.min(h * 0.72f, 26f);
        float leftCx = w * 0.34f;
        float rightCx = w * 0.66f;
        drawGlyph(canvas, leftCx, cy, size, leftAI, leftColor);
        drawGlyph(canvas, rightCx, cy, size, rightAI, rightColor);
    }
}
