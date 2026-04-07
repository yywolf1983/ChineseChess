package top.nones.chessgame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import Info.ChessInfo;

public class PvPActivityRound extends View {
    private ChessInfo chessInfo;
    private Paint paint;
    private int type; // 0 表示双人对战，1 表示人机对战
    private String suggestMoveText = ""; // 支招走法文本

    public PvPActivityRound(Context context, ChessInfo chessInfo, int type) {
        super(context);
        this.chessInfo = chessInfo;
        this.type = type;
        initPaint();
    }

    private void initPaint() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(30);
        paint.setColor(Color.BLACK);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (chessInfo != null) {
            drawRoundInfo(canvas);
        }
    }

    private void drawRoundInfo(Canvas canvas) {
        String roundInfo = "回合: " + chessInfo.totalMoves + " 步";
        String turnInfo = chessInfo.IsRedGo ? "红方走棋" : "黑方走棋";
        String statusInfo = getStatusInfo();

        canvas.drawText(roundInfo, 30, 40, paint);
        canvas.drawText(turnInfo, 30, 80, paint);
        canvas.drawText(statusInfo, 30, 120, paint);
        
        // 显示支招信息
        if (suggestMoveText != null && !suggestMoveText.isEmpty()) {
            canvas.drawText("支招: " + suggestMoveText, 30, 160, paint);
        }
    }

    private String getStatusInfo() {
        // 检查是否有一方被将死或王被吃掉
        boolean redKingExists = false;
        boolean blackKingExists = false;
        // 检查整个棋盘，寻找红帅和黑将
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (chessInfo.piece[i][j] == 8) { // 红帅
                    redKingExists = true;
                }
                if (chessInfo.piece[i][j] == 1) { // 黑将
                    blackKingExists = true;
                }
            }
        }
        
        // 优先显示王被吃掉的情况
        if (!redKingExists) {
            return "黑方胜利";
        } else if (!blackKingExists) {
            return "红方胜利";
        } else {
            switch (chessInfo.status) {
                case 0:
                    return "游戏未开始";
                case 1:
                    return "游戏进行中";
                case 2:
                    // 游戏结束，根据行棋方判断胜利者
                    if (chessInfo.IsRedGo) {
                        return "黑方胜利";
                    } else {
                        return "红方胜利";
                    }
                default:
                    return "游戏状态未知";
            }
        }
    }

    public void requestDraw() {
        invalidate();
    }

    // Getters and Setters
    public ChessInfo getChessInfo() {
        return chessInfo;
    }

    public void setChessInfo(ChessInfo chessInfo) {
        this.chessInfo = chessInfo;
        invalidate();
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
        invalidate();
    }
    
    // 设置支招走法文本
    public void setSuggestMoveText(String moveText) {
        this.suggestMoveText = moveText;
        invalidate();
    }
}
