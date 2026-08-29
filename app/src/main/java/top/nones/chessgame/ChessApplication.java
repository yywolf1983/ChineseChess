package top.nones.chessgame;

import android.app.Application;
import com.reggate.lib.RegGateConfig;

import AICore.PikafishAI;

public class ChessApplication extends Application {
    @Override
    public void onCreate() {
        RegGateConfig.init(this)
                .mainActivity(PvMActivity.class)
                .build();
        super.onCreate();
        // 预热 AI 引擎: 后台加载 NNUE + 初始化, 与首屏并行, 进入 AI 对战时更快就绪。
        // 若担心低端机启动期内存/IO 占用, 可改为在首屏空闲或进入 AI 对局前再调用 preload()。
        PikafishAI.preload(this);
    }
}