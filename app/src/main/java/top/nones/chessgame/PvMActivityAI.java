package top.nones.chessgame;

import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import Info.ChessInfo;
import Info.Pos;
import ChessMove.Rule;
import ChessMove.Move;
import AICore.PikafishAI;
import CustomView.ChessView;
import CustomView.RoundView;
import top.nones.chessgame.R;

public class PvMActivityAI {
    private PvMActivity activity;
    private int aiRetryCount = 0;
    private final Object aiAnalysisLock = new Object();
    private volatile boolean isAIAnalyzing = false;
    private int dotCount = 0;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private java.util.concurrent.ScheduledFuture<?> depthUpdateFuture;
    
    public PvMActivityAI(PvMActivity activity) {
        this.activity = activity;
    }
    
    // 计算AI最佳移动
    public Move calculateAIMove() {
        return calculateAIMoveWithDepthUpdate();
    }
    
    // 计算AI最佳移动，带有深度更新
    public Move calculateAIMoveWithDepthUpdate() {
        // 显示AI开始搜索的信息，包含深度
        boolean isRed = this.activity != null && this.activity.chessInfo != null && this.activity.chessInfo.IsRedGo;
        
        // 开始AI回合计时
        if (this.activity != null) {
            this.activity.startTurnTimer();
        }
        
        startAISearch(isRed);
        
        if (this.activity == null || this.activity.chessInfo == null || this.activity.pikafishAI == null || !this.activity.pikafishAI.isInitialized() || this.activity.chessInfo.piece == null) {
            return null;
        }
        
        // 验证棋盘状态的有效性
        if (this.activity.chessInfo.piece.length != 10) {
            return null;
        }
        
        for (int i = 0; i < 10; i++) {
            if (this.activity.chessInfo.piece[i] == null || this.activity.chessInfo.piece[i].length != 9) {
                return null;
            }
        }
        
        // 检查将（帅）是否存在
        boolean redKingExists = false;
        boolean blackKingExists = false;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (this.activity.chessInfo.piece[i][j] == 1) { // 黑将
                    blackKingExists = true;
                } else if (this.activity.chessInfo.piece[i][j] == 8) { // 红帅
                    redKingExists = true;
                }
            }
        }
        
        // 如果游戏已经结束，返回 null
        if (!redKingExists || !blackKingExists) {
            return null;
        }
        
        // 检查是否被将死
        boolean isCurrentPlayerRed = this.activity.chessInfo.IsRedGo;
        
        if (Rule.isDead(this.activity.chessInfo.piece, isCurrentPlayerRed)) {
            return null;
        }
        
        // 检查是否有可移动的棋子
        boolean hasValidMoves = false;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                int piece = this.activity.chessInfo.piece[i][j];
                if (piece != 0) {
                    boolean pieceIsRed = piece >= 8 && piece <= 14;
                    if (pieceIsRed == isCurrentPlayerRed) {
                        List<Pos> possibleMoves = Rule.PossibleMoves(this.activity.chessInfo.piece, j, i, piece);
                        if (!possibleMoves.isEmpty()) {
                            hasValidMoves = true;
                            break;
                        }
                    }
                }
            }
            if (hasValidMoves) break;
        }
        
        if (!hasValidMoves) {
            return null;
        }
        
        // 使用PikafishAI获取最佳走法和评分
        PikafishAI.MoveWithScore moveWithScore = this.activity.pikafishAI.getBestMoveWithScore(this.activity.chessInfo);
        if (moveWithScore == null) {
            return null;
        }
        Move move = moveWithScore.move;
        int score = moveWithScore.score;
        
        // 更新评分显示
        if (this.activity.roundView != null) {
            this.activity.roundView.setMoveScore(score);
        }
        
        // 验证移动的有效性
        if (move != null) {
            Pos fromPos = move.fromPos;
            Pos toPos = move.toPos;
            if (fromPos == null || toPos == null) {
                return null;
            }
            if (fromPos.x < 0 || fromPos.x >= 9 || fromPos.y < 0 || fromPos.y >= 10 || toPos.x < 0 || toPos.x >= 9 || toPos.y < 0 || toPos.y >= 10) {
                return null;
            }
            
            // 检查起始位置是否有棋子
            int piece = this.activity.chessInfo.piece[fromPos.y][fromPos.x];
            if (piece == 0) {
                return null;
            }
            
            // 验证棋子颜色是否正确，防止AI走对方的棋子
            boolean pieceIsRed = piece >= 8 && piece <= 14;
            
            if (pieceIsRed != isRed) {
                return null;
            }
            
            // 验证移动是否合法
            List<Pos> possibleMoves = Rule.PossibleMoves(this.activity.chessInfo.piece, fromPos.x, fromPos.y, piece);
            if (!possibleMoves.contains(toPos)) {
                return null;
            }
        }
        
        // 中断AI搜索
        if (this.activity != null && this.activity.pikafishAI != null && this.activity.pikafishAI.isInitialized()) {
            this.activity.pikafishAI.interrupt();
        }
        
        // 停止AI搜索动画
        stopAISearch();
        
        return move;
    }
    
    // 执行AI移动
    public boolean executeAIMove(Move move) {
        if (this.activity == null || this.activity.chessInfo == null || this.activity.chessInfo.piece == null) {
            return false;
        }
        
        // 检查将（帅）是否存在
        boolean redKingExists = false;
        boolean blackKingExists = false;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (this.activity.chessInfo.piece[i][j] == 1) { // 黑将
                    blackKingExists = true;
                } else if (this.activity.chessInfo.piece[i][j] == 8) { // 红帅
                    redKingExists = true;
                }
            }
        }
        
        // 如果游戏已经结束，显示相应的提示信息
        if (!redKingExists) {
            if (this.activity != null) {
                Toast.makeText(this.activity, "红方胜利！黑将被吃掉了", Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        if (!blackKingExists) {
            if (this.activity != null) {
                Toast.makeText(this.activity, "黑方胜利！红帅被吃掉了", Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        
        if (move == null) {
            // 检查AI是否被将死
            if (this.activity.chessInfo.IsRedGo) {
                if (Rule.isDead(this.activity.chessInfo.piece, true)) {
                    if (this.activity != null) {
                        Toast.makeText(this.activity, "红方被将死！黑方胜利", Toast.LENGTH_SHORT).show();
                    }
                    return false;
                }
            } else {
                if (Rule.isDead(this.activity.chessInfo.piece, false)) {
                    if (this.activity != null) {
                        Toast.makeText(this.activity, "黑方被将死！红方胜利", Toast.LENGTH_SHORT).show();
                    }
                    return false;
                }
            }
            return false;
        }
        
        Pos fromPos = move.fromPos;
        Pos toPos = move.toPos;
        
        // 检查移动的合法性
        if (fromPos == null || toPos == null) {
            return false;
        }
        
        if (fromPos.x < 0 || fromPos.x >= 9 || fromPos.y < 0 || fromPos.y >= 10 || toPos.x < 0 || toPos.x >= 9 || toPos.y < 0 || toPos.y >= 10) {
            return false;
        }
        
        // 检查起始位置是否有棋子
        if (this.activity.chessInfo.piece[fromPos.y][fromPos.x] == 0) {
            return false;
        }
        
        int tmp = this.activity.chessInfo.piece[toPos.y][toPos.x];
        int piece = this.activity.chessInfo.piece[fromPos.y][fromPos.x];
        boolean isRed = piece >= 8 && piece <= 14;
        
        // 检查棋子颜色是否与当前回合匹配，防止AI走对方的棋子
        if (isRed != this.activity.chessInfo.IsRedGo) {
            return false;
        }
        
        // 检查移动是否合法
        List<Pos> possibleMoves = Rule.PossibleMoves(this.activity.chessInfo.piece, fromPos.x, fromPos.y, piece);
        if (!possibleMoves.contains(toPos)) {
            return false;
        }
        
        // 执行移动
        this.activity.chessInfo.piece[toPos.y][toPos.x] = piece;
        this.activity.chessInfo.piece[fromPos.y][fromPos.x] = 0;
        this.activity.chessInfo.IsChecked = Rule.isKingDanger(this.activity.chessInfo.piece, !isRed);
        this.activity.chessInfo.Select = new int[]{-1, -1};
        this.activity.chessInfo.ret.clear();
        this.activity.chessInfo.prePos = fromPos;
        this.activity.chessInfo.curPos = toPos;
        
        // 生成并记录标准象棋记谱走法
        String moveString = this.activity.generateMoveString(this.activity.chessInfo, piece, fromPos, toPos, isRed);
        if (moveString != null) {
            Utils.LogUtils.i("Move", "AI走棋: " + moveString);
        }
        
        // 停止AI回合计时（在updateAllInfo之前调用，确保获取正确的行棋方）
        this.activity.stopTurnTimer();
        
        // 更新游戏信息
        this.activity.chessInfo.updateAllInfo(this.activity.chessInfo.prePos, this.activity.chessInfo.curPos, this.activity.chessInfo.piece[toPos.y][toPos.x], tmp);
        this.activity.chessInfo.isMachine = true; // 标记为AI移动
        
        // AI移动也添加到栈中，这样保存棋谱时能包含AI的走法记录
        try {
            this.activity.infoSet.pushInfo(this.activity.chessInfo);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        
        // AI移动后重新绘制界面
        if (this.activity.chessView != null) {
            this.activity.chessView.requestDraw();
        }
        if (this.activity.roundView != null) {
            this.activity.roundView.requestDraw();
        }
        
        // 增加继续对局后的回合计数器
        this.activity.continueGameRoundCount++;
        
        // 开始对方的回合计时
        this.activity.startTurnTimer();
        
        // 检查游戏状态
        if (this.activity.controlsManager != null) {
            this.activity.controlsManager.checkGameStatus(isRed);
        }
        
        // 停止深度更新动画
        stopAISearch();
        
        // 双机对战模式下，自动触发下一次AI移动
        if (this.activity.gameMode == 3 && this.activity.chessInfo.status == 1) {
            // 直接触发下一次AI移动
            checkAIMove();
        }
        
        return true;
    }
    
    // 启动AI线程
    public void startAIThread() {
        if (this.activity == null || this.activity.chessInfo == null || this.activity.chessInfo.status != 1) {
            return;
        }
        
        // 保存当前实例引用
        final PvMActivityAI aiInstance = this;
        
        // 使用线程池执行AI分析任务
        this.executorService.execute(new AIThreadRunnable(aiInstance));
    }
    
    // AI线程的Runnable类
    private static class AIThreadRunnable implements Runnable {
        private final PvMActivityAI aiInstance;
        
        public AIThreadRunnable(PvMActivityAI aiInstance) {
            this.aiInstance = aiInstance;
        }
        
        @Override
        public void run() {
            if (aiInstance == null) {
                return;
            }
            
            PvMActivity currentActivity = aiInstance.activity;
            if (currentActivity == null) {
                return;
            }
            
            Move move = aiInstance.calculateAIMove();
            
            currentActivity = aiInstance.activity;
            if (currentActivity == null) {
                return;
            }
            
            final Move finalMove = move;
            currentActivity.runOnUiThread(new AIUIRunnable(aiInstance, currentActivity, finalMove));
        }
    }
    
    // AI UI线程的Runnable类
    private static class AIUIRunnable implements Runnable {
        private final PvMActivityAI aiInstance;
        private final PvMActivity activity;
        private final Move move;
        
        public AIUIRunnable(PvMActivityAI aiInstance, PvMActivity activity, Move move) {
            this.aiInstance = aiInstance;
            this.activity = activity;
            this.move = move;
        }
        
        @Override
        public void run() {
            if (aiInstance == null || activity == null) {
                return;
            }
            
            if (move != null) {
                aiInstance.executeAIMove(move);
            } else {
                // 停止深度更新
                aiInstance.stopAISearch();
                
                // AI无法找到有效移动，检查是否被将死
                if (activity.chessInfo != null) {
                    boolean isRed = activity.chessInfo.IsRedGo;
                    if (activity.chessInfo.piece != null && Rule.isDead(activity.chessInfo.piece, isRed)) {
                        Toast.makeText(activity, isRed ? "红方被将死！黑方胜利" : "黑方被将死！红方胜利", Toast.LENGTH_SHORT).show();
                    } else {
                        // 重试几次
                        if (aiInstance.aiRetryCount < 3) {
                            aiInstance.aiRetryCount++;
                            aiInstance.startAIThread();
                        } else {
                            aiInstance.aiRetryCount = 0;
                        }
                    }
                } else {
                    // 重置重试计数
                    aiInstance.aiRetryCount = 0;
                }
            }
        }
    }
    
    // 检查是否需要AI移动
    public void checkAIMove() {
        if (this.activity == null || this.activity.chessInfo == null || this.activity.chessInfo.status != 1) {
            return;
        }
        
        // 根据游戏模式判断是否需要AI移动
        if (this.activity.gameMode == 1) { // 人机对战（玩家红）
            if (!this.activity.chessInfo.IsRedGo) { // AI控制黑方
                this.startAIThread();
            }
        } else if (this.activity.gameMode == 2) { // 人机对战（玩家黑）
            if (this.activity.chessInfo.IsRedGo) { // AI控制红方
                this.startAIThread();
            }
        } else if (this.activity.gameMode == 3) { // 双机对战
            // 双机对战模式下，双方都由AI控制
            this.startAIThread();
        }
    }
    
    // 显示AI最佳移动
    public void showAIMove(final boolean isRed) {
        // 首先检查是否已经在分析中
        if (isAIAnalyzing) {
            return;
        }
        
        // 检查activity、chessInfo和chessView是否初始化
        if (this.activity == null || this.activity.chessInfo == null || this.activity.chessView == null) {
            return;
        }
        
        // 初始化aiInfoTextView（如果未初始化）
        if (this.activity.aiInfoTextView == null) {
            initAIInfoTextView();
            if (this.activity.aiInfoTextView == null) {
                // 如果初始化失败，返回
                return;
            }
        }
        
        // 启动AI搜索动画，显示搜索深度
        startAISearch(isRed);
        
        // 保存当前实例引用
        final PvMActivityAI aiInstance = this;
        final PvMActivity currentActivity = this.activity;
        
        // 使用线程池执行AI分析任务
        this.executorService.execute(new ShowAIMoveRunnable(aiInstance, currentActivity, isRed));
    }
    
    // 显示AI最佳移动的Runnable类
    private static class ShowAIMoveRunnable implements Runnable {
        private final PvMActivityAI aiInstance;
        private final PvMActivity activity;
        private final boolean isRed;
        
        public ShowAIMoveRunnable(PvMActivityAI aiInstance, PvMActivity activity, boolean isRed) {
            this.aiInstance = aiInstance;
            this.activity = activity;
            this.isRed = isRed;
        }
        
        @Override
        public void run() {
            if (aiInstance == null || activity == null) {
                return;
            }
            
            // 使用同步块确保整个分析过程的原子性
            synchronized (aiInstance.aiAnalysisLock) {
                if (aiInstance.isAIAnalyzing) {
                    return;
                }
                aiInstance.isAIAnalyzing = true;
                
                Move move = null;
                
                try {
                    // 直接使用原始chessInfo进行分析
                    if (activity.pikafishAI != null && activity.pikafishAI.isInitialized()) {
                        PikafishAI.MoveWithScore moveWithScore = activity.pikafishAI.getBestMoveWithScore(activity.chessInfo);
                        if (moveWithScore != null) {
                            move = moveWithScore.move;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 中断AI搜索
                    if (activity.pikafishAI != null && activity.pikafishAI.isInitialized()) {
                        activity.pikafishAI.interrupt();
                    }
                    
                    // 手动更新搜索深度，传递正确的isRed值
                    if (activity != null && activity.roundView != null) {
                        int currentDepth = 0;
                        if (activity.pikafishAI != null) {
                            currentDepth = activity.pikafishAI.getCurrentDepth();
                        }
                        if (currentDepth > 0) {
                            activity.roundView.setSearchDepth(currentDepth, isRed);
                        }
                    }
                    
                    aiInstance.isAIAnalyzing = false;
                    aiInstance.dotCount = 0;
                    
                    final Move finalMove = move;
                    // 再次检查activity是否为null
                    PvMActivity currentActivity = aiInstance.activity;
                    if (currentActivity != null) {
                        currentActivity.runOnUiThread(new ShowAIMoveUIRunnable(aiInstance, currentActivity, finalMove, isRed));
                    }
                }
            }
        }
    }
    
    // 显示AI最佳移动UI的Runnable类
    private static class ShowAIMoveUIRunnable implements Runnable {
        private final PvMActivityAI aiInstance;
        private final PvMActivity activity;
        private final Move move;
        private final boolean isRed;
        
        public ShowAIMoveUIRunnable(PvMActivityAI aiInstance, PvMActivity activity, Move move, boolean isRed) {
            this.aiInstance = aiInstance;
            this.activity = activity;
            this.move = move;
            this.isRed = isRed;
        }
        
        @Override
        public void run() {
            if (aiInstance == null || activity == null || activity.aiInfoTextView == null || activity.chessInfo == null || activity.chessView == null) {
                return;
            }
            
            // 停止AI搜索动画
            aiInstance.stopAISearch();
            
            if (move != null && move.fromPos != null && move.toPos != null) {
                // 转换为显示坐标
                int displayFromX = move.fromPos.x;
                int displayFromY = move.fromPos.y;
                int displayToX = move.toPos.x;
                int displayToY = move.toPos.y;

                
                // 获取棋子信息
                int piece = 0;
                if (activity.chessInfo != null && activity.chessInfo.piece != null && move.fromPos != null) {
                    piece = activity.chessInfo.piece[move.fromPos.y][move.fromPos.x];
                }
                String[] pieceNames = {
                    "", "将", "士", "象", "马", "车", "炮", "卒",
                    "帅", "士", "相", "马", "车", "炮", "兵"
                };
                String pieceName = "未知";
                if (piece >= 1 && piece <= pieceNames.length - 1) {
                    pieceName = pieceNames[piece - 1];
                }
                
                // 转换为传统中国象棋记谱法
                String moveInfo = activity.generateMoveString(activity.chessInfo, piece, move.fromPos, move.toPos, isRed);
                if (moveInfo == null) {
                    moveInfo = "未知走法";
                }
                
                // 生成提示信息
                String hintText = (isRed ? "红方" : "黑方") + ": " + moveInfo + " (" + (char)('a' + displayFromX) + (9 - displayFromY) + "到" + (char)('a' + displayToX) + (9 - displayToY) + ")";
                aiInstance.updateAIInfoText(hintText);
                
                // 选中需要移动的棋子
                // 设置支招位置信息
                activity.chessInfo.suggestFromPos = move.fromPos;
                activity.chessInfo.suggestToPos = move.toPos;
                // 不要修改prePos和IsChecked，避免影响棋局状态
                // 获取可能的移动位置
                List<Pos> possibleMoves = Rule.PossibleMoves(activity.chessInfo.piece, move.fromPos.x, move.fromPos.y, piece);
                activity.chessInfo.ret = possibleMoves;
                // 重新绘制界面，显示选中效果
                activity.chessView.requestDraw();
            } else {
                aiInstance.updateAIInfoText("AI无法找到有效移动");
                if (activity != null) {
                    Toast.makeText(activity, "AI无法找到有效移动", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    
    // 为支招计算AI最佳移动（使用临时对象）
    public Move calculateAIMoveForSuggestion(ChessInfo tempChessInfo) {
        if (this.activity == null || tempChessInfo == null || this.activity.pikafishAI == null || !this.activity.pikafishAI.isInitialized() || tempChessInfo.piece == null) {
            return null;
        }
        
        // 验证棋盘状态的有效性
        if (tempChessInfo.piece.length != 10) {
            return null;
        }
        
        for (int i = 0; i < 10; i++) {
            if (tempChessInfo.piece[i] == null || tempChessInfo.piece[i].length != 9) {
                return null;
            }
        }
        
        // 检查将（帅）是否存在
        boolean redKingExists = false;
        boolean blackKingExists = false;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                int piece = tempChessInfo.piece[i][j];
                if (piece == 8) { // 红帅
                    redKingExists = true;
                } else if (piece == 1) { // 黑将
                    blackKingExists = true;
                }
                if (redKingExists && blackKingExists) {
                    break;
                }
            }
            if (redKingExists && blackKingExists) {
                break;
            }
        }
        
        if (!redKingExists || !blackKingExists) {
            return null;
        }
        
        // 检查是否被将死
        boolean isRed = tempChessInfo.IsRedGo;
        if (Rule.isDead(tempChessInfo.piece, isRed)) {
            return null;
        }
        
        // 检查是否有可移动的棋子
        boolean hasValidMoves = false;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                int piece = tempChessInfo.piece[i][j];
                if (piece != 0) {
                    boolean isPieceRed = (piece >= 8);
                    if (isPieceRed == isRed) {
                        List<Pos> moves = Rule.PossibleMoves(tempChessInfo.piece, j, i, piece);
                        if (!moves.isEmpty()) {
                            hasValidMoves = true;
                            break;
                        }
                    }
                }
            }
            if (hasValidMoves) {
                break;
            }
        }
        
        if (!hasValidMoves) {
            return null;
        }
        
        // 获取最佳移动
        PikafishAI.MoveWithScore moveWithScore = this.activity.pikafishAI.getBestMoveWithScore(tempChessInfo);
        return moveWithScore != null && moveWithScore.move != null ? moveWithScore.move : null;
    }
    
    // 初始化AI支招信息显示
    public void initAIInfoTextView() {
        if (this.activity == null || this.activity.relativeLayout == null) return;
        
        // 创建AI信息TextView
        this.activity.aiInfoTextView = new android.widget.TextView(this.activity);
        this.activity.aiInfoTextView.setText("点击支招-AI建议");
        this.activity.aiInfoTextView.setTextSize(14);
        this.activity.aiInfoTextView.setTextColor(android.graphics.Color.BLACK);
        this.activity.aiInfoTextView.setPadding(15, 8, 15, 8);
        this.activity.aiInfoTextView.setGravity(android.view.Gravity.CENTER);
        this.activity.aiInfoTextView.setBackgroundColor(android.graphics.Color.parseColor("#FFFFCC"));
        this.activity.aiInfoTextView.setVisibility(android.view.View.VISIBLE);
        
        // 设置字体样式
        this.activity.aiInfoTextView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        
        // 设置AI信息布局参数
        android.widget.RelativeLayout.LayoutParams aiParams = new android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
            android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        aiParams.addRule(android.widget.RelativeLayout.BELOW, R.id.chessView);
        aiParams.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
        aiParams.setMargins(30, 2, 30, 5);
        this.activity.aiInfoTextView.setLayoutParams(aiParams);
        
        // 添加到布局
        this.activity.relativeLayout.addView(this.activity.aiInfoTextView);
    }
    
    // 更新AI信息文本
    private void updateAIInfoText(final String text) {
        // 这里可以添加更新UI的逻辑，比如显示AI思考过程
        // 更新固定显示的TextView，确保在UI线程中执行
        if (this.activity != null) {
            this.activity.runOnUiThread(new UpdateAIInfoRunnable(this, text));
        }
    }
    
    // 启动AI搜索
    private void startAISearch(boolean isRed) {
        // 显示AI开始搜索的信息，包含深度
        if (this.activity != null) {
            // 初始化AI信息TextView（如果未初始化），确保在主线程中执行
            if (this.activity.aiInfoTextView == null) {
                this.activity.runOnUiThread(new InitAIInfoTextViewRunnable(this));
            }
            
            // 启动深度更新线程，保存Future对象以便后续取消
            if (this.depthUpdateFuture != null) {
                this.depthUpdateFuture.cancel(true);
            }
            this.depthUpdateFuture = this.scheduledExecutorService.scheduleAtFixedRate(new DepthUpdateRunnable(this, isRed), 0, 500, TimeUnit.MILLISECONDS);
        }
    }
    
    // 初始化AI信息TextView的Runnable类
    private static class InitAIInfoTextViewRunnable implements Runnable {
        private final PvMActivityAI aiInstance;
        
        public InitAIInfoTextViewRunnable(PvMActivityAI aiInstance) {
            this.aiInstance = aiInstance;
        }
        
        @Override
        public void run() {
            if (aiInstance != null && aiInstance.activity != null && aiInstance.activity.relativeLayout != null) {
                aiInstance.initAIInfoTextView();
            }
        }
    }
    
    // 深度更新线程的Runnable类
    private static class DepthUpdateRunnable implements Runnable {
        private final PvMActivityAI aiInstance;
        private final boolean isRed;
        private int dotCount = 0;
        
        public DepthUpdateRunnable(PvMActivityAI aiInstance, boolean isRed) {
            this.aiInstance = aiInstance;
            this.isRed = isRed;
        }
        
        @Override
        public void run() {
            if (aiInstance != null && aiInstance.activity != null) {
                // 获取真实的AI搜索深度
                int currentDepth = 0;
                if (aiInstance.activity.pikafishAI != null) {
                    currentDepth = aiInstance.activity.pikafishAI.getCurrentDepth();
                }
                
                // 添加动画效果，显示不同数量的点
                dotCount = (dotCount + 1) % 4;
                String dots = "";
                for (int i = 0; i < dotCount; i++) {
                    dots += ".";
                }
                
                // 显示真实的搜索深度和动画效果，包含红方/黑方信息
                String depthText = (isRed ? "红方" : "黑方") + "AI正在思考" + dots + " (搜索深度: " + (currentDepth > 0 ? currentDepth : 1) + "层)";
                aiInstance.updateAIInfoText(depthText);
            }
        }
    }
    
    // 停止AI搜索
    private void stopAISearch() {
        // 取消深度更新线程
        if (this.depthUpdateFuture != null) {
            this.depthUpdateFuture.cancel(true);
            this.depthUpdateFuture = null;
        }
        // 清除AI信息文本
        if (this.activity != null && this.activity.aiInfoTextView != null) {
            this.activity.runOnUiThread(new ClearAIInfoRunnable(this));
        }
    }
    
    // 清除AI信息的Runnable类
    private static class ClearAIInfoRunnable implements Runnable {
        private final PvMActivityAI aiInstance;
        
        public ClearAIInfoRunnable(PvMActivityAI aiInstance) {
            this.aiInstance = aiInstance;
        }
        
        @Override
        public void run() {
            if (aiInstance != null && aiInstance.activity != null && aiInstance.activity.aiInfoTextView != null) {
                aiInstance.activity.aiInfoTextView.setText("点击支招-AI建议");
            }
        }
    }
    
    // 更新AI信息的Runnable类
    private static class UpdateAIInfoRunnable implements Runnable {
        private final PvMActivityAI aiInstance;
        private final String text;
        
        public UpdateAIInfoRunnable(PvMActivityAI aiInstance, String text) {
            this.aiInstance = aiInstance;
            this.text = text;
        }
        
        @Override
        public void run() {
            if (aiInstance == null) {
                return;
            }
            
            PvMActivity currentActivity = aiInstance.activity;
            if (currentActivity != null && currentActivity.aiInfoTextView != null) {
                currentActivity.aiInfoTextView.setText(text);
            }
        }
    }
    
    // 双机对战模式的Runnable类
    private static class DoubleAIRunnable implements Runnable {
        private final PvMActivityAI aiInstance;
        
        public DoubleAIRunnable(PvMActivityAI aiInstance) {
            this.aiInstance = aiInstance;
        }
        
        @Override
        public void run() {
            if (aiInstance == null) {
                return;
            }
            
            PvMActivity currentActivity = aiInstance.activity;
            if (currentActivity == null) {
                return;
            }
            
            currentActivity.runOnUiThread(new DoubleAIUIRunnable(aiInstance));
        }
    }
    
    // 双机对战模式UI的Runnable类
    private static class DoubleAIUIRunnable implements Runnable {
        private final PvMActivityAI aiInstance;
        
        public DoubleAIUIRunnable(PvMActivityAI aiInstance) {
            this.aiInstance = aiInstance;
        }
        
        @Override
        public void run() {
            if (aiInstance == null) {
                return;
            }
            
            aiInstance.checkAIMove();
        }
    }
    
    // 关闭线程池
    public void shutdown() {
        executorService.shutdown();
        scheduledExecutorService.shutdown();
    }
}