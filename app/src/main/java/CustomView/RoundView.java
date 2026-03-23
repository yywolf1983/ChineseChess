package CustomView;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import Info.ChessInfo;
import top.nones.chessgame.PvMActivity;

/**
 * Created by 77304 on 2021/4/8.
 */

public class RoundView extends View {
    public ChessInfo chessInfo;
    private int gameMode = 0; // 对战模式
    private int moveScore = 0; // 走法评分
    private int targetMoveScore = 0; // 目标评分（用于平滑过渡）
    private long redTime = 0; // 红方行棋时间（毫秒）
    private long blackTime = 0; // 黑方行棋时间（毫秒）
    private int redSearchDepth = 0; // 红方AI搜索深度
    private int blackSearchDepth = 0; // 黑方AI搜索深度

    private Paint backgroundPaint;
    private Paint redTextPaint;
    private Paint blackTextPaint; // 黑方回合画笔
    private Paint infoTextPaint; // 模式和评分画笔
    private Paint borderPaint; // 边框画笔
    private Paint modeTextPaint; // 模式文本画笔
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
    
    // 设置走法评分（平滑过渡）
    public void setMoveScore(int score) {
        this.targetMoveScore = score;
        invalidate();
    }
    
    // 立即更新走法评分（跳过平滑过渡）
    public void setMoveScoreImmediately(int score) {
        this.moveScore = score;
        this.targetMoveScore = score;
        invalidate();
    }
    
    // 设置时间
    public void setTime(long redTime, long blackTime) {
        this.redTime = redTime;
        this.blackTime = blackTime;
        invalidate();
    }
    
    // 设置搜索深度
    public void setSearchDepth(int depth, boolean isRed) {
        if (isRed) {
            this.redSearchDepth = depth;
        } else {
            this.blackSearchDepth = depth;
        }
        invalidate();
    }
    
    // 重载方法，保持向后兼容
    public void setSearchDepth(int depth) {
        // 默认为黑方深度
        this.blackSearchDepth = depth;
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

        // 边框画笔
        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.rgb(160, 82, 45)); // 深棕色边框
        borderPaint.setStrokeWidth(3);
        borderPaint.setAntiAlias(true);

        // 将dp转换为像素，统一文字大小
        float textSize = convertDpToPixel(16, getContext());

        // 红色文本画笔（红方回合）
        redTextPaint = new Paint();
        redTextPaint.setTextSize(textSize); // 使用dp单位
        redTextPaint.setStrokeWidth(2);
        redTextPaint.setAntiAlias(true);
        redTextPaint.setColor(Color.RED);
        redTextPaint.setFakeBoldText(true); // 加粗字体，美化效果

        // 黑色文本画笔（黑方回合）
        blackTextPaint = new Paint();
        blackTextPaint.setTextSize(textSize); // 使用dp单位
        blackTextPaint.setStrokeWidth(2);
        blackTextPaint.setAntiAlias(true);
        blackTextPaint.setColor(Color.BLACK); // 黑方使用黑色
        blackTextPaint.setFakeBoldText(true); // 加粗字体，美化效果

        // 模式文本画笔（突出显示模式）
        modeTextPaint = new Paint();
        modeTextPaint.setTextSize(textSize + 2); // 模式文本稍大一点，突出显示
        modeTextPaint.setStrokeWidth(2);
        modeTextPaint.setAntiAlias(true);
        modeTextPaint.setColor(Color.WHITE); // 模式文本使用白色，与背景对比更明显
        modeTextPaint.setFakeBoldText(true); // 加粗字体，美化效果

