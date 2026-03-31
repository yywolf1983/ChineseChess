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
        this.aiMoveHistory = new java.util.ArrayList<>();
    }
    
    // 记录AI着法历史
    private java.util.List<String> aiMoveHistory;
    
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
                if (this.activity.chessInfo.isThreefoldRepetition() || this.activity.chessInfo.isPerpetualCheck() || 
                    this.activity.chessInfo.getPerpetualAttackSide() != null) {
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
                    // 重置长捉计数
                    this.activity.chessInfo.consecutiveAttackRed = 0;
                    this.activity.chessInfo.consecutiveAttackBlack = 0;
                    this.activity.chessInfo.lastAttackedPiecePos = null;
                    this.activity.chessInfo.lastAttackedPieceType = 0;
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
        
        // 获取AI着法，并检查是否会导致重复局面
        Move move = null;
        int maxRetryCount = 10; // 增加最大重试次数
        int retryCount = 0;
        java.util.Set<String> triedMoves = new java.util.HashSet<>(); // 记录已尝试的着法
        java.util.List<Move> allPossibleMoves = new java.util.ArrayList<>(); // 存储所有可能的着法
        
        // 如果是强制变着模式，收集所有合法着法以供选择
        if (this.activity.chessInfo.forceVariation) {
            collectAllPossibleMoves(allPossibleMoves);
        }
        
        while (retryCount < maxRetryCount) {
            PikafishAI.MoveWithScore moveWithScore;
            
            // 如果是强制变着模式且有收集到着法，从列表中选择
            if (this.activity.chessInfo.forceVariation && !allPossibleMoves.isEmpty() && retryCount > 0) {
                // 尝试从所有可能着法中选择一个不同的
                move = selectDifferentMove(allPossibleMoves, triedMoves);
                if (move != null) {
                    // 模拟获取分数
                    moveWithScore = new PikafishAI.MoveWithScore(move, 0);
                } else {
                    // 如果没有不同的着法，使用正常AI计算
                    moveWithScore = this.activity.pikafishAI.getBestMoveWithScore(this.activity.chessInfo);
                }
            } else {
                // 正常AI计算
                moveWithScore = this.activity.pikafishAI.getBestMoveWithScore(this.activity.chessInfo);
            }
            
            if (moveWithScore == null) {
                break;
            }
            
            move = moveWithScore.move;
            int score = moveWithScore.score;
            
            boolean isRedTurn = this.activity.chessInfo.IsRedGo;
            score = PvMActivity.normalizeScore(score, isRedTurn);
            
            this.currentAIScore = score;
            
            if (move == null) {
                break;
            }
            
            Pos fromPos = move.fromPos;
            Pos toPos = move.toPos;
            if (fromPos == null || toPos == null) {
                break;
            }
            if (fromPos.x < 0 || fromPos.x >= 9 || fromPos.y < 0 || fromPos.y >= 10 || toPos.x < 0 || toPos.x >= 9 || toPos.y < 0 || toPos.y >= 10) {
                break;
            }
            
            int piece = this.activity.chessInfo.piece[fromPos.y][fromPos.x];
            if (piece == 0) {
                break;
            }
            
            boolean pieceIsRed = piece >= 8 && piece <= 14;
            boolean currentIsRed = this.activity.chessInfo.IsRedGo;
            
            if (pieceIsRed != currentIsRed) {
                break;
            }
            
            List<Pos> possibleMoves = Rule.PossibleMoves(this.activity.chessInfo.piece, fromPos.x, fromPos.y, piece);
            if (!possibleMoves.contains(toPos)) {
                break;
            }
            
            // 检查这个着法是否会导致重复局面
            boolean leadsToRepetition = checkIfMoveLeadsToRepetition(move);
            String moveKey = fromPos.x + "," + fromPos.y + "->" + toPos.x + "," + toPos.y;
            
            // 如果这个着法已经尝试过，或者会导致重复局面，则重新计算
            if (!triedMoves.contains(moveKey) && !leadsToRepetition) {
                // 这个着法不会导致重复局面，可以使用
                triedMoves.add(moveKey);
                
                // 如果是强制变着模式，检查是否与历史着法相同
                if (this.activity.chessInfo.forceVariation && !aiMoveHistory.isEmpty()) {
                    // 检查这个着法是否与最近的历史着法相同
                    String lastMove = aiMoveHistory.isEmpty() ? "" : aiMoveHistory.get(aiMoveHistory.size() - 1);
                    if (moveKey.equals(lastMove)) {
                        // 与历史着法相同，需要重新计算
                        retryCount++;
                        if (retryCount >= maxRetryCount) {
                            // 如果达到最大重试次数，强制选择一个不同的着法
                            move = forceSelectDifferentMove(allPossibleMoves, triedMoves);
                            if (move != null) {
                                break;
                            }
                        }
                        continue;
                    }
                }
                break;
            } else {
                // 这个着法会导致重复局面或已经尝试过，需要重新计算
                triedMoves.add(moveKey);
                retryCount++;
                
                // 如果是强制变着模式，增加随机性
                if (this.activity.chessInfo.forceVariation) {
                    this.activity.chessInfo.variationRandomness = Math.min(5, this.activity.chessInfo.variationRandomness + 1);
                }
                
                // 如果达到最大重试次数，强制选择一个不同的着法
                if (retryCount >= maxRetryCount && this.activity.chessInfo.forceVariation) {
                    move = forceSelectDifferentMove(allPossibleMoves, triedMoves);
                    if (move != null) {
                        break;
                    }
                }
                
                // 短暂延迟后重新计算
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // 重新开始AI搜索
                if (this.activity.pikafishAI != null && this.activity.pikafishAI.isInitialized()) {
                    this.activity.pikafishAI.interrupt();
                    startAISearch(isRedTurn);
                }
            }
        }
        
        if (this.activity != null && this.activity.pikafishAI != null && this.activity.pikafishAI.isInitialized()) {
            this.activity.pikafishAI.interrupt();
        }
        
        stopAISearch();
        
        return move;
    }
    
    // 检查着法是否会导致重复局面
    private boolean checkIfMoveLeadsToRepetition(Move move) {
        if (this.activity == null || this.activity.chessInfo == null || move == null || 
            move.fromPos == null || move.toPos == null) {
            return false;
        }
        
        // 模拟执行这个着法
        int[][] simulatedBoard = new int[10][9];
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                simulatedBoard[i][j] = this.activity.chessInfo.piece[i][j];
            }
        }
        
        int piece = simulatedBoard[move.fromPos.y][move.fromPos.x];
        int capturedPiece = simulatedBoard[move.toPos.y][move.toPos.x];
        
        // 执行移动
        simulatedBoard[move.toPos.y][move.toPos.x] = piece;
        simulatedBoard[move.fromPos.y][move.fromPos.x] = 0;
        
        // 切换回合
        boolean simulatedIsRedGo = !this.activity.chessInfo.IsRedGo;
        
        // 生成模拟局面的哈希
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                sb.append(simulatedBoard[i][j]);
            }
        }
        sb.append(simulatedIsRedGo ? "R" : "B");
        String simulatedHash = sb.toString();
        
        // 检查这个局面是否已经在历史中出现过
        Integer count = this.activity.chessInfo.positionHistory.get(simulatedHash);
        return count != null && count >= 2; // 如果已经出现过2次，再出现就会导致3次重复
    }
    
    public boolean executeAIMove(Move move) {
        if (this.activity == null || this.activity.chessInfo == null || this.activity.chessInfo.piece == null) {
            return false;
        }
        
        // 检查当前局面是否已经是重复局面，如果是则强制AI变着
        if (this.activity.chessInfo.isThreefoldRepetition()) {
            // 启用强制变着模式
            this.activity.chessInfo.forceVariation = true;
            this.activity.chessInfo.variationRandomness = 5; // 设置高随机性
            
            // 通知用户需要重新计算
            if (this.activity != null) {
                this.activity.runOnUiThread(() -> {
                    Toast.makeText(this.activity, "重复局面，AI重新计算着法...", Toast.LENGTH_SHORT).show();
                });
            }
            
            // 重新触发AI计算
            return triggerAIRecalculation();
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
        
        // 记录AI着法历史
        String moveKey = fromPos.x + "," + fromPos.y + "->" + toPos.x + "," + toPos.y;
        aiMoveHistory.add(moveKey);
        // 只保留最近10个着法
        if (aiMoveHistory.size() > 10) {
            aiMoveHistory.remove(0);
        }
        
        // 走棋后重置强制变着模式，因为局面已经改变
        if (this.activity.chessInfo.forceVariation) {
            this.activity.chessInfo.forceVariation = false;
            this.activity.chessInfo.variationRandomness = 0;
        }
        
        // 检查AI走棋后是否导致重复局面
        if (checkIfMoveLeadsToRepetition(move)) {
            // 撤销这个着法，因为它会导致重复局面
            this.activity.chessInfo.piece[fromPos.y][fromPos.x] = piece;
            this.activity.chessInfo.piece[toPos.y][toPos.x] = tmp;
            
            // 重新触发AI计算
            new Thread(() -> {
                try {
                    Thread.sleep(100); // 短暂延迟
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // 在UI线程中重新触发AI计算
                if (this.activity != null) {
                    this.activity.runOnUiThread(() -> {
                        checkAIMove();
                    });
                }
            }).start();
            
            return false;
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
    
    // 重新触发AI计算
    private boolean triggerAIRecalculation() {
        if (this.activity == null || this.activity.chessInfo == null || this.activity.chessInfo.status != 1) {
            return false;
        }
        
        // 重置重复局面计数
        String currentHash = this.activity.chessInfo.generatePositionHash();
        if (this.activity.chessInfo.positionHistory.containsKey(currentHash)) {
            this.activity.chessInfo.positionHistory.put(currentHash, 1);
        }
        
        // 增加随机性
        this.activity.chessInfo.variationRandomness = 5;
        
        // 重新触发AI计算
        new Thread(() -> {
            try {
                Thread.sleep(200); // 短暂延迟让用户看到提示
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            if (this.activity != null) {
                this.activity.runOnUiThread(() -> {
                    checkAIMove();
                });
            }
        }).start();
        
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
    
    // 收集所有可能的合法着法
    private void collectAllPossibleMoves(java.util.List<Move> allPossibleMoves) {
        if (this.activity == null || this.activity.chessInfo == null || this.activity.chessInfo.piece == null) {
            return;
        }
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                int piece = this.activity.chessInfo.piece[i][j];
                if (piece != 0) {
                    boolean pieceIsRed = piece >= 8 && piece <= 14;
                    boolean currentIsRed = this.activity.chessInfo.IsRedGo;
                    
                    // 只收集当前回合方的着法
                    if ((pieceIsRed && currentIsRed) || (!pieceIsRed && !currentIsRed)) {
                        List<Pos> possibleMoves = Rule.PossibleMoves(this.activity.chessInfo.piece, j, i, piece);
                        for (Pos toPos : possibleMoves) {
                            Move move = new Move(new Pos(j, i), toPos);
                            allPossibleMoves.add(move);
                        }
                    }
                }
            }
        }
    }
    
    // 选择不同于已尝试过的着法
    private Move selectDifferentMove(java.util.List<Move> allPossibleMoves, java.util.Set<String> triedMoves) {
        for (Move move : allPossibleMoves) {
            String moveKey = move.fromPos.x + "," + move.fromPos.y + "->" + move.toPos.x + "," + move.toPos.y;
            if (!triedMoves.contains(moveKey)) {
                return move;
            }
        }
        return null; // 如果没有不同的着法，返回null
    }
    
    // 强制选择一个不同的着法（即使不是最佳着法）
    private Move forceSelectDifferentMove(java.util.List<Move> allPossibleMoves, java.util.Set<String> triedMoves) {
        // 首先尝试选择一个不同的着法
        Move move = selectDifferentMove(allPossibleMoves, triedMoves);
        if (move != null) {
            return move;
        }
        
        // 如果没有不同的着法，随机选择一个着法
        if (!allPossibleMoves.isEmpty()) {
            int randomIndex = (int) (Math.random() * allPossibleMoves.size());
            return allPossibleMoves.get(randomIndex);
        }
        
        return null;
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