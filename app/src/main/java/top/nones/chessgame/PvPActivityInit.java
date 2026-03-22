package top.nones.chessgame;

import android.media.MediaPlayer;
import android.os.Bundle;
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
    }

    private void initChessInfo() {
        // 总是使用新的游戏状态，不加载旧存档
        chessInfo = new ChessInfo();
        infoSet = new InfoSet();
        try {
            infoSet.pushInfo(chessInfo);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
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
        paramsRound.setMargins(30, 30, 30, 30);
        paramsRound.height = 150; // 设置固定高度
        roundView.setLayoutParams(paramsRound);
        roundView.setId(R.id.roundView);
    }

    private void initChessView() {
        chessView = new ChessView(activity, chessInfo);
        relativeLayout.addView(chessView);

        RelativeLayout.LayoutParams paramsChess = (RelativeLayout.LayoutParams) chessView.getLayoutParams();
        paramsChess.addRule(RelativeLayout.BELOW, R.id.roundView);
        chessView.setLayoutParams(paramsChess);
        chessView.setId(R.id.chessView);
    }

    private void initButtonGroup() {
        LinearLayout buttonGroup = (LinearLayout) activity.getLayoutInflater().inflate(R.layout.button_group, relativeLayout, false);
        relativeLayout.addView(buttonGroup);

        RelativeLayout.LayoutParams paramsV = (RelativeLayout.LayoutParams) buttonGroup.getLayoutParams();
        paramsV.addRule(RelativeLayout.BELOW, R.id.chessView);
        paramsV.addRule(RelativeLayout.CENTER_HORIZONTAL);
        paramsV.width = RelativeLayout.LayoutParams.MATCH_PARENT;
        paramsV.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
        paramsV.setMargins(30, 120, 30, 10);
        buttonGroup.setLayoutParams(paramsV);
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
            e.printStackTrace();
        }
    }

    private void playMusic(MediaPlayer mediaPlayer) {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void stopMusic(MediaPlayer mediaPlayer) {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
                mediaPlayer.seekTo(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void playEffect(MediaPlayer mediaPlayer) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(0);
                mediaPlayer.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
