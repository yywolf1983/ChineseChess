package top.nones.chessgame;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import Info.Setting;

public class SettingsActivity extends AppCompatActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {
    private CheckBox cbMusic;
    private CheckBox cbEffect;
    private Button btnSave;
    private Button btnCancel;
    private SeekBar sbDepth;
    private TextView tvDepth;

    private Setting setting;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 初始化控件
        cbMusic = findViewById(R.id.cb_music);
        cbEffect = findViewById(R.id.cb_effect);
        btnSave = findViewById(R.id.btn_save);
        btnCancel = findViewById(R.id.btn_cancel);
        sbDepth = findViewById(R.id.sb_depth);
        tvDepth = findViewById(R.id.tv_depth);

        // 初始化设置
        sharedPreferences = getSharedPreferences("setting", MODE_PRIVATE);
        setting = new Setting(sharedPreferences);

        // 设置复选框状态
        if (cbMusic != null) {
            cbMusic.setChecked(setting.isMusicPlay);
        }
        if (cbEffect != null) {
            cbEffect.setChecked(setting.isEffectPlay);
        }
        
        // 设置深度滑块
        if (sbDepth != null) {
            sbDepth.setMax(45);
            int depth = setting.depth;
            if (depth < 5) {
                depth = 5;
            } else if (depth > 45) {
                depth = 45;
            }
            sbDepth.setProgress(depth);
            sbDepth.setOnSeekBarChangeListener(this);
        }
        
        // 设置深度文本
        if (tvDepth != null) {
            int depth = setting.depth;
            if (depth < 5) {
                depth = 5;
            } else if (depth > 45) {
                depth = 45;
            }
            tvDepth.setText("棋局深度: " + depth);
        }

        // 设置按钮监听器
        if (btnSave != null) {
            btnSave.setOnClickListener(this);
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        if (v == null) {
            return;
        }
        if (v.getId() == R.id.btn_save) {
            // 保存设置
            if (cbMusic != null) {
                setting.isMusicPlay = cbMusic.isChecked();
            }
            if (cbEffect != null) {
                setting.isEffectPlay = cbEffect.isChecked();
            }
            if (sbDepth != null) {
                int depth = sbDepth.getProgress();
                if (depth < 5) {
                    depth = 5;
                } else if (depth > 45) {
                    depth = 45;
                }
                setting.depth = depth;
            }
            setting.saveSetting(sharedPreferences);
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            finish();
        } else if (v.getId() == R.id.btn_cancel) {
            // 取消，直接返回
            finish();
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (tvDepth != null) {
            int depth = progress;
            if (depth < 5) {
                depth = 5;
                seekBar.setProgress(5);
            } else if (depth > 45) {
                depth = 45;
                seekBar.setProgress(45);
            }
            tvDepth.setText("棋局深度: " + depth);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }
}