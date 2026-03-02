package CustomDialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import top.nones.chessgame.R;

/**
 * Created by 77304 on 2021/4/19.
 */

public class CommonDialog extends Dialog implements View.OnClickListener {
    public Button posBtn, negBtn;
    public TextView tv_title, tv_content;
    public String title, content;

    public CommonDialog(Context context, String title, String content) {
        super(context, R.style.CustomDialog);
        this.title = title;
        this.content = content;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_common);
        setCanceledOnTouchOutside(false);
        initView();
        initEvent();
    }

    private void initView() {
        posBtn = (Button) findViewById(R.id.posBtn);
        negBtn = (Button) findViewById(R.id.negBtn);

        tv_title = (TextView) findViewById(R.id.tv_title);
        tv_title.setText(title != null ? title : "");
        tv_content = (TextView) findViewById(R.id.tv_content);
        tv_content.setText(content != null ? content : "");

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

    public CommonDialog.OnClickBottomListener onClickBottomListener;

    public CommonDialog setOnClickBottomListener(CommonDialog.OnClickBottomListener onClickBottomListener) {
        this.onClickBottomListener = onClickBottomListener;
        return this;
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
