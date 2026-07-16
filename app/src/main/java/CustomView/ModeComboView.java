package CustomView;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import top.nones.chessgame.R;

/**
 * 模式选择弹窗中的「单阵营示意」图标：红方/黑方玩家 或 电脑。
 * 形状区分人/电脑，颜色区分阵营（红/黑）与电脑（蓝）。
 * 一张卡片左右各放一个，文字居中，形成「红 vs 黑 / 玩家 vs 电脑」的对照。
 */
public class ModeComboView extends View {
    private boolean ai = false;
    private int color = Color.rgb(200, 40, 40);
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

    public void setSide(boolean isAI, int color) {
        this.ai = isAI;
        this.color = color;
        invalidate();
    }

    private void drawGlyph(Canvas canvas, float cx, float cy, float size, boolean isAI, int color) {
        int s = (int) size;
        if (!isAI) {
            // 玩家：使用矢量人形图标（ic_player），按阵营色着色，与 RoundView 一致
            Drawable d = androidx.core.content.ContextCompat.getDrawable(getContext(), R.drawable.ic_player);
            if (d != null) {
                d = androidx.core.graphics.drawable.DrawableCompat.wrap(d.mutate());
                androidx.core.graphics.drawable.DrawableCompat.setTint(d, color);
                d.setBounds(Math.round(cx - s / 2f), Math.round(cy - s / 2f),
                        Math.round(cx + s / 2f), Math.round(cy + s / 2f));
                d.draw(canvas);
            }
        } else {
            // 电脑：使用矢量机器人图标（ic_ai，自带配色）
            Drawable d = androidx.core.content.ContextCompat.getDrawable(getContext(), R.drawable.ic_ai);
            if (d != null) {
                d = androidx.core.graphics.drawable.DrawableCompat.wrap(d.mutate());
                d.setBounds(Math.round(cx - s / 2f), Math.round(cy - s / 2f),
                        Math.round(cx + s / 2f), Math.round(cy + s / 2f));
                d.draw(canvas);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float cy = h / 2f;
        float size = Math.min(h * 0.92f, 68f);
        drawGlyph(canvas, w / 2f, cy, size, ai, color);
    }
}
