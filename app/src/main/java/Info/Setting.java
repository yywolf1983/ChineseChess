package Info;

import android.content.SharedPreferences;
import java.io.Serializable;

public class Setting implements Serializable {
    public boolean isMusicPlay;
    public boolean isEffectPlay;
    public int mLevel;
    public int depth;
    public int skillLevel;
    public int multiPV;

    public Setting(SharedPreferences sharedPreferences) {
        isMusicPlay = sharedPreferences.getBoolean("isMusicPlay", true);
        isEffectPlay = sharedPreferences.getBoolean("isEffectPlay", true);
        mLevel = sharedPreferences.getInt("mLevel", 3);
        depth = sharedPreferences.getInt("depth", 10);
        skillLevel = sharedPreferences.getInt("skillLevel", 20);
        multiPV = sharedPreferences.getInt("multiPV", 1);
    }

    public void saveSetting(SharedPreferences sharedPreferences) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isMusicPlay", isMusicPlay);
        editor.putBoolean("isEffectPlay", isEffectPlay);
        editor.putInt("mLevel", mLevel);
        editor.putInt("depth", depth);
        editor.putInt("skillLevel", skillLevel);
        editor.putInt("multiPV", multiPV);
        editor.apply();
    }
}