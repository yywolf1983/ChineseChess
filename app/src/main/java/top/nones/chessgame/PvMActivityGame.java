package top.nones.chessgame;

import android.util.Log;
import android.view.MotionEvent;

import java.util.List;

import ChessMove.Rule;
import ChessMove.Move;
import Info.ChessInfo;
import Info.Pos;

public class PvMActivityGame {
    private PvMActivity activity;
    private Boolean suggestForRed = null; // 记录支招是给红方还是黑方，null表示没有支招
    
    public PvMActivityGame(PvMActivity activity) {
        this.activity = activity;
    }
    
    // 设置支招信息，并记录是给哪一方的
    public void setSuggestMove(String moveText, boolean forRed) {
        if (activity.roundView != null) {
            // 清空步数信息，只显示支招内容
            activity.roundView.setMoveInfoText("");
            activity.roundView.setSuggestMoveText(moveText);
            suggestForRed = forRed;
        }
    }
    
    // 检查是否应该清除支招信息
    public boolean shouldClearSuggest(boolean isRed) {
        if (suggestForRed == null) {
            return false;
        }
        return isRed == suggestForRed;
    }
    
    // 清除支招信息
    public void clearSuggest() {
        if (activity.roundView != null) {
            activity.roundView.setSuggestMoveText("");
        }
        suggestForRed = null;
    }
    
    // 检查双方老将是否见面
    private boolean isKingFaceToFace(int[][] piece) {
        if (piece == null) {
            return false;
        }
        
        int redKingRow = -1, redKingCol = -1;
        int blackKingRow = -1, blackKingCol = -1;
        
        // 找到红帅和黑将的位置
        for (int i = 0; i < 10; i++) {
            if (piece[i] == null) {
                continue;
            }
            for (int j = 0; j < 9; j++) {
                if (piece[i][j] == 8) { // 红帅
                    redKingRow = i;
                    redKingCol = j;
                } else if (piece[i][j] == 1) { // 黑将
                    blackKingRow = i;
                    blackKingCol = j;
                }
            }
        }
        
        // 检查是否都找到了
        if (redKingRow == -1 || blackKingRow == -1) {
            return false;
        }
        
        // 检查是否在同一列
        if (redKingCol != blackKingCol) {
            return false;
        }
        
        // 检查中间是否有棋子
        int start = Math.min(redKingRow, blackKingRow) + 1;
        int end = Math.max(redKingRow, blackKingRow);
        for (int i = start; i < end; i++) {
            if (piece[i] == null || piece[i][redKingCol] != 0) {
                return false;
            }
        }
        
        return true;
    }
    
