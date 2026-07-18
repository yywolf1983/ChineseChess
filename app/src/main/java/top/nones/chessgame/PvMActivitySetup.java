package top.nones.chessgame;

import android.app.AlertDialog;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import Info.ChessInfo;
import Info.InfoSet;
import CustomView.ChessView;
import CustomView.SetupModeView;
import top.nones.chessgame.FENHandler;
import ChessMove.Rule;
import Utils.LogUtils;

public class PvMActivitySetup {
    private PvMActivity activity;
    private int selectedPieceID = 0;
    private int[] selectedBoardPiecePos = {-1, -1};
    
    public PvMActivitySetup(PvMActivity activity) {
        this.activity = activity;
    }
    
    public int getSelectedPieceID() {
        return selectedPieceID;
    }
    
    public void setSelectedPieceID(int pieceID) {
        this.selectedPieceID = pieceID;
        // 选择面板棋子时，清除棋盘上已选中的棋子，保持选中状态互斥
        if (pieceID != 0) {
            this.selectedBoardPiecePos = new int[]{-1, -1};
        }
    }
    
    public int[] getSelectedBoardPiecePos() {
        return selectedBoardPiecePos;
    }
    
    public void setSelectedBoardPiecePos(int[] pos) {
        this.selectedBoardPiecePos = pos;
    }
    
    // 放置棋子
    public void placePiece(int x, int y, int pieceID, int sourceX, int sourceY) {
        if (activity == null || activity.chessInfo == null || activity.chessInfo.piece == null) {
            return;
        }
        
        if (x < 0 || x >= 9 || y < 0 || y >= 10) {
            return;
        }
        
        // 获取锁对象
        Object lock = activity.chessInfo.getLock();
        synchronized (lock) {
            // 保存操作前的状态，用于回滚
            int originalSourcePiece = 0;
            int originalTargetPiece = 0;
            int originalAttackNum_B = activity.chessInfo.attackNum_B;
            int originalAttackNum_R = activity.chessInfo.attackNum_R;
            boolean rollbackNeeded = false;
            
            try {
                // 保存原始状态
                if (activity.chessInfo.piece[y] != null) {
                    originalTargetPiece = activity.chessInfo.piece[y][x];
                }
                if (sourceX != -1 && sourceY != -1 && sourceX >= 0 && sourceX < 9 && 
                    sourceY >= 0 && sourceY < 10 && activity.chessInfo.piece[sourceY] != null) {
                    originalSourcePiece = activity.chessInfo.piece[sourceY][sourceX];
                }
                
                // 如果是移除棋子，不需要检查数量限制
                if (pieceID != 0) {
                    // 检查棋子数量限制时，需要考虑目标位置的原棋子
                    if (!checkPieceCountWithTarget(pieceID, sourceX, sourceY, x, y)) {
                        // 显示数量限制提示
                        return;
                    }
                    
                    // 检查位置合理性
                    if (!isValidPiecePosition(pieceID, x, y)) {
                        // 显示位置不合理提示
                        return;
                    }
                }
                
                // 标记需要回滚
                rollbackNeeded = true;
                
                // 先临时保存原位置的棋子（如果有来源位置）
                if (sourceX != -1 && sourceY != -1 && sourceX >= 0 && sourceX < 9 && 
                    sourceY >= 0 && sourceY < 10 && activity.chessInfo.piece[sourceY] != null) {
                    // 先将原位置设为0，避免数量检查错误
                    activity.chessInfo.piece[sourceY][sourceX] = 0;
                }
                
                // 放置新棋子
                if (activity.chessInfo.piece[y] != null) {
                    activity.chessInfo.piece[y][x] = pieceID;
                }
                
                // 重新计算攻击棋子数量
                activity.chessInfo.attackNum_B = 0;
                activity.chessInfo.attackNum_R = 0;
                for (int i = 0; i < 10 && i < activity.chessInfo.piece.length; i++) {
                    if (activity.chessInfo.piece[i] != null) {
                        for (int j = 0; j < 9 && j < activity.chessInfo.piece[i].length; j++) {
                            int piece = activity.chessInfo.piece[i][j];
                            if (piece != 0) {
                                // 黑方攻击棋子：车(5)、马(4)、炮(6)、卒(7)
                                if (piece == 4 || piece == 5 || piece == 6 || piece == 7) {
                                    activity.chessInfo.attackNum_B++;
                                }
                                // 红方攻击棋子：车(12)、马(11)、炮(13)、兵(14)
                                else if (piece == 11 || piece == 12 || piece == 13 || piece == 14) {
                                    activity.chessInfo.attackNum_R++;
                                }
                            }
                        }
                    }
                }
                // 重新绘制界面
                if (activity.chessView != null) {
                    activity.chessView.requestDraw();
                    // 立即刷新
                    activity.chessView.invalidate();
                }
                if (activity.setupModeView != null) {
                    activity.setupModeView.invalidate();
                    // 立即刷新
                    activity.setupModeView.postInvalidate();
                }
                
                // 不再自动检查摆棋完成，由用户点击摆棋按钮结束
                
                // 检查和棋条件，确保摆棋模式下也能提示和棋
                if (activity.controlsManager != null && activity.chessInfo != null && activity.chessInfo.status == 1) {
                    activity.controlsManager.checkGameStatus(activity.chessInfo.IsRedGo);
                }
                
                // 操作成功，不需要回滚
                rollbackNeeded = false;
            } catch (Exception e) {
                LogUtils.e("PvMActivitySetup", "Error in placePiece", e);
                rollbackNeeded = true;
            } finally {
                // 如果需要回滚，恢复原始状态
                if (rollbackNeeded) {
                    try {
                        // 恢复来源位置的棋子
                        if (sourceX != -1 && sourceY != -1 && sourceX >= 0 && sourceX < 9 && 
                            sourceY >= 0 && sourceY < 10 && activity.chessInfo.piece[sourceY] != null) {
                            activity.chessInfo.piece[sourceY][sourceX] = originalSourcePiece;
                        }
                        // 恢复目标位置的棋子
                        if (activity.chessInfo.piece[y] != null) {
                            activity.chessInfo.piece[y][x] = originalTargetPiece;
                        }
                        // 恢复攻击棋子数量
                        activity.chessInfo.attackNum_B = originalAttackNum_B;
                        activity.chessInfo.attackNum_R = originalAttackNum_R;
                        LogUtils.d("PvMActivitySetup", "Rollback completed for placePiece operation");
                    } catch (Exception rollbackException) {
                        LogUtils.e("PvMActivitySetup", "Error during rollback", rollbackException);
                    }
                }
            }
        }
    }
    
