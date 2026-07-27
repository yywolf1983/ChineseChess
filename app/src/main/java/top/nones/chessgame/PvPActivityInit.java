package top.nones.chessgame;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import androidx.appcompat.app.AppCompatActivity;

import ChessMove.Rule;
import CustomView.ChessView;
import top.nones.chessgame.PvPActivityRound;
import Info.ChessInfo;
import Info.InfoSet;
import Info.SaveInfo;
import Info.Setting;
import Utils.LogUtils;

public class PvPActivityInit {
    private static final int MIN_CLICK_DELAY_TIME = 100;
    private static long curClickTime = 0L;
    private static long lastClickTime = 0L;
    private static MediaPlayer backMusic;
    private static MediaPlayer selectMusic;
    private static MediaPlayer clickMusic;
    private static MediaPlayer captureMusic;
    private static MediaPlayer checkMusic;
    private static MediaPlayer winMusic;
    private static Setting setting;
    
    private AppCompatActivity activity;
    private RelativeLayout relativeLayout;
    private ChessInfo chessInfo;
    private InfoSet infoSet;
    private ChessView chessView;
    private PvPActivityRound roundView;

    public PvPActivityInit(AppCompatActivity activity) {
        this.activity = activity;
    }

    public void initialize(Bundle savedInstanceState, RelativeLayout layout) {
        this.relativeLayout = layout;
        initChessInfo();
        initSetting();
        initMusic();
        initRoundView();
        initChessView();
        initButtonGroup();
        addFlipButton();
        // 菜单按钮在 initRoundView 中加入，会被后加入的 chessView 覆盖，这里提到顶层
        android.view.View bm = activity.findViewById(R.id.btn_menu);
        if (bm != null) bm.bringToFront();
    }

    private void initChessInfo() {
        // 总是使用新的游戏状态，不加载旧存档
        chessInfo = new ChessInfo();
        infoSet = new InfoSet();
        try {
            infoSet.pushInfo(chessInfo);
        } catch (CloneNotSupportedException e) {
            LogUtils.e("PvPActivityInit", "操作失败", e);
        }
    }

    private void initSetting() {
        if (setting == null) {
            setting = new Setting(activity.getSharedPreferences("setting", AppCompatActivity.MODE_PRIVATE));
        }
    }

    private void initMusic() {
        backMusic = MediaPlayer.create(activity, R.raw.background);
        if (backMusic != null) {
            backMusic.setLooping(true);
            backMusic.setVolume(0.2f, 0.2f);
        }
        selectMusic = MediaPlayer.create(activity, R.raw.select);
        if (selectMusic != null) {
            selectMusic.setVolume(5f, 5f);
        }
        clickMusic = MediaPlayer.create(activity, R.raw.click);
        if (clickMusic != null) {
            clickMusic.setVolume(5f, 5f);
        }
        captureMusic = MediaPlayer.create(activity, R.raw.capture);
        if (captureMusic != null) {
            captureMusic.setVolume(5f, 5f);
        }
        checkMusic = MediaPlayer.create(activity, R.raw.checkmate);
        if (checkMusic != null) {
            checkMusic.setVolume(5f, 5f);
        }
        winMusic = MediaPlayer.create(activity, R.raw.win);
        if (winMusic != null) {
            winMusic.setVolume(5f, 5f);
        }
    }

    private void initRoundView() {
        roundView = new PvPActivityRound(activity, chessInfo, 0);
        relativeLayout.addView(roundView);

        RelativeLayout.LayoutParams paramsRound = (RelativeLayout.LayoutParams) roundView.getLayoutParams();
        paramsRound.addRule(RelativeLayout.CENTER_IN_PARENT);
        paramsRound.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        paramsRound.setMargins(0, 0, 0, 0); // 贴屏幕顶部，不留空隙
        paramsRound.height = RelativeLayout.LayoutParams.WRAP_CONTENT; // 使用内部实测高度，避免硬凑150dp留白
        roundView.setLayoutParams(paramsRound);
        roundView.setId(R.id.roundView);

        // 顶部 round 区域放置一个小的「菜单」图标按钮（下拉菜单：新局/保存/加载/设置/模式切换）
        try {
            android.widget.ImageButton btnMenu = new android.widget.ImageButton(activity);
            btnMenu.setId(R.id.btn_menu);
            btnMenu.setImageResource(R.drawable.ic_menu);
            btnMenu.setBackground(null); // 完全透明
            btnMenu.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
            btnMenu.setContentDescription("菜单");
            // 左上角、贴近屏幕左缘；停留在 round 信息条顶部区域内（不进入棋盘）
            float density = activity.getResources().getDisplayMetrics().density;
            int menuSize = (int) (42 * density); // 按钮尺寸略放大，使图标更大
            android.widget.RelativeLayout.LayoutParams mp = new android.widget.RelativeLayout.LayoutParams(menuSize, menuSize);
            mp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
            mp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
            mp.setMargins((int) (4 * density), (int) (2 * density), 0, 0); // 再上移一点
            btnMenu.setLayoutParams(mp);
            relativeLayout.addView(btnMenu);
            } catch (Exception e) {
                LogUtils.e("PvPActivityInit", "添加菜单按钮失败: " + e.getMessage());
            }
    }

    private void initChessView() {
        chessView = new ChessView(activity, chessInfo);
        relativeLayout.addView(chessView);

        RelativeLayout.LayoutParams paramsChess = (RelativeLayout.LayoutParams) chessView.getLayoutParams();
        paramsChess.addRule(RelativeLayout.BELOW, R.id.roundView);
        paramsChess.width = RelativeLayout.LayoutParams.MATCH_PARENT;
        paramsChess.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
        paramsChess.setMargins(0, 0, 0, 0); // 棋盘上下不留空隙
        chessView.setLayoutParams(paramsChess);
        chessView.setId(R.id.chessView);
    }

