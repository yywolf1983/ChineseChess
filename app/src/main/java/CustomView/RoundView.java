package CustomView;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import top.nones.chessgame.R;

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
    private int lastSearchDepth = 0; // 最近一次有效深度（用于持续显示，不随行棋方切换消失）
    private int pendingFinalDepth = 0; // 思考过程中的最大深度，仅在思考结束时固定显示
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
    private Paint borderPaint; // 边框画笔（底部信息条）
    private Paint boardBorderPaint; // 棋盘外框专用画笔（加粗）
    private Paint modeTextPaint; // 模式文本画笔
    private Paint aiTextPaint; // 电脑方（AI）文本画笔
    private Paint iconPaint; // 阵营图标（人/电脑）画笔
    private Paint winBgPaint; // 胜利背景画笔（缓存，避免onDraw中频繁创建）
    private Path scoreBarPath; // 评分条裁剪路径（缓存，避免onDraw中频繁创建）
    private Paint redBarPaint; // 红方进度条画笔（缓存，避免onDraw中频繁创建）
    private Paint blackBarPaint; // 黑方进度条画笔（缓存，避免onDraw中频繁创建）
    private HideLoadingCompleteRunnable hideLoadingCompleteRunnable; // 隐藏加载完成回调（缓存引用，便于移除）
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
        postInvalidate();
    }
    
    // 设置走法评分（平滑过渡）
    public void setMoveScore(int score) {
        this.targetMoveScore = score;
        postInvalidate();
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
        postInvalidate();
    }
    
    // 设置搜索深度
    public void setSearchDepth(int depth, boolean isRed) {
        // 思考过程中只记录最大深度，不实时更新显示；思考结束（depth=0）时才固定显示最终值
        if (depth > 0) {
            if (isRed) {
                this.redSearchDepth = depth;
            } else {
                this.blackSearchDepth = depth;
            }
            if (depth > this.pendingFinalDepth) {
                this.pendingFinalDepth = depth;
            }
        } else {
            // 只有真正从"思考中"结束（之前在思考）才把最终深度固定显示，
            // 避免连续多次 depth=0 把已显示的最终值清空
            if (this.isAIThinking) {
                this.lastSearchDepth = this.pendingFinalDepth;
            }
            this.pendingFinalDepth = 0;
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
        postInvalidate();
    }
    
    // 标记"AI 正在思考"：仅维持思考动画与行棋方，不写入任何深度值。
    // 与 setSearchDepth(1) 的区别：不会把"启动初期的占位深度 1"误当作
    // 真实深度记录进 pendingFinalDepth，从而避免搜索被中断时深度停在 1。
    public void markThinking(boolean isRed) {
        this.isAIThinking = true;
        this.isRedTurn = isRed;
        if (!isSuggestMode) {
            this.aiThinkingProgress = 0;
        }
        syncDotAnimation();
        postInvalidate();
    }

    // 清除搜索深度与思考状态（用于棋谱导航/加载，避免残留深度与思考动画）
    public void clearSearchState() {
        this.redSearchDepth = 0;
        this.blackSearchDepth = 0;
        this.lastSearchDepth = 0;
        this.pendingFinalDepth = 0;
        this.isAIThinking = false;
        this.aiThinkingProgress = 0;
        syncDotAnimation();
        postInvalidate();
    }

    // 重载方法，保持向后兼容
    public void setSearchDepth(int depth) {
        // 思考过程中只记录最大深度，不实时更新显示；思考结束（depth=0）时才固定显示最终值
        if (depth > 0) {
            // 默认为黑方深度
            this.blackSearchDepth = depth;
            if (depth > this.pendingFinalDepth) {
                this.pendingFinalDepth = depth;
            }
        } else {
            if (this.isAIThinking) {
                this.lastSearchDepth = this.pendingFinalDepth;
            }
            this.pendingFinalDepth = 0;
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
        postInvalidate();
    }
    
    // 设置 AI 加载状态
    public void setAILoading(boolean loading) {
        if (this.isAILoading && !loading) {
            // 从加载中变为加载完成，显示加载完成提示
            this.isShowLoadingComplete = true;
            this.showLoadingCompleteTime = System.currentTimeMillis();
            invalidate();
            // 延迟2秒后隐藏加载完成提示（复用缓存的Runnable实例，便于在onDetachedFromWindow中移除）
            if (hideLoadingCompleteRunnable == null) {
                hideLoadingCompleteRunnable = new HideLoadingCompleteRunnable(this);
            }
            removeCallbacks(hideLoadingCompleteRunnable);
            postDelayed(hideLoadingCompleteRunnable, 2000);
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
        postInvalidate();
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

        // 棋盘外框专用画笔（与底部信息条边框同宽，保留原始细线）
        boardBorderPaint = new Paint();
        boardBorderPaint.setStyle(Paint.Style.STROKE);
        boardBorderPaint.setColor(Color.rgb(100, 60, 30));
        boardBorderPaint.setStrokeWidth(convertDpToPixel(2.5f, getContext()));
        boardBorderPaint.setAntiAlias(true);

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

        aiTextPaint = new Paint();
        aiTextPaint.setTextSize(convertDpToPixel(13, getContext()));
        aiTextPaint.setStrokeWidth(convertDpToPixel(0.5f, getContext()));
        aiTextPaint.setAntiAlias(true);
        aiTextPaint.setColor(Color.rgb(110, 175, 240));
        aiTextPaint.setFakeBoldText(true);

        iconPaint = new Paint();
        iconPaint.setAntiAlias(true);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(convertDpToPixel(1.5f, getContext()));
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setColor(Color.rgb(200, 40, 40));

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

        winBgPaint = new Paint();
        winBgPaint.setStyle(Paint.Style.FILL);
        winBgPaint.setColor(Color.argb(200, 255, 255, 255));

        scoreBarPath = new Path();

        redBarPaint = new Paint();
        redBarPaint.setStyle(Paint.Style.FILL);
        redBarPaint.setColor(Color.rgb(180, 30, 30));

        blackBarPaint = new Paint();
        blackBarPaint.setStyle(Paint.Style.FILL);
        blackBarPaint.setColor(Color.rgb(40, 40, 40));
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
        // 移除隐藏加载完成提示的回调，防止内存泄漏
        if (hideLoadingCompleteRunnable != null) {
            removeCallbacks(hideLoadingCompleteRunnable);
        }
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
        
        // 绘制边框 - 使用dp单位确保不同屏幕一致性，颜色随行棋方变化
        float borderPadding = convertDpToPixel(3, getContext());
        float cornerRadius = convertDpToPixel(6, getContext());
        android.graphics.RectF rectF = new android.graphics.RectF(
            borderPadding, borderPadding, 
            width - borderPadding, height - borderPadding);
        // 红方行棋用红色边框，黑方行棋用深色边框（使用加粗的棋盘专用画笔）
        int turnColor = chessInfo.IsRedGo ? Color.rgb(180, 40, 40) : Color.rgb(35, 35, 35);
        boardBorderPaint.setColor(turnColor);
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, boardBorderPaint);
        // 底部信息条（滚动条）外框同样随行棋方显示红/黑，保留原始细线宽
        borderPaint.setColor(turnColor);
        
        // 计算垂直间距与行坐标
        float paddingTop = convertDpToPixel(8, getContext());
        float lineHeight = convertDpToPixel(22, getContext());

        // 双方阵营（人/电脑）判定：红方为电脑当且仅当 gameMode 为 2 或 3；黑方为电脑当且仅当 gameMode 为 1 或 3
        boolean isRedAI = (gameMode == 2 || gameMode == 3);
        boolean isBlackAI = (gameMode == 1 || gameMode == 3);
        int redSideColor = isRedAI ? Color.rgb(90, 150, 235) : Color.rgb(200, 40, 40);
        int blackSideColor = isBlackAI ? Color.rgb(90, 150, 235) : Color.rgb(40, 40, 40);

        // ========== 行坐标（紧凑三行：形势/回合/深度 → 时间+图标+评分条 → AI提示） ==========
        float formY = paddingTop + convertDpToPixel(16, getContext());   // 第1行：回合 / 形势 / 深度
        float row2Y = paddingTop + convertDpToPixel(44, getContext());   // 第2行：时间 + 阵营图标 + 评分条
        float row3Y = paddingTop + convertDpToPixel(66, getContext());   // 第3行：AI思考 / 支招 / 步数


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
        
        // 红方阵营图标（时间旁）+ 红方时间（左侧）
        // 轮到红方走棋时，图标加大并加金色高亮环，提示当前行棋方
        boolean isRedTurnNow = chessInfo.IsRedGo;
        float sideIconSize2 = convertDpToPixel(isRedTurnNow ? 22 : 16, getContext());
        float redIconCx = convertDpToPixel(12, getContext()) + sideIconSize2 / 2;
        float redIconCy = row2Y - convertDpToPixel(4, getContext());
        if (isRedTurnNow) {
            drawActiveRing(canvas, redIconCx, redIconCy, sideIconSize2);
        }
        drawSideIcon(canvas, redIconCx, redIconCy, sideIconSize2, isRedAI, redSideColor);
        float redTimeX = convertDpToPixel(12, getContext()) + sideIconSize2 + convertDpToPixel(4, getContext());
        redTextPaint.setTextSize(convertDpToPixel(13, getContext()));
        redTextPaint.setTextAlign(Paint.Align.LEFT);
        redTextPaint.setFakeBoldText(true);
        String redText = formatTime(redTime);
        canvas.drawText(redText, redTimeX, row2Y, redTextPaint);

        // 评分（居中）- 双向进度条
        drawScoreBar(canvas, width, row2Y, moveScore);

        // 黑方时间（右侧）
        blackTextPaint.setTextSize(convertDpToPixel(13, getContext()));
        blackTextPaint.setTextAlign(Paint.Align.RIGHT);
        blackTextPaint.setFakeBoldText(true);
        String blackText = formatTime(blackTime);
        canvas.drawText(blackText, width - convertDpToPixel(12, getContext()), row2Y, blackTextPaint);

        // 黑方阵营图标（时间左侧）
        // 轮到黑方走棋时，图标加大并加金色高亮环，提示当前行棋方
        boolean isBlackTurnNow = !chessInfo.IsRedGo;
        float blackIconSize = convertDpToPixel(isBlackTurnNow ? 22 : 16, getContext());
        float blackTimeW = blackTextPaint.measureText(blackText);
        float blackIconCx = width - convertDpToPixel(12, getContext()) - blackTimeW
                - convertDpToPixel(4, getContext()) - blackIconSize / 2;
        float blackIconCy = row2Y - convertDpToPixel(4, getContext());
        if (isBlackTurnNow) {
            drawActiveRing(canvas, blackIconCx, blackIconCy, blackIconSize);
        }
        drawSideIcon(canvas, blackIconCx, blackIconCy, blackIconSize, isBlackAI, blackSideColor);

        // 评分条上方：回合数 + 形势（带文字提示）
        int score = moveScore;
        int totalMoves = chessInfo.totalMoves;
        int roundCount = (totalMoves + 1) / 2;
        boolean isResult = scoreText != null
                && (scoreText.contains("胜利") || scoreText.contains("和棋"));
        String formStr;
        int formColor;
        if (isResult) {
            formStr = scoreText;
            formColor = scoreText.contains("红方") ? Color.rgb(215, 60, 60) : Color.rgb(30, 30, 30);
        } else if (score > 0) {
            formStr = "红方 +" + score;
            formColor = Color.rgb(215, 60, 60);
        } else if (score < 0) {
            formStr = "黑方 +" + Math.abs(score);
            formColor = Color.rgb(30, 30, 30);
        } else {
            formStr = "形势均势";
            formColor = Color.rgb(255, 245, 220);
        }
        // 回合/深度用固定暖白色，只有分数（形势）颜色随优劣变化，避免文字颜色频繁跳变
        int neutralColor = Color.rgb(252, 246, 235);
        // 第1行改为固定槽位：左=回合、中=形势、右=深度，三者互不依赖，
        // 深度出现/消失或形势字数变化时各行不再整体左右晃动
        String roundStr = "第" + roundCount + "回合";
        float padX = convertDpToPixel(12, getContext());
        infoTextPaint.setTextSize(convertDpToPixel(14, getContext()));
        infoTextPaint.setFakeBoldText(true);
        // 左：回合（固定左对齐）
        infoTextPaint.setColor(neutralColor);
        infoTextPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(roundStr, padX, formY, infoTextPaint);
        // 中：形势/分数（始终居中，字号比回合、深度更大，突出评分）
        infoTextPaint.setTextSize(convertDpToPixel(16, getContext()));
        infoTextPaint.setColor(formColor);
        infoTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(formStr, width / 2, formY + convertDpToPixel(3, getContext()), infoTextPaint);
        // 右：深度（有值时右对齐固定槽位，消失也不影响其他两段），恢复原始字号
        infoTextPaint.setTextSize(convertDpToPixel(14, getContext()));
        infoTextPaint.setColor(neutralColor);
        infoTextPaint.setTextAlign(Paint.Align.RIGHT);
        if (lastSearchDepth > 0) {
            canvas.drawText("深度 " + lastSearchDepth, width - padX, formY, infoTextPaint);
        }
        infoTextPaint.setTextAlign(Paint.Align.LEFT);
        
        // ========== 第3行（可选）：AI加载信息 / 支招信息 ==========
        float currentY = row3Y;
        
        boolean hasSuggest = (suggestMoveText != null && !suggestMoveText.isEmpty())
                || (suggestMoveTexts != null && !suggestMoveTexts.isEmpty());
        boolean hasAIOrSuggestInfo = hasSuggest || isAILoading || isShowLoadingComplete || isAIThinking || isSuggestMode;
        
        // 绘制AI加载中、加载完成、支招思考或AI走棋思考动画
        float aiTextSize = convertDpToPixel(14, getContext());
        infoTextPaint.setColor(Color.rgb(130, 195, 255)); // AI/支招提示统一醒目蓝
        if (isSuggestMode) {
            drawThinkingText(canvas, width, currentY, aiTextSize, "AI思考中", aiThinkingProgress);
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
        
        // 只在没有AI信息和支招信息时显示步数信息（用中性暖白色）
        if (!hasAIOrSuggestInfo && moveInfoText != null && !moveInfoText.isEmpty()) {
            infoTextPaint.setTextSize(aiTextSize);
            infoTextPaint.setTextAlign(Paint.Align.CENTER);
            infoTextPaint.setColor(neutralColor);
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
    
    // 在 (cx, cy) 处绘制一个阵营图标：isAI 为 true 表示电脑（机器人），否则为玩家（棋子）
    private void drawSideIcon(Canvas canvas, float cx, float cy, float size, boolean isAI, int color) {
        // 机器人（AI）使用固定科技蓝色，与红/黑阵营区分；玩家按阵营色
        int iconColor = isAI ? Color.parseColor("#3D7BFF") : color;
        // 圆形底色衬底，让图标更醒目
        Paint bgPaint = new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.argb(45, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor)));
        canvas.drawCircle(cx, cy, size * 0.56f, bgPaint);

        // 使用开源图标：玩家=人(ic_player)、电脑=机器人(ic_ai)
        // 玩家图标按阵营色着色；机器人图标自带配色，不重新着色以免变单色
        int resId = isAI ? R.drawable.ic_ai : R.drawable.ic_player;
        Drawable d = ContextCompat.getDrawable(getContext(), resId);
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
            if (!isAI) {
                DrawableCompat.setTint(d, iconColor);
            }
            int s = (int) (size * 0.94f);
            d.setBounds(Math.round(cx - s / 2f), Math.round(cy - s / 2f),
                    Math.round(cx + s / 2f), Math.round(cy + s / 2f));
            d.draw(canvas);
        }
    }

    // 行棋方高亮环：围绕时间旁的阵营图标，金色环提示当前该方走棋
    private void drawActiveRing(Canvas canvas, float cx, float cy, float size) {
        Paint ring = new Paint();
        ring.setAntiAlias(true);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(convertDpToPixel(2f, getContext()));
        ring.setColor(Color.rgb(245, 210, 120));
        canvas.drawCircle(cx, cy, size * 0.6f, ring);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        
        // 获取宽度
        int width = MeasureSpec.getSize(widthMeasureSpec);
        
        // 计算高度 - 紧凑三行布局，避免占用过多屏幕空间
        int height;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            height = MeasureSpec.getSize(heightMeasureSpec);
        } else {
            // 使用dp单位计算高度，确保在不同屏幕密度下显示正确
            height = (int) convertDpToPixel(88, getContext()); // 紧凑三行：形势/时间评分条/AI提示
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
        float barWidth = width * 0.56f;
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
        
        scoreBarPath.reset();
        scoreBarPath.addRoundRect(barRect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW);
        canvas.save();
        canvas.clipPath(scoreBarPath);
        
        float maxScore = 1000f;
        float scoreRatio = Math.abs(score) / maxScore;
        if (scoreRatio > 1) scoreRatio = 1;
        
        float centerX = width / 2;
        float totalRange = barWidth / 2;
        
        // redBarPaint 和 blackBarPaint 已在 initPaints() 中初始化缓存

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
