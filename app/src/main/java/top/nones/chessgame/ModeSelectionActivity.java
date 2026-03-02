package top.nones.chessgame;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 模式选择Activity
 */
public class ModeSelectionActivity extends Activity implements View.OnClickListener {
    // 游戏模式选项
    private static final String[] modeNames = {
        "双人对战",
        "人机对战（玩家红）",
        "人机对战（玩家黑）",
        "双机对战"
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置布局
        setContentView(R.layout.activity_mode_selection);
        
        // 获取当前模式
        int currentMode = getIntent().getIntExtra("currentMode", 0);
        
        // 设置当前模式显示
        TextView currentModeText = findViewById(R.id.current_mode_text);
        currentModeText.setText("当前模式: " + modeNames[currentMode]);
        
        // 设置模式按钮
        setupModeButtons();
        
        // 设置取消按钮
        Button cancelButton = findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(this);
    }
    
    private void setupModeButtons() {
        // 设置双人对战按钮
        Button mode0Button = findViewById(R.id.mode_0_button);
        mode0Button.setText(modeNames[0]);
        mode0Button.setOnClickListener(this);
        mode0Button.setTag(0);
        
        // 设置人机对战（玩家红）按钮
        Button mode1Button = findViewById(R.id.mode_1_button);
        mode1Button.setText(modeNames[1]);
        mode1Button.setOnClickListener(this);
        mode1Button.setTag(1);
        
        // 设置人机对战（玩家黑）按钮
        Button mode2Button = findViewById(R.id.mode_2_button);
        mode2Button.setText(modeNames[2]);
        mode2Button.setOnClickListener(this);
        mode2Button.setTag(2);
        
        // 设置双机对战按钮
        Button mode3Button = findViewById(R.id.mode_3_button);
        mode3Button.setText(modeNames[3]);
        mode3Button.setOnClickListener(this);
        mode3Button.setTag(3);
    }
    
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.cancel_button) {
            finish();
        } else {
            // 获取按钮标签，即模式值
            Integer mode = (Integer) v.getTag();
            if (mode != null) {
                selectMode(mode);
            }
        }
    }
    
    private void selectMode(int mode) {
        // 创建返回意图
        Intent resultIntent = new Intent();
        resultIntent.putExtra("selectedMode", mode);
        setResult(RESULT_OK, resultIntent);
        
        // 显示提示
        Toast.makeText(this, "已选择: " + modeNames[mode], Toast.LENGTH_SHORT).show();
        
        // 结束Activity
        finish();
    }
}
