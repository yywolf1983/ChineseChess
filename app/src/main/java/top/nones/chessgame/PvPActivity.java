package top.nones.chessgame;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import CustomView.ChessView;
import Info.ChessInfo;
import Info.InfoSet;

public class PvPActivity extends AppCompatActivity implements View.OnTouchListener, View.OnClickListener {
    private RelativeLayout relativeLayout;
    private PvPActivityInit initModule;
    private PvPActivityGame gameModule;
    private PvPActivityControls controlsModule;
    private PvPActivityRound roundModule;
    private ChessView chessView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pvp);
        relativeLayout = (RelativeLayout) findViewById(R.id.relativeLayout);

        // 初始化模块
        initModule = new PvPActivityInit(this);
        initModule.initialize(savedInstanceState, relativeLayout);

        // 获取初始化的对象
        ChessInfo chessInfo = initModule.getChessInfo();
        InfoSet infoSet = initModule.getInfoSet();
        chessView = initModule.getChessView();

        // 初始化游戏模块
        gameModule = new PvPActivityGame(this, chessInfo, infoSet, chessView);

        // 初始化回合模块
        roundModule = new PvPActivityRound(this, chessInfo, 0);
        gameModule.setRoundView(roundModule);

        // 初始化控制模块
        controlsModule = new PvPActivityControls(this, chessInfo, infoSet, gameModule);

        // 设置触摸监听器
        chessView.setOnTouchListener((view, event) -> {
            // 先让ChessView处理触摸事件（用于摆棋窗口拖动和棋子点击）
            boolean handled = chessView.onTouchEvent(event);
            if (handled) {
                // 摆棋模式的触摸事件已经由SetupModeView处理
                return true;
            }
            // 否则由Activity处理
            return onTouch(view, event);
        });

        // 按钮点击监听器由PvPActivityControls模块处理
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        return gameModule.onTouch(view, event);
    }

    @Override
    public void onClick(View view) {
        controlsModule.onClick(view);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        long lastClickTime = System.currentTimeMillis();
        if (lastClickTime - PvPActivityInit.getCurClickTime() < PvPActivityInit.getMinClickDelayTime()) {
            return true;
        }
        PvPActivityInit.setCurClickTime(lastClickTime);
        PvPActivityInit.setLastClickTime(lastClickTime);

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 直接退出
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        initModule.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        initModule.onStop();
        super.onStop();
    }

    @Override
    protected void onStart() {
        initModule.onStart();
        super.onStart();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1003 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                controlsModule.saveChessNotationToUri(uri);
            }
        }
    }

    // Getters for modules to access
    public ChessView getChessView() {
        return chessView;
    }

    public PvPActivityRound getRoundView() {
        return roundModule;
    }
}
