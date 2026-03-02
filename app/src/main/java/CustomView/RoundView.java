package CustomView;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import Info.ChessInfo;

/**
 * Created by 77304 on 2021/4/8.
 */

public class RoundView extends View {
    public ChessInfo chessInfo;
    private int gameMode = 0; // 对战模式
    private int moveScore = 0; // 走法评分

    private Paint backgroundPaint;
    private Paint redTextPaint;
    private Paint whiteTextPaint;
    private int viewWidth = 0;
    private int viewHeight = 0;

    public RoundView(Context context, ChessInfo chessInfo) {
        super(context);
        this.chessInfo = chessInfo;
        initPaints();
    }
    
    public RoundView(Context context, ChessInfo chessInfo, int gameMode) {
        super(context);
        this.chessInfo = chessInfo;
        this.gameMode = gameMode;
        initPaints();
    }
    
    // 设置对战模式
    public void setGameMode(int mode) {
        this.gameMode = mode;
        invalidate();
    }
    
    // 设置走法评分
    public void setMoveScore(int score) {
        this.moveScore = score;
        invalidate();
    }
    
    // 设置ChessInfo对象
    public void setChessInfo(ChessInfo chessInfo) {
        this.chessInfo = chessInfo;
        invalidate();
    }

    private void initPaints() {
        // 背景画笔
        backgroundPaint = new Paint();
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(Color.rgb(205, 133, 63)); // 更深的棕色背景，提高对比度

        // 红色文本画笔（红方回合）
        redTextPaint = new Paint();
        redTextPaint.setTextSize(48); // 增大字体大小
        redTextPaint.setStrokeWidth(2);
        redTextPaint.setAntiAlias(true);
        redTextPaint.setColor(Color.RED);

        // 白色文本画笔（黑方回合和步数）
        whiteTextPaint = new Paint();
        whiteTextPaint.setTextSize(38); // 增大字体大小
        whiteTextPaint.setStrokeWidth(2);
        whiteTextPaint.setAntiAlias(true);
        whiteTextPaint.setColor(Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (canvas == null || chessInfo == null) {
            return;
        }
        
        // 获取视图尺寸
        int width = getWidth();
        int height = getHeight();
        
        // 绘制背景
        canvas.drawRect(0, 0, width, height, backgroundPaint);
        
        // 绘制对战模式和评分
        String modeText = getGameModeName(gameMode);
        String scoreText = "评分: " + moveScore;
        float modeX = width / 2;
        float modeY = height / 4; // 上半部分
        float scoreY = height / 2; // 中间部分
        whiteTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(modeText, modeX, modeY, whiteTextPaint);
        canvas.drawText(scoreText, modeX, scoreY, whiteTextPaint);
        whiteTextPaint.setTextAlign(Paint.Align.LEFT); // 重置文本对齐
        
        // 绘制回合信息
        String turnText = chessInfo.IsRedGo ? "红方回合" : "黑方回合";
        Paint turnPaint = chessInfo.IsRedGo ? redTextPaint : whiteTextPaint;
        
        // 计算文本位置
        float turnX = width / 4;
        float textY = height * 2 / 3; // 下半部分
        
        canvas.drawText(turnText, turnX, textY, turnPaint);
        
        // 绘制回合数 - 使用totalMoves作为总走步数
        // 每走一步棋，totalMoves就会增加1，所以总走步数就是totalMoves
        int totalMoves = chessInfo.totalMoves;
        // 回合数 = (总走步数 + 1) / 2，因为每回合有2步
        int roundCount = (totalMoves + 1) / 2;
        String stepText = "回合: " + roundCount;
        float stepX = width * 3 / 4;
        // 确保步数文本右对齐
        whiteTextPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(stepText, stepX, textY, whiteTextPaint);
        whiteTextPaint.setTextAlign(Paint.Align.LEFT); // 重置文本对齐
    }
    
    // 获取对战模式名称
    private String getGameModeName(int mode) {
        switch (mode) {
            case 0:
                return "双人对战";
            case 1:
                return "人机对战（玩家红）";
            case 2:
                return "人机对战（玩家黑）";
            default:
                return "未知模式";
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        
        // 获取宽度
        int width = MeasureSpec.getSize(widthMeasureSpec);
        
        // 计算高度
        int height;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            height = MeasureSpec.getSize(heightMeasureSpec);
        } else {
            // 固定高度，确保足够显示对战模式、回合和步数信息
            height = 180; // 固定高度180px
        }
        
        viewWidth = width;
        viewHeight = height;
        
        setMeasuredDimension(viewWidth, viewHeight);
    }

    // 外部调用的绘制方法
    public void requestDraw() {
        invalidate();
    }
}
