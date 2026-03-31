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
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private java.util.concurrent.ScheduledFuture<?> depthUpdateFuture;
    
    public PvMActivityAI(PvMActivity activity) {
        this.activity = activity;
    }
    
    public Move calculateAIMove() {
        return calculateAIMoveWithDepthUpdate();
    }
    
    private int currentAIScore = 0;

    public Move calculateAIMoveWithDepthUpdate() {
        boolean isRed = this.activity != null && this.activity.chessInfo != null && this.activity.chessInfo.IsRedGo;
        
        if (this.activity != null) {
            this.activity.startTurnTimer();
            
            // 检查是否需要强制变着
            if (this.activity.chessInfo != null && this.activity.chessInfo.status == 1) {
                if (this.activity.chessInfo.isThreefoldRepetition() || this.activity.chessInfo.isPerpetualCheck()) {
                    // 启用强制变着模式
                    this.activity.chessInfo.forceVariation = true;
                    this.activity.chessInfo.variationRandomness = 3; // 设置中等随机性
                    // 重置重复局面计数
                    String currentHash = this.activity.chessInfo.generatePositionHash();
                    if (this.activity.chessInfo.positionHistory.containsKey(currentHash)) {
                        this.activity.chessInfo.positionHistory.put(currentHash, 1);
                    }
                    // 重置长将计数
                    this.activity.chessInfo.consecutiveCheckRed = 0;
                    this.activity.chessInfo.consecutiveCheckBlack = 0;
                }
            }
        }
        
        startAISearch(isRed);
        
        if (this.activity == null || this.activity.chessInfo == null || this.activity.pikafishAI == null || !this.activity.pikafishAI.isInitialized() || this.activity.chessInfo.piece == null) {
            return null;
        }
        
        if (this.activity.chessInfo.piece.length != 10) {
            return null;
        }
        
        for (int i = 0; i < 10; i++) {
            if (this.activity.chessInfo.piece[i] == null || this.activity.chessInfo.piece[i].length != 9) {
                return null;
            }
        }
        
        boolean redKingExists = false;
        boolean blackKingExists = false;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (this.activity.chessInfo.piece[i][j] == 1) {
                    blackKingExists = true;
                } else if (this.activity.chessInfo.piece[i][j] == 8) {
                    redKingExists = true;
                }
            }
        }
        
        if (!redKingExists || !blackKingExists) {
            return null;
        }
        
        boolean isCurrentPlayerRed = this.activity.chessInfo.IsRedGo;
        
        if (Rule.isDead(this.activity.chessInfo.piece, isCurrentPlayerRed)) {
            return null;
        }
        
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
        
        PikafishAI.MoveWithScore moveWithScore = this.activity.pikafishAI.getBestMoveWithScore(this.activity.chessInfo);
        if (moveWithScore == null) {
            return null;
        }
        Move move = moveWithScore.move;
        int score = moveWithScore.score;
        
        boolean isRedTurn = this.activity.chessInfo.IsRedGo;
        score = PvMActivity.normalizeScore(score, isRedTurn);
        
        this.currentAIScore = score;
        
        if (move != null) {
            Pos fromPos = move.fromPos;
            Pos toPos = move.toPos;
            if (fromPos == null || toPos == null) {
                return null;
            }
            if (fromPos.x < 0 || fromPos.x >= 9 || fromPos.y < 0 || fromPos.y >= 10 || toPos.x < 0 || toPos.x >= 9 || toPos.y < 0 || toPos.y >= 10) {
                return null;
            }
            
            int piece = this.activity.chessInfo.piece[fromPos.y][fromPos.x];
            if (piece == 0) {
                return null;
            }
            
            boolean pieceIsRed = piece >= 8 && piece <= 14;
            boolean currentIsRed = this.activity.chessInfo.IsRedGo;
            
            if (pieceIsRed != currentIsRed) {
                return null;
            }
            
            List<Pos> possibleMoves = Rule.PossibleMoves(this.activity.chessInfo.piece, fromPos.x, fromPos.y, piece);
            if (!possibleMoves.contains(toPos)) {
                return null;
            }
        }
        
        if (this.activity != null && this.activity.pikafishAI != null && this.activity.pikafishAI.isInitialized()) {
            this.activity.pikafishAI.interrupt();
        }
        
        stopAISearch();
        
        return move;
    }
    
    public boolean executeAIMove(Move move) {
        if (this.activity == null || this.activity.chessInfo == null || this.activity.chessInfo.piece == null) {
            return false;
        }
        
        boolean redKingExists = false;
        boolean blackKingExists = false;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (this.activity.chessInfo.piece[i][j] == 1) {
                    blackKingExists = true;
                } else if (this.activity.chessInfo.piece[i][j] == 8) {
                    redKingExists = true;
                }
            }
        }
        
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
        
        if (fromPos == null || toPos == null) {
            return false;
        }
        
        if (fromPos.x < 0 || fromPos.x >= 9 || fromPos.y < 0 || fromPos.y >= 10 || toPos.x < 0 || toPos.x >= 9 || toPos.y < 0 || toPos.y >= 10) {
            return false;
        }
        
        if (this.activity.chessInfo.piece[fromPos.y][fromPos.x] == 0) {
            return false;
        }
        
        int tmp = this.activity.chessInfo.piece[toPos.y][toPos.x];
        int piece = this.activity.chessInfo.piece[fromPos.y][fromPos.x];
        boolean isRed = piece >= 8 && piece <= 14;
        
        if (isRed != this.activity.chessInfo.IsRedGo) {
            return false;
        }
        
        List<Pos> possibleMoves = Rule.PossibleMoves(this.activity.chessInfo.piece, fromPos.x, fromPos.y, piece);
        if (!possibleMoves.contains(toPos)) {
            return false;
        }
        
        if (Rule.isKingDanger(this.activity.chessInfo.piece, isRed)) {
            int temp = this.activity.chessInfo.piece[toPos.y][toPos.x];
            this.activity.chessInfo.piece[toPos.y][toPos.x] = piece;
            this.activity.chessInfo.piece[fromPos.y][fromPos.x] = 0;
            
            boolean stillInCheck = Rule.isKingDanger(this.activity.chessInfo.piece, isRed);
            
            this.activity.chessInfo.piece[fromPos.y][fromPos.x] = piece;
            this.activity.chessInfo.piece[toPos.y][toPos.x] = temp;
            
            if (stillInCheck) {
                return false;
            }
        }
        
        this.activity.chessInfo.piece[toPos.y][toPos.x] = piece;
        this.activity.chessInfo.piece[fromPos.y][fromPos.x] = 0;
        this.activity.chessInfo.IsChecked = Rule.isKingDanger(this.activity.chessInfo.piece, !isRed);
        this.activity.chessInfo.Select = new int[]{-1, -1};
        this.activity.chessInfo.ret.clear();
        this.activity.chessInfo.prePos = fromPos;
        this.activity.chessInfo.curPos = toPos;
        
        String moveString = this.activity.generateMoveString(this.activity.chessInfo, piece, fromPos, toPos, isRed);
        if (moveString != null) {
            Utils.LogUtils.i("Move", "AI走棋: " + moveString);
        }
        
        this.activity.stopTurnTimer();
        
        boolean isCheck = this.activity.chessInfo.IsChecked;
        this.activity.chessInfo.updateAllInfo(this.activity.chessInfo.prePos, this.activity.chessInfo.curPos, this.activity.chessInfo.piece[toPos.y][toPos.x], tmp, isCheck);
        this.activity.chessInfo.isMachine = true;
        
        // 走棋后重置强制变着模式，因为局面已经改变
        if (this.activity.chessInfo.forceVariation) {
            this.activity.chessInfo.forceVariation = false;
            this.activity.chessInfo.variationRandomness = 0;
        }
        
        try {
            this.activity.infoSet.pushInfo(this.activity.chessInfo);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        
        if (this.activity.roundView != null && this.activity.pikafishAI != null && this.activity.pikafishAI.isInitialized()) {
            try {
                PikafishAI.MoveWithScore moveWithScore = this.activity.pikafishAI.getBestMoveWithScore(this.activity.chessInfo);
                if (moveWithScore != null) {
                    int score = moveWithScore.score;
                    boolean isRedTurn = this.activity.chessInfo.IsRedGo;
                    score = PvMActivity.normalizeScore(score, isRedTurn);
                    this.activity.roundView.setMoveScore(score);
                }
            } catch (Exception e) {
                this.activity.roundView.setMoveScore(this.currentAIScore);
            }
        }
        
        if (this.activity.chessView != null) {
            this.activity.chessView.requestDraw();
        }
        if (this.activity.roundView != null) {
            this.activity.roundView.requestDraw();
        }
        
        this.activity.continueGameRoundCount++;
        
        this.activity.startTurnTimer();
        
        // 如果刚刚执行了强制变着，跳过和棋检查，因为强制变着模式下应该允许AI走不同的棋
        if (this.activity.controlsManager != null && !this.activity.chessInfo.forceVariation) {
            this.activity.controlsManager.checkGameStatus(isRed);
        }
        
        stopAISearch();
        
        // 重置AI思考状态，确保AI行棋后不显示"AI正在思考"
        if (this.activity != null && this.activity.roundView != null) {
            this.activity.roundView.setSearchDepth(0, isRed);
        }
        
        if (this.activity.gameMode == 3 && this.activity.chessInfo.status == 1 && this.activity.chessView != null) {
            final PvMActivityAI aiInstance = this;
            this.activity.chessView.postDelayed(new DoubleAIMoveRunnable(aiInstance), 100);
        }
        
        return true;
    }
    
    // 双机对战模式的Runnable类
    private static class DoubleAIMoveRunnable implements Runnable {
        private final PvMActivityAI aiInstance;
        
        public DoubleAIMoveRunnable(PvMActivityAI aiInstance) {
            this.aiInstance = aiInstance;
        }
        
        @Override
        public void run() {
            if (aiInstance != null && aiInstance.activity != null && aiInstance.activity.chessInfo != null && aiInstance.activity.chessInfo.status == 1) {
                aiInstance.checkAIMove();
            }
        }
    }
    
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
                aiInstance.stopAISearch();
                
                if (activity.chessInfo != null) {
                    boolean isRed = activity.chessInfo.IsRedGo;
                    if (activity.chessInfo.piece != null && Rule.isDead(activity.chessInfo.piece, isRed)) {
                        Toast.makeText(activity, isRed ? "红方被将死！黑方胜利" : "黑方被将死！红方胜利", Toast.LENGTH_SHORT).show();
                    } else {
                        if (aiInstance.aiRetryCount < 3) {
                            aiInstance.aiRetryCount++;
                            aiInstance.startAIThread();
                        } else {
                            aiInstance.aiRetryCount = 0;
                        }
                    }
                } else {
                    aiInstance.aiRetryCount = 0;
                }
            }
        }
    }
    
    public void startAIThread() {
        if (this.activity == null || this.activity.chessInfo == null || this.activity.chessInfo.status != 1) {
            return;
        }
        
        final PvMActivityAI aiInstance = this;
        
        this.executorService.execute(new AIThreadRunnable(aiInstance));
    }
    
    public void checkAIMove() {
        if (this.activity == null || this.activity.chessInfo == null || this.activity.chessInfo.status != 1) {
            return;
        }
        
        if (this.activity.gameMode == 1) {
            if (!this.activity.chessInfo.IsRedGo) {
                this.startAIThread();
            }
        } else if (this.activity.gameMode == 2) {
            if (this.activity.chessInfo.IsRedGo) {
                this.startAIThread();
            }
        } else if (this.activity.gameMode == 3) {
            this.startAIThread();
        }
    }
    
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
            
            synchronized (aiInstance.aiAnalysisLock) {
                if (aiInstance.isAIAnalyzing) {
                    return;
                }
                aiInstance.isAIAnalyzing = true;
                
                // 设置为支招模式，显示AI正在思考
                if (activity.roundView != null) {
                    activity.roundView.setSuggestMode(true);
                }
                
                // 启动深度更新任务
                aiInstance.startAISearch(isRed);
                
                Move move = null;
                int score = 0;
                int currentDepth = 0;
                
                try {
                    if (activity.pikafishAI != null && activity.pikafishAI.isInitialized()) {
                        PikafishAI.MoveWithScore moveWithScore = activity.pikafishAI.getBestMoveWithScore(activity.chessInfo);
                        if (moveWithScore != null) {
                            move = moveWithScore.move;
                            score = moveWithScore.score;
                            score = PvMActivity.normalizeScore(score, activity.chessInfo.IsRedGo);
                            aiInstance.currentAIScore = score;
                        }
                        // 获取最终的搜索深度
                        currentDepth = activity.pikafishAI.getCurrentDepth();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 停止深度更新任务
                    aiInstance.stopAISearch();
                    
                    if (activity.pikafishAI != null && activity.pikafishAI.isInitialized()) {
                        activity.pikafishAI.interrupt();
                    }
                    
                    if (activity != null && activity.roundView != null) {
                        // 确保即使AI被中断，也能显示最后的深度信息
                        if (currentDepth > 0) {
                            activity.roundView.setSearchDepth(currentDepth, isRed);
                        }
                        activity.roundView.setMoveScore(aiInstance.currentAIScore);
                        // 支招完成后，重置为非支招模式
                        activity.roundView.setSuggestMode(false);
                        // 发送深度为0的调用，隐藏"AI正在思考"提示，但保留深度信息
                        activity.roundView.setSearchDepth(0, isRed);
                    }
                    
                    aiInstance.isAIAnalyzing = false;
                    
                    final Move finalMove = move;
                    PvMActivity currentActivity = aiInstance.activity;
                    if (currentActivity != null) {
                        currentActivity.runOnUiThread(new ShowAIMoveUIRunnable(aiInstance, currentActivity, finalMove, isRed));
                    }
                }
            }
        }
    }
    
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
            if (aiInstance == null || activity == null || activity.chessInfo == null || activity.chessView == null) {
                return;
            }
            
            aiInstance.stopAISearch();
            
            if (move != null && move.fromPos != null && move.toPos != null) {
                int piece = 0;
                if (activity.chessInfo != null && activity.chessInfo.piece != null && move.fromPos != null) {
                    piece = activity.chessInfo.piece[move.fromPos.y][move.fromPos.x];
                }
                
                activity.chessInfo.suggestFromPos = move.fromPos;
                activity.chessInfo.suggestToPos = move.toPos;
                List<Pos> possibleMoves = Rule.PossibleMoves(activity.chessInfo.piece, move.fromPos.x, move.fromPos.y, piece);
                activity.chessInfo.ret = possibleMoves;
                activity.chessView.requestDraw();
            }
        }
    }
    
    public void showAIMove(final boolean isRed) {
        if (isAIAnalyzing) {
            return;
        }
        
        if (this.activity == null || this.activity.chessInfo == null || this.activity.chessView == null) {
            return;
        }
        
        startAISearch(isRed);
        
        final PvMActivityAI aiInstance = this;
        final PvMActivity currentActivity = this.activity;
        
        this.executorService.execute(new ShowAIMoveRunnable(aiInstance, currentActivity, isRed));
    }
    
    public Move calculateAIMoveForSuggestion(ChessInfo tempChessInfo) {
        if (this.activity == null || tempChessInfo == null || this.activity.pikafishAI == null || !this.activity.pikafishAI.isInitialized() || tempChessInfo.piece == null) {
            return null;
        }
        
        if (tempChessInfo.piece.length != 10) {
            return null;
        }
        
        for (int i = 0; i < 10; i++) {
            if (tempChessInfo.piece[i] == null || tempChessInfo.piece[i].length != 9) {
                return null;
            }
        }
        
        boolean redKingExists = false;
        boolean blackKingExists = false;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                int piece = tempChessInfo.piece[i][j];
                if (piece == 8) {
                    redKingExists = true;
                } else if (piece == 1) {
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
        
        boolean isRed = tempChessInfo.IsRedGo;
        if (Rule.isDead(tempChessInfo.piece, isRed)) {
            return null;
        }
        
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
        
        PikafishAI.MoveWithScore moveWithScore = this.activity.pikafishAI.getBestMoveWithScore(tempChessInfo);
        return moveWithScore != null && moveWithScore.move != null ? moveWithScore.move : null;
    }
    
    private static class DepthUpdateRunnable implements Runnable {
        private final PvMActivityAI aiInstance;
        private final boolean isRed;
        
        public DepthUpdateRunnable(PvMActivityAI aiInstance, boolean isRed) {
            this.aiInstance = aiInstance;
            this.isRed = isRed;
        }
        
        @Override
        public void run() {
            if (aiInstance != null && aiInstance.activity != null && aiInstance.activity.roundView != null) {
                int currentDepth = 0;
                if (aiInstance.activity.pikafishAI != null) {
                    currentDepth = aiInstance.activity.pikafishAI.getCurrentDepth();
                }
                
                aiInstance.activity.roundView.setSearchDepth(currentDepth, isRed);
            }
        }
    }
    
    private void startAISearch(boolean isRed) {
        if (this.activity != null) {
            if (this.depthUpdateFuture != null) {
                this.depthUpdateFuture.cancel(true);
            }
            this.depthUpdateFuture = this.scheduledExecutorService.scheduleAtFixedRate(new DepthUpdateRunnable(this, isRed), 0, 500, TimeUnit.MILLISECONDS);
        }
    }
    
    private void stopAISearch() {
        if (this.depthUpdateFuture != null) {
            this.depthUpdateFuture.cancel(true);
            this.depthUpdateFuture = null;
        }
    }
    
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            scheduledExecutorService.shutdownNow();
            try {
                if (!scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}