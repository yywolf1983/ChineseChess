package CustomView;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.drawable.DrawableCompat;

import top.nones.chessgame.R;

/**
 * 「模式」按钮图标：以「红方侧 vs 黑方侧」的双 glyph 组合表示当前对战模式。
 * - 形状区分 人(ic_player) / 电脑(ic_ai)；玩家(ic_player)图标按 iconColor（按钮背景对比色）着色以保证可见，
 *   机器人(ic_ai)图标保持原有颜色不变。
 * - 与 ModePickerDialog 的卡片图标逻辑保持一致：
 *     leftAI  = (mode==2 或 3)  // 红方为电脑
 *     rightAI = (mode==1 或 3)  // 黑方为电脑
 *
 * side 控制绘制范围：
 *     SIDE_BOTH  - 两 glyph 并排（用于需要组合图标之处）
 *     SIDE_LEFT  - 仅左方 glyph（作为按钮左侧图标）
 *     SIDE_RIGHT - 仅右方 glyph（作为按钮右侧图标）
 */
public class ModeIconDrawable extends Drawable {
    public static final int SIDE_BOTH = 0;
    public static final int SIDE_LEFT = 1;
    public static final int SIDE_RIGHT = 2;

    private final Context context;
    private final int mode;
    private final int side;
    private final int iconColor;
    private final int intrinsicW;
    private final int intrinsicH;

    public ModeIconDrawable(Context context, int mode, float density) {
        this(context, mode, density, SIDE_BOTH, Color.WHITE);
    }

    public ModeIconDrawable(Context context, int mode, float density, int side) {
        this(context, mode, density, side, Color.WHITE);
    }

    public ModeIconDrawable(Context context, int mode, float density, int side, int iconColor) {
        this.context = context;
        this.mode = mode;
        this.side = side;
        this.iconColor = iconColor;
        if (side == SIDE_BOTH) {
            // 宽幅画布（顶部模式按钮用）：两 glyph 左右并排，装满按钮高度且不过高
            this.intrinsicW = (int) (52f * density);
            this.intrinsicH = (int) (28f * density);
        } else {
            this.intrinsicW = (int) (18f * density);
            this.intrinsicH = (int) (18f * density);
        }
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

        if (side == SIDE_BOTH) {
            // 宽幅画布：字形尽量放大（按宽度取 0.44），两 glyph 分居左右，互不重叠
            float g = Math.min(H * 0.96f, W * 0.44f);
            float leftCx = b.left + W * 0.26f;
            float rightCx = b.left + W * 0.74f;
            drawGlyph(canvas, leftCx, cy, g, leftAI());
            drawGlyph(canvas, rightCx, cy, g, rightAI());
            // 中间极细的连接点，强调「对战双方」对照
            android.graphics.Paint dot = new android.graphics.Paint();
            dot.setAntiAlias(true);
            dot.setColor(Color.argb(140, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor)));
            canvas.drawCircle(b.left + W * 0.5f, cy, Math.max(1f, g * 0.05f), dot);
        } else if (side == SIDE_LEFT) {
            float g = Math.min(H * 0.92f, W * 0.92f);
            drawGlyph(canvas, b.left + W / 2f, cy, g, leftAI());
        } else { // SIDE_RIGHT
            float g = Math.min(H * 0.92f, W * 0.92f);
            drawGlyph(canvas, b.left + W / 2f, cy, g, rightAI());
        }
    }

    private void drawGlyph(Canvas canvas, float cx, float cy, float size, boolean isAI) {
        Drawable d = androidx.core.content.ContextCompat.getDrawable(context,
                isAI ? R.drawable.ic_ai : R.drawable.ic_player);
        if (d == null) return;
        if (!isAI) {
            // 玩家图标按按钮背景对比色着色以保证可见；机器人（AI）图标保持原有颜色不变
            d = DrawableCompat.wrap(d.mutate());
            DrawableCompat.setTint(d, iconColor);
        }
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
        // 颜色由各 glyph 按 iconColor 着色，忽略外部滤镜
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}
