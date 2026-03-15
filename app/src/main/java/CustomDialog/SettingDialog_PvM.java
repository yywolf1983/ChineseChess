package CustomDialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.IdRes;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import top.nones.chessgame.PvMActivity;
import static top.nones.chessgame.PvMActivity.selectMusic;
import top.nones.chessgame.R;

/**
 * Created by 77304 on 2021/4/14.
 */

public class SettingDialog_PvM extends Dialog implements RadioGroup.OnCheckedChangeListener, SeekBar.OnSeekBarChangeListener, View.OnClickListener {
    // 添加playEffect方法
    private void playEffect(android.media.MediaPlayer mediaPlayer) {
        if (mediaPlayer != null && PvMActivity.setting != null && PvMActivity.setting.isEffectPlay) {
            try {
                mediaPlayer.seekTo(0);
                mediaPlayer.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public Button posBtn, negBtn;
    public RadioGroup musicGroup;
    public RadioGroup effectGroup;
    public LinearLayout levelGroup;
    public RadioButton musicTrue, musicFalse;
    public RadioButton effectTrue, effectFalse;
    public SeekBar timeSeekBar;
    public TextView timeValue;

    public boolean isMusicPlay, isEffectPlay;
    public int thinkingTime; // 思考时间（秒）
    public int searchDepth; // 搜索深度
    public int skillLevel; // 技能级别（1-20）
    public int multiPV; // 多主变搜索（1-5）

    public SettingDialog_PvM(Context context) {
        super(context, R.style.CustomDialog);

        // 添加空值检查，防止崩溃
        if (PvMActivity.setting != null) {
            isMusicPlay = PvMActivity.setting.isMusicPlay;
            isEffectPlay = PvMActivity.setting.isEffectPlay;
            // 将原来的mLevel转换为思考时间，1-3级对应3-7秒
            thinkingTime = PvMActivity.setting.mLevel * 2 + 1;
            searchDepth = PvMActivity.setting.depth;
            skillLevel = PvMActivity.setting.skillLevel;
            multiPV = PvMActivity.setting.multiPV;
        } else {
            // 设置默认值
            isMusicPlay = true;
            isEffectPlay = true;
            thinkingTime = 5; // 默认5秒
            searchDepth = 10; // 默认搜索深度10
            skillLevel = 20; // 默认最高技能级别
            multiPV = 1; // 默认单主变
        }
    }

    public SeekBar depthSeekBar;
    public TextView depthValue;
    public SeekBar skillLevelSeekBar;
    public TextView skillLevelValue;
    public SeekBar multiPVSeekBar;
    public TextView multiPVValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_setting_pvm);
        setCanceledOnTouchOutside(false);
        initView();
        initEvent();
        if (isMusicPlay) {
            musicTrue.setChecked(true);
        } else {
            musicFalse.setChecked(true);
        }
        if (isEffectPlay) {
            effectTrue.setChecked(true);
        } else {
            effectFalse.setChecked(true);
        }
        // 设置思考时间滑块
        timeSeekBar.setProgress(thinkingTime);
        timeValue.setText(thinkingTime + "秒");
        // 设置搜索深度滑块
        depthSeekBar.setProgress(searchDepth);
        depthValue.setText(searchDepth + "层");
        // 设置技能级别滑块
        skillLevelSeekBar.setProgress(skillLevel);
        skillLevelValue.setText(skillLevel + "级");
        // 设置MultiPV滑块
        multiPVSeekBar.setProgress(multiPV - 1); // 因为SeekBar从0开始，所以减1
        multiPVValue.setText(multiPV + "变");
        musicGroup.setOnCheckedChangeListener(this);
        effectGroup.setOnCheckedChangeListener(this);
        timeSeekBar.setOnSeekBarChangeListener(this);
        depthSeekBar.setOnSeekBarChangeListener(this);
        skillLevelSeekBar.setOnSeekBarChangeListener(this);
        multiPVSeekBar.setOnSeekBarChangeListener(this);
    }

    private void initView() {
        posBtn = (Button) findViewById(R.id.posBtn);
        negBtn = (Button) findViewById(R.id.negBtn);

        musicGroup = (RadioGroup) findViewById(R.id.musicGroup);
        musicTrue = (RadioButton) findViewById(R.id.musicTrue);
        musicFalse = (RadioButton) findViewById(R.id.musicFalse);

        effectGroup = (RadioGroup) findViewById(R.id.effectGroup);
        effectTrue = (RadioButton) findViewById(R.id.effectTrue);
        effectFalse = (RadioButton) findViewById(R.id.effectFalse);

        levelGroup = (LinearLayout) findViewById(R.id.levelGroup);
        timeSeekBar = (SeekBar) findViewById(R.id.timeSeekBar);
        timeValue = (TextView) findViewById(R.id.timeValue);
        
        // 初始化搜索深度滑块
        depthSeekBar = (SeekBar) findViewById(R.id.depthSeekBar);
        depthValue = (TextView) findViewById(R.id.depthValue);
        // 初始化技能级别滑块
        skillLevelSeekBar = (SeekBar) findViewById(R.id.skillLevelSeekBar);
        skillLevelValue = (TextView) findViewById(R.id.skillLevelValue);
        // 初始化MultiPV滑块
        multiPVSeekBar = (SeekBar) findViewById(R.id.multiPVSeekBar);
        multiPVValue = (TextView) findViewById(R.id.multiPVValue);
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
            // 保存设置到PvMActivity.setting
            if (PvMActivity.setting != null) {
                PvMActivity.setting.isMusicPlay = isMusicPlay;
                PvMActivity.setting.isEffectPlay = isEffectPlay;
                // 将思考时间转换回mLevel（1-3级）
                PvMActivity.setting.mLevel = (thinkingTime - 1) / 2;
                PvMActivity.setting.depth = searchDepth;
                PvMActivity.setting.skillLevel = skillLevel;
                PvMActivity.setting.multiPV = multiPV;
                // 立即生效：更新音乐播放状态
                if (isMusicPlay && PvMActivity.backMusic != null && !PvMActivity.backMusic.isPlaying()) {
                    PvMActivity.backMusic.start();
                } else if (!isMusicPlay && PvMActivity.backMusic != null && PvMActivity.backMusic.isPlaying()) {
                    PvMActivity.backMusic.pause();
                }
                // 保存设置到SharedPreferences
                PvMActivity.setting.saveSetting(((android.content.ContextWrapper)getContext()).getSharedPreferences("setting", android.content.Context.MODE_PRIVATE));
            }
            if (onClickBottomListener != null) {
                onClickBottomListener.onPositiveClick();
            }
        } else if (id == R.id.negBtn) {
            if (onClickBottomListener != null) {
                onClickBottomListener.onNegtiveClick();
            }
        }
    }