    private void initButtonGroup() {
        LinearLayout buttonGroup = (LinearLayout) activity.getLayoutInflater().inflate(R.layout.button_group, relativeLayout, false);
        buttonGroup.setId(R.id.button_group);
        relativeLayout.addView(buttonGroup);

        RelativeLayout.LayoutParams paramsV = (RelativeLayout.LayoutParams) buttonGroup.getLayoutParams();
        paramsV.addRule(RelativeLayout.BELOW, R.id.chessView);
        paramsV.addRule(RelativeLayout.CENTER_HORIZONTAL);
        paramsV.width = RelativeLayout.LayoutParams.MATCH_PARENT;
        paramsV.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
        paramsV.setMargins(0, 0, 0, 0); // 按钮组贴棋盘，不留空隙
        buttonGroup.setLayoutParams(paramsV);
        
        // 设置按钮监听器
        setupButtonListeners(buttonGroup);

        // 顶部「菜单」按钮：下拉菜单包含 新局/保存/加载/设置/模式切换
        android.view.View btnMenu = activity.findViewById(R.id.btn_menu);
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(activity, v);
                android.view.Menu menu = popup.getMenu();
                menu.add(0, R.id.btn_retry, 0, "新局");
                menu.add(0, R.id.btn_load, 1, "加载");
                menu.add(0, R.id.btn_save, 2, "保存");
                menu.add(0, R.id.btn_settings, 3, "设置");
                popup.setOnMenuItemClickListener(item -> {
                    android.view.View target = buttonGroup.findViewById(item.getItemId());
                    if (target != null) {
                        target.performClick();
                    }
                    return true;
                });
                popup.show();
            });
        }
    }
    
    // 递归设置按钮监听器，处理嵌套布局
    private void setupButtonListeners(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof Button) {
                // 直接是Button
                Button btn = (Button) child;
                btn.setOnClickListener((View.OnClickListener) activity);
            } else if (child instanceof ViewGroup) {
                // 是ViewGroup，递归处理
                setupButtonListeners((ViewGroup) child);
            }
        }
    }

    // 添加翻转棋盘按钮（棋盘右下角外侧，半透明圆形）
    private void addFlipButton() {
        if (activity == null || chessView == null || relativeLayout == null) return;

        int sizeDp = 44;
        float density = activity.getResources().getDisplayMetrics().density;
        int sizePx = (int) (sizeDp * density + 0.5f);
        int paddingPx = (int) (10 * density + 0.5f);

        ImageView flipButton = new ImageView(activity);
        flipButton.setImageResource(R.drawable.ic_flip);
        flipButton.setBackgroundResource(R.drawable.bg_flip_button);
        flipButton.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        flipButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            flipButton.setElevation(4 * density);
        }
        flipButton.setOnClickListener(v -> {
            if (chessView != null) {
                chessView.toggleFlip();
            }
        });
        relativeLayout.addView(flipButton);

        RelativeLayout.LayoutParams flipParams = new RelativeLayout.LayoutParams(sizePx, sizePx);
        flipParams.addRule(RelativeLayout.ABOVE, R.id.button_group);
        flipParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        flipParams.setMargins(0, 0,
                (int) (12 * density + 0.5f),
                (int) (-18 * density + 0.5f));
        flipButton.setLayoutParams(flipParams);
    }

    // Getters
    public ChessInfo getChessInfo() {
        return chessInfo;
    }

    public InfoSet getInfoSet() {
        return infoSet;
    }

    public ChessView getChessView() {
        return chessView;
    }

    public PvPActivityRound getRoundView() {
        return roundView;
    }

    public static MediaPlayer getBackMusic() {
        return backMusic;
    }

    public static MediaPlayer getSelectMusic() {
        return selectMusic;
    }

    public static MediaPlayer getClickMusic() {
        return clickMusic;
    }

    public static MediaPlayer getCaptureMusic() {
        return captureMusic;
    }

    public static MediaPlayer getCheckMusic() {
        return checkMusic;
    }

    public static MediaPlayer getWinMusic() {
        return winMusic;
    }

    public static Setting getSetting() {
        return setting;
    }

    public static int getMinClickDelayTime() {
        return MIN_CLICK_DELAY_TIME;
    }

    public static long getCurClickTime() {
        return curClickTime;
    }

    public static void setCurClickTime(long curClickTime) {
        PvPActivityInit.curClickTime = curClickTime;
    }

    public static long getLastClickTime() {
        return lastClickTime;
    }

    public static void setLastClickTime(long lastClickTime) {
        PvPActivityInit.lastClickTime = lastClickTime;
    }

    public void onStart() {
        playMusic(backMusic);
    }

    public void onPause() {
        stopMusic(backMusic);
    }

    public void onStop() {
        try {
            SaveInfo.SerializeChessInfo(chessInfo, "ChessInfo_pvp.bin");
            SaveInfo.SerializeInfoSet(infoSet, "InfoSet_pvp.bin");
        } catch (Exception e) {
            LogUtils.e("PvPActivityInit", "操作失败", e);
        }
    }

    private void playMusic(MediaPlayer mediaPlayer) {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.start();
            } catch (Exception e) {
                LogUtils.e("PvPActivityInit", "操作失败", e);
            }
        }
    }

    private void stopMusic(MediaPlayer mediaPlayer) {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
                mediaPlayer.seekTo(0);
            } catch (Exception e) {
                LogUtils.e("PvPActivityInit", "操作失败", e);
            }
        }
    }

    public static void playEffect(MediaPlayer mediaPlayer) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(0);
                mediaPlayer.start();
            } catch (Exception e) {
                LogUtils.e("PvPActivityInit", "操作失败", e);
            }
        }
    }
}
