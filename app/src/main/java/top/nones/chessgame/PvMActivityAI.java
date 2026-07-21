package top.nones.chessgame;

import android.media.MediaPlayer;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import Info.ChessInfo;
import Info.Pos;
import ChessMove.Rule;
import ChessMove.Move;
import AICore.PikafishAI;
import AICore.OpeningBook;
import CustomView.ChessView;
import CustomView.RoundView;
import top.nones.chessgame.R;
import Utils.LogUtils;

public class PvMActivityAI {
    private PvMActivity activity;
    private int aiRetryCount = 0;
    private final AtomicBoolean aiAnalyzingState = new AtomicBoolean(false);
    public volatile boolean isAIAnalyzing = false;
    // AI 代际：每次 tryStartAnalyzing 递增，用于 tryFinishAnalyzing 判断是否仍持有状态
    private final java.util.concurrent.atomic.AtomicInteger aiGeneration = new java.util.concurrent.atomic.AtomicInteger(0);
    // 标记 AI 是否被外部中断（stopAIAnalysis），用于让旧 AI 线程尽快退出
    private final AtomicBoolean aiInterrupted = new AtomicBoolean(false);
    // 使用有界队列和自定义拒绝策略，避免线程堆积
    private java.util.concurrent.ThreadPoolExecutor executorService;
    // 内置开局库（仅双机对战 gameMode==3 时使用，随机走开局前两步）
    private final OpeningBook openingBook = new OpeningBook();
    // 开局库着法返回前的保底最短等待时间，避免 AI 瞬间落子、提升体验
    private static final long OPENING_BOOK_MIN_WAIT_MS = 1000;
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private java.util.concurrent.ScheduledFuture<?> depthUpdateFuture;
    
    public PvMActivityAI(PvMActivity activity) {
        this.activity = activity;
        initExecutorService();
    }
    
    // 播放音效
    private void playEffect(MediaPlayer mediaPlayer) {
        Utils.SoundManager.playEffect(mediaPlayer);
    }
    
    // 初始化线程池
    private void initExecutorService() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(1, Math.min(availableProcessors / 2, 2));
        int maximumPoolSize = Math.max(2, Math.min(availableProcessors, 4));
        long keepAliveTime = 30L;
        
