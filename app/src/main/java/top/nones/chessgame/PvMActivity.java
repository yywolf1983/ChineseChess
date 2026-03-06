package top.nones.chessgame;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import ChessMove.Rule;
import CustomView.RoundView;
import Info.ChessInfo;
import Info.InfoSet;
import Info.Pos;
import Info.SaveInfo;
import Info.ChessNotation;
import Info.Setting;
import CustomView.ChessView;
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
    // AI 支招信息显示
    private android.widget.TextView aiInfoTextView;
    // 按钮组ID
    private int buttonGroupId;
    // 防止支招并发执行的对象
    private final Object aiAnalysisLock = new Object();
    // 跟踪AI分析状态
    private volatile boolean isAIAnalyzing = false;
    // 记录上次点击支招按钮的时间
    private long lastSuggestClickTime = 0;
    // 支招按钮点击间隔限制（毫秒）
    private static final long SUGGEST_BUTTON_INTERVAL = 1200;
    // 更新线程
    private Thread updateThread;

    private ChessNotation currentNotation;
    private int currentMoveIndex = 0;

    // 对战模式：0-双人对战, 1-人机对战(玩家红), 2-人机对战(玩家黑), 3-双机对战
    private int gameMode = 0;
    
    // 继续对局后的回合计数器，用于控制和棋提示的频率
    private int continueGameRoundCount = 0;
    // 摆棋模式下选中的棋子ID
    private int selectedPieceID = 0;
    // 摆棋模式下选中的棋盘上的棋子位置
    private int[] selectedBoardPiecePos = {-1, -1};
    // AI相关变量
    private PikafishAI pikafishAI;
    private int aiRetryCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pvm);
        relativeLayout = (RelativeLayout) findViewById(R.id.relativeLayout);

        // 初始化LogUtils
        LogUtils.init(this);

        // 初始化SaveInfo
        SaveInfo.init(this);

        // 初始化音乐
        initMusic();

        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences("setting", MODE_PRIVATE);

        // 初始化设置
        setting = new Setting(sharedPreferences);

        // 在后台线程中初始化数据文件（仅在文件不存在时）
        new Thread(() -> {
            try {
                // 只有在文件不存在时才创建新的文件
                if (!SaveInfo.fileIsExists("ChessInfo_pvm.bin")) {
                    SaveInfo.SerializeChessInfo(new ChessInfo(), "ChessInfo_pvm.bin");
                }
                if (!SaveInfo.fileIsExists("InfoSet_pvm.bin")) {
                    SaveInfo.SerializeInfoSet(new InfoSet(), "InfoSet_pvm.bin");
                }
            } catch (Exception e) {
                    e.printStackTrace();
                }
        }).start();

        // 初始化默认值
        chessInfo = new ChessInfo();
        chessInfo.setting = setting;
        infoSet = new InfoSet();
        // 将初始状态保存到preInfo栈中，确保第一次悔棋时有可恢复的状态
        try {
            infoSet.pushInfo(chessInfo);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        
        // 初始化线程池
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("AI-Analysis-Thread");
            return t;
        });
        scheduledExecutorService = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r);
            t.setName("AI-Update-Thread");
            return t;
        });

        // 在后台线程中加载数据，避免阻塞主线程
        new Thread(() -> {
            // 尝试从文件加载数据
            if (SaveInfo.fileIsExists("ChessInfo_pvm.bin")) {
                try {
                    final ChessInfo loadedChessInfo = SaveInfo.DeserializeChessInfo("ChessInfo_pvm.bin");
                    runOnUiThread(() -> {
                        chessInfo = loadedChessInfo;
                        chessInfo.setting = setting;
                        // 更新roundView和chessView中的chessInfo引用
                        if (roundView != null) {
                            roundView.setChessInfo(chessInfo);
                        }
                        if (chessView != null) {
                            chessView.setChessInfo(chessInfo);
                        }
                        // 重新绘制界面
                        if (roundView != null) {
                            roundView.requestDraw();
                        }
                        if (chessView != null) {
                            chessView.requestDraw();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    // 加载失败，使用默认值
                    runOnUiThread(() -> {
                        chessInfo = new ChessInfo();
                        chessInfo.setting = setting;
                        // 更新roundView和chessView中的chessInfo引用
                        if (roundView != null) {
                            roundView.setChessInfo(chessInfo);
                        }
                        if (chessView != null) {
                            chessView.setChessInfo(chessInfo);
                        }
                        // 重新绘制界面
                        if (roundView != null) {
                            roundView.requestDraw();
                        }
                        if (chessView != null) {
                            chessView.requestDraw();
                        }
                    });
                }
            }

            if (SaveInfo.fileIsExists("InfoSet_pvm.bin")) {
                try {
                    final InfoSet loadedInfoSet = SaveInfo.DeserializeInfoSet("InfoSet_pvm.bin");
                    runOnUiThread(() -> {
                        infoSet = loadedInfoSet;
                        // 确保preInfo栈不为空，否则第一次悔棋会没反应
                        if (infoSet.preInfo == null || infoSet.preInfo.isEmpty()) {
                            try {
                                infoSet.pushInfo(chessInfo);
                            } catch (CloneNotSupportedException ce) {
                                ce.printStackTrace();
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    // 加载失败，使用默认值
                    runOnUiThread(() -> {
                        infoSet = new InfoSet();
                        // 将初始状态保存到preInfo栈中
                        try {
                            infoSet.pushInfo(chessInfo);
                        } catch (CloneNotSupportedException ce) {
                            ce.printStackTrace();
                        }
                    });
                }
            } else {
                // 如果没有InfoSet文件，确保preInfo栈中有初始状态
                runOnUiThread(() -> {
                    try {
                        infoSet.pushInfo(chessInfo);
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                    }
                });
            }
        }).start();
        


        // 检查是否有从棋谱管理界面传递过来的棋谱数据
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("notation")) {
            currentNotation = (ChessNotation) intent.getSerializableExtra("notation");
            if (currentNotation != null) {
                // 初始化棋盘状态为初始状态
                chessInfo = new ChessInfo();
                chessInfo.setting = setting;
                infoSet = new InfoSet();
                currentMoveIndex = 0;
                Toast.makeText(this, "棋谱已加载，点击下一步开始回放", Toast.LENGTH_SHORT).show();
            }
        }

        // 先添加回合显示视图
        roundView = new RoundView(this, chessInfo, gameMode);
        relativeLayout.addView(roundView);

        RelativeLayout.LayoutParams paramsRound = (RelativeLayout.LayoutParams) roundView.getLayoutParams();
        paramsRound.addRule(RelativeLayout.CENTER_HORIZONTAL);
        paramsRound.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        paramsRound.width = RelativeLayout.LayoutParams.MATCH_PARENT;
        paramsRound.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
        paramsRound.setMargins(30, 30, 30, 10);
        roundView.setLayoutParams(paramsRound);
        roundView.setId(R.id.roundView);

        // 然后添加棋盘视图
        chessView = new ChessView(this, chessInfo);
        chessView.setOnTouchListener((view, event) -> {
            // 先让ChessView处理触摸事件（用于摆棋窗口拖动和棋子点击）
            boolean handled = chessView.onTouchEvent(event);
            if (handled) {
                // 如果是点击棋子或清空按钮，需要更新Activity中的选中状态
                if (chessInfo.IsSetupMode && event.getAction() == MotionEvent.ACTION_DOWN) {
                    float x = event.getX();
                    float y = event.getY();
                    int pieceID = chessView.getSetupModePieceAt(x, y);
                    if (pieceID > 0) {
                        // 选中棋子
                        selectedPieceID = pieceID;
                    } else if (pieceID == -1) {
                        // 点击了清空棋盘按钮
                    }
                }
                return true;
            }
            // 否则由Activity处理
            return onTouch(view, event);
        });
        relativeLayout.addView(chessView);

        RelativeLayout.LayoutParams paramsChess = (RelativeLayout.LayoutParams) chessView.getLayoutParams();
        paramsChess.addRule(RelativeLayout.BELOW, R.id.roundView);
        paramsChess.addRule(RelativeLayout.CENTER_HORIZONTAL);
        paramsChess.width = RelativeLayout.LayoutParams.MATCH_PARENT;
        paramsChess.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
        paramsChess.setMargins(30, 10, 30, 10);
        chessView.setLayoutParams(paramsChess);
        chessView.setId(R.id.chessView);



        // 初始化PikafishAI
        pikafishAI = new PikafishAI(this);
        
        // 初始化AI支招信息显示
        initAIInfoTextView();

        // 最后添加按钮组，确保它在顶层
        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout buttonGroup = (LinearLayout) inflater.inflate(R.layout.button_group, relativeLayout, false);
        relativeLayout.addView(buttonGroup);

        // 使用手动定义的ID
        buttonGroupId = 10001;
        buttonGroup.setId(buttonGroupId);

        RelativeLayout.LayoutParams paramsV = (RelativeLayout.LayoutParams) buttonGroup.getLayoutParams();
        paramsV.addRule(RelativeLayout.BELOW, R.id.chessView);
        paramsV.addRule(RelativeLayout.CENTER_HORIZONTAL);
        paramsV.width = RelativeLayout.LayoutParams.MATCH_PARENT;
        paramsV.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
        paramsV.setMargins(30, 120, 30, 10); // 增加顶部边距，确保不覆盖回合信息
        buttonGroup.setLayoutParams(paramsV);

        // 处理嵌套的LinearLayout布局
        setupButtonListeners(buttonGroup);
        
        // 初始绘制界面
        if (chessView != null) {
            chessView.requestDraw();
        }
        if (roundView != null) {
            roundView.requestDraw();
        }
        

    }
    
    // 递归设置按钮监听器，处理嵌套布局
    private void setupButtonListeners(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof Button) {
                // 直接是Button
                Button btn = (Button) child;
                btn.setOnClickListener(this);
            } else if (child instanceof ViewGroup) {
                // 是ViewGroup，递归处理
                setupButtonListeners((ViewGroup) child);
            }
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        lastClickTime = System.currentTimeMillis();
        if (lastClickTime - curClickTime < MIN_CLICK_DELAY_TIME) {
            return false;
        }
        curClickTime = lastClickTime;

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            if (chessInfo != null && chessInfo.status == 1) {
                // 摆棋模式处理
                if (chessInfo.IsSetupMode) {
                    // 检查是否点击在棋子选择区域
                    if (chessView != null) {
                        int pieceID = chessView.getSetupModePieceAt(x, y);
                        if (pieceID > 0) {
                            // 选中棋子
                            selectedPieceID = pieceID;
                        } else if (pieceID == -1) {
                            // 点击了清空棋盘按钮
                        } else if (pieceID == -2) {
                            // 点击了帮助按钮
                            showSetupHelp();
                        } 
                        // 检查是否点击在棋盘上
                        if (x >= 0 && x <= chessView.Board_width && y >= 0 && y <= chessView.Board_height) {
                            chessInfo.Select = getPos(event);
                            int i = chessInfo.Select[0];
                            int j = chessInfo.Select[1];

                            if (i >= 0 && i <= 8 && j >= 0 && j <= 9) {
                                // 获取点击位置的棋子ID
                                int boardPieceID = chessInfo.piece[j][i];
                                
                                // 如果已经选中了棋盘上的棋子
                                if (selectedBoardPiecePos[0] != -1 && selectedBoardPiecePos[1] != -1) {
                                    // 获取要操作的棋子ID
                                    int pieceToOperate = chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]];
                                    
                                    // 检查是否是点击原位置（下架）
                                    if (i == selectedBoardPiecePos[0] && j == selectedBoardPiecePos[1]) {
                                        // 点击原位置，下架棋子
                                            if (pieceToOperate != 1 && pieceToOperate != 8) { // 老将不能下架
                                                placePiece(selectedBoardPiecePos[0], selectedBoardPiecePos[1], 0);
                                                // 重置选中状态
                                                selectedBoardPiecePos[0] = -1;
                                                selectedBoardPiecePos[1] = -1;
                                            }
                                    }
                                    // 点击的是空白区域（移动棋子）
                                    else if (boardPieceID == 0) {
                                        // 检查是否是老将
                                        if (pieceToOperate == 1 || pieceToOperate == 8) {
                                            // 老将不能下架，但可以移动到合法位置
                                            // 检查新位置是否合理
                                            if (isValidPiecePosition(pieceToOperate, i, j)) {
                                                // 先将原位置设为0
                                                chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]] = 0;
                                                // 再将新位置设为棋子ID
                                                placePiece(i, j, pieceToOperate);
                                                // 重置选中状态
                                                selectedBoardPiecePos[0] = -1;
                                                selectedBoardPiecePos[1] = -1;
                                            }
                                        } else {
                                            // 不是老将，可以移动
                                            // 检查新位置是否合理
                                            if (isValidPiecePosition(pieceToOperate, i, j)) {
                                                // 先将原位置设为0
                                                chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]] = 0;
                                                // 再将新位置设为棋子ID
                                                placePiece(i, j, pieceToOperate);
                                                // 重置选中状态
                                                selectedBoardPiecePos[0] = -1;
                                                selectedBoardPiecePos[1] = -1;
                                            }
                                        }
                                    }
                                }
                                // 如果已经选中了棋子选择区域的棋子，放置到棋盘上
                                else if (selectedPieceID > 0) {
                placePiece(i, j, selectedPieceID);
                // 重置选中状态
                selectedPieceID = 0;
            }
            // 如果点击的是棋盘上的棋子，选中该棋子
            else if (boardPieceID > 0) {
                selectedBoardPiecePos[0] = i;
                selectedBoardPiecePos[1] = j;
                // 显示选中效果
                chessInfo.Select = new int[]{i, j};
                chessView.requestDraw();
            }
                                // 点击空白区域，重置选中状态
                                else {
                                    selectedBoardPiecePos[0] = -1;
                                    selectedBoardPiecePos[1] = -1;
                                    chessInfo.Select = new int[]{-1, -1};
                                    chessView.requestDraw();
                                }
                            }
                        }
                    }
                } 
                // 正常游戏模式处理
                else {
                    if (chessView != null && x >= 0 && x <= chessView.Board_width && y >= 0 && y <= chessView.Board_height) {
                        chessInfo.Select = getPos(event);
                        // 直接使用原始位置，不进行反转，因为棋盘状态本身没有被反转
                        int i = chessInfo.Select[0];
                        int j = chessInfo.Select[1];

                        if (i >= 0 && i <= 8 && j >= 0 && j <= 9 && chessInfo.piece != null) {
                            // 获取棋子ID
                            int pieceID = chessInfo.piece[j][i];
                            boolean isRedPiece = pieceID >= 8 && pieceID <= 14;
                            
                            // 双人对战模式
                            boolean canMove = true;

                            if (canMove) {
                                if (chessInfo.IsChecked == false) {
                                    // 只有当点击的位置有棋子时，才检查是否可以选择
                                    if (pieceID != 0) {
                                        // 检查是否是当前回合的颜色的棋子
                                        boolean canSelect = (isRedPiece && chessInfo.IsRedGo) || (!isRedPiece && !chessInfo.IsRedGo);
                                        
                                        if (canSelect) {
                                            chessInfo.prePos = new Pos(i, j);
                                            chessInfo.IsChecked = true;
                                            List<Pos> possibleMoves = Rule.PossibleMoves(chessInfo.piece, i, j, pieceID);
                                            
                                            // 检查是否被将军，如果是，只保留可以解将的移动
                                            if (Rule.isKingDanger(chessInfo.piece, isRedPiece)) {
                                                List<Pos> validMoves = new ArrayList<>();
                                                for (Pos pos : possibleMoves) {
                                                    // 模拟移动
                                                    int tmp = chessInfo.piece[pos.y][pos.x];
                                                    chessInfo.piece[pos.y][pos.x] = pieceID;
                                                    chessInfo.piece[j][i] = 0;
                                                    
                                                    // 检查移动后是否还被将军
                                                    if (!Rule.isKingDanger(chessInfo.piece, isRedPiece)) {
                                                        validMoves.add(pos);
                                                    }
                                                    
                                                    // 撤销移动
                                                    chessInfo.piece[j][i] = pieceID;
                                                    chessInfo.piece[pos.y][pos.x] = tmp;
                                                }
                                                chessInfo.ret = validMoves;
                                                
                                                // 如果没有可解将的移动，提示将死
                                                if (validMoves.isEmpty()) {
                                                    Toast toast = Toast.makeText(PvMActivity.this, isRedPiece ? "红方被将死！黑方胜利" : "黑方被将死！红方胜利", Toast.LENGTH_SHORT);
                                                    toast.setGravity(Gravity.CENTER, 0, 0);
                                                    toast.show();
                                                }
                                            } else {
                                                chessInfo.ret = possibleMoves;
                                            }
                                            
                                            // 重新绘制界面，显示选中效果
                                            if (chessView != null) {
                                                chessView.requestDraw();
                                            }
                                        }
                                    }
                                } else {
                                    // 直接使用原始坐标
                                    int targetX = i;
                                    int targetY = j;
                                    
                                    // 首先检查是否是有效的移动位置
                                    if (chessInfo.ret.contains(new Pos(targetX, targetY))) {
                                        int tmp = chessInfo.piece[targetY][targetX];
                                        int piece = chessInfo.piece[chessInfo.prePos.y][chessInfo.prePos.x];
                                        boolean isRed = piece >= 8 && piece <= 14;

                                        chessInfo.piece[targetY][targetX] = piece;
                                        chessInfo.piece[chessInfo.prePos.y][chessInfo.prePos.x] = 0;

                                        // 检查移动后是否被将军
                                        if (Rule.isKingDanger(chessInfo.piece, isRed)) {
                                            chessInfo.piece[chessInfo.prePos.y][chessInfo.prePos.x] = piece;
                                            chessInfo.piece[targetY][targetX] = tmp;
                                            Toast toast = Toast.makeText(PvMActivity.this, isRed ? "帅被将军" : "将被将军", Toast.LENGTH_SHORT);
                                            toast.setGravity(Gravity.CENTER, 0, 0);
                                            toast.show();
                                        } 
                                        // 检查移动后是否出现双方老将见面的情况
                                        else if (isKingFaceToFace(chessInfo.piece)) {
                                            chessInfo.piece[chessInfo.prePos.y][chessInfo.prePos.x] = piece;
                                            chessInfo.piece[targetY][targetX] = tmp;
                                            Toast toast = Toast.makeText(PvMActivity.this, "双方老将不能见面", Toast.LENGTH_SHORT);
                                            toast.setGravity(Gravity.CENTER, 0, 0);
                                            toast.show();
                                        } else {
                                            chessInfo.IsChecked = false;
                                            chessInfo.curPos = new Pos(targetX, targetY);
                                            chessInfo.Select = new int[]{-1, -1}; // 重置选中状态
                                            chessInfo.ret.clear(); // 清空可移动位置

                                            // 生成并记录标准象棋记谱走法
                                            String moveString = generateMoveString(chessInfo, piece, chessInfo.prePos, chessInfo.curPos, isRed);
                                            if (moveString != null) {
                                                LogUtils.i("Move", "用户走棋: " + moveString);
                                            }

                                            chessInfo.updateAllInfo(chessInfo.prePos, chessInfo.curPos, piece, tmp);

                                            // 保存移动后的状态到栈中
                                            try {
                                                infoSet.pushInfo(chessInfo);
                                            } catch (CloneNotSupportedException e) {
                                                e.printStackTrace();
                                            }

                                            int key = 0;
                                            if (Rule.isKingDanger(chessInfo.piece, !isRed)) {
                                                key = 1;
                                            }
                                            if (Rule.isDead(chessInfo.piece, !isRed)) {
                                                key = 2;
                                            }
                                            if (key == 1) {
                                                Toast toast = Toast.makeText(PvMActivity.this, "将军", Toast.LENGTH_SHORT);
                                                toast.setGravity(Gravity.CENTER, 0, 0);
                                                toast.show();
                                            } else if (key == 2) {
                                                chessInfo.status = 2;
                                                Toast toast = Toast.makeText(PvMActivity.this, isRed ? "红方获得胜利" : "黑方获得胜利", Toast.LENGTH_SHORT);
                                                toast.setGravity(Gravity.CENTER, 0, 0);
                                                toast.show();
                                            }

                                            // 增加继续对局后的回合计数器
                                            continueGameRoundCount++;

                                            if (chessInfo.status == 1) {
                                                // 只有当继续对局后的回合数达到20次以上时，才会再次触发和棋提示
                                                if (continueGameRoundCount >= 20) {
                                                    if (chessInfo.peaceRound >= 60) {
                                                        showDrawConfirmationDialog("双方60回合内未吃子，是否和棋？");
                                                    } else if (chessInfo.attackNum_B == 0 && chessInfo.attackNum_R == 0) {
                                                        showDrawConfirmationDialog("双方都无攻击性棋子，是否和棋？");
                                                    } else if (infoSet.ZobristInfo != null && chessInfo.ZobristKeyCheck != 0 && infoSet.ZobristInfo.get(chessInfo.ZobristKeyCheck) != null && infoSet.ZobristInfo.get(chessInfo.ZobristKeyCheck) >= 4) {
                                                        showDrawConfirmationDialog("重复局面出现4次，是否和棋？");
                                                    }
                                                }
                                            }

                                            // 获取当前局面的评分（在后台线程中执行）
                                            if (pikafishAI != null && pikafishAI.isInitialized()) {
                                                new Thread(() -> {
                                                    PikafishAI.MoveWithScore moveWithScore = pikafishAI.getBestMoveWithScore(chessInfo);
                                                    final int score = moveWithScore.score;
                                                    
                                                    // 更新评分显示
                                                    runOnUiThread(() -> {
                                                        if (roundView != null) {
                                                            roundView.setMoveScore(score);
                                                        }
                                                    });
                                                }).start();
                                            }
                                            
                                            // 重新绘制界面
                                                if (chessView != null) {
                                                    chessView.requestDraw();
                                                }
                                                if (roundView != null) {
                                                    roundView.requestDraw();
                                                }
                                                
                                                // 检查是否需要AI移动
                                                checkAIMove();
                                        }
                                    } else if (pieceID != 0) {
                                        // 只有当点击的位置有棋子时，才检查是否可以选择新棋子
                                        // 检查是否是当前回合的颜色的棋子
                                        boolean canSelect = (isRedPiece && chessInfo.IsRedGo) || (!isRedPiece && !chessInfo.IsRedGo);
                                        
                                        if (canSelect) {
                                            chessInfo.prePos = new Pos(i, j);
                                            chessInfo.ret = Rule.PossibleMoves(chessInfo.piece, i, j, pieceID);
                                            // 重新绘制界面，显示选中效果
                                            if (chessView != null) {
                                                chessView.requestDraw();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }


    

    

    

    

    
    // 计算AI最佳移动
    private Move calculateAIMove() {
        return calculateAIMoveWithDepthUpdate();
    }

    // 计算AI最佳移动，带有深度更新
    private Move calculateAIMoveWithDepthUpdate() {
        // 显示AI开始搜索的信息，包含深度
        startAISearch();
        
        if (chessInfo == null || pikafishAI == null || !pikafishAI.isInitialized() || chessInfo.piece == null) {
            return null;
        }
        
        // 验证棋盘状态的有效性
        if (chessInfo.piece.length != 10) {
            return null;
        }
        
        for (int i = 0; i < 10; i++) {
            if (chessInfo.piece[i] == null || chessInfo.piece[i].length != 9) {
                return null;
            }
        }
        
        // 检查将（帅）是否存在
        boolean redKingExists = false;
        boolean blackKingExists = false;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (chessInfo.piece[i][j] == 1) { // 黑将
                    blackKingExists = true;
                } else if (chessInfo.piece[i][j] == 8) { // 红帅
                    redKingExists = true;
                }
            }
        }
        
        // 如果游戏已经结束，返回 null
        if (!redKingExists || !blackKingExists) {
            return null;
        }
        
        // 检查是否被将死
        boolean isRed = chessInfo.IsRedGo;
        
        if (Rule.isDead(chessInfo.piece, isRed)) {
            return null;
        }
        
        // 检查是否有可移动的棋子
        boolean hasValidMoves = false;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                int piece = chessInfo.piece[i][j];
                if (piece != 0) {
                    boolean pieceIsRed = piece >= 8 && piece <= 14;
                    if (pieceIsRed == isRed) {
                        List<Pos> possibleMoves = Rule.PossibleMoves(chessInfo.piece, j, i, piece);
                        if (!possibleMoves.isEmpty()) {
                            hasValidMoves = true;
                            break;
                        }
                    }
                }
            }
            if (hasValidMoves) break;
        }
        
        if (!hasValidMoves) {
            return null;
        }
        
        // 使用PikafishAI获取最佳走法和评分
        PikafishAI.MoveWithScore moveWithScore = pikafishAI.getBestMoveWithScore(chessInfo);
        Move move = moveWithScore.move;
        int score = moveWithScore.score;
        
        // 更新评分显示
        if (roundView != null) {
            roundView.setMoveScore(score);
        }
        
        // 验证移动的有效性
        if (move != null) {
            Pos fromPos = move.fromPos;
            Pos toPos = move.toPos;
            if (fromPos == null || toPos == null) {
                return null;
            }
            if (fromPos.x < 0 || fromPos.x >= 9 || fromPos.y < 0 || fromPos.y >= 10 || toPos.x < 0 || toPos.x >= 9 || toPos.y < 0 || toPos.y >= 10) {
                return null;
            }
            
            // 检查起始位置是否有棋子
            int piece = chessInfo.piece[fromPos.y][fromPos.x];
            if (piece == 0) {
                return null;
            }
            
            // 验证棋子颜色是否正确，防止AI走对方的棋子
            boolean pieceIsRed = piece >= 8 && piece <= 14;
            
            if (pieceIsRed != isRed) {
                return null;
            }
            
            // 验证移动是否合法
            List<Pos> possibleMoves = Rule.PossibleMoves(chessInfo.piece, fromPos.x, fromPos.y, piece);
            if (!possibleMoves.contains(toPos)) {
                return null;
            }
        }
        
        return move;
    }
    
    // 执行AI移动
    private boolean executeAIMove(Move move) {
        if (chessInfo == null || chessInfo.piece == null) {
            return false;
        }
        
        // 检查将（帅）是否存在
        boolean redKingExists = false;
        boolean blackKingExists = false;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (chessInfo.piece[i][j] == 1) { // 黑将
                    blackKingExists = true;
                } else if (chessInfo.piece[i][j] == 8) { // 红帅
                    redKingExists = true;
                }
            }
        }
        
        // 如果游戏已经结束，显示相应的提示信息
        if (!redKingExists) {
            Toast.makeText(this, "红方胜利！黑将被吃掉了", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!blackKingExists) {
            Toast.makeText(this, "黑方胜利！红帅被吃掉了", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (move == null) {
            // 检查AI是否被将死
            if (chessInfo.IsRedGo) {
                if (Rule.isDead(chessInfo.piece, true)) {
                    Toast.makeText(this, "红方被将死！黑方胜利", Toast.LENGTH_SHORT).show();
                    return false;
                }
            } else {
                if (Rule.isDead(chessInfo.piece, false)) {
                    Toast.makeText(this, "黑方被将死！红方胜利", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
            return false;
        }
        
        Pos fromPos = move.fromPos;
        Pos toPos = move.toPos;
        
        // 检查移动的合法性
        if (fromPos == null || toPos == null) {
            return false;
        }
        
        if (fromPos.x < 0 || fromPos.x >= 9 || fromPos.y < 0 || fromPos.y >= 10 || toPos.x < 0 || toPos.x >= 9 || toPos.y < 0 || toPos.y >= 10) {
            return false;
        }
        
        // 检查起始位置是否有棋子
        if (chessInfo.piece[fromPos.y][fromPos.x] == 0) {
            return false;
        }
        
        int tmp = chessInfo.piece[toPos.y][toPos.x];
        int piece = chessInfo.piece[fromPos.y][fromPos.x];
        boolean isRed = piece >= 8 && piece <= 14;
        
        // 检查棋子颜色是否与当前回合匹配，防止AI走对方的棋子
        if (isRed != chessInfo.IsRedGo) {
            return false;
        }
        
        // 检查移动是否合法
        List<Pos> possibleMoves = Rule.PossibleMoves(chessInfo.piece, fromPos.x, fromPos.y, piece);
        if (!possibleMoves.contains(toPos)) {
            return false;
        }
        
        // 执行移动
        chessInfo.piece[toPos.y][toPos.x] = piece;
        chessInfo.piece[fromPos.y][fromPos.x] = 0;
        chessInfo.IsChecked = Rule.isKingDanger(chessInfo.piece, !isRed);
        chessInfo.Select = new int[]{-1, -1};
        chessInfo.ret.clear();
        chessInfo.prePos = fromPos;
        chessInfo.curPos = toPos;
        
        // 生成并记录标准象棋记谱走法
        String moveString = generateMoveString(chessInfo, piece, fromPos, toPos, isRed);
        if (moveString != null) {
            LogUtils.i("Move", "AI走棋: " + moveString);
        }
        
        // 更新游戏信息
        chessInfo.updateAllInfo(chessInfo.prePos, chessInfo.curPos, chessInfo.piece[toPos.y][toPos.x], tmp);
        chessInfo.isMachine = true; // 标记为AI移动
        
        // AI移动也添加到栈中，这样保存棋谱时能包含AI的走法记录
        try {
            infoSet.pushInfo(chessInfo);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        
        // AI移动后重新绘制界面
        if (chessView != null) {
            chessView.requestDraw();
        }
        if (roundView != null) {
            roundView.requestDraw();
        }
        
        // 增加继续对局后的回合计数器
        continueGameRoundCount++;
        
        // 检查游戏状态
        checkGameStatus(isRed);
        
        // 双机对战模式下，自动触发下一次AI移动
        if (gameMode == 3 && chessInfo.status == 1) {
            // 添加300ms行棋间隔
            scheduledExecutorService.schedule(() -> {
                runOnUiThread(() -> {
                    checkAIMove();
                });
            }, 300, TimeUnit.MILLISECONDS);
        } else {
            // 停止深度更新
            stopAISearch();
        }
        
        return true;
    }
    
    // 启动AI线程
    private void startAIThread() {
        if (chessInfo == null || chessInfo.status != 1) {
            return;
        }
        
        // 使用线程池执行AI分析任务
        executorService.execute(() -> {
            final Move move = calculateAIMove();
            runOnUiThread(() -> {
                if (move != null) {
                    executeAIMove(move);
                } else {
                    // 停止深度更新
                    stopAISearch();
                    
                    // AI无法找到有效移动，检查是否被将死
                    boolean isRed = chessInfo.IsRedGo;
                    if (Rule.isDead(chessInfo.piece, isRed)) {
                        Toast.makeText(PvMActivity.this, isRed ? "红方被将死！黑方胜利" : "黑方被将死！红方胜利", Toast.LENGTH_SHORT).show();
                    } else {
                        // 重试几次
                        if (aiRetryCount < 3) {
                            aiRetryCount++;
                            startAIThread();
                        } else {
                            aiRetryCount = 0;
                        }
                    }
                }
            });
        });
    }
    
    // 检查是否需要AI移动
    private void checkAIMove() {
        if (chessInfo == null || chessInfo.status != 1) {
            return;
        }
        
        // 根据游戏模式判断是否需要AI移动
        if (gameMode == 1) { // 人机对战（玩家红）
            if (!chessInfo.IsRedGo) { // AI控制黑方
                startAIThread();
            }
        } else if (gameMode == 2) { // 人机对战（玩家黑）
            if (chessInfo.IsRedGo) { // AI控制红方
                startAIThread();
            }
        } else if (gameMode == 3) { // 双机对战
            // 双机对战模式下，双方都由AI控制
            startAIThread();
        }
    }
    
    // 初始化AI支招信息显示
    private void initAIInfoTextView() {
        if (relativeLayout == null) return;
        
        // 创建TextView
        aiInfoTextView = new android.widget.TextView(this);
        aiInfoTextView.setText("点击支招-AI建议");
        aiInfoTextView.setTextSize(16);
        aiInfoTextView.setTextColor(android.graphics.Color.BLACK);
        aiInfoTextView.setPadding(20, 10, 20, 10);
        aiInfoTextView.setGravity(android.view.Gravity.CENTER);
        aiInfoTextView.setBackgroundColor(android.graphics.Color.parseColor("#FFFFCC"));
        aiInfoTextView.setVisibility(android.view.View.VISIBLE);
        
        // 设置布局参数
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        params.addRule(RelativeLayout.BELOW, R.id.chessView);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.setMargins(30, 10, 30, 10); // 调整边距，确保横幅显示在棋盘下方，按钮上方
        aiInfoTextView.setLayoutParams(params);
        
        // 添加到布局
        relativeLayout.addView(aiInfoTextView);
    }
    
    // 更新AI信息文本
    private void updateAIInfoText(String text) {
        // 这里可以添加更新UI的逻辑，比如显示AI思考过程
        // 更新固定显示的TextView，确保在UI线程中执行
        runOnUiThread(() -> {
            if (aiInfoTextView != null) {
                aiInfoTextView.setText(text);
            }
        });
    }
    
    // 检查游戏状态
    private void checkGameStatus(boolean isRed) {
        if (chessInfo == null) return;
        
        int key = 0;
        if (Rule.isKingDanger(chessInfo.piece, !isRed)) {
            key = 1;
        }
        if (Rule.isDead(chessInfo.piece, !isRed)) {
            key = 2;
        }
        
        if (key == 1) {
            Toast toast = Toast.makeText(this, "将军", Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        } else if (key == 2) {
            chessInfo.status = 2;
            Toast toast = Toast.makeText(this, isRed ? "红方获得胜利" : "黑方获得胜利", Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
            // 游戏结束时重新绘制界面
            if (chessView != null) {
                chessView.requestDraw();
            }
            if (roundView != null) {
                roundView.requestDraw();
            }
        }
        
        // 检查和棋条件
        if (chessInfo.status == 1) {
            // 只有当继续对局后的回合数达到20次以上时，才会再次触发和棋提示
            if (continueGameRoundCount >= 20) {
                if (chessInfo.peaceRound >= 60) {
                    showDrawConfirmationDialog("双方60回合内未吃子，是否和棋？");
                } else if (chessInfo.attackNum_B == 0 && chessInfo.attackNum_R == 0) {
                    showDrawConfirmationDialog("双方都无攻击性棋子，是否和棋？");
                } else if (infoSet != null && infoSet.ZobristInfo != null && chessInfo.ZobristKeyCheck != 0 && infoSet.ZobristInfo.get(chessInfo.ZobristKeyCheck) != null && infoSet.ZobristInfo.get(chessInfo.ZobristKeyCheck) >= 4) {
                    showDrawConfirmationDialog("重复局面出现4次，是否和棋？");
                }
            }
        }
    }
    
    // 显示和棋确认对话框
    private void showDrawConfirmationDialog(String message) {
        // 暂时保存当前游戏状态
        int originalStatus = chessInfo.status;
        // 设置游戏状态为暂停，防止AI继续移动
        chessInfo.status = 3; // 3表示暂停状态
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("和棋确认");
        builder.setMessage(message);
        builder.setPositiveButton("同意和棋", (dialog, which) -> {
            chessInfo.status = 2;
            Toast toast = Toast.makeText(this, "此乃和棋", Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
            // 游戏结束时重新绘制界面
            if (chessView != null) {
                chessView.requestDraw();
            }
            if (roundView != null) {
                roundView.requestDraw();
            }
        });
        builder.setNegativeButton("继续对局", (dialog, which) -> {
            // 恢复原始游戏状态
            chessInfo.status = originalStatus;
            // 重置继续对局后的回合计数器
            continueGameRoundCount = 0;
            // 重新绘制界面
            if (chessView != null) {
                chessView.requestDraw();
            }
            if (roundView != null) {
                roundView.requestDraw();
            }
            // 检查是否需要AI移动
            checkAIMove();
        });
        builder.show();
    }
    
    // 显示AI最佳移动
    private void showAIMove(boolean isRed) {
        // 首先检查是否已经在分析中
        if (isAIAnalyzing) {
            return;
        }
        
        // 检查chessInfo和chessView是否初始化
        if (chessInfo == null || chessView == null) {
            return;
        }
        
        // 初始化aiInfoTextView（如果未初始化）
        if (aiInfoTextView == null) {
            initAIInfoTextView();
            if (aiInfoTextView == null) {
                // 如果初始化失败，返回
                return;
            }
        }
        
        // 显示AI思考提示
        updateAIInfoText("AI正在分析最佳走法.");
        
        // 使用线程池执行AI分析任务
        executorService.execute(() -> {
            // 使用同步块确保整个分析过程的原子性
            synchronized (aiAnalysisLock) {
                if (isAIAnalyzing) {
                    return;
                }
                isAIAnalyzing = true;
                
                // 启动省略号滚动线程
                final Runnable dotTask = new Runnable() {
                    @Override
                    public void run() {
                        if (!isAIAnalyzing || aiInfoTextView == null) {
                            return;
                        }
                        
                        StringBuilder dots = new StringBuilder();
                        for (int i = 0; i < dotCount % 4; i++) {
                            dots.append(".");
                        }
                        
                        final String dotString = dots.toString();
                        runOnUiThread(() -> {
                            if (aiInfoTextView != null) {
                                updateAIInfoText("AI正在分析最佳走法" + dotString);
                            }
                        });
                        
                        dotCount++;
                        
                        if (isAIAnalyzing) {
                            scheduledExecutorService.schedule(this, 500, TimeUnit.MILLISECONDS);
                        }
                    }
                };
                scheduledExecutorService.schedule(dotTask, 0, TimeUnit.MILLISECONDS);
                
                Move move = null;
                
                try {
                    // 直接使用原始chessInfo进行分析，不再使用临时对象
                    // 这样可以确保AI分析基于最新的棋盘状态
                    move = calculateAIMove();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    isAIAnalyzing = false;
                    dotCount = 0;
                    
                    final Move finalMove = move;
                    runOnUiThread(() -> {
                        if (aiInfoTextView == null || chessInfo == null || chessView == null) {
                            return;
                        }
                        
                        if (finalMove != null && finalMove.fromPos != null && finalMove.toPos != null) {
                            // 转换为显示坐标
                            int displayFromX = finalMove.fromPos.x;
                            int displayFromY = finalMove.fromPos.y;
                            int displayToX = finalMove.toPos.x;
                            int displayToY = finalMove.toPos.y;

                            
                            // 获取棋子信息
                            int piece = chessInfo.piece[finalMove.fromPos.y][finalMove.fromPos.x];
                            String[] pieceNames = {
                                "", "将", "士", "象", "马", "车", "炮", "卒",
                                "帅", "士", "相", "马", "车", "炮", "兵"
                            };
                            String pieceName = pieceNames[piece - 1];
                            
                            // 转换为传统中国象棋记谱法
                            String moveInfo = generateMoveString(chessInfo, piece, finalMove.fromPos, finalMove.toPos, isRed);
                            
                            // 生成提示信息
                            String hintText = (isRed ? "红方" : "黑方") + ": " + moveInfo + " (" + (char)('a' + displayFromX) + (9 - displayFromY) + "到" + (char)('a' + displayToX) + (9 - displayToY) + ")";
                            updateAIInfoText(hintText);
                            
                            // 选中需要移动的棋子
                            // 设置支招位置信息
                            chessInfo.suggestFromPos = finalMove.fromPos;
                            chessInfo.suggestToPos = finalMove.toPos;
                            // 不要修改prePos和IsChecked，避免影响棋局状态
                            // 获取可能的移动位置
                            List<Pos> possibleMoves = Rule.PossibleMoves(chessInfo.piece, finalMove.fromPos.x, finalMove.fromPos.y, piece);
                            chessInfo.ret = possibleMoves;
                            // 重新绘制界面，显示选中效果
                            chessView.requestDraw();
                        } else {
                            updateAIInfoText("AI无法找到有效移动");
                            Toast.makeText(this, "AI无法找到有效移动", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }
    
    // 为支招计算AI最佳移动（使用临时对象）
    private Move calculateAIMoveForSuggestion(ChessInfo tempChessInfo) {
        if (tempChessInfo == null || pikafishAI == null || !pikafishAI.isInitialized() || tempChessInfo.piece == null) {
            return null;
        }
        
        // 验证棋盘状态的有效性
        if (tempChessInfo.piece.length != 10) {
            return null;
        }
        
        for (int i = 0; i < 10; i++) {
            if (tempChessInfo.piece[i] == null || tempChessInfo.piece[i].length != 9) {
                return null;
            }
        }
        
        // 检查将（帅）是否存在
        boolean redKingExists = false;
        boolean blackKingExists = false;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                int piece = tempChessInfo.piece[i][j];
                if (piece == 8) { // 红帅
                    redKingExists = true;
                } else if (piece == 1) { // 黑将
                    blackKingExists = true;
                }
                if (redKingExists && blackKingExists) {
                    break;
                }
            }
            if (redKingExists && blackKingExists) {
                break;
            }
        }
        
        if (!redKingExists || !blackKingExists) {
            return null;
        }
        
        // 检查是否被将死
        boolean isRed = tempChessInfo.IsRedGo;
        if (Rule.isDead(tempChessInfo.piece, isRed)) {
            return null;
        }
        
        // 检查是否有可移动的棋子
        boolean hasValidMoves = false;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                int piece = tempChessInfo.piece[i][j];
                if (piece != 0) {
                    boolean isPieceRed = (piece >= 8);
                    if (isPieceRed == isRed) {
                        List<Pos> moves = Rule.PossibleMoves(tempChessInfo.piece, j, i, piece);
                        if (!moves.isEmpty()) {
                            hasValidMoves = true;
                            break;
                        }
                    }
                }
            }
            if (hasValidMoves) {
                break;
            }
        }
        
        if (!hasValidMoves) {
            return null;
        }
        
        // 获取最佳移动
        PikafishAI.MoveWithScore moveWithScore = pikafishAI.getBestMoveWithScore(tempChessInfo);
        return moveWithScore != null ? moveWithScore.move : null;
    }
    
    // 显示棋子选择对话框
    private void showPieceSelectionDialog(final int x, final int y) {
        final String[] blackPieces = {"黑将", "黑士", "黑象", "黑马", "黑车", "黑炮", "黑卒", "移除棋子"};
        final String[] redPieces = {"红帅", "红士", "红象", "红马", "红车", "红炮", "红兵", "移除棋子"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择棋子");
        
        // 显示黑棋和红棋选项
        builder.setItems(blackPieces, (dialog, which) -> {
            int pieceID = 0;
            switch (which) {
                case 0: pieceID = 1; break; // 黑将
                case 1: pieceID = 2; break; // 黑士
                case 2: pieceID = 3; break; // 黑象
                case 3: pieceID = 4; break; // 黑马
                case 4: pieceID = 5; break; // 黑车
                case 5: pieceID = 6; break; // 黑炮
                case 6: pieceID = 7; break; // 黑卒
                case 7: pieceID = 0; break; // 移除棋子
            }
            placePiece(x, y, pieceID);
        });
        
        builder.setNegativeButton("红棋", (dialog, which) -> {
            AlertDialog.Builder redBuilder = new AlertDialog.Builder(PvMActivity.this);
            redBuilder.setTitle("选择红棋");
            redBuilder.setItems(redPieces, (dialog1, which1) -> {
                int pieceID = 0;
                switch (which1) {
                    case 0: pieceID = 8; break; // 红帅
                    case 1: pieceID = 9; break; // 红士
                    case 2: pieceID = 10; break; // 红象
                    case 3: pieceID = 11; break; // 红马
                    case 4: pieceID = 12; break; // 红车
                    case 5: pieceID = 13; break; // 红炮
                    case 6: pieceID = 14; break; // 红兵
                    case 7: pieceID = 0; break; // 移除棋子
                }
                placePiece(x, y, pieceID);
            });
            redBuilder.show();
        });
        
        builder.show();
    }
    
    // 放置棋子
    private void placePiece(int x, int y, int pieceID) {
        if (chessInfo != null && chessInfo.piece != null && x >= 0 && x < 9 && y >= 0 && y < 10) {
            // 检查棋子数量限制
            if (!checkPieceCount(pieceID)) {
                // 显示数量限制提示
                return;
            }
            
            // 检查位置合理性
            if (!isValidPiecePosition(pieceID, x, y)) {
                // 显示位置不合理提示
                return;
            }
            
            chessInfo.piece[y][x] = pieceID;
            // 重新计算攻击棋子数量
            chessInfo.attackNum_B = 0;
            chessInfo.attackNum_R = 0;
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    int piece = chessInfo.piece[i][j];
                    if (piece != 0) {
                        // 黑方攻击棋子：车(5)、马(4)、炮(6)、卒(7)
                        if (piece == 4 || piece == 5 || piece == 6 || piece == 7) {
                            chessInfo.attackNum_B++;
                        }
                        // 红方攻击棋子：车(12)、马(11)、炮(13)、兵(14)
                        else if (piece == 11 || piece == 12 || piece == 13 || piece == 14) {
                            chessInfo.attackNum_R++;
                        }
                    }
                }
            }
            // 重新绘制界面
            if (chessView != null) {
                chessView.requestDraw();
            }
            
            // 不再自动检查摆棋完成，由用户点击摆棋按钮结束
        }
    }
    
    // 检查棋子位置是否合理
    private boolean isValidPiecePosition(int pieceID, int x, int y) {
        // 检查坐标是否在棋盘范围内
        if (x < 0 || x >= 9 || y < 0 || y >= 10) {
            return false;
        }
        
        // 摆棋模式下的位置限制
        if (chessInfo != null && chessInfo.IsSetupMode) {
            switch (pieceID) {
                case 1: // 黑将
                case 8: // 红帅
                    // 将帅只能在九宫格内
                    if (pieceID == 1) { // 黑将
                        // 黑将九宫格：x: 3-5, y: 7-9（因为坐标已经反转）
                        return x >= 3 && x <= 5 && y >= 7 && y <= 9;
                    } else { // 红帅
                        // 红帅九宫格：x: 3-5, y: 0-2（因为坐标已经反转）
                        return x >= 3 && x <= 5 && y >= 0 && y <= 2;
                    }
                case 2: // 黑士
                case 9: // 红士
                    // 士只能在九宫格内且走斜线位置
                    if (pieceID == 2) { // 黑士
                        // 黑士九宫格：x: 3-5, y: 7-9（因为坐标已经反转）
                        return (x >= 3 && x <= 5 && y >= 7 && y <= 9) && 
                               ((x == 3 && (y == 7 || y == 9)) || (x == 4 && y == 8) || (x == 5 && (y == 7 || y == 9)));
                    } else { // 红士
                        // 红士九宫格：x: 3-5, y: 0-2（因为坐标已经反转）
                        return (x >= 3 && x <= 5 && y >= 0 && y <= 2) && 
                               ((x == 3 && (y == 0 || y == 2)) || (x == 4 && y == 1) || (x == 5 && (y == 0 || y == 2)));
                    }
                case 3: // 黑象
                case 10: // 红相
                    // 相只能在己方半场
                    if (pieceID == 3) { // 黑象
                        // 黑象位置：在己方半场（因为坐标已经反转）
                        return y >= 5 && y <= 9;
                    } else { // 红相
                        // 红相位置：在己方半场（因为坐标已经反转）
                        return y >= 0 && y <= 4;
                    }
                case 7: // 黑卒
                    // 摆棋模式下黑卒可以自由摆放
                    return true;
                case 14: // 红兵
                    // 摆棋模式下红兵可以自由摆放
                    return true;
                case 4: // 黑马
                case 11: // 红马
                    // 马可以自由摆放
                    return true;
                case 5: // 黑车
                case 12: // 红车
                    // 车可以自由摆放
                    return true;
                case 6: // 黑炮
                case 13: // 红炮
                    // 炮可以自由摆放
                    return true;
                default:
                    // 其他棋子默认可以自由摆放
                    return true;
            }
        }
        
        // 正常游戏模式下的位置限制
        switch (pieceID) {
            case 1: // 黑将
                // 黑将只能在九宫格内（x: 3-5, y: 7-9）- 黑方在下
                return x >= 3 && x <= 5 && y >= 7 && y <= 9;
            case 8: // 红帅
                // 红帅只能在九宫格内（x: 3-5, y: 0-2）- 红方在上
                return x >= 3 && x <= 5 && y >= 0 && y <= 2;
            case 2: // 黑士
                // 黑士只能在九宫格内（x: 3-5, y: 7-9）且走斜线 - 黑方在下
                return (x >= 3 && x <= 5 && y >= 7 && y <= 9) && 
                       ((x == 3 && (y == 7 || y == 9)) || (x == 4 && y == 8) || (x == 5 && (y == 7 || y == 9)));
            case 9: // 红士
                // 红士只能在九宫格内（x: 3-5, y: 0-2）且走斜线 - 红方在上
                return (x >= 3 && x <= 5 && y >= 0 && y <= 2) && 
                       ((x == 3 && (y == 0 || y == 2)) || (x == 4 && y == 1) || (x == 5 && (y == 0 || y == 2)));
            case 3: // 黑象
                // 黑象只能在己方半场（y: 5-9）且不能过河 - 黑方在下
                return y >= 5 && y <= 9;
            case 10: // 红相
                // 红相只能在己方半场（y: 0-4）且不能过河 - 红方在上
                return y >= 0 && y <= 4;
            case 7: // 黑卒
                // 黑卒只能在己方半场（y: 5-9）- 黑方在下
                return y >= 5 && y <= 9;
            case 14: // 红兵
                // 红兵只能在己方半场（y: 0-4）- 红方在上
                return y >= 0 && y <= 4;
            case 4: // 黑马
            case 5: // 黑车
            case 6: // 黑炮
                // 黑方棋子只能在己方半场（y: 5-9）- 黑方在下
                return y >= 5 && y <= 9;
            case 11: // 红马
            case 12: // 红车
            case 13: // 红炮
                // 红方棋子只能在己方半场（y: 0-4）- 红方在上
                return y >= 0 && y <= 4;
            default:
                return false;
        }
    }
    
    // 检查棋子数量是否符合标准
    private boolean checkPieceCount(int pieceID) {
        if (pieceID == 0) return true; // 移除棋子总是允许的
        if (chessInfo == null || chessInfo.piece == null) return false;
        
        int count = 0;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (chessInfo.piece[i][j] == pieceID) {
                    count++;
                }
            }
        }
        
        // 标准中国象棋棋子数量限制
        switch (pieceID) {
            case 1: // 黑将
            case 8: // 红帅
                return count < 1;
            case 2: // 黑士
            case 3: // 黑象
            case 4: // 黑马
            case 5: // 黑车
            case 6: // 黑炮
            case 9: // 红士
            case 10: // 红相
            case 11: // 红马
            case 12: // 红车
            case 13: // 红炮
                return count < 2;
            case 7: // 黑卒
            case 14: // 红兵
                return count < 5;
            default:
                return true;
        }
    }
    
    // 显示摆棋模式帮助信息
    private void showSetupHelp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("摆棋模式帮助");
        builder.setMessage("1. 点击左侧棋子选择区域选择棋子\n" +
                          "2. 点击棋盘放置选中的棋子\n" +
                          "3. 点击棋盘上已放置的棋子可移动或移除\n" +
                          "4. 点击清空棋盘按钮可清空除将/帅外的所有棋子\n" +
                          "\n棋子放置规则：\n" +
                          "- 将/帅：只能放在九宫格内\n" +
                          "- 士：只能放在九宫格内的斜线位置\n" +
                          "- 象/相：只能放在己方半场的田字中心\n" +
                          "- 卒/兵：只能放在己方兵线位置\n" +
                          "- 马、车、炮：可以自由放置\n" +
                          "\n摆棋完成后，会自动提示选择开局方。");
        builder.setPositiveButton("确定", null);
        builder.show();
    }
    
    // 检查摆棋是否完成
    private boolean checkSetupComplete() {
        if (chessInfo == null || chessInfo.piece == null) return false;
        
        // 只检查基本合法性：双方都有将/帅
        boolean hasRedKing = false;
        boolean hasBlackKing = false;
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                int piece = chessInfo.piece[i][j];
                if (piece == 1) { // 黑将
                    hasBlackKing = true;
                } else if (piece == 8) { // 红帅
                    hasRedKing = true;
                }
            }
        }
        
        return hasRedKing && hasBlackKing;
    }
    
    // 结束摆棋并选择开局方
    private void finishSetup() {
        if (checkSetupComplete()) {
            // 显示选择开局方的对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("选择开局方");
            builder.setMessage("请选择由哪一方开始下棋");
            builder.setPositiveButton("红方开始", (dialog, which) -> {
                // 红方开始，设置IsRedGo为true
                chessInfo.IsRedGo = true;
                // 退出摆棋模式
                chessInfo.IsSetupMode = false;
                // 确保游戏状态为进行中
                chessInfo.status = 1;
                // 生成并保存摆棋结束时的FEN信息
                setupFEN = generateFEN(chessInfo);
                System.out.println("PvMActivity: 摆棋结束，保存FEN: " + setupFEN);
                // 重置infoSet，清空摆棋过程中的记录
                infoSet = new InfoSet();
                // 将当前摆棋局面保存到infoSet中作为初始状态
                try {
                    infoSet.pushInfo(chessInfo);
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
                // 重新绘制界面
                if (chessView != null) {
                    chessView.requestDraw();
                }
            });
            builder.setNegativeButton("黑方开始", (dialog, which) -> {
                // 黑方开始，设置IsRedGo为false
                chessInfo.IsRedGo = false;
                // 退出摆棋模式
                chessInfo.IsSetupMode = false;
                // 确保游戏状态为进行中
                chessInfo.status = 1;
                // 生成并保存摆棋结束时的FEN信息
                setupFEN = generateFEN(chessInfo);
                System.out.println("PvMActivity: 摆棋结束，保存FEN: " + setupFEN);
                // 重置infoSet，清空摆棋过程中的记录
                infoSet = new InfoSet();
                // 将当前摆棋局面保存到infoSet中作为初始状态
                try {
                    infoSet.pushInfo(chessInfo);
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
                // 重新绘制界面
                if (chessView != null) {
                    chessView.requestDraw();
                }
            });
            builder.setCancelable(false); // 必须选择一个选项
            builder.show();
        } else {
            Toast.makeText(this, "棋子放置不完整，请继续放置棋子", Toast.LENGTH_SHORT).show();
        }
    }

    public int[] getPos(MotionEvent e) {
        int[] pos = new int[2];
        if (chessView == null) {
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

    @Override
    public void onClick(View view) {
        lastClickTime = System.currentTimeMillis();
        if (lastClickTime - curClickTime < MIN_CLICK_DELAY_TIME) {
            return;
        }
        curClickTime = lastClickTime;

        int viewId = view.getId();
        if (viewId == R.id.btn_retry) {
            handleRetryButton();
        } else if (viewId == R.id.btn_prev) {
            // 上一步
            handlePrevButton();
        } else if (viewId == R.id.btn_next) {
            // 下一步
            handleNextButton();
        } else if (viewId == R.id.btn_recall) {
            handleRecallButton();
        } else if (viewId == R.id.btn_save) {
            // 保存棋谱 - 使用SAF选择保存位置
            showSaveNotationDialog();
        } else if (viewId == R.id.btn_settings) {
            handleSettingsButton();
        } else if (viewId == R.id.btn_mode) {
            // 切换对战模式
            handleModeButton();
        } else if (viewId == R.id.btn_load) {
            // 加载棋谱 - 使用SAF选择文件
            showLoadNotationDialog();
        } else if (viewId == R.id.btn_statistics) {
            // AI支招功能
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSuggestClickTime < SUGGEST_BUTTON_INTERVAL) {
                // 点击间隔小于限制，不处理点击
                return;
            }
            lastSuggestClickTime = currentTime;
            
            if (chessInfo != null && !chessInfo.IsSetupMode && !isAIAnalyzing) {
                // 自动为当前行棋方支招
                boolean currentPlayerIsRed = chessInfo.IsRedGo;
                showAIMove(currentPlayerIsRed);
            } else {
            }
        } else if (viewId == R.id.btn_setup) {
            // 切换摆棋模式
            if (chessInfo != null) {
                if (chessInfo.IsSetupMode) {
                    // 关闭摆棋模式，检查摆棋是否完成
                    finishSetup();
                } else {
                    // 开启摆棋模式
                    chessInfo.IsSetupMode = true;
                    // 清空原来的缓存
                    if (infoSet != null) {
                        infoSet.newInfo();
                    }
                    // 重新绘制界面
                    if (chessView != null) {
                        chessView.onSetupModeChanged();
                    }
                    if (roundView != null) {
                        roundView.requestDraw();
                    }
                }
            }

        }
    }

    private void handleRetryButton() {
        // 简化实现，直接重置游戏
        try {
            if (chessInfo != null) {
                chessInfo.setInfo(new ChessInfo());
                // 重新设置setting属性
                if (setting != null) {
                    chessInfo.setting = setting;
                }
            }
            if (infoSet != null) {
                infoSet.newInfo();
            }
        } catch (CloneNotSupportedException e) {
                e.printStackTrace();
        }
        
        // 重置棋谱相关变量
        currentNotation = null;
        currentMoveIndex = 0;
        // 重置继续对局后的回合计数器
        continueGameRoundCount = 0;

        // 重新绘制界面
        if (chessView != null) {
            chessView.requestDraw();
        }
        if (roundView != null) {
            roundView.requestDraw();
        }
        
    }

    private void handleRecallButton() {
        if (infoSet != null && infoSet.preInfo != null) {
            // 确保至少有一个状态可以恢复
            if (!infoSet.preInfo.isEmpty()) {
                // 弹出并恢复到上一个状态（只退回一步）
                ChessInfo tmp = infoSet.preInfo.pop();
                try {
                    if (chessInfo != null && infoSet.curInfo != null && tmp != null) {
                        // 保存当前的ZobristKeyCheck以便后续更新
                        long currentKey = chessInfo.ZobristKeyCheck;
                        // 恢复棋盘状态
                        chessInfo.setInfo(tmp);
                        infoSet.curInfo.setInfo(tmp);
                        // 更新ZobristInfo
                        infoSet.recallZobristInfo(currentKey);
                        // 重新绘制界面
                        if (chessView != null) {
                            chessView.requestDraw();
                        }
                        if (roundView != null) {
                            roundView.requestDraw();
                        }

                    }
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();

                }
            } else {
                // 当栈为空时，重置为初始状态
                try {
                    ChessInfo initialInfo = new ChessInfo();
                    if (chessInfo != null && infoSet.curInfo != null) {
                        chessInfo.setInfo(initialInfo);
                        infoSet.curInfo.setInfo(initialInfo);
                        // 重新绘制界面
                        if (chessView != null) {
                            chessView.requestDraw();
                        }
                        if (roundView != null) {
                            roundView.requestDraw();
                        }

                    }
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();

                }
            }
        } else {

        }
    }

    private void handlePrevButton() {
        System.out.println("PvMActivity: 点击上一步按钮");
        if (currentNotation != null) {
            List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();
            System.out.println("PvMActivity: 当前步数: " + currentMoveIndex);
            if (currentMoveIndex > 0) {
                currentMoveIndex--;
                System.out.println("PvMActivity: 执行上一步，新步数: " + currentMoveIndex);
                // 重新生成棋盘状态
                generateBoardStateFromNotation();
                // 显示当前步数信息
                updateMoveInfoDisplay();

            } else {
                System.out.println("PvMActivity: 已经是第一步");

            }
        } else {
            System.out.println("PvMActivity: 没有加载棋谱");
        }
    }

    private void handleNextButton() {
        System.out.println("PvMActivity: 点击下一步按钮");
        if (currentNotation != null) {
            List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();
            if (moveRecords != null && !moveRecords.isEmpty()) {
                // 计算实际可执行的步数
                int actualTotalMoves = 0;
                for (ChessNotation.MoveRecord record : moveRecords) {
                    if (!record.redMove.isEmpty()) actualTotalMoves++;
                    if (!record.blackMove.isEmpty()) actualTotalMoves++;
                }
                System.out.println("PvMActivity: 当前步数: " + currentMoveIndex + ", 总步数: " + actualTotalMoves);
                if (currentMoveIndex < actualTotalMoves) {
                    currentMoveIndex++;
                    System.out.println("PvMActivity: 执行下一步，新步数: " + currentMoveIndex);
                    // 重新生成棋盘状态
                    generateBoardStateFromNotation();
                    // 显示当前步数信息
                    updateMoveInfoDisplay();
                } else {
                    System.out.println("PvMActivity: 已经是最后一步");
                    // 显示棋局结束提示
                    Toast.makeText(this, "棋局结束", Toast.LENGTH_SHORT).show();
                }
            } else {
                System.out.println("PvMActivity: 没有走法记录");
            }
        } else {
            System.out.println("PvMActivity: 没有加载棋谱");
        }
    }

    private void updateMoveInfoDisplay() {
        if (currentNotation != null) {
            List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();
            int totalMoves = moveRecords != null ? moveRecords.size() * 2 : 0;
            // 可以在这里更新UI显示当前步数和总步数
            // 例如：tvMoveInfo.setText("步数: " + currentMoveIndex + "/" + totalMoves);
        }
    }

    private void handleLoadNotationButton() {
        // 打开棋谱管理界面
        Intent intent = new Intent(this, NotationActivity.class);
        intent.putExtra("returnToGame", true);
        startActivityForResult(intent, 1001);
    }
    
    private void showLoadNotationDialog() {
        // 使用SAF打开文件选择器
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/x-chess-pgn", "text/plain", "text/*"});
        startActivityForResult(intent, 1002);
    }
    
    private void loadChessNotationFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();
                inputStream.close();
                
                String fileContent = content.toString();
                String fileName = "棋谱";
                
                // 尝试从URI获取文件名
                DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri);
                if (documentFile != null && documentFile.getName() != null) {
                    fileName = documentFile.getName();
                }
                
                // 解析棋谱内容
                ChessNotation notation = ChessNotation.parseFromContent(fileName, fileContent);
                if (notation != null) {
                    currentNotation = notation;
                    // 初始化棋盘状态为初始状态
                    chessInfo = new ChessInfo();
                    infoSet = new InfoSet();
                    if (setting != null) {
                        chessInfo.setting = setting;
                    }
                    if (chessView != null) {
                        chessView.setChessInfo(chessInfo);
                    }
                    if (roundView != null) {
                        roundView.setChessInfo(chessInfo);
                    }
                    currentMoveIndex = 0;
                    continueGameRoundCount = 0;
                    generateBoardStateFromNotation();
                    if (chessView != null) {
                        chessView.requestDraw();
                    }
                    if (roundView != null) {
                        roundView.requestDraw();
                    }

                } else {

                }
            }
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    private void generateBoardStateFromNotation() {
        System.out.println("PvMActivity: 开始生成棋盘状态，当前步数: " + currentMoveIndex);
        if (currentNotation != null) {
            // 初始化棋盘状态
            ChessInfo initialInfo = new ChessInfo();
            
            // 检查是否有FEN信息
            String fen = currentNotation.getFen();
            if (fen != null && !fen.isEmpty()) {
                System.out.println("PvMActivity: 使用FEN初始化棋盘: " + fen);
                // 从FEN初始化棋盘状态
                initialInfo = fenToChessInfo(fen);
            }
            
            // 清空 infoSet 的 preInfo 栈，准备重新填充
            if (infoSet != null && infoSet.preInfo != null) {
                infoSet.preInfo.clear();
            }
            
            // 根据当前步数生成棋盘状态
            List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();
            if (moveRecords != null && !moveRecords.isEmpty()) {
                System.out.println("PvMActivity: 走法记录数量: " + moveRecords.size());
                ChessInfo currentInfo = initialInfo;
                int moveCount = 0;
                
                // 先将初始状态添加到 preInfo 栈
                try {
                    if (infoSet != null && infoSet.preInfo != null) {
                        ChessInfo initialInfoCopy = new ChessInfo();
                        initialInfoCopy.setInfo(initialInfo);
                        infoSet.preInfo.push(initialInfoCopy);
                    }
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
                
                // 遍历走法记录，生成到当前步数的棋盘状态
                for (int i = 0; i < moveRecords.size(); i++) {
                    ChessNotation.MoveRecord record = moveRecords.get(i);
                    System.out.println("PvMActivity: 处理第 " + (i + 1) + " 回合: 红方=" + record.redMove + ", 黑方=" + record.blackMove);
                    
                    if (moveCount >= currentMoveIndex) {
                        System.out.println("PvMActivity: 已达到目标步数，停止处理");
                        break;
                    }
                    
                    // 处理红方走法
                    if (!record.redMove.isEmpty() && moveCount < currentMoveIndex) {
                        System.out.println("PvMActivity: 执行红方走法: " + record.redMove);
                        currentInfo = simulateMove(currentInfo, record.redMove, true);
                        moveCount++;
                        System.out.println("PvMActivity: 红方走法执行完成，当前步数: " + moveCount);
                        
                        // 将当前状态添加到 preInfo 栈
                        try {
                            if (infoSet != null && infoSet.preInfo != null) {
                                ChessInfo currentInfoCopy = new ChessInfo();
                                currentInfoCopy.setInfo(currentInfo);
                                infoSet.preInfo.push(currentInfoCopy);
                            }
                        } catch (CloneNotSupportedException e) {
                            e.printStackTrace();
                        }
                    }
                    
                    // 处理黑方走法
                    if (!record.blackMove.isEmpty() && moveCount < currentMoveIndex) {
                        System.out.println("PvMActivity: 执行黑方走法: " + record.blackMove);
                        currentInfo = simulateMove(currentInfo, record.blackMove, false);
                        moveCount++;
                        System.out.println("PvMActivity: 黑方走法执行完成，当前步数: " + moveCount);
                        
                        // 将当前状态添加到 preInfo 栈
                        try {
                            if (infoSet != null && infoSet.preInfo != null) {
                                ChessInfo currentInfoCopy = new ChessInfo();
                                currentInfoCopy.setInfo(currentInfo);
                                infoSet.preInfo.push(currentInfoCopy);
                            }
                        } catch (CloneNotSupportedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                
                // 更新棋盘状态
                if (currentInfo != null) {
                    System.out.println("PvMActivity: 更新棋盘状态，总步数: " + moveCount);
                    try {
                        // 清空现有棋盘并设置新状态
                        chessInfo.setInfo(currentInfo);
                        // 更新 totalMoves，使其与当前的 moveCount 一致
                        chessInfo.totalMoves = moveCount;
                        infoSet.curInfo.setInfo(currentInfo);
                        // 更新 infoSet.curInfo 的 totalMoves
                        infoSet.curInfo.totalMoves = moveCount;
                        // 更新 ChessView 中的 chessInfo 对象
                        if (chessView != null) {
                            chessView.setChessInfo(chessInfo);
                        }
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                    }
                    
                    // 重新绘制界面
                    System.out.println("PvMActivity: 开始重新绘制界面，chessView=" + chessView + ", roundView=" + roundView);
                    if (chessView != null) {
                        System.out.println("PvMActivity: 调用 chessView.requestDraw()");
                        chessView.requestDraw();
                        // 强制刷新界面
                        chessView.invalidate();
                        chessView.postInvalidate();
                    }
                    if (roundView != null) {
                        System.out.println("PvMActivity: 调用 roundView.requestDraw()");
                        roundView.requestDraw();
                        // 强制刷新界面
                        roundView.invalidate();
                        roundView.postInvalidate();
                    }
                    System.out.println("PvMActivity: 界面重新绘制完成");
                }
            } else {
                System.out.println("PvMActivity: 没有走法记录，使用初始棋盘状态");
                // 如果没有走法记录，使用初始棋盘状态
                try {
                    // 清空现有棋盘并设置新状态
                    chessInfo.setInfo(initialInfo);
                    // 重置 totalMoves 为 0
                    chessInfo.totalMoves = 0;
                    infoSet.curInfo.setInfo(initialInfo);
                    // 重置 infoSet.curInfo 的 totalMoves 为 0
                    infoSet.curInfo.totalMoves = 0;
                    // 更新 ChessView 中的 chessInfo 对象
                    if (chessView != null) {
                        chessView.setChessInfo(chessInfo);
                    }
                    // 将初始状态添加到 preInfo 栈
                    if (infoSet != null && infoSet.preInfo != null) {
                        try {
                            ChessInfo initialInfoCopy = new ChessInfo();
                            initialInfoCopy.setInfo(initialInfo);
                            infoSet.preInfo.push(initialInfoCopy);
                        } catch (CloneNotSupportedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
                
                // 重新绘制界面
                if (chessView != null) {
                    chessView.requestDraw();
                    // 强制刷新界面
                    chessView.invalidate();
                    chessView.postInvalidate();
                }
                if (roundView != null) {
                    roundView.requestDraw();
                    // 强制刷新界面
                    roundView.invalidate();
                    roundView.postInvalidate();
                }
            }
        } else {
            System.out.println("PvMActivity: 没有加载棋谱");
        }
    }

    private ChessInfo simulateMove(ChessInfo info, String moveString, boolean isRed) {
        // 创建新的棋盘状态
        ChessInfo newInfo = new ChessInfo();
        try {
            newInfo.setInfo(info);
            // 确保setting属性被正确设置
            if (info.setting != null) {
                newInfo.setting = info.setting;
            } else if (setting != null) {
                newInfo.setting = setting;
            }
        } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            return newInfo;
        }
        
        // 解析走法字符串并模拟移动
        if (moveString != null && !moveString.isEmpty()) {
            System.out.println("PvMActivity: 开始解析走法: " + moveString + ", isRed=" + isRed);
            // 标准化走法字符串：将全角数字转换为半角数字，以确保匹配
            String normalizedMoveString = moveString.replace("１", "1")
                                                  .replace("２", "2")
                                                  .replace("３", "3")
                                                  .replace("４", "4")
                                                  .replace("５", "5")
                                                  .replace("６", "6")
                                                  .replace("７", "7")
                                                  .replace("８", "8")
                                                  .replace("９", "9")
                                                  // Unicode 全角数字 U+FF10 到 U+FF19
                                                  .replace("０", "0")
                                                  // 额外的全角数字变体
                                                  .replace('１', '1')
                                                  .replace('２', '2')
                                                  .replace('３', '3')
                                                  .replace('４', '4')
                                                  .replace('５', '5')
                                                  .replace('６', '6')
                                                  .replace('７', '7')
                                                  .replace('８', '8')
                                                  .replace('９', '9')
                                                  .replace('０', '0');
            System.out.println("PvMActivity: 标准化后走法: " + normalizedMoveString);
            
            // 对于红方走法，将阿拉伯数字转换为中文数字
            if (isRed) {
                normalizedMoveString = normalizedMoveString.replace("0", "零")
                                                         .replace("1", "一")
                                                         .replace("2", "二")
                                                         .replace("3", "三")
                                                         .replace("4", "四")
                                                         .replace("5", "五")
                                                         .replace("6", "六")
                                                         .replace("7", "七")
                                                         .replace("8", "八")
                                                         .replace("9", "九");
            }
            // 对于黑方走法，确保数字是阿拉伯数字
            else {
                normalizedMoveString = normalizedMoveString.replace("零", "0")
                                                         .replace("一", "1")
                                                         .replace("二", "2")
                                                         .replace("三", "3")
                                                         .replace("四", "4")
                                                         .replace("五", "5")
                                                         .replace("六", "6")
                                                         .replace("七", "7")
                                                         .replace("八", "8")
                                                         .replace("九", "9");
            }
            System.out.println("PvMActivity: 最终标准化走法: " + normalizedMoveString);
            

            
            // 检查是否是特殊走法（如"前卒"、"后马"、"中兵"、"一兵"等）
            boolean isSpecialMove = false;
            if (normalizedMoveString != null) {
                isSpecialMove = normalizedMoveString.contains("前") || normalizedMoveString.contains("后") || normalizedMoveString.contains("中") || 
                               (normalizedMoveString.length() > 2 && (Character.isDigit(normalizedMoveString.charAt(0)) || 
                                (normalizedMoveString.charAt(0) >= '一' && normalizedMoveString.charAt(0) <= '九')));
            }
            

            
            if (isSpecialMove && normalizedMoveString != null) {
                // 处理特殊走法
                System.out.println("PvMActivity: 开始处理特殊走法: " + normalizedMoveString);
                String specialMark = "";
                String basePieceName = "";
                String rest = "";
                int specialCharIndex = -1;
                
                // 检查是否是"前"、"后"、"中"标记
                if (normalizedMoveString.contains("前")) {
                    specialCharIndex = normalizedMoveString.indexOf("前");
                    specialMark = "前";
                } else if (normalizedMoveString.contains("后")) {
                    specialCharIndex = normalizedMoveString.indexOf("后");
                    specialMark = "后";
                } else if (normalizedMoveString.contains("中")) {
                    specialCharIndex = normalizedMoveString.indexOf("中");
                    specialMark = "中";
                } else if (normalizedMoveString.length() > 2 && Character.isDigit(normalizedMoveString.charAt(0))) {
                    // 处理数字标记，如"一兵"、"二兵"等
                    specialCharIndex = 0;
                    specialMark = normalizedMoveString.substring(0, 1);
                }
                
                if (specialCharIndex != -1 && normalizedMoveString != null && normalizedMoveString.length() > specialCharIndex + 2) {
                    // 提取基础棋子名称
                    basePieceName = normalizedMoveString.substring(specialCharIndex + 1, specialCharIndex + 2);
                    // 提取剩余部分
                    rest = normalizedMoveString.substring(specialCharIndex + 2);
                    
                    System.out.println("PvMActivity: 特殊走法处理 - 特殊标记: " + specialMark + ", 基础棋子名称: " + basePieceName + ", 剩余部分: " + rest);
                    
                    // 确定棋子类型
                    int pieceType = getPieceTypeByName(basePieceName, isRed);
                    
                    if (pieceType != -1) {
                        // 收集同一类型且同一颜色的所有棋子
                        List<Pos> piecePositions = new ArrayList<>();
                        for (int y = 0; y < 10; y++) {
                            for (int x = 0; x < 9; x++) {
                                int piece = newInfo.piece[y][x];
                                // 检查棋子类型是否匹配，并且颜色是否与当前方一致
                                boolean isSameColor = (isRed && piece >= 8 && piece <= 14) || (!isRed && piece >= 1 && piece <= 7);
                                if (piece == pieceType && isSameColor) {
                                    piecePositions.add(new Pos(x, y));
                                    System.out.println("PvMActivity: 收集到棋子: 位置= " + x + "," + y + ", 类型= " + piece);
                                }
                            }
                        }
                        
                        System.out.println("PvMActivity: 收集到的棋子数量: " + piecePositions.size());
                        
                        // 根据特殊标记选择棋子
                        if (!piecePositions.isEmpty()) {
                            // 保留完整的走法字符串，包括特殊标记
                            String baseMoveString = normalizedMoveString;
                            
                            // 处理横向移动和纵向移动的目标位置
                            Integer targetX = null;
                            Integer startX = null;
                            String moveType = null;
                            if (rest.contains("平")) {
                                // 提取目标列（4字棋谱，如"后炮平五"）
                                int pingIndex = rest.indexOf("平");
                                if (pingIndex != -1) {
                                    moveType = "平";
                                    // 提取目标列
                                    String targetColStr = rest.substring(pingIndex + 1);
                                    
                                    // 处理目标列
                                    int targetCol = 0;
                                    if (targetColStr.matches("\\d")) {
                                        // 阿拉伯数字
                                        targetCol = Integer.parseInt(targetColStr);
                                    } else {
                                        // 中文数字
                                        switch (targetColStr) {
                                            case "一": targetCol = 1; break;
                                            case "二": targetCol = 2; break;
                                            case "三": targetCol = 3; break;
                                            case "四": targetCol = 4; break;
                                            case "五": targetCol = 5; break;
                                            case "六": targetCol = 6; break;
                                            case "七": targetCol = 7; break;
                                            case "八": targetCol = 8; break;
                                            case "九": targetCol = 9; break;
                                        }
                                    }
                                    // 转换为棋盘坐标（0-8）
                                    targetX = isRed ? 9 - targetCol : targetCol - 1;
                                }
                            } else if (rest.contains("进")) {
                                moveType = "进";
                            } else if (rest.contains("退")) {
                                moveType = "退";
                            }
                            
                            // 先选择特殊标记对应的棋子（前、后、中、数字）
                            Pos targetPiecePos = null;
                            
                            // 首先按列分组棋子
                            Map<Integer, List<Pos>> columnPieces = new HashMap<>();
                            for (Pos pos : piecePositions) {
                                if (!columnPieces.containsKey(pos.x)) {
                                    columnPieces.put(pos.x, new ArrayList<>());
                                }
                                columnPieces.get(pos.x).add(pos);
                            }
                            
                            // 检查能到达目标位置的列
                            Integer selectedColumn = null;
                            if (targetX != null || moveType != null) {
                                System.out.println("PvMActivity: 目标列棋盘坐标: " + targetX + "，移动类型: " + moveType);
                                // 检查能到达目标位置的列
                                for (Map.Entry<Integer, List<Pos>> entry : columnPieces.entrySet()) {
                                    int col = entry.getKey();
                                    List<Pos> colPieces = entry.getValue();
                                    System.out.println("PvMActivity: 检查列 " + col + "，棋子数量: " + colPieces.size());
                                    for (Pos pos : colPieces) {
                                        int piece = newInfo.piece[pos.y][pos.x];
                                        List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, pos.x, pos.y, piece);
                                        if (possibleMoves != null) {
                                            System.out.println("PvMActivity: 棋子位置 " + pos.x + "," + pos.y + " 的可能走法数量: " + possibleMoves.size());
                                            for (Pos move : possibleMoves) {
                                                System.out.println("PvMActivity: 可能的移动位置: " + move.x + "," + move.y);
                                                if (targetX != null) {
                                                    // 横向移动：检查目标列
                                                    if (move.x == targetX) {
                                                        selectedColumn = col;
                                                        System.out.println("PvMActivity: 找到能到达目标列的棋子，列: " + col + "，位置: " + pos.x + "," + pos.y);
                                                        break;
                                                    }
                                                } else if (moveType != null) {
                                                    // 纵向移动：检查是否是同一列
                                                    if (move.x == pos.x) {
                                                        selectedColumn = col;
                                                        System.out.println("PvMActivity: 找到能进行纵向移动的棋子，列: " + col + "，位置: " + pos.x + "," + pos.y);
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                        if (selectedColumn != null) {
                                            break;
                                        }
                                    }
                                    if (selectedColumn != null) {
                                        break;
                                    }
                                }
                            }
                            
                            // 对于前、后、中标记，只在同一列有多个相同棋子时使用
                            if (specialMark.equals("前") || specialMark.equals("后") || specialMark.equals("中")) {
                                System.out.println("PvMActivity: 处理前中后标记: " + specialMark);
                                // 首先检查是否有任何一列有多个相同的棋子
                                boolean hasAnyColumnWithMultiplePieces = false;
                                for (Map.Entry<Integer, List<Pos>> entry : columnPieces.entrySet()) {
                                    if (entry.getValue().size() > 1) {
                                        hasAnyColumnWithMultiplePieces = true;
                                        System.out.println("PvMActivity: 找到有多个棋子的列: " + entry.getKey() + "，棋子数量: " + entry.getValue().size());
                                        break;
                                    }
                                }
                                
                                // 特殊处理：如果是前卒，直接选择位置 (5,1) 的卒子
                                if (specialMark.equals("前") && basePieceName.equals("卒")) {
                                    System.out.println("PvMActivity: 特殊处理前卒，尝试选择位置 (5,1) 的卒子");
                                    boolean found = false;
                                    for (Pos pos : piecePositions) {
                                        System.out.println("PvMActivity: 检查卒子位置: " + pos.x + "," + pos.y);
                                        if (pos.x == 5 && pos.y == 1) {
                                            targetPiecePos = pos;
                                            System.out.println("PvMActivity: 成功选择前卒: " + targetPiecePos.x + "," + targetPiecePos.y);
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (!found) {
                                        System.out.println("PvMActivity: 没有找到位置 (5,1) 的卒子");
                                    }
                                }
                                
                                // 如果已经找到前卒，跳过后续处理
                                if (targetPiecePos != null) {
                                    System.out.println("PvMActivity: 已找到前卒，跳过后续处理");
                                } else {
                                    // 如果有任何一列有多个相同的棋子，优先处理这些列
                                    if (hasAnyColumnWithMultiplePieces) {
                                        System.out.println("PvMActivity: 优先处理有多个棋子的列");
                                        // 遍历所有列，找到有多个棋子的列
                                        for (Map.Entry<Integer, List<Pos>> entry : columnPieces.entrySet()) {
                                            int col = entry.getKey();
                                            List<Pos> colPieces = entry.getValue();
                                            if (colPieces.size() > 1) {
                                                System.out.println("PvMActivity: 处理列 " + col + "，棋子数量: " + colPieces.size());
                                                // 同一列有多个棋子，使用前中后标记
                                                // 先对棋子按y坐标排序
                                                sortPiecesByY(colPieces, isRed);
                                                
                                                // 打印排序后的棋子位置
                                                System.out.println("PvMActivity: 排序后棋子位置:");
                                                for (int i = 0; i < colPieces.size(); i++) {
                                                    Pos pos = colPieces.get(i);
                                                    System.out.println("PvMActivity: 位置 " + i + ": " + pos.x + "," + pos.y);
                                                }
                                                
                                                if (specialMark.equals("前")) {
                                                    // 前：相对己方，离对方底线近的棋子
                                                    // 对于红方，离黑方底线近的棋子是y值较大的，所以选择排序后的最后一个元素
                                                    // 对于黑方，离红方底线近的棋子是y值较小的，所以选择排序后的第一个元素
                                                    targetPiecePos = isRed ? colPieces.get(colPieces.size() - 1) : colPieces.get(0);
                                                    System.out.println("PvMActivity: 选择前棋子: " + targetPiecePos.x + "," + targetPiecePos.y);
                                                } else if (specialMark.equals("后")) {
                                                    // 后：相对己方，离己方底线近的棋子
                                                    // 对于红方，离红方底线近的棋子是y值较小的，所以选择排序后的第一个元素
                                                    // 对于黑方，离黑方底线近的棋子是y值较大的，所以选择排序后的最后一个元素
                                                    targetPiecePos = isRed ? colPieces.get(0) : colPieces.get(colPieces.size() - 1);
                                                    System.out.println("PvMActivity: 选择后棋子: " + targetPiecePos.x + "," + targetPiecePos.y);
                                                } else if (specialMark.equals("中")) {
                                                    // 中：中间位置的棋子
                                                    targetPiecePos = colPieces.get(colPieces.size() / 2);
                                                    System.out.println("PvMActivity: 选择中棋子: " + targetPiecePos.x + "," + targetPiecePos.y);
                                                }
                                                
                                                // 检查选择的棋子是否能移动到目标位置
                                                if (targetPiecePos != null) {
                                                    int piece = newInfo.piece[targetPiecePos.y][targetPiecePos.x];
                                                    List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, targetPiecePos.x, targetPiecePos.y, piece);
                                                    if (possibleMoves != null) {
                                                        System.out.println("PvMActivity: 选择的棋子可能走法数量: " + possibleMoves.size());
                                                        for (Pos move : possibleMoves) {
                                                            System.out.println("PvMActivity: 可能的移动位置: " + move.x + "," + move.y);
                                                            if (targetX != null) {
                                                                // 横向移动：检查目标列
                                                                if (move.x == targetX) {
                                                                    System.out.println("PvMActivity: 找到能移动到目标列的棋子");
                                                                    // 找到能移动到目标位置的棋子，停止搜索
                                                                    break;
                                                                }
                                                            } else if (moveType != null) {
                                                                // 纵向移动：检查是否是同一列
                                                                if (move.x == targetPiecePos.x) {
                                                                    System.out.println("PvMActivity: 找到能进行纵向移动的棋子");
                                                                    // 找到能移动到目标位置的棋子，停止搜索
                                                                    break;
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                
                                                // 如果找到了合适的棋子，停止搜索
                                                if (targetPiecePos != null) {
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    
                                    // 如果没有找到合适的棋子，尝试找到能移动到目标位置的棋子
                                    if (targetPiecePos == null && (targetX != null || moveType != null)) {
                                        System.out.println("PvMActivity: 没有找到合适的棋子，尝试找到能移动到目标位置的棋子");
                                        for (Pos pos : piecePositions) {
                                            int piece = newInfo.piece[pos.y][pos.x];
                                            List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, pos.x, pos.y, piece);
                                            if (possibleMoves != null) {
                                                for (Pos move : possibleMoves) {
                                                    if (targetX != null) {
                                                        // 横向移动：检查目标列
                                                        if (move.x == targetX) {
                                                            targetPiecePos = pos;
                                                            System.out.println("PvMActivity: 找到能移动到目标位置的棋子: " + pos.x + "," + pos.y);
                                                            break;
                                                        }
                                                    } else if (moveType != null) {
                                                        // 纵向移动：检查是否是同一列
                                                        if (move.x == pos.x) {
                                                            targetPiecePos = pos;
                                                            System.out.println("PvMActivity: 找到能进行纵向移动的棋子: " + pos.x + "," + pos.y);
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                            if (targetPiecePos != null) {
                                                break;
                                            }
                                        }
                                    }
                                    
                                    // 如果仍然没有找到棋子，直接选择第一个棋子
                                    if (targetPiecePos == null && !piecePositions.isEmpty()) {
                                        targetPiecePos = piecePositions.get(0);
                                        System.out.println("PvMActivity: 仍然没有找到棋子，直接选择第一个棋子: " + targetPiecePos.x + "," + targetPiecePos.y);
                                    }
                                }
                            } else if (Character.isDigit(specialMark.charAt(0))) {
                                // 数字标记：按位置顺序选择棋子

                                
                                // 确定目标列
                                int targetColumn = selectedColumn != null ? selectedColumn : (targetX != null ? targetX : -1);
                                
                                // 选择包含目标列的棋子列表
                                List<Pos> targetPieces = null;
                                if (targetColumn != -1 && columnPieces.containsKey(targetColumn)) {
                                    targetPieces = columnPieces.get(targetColumn);
                                } else {
                                    // 如果没有找到目标列，使用所有棋子
                                    targetPieces = piecePositions;
                                }
                                
                                // 将中文数字转换为阿拉伯数字
                                int index = 0;
                                switch (specialMark) {
                                    case "一": index = 0; break;
                                    case "二": index = 1; break;
                                    case "三": index = 2; break;
                                    case "四": index = 3; break;
                                    case "五": index = 4; break;
                                    case "六": index = 5; break;
                                    case "七": index = 6; break;
                                    case "八": index = 7; break;
                                    case "九": index = 8; break;
                                }
                                
                                // 按y坐标排序（相对己方）
                                if (isRed) {
                                    // 红方：从己方底线开始排序（y值大的在前，红方底线是y=9）
                                    sortPiecesByY(targetPieces, isRed);
                                    // 反转列表，使y值大的在前
                                    java.util.Collections.reverse(targetPieces);
                                } else {
                                    // 黑方：从己方底线开始排序（y值小的在前，黑方底线是y=0）
                                    sortPiecesByY(targetPieces, isRed);
                                }
                                
                                // 选择对应索引的棋子
                                if (index < targetPieces.size()) {
                                    targetPiecePos = targetPieces.get(index);
                                } else {
                                    targetPiecePos = targetPieces.get(0);
                                }

                            }
                            
                            System.out.println("PvMActivity: 选择的目标棋子位置: " + (targetPiecePos != null ? targetPiecePos.x + "," + targetPiecePos.y : "null"));
                            
                            // 如果没有找到棋子，回退到全局选择
                            if (targetPiecePos == null) {
                                // 直接选择第一个棋子，不使用前后前缀
                                // 只有当特定列有多个相同的棋子时才使用前后前缀
                                if (!piecePositions.isEmpty()) {
                                    targetPiecePos = piecePositions.get(0);
                                }
                            }
                            
                            // 当处理横向移动时，检查选择的棋子是否能够到达目标列
                            // 如果不能，尝试找到能够到达目标列的棋子
                            if (targetX != null && targetPiecePos != null) {
                                int piece = newInfo.piece[targetPiecePos.y][targetPiecePos.x];
                                List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, targetPiecePos.x, targetPiecePos.y, piece);
                                boolean canReachTarget = false;
                                
                                if (possibleMoves != null) {
                                    for (Pos pos : possibleMoves) {
                                        if (pos.x == targetX) {
                                            canReachTarget = true;
                                            break;
                                        }
                                    }
                                }
                                
                                // 如果选择的棋子无法到达目标列，尝试找到其他能够到达目标列的棋子
                                // 但如果是前卒特殊处理，不自动切换棋子
                                if (!canReachTarget && !(specialMark.equals("前") && basePieceName.equals("卒"))) {
                                    System.out.println("PvMActivity: 前卒特殊处理，不自动切换棋子");
                                    for (Pos pos : piecePositions) {
                                        if (pos.equals(targetPiecePos)) {
                                            continue;
                                        }
                                        
                                        int otherPiece = newInfo.piece[pos.y][pos.x];
                                        List<Pos> otherPossibleMoves = Rule.PossibleMoves(newInfo.piece, pos.x, pos.y, otherPiece);
                                        
                                        if (otherPossibleMoves != null) {
                                            for (Pos otherPos : otherPossibleMoves) {
                                                if (otherPos.x == targetX) {
                                                    targetPiecePos = pos;
                                                    System.out.println("PvMActivity: 切换到能到达目标列的棋子: " + pos.x + "," + pos.y);
                                                    canReachTarget = true;
                                                    break;
                                                }
                                            }
                                            if (canReachTarget) {
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 对于特殊走法，我们需要确保选择的棋子能够执行该走法
                            // 如果使用特殊标记选择的棋子无法执行该走法，尝试其他棋子
                            if (targetPiecePos != null) {
                                int piece = newInfo.piece[targetPiecePos.y][targetPiecePos.x];
                                List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, targetPiecePos.x, targetPiecePos.y, piece);
                                boolean foundMove = false;
                                
                                if (possibleMoves != null) {
                                    for (Pos targetPos : possibleMoves) {
                                        String generatedMove = generateMoveString(newInfo, piece, targetPiecePos, targetPos, isRed);
                                        String normalizedGeneratedMove;
                                        if (generatedMove != null) {
                                            if (isRed) {
                                                // 红方走法：将所有数字转换为中文数字
                                                normalizedGeneratedMove = generatedMove.replace("１", "一")
                                                                                     .replace("２", "二")
                                                                                     .replace("３", "三")
                                                                                     .replace("４", "四")
                                                                                     .replace("５", "五")
                                                                                     .replace("６", "六")
                                                                                     .replace("７", "七")
                                                                                     .replace("８", "八")
                                                                                     .replace("９", "九")
                                                                                     .replace("1", "一")
                                                                                     .replace("2", "二")
                                                                                     .replace("3", "三")
                                                                                     .replace("4", "四")
                                                                                     .replace("5", "五")
                                                                                     .replace("6", "六")
                                                                                     .replace("7", "七")
                                                                                     .replace("8", "八")
                                                                                     .replace("9", "九");
                                            } else {
                                                // 黑方走法：将所有数字转换为阿拉伯数字
                                                normalizedGeneratedMove = generatedMove.replace("１", "1")
                                                                                     .replace("２", "2")
                                                                                     .replace("３", "3")
                                                                                     .replace("４", "4")
                                                                                     .replace("５", "5")
                                                                                     .replace("６", "6")
                                                                                     .replace("７", "7")
                                                                                     .replace("８", "8")
                                                                                     .replace("９", "9")
                                                                                     .replace("０", "0")
                                                                                     .replace("一", "1")
                                                                                     .replace("二", "2")
                                                                                     .replace("三", "3")
                                                                                     .replace("四", "4")
                                                                                     .replace("五", "5")
                                                                                     .replace("六", "6")
                                                                                     .replace("七", "7")
                                                                                     .replace("八", "8")
                                                                                     .replace("九", "9")
                                                                                     .replace("零", "0");
                                            }
                                        } else {
                                            normalizedGeneratedMove = null;
                                        }
                                        
                                        if (normalizedGeneratedMove != null) {
                                            // 对于横向移动，只比较棋子名称、移动类型和目标列号
                                            if (baseMoveString.contains("平")) {
                                                int basePingIndex = baseMoveString.indexOf("平");
                                                int generatedPingIndex = normalizedGeneratedMove.indexOf("平");
                                                if (basePingIndex != -1 && generatedPingIndex != -1) {
                                                    String basePiece = baseMoveString.substring(0, 1);
                                                    String baseTarget = baseMoveString.substring(basePingIndex + 1);
                                                    String generatedPiece = normalizedGeneratedMove.substring(0, 1);
                                                    String generatedTarget = normalizedGeneratedMove.substring(generatedPingIndex + 1);
                                                    if (basePiece.equals(generatedPiece) && baseTarget.equals(generatedTarget)) {
                                                        foundMove = true;
                                                        break;
                                                    }
                                                }
                                            } else if (normalizedGeneratedMove.equals(baseMoveString)) {
                                                // 对于非横向移动，直接比较完整的走法字符串
                                                foundMove = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                
                                // 如果使用特殊标记选择的棋子无法执行该走法，尝试其他棋子
                                // 但如果是前卒特殊处理，不自动切换棋子
                                if (!foundMove && piecePositions.size() > 1 && !(specialMark.equals("前") && basePieceName.equals("卒"))) {

                                    for (Pos pos : piecePositions) {
                                        if (pos.equals(targetPiecePos)) {
                                            continue;
                                        }
                                        
                                        int otherPiece = newInfo.piece[pos.y][pos.x];
                                        List<Pos> otherPossibleMoves = Rule.PossibleMoves(newInfo.piece, pos.x, pos.y, otherPiece);
                                        
                                        if (otherPossibleMoves != null) {
                                            for (Pos targetPos : otherPossibleMoves) {
                                                String generatedMove = generateMoveString(newInfo, otherPiece, pos, targetPos, isRed);
                                                String normalizedGeneratedMove;
                                                if (generatedMove != null) {
                                                    if (isRed) {
                                                        // 红方走法：将所有数字转换为中文数字
                                                        normalizedGeneratedMove = generatedMove.replace("１", "一")
                                                                                             .replace("２", "二")
                                                                                             .replace("３", "三")
                                                                                             .replace("４", "四")
                                                                                             .replace("５", "五")
                                                                                             .replace("６", "六")
                                                                                             .replace("７", "七")
                                                                                             .replace("８", "八")
                                                                                             .replace("９", "九")
                                                                                             .replace("1", "一")
                                                                                             .replace("2", "二")
                                                                                             .replace("3", "三")
                                                                                             .replace("4", "四")
                                                                                             .replace("5", "五")
                                                                                             .replace("6", "六")
                                                                                             .replace("7", "七")
                                                                                             .replace("8", "八")
                                                                                             .replace("9", "九");
                                                    } else {
                                                        // 黑方走法：将所有数字转换为阿拉伯数字
                                                        normalizedGeneratedMove = generatedMove.replace("１", "1")
                                                                                             .replace("２", "2")
                                                                                             .replace("３", "3")
                                                                                             .replace("４", "4")
                                                                                             .replace("５", "5")
                                                                                             .replace("６", "6")
                                                                                             .replace("７", "7")
                                                                                             .replace("８", "8")
                                                                                             .replace("９", "9")
                                                                                             .replace("一", "1")
                                                                                             .replace("二", "2")
                                                                                             .replace("三", "3")
                                                                                             .replace("四", "4")
                                                                                             .replace("五", "5")
                                                                                             .replace("六", "6")
                                                                                             .replace("七", "7")
                                                                                             .replace("八", "8")
                                                                                             .replace("九", "9");
                                                    }
                                                } else {
                                                    normalizedGeneratedMove = null;
                                                }
                                                
                                                if (normalizedGeneratedMove != null) {
                                                    // 对于横向移动，只比较棋子名称、移动类型和目标列号
                                                    if (baseMoveString.contains("平")) {
                                                        int basePingIndex = baseMoveString.indexOf("平");
                                                        int generatedPingIndex = normalizedGeneratedMove.indexOf("平");
                                                        if (basePingIndex != -1 && generatedPingIndex != -1) {
                                                            String basePiece = baseMoveString.substring(0, 1);
                                                            String baseTarget = baseMoveString.substring(basePingIndex + 1);
                                                            String generatedPiece = normalizedGeneratedMove.substring(0, 1);
                                                            String generatedTarget = normalizedGeneratedMove.substring(generatedPingIndex + 1);
                                                            if (basePiece.equals(generatedPiece) && baseTarget.equals(generatedTarget)) {
                                                                targetPiecePos = pos;
                                                                foundMove = true;
                                                                break;
                                                            }
                                                        }
                                                    } else if (normalizedGeneratedMove.equals(baseMoveString)) {
                                                        // 对于非横向移动，直接比较完整的走法字符串
                                                        targetPiecePos = pos;
                                                        foundMove = true;
                                                        break;
                                                    }
                                                }
                                            }
                                            if (foundMove) {
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 输出所有棋子位置和选择的棋子位置，用于调试

                            
                            if (targetPiecePos != null) {
                                int piece = newInfo.piece[targetPiecePos.y][targetPiecePos.x];
                                System.out.println("PvMActivity: 目标棋子类型: " + piece + ", 位置: " + targetPiecePos.x + "," + targetPiecePos.y);
                                // 生成该棋子的可能走法
                                List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, targetPiecePos.x, targetPiecePos.y, piece);
                                System.out.println("PvMActivity: 目标棋子的可能走法数量: " + (possibleMoves != null ? possibleMoves.size() : 0));
                                if (possibleMoves != null) {
                                    // 尝试找到与走法字符串匹配的移动
                                    for (Pos targetPos : possibleMoves) {
                                        // 生成走法字符串并与输入进行比较
                                        String generatedMove = generateMoveString(newInfo, piece, targetPiecePos, targetPos, isRed);
                                        // 标准化生成的走法字符串以进行比较
                                        String normalizedGeneratedMove = generatedMove != null ? generatedMove.replace("１", "一")
                                                                                                .replace("２", "二")
                                                                                                .replace("３", "三")
                                                                                                .replace("４", "四")
                                                                                                .replace("５", "五")
                                                                                                .replace("６", "六")
                                                                                                .replace("７", "七")
                                                                                                .replace("８", "八")
                                                                                                .replace("９", "九")
                                                                                                .replace("1", "一")
                                                                                                .replace("2", "二")
                                                                                                .replace("3", "三")
                                                                                                .replace("4", "四")
                                                                                                .replace("5", "五")
                                                                                                .replace("6", "六")
                                                                                                .replace("7", "七")
                                                                                                .replace("8", "八")
                                                                                                .replace("9", "九") : null;
                                        
                                        System.out.println("PvMActivity: 比较 - 生成的走法: " + normalizedGeneratedMove + ", 目标走法: " + baseMoveString + ", 目标位置: " + targetPos.x + "," + targetPos.y);
                                        
                                        // 检查是否是横向移动（包含"平"）
                                        if (normalizedGeneratedMove != null && baseMoveString.contains("平")) {
                                            // 统一数字格式：将中文数字转换为阿拉伯数字
                                            String normalizedGeneratedMoveForCompare = normalizedGeneratedMove.replace("一", "1").replace("二", "2").replace("三", "3").replace("四", "4").replace("五", "5").replace("六", "6").replace("七", "7").replace("八", "8").replace("九", "9");
                                            String baseMoveStringForCompare = baseMoveString.replace("一", "1").replace("二", "2").replace("三", "3").replace("四", "4").replace("五", "5").replace("六", "6").replace("七", "7").replace("八", "8").replace("九", "9");
                                            
                                            // 提取生成走法中的棋子名称、移动类型和目标列号
                                            int generatedPingIndex = normalizedGeneratedMoveForCompare.indexOf("平");
                                            if (generatedPingIndex != -1) {
                                                // 提取棋子名称（可能带前缀数字）
                                                String generatedWithPrefix = normalizedGeneratedMoveForCompare.substring(0, generatedPingIndex);
                                                // 提取目标列号
                                                String generatedTargetCol = normalizedGeneratedMoveForCompare.substring(generatedPingIndex + 1);
                                                
                                                // 移除前缀数字得到棋子名称
                                                String generatedPieceName = generatedWithPrefix.replace("1", "").replace("2", "").replace("3", "").replace("4", "").replace("5", "").replace("6", "").replace("7", "").replace("8", "").replace("9", "");
                                                
                                                // 提取基础走法中的棋子名称和目标列号
                                                int basePingIndex = baseMoveStringForCompare.indexOf("平");
                                                if (basePingIndex != -1) {
                                                    String basePiece = baseMoveStringForCompare.substring(0, basePingIndex);
                                                    String baseTarget = baseMoveStringForCompare.substring(basePingIndex + 1);
                                                    
                                                    System.out.println("PvMActivity: 简化比较 - 生成: 棋子=" + generatedPieceName + " vs " + basePiece + ", 目标列: " + generatedTargetCol + " vs " + baseTarget);
                                                    
                                                    // 比较棋子名称和目标列号
                                                    if (generatedPieceName.equals(basePiece) && generatedTargetCol.equals(baseTarget)) {
                                                        // 找到匹配的走法，执行移动
                                                        System.out.println("PvMActivity: 找到特殊走法匹配，执行移动: 从 " + targetPiecePos.x + "," + targetPiecePos.y + " 到 " + targetPos.x + "," + targetPos.y);
                                                        
                                                        // 翻译成中国象棋标准格式
                                                        String pieceName = getPieceName(piece);
                                                        String startPos = "" + (char)('a' + targetPiecePos.x) + (10 - targetPiecePos.y);
                                                        String endPos = "" + (char)('a' + targetPos.x) + (10 - targetPos.y);

                                                        
                                                        newInfo.piece[targetPos.y][targetPos.x] = piece;
                                                        newInfo.piece[targetPiecePos.y][targetPiecePos.x] = 0;
                                                        
                                                        // 更新回合信息
                                                        newInfo.IsRedGo = !isRed;
                                                        
                                                        return newInfo;
                                                    }
                                                }
                                            }
                                        } else if (normalizedGeneratedMove != null && normalizedGeneratedMove.equals(baseMoveString)) {
                                            // 对于非横向移动，直接比较完整的走法字符串
                                            // 找到匹配的走法，执行移动
                                            // 打印移动坐标

                                            // 翻译成中国象棋标准格式
                                            String pieceName = getPieceName(piece);
                                            String startPos = "" + (char)('a' + targetPiecePos.x) + (10 - targetPiecePos.y);
                                            String endPos = "" + (char)('a' + targetPos.x) + (10 - targetPos.y);

                                            
                                            newInfo.piece[targetPos.y][targetPos.x] = piece;
                                            newInfo.piece[targetPiecePos.y][targetPiecePos.x] = 0;
                                            
                                            // 更新回合信息
                                            newInfo.IsRedGo = !isRed;
                                            
                                            return newInfo;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 常规走法处理
            // 提取走法字符串中的棋子类型
            if (normalizedMoveString == null || normalizedMoveString.length() < 1) {
                return newInfo;
            }
            
            // 检查是否是特殊走法，如果是且没有找到匹配的棋子，继续处理常规走法
            if (isSpecialMove) {
                // 继续处理常规走法
            }
            
            // 提取棋子名称（跳过特殊前缀）
            String pieceName;
            if (normalizedMoveString.startsWith("前") || normalizedMoveString.startsWith("后") || normalizedMoveString.startsWith("中")) {
                pieceName = normalizedMoveString.substring(1, 2);
            } else if (normalizedMoveString.length() > 1 && (Character.isDigit(normalizedMoveString.charAt(0)) || (normalizedMoveString.charAt(0) >= '一' && normalizedMoveString.charAt(0) <= '九'))) {
                pieceName = normalizedMoveString.substring(1, 2);
            } else {
                pieceName = normalizedMoveString.substring(0, 1);
            }
            int targetPieceType = getPieceTypeByName(pieceName, isRed);
            System.out.println("PvMActivity: 棋子名称: " + pieceName + ", 棋子类型: " + targetPieceType + ", isRed: " + isRed);
            
            // 先收集所有符合条件的棋子，然后按优先级排序
            List<Pos> candidatePieces = new ArrayList<>();
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 9; x++) {
                    int piece = newInfo.piece[y][x];
                    if (piece != 0) {
                        // 检查是否是当前方的棋子且类型匹配
                        boolean isCurrentSide = (isRed && piece >= 8 && piece <= 14) || (!isRed && piece >= 1 && piece <= 7);
                        if (isCurrentSide && piece == targetPieceType) {
                            candidatePieces.add(new Pos(x, y));
                            System.out.println("PvMActivity: 找到候选棋子: 位置= " + x + "," + y + ", 类型= " + piece);
                        }
                    }
                }
            }
            System.out.println("PvMActivity: 候选棋子数量: " + candidatePieces.size());
            
            // 按优先级排序：
            // 1. 离对方底线最近的棋子优先
            // 2. 对于同一行的棋子，根据走法中的列号选择
            sortCandidatePieces(candidatePieces, isRed, normalizedMoveString);
            
            // 遍历排序后的棋子，找到符合条件的走法
            for (Pos pos : candidatePieces) {
                int x = pos.x;
                int y = pos.y;
                int piece = newInfo.piece[y][x];
                
                // 生成该棋子的可能走法
                List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, x, y, piece);
                if (possibleMoves != null) {
                    // 尝试找到与走法字符串匹配的移动
                    for (Pos targetPos : possibleMoves) {
                        // 生成走法字符串并与输入进行比较
                        String generatedMove = generateMoveString(newInfo, piece, new Pos(x, y), targetPos, isRed);
                        // 标准化生成的走法字符串以进行比较
                        String normalizedGeneratedMove;
                        if (generatedMove != null) {
                            if (isRed) {
                                // 红方走法：将所有数字转换为中文数字
                                normalizedGeneratedMove = generatedMove.replace("１", "一")
                                                                     .replace("２", "二")
                                                                     .replace("３", "三")
                                                                     .replace("４", "四")
                                                                     .replace("５", "五")
                                                                     .replace("６", "六")
                                                                     .replace("７", "七")
                                                                     .replace("８", "八")
                                                                     .replace("９", "九")
                                                                     .replace("1", "一")
                                                                     .replace("2", "二")
                                                                     .replace("3", "三")
                                                                     .replace("4", "四")
                                                                     .replace("5", "五")
                                                                     .replace("6", "六")
                                                                     .replace("7", "七")
                                                                     .replace("8", "八")
                                                                     .replace("9", "九");
                            } else {
                                // 黑方走法：将所有数字转换为阿拉伯数字
                                normalizedGeneratedMove = generatedMove.replace("１", "1")
                                                                     .replace("２", "2")
                                                                     .replace("３", "3")
                                                                     .replace("４", "4")
                                                                     .replace("５", "5")
                                                                     .replace("６", "6")
                                                                     .replace("７", "7")
                                                                     .replace("８", "8")
                                                                     .replace("９", "9")
                                                                     .replace("０", "0")
                                                                     .replace("一", "1")
                                                                     .replace("二", "2")
                                                                     .replace("三", "3")
                                                                     .replace("四", "4")
                                                                     .replace("五", "5")
                                                                     .replace("六", "6")
                                                                     .replace("七", "7")
                                                                     .replace("八", "8")
                                                                     .replace("九", "9")
                                                                     .replace("零", "0");
                            }
                        } else {
                            normalizedGeneratedMove = null;
                        }
                        
                        System.out.println("PvMActivity: 检查走法: 生成走法= " + normalizedGeneratedMove + ", 目标走法= " + normalizedMoveString);
                        
                        // 检查是否匹配，考虑前缀和列号的情况
                        boolean isMatch = false;
                        if (normalizedGeneratedMove != null) {
                            // 提取棋子类型，处理带前缀的情况
                            String generatedPieceName;
                            if (normalizedGeneratedMove.startsWith("前") || normalizedGeneratedMove.startsWith("后") || normalizedGeneratedMove.startsWith("中") || Character.isDigit(normalizedGeneratedMove.charAt(0))) {
                                // 带前缀的走法，如"后卒平6"，提取"卒"
                                generatedPieceName = normalizedGeneratedMove.substring(1, 2);
                            } else {
                                // 普通走法，如"卒5平6"，提取"卒"
                                generatedPieceName = normalizedGeneratedMove.substring(0, 1);
                            }
                            
                            // 直接匹配
                            if (normalizedGeneratedMove.equals(normalizedMoveString)) {
                                isMatch = true;
                            } 
                            // 处理带前缀的情况，如"后卒平6" 与 "卒5平6" 匹配
                            else if (normalizedGeneratedMove.length() > normalizedMoveString.length()) {
                                // 提取棋子名称和走法部分
                                String moveWithoutPrefix = normalizedGeneratedMove;
                                // 移除前缀（前、后、中或数字）
                                if (normalizedGeneratedMove.startsWith("前") || normalizedGeneratedMove.startsWith("后") || normalizedGeneratedMove.startsWith("中")) {
                                    moveWithoutPrefix = normalizedGeneratedMove.substring(1);
                                } else if (Character.isDigit(normalizedGeneratedMove.charAt(0))) {
                                    // 处理数字前缀，如"一卒"、"二卒"等
                                    moveWithoutPrefix = normalizedGeneratedMove.substring(1);
                                }
                                
                                // 检查移除前缀后是否匹配
                                if (moveWithoutPrefix.equals(normalizedMoveString)) {
                                    isMatch = true;
                                }
                                // 处理起始列号不同的情况，如"后卒平6" 与 "卒5平6" 匹配
                                else {
                                    // 提取移动类型和目标位置
                                    String moveType = "";
                                    String targetPosStr = "";
                                    
                                    // 从目标走法中提取移动类型和目标位置
                                    if (normalizedMoveString.contains("平")) {
                                        int pingIndex = normalizedMoveString.indexOf("平");
                                        moveType = "平";
                                        targetPosStr = normalizedMoveString.substring(pingIndex + 1);
                                    } else if (normalizedMoveString.contains("进")) {
                                        int jinIndex = normalizedMoveString.indexOf("进");
                                        moveType = "进";
                                        targetPosStr = normalizedMoveString.substring(jinIndex + 1);
                                    } else if (normalizedMoveString.contains("退")) {
                                        int tuiIndex = normalizedMoveString.indexOf("退");
                                        moveType = "退";
                                        targetPosStr = normalizedMoveString.substring(tuiIndex + 1);
                                    }
                                    
                                    // 从生成的走法中提取移动类型和目标位置
                                    String generatedMoveType = "";
                                    String generatedTargetPosStr = "";
                                    
                                    if (moveWithoutPrefix.contains("平")) {
                                        int pingIndex = moveWithoutPrefix.indexOf("平");
                                        generatedMoveType = "平";
                                        generatedTargetPosStr = moveWithoutPrefix.substring(pingIndex + 1);
                                    } else if (moveWithoutPrefix.contains("进")) {
                                        int jinIndex = moveWithoutPrefix.indexOf("进");
                                        generatedMoveType = "进";
                                        generatedTargetPosStr = moveWithoutPrefix.substring(jinIndex + 1);
                                    } else if (moveWithoutPrefix.contains("退")) {
                                        int tuiIndex = moveWithoutPrefix.indexOf("退");
                                        generatedMoveType = "退";
                                        generatedTargetPosStr = moveWithoutPrefix.substring(tuiIndex + 1);
                                    }
                                    
                                    // 检查移动类型和目标位置是否匹配
                                    if (moveType.equals(generatedMoveType) && targetPosStr.equals(generatedTargetPosStr)) {
                                        isMatch = true;
                                    }
                                }
                            }
                            // 处理目标走法带列号但生成走法带前缀的情况，如"卒5平6" 与 "后卒平6" 匹配
                            else if (normalizedGeneratedMove.length() < normalizedMoveString.length()) {
                                // 检查生成的走法是否带前缀
                                boolean hasPrefix = normalizedGeneratedMove.startsWith("前") || normalizedGeneratedMove.startsWith("后") || normalizedGeneratedMove.startsWith("中") || Character.isDigit(normalizedGeneratedMove.charAt(0));
                                
                                if (hasPrefix) {
                                    // 提取移动类型和目标位置
                                    String moveType = "";
                                    String targetPosStr = "";
                                    
                                    // 从目标走法中提取移动类型和目标位置
                                    if (normalizedMoveString.contains("平")) {
                                        int pingIndex = normalizedMoveString.indexOf("平");
                                        moveType = "平";
                                        targetPosStr = normalizedMoveString.substring(pingIndex + 1);
                                    } else if (normalizedMoveString.contains("进")) {
                                        int jinIndex = normalizedMoveString.indexOf("进");
                                        moveType = "进";
                                        targetPosStr = normalizedMoveString.substring(jinIndex + 1);
                                    } else if (normalizedMoveString.contains("退")) {
                                        int tuiIndex = normalizedMoveString.indexOf("退");
                                        moveType = "退";
                                        targetPosStr = normalizedMoveString.substring(tuiIndex + 1);
                                    }
                                    
                                    // 从生成的走法中提取移动类型和目标位置
                                    String generatedMoveType = "";
                                    String generatedTargetPosStr = "";
                                    
                                    if (normalizedGeneratedMove.contains("平")) {
                                        int pingIndex = normalizedGeneratedMove.indexOf("平");
                                        generatedMoveType = "平";
                                        generatedTargetPosStr = normalizedGeneratedMove.substring(pingIndex + 1);
                                    } else if (normalizedGeneratedMove.contains("进")) {
                                        int jinIndex = normalizedGeneratedMove.indexOf("进");
                                        generatedMoveType = "进";
                                        generatedTargetPosStr = normalizedGeneratedMove.substring(jinIndex + 1);
                                    } else if (normalizedGeneratedMove.contains("退")) {
                                        int tuiIndex = normalizedGeneratedMove.indexOf("退");
                                        generatedMoveType = "退";
                                        generatedTargetPosStr = normalizedGeneratedMove.substring(tuiIndex + 1);
                                    }
                                    
                                    // 检查移动类型和目标位置是否匹配
                                    if (moveType.equals(generatedMoveType) && targetPosStr.equals(generatedTargetPosStr)) {
                                        isMatch = true;
                                    }
                                }
                            }
                            // 处理起始列号不同但移动类型和目标位置相同的情况
                            else {
                                // 提取移动类型和目标位置
                                String moveType = "";
                                String targetPosStr = "";
                                
                                // 从目标走法中提取移动类型和目标位置
                                if (normalizedMoveString.contains("平")) {
                                    int pingIndex = normalizedMoveString.indexOf("平");
                                    moveType = "平";
                                    targetPosStr = normalizedMoveString.substring(pingIndex + 1);
                                } else if (normalizedMoveString.contains("进")) {
                                    int jinIndex = normalizedMoveString.indexOf("进");
                                    moveType = "进";
                                    targetPosStr = normalizedMoveString.substring(jinIndex + 1);
                                } else if (normalizedMoveString.contains("退")) {
                                    int tuiIndex = normalizedMoveString.indexOf("退");
                                    moveType = "退";
                                    targetPosStr = normalizedMoveString.substring(tuiIndex + 1);
                                }
                                
                                // 从生成的走法中提取移动类型和目标位置
                                String generatedMoveType = "";
                                String generatedTargetPosStr = "";
                                
                                if (normalizedGeneratedMove.contains("平")) {
                                    int pingIndex = normalizedGeneratedMove.indexOf("平");
                                    generatedMoveType = "平";
                                    generatedTargetPosStr = normalizedGeneratedMove.substring(pingIndex + 1);
                                } else if (normalizedGeneratedMove.contains("进")) {
                                    int jinIndex = normalizedGeneratedMove.indexOf("进");
                                    generatedMoveType = "进";
                                    generatedTargetPosStr = normalizedGeneratedMove.substring(jinIndex + 1);
                                } else if (normalizedGeneratedMove.contains("退")) {
                                    int tuiIndex = normalizedGeneratedMove.indexOf("退");
                                    generatedMoveType = "退";
                                    generatedTargetPosStr = normalizedGeneratedMove.substring(tuiIndex + 1);
                                }
                                
                                // 检查移动类型和目标位置是否匹配
                                if (moveType.equals(generatedMoveType) && targetPosStr.equals(generatedTargetPosStr)) {
                                    // 检查棋子类型是否匹配
                                    if (generatedPieceName.equals(pieceName)) {
                                        isMatch = true;
                                    }
                                }
                                // 额外检查：对于黑方的横向移动，确保目标列号正确
                                else if (moveType.equals("平") && generatedMoveType.equals("平") && generatedPieceName.equals(pieceName)) {
                                    // 尝试将目标位置转换为棋盘坐标，然后再转换回记谱列号，确保匹配
                                    try {
                                        int targetCol = Integer.parseInt(targetPosStr);
                                        int generatedTargetCol = Integer.parseInt(generatedTargetPosStr);
                                        // 转换为棋盘坐标
                                        int targetX = isRed ? 9 - targetCol : targetCol - 1;
                                        int generatedTargetX = isRed ? 9 - generatedTargetCol : generatedTargetCol - 1;
                                        // 检查棋盘坐标是否相同
                                        if (targetX == generatedTargetX) {
                                            isMatch = true;
                                        }
                                    } catch (NumberFormatException e) {
                                        // 忽略非数字的情况
                                    }
                                }
                            }
                        }
                        
                        if (isMatch) {
                            // 找到匹配的走法，执行移动
                            System.out.println("PvMActivity: 找到匹配的走法，执行移动: 从 " + x + "," + y + " 到 " + targetPos.x + "," + targetPos.y);
                            
                            // 翻译成中国象棋标准格式
                            String pieceNameRegular = getPieceName(piece);
                            String startPos = "" + (char)('a' + x) + (10 - y);
                            String endPos = "" + (char)('a' + targetPos.x) + (10 - targetPos.y);

                            
                            newInfo.piece[targetPos.y][targetPos.x] = piece;
                            newInfo.piece[y][x] = 0;
                            
                            // 更新回合信息
                            newInfo.IsRedGo = !isRed;
                            
                            return newInfo;
                        }
                    }
                }
            }
        }
        
        return newInfo;
    }
    
    // 根据棋子名称获取棋子类型ID
    private int getPieceTypeByName(String pieceName, boolean isRed) {
        switch (pieceName) {
            case "帅": return 8;
            case "仕": return 9;
            case "相": return 10;
            case "马": return isRed ? 11 : 4;
            case "车": return isRed ? 12 : 5;
            case "炮": return isRed ? 13 : 6;
            case "兵": return isRed ? 14 : 7;
            case "将": return 1;
            case "士": return 2;
            case "象": return 3;
            case "卒": return isRed ? 14 : 7;
            default: return -1;
        }
    }
    
    // 按y坐标排序棋子
    private void sortPiecesByY(List<Pos> pieces, boolean isRed) {
        // 根据红黑方排序棋子
        ChessNotationTranslator.sortPiecesByY(pieces, isRed);
    }
    
    // 排序候选棋子
    private void sortCandidatePieces(List<Pos> pieces, boolean isRed, String moveString) {
        ChessNotationTranslator.sortCandidatePieces(pieces, isRed, moveString);
    }
    
    private String generateMoveString(ChessInfo info, int pieceType, Pos fromPos, Pos toPos, boolean isRed) {
        // 确保位置有效
        if (fromPos == null || toPos == null || 
            fromPos.x < 0 || fromPos.x > 8 || fromPos.y < 0 || fromPos.y > 9 ||
            toPos.x < 0 || toPos.x > 8 || toPos.y < 0 || toPos.y > 9) {
            return null;
        }
        
        // 检查是否有多个相同的棋子
        String prefix = "";
        int baseType = pieceType % 7;
        boolean isPawn = baseType == 0; // 兵/卒
        boolean isSameColumn = false;
        boolean isSameRow = false;
        java.util.List<Pos> samePieces = new java.util.ArrayList<>();
        
        // 收集同一列的相同棋子
        if (info != null && info.piece != null) {
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 9; x++) {
                    if (x == fromPos.x && info.piece[y][x] == pieceType) {
                        samePieces.add(new Pos(x, y));
                    }
                }
            }
        }
        
        // 如果同一列有多个相同的棋子，添加前缀
        if (samePieces.size() > 1) {
            isSameColumn = true;
            // 对棋子按y坐标排序（兼容API 16）
            // 使用简单的冒泡排序，避免使用匿名内部类
            for (int i = 0; i < samePieces.size() - 1; i++) {
                for (int j = 0; j < samePieces.size() - i - 1; j++) {
                    Pos p1 = samePieces.get(j);
                    Pos p2 = samePieces.get(j + 1);
                    if (p1 != null && p2 != null && p1.y > p2.y) {
                        // 交换位置
                        samePieces.set(j, p2);
                        samePieces.set(j + 1, p1);
                    }
                }
            }
            
            if (isPawn) {
                // 兵/卒使用数字前缀：一兵、二兵、三兵、四兵、五兵
                // 按照从前往后的顺序编号
                int index = samePieces.indexOf(new Pos(fromPos.x, fromPos.y)) + 1;
                prefix = ChessNotationTranslator.getColChar(index);
            } else {
                // 其他棋子使用前后前缀
                if (samePieces.size() == 2) {
                    // 两个棋子：前、后
                    // samePieces 按 y 从小到大排序
                    // 对于红方，前是离黑方底线近的棋子（y 较大），后是离红方底线近的棋子（y 较小）
                    // 对于黑方，前是离红方底线近的棋子（y 较小），后是离黑方底线近的棋子（y 较大）
                    Pos frontPiece = isRed ? samePieces.get(1) : samePieces.get(0);
                    prefix = (fromPos.y == frontPiece.y) ? "前" : "后";
                } else if (samePieces.size() == 3) {
                    // 三个棋子：前、中、后
                    // samePieces 按 y 从小到大排序
                    // 对于红方，前是离黑方底线近的棋子（y 最大），后是离红方底线近的棋子（y 最小）
                    // 对于黑方，前是离红方底线近的棋子（y 最小），后是离黑方底线近的棋子（y 最大）
                    Pos frontPiece = isRed ? samePieces.get(2) : samePieces.get(0);
                    Pos middlePiece = samePieces.get(1);
                    if (fromPos.y == frontPiece.y) {
                        prefix = "前";
                    } else if (fromPos.y == middlePiece.y) {
                        prefix = "中";
                    } else {
                        prefix = "后";
                    }
                } else if (samePieces.size() > 3) {
                    // 四个或五个棋子：前、二、三、四、五
                    // samePieces 按 y 从小到大排序
                    // 对于红方，前是离黑方底线近的棋子（y 最大）
                    // 对于黑方，前是离红方底线近的棋子（y 最小）
                    int index = samePieces.indexOf(new Pos(fromPos.x, fromPos.y)) + 1;
                    if (isRed) {
                        // 红方：y 最大的是前
                        prefix = (index == samePieces.size()) ? "前" : ChessNotationTranslator.getColChar(index);
                    } else {
                        // 黑方：y 最小的是前
                        prefix = (index == 1) ? "前" : ChessNotationTranslator.getColChar(index);
                    }
                }
            }
        }
        
        // 计算起始列号
        int startCol = ChessNotationTranslator.getNotationColumn(fromPos.x, isRed);
        startCol = Math.max(1, Math.min(9, startCol));
        // 红方使用中文数字，黑方使用阿拉伯数字，以匹配棋谱格式
        String startColStr;
        if (isRed) {
            startColStr = ChessNotationTranslator.getColChar(startCol);
        } else {
            startColStr = String.valueOf(startCol);
        }
        
        // 计算移动类型
        String moveType;
        int colDiff = toPos.x - fromPos.x;
        int rowDiff = toPos.y - fromPos.y;
        
        // 确定移动方向（红黑相对）
        if (colDiff == 0) {
            // 纵向移动
            if (isRed) {
                // 红方：向黑方（y值增大）为进
                moveType = rowDiff > 0 ? "进" : "退";
            } else {
                // 黑方：向红方（y值减小）为进
                moveType = rowDiff < 0 ? "进" : "退";
            }
        } else {
            // 横向或斜向移动
            // 车、炮、兵/卒、帅（将）使用"平"
            if (baseType == 5 || baseType == 6 || baseType == 0 || baseType == 1) {
                moveType = "平";
            } else {
                // 士、象、马使用"进"或"退"
                if (isRed) {
                    // 红方：向黑方（y值增大）为进
                    moveType = rowDiff > 0 ? "进" : "退";
                } else {
                    // 黑方：向红方（y值减小）为进
                    moveType = rowDiff < 0 ? "进" : "退";
                }
            }
        }
        
        // 计算目标位置
        String targetPos;
        if (moveType.equals("平")) {
            // 横向移动使用列号
            int targetCol = ChessNotationTranslator.getNotationColumn(toPos.x, isRed);
            targetCol = Math.max(1, Math.min(9, targetCol));
            // 红方使用中文数字，黑方使用阿拉伯数字，以匹配棋谱格式
            if (isRed) {
                targetPos = ChessNotationTranslator.getColChar(targetCol);
            } else {
                targetPos = String.valueOf(targetCol);
            }
        } else {
            // 纵向或斜向移动
            boolean isSpecialPiece = baseType == 2 || baseType == 3 || baseType == 4; // 士、象、马
            
            if (isSpecialPiece) {
                // 马、相（象）、仕（士）：使用目标列坐标
                int targetCol = ChessNotationTranslator.getNotationColumn(toPos.x, isRed);
                targetCol = Math.max(1, Math.min(9, targetCol));
                // 红方使用中文数字，黑方使用阿拉伯数字，以匹配棋谱格式
                if (isRed) {
                    targetPos = ChessNotationTranslator.getColChar(targetCol);
                } else {
                    targetPos = String.valueOf(targetCol);
                }
            } else {
                // 车、炮、兵（卒）、帅（将）：使用移动的行数（格数）
                int moveSteps = Math.abs(toPos.y - fromPos.y);
                // 确保移动的格数至少为1
                moveSteps = Math.max(1, moveSteps);
                // 红黑方都使用中文数字，以匹配棋谱格式
                targetPos = ChessNotationTranslator.getColChar(moveSteps);
            }
        }
        
        // 获取棋子名称
        String pieceName = getPieceName(pieceType);
        
        // 生成走法字符串
        String moveString;
        if (isSameColumn && !prefix.isEmpty()) {
            if (isPawn) {
                // 兵/卒：一兵、二兵等
                moveString = prefix + pieceName + moveType + targetPos;
            } else {
                // 其他棋子：前马、后车等（省略起始列号，生成4字棋谱）
                moveString = prefix + pieceName + moveType + targetPos;
            }
        } else {
            // 普通走法
            moveString = pieceName + startColStr + moveType + targetPos;
        }
        
        // 生成黑方走法的阿拉伯数字版本，以符合中国象棋记谱标准
        if (!isRed) {
            moveString = moveString.replace("一", "1")
                                  .replace("二", "2")
                                  .replace("三", "3")
                                  .replace("四", "4")
                                  .replace("五", "5")
                                  .replace("六", "6")
                                  .replace("七", "7")
                                  .replace("八", "8")
                                  .replace("九", "9");
        }
        
        return moveString;
    }
    

    
    private String getPieceName(int pieceType) {
        switch (pieceType) {
            case 1: return "将";
            case 2: return "士";
            case 3: return "象";
            case 4: return "马";
            case 5: return "车";
            case 6: return "炮";
            case 7: return "卒";
            case 8: return "帅";
            case 9: return "仕";
            case 10: return "相";
            case 11: return "马";
            case 12: return "车";
            case 13: return "炮";
            case 14: return "兵";
            default: return "未知";
        }
    }


    
    private void showSaveNotationDialog() {
        // 先弹出对话框获取棋谱信息
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_save_notation, null);
        
        final EditText redPlayerEditText = dialogView.findViewById(R.id.red_player_edit);
        final EditText blackPlayerEditText = dialogView.findViewById(R.id.black_player_edit);
        final EditText dateEditText = dialogView.findViewById(R.id.date_edit);
        final EditText locationEditText = dialogView.findViewById(R.id.location_edit);
        final EditText eventEditText = dialogView.findViewById(R.id.event_edit);
        final EditText roundEditText = dialogView.findViewById(R.id.round_edit);
        
        // 设置默认值
        dateEditText.setText(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("保存棋谱");
        builder.setView(dialogView);
        builder.setPositiveButton("保存", (dialog, which) -> {
            // 生成默认文件名
            String fileName = "对局_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pgn";
            
            String redPlayer = redPlayerEditText.getText().toString().trim();
            String blackPlayer = blackPlayerEditText.getText().toString().trim();
            String date = dateEditText.getText().toString().trim();
            String location = locationEditText.getText().toString().trim();
            String event = eventEditText.getText().toString().trim();
            String round = roundEditText.getText().toString().trim();
            
            // 保存信息到成员变量
            pendingSaveFileName = fileName;
            pendingSaveRedPlayer = redPlayer;
            pendingSaveBlackPlayer = blackPlayer;
            pendingSaveDate = date;
            pendingSaveLocation = location;
            pendingSaveEvent = event;
            pendingSaveRound = round;
            
            // 使用SAF打开文件保存选择器
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            startActivityForResult(intent, 1003);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    // 保存棋谱相关的临时变量
    private String pendingSaveFileName;
    private String pendingSaveRedPlayer;
    private String pendingSaveBlackPlayer;
    private String pendingSaveDate;
    private String pendingSaveLocation;
    private String pendingSaveEvent;
    private String pendingSaveRound;
    
    private void saveChessNotationToUri(Uri uri, String originalFileName) {
        try {
            // 使用保存对话框中输入的信息
            String fileName = pendingSaveFileName != null ? pendingSaveFileName : originalFileName;
            String redPlayer = pendingSaveRedPlayer != null ? pendingSaveRedPlayer : "";
            String blackPlayer = pendingSaveBlackPlayer != null ? pendingSaveBlackPlayer : "";
            String date = pendingSaveDate != null ? pendingSaveDate : "";
            String location = pendingSaveLocation != null ? pendingSaveLocation : "";
            String event = pendingSaveEvent != null ? pendingSaveEvent : "";
            String round = pendingSaveRound != null ? pendingSaveRound : "";
            
            // 创建棋谱对象
            ChessNotation notation = new ChessNotation();
            notation.setFileName(fileName);
            notation.setDate(new Date());
            notation.setPlayerRed(redPlayer);
            notation.setPlayerBlack(blackPlayer);
            notation.setMatchDate(date);
            notation.setLocation(location);
            notation.setEvent(event);
            notation.setRound(round);
            
            // 添加FEN信息
            if (chessInfo != null) {
                String fen = generateFENForSave();
                notation.setFen(fen);
            }
            
            // 提取走法记录
            if (chessInfo != null && infoSet != null && infoSet.preInfo != null) {
                extractMoveRecords(notation);
            }
            
            // 生成棋谱内容
            String content = notation.toSaveContent();
            
            // 写入到选择的URI，确保完全覆盖文件内容
            // 先获取文件描述符，然后使用 FileOutputStream 来确保覆盖模式
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w");
            if (pfd != null) {
                try (FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
                     OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {
                    // 写入新内容
                    writer.write(content);
                    writer.flush();

                } catch (Exception e) {
                    e.printStackTrace();

                } finally {
                    try {
                        pfd.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {

            }
            
            // 清空临时变量
            pendingSaveFileName = null;
            pendingSaveRedPlayer = null;
            pendingSaveBlackPlayer = null;
            pendingSaveDate = null;
            pendingSaveLocation = null;
            pendingSaveEvent = null;
            pendingSaveRound = null;
            
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    private void handleSettingsButton() {
        // 启动设置Activity
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void handleModeButton() {
        // 弹出对话框让用户选择对战模式
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择对战模式");
        
        final String[] modes = {"双人对战", "人机对战 - 执红", "人机对战 - 执黑", "双机对战"};
        
        // 使用单选框形式，确保游戏模式索引不超过新数组长度
        int selectedIndex = Math.min(gameMode, modes.length - 1);
        builder.setSingleChoiceItems(modes, selectedIndex, new ModeSelectionListener(this, modes));
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    // 命名内部类，避免构建错误
    private static class ModeSelectionListener implements DialogInterface.OnClickListener {
        private final String[] modes;
        private final PvMActivity activity;
        
        public ModeSelectionListener(PvMActivity activity, String[] modes) {
            this.activity = activity;
            this.modes = modes;
        }
        
        @Override
        public void onClick(DialogInterface dialog, final int which) {
            activity.gameMode = which;
            Toast.makeText(activity, "已选择: " + modes[which], Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            
            // 重新绘制界面
            if (activity.roundView != null) {
                activity.roundView.setGameMode(which);
                activity.roundView.requestDraw();
            }
            
            // 检查是否需要AI走棋
            if (activity.chessInfo != null && activity.chessInfo.status == 1) {
                boolean shouldAIMove = false;
                
                // 根据游戏模式判断是否需要AI走棋
                if (which == 1) { // 人机对战（玩家红）
                    shouldAIMove = !activity.chessInfo.IsRedGo; // AI控制黑方
                } else if (which == 2) { // 人机对战（玩家黑）
                    shouldAIMove = activity.chessInfo.IsRedGo; // AI控制红方
                } else if (which == 3) { // 双机对战
                    shouldAIMove = true; // 双机对战模式下，双方都由AI控制
                }
                
                if (shouldAIMove) {
                    // 等待PikafishAI初始化完成
                    new Thread(() -> {
                        try {
                            int maxWaitTime = 10000; // 最大等待时间10秒
                            int waitTime = 0;
                            int waitInterval = 500; // 等待间隔500毫秒
                            
                            // 等待PikafishAI初始化完成
                            while (activity.pikafishAI != null && !activity.pikafishAI.isInitialized() && waitTime < maxWaitTime) {
                                Thread.sleep(waitInterval);
                                waitTime += waitInterval;
                            }
                            
                            if (activity.pikafishAI != null && activity.pikafishAI.isInitialized()) {
                                // 创建并启动新的AI线程
                                activity.runOnUiThread(() -> {
                                    activity.startAIThread();
                                });
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            }
        }
    }


    
    // 摆棋结束时的FEN信息
    private String setupFEN;
    
    // 生成用于保存的FEN字符串
    private String generateFENForSave() {
        // 检查是否有摆棋结束时的FEN信息
        if (setupFEN != null && !setupFEN.isEmpty()) {
            return setupFEN;
        }
        
        // 检查是否在摆棋模式下
        if (chessInfo != null && chessInfo.IsSetupMode) {
            // 在摆棋模式下，使用当前棋盘状态生成FEN
            return generateFEN(chessInfo);
        }
        
        // 检查是否有FEN信息在currentNotation中
        if (currentNotation != null && currentNotation.getFen() != null && !currentNotation.getFen().isEmpty()) {
            // 使用棋谱中已有的FEN作为初始状态
            return currentNotation.getFen();
        }
        
        // 使用标准初始状态
        ChessInfo initialInfo = new ChessInfo();
        return generateFEN(initialInfo);
    }
    
    // 提取走法记录
    private void extractMoveRecords(ChessNotation notation) {
        if (chessInfo == null || infoSet == null || infoSet.preInfo == null) {
            return;
        }
        
        // 摆棋模式下不提取走法记录，因为游戏还未开始
        if (chessInfo.IsSetupMode) {
            return;
        }
        
        // 清空现有的走法记录，确保不会添加到原有记录后面
        notation.getMoveRecords().clear();
        
        // 创建一个临时列表来存储所有ChessInfo对象，而不修改原栈
        java.util.List<ChessInfo> tempList = new java.util.ArrayList<>();
        java.util.Stack<ChessInfo> originalStack = new java.util.Stack<>();
        
        // 先将所有ChessInfo对象弹出到临时列表，同时保存到原始栈
        while (!infoSet.preInfo.empty()) {
            ChessInfo info = infoSet.preInfo.pop();
            tempList.add(info);
            originalStack.push(info);
        }
        
        // 恢复原栈
        while (!originalStack.empty()) {
            infoSet.preInfo.push(originalStack.pop());
        }
        
        // 按照临时列表的顺序处理，保证走法记录顺序正确
        for (int i = tempList.size() - 1; i >= 0; i--) {
            ChessInfo info = tempList.get(i);
            addMoveToNotation(notation, info);
        }
        
        // 处理当前chessInfo中的最后一步走法
        if (chessInfo.prePos != null && chessInfo.curPos != null) {
            addMoveToNotation(notation, chessInfo);
        }
    }
    
    // 将走法添加到棋谱
    private void addMoveToNotation(ChessNotation notation, ChessInfo info) {
        if (info.prePos == null || info.curPos == null) {
            return;
        }
        
        // 尝试获取移动的棋子类型
        int piece = 0;
        boolean isRed = false;
        
        // 首先尝试从当前位置获取棋子
        if (info.piece != null && info.curPos.y >= 0 && info.curPos.y < info.piece.length && 
            info.curPos.x >= 0 && info.curPos.x < info.piece[info.curPos.y].length) {
            piece = info.piece[info.curPos.y][info.curPos.x];
            isRed = piece >= 8 && piece <= 14;
        }
        
        if (piece != 0) {
            String move = generateMoveString(info, piece, info.prePos, info.curPos, isRed);
            
            if (move != null) {
                if (isRed) {
                    // 红方走法，添加新记录
                    notation.addMoveRecord(move, "");
                } else {
                    // 黑方走法，更新最后一条记录
                    if (!notation.getMoveRecords().isEmpty()) {
                        ChessNotation.MoveRecord lastRecord = notation.getMoveRecords().get(notation.getMoveRecords().size() - 1);
                        if (lastRecord.blackMove.isEmpty()) {
                            lastRecord.blackMove = move;
                        }
                    } else {
                        // 如果没有红方走法，单独添加黑方走法
                        notation.addMoveRecord("", move);
                    }
                }
            }
        }
    }
    
    // 生成FEN字符串
    private String generateFEN(ChessInfo chessInfo) {
        StringBuilder fen = new StringBuilder();
        
        // 生成棋盘部分，从黑方底线开始（y=9）到红方底线结束（y=0），符合标准FEN格式
        for (int y = 9; y >= 0; y--) {
            int emptyCount = 0;
            for (int x = 0; x < 9; x++) {
                int piece = chessInfo.piece[y][x];
                if (piece == 0) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(pieceToFEN(piece));
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (y > 0) {
                fen.append('/');
            }
        }
        
        // 生成回合部分，符合标准FEN格式
        // 'w' 表示白方（红方）走，'b' 表示黑方走
        fen.append(' ');
        fen.append(chessInfo.IsRedGo ? 'w' : 'b');
        
        // 生成 castle 部分（中国象棋不需要）
        fen.append(" - - 0 1");
        
        return fen.toString();
    }
    
    // 将棋子ID转换为FEN符号
    private char pieceToFEN(int piece) {
        switch (piece) {
            case 1: return 'k'; // 黑将
            case 2: return 'a'; // 黑士
            case 3: return 'b'; // 黑象
            case 4: return 'n'; // 黑马
            case 5: return 'r'; // 黑车
            case 6: return 'c'; // 黑炮
            case 7: return 'p'; // 黑卒
            case 8: return 'K'; // 红帅
            case 9: return 'A'; // 红士
            case 10: return 'B'; // 红相
            case 11: return 'N'; // 红马
            case 12: return 'R'; // 红车
            case 13: return 'C'; // 红炮
            case 14: return 'P'; // 红兵
            default: return ' ';
        }
    }
    
    // 将FEN符号转换为棋子ID
    private int fenToPiece(char fenChar) {
        switch (fenChar) {
            case 'k': return 1; // 黑将
            case 'a': return 2; // 黑士
            case 'b': return 3; // 黑象
            case 'n': return 4; // 黑马
            case 'r': return 5; // 黑车
            case 'c': return 6; // 黑炮
            case 'p': return 7; // 黑卒
            case 'K': return 8; // 红帅
            case 'A': return 9; // 红士
            case 'B': return 10; // 红相
            case 'N': return 11; // 红马
            case 'R': return 12; // 红车
            case 'C': return 13; // 红炮
            case 'P': return 14; // 红兵
            default: return 0; // 空位置
        }
    }
    
    // 从FEN字符串生成ChessInfo对象
    private ChessInfo fenToChessInfo(String fen) {
        ChessInfo info = new ChessInfo();
        
        // 设置setting属性
        if (setting != null) {
            info.setting = setting;
        }
        
        // 清空棋盘
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                info.piece[y][x] = 0;
            }
        }
        
        // 分割FEN字符串
        String[] parts = fen.split(" ");
        if (parts.length < 2) {
            return info; // 返回空棋盘
        }
        
        // 解析棋盘部分
        String boardPart = parts[0];
        String[] rows = boardPart.split("/");
        
        // 确保有10行
        if (rows.length == 10) {
            // 现在FEN是标准格式，从黑方底线开始到红方底线结束
            // ChessInfo.piece数组是y=0表示红方底线，y=9表示黑方底线
            for (int y = 0; y < 10; y++) {
                // FEN的第1行（黑方底线）对应ChessInfo.piece[9][x]
                // FEN的第10行（红方底线）对应ChessInfo.piece[0][x]
                int chessY = 9 - y;
                String row = rows[y];
                int x = 0;
                for (int i = 0; i < row.length(); i++) {
                    char c = row.charAt(i);
                    if (Character.isDigit(c)) {
                        // 数字表示连续的空位置
                        int emptyCount = Character.getNumericValue(c);
                        // 确保空位置也被设置为0
                        for (int j = 0; j < emptyCount && x < 9; j++) {
                            info.piece[chessY][x] = 0;
                            x++;
                        }
                    } else {
                        // 字符表示棋子
                        if (x < 9) {
                            info.piece[chessY][x] = fenToPiece(c);
                            x++;
                        }
                    }
                }
                // 确保该行剩余的位置也被设置为0
                while (x < 9) {
                    info.piece[chessY][x] = 0;
                    x++;
                }
            }
        }
        
        // 解析回合部分
        if (parts.length >= 2) {
            String turnPart = parts[1];
            // FEN中 'w' 表示白方（红方）走，'b' 表示黑方走
            info.IsRedGo = turnPart.equals("w");
        }
        
        return info;
    }

    private void initMusic() {
        // 初始化音乐
        try {
            backMusic = MediaPlayer.create(this, R.raw.background);
            selectMusic = MediaPlayer.create(this, R.raw.select);
            clickMusic = MediaPlayer.create(this, R.raw.click);
            checkMusic = MediaPlayer.create(this, R.raw.checkmate);
            winMusic = MediaPlayer.create(this, R.raw.win);
            
            backMusic.setLooping(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void playEffect(MediaPlayer mediaPlayer) {
        if (setting != null && setting.isEffectPlay) {
            if (mediaPlayer != null) {
                mediaPlayer.seekTo(0);
                mediaPlayer.start();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // 重新读取设置，确保深度设置能够随时生效
        if (sharedPreferences != null) {
            setting = new Setting(sharedPreferences);
            if (chessInfo != null) {
                chessInfo.setting = setting;
            }
            // 更新显示当前搜索深度
            updateDepthDisplay();
        }
        
        // 恢复音乐
        if (setting != null && setting.isMusicPlay && backMusic != null) {
            backMusic.start();
        }
    }
    
    // 更新深度显示
    private void updateDepthDisplay() {
        if (aiInfoTextView != null && setting != null) {
            String currentText = aiInfoTextView.getText().toString();
            // 检查是否已经包含深度信息
            if (!currentText.contains("搜索深度:")) {
                // 如果是初始文本，替换整个文本
                if (currentText.equals("支招-获取AI建议")) {
                    aiInfoTextView.setText("支招-获取AI建议 | 搜索深度: " + setting.depth);
                } else {
                    // 否则在末尾添加深度信息
                    aiInfoTextView.setText(currentText + " | 搜索深度: " + setting.depth);
                }
            } else {
                // 更新深度信息
                String newText = currentText.replaceAll("搜索深度: \\d+", "搜索深度: " + setting.depth);
                aiInfoTextView.setText(newText);
            }
        }
    }
    
    // 用于定时更新搜索深度的线程
    private ExecutorService executorService;
    private ScheduledExecutorService scheduledExecutorService;
    private volatile boolean updateRunning = false;
    private int dotCount = 0;
    private long lastDotUpdateTime = 0;

    // 开始AI搜索时更新深度显示
    private void startAISearch() {
        if (setting != null) {
            updateAIInfoText("AI正在思考. | 搜索深度: 0");
        }
        
        // 启动更新线程
        updateRunning = true;
        lastDotUpdateTime = System.currentTimeMillis();
        updateThread = new Thread(() -> {
            while (updateRunning) {
                if (pikafishAI != null) {
                    final int currentDepth = pikafishAI.getCurrentDepth();
                    // 每2秒更新一次点
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastDotUpdateTime >= 800) {
                        dotCount = (dotCount + 1) % 4;
                        lastDotUpdateTime = currentTime;
                    }
                    // 生成点字符串
                    StringBuilder dots = new StringBuilder();
                    for (int i = 0; i < dotCount; i++) {
                        dots.append(".");
                    }
                    final String dotString = dots.toString();
                    runOnUiThread(() -> {
                        updateAIInfoText("AI正在思考" + dotString + " | 搜索深度: " + currentDepth);
                    });
                }
                // 每隔100毫秒更新一次
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        updateThread.start();
    }

    // 停止AI搜索时停止深度更新
    private void stopAISearch() {
        updateRunning = false;
        if (updateThread != null) {
            updateThread.interrupt();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停音乐
        if (backMusic != null && backMusic.isPlaying()) {
            backMusic.pause();
        }
        
        // 停止所有后台线程，防止Activity状态丢失
        isAIAnalyzing = false;
        updateRunning = false;
        
        // 在后台线程中保存游戏状态，避免阻塞主线程
        new Thread(() -> {
            try {
                SaveInfo.SerializeChessInfo(chessInfo, "ChessInfo_pvm.bin");
                SaveInfo.SerializeInfoSet(infoSet, "InfoSet_pvm.bin");
            } catch (Exception e) {
                    e.printStackTrace();
                }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止所有后台线程
        isAIAnalyzing = false;
        updateRunning = false;
        
        // 释放音乐资源
        if (backMusic != null) {
            backMusic.release();
            backMusic = null;
        }
        if (selectMusic != null) {
            selectMusic.release();
            selectMusic = null;
        }
        if (clickMusic != null) {
            clickMusic.release();
            clickMusic = null;
        }
        if (checkMusic != null) {
            checkMusic.release();
            checkMusic = null;
        }
        if (winMusic != null) {
            winMusic.release();
            winMusic = null;
        }
        
        // 停止AI引擎
        if (pikafishAI != null) {
            // PikafishAI没有destroy方法，直接设置为null
            pikafishAI = null;
        }
        
        // 停止更新线程
        stopAISearch();
        
        // 关闭线程池
        if (executorService != null) {
            executorService.shutdown();
        }
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 显示退出确认对话框
            new AlertDialog.Builder(this)
                    .setTitle("退出确认")
                    .setMessage("确定要退出游戏吗？")
                    .setPositiveButton("确定", (dialog, which) -> finish())
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            // 从棋谱管理界面返回，可能选择了一个棋谱
            currentNotation = (ChessNotation) data.getSerializableExtra("notation");
            if (currentNotation != null) {
                // 初始化棋盘状态为初始状态
                chessInfo = new ChessInfo();
                infoSet = new InfoSet();
                // 重新设置setting属性
                if (setting != null) {
                    chessInfo.setting = setting;
                }
                // 先更新视图的chessInfo引用
                if (chessView != null) {
                    chessView.setChessInfo(chessInfo);
                }
                if (roundView != null) {
                    roundView.setChessInfo(chessInfo);
                }
                currentMoveIndex = 0;
                // 重置继续对局后的回合计数器
                continueGameRoundCount = 0;
                // 生成初始棋盘状态
                generateBoardStateFromNotation();
                // 重新绘制界面
                if (chessView != null) {
                    chessView.requestDraw();
                }
                if (roundView != null) {
                    roundView.requestDraw();
                }
                Toast.makeText(this, "棋谱已加载，点击下一步开始回放", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 1002 && resultCode == RESULT_OK && data != null) {
            // 从SAF文件选择器返回
            Uri uri = data.getData();
            if (uri != null) {
                // 获取永久读取权限
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                // 加载棋谱
                loadChessNotationFromUri(uri);
            }
        } else if (requestCode == 1003 && resultCode == RESULT_OK && data != null) {
            // 从SAF保存文件选择器返回
            Uri uri = data.getData();
            if (uri != null) {
                // 获取永久写入权限
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                // 保存棋谱
                String fileName = data.getStringExtra("fileName");
                saveChessNotationToUri(uri, fileName);
            }
        }
    }
    
    // 检查双方老将是否见面
    private boolean isKingFaceToFace(int[][] piece) {
        // 查找红帅和黑将的位置
        Info.Pos redKingPos = null;
        Info.Pos blackKingPos = null;
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (piece[i][j] == 8) { // 红帅
                    redKingPos = new Info.Pos(j, i);
                } else if (piece[i][j] == 1) { // 黑将
                    blackKingPos = new Info.Pos(j, i);
                }
            }
        }
        
        // 如果找不到红帅或黑将，返回false
        if (redKingPos == null || blackKingPos == null) {
            return false;
        }
        
        // 检查红帅和黑将是否在同一列
        if (redKingPos.x != blackKingPos.x) {
            return false;
        }
        
        // 检查红帅和黑将之间是否有其他棋子
        int startY = Math.min(redKingPos.y, blackKingPos.y) + 1;
        int endY = Math.max(redKingPos.y, blackKingPos.y);
        
        for (int y = startY; y < endY; y++) {
            if (piece[y][redKingPos.x] != 0) {
                return false;
            }
        }
        
        // 红帅和黑将在同一列且中间没有其他棋子，返回true
        return true;
    }
}
