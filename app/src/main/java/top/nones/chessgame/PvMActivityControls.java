package top.nones.chessgame;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import Info.ChessInfo;
import Info.InfoSet;
import Info.Pos;
import ChessMove.Rule;
import CustomView.ChessView;
import CustomView.RoundView;
import Utils.LogUtils;

public class PvMActivityControls {
    private PvMActivity activity;
    private final Object aiAnalysisLock = new Object();
    private volatile boolean isAIAnalyzing = false;
    private long lastSuggestClickTime = 0;
    private static final long SUGGEST_BUTTON_INTERVAL = 1200;
    private boolean isForceVariationDialogShowing = false; // 防止强制变着对话框重复弹出
    private boolean justExecutedForceVariation = false; // 标记刚刚执行了强制变着
    private int forceVariationCooldown = 0; // 强制变着后冷却回合数，三回合内不再提示
    private int forceVariationHintRound = 0; // 记录上次浮窗提示的回合数
    private long lastCheckHintTime = 0; // 记录上次将军提示的时间戳
    
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
            // 重置setupFEN，确保新局使用标准初始局面
            activity.notationManager.setSetupFEN(null);
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
            // 重新读取设置，确保新模式下使用最新设置
            if (PvMActivity.setting != null && activity.chessInfo != null) {
                activity.chessInfo.setting = PvMActivity.setting;
            }
            // 更新PikafishAI的设置
            if (activity.pikafishAI != null) {
                int skillLevel = PvMActivity.setting != null ? PvMActivity.setting.skillLevel : 20;
                int multiPV = PvMActivity.setting != null ? PvMActivity.setting.multiPV : 1;
                int depth = PvMActivity.setting != null ? PvMActivity.setting.depth : 10;
                int thinkingTime = PvMActivity.setting != null ? PvMActivity.setting.mLevel : 5;
                activity.pikafishAI.updateSettings(skillLevel, multiPV, depth, thinkingTime);
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

        // 检查AI是否正在分析，如果是则禁止人类玩家移动棋子
        if (activity.aiManager != null && activity.aiManager.isAIAnalyzing) {
            return false;
        }

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
                                        
                                        // 只有被将军时才检查棋子是否能解将
                                        boolean canDefendCheck = true;
                                        // 检查当前行棋方的王是否被将军
                                        boolean isChecked = Rule.isKingDanger(activity.chessInfo.piece, activity.chessInfo.IsRedGo);
                                        if (isChecked) {
                                            // 检查点击的棋子是否能够解将
                                            canDefendCheck = Rule.CanDefendCheck(activity.chessInfo.piece, i, j, pieceID);
                                        }
                                        
                                        if (canSelect && canDefendCheck) {
                                            // 开始计时
                                            activity.startTurnTimer();
                                            activity.chessInfo.prePos = new Pos(i, j);
                                            activity.chessInfo.IsChecked = true;
                                            java.util.List<Pos> possibleMoves = Rule.PossibleMoves(activity.chessInfo.piece, i, j, pieceID);
                                            
                                            activity.chessInfo.ret = possibleMoves;
                                            
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

                                        // 检查移动前是否被将军
                                        boolean wasChecked = Rule.isKingDanger(activity.chessInfo.piece, isRed);
                                        
                                        // 如果被将军，检查移动是否能解将
                                        if (wasChecked) {
                                            // 创建棋盘的临时副本
                                            int[][] tempPiece = new int[10][9];
                                            for (int row = 0; row < 10; row++) {
                                                for (int col = 0; col < 9; col++) {
                                                    tempPiece[row][col] = activity.chessInfo.piece[row][col];
                                                }
                                            }
                                            
                                            // 执行移动
                                            tempPiece[targetY][targetX] = piece;
                                            tempPiece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x] = 0;
                                            
                                            // 检查移动后是否还被将军
                                            boolean isStillChecked = Rule.isKingDanger(tempPiece, isRed);
                                            if (isStillChecked) {
                                                // 移除Toast提示，通过界面显示提示信息
                                                return false;
                                            }
                                        }

                                        activity.chessInfo.piece[targetY][targetX] = piece;
                                        activity.chessInfo.piece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x] = 0;

