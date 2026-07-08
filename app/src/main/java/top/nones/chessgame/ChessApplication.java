package top.nones.chessgame;

import android.app.Application;
import com.reggate.lib.RegGateConfig;

public class ChessApplication extends Application {
    @Override
    public void onCreate() {
        RegGateConfig.init(this)
                .mainActivity(PvMActivity.class)
                .build();
        super.onCreate();
    }
}