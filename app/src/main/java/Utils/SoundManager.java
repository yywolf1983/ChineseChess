package Utils;

import android.media.MediaPlayer;

import Info.Setting;

public class SoundManager {

    private SoundManager() {
    }

    public static void playEffect(MediaPlayer mediaPlayer) {
        if (mediaPlayer != null) {
            Setting setting = GameResourceManager.getInstance().getSetting();
            boolean shouldPlay = setting == null || setting.isEffectPlay;
            if (shouldPlay) {
                try {
                    mediaPlayer.seekTo(0);
                    mediaPlayer.start();
                } catch (Exception e) {
                    LogUtils.e("SoundManager", "播放音效失败", e);
                }
            }
        }
    }
}