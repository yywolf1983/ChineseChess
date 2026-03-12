package Info;

import android.content.SharedPreferences;
import java.io.Serializable;

public class Setting implements Serializable {
    public boolean isMusicPlay;
    public boolean isEffectPlay;
    public int mLevel;
    public int depth;

    public Setting(SharedPreferences sharedPreferences) {
        isMusicPlay = sharedPreferences.getBoolean("isMusicPlay", true);
        isEffectPlay = sharedPreferences.getBoolean("isEffectPlay", true);
        mLevel = sharedPreferences.getInt("mLevel", 3);
        depth = sharedPreferences.getInt("depth", 10);
    }

    public void saveSetting(SharedPreferences sharedPreferences) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isMusicPlay", isMusicPlay);
        editor.putBoolean("isEffectPlay", isEffectPlay);
        editor.putInt("mLevel", mLevel);
        editor.putInt("depth", depth);
        editor.apply();
    }
}