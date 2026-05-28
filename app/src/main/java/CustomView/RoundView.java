package CustomView;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.List;

import Info.ChessInfo;
import ChessMove.Rule;
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
    private boolean isAIThinking = false; // AI是否正在思考
    private boolean isRedTurn = false; // 当前是否是红方回合
    private int aiThinkingProgress = 0; // AI思考动画进度
    private boolean isSuggestMode = false; // 是否处于支招模式
    private String suggestMoveText = ""; // 支招走法文本
    private List<String> suggestMoveTexts = null; // 彩色支招文本列表
    private List<Boolean> suggestMoveIsRed = null; // 彩色支招是否红方
    private String moveInfoText = ""; // 步数信息文本
    private boolean isAILoading = false; // AI 是否正在加载
    private int aiLoadingProgress = 0; // AI 加载动画进度
    private boolean isShowLoadingComplete = false; // 是否显示加载完成
    private long showLoadingCompleteTime = 0; // 显示加载完成的开始时间
    private String cachedBoardKey = "";
    private boolean cachedRedKingExists = true;
    private boolean cachedBlackKingExists = true;
    private boolean cachedRedCheckmated = false;
    private boolean cachedBlackCheckmated = false;
    private boolean cachedRedStalemated = false;
    private boolean cachedBlackStalemated = false;

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
        // 只有当深度大于0时才更新深度值，这样当AI思考完成（depth=0）时，之前的深度信息会被保留
        if (depth > 0) {
            if (isRed) {
                this.redSearchDepth = depth;
            } else {
                this.blackSearchDepth = depth;
            }
        }
        // 当深度为0时，表示AI思考完成，隐藏"AI正在思考"提示
        // 当深度大于0时，表示AI正在思考，显示"AI正在思考"提示
        this.isAIThinking = depth > 0;
        // 只有当AI正在思考时才更新isRedTurn，这样当AI思考完成后，isRedTurn会保持为AI的颜色
        if (this.isAIThinking) {
            this.isRedTurn = isRed;
            // 更新动画进度
            this.aiThinkingProgress = (this.aiThinkingProgress + 1) % 4;
        } else {
            // 重置动画进度
            this.aiThinkingProgress = 0;
            // 不重置isRedTurn，保持为AI的颜色，这样深度信息会正确显示
        }
        invalidate();
    }
    
    // 重载方法，保持向后兼容
    public void setSearchDepth(int depth) {
        // 只有当深度大于0时才更新深度值，这样当AI思考完成（depth=0）时，之前的深度信息会被保留
        if (depth > 0) {
            // 默认为黑方深度
            this.blackSearchDepth = depth;
        }
        this.isAIThinking = depth > 0;
        // 只有当AI正在思考时才更新isRedTurn，默认为黑方
        if (this.isAIThinking) {
            this.isRedTurn = false;
            // 更新动画进度
            this.aiThinkingProgress = (this.aiThinkingProgress + 1) % 4;
        } else {
            // 重置动画进度
            this.aiThinkingProgress = 0;
            // 不重置isRedTurn，保持为黑方，这样深度信息会正确显示
        }
        invalidate();
    }
    
    // 设置 AI 加载状态
    public void setAILoading(boolean loading) {
        if (this.isAILoading && !loading) {
            // 从加载中变为加载完成，显示加载完成提示
            this.isShowLoadingComplete = true;
            this.showLoadingCompleteTime = System.currentTimeMillis();
            invalidate();
            // 延迟2秒后隐藏加载完成提示
            postDelayed(new HideLoadingCompleteRunnable(this), 2000);
        }
        this.isAILoading = loading;
        // 如果正在加载，启动动画
        if (loading) {
            aiLoadingProgress = 0;
            // 使用 postInvalidateDelayed 来持续更新加载动画
            invalidate();
        }
        invalidate();
    }
    
    // 内部类：隐藏加载完成提示的Runnable
    private static class HideLoadingCompleteRunnable implements Runnable {
        private RoundView view;
        
        public HideLoadingCompleteRunnable(RoundView view) {
            this.view = view;
        }
        
        @Override
        public void run() {
            if (view != null) {
                view.isShowLoadingComplete = false;
                view.invalidate();
            }
        }
    }
    
    // 设置ChessInfo对象
    public void setChessInfo(ChessInfo chessInfo) {
        this.chessInfo = chessInfo;
        invalidate();
    }
    
    // 设置支招模式
    public void setSuggestMode(boolean isSuggestMode) {
        this.isSuggestMode = isSuggestMode;
        invalidate();
    }
    
    // 设置支招走法文本
    public void setSuggestMoveText(String moveText) {
        this.suggestMoveText = moveText;
        this.suggestMoveTexts = null; // 清空彩色文本
        this.suggestMoveIsRed = null;
        invalidate();
    }
    
    // 设置带颜色的支招走法文本
    public void setSuggestMoveTextWithColor(List<String> texts, List<Boolean> isRedList) {
        this.suggestMoveTexts = texts;
        this.suggestMoveIsRed = isRedList;
        this.suggestMoveText = ""; // 清空普通文本
        invalidate();
    }
    
    // 设置步数信息文本
    public void setMoveInfoText(String infoText) {
        this.moveInfoText = infoText;
        invalidate();
    }

    private void initPaints() {
        // 背景画笔 - 使用渐变效果
        backgroundPaint = new Paint();
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(Color.rgb(180, 110, 50)); // 温暖的棕色背景

        // 边框画笔 - 使用dp单位确保不同屏幕一致性
        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.rgb(140, 70, 35)); // 深棕色边框
        borderPaint.setStrokeWidth(convertDpToPixel(1.5f, getContext()));
        borderPaint.setAntiAlias(true);

        // 统一文字大小 - 使用dp单位
        float textSize = convertDpToPixel(14, getContext());

        // 红色文本画笔（红方回合）- 使用更醒目的红色
        redTextPaint = new Paint();
        redTextPaint.setTextSize(textSize);
        redTextPaint.setStrokeWidth(convertDpToPixel(0.5f, getContext()));
        redTextPaint.setAntiAlias(true);
        redTextPaint.setColor(Color.rgb(220, 30, 30)); // 更鲜艳的红色
        redTextPaint.setFakeBoldText(true);
        redTextPaint.setShadowLayer(convertDpToPixel(1.5f, getContext()), 
            convertDpToPixel(0.5f, getContext()), 
            convertDpToPixel(0.5f, getContext()), 
            Color.argb(100, 0, 0, 0));

        // 黑色文本画笔（黑方回合）- 使用深灰色避免纯黑
        blackTextPaint = new Paint();
        blackTextPaint.setTextSize(textSize);
        blackTextPaint.setStrokeWidth(convertDpToPixel(0.5f, getContext()));
        blackTextPaint.setAntiAlias(true);
        blackTextPaint.setColor(Color.rgb(40, 40, 40)); // 深灰色，避免纯黑
        blackTextPaint.setFakeBoldText(true);
        blackTextPaint.setShadowLayer(convertDpToPixel(1.5f, getContext()), 
            convertDpToPixel(0.5f, getContext()), 
            convertDpToPixel(0.5f, getContext()), 
            Color.argb(80, 255, 255, 255));

        // 模式文本画笔（突出显示模式）
        modeTextPaint = new Paint();
        modeTextPaint.setTextSize(convertDpToPixel(11, getContext()));
        modeTextPaint.setStrokeWidth(convertDpToPixel(0.3f, getContext()));
        modeTextPaint.setAntiAlias(true);
        modeTextPaint.setColor(Color.rgb(255, 245, 220)); // 米白色，与棕色背景对比好
        modeTextPaint.setFakeBoldText(true);
        modeTextPaint.setShadowLayer(convertDpToPixel(1f, getContext()), 
            convertDpToPixel(0.3f, getContext()), 
            convertDpToPixel(0.3f, getContext()), 
            Color.argb(60, 0, 0, 0));

        // 信息文本画笔（评分和搜索深度）
        infoTextPaint = new Paint();
        infoTextPaint.setTextSize(textSize);
        infoTextPaint.setStrokeWidth(convertDpToPixel(0.3f, getContext()));
        infoTextPaint.setAntiAlias(true);
        infoTextPaint.setColor(Color.rgb(255, 250, 240)); // 象牙白，柔和不刺眼
        infoTextPaint.setFakeBoldText(true);
        infoTextPaint.setShadowLayer(convertDpToPixel(1f, getContext()), 
            convertDpToPixel(0.3f, getContext()), 
            convertDpToPixel(0.3f, getContext()), 
            Color.argb(60, 0, 0, 0));
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
        
        // 避免频繁计算和绘制，使用缓存的视图尺寸
        int width = getWidth();
        int height = getHeight();
        
        // 绘制背景
        canvas.drawRect(0, 0, width, height, backgroundPaint);
        
        // 绘制边框 - 使用dp单位确保不同屏幕一致性
        float borderPadding = convertDpToPixel(3, getContext());
        float cornerRadius = convertDpToPixel(6, getContext());
        android.graphics.RectF rectF = new android.graphics.RectF(
            borderPadding, borderPadding, 
            width - borderPadding, height - borderPadding);
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint);
        
        // 计算垂直间距
        float paddingTop = convertDpToPixel(5, getContext());
        float lineHeight = convertDpToPixel(20, getContext());
        
        // ========== 第1行：游戏模式（左） | 当前行棋方（居中突出） | 回合数（右） ==========
        float row1Y = paddingTop + lineHeight * 0.8f;
        
        // 游戏模式（左侧）
        String modeText = getGameModeName(gameMode);
        float modeTextSize = convertDpToPixel(12, getContext());
        modeTextPaint.setTextSize(modeTextSize);
        modeTextPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(modeText, convertDpToPixel(10, getContext()), row1Y, modeTextPaint);
        
        // 当前行棋方（居中，突出显示）- 包含深度信息
        int currentDepthForTurn = chessInfo.IsRedGo ? redSearchDepth : blackSearchDepth;
        String turnText;
        if (currentDepthForTurn > 0) {
            turnText = chessInfo.IsRedGo ? "红方-" + currentDepthForTurn : "黑方-" + currentDepthForTurn;
        } else {
            turnText = chessInfo.IsRedGo ? "红方" : "黑方";
        }
        Paint turnPaint = chessInfo.IsRedGo ? redTextPaint : blackTextPaint;
        float turnTextSize = convertDpToPixel(16, getContext());
        turnPaint.setTextSize(turnTextSize);
        turnPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(turnText, width / 2, row1Y, turnPaint);
        
        // 回合数（右侧）
        int totalMoves = chessInfo.totalMoves;
        int roundCount = (totalMoves + 1) / 2;
        String stepText = "第" + roundCount + "回合";
        float stepTextSize = convertDpToPixel(13, getContext());
        infoTextPaint.setTextSize(stepTextSize);
        infoTextPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(stepText, width - convertDpToPixel(10, getContext()), row1Y, infoTextPaint);
        
        // ========== 第2行：红方时间（左） | 评分（居中） | 黑方时间（右） ==========
        float row2Y = row1Y + lineHeight + convertDpToPixel(2, getContext());
        
        // 评分计算
        String scoreText;
        
        String currentBoardKey = buildBoardKey();
        if (!currentBoardKey.equals(cachedBoardKey)) {
            cachedBoardKey = currentBoardKey;
            refreshEndgameStateCache();
        }
        
        // 检查游戏状态
        boolean isGameOver = chessInfo.status == 2;
        
        // 检查是否是和棋
        boolean isDraw = isGameOver
            && !cachedRedCheckmated
            && !cachedBlackCheckmated
            && !cachedRedStalemated
            && !cachedBlackStalemated
            && cachedRedKingExists
            && cachedBlackKingExists;
        
        // 优先显示王被吃掉的情况
        if (!cachedRedKingExists) {
            scoreText = "黑方胜利！";
        } else if (!cachedBlackKingExists) {
            scoreText = "红方胜利！";
        } else if (isDraw) {
            scoreText = "和棋！";
        } else if (isGameOver || cachedRedCheckmated || cachedBlackCheckmated || cachedRedStalemated || cachedBlackStalemated) {
            if (cachedRedCheckmated || cachedRedStalemated) {
                scoreText = "黑方胜利！";
            } else if (cachedBlackCheckmated || cachedBlackStalemated) {
                scoreText = "红方胜利！";
            } else {
                if (chessInfo.IsRedGo) {
                    scoreText = "黑方胜利！";
                } else {
                    scoreText = "红方胜利！";
                }
            }
        } else {
            // 评分平滑过渡处理
            if (moveScore != targetMoveScore) {
                int diff = targetMoveScore - moveScore;
                if (Math.abs(diff) <= 5) {
                    moveScore = targetMoveScore;
                } else {
                    moveScore += diff / 5;
                }
                postInvalidateDelayed(100);
            }
            
            if (Math.abs(moveScore) > 0) {
                if (moveScore > 0) {
                    scoreText = "红方:" + moveScore;
                } else {
                    scoreText = "黑方:" + Math.abs(moveScore);
                }
            } else {
                scoreText = "均势";
            }
        }
        
        // 红方时间（左侧）
        redTextPaint.setTextSize(convertDpToPixel(14, getContext()));
        redTextPaint.setTextAlign(Paint.Align.LEFT);
        String redText = "红 " + formatTime(redTime);
        canvas.drawText(redText, convertDpToPixel(10, getContext()), row2Y, redTextPaint);
        
        // 评分（居中）
        float scoreTextSize = convertDpToPixel(14, getContext());
        infoTextPaint.setTextSize(scoreTextSize);
        infoTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(scoreText, width / 2, row2Y, infoTextPaint);
        
        // 黑方时间（右侧）
        blackTextPaint.setTextSize(convertDpToPixel(14, getContext()));
        blackTextPaint.setTextAlign(Paint.Align.RIGHT);
        String blackText = "黑 " + formatTime(blackTime);
        canvas.drawText(blackText, width - convertDpToPixel(10, getContext()), row2Y, blackTextPaint);
        
        // ========== 第3行（可选）：AI加载信息 / 支招信息 ==========
        float row3Y = row2Y + lineHeight;
        float currentY = row3Y;
        
        boolean hasAIOrSuggestInfo = (suggestMoveText != null && !suggestMoveText.isEmpty()) || isAILoading || isShowLoadingComplete || isAIThinking;
        
        // 绘制AI加载中、加载完成或AI思考动画
        float aiTextSize = convertDpToPixel(11, getContext());
        if (isAILoading) {
            infoTextPaint.setTextSize(aiTextSize);
            infoTextPaint.setTextAlign(Paint.Align.CENTER);
            
            String dots = "";
            for (int i = 0; i < aiLoadingProgress; i++) {
                dots += ".";
            }
            String loadingText = "AI加载中" + dots;
            canvas.drawText(loadingText, width / 2, currentY, infoTextPaint);
            
            aiLoadingProgress = (aiLoadingProgress + 1) % 4;
            currentY += lineHeight;
            postInvalidateDelayed(300);
        } else if (isShowLoadingComplete) {
            infoTextPaint.setTextSize(aiTextSize);
            infoTextPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("AI加载完成！", width / 2, currentY, infoTextPaint);
            currentY += lineHeight;
        } else if (isAIThinking) {
            // AI思考动画
            infoTextPaint.setTextSize(aiTextSize);
            infoTextPaint.setTextAlign(Paint.Align.CENTER);
            
            String dots = "";
            for (int i = 0; i < aiThinkingProgress; i++) {
                dots += ".";
            }
            String thinkingText = "AI思考中" + dots;
            canvas.drawText(thinkingText, width / 2, currentY, infoTextPaint);
            
            aiThinkingProgress = (aiThinkingProgress + 1) % 4;
            currentY += lineHeight;
            postInvalidateDelayed(800);
        }
        
        // 显示支招走法信息（AI思考时不显示，避免覆盖）
        if (!isAIThinking && suggestMoveText != null && !suggestMoveText.isEmpty()) {
            float originalTextSize = infoTextPaint.getTextSize();
            infoTextPaint.setTextSize(convertDpToPixel(10, getContext()));
            infoTextPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("支招: " + suggestMoveText, width / 2, currentY, infoTextPaint);
            currentY += lineHeight;
            infoTextPaint.setTextSize(originalTextSize);
        }
        
        // 显示彩色支招走法信息（AI思考时不显示，避免覆盖）
        if (!isAIThinking && suggestMoveTexts != null && !suggestMoveTexts.isEmpty() && suggestMoveIsRed != null) {
            float originalTextSize = infoTextPaint.getTextSize();
            boolean originalFakeBold = infoTextPaint.isFakeBoldText();
            float normalSize = convertDpToPixel(11, getContext());
            float firstSize = convertDpToPixel(13, getContext());
            infoTextPaint.setTextAlign(Paint.Align.LEFT);
            
            float totalWidth = 0;
            for (int i = 0; i < suggestMoveTexts.size() && i < suggestMoveIsRed.size(); i++) {
                infoTextPaint.setTextSize(i == 0 ? firstSize : normalSize);
                totalWidth += infoTextPaint.measureText(suggestMoveTexts.get(i));
                if (i < suggestMoveTexts.size() - 1) {
                    totalWidth += infoTextPaint.measureText(" ");
                }
            }
            float startX = (width - totalWidth) / 2;
            
            float x = startX;
            for (int i = 0; i < suggestMoveTexts.size() && i < suggestMoveIsRed.size(); i++) {
                String text = suggestMoveTexts.get(i);
                boolean isRed = suggestMoveIsRed.get(i);
                
                if (isRed) {
                    infoTextPaint.setColor(Color.RED);
                } else {
                    infoTextPaint.setColor(Color.BLACK);
                }
                
                infoTextPaint.setTextSize(i == 0 ? firstSize : normalSize);
                infoTextPaint.setFakeBoldText(i == 0);
                canvas.drawText(text, x, currentY, infoTextPaint);
                x += infoTextPaint.measureText(text) + (i == 0 ? infoTextPaint.measureText(" ") : convertDpToPixel(3, getContext()));
            }
            
            currentY += lineHeight;
            infoTextPaint.setTextSize(originalTextSize);
            infoTextPaint.setFakeBoldText(originalFakeBold);
            infoTextPaint.setColor(Color.WHITE);
        }
        
        // 只在没有AI信息和支招信息时显示步数信息
        if (!hasAIOrSuggestInfo && moveInfoText != null && !moveInfoText.isEmpty()) {
            infoTextPaint.setTextSize(aiTextSize);
            infoTextPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(moveInfoText, width / 2, currentY, infoTextPaint);
        }
        
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
        
        // 计算高度 - 使用更紧凑的布局
        int height;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            height = MeasureSpec.getSize(heightMeasureSpec);
        } else {
            // 使用dp单位计算高度，确保在不同屏幕密度下显示正确
            height = (int) convertDpToPixel(80, getContext()); // 适度增大高度，容纳支招信息
        }
        
        viewWidth = width;
        viewHeight = height;
        
        setMeasuredDimension(viewWidth, viewHeight);
    }

    // 外部调用的绘制方法
    public void requestDraw() {
        invalidate();
    }

    private String buildBoardKey() {
        StringBuilder sb = new StringBuilder(128);
        if (chessInfo == null || chessInfo.piece == null) {
            return "";
        }

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                sb.append((char) ('A' + chessInfo.piece[i][j]));
            }
        }
        sb.append('|').append(chessInfo.status);
        sb.append('|').append(chessInfo.IsRedGo ? 'R' : 'B');
        sb.append('|').append(chessInfo.totalMoves);
        return sb.toString();
    }

    private void refreshEndgameStateCache() {
        cachedRedKingExists = false;
        cachedBlackKingExists = false;

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (chessInfo.piece[i][j] == 8) {
                    cachedRedKingExists = true;
                } else if (chessInfo.piece[i][j] == 1) {
                    cachedBlackKingExists = true;
                }
            }
        }

        cachedRedCheckmated = Rule.isCheckmate(chessInfo.piece, true);
        cachedBlackCheckmated = Rule.isCheckmate(chessInfo.piece, false);
        cachedRedStalemated = Rule.isStalemate(chessInfo.piece, true);
        cachedBlackStalemated = Rule.isStalemate(chessInfo.piece, false);
    }
}
