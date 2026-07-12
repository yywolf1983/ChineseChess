package AICore;

import android.content.Context;
import android.util.Log;

import Info.ChessInfo;
import Info.Pos;
import ChessMove.Move;
import top.nones.chessgame.PvMActivity;
import ChessMove.Rule;
import Utils.LogUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class PikafishAI {
    private static final int DEFAULT_DEPTH = 20;
    private static final int DEFAULT_TIME_MS = 10000;
    private static final int MIN_DEPTH = 5;
    private static final int MIN_TIME_MS = 1000;
    private static final long INIT_TIMEOUT_MS = 5000;
    private static final long MAX_SEARCH_TIME_BUFFER_MS = 5000;

    // ========== JNI Native 方法 ==========
    private static native boolean nativeInit(String nnuePath, String libPath);
    private static native void nativeSendCommand(String command);
    private static native String nativeReadLine();
    private static native void nativeCleanup();

    static {
        try {
            System.loadLibrary("pikafish");
            LogUtils.i("PikafishAI", "libpikafish.so 加载成功 (JNI)");
        } catch (UnsatisfiedLinkError e) {
            LogUtils.e("PikafishAI", "加载 libpikafish.so 失败: " + e.getMessage());
        }
    }

    // ========== 状态字段 ==========
    private boolean initialized = false;
    private Context context;
    private final java.util.concurrent.atomic.AtomicBoolean isSearching = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean shouldStop = new java.util.concurrent.atomic.AtomicBoolean(false);
    private int currentDepth = 0;

    // ========== 缓存设置 ==========
    private int cachedSkillLevel = 20;   // 技能级别 (1-20, 20=满血)
    private int cachedDepth = 10;        // 搜索深度
    private int cachedMultiPV = 1;       // 多主变
    private int cachedTimeSeconds = 5;   // 思考时间（秒）
    private int cachedThreads = 1;       // 线程数
    private int cachedHashMB = 128;      // 哈希表 (MB)

    // 输出读取线程
    private Thread outputReaderThread;
    private final BlockingQueue<String> outputQueue = new LinkedBlockingQueue<>();
    private volatile boolean readerRunning = false;

    // ========== 初始化回调 ==========
    public interface InitializationListener {
        void onInitializationStarted();
        void onInitializationCompleted();
        void onInitializationFailed();
    }

    private InitializationListener initListener = null;

    public void setInitializationListener(InitializationListener listener) {
        this.initListener = listener;
    }

    // ========== MoveWithScore / PvSequenceWithScore ==========
    public static class MoveWithScore {
        public Move move;
        public int score;
        public MoveWithScore(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }

    public static class PvSequenceWithScore {
        public java.util.List<Move> pvSequence;
        public int score;
        public PvSequenceWithScore(java.util.List<Move> pvSequence, int score) {
            this.pvSequence = pvSequence;
            this.score = score;
        }
    }

    // ========== 构造函数 ==========
    public PikafishAI(Context context) {
        this.context = context;
        new Thread(() -> initialize()).start();
    }

    // ========== 初始化 ==========
    private void initialize() {
        initialized = false;
        readerRunning = false;
        outputQueue.clear();

        if (initListener != null) {
            runOnUiThread(() -> initListener.onInitializationStarted());
        }

        try {
            // 1. 复制 NNUE 文件到缓存目录
            String nnuePath = copyNNUEFile();
            if (nnuePath == null) {
                notifyInitFailed();
                return;
            }

            // 2. 获取 native library 目录
            String libPath = context.getApplicationInfo().nativeLibraryDir;

            // 3. 启动输出读取线程
            startOutputReaderThread();

            // 4. 调用 JNI 初始化
            boolean initOk = nativeInit(nnuePath, libPath);

            if (!initOk) {
                LogUtils.e("PikafishAI", "nativeInit 返回 false");
                notifyInitFailed();
                return;
            }

            // 5. 等待 uciok（nativeInit 成功后应该很快收到）
            String line = readLineWithTimeout(INIT_TIMEOUT_MS);
            if (line == null || !line.equals("uciok")) {
                LogUtils.e("PikafishAI", "等待 uciok 超时, 收到: " + line);
                notifyInitFailed();
                return;
            }

            initialized = true;
            LogUtils.i("PikafishAI", "JNI 引擎初始化成功");

            // 6. 设置参数
            setupEngineOptions();

            // 7. 发送 isready 确认
            nativeSendCommand("isready");
            line = readLineWithTimeout(INIT_TIMEOUT_MS);
            if (line == null || !line.equals("readyok")) {
                LogUtils.e("PikafishAI", "等待 readyok 超时, 收到: " + line);
            }

            if (initListener != null) {
                runOnUiThread(() -> initListener.onInitializationCompleted());
            }
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "初始化异常: " + e.getMessage(), e);
            notifyInitFailed();
        }
    }

    private void setupEngineOptions() {
        // 从设置读取用户参数
        readSettingsFromFile();

        // 1. 线程数（自动计算）
        int threadCount = cachedThreads > 0 ? cachedThreads : computeAutoThreads();
        cachedThreads = threadCount;
        nativeSendCommand("setoption name Threads value " + threadCount);

        // 2. 哈希表（自动计算）
        int hashSize = cachedHashMB > 0 ? cachedHashMB : getOptimalHashSize();
        cachedHashMB = hashSize;
        nativeSendCommand("setoption name Hash value " + hashSize);

        // 3. EvalFile 已在 nativeInit 中通过绝对路径设置，这里不再重复发送

        // 4. MultiPV
        nativeSendCommand("setoption name MultiPV value " + Math.max(1, cachedMultiPV));

        // 5. Clear Hash：新游戏时清空
        nativeSendCommand("setoption name Clear Hash");

        LogUtils.i("PikafishAI",
            "引擎参数: Threads=" + threadCount + " Hash=" + hashSize
            + "MB MultiPV=" + cachedMultiPV);
    }

    /** 从 SharedPreferences 读取引擎内部参数到缓存（仅限自动配置项，不覆盖用户设置） */
    private void readSettingsFromFile() {
        try {
            Info.Setting setting = Utils.GameResourceManager.getInstance().getSetting();
            if (setting != null) {
                // 只读取引擎内部参数（0=自动）；depth/skillLevel/multiPV/mLevel 由调用方显式传入
                cachedThreads = setting.threads;
                cachedHashMB = setting.hashMB;
            }
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "读取设置失败: " + e.getMessage());
        }
    }

    /** 根据设备核心数自动计算线程数 */
    private int computeAutoThreads() {
        int totalCores = Runtime.getRuntime().availableProcessors();
        int reservedCores = totalCores <= 4 ? 2 : (totalCores <= 8 ? 3 : 4);
        return Math.max(1, totalCores - reservedCores);
    }

    /**
     * 根据 skillLevel 计算削弱因子
     * skillLevel 1-20: 20=满血(1.0x), 10=半血(0.5x), 1=最弱(0.25x)
     */
    private float getSkillFactor() {
        // 非线性映射让低级别明显变弱：skillLevel²/400 在20级时=1.0，1级时=0.0025
        // 改用线性+保底：skillLevel 20→1.0, 1→0.25
        return 0.25f + 0.75f * (cachedSkillLevel / 20.0f);
    }

    /**
     * 获取技能削弱后的搜索深度
     */
    private int getEffectiveDepth() {
        float factor = getSkillFactor();
        int eff = Math.round(cachedDepth * factor);
        return Math.max(MIN_DEPTH, eff);
    }

    /**
     * 获取技能削弱后的思考时间（毫秒）
     */
    private int getEffectiveTimeMs() {
        float factor = getSkillFactor();
        int baseMs = cachedTimeSeconds * 1000;
        int eff = Math.round(baseMs * factor);
        return Math.max(MIN_TIME_MS, eff);
    }

    /**
     * 供外部使用的有效思考时间（已含 skillLevel 削弱，单位毫秒）
     * 调用方可以用此值作为超时计算的基准
     */
    public int getEffectiveThinkTimeMs() {
        return getEffectiveTimeMs();
    }

    private void notifyInitFailed() {
        if (initListener != null) {
            runOnUiThread(() -> initListener.onInitializationFailed());
        }
    }

    private void runOnUiThread(Runnable action) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(action);
        } else {
            action.run();
        }
    }

    // ========== 输出读取线程 ==========
    private void startOutputReaderThread() {
        readerRunning = true;
        outputReaderThread = new Thread(() -> {
            LogUtils.i("PikafishAI", "输出读取线程启动");
            while (readerRunning) {
                try {
                    String line = nativeReadLine();
                    if (line == null) {
                        // EOF (引擎关闭)
                        LogUtils.i("PikafishAI", "输出读取线程: 收到 EOF");
                        break;
                    }
                    outputQueue.offer(line);
                } catch (Exception e) {
                    if (readerRunning) {
                        LogUtils.e("PikafishAI", "输出读取异常: " + e.getMessage());
                    }
                    break;
                }
            }
            LogUtils.i("PikafishAI", "输出读取线程退出");
        }, "pikafish-output-reader");
        outputReaderThread.setDaemon(true);
        outputReaderThread.start();
    }

    // ========== 带超时的行读取 ==========
    private String readLineWithTimeout(long timeoutMs) {
        try {
            String line = outputQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            return line;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ========== NNUE 文件复制 ==========
    private String copyNNUEFile() {
        try {
            File cacheDir = context.getCacheDir();
            File nnueFile = new File(cacheDir, "pikafish.nnue");

            // 如果已存在且大小正确，直接返回
            if (nnueFile.exists()) {
                LogUtils.i("PikafishAI", "NNUE 文件已存在: " + nnueFile.getAbsolutePath());
                return nnueFile.getAbsolutePath();
            }

            InputStream is = context.getAssets().open("pikafish.nnue");
            FileOutputStream os = new FileOutputStream(nnueFile);
            byte[] buffer = new byte[8192];
            int length;
            long totalBytes = 0;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
                totalBytes += length;
            }
            is.close();
            os.close();
            LogUtils.i("PikafishAI", "NNUE 文件复制成功: " + totalBytes + " bytes -> " + nnueFile.getAbsolutePath());
            return nnueFile.getAbsolutePath();
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "复制 NNUE 文件失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 清空哈希表（新游戏时应调用）
     */
    public void clearHash() {
        if (initialized) {
            nativeSendCommand("setoption name Clear Hash");
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            LogUtils.i("PikafishAI", "哈希表已清空");
        }
    }

    /**
     * 在每次搜索前同步 chessInfo.setting 到本地缓存
     */
    private void syncSettingsFromChessInfo(ChessInfo chessInfo) {
        if (chessInfo == null || chessInfo.setting == null) return;
        Info.Setting s = chessInfo.setting;
        cachedSkillLevel = Math.max(1, Math.min(20, s.skillLevel));
        cachedDepth = Math.max(MIN_DEPTH, s.depth);
        cachedMultiPV = Math.max(1, s.multiPV);
        cachedTimeSeconds = Math.max(1, Math.min(60, s.mLevel));
        cachedThreads = s.threads;
        cachedHashMB = s.hashMB;
    }

    // ========== 哈希表大小计算 ==========
    private int getOptimalHashSize() {
        try {
            long maxMemory = Runtime.getRuntime().maxMemory();
            int maxMemoryMB = (int) (maxMemory / (1024 * 1024));
            if (maxMemoryMB >= 2048) return 512;
            else if (maxMemoryMB >= 1024) return 256;
            else if (maxMemoryMB >= 512) return 128;
            else return 64;
        } catch (Exception e) {
            return 128;
        }
    }

    // ========== 公共 API ==========

    public boolean isInitialized() {
        return initialized;
    }

    public int getCurrentDepth() {
        return currentDepth;
    }

    public Move getBestMove(ChessInfo chessInfo) {
        return getBestMoveWithScore(chessInfo).move;
    }

    /**
     * 获取最佳走法和评分（核心搜索方法）
     */
    public MoveWithScore getBestMoveWithScore(ChessInfo chessInfo) {
        if (!initialized) {
            LogUtils.e("PikafishAI", "AI 未初始化");
            return new MoveWithScore(getDefaultMove(chessInfo), 0);
        }

        try {
            // 停止之前的搜索
            stopPreviousSearch();
            // 同步最新设置
            syncSettingsFromChessInfo(chessInfo);

            shouldStop.set(false);
            isSearching.set(true);
            currentDepth = 0;

            // 生成 FEN 并设置局面
            String fen = boardToFEN(chessInfo);
            LogUtils.i("PikafishAI", "FEN: " + fen);
            nativeSendCommand("position fen " + fen);

            // 计算搜索参数（从缓存设置 + skillLevel 削弱）
            // 双限制：时间为主，时间限制内到达层数就以层数为准（go depth X movetime Y，先到为准）
            int time = getEffectiveTimeMs();
            int effDepth = getEffectiveDepth();

            // 强制变着模式
            boolean wasForceVariation = false;
            if (chessInfo != null && chessInfo.forceVariation) {
                wasForceVariation = true;
                nativeSendCommand("setoption name MultiPV value 3");
                LogUtils.i("PikafishAI", "强制变着模式: depth=" + effDepth + " movetime=" + time + "ms");
            } else {
                nativeSendCommand("setoption name MultiPV value " + cachedMultiPV);
            }

            // 启动搜索：双限制，谁先到就以谁为准
            LogUtils.i("PikafishAI", "go depth " + effDepth + " movetime " + time
                + " (skillLevel=" + cachedSkillLevel + " 原始depth=" + cachedDepth
                + " 原始time=" + cachedTimeSeconds + "s)");
            nativeSendCommand("go depth " + effDepth + " movetime " + time);

            // 读取搜索结果
            String bestMoveStr = null;
            List<String> possibleMoves = new ArrayList<>();
            int score = 0;
            long startTime = System.currentTimeMillis();
            long maxSearchTime = time + MAX_SEARCH_TIME_BUFFER_MS;

            while (!Thread.currentThread().isInterrupted()) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > maxSearchTime) {
                    LogUtils.w("PikafishAI", "搜索超时, 强制停止");
                    nativeSendCommand("stop");
                    break;
                }

                if (shouldStop.get() && bestMoveStr == null) {
                    nativeSendCommand("stop");
                }

                String line = readLineWithTimeout(100);
                if (line == null) {
                    if (shouldStop.get()) break;
                    continue;
                }

                if (line.startsWith("info")) {
                    String[] parts = line.split(" ");
                    int infoMultiPV = 1;
                    int infoDepth = 0;
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("multipv") && i + 1 < parts.length) {
                            try { infoMultiPV = Integer.parseInt(parts[i + 1]); } catch (NumberFormatException ignored) {}
                            // Don't break - continue parsing other fields
                        }
                    }

                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("depth") && i + 1 < parts.length) {
                            try {
                                int newDepth = Integer.parseInt(parts[i + 1]);
                                infoDepth = newDepth;
                                if (newDepth > currentDepth) {
                                    currentDepth = newDepth;
                                }
                            } catch (NumberFormatException ignored) {}
                        } else if (infoMultiPV == 1 && parts[i].equals("score") && i + 2 < parts.length) {
                            if (parts[i + 1].equals("cp")) {
                                try { score = Integer.parseInt(parts[i + 2]); } catch (NumberFormatException ignored) {}
                            } else if (parts[i + 1].equals("mate")) {
                                try {
                                    int mateIn = Integer.parseInt(parts[i + 2]);
                                    score = mateIn > 0 ? 1000 - mateIn * 10 : -1000 + mateIn * 10;
                                } catch (NumberFormatException ignored) {}
                            }
                        } else if (infoMultiPV == 1 && parts[i].equals("pv") && i + 1 < parts.length) {
                            if (bestMoveStr == null) {
                                bestMoveStr = parts[i + 1];
                            }
                        } else if (parts[i].equals("pv") && i + 1 < parts.length) {
                            if (chessInfo != null && chessInfo.forceVariation) {
                                String move = parts[i + 1];
                                if (!possibleMoves.contains(move)) {
                                    possibleMoves.add(move);
                                }
                            }
                        } else if (infoMultiPV == 1 && parts[i].equals("wdl") && i + 3 < parts.length) {
                            // WDL: win draw loss per-mille (e.g. "wdl 451 320 229")
                            try {
                                int w = Integer.parseInt(parts[i + 1]);
                                int d = Integer.parseInt(parts[i + 2]);
                                int l = Integer.parseInt(parts[i + 3]);
                                if (infoDepth > 0) {
                                    LogUtils.v("PikafishAI", "depth=" + infoDepth + " WDL: " + w + "/" + d + "/" + l);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                } else if (line.startsWith("bestmove")) {
                    String[] parts = line.split(" ");
                    if (parts.length > 1) {
                        bestMoveStr = parts[1];
                    }
                    break;
                }
            }

            // 强制变着：随机选非最佳走法
            if (wasForceVariation && bestMoveStr != null && !possibleMoves.isEmpty()) {
                List<String> alternatives = new ArrayList<>();
                for (String m : possibleMoves) {
                    if (!m.equals(bestMoveStr)) alternatives.add(m);
                }
                if (!alternatives.isEmpty()) {
                    bestMoveStr = alternatives.get(new java.util.Random().nextInt(alternatives.size()));
                    LogUtils.i("PikafishAI", "强制变着: 随机选择 " + bestMoveStr);
                }
            }

            // 更新 UI 深度
            updateUIAfterSearch(currentDepth);

            if (bestMoveStr != null) {
                Move move = uciToMove(bestMoveStr);
                LogUtils.i("PikafishAI", "最佳走法: " + move + ", 评分: " + score);
                return new MoveWithScore(move, score);
            }
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "搜索异常: " + e.getMessage(), e);
        } finally {
            isSearching.set(false);
        }

        return new MoveWithScore(getDefaultMove(chessInfo), 0);
    }

    /**
     * 快速评估（depth=1, movetime=300ms）
     */
    public int evaluatePositionQuickly(ChessInfo chessInfo) {
        if (!initialized) return 0;

        if (isSearching.get()) {
            shouldStop.set(true);
            nativeSendCommand("stop");
            long waitStart = System.currentTimeMillis();
            while (isSearching.get() && System.currentTimeMillis() - waitStart < 500) {
                try { Thread.sleep(20); } catch (InterruptedException ignored) {}
            }
        }

        try {
            shouldStop.set(false);
            isSearching.set(true);

            String fen = boardToFEN(chessInfo);
            nativeSendCommand("position fen " + fen);
            nativeSendCommand("setoption name MultiPV value 1");
            nativeSendCommand("go depth 1 movetime 300");

            int score = 0;
            long startTime = System.currentTimeMillis();
            long maxWait = 2000;

            while (!Thread.currentThread().isInterrupted()) {
                if (System.currentTimeMillis() - startTime > maxWait) {
                    nativeSendCommand("stop");
                    break;
                }
                String line = readLineWithTimeout(100);
                if (line == null) continue;
                if (line.startsWith("info")) {
                    String[] parts = line.split(" ");
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("score") && i + 2 < parts.length) {
                            if (parts[i + 1].equals("cp")) {
                                try { score = Integer.parseInt(parts[i + 2]); } catch (NumberFormatException ignored) {}
                            } else if (parts[i + 1].equals("mate")) {
                                try {
                                    int mateIn = Integer.parseInt(parts[i + 2]);
                                    score = mateIn > 0 ? 1000 - mateIn * 10 : -1000 + mateIn * 10;
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                } else if (line.startsWith("bestmove")) {
                    break;
                }
            }

            LogUtils.i("PikafishAI", "快速评估: score=" + score);
            return score;
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "快速评估异常: " + e.getMessage());
            return 0;
        } finally {
            isSearching.set(false);
            shouldStop.set(false);
        }
    }

    /**
     * 获取完整 PV 序列
     */
    public PvSequenceWithScore getPvSequenceWithScore(ChessInfo chessInfo) {
        if (!initialized) {
            return new PvSequenceWithScore(new ArrayList<>(), 0);
        }

        try {
            stopPreviousSearch();
            // 同步最新设置
            syncSettingsFromChessInfo(chessInfo);

            // 【诊断】打印 sync 后的缓存值
            int effSkillFactorPct = Math.round(getSkillFactor() * 100);
            int time = getEffectiveTimeMs();
            int effDepth = getEffectiveDepth();
            LogUtils.i("PikafishAI", "搜索参数: cachedDepth=" + cachedDepth
                + " cachedTime=" + cachedTimeSeconds + "s"
                + " skillLevel=" + cachedSkillLevel + " (因子=" + effSkillFactorPct + "%)"
                + " → effDepth=" + effDepth + " effTime=" + time + "ms");

            shouldStop.set(false);
            isSearching.set(true);
            currentDepth = 0;

            String fen = boardToFEN(chessInfo);
            nativeSendCommand("position fen " + fen);

            // 双限制：时间为主，时间限制内到层数就以层数为准
            if (chessInfo != null && chessInfo.forceVariation) {
                nativeSendCommand("setoption name MultiPV value 3");
            } else {
                nativeSendCommand("setoption name MultiPV value " + cachedMultiPV);
            }

            String goCmd = "go depth " + effDepth + " movetime " + time;
            LogUtils.i("PikafishAI", "发送搜索命令: " + goCmd + " (skillLevel=" + cachedSkillLevel
                + " 原始depth=" + cachedDepth + " 原始time=" + cachedTimeSeconds + "s)");
            nativeSendCommand(goCmd);

            String bestMoveStr = null;
            List<String> pvMoveList = new ArrayList<>();
            int score = 0;
            long startTime = System.currentTimeMillis();
            long maxSearchTime = time + MAX_SEARCH_TIME_BUFFER_MS;
            int infoLineCount = 0;
            int maxDepthSeen = 0;

            while (!Thread.currentThread().isInterrupted()) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > maxSearchTime) {
                    LogUtils.w("PikafishAI", "搜索超时(" + elapsed + "ms >= " + maxSearchTime + "ms)，强制停止");
                    nativeSendCommand("stop");
                    break;
                }
                if (shouldStop.get() && bestMoveStr == null) {
                    nativeSendCommand("stop");
                }

                String line = readLineWithTimeout(100);
                if (line == null) {
                    if (shouldStop.get()) break;
                    continue;
                }

                if (line.startsWith("info")) {
                    infoLineCount++;
                    String[] parts = line.split(" ");
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("depth") && i + 1 < parts.length) {
                            try {
                                int newDepth = Integer.parseInt(parts[i + 1]);
                                if (newDepth > currentDepth) currentDepth = newDepth;
                                if (newDepth > maxDepthSeen) maxDepthSeen = newDepth;
                            } catch (NumberFormatException ignored) {}
                        } else if (parts[i].equals("score") && i + 2 < parts.length) {
                            if (parts[i + 1].equals("cp")) {
                                try { score = Integer.parseInt(parts[i + 2]); } catch (NumberFormatException ignored) {}
                            } else if (parts[i + 1].equals("mate")) {
                                try {
                                    int mateIn = Integer.parseInt(parts[i + 2]);
                                    score = mateIn > 0 ? 1000 - mateIn * 10 : -1000 + mateIn * 10;
                                } catch (NumberFormatException ignored) {}
                            }
                        } else if (parts[i].equals("pv") && i + 1 < parts.length) {
                            pvMoveList.clear();
                            for (int j = i + 1; j < parts.length; j++) {
                                pvMoveList.add(parts[j]);
                            }
                            if (bestMoveStr == null) {
                                bestMoveStr = parts[i + 1];
                            }
                        }
                    }
                } else if (line.startsWith("bestmove")) {
                    if (bestMoveStr == null) {
                        String[] parts = line.split(" ");
                        if (parts.length > 1) bestMoveStr = parts[1];
                    }
                    LogUtils.i("PikafishAI", "收到 bestmove: " + line);
                    break;
                } else if (!line.startsWith("info")) {
                    // 非 info/bestmove 的行（如 uciok 等）
                    LogUtils.d("PikafishAI", "引擎输出: " + line);
                }
            }

            long searchElapsed = System.currentTimeMillis() - startTime;
            LogUtils.i("PikafishAI", "搜索结束: 耗时=" + searchElapsed + "ms"
                + " info行数=" + infoLineCount + " 最大depth=" + maxDepthSeen
                + " bestmove=" + bestMoveStr + " pv长度=" + pvMoveList.size()
                + " score=" + score);

            updateUIAfterSearch(currentDepth);

            List<Move> moveSequence = new ArrayList<>();
            for (String uciMove : pvMoveList) {
                Move move = uciToMove(uciMove);
                if (move != null) moveSequence.add(move);
            }

            return new PvSequenceWithScore(moveSequence, score);
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "获取 PV 序列异常: " + e.getMessage());
            return new PvSequenceWithScore(new ArrayList<>(), 0);
        } finally {
            isSearching.set(false);
        }
    }

    // ========== 辅助方法 ==========

    private void stopPreviousSearch() {
        shouldStop.set(true);
        if (isSearching.get()) {
            nativeSendCommand("stop");
            try { Thread.sleep(100); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void interrupt() {
        shouldStop.set(true);
        if (isSearching.get()) {
            nativeSendCommand("stop");
        }
    }

    public void updateSettings(int skillLevel, int multiPV) {
        updateSettings(skillLevel, multiPV, DEFAULT_DEPTH, DEFAULT_TIME_MS / 1000);
    }

    public void updateSettings(int skillLevel, int multiPV, int depth, int thinkingTimeSeconds) {
        if (!initialized) return;

        // 缓存所有设置
        cachedSkillLevel = Math.max(1, Math.min(20, skillLevel));
        cachedMultiPV = Math.max(1, multiPV);
        cachedDepth = Math.max(MIN_DEPTH, depth);
        cachedTimeSeconds = Math.max(1, Math.min(60, thinkingTimeSeconds));

        // 读取线程/哈希设置（以用户设置为准）
        readSettingsFromFile();

        // 应用引擎选项
        nativeSendCommand("setoption name MultiPV value " + cachedMultiPV);

        int threadCount = cachedThreads > 0 ? cachedThreads : computeAutoThreads();
        nativeSendCommand("setoption name Threads value " + threadCount);
        int hashSize = cachedHashMB > 0 ? cachedHashMB : getOptimalHashSize();
        nativeSendCommand("setoption name Hash value " + hashSize);

        int effDepth = getEffectiveDepth();
        int effTimeMs = getEffectiveTimeMs();
        LogUtils.i("PikafishAI", "更新设置: SkillLevel=" + cachedSkillLevel +
            " MultiPV=" + cachedMultiPV + " Depth=" + cachedDepth +
            " Time=" + cachedTimeSeconds + "s" +
            " → 有效Depth=" + effDepth + " 有效Time=" + effTimeMs + "ms" +
            " (因子=" + String.format(Locale.US, "%.2f", getSkillFactor()) + ")");

        nativeSendCommand("isready");
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
    }

    public void close() {
        shouldStop.set(true);
        isSearching.set(false);

        // 停止输出读取线程
        readerRunning = false;

        // 调用 JNI cleanup
        try {
            nativeSendCommand("quit");
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            nativeCleanup();
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "关闭引擎异常: " + e.getMessage());
        }

        // 等待读取线程结束
        if (outputReaderThread != null && outputReaderThread.isAlive()) {
            try {
                outputReaderThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        initialized = false;
        currentDepth = 0;
        outputQueue.clear();
        LogUtils.i("PikafishAI", "引擎已关闭");
    }

    private void updateUIAfterSearch(int finalDepth) {
        try {
            PvMActivity activity = PvMActivity.getInstance();
            if (activity != null && activity.roundView != null && activity.chessInfo != null) {
                boolean isRed = activity.chessInfo.IsRedGo;
                try {
                    activity.roundView.setSearchDepth(finalDepth, isRed);
                } catch (NoSuchMethodError e) {
                    activity.roundView.setSearchDepth(finalDepth);
                }
            }
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "更新搜索深度失败: " + e.getMessage());
        }
    }

    // ========== FEN / UCI 转换（与原来一致）==========

    private String boardToFEN(ChessInfo chessInfo) {
        StringBuilder fen = new StringBuilder();
        for (int y = 0; y < 10; y++) {
            int emptyCount = 0;
            for (int x = 0; x < 9; x++) {
                int piece = chessInfo.piece[y][x];
                if (piece == 0) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(pieceToFEN(piece));
                }
            }
            if (emptyCount > 0) fen.append(emptyCount);
            if (y < 9) fen.append('/');
        }
        fen.append(' ');
        fen.append(chessInfo.IsRedGo ? 'b' : 'w');
        fen.append(" - - 0 1");
        return fen.toString();
    }

    private char pieceToFEN(int piece) {
        switch (piece) {
            case 1: return 'K'; case 2: return 'A'; case 3: return 'B';
            case 4: return 'N'; case 5: return 'R'; case 6: return 'C';
            case 7: return 'P'; case 8: return 'k'; case 9: return 'a';
            case 10: return 'b'; case 11: return 'n'; case 12: return 'r';
            case 13: return 'c'; case 14: return 'p';
            default: return ' ';
        }
    }

    private Move uciToMove(String uci) {
        if (uci == null || uci.length() != 4) return null;
        try {
            int fromX = uci.charAt(0) - 'a';
            int fromY = 9 - (uci.charAt(1) - '0');
            int toX = uci.charAt(2) - 'a';
            int toY = 9 - (uci.charAt(3) - '0');
            if (fromX < 0 || fromX >= 9 || fromY < 0 || fromY >= 10 ||
                toX < 0 || toX >= 9 || toY < 0 || toY >= 10) {
                return null;
            }
            return new Move(new Pos(fromX, fromY), new Pos(toX, toY));
        } catch (Exception e) {
            return null;
        }
    }

    private Move getDefaultMove(ChessInfo chessInfo) {
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                int piece = chessInfo.piece[y][x];
                if ((chessInfo.IsRedGo && piece >= 8) || (!chessInfo.IsRedGo && piece <= 7)) {
                    List<Pos> possibleMoves = Rule.PossibleMoves(chessInfo.piece, x, y, piece);
                    if (!possibleMoves.isEmpty()) {
                        return new Move(new Pos(x, y), possibleMoves.get(0));
                    }
                }
            }
        }
        return null;
    }
}
