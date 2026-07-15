package top.nones.chessgame;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import top.nones.chessgame.R;

/**
 * 保存棋谱信息弹窗：圆角卡片 + 图标标题 + 现代输入框 + 日期选择器。
 * 人机和双人模式共用，统一收集对局信息后通过回调返回。
 */
public class SaveNotationDialog extends Dialog {
    public interface OnSaveInfoConfirmed {
        void onConfirm(String fileName, String redPlayer, String blackPlayer,
                       String date, String location, String event, String round);
    }

    private final OnSaveInfoConfirmed listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

    public SaveNotationDialog(Context context, OnSaveInfoConfirmed listener) {
        super(context, R.style.CustomDialog);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_save_notation);

        Window window = getWindow();
        if (window != null) {
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }

        EditText fileNameEdit = findViewById(R.id.file_name_edit);
        EditText redEdit = findViewById(R.id.red_player_edit);
        EditText blackEdit = findViewById(R.id.black_player_edit);
        EditText dateEdit = findViewById(R.id.date_edit);
        EditText locationEdit = findViewById(R.id.location_edit);
        EditText eventEdit = findViewById(R.id.event_edit);
        EditText roundEdit = findViewById(R.id.round_edit);

        String today = dateFormat.format(new Date());
        dateEdit.setText(today);
        dateEdit.setOnClickListener(v -> showDatePicker(dateEdit));

        String defaultFileName = "对局_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        fileNameEdit.setText(defaultFileName);
        fileNameEdit.setSelection(fileNameEdit.getText().length());

        findViewById(R.id.save_cancel).setOnClickListener(v -> dismiss());
        Button confirm = findViewById(R.id.save_confirm);
        confirm.setOnClickListener(v -> {
            String fileName = fileNameEdit.getText().toString().trim();
            if (TextUtils.isEmpty(fileName)) {
                fileName = defaultFileName;
            }
            if (listener != null) {
                listener.onConfirm(fileName,
                        redEdit.getText().toString().trim(),
                        blackEdit.getText().toString().trim(),
                        dateEdit.getText().toString().trim(),
                        locationEdit.getText().toString().trim(),
                        eventEdit.getText().toString().trim(),
                        roundEdit.getText().toString().trim());
            }
            dismiss();
        });
    }

    private void showDatePicker(EditText dateEdit) {
        Calendar calendar = Calendar.getInstance();
        try {
            Date parsed = dateFormat.parse(dateEdit.getText().toString());
            if (parsed != null) {
                calendar.setTime(parsed);
            }
        } catch (ParseException ignored) {
        }
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog picker = new DatePickerDialog(getContext(),
                (view, y, m, d) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(y, m, d);
                    dateEdit.setText(dateFormat.format(selected.getTime()));
                }, year, month, day);
        picker.show();
    }
}
