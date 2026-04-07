package CustomView;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

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
    private String moveInfoText = ""; // 步数信息文本

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
        invalidate();
    }
    
    // 设置步数信息文本
    public void setMoveInfoText(String infoText) {
        this.moveInfoText = infoText;
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
        
        // 避免频繁计算和绘制，使用缓存的视图尺寸
        int width = getWidth();
        int height = getHeight();
        
        // 绘制背景
        canvas.drawRect(0, 0, width, height, backgroundPaint);
        
        // 绘制边框
        android.graphics.RectF rectF = new android.graphics.RectF(5, 5, width - 5, height - 5);
        canvas.drawRoundRect(rectF, 10, 10, borderPaint);
        
        // 计算垂直间距，调整为更美观的布局
        float paddingTop = convertDpToPixel(4, getContext());
        float lineHeight = convertDpToPixel(24, getContext());
        
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
        
        // 检查游戏状态
        boolean isGameOver = chessInfo.status == 2;
        
        // 检查是否有一方被将死
        boolean redCheckmated = Rule.isCheckmate(chessInfo.piece, true);
        boolean blackCheckmated = Rule.isCheckmate(chessInfo.piece, false);
        
        // 检查是否有一方被困毙
        boolean redStalemated = Rule.isStalemate(chessInfo.piece, true);
        boolean blackStalemated = Rule.isStalemate(chessInfo.piece, false);
        
        // 检查是否是和棋
        boolean isDraw = isGameOver && !redCheckmated && !blackCheckmated && !redStalemated && !blackStalemated && redKingExists && blackKingExists;
        
        // 优先显示王被吃掉的情况
        if (!redKingExists) {
            scoreText = "黑方胜利！";
        } else if (!blackKingExists) {
            scoreText = "红方胜利！";
        } else if (isDraw) {
            // 和棋
            scoreText = "和棋！";
        } else if (isGameOver || redCheckmated || blackCheckmated || redStalemated || blackStalemated) {
            // 游戏结束或被将死或被困毙，根据情况判断胜利者
            if (redCheckmated || redStalemated) {
                scoreText = "黑方胜利！";
            } else if (blackCheckmated || blackStalemated) {
                scoreText = "红方胜利！";
            } else {
                // 游戏结束，根据行棋方判断胜利者
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
                    // 差异较小时直接设置
                    moveScore = targetMoveScore;
                } else {
                    // 差异较大时使用平滑过渡
                    moveScore += diff / 5;
                }
                // 触发下一次重绘，增加延迟时间
                postInvalidateDelayed(100);
            }
            
            // 显示绝对值并添加红方或黑方前缀
            if (moveScore > 0) {
                scoreText = "红方:" + moveScore;
            } else if (moveScore < 0) {
                scoreText = "黑方:" + Math.abs(moveScore);
            } else {
                scoreText = "0";
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
        
        // 红方时间（左半部分居中）
        float redCenterX = width * 1 / 3;
        // 绘制时间
        redTextPaint.setTextAlign(Paint.Align.CENTER);
        String redText = formatTime(redTime);
        canvas.drawText(redText, redCenterX, textY, redTextPaint);
        
        // 显示当前行棋方（中间位置）
        float turnCenterX = width * 1 / 2;
        String turnText = chessInfo.IsRedGo ? "红方" : "黑方";
        // 使用相应的颜色
        Paint turnPaint = chessInfo.IsRedGo ? redTextPaint : blackTextPaint;
        turnPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(turnText, turnCenterX, textY, turnPaint);
        
        // 黑方时间（右半部分居中）
        float blackCenterX = width * 2 / 3;
        // 绘制时间
        blackTextPaint.setTextAlign(Paint.Align.CENTER);
        String blackText = formatTime(blackTime);
        canvas.drawText(blackText, blackCenterX, textY, blackTextPaint);
        
        // 绘制AI思考提示（在时间行下面）
        boolean shouldShowAIInfo = false;
        int currentDepth = 0;
        
        // 检查是否应该显示AI信息
        if (isSuggestMode) {
            // 支招模式：总是显示
            shouldShowAIInfo = true;
            // 支招模式下，使用当前行棋方的深度
            currentDepth = chessInfo.IsRedGo ? redSearchDepth : blackSearchDepth;
        } else if (gameMode == 1) {
            // 人机对战（玩家红）：显示黑方（AI）的深度
            shouldShowAIInfo = true;
            // 显示黑方的深度
            currentDepth = blackSearchDepth;
        } else if (gameMode == 2) {
            // 人机对战（玩家黑）：显示红方（AI）的深度
            shouldShowAIInfo = true;
            // 显示红方的深度
            currentDepth = redSearchDepth;
        } else if (gameMode == 3) {
            // 双机对战：总是显示
            shouldShowAIInfo = true;
            // 双机对战下，使用AI的颜色对应的深度
            currentDepth = isRedTurn ? redSearchDepth : blackSearchDepth;
        } else if (gameMode == 0) {
            // 双人模式：只在有深度信息时显示
            // 检查是否有深度信息
            int redDepth = redSearchDepth;
            int blackDepth = blackSearchDepth;
            if (redDepth > 0 || blackDepth > 0) {
                shouldShowAIInfo = true;
                // 双人模式下，使用当前行棋方的深度
                currentDepth = chessInfo.IsRedGo ? redDepth : blackDepth;
            } else {
                shouldShowAIInfo = false;
            }
        }
        
        // 额外检查：如果有深度信息且不是双人模式，强制显示深度
        if (!shouldShowAIInfo && gameMode != 0) {
            // 检查是否有深度信息
            int redDepth = redSearchDepth;
            int blackDepth = blackSearchDepth;
            if (redDepth > 0 || blackDepth > 0) {
                shouldShowAIInfo = true;
                // 根据游戏模式选择显示哪个深度
                if (gameMode == 1) {
                    currentDepth = blackDepth;
                } else if (gameMode == 2) {
                    currentDepth = redDepth;
                } else if (gameMode == 3) {
                    currentDepth = isRedTurn ? redDepth : blackDepth;
                } else if (isSuggestMode) {
                    currentDepth = chessInfo.IsRedGo ? redDepth : blackDepth;
                }
            }
        }
        
        // 检查是否有深度信息需要显示
        shouldShowAIInfo = shouldShowAIInfo && currentDepth > 0;
        
        // 计算文本位置
        float aiTextY = textY + lineHeight;
        float currentY = aiTextY;
        
        // 检查是否有AI信息或支招信息
        boolean hasAIOrSuggestInfo = shouldShowAIInfo || (suggestMoveText != null && !suggestMoveText.isEmpty());
        
        // 绘制AI信息
        if (shouldShowAIInfo) {
            infoTextPaint.setTextAlign(Paint.Align.CENTER);
            
            String aiText = "";
            
            // 如果AI正在思考，显示思考提示
            if (isAIThinking) {
                String dots = "";
                for (int i = 0; i < aiThinkingProgress; i++) {
                    dots += ".";
                }
                aiText = "AI正在思考" + dots;
                // 添加行棋层数，用竖线分开
                aiText += " | 深度: " + currentDepth;
            } else {
                // AI思考完成，只显示深度信息
                aiText = "深度: " + currentDepth;
            }
            
            // 绘制文本
            canvas.drawText(aiText, width / 2, currentY, infoTextPaint);
            
            // 调整当前位置
            currentY += lineHeight;
        }
        
        // 显示支招走法信息
        if (suggestMoveText != null && !suggestMoveText.isEmpty()) {
            infoTextPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("支招: " + suggestMoveText, width / 2, currentY, infoTextPaint);
            currentY += lineHeight;
        }
        
        // 只在没有AI信息和支招信息时显示步数信息
        if (!hasAIOrSuggestInfo && moveInfoText != null && !moveInfoText.isEmpty()) {
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
        
        // 计算高度
        int height;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            height = MeasureSpec.getSize(heightMeasureSpec);
        } else {
            // 使用dp单位计算高度，确保在不同屏幕密度下显示正确
            height = (int) convertDpToPixel(130, getContext()); // 调整高度，适应更大的行间距
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
