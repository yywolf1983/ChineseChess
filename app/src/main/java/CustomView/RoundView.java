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
    private Paint blackTextPaint; // 黑方回合画笔
    private Paint infoTextPaint; // 模式和评分画笔
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
        redTextPaint.setTextSize(42); // 红方回合字体大小
        redTextPaint.setStrokeWidth(2);
        redTextPaint.setAntiAlias(true);
        redTextPaint.setColor(Color.RED);
        redTextPaint.setFakeBoldText(true); // 加粗字体，美化效果

        // 黑色文本画笔（黑方回合）
        blackTextPaint = new Paint();
        blackTextPaint.setTextSize(42); // 黑方回合字体大小，与红方一致
        blackTextPaint.setStrokeWidth(2);
        blackTextPaint.setAntiAlias(true);
        blackTextPaint.setColor(Color.BLACK); // 黑方使用黑色
        blackTextPaint.setFakeBoldText(true); // 加粗字体，美化效果

        // 信息文本画笔（模式和评分）
        infoTextPaint = new Paint();
        infoTextPaint.setTextSize(32); // 减小模式和评分的字体大小
        infoTextPaint.setStrokeWidth(2);
        infoTextPaint.setAntiAlias(true);
        infoTextPaint.setColor(Color.BLACK); // 信息文本使用黑色
        infoTextPaint.setFakeBoldText(true); // 加粗字体，美化效果
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
        infoTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(modeText, modeX, modeY, infoTextPaint);
        canvas.drawText(scoreText, modeX, scoreY, infoTextPaint);
        infoTextPaint.setTextAlign(Paint.Align.LEFT); // 重置文本对齐
        
        // 绘制回合信息
        String turnText = chessInfo.IsRedGo ? "红方回合" : "黑方回合";
        Paint turnPaint = chessInfo.IsRedGo ? redTextPaint : blackTextPaint;
        
        // 计算文本位置 - 向两边移动，增加间距
        float turnX = width / 6; // 进一步向左移动
        float textY = height * 2 / 3; // 下半部分
        
        // 绘制简单的图标提示
        float iconSize = 30;
        float iconX = turnX - iconSize - 10;
        float iconY = textY - 15;
        if (chessInfo.IsRedGo) {
            // 绘制红色圆形作为红方图标
            Paint redIconPaint = new Paint();
            redIconPaint.setColor(Color.RED);
            redIconPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(iconX, iconY, iconSize/2, redIconPaint);
        } else {
            // 绘制黑色圆形作为黑方图标
            Paint blackIconPaint = new Paint();
            blackIconPaint.setColor(Color.BLACK);
            blackIconPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(iconX, iconY, iconSize/2, blackIconPaint);
        }
        
        canvas.drawText(turnText, turnX, textY, turnPaint);
        
        // 绘制回合数 - 使用totalMoves作为总走步数
        // 每走一步棋，totalMoves就会增加1，所以总走步数就是totalMoves
        int totalMoves = chessInfo.totalMoves;
        // 回合数 = (总走步数 + 1) / 2，因为每回合有2步
        int roundCount = (totalMoves + 1) / 2;
        String stepText = "回合: " + roundCount;
        float stepX = width * 5 / 6; // 进一步向右移动
        // 确保步数文本右对齐
        infoTextPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(stepText, stepX, textY, infoTextPaint);
        infoTextPaint.setTextAlign(Paint.Align.LEFT); // 重置文本对齐
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
            case 3:
                return "双机对战";
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