    // 处理触摸事件
    public boolean handleTouch(android.view.View view, android.view.MotionEvent event) {
        activity.lastClickTime = System.currentTimeMillis();
        if (activity.lastClickTime - activity.curClickTime < activity.MIN_CLICK_DELAY_TIME) {
            return false;
        }
        activity.curClickTime = activity.lastClickTime;

        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            if (activity.chessInfo != null && activity.chessInfo.status == 1) {
                // 摆棋模式处理
                if (activity.chessInfo.IsSetupMode) {
                    // 检查是否点击在棋盘上
                    if (activity.chessView != null && x >= 0 && x <= activity.chessView.Board_width && y >= 0 && y <= activity.chessView.Board_height) {
                            activity.chessInfo.Select = activity.getPos(event);
                            int i = activity.chessInfo.Select[0];
                            int j = activity.chessInfo.Select[1];

                            if (i >= 0 && i <= 8 && j >= 0 && j <= 9) {
                                // 获取点击位置的棋子ID
                                int boardPieceID = activity.chessInfo.piece[j][i];
                                
                                // 如果已经选中了棋盘上的棋子
                                int[] selectedBoardPiecePos = activity.setupManager.getSelectedBoardPiecePos();
                                if (selectedBoardPiecePos[0] != -1 && selectedBoardPiecePos[1] != -1) {
                                    // 获取要操作的棋子ID
                                    int pieceToOperate = activity.chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]];
                                    
                                    // 检查是否是点击原位置（下架）
                                    if (i == selectedBoardPiecePos[0] && j == selectedBoardPiecePos[1]) {
                                        // 点击原位置，下架棋子
                                            if (pieceToOperate != 1 && pieceToOperate != 8) { // 老将不能下架
                                                activity.setupManager.placePiece(selectedBoardPiecePos[0], selectedBoardPiecePos[1], 0);
                                                // 重置选中状态
                                                activity.setupManager.setSelectedBoardPiecePos(new int[]{-1, -1});
                                            }
                                    }
                                    // 点击的是空白区域（移动棋子）
                                    else if (boardPieceID == 0) {
                                        // 检查是否是老将
                                        if (pieceToOperate == 1 || pieceToOperate == 8) {
                                            // 老将不能下架，但可以移动到合法位置
                                            // 检查新位置是否合理
                                            if (activity.setupManager.isValidPiecePosition(pieceToOperate, i, j)) {
                                                // 先将原位置设为0
                                                activity.chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]] = 0;
                                                // 再将新位置设为棋子ID
                                                activity.setupManager.placePiece(i, j, pieceToOperate);
                                                // 重置选中状态
                                                activity.setupManager.setSelectedBoardPiecePos(new int[]{-1, -1});
                                            }
                                        } else {
                                            // 不是老将，可以移动
                                            // 检查新位置是否合理
                                            if (activity.setupManager.isValidPiecePosition(pieceToOperate, i, j)) {
                                                // 先将原位置设为0
                                                activity.chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]] = 0;
                                                // 再将新位置设为棋子ID
                                                activity.setupManager.placePiece(i, j, pieceToOperate);
                                                // 重置选中状态
                                                activity.setupManager.setSelectedBoardPiecePos(new int[]{-1, -1});
                                            }
                                        }
                                    }
                                }
                                // 如果已经选中了棋子选择区域的棋子，放置到棋盘上
                                else if (activity.setupManager.getSelectedPieceID() > 0) {
                                    activity.setupManager.placePiece(i, j, activity.setupManager.getSelectedPieceID());
                                    // 重置选中状态
                                    activity.setupManager.setSelectedPieceID(0);
                                    activity.setupModeView.clearSelection();
                                }
                                // 如果点击的是棋盘上的棋子，选中该棋子
                                else if (boardPieceID > 0) {
                                    activity.setupManager.setSelectedBoardPiecePos(new int[]{i, j});
                                    // 显示选中效果
                                    activity.chessInfo.Select = new int[]{i, j};
                                    activity.chessView.requestDraw();
                                }
                                // 点击空白区域，重置选中状态
                                else {
                                    activity.setupManager.setSelectedBoardPiecePos(new int[]{-1, -1});
                                    activity.chessInfo.Select = new int[]{-1, -1};
                                    activity.chessView.requestDraw();
                                }
                            }
                        }
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
                                        
                                        // 检查当前行棋方的王是否被将军
                                        boolean isChecked = Rule.isKingDanger(activity.chessInfo.piece, activity.chessInfo.IsRedGo);
                                        // 只有被将军时才检查棋子是否能解将
                                        boolean canDefendCheck = true;
                                        if (isChecked) {
                                            canDefendCheck = Rule.CanDefendCheck(activity.chessInfo.piece, i, j, pieceID);
                                        }
                                        
                                        if (canSelect && canDefendCheck) {
                                            activity.chessInfo.prePos = new Pos(i, j);
                                            activity.chessInfo.IsChecked = true;
                                            List<Pos> possibleMoves = Rule.PossibleMoves(activity.chessInfo.piece, i, j, pieceID);
                                            
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

                        // 检查移动后是否出现双方老将见面的情况
                        if (isKingFaceToFace(activity.chessInfo.piece)) {
                            activity.chessInfo.piece[activity.chessInfo.prePos.y][activity.chessInfo.prePos.x] = piece;
                            activity.chessInfo.piece[targetY][targetX] = tmp;
                            // 移除Toast提示，通过界面显示提示信息
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

                            // 检查是否将军
                            boolean isCheck = Rule.isKingDanger(activity.chessInfo.piece, !isRed);
                            activity.chessInfo.updateAllInfo(activity.chessInfo.prePos, activity.chessInfo.curPos, piece, tmp, isCheck);

                            // 保存移动后的状态到栈中
                            try {
                                activity.infoSet.pushInfo(activity.chessInfo);
                            } catch (CloneNotSupportedException e) {
                                e.printStackTrace();
                            }

                            // 检查游戏状态，包括将死
                            if (activity.controlsManager != null) {
                                activity.controlsManager.checkGameStatus(isRed);
                            }

                            // 增加继续对局后的回合计数器
                            activity.continueGameRoundCount++;

                            // 获取当前局面的评分（在后台线程中执行）
                            if (activity.pikafishAI != null && activity.pikafishAI.isInitialized()) {
                                new Thread(() -> {
                                    AICore.PikafishAI.MoveWithScore moveWithScore = activity.pikafishAI.getBestMoveWithScore(activity.chessInfo);
                                    final int score = moveWithScore.score;
                                    
                                    // 更新评分显示
                                    activity.runOnUiThread(() -> {
                                        if (activity.roundView != null) {
                                            activity.roundView.setMoveScore(score);
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
                                activity.aiManager.checkAIMove();
                        }
                                    } else if (pieceID != 0) {
                                        // 只有当点击的位置有棋子时，才检查是否可以选择新棋子
                                        // 检查是否是当前回合的颜色的棋子
                                        boolean canSelect = (isRedPiece && activity.chessInfo.IsRedGo) || (!isRedPiece && !activity.chessInfo.IsRedGo);
                                        
                                        // 检查当前行棋方的王是否被将军
                                        boolean isChecked = Rule.isKingDanger(activity.chessInfo.piece, activity.chessInfo.IsRedGo);
                                        // 只有被将军时才检查棋子是否能解将
                                        boolean canDefendCheck = true;
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
    
    // 检查是否需要AI移动
    public void checkAIMove() {
        // 委托给aiManager处理
        activity.aiManager.checkAIMove();
    }
    
    // 检查游戏状态
    private void checkGameStatus(boolean isRed) {
        // 检查是否被将军
        if (Rule.isKingDanger(activity.chessInfo.piece, !isRed)) {
            // 移除Toast提示，通过界面显示提示信息
        }
    }
}