package top.nones.chessgame;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import Info.ChessInfo;
import Info.InfoSet;
import Info.Pos;
import ChessMove.Rule;
import CustomView.ChessView;
import CustomView.RoundView;

public class PvMActivityControls {
    private PvMActivity activity;
    private final Object aiAnalysisLock = new Object();
    private volatile boolean isAIAnalyzing = false;
    private long lastSuggestClickTime = 0;
    private static final long SUGGEST_BUTTON_INTERVAL = 1200;
    private boolean isForceVariationDialogShowing = false; // 防止强制变着对话框重复弹出
    
    public PvMActivityControls(PvMActivity activity) {
        this.activity = activity;
    }
    
    // 递归设置按钮监听器，处理嵌套布局
    public void setupButtonListeners(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof Button) {
                // 直接是Button
                Button btn = (Button) child;
                btn.setOnClickListener(activity);
            } else if (child instanceof ViewGroup) {
                // 是ViewGroup，递归处理
                setupButtonListeners((ViewGroup) child);
            }
        }
    }
    
    // 处理重试按钮
    public void handleRetryButton() {
        // 显示新局确认对话框
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
        builder.setTitle("新局确认");
        builder.setMessage("确定要开始新局吗？当前游戏进度将被清除。");
        builder.setPositiveButton("确定", (dialog, which) -> {
            // 完全重置游戏状态
            try {
                // 创建新的ChessInfo对象
                activity.chessInfo = new ChessInfo();
                // 重新设置setting属性
                if (PvMActivity.setting != null) {
                    activity.chessInfo.setting = PvMActivity.setting;
                }
                // 确保摆棋模式被关闭
                activity.chessInfo.IsSetupMode = false;
                
                // 创建新的InfoSet对象
                activity.infoSet = new InfoSet();
                // 重新推入初始状态
                activity.infoSet.pushInfo(activity.chessInfo);
            } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
            }
            
            // 重置棋谱相关变量
            activity.notationManager.setCurrentNotation(null);
            activity.notationManager.setCurrentMoveIndex(0);
            // 重置继续对局后的回合计数器
            activity.continueGameRoundCount = 0;
            // 重置时间
            activity.redTime = 0;
            activity.blackTime = 0;
            activity.currentTurnStartTime = 0;
            activity.updateTimeDisplay();

            // 重新绘制界面
            if (activity.chessView != null) {
                activity.chessView.setChessInfo(activity.chessInfo);
                activity.chessView.requestDraw();
            }
            if (activity.roundView != null) {
                activity.roundView.setChessInfo(activity.chessInfo);
                activity.roundView.requestDraw();
            }
            if (activity.setupModeView != null) {
                activity.setupModeView.setChessInfo(activity.chessInfo);
                activity.setupModeView.setVisibility(View.GONE);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    // 处理悔棋按钮
    public void handleRecallButton() {
        if (activity.infoSet != null && activity.infoSet.preInfo != null && activity.chessInfo != null && activity.infoSet.curInfo != null) {
            // 确保保留至少一个初始状态，只允许悔到初始状态，但不会把初始状态也悔掉
            if (activity.infoSet.preInfo.size() > 1) {
                // 弹出栈顶元素（当前状态）
                activity.infoSet.preInfo.pop();
                // 恢复到新的栈顶元素的状态
                ChessInfo tmp = activity.infoSet.preInfo.peek();
                try {
                    if (tmp != null) {
                        // 恢复棋盘状态
                        activity.chessInfo.setInfo(tmp);
                        activity.infoSet.curInfo.setInfo(tmp);
                        // 清除当前chessInfo的过时走法记录，避免保存棋谱时处理到这些值
                        activity.chessInfo.prePos = null;
                        activity.chessInfo.curPos = null;
                        // 重置时间
                        activity.redTime = 0;
                        activity.blackTime = 0;
                        activity.currentTurnStartTime = 0;
                        activity.updateTimeDisplay();
                        // 重新绘制界面
                        if (activity.chessView != null) {
                            activity.chessView.requestDraw();
                        }
                        if (activity.roundView != null) {
                            activity.roundView.requestDraw();
                        }

                    }
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();

                }
            } else if (activity.infoSet.preInfo.size() == 1) {
                // 只剩一个状态了，这就是初始状态，直接恢复它但不弹出
                ChessInfo tmp = activity.infoSet.preInfo.peek();
                try {
                    if (tmp != null) {
                        // 恢复棋盘状态
                        activity.chessInfo.setInfo(tmp);
                        activity.infoSet.curInfo.setInfo(tmp);
                        // 清除当前chessInfo的过时走法记录，避免保存棋谱时处理到这些值
                        activity.chessInfo.prePos = null;
                        activity.chessInfo.curPos = null;
                        // 重置时间
                        activity.redTime = 0;
                        activity.blackTime = 0;
                        activity.currentTurnStartTime = 0;
                        activity.updateTimeDisplay();
                        // 重新绘制界面
                        if (activity.chessView != null) {
                            activity.chessView.requestDraw();
                        }
                        if (activity.roundView != null) {
                            activity.roundView.requestDraw();
                        }

                    }
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();

                }
            }
        }
    }
    
    // 处理设置按钮
    public void handleSettingsButton() {
        // 显示设置对话框
        CustomDialog.SettingDialog_PvM settingDialog = new CustomDialog.SettingDialog_PvM(activity);
        settingDialog.setOnClickBottomListener(new SettingDialogListener(settingDialog));
        settingDialog.show();
    }
    
    // 静态内部类，避免匿名内部类导致的空指针异常
    private static class SettingDialogListener implements CustomDialog.SettingDialog_PvM.OnClickBottomListener {
        private final CustomDialog.SettingDialog_PvM dialog;
        
        public SettingDialogListener(CustomDialog.SettingDialog_PvM dialog) {
            this.dialog = dialog;
        }
        
        @Override
        public void onPositiveClick() {
            if (dialog != null) {
                dialog.dismiss();
            }
        }
        
        @Override
        public void onNegtiveClick() {
            if (dialog != null) {
                dialog.dismiss();
            }
        }
    }
    
    // 处理模式按钮
    public void handleModeButton() {
        // 显示模式切换对话框
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
        builder.setTitle("选择对战模式");
        builder.setItems(new String[]{"双人对战", "人机对战(玩家红)", "人机对战(玩家黑)", "双机对战"}, (dialog, which) -> {
            activity.gameMode = which;
            // 更新RoundView的游戏模式显示
            if (activity.roundView != null) {
                activity.roundView.setGameMode(which);
            }
            // 不重置游戏，从当前棋局开始
            // 检查是否需要AI移动
            activity.gameManager.checkAIMove();
        });
        builder.show();
    }
    
    // 处理统计/支招按钮
    public void handleStatisticsButton() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSuggestClickTime < SUGGEST_BUTTON_INTERVAL) {
            // 点击间隔小于限制，不处理点击
            return;
        }
        lastSuggestClickTime = currentTime;
        
        if (activity.chessInfo != null && !activity.chessInfo.IsSetupMode && !isAIAnalyzing) {
            // 自动为当前行棋方支招
            boolean currentPlayerIsRed = activity.chessInfo.IsRedGo;
            activity.aiManager.showAIMove(currentPlayerIsRed);
        } else {
        }
    }
    
    // 处理上一步按钮
    public void handlePrevButton() {
        activity.notationManager.handlePrevButton();
    }
    
    // 处理下一步按钮
    public void handleNextButton() {
        activity.notationManager.handleNextButton();
    }
    
    // 处理加载棋谱按钮
    public void handleLoadNotationButton() {
        // 打开棋谱管理界面
        Intent intent = new Intent(activity, NotationActivity.class);
        intent.putExtra("returnToGame", true);
        activity.startActivityForResult(intent, 1001);
    }
    
    // 处理触摸事件
    public boolean handleTouch(View view, MotionEvent event) {
        long lastClickTime = System.currentTimeMillis();
        if (lastClickTime - PvMActivity.curClickTime < PvMActivity.MIN_CLICK_DELAY_TIME) {
            return false;
        }
        PvMActivity.curClickTime = lastClickTime;

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            if (activity.chessInfo != null && activity.chessInfo.status == 1) {
                // 摆棋模式处理
                    if (activity.chessInfo.IsSetupMode) {
                        activity.setupManager.handleSetupModeTouch(x, y, event);
                    } 
                // 正常游戏模式处理
                else {
                    if (activity.chessView != null && x >= 0 && x <= activity.chessView.Board_width && y >= 0 && y <= activity.chessView.Board_height) {
                        activity.chessInfo.Select = activity.getPos(event);
                        // 直接使用原始位置，不进行反转，因为棋盘状态本身没有被反转
                        int i = activity.chessInfo.Select[0];
                        int j = activity.chessInfo.Select[1];

                        if (i >= 0 && i <= 8 && j >= 0 && j <= 9 && activity.chessInfo.piece != null) {
                            // 获取棋子ID
                            int pieceID = activity.chessInfo.piece[j][i];
                            boolean isRedPiece = pieceID >= 8 && pieceID <= 14;
                            
                            // 双人对战模式
                            boolean canMove = true;

                            if (canMove) {
                                if (activity.chessInfo.IsChecked == false) {
                                    // 只有当点击的位置有棋子时，才检查是否可以选择
                                    if (pieceID != 0) {
                                        // 检查是否是当前回合的颜色的棋子
                                        boolean canSelect = (isRedPiece && activity.chessInfo.IsRedGo) || (!isRedPiece && !activity.chessInfo.IsRedGo);
                                        
                                        if (canSelect) {
                                            // 开始计时
                                            activity.startTurnTimer();
                                            activity.chessInfo.prePos = new Pos(i, j);
                                            activity.chessInfo.IsChecked = true;
                                            java.util.List<Pos> possibleMoves = Rule.PossibleMoves(activity.chessInfo.piece, i, j, pieceID);
                                            
                                            // 检查是否被将军，如果是，只保留可以解将的移动
                                            if (Rule.isKingDanger(activity.chessInfo.piece, isRedPiece)) {
                                                java.util.List<Pos> validMoves = new java.util.ArrayList<>();
                                                for (Pos pos : possibleMoves) {
                                                    // 模拟移动
                                                    int tmp = activity.chessInfo.piece[pos.y][pos.x];
                                                    activity.chessInfo.piece[pos.y][pos.x] = pieceID;
                                                    activity.chessInfo.piece[j][i] = 0;
                                                    
                                                    // 检查移动后是否还被将军
                                                    if (!Rule.isKingDanger(activity.chessInfo.piece, isRedPiece)) {
                                                        validMoves.add(pos);
                                                    }
                                                    
                                                    // 撤销移动
                                                    activity.chessInfo.piece[j][i] = pieceID;
                                                    activity.chessInfo.piece[pos.y][pos.x] = tmp;
                                                }
                                                activity.chessInfo.ret = validMoves;
                                                
                                                // 如果没有可解将的移动，提示将死
                                                if (validMoves.isEmpty()) {
                                                    Toast toast = Toast.makeText(activity, isRedPiece ? "红方被将死！黑方胜利" : "黑方被将死！红方胜利", Toast.LENGTH_SHORT);
                                                    toast.setGravity(android.view.Gravity.CENTER, 0, 0);
                                                    toast.show();
                                                }
                                            } else {
                                                activity.chessInfo.ret = possibleMoves;
                                            }
                                            
                                            // 重新绘制界面，显示选中效果
                                            if (activity.chessView != null) {
                                                activity.chessView.requestDraw();
                                            }
                                        }
                                    }
                                } else {
                                    // 直接使用原始坐标
                                    int targetX = i;
                                    int targetY = j;
                                    
                                    // 首先检查是否是有效的移动位置
                                    if (activity.chessInfo.ret.contains(new Pos(targetX, targetY))) {
                                        int tmp = activity.chessInfo.piece[targetY][targetX];
                                        int piece = activity.chessInfo.piece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x];
                                        boolean isRed = piece >= 8 && piece <= 14;

                                        activity.chessInfo.piece[targetY][targetX] = piece;
                                        activity.chessInfo.piece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x] = 0;

                                        // 检查移动后是否被将军
                                        if (Rule.isKingDanger(activity.chessInfo.piece, isRed)) {
                                            activity.chessInfo.piece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x] = piece;
                                            activity.chessInfo.piece[targetY][targetX] = tmp;
                                            Toast toast = Toast.makeText(activity, isRed ? "帅被将军" : "将被将军", Toast.LENGTH_SHORT);
                                            toast.setGravity(android.view.Gravity.CENTER, 0, 0);
                                            toast.show();
                                        } 
                                        // 检查移动后是否出现双方老将见面的情况
                                        else if (isKingFaceToFace(activity.chessInfo.piece)) {
                                            activity.chessInfo.piece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x] = piece;
                                            activity.chessInfo.piece[targetY][targetX] = tmp;
                                            Toast toast = Toast.makeText(activity, "双方老将不能见面", Toast.LENGTH_SHORT);
                                            toast.setGravity(android.view.Gravity.CENTER, 0, 0);
                                            toast.show();
                                        } else {
                                            activity.chessInfo.IsChecked = false;
                                            activity.chessInfo.curPos = new Pos(targetX, targetY);
                                            activity.chessInfo.Select = new int[]{-1, -1}; // 重置选中状态
                                            activity.chessInfo.ret.clear(); // 清空可移动位置

                                            // 生成并记录标准象棋记谱走法
                                            String moveString = activity.generateMoveString(activity.chessInfo, piece, activity.chessInfo.prePos, activity.chessInfo.curPos, isRed);
                                            if (moveString != null) {
                                                Utils.LogUtils.i("Move", "用户走棋: " + moveString);
                                            }

                                            // 停止计时（在updateAllInfo之前调用，确保获取正确的行棋方）
                                            activity.stopTurnTimer();

                                            // 检查是否将军
                                            boolean isCheck = Rule.isKingDanger(activity.chessInfo.piece, !isRed);
                                            activity.chessInfo.updateAllInfo(activity.chessInfo.prePos, activity.chessInfo.curPos, piece, tmp, isCheck);

                                            // 开始对方的回合计时
                                            activity.startTurnTimer();

                                            // 保存移动后的状态到栈中
                                            try {
                                                activity.infoSet.pushInfo(activity.chessInfo);
                                            } catch (CloneNotSupportedException e) {
                                                e.printStackTrace();
                                            }

                                            int key = 0;
                                            if (Rule.isKingDanger(activity.chessInfo.piece, !isRed)) {
                                                key = 1;
                                            }
                                            if (Rule.isDead(activity.chessInfo.piece, !isRed)) {
                                                key = 2;
                                            }
                                            if (key == 1) {
                                                Toast toast = Toast.makeText(activity, "将军", Toast.LENGTH_SHORT);
                                                toast.setGravity(android.view.Gravity.CENTER, 0, 0);
                                                toast.show();
                                            } else if (key == 2) {
                                                activity.chessInfo.status = 2;
                                                Toast toast = Toast.makeText(activity, isRed ? "红方获得胜利" : "黑方获得胜利", Toast.LENGTH_SHORT);
                                                toast.setGravity(android.view.Gravity.CENTER, 0, 0);
                                                toast.show();
                                            }

                                            // 增加继续对局后的回合计数器
                                            activity.continueGameRoundCount++;

                                            // 检查游戏状态，包括强制变着和和棋条件
                                            checkGameStatus(isRed);

                                            // 获取当前局面的评分（在后台线程中执行）
                                            if (activity.pikafishAI != null && activity.pikafishAI.isInitialized()) {
                                                new Thread(() -> {
                                                    AICore.PikafishAI.MoveWithScore moveWithScore = activity.pikafishAI.getBestMoveWithScore(activity.chessInfo);
                                                    int score = moveWithScore.score;
                                                    
                                                    // 确保评分始终以红方为基准
                                                    boolean isRedTurn = activity.chessInfo.IsRedGo;
                                                    score = PvMActivity.normalizeScore(score, isRedTurn);
                                                    
                                                    final int finalScore = score;
                                                    // 更新评分显示
                                                    activity.runOnUiThread(() -> {
                                                        if (activity.roundView != null) {
                                                            activity.roundView.setMoveScore(finalScore);
                                                        }
                                                    });
                                                }).start();
                                            }
                                            
                                            // 重新绘制界面
                                                if (activity.chessView != null) {
                                                    activity.chessView.requestDraw();
                                                }
                                                if (activity.roundView != null) {
                                                    activity.roundView.requestDraw();
                                                }
                                                
                                                // 检查是否需要AI移动
                                                activity.gameManager.checkAIMove();
                                        }
                                    } else if (pieceID != 0) {
                                        // 只有当点击的位置有棋子时，才检查是否可以选择新棋子
                                        // 检查是否是当前回合的颜色的棋子
                                        boolean canSelect = (isRedPiece && activity.chessInfo.IsRedGo) || (!isRedPiece && !activity.chessInfo.IsRedGo);
                                        
                                        if (canSelect) {
                                            activity.chessInfo.prePos = new Pos(i, j);
                                            activity.chessInfo.ret = Rule.PossibleMoves(activity.chessInfo.piece, i, j, pieceID);
                                            // 重新绘制界面，显示选中效果
                                            if (activity.chessView != null) {
                                                activity.chessView.requestDraw();
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
    
    // 显示和棋确认对话框
    public void showDrawConfirmationDialog(String message) {
        // 暂时保存当前游戏状态
        int originalStatus = activity.chessInfo.status;
        // 设置游戏状态为暂停，防止AI继续移动
        activity.chessInfo.status = 3; // 3表示暂停状态
        
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("和棋确认");
        builder.setMessage(message);
        builder.setPositiveButton("同意和棋", (dialog, which) -> {
            activity.chessInfo.status = 2;
            Toast toast = Toast.makeText(activity, "此乃和棋", Toast.LENGTH_SHORT);
            toast.setGravity(android.view.Gravity.CENTER, 0, 0);
            toast.show();
            // 游戏结束时重新绘制界面
            if (activity.chessView != null) {
                activity.chessView.requestDraw();
            }
            if (activity.roundView != null) {
                activity.roundView.requestDraw();
            }
        });
        builder.setNegativeButton("继续对局", (dialog, which) -> {
            // 恢复原始游戏状态
            activity.chessInfo.status = originalStatus;
            // 重置继续对局后的回合计数器
            activity.continueGameRoundCount = 0;
            // 重置和棋相关计数器，避免频繁提示
            if (activity.chessInfo.peaceRound >= 30) {
                activity.chessInfo.peaceRound = 0;
            }
            // 重置重复局面计数（清除当前局面的记录）
            String currentHash = activity.chessInfo.generatePositionHash();
            if (activity.chessInfo.positionHistory.containsKey(currentHash)) {
                activity.chessInfo.positionHistory.put(currentHash, 1);
            }
            // 重置长将计数
            activity.chessInfo.consecutiveCheckRed = 0;
            activity.chessInfo.consecutiveCheckBlack = 0;
            // 重新绘制界面
            if (activity.chessView != null) {
                activity.chessView.requestDraw();
            }
            if (activity.roundView != null) {
                activity.roundView.requestDraw();
            }
            // 检查是否需要AI移动
            activity.gameManager.checkAIMove();
        });
        builder.setCancelable(false);
        builder.show();
    }
    
    // 检查双方老将是否见面
    private boolean isKingFaceToFace(int[][] piece) {
        // 查找红帅和黑将的位置
        int redKingX = -1, redKingY = -1;
        int blackKingX = -1, blackKingY = -1;
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (piece[i][j] == 8) { // 红帅
                    redKingX = j;
                    redKingY = i;
                } else if (piece[i][j] == 1) { // 黑将
                    blackKingX = j;
                    blackKingY = i;
                }
            }
        }
        
        // 如果双方老将都存在且在同一列
        if (redKingX != -1 && blackKingX != -1 && redKingX == blackKingX) {
            // 检查中间是否有棋子
            int startY = Math.min(redKingY, blackKingY) + 1;
            int endY = Math.max(redKingY, blackKingY) - 1;
            
            for (int y = startY; y <= endY; y++) {
                if (piece[y][redKingX] != 0) {
                    return false; // 中间有棋子，不会见面
                }
            }
            return true; // 中间没有棋子，老将见面
        }
        
        return false;
    }
    
    // 检查游戏状态
    public void checkGameStatus(boolean isRed) {
        if (activity.chessInfo == null) return;
        
        int key = 0;
        if (Rule.isKingDanger(activity.chessInfo.piece, !isRed)) {
            key = 1;
        }
        if (Rule.isDead(activity.chessInfo.piece, !isRed)) {
            key = 2;
        }
        
        if (key == 1) {
            Toast toast = Toast.makeText(activity, "将军", Toast.LENGTH_SHORT);
            toast.setGravity(android.view.Gravity.CENTER, 0, 0);
            toast.show();
        } else if (key == 2) {
            activity.chessInfo.status = 2;
            Toast toast = Toast.makeText(activity, isRed ? "红方获得胜利" : "黑方获得胜利", Toast.LENGTH_SHORT);
            toast.setGravity(android.view.Gravity.CENTER, 0, 0);
            toast.show();
            // 游戏结束时重新绘制界面
            if (activity.chessView != null) {
                activity.chessView.requestDraw();
            }
            if (activity.roundView != null) {
                activity.roundView.requestDraw();
            }
        }
        
        // 检查和棋条件
        if (activity.chessInfo.status == 1) {
            // 检查三次重复局面，弹出强制变着提示
            if (!isForceVariationDialogShowing && activity.chessInfo.isThreefoldRepetition()) {
                showForceVariationDialog();
                return;
            }
            
            // 检查长将，弹出强制变着提示
            if (!isForceVariationDialogShowing && activity.chessInfo.isPerpetualCheck()) {
                showForceVariationDialog();
                return;
            }
            
            // 检查其他和棋条件，统一显示确认对话框
            String drawReason = null;
            if (activity.continueGameRoundCount >= 20) {
                if (activity.chessInfo.peaceRound >= 30) {
                    drawReason = "双方30回合内未吃子，是否和棋？";
                } else if (activity.chessInfo.attackNum_B == 0 && activity.chessInfo.attackNum_R == 0) {
                    drawReason = "双方都无攻击性棋子，是否和棋？";
                }
            }
            
            if (drawReason != null) {
                showDrawConfirmationDialog(drawReason);
            }
        }
    }
    
    // 显示强制变着对话框
    private void showForceVariationDialog() {
        // 防止重复弹出
        if (isForceVariationDialogShowing) {
            return;
        }
        
        // 标记对话框正在显示
        isForceVariationDialogShowing = true;
        
        // 暂时保存当前游戏状态
        int originalStatus = activity.chessInfo.status;
        // 设置游戏状态为暂停，防止AI继续移动
        activity.chessInfo.status = 3; // 3表示暂停状态
        
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("强制变着");
        
        // 根据变着原因设置不同的提示信息
        String message = "";
        if (activity.chessInfo.isPerpetualCheck()) {
            String side = activity.chessInfo.getPerpetualCheckSide();
            message = side + "长将，请变着！\n确认后将增加AI走法的随机性。";
        } else {
            message = "检测到重复局面，请变着！\n确认后将增加AI走法的随机性。";
        }
        builder.setMessage(message);
        builder.setPositiveButton("确认变着", (dialog, which) -> {
            // 恢复游戏状态
            activity.chessInfo.status = originalStatus;
            // 启用强制变着模式
            activity.chessInfo.forceVariation = true;
            activity.chessInfo.variationRandomness = 3; // 设置中等随机性
            // 重置重复局面计数
            String currentHash = activity.chessInfo.generatePositionHash();
            if (activity.chessInfo.positionHistory.containsKey(currentHash)) {
                activity.chessInfo.positionHistory.put(currentHash, 1);
            }
            // 重置长将计数
            activity.chessInfo.consecutiveCheckRed = 0;
            activity.chessInfo.consecutiveCheckBlack = 0;
            // 重置继续对局后的回合计数器
            activity.continueGameRoundCount = 0;
            // 无需提示，对话框已明确说明
            // 重新绘制界面
            if (activity.chessView != null) {
                activity.chessView.requestDraw();
            }
            if (activity.roundView != null) {
                activity.roundView.requestDraw();
            }
            // 立即检查是否需要AI移动，确保强制变着立即生效
            activity.gameManager.checkAIMove();
            
            // 对话框关闭，重置标志位
            isForceVariationDialogShowing = false;
        });
        builder.setNegativeButton("和棋", (dialog, which) -> {
            activity.chessInfo.status = 2;
            String toastMessage = "";
            if (activity.chessInfo.isPerpetualCheck()) {
                String side = activity.chessInfo.getPerpetualCheckSide();
                toastMessage = side + "长将，此乃和棋";
            } else {
                toastMessage = "三次重复局面，此乃和棋";
            }
            Toast toast = Toast.makeText(activity, toastMessage, Toast.LENGTH_SHORT);
            toast.setGravity(android.view.Gravity.CENTER, 0, 0);
            toast.show();
            // 游戏结束时重新绘制界面
            if (activity.chessView != null) {
                activity.chessView.requestDraw();
            }
            if (activity.roundView != null) {
                activity.roundView.requestDraw();
            }
            
            // 对话框关闭，重置标志位
            isForceVariationDialogShowing = false;
        });
        builder.setCancelable(false);
        builder.show();
    }
}