package top.nones.chessgame;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import top.nones.chessgame.R;

/**
 * 模式选择底部弹窗：图标卡片形式展示四种对战模式。
 * 行为与原 AlertDialog 选择保持一致（切换不清盘），仅统一 UI。
 */
public class ModePickerDialog extends Dialog implements View.OnClickListener {
    private static final String[] MODE_NAMES = {
            "双人对战",
            "人机对战(玩家红)",
            "人机对战(玩家黑)",
            "双机对战"
    };

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
            ImageView check = card.findViewById(R.id.mode_card_check);
            check.setVisibility(i == currentMode ? View.VISIBLE : View.GONE);
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
