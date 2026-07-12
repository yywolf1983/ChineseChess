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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class PikafishAI {
    private static final long INIT_TIMEOUT_MS = 5000;
    // 超时缓冲：取 timeMs 的 50% 但不超过 2000ms，最短 800ms
    // 短时限（1s→800ms缓冲→总计1.8s）、长时限（60s→2000ms缓冲→总计62s）
    private long getSearchTimeBuffer(long timeMs) {
        return Math.max(800, Math.min(2000, timeMs / 2));
    }
    private static final int MAX_INIT_RETRIES = 3;
    private static final long INIT_RETRY_DELAY_MS = 1000;

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

    // ========== 状态字段（跨线程访问，需 volatile 或 Atomic）==========
    private volatile boolean initialized = false;
    private final AtomicReference<CountDownLatch> initLatchRef = new AtomicReference<>(new CountDownLatch(1));
    private volatile boolean initInProgress = false;
    private Context context;
    private final java.util.concurrent.atomic.AtomicBoolean isSearching = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean shouldStop = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final AtomicInteger currentDepth = new AtomicInteger(0);

    // ========== 线程安全锁 ==========
    /** 保护所有 nativeSendCommand/nativeReadLine 调用，防止多线程交错发送命令导致引擎崩溃 */
    private final ReentrantLock nativeLock = new ReentrantLock();
    /** 保护搜索操作（getBestMoveWithScore/getPvSequenceWithScore/evaluatePositionQuickly）互斥 */
    private final ReentrantLock searchLock = new ReentrantLock();

    // ========== 缓存设置 ==========
    // 所有默认值与 Info.Setting 类中的默认值保持一致
    private volatile int cachedSkillLevel = 20;   // 技能级别 (1-20, 20=满血)
    private volatile int cachedDepth = 10;        // 搜索深度 (5-120)
    private volatile int cachedMultiPV = 0;       // 多主变 (0-5, 0=禁用)
    private volatile int cachedTimeSeconds = 3;   // 思考时间（秒, 1-60）
    private volatile int cachedThreads = 0;       // 线程数 (0=自动)
    private volatile int cachedHashMB = 0;        // 哈希表 MB (0=自动)
    private volatile int cachedContempt = 20;     // 蔑视值 (centipawns)
    private volatile String cachedNumaPolicy = "auto"; // NUMA 策略

    // 输出读取线程
    private Thread outputReaderThread;
    private final BlockingQueue<String> outputQueue = new LinkedBlockingQueue<>();
    private volatile boolean readerRunning = false;
    private final Object initLock = new Object();

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

    // ========== 初始化（带重试）==========
    private void initialize() {
        synchronized (initLock) {
            if (initialized) return;
            if (initInProgress) return;
            initInProgress = true;
        }

        if (initListener != null) {
            runOnUiThread(() -> initListener.onInitializationStarted());
        }

        for (int attempt = 1; attempt <= MAX_INIT_RETRIES; attempt++) {
            LogUtils.i("PikafishAI", "引擎初始化尝试 " + attempt + "/" + MAX_INIT_RETRIES);
            if (tryInitializeOnce()) {
                // 成功
                synchronized (initLock) { initInProgress = false; }
                return;
            }
            // 失败，清理后重试
            LogUtils.w("PikafishAI", "初始化尝试 " + attempt + " 失败");
            cleanupAfterFailedInit();
            if (attempt < MAX_INIT_RETRIES) {
                try { Thread.sleep(INIT_RETRY_DELAY_MS * attempt); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        synchronized (initLock) { initInProgress = false; }
        notifyInitFailed();
    }

    /** 单次初始化尝试，成功返回 true */
    private boolean tryInitializeOnce() {
        initialized = false;
        readerRunning = false;
        outputQueue.clear();
        currentDepth.set(0);

        try {
            // 1. 复制 NNUE 文件到缓存目录
            String nnuePath = copyNNUEFile();
            if (nnuePath == null) {
                LogUtils.e("PikafishAI", "NNUE 文件复制失败");
                return false;
            }

            // 2. 获取 native library 目录
            String libPath = context.getApplicationInfo().nativeLibraryDir;

            // 3. 调用 JNI 初始化（此时引擎已启动，内部发了 uci 命令）
            boolean initOk = nativeInit(nnuePath, libPath);
            if (!initOk) {
                LogUtils.e("PikafishAI", "nativeInit 返回 false");
                return false;
            }

            // 4. 引擎启动成功后，再启动输出读取线程
            startOutputReaderThread();

            // 5. 排空初始化残留输出（nativeInit 已在 JNI 层完成 uci/uciok 握手
            //    并发送了 isready，管道中可能残留 readyok 等行）
            drainOutputQueue();

            // 6. 设置引擎参数（必须在 isready 之前）
            setupEngineOptions();
            // setoption（如 Clear Hash）可能产生引擎输出，排空避免污染 sendIsReady
            drainOutputQueue();

            // 7. 确认引擎就绪（发送 isready + 等待 readyok）
            if (!sendIsReady()) {
                LogUtils.e("PikafishAI", "isready/readyok 握手失败");
                return false;
            }

            // 8. 所有步骤完成后再标记 initialized，防止半初始化状态被外部误用
            initialized = true;
            LogUtils.i("PikafishAI", "引擎初始化成功");

            // 9. 通知成功
            initLatchRef.get().countDown();
            if (initListener != null) {
                runOnUiThread(() -> initListener.onInitializationCompleted());
            }
            return true;
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "初始化异常: " + e.getMessage(), e);
            return false;
        }
    }

    /** 发送 isready 并等待 readyok（跳过可能夹杂的中间输出行） */
    private boolean sendIsReady() {
        sendCommand("isready");
        long deadline = System.currentTimeMillis() + INIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            long remaining = deadline - System.currentTimeMillis();
            String line = readLineWithTimeout(Math.min(200, remaining));
            if ("readyok".equals(line)) {
                return true;
            }
            if (line != null) {
                LogUtils.v("PikafishAI", "sendIsReady 跳过中间输出: " + line);
            }
        }
        LogUtils.e("PikafishAI", "等待 readyok 超时（" + INIT_TIMEOUT_MS + "ms）");
        return false;
    }

    /** 初始化失败后的清理 */
    private void cleanupAfterFailedInit() {
        stopOutputReaderThread();
        nativeLock.lock();
        try { nativeCleanup(); } catch (Exception ignored) {}
        finally { nativeLock.unlock(); }
        initialized = false;
        outputQueue.clear();
        // 重置 latch 以备重试
        CountDownLatch oldLatch = initLatchRef.getAndSet(new CountDownLatch(1));
        oldLatch.countDown(); // 唤醒任何正在等待旧 latch 的线程
    }

    // ========== 引擎参数管理 ==========

    /** 参数范围常量，与 Info.Setting 默认值保持一致 */
    private static final int VALID_SKILL_LEVEL_MIN = 1;
    private static final int VALID_SKILL_LEVEL_MAX = 20;
    private static final int VALID_DEPTH_MIN = 5;
    private static final int VALID_DEPTH_MAX = 120;
    private static final int VALID_TIME_SEC_MIN = 1;
    private static final int VALID_TIME_SEC_MAX = 60;
    private static final int VALID_MULTIPV_MIN = 0;
    private static final int VALID_MULTIPV_MAX = 5;
    private static final int VALID_CONTEMPT_MIN = -100;
    private static final int VALID_CONTEMPT_MAX = 100;
    private static final int VALID_THREADS_MAX = 128;
    private static final int VALID_HASH_MB_MAX = 4096;

    /** 参数范围单向夹紧（不会抛异常，始终返回有效值） */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 对所有引擎参数做单向夹紧，确保后续下发给引擎的值都在安全范围内。
     * 此方法修改 cachedXxx 字段，调用后缓存值一定是合法值。
     */
    private void validateAndClampAllParams() {
        cachedSkillLevel = clamp(cachedSkillLevel, VALID_SKILL_LEVEL_MIN, VALID_SKILL_LEVEL_MAX);
        cachedDepth = clamp(cachedDepth, VALID_DEPTH_MIN, VALID_DEPTH_MAX);
        cachedMultiPV = clamp(cachedMultiPV, VALID_MULTIPV_MIN, VALID_MULTIPV_MAX);
        cachedTimeSeconds = clamp(cachedTimeSeconds, VALID_TIME_SEC_MIN, VALID_TIME_SEC_MAX);
        cachedContempt = clamp(cachedContempt, VALID_CONTEMPT_MIN, VALID_CONTEMPT_MAX);
        cachedThreads = clamp(cachedThreads, 0, VALID_THREADS_MAX);
        cachedHashMB = clamp(cachedHashMB, 0, VALID_HASH_MB_MAX);
        if (cachedNumaPolicy == null || cachedNumaPolicy.isEmpty()) {
            cachedNumaPolicy = "auto";
        }
    }

    /**
     * 将所有缓存的引擎参数实际下发到引擎（线程安全，通过 sendCommand 持有 nativeLock）。
     * 调用前应确保 validateAndClampAllParams() 已被调用。
     */
    private void applyAllEngineOptions() {
        // EvalFile 已在 nativeInit 中通过绝对路径设置，这里不重复发送

        // Threads
        boolean threadsAuto = cachedThreads <= 0;
        int threadCount = threadsAuto ? computeAutoThreads() : cachedThreads;
        cachedThreads = threadCount;
        sendCommand("setoption name Threads value " + threadCount);

        // Hash
        boolean hashAuto = cachedHashMB <= 0;
        int hashSize = hashAuto ? getOptimalHashSize() : cachedHashMB;
        cachedHashMB = hashSize;
        sendCommand("setoption name Hash value " + hashSize);

        // Skill Level
        sendCommand("setoption name Skill Level value " + cachedSkillLevel);

        // Contempt
        sendCommand("setoption name Contempt value " + cachedContempt);

        // NumaPolicy
        sendCommand("setoption name NumaPolicy value " + cachedNumaPolicy);

        // MultiPV（引擎要求 MultiPV >= 1；设置中 0 表示禁用多主变→下发 1）
        int engineMultiPV = Math.max(1, cachedMultiPV);
        sendCommand("setoption name MultiPV value " + engineMultiPV);

        LogUtils.i("PikafishAI",
            "引擎参数已应用: Threads=" + threadCount + (threadsAuto ? "(auto)" : "") + " Hash=" + hashSize
            + "MB" + (hashAuto ? "(auto)" : "") + " MultiPV=" + engineMultiPV + " SkillLevel=" + cachedSkillLevel
            + " Contempt=" + cachedContempt + " NumaPolicy=" + cachedNumaPolicy);
    }

    private void setupEngineOptions() {
        // 1. 从持久化设置读取参数到缓存
        readSettingsFromFile();
        // 2. 夹紧校验
        validateAndClampAllParams();
        // 3. 下发给引擎
        applyAllEngineOptions();
        // 4. 新游戏时清空哈希
        sendCommand("setoption name Clear Hash");
    }

    /**
     * 从持久化设置读取全部引擎参数到缓存。
     * 优先从 GameResourceManager 读取（与 Setting 构造来源一致），
     * 读取失败时保持当前缓存值不变。
     */
    private void readSettingsFromFile() {
        try {
            Info.Setting setting = Utils.GameResourceManager.getInstance().getSetting();
            if (setting != null) {
                cachedThreads = setting.threads;
                cachedHashMB = setting.hashMB;
                cachedContempt = setting.contempt;
                cachedNumaPolicy = (setting.numaPolicy != null && !setting.numaPolicy.isEmpty())
                    ? setting.numaPolicy : "auto";
                // 关键：depth 和 mLevel(思考时间) 也必须从持久化设置读取，
                // 否则 updateSettings(int, int) 会使用过期的缓存值
                cachedDepth = setting.depth;
                cachedTimeSeconds = setting.mLevel;
            }
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "读取设置失败: " + e.getMessage());
        }
    }

    /** 根据设备核心数自动计算线程数（为 UI 预留 1-2 核） */
    private int computeAutoThreads() {
        int totalCores = Runtime.getRuntime().availableProcessors();
        // 棋类引擎对 UI 线程占用极少，预留 1 核足够
        int reservedCores = totalCores <= 2 ? 1 : (totalCores <= 6 ? 1 : 2);
        return Math.max(1, totalCores - reservedCores);
    }

    /**
     * 供外部使用的思考时间（毫秒），直接使用已校验的缓存值。
     * 引擎的 Skill Level 选项自行处理棋力削弱，Java 层不再重复削弱。
     */
    public int getEffectiveThinkTimeMs() {
        return cachedTimeSeconds * 1000;
    }

    private void notifyInitFailed() {
        cleanupAfterFailedInit();
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

    private void stopOutputReaderThread() {
        readerRunning = false;
        if (outputReaderThread != null && outputReaderThread.isAlive()) {
            try {
                outputReaderThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        outputReaderThread = null;
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

    /** 清空 outputQueue 中所有残留数据 */
    private void drainOutputQueue() {
        outputQueue.clear();
    }

    /**
     * 线程安全地向引擎发送命令。
     * 所有 nativeSendCommand 调用必须经过此方法，防止多线程交错写入导致引擎崩溃。
     */
    private void sendCommand(String command) {
        nativeLock.lock();
        try {
            nativeSendCommand(command);
        } finally {
            nativeLock.unlock();
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

            try (InputStream is = context.getAssets().open("pikafish.nnue");
                 FileOutputStream os = new FileOutputStream(nnueFile)) {
                byte[] buffer = new byte[8192];
                int length;
                long totalBytes = 0;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                    totalBytes += length;
                }
                LogUtils.i("PikafishAI", "NNUE 文件复制成功: " + totalBytes + " bytes -> " + nnueFile.getAbsolutePath());
            }
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
            // 在搜索锁空闲时操作，避免与 go 命令冲突
            searchLock.lock();
            try {
                sendCommand("setoption name Clear Hash");
                LogUtils.i("PikafishAI", "哈希表已清空");
            } finally {
                searchLock.unlock();
            }
        }
    }

    /**
     * 在每次搜索前同步 chessInfo.setting 到本地缓存，并对所有值做范围校验。
     */
    private void syncSettingsFromChessInfo(ChessInfo chessInfo) {
        if (chessInfo == null || chessInfo.setting == null) return;
        Info.Setting s = chessInfo.setting;
        cachedSkillLevel = s.skillLevel;
        cachedDepth = s.depth;
        cachedMultiPV = s.multiPV;
        cachedTimeSeconds = s.mLevel;
        cachedThreads = s.threads;
        cachedHashMB = s.hashMB;
        cachedContempt = s.contempt;
        cachedNumaPolicy = (s.numaPolicy != null && !s.numaPolicy.isEmpty()) ? s.numaPolicy : "auto";
        // 统一校验范围
        validateAndClampAllParams();
    }

    // ========== 哈希表大小计算 ==========
    private int getOptimalHashSize() {
        try {
            long maxMemory = Runtime.getRuntime().maxMemory();
            int maxMemoryMB = (int) (maxMemory / (1024 * 1024));
            // 按 JVM 堆内存比例分配 Hash，引擎 Hash + NNUE + App 应控制在堆的 60% 内
            if (maxMemoryMB >= 2048) return 512;
            else if (maxMemoryMB >= 1024) return 256;
            else if (maxMemoryMB >= 384) return 128;   // 降低阈值，覆盖更多中端机型
            else if (maxMemoryMB >= 256) return 64;
            else return 32;
        } catch (Exception e) {
            return 128;
        }
    }

    // ========== 公共 API ==========

    /**
     * 等待引擎初始化完成（最长等待 timeoutMs 毫秒）
     * @return true 表示初始化成功，false 表示超时或失败
     */
    public boolean waitForInit(long timeoutMs) {
        if (initialized) return true;
        try {
            return initLatchRef.get().await(timeoutMs, TimeUnit.MILLISECONDS) && initialized;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return initialized;
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public int getCurrentDepth() {
        return currentDepth.get();
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

        // 获取搜索锁（非阻塞，抢不到说明已有搜索在运行）
        if (!searchLock.tryLock()) {
            LogUtils.w("PikafishAI", "已有搜索在运行，跳过本次请求");
            return new MoveWithScore(getDefaultMove(chessInfo), 0);
        }

        try {
            // 等待初始化完成
            if (!waitForInit(10000)) {
                LogUtils.e("PikafishAI", "等待引擎初始化超时");
                return new MoveWithScore(getDefaultMove(chessInfo), 0);
            }
            // 停止之前的搜索并清空残留输出
            stopPreviousSearch();
            drainOutputQueue();

            shouldStop.set(false);
            isSearching.set(true);
            currentDepth.set(0);

            // 计算搜索参数：使用缓存值（已在 updateSettings 中校验范围）
            int depth = cachedDepth;
            int timeMs = cachedTimeSeconds * 1000;

            // 下发引擎参数：正常模式应用用户设置，强制变着模式覆盖多样性参数
            boolean wasForceVariation = false;
            if (chessInfo != null && chessInfo.forceVariation) {
                wasForceVariation = true;
                int randomness = Math.max(1, chessInfo.variationRandomness);
                depth = depth + randomness;
                sendCommand("setoption name Skill Level value 20");
                sendCommand("setoption name Contempt value 0");
                sendCommand("setoption name MultiPV value 3");
                LogUtils.i("PikafishAI", "强制变着模式: SkillLevel=20 Contempt=0 MultiPV=3 depth=" + depth + " movetime=" + timeMs + "ms randomness=" + randomness);
            } else {
                // 正常模式：确保引擎参数与用户设置一致（尤其强制变着后需恢复）
                applyAllEngineOptions();
                LogUtils.i("PikafishAI", "正常模式: SkillLevel=" + cachedSkillLevel
                    + " Contempt=" + cachedContempt + " MultiPV=" + Math.max(1, cachedMultiPV));
            }

            // 生成 FEN 并设置局面
            String fen = boardToFEN(chessInfo);
            LogUtils.i("PikafishAI", "FEN: " + fen);
            sendCommand("position fen " + fen);

            // 启动搜索：双限制，谁先到就以谁为准
            LogUtils.i("PikafishAI", "go depth " + depth + " movetime " + timeMs
                + " (设置depth=" + cachedDepth + " 设置time=" + cachedTimeSeconds + "s)");
            sendCommand("go depth " + depth + " movetime " + timeMs);

            // 读取搜索结果
            String bestMoveStr = null;
            List<String> possibleMoves = new ArrayList<>();
            int score = 0;
            long startTime = System.currentTimeMillis();
            long maxSearchTime = timeMs + getSearchTimeBuffer(timeMs);

            while (!Thread.currentThread().isInterrupted()) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > maxSearchTime) {
                    LogUtils.w("PikafishAI", "搜索超时, 强制停止");
                    sendCommand("stop");
                    break;
                }

                if (shouldStop.get() && bestMoveStr == null) {
                    sendCommand("stop");
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
                                int cur = currentDepth.get();
                                if (newDepth > cur) {
                                    currentDepth.set(newDepth);
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
            updateUIAfterSearch(currentDepth.get());

            if (bestMoveStr != null) {
                Move move = uciToMove(bestMoveStr);
                LogUtils.i("PikafishAI", "最佳走法: " + move + ", 评分: " + score);
                return new MoveWithScore(move, score);
            }
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "搜索异常: " + e.getMessage(), e);
        } finally {
            isSearching.set(false);
            searchLock.unlock();
        }

        return new MoveWithScore(getDefaultMove(chessInfo), 0);
    }

    /**
     * 快速评估（轻量级搜索：depth=1, movetime=300ms）。
     * 用于局面评分等不需要高精度的场景，不参与实际走法决策。
     */
    public int evaluatePositionQuickly(ChessInfo chessInfo) {
        if (!initialized && !waitForInit(5000)) return 0;

        // 尝试获取搜索锁，如果被占用就停止当前搜索后重试
        if (!searchLock.tryLock()) {
            shouldStop.set(true);
            sendCommand("stop");
            long waitStart = System.currentTimeMillis();
            while (!searchLock.tryLock()) {
                if (System.currentTimeMillis() - waitStart > 1000) {
                    LogUtils.w("PikafishAI", "无法获取搜索锁，放弃快速评估");
                    return 0;
                }
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
        }

        try {
            shouldStop.set(false);
            isSearching.set(true);
            drainOutputQueue();

            // 同步最新设置，确保 Skill Level / Contempt 等参数与当前局面一致
            syncSettingsFromChessInfo(chessInfo);

            String fen = boardToFEN(chessInfo);
            // 快速评估：使用用户设置的引擎参数（Skill Level、Contempt、Threads、Hash 等全部下发）
            // MultiPV 固定为 1（快速评估只需单路主变）
            applyAllEngineOptions();
            sendCommand("setoption name MultiPV value 1");
            sendCommand("position fen " + fen);
            // 快速评估固定 depth=1、movetime=300ms，保证响应速度
            sendCommand("go depth 1 movetime 300");

            int score = 0;
            long startTime = System.currentTimeMillis();
            long maxWait = 2000;

            while (!Thread.currentThread().isInterrupted()) {
                if (System.currentTimeMillis() - startTime > maxWait) {
                    sendCommand("stop");
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
            searchLock.unlock();
        }
    }

    /**
     * 获取完整 PV 序列
     */
    public PvSequenceWithScore getPvSequenceWithScore(ChessInfo chessInfo) {
        if (!initialized && !waitForInit(10000)) {
            return new PvSequenceWithScore(new ArrayList<>(), 0);
        }

        // 获取搜索锁
        if (!searchLock.tryLock()) {
            LogUtils.w("PikafishAI", "已有搜索在运行，跳过 PV 序列请求");
            return new PvSequenceWithScore(new ArrayList<>(), 0);
        }

        try {
            stopPreviousSearch();
            drainOutputQueue();

            // 调用方已通过 updateSettings 同步并下发参数，此处直接使用缓存值
            int depth = cachedDepth;
            int timeMs = cachedTimeSeconds * 1000;
            LogUtils.i("PikafishAI", "PV搜索参数: depth=" + depth
                + " time=" + cachedTimeSeconds + "s"
                + " skillLevel=" + cachedSkillLevel);

            shouldStop.set(false);
            isSearching.set(true);
            currentDepth.set(0);

            // 下发引擎参数：正常模式应用用户设置，强制变着模式覆盖多样性参数
            if (chessInfo != null && chessInfo.forceVariation) {
                depth = depth + Math.max(1, chessInfo.variationRandomness);
                sendCommand("setoption name Skill Level value 20");
                sendCommand("setoption name Contempt value 0");
                sendCommand("setoption name MultiPV value 3");
            } else {
                applyAllEngineOptions();
            }

            String fen = boardToFEN(chessInfo);
            sendCommand("position fen " + fen);

            String goCmd = "go depth " + depth + " movetime " + timeMs;
            LogUtils.i("PikafishAI", "发送搜索命令: " + goCmd);
            sendCommand(goCmd);

            String bestMoveStr = null;
            List<String> pvMoveList = new ArrayList<>();
            int score = 0;
            long startTime = System.currentTimeMillis();
            long maxSearchTime = timeMs + getSearchTimeBuffer(timeMs);
            int infoLineCount = 0;
            int maxDepthSeen = 0;

            while (!Thread.currentThread().isInterrupted()) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > maxSearchTime) {
                    LogUtils.w("PikafishAI", "搜索超时(" + elapsed + "ms >= " + maxSearchTime + "ms)，强制停止");
                    sendCommand("stop");
                    break;
                }
                if (shouldStop.get() && bestMoveStr == null) {
                    sendCommand("stop");
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
                                int cur = currentDepth.get();
                                if (newDepth > cur) currentDepth.set(newDepth);
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

            updateUIAfterSearch(currentDepth.get());

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
            searchLock.unlock();
        }
    }

    // ========== 辅助方法 ==========

    private void stopPreviousSearch() {
        shouldStop.set(true);
        if (isSearching.get()) {
            sendCommand("stop");
            // 等待 bestmove 到达（最多 300ms），避免残留输出污染下次搜索
            long waitStart = System.currentTimeMillis();
            while (System.currentTimeMillis() - waitStart < 300) {
                String line = readLineWithTimeout(100);
                if (line != null && line.startsWith("bestmove")) {
                    LogUtils.d("PikafishAI", "stopPreviousSearch 收到 bestmove: " + line);
                    break;
                }
                if (line == null) continue;
            }
            // 再清空队列中剩余的垃圾数据
            drainOutputQueue();
        }
    }

    public void interrupt() {
        shouldStop.set(true);
        if (isSearching.get()) {
            sendCommand("stop");
            // 不等待 bestmove，由搜索线程自己处理
        }
    }

    public void updateSettings(int skillLevel, int multiPV) {
        // 从持久化存储读取当前深度和时间 → 保证默认值来自用户设置而非硬编码
        readSettingsFromFile();
        updateSettings(skillLevel, multiPV, cachedDepth, cachedTimeSeconds);
    }

    public void updateSettings(int skillLevel, int multiPV, int depth, int thinkingTimeSeconds) {
        if (!initialized) return;

        // 更新上层设置参数（先缓存再校验）
        cachedSkillLevel = skillLevel;
        cachedMultiPV = multiPV;
        cachedDepth = depth;
        cachedTimeSeconds = thinkingTimeSeconds;

        // 读取并覆盖引擎内部参数（threads、hashMB、contempt、numaPolicy）
        readSettingsFromFile();

        // 统一校验所有参数范围
        validateAndClampAllParams();

        // 一次性下发全部引擎参数
        applyAllEngineOptions();

        LogUtils.i("PikafishAI", "更新设置: SkillLevel=" + cachedSkillLevel +
            " MultiPV=" + cachedMultiPV + " Depth=" + cachedDepth +
            " Time=" + cachedTimeSeconds + "s Threads=" + cachedThreads +
            " Hash=" + cachedHashMB + "MB");

        // 非阻塞发送 isready（不等待 readyok，避免 ANR）
        sendCommand("isready");
        // 仅在无搜索时清理输出队列，避免清空搜索结果
        if (!isSearching.get()) {
            drainOutputQueue();
        }
    }

    public void close() {
        shouldStop.set(true);
        isSearching.set(false);

        // 停止输出读取线程
        stopOutputReaderThread();

        // 发送 quit 并等待引擎退出
        try {
            sendCommand("quit");
            // 等待引擎管道断开（nativeReadLine 返回 null 表示EOF）
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "发送 quit 命令异常: " + e.getMessage());
        }

        // 调用 JNI cleanup（线程安全，持有 nativeLock）
        nativeLock.lock();
        try {
            nativeCleanup();
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "关闭引擎异常: " + e.getMessage());
        } finally {
            nativeLock.unlock();
        }

        initialized = false;
        currentDepth.set(0);
        outputQueue.clear();
        // 重建 latch 以备下次初始化
        initLatchRef.set(new CountDownLatch(1));
        synchronized (initLock) { initInProgress = false; }
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
