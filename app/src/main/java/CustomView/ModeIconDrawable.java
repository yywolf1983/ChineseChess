package CustomView;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.drawable.DrawableCompat;

import top.nones.chessgame.R;

/**
 * 「模式」按钮顶部图标：以「红方侧 vs 黑方侧」的双 glyph 组合表示当前对战模式。
 * - 形状区分 人(ic_player) / 电脑(ic_ai)；颜色区分阵营（红/黑）与电脑（蓝）。
 * - 与 ModePickerDialog 的卡片图标逻辑保持一致：
 *     leftAI  = (mode==2 或 3)  // 红方为电脑
 *     rightAI = (mode==1 或 3)  // 黑方为电脑
 */
public class ModeIconDrawable extends Drawable {
    // 与 ModePickerDialog 保持一致的阵营色
    private static final int RED = Color.rgb(200, 40, 40);
    private static final int BLACK = Color.rgb(45, 45, 45);
    private static final int BLUE = Color.rgb(90, 150, 235);

    private final Context context;
    private final int mode;
    private final int intrinsicW;
    private final int intrinsicH;

    public ModeIconDrawable(Context context, int mode, float density) {
        this.context = context;
        this.mode = mode;
        this.intrinsicW = (int) (52f * density);
        this.intrinsicH = (int) (24f * density);
    }

    private boolean leftAI() {
        return mode == 2 || mode == 3;
    }

    private boolean rightAI() {
        return mode == 1 || mode == 3;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        int W = b.width();
        int H = b.height();
        float cy = b.top + H / 2f;
        float g = Math.min(H * 0.94f, W * 0.42f);
        float leftCx = b.left + W * 0.30f;
        float rightCx = b.left + W * 0.70f;

        drawGlyph(canvas, leftCx, cy, g, leftAI(), leftAI() ? BLUE : RED);
        drawGlyph(canvas, rightCx, cy, g, rightAI(), rightAI() ? BLUE : BLACK);

        // 中间极细的连接点，强调「对战双方」对照
        android.graphics.Paint dot = new android.graphics.Paint();
        dot.setAntiAlias(true);
        dot.setColor(Color.argb(120, 200, 210, 220));
        canvas.drawCircle(b.left + W * 0.5f, cy, Math.max(1f, g * 0.05f), dot);
    }

    private void drawGlyph(Canvas canvas, float cx, float cy, float size, boolean isAI, int color) {
        Drawable d;
        if (!isAI) {
            d = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_player);
            if (d != null) {
                d = DrawableCompat.wrap(d.mutate());
                DrawableCompat.setTint(d, color);
            }
        } else {
            d = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_ai);
        }
        if (d == null) return;
        int s = Math.round(size);
        d.setBounds(Math.round(cx - s / 2f), Math.round(cy - s / 2f),
                Math.round(cx + s / 2f), Math.round(cy + s / 2f));
        d.draw(canvas);
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicW;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicH;
    }

    @Override
    public void setAlpha(int alpha) {
        // 组合图标不做整体透明度动画，忽略
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        // 颜色由各 glyph 自带/着色，忽略外部滤镜
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}