    // 简化的放置棋子方法（不带来源位置）
    public void placePiece(int x, int y, int pieceID) {
        placePiece(x, y, pieceID, -1, -1);
    }
    
    // 检查棋子位置是否合理
    public boolean isValidPiecePosition(int pieceID, int x, int y) {
        // 检查坐标是否在棋盘范围内
        if (x < 0 || x >= 9 || y < 0 || y >= 10) {
            return false;
        }
        
        // 摆棋模式下的位置限制
        if (activity.chessInfo != null && activity.chessInfo.IsSetupMode) {
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
    
    // 检查棋子数量是否符合标准（支持来源位置和目标位置）
    public boolean checkPieceCountWithTarget(int pieceID, int sourceX, int sourceY, int targetX, int targetY) {
        if (pieceID == 0) return true; // 移除棋子总是允许的
        if (activity == null || activity.chessInfo == null || activity.chessInfo.piece == null) return false;
        
        Object lock = activity.chessInfo.getLock();
        synchronized (lock) {
            int count = 0;
            try {
                for (int i = 0; i < 10 && i < activity.chessInfo.piece.length; i++) {
                    if (activity.chessInfo.piece[i] != null) {
                        for (int j = 0; j < 9 && j < activity.chessInfo.piece[i].length; j++) {
                            // 如果是来源位置或目标位置，不计入数量
                            if ((i == sourceY && j == sourceX) || (i == targetY && j == targetX)) {
                                continue;
                            }
                            if (activity.chessInfo.piece[i][j] == pieceID) {
                                count++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LogUtils.e("PvMActivitySetup", "Error in checkPieceCountWithTarget", e);
                return false;
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
    }

    // 检查棋子数量是否符合标准（支持来源位置，来源位置的棋子不计入数量）
    public boolean checkPieceCount(int pieceID, int sourceX, int sourceY) {
        return checkPieceCountWithTarget(pieceID, sourceX, sourceY, -1, -1);
    }
    
    // 简化的棋子数量检查（不带来源位置）
    public boolean checkPieceCount(int pieceID) {
        return checkPieceCount(pieceID, -1, -1);
    }
    
    // 显示摆棋模式帮助信息
    public void showSetupHelp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("摆棋模式帮助");
        builder.setMessage("1. 在「选择棋子」面板点击一个棋子，再点击棋盘即可放置\n" +
                          "2. 点击棋盘上已放置的棋子可选中它，再点其他位置即可移动\n" +
                          "3. 点击「清空棋盘」可移除除将/帅外的所有棋子\n" +
                          "\n摆棋规则（摆棋模式下较为宽松）：\n" +
                          "- 将/帅：只能放在九宫格内\n" +
                          "- 士：只能放在九宫格内的斜线位置\n" +
                          "- 象/相：只能放在己方半场\n" +
                          "- 马、车、炮、卒/兵：可自由摆放在任意合法格\n" +
                          "\n双方都摆好将/帅后，点击「完成」即可选择开局方并开始。");
        builder.setPositiveButton("确定", null);
        builder.show();
    }
    
    // 检查摆棋是否完成
    public boolean checkSetupComplete() {
        if (activity == null || activity.chessInfo == null || activity.chessInfo.piece == null) return false;
        
        Object lock = activity.chessInfo.getLock();
        synchronized (lock) {
            // 只检查基本合法性：双方都有将/帅
            boolean hasRedKing = false;
            boolean hasBlackKing = false;
            
            try {
                for (int i = 0; i < 10 && i < activity.chessInfo.piece.length; i++) {
                    if (activity.chessInfo.piece[i] != null) {
                        for (int j = 0; j < 9 && j < activity.chessInfo.piece[i].length; j++) {
                            int piece = activity.chessInfo.piece[i][j];
                            if (piece == 1) { // 黑将
                                hasBlackKing = true;
                            } else if (piece == 8) { // 红帅
                                hasRedKing = true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LogUtils.e("PvMActivitySetup", "Error in checkSetupComplete", e);
                return false;
            }
            
            return hasRedKing && hasBlackKing;
        }
    }
    
    // 结束摆棋并选择开局方
    public void finishSetup() {
        if (activity != null && checkSetupComplete()) {
            // 显示选择开局方的对话框
            showChooseSideDialog(null, false);
        } else {
            // 摆棋未完成，给出明确提示而不是静默无反应
            boolean hasRed = false;
            boolean hasBlack = false;
            if (activity.chessInfo.piece != null) {
                for (int r = 0; r < 10 && r < activity.chessInfo.piece.length; r++) {
                    if (activity.chessInfo.piece[r] == null) continue;
                    for (int c = 0; c < 9 && c < activity.chessInfo.piece[r].length; c++) {
                        int p = activity.chessInfo.piece[r][c];
                        if (p == 8) hasRed = true;
                        else if (p == 1) hasBlack = true;
                    }
                }
            }
            StringBuilder missing = new StringBuilder();
            if (!hasRed) {
                missing.append("红帅");
            }
            if (!hasBlack) {
                if (missing.length() > 0) {
                    missing.append("、");
                }
                missing.append("黑将");
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("摆棋未完成");
            builder.setMessage("当前棋盘缺少" + (missing.length() > 0 ? missing.toString() : "必要棋子") +
                    "，请先摆放完整（双方都需有将/帅）后再完成。");
            builder.setPositiveButton("继续摆棋", null);
            builder.show();
        }
    }

    // 显示选择开局方的对话框，warning 为可选提示（例如选择了被将方不走时的警告）
    // alreadyWarned 表示本次弹框已是冲突后的二次提示，用户再次选择即按用户意思执行
    private void showChooseSideDialog(String warning, boolean alreadyWarned) {
        if (activity == null) {
            return;
        }
        String message = "请选择由哪一方开始下棋";
        if (warning != null && !warning.isEmpty()) {
            message = warning + "\n\n请选择由哪一方开始下棋";
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("选择开局方");
        builder.setMessage(message);
        builder.setPositiveButton("红方开始", (dialog, which) -> confirmFinishSetup(true, alreadyWarned));
        builder.setNegativeButton("黑方开始", (dialog, which) -> confirmFinishSetup(false, alreadyWarned));
        builder.setCancelable(false); // 必须选择一个选项
        AlertDialog dialog = builder.show();
        // 若存在警告提示，将消息文字改为醒目颜色以引起注意
        if (warning != null && !warning.isEmpty()) {
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) {
                messageView.setTextColor(0xFFFF8C00); // 橙色，提示需注意
            }
        }
    }

    // 校验并确认开局方：若所选方不动、而对方正被将军，则该开局方非法。
    // 象棋规则下被将的一方必须应对，故提示用户（仅一次）让其重新决定；
    // 用户再次选择后按用户意思执行，不再拦截。
    // 若双方帅/将同时被将则局面非法，提示并停留在摆棋模式要求用户修正。
    private void confirmFinishSetup(boolean redToMove, boolean alreadyWarned) {
        if (activity == null || activity.chessInfo == null || activity.chessInfo.piece == null) {
            return;
        }
        int[][] piece = activity.chessInfo.piece;
        boolean redChecked = Rule.isKingDanger(piece, true);
        boolean blackChecked = Rule.isKingDanger(piece, false);
        if (redChecked && blackChecked) {
            Toast.makeText(activity, "当前局面非法：双方帅/将同时被将，请调整棋子后再完成摆棋",
                    Toast.LENGTH_LONG).show();
            return; // 保持摆棋模式，等待用户修正
        }
        if (!alreadyWarned) {
            if (redChecked && !redToMove) {
                // 红方被将，但用户选择黑方先行：提示一次并让用户重新决定
                showChooseSideDialog("提示：红方正被将军，按规则应由红方先行应对。是否仍选择黑方开始？", true);
                return;
            }
            if (blackChecked && redToMove) {
                // 黑方被将，但用户选择红方先行：提示一次并让用户重新决定
                showChooseSideDialog("提示：黑方正被将军，按规则应由黑方先行应对。是否仍选择红方开始？", true);
                return;
            }
        }
        // 用户所选符合规则（双方均未被将），或已提示过一次后用户再次决定：按用户意思执行
        performFinishSetup(redToMove);
    }

    // 真正完成摆棋：设置开局方并退出摆棋模式（原两支分支的公共逻辑）
    private void performFinishSetup(boolean redToMove) {
        if (activity != null && activity.chessInfo != null) {
            Object lock = activity.chessInfo.getLock();
            synchronized (lock) {
                // 设置开局方
                activity.chessInfo.IsRedGo = redToMove;
                // 生成并保存摆棋结束时的FEN信息（在IsSetupMode被设置为false之前）
                FENHandler fenHandler = new FENHandler();
                String setupFEN = fenHandler.generateFEN(activity.chessInfo);
                if (activity.notationManager != null) {
                    activity.notationManager.setSetupFEN(setupFEN);
                    LogUtils.d("PvMActivitySetup", "摆棋结束，保存FEN: " + setupFEN);
                }
                // 退出摆棋模式
                activity.chessInfo.IsSetupMode = false;
                // 确保游戏状态为进行中
                activity.chessInfo.status = 1;
                // 重置infoSet，清空摆棋过程中的记录
                activity.infoSet = new InfoSet();
                // 摆棋结束：清空评分曲线历史（新局面的走势从头开始）
                activity.chessInfo.evalHistory.clear();
                activity.chessInfo.currentEvaluation = 0;
                // 将当前摆棋局面保存到infoSet中作为初始状态
                try {
                    if (activity.infoSet != null) {
                        activity.infoSet.pushInfo(activity.chessInfo);
                    }
                } catch (CloneNotSupportedException e) {
                    LogUtils.e("PvMActivitySetup", "操作失败", e);
                }
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
                // 刷新评分曲线（已清空历史，显示空曲线）
                activity.refreshScoreCurve();
                // 摆棋结束后评估局面分数
                activity.triggerPositionEvaluation();
                // 摆棋真正完成：还原按钮状态（摆棋→完成 还原、其它按钮恢复可用）
                restoreAfterSetup();
            }
        }
    }

    // 摆棋真正完成后还原界面：隐藏摆棋面板、恢复回合视图、完成按钮还原、其它按钮恢复可用
    private void restoreAfterSetup() {
        if (activity == null) return;
        // 摆棋按钮还原为"摆棋"：恢复原色原图标，并重新启用其它按钮
        activity.applySetupModeButtonUI(false);
        // 隐藏摆棋模式视图
        if (activity.setupModeView != null) {
            activity.setupModeView.setVisibility(View.GONE);
        }
        // 恢复回合信息视图
        if (activity.roundView != null) {
            activity.roundView.setVisibility(View.VISIBLE);
            // 确保RoundView的chessInfo引用是最新的
            activity.roundView.setChessInfo(activity.chessInfo);
        }
        // 确保ChessView的chessInfo引用是最新的
        if (activity.chessView != null) {
            activity.chessView.setChessInfo(activity.chessInfo);
            activity.chessView.requestDraw();
        }
        // 摆棋结束后默认设置为双人模式
        activity.gameMode = 0;
        // 更新RoundView的游戏模式显示
        if (activity.roundView != null) {
            activity.roundView.setGameMode(0);
        }
    }

    // 处理摆棋模式的触摸事件
    public boolean handleSetupModeTouch(float x, float y, android.view.MotionEvent event) {
        if (activity == null || activity.chessInfo == null || !activity.chessInfo.IsSetupMode) {
            return false;
        }
        
        try {
            // 检查是否点击在棋盘上
            if (activity.chessView != null && x >= 0 && x <= activity.chessView.Board_width && 
                y >= 0 && y <= activity.chessView.Board_height) {
                int[] pos = activity.getPos(event);
                if (pos != null && pos.length >= 2) {
                    activity.chessInfo.Select = pos;
                    int i = pos[0];
                    int j = pos[1];

                    if (i >= 0 && i <= 8 && j >= 0 && j <= 9) {
                        // 获取点击位置的棋子ID
                        int boardPieceID = 0;
                        if (activity.chessInfo.piece != null && activity.chessInfo.piece.length > j && 
                            activity.chessInfo.piece[j] != null && activity.chessInfo.piece[j].length > i) {
                            boardPieceID = activity.chessInfo.piece[j][i];
                        }

                        // 如果已经选中了棋盘上的棋子
                        if (selectedBoardPiecePos != null && 
                            selectedBoardPiecePos[0] != -1 && selectedBoardPiecePos[1] != -1) {
                            // 获取要操作的棋子ID
                            int pieceToOperate = 0;
                            if (activity.chessInfo.piece != null && 
                                activity.chessInfo.piece.length > selectedBoardPiecePos[1] && 
                                activity.chessInfo.piece[selectedBoardPiecePos[1]] != null && 
                                activity.chessInfo.piece[selectedBoardPiecePos[1]].length > selectedBoardPiecePos[0]) {
                                pieceToOperate = activity.chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]];
                            }
                            
                            // 检查是否是点击原位置（下架）
                            if (i == selectedBoardPiecePos[0] && j == selectedBoardPiecePos[1]) {
                                // 点击原位置，下架所有棋子（包括将/帅）
                                placePiece(selectedBoardPiecePos[0], selectedBoardPiecePos[1], 0);
                                // 重置选中状态
                                selectedBoardPiecePos[0] = -1;
                                selectedBoardPiecePos[1] = -1;
                            }
                            // 点击的是其他位置（移动或覆盖棋子）
                            else {
                                // 移动或覆盖棋子，先将原位置设为0，然后放置到新位置
                                if (isValidPiecePosition(pieceToOperate, i, j)) {
                                    // 使用新的 placePiece 方法，传递来源位置
                                    placePiece(i, j, pieceToOperate, selectedBoardPiecePos[0], selectedBoardPiecePos[1]);
                                    // 重置选中状态
                                    selectedBoardPiecePos[0] = -1;
                                    selectedBoardPiecePos[1] = -1;
                                }
                            }
                        }
                        // 如果已经选中了棋子选择区域的棋子，放置到棋盘上
                        else if (selectedPieceID > 0) {
                            // 直接使用新的放置逻辑，placePiece方法内部已经处理了目标位置的棋子
                            // 使用 checkPieceCountWithTarget 来检查数量时会排除目标位置的原棋子
                            placePiece(i, j, selectedPieceID, -1, -1);
                            // 重置选中状态
                            selectedPieceID = 0;
                        }
                        // 如果点击的是棋盘上的棋子，选中该棋子
                        else if (boardPieceID > 0) {
                            // 选中棋盘棋子时清除面板选中，保持状态互斥
                            selectedPieceID = 0;
                            if (activity.setupModeView != null) {
                                activity.setupModeView.clearSelection();
                            }
                            selectedBoardPiecePos[0] = i;
                            selectedBoardPiecePos[1] = j;
                            // 显示选中效果
                            activity.chessInfo.Select = new int[]{i, j};
                            // 同时刷新两个视图
                            if (activity.chessView != null) {
                                activity.chessView.requestDraw();
                            }
                            if (activity.setupModeView != null) {
                                activity.setupModeView.invalidate();
                            }
                        }
                        // 点击空白区域，重置选中状态
                        else {
                            selectedBoardPiecePos[0] = -1;
                            selectedBoardPiecePos[1] = -1;
                            activity.chessInfo.Select = new int[]{-1, -1};
                            // 同时刷新两个视图
                            if (activity.chessView != null) {
                                activity.chessView.requestDraw();
                            }
                            if (activity.setupModeView != null) {
                                activity.setupModeView.invalidate();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.e("PvMActivitySetup", "Error in handleSetupModeTouch", e);
        }
        return false;
    }
    
    // 处理摆棋模式的切换
    public void toggleSetupMode() {
        if (activity == null || activity.chessInfo == null) {
            return;
        }
        
        try {
            if (activity.chessInfo.IsSetupMode) {
                // 关闭摆棋模式，检查摆棋是否完成
                // 完成/退出的 UI 还原统一在 finishSetup 内（仅在真正完成时）执行，
                // 避免棋局不完整时仍把摆棋面板隐藏掉的旧问题
                finishSetup();

            } else {
                // 开启摆棋模式前，中断所有行棋
                // 停止所有 AI（分析、引擎搜索、局面评分）并复位支招按钮
                activity.stopAllAI();
                // 停止计时器
                activity.stopTurnTimer();
                // 开启摆棋模式
                activity.chessInfo.IsSetupMode = true;
                // 摆棋按钮变为"完成"：变色 + 变图标，并禁用其它按钮
                activity.applySetupModeButtonUI(true);
                // 清除旧的支招提示线和走棋轨迹
                activity.chessInfo.suggestMoves = new java.util.ArrayList<>();
                activity.chessInfo.suggestMoveLabels = new java.util.ArrayList<>();
                activity.chessInfo.suggestMovesIsRed = new java.util.ArrayList<>();
                activity.chessInfo.prePos = null;
                activity.chessInfo.curPos = null;
                activity.chessInfo.ret = new java.util.concurrent.CopyOnWriteArrayList<>();
                // 显示摆棋模式视图
                if (activity.setupModeView != null) {
                    // 确保布局参数正确
                    android.widget.RelativeLayout.LayoutParams paramsSetup = (android.widget.RelativeLayout.LayoutParams) activity.setupModeView.getLayoutParams();
                    if (paramsSetup != null) {
                        paramsSetup.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
                        paramsSetup.addRule(android.widget.RelativeLayout.BELOW, R.id.roundView);
                        paramsSetup.width = android.widget.RelativeLayout.LayoutParams.MATCH_PARENT;
                        paramsSetup.height = android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT;
                        paramsSetup.setMargins(30, 10, 30, 10);
                        activity.setupModeView.setLayoutParams(paramsSetup);
                    }
                    // 先设置布局参数，再显示视图
                    activity.setupModeView.setVisibility(View.VISIBLE);
                    // 确保视图在最上层
                    activity.setupModeView.bringToFront();
                }
                // 隐藏回合信息视图
                if (activity.roundView != null) {
                    activity.roundView.setVisibility(View.GONE);
                }

                // 不需要清空缓存，保持当前局面
                // 更新视图中的chessInfo引用
                if (activity.chessView != null) {
                    activity.chessView.setChessInfo(activity.chessInfo);
                }
                if (activity.setupModeView != null) {
                    activity.setupModeView.setChessInfo(activity.chessInfo);
                }
                if (activity.roundView != null) {
                    activity.roundView.setChessInfo(activity.chessInfo);
                }
                // 重新绘制界面
                if (activity.chessView != null) {
                    activity.chessView.requestDraw();
                    // 立即刷新
                    activity.chessView.invalidate();
                }
                if (activity.setupModeView != null) {
                    activity.setupModeView.invalidate();
                    // 立即刷新
                    activity.setupModeView.postInvalidate();
                }
            }
        } catch (Exception e) {
            LogUtils.e("PvMActivitySetup", "Error in toggleSetupMode", e);
        }
    }
}