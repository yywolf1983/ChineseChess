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
        // 使用 ApplicationContext，避免单例持有 Activity 导致内存泄漏
        Context appContext = context.getApplicationContext();
        if (selectMusic == null) {
            selectMusic = MediaPlayer.create(appContext, R.raw.select);
            if (selectMusic != null) {
                selectMusic.setVolume(1.0f, 1.0f);
            }
        }
        if (clickMusic == null) {
            clickMusic = MediaPlayer.create(appContext, R.raw.click);
            if (clickMusic != null) {
                clickMusic.setVolume(1.0f, 1.0f);
            }
        }
        if (captureMusic == null) {
            captureMusic = MediaPlayer.create(appContext, R.raw.capture);
            if (captureMusic != null) {
                captureMusic.setVolume(1.0f, 1.0f);
            }
        }
        if (checkMusic == null) {
            checkMusic = MediaPlayer.create(appContext, R.raw.checkmate);
            if (checkMusic != null) {
                checkMusic.setVolume(1.0f, 1.0f);
            }
        }
        if (winMusic == null) {
            winMusic = MediaPlayer.create(appContext, R.raw.win);
            if (winMusic != null) {
                winMusic.setVolume(1.0f, 1.0f);
            }
        }
    }

    public void initBackgroundMusic(Context context) {
        if (backMusic == null) {
            backMusic = MediaPlayer.create(context.getApplicationContext(), R.raw.background);
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
        try { if (backMusic != null) { backMusic.release(); backMusic = null; } } catch (Exception ignored) {}
        try { if (selectMusic != null) { selectMusic.release(); selectMusic = null; } } catch (Exception ignored) {}
        try { if (clickMusic != null) { clickMusic.release(); clickMusic = null; } } catch (Exception ignored) {}
        try { if (captureMusic != null) { captureMusic.release(); captureMusic = null; } } catch (Exception ignored) {}
        try { if (checkMusic != null) { checkMusic.release(); checkMusic = null; } } catch (Exception ignored) {}
        try { if (winMusic != null) { winMusic.release(); winMusic = null; } } catch (Exception ignored) {}
        setting = null;
        sharedPreferences = null;
    }
}