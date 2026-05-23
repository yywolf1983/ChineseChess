package top.nones.chessgame;

import android.content.Context;
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
import java.lang.ref.WeakReference;
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
import CustomView.SetupModeView;
import Utils.LogUtils;
import AICore.PikafishAI;
import ChessMove.Move;

public class PvMActivity extends AppCompatActivity implements View.OnTouchListener, View.OnClickListener {
    // 静态实例，使用WeakReference避免内存泄漏
    private static WeakReference<PvMActivity> weakInstance;
    
    // 获取Activity实例的方法
    public static PvMActivity getInstance() {
        return weakInstance != null ? weakInstance.get() : null;
    }
    
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

    // 按钮组ID
    private int buttonGroupId;
    // 对战模式：0-双人对战, 1-人机对战(玩家红), 2-人机对战(玩家黑), 3-双机对战
    public int gameMode = 0;
    
    // 继续对局后的回合计数器，用于控制和棋提示的频率
    public int continueGameRoundCount = 0;
    // AI相关变量
    public PikafishAI pikafishAI;
    
    // 行棋时间记录
    public long redTime = 0; // 红方行棋时间（毫秒）
    public long blackTime = 0; // 黑方行棋时间（毫秒）
    public long currentTurnStartTime = 0; // 当前回合开始时间
    
    // 时间更新线程
    private ScheduledExecutorService timeUpdateExecutor;
    
    // 模块管理器
    public PvMActivityNotation notationManager;
    public PvMActivitySetup setupManager;
    public PvMActivityControls controlsManager;
    public PvMActivityAI aiManager;
    public PvMActivityGame gameManager;
    
    // 相机拍照临时文件
    private java.io.File cameraImageFile;
    
    // 识别服务
    private ChessRecognitionService recognitionService;
    
