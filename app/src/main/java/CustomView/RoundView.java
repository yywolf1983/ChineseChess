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
        float lineHeight = convertDpToPixel(24, getContext());
        
        // 绘制对战模式（突出显示）
        String modeText = getGameModeName(gameMode);
        float modeX = width / 2;
        float modeY = paddingTop + lineHeight * 0.8f; // 调整垂直位置
        modeTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(modeText, modeX, modeY, modeTextPaint);
        
        // 绘制评分和搜索深度（并排显示）
        String scoreText = "评分: " + moveScore;
        // 获取当前搜索深度
        int searchDepth = 10; // 默认值
        if (PvMActivity.setting != null) {
            searchDepth = PvMActivity.setting.depth;
        }
        String depthText = "深度: " + searchDepth + "层";
        
        float infoY = modeY + lineHeight; // 中间部分，调整位置
        float scoreX = width * 1 / 3; // 左侧
        float depthX = width * 2 / 3; // 右侧
        infoTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(scoreText, scoreX, infoY, infoTextPaint);
        canvas.drawText(depthText, depthX, infoY, infoTextPaint);
        
        // 绘制回合信息和回合数（下半部分）
        String turnText = chessInfo.IsRedGo ? "红方" : "黑方";
        Paint turnPaint = chessInfo.IsRedGo ? redTextPaint : blackTextPaint;
        
        float textY = infoY + lineHeight; // 下半部分，调整位置
        
        // 绘制简单的图标提示
        float iconSize = convertDpToPixel(16, getContext());
        float turnX = width * 1 / 4; // 左侧
        float iconX = turnX - iconSize - convertDpToPixel(8, getContext());
        float iconY = textY - iconSize / 2;
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
        
        turnPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(turnText, turnX, textY, turnPaint);
        
        // 绘制回合数
        int totalMoves = chessInfo.totalMoves;
        int roundCount = (totalMoves + 1) / 2;
        String stepText = "回合: " + roundCount;
        float stepX = width * 3 / 4; // 右侧
        infoTextPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(stepText, stepX, textY, infoTextPaint);
        
        // 重置文本对齐
        infoTextPaint.setTextAlign(Paint.Align.LEFT);
        turnPaint.setTextAlign(Paint.Align.LEFT);
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
            height = (int) convertDpToPixel(100, getContext()); // 100dp高度，减少页头高度，让按钮显示出来
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
