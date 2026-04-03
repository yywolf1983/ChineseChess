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
import Utils.LogUtils;

public class PvMActivityAI {
    private PvMActivity activity;
    private int aiRetryCount = 0;
    private final Object aiAnalysisLock = new Object();
    private volatile boolean isAIAnalyzing = false;
    // 使用有界队列和自定义拒绝策略，避免线程堆积
    private java.util.concurrent.ThreadPoolExecutor executorService;
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private java.util.concurrent.ScheduledFuture<?> depthUpdateFuture;
    private static final long AI_TIMEOUT_BUFFER_MS = 5000; // AI计算超时缓冲时间5秒
    
    public PvMActivityAI(PvMActivity activity) {
        this.activity = activity;
        this.aiMoveHistory = new java.util.ArrayList<>();
        initExecutorService();
    }
    
    // 初始化线程池
    private void initExecutorService() {
        // 优化线程池配置，根据CPU核心数动态调整
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(1, Math.min(availableProcessors - 2, 3)); // 保留更多核心给系统
        int maximumPoolSize = Math.max(3, Math.min(availableProcessors - 1, 5)); // 减少最大线程数
        long keepAliveTime = 60L;
        
        // 初始化线程池
        executorService = new java.util.concurrent.ThreadPoolExecutor(
            corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(5), // 减小队列大小
            java.util.concurrent.Executors.defaultThreadFactory(),
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy() // 使用CallerRunsPolicy，避免任务被丢弃
        );
        // 允许核心线程超时，避免空闲时占用资源
        executorService.allowCoreThreadTimeOut(true);
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
        
        // 移除胜利判断，只保留被将判断
        
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
            // 空值检查
            if (this.activity == null || this.activity.chessInfo == null || this.activity.pikafishAI == null || !this.activity.pikafishAI.isInitialized() || this.executorService == null) {
                LogUtils.e("PvMActivityAI", "空值检查失败，activity或chessInfo或pikafishAI或executorService为null");
                break;
            }
            
            PikafishAI.MoveWithScore moveWithScore = null;
            
            // 计算超时时间：设置的时间 + 缓冲时间
            int thinkingTime = 10; // 默认10秒
            if (this.activity.chessInfo.setting != null) {
                thinkingTime = this.activity.chessInfo.setting.mLevel;
            }
            long aiTimeoutMs = thinkingTime * 1000 + AI_TIMEOUT_BUFFER_MS;
            
            // 如果是强制变着模式且有收集到着法，从列表中选择
            if (this.activity.chessInfo.forceVariation && !allPossibleMoves.isEmpty() && retryCount > 0) {
                // 尝试从所有可能着法中选择一个不同的
                move = selectDifferentMove(allPossibleMoves, triedMoves);
                if (move != null) {
                    // 模拟获取分数
                    moveWithScore = new PikafishAI.MoveWithScore(move, 0);
                } else {
                    // 如果没有不同的着法，使用正常AI计算（带超时机制）
                    try {
                        java.util.concurrent.Future<PikafishAI.MoveWithScore> future = executorService.submit(() -> {
                            if (this.activity == null || this.activity.chessInfo == null || this.activity.pikafishAI == null || !this.activity.pikafishAI.isInitialized()) {
                                return null;
                            }
                            return this.activity.pikafishAI.getBestMoveWithScore(this.activity.chessInfo);
                        });
                        moveWithScore = future.get(aiTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (java.util.concurrent.TimeoutException e) {
                        LogUtils.e("PvMActivityAI", "AI计算超时 (限制: " + aiTimeoutMs + "ms)");
                        break;
                    } catch (Exception e) {
                        LogUtils.e("PvMActivityAI", "AI计算异常: " + e.getMessage());
                        e.printStackTrace();
                        break;
                    }
                }
            } else {
                // 正常AI计算（带超时机制）
                try {
                    java.util.concurrent.Future<PikafishAI.MoveWithScore> future = executorService.submit(() -> {
                        if (this.activity == null || this.activity.chessInfo == null || this.activity.pikafishAI == null || !this.activity.pikafishAI.isInitialized()) {
                            return null;
                        }
                        return this.activity.pikafishAI.getBestMoveWithScore(this.activity.chessInfo);
                    });
                    moveWithScore = future.get(aiTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    LogUtils.e("PvMActivityAI", "AI计算超时 (限制: " + aiTimeoutMs + "ms)");
                    break;
                } catch (Exception e) {
                    LogUtils.e("PvMActivityAI", "AI计算异常: " + e.getMessage());
                    e.printStackTrace();
                    break;
                }
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
                
                // 移除延迟，直接重新计算
                // 不再使用Thread.sleep，避免不必要的延迟
                
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
            // 移除胜利判断，只保留被将判断
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
        
        if (this.activity.roundView != null) {
            // 直接使用当前分数，避免再次触发AI搜索
            this.activity.roundView.setMoveScore(this.currentAIScore);
        }
        
        if (this.activity.chessView != null) {
            this.activity.chessView.requestDraw();
        }
        if (this.activity.roundView != null) {
            this.activity.roundView.requestDraw();
        }
        
        // AI落子后清除支招信息（只有获得支招的一方落子后才清除）
        if (this.activity.gameManager != null) {
            // 判断AI是否是获得支招的一方
            // AI落子方是isRed，需要判断是否与suggestForRed一致
            if (this.activity.gameManager.shouldClearSuggest(isRed)) {
                this.activity.gameManager.clearSuggest();
            }
        } else if (this.activity.roundView != null) {
            // 如果没有gameManager，直接清除
            this.activity.roundView.setSuggestMoveText("");
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
            // 移除延迟，直接触发下一次AI计算
            this.activity.chessView.post(new DoubleAIMoveRunnable(aiInstance));
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
                // 移除延迟，直接开始AI计算
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
                    // 移除胜利判断，只保留被将判断
                    {
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
                
                // 使用Future和超时机制来避免长时间阻塞
                java.util.concurrent.Future<PikafishAI.MoveWithScore> future = null;
                
                // 计算超时时间：设置的时间 + 缓冲时间
                int thinkingTime = 10; // 默认10秒
                if (activity.chessInfo != null && activity.chessInfo.setting != null) {
                    thinkingTime = activity.chessInfo.setting.mLevel;
                }
                long aiTimeoutMs = thinkingTime * 1000 + AI_TIMEOUT_BUFFER_MS;
                LogUtils.i("PvMActivityAI", "AI计算超时时间: " + aiTimeoutMs + "ms (设置时间: " + (thinkingTime * 1000) + "ms + 缓冲: " + AI_TIMEOUT_BUFFER_MS + "ms)");
                
                try {
                    if (activity.pikafishAI != null && activity.pikafishAI.isInitialized() && activity.chessInfo != null && aiInstance.executorService != null) {
                        // 在单独的线程中执行AI计算
                        future = aiInstance.executorService.submit(() -> {
                            if (activity == null || activity.chessInfo == null || activity.pikafishAI == null || !activity.pikafishAI.isInitialized()) {
                                return null;
                            }
                            return activity.pikafishAI.getBestMoveWithScore(activity.chessInfo);
                        });
                        
                        try {
                            // 等待AI计算结果，设置超时（根据设置动态计算）
                            PikafishAI.MoveWithScore moveWithScore = future.get(aiTimeoutMs, TimeUnit.MILLISECONDS);
                            if (moveWithScore != null && activity.chessInfo != null) {
                                move = moveWithScore.move;
                                score = moveWithScore.score;
                                score = PvMActivity.normalizeScore(score, activity.chessInfo.IsRedGo);
                                aiInstance.currentAIScore = score;
                            }
                            // 获取最终的搜索深度
                            currentDepth = activity.pikafishAI.getCurrentDepth();
                        } catch (java.util.concurrent.TimeoutException e) {
                            // 超时处理
                            LogUtils.e("PvMActivityAI", "AI计算超时 (限制: " + aiTimeoutMs + "ms)");
                            if (future != null) {
                                future.cancel(true);
                            }
                            if (activity.pikafishAI != null) {
                                activity.pikafishAI.interrupt();
                            }
                            
                            // 在UI线程显示超时提示
                            if (activity != null) {
                                activity.runOnUiThread(() -> {
                                    Toast.makeText(activity, "AI计算超时，请重试", Toast.LENGTH_SHORT).show();
                                });
                            }
                        } catch (Exception e) {
                            LogUtils.e("PvMActivityAI", "AI计算异常: " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        LogUtils.e("PvMActivityAI", "空值检查失败，activity.chessInfo或activity.pikafishAI为null");
                    }
                } catch (Exception e) {
                    LogUtils.e("PvMActivityAI", "AI线程异常: " + e.getMessage());
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
                
                // 在RoundView中显示支招走法信息
                // 判断支招是给哪一方的（当前行棋方）
                boolean suggestForRed = activity.chessInfo.IsRedGo;
                String moveText = convertMoveToChineseNotation(move, piece);
                
                // 清空步数信息，只显示支招内容
                if (activity.roundView != null) {
                    activity.roundView.setMoveInfoText("");
                }
                
                // 使用PvPActivityGame的setSuggestMove方法（如果存在）
                if (activity.gameManager != null) {
                    activity.gameManager.setSuggestMove(moveText, suggestForRed);
                } else if (activity.roundView != null) {
                    // 直接设置到RoundView
                    activity.roundView.setSuggestMoveText(moveText);
                }
            }
        }
        
        // 将走法转换为标准中文象棋记谱格式
        // 格式：棋子名称 + 起始纵线 + 走法（进/退/平） + 目标位置
        private String convertMoveToChineseNotation(Move move, int piece) {
            if (move == null || move.fromPos == null || move.toPos == null) {
                return "";
            }
            
            // 获取棋子名称
            String pieceName = getPieceName(piece);
            
            // 判断是红方还是黑方
            boolean isRed = piece >= 8;
            
            // 获取起始纵线（从左到右，红方：一至九，黑方：1-9）
            String fromFile = getFileName(move.fromPos.x, isRed);
            
            // 判断走法类型
            String moveType = getMoveType(move, isRed);
            
            // 获取目标位置
            String targetPos = getTargetPosition(move, piece, isRed);
            
            return pieceName + fromFile + moveType + targetPos;
        }
        
        // 获取棋子名称
        private String getPieceName(int piece) {
            switch (piece) {
                case 1: return "将";
                case 2: return "士";
                case 3: return "象";
                case 4: return "马";
                case 5: return "车";
                case 6: return "炮";
                case 7: return "卒";
                case 8: return "帅";
                case 9: return "仕";
                case 10: return "相";
                case 11: return "马";
                case 12: return "车";
                case 13: return "炮";
                case 14: return "兵";
                default: return "";
            }
        }
        
        // 获取纵线名称（从左到右）
        private String getFileName(int x, boolean isRed) {
            String[] redFiles = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};
            String[] blackFiles = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};
            
            // 红方从右到左是一至九，黑方从左到右是1-9
            if (isRed) {
                return redFiles[8 - x]; // 红方：右边是一，左边是九
            } else {
                return blackFiles[x]; // 黑方：左边是1，右边是9
            }
        }
        
        // 判断走法类型（进/退/平）
        private String getMoveType(Move move, boolean isRed) {
            int dy = move.toPos.y - move.fromPos.y;
            
            // 红方：向黑方（y增大）为进，向己方（y减小）为退
            // 黑方：向红方（y减小）为进，向己方（y增大）为退
            if (isRed) {
                if (dy > 0) return "进";
                else if (dy < 0) return "退";
                else return "平";
            } else {
                if (dy < 0) return "进";
                else if (dy > 0) return "退";
                else return "平";
            }
        }
        
        // 获取目标位置
        private String getTargetPosition(Move move, int piece, boolean isRed) {
            // 判断是否为直线移动的棋子（车、炮、兵/卒、将/帅）
            boolean isStraightPiece = (piece == 1 || piece == 5 || piece == 6 || piece == 7 ||
                                       piece == 8 || piece == 12 || piece == 13 || piece == 14);
            
            if (isStraightPiece && move.fromPos.x == move.toPos.x) {
                // 直线移动且在同一纵线上，返回步数（进/退的格数）
                int steps = Math.abs(move.toPos.y - move.fromPos.y);
                return getStepName(steps, isRed);
            } else {
                // 其他情况，返回目标纵线
                return getFileName(move.toPos.x, isRed);
            }
        }
        
        // 获取步数名称
        private String getStepName(int steps, boolean isRed) {
            String[] redSteps = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
            String[] blackSteps = {"", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
            
            if (isRed) {
                return redSteps[steps];
            } else {
                return blackSteps[steps];
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
        // 移除胜利判断，只保留被将判断
        
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
            // 进一步减少深度更新频率，从1000ms改为500ms，避免频繁更新UI
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
            executorService = null; // 清空引用，下次使用时会重新初始化
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
            scheduledExecutorService = null; // 清空引用，下次使用时会重新初始化
        }
    }
}