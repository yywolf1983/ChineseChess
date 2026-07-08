package top.nones.chessgame;

import ChessMove.Rule;
import Info.ChessInfo;

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
        // 清除ChessInfo中的支招数据
        if (activity.chessInfo != null) {
            activity.chessInfo.suggestMoves.clear();
            activity.chessInfo.suggestMoveLabels.clear();
            activity.chessInfo.suggestFromPos = null;
            activity.chessInfo.suggestToPos = null;
        }
    }
    
    // 检查双方老将是否见面
    private boolean isKingFaceToFace(int[][] piece) {
        return Rule.isKingFaceToFace(piece);
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