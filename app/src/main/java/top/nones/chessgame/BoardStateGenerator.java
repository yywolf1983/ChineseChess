package top.nones.chessgame;

import Info.ChessInfo;
import Info.ChessNotation;

public class BoardStateGenerator {
    private PvMActivity activity;
    
    public BoardStateGenerator(PvMActivity activity) {
        this.activity = activity;
    }
    
    // 生成棋盘状态
    public void generateBoardStateFromNotation(ChessNotation notation, int moveIndex) {
        if (activity == null) {
            System.out.println("PvMActivity: 生成棋盘状态失败，activity 为 null");
            return;
        }
        
        System.out.println("PvMActivity: 开始生成棋盘状态，当前步数: " + moveIndex);
        if (notation != null) {
            // 初始化棋盘状态
            ChessInfo initialInfo = new ChessInfo();
            
            // 检查是否有FEN信息
            String fen = notation.getFen();
            if (fen != null && !fen.isEmpty()) {
                System.out.println("PvMActivity: 使用FEN初始化棋盘: " + fen);
                // 从FEN初始化棋盘状态
                FENHandler fenHandler = new FENHandler();
                initialInfo = fenHandler.fenToChessInfo(fen);
                // 更新setupFEN为当前棋谱的FEN，清除之前的残留信息
                if (activity.notationManager != null) {
                    activity.notationManager.setSetupFEN(fen);
                }
            } else {
                // 如果没有FEN信息，清除setupFEN
                if (activity.notationManager != null) {
                    activity.notationManager.setSetupFEN(null);
                }
            }
            
            // 根据当前步数生成棋盘状态
            java.util.List<ChessNotation.MoveRecord> moveRecords = notation.getMoveRecords();
            if (moveRecords != null && !moveRecords.isEmpty()) {
                System.out.println("PvMActivity: 走法记录数量: " + moveRecords.size());
                ChessInfo currentInfo = initialInfo;
                int moveCount = 0;
                
                // 遍历走法记录，生成到当前步数的棋盘状态
                for (int i = 0; i < moveRecords.size(); i++) {
                    ChessNotation.MoveRecord record = moveRecords.get(i);
                    if (record == null) {
                        continue;
                    }
                    System.out.println("PvMActivity: 处理第 " + (i + 1) + " 回合: 红方=" + record.redMove + ", 黑方=" + record.blackMove);
                    
                    if (moveCount >= moveIndex) {
                        System.out.println("PvMActivity: 已达到目标步数，停止处理");
                        break;
                    }
                    
                    // 处理红方走法
                    if (!record.redMove.isEmpty() && moveCount < moveIndex) {
                        System.out.println("PvMActivity: 执行红方走法: " + record.redMove);
                        MoveSimulator moveSimulator = new MoveSimulator(activity);
                        ChessInfo tempInfo = moveSimulator.simulateMove(currentInfo, record.redMove, true);
                        if (tempInfo != null) {
                            currentInfo = tempInfo;
                            moveCount++;
                            System.out.println("PvMActivity: 红方走法执行完成，当前步数: " + moveCount);
                        }
                    }
                    
                    // 处理黑方走法
                    if (!record.blackMove.isEmpty() && moveCount < moveIndex) {
                        System.out.println("PvMActivity: 执行黑方走法: " + record.blackMove);
                        MoveSimulator moveSimulator = new MoveSimulator(activity);
                        ChessInfo tempInfo = moveSimulator.simulateMove(currentInfo, record.blackMove, false);
                        if (tempInfo != null) {
                            currentInfo = tempInfo;
                            moveCount++;
                            System.out.println("PvMActivity: 黑方走法执行完成，当前步数: " + moveCount);
                        }
                    }
                }
                
                // 更新棋盘状态
                if (currentInfo != null && activity.chessInfo != null && activity.infoSet != null && activity.infoSet.curInfo != null) {
                    System.out.println("PvMActivity: 更新棋盘状态，总步数: " + moveCount);
                    try {
                        // 清空现有棋盘并设置新状态
                        activity.chessInfo.setInfo(currentInfo);
                        // 更新 totalMoves，使其与当前的 moveCount 一致
                        activity.chessInfo.totalMoves = moveCount;
                        activity.infoSet.curInfo.setInfo(currentInfo);
                        // 更新 infoSet.curInfo 的 totalMoves
                        activity.infoSet.curInfo.totalMoves = moveCount;
                        // 更新 ChessView 中的 chessInfo 对象
                        if (activity.chessView != null) {
                            activity.chessView.setChessInfo(activity.chessInfo);
                        }
                        // 重新初始化 infoSet.preInfo 栈
                        activity.infoSet.newInfo();
                        activity.infoSet.pushInfo(activity.chessInfo);
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                    }
                    
                    // 重置时间相关变量，确保时间显示正确
                    activity.currentTurnStartTime = 0;
                    activity.redTime = 0;
                    activity.blackTime = 0;
                    
                    // 更新时间显示
                    if (activity.roundView != null) {
                        activity.roundView.setTime(0, 0);
                    }
                    
                    // 重新绘制界面
                    System.out.println("PvMActivity: 开始重新绘制界面，chessView=" + activity.chessView + ", roundView=" + activity.roundView);
                    if (activity.chessView != null) {
                        System.out.println("PvMActivity: 调用 chessView.requestDraw()");
                        activity.chessView.requestDraw();
                        // 强制刷新界面
                        activity.chessView.invalidate();
                        activity.chessView.postInvalidate();
                    }
                    if (activity.roundView != null) {
                        System.out.println("PvMActivity: 调用 roundView.requestDraw()");
                        activity.roundView.requestDraw();
                        // 强制刷新界面
                        activity.roundView.invalidate();
                        activity.roundView.postInvalidate();
                    }
                    System.out.println("PvMActivity: 界面重新绘制完成");
                }
            } else {
                System.out.println("PvMActivity: 没有走法记录，使用初始棋盘状态");
                // 如果没有走法记录，使用初始棋盘状态
                if (activity.chessInfo != null && activity.infoSet != null && activity.infoSet.curInfo != null) {
                    try {
                        // 清空现有棋盘并设置新状态
                        activity.chessInfo.setInfo(initialInfo);
                        // 重置 totalMoves 为 0
                        activity.chessInfo.totalMoves = 0;
                        activity.infoSet.curInfo.setInfo(initialInfo);
                        // 重置 infoSet.curInfo 的 totalMoves 为 0
                        activity.infoSet.curInfo.totalMoves = 0;
                        // 更新 ChessView 中的 chessInfo 对象
                        if (activity.chessView != null) {
                            activity.chessView.setChessInfo(activity.chessInfo);
                        }
                        // 重新初始化 infoSet.preInfo 栈
                        activity.infoSet.newInfo();
                        activity.infoSet.pushInfo(activity.chessInfo);
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                    }
                    
                    // 重新绘制界面
                    if (activity.chessView != null) {
                        activity.chessView.requestDraw();
                        // 强制刷新界面
                        activity.chessView.invalidate();
                        activity.chessView.postInvalidate();
                    }
                    if (activity.roundView != null) {
                        activity.roundView.requestDraw();
                        // 强制刷新界面
                        activity.roundView.invalidate();
                        activity.roundView.postInvalidate();
                    }
                }
            }
        } else {
            System.out.println("PvMActivity: 没有加载棋谱");
        }
    }
}