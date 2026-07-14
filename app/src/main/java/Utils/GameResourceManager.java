package Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;

import Info.Setting;
import top.nones.chessgame.R;

public class GameResourceManager {

    private static GameResourceManager instance;

    private Setting setting;
    private MediaPlayer backMusic;
    private MediaPlayer selectMusic;
    private MediaPlayer clickMusic;
    private MediaPlayer captureMusic;
    private MediaPlayer checkMusic;
    private MediaPlayer winMusic;
    private SharedPreferences sharedPreferences;

    private GameResourceManager() {
    }

    public static synchronized GameResourceManager getInstance() {
        if (instance == null) {
            instance = new GameResourceManager();
        }
        return instance;
    }

    public void initSettings(Context context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.getSharedPreferences("setting", Context.MODE_PRIVATE);
        }
        if (setting == null) {
            setting = new Setting(sharedPreferences);
        }
    }

    public void initSoundEffects(Context context) {
        if (selectMusic == null) {
            selectMusic = MediaPlayer.create(context, R.raw.select);
            if (selectMusic != null) {
                selectMusic.setVolume(5f, 5f);
            }
        }
        if (clickMusic == null) {
            clickMusic = MediaPlayer.create(context, R.raw.click);
            if (clickMusic != null) {
                clickMusic.setVolume(5f, 5f);
            }
        }
        if (captureMusic == null) {
            captureMusic = MediaPlayer.create(context, R.raw.capture);
            if (captureMusic != null) {
                captureMusic.setVolume(5f, 5f);
            }
        }
        if (checkMusic == null) {
            checkMusic = MediaPlayer.create(context, R.raw.checkmate);
            if (checkMusic != null) {
                checkMusic.setVolume(5f, 5f);
            }
        }
        if (winMusic == null) {
            winMusic = MediaPlayer.create(context, R.raw.win);
        }
    }

    public void initBackgroundMusic(Context context) {
        if (backMusic == null) {
            backMusic = MediaPlayer.create(context, R.raw.background);
            if (backMusic != null) {
                backMusic.setLooping(true);
            }
        }
    }
    
    public void resetBackMusic() {
        if (backMusic != null) {
            try {
                backMusic.release();
            } catch (Exception ignored) {}
            backMusic = null;
        }
    }

    public Setting getSetting() {
        return setting;
    }

    public void setSetting(Setting setting) {
        this.setting = setting;
    }

    public MediaPlayer getBackMusic() {
        return backMusic;
    }

    public MediaPlayer getSelectMusic() {
        return selectMusic;
    }

    public MediaPlayer getClickMusic() {
        return clickMusic;
    }

    public MediaPlayer getCaptureMusic() {
        return captureMusic;
    }

    public MediaPlayer getCheckMusic() {
        return checkMusic;
    }

    public MediaPlayer getWinMusic() {
        return winMusic;
    }

    public SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    public void release() {
        if (backMusic != null) {
            backMusic.release();
            backMusic = null;
        }
        if (selectMusic != null) {
            selectMusic.release();
            selectMusic = null;
        }
        if (clickMusic != null) {
            clickMusic.release();
            clickMusic = null;
        }
        if (captureMusic != null) {
            captureMusic.release();
            captureMusic = null;
        }
        if (checkMusic != null) {
            checkMusic.release();
            checkMusic = null;
        }
        if (winMusic != null) {
            winMusic.release();
            winMusic = null;
        }
        setting = null;
        sharedPreferences = null;
    }
}