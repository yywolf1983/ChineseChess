package Utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import top.nones.chessgame.R;

/**
 * 顶部回合信息条上的「图标 + 文字提示」按钮工厂（无背景，直接浮于木色信息条上）。
 * - 菜单按钮：汉堡图标在左，「菜单」二字在右（末尾带下拉箭头）
 * - 模式按钮：当前模式名在左（随模式着色），双方对照图标在右
 * 文字统一加暗投影，在木色背景上保持清晰。
 */
public final class TopChipFactory {

    /** 奶油白，与 RoundView 信息文字同色 */
    public static final int TEXT_COLOR = 0xFFFBF3E0;
    /** 菜单按钮高度（dp）：比原纯图标按钮矮，位置需按垂直中心对齐下移 */
    public static final int CHIP_HEIGHT_DP = 32;
    /** 模式按钮高度（dp）：略低于菜单按钮，避免图标周围留白显得按钮过高 */
    public static final int MODE_CHIP_HEIGHT_DP = 28;
    /** 改造前的纯图标按钮尺寸（dp），用于换算居中偏移 */
    public static final int ORIGIN_ICON_DP = 42;

    private TopChipFactory() {
    }

    /** 由原纯图标按钮的顶部偏移（dp）换算为现按钮的顶部偏移（px），保持垂直中心不变 */
    public static int topFromIconTop(int oldTopDp, float density) {
        return topFromIconTop(oldTopDp, CHIP_HEIGHT_DP, density);
    }

    /** 指定现按钮高度的换算版本 */
    public static int topFromIconTop(int oldTopDp, int chipHeightDp, float density) {
        return (int) ((oldTopDp + (ORIGIN_ICON_DP - chipHeightDp) / 2f) * density);
    }

    /** 菜单按钮：[汉堡图标] [菜单] [下拉箭头] */
    public static LinearLayout createMenuChip(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        LinearLayout chip = newChip(ctx, d, (int) (8 * d), (int) (4 * d));

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.ic_menu);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int iconSize = (int) (22 * d);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(iconSize, iconSize);
        ip.rightMargin = (int) (5 * d);
        chip.addView(icon, ip);

        chip.addView(newLabel(ctx, "菜单", TEXT_COLOR));

        ImageView arrow = new ImageView(ctx);
        arrow.setImageResource(R.drawable.ic_arrow_drop);
        arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int arrowSize = (int) (14 * d);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(arrowSize, arrowSize);
        ap.leftMargin = (int) (3 * d);
        chip.addView(arrow, ap);

        return chip;
    }

    /** 模式按钮：[当前模式名（模式色）] [双方对照图标]，文字/图标按 id 暴露，便于切换模式后刷新 */
    public static LinearLayout createModeChip(Context ctx, String modeName, int modeColor,
                                              int textId, int iconId) {
        float d = ctx.getResources().getDisplayMetrics().density;
        // 右侧内边距收紧，让图标更贴近屏幕右缘
        LinearLayout chip = newChip(ctx, d, (int) (4 * d), (int) (2 * d));

        TextView label = newLabel(ctx, modeName, modeColor);
        label.setId(textId);
        chip.addView(label);

        ImageView icon = new ImageView(ctx);
        icon.setId(iconId);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        // 与 ModeIconDrawable(SIDE_BOTH) 的固有尺寸一致，图标按原始大小显示、不再被缩小
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams((int) (52 * d), (int) (28 * d));
        ip.leftMargin = (int) (2 * d); // 文字与图标更紧凑
        chip.addView(icon, ip);

        return chip;
    }

    /** 刷新模式按钮上的当前模式名与颜色 */
    public static void setModeName(LinearLayout chip, String modeName, int modeColor) {
        if (chip == null) return;
        for (int i = 0; i < chip.getChildCount(); i++) {
            View child = chip.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                tv.setText(modeName);
                tv.setTextColor(modeColor);
                return;
            }
        }
    }

    private static LinearLayout newChip(Context ctx, float density, int padLeft, int padRight) {
        LinearLayout chip = new LinearLayout(ctx);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(null); // 不加背景，保持与改造前一致的透明效果
        chip.setPadding(padLeft, 0, padRight, 0);
        chip.setClickable(true);
        return chip;
    }

    private static TextView newLabel(Context ctx, String text, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setSingleLine(true);
        tv.setIncludeFontPadding(false);
        tv.setShadowLayer(3f, 0.5f, 1f, Color.argb(200, 20, 12, 6));
        return tv;
    }
}
