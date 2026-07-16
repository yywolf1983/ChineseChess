package top.nones.chessgame;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import CustomView.ModeComboView;
import top.nones.chessgame.R;

/**
 * 模式选择底部弹窗：图标卡片形式展示四种对战模式。
 * 行为与原 AlertDialog 选择保持一致（切换不清盘），仅统一 UI。
 */
public class ModePickerDialog extends Dialog implements View.OnClickListener {
    private static final String[] MODE_NAMES = {
            "双人对战",
            "玩家执红",
            "玩家执黑",
            "双机对战"
    };

    // 每种模式的主题色（用于色条与选中高亮，直观区分模式）
    private static final int[] THEME_COLORS = {
            Color.rgb(76, 175, 80),   // 0 双人：绿
            Color.rgb(200, 40, 40),   // 1 玩家红：红
            Color.rgb(45, 45, 45),    // 2 玩家黑：黑
            Color.rgb(90, 150, 235)   // 3 双机：蓝
    };

    private static final int RED = Color.rgb(200, 40, 40);
    private static final int BLACK = Color.rgb(45, 45, 45);
    private static final int BLUE = Color.rgb(90, 150, 235);

    private final int currentMode;
    private final OnModeSelectedListener listener;

    public interface OnModeSelectedListener {
        void onModeSelected(int mode);
    }

    public ModePickerDialog(Context context, int currentMode, OnModeSelectedListener listener) {
        super(context, R.style.ModePickerBottomSheet);
        this.currentMode = currentMode;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_mode_picker);

        Window window = getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }

        int[] cardIds = {R.id.mode_card_0, R.id.mode_card_1, R.id.mode_card_2, R.id.mode_card_3};
        for (int i = 0; i < cardIds.length; i++) {
            LinearLayout card = findViewById(cardIds[i]);
            card.setTag(i);
            card.setOnClickListener(this);
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
                card.setBackgroundResource(R.drawable.bg_mode_card);
                name.setTextColor(THEME_COLORS[i]);
                check.setVisibility(View.GONE);
            }
        }

        findViewById(R.id.mode_cancel).setOnClickListener(v -> dismiss());
    }

    @Override
    public void onClick(View v) {
        Object tag = v.getTag();
        if (tag instanceof Integer) {
            int mode = (Integer) tag;
            if (listener != null) {
                listener.onModeSelected(mode);
            }
            dismiss();
        }
    }
}
