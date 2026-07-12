package Info;

import android.content.SharedPreferences;
import java.io.Serializable;

public class Setting implements Serializable {
    // ---- 用户可见设置 ----
    public boolean isMusicPlay;
    public boolean isEffectPlay;
    public int mLevel;           // 思考时间（秒）
    public int depth;            // 搜索深度
    public int skillLevel;       // 技能级别 (1-20)
    public int multiPV;          // 多主变 (1-5)
    public int contempt;         // 蔑视值
    public boolean forceVariation;  // 强制变着

    // ---- 引擎内部参数 ----
    public int threads;          // 引擎线程数 (0=自动)
    public int hashMB;           // 哈希表大小 (MB, 0=自动)
    public String evalFile;      // NNUE 网络文件名
    public String numaPolicy;    // NUMA 策略

    public Setting(SharedPreferences sharedPreferences) {
        isMusicPlay = sharedPreferences.getBoolean("isMusicPlay", true);
        isEffectPlay = sharedPreferences.getBoolean("isEffectPlay", true);
        mLevel = Math.max(1, Math.min(60, sharedPreferences.getInt("mLevel", 3)));
        depth = Math.max(5, Math.min(120, sharedPreferences.getInt("depth", 10)));
        skillLevel = Math.max(1, Math.min(20, sharedPreferences.getInt("skillLevel", 20)));
        multiPV = Math.max(1, Math.min(5, sharedPreferences.getInt("multiPV", 1)));
        contempt = sharedPreferences.getInt("contempt", 20);
        forceVariation = sharedPreferences.getBoolean("forceVariation", true);
        threads = sharedPreferences.getInt("threads", 0);
        hashMB = sharedPreferences.getInt("hashMB", 0);
        evalFile = sharedPreferences.getString("evalFile", "pikafish.nnue");
        numaPolicy = sharedPreferences.getString("numaPolicy", "auto");
    }

    public void saveSetting(SharedPreferences sharedPreferences) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isMusicPlay", isMusicPlay);
        editor.putBoolean("isEffectPlay", isEffectPlay);
        editor.putInt("mLevel", mLevel);
        editor.putInt("depth", depth);
        editor.putInt("skillLevel", skillLevel);
        editor.putInt("multiPV", multiPV);
        editor.putInt("contempt", contempt);
        editor.putBoolean("forceVariation", forceVariation);
        editor.putInt("threads", threads);
        editor.putInt("hashMB", hashMB);
        if (numaPolicy != null) {
            editor.putString("numaPolicy", numaPolicy);
        }
        if (evalFile != null) {
            editor.putString("evalFile", evalFile);
        }
        editor.apply();
    }
}