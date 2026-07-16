package top.nones.chessgame;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
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
    
    // 播放音效
    private void playEffect(MediaPlayer mediaPlayer) {
        Utils.SoundManager.playEffect(mediaPlayer);
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
        try {
            LogUtils.d("PvMActivityControls", "handleRetryButton called");
            // 显示新局确认对话框
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
            builder.setTitle("新局确认");
            builder.setMessage("确定要开始新局吗？当前游戏进度将被清除。");
            builder.setPositiveButton("确定", (dialog, which) -> {
                try {
                    // 完全重置游戏状态
                    try {
                        // 创建新的ChessInfo对象
                        activity.chessInfo = new ChessInfo();
                        // 重新设置setting属性
                        if (activity.setting != null) {
                            activity.chessInfo.setting = activity.setting;
                        }
                        // 确保摆棋模式被关闭
                        activity.chessInfo.IsSetupMode = false;
                        
                        // 创建新的InfoSet对象
                        activity.infoSet = new InfoSet();
                        // 重新推入初始状态
                        activity.infoSet.pushInfo(activity.chessInfo);
                    } catch (CloneNotSupportedException e) {
                        LogUtils.e("PvMActivityControls", "Error pushing info in retry", e);
                    }
                    
                    // 重置棋谱相关变量
                    activity.notationManager.setCurrentNotation(null);
                    activity.notationManager.setCurrentMoveIndex(0);
                    // 未加载棋谱：同步上一步/下一步按钮为不可用
                    activity.notationManager.updateNavButtonsEnabled();
                    // 重置setupFEN，确保新局使用标准初始局面
                    activity.notationManager.setSetupFEN(null);
                    // 重置继续对局后的回合计数器
                    activity.continueGameRoundCount = 0;
                    // 重置时间
                    activity.redTime = 0;
                    activity.blackTime = 0;
                    activity.currentTurnStartTime = 0;
                    activity.updateTimeDisplay();

                    // 重新绘制界面，更新所有视图的chessInfo引用
                    if (activity.chessView != null) {
                        activity.chessView.setChessInfo(activity.chessInfo);
                        activity.chessView.requestDraw();
                    }
                    if (activity.roundView != null) {
                        activity.roundView.setChessInfo(activity.chessInfo);
                        activity.roundView.setMoveScore(0);
                        // 新局后清空显示信息（步数信息 + 残留支招信息）
                        activity.roundView.setMoveInfoText("");
                        activity.roundView.setSuggestMoveText("");
                        activity.roundView.requestDraw();
                    }
                    if (activity.setupModeView != null) {
                        activity.setupModeView.setChessInfo(activity.chessInfo);
                        activity.setupModeView.setVisibility(View.GONE);
                    }
                    // 确保摆棋按钮文字恢复为"摆棋"
                    android.view.View setupBtn = activity.findViewById(R.id.btn_setup);
                    if (setupBtn instanceof android.widget.Button) {
                        ((android.widget.Button) setupBtn).setText("摆棋");
                    }
                    // 还原摆棋按钮视觉状态并恢复其它被禁用的按钮
                    activity.applySetupModeButtonUI(false);
                    // 停止之前的 AI 分析，避免状态残留
                    if (activity.aiManager != null) {
                        activity.aiManager.stopAIAnalysis();
                    }
                    // 新局：重置开局库，下一局双机对战将重新随机选取开局
                    if (activity.aiManager != null) {
                        activity.aiManager.resetOpeningBook();
                    }
                    // 新局后检查是否需要 AI 先手
                    if (activity.gameManager != null) {
                        activity.gameManager.checkAIMove();
                    }
                    LogUtils.d("PvMActivityControls", "handleRetryButton completed");
                } catch (Exception e) {
                    LogUtils.e("PvMActivityControls", "Error in positive button click", e);
                }
            });
            builder.setNegativeButton("取消", null);
            builder.show();
        } catch (Exception e) {
            LogUtils.e("PvMActivityControls", "Error in handleRetryButton", e);
        }
    }
    
    // 处理悔棋按钮
    public void handleRecallButton() {
        try {
            LogUtils.d("PvMActivityControls", "handleRecallButton called");
            if (activity.aiManager != null) {
                activity.aiManager.stopAIAnalysis();
            }
            if (activity.infoSet != null && activity.infoSet.preInfo != null
                    && activity.chessInfo != null && activity.infoSet.curInfo != null) {
                int size = activity.infoSet.preInfo.size();
                if (size > 1) {
                    activity.infoSet.preInfo.pop();
                    restoreBoardState(activity.infoSet.preInfo.peek());
                } else if (size == 1) {
                    restoreBoardState(activity.infoSet.preInfo.peek());
                }
            }
            if (activity.gameManager != null) {
                activity.gameManager.checkAIMove();
            }
            LogUtils.d("PvMActivityControls", "handleRecallButton completed");
        } catch (Exception e) {
            LogUtils.e("PvMActivityControls", "Error in handleRecallButton", e);
        }
    }

    private void restoreBoardState(ChessInfo state) {
        if (state == null) return;
        try {
            activity.chessInfo.setInfo(state);
            activity.infoSet.curInfo.setInfo(state);
            activity.chessInfo.prePos = null;
            activity.chessInfo.curPos = null;
            activity.redTime = 0;
            activity.blackTime = 0;
            activity.currentTurnStartTime = 0;
            activity.updateTimeDisplay();
            if (activity.chessView != null) {
                activity.chessView.requestDraw();
            }
            if (activity.roundView != null) {
                activity.roundView.requestDraw();
            }
            activity.triggerPositionEvaluation();
        } catch (CloneNotSupportedException e) {
            LogUtils.e("PvMActivityControls", "Error restoring board state", e);
        }
    }
    
    // 处理设置按钮
    public void handleSettingsButton() {
        try {
            LogUtils.d("PvMActivityControls", "handleSettingsButton called");
            // 显示设置对话框
            CustomDialog.SettingDialog_PvM settingDialog = new CustomDialog.SettingDialog_PvM(activity);
            settingDialog.setOnClickBottomListener(new SettingDialogListener(settingDialog));
            settingDialog.show();
            LogUtils.d("PvMActivityControls", "handleSettingsButton completed");
        } catch (Exception e) {
            LogUtils.e("PvMActivityControls", "Error in handleSettingsButton", e);
        }
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
        try {
            LogUtils.d("PvMActivityControls", "handleModeButton called");
            // 弹出统一的模式选择底部弹窗（图标卡片）
            ModePickerDialog dialog = new ModePickerDialog(activity, activity.gameMode, mode -> {
                try {
                    // 先停止当前AI分析，避免isAIAnalyzing标志残留导致棋子无法选中
                    if (activity.aiManager != null) {
                        activity.aiManager.stopAIAnalysis();
                    }
                    activity.gameMode = mode;
                    // 切换到双机对战时重置开局库，立即重新随机选取开局
                    if (mode == 3 && activity.aiManager != null) {
                        activity.aiManager.resetOpeningBook();
                    }
                    // 更新RoundView的游戏模式显示
                    if (activity.roundView != null) {
                        activity.roundView.setGameMode(mode);
                    }
                    // 重新读取设置，确保新模式下使用最新设置
                    if (activity.setting != null && activity.chessInfo != null) {
                        activity.chessInfo.setting = activity.setting;
                    }
                    // updateSettings 已移到 AIThreadRunnable 后台执行，无需在此处同步调用（避免主线程 ANR）
                    // 不重置游戏，从当前棋局开始
                    // 检查是否需要AI移动
                    activity.gameManager.checkAIMove();
                    LogUtils.d("PvMActivityControls", "handleModeButton completed");
                } catch (Exception e) {
                    LogUtils.e("PvMActivityControls", "Error in mode button click", e);
                }
            });
            dialog.show();
        } catch (Exception e) {
            LogUtils.e("PvMActivityControls", "Error in handleModeButton", e);
        }
    }
    
    // 处理统计/支招按钮
    public void handleStatisticsButton() {
        try {
            LogUtils.d("PvMActivityControls", "handleStatisticsButton called");

            // 如果 AI 正在分析，立即中断（不受点击间隔限制）
            if (activity.aiManager != null && activity.aiManager.isAIAnalyzing) {
                LogUtils.d("PvMActivityControls", "AI is analyzing, interrupting it");
                activity.aiManager.stopAIAnalysis();
                if (activity.pikafishAI != null) {
                    activity.pikafishAI.interrupt();
                }
                updateSuggestButton(false);
                return;
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSuggestClickTime < SUGGEST_BUTTON_INTERVAL) {
                // 点击间隔小于限制，不处理点击
                LogUtils.d("PvMActivityControls", "Suggest button click skipped due to interval");
                return;
            }
            lastSuggestClickTime = currentTime;

            if (activity.chessInfo != null && !activity.chessInfo.IsSetupMode) {
                // 自动为当前行棋方支招
                boolean currentPlayerIsRed = activity.chessInfo.IsRedGo;
                activity.aiManager.showAIMove(currentPlayerIsRed);
            }
            LogUtils.d("PvMActivityControls", "handleStatisticsButton completed");
        } catch (Exception e) {
            LogUtils.e("PvMActivityControls", "Error in handleStatisticsButton", e);
        }
    }

    // 更新支招按钮的 UI 状态：analyzing=true 显示"中断"+红色，false 显示"支招"+青色
    public void updateSuggestButton(boolean analyzing) {
        try {
            android.view.View btnView = activity.findViewById(R.id.btn_statistics);
            if (!(btnView instanceof Button)) return;
            Button btn = (Button) btnView;
            if (analyzing) {
                btn.setText("中断");
                btn.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_stop, 0, 0);
                btn.setBackgroundResource(R.drawable.bg_board_btn_stop_red);
            } else {
                btn.setText("支招");
                btn.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_suggest, 0, 0);
                btn.setBackgroundResource(R.drawable.bg_board_btn_suggest_teal);
            }
        } catch (Exception e) {
            LogUtils.e("PvMActivityControls", "Error updating suggest button", e);
        }
    }
    
    // 处理上一步按钮
    public void handlePrevButton() {
        try {
            LogUtils.d("PvMActivityControls", "handlePrevButton called");
            activity.notationManager.handlePrevButton();
            LogUtils.d("PvMActivityControls", "handlePrevButton completed");
        } catch (Exception e) {
            LogUtils.e("PvMActivityControls", "Error in handlePrevButton", e);
        }
    }
    
    // 处理下一步按钮
    public void handleNextButton() {
        try {
            LogUtils.d("PvMActivityControls", "handleNextButton called");
            activity.notationManager.handleNextButton();
            LogUtils.d("PvMActivityControls", "handleNextButton completed");
        } catch (Exception e) {
            LogUtils.e("PvMActivityControls", "Error in handleNextButton", e);
        }
    }
    
    // 处理加载棋谱按钮
    public void handleLoadNotationButton() {
        try {
            LogUtils.d("PvMActivityControls", "handleLoadNotationButton called");
            // 打开棋谱管理界面
            Intent intent = new Intent(activity, NotationActivity.class);
            intent.putExtra("returnToGame", true);
            activity.startActivityForResult(intent, 1001);
            LogUtils.d("PvMActivityControls", "handleLoadNotationButton completed");
        } catch (Exception e) {
            LogUtils.e("PvMActivityControls", "Error in handleLoadNotationButton", e);
        }
    }
    
    private static final long CHECK_HINT_INTERVAL_MS = 1000;
    private static final int GAME_STATUS_PLAYING = 1;
    private static final int GAME_STATUS_ENDED = 2;

    public boolean handleTouch(View view, MotionEvent event) {
        try {
            long now = System.currentTimeMillis();
            if (now - activity.curClickTime < PvMActivity.MIN_CLICK_DELAY_TIME) {
                return false;
            }
            activity.curClickTime = now;

            if (activity.aiManager != null && activity.aiManager.isAIAnalyzing) {
                return false;
            }

            if (event.getAction() != MotionEvent.ACTION_DOWN) {
                return false;
            }

            float x = event.getX();
            float y = event.getY();

            if (activity.chessInfo == null || activity.chessInfo.status != GAME_STATUS_PLAYING) {
                return false;
            }

            if (activity.chessInfo.IsSetupMode) {
                activity.setupManager.handleSetupModeTouch(x, y, event);
                return false;
            }

            if (activity.chessView == null
                    || x < 0 || x > activity.chessView.Board_width
                    || y < 0 || y > activity.chessView.Board_height) {
                return false;
            }

            activity.chessInfo.Select = activity.getPos(event);
            int col = activity.chessInfo.Select[0];
            int row = activity.chessInfo.Select[1];

            if (col < 0 || col > 8 || row < 0 || row > 9 || activity.chessInfo.piece == null) {
                return false;
            }

            int pieceID = activity.chessInfo.piece[row][col];

            if (!activity.chessInfo.IsChecked) {
                handlePieceSelection(col, row, pieceID);
            } else {
                handlePieceMoveOrReselect(col, row, pieceID);
            }
        } catch (Exception e) {
            LogUtils.e("PvMActivityControls", "Error in handleTouch", e);
        }
        return false;
    }

    private boolean canSelectPiece(int pieceID) {
        boolean isRedPiece = pieceID >= 8 && pieceID <= 14;
        return (isRedPiece && activity.chessInfo.IsRedGo)
                || (!isRedPiece && !activity.chessInfo.IsRedGo);
    }

    private boolean canPieceDefendCheck(int col, int row, int pieceID) {
        boolean isChecked = Rule.isKingDanger(activity.chessInfo.piece, activity.chessInfo.IsRedGo);
        if (!isChecked) return true;
        return Rule.CanDefendCheck(activity.chessInfo.piece, col, row, pieceID);
    }

    private void handlePieceSelection(int col, int row, int pieceID) {
        if (pieceID == 0) return;
        if (!canSelectPiece(pieceID)) return;
        if (!canPieceDefendCheck(col, row, pieceID)) {
            showCheckHint();
            return;
        }
        activity.startTurnTimer();
        activity.chessInfo.prePos = new Pos(col, row);
        activity.chessInfo.IsChecked = true;
        activity.chessInfo.ret = Rule.PossibleMoves(activity.chessInfo.piece, col, row, pieceID);
        playEffect(activity.selectMusic);
        if (activity.chessView != null) {
            activity.chessView.requestDraw();
        }
    }

    private void handlePieceMoveOrReselect(int col, int row, int pieceID) {
        Pos target = new Pos(col, row);
        if (activity.chessInfo.ret != null && activity.chessInfo.ret.contains(target)) {
            executePieceMove(col, row);
        } else if (pieceID != 0 && canSelectPiece(pieceID)
                && canPieceDefendCheck(col, row, pieceID)) {
            activity.chessInfo.prePos = new Pos(col, row);
            activity.chessInfo.ret = Rule.PossibleMoves(activity.chessInfo.piece, col, row, pieceID);
            if (activity.chessView != null) {
                activity.chessView.requestDraw();
            }
        }
    }

    private void executePieceMove(int targetX, int targetY) {
        if (activity.chessInfo.prePos == null) return;

        int capturedPiece = activity.chessInfo.piece[targetY][targetX];
        int movingPiece = activity.chessInfo.piece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x];
        boolean isRed = movingPiece >= 8 && movingPiece <= 14;
        boolean wasChecked = Rule.isKingDanger(activity.chessInfo.piece, isRed);

        if (wasChecked) {
            int[][] tempPiece = ChessMove.Rule.copyBoard(activity.chessInfo.piece);
            tempPiece[targetY][targetX] = movingPiece;
            tempPiece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x] = 0;
            if (Rule.isKingDanger(tempPiece, isRed)) {
                return;
            }
        }

        activity.chessInfo.piece[targetY][targetX] = movingPiece;
        activity.chessInfo.piece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x] = 0;

        boolean isCaptureKing = capturedPiece == 1 || capturedPiece == 8;
        if (isCaptureKing) {
            finishMoveWithKingCapture(targetX, targetY, movingPiece, isRed, capturedPiece);
            return;
        }

        if (isKingFaceToFace(activity.chessInfo.piece)) {
            activity.chessInfo.piece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x] = movingPiece;
            activity.chessInfo.piece[targetY][targetX] = capturedPiece;
            return;
        }

        if (Rule.isKingDanger(activity.chessInfo.piece, isRed)) {
            activity.chessInfo.piece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x] = movingPiece;
            activity.chessInfo.piece[targetY][targetX] = capturedPiece;
            showSelfCheckHint();
            return;
        }

        if (wasChecked && Rule.isKingDanger(activity.chessInfo.piece, isRed)) {
            activity.chessInfo.piece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x] = movingPiece;
            activity.chessInfo.piece[targetY][targetX] = capturedPiece;
            return;
        }

        completeNormalMove(targetX, targetY, movingPiece, isRed, capturedPiece, wasChecked);
    }

    private void finishMoveWithKingCapture(int targetX, int targetY, int movingPiece,
                                           boolean isRed, int capturedPiece) {
        activity.chessInfo.IsChecked = false;
        activity.chessInfo.curPos = new Pos(targetX, targetY);
        activity.chessInfo.Select = new int[]{-1, -1};
        activity.chessInfo.ret.clear();

        playEffect(activity.captureMusic);

        String moveString = activity.generateMoveString(activity.chessInfo, movingPiece,
                activity.chessInfo.prePos, activity.chessInfo.curPos, isRed);
        if (moveString != null) {
            Utils.LogUtils.i("Move", "用户走棋: " + moveString);
        }

        activity.stopTurnTimer();
        activity.chessInfo.status = GAME_STATUS_ENDED;

        try {
            activity.infoSet.pushInfo(activity.chessInfo);
        } catch (CloneNotSupportedException e) {
            LogUtils.e("PvMActivityControls", "操作失败", e);
        }

        if (activity.chessView != null) activity.chessView.requestDraw();
        if (activity.roundView != null) activity.roundView.requestDraw();
    }

    private void completeNormalMove(int targetX, int targetY, int movingPiece,
                                    boolean isRed, int capturedPiece, boolean wasChecked) {
        activity.chessInfo.IsChecked = false;
        activity.chessInfo.curPos = new Pos(targetX, targetY);
        activity.chessInfo.Select = new int[]{-1, -1};
        activity.chessInfo.ret.clear();

        String moveString = activity.generateMoveString(activity.chessInfo, movingPiece,
                activity.chessInfo.prePos, activity.chessInfo.curPos, isRed);
        if (moveString != null) {
            Utils.LogUtils.i("Move", "用户走棋: " + moveString);
        }

        activity.stopTurnTimer();

        boolean isCheck = Rule.isKingDanger(activity.chessInfo.piece, !isRed);
        activity.chessInfo.updateAllInfo(activity.chessInfo.prePos, activity.chessInfo.curPos,
                movingPiece, capturedPiece, isCheck);

        if (isCheck) {
            playEffect(activity.checkMusic);
        } else if (capturedPiece != 0) {
            playEffect(activity.captureMusic);
        } else {
            playEffect(activity.clickMusic);
        }

        activity.startTurnTimer();

        try {
            activity.infoSet.pushInfo(activity.chessInfo);
        } catch (CloneNotSupportedException e) {
            LogUtils.e("PvMActivityControls", "操作失败", e);
        }

        if (isCheck) {
            long now = System.currentTimeMillis();
            if (now - lastCheckHintTime > CHECK_HINT_INTERVAL_MS) {
                android.widget.Toast toast = android.widget.Toast.makeText(activity,
                        "正在被将军", android.widget.Toast.LENGTH_SHORT);
                toast.setGravity(android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL, 0, 150);
                toast.show();
                lastCheckHintTime = now;
            }
        }

        activity.continueGameRoundCount++;
        checkGameStatus(isRed);
        activity.triggerPositionEvaluation();

        if (activity.chessView != null) activity.chessView.requestDraw();
        if (activity.roundView != null) activity.roundView.requestDraw();

        if (activity.gameManager != null) {
            if (activity.gameManager.shouldClearSuggest(isRed)) {
                activity.gameManager.clearSuggest();
            }
            activity.gameManager.checkAIMove();
        }
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
        return Rule.isKingFaceToFace(piece);
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
                    LogUtils.e("PvMActivityControls", "操作失败", e);
                }
                toast.show();
                // 设置500毫秒后取消提示
                activity.getWindow().getDecorView().postDelayed(() -> {
                    try {
                        toast.cancel();
                    } catch (Exception e) {
                        LogUtils.e("PvMActivityControls", "操作失败", e);
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
                    // 检查用户是否开启了强制变着功能
                    boolean forceVariationEnabled = activity.setting != null && activity.setting.forceVariation;
                    
                    // 检查三次重复局面，后台强制变着并显示浮窗提示
                    if (activity.chessInfo.isThreefoldRepetition()) {
                        if (forceVariationEnabled) {
                            handleForceVariation();
                        } else {
                            LogUtils.i("PvMActivityControls", "检测到三次重复局面，但用户已关闭强制变着");
                        }
                        return;
                    }
                    
                    // 检查长将，后台强制变着并显示浮窗提示
                    if (activity.chessInfo.isPerpetualCheck()) {
                        if (forceVariationEnabled) {
                            handleForceVariation();
                        } else {
                            LogUtils.i("PvMActivityControls", "检测到长将，但用户已关闭强制变着");
                        }
                        return;
                    }
                    
                    // 检查长捉，后台强制变着并显示浮窗提示
                    if (activity.chessInfo.getPerpetualAttackSide() != null) {
                        if (forceVariationEnabled) {
                            handleForceVariation();
                        } else {
                            LogUtils.i("PvMActivityControls", "检测到长捉，但用户已关闭强制变着");
                        }
                        return;
                    }
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
        
        // 根据象棋规则判断哪一方需要变着
        String forbiddenSide = activity.chessInfo.getForbiddenSide();
        boolean isBothForbidden = activity.chessInfo.isBothSidesPerpetualCheck() || 
                                  activity.chessInfo.isBothSidesPerpetualAttack();
        
        // 如果双方都禁止（双方长将或双方长捉），判和
        if (isBothForbidden) {
            activity.chessInfo.status = 2;
            android.widget.Toast toast = android.widget.Toast.makeText(activity, "双方长将/长捉，此乃和棋", android.widget.Toast.LENGTH_SHORT);
            toast.setGravity(android.view.Gravity.CENTER, 0, 0);
            toast.show();
            return;
        }
        
        // 如果一方禁止一方允许，只有禁止方需要变着
        boolean needForceVariation = false;
        if (forbiddenSide != null) {
            // 当前回合方是否是禁止方
            boolean isRedTurn = activity.chessInfo.IsRedGo;
            boolean isForbiddenSideTurn = (forbiddenSide.equals("红方") && isRedTurn) || 
                                          (forbiddenSide.equals("黑方") && !isRedTurn);
            needForceVariation = isForbiddenSideTurn;
        } else {
            // 三次重复局面，双方都需要变着
            needForceVariation = true;
        }
        
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
            if (forbiddenSide != null) {
                showForceVariationHintWithSide(forbiddenSide);
            } else {
                showForceVariationHint();
            }
            forceVariationHintRound = activity.chessInfo.totalMoves;
        }
        
        // 重新绘制界面
        if (activity.chessView != null) {
            activity.chessView.requestDraw();
        }
        if (activity.roundView != null) {
            activity.roundView.requestDraw();
        }
        
        // 只有在非用户模式（人机对战或双机对战）下且当前回合方需要变着时才启用强制变着模式
        if (activity.gameMode != 0 && needForceVariation) {
            // 启用强制变着模式
            activity.chessInfo.forceVariation = true;
            activity.chessInfo.variationRandomness = 3; // 设置中等随机性
            // 标记刚刚执行了强制变着，跳过下次和棋检查
            justExecutedForceVariation = true;
            // 不立即检查AI移动，让AI在自己的回合正常行棋
            // activity.gameManager.checkAIMove();
        } else {
            // 当前回合方不需要变着，关闭强制变着模式
            activity.chessInfo.forceVariation = false;
        }
    }
    

    
    private void showCheckHint() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCheckHintTime > 1500) {
            android.widget.Toast toast = android.widget.Toast.makeText(activity, "正被将军", android.widget.Toast.LENGTH_SHORT);
            toast.setGravity(android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL, 0, 150);
            toast.show();
            lastCheckHintTime = currentTime;
        }
    }

    private void showSelfCheckHint() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCheckHintTime > 1500) {
            android.widget.Toast toast = android.widget.Toast.makeText(activity, "移动后将被将军", android.widget.Toast.LENGTH_SHORT);
            toast.setGravity(android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL, 0, 150);
            toast.show();
            lastCheckHintTime = currentTime;
        }
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
    
    // 显示强制变着浮窗提示（带变着方信息）
    private void showForceVariationHintWithSide(String forbiddenSide) {
        String message = forbiddenSide + "违规，必须变着！";
        if (activity.chessInfo.isPerpetualCheck()) {
            message = forbiddenSide + "长将，必须变着！";
        } else if (activity.chessInfo.getPerpetualAttackSide() != null) {
            message = forbiddenSide + "长捉，必须变着！";
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