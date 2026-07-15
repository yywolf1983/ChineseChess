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
            // 电脑：屏幕 + 支架 + 底座
            android.graphics.RectF screen = new android.graphics.RectF(
                    cx - r * 0.78f, cy - r * 0.72f, cx + r * 0.78f, cy + r * 0.22f);
            canvas.drawRoundRect(screen, 2, 2, paint);
            canvas.drawLine(cx, cy + r * 0.22f, cx, cy + r * 0.72f, paint);
            canvas.drawLine(cx - r * 0.62f, cy + r * 0.72f, cx + r * 0.62f, cy + r * 0.72f, paint);
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
