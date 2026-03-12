package top.nones.chessgame;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.RadioButton;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.DialogInterface;
import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import ChessMove.Rule;
import CustomView.RoundView;
import Info.ChessInfo;
import Info.InfoSet;
import Info.Pos;
import Info.SaveInfo;
import Info.ChessNotation;
import Info.Setting;
import CustomView.ChessView;
import CustomView.SetupModeView;
import Utils.LogUtils;
import AICore.PikafishAI;
import ChessMove.Move;

public class PvMActivity extends AppCompatActivity implements View.OnTouchListener, View.OnClickListener {
    // 从HomeActivity移动过来的静态变量和方法
    public static final int MIN_CLICK_DELAY_TIME = 100;
    public static long curClickTime = 0L;
    public static long lastClickTime = 0L;
    public static Setting setting;
    public static MediaPlayer backMusic;
    public static MediaPlayer selectMusic;
    public static MediaPlayer clickMusic;
    public static MediaPlayer checkMusic;
    public static MediaPlayer winMusic;
    public static SharedPreferences sharedPreferences;
    public RelativeLayout relativeLayout;
    public ChessInfo chessInfo;
    public InfoSet infoSet;
    public ChessView chessView;
    public RoundView roundView;
    public SetupModeView setupModeView;
    // AI 支招信息显示
    public android.widget.TextView aiInfoTextView;
    // 按钮组ID
    private int buttonGroupId;
    // 对战模式：0-双人对战, 1-人机对战(玩家红), 2-人机对战(玩家黑), 3-双机对战
    public int gameMode = 0;
    
    // 继续对局后的回合计数器，用于控制和棋提示的频率
    public int continueGameRoundCount = 0;
    // AI相关变量
    public PikafishAI pikafishAI;
    