    // 防止多次启动相机/图片选择器 (使用AtomicBoolean解决竞态条件)
    private java.util.concurrent.atomic.AtomicBoolean isLaunchingCamera = new java.util.concurrent.atomic.AtomicBoolean(false);
    private java.util.concurrent.atomic.AtomicBoolean isLaunchingGallery = new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile long lastCameraLaunchTime = 0;
    private volatile long lastGalleryLaunchTime = 0;
    private static final long LAUNCH_COOLDOWN_MS = 3000; // 3秒冷却时间
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pvm);
        // 初始化静态实例
        weakInstance = new WeakReference<>(this);
        relativeLayout = (RelativeLayout) findViewById(R.id.relativeLayout);

        // 恢复保存的状态
        if (savedInstanceState != null) {
            String filePath = savedInstanceState.getString("camera_image_file");
            if (filePath != null) {
                cameraImageFile = new java.io.File(filePath);
            }
        }

        // 先初始化模块
        initModules();
        
        // 使用PvMActivityInit类处理初始化逻辑
        PvMActivityInit init = new PvMActivityInit(this);
        init.init();
        init.initViews();
        init.initBackgroundTasks();
        
        // 初始化识别服务
        initRecognitionService();
        
        // 初始化时间更新线程
        initTimeUpdateExecutor();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存相机文件路径，防止Activity重建时丢失
        if (cameraImageFile != null) {
            outState.putString("camera_image_file", cameraImageFile.getAbsolutePath());
        }
    }
    
    // 初始化时间更新线程
    private void initTimeUpdateExecutor() {
        timeUpdateExecutor = Executors.newSingleThreadScheduledExecutor();
        // 每100毫秒更新一次时间显示
        timeUpdateExecutor.scheduleAtFixedRate(() -> {
            if (currentTurnStartTime > 0 && chessInfo != null && roundView != null) {
                runOnUiThread(() -> {
                    // 计算当前回合已经经过的时间
                    long elapsed = System.currentTimeMillis() - currentTurnStartTime;
                    // 显示当前行棋方的时间，同时保留对方时间
                    if (chessInfo.IsRedGo) {
                        roundView.setTime(elapsed, blackTime);
                    } else {
                        roundView.setTime(redTime, elapsed);
                    }
                });
            } else if (chessInfo != null && roundView != null) {
                // 行棋后保留当前时间
                runOnUiThread(() -> {
                    roundView.setTime(redTime, blackTime);
                });
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
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
        try {
            long currentTime = System.currentTimeMillis();
            if (currentTime - curClickTime < MIN_CLICK_DELAY_TIME) {
                LogUtils.d("PvMActivity", "Button click skipped due to debounce");
                return;
            }
            curClickTime = currentTime;

            int viewId = view.getId();
            LogUtils.d("PvMActivity", "Button clicked: " + viewId);
            
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
            } else if (viewId == R.id.btn_camera_recognize) {
                // 拍照识别 - 直接打开相机
                android.util.Log.d("PvMActivity", "点击相机按钮: isLaunchingCamera=" + isLaunchingCamera.get() + ", isLaunchingGallery=" + isLaunchingGallery.get());
                dispatchCameraIntent();
            }
        } catch (Exception e) {
            LogUtils.e("PvMActivity", "Error in button click handler", e);
        }
    }
    
    // 格式化时间（毫秒转分:秒）
    public String formatTime(long milliseconds) {
        int seconds = (int) (milliseconds / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    // 标准化评分，确保显示优势方积分
    public static int normalizeScore(int score, boolean isRedTurn) {
        // 确保评分始终以红方为基准
        // 当黑方行棋时，引擎返回的评分是基于黑方视角的，需要取反
        return isRedTurn ? score : -score;
    }
    
    // 更新时间显示
    public void updateTimeDisplay() {
        if (roundView != null) {
            roundView.setTime(redTime, blackTime);
        }
    }
    
    // 开始当前回合计时
    public void startTurnTimer() {
        // 只有在currentTurnStartTime为0时才重新开始计时
        // 这样可以避免点击棋子时重置时间
        if (currentTurnStartTime == 0) {
            currentTurnStartTime = System.currentTimeMillis();
        }
    }
    
    // 结束当前回合计时
    public void stopTurnTimer() {
        if (currentTurnStartTime > 0) {
            // 计算当前回合已经经过的时间
            long elapsed = System.currentTimeMillis() - currentTurnStartTime;
            // 更新当前行棋方的时间
            if (chessInfo != null) {
                // 现在stopTurnTimer是在updateAllInfo之前调用的
                // 所以chessInfo.IsRedGo还没有切换，使用当前的IsRedGo来更新时间
                if (chessInfo.IsRedGo) {
                    // 当前是红方回合，更新红方时间
                    redTime = elapsed;
                } else {
                    // 当前是黑方回合，更新黑方时间
                    blackTime = elapsed;
                }
            }
            // 行棋后保留当前时间，不归零
            currentTurnStartTime = 0;
            // 更新时间显示
            updateTimeDisplay();
        }
    }
    
    // 生成走法字符串
    public String generateMoveString(ChessInfo chessInfo, int piece, Pos fromPos, Pos toPos, boolean isRed) {
        // 实现走法字符串生成逻辑
        StringBuilder move = new StringBuilder();
        
        // 参数检查
        if (fromPos == null || toPos == null) {
            return "未知走法";
        }
        
        // 获取棋子名称
        String pieceName = getPieceName(piece, isRed);
        
        // 计算列号（红方从右往左数，黑方从左往右数）
        int fromCol = isRed ? (9 - fromPos.x) : (fromPos.x + 1);
        int toCol = isRed ? (9 - toPos.x) : (toPos.x + 1);
        
        // 检查同一列是否有多个相同棋子
        boolean hasSamePieceInSameColumn = false;
        if (chessInfo != null && chessInfo.piece != null) {
            for (int y = 0; y < 10; y++) {
                if (y != fromPos.y && chessInfo.piece[y] != null && chessInfo.piece[y][fromPos.x] == piece) {
                    hasSamePieceInSameColumn = true;
                    break;
                }
            }
        }
        
        // 构建走法字符串
        move.append(pieceName);
        
        // 如果是车、马、炮、兵/卒、象/相、士/仕，添加起始列号（或者前/后标识）
        if (piece == 2 || piece == 3 || piece == 4 || piece == 5 || piece == 6 || piece == 7 || 
            piece == 9 || piece == 10 || piece == 11 || piece == 12 || piece == 13 || piece == 14) {
            if (hasSamePieceInSameColumn && chessInfo != null && chessInfo.piece != null) {
                // 如果同一列有多个相同棋子，使用前/后标识
                boolean isFront = false;
                if (isRed) {
                    // 红方：y越小越靠前
                    isFront = true;
                    for (int y = 0; y < fromPos.y; y++) {
                        if (chessInfo.piece[y] != null && chessInfo.piece[y][fromPos.x] == piece) {
                            isFront = false;
                            break;
                        }
                    }
                } else {
                    // 黑方：y越大越靠前
                    isFront = true;
                    for (int y = fromPos.y + 1; y < 10; y++) {
                        if (chessInfo.piece[y] != null && chessInfo.piece[y][fromPos.x] == piece) {
                            isFront = false;
                            break;
                        }
                    }
                }
                move.append(isFront ? "前" : "后");
            } else {
                // 否则使用列号
                move.append(fromCol);
            }
        }
        
        // 计算移动类型和目标位置
        boolean isHorse = (piece == 4 || piece == 11); // 马
        boolean isElephant = (piece == 3 || piece == 10); // 象/相
        boolean isAdvisor = (piece == 2 || piece == 9); // 士/仕
        boolean isPawn = (piece == 7 || piece == 14); // 卒/兵
        
        if (isHorse || isElephant || isAdvisor) {
            // 马、象/相、士/仕的移动：斜向，用进/退+目标列号
            if (isRed && toPos.y > fromPos.y || !isRed && toPos.y < fromPos.y) {
                // 前进
                move.append("进").append(toCol);
            } else {
                // 后退
                move.append("退").append(toCol);
            }
        } else if (isPawn) {
            // 卒/兵的移动
            boolean isPawnCrossedRiver = false;
            if (isRed) {
                // 红方兵过河：y < 5（在黑方区域）
                isPawnCrossedRiver = fromPos.y < 5;
            } else {
                // 黑方卒过河：y >= 5（在红方区域）
                isPawnCrossedRiver = fromPos.y >= 5;
            }
            
            if (fromPos.x == toPos.x) {
                // 纵向移动
                int distance = Math.abs(toPos.y - fromPos.y);
                if (isRed && toPos.y > fromPos.y || !isRed && toPos.y < fromPos.y) {
                    // 前进
                    move.append("进").append(distance);
                } else {
                    // 后退（兵/卒不能后退）
                    move.append("进").append(distance);
                }
            } else {
                // 横向移动（只有过河后才能平）
                move.append("平").append(toCol);
            }
        } else {
            // 其他棋子（车、炮等）的移动
            if (fromPos.x == toPos.x) {
                // 纵向移动
                int distance = Math.abs(toPos.y - fromPos.y);
                if (isRed && toPos.y > fromPos.y || !isRed && toPos.y < fromPos.y) {
                    // 前进
                    move.append("进").append(distance);
                } else {
                    // 后退
                    move.append("退").append(distance);
                }
            } else {
                // 横向移动
                move.append("平").append(toCol);
            }
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
            default: return "未知";
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
    
    // 相机拍照
    public void dispatchCameraIntent() {
        // 使用compareAndSet保证原子性，防止竞态条件
        if (!isLaunchingCamera.compareAndSet(false, true)) {
            android.util.Log.d("PvMActivity", "相机已在启动中，跳过");
            return;
        }
        
        // 额外检查冷却时间，防止快速重复点击
        long now = System.currentTimeMillis();
        if (now - lastCameraLaunchTime < LAUNCH_COOLDOWN_MS) {
            android.util.Log.d("PvMActivity", "相机冷却中，跳过 (距上次启动=" + (now - lastCameraLaunchTime) + "ms)");
            isLaunchingCamera.set(false);
            return;
        }
        lastCameraLaunchTime = now;
        
        try {
            cameraImageFile = createImageFile();
            if (cameraImageFile == null) {
                Toast.makeText(this, "无法创建图片文件", Toast.LENGTH_SHORT).show();
                isLaunchingCamera.set(false);
                return;
            }
            
            Intent takePictureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            Uri photoURI = androidx.core.content.FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    cameraImageFile);
            takePictureIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoURI);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            android.util.Log.d("PvMActivity", "启动相机: file=" + cameraImageFile.getAbsolutePath());
            startActivityForResult(takePictureIntent, 1004);
        } catch (Exception ex) {
            android.util.Log.e("PvMActivity", "Error launching camera: " + ex.getMessage());
            Toast.makeText(this, "无法启动相机: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            isLaunchingCamera.set(false);
        }
    }
    
    // 图片选择
    public void dispatchGalleryIntent() {
        // 使用compareAndSet保证原子性，防止竞态条件
        if (!isLaunchingGallery.compareAndSet(false, true)) {
            android.util.Log.d("PvMActivity", "图片选择器已在启动中，跳过");
            return;
        }
        
        // 额外检查冷却时间，防止快速重复点击
        long now = System.currentTimeMillis();
        if (now - lastGalleryLaunchTime < LAUNCH_COOLDOWN_MS) {
            android.util.Log.d("PvMActivity", "图片选择器冷却中，跳过 (距上次启动=" + (now - lastGalleryLaunchTime) + "ms)");
            isLaunchingGallery.set(false);
            return;
        }
        lastGalleryLaunchTime = now;
        
        try {
            Intent pickPhotoIntent = new Intent(Intent.ACTION_PICK, 
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickPhotoIntent.setType("image/*");
            android.util.Log.d("PvMActivity", "启动图片选择器");
            startActivityForResult(pickPhotoIntent, 1005);
        } catch (Exception ex) {
            android.util.Log.e("PvMActivity", "Error launching gallery: " + ex.getMessage());
            // 备用方案：使用ACTION_GET_CONTENT
            try {
                Intent backupIntent = new Intent(Intent.ACTION_GET_CONTENT);
                backupIntent.setType("image/*");
                backupIntent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(backupIntent, "选择图片"), 1005);
            } catch (Exception ex2) {
                Toast.makeText(this, "无法打开图片选择: " + ex2.getMessage(), Toast.LENGTH_SHORT).show();
                isLaunchingGallery.set(false);
            }
        }
    }
    
    // 创建临时图片文件
    private java.io.File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "CHESS_" + timeStamp + "_";
        java.io.File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }
        java.io.File image = java.io.File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        android.util.Log.d("PvMActivity", "创建图片文件: " + image.getAbsolutePath());
        return image;
    }

    // 初始化识别服务
    private volatile boolean recognitionInitDone = false;

    private void initRecognitionService() {
        if (recognitionService == null) {
            new Thread(() -> {
                try {
                    recognitionService = new ChessRecognitionService(this);
                    recognitionService.initialize();
                    recognitionInitDone = true;
                    android.util.Log.d("PvMActivity", "Recognition service initialized");
                } catch (Exception e) {
                    android.util.Log.e("PvMActivity", "Failed to init recognition: " + e.getMessage());
                    recognitionInitDone = true; // 标记完成，避免死等
                }
            }).start();
        }
    }
    
    // 处理识别结果
    private void processRecognitionResult(android.graphics.Bitmap bitmap) {
        if (recognitionService == null) {
            initRecognitionService();
        }
        
        // 在后台线程中处理，避免ANR
        new Thread(() -> {
            try {
                // 等待初始化完成（最多等 10 秒）
                int waited = 0;
                while (!recognitionInitDone && waited < 100) {
                    Thread.sleep(100);
                    waited++;
                }
                if (recognitionService == null) {
                    runOnUiThread(() -> Toast.makeText(this, "识别服务未就绪", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                final android.graphics.Bitmap finalBitmap = bitmap;
                ChessInfo recognizedInfo = recognitionService.recognize(finalBitmap);
                if (recognizedInfo != null) {
                    runOnUiThread(() -> {
                        try {
                            applyRecognitionResult(recognizedInfo);
                            if (chessInfo != null) {
                                String side = chessInfo.IsRedGo ? "红方先行" : "黑方先行";
                                Toast.makeText(PvMActivity.this, "识别成功，" + side, Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            android.util.Log.e("PvMActivity", "应用识别结果失败: " + e.getMessage(), e);
                            Toast.makeText(PvMActivity.this, "应用识别结果失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        } finally {
                            // 释放缩放后的bitmap
                            if (finalBitmap != null && !finalBitmap.isRecycled()) {
                                finalBitmap.recycle();
                            }
                        }
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show());
                    // 释放bitmap
                    if (finalBitmap != null && !finalBitmap.isRecycled()) {
                        finalBitmap.recycle();
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("PvMActivity", "Recognition error: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(this, "出错: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    // 应用识别结果到棋盘：识别结果 → FEN → chessInfo → 棋盘显示（链路一致）
    private void applyRecognitionResult(ChessInfo recognizedInfo) {
        if (recognizedInfo != null && chessInfo != null) {
            FENHandler fenHandler = new FENHandler();
            
            // 1. 统计识别结果中的棋子数量
            int recognizedPieceCount = 0;
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    if (recognizedInfo.piece[i][j] > 0) recognizedPieceCount++;
                }
            }
            
            // 2. 识别结果 → FEN
            String fen = fenHandler.generateFEN(recognizedInfo);
            android.util.Log.d("PvMActivity", "识别棋子数=" + recognizedPieceCount + ", FEN=" + fen);
            
            // 3. FEN → ChessInfo（验证 FEN 解析正确性）
            ChessInfo parsedInfo = fenHandler.fenToChessInfo(fen);
            
            // 4. 自检：比对 FEN 往返是否一致
            int matchCount = 0, mismatchCount = 0;
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    if (recognizedInfo.piece[i][j] == parsedInfo.piece[i][j]) {
                        matchCount++;
                    } else {
                        mismatchCount++;
                    }
                }
            }
            android.util.Log.d("PvMActivity", "FEN往返自检: 匹配=" + matchCount + "/90, 不匹配=" + mismatchCount);
            if (mismatchCount > 0) {
                android.util.Log.e("PvMActivity", "FEN往返不一致！位置差异=" + mismatchCount);
            }
            
            // 5. 复制 FEN 解析后的棋子到 chessInfo
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    chessInfo.piece[i][j] = parsedInfo.piece[i][j];
                }
            }
            chessInfo.IsRedGo = parsedInfo.IsRedGo;
            
            // 6. 保存识别的 FEN 到 notationManager（关键！）
            if (notationManager != null) {
                notationManager.setSetupFEN(fen);
                android.util.Log.d("PvMActivity", "已保存识别FEN到notationManager: " + fen);
            }
            
            // 7. 进入摆棋模式
            if (!chessInfo.IsSetupMode) {
                if (aiManager != null) {
                    aiManager.stopAIAnalysis();
                }
                stopTurnTimer();
                chessInfo.IsSetupMode = true;
                if (setupModeView != null) {
                    android.widget.RelativeLayout.LayoutParams paramsSetup = (android.widget.RelativeLayout.LayoutParams) setupModeView.getLayoutParams();
                    if (paramsSetup != null) {
                        paramsSetup.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
                        paramsSetup.addRule(android.widget.RelativeLayout.BELOW, R.id.roundView);
                        paramsSetup.width = android.widget.RelativeLayout.LayoutParams.MATCH_PARENT;
                        paramsSetup.height = android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT;
                        paramsSetup.setMargins(30, 10, 30, 10);
                        setupModeView.setLayoutParams(paramsSetup);
                    }
                    setupModeView.setVisibility(View.VISIBLE);
                    setupModeView.bringToFront();
                }
                if (roundView != null) {
                    roundView.setVisibility(View.GONE);
                }
            }
            
            // 8. 重新计算攻击棋子数量
            chessInfo.attackNum_B = 0;
            chessInfo.attackNum_R = 0;
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    int piece = chessInfo.piece[i][j];
                    if (piece != 0) {
                        if (piece == 4 || piece == 5 || piece == 6 || piece == 7) {
                            chessInfo.attackNum_B++;
                        }
                        if (piece == 11 || piece == 12 || piece == 13 || piece == 14) {
                            chessInfo.attackNum_R++;
                        }
                    }
                }
            }
            
            // 9. 更新所有视图
            if (chessView != null) {
                chessView.setChessInfo(chessInfo);
                chessView.requestDraw();
                chessView.invalidate();
            }
            if (roundView != null) {
                roundView.setChessInfo(chessInfo);
                roundView.requestDraw();
            }
            if (setupModeView != null) {
                setupModeView.setChessInfo(chessInfo);
                setupModeView.invalidate();
                setupModeView.postInvalidate();
            }
            
            // 初始化支招相关列表（不能设为null，否则AI代码会崩溃）
            chessInfo.suggestMoves = new ArrayList<>();
            chessInfo.suggestMoveLabels = new ArrayList<>();
            chessInfo.suggestMovesIsRed = new ArrayList<>();
            chessInfo.suggestMoveNotations = new ArrayList<>();
            chessInfo.suggestFromPos = null;
            chessInfo.suggestToPos = null;
            
            // 10. 重置游戏状态（为退出摆棋模式做准备）
            chessInfo.status = 1; // 游戏状态：进行中
            // 重置 infoSet，清空之前的记录
            infoSet = new InfoSet();
            try {
                infoSet.pushInfo(chessInfo);
                android.util.Log.d("PvMActivity", "已重置infoSet并保存识别局面");
            } catch (CloneNotSupportedException e) {
                android.util.Log.e("PvMActivity", "重置infoSet失败: " + e.getMessage());
            }
            
            // 11. 验证显示：从 chessInfo 重新生成 FEN，确认显示与 FEN 一致
            String displayFen = fenHandler.generateFEN(chessInfo);
            if (!fen.equals(displayFen)) {
                android.util.Log.e("PvMActivity", "FEN→显示不一致！原FEN=" + fen + ", 显示FEN=" + displayFen);
            } else {
                android.util.Log.d("PvMActivity", "FEN→显示验证通过 ✓, 棋子数=" + recognizedPieceCount 
                    + ", 红攻击子=" + chessInfo.attackNum_R + ", 黑攻击子=" + chessInfo.attackNum_B);
            }
        }
    }
    
    // 处理Activity结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        android.util.Log.d("PvMActivity", "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        
        // 重置启动标志
        if (requestCode == 1004) {
            isLaunchingCamera.set(false);
            android.util.Log.d("PvMActivity", "相机标志已重置");
        } else if (requestCode == 1005) {
            isLaunchingGallery.set(false);
            android.util.Log.d("PvMActivity", "图片选择器标志已重置");
        }
        
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
        } else if ((requestCode == 1004 || requestCode == 1005) && resultCode == RESULT_OK) {
            // 处理相机拍照或图片选择结果
            android.graphics.Bitmap bitmap = null;
            
            if (requestCode == 1004) {
                // 相机拍照结果 - 需要处理 EXIF 旋转
                android.util.Log.d("PvMActivity", "相机返回: cameraImageFile=" + (cameraImageFile != null ? cameraImageFile.getAbsolutePath() : "null"));
                
                // 尝试从文件获取图片
                if (cameraImageFile != null && cameraImageFile.exists()) {
                    bitmap = android.graphics.BitmapFactory.decodeFile(cameraImageFile.getAbsolutePath());
                    android.util.Log.d("PvMActivity", "从文件加载图片: " + (bitmap != null ? bitmap.getWidth() + "x" + bitmap.getHeight() : "null"));
                }
                
                // 备用方案：从data intent获取图片
                if (bitmap == null && data != null && data.getExtras() != null) {
                    android.util.Log.d("PvMActivity", "尝试从data extras获取图片");
                    Object extra = data.getExtras().get("data");
                    if (extra instanceof android.graphics.Bitmap) {
                        bitmap = (android.graphics.Bitmap) extra;
                        android.util.Log.d("PvMActivity", "从data获取图片: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                    }
                }
                
                if (bitmap != null) {
                    // 读取 EXIF 方向并旋转图片到正方向
                    if (cameraImageFile != null && cameraImageFile.exists()) {
                        try {
                            android.media.ExifInterface exif = new android.media.ExifInterface(cameraImageFile.getAbsolutePath());
                            int orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 
                                    android.media.ExifInterface.ORIENTATION_NORMAL);
                            android.graphics.Matrix matrix = new android.graphics.Matrix();
                            switch (orientation) {
                                case android.media.ExifInterface.ORIENTATION_ROTATE_90:
                                    matrix.postRotate(90);
                                    break;
                                case android.media.ExifInterface.ORIENTATION_ROTATE_180:
                                    matrix.postRotate(180);
                                    break;
                                case android.media.ExifInterface.ORIENTATION_ROTATE_270:
                                    matrix.postRotate(270);
                                    break;
                                case android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                                    matrix.postScale(-1, 1);
                                    break;
                                case android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL:
                                    matrix.postScale(1, -1);
                                    break;
                                default:
                                    break;
                            }
                            if (orientation != android.media.ExifInterface.ORIENTATION_NORMAL 
                                    && orientation != android.media.ExifInterface.ORIENTATION_UNDEFINED) {
                                android.graphics.Bitmap rotated = android.graphics.Bitmap.createBitmap(
                                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                                if (rotated != bitmap) {
                                    bitmap.recycle();
                                    bitmap = rotated;
                                }
                                android.util.Log.d("PvMActivity", "已旋转图片: orientation=" + orientation 
                                        + ", 尺寸=" + bitmap.getWidth() + "x" + bitmap.getHeight());
                            }
                        } catch (Exception e) {
                            android.util.Log.e("PvMActivity", "读取EXIF失败: " + e.getMessage());
                        }
                    } else {
                        android.util.Log.w("PvMActivity", "cameraImageFile为空或不存在，跳过EXIF读取");
                    }
                }
            } else if (requestCode == 1005 && data != null) {
                // 图片选择结果
                Uri imageUri = data.getData();
                if (imageUri != null) {
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(imageUri);
                        bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
                        if (inputStream != null) {
                            inputStream.close();
                        }
                    } catch (Exception e) {
                        android.util.Log.e("PvMActivity", "Error loading image: " + e.getMessage());
                    }
                }
            }
            
            if (bitmap != null) {
                // 缩放图片到合理大小，防止OOM
                int maxSize = 1280;
                if (bitmap.getWidth() > maxSize || bitmap.getHeight() > maxSize) {
                    float scale = Math.min((float) maxSize / bitmap.getWidth(), (float) maxSize / bitmap.getHeight());
                    int newWidth = Math.round(bitmap.getWidth() * scale);
                    int newHeight = Math.round(bitmap.getHeight() * scale);
                    android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                    if (scaled != bitmap) {
                        bitmap.recycle();
                        bitmap = scaled;
                    }
                    android.util.Log.d("PvMActivity", "图片已缩放: " + newWidth + "x" + newHeight);
                }
                processRecognitionResult(bitmap);
            } else {
                Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show();
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
    protected void onPause() {
        super.onPause();
        // 暂停音乐
        if (backMusic != null && backMusic.isPlaying()) {
            backMusic.pause();
        }
        // 暂停时间更新线程
        if (timeUpdateExecutor != null) {
            timeUpdateExecutor.shutdownNow();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 停止AI分析
        if (aiManager != null) {
            aiManager.stopAIAnalysis();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 恢复音乐
        if (backMusic != null && setting != null && setting.isMusicPlay && !backMusic.isPlaying()) {
            backMusic.start();
        }
        // 重新初始化时间更新线程
        initTimeUpdateExecutor();
        
        // 安全重置相机/图片选择器标志（防止状态卡住）
        // 注意：这里不直接重置，因为onResume可能在onActivityResult之前调用
        // 只在超过冷却时间后才重置
        long now = System.currentTimeMillis();
        if (isLaunchingCamera.get() && (now - lastCameraLaunchTime > LAUNCH_COOLDOWN_MS * 2)) {
            android.util.Log.d("PvMActivity", "onResume: 检测到相机标志可能卡住，重置");
            isLaunchingCamera.set(false);
        }
        if (isLaunchingGallery.get() && (now - lastGalleryLaunchTime > LAUNCH_COOLDOWN_MS * 2)) {
            android.util.Log.d("PvMActivity", "onResume: 检测到图片选择器标志可能卡住，重置");
            isLaunchingGallery.set(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        final PikafishAI aiToClose = pikafishAI;
        final PvMActivityAI aiManagerToShutdown = aiManager;

        // 关闭时间更新线程（主线程避免等待）
        if (timeUpdateExecutor != null) {
            timeUpdateExecutor.shutdownNow();
        }

        // 将可能阻塞的关闭逻辑放到后台，避免destroy阶段卡顿
        new Thread(() -> {
            long cleanupStartMs = System.currentTimeMillis();
            if (aiToClose != null) {
                aiToClose.close();
            }
            if (aiManagerToShutdown != null) {
                aiManagerToShutdown.shutdown();
            }
            LogUtils.i("Perf", "onDestroy background cleanup cost=" + (System.currentTimeMillis() - cleanupStartMs) + "ms");
        }, "pvm-destroy-cleanup").start();

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
        // 清理静态引用
        if (weakInstance != null && weakInstance.get() == this) {
            weakInstance.clear();
            weakInstance = null;
        }
    }
}