                                        // 检查是否吃掉了对方的老将
                                        boolean isCaptureKing = tmp == 1 || tmp == 8;
                                        if (isCaptureKing) {
                                            // 吃掉对方老将，游戏结束
                                            activity.chessInfo.IsChecked = false;
                                            activity.chessInfo.curPos = new Pos(targetX, targetY);
                                            activity.chessInfo.Select = new int[]{-1, -1}; // 重置选中状态
                                            activity.chessInfo.ret.clear(); // 清空可移动位置

                                            // 生成并记录标准象棋记谱走法
                                            String moveString = activity.generateMoveString(activity.chessInfo, piece, activity.chessInfo.prePos, activity.chessInfo.curPos, isRed);
                                            if (moveString != null) {
                                                Utils.LogUtils.i("Move", "用户走棋: " + moveString);
                                            }

                                            // 停止计时
                                            activity.stopTurnTimer();

                                            // 游戏结束
                                            activity.chessInfo.status = 2;
                                            // 移除Toast提示，通过界面显示胜利信息

                                            // 保存移动后的状态到栈中
                                            try {
                                                activity.infoSet.pushInfo(activity.chessInfo);
                                            } catch (CloneNotSupportedException e) {
                                                e.printStackTrace();
                                            }

                                            // 重新绘制界面
                                            if (activity.chessView != null) {
                                                activity.chessView.requestDraw();
                                            }
                                            if (activity.roundView != null) {
                                                activity.roundView.requestDraw();
                                            }

                                            return false;
                                        }

