package CustomDialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.IdRes;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import top.nones.chessgame.PvMActivity;
import top.nones.chessgame.R;
import Utils.LogUtils;
import Utils.SoundManager;

/**
 * Created by 77304 on 2021/4/14.
 */

public class SettingDialog_PvM extends Dialog implements RadioGroup.OnCheckedChangeListener, SeekBar.OnSeekBarChangeListener, View.OnClickListener {
    public Button posBtn, negBtn;
    public Button timeMinusBtn, timePlusBtn;
    public Button depthMinusBtn, depthPlusBtn;
    public Button skillLevelMinusBtn, skillLevelPlusBtn;
    public Button multiPVMinusBtn, multiPVPlusBtn;
    public RadioGroup musicGroup;
    public RadioGroup effectGroup;
    public RadioGroup forceVariationGroup;
    public LinearLayout levelGroup;
    public RadioButton musicTrue, musicFalse;
    public RadioButton effectTrue, effectFalse;
    public RadioButton forceVariationTrue, forceVariationFalse;
    public SeekBar timeSeekBar;
    public TextView timeValue;

    public boolean isMusicPlay, isEffectPlay;
    public int thinkingTime; // 思考时间（秒）
    public int searchDepth; // 搜索深度
    public int skillLevel; // 技能级别（1-20）
    public int multiPV; // 多主变搜索（1-5）
    public boolean forceVariation; // 是否开启强制变着

    private void playSelectSound() {
        PvMActivity activity = PvMActivity.getInstance();
        if (activity != null) {
            SoundManager.playEffect(activity.selectMusic);
        }
    }
    
    public SettingDialog_PvM(Context context) {
        super(context, R.style.CustomDialog);

        PvMActivity activity = PvMActivity.getInstance();
        if (activity != null && activity.setting != null) {
            isMusicPlay = activity.setting.isMusicPlay;
            isEffectPlay = activity.setting.isEffectPlay;
            thinkingTime = activity.setting.mLevel;
            searchDepth = activity.setting.depth;
            skillLevel = activity.setting.skillLevel;
            multiPV = activity.setting.multiPV;
            forceVariation = activity.setting.forceVariation;
        } else {
            isMusicPlay = true;
            isEffectPlay = true;
            thinkingTime = 3;    // 与 Info.Setting 默认值一致
            searchDepth = 10;    // 与 Info.Setting 默认值一致
            skillLevel = 20;     // 与 Info.Setting 默认值一致
            multiPV = 1;         // 与 Info.Setting 默认值一致
            forceVariation = true;
        }
        
        // 确保所有参数在合理范围内
        thinkingTime = Math.max(1, Math.min(60, thinkingTime));
        searchDepth = Math.max(5, Math.min(120, searchDepth));
        skillLevel = Math.max(1, Math.min(20, skillLevel));
        multiPV = Math.max(1, Math.min(5, multiPV));
    }