        // 信息文本画笔（评分和搜索深度）
        infoTextPaint = new Paint();
        infoTextPaint.setTextSize(textSize); // 使用dp单位
        infoTextPaint.setStrokeWidth(2);
        infoTextPaint.setAntiAlias(true);
        infoTextPaint.setColor(Color.WHITE); // 信息文本使用白色，与背景对比更明显
        infoTextPaint.setFakeBoldText(true); // 加粗字体，美化效果
    }
    
    // 将dp转换为像素
    private float convertDpToPixel(float dp, Context context) {
        return dp * context.getResources().getDisplayMetrics().density;
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
        
        // 绘制边框
        android.graphics.RectF rectF = new android.graphics.RectF(5, 5, width - 5, height - 5);
        canvas.drawRoundRect(rectF, 10, 10, borderPaint);
        
        // 计算垂直间距
        float paddingTop = convertDpToPixel(10, getContext());
        float lineHeight = convertDpToPixel(22, getContext());
        
        // 绘制对战模式（突出显示）
        String modeText = getGameModeName(gameMode);
        float modeX = width / 2;
        float modeY = paddingTop + lineHeight * 0.8f; // 调整垂直位置
        modeTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(modeText, modeX, modeY, modeTextPaint);
        
        // 绘制评分和回合信息（居中显示）
        float infoY = modeY + lineHeight; // 中间部分，调整位置
        
        // 绘制评分（左侧）
        String scoreText;
        
        // 检查是否有一方被将死或王被吃掉
        boolean redKingExists = false;
        boolean blackKingExists = false;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (chessInfo.piece[i][j] == 1) { // 黑将
                    blackKingExists = true;
                } else if (chessInfo.piece[i][j] == 8) { // 红帅
                    redKingExists = true;
                }
            }
        }
        
        // 检查是否被将死
        boolean redDead = false;
        boolean blackDead = false;
        try {
            redDead = ChessMove.Rule.isDead(chessInfo.piece, true);
            blackDead = ChessMove.Rule.isDead(chessInfo.piece, false);
        } catch (Exception e) {
            // 忽略异常
        }
        
        // 优先显示将死或王被吃掉的情况
        if (!redKingExists) {
            scoreText = "黑方胜利！";
        } else if (!blackKingExists) {
            scoreText = "红方胜利！";
        } else if (redDead) {
            scoreText = "黑方胜利！";
        } else if (blackDead) {
            scoreText = "红方胜利！";
        } else {
            // 评分平滑过渡处理
            if (moveScore != targetMoveScore) {
                int diff = targetMoveScore - moveScore;
                if (Math.abs(diff) <= 5) {
                    // 差异较小时直接设置
                    moveScore = targetMoveScore;
                } else {
                    // 差异较大时使用平滑过渡
                    moveScore += diff / 5;
                }
                // 触发下一次重绘
                postInvalidateDelayed(50);
            }
            
            if (moveScore > 0) {
                scoreText = "红方领先: " + moveScore;
            } else if (moveScore < 0) {
                scoreText = "黑方领先: " + Math.abs(moveScore);
            } else {
                scoreText = "双方均势";
            }
        }
        float scoreX = width * 1 / 3; // 左侧
        infoTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(scoreText, scoreX, infoY, infoTextPaint);
        
        // 绘制回合数（右侧）
        int totalMoves = chessInfo.totalMoves;
        int roundCount = (totalMoves + 1) / 2;
        String stepText = "第" + roundCount + "回合";
        float stepX = width * 2 / 3; // 右侧
        infoTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(stepText, stepX, infoY, infoTextPaint);
        
        // 绘制红方和黑方时间（居中显示）
        float textY = infoY + lineHeight; // 下半部分，调整位置
        float iconSize = convertDpToPixel(16, getContext());
        
        // 红方时间（左半部分居中）
        float redCenterX = width * 1 / 3;
        // 绘制时间
        redTextPaint.setTextAlign(Paint.Align.CENTER);
        String redText = formatTime(redTime);
        canvas.drawText(redText, redCenterX, textY, redTextPaint);
        
        // 绘制搜索深度（中间位置）
        float depthCenterX = width * 1 / 2;
        infoTextPaint.setTextAlign(Paint.Align.CENTER);
        // 只要返回层数就显示，优先显示较大的层数
        if (redSearchDepth > 0 || blackSearchDepth > 0) {
            int depth = Math.max(redSearchDepth, blackSearchDepth);
            String depthText = depth + "层";
            canvas.drawText(depthText, depthCenterX, textY, infoTextPaint);
        }
        
        // 黑方时间（右半部分居中）
        float blackCenterX = width * 2 / 3;
        // 绘制时间
        blackTextPaint.setTextAlign(Paint.Align.CENTER);
        String blackText = formatTime(blackTime);
        canvas.drawText(blackText, blackCenterX, textY, blackTextPaint);
        
        // 重置文本对齐
        infoTextPaint.setTextAlign(Paint.Align.LEFT);
        redTextPaint.setTextAlign(Paint.Align.LEFT);
        blackTextPaint.setTextAlign(Paint.Align.LEFT);
    }
    
    // 格式化时间（毫秒转分:秒）
    private String formatTime(long milliseconds) {
        int seconds = (int) (milliseconds / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
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
            // 使用dp单位计算高度，确保在不同屏幕密度下显示正确
            height = (int) convertDpToPixel(100, getContext()); // 100dp高度，使布局更加紧凑
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
