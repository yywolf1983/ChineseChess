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
    private static final int AI_DOT_DELAY_MS = 500;
    private static final int AI_DOT_MAX = 3;
    private static final int AI_DOT_CYCLE = AI_DOT_MAX + 1;

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
    private boolean dotAnimationScheduled = false;

    private final Runnable aiDotAnimationRunnable = () -> {
        dotAnimationScheduled = false;
        if (!shouldAnimateDots()) {
            return;
        }
        if (isSuggestMode || isAIThinking) {
            aiThinkingProgress = (aiThinkingProgress + 1) % AI_DOT_CYCLE;
        }
        if (isAILoading) {
            aiLoadingProgress = (aiLoadingProgress + 1) % AI_DOT_CYCLE;
        }
        invalidate();
        scheduleNextDotAnimation();
    };

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
        boolean wasThinking = this.isAIThinking;
        this.isAIThinking = depth > 0;
        // 只有当AI正在思考时才更新isRedTurn，这样当AI思考完成后，isRedTurn会保持为AI的颜色
        if (this.isAIThinking) {
            this.isRedTurn = isRed;
            if (!wasThinking && !isSuggestMode) {
                this.aiThinkingProgress = 0;
            }
        } else if (!isSuggestMode) {
            this.aiThinkingProgress = 0;
        }
        syncDotAnimation();
        invalidate();
    }
    
    // 重载方法，保持向后兼容
    public void setSearchDepth(int depth) {
        // 只有当深度大于0时才更新深度值，这样当AI思考完成（depth=0）时，之前的深度信息会被保留
        if (depth > 0) {
            // 默认为黑方深度
            this.blackSearchDepth = depth;
        }
        boolean wasThinking = this.isAIThinking;
        this.isAIThinking = depth > 0;
        // 只有当AI正在思考时才更新isRedTurn，默认为黑方
        if (this.isAIThinking) {
            this.isRedTurn = false;
            if (!wasThinking && !isSuggestMode) {
                this.aiThinkingProgress = 0;
            }
        } else if (!isSuggestMode) {
            this.aiThinkingProgress = 0;
        }
        syncDotAnimation();
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
        aiLoadingProgress = 0;
        syncDotAnimation();
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
        if (isSuggestMode) {
            this.aiThinkingProgress = 0;
        }
        syncDotAnimation();
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
        backgroundPaint = new Paint();
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(Color.rgb(180, 130, 80));

        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.rgb(100, 60, 30));
        borderPaint.setStrokeWidth(convertDpToPixel(1.5f, getContext()));
        borderPaint.setAntiAlias(true);

        float textSize = convertDpToPixel(12, getContext());

        redTextPaint = new Paint();
        redTextPaint.setTextSize(textSize);
        redTextPaint.setStrokeWidth(convertDpToPixel(0.5f, getContext()));
        redTextPaint.setAntiAlias(true);
        redTextPaint.setColor(Color.rgb(200, 40, 40));
        redTextPaint.setFakeBoldText(true);
        redTextPaint.setShadowLayer(convertDpToPixel(1.5f, getContext()), 
            convertDpToPixel(0.5f, getContext()), 
            convertDpToPixel(0.5f, getContext()), 
            Color.argb(100, 0, 0, 0));

        blackTextPaint = new Paint();
        blackTextPaint.setTextSize(textSize);
        blackTextPaint.setStrokeWidth(convertDpToPixel(0.5f, getContext()));
        blackTextPaint.setAntiAlias(true);
        blackTextPaint.setColor(Color.rgb(35, 35, 35));
        blackTextPaint.setFakeBoldText(true);
        blackTextPaint.setShadowLayer(convertDpToPixel(1.5f, getContext()), 
            convertDpToPixel(0.5f, getContext()), 
            convertDpToPixel(0.5f, getContext()), 
            Color.argb(80, 255, 255, 255));

        modeTextPaint = new Paint();
        modeTextPaint.setTextSize(convertDpToPixel(14, getContext()));
        modeTextPaint.setStrokeWidth(convertDpToPixel(1f, getContext()));
        modeTextPaint.setAntiAlias(true);
        modeTextPaint.setColor(Color.rgb(230, 200, 130));
        modeTextPaint.setFakeBoldText(true);
        modeTextPaint.setStyle(Paint.Style.FILL);
        modeTextPaint.clearShadowLayer();
        modeTextPaint.setStrokeWidth(2f);

        infoTextPaint = new Paint();
        infoTextPaint.setTextSize(textSize);
        infoTextPaint.setStrokeWidth(convertDpToPixel(0.3f, getContext()));
        infoTextPaint.setAntiAlias(true);
        infoTextPaint.setColor(Color.rgb(245, 240, 230));
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

    private String buildDotSuffix(int progress) {
        int dotCount = progress % AI_DOT_CYCLE;
        StringBuilder dots = new StringBuilder(dotCount);
        for (int i = 0; i < dotCount; i++) {
            dots.append('.');
        }
        return dots.toString();
    }

    private void drawThinkingText(Canvas canvas, float width, float y, float textSize, String prefix, int progress) {
        infoTextPaint.setTextSize(textSize);
        infoTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(prefix + buildDotSuffix(progress), width / 2, y, infoTextPaint);
    }

    private boolean shouldAnimateDots() {
        return isSuggestMode || isAILoading || isAIThinking;
    }

    private void scheduleNextDotAnimation() {
        if (shouldAnimateDots()) {
            dotAnimationScheduled = true;
            postDelayed(aiDotAnimationRunnable, AI_DOT_DELAY_MS);
        }
    }

    private void stopDotAnimation() {
        removeCallbacks(aiDotAnimationRunnable);
        dotAnimationScheduled = false;
    }

    private void syncDotAnimation() {
        if (shouldAnimateDots()) {
            if (!dotAnimationScheduled) {
                scheduleNextDotAnimation();
            }
        } else {
            stopDotAnimation();
            aiThinkingProgress = 0;
            aiLoadingProgress = 0;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopDotAnimation();
        super.onDetachedFromWindow();
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
        float paddingTop = convertDpToPixel(6, getContext());
        float lineHeight = convertDpToPixel(24, getContext());
        
        // ========== 第1行：游戏模式（左） | 当前行棋方（居中突出） | 回合数（右） ==========
        float row1Y = paddingTop + lineHeight * 0.8f;
        
        // 游戏模式（左侧）+ 深度
        float modeTextSize = convertDpToPixel(15, getContext());
        modeTextPaint.setTextSize(modeTextSize);
        modeTextPaint.setTextAlign(Paint.Align.LEFT);
        modeTextPaint.setFakeBoldText(true);
        modeTextPaint.clearShadowLayer();
        
        float modeStartX = convertDpToPixel(10, getContext());
        float currentX = modeStartX;
        
        switch (gameMode) {
            case 0:
                modeTextPaint.setColor(Color.rgb(230, 195, 80));
                canvas.drawText("双人", currentX, row1Y, modeTextPaint);
                currentX += modeTextPaint.measureText("双人");
                modeTextPaint.setColor(Color.rgb(220, 160, 50));
                canvas.drawText("对战", currentX, row1Y, modeTextPaint);
                break;
            case 1:
                modeTextPaint.setColor(Color.rgb(230, 200, 130));
                canvas.drawText("玩家", currentX, row1Y, modeTextPaint);
                currentX += modeTextPaint.measureText("玩家");
                modeTextPaint.setColor(Color.rgb(200, 40, 40));
                canvas.drawText("红棋", currentX, row1Y, modeTextPaint);
                break;
            case 2:
                modeTextPaint.setColor(Color.rgb(230, 200, 130));
                canvas.drawText("玩家", currentX, row1Y, modeTextPaint);
                currentX += modeTextPaint.measureText("玩家");
                modeTextPaint.setColor(Color.rgb(35, 35, 35));
                canvas.drawText("黑棋", currentX, row1Y, modeTextPaint);
                break;
            case 3:
                modeTextPaint.setColor(Color.rgb(230, 195, 80));
                canvas.drawText("双机", currentX, row1Y, modeTextPaint);
                currentX += modeTextPaint.measureText("双机");
                modeTextPaint.setColor(Color.rgb(100, 60, 130));
                canvas.drawText("对战", currentX, row1Y, modeTextPaint);
                break;
            default:
                modeTextPaint.setColor(Color.rgb(255, 225, 150));
                canvas.drawText(getGameModeName(gameMode), currentX, row1Y, modeTextPaint);
                break;
        }
        
        
        
        // 当前行棋方（居中，突出显示）
        String turnText = chessInfo.IsRedGo ? "红方" : "黑方";
        Paint turnPaint = chessInfo.IsRedGo ? redTextPaint : blackTextPaint;
        float turnTextSize = convertDpToPixel(18, getContext());
        turnPaint.setTextSize(turnTextSize);
        turnPaint.setTextAlign(Paint.Align.CENTER);
        turnPaint.setFakeBoldText(true);
        canvas.drawText(turnText, width / 2, row1Y + convertDpToPixel(4, getContext()), turnPaint);
        
        // 搜索深度（当前行棋方文字右下方）
        int currentDepthForTurn = chessInfo.IsRedGo ? redSearchDepth : blackSearchDepth;
        if (currentDepthForTurn > 0) {
            String depthText = "深度" + currentDepthForTurn;
            infoTextPaint.setTextSize(convertDpToPixel(13, getContext()));
            infoTextPaint.setTextAlign(Paint.Align.LEFT);
            infoTextPaint.setColor(Color.argb(150, 0, 0, 0));
            float turnTextWidth = turnPaint.measureText(turnText);
            float depthX = width / 2 + turnTextWidth / 2 + convertDpToPixel(4, getContext());
            float depthY = row1Y + convertDpToPixel(8, getContext());
            canvas.drawText(depthText, depthX, depthY, infoTextPaint);
        }
        
        // 分数（当前行棋方文字左下方，比深度大些）
        int score = moveScore;
        String scoreDisplayText;
        int textColor;
        if (score > 0) {
            scoreDisplayText = String.valueOf(score);
            textColor = Color.rgb(200, 40, 40);
        } else if (score < 0) {
            scoreDisplayText = String.valueOf(Math.abs(score));
            textColor = Color.rgb(35, 35, 35);
        } else {
            scoreDisplayText = "均势";
            textColor = Color.rgb(100, 90, 80);
        }
        float turnTextWidth = turnPaint.measureText(turnText);
        infoTextPaint.setTextSize(convertDpToPixel(15, getContext()));
        infoTextPaint.setTextAlign(Paint.Align.RIGHT);
        infoTextPaint.setFakeBoldText(true);
        infoTextPaint.setColor(textColor);
        infoTextPaint.setShadowLayer(convertDpToPixel(1f, getContext()), 0, 0, Color.argb(80, 255, 255, 255));
        float scoreX = width / 2 - turnTextWidth / 2 - convertDpToPixel(4, getContext());
        float scoreY = row1Y + convertDpToPixel(8, getContext());
        canvas.drawText(scoreDisplayText, scoreX, scoreY, infoTextPaint);
        infoTextPaint.clearShadowLayer();
        
        // 回合数（右侧）
        int totalMoves = chessInfo.totalMoves;
        int roundCount = (totalMoves + 1) / 2;
        String stepText = "第" + roundCount + "回合";
        float stepTextSize = convertDpToPixel(16, getContext());
        infoTextPaint.setTextSize(stepTextSize);
        infoTextPaint.setTextAlign(Paint.Align.RIGHT);
        infoTextPaint.setFakeBoldText(true);
        infoTextPaint.setColor(Color.rgb(245, 240, 230));
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
        redTextPaint.setTextSize(convertDpToPixel(15, getContext()));
        redTextPaint.setTextAlign(Paint.Align.LEFT);
        redTextPaint.setFakeBoldText(true);
        String redText = formatTime(redTime);
        canvas.drawText(redText, convertDpToPixel(10, getContext()), row2Y, redTextPaint);
        
        // 评分（居中）- 双向进度条
        drawScoreBar(canvas, width, row2Y, moveScore);
        
        // 黑方时间（右侧）
        blackTextPaint.setTextSize(convertDpToPixel(15, getContext()));
        blackTextPaint.setTextAlign(Paint.Align.RIGHT);
        blackTextPaint.setFakeBoldText(true);
        String blackText = formatTime(blackTime);
        canvas.drawText(blackText, width - convertDpToPixel(10, getContext()), row2Y, blackTextPaint);
        
        // ========== 第3行（可选）：AI加载信息 / 支招信息 ==========
        float row3Y = row2Y + lineHeight;
        float currentY = row3Y;
        
        boolean hasSuggest = (suggestMoveText != null && !suggestMoveText.isEmpty())
                || (suggestMoveTexts != null && !suggestMoveTexts.isEmpty());
        boolean hasAIOrSuggestInfo = hasSuggest || isAILoading || isShowLoadingComplete || isAIThinking || isSuggestMode;
        
        // 绘制AI加载中、加载完成、支招思考或AI走棋思考动画
        float aiTextSize = convertDpToPixel(13, getContext());
        if (isSuggestMode) {
            drawThinkingText(canvas, width, currentY, aiTextSize, "AI正在思考", aiThinkingProgress);
            currentY += lineHeight;
        } else if (!hasSuggest) {
            if (isAILoading) {
                drawThinkingText(canvas, width, currentY, aiTextSize, "AI加载中", aiLoadingProgress);
                currentY += lineHeight;
            } else if (isShowLoadingComplete) {
                infoTextPaint.setTextSize(aiTextSize);
                infoTextPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("AI加载完成！", width / 2, currentY, infoTextPaint);
                currentY += lineHeight;
            } else if (isAIThinking) {
                drawThinkingText(canvas, width, currentY, aiTextSize, "AI思考中", aiThinkingProgress);
                currentY += lineHeight;
            }
        }
        
        // 显示支招走法信息（支招思考或AI走棋思考时不显示，避免覆盖）
        if (!isSuggestMode && !isAIThinking && suggestMoveText != null && !suggestMoveText.isEmpty()) {
            float originalTextSize = infoTextPaint.getTextSize();
            infoTextPaint.setTextSize(convertDpToPixel(12, getContext()));
            infoTextPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("支招: " + suggestMoveText, width / 2, currentY, infoTextPaint);
            currentY += lineHeight;
            infoTextPaint.setTextSize(originalTextSize);
        }
        
        // 显示彩色支招走法信息（支招思考或AI走棋思考时不显示，避免覆盖）
        if (!isSuggestMode && !isAIThinking && suggestMoveTexts != null && !suggestMoveTexts.isEmpty() && suggestMoveIsRed != null) {
            float originalTextSize = infoTextPaint.getTextSize();
            boolean originalFakeBold = infoTextPaint.isFakeBoldText();
            float normalSize = convertDpToPixel(13, getContext());
            float firstSize = convertDpToPixel(15, getContext());
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
                return "玩家红棋";
            case 2:
                return "玩家黑棋";
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
        
        // 计算高度 - 增大高度以容纳更大的字体
        int height;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            height = MeasureSpec.getSize(heightMeasureSpec);
        } else {
            // 使用dp单位计算高度，确保在不同屏幕密度下显示正确
            height = (int) convertDpToPixel(100, getContext()); // 适配当前字体大小
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
    
    private void drawScoreBar(Canvas canvas, int width, float centerY, int score) {
        float barWidth = width * 0.7f;
        float barHeight = convertDpToPixel(14, getContext());
        float barX = (width - barWidth) / 2;
        float barY = centerY - barHeight / 2 - convertDpToPixel(3, getContext());
        
        float cornerRadius = barHeight / 2;
        android.graphics.RectF barRect = new android.graphics.RectF(barX, barY, barX + barWidth, barY + barHeight);
        
        boolean isGameOver = chessInfo.status == 2;
        boolean hasWinner = !cachedRedKingExists || !cachedBlackKingExists || 
                           cachedRedCheckmated || cachedBlackCheckmated || 
                           cachedRedStalemated || cachedBlackStalemated;
        
        boolean isDraw = isGameOver
            && !cachedRedCheckmated
            && !cachedBlackCheckmated
            && !cachedRedStalemated
            && !cachedBlackStalemated
            && cachedRedKingExists
            && cachedBlackKingExists;
            
            if (isGameOver || hasWinner) {
            String winText = "";
            int winColor = Color.rgb(0, 0, 0);
            
            if (!cachedRedKingExists || cachedRedCheckmated || cachedRedStalemated) {
                winText = "黑方胜利！";
                winColor = Color.rgb(0, 0, 0);
            } else if (!cachedBlackKingExists || cachedBlackCheckmated || cachedBlackStalemated) {
                winText = "红方胜利！";
                winColor = Color.rgb(255, 0, 0);
            } else if (isDraw) {
                winText = "和棋！";
                winColor = Color.rgb(128, 128, 128);
            } else if (isGameOver) {
                winText = chessInfo.IsRedGo ? "黑方胜利！" : "红方胜利！";
                winColor = chessInfo.IsRedGo ? Color.rgb(0, 0, 0) : Color.rgb(255, 0, 0);
            }
            
            Paint winBgPaint = new Paint();
            winBgPaint.setStyle(Paint.Style.FILL);
            winBgPaint.setColor(Color.argb(200, 255, 255, 255));
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, winBgPaint);
            
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, borderPaint);
            
            infoTextPaint.setTextSize(convertDpToPixel(12, getContext()));
            infoTextPaint.setTextAlign(Paint.Align.CENTER);
            infoTextPaint.setFakeBoldText(true);
            infoTextPaint.setColor(winColor);
            infoTextPaint.setShadowLayer(convertDpToPixel(2f, getContext()), 0, 0, Color.argb(100, 255, 255, 255));
            canvas.drawText(winText, width / 2, centerY - convertDpToPixel(3, getContext()) + convertDpToPixel(4, getContext()), infoTextPaint);
            
            infoTextPaint.clearShadowLayer();
            infoTextPaint.setColor(Color.rgb(255, 250, 240));
            return;
        }
        
        android.graphics.Path clipPath = new android.graphics.Path();
        clipPath.addRoundRect(barRect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);
        
        float maxScore = 1000f;
        float scoreRatio = Math.abs(score) / maxScore;
        if (scoreRatio > 1) scoreRatio = 1;
        
        float centerX = width / 2;
        float totalRange = barWidth / 2;
        
        Paint redBarPaint = new Paint();
        redBarPaint.setStyle(Paint.Style.FILL);
        redBarPaint.setColor(Color.rgb(180, 30, 30));
        
        Paint blackBarPaint = new Paint();
        blackBarPaint.setStyle(Paint.Style.FILL);
        blackBarPaint.setColor(Color.rgb(40, 40, 40));
        
        float redStartX, redEndX, blackStartX, blackEndX;
        
        if (score > 0) {
            redStartX = barX;
            redEndX = centerX + totalRange * scoreRatio;
            blackStartX = centerX + totalRange * scoreRatio;
            blackEndX = barX + barWidth;
        } else if (score < 0) {
            redStartX = barX;
            redEndX = centerX - totalRange * scoreRatio;
            blackStartX = centerX - totalRange * scoreRatio;
            blackEndX = barX + barWidth;
        } else {
            redStartX = barX;
            redEndX = centerX;
            blackStartX = centerX;
            blackEndX = barX + barWidth;
        }
        
        android.graphics.RectF redRect = new android.graphics.RectF(redStartX, barY, redEndX, barY + barHeight);
        canvas.drawRect(redRect, redBarPaint);
        
        android.graphics.RectF blackRect = new android.graphics.RectF(blackStartX, barY, blackEndX, barY + barHeight);
        canvas.drawRect(blackRect, blackBarPaint);
        
        canvas.restore();
        
        canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, borderPaint);
        
        infoTextPaint.clearShadowLayer();
        infoTextPaint.setColor(Color.rgb(255, 250, 240));
    }
}