    public SettingDialog_PvM.OnClickBottomListener onClickBottomListener;

    public SettingDialog_PvM setOnClickBottomListener(SettingDialog_PvM.OnClickBottomListener onClickBottomListener) {
        this.onClickBottomListener = onClickBottomListener;
        return this;
    }

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, @IdRes int i) {
        playEffect(selectMusic);
        RadioButton checked = (RadioButton) findViewById(radioGroup.getCheckedRadioButtonId());
        int id = radioGroup.getId();
        if (id == R.id.musicGroup) {
            if (checked.getId() == R.id.musicTrue) {
                isMusicPlay = true;
            } else {
                isMusicPlay = false;
            }
        } else if (id == R.id.effectGroup) {
            if (checked.getId() == R.id.effectTrue) {
                isEffectPlay = true;
            } else {
                isEffectPlay = false;
            }
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            playEffect(selectMusic);
            if (seekBar == timeSeekBar) {
                thinkingTime = progress;
                timeValue.setText(thinkingTime + "秒");
            } else if (seekBar == depthSeekBar) {
                // 确保搜索深度在5-35之间
                searchDepth = Math.max(5, Math.min(35, progress));
                depthValue.setText(searchDepth + "层");
            } else if (seekBar == skillLevelSeekBar) {
                // 确保技能级别在1-20之间
                skillLevel = Math.max(1, Math.min(20, progress));
                skillLevelValue.setText(skillLevel + "级");
            } else if (seekBar == multiPVSeekBar) {
                // 确保MultiPV在1-5之间（因为SeekBar从0开始，所以加1）
                multiPV = Math.max(1, Math.min(5, progress + 1));
                multiPVValue.setText(multiPV + "变");
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // 开始拖动时的处理
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // 停止拖动时的处理
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