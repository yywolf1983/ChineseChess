package CustomView;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

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
    private boolean isAIStopped = false;  // AI行棋/支招是否被非正常中断（显示"已停止"而非"思考中"）
    private boolean isRedTurn = false; // 当前是否是红方回合
    private int aiThinkingProgress = 0; // AI思考动画进度
    private boolean isSuggestMode = false; // 是否处于支招模式
    private boolean isSimulatingView = false; // 是否处于模拟行棋演示中
    private int simProgress = 0; // 模拟行棋动画进度
    private String suggestMoveText = ""; // 支招走法文本
    private List<String> suggestMoveTexts = null; // 彩色支招文本列表
    private List<Boolean> suggestMoveIsRed = null; // 彩色支招是否红方
    private List<Boolean> suggestMoveIsPlayed = null; // 彩色支招是否已走（置灰）
    private String moveInfoText = ""; // 步数信息文本
    private String bestMoveText = ""; // 最优一步（支招结果核心着法，显示在回合信息条）
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
        if (isSimulatingView) {
            simProgress = (simProgress + 1) % AI_DOT_CYCLE;
        }
        invalidate();
        scheduleNextDotAnimation();
    };

    private Paint backgroundPaint;
    private Paint bgGradPaint; // 回合信息条背景渐变（上亮下暗木纹立体感）
    private Paint tagPaint;           // 通用胶囊底片（第3行提示等）
    private Paint redTextPaint;
    private Paint blackTextPaint; // 黑方回合画笔
    private Paint infoTextPaint; // 模式和评分画笔
    private Paint borderPaint; // 边框画笔（底部信息条）
    private Paint boardBorderPaint; // 棋盘外框专用画笔（加粗）
    private Paint modeTextPaint; // 模式文本画笔
    private Paint aiTextPaint; // 电脑方（AI）文本画笔
    private Paint winBgPaint; // 胜利背景画笔（缓存，避免onDraw中频繁创建）
    private Path scoreBarPath; // 评分条裁剪路径（缓存，避免onDraw中频繁创建）
    private Paint redBarPaint; // 红方进度条画笔（缓存，避免onDraw中频繁创建）
    private Paint blackBarPaint; // 黑方进度条画笔（缓存，避免onDraw中频繁创建）
    private Paint turnPaint; // 行棋方指示图标画笔（红/黑圆点，缓存）
    private Paint glassBasePaint; // 毛玻璃底板（磨砂玻璃，半透明透出背景）
    private Paint glassHiPaint; // 玻璃顶部高光（运行时设置渐变 shader）
    private Paint glassShadowPaint; // 玻璃底部阴影（运行时设置渐变 shader）
    private Paint glassBorderPaint; // 毛玻璃柔光描边（冷色边缘光晕）
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
        // 已被用户中断（显示"已停止"）时，拒绝任何"恢复思考"的写入，
        // 否则后台仍运行的 DepthUpdateRunnable 在 cancel 生效前拿到引擎真实深度后，
        // 会重新 isAIThinking=true 并清掉 isAIStopped，导致"点中断后还是显示思考中"。
        // 下一手 AI 开始时 tryStartAnalyzing 已先 resetAIStopped 清标志，此处才会正常生效。
        if (this.isAIStopped) {
            return;
        }
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
        // 开始思考即清除"已停止"标记，恢复正常思考提示
        if (this.isAIThinking) {
            this.isAIStopped = false;
        }
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
        // 已被用户中断（显示"已停止"）时，不允许任何 markThinking 覆盖回"思考中"，
        // 否则 DepthUpdateRunnable 或下一手启动前的瞬时调用会抹掉中断提示，导致动画残留
        if (this.isAIStopped) {
            return;
        }
        this.isAIThinking = true;
        this.isRedTurn = isRed;
        this.isAIStopped = false; // 正常开始思考，清除停止标记
        if (!isSuggestMode) {
            this.aiThinkingProgress = 0;
        }
        syncDotAnimation();
        postInvalidate();
    }

    // 标记"AI 行棋/支招被非正常中断"：清除思考中的动画状态，并显示"已停止"提示。
    // 是否已真正处于思考由调用方（stopAIAnalysis 中的 wasSearching）判断，
    // 此处直接置位，避免在 runOnUiThread 前状态已被 finishAnalyzing 清除导致漏显
    // （人机模式 isAIThinking 先被清，故必须直接置位；支招模式 isSuggestMode 仍在才显示，
    // 因此统一改为直接置位、由调用方决定是否调用本方法）。
    public void markAIStopped() {
        this.isAIStopped = true;
        this.isAIThinking = false;
        this.aiThinkingProgress = 0;
        this.isSuggestMode = false;
        syncDotAnimation();
        postInvalidate();
    }

    public boolean isAIStopped() {
        return this.isAIStopped;
    }

    public boolean isAIThinking() {
        return this.isAIThinking;
    }

    // 清除"已停止"标记（仅清 isAIStopped，不动其他思考动画状态），
    // 供下一手 AI 正常开始时调用，使 markThinking 能重新显示"思考中"
    public void resetAIStopped() {
        this.isAIStopped = false;
        postInvalidate();
    }

    // 当前是否正处于 AI 思考/支招/加载中（供中断判断使用）
    public boolean isAISearching() {
        return isAIThinking || isSuggestMode || isAILoading;
    }

    // 清除搜索深度与思考状态（用于棋谱导航/加载，避免残留深度与思考动画）
    public void clearSearchState() {
        this.redSearchDepth = 0;
        this.blackSearchDepth = 0;
        this.lastSearchDepth = 0;
        this.pendingFinalDepth = 0;
        this.isAIThinking = false;
        this.isAIStopped = false;
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
    
    // 设置模拟行棋演示状态（回合信息条显示"模拟行棋中"）
    public void setSimulating(boolean simulating) {
        this.isSimulatingView = simulating;
        if (simulating) {
            this.simProgress = 0;
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
        this.suggestMoveIsPlayed = null; // 默认不置灰
        this.suggestMoveText = ""; // 清空普通文本
        invalidate();
    }

    // 设置带颜色的支招走法文本（含已走置灰标记）
    public void setSuggestMoveTextWithColor(List<String> texts, List<Boolean> isRedList, List<Boolean> isPlayedList) {
        this.suggestMoveTexts = texts;
        this.suggestMoveIsRed = isRedList;
        this.suggestMoveIsPlayed = isPlayedList;
        this.suggestMoveText = ""; // 清空普通文本
        invalidate();
    }
    
    // 设置步数信息文本
    public void setMoveInfoText(String infoText) {
        this.moveInfoText = infoText;
        invalidate();
    }

    // 设置最优一步走法文本（用于回合信息条显示核心着法）
    public void setBestMoveText(String text) {
        this.bestMoveText = (text != null) ? text : "";
        // 清空彩色多步文本，确保回合信息条只显示最优一步
        this.suggestMoveText = "";
        this.suggestMoveTexts = null;
        this.suggestMoveIsRed = null;
        invalidate();
    }

    // 当前是否显示最优一步（供点击模拟判断）
    public boolean hasBestMove() {
        return bestMoveText != null && !bestMoveText.isEmpty();
    }

    private void initPaints() {
        backgroundPaint = new Paint();
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(Color.rgb(180, 130, 80));

        // 回合信息条背景：上亮下暗的木纹渐变（立体卡片感），shader 在 onDraw 中按尺寸设置
        bgGradPaint = new Paint();
        bgGradPaint.setStyle(Paint.Style.FILL);
        bgGradPaint.setAntiAlias(true);

        // 通用胶囊底片（第3行 AI/支招提示等）
        tagPaint = new Paint();
        tagPaint.setStyle(Paint.Style.FILL);
        tagPaint.setAntiAlias(true);

        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.rgb(100, 60, 30));
        borderPaint.setStrokeWidth(convertDpToPixel(1.5f, getContext()));
        borderPaint.setAntiAlias(true);

        // 棋盘外框专用画笔（回合信息条外框，加粗以更醒目）
        boardBorderPaint = new Paint();
        boardBorderPaint.setStyle(Paint.Style.STROKE);
        boardBorderPaint.setColor(Color.rgb(100, 60, 30));
        boardBorderPaint.setStrokeWidth(convertDpToPixel(4.5f, getContext()));
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
        redBarPaint.setColor(Color.rgb(150, 22, 20)); // 深红

        redBarPaint.setAlpha(255); // 加深、更实（毛玻璃高光/阴影/柔光边仍保留）
        blackBarPaint = new Paint();
        blackBarPaint.setStyle(Paint.Style.FILL);
        blackBarPaint.setColor(Color.rgb(16, 16, 16)); // 近黑，加深
        blackBarPaint.setAlpha(255); // 加深、更实（毛玻璃高光/阴影/柔光边仍保留）
        blackBarPaint.setAntiAlias(true);

        // 评分条毛玻璃效果相关画笔
        glassBasePaint = new Paint();
        glassBasePaint.setStyle(Paint.Style.FILL);
        glassBasePaint.setAntiAlias(true);
        glassBasePaint.setColor(Color.argb(38, 232, 240, 248)); // 冷白磨砂玻璃底

        glassHiPaint = new Paint();
        glassHiPaint.setStyle(Paint.Style.FILL);
        glassHiPaint.setAntiAlias(true);

        glassShadowPaint = new Paint();
        glassShadowPaint.setStyle(Paint.Style.FILL);
        glassShadowPaint.setAntiAlias(true);

        glassBorderPaint = new Paint();
        glassBorderPaint.setStyle(Paint.Style.STROKE);
        glassBorderPaint.setAntiAlias(true);
        glassBorderPaint.setStrokeWidth(convertDpToPixel(1.6f, getContext()));
        glassBorderPaint.setColor(Color.argb(120, 220, 240, 255)); // 玻璃冷色边缘光晕

        turnPaint = new Paint();
        turnPaint.setAntiAlias(true);
        turnPaint.setStyle(Paint.Style.FILL);
        // 描边用于浅色背景上更醒目（颜色在 onDraw 中按行棋方设置）
        turnPaint.setStrokeWidth(convertDpToPixel(1f, getContext()));
        turnPaint.setStrokeJoin(Paint.Join.ROUND);
        turnPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** 绘制行棋方指示圈：红/黑描边圆 + 圈内对应模式的玩家/电脑图标；
     *  圆圈更小并紧贴图标，当前行棋方那一侧的外圈加粗（active=true） */
    private void drawTurnSideCircle(Canvas canvas, float cx, float cy, boolean active,
                                    float iconSize, float r, int ringColor, int side) {
        if (turnPaint == null) return;
        float density = getResources().getDisplayMetrics().density;
        if (active) {
            // 行棋方：实心填充（同色径向渐变，更有质感）+ 金色柔光描边
            int fillLight = side == ModeIconDrawable.SIDE_LEFT
                    ? Color.rgb(214, 70, 60) : Color.rgb(70, 70, 80);
            int fillDark = side == ModeIconDrawable.SIDE_LEFT
                    ? Color.rgb(150, 28, 22) : Color.rgb(20, 20, 26);
            android.graphics.RadialGradient rg = new android.graphics.RadialGradient(
                    cx - r * 0.3f, cy - r * 0.3f, r * 1.5f, fillLight, fillDark,
                    android.graphics.Shader.TileMode.CLAMP);
            turnPaint.setStyle(Paint.Style.FILL);
            turnPaint.setShader(rg);
            turnPaint.setStrokeWidth(0);
            canvas.drawCircle(cx, cy, r, turnPaint);
            turnPaint.setShader(null);
            // 金色柔光描边
            turnPaint.setStyle(Paint.Style.STROKE);
            turnPaint.setStrokeWidth(convertDpToPixel(2.2f, getContext()));
            turnPaint.setColor(side == ModeIconDrawable.SIDE_LEFT
                    ? Color.rgb(255, 214, 120) : Color.rgb(200, 205, 215));
            canvas.drawCircle(cx, cy, r, turnPaint);
        } else {
            // 非行棋方：仅细圈（弱化）
            turnPaint.setStyle(Paint.Style.STROKE);
            turnPaint.setStrokeWidth(convertDpToPixel(1.6f, getContext()));
            turnPaint.setColor(ringColor);
            canvas.drawCircle(cx, cy, r, turnPaint);
        }
        // 圈内：固定尺寸的模式图标，紧贴外圈
        ModeIconDrawable d = new ModeIconDrawable(getContext(), gameMode, density, side,
                active ? Color.WHITE : Color.rgb(150, 150, 150));
        int s = (int) iconSize;
        d.setBounds((int) (cx - s / 2f), (int) (cy - s / 2f),
                (int) (cx + s / 2f), (int) (cy + s / 2f));
        d.draw(canvas);
    }

    /** 仅绘制行棋方模式图标（不画外圈），用于非行棋方弱化指示 */
    private void drawTurnSideIcon(Canvas canvas, float cx, float cy, float iconSize, int side, int color) {
        float density = getResources().getDisplayMetrics().density;
        ModeIconDrawable d = new ModeIconDrawable(getContext(), gameMode, density, side, color);
        int s = (int) iconSize;
        d.setBounds((int) (cx - s / 2f), (int) (cy - s / 2f),
                (int) (cx + s / 2f), (int) (cy + s / 2f));
        d.draw(canvas);
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

    /** 绘制一条居中提示文字，并在其后方叠加半透明圆角胶囊底片，使提示更醒目 */
    private void drawCenteredChipText(Canvas canvas, float width, float y, float textSize,
                                      String text, int chipColor) {
        infoTextPaint.setTextSize(textSize);
        infoTextPaint.setTextAlign(Paint.Align.CENTER);
        float tw = infoTextPaint.measureText(text);
        float padX = convertDpToPixel(10, getContext());
        float chipH = convertDpToPixel(21, getContext());
        float chipW = tw + padX * 2;
        float cx = width / 2;
        android.graphics.RectF chip = new android.graphics.RectF(
                cx - chipW / 2f, y - chipH * 0.74f, cx + chipW / 2f, y + chipH * 0.74f);
        tagPaint.setColor(chipColor);
        canvas.drawRoundRect(chip, chipH * 0.5f, chipH * 0.5f, tagPaint);
        canvas.drawText(text, cx, y, infoTextPaint);
    }

    private boolean shouldAnimateDots() {
        return isSuggestMode || isAILoading || isAIThinking || isSimulatingView;
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
        
        // 绘制背景：上亮下暗木纹渐变（立体卡片感），并叠加顶部高光、底部暗边
        // 每次按当前尺寸设置 shader（尺寸不变时重复设置开销极小，且避免缓存失效问题）
        android.graphics.LinearGradient bgGrad = new android.graphics.LinearGradient(
                0, 0, 0, height,
                Color.rgb(201, 150, 96),   // 顶部亮木色
                Color.rgb(150, 104, 62),   // 底部暗木色
                android.graphics.Shader.TileMode.CLAMP);
        bgGradPaint.setShader(bgGrad);
        canvas.drawRect(0, 0, width, height, bgGradPaint);

        // 顶部高光条（玻璃反光质感）
        android.graphics.LinearGradient topHi = new android.graphics.LinearGradient(
                0, 0, 0, convertDpToPixel(18, getContext()),
                Color.argb(70, 255, 248, 235), Color.argb(0, 255, 248, 235),
                android.graphics.Shader.TileMode.CLAMP);
        Paint topHiPaint = new Paint();
        topHiPaint.setStyle(Paint.Style.FILL);
        topHiPaint.setShader(topHi);
        topHiPaint.setAntiAlias(true);
        canvas.drawRect(0, 0, width, convertDpToPixel(18, getContext()), topHiPaint);

        // 底部暗边（增强厚度/立体感）
        android.graphics.LinearGradient botSh = new android.graphics.LinearGradient(
                0, height - convertDpToPixel(16, getContext()), 0, height,
                Color.argb(0, 0, 0, 0), Color.argb(55, 40, 24, 10),
                android.graphics.Shader.TileMode.CLAMP);
        Paint botShPaint = new Paint();
        botShPaint.setStyle(Paint.Style.FILL);
        botShPaint.setShader(botSh);
        botShPaint.setAntiAlias(true);
        canvas.drawRect(0, height - convertDpToPixel(16, getContext()), width, height, botShPaint);
        
        // 绘制边框 - 使用dp单位确保不同屏幕一致性，颜色随行棋方变化
        float borderPadding = convertDpToPixel(3, getContext());
        float cornerRadius = convertDpToPixel(11, getContext());
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
        float paddingTop = convertDpToPixel(6, getContext());
        float lineHeight = convertDpToPixel(20, getContext());

        // ========== 行坐标（紧凑三行，随高度增加均匀拉开上下间距，排版更舒展） ==========
        float formY = paddingTop + convertDpToPixel(15, getContext());   // 第1行：回合 / 形势 / 深度
        float row2Y = paddingTop + convertDpToPixel(43, getContext());   // 第2行：时间 + 阵营图标 + 评分条
        float row3Y = paddingTop + convertDpToPixel(69, getContext());   // 第3行：AI思考 / 支招 / 步数


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
        
        // 红方时间（左侧）：纯文字，无背景胶囊
        float redTimeX = convertDpToPixel(12, getContext());
        redTextPaint.setTextSize(convertDpToPixel(13, getContext()));
        redTextPaint.setTextAlign(Paint.Align.LEFT);
        redTextPaint.setFakeBoldText(true);
        String redText = formatTime(redTime);
        float redTimeW = redTextPaint.measureText(redText);
        canvas.drawText(redText, redTimeX, row2Y, redTextPaint);

        // 黑方时间（右侧）：纯文字，无背景胶囊
        blackTextPaint.setTextSize(convertDpToPixel(13, getContext()));
        blackTextPaint.setTextAlign(Paint.Align.RIGHT);
        blackTextPaint.setFakeBoldText(true);
        String blackText = formatTime(blackTime);
        float blackRightX = width - convertDpToPixel(12, getContext());
        float blackTimeW = blackTextPaint.measureText(blackText);
        canvas.drawText(blackText, blackRightX, row2Y, blackTextPaint);

        // 评分条：左右两端分别贴近红/黑时间文字，填满两者之间的空隙（左半红、右半黑对应两侧）
        float barGap = convertDpToPixel(10, getContext());
        float barX = redTimeX + redTimeW + barGap;
        float barEnd = blackRightX - blackTimeW - barGap;
        float barWidth = Math.max(convertDpToPixel(60, getContext()), barEnd - barX);
        drawScoreBar(canvas, barX, barWidth, row2Y, moveScore);

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
        // 中：形势/分数（始终居中，字号比回合、深度更大，突出评分，带柔光）
        infoTextPaint.setTextSize(convertDpToPixel(16, getContext()));
        infoTextPaint.setColor(formColor);
        infoTextPaint.setTextAlign(Paint.Align.CENTER);
        infoTextPaint.setShadowLayer(convertDpToPixel(3, getContext()), 0, convertDpToPixel(1, getContext()),
                Color.argb(90, 255, 240, 200));
        canvas.drawText(formStr, width / 2, formY + convertDpToPixel(3, getContext()), infoTextPaint);
        infoTextPaint.setShadowLayer(0, 0, 0, 0);
        // 行棋方指示：居中"形势/分数"文字左右各一个红/黑圈，圈更小并紧贴圈内模式图标；
        // 当前行棋方那一侧的外圈加粗（突出轮到谁走）。原来的单个红黑点已移除。
        float formTextW = infoTextPaint.measureText(formStr);
        float formCx = width / 2;
        float formTextLeft = formCx - formTextW / 2;
        float formTextRight = formCx + formTextW / 2;
        float cyMark = formY + convertDpToPixel(3, getContext()) - convertDpToPixel(4.5f, getContext()); // 头标圆心对齐分数文字的视觉中心
        boolean redGo = chessInfo.IsRedGo;
        float iconSize = convertDpToPixel(16, getContext());  // 圈内玩家图标直径（固定）
        float aiIconSize = convertDpToPixel(19, getContext()); // 机器人图标略放大，抵消其图形留白，与玩家图标视觉一致
        float r = convertDpToPixel(10, getContext());          // 圈半径：比图标略大，紧贴图标
        float turnGap = convertDpToPixel(12, getContext());        // 文字与圆圈之间的留白
        // 行棋方：加圈 + 模式图标；非行棋方：不加圈，仅显示（弱化）模式图标
        float redCx = formTextLeft - turnGap - r;
        float blackCx = formTextRight + turnGap + r;
        // 判断两侧是否为电脑（机器人）图标，是则放大绘制
        boolean leftIsAI = (gameMode == 2 || gameMode == 3);
        boolean rightIsAI = (gameMode == 1 || gameMode == 3);
        float redSize = leftIsAI ? aiIconSize : iconSize;
        float blackSize = rightIsAI ? aiIconSize : iconSize;
        if (redGo) {
            drawTurnSideCircle(canvas, redCx, cyMark, true, redSize, r,
                    Color.rgb(214, 60, 56), ModeIconDrawable.SIDE_LEFT);
            drawTurnSideIcon(canvas, blackCx, cyMark, blackSize, ModeIconDrawable.SIDE_RIGHT, Color.rgb(120, 120, 120));
        } else {
            drawTurnSideCircle(canvas, blackCx, cyMark, true, blackSize, r,
                    Color.rgb(35, 35, 35), ModeIconDrawable.SIDE_RIGHT);
            drawTurnSideIcon(canvas, redCx, cyMark, redSize, ModeIconDrawable.SIDE_LEFT, Color.rgb(120, 120, 120));
        }
        // 右：深度（有值时右对齐固定槽位，消失也不影响其他两段），纯文字无背景
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
        boolean hasAIOrSuggestInfo = hasSuggest || isAILoading || isShowLoadingComplete || isAIThinking || isSuggestMode
                || (bestMoveText != null && !bestMoveText.isEmpty()) || isSimulatingView;
        
        // 绘制AI加载中、加载完成、支招思考或AI走棋思考动画（统一加上半透明冷色胶囊底片更醒目）
        float aiTextSize = convertDpToPixel(14, getContext());
        infoTextPaint.setColor(Color.rgb(150, 205, 255)); // AI/支招提示统一醒目蓝

        // 辅助：绘制一条居中提示文字（带冷色胶囊底片）
        // 注意：Java 不支持闭包捕获可变局部变量，这里用独立代码块逐条处理
        // 第3行提示统一蓝色，已设好 infoTextPaint.setColor；每条绘制前画胶囊
        if (isSuggestMode) {
            String t = "AI思考中" + buildDotSuffix(aiThinkingProgress);
            drawCenteredChipText(canvas, width, currentY, aiTextSize, t, Color.argb(46, 70, 130, 200));
            currentY += lineHeight;
        } else if (!hasSuggest) {
            if (isAILoading) {
                String t = "AI加载中" + buildDotSuffix(aiLoadingProgress);
                drawCenteredChipText(canvas, width, currentY, aiTextSize, t, Color.argb(46, 70, 130, 200));
                currentY += lineHeight;
            } else if (isShowLoadingComplete) {
                drawCenteredChipText(canvas, width, currentY, aiTextSize, "AI加载完成！", Color.argb(46, 70, 130, 200));
                currentY += lineHeight;
            } else if (isAIThinking) {
                String t = "AI思考中" + buildDotSuffix(aiThinkingProgress);
                drawCenteredChipText(canvas, width, currentY, aiTextSize, t, Color.argb(46, 70, 130, 200));
                currentY += lineHeight;
            } else if (isAIStopped) {
                // AI 行棋被非正常中断：提示已停止，而非残留"思考中"。
                // 背景与其他 AI 提示保持一致的蓝色胶囊底；文字用警示橙红色区分。
                infoTextPaint.setColor(Color.rgb(255, 150, 130));
                drawCenteredChipText(canvas, width, currentY, aiTextSize, "AI停止思考",
                        Color.argb(46, 70, 130, 200));
                currentY += lineHeight;
            }
        }

        // 显示支招走法信息（支招思考或AI走棋思考时不显示，避免覆盖）
        if (!isSuggestMode && !isAIThinking && suggestMoveText != null && !suggestMoveText.isEmpty()) {
            String t = "支招: " + suggestMoveText;
            drawCenteredChipText(canvas, width, currentY, convertDpToPixel(13, getContext()), t,
                    Color.argb(40, 70, 130, 200));
            currentY += lineHeight;
        }
        
        // 显示最优一步（支招结果的核心着法，显示在回合信息条，可点击模拟行棋）
        // 沿用原支招彩色显示模式：按行棋方着色（红方红、黑方黑），不加"最优"前缀
        if (!isSuggestMode && !isAIThinking && bestMoveText != null && !bestMoveText.isEmpty()) {
            float bestTextSize = infoTextPaint.getTextSize();
            boolean bestFakeBold = infoTextPaint.isFakeBoldText();
            infoTextPaint.setColor(chessInfo.IsRedGo ? Color.RED : Color.BLACK);
            infoTextPaint.setTextSize(convertDpToPixel(15, getContext()));
            infoTextPaint.setTextAlign(Paint.Align.CENTER);
            infoTextPaint.setFakeBoldText(true);
            canvas.drawText(bestMoveText, width / 2, currentY, infoTextPaint);
            currentY += lineHeight;
            infoTextPaint.setTextSize(bestTextSize);
            infoTextPaint.setFakeBoldText(bestFakeBold);
        }

        // 显示彩色支招走法信息（支招思考或AI走棋思考时不显示，避免覆盖）
        if (!isSuggestMode && !isAIThinking && suggestMoveTexts != null && !suggestMoveTexts.isEmpty() && suggestMoveIsRed != null) {
            float originalTextSize = infoTextPaint.getTextSize();
            boolean originalFakeBold = infoTextPaint.isFakeBoldText();
            float normalSize = convertDpToPixel(13, getContext());
            infoTextPaint.setTextAlign(Paint.Align.LEFT);
            
            float gap = convertDpToPixel(3, getContext());
            float sidePadX = convertDpToPixel(8, getContext());
            float availWidth = Math.max(0, width - 2 * sidePadX);
            float drawSize = normalSize;
            // 先按标准字号测量总宽度
            float totalWidth = 0;
            for (int i = 0; i < suggestMoveTexts.size() && i < suggestMoveIsRed.size(); i++) {
                infoTextPaint.setTextSize(normalSize);
                totalWidth += infoTextPaint.measureText(suggestMoveTexts.get(i));
                if (i < suggestMoveTexts.size() - 1) {
                    totalWidth += gap;
                }
            }
            // 若整条支招超出可用宽度，等比缩小字号（设下限，保证可读），
            // 使完整变线在一行内全部可见，避免被画布裁掉。
            if (totalWidth > availWidth && totalWidth > 0) {
                drawSize = Math.max(convertDpToPixel(9, getContext()), normalSize * availWidth / totalWidth);
                infoTextPaint.setTextSize(drawSize);
                float measured = 0;
                for (int i = 0; i < suggestMoveTexts.size() && i < suggestMoveIsRed.size(); i++) {
                    measured += infoTextPaint.measureText(suggestMoveTexts.get(i));
                    if (i < suggestMoveTexts.size() - 1) {
                        measured += gap;
                    }
                }
                totalWidth = measured;
            }
            float startX = (width - totalWidth) / 2;

            float x = startX;
            for (int i = 0; i < suggestMoveTexts.size() && i < suggestMoveIsRed.size(); i++) {
                String text = suggestMoveTexts.get(i);
                boolean isRed = suggestMoveIsRed.get(i);
                boolean isPlayed = (suggestMoveIsPlayed != null && i < suggestMoveIsPlayed.size())
                        ? suggestMoveIsPlayed.get(i) : false;

                if (isPlayed) {
                    // 已走步骤：与棕底协调的柔和暖灰，弱化但不刺眼
                    infoTextPaint.setColor(Color.rgb(206, 190, 168));
                } else if (isRed) {
                    infoTextPaint.setColor(Color.RED);
                } else {
                    infoTextPaint.setColor(Color.BLACK);
                }

                infoTextPaint.setTextSize(drawSize);
                infoTextPaint.setFakeBoldText(false);
                canvas.drawText(text, x, currentY, infoTextPaint);
                x += infoTextPaint.measureText(text) + gap;
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
            height = (int) convertDpToPixel(84, getContext()); // 紧凑三行：形势/时间评分条/AI提示（较原 74dp 略增高）
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
    
    private void drawScoreBar(Canvas canvas, float barX, float barWidth, float centerY, int score) {
        float barHeight = convertDpToPixel(14, getContext());
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
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, glassBorderPaint); // 毛玻璃柔光描边

            infoTextPaint.setTextSize(convertDpToPixel(12, getContext()));
            infoTextPaint.setTextAlign(Paint.Align.CENTER);
            infoTextPaint.setFakeBoldText(true);
            infoTextPaint.setColor(winColor);
            infoTextPaint.setShadowLayer(convertDpToPixel(2f, getContext()), 0, 0, Color.argb(100, 255, 255, 255));
            canvas.drawText(winText, barX + barWidth / 2, centerY - convertDpToPixel(3, getContext()) + convertDpToPixel(4, getContext()), infoTextPaint);
            
            infoTextPaint.clearShadowLayer();
            infoTextPaint.setColor(Color.rgb(255, 250, 240));
            return;
        }
        
        scoreBarPath.reset();
        scoreBarPath.addRoundRect(barRect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW);

        // 1) 磨砂玻璃底板（半透明，透出背景形成磨砂质感）
        canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, glassBasePaint);

        canvas.save();
        canvas.clipPath(scoreBarPath);

        float maxScore = 1000f;
        float scoreRatio = Math.abs(score) / maxScore;
        if (scoreRatio > 1) scoreRatio = 1;

        float centerX = barX + barWidth / 2;
        float totalRange = barWidth / 2;

        // redBarPaint / blackBarPaint 已在 initPaints() 中初始化缓存（半透明磨砂）

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

        // 2) 顶部高光（玻璃反光：顶亮 → 中透明）
        glassHiPaint.setShader(new android.graphics.LinearGradient(
                0, barY, 0, barY + barHeight,
                Color.argb(130, 255, 255, 255), Color.argb(0, 255, 255, 255),
                android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(barRect.left, barY, barRect.right, barY + barHeight, glassHiPaint);
        glassHiPaint.setShader(null);

        // 3) 底部阴影（玻璃厚度 / 立体感）
        glassShadowPaint.setShader(new android.graphics.LinearGradient(
                0, barY + barHeight * 0.5f, 0, barY + barHeight,
                Color.argb(0, 0, 0, 0), Color.argb(70, 0, 0, 0),
                android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(barRect.left, barY + barHeight * 0.5f, barRect.right, barY + barHeight, glassShadowPaint);
        glassShadowPaint.setShader(null);

        canvas.restore();

        // 4) 毛玻璃柔光描边（冷色边缘光晕）+ 原行棋方外框
        canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, glassBorderPaint);
        canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, borderPaint);
        
        infoTextPaint.clearShadowLayer();
        infoTextPaint.setColor(Color.rgb(255, 250, 240));
    }
}