    public SeekBar depthSeekBar;
    public TextView depthValue;
    public SeekBar skillLevelSeekBar;
    public TextView skillLevelValue;
    public SeekBar multiPVSeekBar;
    public TextView multiPVValue;
    public TextView multiPVWarning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_setting_pvm);
        setCanceledOnTouchOutside(false);

        // 设置对话框宽度为屏幕90%，确保够宽
        Window window = getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.9);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

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
        // 设置MultiPV滑块（1-5）
        multiPVSeekBar.setProgress(multiPV);
        multiPVValue.setText(multiPV + "变");
        // 设置强制变着选项
        if (forceVariation) {
            forceVariationTrue.setChecked(true);
        } else {
            forceVariationFalse.setChecked(true);
        }
        musicGroup.setOnCheckedChangeListener(this);
        effectGroup.setOnCheckedChangeListener(this);
        forceVariationGroup.setOnCheckedChangeListener(this);
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
        timeMinusBtn = (Button) findViewById(R.id.timeMinusBtn);
        timePlusBtn = (Button) findViewById(R.id.timePlusBtn);
        
        // 初始化搜索深度滑块
        depthSeekBar = (SeekBar) findViewById(R.id.depthSeekBar);
        depthValue = (TextView) findViewById(R.id.depthValue);
        depthMinusBtn = (Button) findViewById(R.id.depthMinusBtn);
        depthPlusBtn = (Button) findViewById(R.id.depthPlusBtn);
        // 初始化技能级别滑块
        skillLevelSeekBar = (SeekBar) findViewById(R.id.skillLevelSeekBar);
        skillLevelValue = (TextView) findViewById(R.id.skillLevelValue);
        skillLevelMinusBtn = (Button) findViewById(R.id.skillLevelMinusBtn);
        skillLevelPlusBtn = (Button) findViewById(R.id.skillLevelPlusBtn);
        // 初始化MultiPV滑块
        multiPVSeekBar = (SeekBar) findViewById(R.id.multiPVSeekBar);
        multiPVValue = (TextView) findViewById(R.id.multiPVValue);
        multiPVMinusBtn = (Button) findViewById(R.id.multiPVMinusBtn);
        multiPVPlusBtn = (Button) findViewById(R.id.multiPVPlusBtn);
        multiPVWarning = (TextView) findViewById(R.id.multiPVWarning);
        // 初始化强制变着选项
        forceVariationGroup = (RadioGroup) findViewById(R.id.forceVariationGroup);
        forceVariationTrue = (RadioButton) findViewById(R.id.forceVariationTrue);
        forceVariationFalse = (RadioButton) findViewById(R.id.forceVariationFalse);
    }


    private void initEvent() {
        //设置确定按钮被点击后，向外界提供监听
        posBtn.setOnClickListener(this);
        //设置取消按钮被点击后，向外界提供监听
        negBtn.setOnClickListener(this);
        
        // 为思考时间按钮添加点击事件
        timeMinusBtn.setOnClickListener(this);
        timePlusBtn.setOnClickListener(this);
        // 为搜索深度按钮添加点击事件
        depthMinusBtn.setOnClickListener(this);
        depthPlusBtn.setOnClickListener(this);
        // 为技能级别按钮添加点击事件
        skillLevelMinusBtn.setOnClickListener(this);
        skillLevelPlusBtn.setOnClickListener(this);
        // 为MultiPV按钮添加点击事件
        multiPVMinusBtn.setOnClickListener(this);
        multiPVPlusBtn.setOnClickListener(this);
    }

    @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.posBtn) {
                // 当思考时间超过15秒时，显示提示弹窗
                if (thinkingTime > 15) {
                    new android.app.AlertDialog.Builder(getContext())
                        .setTitle("提示")
                        .setMessage("时间过长可能造成手机卡死，请斟酌手机性能设置")
                        .setPositiveButton("设置到10秒以下", (dialog, which) -> {
                            // 设置思考时间为10秒
                            thinkingTime = 10;
                            timeSeekBar.setProgress(10);
                            timeValue.setText("10秒");
                            // 保存设置
                            saveSettings();
                            if (onClickBottomListener != null) {
                                onClickBottomListener.onPositiveClick();
                            }
                            dismiss();
                        })
                        .setNegativeButton("确认设置", (dialog, which) -> {
                            // 确认保存设置
                            saveSettings();
                            if (onClickBottomListener != null) {
                                onClickBottomListener.onPositiveClick();
                            }
                            dismiss();
                        })
                        .show();
                } else {
                    // 直接保存设置
                    saveSettings();
                    if (onClickBottomListener != null) {
                        onClickBottomListener.onPositiveClick();
                    }
                }
            } else if (id == R.id.negBtn) {
                if (onClickBottomListener != null) {
                    onClickBottomListener.onNegtiveClick();
                }
            } else if (id == R.id.timeMinusBtn) {
                // 减少思考时间
                playSelectSound();
                if (thinkingTime > 1) {
                    thinkingTime--;
                    timeSeekBar.setProgress(thinkingTime);
                    timeValue.setText(thinkingTime + "秒");
                }
            } else if (id == R.id.timePlusBtn) {
                // 增加思考时间
                playSelectSound();
                if (thinkingTime < 60) {
                    thinkingTime++;
                    timeSeekBar.setProgress(thinkingTime);
                    timeValue.setText(thinkingTime + "秒");
                }
            } else if (id == R.id.depthMinusBtn) {
                // 减少搜索深度
                playSelectSound();
                if (searchDepth > 5) {
                    searchDepth--;
                    depthSeekBar.setProgress(searchDepth);
                    depthValue.setText(searchDepth + "层");
                }
            } else if (id == R.id.depthPlusBtn) {
                // 增加搜索深度
                playSelectSound();
                if (searchDepth < 120) {
                    searchDepth++;
                    depthSeekBar.setProgress(searchDepth);
                    depthValue.setText(searchDepth + "层");
                }
            } else if (id == R.id.skillLevelMinusBtn) {
                // 减少技能级别
                playSelectSound();
                if (skillLevel > 1) {
                    skillLevel--;
                    skillLevelSeekBar.setProgress(skillLevel);
                    skillLevelValue.setText(skillLevel + "级");
                }
            } else if (id == R.id.skillLevelPlusBtn) {
                // 增加技能级别
                playSelectSound();
                if (skillLevel < 20) {
                    skillLevel++;
                    skillLevelSeekBar.setProgress(skillLevel);
                    skillLevelValue.setText(skillLevel + "级");
                }
            } else if (id == R.id.multiPVMinusBtn) {
                // 减少MultiPV
                playSelectSound();
                if (multiPV > 1) {
                    multiPV--;
                    multiPVSeekBar.setProgress(multiPV);
                    multiPVValue.setText(multiPV + "变");
                }
            } else if (id == R.id.multiPVPlusBtn) {
                // 增加MultiPV
                playSelectSound();
                if (multiPV < 5) {
                    multiPV++;
                    multiPVSeekBar.setProgress(multiPV);
                    multiPVValue.setText(multiPV + "变");
                    showMultiPVChangeHint();
                }
            }
        }
        
        // 保存设置的方法
        private void saveSettings() {
            PvMActivity activity = PvMActivity.getInstance();
            if (activity != null && activity.setting != null) {
                activity.setting.isMusicPlay = isMusicPlay;
                activity.setting.isEffectPlay = isEffectPlay;
                activity.setting.mLevel = thinkingTime;
                activity.setting.depth = searchDepth;
                activity.setting.skillLevel = skillLevel;
                activity.setting.multiPV = multiPV;
                activity.setting.forceVariation = forceVariation;
                
                if (isMusicPlay && activity.backMusic != null && !activity.backMusic.isPlaying()) {
                    activity.backMusic.start();
                } else if (!isMusicPlay && activity.backMusic != null && activity.backMusic.isPlaying()) {
                    activity.backMusic.pause();
                }
                
                activity.setting.saveSetting(((android.content.ContextWrapper)getContext()).getSharedPreferences("setting", android.content.Context.MODE_PRIVATE));
                
                try {
                    if (activity.chessInfo != null) {
                        activity.chessInfo.setting = activity.setting;
                    }
                    // updateSettings 已移到 AIThreadRunnable 后台执行，无需在此同步调用（避免主线程 ANR）
                    // 新设置已持久化，下次 AI 走棋时会自动读取
                } catch (Exception e) {
                    LogUtils.e("SettingDialog_PvM", "更新设置失败: " + e.getMessage());
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
        playSelectSound();
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
        } else if (id == R.id.forceVariationGroup) {
            if (checked.getId() == R.id.forceVariationTrue) {
                forceVariation = true;
            } else {
                forceVariation = false;
            }
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            playSelectSound();
            if (seekBar == timeSeekBar) {
                // 确保思考时间在1-60秒之间
                thinkingTime = Math.max(1, Math.min(60, progress));
                timeValue.setText(thinkingTime + "秒");
            } else if (seekBar == depthSeekBar) {
                // 确保搜索深度在5-120之间
                searchDepth = Math.max(5, Math.min(120, progress));
                depthValue.setText(searchDepth + "层");
            } else if (seekBar == skillLevelSeekBar) {
                // 确保技能级别在1-20之间
                skillLevel = Math.max(1, Math.min(20, progress));
                skillLevelValue.setText(skillLevel + "级");
            } else if (seekBar == multiPVSeekBar) {
                // 确保MultiPV在1-5之间
                int newMultiPV = Math.max(1, Math.min(5, progress));
                if (newMultiPV != multiPV) {
                    multiPV = newMultiPV;
                    multiPVValue.setText(multiPV + "变");
                }
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // 开始拖动时的处理
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (seekBar == multiPVSeekBar) {
            showMultiPVChangeHint();
        }
    }

    private void showMultiPVChangeHint() {
        // multiPV=1 无额外开销，不提示；multiPV>=2 时每条变线需要额外搜索时间
        if (multiPV <= 1) {
            return;
        }
        int suggestTime = (multiPV - 1) * 3;
        String message = "MultiPV=" + multiPV + "，建议思考时间至少" + suggestTime + "秒";
        android.widget.Toast toast = android.widget.Toast.makeText(getContext(), message, android.widget.Toast.LENGTH_LONG);
        toast.setGravity(android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL, 0, 150);
        toast.show();
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