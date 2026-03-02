package CustomDialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.IdRes;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import top.nones.chessgame.R;

import static top.nones.chessgame.PvMActivity.playEffect;
import static top.nones.chessgame.PvMActivity.selectMusic;

/**
 * Created by 77304 on 2021/4/19.
 */

public class RetryDialog extends Dialog implements RadioGroup.OnCheckedChangeListener, View.OnClickListener {
    public Button posBtn, negBtn;
    public RadioGroup holdGroup;
    public RadioButton holdRed, holdBlack;

    public boolean isPlayerRed;

    public RetryDialog(Context context) {
        super(context, R.style.CustomDialog);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_retry);
        setCanceledOnTouchOutside(false);
        initView();
        initEvent();
        isPlayerRed = true;
        holdRed.setChecked(true);
        holdGroup.setOnCheckedChangeListener(this);
    }

    private void initView() {
        posBtn = (Button) findViewById(R.id.posBtn);
        negBtn = (Button) findViewById(R.id.negBtn);

        holdGroup = (RadioGroup) findViewById(R.id.holdGroup);
        holdRed = (RadioButton) findViewById(R.id.holdRed);
        holdBlack = (RadioButton) findViewById(R.id.holdBlack);
    }


    private void initEvent() {
        //设置确定按钮被点击后，向外界提供监听
        posBtn.setOnClickListener(this);
        //设置取消按钮被点击后，向外界提供监听
        negBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.posBtn) {
            if (onClickBottomListener != null) {
                onClickBottomListener.onPositiveClick();
            }
        } else if (id == R.id.negBtn) {
            if (onClickBottomListener != null) {
                onClickBottomListener.onNegtiveClick();
            }
        }
    }

    public RetryDialog.OnClickBottomListener onClickBottomListener;

    public RetryDialog setOnClickBottomListener(RetryDialog.OnClickBottomListener onClickBottomListener) {
        this.onClickBottomListener = onClickBottomListener;
        return this;
    }

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, @IdRes int i) {
        playEffect(selectMusic);
        RadioButton checked = (RadioButton) findViewById(radioGroup.getCheckedRadioButtonId());
        int id = radioGroup.getId();
        if (id == R.id.holdGroup) {
            if (checked.getId() == R.id.holdRed) {
                isPlayerRed = true;
            } else {
                isPlayerRed = false;
            }
        }
    }

    public interface OnClickBottomListener {
        /**
         * 点击确定按钮事件
         */
        public void onPositiveClick();

        /**
         * 点击取消按钮事件
         */
        public void onNegtiveClick();
    }
}
