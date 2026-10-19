package top.nones.chessgame;

import android.app.Application;
import com.reggate.lib.RegGateConfig;

import AICore.PikafishAI;

public class ChessApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        RegGateConfig.init(this)
                .mainActivity(PvMActivity.class)
                .build();
        // 预热 AI 引擎: 后台加载 NNUE + 初始化, 与首屏并行, 进入 AI 对战时更快就绪。
        // 放到后台线程避免阻塞主线程导致启动 ANR。
        new Thread(() -> PikafishAI.preload(this), "PikafishPreload").start();
    }
}