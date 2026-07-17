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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class PikafishAI {
    private static final long INIT_TIMEOUT_MS = 5000;
    // 超时缓冲：取 timeMs 的 1/3，最短 300ms 最长 2000ms
    // 1s→300ms缓冲 → 总计1.3s  5s→1666ms → 总计6.7s  60s→2000ms → 总计62s
    private long getSearchTimeBuffer(long timeMs) {
        return Math.max(300, Math.min(2000, timeMs / 3));
    }
    private static final int MAX_INIT_RETRIES = 3;
    private static final long INIT_RETRY_DELAY_MS = 1000;

    // ========== JNI Native 方法 ==========
    private static native boolean nativeInit(String nnuePath, String libPath);
    private static native void nativeSendCommand(String command);
    private static native String nativeReadLine();
    private static native void nativeCleanup();

    private static volatile boolean libraryLoaded = false;
    // 全局初始化锁：确保 close() 和 initialize() 不会并发执行
    // （Activity 重建时旧 onDestroy 的后台 close 可能与新 onCreate 的 init 竞争）
    private static final Object GLOBAL_INIT_LOCK = new Object();

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("pikafish");
            loaded = true;
            LogUtils.i("PikafishAI", "libpikafish.so 加载成功 (JNI)");
        } catch (UnsatisfiedLinkError e) {
            LogUtils.e("PikafishAI", "加载 libpikafish.so 失败: " + e.getMessage());
        } catch (Throwable t) {
            LogUtils.e("PikafishAI", "加载 libpikafish.so 抛出异常: " + t.getClass().getName()
                + ": " + t.getMessage(), t);
        }
        libraryLoaded = loaded;
    }

    // ========== 状态字段（跨线程访问，需 volatile 或 Atomic）==========
    private volatile boolean initialized = false;
    private volatile boolean nativeInited = false;
    private final AtomicReference<CountDownLatch> initLatchRef = new AtomicReference<>(new CountDownLatch(1));
    private volatile boolean initInProgress = false;
    private Context context;
    private final java.util.concurrent.atomic.AtomicBoolean isSearching = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean shouldStop = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final AtomicInteger currentDepth = new AtomicInteger(0);
    // 搜索代际：每次 interrupt() 递增，旧搜索检测到代际变化即退出
    private final AtomicInteger searchGeneration = new AtomicInteger(0);

    // ========== 线程安全锁 ==========
    /**
     * 保护所有写入类 native 调用（nativeSendCommand / nativeCleanup），
     * 防止多线程交错写入导致引擎命令解析错误或崩溃。
     * 注意：nativeReadLine 由单一的输出读取线程调用，UCI 引擎的 stdin/stdout 是独立管道，
     * 读写并发是安全的，因此读操作不需要持有此锁。
     */
    private final ReentrantLock nativeLock = new ReentrantLock();
    /** 保护搜索操作（getBestMoveWithScore/getPvSequenceWithScore/evaluatePositionQuickly）互斥 */
    private final ReentrantLock searchLock = new ReentrantLock();
    /** 标记有待应用的引擎参数（搜索中无法下发 setoption，延迟到下次搜索前应用） */
    private final AtomicBoolean pendingOptionsUpdate = new AtomicBoolean(false);

    // ========== 缓存设置 ==========
    // 所有默认值与 Info.Setting 类中的默认值保持一致
    private volatile int cachedSkillLevel = 20;   // 技能级别 (1-20, 20=满血)
    private volatile int cachedDepth = 10;        // 搜索深度 (5-120)
    private volatile int cachedMultiPV = 1;       // 多主变 (1-5, 1=单主变无额外开销)

    /** 最近一次 PV 搜索得到的多条候选变线（索引 1 为最佳），供界面“引擎结果”框逐条展示 */
    private volatile java.util.List<PikafishAI.PvSequenceWithScore> lastMultiPvLines = new java.util.ArrayList<>();

    /** 获取最近一次支招搜索的多条候选变线（按质量排序，索引 1 最佳） */
    public java.util.List<PikafishAI.PvSequenceWithScore> getLastMultiPvLines() {
        return lastMultiPvLines;
    }
    private volatile int cachedTimeSeconds = 3;   // 思考时间（秒, 1-60）
    private volatile int cachedThreads = 0;       // 线程数 (0=自动)
    private volatile int cachedHashMB = 0;        // 哈希表 MB (0=自动)
    private volatile int cachedContempt = 20;     // 蔑视值 (centipawns)
    // 记录上次实际下发的值，避免重复发送不变的参数
    private int lastAppliedThreadCount = -1;
    private int lastAppliedHashSize = -1;
    private volatile String cachedNumaPolicy = "auto"; // NUMA 策略

    // 输出读取线程
    private Thread outputReaderThread;
    private final BlockingQueue<String> outputQueue = new LinkedBlockingQueue<>();
    private volatile boolean readerRunning = false;
    private volatile boolean readerExited = false;
    private final Object initLock = new Object();
    private static final long ENGINE_QUIT_TIMEOUT_MS = 3000;

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
        /** 将死步数：>0 表示行棋方将在 mateIn 步内将死对方；<0 表示行棋方将在 |mateIn| 步内被将死；0 表示非将死局面 */
        public int mateIn;
        public PvSequenceWithScore(java.util.List<Move> pvSequence, int score) {
            this.pvSequence = pvSequence;
            this.score = score;
            this.mateIn = 0;
        }
    }

    /**
     * 计算候选变线的综合排序分值：
     * - 将死对方（mateIn > 0）：越高越优，且步数越少越优；
     * - 被将死（mateIn < 0）：越低越劣，且步数越少越劣；
     * - 非将死：直接取原始评分。
     * 用于让最优变线（含速胜的“杀N”）排在最前面。
     */
    private static int rankPvLine(PikafishAI.PvSequenceWithScore line) {
        if (line == null) return Integer.MIN_VALUE;
        if (line.mateIn > 0) return 1_000_000 - line.mateIn;
        if (line.mateIn < 0) return -1_000_000 - line.mateIn;
        return line.score;
    }

    // ========== 构造函数 ==========
    public PikafishAI(Context context) {
        this.context = context;
        new Thread(() -> initialize()).start();
    }

    // ========== 初始化（带重试）==========
    private void initialize() {
        // 全局锁：确保与 close() 互斥，防止 Activity 重建时新旧实例竞争 native 引擎
        synchronized (GLOBAL_INIT_LOCK) {
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
        } // synchronized (GLOBAL_INIT_LOCK)
    }

    /**
     * 手动触发重新初始化（初始化失败后重试）。
     * 线程安全，若已在初始化中则直接返回。
     */
    public void retryInitialize() {
        if (initialized) return;
        synchronized (initLock) {
            if (initialized) return;
            if (initInProgress) return;
        }
        new Thread(() -> initialize()).start();
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

            // 验证 NNUE 文件可读
            File nnueFile = new File(nnuePath);
            if (!nnueFile.canRead() || nnueFile.length() <= 0) {
                LogUtils.e("PikafishAI", "NNUE 文件不可读或为空: " + nnuePath
                    + " canRead=" + nnueFile.canRead() + " length=" + nnueFile.length());
                return false;
            }

            // 2. 获取 native library 目录
            String libPath = context.getApplicationInfo().nativeLibraryDir;

            // 3. 初始化前内存诊断
            Runtime rt = Runtime.getRuntime();
            long freeMemory = rt.freeMemory();
            long totalMemory = rt.totalMemory();
            long maxMemory = rt.maxMemory();
            long availableHeap = maxMemory - totalMemory + freeMemory;
            LogUtils.i("PikafishAI", "初始化前内存状态 - 可用堆=" + (availableHeap / 1024 / 1024)
                + "MB, 最大堆=" + (maxMemory / 1024 / 1024) + "MB, NNUE大小=" + (nnueFile.length() / 1024 / 1024) + "MB");

            // 4. 调用 JNI 初始化（此时引擎已启动，内部发了 uci 命令）
            if (!libraryLoaded) {
                LogUtils.e("PikafishAI", "库未加载，无法初始化引擎");
                return false;
            }
            LogUtils.i("PikafishAI", "调用 nativeInit: nnuePath=" + nnuePath
                + " size=" + nnueFile.length() + " libPath=" + libPath);
            long initStartTime = System.currentTimeMillis();
            boolean initOk;
            try {
                initOk = nativeInit(nnuePath, libPath);
            } catch (Throwable t) {
                // nativeInit 可能抛出 UnsatisfiedLinkError 或其他 Error
                long cost = System.currentTimeMillis() - initStartTime;
                LogUtils.e("PikafishAI", "nativeInit 抛出异常 (耗时=" + cost + "ms): "
                    + t.getClass().getName() + ": " + t.getMessage(), t);
                return false;
            }
            long initCost = System.currentTimeMillis() - initStartTime;
            if (!initOk) {
                LogUtils.e("PikafishAI", "nativeInit 返回 false (耗时=" + initCost + "ms)");
                return false;
            }
            LogUtils.i("PikafishAI", "nativeInit 返回成功 (耗时=" + initCost + "ms)");
            // nativeInit 返回成功后才标记，确保 cleanup 时可以安全调用 nativeCleanup
            nativeInited = true;

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
        // 只有 nativeInit 成功返回后才调用 nativeCleanup，避免对半初始化状态调用清理
        if (nativeInited) {
            nativeLock.lock();
            try {
                if (libraryLoaded) {
                    nativeCleanup();
                }
            } catch (Throwable t) {
                LogUtils.w("PikafishAI", "cleanupAfterFailedInit: nativeCleanup 抛出异常: "
                    + t.getClass().getName() + ": " + t.getMessage());
            } finally {
                nativeLock.unlock();
            }
            // 无论 nativeCleanup 是否成功，都重置标志，避免重复调用
            nativeInited = false;
        }
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
    private static final int VALID_MULTIPV_MIN = 1;
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

        // Threads（值未变则跳过，避免不必要的引擎操作）
        boolean threadsAuto = cachedThreads <= 0;
        int threadCount = threadsAuto ? computeAutoThreads() : cachedThreads;
        if (threadCount != lastAppliedThreadCount) {
            sendCommand("setoption name Threads value " + threadCount);
            lastAppliedThreadCount = threadCount;
        }

        // Hash（值未变则跳过，Hash 重分配代价高）
        boolean hashAuto = cachedHashMB <= 0;
        int hashSize = hashAuto ? getOptimalHashSize() : cachedHashMB;
        if (hashSize != lastAppliedHashSize) {
            sendCommand("setoption name Hash value " + hashSize);
            lastAppliedHashSize = hashSize;
        }

        // Skill Level / Contempt：引擎原生不支持（UNSUPPORTED_OPTIONS），不发送

        // NumaPolicy
        sendCommand("setoption name NumaPolicy value " + cachedNumaPolicy);

        // MultiPV（validateAndClampAllParams 已确保 >=1，直接使用缓存值）
        sendCommand("setoption name MultiPV value " + cachedMultiPV);

        LogUtils.i("PikafishAI",
            "引擎参数已应用: Threads=" + threadCount + (threadsAuto ? "(auto)" : "") + " Hash=" + hashSize
            + "MB" + (hashAuto ? "(auto)" : "") + " MultiPV=" + cachedMultiPV + " NumaPolicy=" + cachedNumaPolicy);
    }

    /**
     * 单独设置 MultiPV（线程安全）。用于强制变着/恢复正常模式时仅覆盖此参数，
     * 避免重复下发 Threads/Hash/NumaPolicy 等不变项。
     */
    private void applyMultiPV(int multiPV) {
        sendCommand("setoption name MultiPV value " + multiPV);
    }

    private void setupEngineOptions() {
        // 1. 从持久化设置读取参数到缓存
        readSettingsFromFile();
        // 2. 夹紧校验
        validateAndClampAllParams();
        // 3. 下发给引擎
        applyAllEngineOptions();
        // 注意：初始化阶段不发送 Clear Hash，刚启动的引擎哈希表本身为空，
        // Clear Hash 在某些引擎版本中可能导致 hang。新游戏时通过 clearHash() 调用。
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
                cachedSkillLevel = setting.skillLevel;
                cachedDepth = setting.depth;
                cachedMultiPV = setting.multiPV;
                cachedTimeSeconds = setting.mLevel;
                cachedThreads = setting.threads;
                cachedHashMB = setting.hashMB;
                cachedContempt = setting.contempt;
                cachedNumaPolicy = (setting.numaPolicy != null && !setting.numaPolicy.isEmpty())
                    ? setting.numaPolicy : "auto";
            }
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "读取设置失败: " + e.getMessage());
        }
    }

    /** 根据设备核心数自动计算线程数 */
    private int computeAutoThreads() {
        int totalCores = Runtime.getRuntime().availableProcessors();
        // 棋类引擎搜索时 UI 线程几乎空闲，仅预留 1 核
        // proot 模式通常用全部核心，这里尽量接近
        int reservedCores = totalCores <= 2 ? 0 : 1;
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
        if (!libraryLoaded) {
            LogUtils.w("PikafishAI", "库未加载，不启动输出读取线程");
            readerExited = true;
            return;
        }
        readerRunning = true;
        readerExited = false;
        outputReaderThread = new Thread(() -> {
            LogUtils.i("PikafishAI", "输出读取线程启动");
            try {
                while (readerRunning) {
                    try {
                        String line = nativeReadLine();
                        if (line == null) {
                            // EOF (引擎关闭)
                            LogUtils.i("PikafishAI", "输出读取线程: 收到 EOF");
                            break;
                        }
                        outputQueue.offer(line);
                    } catch (Throwable t) {
                        // 捕获 Exception 和 Error，确保线程退出时能正确设置标志
                        if (readerRunning) {
                            LogUtils.e("PikafishAI", "输出读取异常: " + t.getClass().getName()
                                + ": " + t.getMessage(), t);
                        }
                        break;
                    }
                }
            } finally {
                readerExited = true;
                readerRunning = false;
                LogUtils.i("PikafishAI", "输出读取线程退出");
            }
        }, "pikafish-output-reader");
        outputReaderThread.setDaemon(false);
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
     * 库未加载时直接返回，避免 UnsatisfiedLinkError。
     */
    private void sendCommand(String command) {
        if (!libraryLoaded) {
            LogUtils.w("PikafishAI", "库未加载，跳过命令: " + command);
            return;
        }
        nativeLock.lock();
        try {
            nativeSendCommand(command);
        } catch (Throwable t) {
            LogUtils.e("PikafishAI", "nativeSendCommand 抛出异常: " + t.getClass().getName()
                + ": " + t.getMessage() + ", command=" + command, t);
        } finally {
            nativeLock.unlock();
        }
    }

    // ========== NNUE 文件复制 ==========
    private String copyNNUEFile() {
        try {
            File cacheDir = context.getCacheDir();
            File nnueFile = new File(cacheDir, "pikafish.nnue");

            // 获取 assets 中原始文件大小（noCompress 配置后 openFd 可直接获取）
            long expectedSize = -1;
            try (android.content.res.AssetFileDescriptor afd = context.getAssets().openFd("pikafish.nnue")) {
                expectedSize = afd.getLength();
            } catch (Exception ignored) {
                // openFd 失败时忽略，后续按旧逻辑处理
            }

            // 如果已存在且大小匹配，直接返回
            if (nnueFile.exists() && nnueFile.length() > 0) {
                if (expectedSize <= 0 || nnueFile.length() == expectedSize) {
                    LogUtils.i("PikafishAI", "NNUE 文件已存在: " + nnueFile.length() + " bytes");
                    return nnueFile.getAbsolutePath();
                }
                LogUtils.w("PikafishAI", "NNUE 文件大小不匹配 (expected=" + expectedSize
                    + ", actual=" + nnueFile.length() + ")，重新复制");
                nnueFile.delete();
            }

            // 复制文件
            long totalBytes = 0;
            try (InputStream is = context.getAssets().open("pikafish.nnue");
                 FileOutputStream os = new FileOutputStream(nnueFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                    totalBytes += length;
                }
            }

            if (totalBytes <= 0) {
                LogUtils.e("PikafishAI", "NNUE 文件复制后大小为 0");
                nnueFile.delete();
                return null;
            }

            LogUtils.i("PikafishAI", "NNUE 文件复制成功: " + totalBytes + " bytes -> " + nnueFile.getAbsolutePath());
            return nnueFile.getAbsolutePath();
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "复制 NNUE 文件失败: " + e.getMessage(), e);
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
    // Hash 表分配在 native 内存，不受 Java 堆限制，应基于设备物理内存计算
    private static volatile Long cachedTotalMemory = null;

    /** 获取设备物理内存总量（字节），读取 /proc/meminfo */
    private long getTotalPhysicalMemory() {
        if (cachedTotalMemory != null) return cachedTotalMemory;
        synchronized (PikafishAI.class) {
            if (cachedTotalMemory != null) return cachedTotalMemory;
            long memTotal = 0;
            try (java.io.FileReader fr = new java.io.FileReader("/proc/meminfo");
                 java.io.BufferedReader br = new java.io.BufferedReader(fr)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("MemTotal:")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 2) {
                            memTotal = Long.parseLong(parts[1]) * 1024; // kB → bytes
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                LogUtils.w("PikafishAI", "读取 /proc/meminfo 失败，使用 Java 堆估算: " + e.getMessage());
            }
            if (memTotal <= 0) {
                // 降级：用 Java 堆 * 4 作为粗略估算
                memTotal = Runtime.getRuntime().maxMemory() * 4;
            }
            cachedTotalMemory = memTotal;
            LogUtils.i("PikafishAI", "设备物理内存: " + (memTotal / 1024 / 1024) + "MB");
            return memTotal;
        }
    }

    private int getOptimalHashSize() {
        try {
            long totalMem = getTotalPhysicalMemory();
            int totalMemMB = (int) (totalMem / (1024 * 1024));
            // Hash 表是 native 内存，不受 Java 堆限制
            // 分配策略：物理内存的 5-10%，留足空间给 NNUE(50MB) + 引擎运行时 + Android 系统
            if (totalMemMB >= 6144) return 512;    // 6GB+ 设备: 512MB
            else if (totalMemMB >= 4096) return 256; // 4-6GB 设备: 256MB
            else if (totalMemMB >= 2048) return 128; // 2-4GB 设备: 128MB
            else if (totalMemMB >= 1024) return 64;  // 1-2GB 设备: 64MB
            else if (totalMemMB >= 512) return 32;   // 512MB-1GB 设备: 32MB
            else return 16;                           // 小内存设备: 16MB
        } catch (Exception e) {
            return 64; // 默认值
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

        // 获取搜索锁（等待快速评估等短期操作完成，最多等2秒）
        if (!searchLock.tryLock()) {
            LogUtils.i("PikafishAI", "搜索锁被占用，等待释放...");
            long waitStart = System.currentTimeMillis();
            while (!searchLock.tryLock()) {
                if (System.currentTimeMillis() - waitStart > 2000) {
                    LogUtils.e("PikafishAI", "等待搜索锁超时（2s），放弃本次搜索");
                    return new MoveWithScore(getDefaultMove(chessInfo), 0);
                }
                try { Thread.sleep(50); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new MoveWithScore(getDefaultMove(chessInfo), 0);
                }
            }
            LogUtils.i("PikafishAI", "搜索锁已获取（等待了" + (System.currentTimeMillis() - waitStart) + "ms）");
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

            // 应用延迟的引擎参数（搜索中无法下发的 setoption）
            if (pendingOptionsUpdate.compareAndSet(true, false)) {
                applyAllEngineOptions();
                LogUtils.i("PikafishAI", "已应用延迟的引擎参数");
            }

            // 记录本次搜索的代际，用于检测中断
            final int myGeneration = searchGeneration.get();
            shouldStop.set(false);
            isSearching.set(true);
            currentDepth.set(0);

            // 计算搜索参数：使用缓存值（已在 updateSettings 中校验范围）
            int depth = cachedDepth;
            int timeMs = cachedTimeSeconds * 1000;

            // 下发引擎参数：强制变着模式仅覆盖 MultiPV
            boolean wasForceVariation = false;
            if (chessInfo != null && chessInfo.forceVariation) {
                wasForceVariation = true;
                int randomness = Math.max(1, chessInfo.variationRandomness);
                depth = depth + randomness;
                int varMultiPV = Math.max(2, cachedMultiPV);
                applyMultiPV(varMultiPV);
                LogUtils.i("PikafishAI", "强制变着模式: MultiPV=" + varMultiPV
                    + " depth=" + depth + " movetime=" + timeMs + "ms randomness=" + randomness);
            } else {
                // 正常模式恢复用户设置的 MultiPV（单个命令廉价，幂等）
                applyMultiPV(cachedMultiPV);
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

            boolean stopSent = false;
            long stopSentTime = 0;
            while (!Thread.currentThread().isInterrupted()) {
                // 代际变化表示被中断，退出搜索
                if (searchGeneration.get() != myGeneration) {
                    if (!stopSent) {
                        sendCommand("stop");
                    }
                    break;
                }
                long elapsed = System.currentTimeMillis() - startTime;

                // 超时时发送 stop（只发一次），继续等待 bestmove
                // 确保拿到引擎确认的最优招法，而非搜索中间状态的候选
                if (!stopSent) {
                    if (elapsed > maxSearchTime) {
                        LogUtils.w("PikafishAI", "搜索超时, 强制停止");
                        sendCommand("stop");
                        stopSent = true;
                        stopSentTime = elapsed;
                    } else if (shouldStop.get()) {
                        LogUtils.i("PikafishAI", "收到停止信号, 停止搜索");
                        sendCommand("stop");
                        stopSent = true;
                        stopSentTime = elapsed;
                    }
                } else if (elapsed - stopSentTime > 300) {
                    // stop 发送后 300ms 还未收到 bestmove，放弃等待
                    LogUtils.e("PikafishAI", "停止后等待 bestmove 超时（300ms）");
                    break;
                }

                String line = readLineWithTimeout(100);
                if (line == null) {
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

            if (bestMoveStr != null) {
                Move move = uciToMove(bestMoveStr);
                LogUtils.i("PikafishAI", "最佳走法: " + move + ", 评分: " + score);
                return new MoveWithScore(move, score);
            }
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "搜索异常: " + e.getMessage(), e);
        } finally {
            isSearching.set(false);
            // 搜索结束后把真实最终深度刷新给 UI。
            // 否则界面只依赖每 1 秒一次的 DepthUpdateRunnable：当一次搜索在 1 秒内
            // 完成（思考时间设短 / 简单或一步杀局面）时，定时任务来不及刷新，
            // 深度会停在开始思考时的占位值 1。
            try {
                updateUIAfterSearch(currentDepth.get());
            } catch (Exception ignored) {
            }
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

        // 记录本次搜索的代际，用于检测中断
        final int myGeneration = searchGeneration.get();

        // 尝试获取搜索锁。搜索锁由主 AI 搜索（getBestMoveWithScore /
        // getPvSequenceWithScore）独占，快速评估与它们共用同一引擎，必须串行。
        // 注意：拿不到锁时绝不能再发送 stop —— 那样会中断正在进行的主搜索，
        // 表现为"搜索被中断"、深度停在占位值 1。正确做法是等待主搜索释放锁。
        if (!searchLock.tryLock()) {
            long waitStart = System.currentTimeMillis();
            while (!searchLock.tryLock()) {
                if (System.currentTimeMillis() - waitStart > 2000) {
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

            String fen = boardToFEN(chessInfo);
            // 快速评估：复用引擎现有参数，不下发 Hash/Threads（避免重分配和性能损耗）
            // 仅确保 MultiPV=1（快速评估只需单路主变）
            if (cachedMultiPV > 1) {
                sendCommand("setoption name MultiPV value 1");
            }
            sendCommand("position fen " + fen);
            // 快速评估固定 depth=1、movetime=300ms，保证响应速度
            sendCommand("go depth 1 movetime 300");

            int score = 0;
            long startTime = System.currentTimeMillis();
            long maxWait = 2000;

            while (!Thread.currentThread().isInterrupted() && searchGeneration.get() == myGeneration) {
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
            // 恢复 MultiPV 设置（如果之前修改过）
            if (cachedMultiPV > 1) {
                sendCommand("setoption name MultiPV value " + cachedMultiPV);
            }
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

        // 获取搜索锁（等待快速评估等短期操作完成，最多等2秒）
        if (!searchLock.tryLock()) {
            LogUtils.i("PikafishAI", "搜索锁被占用（PV序列），等待释放...");
            long waitStart = System.currentTimeMillis();
            while (!searchLock.tryLock()) {
                if (System.currentTimeMillis() - waitStart > 2000) {
                    LogUtils.e("PikafishAI", "等待搜索锁超时（2s），放弃 PV 序列请求");
                    return new PvSequenceWithScore(new ArrayList<>(), 0);
                }
                try { Thread.sleep(50); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new PvSequenceWithScore(new ArrayList<>(), 0);
                }
            }
        }

        try {
            stopPreviousSearch();
            drainOutputQueue();

            // 应用延迟的引擎参数（搜索中无法下发的 setoption）
            if (pendingOptionsUpdate.compareAndSet(true, false)) {
                applyAllEngineOptions();
                LogUtils.i("PikafishAI", "已应用延迟的引擎参数（PV序列）");
            }

            // 调用方已通过 updateSettings 同步并下发参数，此处直接使用缓存值
            int depth = cachedDepth;
            int timeMs = cachedTimeSeconds * 1000;
            LogUtils.i("PikafishAI", "PV搜索参数: depth=" + depth
                + " time=" + cachedTimeSeconds + "s"
                + " skillLevel=" + cachedSkillLevel);

            // 记录本次搜索的代际，用于检测中断
            final int myGeneration = searchGeneration.get();
            shouldStop.set(false);
            isSearching.set(true);
            currentDepth.set(0);

            // 下发引擎参数：强制变着模式仅覆盖 MultiPV；支招结果框需展示多条候选变线，至少 5 条
            int effectiveMultiPV = cachedMultiPV;
            if (chessInfo != null && chessInfo.forceVariation) {
                depth = depth + Math.max(1, chessInfo.variationRandomness);
                effectiveMultiPV = Math.max(2, cachedMultiPV);
            }
            // 保证支招结果框至少展示 5 条候选变线
            effectiveMultiPV = Math.max(5, effectiveMultiPV);
            applyMultiPV(effectiveMultiPV);
            LogUtils.i("PikafishAI", "支招搜索 MultiPV=" + effectiveMultiPV);

            String fen = boardToFEN(chessInfo);
            sendCommand("position fen " + fen);

            String goCmd = "go depth " + depth + " movetime " + timeMs;
            LogUtils.i("PikafishAI", "发送搜索命令: " + goCmd);
            sendCommand(goCmd);

            String bestMoveStr = null;
            int score = 0;
            // 按 multipv 序号分别收集多条候选变线
            java.util.Map<Integer, java.util.List<String>> pvMap = new java.util.LinkedHashMap<>();
            java.util.Map<Integer, Integer> scoreMap = new java.util.HashMap<>();
            java.util.Map<Integer, Integer> mateInMap = new java.util.HashMap<>();
            int maxMultiPVSeen = 1;
            long startTime = System.currentTimeMillis();
            long maxSearchTime = timeMs + getSearchTimeBuffer(timeMs);
            int infoLineCount = 0;
            int maxDepthSeen = 0;

            boolean stopSent = false;
            long stopSentTime = 0;
            while (!Thread.currentThread().isInterrupted() && searchGeneration.get() == myGeneration) {
                long elapsed = System.currentTimeMillis() - startTime;

                // 超时或收到停止信号时发送 stop（只发一次），继续等待 bestmove
                // 确保拿到引擎确认的最优招法，而非搜索中间状态的候选
                if (!stopSent) {
                    if (elapsed > maxSearchTime) {
                        LogUtils.w("PikafishAI", "搜索超时(" + elapsed + "ms >= " + maxSearchTime + "ms)，强制停止");
                        sendCommand("stop");
                        stopSent = true;
                        stopSentTime = elapsed;
                    } else if (shouldStop.get()) {
                        LogUtils.i("PikafishAI", "收到停止信号, 停止搜索");
                        sendCommand("stop");
                        stopSent = true;
                        stopSentTime = elapsed;
                    }
                } else if (elapsed - stopSentTime > 500) {
                    LogUtils.e("PikafishAI", "停止后等待 bestmove 超时（500ms）");
                    break;
                }

                String line = readLineWithTimeout(100);
                if (line == null) {
                    continue;
                }

                if (line.startsWith("info")) {
                    infoLineCount++;
                    String[] parts = line.split(" ");
                    // 第一遍：解析 depth 与 multipv 序号
                    int infoMultiPV = 1;
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("multipv") && i + 1 < parts.length) {
                            try { infoMultiPV = Integer.parseInt(parts[i + 1]); } catch (NumberFormatException ignored) {}
                        } else if (parts[i].equals("depth") && i + 1 < parts.length) {
                            try {
                                int newDepth = Integer.parseInt(parts[i + 1]);
                                int cur = currentDepth.get();
                                if (newDepth > cur) currentDepth.set(newDepth);
                                if (newDepth > maxDepthSeen) maxDepthSeen = newDepth;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    if (infoMultiPV > maxMultiPVSeen) maxMultiPVSeen = infoMultiPV;
                    // 第二遍：按 multipv 序号分别记录 score 与 pv
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("score") && i + 2 < parts.length) {
                            int s = 0;
                            int mateIn = 0;
                            if (parts[i + 1].equals("cp")) {
                                try { s = Integer.parseInt(parts[i + 2]); } catch (NumberFormatException ignored) {}
                            } else if (parts[i + 1].equals("mate")) {
                                try {
                                    mateIn = Integer.parseInt(parts[i + 2]);
                                    s = mateIn > 0 ? 1000 - mateIn * 10 : -1000 + mateIn * 10;
                                } catch (NumberFormatException ignored) {}
                            }
                            scoreMap.put(infoMultiPV, s);
                            mateInMap.put(infoMultiPV, mateIn);
                            if (infoMultiPV == 1) score = s;
                        } else if (parts[i].equals("pv") && i + 1 < parts.length) {
                            java.util.List<String> cur = new java.util.ArrayList<>();
                            for (int j = i + 1; j < parts.length; j++) {
                                cur.add(parts[j]);
                            }
                            pvMap.put(infoMultiPV, cur);
                            if (infoMultiPV == 1 && bestMoveStr == null) {
                                bestMoveStr = parts[i + 1];
                            }
                        }
                    }
                } else if (line.startsWith("bestmove")) {
                    // bestmove 行是引擎最终确认的最优步，必须覆盖 info 行的中间结果
                    String[] parts = line.split(" ");
                    if (parts.length > 1) {
                        bestMoveStr = parts[1];
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
                + " bestmove=" + bestMoveStr + " pv长度=" + (pvMap.get(1) != null ? pvMap.get(1).size() : 0)
                + " score=" + score);

            // 组装多条候选变线（按 multipv 序号 1..N，索引 1 为最佳）
            java.util.List<PikafishAI.PvSequenceWithScore> multiLines = new java.util.ArrayList<>();
            for (int idx = 1; idx <= maxMultiPVSeen; idx++) {
                java.util.List<String> pvl = pvMap.get(idx);
                if (pvl != null && !pvl.isEmpty()) {
                    java.util.List<Move> seq = new java.util.ArrayList<>();
                    for (String uciMove : pvl) {
                        Move mv = uciToMove(uciMove);
                        if (mv != null) seq.add(mv);
                    }
                    Integer sc = scoreMap.get(idx);
                    PikafishAI.PvSequenceWithScore line = new PikafishAI.PvSequenceWithScore(seq, sc == null ? 0 : sc);
                    Integer mi = mateInMap.get(idx);
                    line.mateIn = (mi == null ? 0 : mi);
                    multiLines.add(line);
                }
            }
            // 按评分从高到低排序，保证评分最高的变线展示在最上面。
            // 注意：将死（mate）变线的 score 通常为 0，胜负由 mateIn 表示，
            // 因此排序需把 mate 纳入优先度（杀棋最高、被将死最低），否则最优的“杀N”会变线沉到后面。
            java.util.Collections.sort(multiLines, (a, b) -> Integer.compare(rankPvLine(b), rankPvLine(a)));
            lastMultiPvLines = multiLines;

            // 主变线（multipv 1）用于界面支招高亮，保持原有行为
            java.util.List<Move> moveSequence = new java.util.ArrayList<>();
            java.util.List<String> mainPv = pvMap.get(1);
            if (mainPv != null) {
                for (String uciMove : mainPv) {
                    Move m = uciToMove(uciMove);
                    if (m != null) moveSequence.add(m);
                }
            }

            PvSequenceWithScore result = new PvSequenceWithScore(moveSequence, score);
            Integer mainMate = mateInMap.get(1);
            result.mateIn = (mainMate == null ? 0 : mainMate);
            return result;
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
        searchGeneration.incrementAndGet();
        shouldStop.set(true);
        // 尝试获取锁确保 stop 在 go 之后发送
        if (searchLock.tryLock()) {
            try {
                if (isSearching.get()) {
                    sendCommand("stop");
                }
            } finally {
                searchLock.unlock();
            }
        }
        // 如果获取不到锁，搜索线程会在循环中检测到代际变化并自行发送 stop
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

        // 搜索进行中不下发 setoption（尤其 Hash 会重新分配置换表内存，导致 native 崩溃）
        if (isSearching.get()) {
            pendingOptionsUpdate.set(true);
            LogUtils.i("PikafishAI", "搜索进行中，引擎参数延迟应用: SkillLevel=" + cachedSkillLevel +
                " MultiPV=" + cachedMultiPV + " Depth=" + cachedDepth +
                " Time=" + cachedTimeSeconds + "s Threads=" + cachedThreads +
                " Hash=" + cachedHashMB + "MB");
        } else {
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
    }

    public void close() {
        // 持有全局锁，防止与新 PikafishAI 的 initialize() 并发
        synchronized (GLOBAL_INIT_LOCK) {
            shouldStop.set(true);
            // 先中断当前搜索，等待搜索线程退出
            searchGeneration.incrementAndGet();
            shouldStop.set(true);
            if (searchLock.tryLock()) {
                try {
                    if (isSearching.get()) {
                        sendCommand("stop");
                    }
                } finally {
                    searchLock.unlock();
                }
            }
            isSearching.set(false);

            boolean engineQuitCleanly = false;

        try {
            // 1. 先发送 quit 命令，让引擎主动关闭 stdout 管道
            //    这样 nativeReadLine 会返回 null（EOF），读取线程自然退出
            sendCommand("quit");

            // 2. 等待读取线程检测到 EOF 并自然退出（带超时）
            long waitStart = System.currentTimeMillis();
            while (System.currentTimeMillis() - waitStart < ENGINE_QUIT_TIMEOUT_MS) {
                if (readerExited) {
                    engineQuitCleanly = true;
                    LogUtils.i("PikafishAI", "引擎已正常退出（读取线程检测到 EOF）");
                    break;
                }
                try { Thread.sleep(50); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (!engineQuitCleanly) {
                LogUtils.w("PikafishAI", "引擎退出超时（" + ENGINE_QUIT_TIMEOUT_MS + "ms），强制停止读取线程");
                // 强制停止读取线程（注意：阻塞在 nativeReadLine 的线程可能无法响应中断）
                stopOutputReaderThread();
            }
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "发送 quit 命令异常: " + e.getMessage());
            // 异常情况下也确保停止读取线程
            stopOutputReaderThread();
        }

        // 3. 调用 JNI cleanup（线程安全，持有 nativeLock）
        if (nativeInited) {
            nativeLock.lock();
            try {
                if (libraryLoaded) {
                    nativeCleanup();
                }
            } catch (Throwable t) {
                LogUtils.e("PikafishAI", "关闭引擎 nativeCleanup 抛出异常: "
                    + t.getClass().getName() + ": " + t.getMessage());
            } finally {
                nativeLock.unlock();
            }
            // 无论 nativeCleanup 是否成功，都重置标志，避免重复调用
            nativeInited = false;
        }

        initialized = false;
        currentDepth.set(0);
        outputQueue.clear();
        // 重建 latch 以备下次初始化
        initLatchRef.set(new CountDownLatch(1));
        synchronized (initLock) { initInProgress = false; }
        LogUtils.i("PikafishAI", "引擎已关闭" + (engineQuitCleanly ? "（正常退出）" : "（强制退出）"));
        } // synchronized (GLOBAL_INIT_LOCK)
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