                                        // 检查移动后是否出现双方老将见面的情况
                                        if (isKingFaceToFace(activity.chessInfo.piece)) {
                                            activity.chessInfo.piece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x] = piece;
                                            activity.chessInfo.piece[targetY][targetX] = tmp;
                                            // 移除Toast提示，通过界面显示提示信息
                                        } else {
                                            // 检查移动后是否会导致自己被将军
                                            boolean isCheckAfterMove = Rule.isKingDanger(activity.chessInfo.piece, isRed);
                                            if (isCheckAfterMove) {
                                                activity.chessInfo.piece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x] = piece;
                                                activity.chessInfo.piece[targetY][targetX] = tmp;
                                                // 移除Toast提示，通过界面显示提示信息
                                                return false;
                                            }
                                            
                                            // 检查移动是否解将（如果之前被将军）
                                            if (wasChecked) {
                                                boolean isStillChecked = Rule.isKingDanger(activity.chessInfo.piece, isRed);
                                                if (isStillChecked) {
                                                    activity.chessInfo.piece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x] = piece;
                                                    activity.chessInfo.piece[targetY][targetX] = tmp;
                                                    // 移除Toast提示，通过界面显示提示信息
                                                    return false;
                                                }
                                            }
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
                                            if (key == 1) {
                                                // 移除Toast提示，通过界面显示提示信息
                                            }

                                            // 增加继续对局后的回合计数器
                                            activity.continueGameRoundCount++;

                                            // 检查游戏状态，包括强制变着和和棋条件
                                            checkGameStatus(isRed);

                                            // 获取当前局面的评分（在后台线程中执行）
                                            // 只在非双人对战模式下获取评分，避免双人对战时显示AI思考
                                            if (activity.pikafishAI != null && activity.pikafishAI.isInitialized() && activity.gameMode != 0) {
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
                                                
                                                // 玩家落子后清除支招信息（只有获得支招的一方落子后才清除）
                                                if (activity.gameManager != null) {
                                                    if (activity.gameManager.shouldClearSuggest(isRed)) {
                                                        activity.gameManager.clearSuggest();
                                                    }
                                                }
                                                
                                                // 检查是否需要AI移动
                                                activity.gameManager.checkAIMove();
                                        }
                                    } else if (pieceID != 0) {
                                        // 只有当点击的位置有棋子时，才检查是否可以选择新棋子
                                        // 检查是否是当前回合的颜色的棋子
                                        boolean canSelect = (isRedPiece && activity.chessInfo.IsRedGo) || (!isRedPiece && !activity.chessInfo.IsRedGo);
                                        
                                        // 只有被将军时才检查棋子是否能解将
                                        boolean canDefendCheck = true;
                                        // 检查当前行棋方的王是否被将军
                                        boolean isChecked = Rule.isKingDanger(activity.chessInfo.piece, activity.chessInfo.IsRedGo);
                                        if (isChecked) {
                                            canDefendCheck = Rule.CanDefendCheck(activity.chessInfo.piece, i, j, pieceID);
                                        }
                                        
                                        if (canSelect && canDefendCheck) {
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
            // 移除Toast提示，通过界面显示和棋信息
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
        // 检查是否将死
        if (Rule.isCheckmate(activity.chessInfo.piece, !isRed)) {
            key = 2;
        }
        // 检查是否被困毙
        if (Rule.isStalemate(activity.chessInfo.piece, !isRed)) {
            key = 3;
        }
        
        if (key == 1) {
            long currentTime = System.currentTimeMillis();
            // 确保一次将军只提示一次，通过时间戳控制
            if (currentTime - lastCheckHintTime > 1000) { // 1秒内只提示一次
                Toast toast = Toast.makeText(activity, "将军", Toast.LENGTH_SHORT);
                toast.setGravity(android.view.Gravity.CENTER, 0, 0);
                // 设置文本颜色为红色
                try {
                    View view = toast.getView();
                    if (view != null) {
                        TextView textView = view.findViewById(android.R.id.message);
                        if (textView != null) {
                            textView.setTextColor(android.graphics.Color.RED);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                toast.show();
                // 设置500毫秒后取消提示
                activity.getWindow().getDecorView().postDelayed(() -> {
                    try {
                        toast.cancel();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, 500);
                lastCheckHintTime = currentTime;
            }
        } else if (key == 2 || key == 3) {
            // 检查将死或被困毙，游戏结束
            activity.chessInfo.status = 2;
            // 停止计时
            activity.stopTurnTimer();
            // 重新绘制界面
            if (activity.chessView != null) {
                activity.chessView.requestDraw();
            }
            if (activity.roundView != null) {
                activity.roundView.requestDraw();
            }
        }
        
            // 检查和棋条件，无论是否在摆棋模式下
        if (activity.chessInfo.status == 1) {
            // 检查冷却回合数
            if (forceVariationCooldown > 0) {
                forceVariationCooldown--;
                LogUtils.i("PvMActivityControls", "强制变着冷却中，剩余回合: " + forceVariationCooldown);
            } else {
                // 如果刚刚执行了强制变着，跳过强制变着检查
                if (!justExecutedForceVariation) {
                    // 检查三次重复局面，后台强制变着并显示浮窗提示
                    if (activity.chessInfo.isThreefoldRepetition()) {
                        handleForceVariation();
                        return;
                    }
                    
                    // 长将和长捉暂时不启用强制变着，只在真正的局面重复时启用
                    // 检查长将，后台强制变着并显示浮窗提示
                    // if (activity.chessInfo.isPerpetualCheck()) {
                    //     handleForceVariation();
                    //     return;
                    // }
                    
                    // 检查长捉，后台强制变着并显示浮窗提示
                    // if (activity.chessInfo.getPerpetualAttackSide() != null) {
                    //     handleForceVariation();
                    //     return;
                    // }
                } else {
                    // 重置强制变着标志，允许下次检查
                    justExecutedForceVariation = false;
                }
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
    
    // 处理强制变着逻辑
    private void handleForceVariation() {
        
        // 重置重复局面计数
        String currentHash = activity.chessInfo.generatePositionHash();
        if (activity.chessInfo.positionHistory.containsKey(currentHash)) {
            activity.chessInfo.positionHistory.put(currentHash, 1);
        }
        // 重置长将计数
        activity.chessInfo.consecutiveCheckRed = 0;
        activity.chessInfo.consecutiveCheckBlack = 0;
        // 重置长捉计数
        activity.chessInfo.consecutiveAttackRed = 0;
        activity.chessInfo.consecutiveAttackBlack = 0;
        activity.chessInfo.lastAttackedPiecePos = null;
        activity.chessInfo.lastAttackedPieceType = 0;
        // 重置继续对局后的回合计数器
        activity.continueGameRoundCount = 0;
        // 设置强制变着冷却回合数为3，三回合内不再检查
        forceVariationCooldown = 3;
        LogUtils.i("PvMActivityControls", "设置强制变着冷却，3回合内不再检查");
        
        // 检查是否需要显示浮窗提示（十回合内只提示一次）
        if (activity.chessInfo.totalMoves - forceVariationHintRound >= 10) {
            showForceVariationHint();
            forceVariationHintRound = activity.chessInfo.totalMoves;
        }
        
        // 重新绘制界面
        if (activity.chessView != null) {
            activity.chessView.requestDraw();
        }
        if (activity.roundView != null) {
            activity.roundView.requestDraw();
        }
        
        // 只有在非用户模式（人机对战或双机对战）下才启用强制变着模式
        if (activity.gameMode != 0) {
            // 启用强制变着模式
            activity.chessInfo.forceVariation = true;
            activity.chessInfo.variationRandomness = 3; // 设置中等随机性
            // 不立即检查AI移动，让AI在自己的回合正常行棋
            // activity.gameManager.checkAIMove();
        }
        
        // 标记刚刚执行了强制变着，跳过下次和棋检查
        justExecutedForceVariation = true;
    }
    

    
    // 显示输棋确认对话框
    private void showLoseConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("认输确认");
        builder.setMessage("强制变着后您将立即输棋，是否认输？");
        builder.setPositiveButton("认输", (dialog, which) -> {
            activity.chessInfo.status = 2;
            Toast toast = Toast.makeText(activity, activity.chessInfo.IsRedGo ? "黑方获得胜利" : "红方获得胜利", Toast.LENGTH_SHORT);
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
        builder.setNegativeButton("继续变着", (dialog, which) -> {
            // 继续强制变着
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
            // 设置强制变着冷却回合数为3，三回合内不再检查
            forceVariationCooldown = 3;
            LogUtils.i("PvMActivityControls", "设置强制变着冷却，3回合内不再检查");
            
            // 显示强制变着提示
            if (activity.chessInfo.totalMoves - forceVariationHintRound >= 10) {
                showForceVariationHint();
                forceVariationHintRound = activity.chessInfo.totalMoves;
            }
            
            // 重新绘制界面
            if (activity.chessView != null) {
                activity.chessView.requestDraw();
            }
            if (activity.roundView != null) {
                activity.roundView.requestDraw();
            }
            
            // 只有在非用户模式（人机对战或双机对战）下才启用强制变着模式
            if (activity.gameMode != 0) {
                // 启用强制变着模式
                activity.chessInfo.forceVariation = true;
                activity.chessInfo.variationRandomness = 3; // 设置中等随机性
            }
            
            // 标记刚刚执行了强制变着，跳过下次和棋检查
            justExecutedForceVariation = true;
        });
        builder.setCancelable(false);
        builder.show();
    }
    
    // 显示强制变着浮窗提示
    private void showForceVariationHint() {
        String message = "";
        if (activity.chessInfo.isPerpetualCheck()) {
            String side = activity.chessInfo.getPerpetualCheckSide();
            message = side + "长将，已强制变着";
        } else if (activity.chessInfo.getPerpetualAttackSide() != null) {
            String side = activity.chessInfo.getPerpetualAttackSide();
            message = side + "长捉，已强制变着";
        } else {
            message = "检测到重复局面，已强制变着";
        }
        
        // 创建浮窗提示
        Toast toast = Toast.makeText(activity, message, Toast.LENGTH_SHORT);
        toast.setGravity(android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL, 0, 100);
        toast.show();
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
            // 设置强制变着冷却回合数为3，三回合内不再提示
            forceVariationCooldown = 3;
            LogUtils.i("PvMActivityControls", "设置强制变着冷却，3回合内不再提示");
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
            
            // 标记刚刚执行了强制变着，跳过下次和棋检查
            justExecutedForceVariation = true;
            
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
            // 移除Toast提示，通过界面显示和棋信息
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