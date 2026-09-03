package top.nones.chessgame;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import CustomView.ModeComboView;
import top.nones.chessgame.R;

/**
 * 模式选择弹窗：以图标卡片形式展示四种对战模式。
 * 从「切换模式」按钮位置（右上角）下拉展开，而非从屏幕底部弹出。
 * 行为与原 AlertDialog 选择保持一致（切换不清盘），仅统一 UI。
 */
public class ModePickerDialog {
    public static final String[] MODE_NAMES = {
            "双人对战",
            "玩家执红",
            "玩家执黑",
            "双机对战"
    };

    // 每种模式的主题色（用于色条与选中高亮，直观区分模式）
    // 在深色卡片上需提亮以保证可读性
    private static final int[] THEME_COLORS = {
            Color.rgb(120, 210, 120), // 0 双人：绿（提亮）
            Color.rgb(235, 90, 90),   // 1 玩家红：红（提亮）
            Color.rgb(200, 200, 205), // 2 玩家黑：浅灰（黑在深底不可见）
            Color.rgb(125, 180, 245)  // 3 双机：蓝（提亮）
    };

    private static final int RED = Color.rgb(200, 40, 40);
    private static final int BLACK = Color.rgb(45, 45, 45);
    private static final int BLUE = Color.rgb(90, 150, 235);

    private final Context context;
    private final int currentMode;
    private final OnModeSelectedListener listener;
    private PopupWindow popup;

    public interface OnModeSelectedListener {
        void onModeSelected(int mode);
    }

    public ModePickerDialog(Context context, int currentMode, OnModeSelectedListener listener) {
        this.context = context;
        this.currentMode = currentMode;
        this.listener = listener;
    }

    /** 从指定锚点（切换模式按钮）位置下拉弹出 */
    public void show(View anchor) {
        if (anchor == null) return;
        LayoutInflater inflater = LayoutInflater.from(context);
        View content = inflater.inflate(R.layout.dialog_mode_picker, null);
        // 关键修复：inflate(null) + measure(UNSPECIFIED) 会让根布局的固定 layout_width 失效，
        // 弹窗被内容撑开导致宽度降不下来。改用 EXACTLY 模式测量并显式给 popup 设定宽度，
        // 使 XML 中的 layout_width（此处 210dp）真正生效。
        float dens = context.getResources().getDisplayMetrics().density;
        // 预留选中态勾选图标(约26dp)的空间，多加约2个字符余量，避免选中时4字模式名折行
        int popW = (int) (240 * dens);
        content.measure(View.MeasureSpec.makeMeasureSpec(popW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED);

        int[] cardIds = {R.id.mode_card_0, R.id.mode_card_1, R.id.mode_card_2, R.id.mode_card_3};
        View.OnClickListener cardClick = v -> {
            Object tag = v.getTag();
            if (tag instanceof Integer) {
                int mode = (Integer) tag;
                if (listener != null) {
                    listener.onModeSelected(mode);
                }
                dismiss();
            }
        };
        for (int i = 0; i < cardIds.length; i++) {
            LinearLayout card = content.findViewById(cardIds[i]);
            card.setTag(i);
            card.setOnClickListener(cardClick);
            TextView name = card.findViewById(R.id.mode_card_name);
            name.setText(MODE_NAMES[i]);

            // 双方阵营图标（形状区分人/电脑，颜色区分阵营/电脑），分别置于文字左右
            ModeComboView glyphLeft = card.findViewById(R.id.mode_card_glyph_left);
            ModeComboView glyphRight = card.findViewById(R.id.mode_card_glyph_right);
            boolean leftAI = (i == 2 || i == 3);   // 红方为电脑
            boolean rightAI = (i == 1 || i == 3);  // 黑方为电脑
            int leftColor = leftAI ? BLUE : RED;
            int rightColor = rightAI ? BLUE : BLACK;
            glyphLeft.setSide(leftAI, leftColor);
            glyphRight.setSide(rightAI, rightColor);

            // 左侧主题色条（始终显示，颜色区分模式）
            View accent = card.findViewById(R.id.mode_card_accent);
            accent.setBackgroundColor(THEME_COLORS[i]);

            ImageView check = card.findViewById(R.id.mode_card_check);
            if (i == currentMode) {
                // 选中态：卡片浅色底 + 名称主题色 + 勾选图标主题色
                card.setBackgroundColor(Color.argb(35,
                        Color.red(THEME_COLORS[i]), Color.green(THEME_COLORS[i]), Color.blue(THEME_COLORS[i])));
                name.setTextColor(THEME_COLORS[i]);
                check.setColorFilter(THEME_COLORS[i]);
                check.setVisibility(View.VISIBLE);
            } else {
                card.setBackgroundResource(R.drawable.bg_mode_card_dark);
                name.setTextColor(THEME_COLORS[i]);
                check.setVisibility(View.GONE);
            }
        }

        View cancel = content.findViewById(R.id.mode_cancel);
        if (cancel != null) {
            cancel.setOnClickListener(v -> dismiss());
        }

        popup = new PopupWindow(content,
                popW,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        // 透明背景，保证点击外部可关闭
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setElevation(8f);

        // 右对齐到按钮右缘。注意：showAsDropDown 的 yoff 是相对按钮【底部】的偏移，
        // 用负值才能把弹窗上移到按钮处并轻微重叠按钮底边，从而紧贴触发按钮。
        int xoff = anchor.getWidth() - popW;
        int yoff = -(int) (4 * context.getResources().getDisplayMetrics().density);
        popup.showAsDropDown(anchor, xoff, yoff, Gravity.NO_GRAVITY);
    }

    public void dismiss() {
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
        }
    }
}