        executorService = new java.util.concurrent.ThreadPoolExecutor(
            corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(20),
            java.util.concurrent.Executors.defaultThreadFactory(),
            new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy()
        );
        executorService.allowCoreThreadTimeOut(true);
    }
    
    // 记录AI着法历史
    private final java.util.List<String> aiMoveHistory = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    
    public Move calculateAIMove() {
        return calculateAIMoveWithDepthUpdate();
    }
    
    public volatile int currentAIScore;

    public Move calculateAIMoveWithDepthUpdate() {
        boolean isRed = this.activity != null && this.activity.chessInfo != null && this.activity.chessInfo.IsRedGo;
        
        if (this.activity != null) {
            this.activity.startTurnTimer();
            
            // 检查是否需要强制变着 - 只在真正的三次重复局面时启用
            if (this.activity.chessInfo != null && this.activity.chessInfo.status == 1) {
                if (this.activity.chessInfo.isThreefoldRepetition()) {
                    // 根据设置决定是否启用强制变着模式
                    if (this.activity.setting != null && this.activity.setting.forceVariation) {
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
                
                // 确保使用最新的设置（引用同步，具体值由 checkAIMove/getBestMoveWithScore 的 syncSettingsFromChessInfo 处理）
                if (this.activity.setting != null) {
                    this.activity.chessInfo.setting = this.activity.setting;
                }
            }
        }
        
        startAISearch(isRed);

        // 双机对战（gameMode==3）开局阶段：使用内置开局库随机走前两步，
        // 避免每局开局千篇一律；超出开局库范围后交还下方引擎正常计算。
        // 走的是真实合法着法，且每步经 Rule.PossibleMoves 校验，不会破坏规则。
        if (this.activity.gameMode == 3 && this.openingBook != null) {
            long openingBookStartMs = System.currentTimeMillis();
            Move bookMove = this.openingBook.getBookMove(this.activity.chessInfo, OpeningBook.DEFAULT_BOOK_PLIES);
            if (bookMove != null) {
                LogUtils.i("PvMActivityAI", "使用开局库着法: " + bookMove);
                // 开局库着法计算极快，为保证体验（展示思考动画、避免瞬间落子）最少等待 1 秒
                long waitMs = OPENING_BOOK_MIN_WAIT_MS - (System.currentTimeMillis() - openingBookStartMs);
                if (waitMs > 0) {
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException e) {
                        // 被外部中断（如悔棋 / 切换模式）时，恢复中断标志并按已算出的着法返回
                        Thread.currentThread().interrupt();
                    }
                }
                return bookMove;
            }
        }
        
        // 空值检查 + 等待引擎初始化完成（异步初始化可能还在进行中）
        if (this.activity == null || this.activity.chessInfo == null || this.activity.pikafishAI == null || this.activity.chessInfo.piece == null) {
            LogUtils.w("PvMActivityAI", "AI计算: 基础对象为空，无法计算");
            return null;
        }
        
        // 等待引擎初始化完成（最多等待5秒），避免初始化未完成时直接返回null
        if (!this.activity.pikafishAI.isInitialized()) {
            // 先尝试触发重新初始化（如果之前失败了）
            this.activity.pikafishAI.retryInitialize();
            LogUtils.i("PvMActivityAI", "AI计算: 引擎未初始化，等待初始化完成...");
            if (!this.activity.pikafishAI.waitForInit(8000)) {
                LogUtils.e("PvMActivityAI", "AI计算: 等待引擎初始化超时");
                return null;
            }
            LogUtils.i("PvMActivityAI", "AI计算: 引擎初始化完成，继续计算");
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
        
        // 始终使用 AI 计算，不随机选择着法
        while (retryCount < maxRetryCount) {
            // 被外部中断时立即退出，避免中断后继续搜索
            if (aiInterrupted.get()) {
                LogUtils.i("PvMActivityAI", "AI 被中断，退出搜索循环");
                break;
            }
            // 空值检查
            if (this.activity == null || this.activity.chessInfo == null || this.activity.pikafishAI == null || !this.activity.pikafishAI.isInitialized() || this.executorService == null) {
                LogUtils.e("PvMActivityAI", "空值检查失败，activity或chessInfo或pikafishAI或executorService为null");
                break;
            }
            
            PikafishAI.MoveWithScore moveWithScore = null;
            
            // 引擎内部已有 maxSearchTime 兜底超时，当前已在后台线程中，
            // 直接调用避免嵌套 submit 到同一 executor 导致死锁
            try {
                if (this.activity != null && this.activity.pikafishAI != null && this.activity.pikafishAI.isInitialized()) {
                    moveWithScore = this.activity.pikafishAI.getBestMoveWithScore(this.activity.chessInfo);
                }
            } catch (Exception e) {
                LogUtils.e("PvMActivityAI", "AI计算异常: " + e.getMessage());
                LogUtils.e("PvMActivityAI", "操作失败", e);
                break;
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
        
        // 获取锁对象
        Object lock = this.activity.chessInfo.getLock();
        synchronized (lock) {
            // 检查当前局面是否已经是重复局面，如果是则强制AI变着
            if (this.activity.chessInfo.isThreefoldRepetition()) {
                // 检查用户是否开启了强制变着功能
                boolean forceVariationEnabled = this.activity.setting != null && this.activity.setting.forceVariation;
                
                if (forceVariationEnabled) {
                    // 启用强制变着模式
                    this.activity.chessInfo.forceVariation = true;
                    this.activity.chessInfo.variationRandomness = 5; // 设置高随机性
                    
                    // 通知用户需要重新计算
                    if (this.activity != null) {
                        this.activity.runOnUiThread(() -> {
                            // 移除Toast提示，避免频繁弹出
                        });
                    }
                    
                    // 重新触发AI计算
                    return triggerAIRecalculation();
                } else {
                    LogUtils.i("PvMActivityAI", "检测到重复局面，但用户已关闭强制变着");
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
            
            if (!redKingExists) {
                if (this.activity != null) {
                    // 移除Toast提示，通过界面显示胜利信息
                }
                return false;
            }
            if (!blackKingExists) {
                if (this.activity != null) {
                    // 移除Toast提示，通过界面显示胜利信息
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
            
            // 检查是否吃掉了对方的老将
            boolean isCaptureKing = tmp == 1 || tmp == 8;
            
            this.activity.chessInfo.piece[toPos.y][toPos.x] = piece;
            this.activity.chessInfo.piece[fromPos.y][fromPos.x] = 0;
            
            // 检查移动后是否会导致自己被将军（但如果吃掉了对方老将，则允许）
            if (!isCaptureKing && Rule.isKingDanger(this.activity.chessInfo.piece, isRed)) {
                // 移动会导致自己被将军，撤销移动
                this.activity.chessInfo.piece[fromPos.y][fromPos.x] = piece;
                this.activity.chessInfo.piece[toPos.y][toPos.x] = tmp;
                LogUtils.e("PvMActivityAI", "AI移动会导致自己被将军，撤销移动");
                return false;
            }
            
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
            // 记录 AI 落子后的局面评分（currentAIScore 即该着法后的评估，红优为正），供曲线本步取点
            this.activity.chessInfo.currentEvaluation = this.currentAIScore;
            this.activity.chessInfo.updateAllInfo(this.activity.chessInfo.prePos, this.activity.chessInfo.curPos, this.activity.chessInfo.piece[toPos.y][toPos.x], tmp, isCheck);
            this.activity.refreshScoreCurve();
            this.activity.chessInfo.isMachine = true;
            
            // 播放AI落子音效，将军优先
            if (isCheck) {
                playEffect(this.activity.checkMusic);
            } else if (tmp != 0) {
                playEffect(this.activity.captureMusic);
            } else {
                playEffect(this.activity.clickMusic);
            }
        
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
                LogUtils.e("PvMActivityAI", "操作失败", e);
            }
            
            if (this.activity.roundView != null) {
                // 直接使用当前分数，避免再次触发AI搜索
                this.activity.roundView.setMoveScore(this.currentAIScore);
            }
            // 用 AI 最终评分（即 round 评分）记录本步曲线点并刷新
            this.activity.recordRoundScore(this.currentAIScore);
            
            if (this.activity.chessView != null) {
                this.activity.chessView.requestDraw();
            }
            if (this.activity.roundView != null) {
                this.activity.roundView.requestDraw();
            }
            
            // AI落子后处理支招：跟随模式下若与候选变线一致则保留并高亮揭示后续，
            // 否则按原逻辑（获得支招的一方落子后）清除支招信息
            if (this.activity.suggestFollowActive) {
                handleMoveForSuggestFollow(new Move(fromPos, toPos));
            } else if (this.activity.gameManager != null) {
                // 判断AI是否是获得支招的一方
                // AI落子方是isRed，需要判断是否与suggestForRed一致
                if (this.activity.gameManager.shouldClearSuggest(isRed)) {
                    this.activity.gameManager.clearSuggest();
                }
            } else if (this.activity.roundView != null) {
                // 如果没有gameManager，直接清除
                this.activity.roundView.setSuggestMoveText("");
                this.activity.clearEngineResultBox();
            }
            
            this.activity.continueGameRoundCount++;
            
            this.activity.startTurnTimer();
            
            // 检查游戏状态，包括将死和和棋条件
            if (this.activity.controlsManager != null) {
                this.activity.controlsManager.checkGameStatus(isRed);
            }
            
            stopAISearch();
            
            // 重置AI分析状态
            finishAnalyzing();
            
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

            // 记录当前 AI 代际，用于 tryFinishAnalyzing 判断是否仍持有状态
            final int myGeneration = aiInstance.aiGeneration.get();
            if (!aiInstance.aiAnalyzingState.get()) {
                return;
            }

            // 在后台线程同步下发引擎参数（含 JNI sendCommand），避免阻塞主线程导致 ANR
            if (currentActivity.setting != null
                && currentActivity.pikafishAI != null
                && currentActivity.pikafishAI.isInitialized()) {
                long startMs = System.currentTimeMillis();
                currentActivity.pikafishAI.updateSettings(
                    currentActivity.setting.skillLevel,
                    currentActivity.setting.multiPV,
                    currentActivity.setting.depth,
                    currentActivity.setting.mLevel);
                LogUtils.i("Perf", "AIThreadRunnable.updateSettings cost=" + (System.currentTimeMillis() - startMs) + "ms"
                    + " depth=" + currentActivity.setting.depth + " time=" + currentActivity.setting.mLevel + "s skillLevel=" + currentActivity.setting.skillLevel);
            }

            // updateSettings 期间可能被中断，检查后跳过搜索
            if (aiInstance.aiInterrupted.get()) {
                LogUtils.i("PvMActivityAI", "updateSettings 后发现已被中断，跳过搜索");
                aiInstance.tryFinishAnalyzing(myGeneration);
                return;
            }

            Move move = null;
            try {
                move = aiInstance.calculateAIMove();
            } catch (Exception e) {
                LogUtils.e("PvMActivityAI", "AI计算异常: " + e.getMessage());
                LogUtils.e("PvMActivityAI", "操作失败", e);
            } finally {
                // 确保无论是否发生异常，锁都会被释放
                aiInstance.stopAISearch();
                // 只有当前线程仍持有分析状态时才释放
                // （stopAIAnalysis 可已释放并启动了新 AI，不能误清新状态）
                aiInstance.tryFinishAnalyzing(myGeneration);
            }
            
            currentActivity = aiInstance.activity;
            if (currentActivity == null) {
                return;
            }

            // 被外部中断时不执行 AI 走法，避免中断后 AI 仍然落子
            if (aiInstance.aiInterrupted.get()) {
                LogUtils.i("PvMActivityAI", "AI 被中断，丢弃走法，不执行 AIUIRunnable");
                return;
            }

            final Move finalMove = move;
            try {
                currentActivity.runOnUiThread(new AIUIRunnable(aiInstance, currentActivity, finalMove));
            } catch (Exception e) {
                LogUtils.e("PvMActivityAI", "UI线程执行异常: " + e.getMessage());
                LogUtils.e("PvMActivityAI", "操作失败", e);
            }
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

            // 如果新的 AI 分析已启动（说明悔棋/切换模式后触发了新 AI），丢弃旧的走法
            if (aiInstance.aiAnalyzingState.get()) {
                LogUtils.i("PvMActivityAI", "丢弃过时的 AI 走法（新的 AI 分析已启动）");
                return;
            }

            try {
                if (move != null) {
                    aiInstance.executeAIMove(move);
                } else {
                    if (activity.chessInfo != null) {
                        // 移除胜利判断，只保留被将判断
                        if (aiInstance.aiRetryCount < 3) {
                            aiInstance.aiRetryCount++;
                            aiInstance.startAIThread();
                        } else {
                            aiInstance.aiRetryCount = 0;
                        }
                    } else {
                        aiInstance.aiRetryCount = 0;
                    }
                }
            } catch (Exception e) {
                LogUtils.e("PvMActivityAI", "执行AI走法异常: " + e.getMessage());
                LogUtils.e("PvMActivityAI", "操作失败", e);
                aiInstance.aiRetryCount = 0;
            }
        }
    }
    
    public void startAIThread() {
        if (this.activity == null || this.activity.chessInfo == null || this.activity.chessInfo.status != 1) {
            return;
        }
        // 摆棋模式下不允许 AI 走棋
        if (this.activity.chessInfo.IsSetupMode) {
            return;
        }

        if (!tryStartAnalyzing()) {
            return;
        }

        final PvMActivityAI aiInstance = this;

        this.executorService.execute(new AIThreadRunnable(aiInstance));
    }
    
    public void checkAIMove() {
        // 模拟行棋演示期间禁止 AI 自动落子，避免打断演示
        if (this.activity != null && this.activity.isSimulating()) {
            return;
        }
        if (this.activity == null || this.activity.chessInfo == null || this.activity.chessInfo.status != 1) {
            return;
        }
        // 摆棋模式下不允许 AI 走棋
        if (this.activity.chessInfo.IsSetupMode) {
            return;
        }
        long checkStartMs = System.currentTimeMillis();

        // 只做轻量的 setting 引用赋值（主线程安全），
        // 实际的 updateSettings（含 JNI sendCommand）已移到后台 AI 线程中执行，避免 ANR
        if (this.activity.setting != null) {
            this.activity.chessInfo.setting = this.activity.setting;
        }
        
        boolean aiShouldMove = false;
        if (this.activity.gameMode == 1) {
            if (!this.activity.chessInfo.IsRedGo) {
                aiShouldMove = true;
            }
        } else if (this.activity.gameMode == 2) {
            if (this.activity.chessInfo.IsRedGo) {
                aiShouldMove = true;
            }
        } else if (this.activity.gameMode == 3) {
            aiShouldMove = true;
        }

        if (aiShouldMove) {
            // 立即在主线程启动 AI 思考动画，不等后台线程调度
            if (this.activity.roundView != null) {
                this.activity.roundView.markThinking(this.activity.chessInfo.IsRedGo);
            }
            this.startAIThread();
        }
        LogUtils.i("Perf", "checkAIMove total cost=" + (System.currentTimeMillis() - checkStartMs) + "ms");
    }
    
    private static class ShowAIMoveRunnable implements Runnable {
        private final PvMActivityAI aiInstance;
        private final PvMActivity activity;
        private final boolean isRed;
        
        // 保存 pv 序列，避免重复计算
        private PikafishAI.PvSequenceWithScore cachedPvSequence = null;
        
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
            long suggestStartMs = System.currentTimeMillis();

            if (!aiInstance.tryStartAnalyzing()) {
                return;
            }

            // 记录当前 AI 代际，用于 tryFinishAnalyzing 判断是否仍持有状态
            final int myGeneration = aiInstance.aiGeneration.get();

            // 启动深度更新任务
            aiInstance.startAISearch(isRed);

            Move move = null;
            int score = 0;
            int currentDepth = 0;

            // 前置：同步最新设置到引擎
            if (activity.chessInfo != null && activity.setting != null) {
                activity.chessInfo.setting = activity.setting;
                LogUtils.i("PvMActivityAI", "当前设置: depth=" + activity.setting.depth
                    + " mLevel=" + activity.setting.mLevel
                    + " skillLevel=" + activity.setting.skillLevel
                    + " multiPV=" + activity.setting.multiPV);
                if (activity.pikafishAI != null && activity.pikafishAI.isInitialized()) {
                    activity.pikafishAI.updateSettings(
                        activity.setting.skillLevel,
                        activity.setting.multiPV,
                        activity.setting.depth,
                        activity.setting.mLevel);
                }
            }

            // updateSettings 期间可能被中断，检查后跳过搜索
            if (aiInstance.aiInterrupted.get()) {
                LogUtils.i("PvMActivityAI", "支招 updateSettings 后发现已被中断，跳过搜索");
                aiInstance.tryFinishAnalyzing(myGeneration);
                return;
            }

            // 引擎内部已有 maxSearchTime 兜底超时，当前已在后台线程中，
            // 直接调用避免嵌套 submit 到同一 executor 导致死锁
            try {
                if (activity.pikafishAI != null && activity.chessInfo != null) {
                    // 等待引擎初始化完成（最多等待5秒）
                    if (!activity.pikafishAI.isInitialized()) {
                        // 先尝试触发重新初始化（如果之前失败了）
                        activity.pikafishAI.retryInitialize();
                        LogUtils.i("PvMActivityAI", "支招: 引擎未初始化，等待初始化完成...");
                        if (!activity.pikafishAI.waitForInit(8000)) {
                            LogUtils.e("PvMActivityAI", "支招: 等待引擎初始化超时");
                        }
                    }
                    if (activity.pikafishAI.isInitialized()) {
                        PikafishAI.PvSequenceWithScore pvSequenceWithScore = activity.pikafishAI.getPvSequenceWithScore(activity.chessInfo);
                        if (pvSequenceWithScore != null) {
                            cachedPvSequence = pvSequenceWithScore;
                            if (pvSequenceWithScore.pvSequence != null && !pvSequenceWithScore.pvSequence.isEmpty()) {
                                move = pvSequenceWithScore.pvSequence.get(0);
                            }
                            score = pvSequenceWithScore.score;
                            score = PvMActivity.normalizeScore(score, activity.chessInfo.IsRedGo);
                            aiInstance.currentAIScore = score;
                        }
                        currentDepth = activity.pikafishAI.getCurrentDepth();
                    } else {
                        LogUtils.e("PvMActivityAI", "支招: 引擎初始化失败，无法计算");
                    }
                } else {
                    LogUtils.e("PvMActivityAI", "支招: 空值检查失败，activity.chessInfo或activity.pikafishAI为null");
                }
            } catch (Exception e) {
                LogUtils.e("PvMActivityAI", "AI计算异常: " + e.getMessage());
                LogUtils.e("PvMActivityAI", "操作失败", e);
            } finally {
                // 停止深度更新任务
                aiInstance.stopAISearch();

                if (activity.pikafishAI != null && activity.pikafishAI.isInitialized()) {
                    activity.pikafishAI.interrupt();
                }

                if (activity.roundView != null) {
                    final int finalScore = aiInstance.currentAIScore;
                    activity.runOnUiThread(() -> {
                        if (activity.roundView != null) {
                            activity.roundView.setMoveScore(finalScore);
                            activity.roundView.setSearchDepth(0, isRed);
                            activity.roundView.setSuggestMode(false);
                        }
                        // 用分析得出的最终评分（round 评分）记录本步曲线点并刷新
                        activity.recordRoundScore(finalScore);
                    });
                }

                aiInstance.tryFinishAnalyzing(myGeneration);

                // 被外部中断时不显示支招结果，避免中断后旧结果仍然弹出
                if (aiInstance.aiInterrupted.get()) {
                    LogUtils.i("PvMActivityAI", "AI 被中断，不显示支招结果");
                    return;
                }

                final Move finalMove = move;
                PvMActivity currentActivity = aiInstance.activity;
                if (currentActivity != null) {
                    currentActivity.runOnUiThread(new ShowAIMoveUIRunnable(aiInstance, currentActivity, finalMove, isRed, cachedPvSequence));
                }
                LogUtils.i("Perf", "showAIMove worker total cost=" + (System.currentTimeMillis() - suggestStartMs) + "ms");
            }
        }
    }
    
    private static class ShowAIMoveUIRunnable implements Runnable {
        private final PvMActivityAI aiInstance;
        private final PvMActivity activity;
        private final Move move;
        private final boolean isRed;
        private final PikafishAI.PvSequenceWithScore cachedPvSequence; // 接收已缓存的 pv 序列
        
        public ShowAIMoveUIRunnable(PvMActivityAI aiInstance, PvMActivity activity, Move move, boolean isRed, PikafishAI.PvSequenceWithScore cachedPvSequence) {
            this.aiInstance = aiInstance;
            this.activity = activity;
            this.move = move;
            this.isRed = isRed;
            this.cachedPvSequence = cachedPvSequence;
        }
        
        @Override
        public void run() {
            if (aiInstance == null || activity == null || activity.chessInfo == null || activity.chessView == null) {
                return;
            }

            // 模拟行棋演示中（尚未按返回）：不显示支招提示线，保持模拟局面直到用户返回
            if (activity.isSimulating()) {
                LogUtils.i("PvMActivityAI", "模拟行棋中，跳过支招结果显示");
                return;
            }
            
            aiInstance.stopAISearch();
            
            if (move != null && move.fromPos != null && move.toPos != null) {
                // 生成多步预测（使用已缓存的 pv 序列，避免重复计算）
                generateMultiStepSuggestions(isRed, cachedPvSequence);
                
                int piece = 0;
                if (activity.chessInfo != null && activity.chessInfo.piece != null && move.fromPos != null) {
                    piece = activity.chessInfo.piece[move.fromPos.y][move.fromPos.x];
                }
                
                List<Pos> possibleMoves = Rule.PossibleMoves(activity.chessInfo.piece, move.fromPos.x, move.fromPos.y, piece);
                activity.chessInfo.ret = possibleMoves;
                activity.chessView.requestDraw();
                
                // 在RoundView中显示支招走法信息
                boolean suggestForRed = isRed;
                
                // 清空步数信息，只显示支招内容
                if (activity.roundView != null) {
                    activity.roundView.setMoveInfoText("");
                }
                
                // 最优一步显示在 RoundView（回合信息条）中，便于用户一眼看到最佳着法
                if (activity.roundView != null && activity.chessInfo.suggestMoveNotations != null
                        && !activity.chessInfo.suggestMoveNotations.isEmpty()) {
                    activity.roundView.setBestMoveText(activity.chessInfo.suggestMoveNotations.get(0));
                }

                // 初始化「跟随支招」状态：记录支招时刻局面快照、清空已走前缀
                try {
                    activity.suggestFollowStartInfo = (ChessInfo) activity.chessInfo.clone();
                    activity.suggestFollowPrefix.clear();
                    activity.suggestFollowActive = true;
                } catch (CloneNotSupportedException e) {
                    activity.suggestFollowStartInfo = null;
                    activity.suggestFollowActive = false;
                }

                // 在按钮组底部框中逐条展示引擎多条候选变线
                aiInstance.fillEngineResultBox(0, false);
            }
        }
        
        private void generateMultiStepSuggestions(boolean forRed, PikafishAI.PvSequenceWithScore pvSequenceWithScore) {
            if (activity == null || activity.chessInfo == null) {
                return;
            }
            
            if (pvSequenceWithScore == null || pvSequenceWithScore.pvSequence == null) {
                LogUtils.e("PvMActivityAI", "使用的 pv 序列无效");
                return;
            }
            
            try {
                java.util.List<Move> moves = new java.util.ArrayList<>();
                java.util.List<String> labels = new java.util.ArrayList<>();
                java.util.List<Boolean> isRedList = new java.util.ArrayList<>();
                java.util.List<String> notations = new java.util.ArrayList<>();
                
                java.util.List<Move> pvSequence = pvSequenceWithScore.pvSequence;
                LogUtils.i("PvMActivityAI", "使用已缓存的 pv 序列，长度: " + pvSequence.size());

                // 支招线只显示两步：当前方一步 + 对方一步，双方各按各自颜色显示
                boolean currentIsRed = forRed;

                ChessInfo simulatedInfo = (ChessInfo) activity.chessInfo.clone();

                for (int i = 0; i < pvSequence.size() && i < 2; i++) {
                    Move move = pvSequence.get(i);
                    
                    if (move == null) {
                        break;
                    }
                    
                    // 获取棋子
                    int piece = 0;
                    if (move.fromPos != null && move.fromPos.y >= 0 && move.fromPos.y < 10 && move.fromPos.x >= 0 && move.fromPos.x < 9) {
                        piece = simulatedInfo.piece[move.fromPos.y][move.fromPos.x];
                    }
                    
                    // 生成中文记谱格式的走法
                    String notation = convertMoveToChineseNotation(move, piece);
                    
                    moves.add(move);
                    labels.add(String.valueOf(i + 1)); // 存储步数 1, 2, 3, 4, 5, 6
                    notations.add(notation); // 存储中文记谱
                    isRedList.add(currentIsRed);
                    
                    // 更新模拟棋盘
                    if (move.fromPos != null && move.toPos != null &&
                        move.fromPos.y >= 0 && move.fromPos.y < 10 && move.fromPos.x >= 0 && move.fromPos.x < 9 &&
                        move.toPos.y >= 0 && move.toPos.y < 10 && move.toPos.x >= 0 && move.toPos.x < 9) {
                        int movedPiece = simulatedInfo.piece[move.fromPos.y][move.fromPos.x];
                        simulatedInfo.piece[move.toPos.y][move.toPos.x] = movedPiece;
                        simulatedInfo.piece[move.fromPos.y][move.fromPos.x] = 0;
                        simulatedInfo.IsRedGo = !simulatedInfo.IsRedGo;
                    }
                    
                    currentIsRed = !currentIsRed;
                }
                
                activity.chessInfo.suggestMoves.clear();
                activity.chessInfo.suggestMoves.addAll(moves);
                activity.chessInfo.suggestMoveLabels.clear();
                activity.chessInfo.suggestMoveLabels.addAll(labels);
                activity.chessInfo.suggestMovesIsRed.clear();
                activity.chessInfo.suggestMovesIsRed.addAll(isRedList);
                activity.chessInfo.suggestMoveNotations.clear();
                activity.chessInfo.suggestMoveNotations.addAll(notations);
                
                // 初始支招（未跟随走棋）一律实线红/黑，不使用虚线
                activity.chessInfo.suggestDashedStepIdx = -1;
                
                activity.chessInfo.suggestFromPos = null;
                activity.chessInfo.suggestToPos = null;
                
                LogUtils.i("PvMActivityAI", "多步支招生成完成，共 " + moves.size() + " 步");
                
            } catch (Exception e) {
                LogUtils.e("PvMActivityAI", "多步支招生成异常: " + e.getMessage());
            }
        }
        
        // 将走法转换为标准中文象棋记谱格式
        // 格式：棋子名称 + 起始纵线 + 走法（进/退/平） + 目标位置
        static String convertMoveToChineseNotation(Move move, int piece) {
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
        static String getPieceName(int piece) {
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
        static String getFileName(int x, boolean isRed) {
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
        static String getMoveType(Move move, boolean isRed) {
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
        static String getTargetPosition(Move move, int piece, boolean isRed) {
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
        static String getStepName(int steps, boolean isRed) {
            String[] redSteps = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
            String[] blackSteps = {"", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
            
            if (isRed) {
                return redSteps[steps];
            } else {
                return blackSteps[steps];
            }
        }
    }
    
    /**
     * 在按钮组底部框中逐条展示引擎多条候选变线：每行一条、单行不折行（可横向滑动）、按阵营着色、每行独立。
     * @param consumed   跟随模式下已走且与变线一致的着法数（非跟随模式传 0）
     * @param followMode 是否为跟随模式：仅显示与已走前缀一致的变线，整行高亮，并在第 5 步之后逐步揭示后续着法
     */
    void fillEngineResultBox(int consumed, boolean followMode) {
        try {
            if (this.activity == null || this.activity.engineResultContainer == null
                    || this.activity.pikafishAI == null || this.activity.chessInfo == null) {
                return;
            }
            final android.widget.LinearLayout container = this.activity.engineResultContainer;
            container.removeAllViews();
            // 记录每行内容视图，填充后统一把 minWidth 撑满整行宽度（斑马纹以行为准）
            final java.util.List<android.widget.LinearLayout> rowViews = new java.util.ArrayList<>();

            java.util.List<PikafishAI.PvSequenceWithScore> lines = this.activity.pikafishAI.getLastMultiPvLines();
            // 防御性排序：确保综合评分最高（含速胜“杀N”）的变线排在最前展示
            lines = new java.util.ArrayList<>(lines);
            java.util.Collections.sort(lines, (a, b) -> {
                int ea = rankLineScore(a), eb = rankLineScore(b);
                return Integer.compare(eb, ea);
            });
            if (lines == null || lines.isEmpty()) {
                this.activity.clearEngineResultBox();
                return;
            }

            // 已走且一致的着法前缀，用于筛选仍匹配的候选变线
            java.util.List<Move> prefix = this.activity.suggestFollowPrefix;

            // 记谱模拟基准局面：跟随模式用支招时刻快照（可回放已走步），否则用当前局面
            ChessInfo baseInfo = null;
            try {
                if (followMode && this.activity.suggestFollowStartInfo != null) {
                    baseInfo = (ChessInfo) this.activity.suggestFollowStartInfo.clone();
                } else {
                    baseInfo = (ChessInfo) this.activity.chessInfo.clone();
                }
            } catch (CloneNotSupportedException e) {
                baseInfo = null;
            }
            if (baseInfo == null) {
                this.activity.clearEngineResultBox();
                return;
            }
            final boolean baseIsRed = baseInfo.IsRedGo; // 评分归一化以支招时刻的行棋方为准

            // 红方走子用红色、黑方走子用青色；已走且一致的步用灰色；命中行整行高亮
            final int RED_MOVE_COLOR = 0xFFFF8A80;
            final int BLACK_MOVE_COLOR = 0xFF80D8FF;
            final int PLAYED_COLOR = 0xFF9AA7B4;
            final int HIGHLIGHT_BG = 0xFF8A6A45;

            int added = 0;
            for (int li = 0; li < lines.size() && added < 8; li++) {
                final int lineIndex = li;
                PikafishAI.PvSequenceWithScore line = lines.get(li);
                if (line == null || line.pvSequence == null || line.pvSequence.isEmpty()) {
                    continue;
                }
                if (followMode) {
                    // 跟随模式：仅显示与已走前缀一致、且仍有后续着法的变线
                    if (!lineMatchesPrefix(line, prefix)) continue;
                    if (line.pvSequence.size() <= consumed) continue;
                } else {
                    // 非最高评分的候选变线：步数不足 4 步的不展示（最高分变线始终显示）
                    if (li > 0 && line.pvSequence.size() < 4) continue;
                }

                int total = line.pvSequence.size();
                int revealed;
                if (followMode) {
                    // 前 5 步只显示 5 步；第 5 步之后每多走一步就揭示一步后续，直到显示到最后一步
                    revealed = Math.min(total, Math.max(PvMActivity.SIM_DISPLAY_STEPS, consumed + 1));
                } else {
                    revealed = Math.min(PvMActivity.SIM_DISPLAY_STEPS, total);
                }
                // 跟随模式：最多保留 SIM_DISPLAY_STEPS 步，超过后前面的逐步消失（滑窗）
                int windowStart = 0;
                if (followMode) {
                    windowStart = Math.max(0, revealed - PvMActivity.SIM_DISPLAY_STEPS);
                }

                // 模拟该变线，生成中文记谱与每步所属阵营（从基准局面开始独立推演）
                // 需从第 0 步完整推演以获得正确棋盘，但仅记录窗口内的着法记谱
                java.util.List<String> notations = new java.util.ArrayList<>();
                java.util.List<Boolean> isRedStep = new java.util.ArrayList<>();
                try {
                    ChessInfo sim = (ChessInfo) baseInfo.clone();
                    for (int i = 0; i < revealed; i++) {
                        Move mv = line.pvSequence.get(i);
                        if (mv == null || mv.fromPos == null || mv.toPos == null) break;
                        boolean redMove = sim.IsRedGo;
                        int piece = 0;
                        if (mv.fromPos.y >= 0 && mv.fromPos.y < 10 && mv.fromPos.x >= 0 && mv.fromPos.x < 9) {
                            piece = sim.piece[mv.fromPos.y][mv.fromPos.x];
                        }
                        if (i >= windowStart) {
                            // 仅窗口内的着法参与记谱展示
                            notations.add(ShowAIMoveUIRunnable.convertMoveToChineseNotation(mv, piece));
                            isRedStep.add(redMove);
                        }
                        if (mv.fromPos.y >= 0 && mv.fromPos.y < 10 && mv.fromPos.x >= 0 && mv.fromPos.x < 9
                                && mv.toPos.y >= 0 && mv.toPos.y < 10 && mv.toPos.x >= 0 && mv.toPos.x < 9) {
                            int mp = sim.piece[mv.fromPos.y][mv.fromPos.x];
                            sim.piece[mv.toPos.y][mv.toPos.x] = mp;
                            sim.piece[mv.fromPos.y][mv.fromPos.x] = 0;
                            sim.IsRedGo = !sim.IsRedGo;
                        }
                    }
                } catch (CloneNotSupportedException e) {
                    LogUtils.e("PvMActivityAI", "克隆棋盘失败: " + e.getMessage());
                }

                if (notations.isEmpty()) continue;

                final float density = this.activity.getResources().getDisplayMetrics().density;
                final int SCORE_COL_W = (int) (44 * density);
                final int MOVE_COL_W = (int) (60 * density);
                // 第一行（综合评分最高的变线）字号 +1，突出首选着法
                final float TEXT_SP = (added == 0) ? 14 : 13;

                android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this.activity);
                hsv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
                hsv.setHorizontalScrollBarEnabled(true);

                android.widget.LinearLayout colRow = new android.widget.LinearLayout(this.activity);
                colRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                colRow.setPadding(0, 0, 0, 0);
                if (followMode) {
                    colRow.setBackgroundColor(HIGHLIGHT_BG); // 命中行整行高亮
                } else {
                    colRow.setBackgroundColor((added % 2 == 0) ? 0xFF322619 : 0xFF4C3A29);
                }
                rowViews.add(colRow);

                // 评分列
                android.widget.TextView scoreTv = new android.widget.TextView(this.activity);
                scoreTv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        SCORE_COL_W, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
                scoreTv.setGravity(android.view.Gravity.CENTER);
                scoreTv.setSingleLine(true);
                scoreTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                scoreTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
                scoreTv.setTypeface(android.graphics.Typeface.MONOSPACE);
                scoreTv.setIncludeFontPadding(false);
                scoreTv.setPadding(0, 0, 0, 0);
                scoreTv.setTextColor(0xFFD6E4F0);
                String scoreStr;
                if (line.mateIn > 0) {
                    scoreStr = "杀" + line.mateIn;
                    scoreTv.setTextColor(0xFFFFD54F);
                } else if (line.mateIn < 0) {
                    scoreStr = "被杀" + Math.abs(line.mateIn);
                    scoreTv.setTextColor(0xFF9AA7B4);
                } else {
                    int normScore = PvMActivity.normalizeScore(line.score, baseIsRed);
                    scoreStr = normScore > 0 ? "+" + normScore : String.valueOf(normScore);
                }
                scoreTv.setText(scoreStr);
                colRow.addView(scoreTv);

                // 每一步一列（固定宽度、居中、按阵营着色；已走且一致的步用灰色）
                for (int i = 0; i < notations.size(); i++) {
                    android.widget.TextView mvTv = new android.widget.TextView(this.activity);
                    mvTv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                            MOVE_COL_W, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
                    mvTv.setGravity(android.view.Gravity.CENTER);
                    mvTv.setSingleLine(true);
                    mvTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    mvTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
                    mvTv.setTypeface(android.graphics.Typeface.MONOSPACE);
                    mvTv.setPadding(0, 0, 0, 0);
                    mvTv.setIncludeFontPadding(false);
                    mvTv.setText(notations.get(i));
                    int color;
                    // 已走且一致的步用灰色显示（考虑滑窗偏移：窗口内第 i 列对应变线第 windowStart+i 步）
                    if (followMode && windowStart + i < consumed) {
                        color = PLAYED_COLOR;
                    } else {
                        color = isRedStep.get(i) ? RED_MOVE_COLOR : BLACK_MOVE_COLOR;
                    }
                    mvTv.setTextColor(color);
                    colRow.addView(mvTv);
                }

                hsv.setTag(lineIndex);
                colRow.setTag(lineIndex);
                android.view.View.OnClickListener simClick = v -> {
                    if (this.activity != null) {
                        this.activity.startSimulation(lineIndex);
                    }
                };
                colRow.setOnClickListener(simClick);
                hsv.setOnClickListener(simClick);
                hsv.addView(colRow);
                container.addView(hsv);
                added++;
            }

            if (added == 0) {
                this.activity.clearEngineResultBox();
                return;
            }
            if (this.activity.engineResultScroll != null) {
                this.activity.engineResultScroll.setVisibility(android.view.View.VISIBLE);
            }

            container.post(() -> {
                try {
                    int fullW = container.getWidth();
                    if (fullW <= 0) return;
                    for (android.widget.LinearLayout row : rowViews) {
                        row.setMinimumWidth(fullW);
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            LogUtils.e("PvMActivityAI", "填充引擎结果框异常: " + e.getMessage());
        }
    }

    /**
     * 处理一步实际走子与支招候选变线的匹配（人机通用）。
     * 若该步与某条仍匹配的候选变线的下一着一致，则保留结果框、高亮命中行并揭示后续；
     * 否则视为偏离，清除支招结果框，恢复常规行为。
     */
    public void handleMoveForSuggestFollow(Move played) {
        if (this.activity == null || !this.activity.suggestFollowActive) {
            return;
        }
        java.util.List<PikafishAI.PvSequenceWithScore> lines =
                this.activity.pikafishAI != null ? this.activity.pikafishAI.getLastMultiPvLines() : null;
        if (lines == null || lines.isEmpty() || played == null
                || played.fromPos == null || played.toPos == null) {
            clearSuggestFollow();
            return;
        }
        int consumed = this.activity.suggestFollowPrefix.size();
        boolean anyMatch = false;
        for (PikafishAI.PvSequenceWithScore line : lines) {
            if (line == null || line.pvSequence == null) continue;
            if (!lineMatchesPrefix(line, this.activity.suggestFollowPrefix)) continue;
            if (line.pvSequence.size() <= consumed) continue;
            if (movesEqual(line.pvSequence.get(consumed), played)) {
                anyMatch = true;
                break;
            }
        }
        if (!anyMatch) {
            clearSuggestFollow();
            return;
        }
        this.activity.suggestFollowPrefix.add(played);
        // 重合命中：把命中的那一路显示到头部「支招」位置（复用现有彩色支招显示），不再显示在下方结果框
        fillSuggestFollowInHeader(this.activity.suggestFollowPrefix.size());
    }

    /**
     * 跟随命中后：将命中的那一路走法显示到头部「支招」位置（彩色多步），并隐藏下方结果框。
     * 记谱推演与揭示窗口（revealed/windowStart 滑窗）逻辑与 fillEngineResultBox(followMode=true) 保持一致。
     */
    private void fillSuggestFollowInHeader(int consumed) {
        try {
            if (this.activity == null || this.activity.roundView == null
                    || this.activity.pikafishAI == null || this.activity.chessInfo == null) {
                return;
            }
            java.util.List<PikafishAI.PvSequenceWithScore> lines =
                    this.activity.pikafishAI.getLastMultiPvLines();
            if (lines == null || lines.isEmpty()) {
                return;
            }
            // 与下方结果框一致：按综合评分排序，最优匹配变线优先
            lines = new java.util.ArrayList<>(lines);
            java.util.Collections.sort(lines, (a, b) -> Integer.compare(rankLineScore(b), rankLineScore(a)));

            java.util.List<Move> prefix = this.activity.suggestFollowPrefix;
            ChessInfo baseInfo;
            try {
                baseInfo = this.activity.suggestFollowStartInfo != null
                        ? (ChessInfo) this.activity.suggestFollowStartInfo.clone()
                        : (ChessInfo) this.activity.chessInfo.clone();
            } catch (CloneNotSupportedException e) {
                return;
            }
            if (baseInfo == null) return;

            // 选出命中的那一路（最优的、仍与已走前缀一致且有后续的变线）
            PikafishAI.PvSequenceWithScore chosen = null;
            for (PikafishAI.PvSequenceWithScore line : lines) {
                if (line == null || line.pvSequence == null || line.pvSequence.isEmpty()) continue;
                if (!lineMatchesPrefix(line, prefix)) continue;
                if (line.pvSequence.size() <= consumed) continue;
                chosen = line;
                break;
            }
            if (chosen == null) return;

            int total = chosen.pvSequence.size();
            // 头部支招「只显示五步」：以当前进行位置为基准截取最多 5 步；
            // 当用户进行到窗口最后一步并继续时，窗口向前滚动，原第 2 步变为第 1 步。
            final int WINDOW_STEPS = 5;
            int windowStart = Math.max(0, consumed - (WINDOW_STEPS - 1));
            int revealed = Math.min(total, windowStart + WINDOW_STEPS);

            // 从基准局面推演该变线，生成窗口内每步的中文记谱、所属阵营与是否已走（置灰用）
            java.util.List<String> notations = new java.util.ArrayList<>();
            java.util.List<Boolean> isRedStep = new java.util.ArrayList<>();
            java.util.List<Boolean> isPlayedStep = new java.util.ArrayList<>();
            try {
                ChessInfo sim = (ChessInfo) baseInfo.clone();
                for (int i = 0; i < revealed; i++) {
                    Move mv = chosen.pvSequence.get(i);
                    if (mv == null || mv.fromPos == null || mv.toPos == null) break;
                    boolean redMove = sim.IsRedGo;
                    int piece = 0;
                    if (mv.fromPos.y >= 0 && mv.fromPos.y < 10 && mv.fromPos.x >= 0 && mv.fromPos.x < 9) {
                        piece = sim.piece[mv.fromPos.y][mv.fromPos.x];
                    }
                    if (i >= windowStart) {
                        notations.add(ShowAIMoveUIRunnable.convertMoveToChineseNotation(mv, piece));
                        isRedStep.add(redMove);
                        isPlayedStep.add(i < consumed); // 已走的前缀步置灰
                    }
                    if (mv.fromPos.y >= 0 && mv.fromPos.y < 10 && mv.fromPos.x >= 0 && mv.fromPos.x < 9
                            && mv.toPos.y >= 0 && mv.toPos.y < 10 && mv.toPos.x >= 0 && mv.toPos.x < 9) {
                        int mp = sim.piece[mv.fromPos.y][mv.fromPos.x];
                        sim.piece[mv.toPos.y][mv.toPos.x] = mp;
                        sim.piece[mv.fromPos.y][mv.fromPos.x] = 0;
                        sim.IsRedGo = !sim.IsRedGo;
                    }
                }
            } catch (CloneNotSupportedException e) {
                return;
            }
            if (notations.isEmpty()) return;

            // 显示到头部「支招」位置（彩色多步，红/黑按阵营着色，符合头部风格），并隐藏下方结果框
            this.activity.roundView.setBestMoveText("");   // 先清「最优一步」，避免与彩色多步在同一行重叠
            this.activity.roundView.setSuggestMoveTextWithColor(notations, isRedStep, isPlayedStep);
            this.activity.clearEngineResultBox();

            // 棋盘同步：把命中路的「下一步要走的棋子」以虚线画在棋盘上（与头部置灰逻辑一致）。
            // 从当前局面（已走 consumed 步）取后续 2 步（下一步 + 对方应对），与初始支招的 2 步风格一致，
            // 其中第 1 步（下一步）用虚线提示。
            try {
                ChessInfo boardSim = (ChessInfo) baseInfo.clone();
                java.util.List<Move> boardMoves = new java.util.ArrayList<>();
                java.util.List<String> boardLabels = new java.util.ArrayList<>();
                java.util.List<Boolean> boardIsRed = new java.util.ArrayList<>();
                // 快进到当前局面
                for (int i = 0; i < consumed && i < chosen.pvSequence.size(); i++) {
                    Move mv = chosen.pvSequence.get(i);
                    if (mv == null || mv.fromPos == null || mv.toPos == null) break;
                    if (mv.fromPos.y >= 0 && mv.fromPos.y < 10 && mv.fromPos.x >= 0 && mv.fromPos.x < 9
                            && mv.toPos.y >= 0 && mv.toPos.y < 10 && mv.toPos.x >= 0 && mv.toPos.x < 9) {
                        int mp = boardSim.piece[mv.fromPos.y][mv.fromPos.x];
                        boardSim.piece[mv.toPos.y][mv.toPos.x] = mp;
                        boardSim.piece[mv.fromPos.y][mv.fromPos.x] = 0;
                        boardSim.IsRedGo = !boardSim.IsRedGo;
                    }
                }
                int end = Math.min(chosen.pvSequence.size(), consumed + 2);
                int stepNo = 1;
                for (int i = consumed; i < end; i++) {
                    Move mv = chosen.pvSequence.get(i);
                    if (mv == null || mv.fromPos == null || mv.toPos == null) break;
                    boardMoves.add(mv);
                    boardLabels.add(String.valueOf(stepNo));
                    boardIsRed.add(boardSim.IsRedGo);
                    stepNo++;
                    if (mv.fromPos.y >= 0 && mv.fromPos.y < 10 && mv.fromPos.x >= 0 && mv.fromPos.x < 9
                            && mv.toPos.y >= 0 && mv.toPos.y < 10 && mv.toPos.x >= 0 && mv.toPos.x < 9) {
                        int mp = boardSim.piece[mv.fromPos.y][mv.fromPos.x];
                        boardSim.piece[mv.toPos.y][mv.toPos.x] = mp;
                        boardSim.piece[mv.fromPos.y][mv.fromPos.x] = 0;
                        boardSim.IsRedGo = !boardSim.IsRedGo;
                    }
                }
                if (!boardMoves.isEmpty() && this.activity.chessInfo != null) {
                    this.activity.chessInfo.suggestMoves.clear();
                    this.activity.chessInfo.suggestMoves.addAll(boardMoves);
                    this.activity.chessInfo.suggestMoveLabels.clear();
                    this.activity.chessInfo.suggestMoveLabels.addAll(boardLabels);
                    this.activity.chessInfo.suggestMovesIsRed.clear();
                    this.activity.chessInfo.suggestMovesIsRed.addAll(boardIsRed);
                    this.activity.chessInfo.suggestDashedStepIdx = 0; // 进入跟随模式：棋盘后续步全部虚线，区别于初次支招实线
                    if (this.activity.chessView != null) this.activity.chessView.requestDraw();
                }
            } catch (CloneNotSupportedException e) {
                LogUtils.e("PvMActivityAI", "棋盘同步跟随支招异常: " + e.getMessage());
            }
        } catch (Exception e) {
            LogUtils.e("PvMActivityAI", "头部显示跟随支招异常: " + e.getMessage());
        }
    }

    /** 结束跟随支招并清除结果框 */
    private void clearSuggestFollow() {
        if (this.activity == null) return;
        // 清除棋盘上的跟随虚线提示，恢复常规状态
        if (this.activity.chessInfo != null) {
            this.activity.chessInfo.suggestDashedStepIdx = -1;
            if (this.activity.chessInfo.suggestMoves != null) {
                this.activity.chessInfo.suggestMoves.clear();
            }
            if (this.activity.chessInfo.suggestMoveLabels != null) {
                this.activity.chessInfo.suggestMoveLabels.clear();
            }
            if (this.activity.chessInfo.suggestMovesIsRed != null) {
                this.activity.chessInfo.suggestMovesIsRed.clear();
            }
            if (this.activity.chessView != null) this.activity.chessView.requestDraw();
        }
        if (this.activity.gameManager != null) {
            this.activity.gameManager.clearSuggest(); // 内部会重置跟随状态并清空结果框
        } else {
            this.activity.suggestFollowActive = false;
            this.activity.suggestFollowPrefix.clear();
            this.activity.suggestFollowStartInfo = null;
            this.activity.clearEngineResultBox();
        }
    }

    /** 两步着法是否相同（按起止坐标比较） */
    private static boolean movesEqual(Move a, Move b) {
        return a != null && b != null && a.fromPos != null && a.toPos != null
                && b.fromPos != null && b.toPos != null
                && a.fromPos.x == b.fromPos.x && a.fromPos.y == b.fromPos.y
                && a.toPos.x == b.toPos.x && a.toPos.y == b.toPos.y;
    }

    /** 候选变线的前若干着是否与已走前缀完全一致 */
    private static boolean lineMatchesPrefix(PikafishAI.PvSequenceWithScore line, java.util.List<Move> prefix) {
        if (line == null || line.pvSequence == null) return false;
        if (prefix == null || prefix.isEmpty()) return true;
        if (line.pvSequence.size() < prefix.size()) return false;
        for (int i = 0; i < prefix.size(); i++) {
            if (!movesEqual(line.pvSequence.get(i), prefix.get(i))) return false;
        }
        return true;
    }

    /** 候选变线综合排序分值（与 PikafishAI.rankPvLine 一致）：杀棋最高、被将死最低、其余取评分 */
    private static int rankLineScore(PikafishAI.PvSequenceWithScore line) {
        if (line == null) return Integer.MIN_VALUE;
        if (line.mateIn > 0) return 1_000_000 - line.mateIn;
        if (line.mateIn < 0) return -1_000_000 - line.mateIn;
        return line.score;
    }

    public void showAIMove(final boolean isRed) {
        if (isAIAnalyzing) {
            return;
        }
        
        if (this.activity == null || this.activity.chessInfo == null || this.activity.chessView == null) {
            return;
        }
        
        // 立即在UI线程显示"AI正在思考"动画，并清除旧的支招信息
        if (this.activity.roundView != null) {
            this.activity.runOnUiThread(() -> {
                this.activity.roundView.setSuggestMoveText("");
                this.activity.clearEngineResultBox();
                this.activity.roundView.setSuggestMode(true);
            });
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
        private final int myGeneration;

        public DepthUpdateRunnable(PvMActivityAI aiInstance, boolean isRed) {
            this.aiInstance = aiInstance;
            this.isRed = isRed;
            // 捕获启动时的代际，用于判断本次分析是否仍有效
            this.myGeneration = aiInstance.aiGeneration.get();
        }

        @Override
        public void run() {
            if (aiInstance != null && aiInstance.activity != null && aiInstance.activity.roundView != null) {
                // AI 不在分析中、或代际已变化（分析已结束/被新分析取代）时，
                // 直接清空深度并重置思考动画，避免残留动画
                if (!aiInstance.isAIAnalyzing || aiInstance.aiGeneration.get() != myGeneration) {
                    aiInstance.activity.roundView.setSearchDepth(0, isRed);
                    return;
                }
                int currentDepth = 0;
                if (aiInstance.activity.pikafishAI != null) {
                    currentDepth = aiInstance.activity.pikafishAI.getCurrentDepth();
                }
                // 二次校验：getCurrentDepth 是 JNI 调用，期间分析可能已结束并递增代际，
                // 必须在写入 RoundView 前再次确认仍在分析中且代际未变，
                // 否则会把"思考中"状态写死，导致 AI 已停止但动画仍在动
                if (!aiInstance.isAIAnalyzing || aiInstance.aiGeneration.get() != myGeneration) {
                    aiInstance.activity.roundView.setSearchDepth(0, isRed);
                    return;
                }
                // 深度为 0 时引擎刚启动尚未返回真实深度：仅维持"思考中"动画，
                // 不写入占位深度 1，避免搜索被中断时占位值 1 被误当作最终深度显示
                if (currentDepth == 0) {
                    aiInstance.activity.roundView.markThinking(isRed);
                } else {
                    aiInstance.activity.roundView.setSearchDepth(currentDepth, isRed);
                }
            }
        }
    }
    
    private void startAISearch(boolean isRed) {
        if (this.activity != null) {
            // AI 不在分析中时，不启动深度更新任务，避免残留动画
            if (!isAIAnalyzing) {
                return;
            }
            if (this.depthUpdateFuture != null) {
                this.depthUpdateFuture.cancel(true);
            }
            // 减少深度更新频率，从500ms改为1000ms，避免频繁更新UI
            this.depthUpdateFuture = this.scheduledExecutorService.scheduleAtFixedRate(new DepthUpdateRunnable(this, isRed), 0, 1000, TimeUnit.MILLISECONDS);
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
    
    // 停止AI分析
    public void stopAIAnalysis() {
        finishAnalyzing();
        // 中断引擎搜索，释放 searchLock，避免后续 AI 计算被阻塞
        if (activity != null && activity.pikafishAI != null) {
            activity.pikafishAI.interrupt();
        }
        // 取消深度更新任务
        stopAISearch();
        // 立即清除 AI 思考动画和支招模式，确保中断后动画不残留
        if (activity != null && activity.roundView != null) {
            activity.runOnUiThread(() -> {
                if (activity.roundView != null) {
                    activity.roundView.setSearchDepth(0, false);
                    activity.roundView.setSuggestMode(false);
                }
            });
        }
    }

    private boolean tryStartAnalyzing() {
        boolean started = aiAnalyzingState.compareAndSet(false, true);
        if (started) {
            isAIAnalyzing = true;
            aiGeneration.incrementAndGet();  // 递增代际
            aiInterrupted.set(false);  // 重置中断标志
            // 支招按钮变为"中断"状态（UI 线程更新）
            notifySuggestButton(true);
        }
        return started;
    }

    private void finishAnalyzing() {
        if (aiAnalyzingState.compareAndSet(true, false)) {
            isAIAnalyzing = false;
            aiInterrupted.set(true);
            // 结束分析时递增代际，使仍在进行中的旧深度更新任务失效，避免残留"思考中"动画
            aiGeneration.incrementAndGet();
            // 恢复支招按钮为"支招"状态
            notifySuggestButton(false);
        }
    }

    /**
     * 只有当前线程仍持有分析状态时才释放。
     * 避免误清 stopAIAnalysis 后新启动的 AI 状态。
     */
    private void tryFinishAnalyzing(int myGeneration) {
        if (myGeneration == aiGeneration.get() && aiAnalyzingState.compareAndSet(true, false)) {
            isAIAnalyzing = false;
            // 结束分析时递增代际，使仍在进行中的旧深度更新任务失效，避免残留"思考中"动画
            aiGeneration.incrementAndGet();
            // 恢复支招按钮为"支招"状态
            notifySuggestButton(false);
        }
    }

    // 通知 UI 线程更新支招按钮显示
    private void notifySuggestButton(boolean analyzing) {
        if (activity == null) return;
        final PvMActivityControls controls = activity.controlsManager;
        if (controls == null) return;
        activity.runOnUiThread(() -> controls.updateSuggestButton(analyzing));
    }

    /** 重置开局库（新对局 / 切换到双机对战时调用，重新随机选取一个开局）。 */
    public void resetOpeningBook() {
        if (openingBook != null) {
            openingBook.reset();
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