    // 模块管理器
    public PvMActivityNotation notationManager;
    public PvMActivitySetup setupManager;
    public PvMActivityControls controlsManager;
    public PvMActivityAI aiManager;
    public PvMActivityGame gameManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pvm);
        relativeLayout = (RelativeLayout) findViewById(R.id.relativeLayout);

        // 先初始化模块
        initModules();
        
        // 使用PvMActivityInit类处理初始化逻辑
        PvMActivityInit init = new PvMActivityInit(this);
        init.init();
        init.initViews();
        init.initBackgroundTasks();
    }
    
    // 初始化模块
    private void initModules() {
        notationManager = new PvMActivityNotation(this);
        setupManager = new PvMActivitySetup(this);
        controlsManager = new PvMActivityControls(this);
        aiManager = new PvMActivityAI(this);
        gameManager = new PvMActivityGame(this);
    }
    
    // 递归设置按钮监听器，处理嵌套布局
    private void setupButtonListeners(ViewGroup viewGroup) {
        controlsManager.setupButtonListeners(viewGroup);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        return controlsManager.handleTouch(view, event);
    }

    @Override
    public void onClick(View view) {
        lastClickTime = System.currentTimeMillis();
        if (lastClickTime - curClickTime < MIN_CLICK_DELAY_TIME) {
            return;
        }
        curClickTime = lastClickTime;

        int viewId = view.getId();
        if (viewId == R.id.btn_retry) {
            controlsManager.handleRetryButton();
        } else if (viewId == R.id.btn_prev) {
            // 上一步
            controlsManager.handlePrevButton();
        } else if (viewId == R.id.btn_next) {
            // 下一步
            controlsManager.handleNextButton();
        } else if (viewId == R.id.btn_recall) {
            controlsManager.handleRecallButton();
        } else if (viewId == R.id.btn_save) {
            // 保存棋谱 - 使用SAF选择保存位置
            notationManager.showSaveNotationDialog();
        } else if (viewId == R.id.btn_settings) {
            controlsManager.handleSettingsButton();
        } else if (viewId == R.id.btn_mode) {
            // 切换对战模式
            controlsManager.handleModeButton();
        } else if (viewId == R.id.btn_load) {
            // 加载棋谱 - 使用SAF选择文件
            notationManager.showLoadNotationDialog();
        } else if (viewId == R.id.btn_statistics) {
            // AI支招功能
            controlsManager.handleStatisticsButton();
        } else if (viewId == R.id.btn_setup) {
            // 切换摆棋模式
            setupManager.toggleSetupMode();
        }
    }
    
    // 生成走法字符串
    public String generateMoveString(ChessInfo chessInfo, int piece, Pos fromPos, Pos toPos, boolean isRed) {
        // 实现走法字符串生成逻辑
        StringBuilder move = new StringBuilder();
        
        // 获取棋子名称
        String pieceName = getPieceName(piece, isRed);
        move.append(pieceName);
        
        // 计算移动类型和目标位置
        if (fromPos.x == toPos.x) {
            // 纵向移动
            int distance = Math.abs(toPos.y - fromPos.y);
            if (isRed && toPos.y < fromPos.y || !isRed && toPos.y > fromPos.y) {
                // 前进
                move.append("进").append(distance);
            } else {
                // 后退
                move.append("退").append(distance);
            }
        } else {
            // 横向移动
            move.append("平").append(toPos.x + 1); // 转换为1-9的列号
        }
        
        return move.toString();
    }
    
    // 获取棋子名称
    private String getPieceName(int piece, boolean isRed) {
        switch (piece) {
            case 1: return "将"; // 黑将
            case 2: return "士"; // 黑士
            case 3: return "象"; // 黑象
            case 4: return "马"; // 黑马
            case 5: return "车"; // 黑车
            case 6: return "炮"; // 黑炮
            case 7: return "卒"; // 黑卒
            case 8: return "帅"; // 红帅
            case 9: return "仕"; // 红士
            case 10: return "相"; // 红相
            case 11: return "马"; // 红马
            case 12: return "车"; // 红车
            case 13: return "炮"; // 红炮
            case 14: return "兵"; // 红兵
            default: return "";
        }
    }
    
    // 获取棋盘坐标
    public int[] getPos(MotionEvent e) {
        int[] pos = new int[2];
        if (chessView == null || e == null) {
            pos[0] = pos[1] = -1;
            return pos;
        }
        double x = e.getX();
        double y = e.getY();
        int[] dis = new int[]{
                chessView.Scale(3), chessView.Scale(41), chessView.Scale(80), chessView.Scale(85)
        };
        x = x - dis[0];
        y = y - dis[1];
        if (x % dis[3] <= dis[2] && y % dis[3] <= dis[2]) {
            pos[0] = (int) Math.floor(x / dis[3]);
            pos[1] = 9 - (int) Math.floor(y / dis[3]);
            // 反转y坐标，与绘制时的逻辑保持一致
            if (pos[0] >= 9 || pos[1] >= 10 || pos[1] < 0) {
                pos[0] = pos[1] = -1;
            }
        } else {
            pos[0] = pos[1] = -1;
        }
        return pos;
    }
    
    // 处理Activity结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            // 从NotationActivity返回，加载选中的棋谱
                String fen = data.getStringExtra("fen");
                if (fen != null) {
                    // 加载棋谱
                    ChessNotation notation = new ChessNotation();
                    notation.setFen(fen);
                    notationManager.setCurrentNotation(notation);
                    notationManager.setCurrentMoveIndex(0);
                    notationManager.generateBoardStateFromNotation();
                }
        } else if (requestCode == 1002 && resultCode == RESULT_OK) {
            // 从文件选择器返回，加载选中的棋谱文件
            Uri uri = data.getData();
            if (uri != null) {
                notationManager.loadChessNotationFromUri(uri);
            }
        } else if (requestCode == 1003 && resultCode == RESULT_OK) {
            // 从文件保存对话框返回，保存棋谱
            Uri uri = data.getData();
            if (uri != null) {
                notationManager.saveChessNotationToUri(uri);
            }
        }
    }
    
    // 处理返回键
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 显示退出确认对话框
            new AlertDialog.Builder(this)
                    .setTitle("退出游戏")
                    .setMessage("确定要退出游戏吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        finish();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    // 生命周期方法
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 关闭AI线程池
        if (aiManager != null) {
            aiManager.shutdown();
        }
    }
}