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

public class PikafishAI {
    private static final int DEFAULT_DEPTH = 20;
    private static final int DEFAULT_TIME_MS = 10000;
    private static final int MIN_DEPTH = 5;
    private static final int MIN_TIME_MS = 1000;
    private static final long INIT_TIMEOUT_MS = 10000;
    private static final long SHUTDOWN_WAIT_SECONDS = 5;
    private static final long MAX_SEARCH_TIME_BUFFER_MS = 5000; // 额外缓冲时间5秒
    
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean initialized = false;
    private Context context;
    private volatile boolean isSearching = false;
    private volatile boolean shouldStop = false;
    
    public PikafishAI(Context context) {
        this.context = context;
        // 在后台线程中初始化，避免阻塞主线程
        new Thread(() -> {
            initialize();
        }).start();
    }
    
    private void initialize() {
        try {
            // 检查是否在模拟器中运行
            boolean isEmulator = isRunningInEmulator();
            if (isEmulator) {
                Log.e("PikafishAI", "在模拟器中运行，尝试初始化AI");

                // 不再跳过初始化，尝试在模拟器中也初始化AI
            }
            
            // 复制神经网络文件到缓存目录
            copyNNUEFile();
            
            // 获取Pikafish可执行文件的路径
            String binaryPath = context.getApplicationInfo().nativeLibraryDir + "/libpikafish.so";
            
            // 检查文件是否存在
            File binaryFile = new File(binaryPath);
            if (!binaryFile.exists()) {
                // 尝试从assets复制
                copyBinary();
                binaryPath = context.getCacheDir().getAbsolutePath() + "/pikafish";
                binaryFile = new File(binaryPath);
                if (!binaryFile.exists()) {
                    return;
                }
            }
            
            // 设置可执行权限
            try {
                // 尝试多种权限设置方式
                Process chmodProcess = Runtime.getRuntime().exec("chmod 755 " + binaryPath);
                chmodProcess.waitFor();
                
                // 验证文件权限
                boolean isExecutable = binaryFile.canExecute();
                
                // 如果仍然不可执行，尝试使用另一种方式
                if (!isExecutable) {
                    chmodProcess = Runtime.getRuntime().exec("chmod u+x " + binaryPath);
                    chmodProcess.waitFor();
                    isExecutable = binaryFile.canExecute();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // 启动Pikafish进程
            try {
                ProcessBuilder pb = new ProcessBuilder(binaryPath);
                pb.redirectErrorStream(true);
                // 设置工作目录为缓存目录，确保引擎能找到pikafish.nnue文件
                pb.directory(context.getCacheDir());
                process = pb.start();
            } catch (Exception e) {
                e.printStackTrace();
                
                // 尝试使用proot启动
                try {
                    String prootPath = context.getCacheDir().getAbsolutePath() + "/proot";
                    
                    // 复制proot到应用目录
                    try {
                        // 根据CPU架构选择合适的proot文件
                        String prootName = "proot-v5.3.0-aarch64-static";
                        String cpuAbi = android.os.Build.CPU_ABI;
                        if (cpuAbi.contains("arm")) {
                            prootName = "proot-v5.3.0-arm-static";
                        }
                        
                        InputStream is = context.getAssets().open(prootName);
                        FileOutputStream os = new FileOutputStream(prootPath);
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = is.read(buffer)) > 0) {
                            os.write(buffer, 0, length);
                        }
                        is.close();
                        os.close();
                        
                        // 设置proot可执行权限
                        Process chmodProcess = Runtime.getRuntime().exec("chmod 755 " + prootPath);
                        chmodProcess.waitFor();
                        Log.e("PikafishAI", "复制并设置proot权限成功");
                    } catch (Exception ex) {
                        Log.e("PikafishAI", "复制proot失败: " + ex.getMessage());
                        return;
                    }
                    
                    // 使用proot启动pikafish
                    Log.e("PikafishAI", "尝试使用proot启动: " + prootPath + " " + binaryPath);
                    ProcessBuilder pb = new ProcessBuilder(prootPath, "--bind=/system", "--bind=/vendor", "--bind=/data", binaryPath);
                    pb.redirectErrorStream(true);
                    // 设置工作目录为缓存目录，确保引擎能找到pikafish.nnue文件
                    pb.directory(context.getCacheDir());
                    process = pb.start();
                    Log.e("PikafishAI", "使用proot启动进程成功");
                    LogUtils.i("PikafishAI", "使用proot启动进程成功");
                } catch (Exception ex) {
                    Log.e("PikafishAI", "使用proot启动失败: " + ex.getMessage());
                    LogUtils.e("PikafishAI", "使用proot启动失败: " + ex.getMessage());
                    ex.printStackTrace();
                    return;
                }
            }
            
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            
            // 发送UCI命令初始化
            sendCommand("uci");
            
            // 读取初始化响应
            String line;
            long startTime = System.currentTimeMillis();
            
            while ((line = reader.readLine()) != null) {
                Log.e("PikafishAI", "初始化响应: " + line);
                LogUtils.d("PikafishAI", "初始化响应: " + line);
                if (line.equals("uciok")) {
                    initialized = true;
                    Log.e("PikafishAI", "UCI初始化成功");
                    LogUtils.i("PikafishAI", "UCI初始化成功");
                    break;
                }
                if (System.currentTimeMillis() - startTime > INIT_TIMEOUT_MS) {
                    Log.e("PikafishAI", "UCI初始化超时");
                    LogUtils.e("PikafishAI", "UCI初始化超时");
                    break;
                }
            }
            
            if (initialized) {
                // 设置基本参数
                // 1. 设置线程数（根据设备CPU核心数，至少给系统保留2-4个核心）
                int totalCores = Runtime.getRuntime().availableProcessors();
                int reservedCores;
                if (totalCores <= 4) {
                    reservedCores = 2; // 4核及以下设备，保留2个核心
                } else if (totalCores <= 8) {
                    reservedCores = 3; // 8核及以下设备，保留3个核心
                } else {
                    reservedCores = 4; // 8核以上设备，保留4个核心
                }
                int threadCount = Math.max(1, totalCores - reservedCores);
                sendCommand("setoption name Threads value " + threadCount);
                LogUtils.i("PikafishAI", "设置线程数: " + threadCount + " (总核心数: " + totalCores + ", 保留核心数: " + reservedCores + ")");
                
                // 2. 设置哈希表大小（根据设备内存情况）
                int hashSize = getOptimalHashSize();
                sendCommand("setoption name Hash value " + hashSize);
                LogUtils.i("PikafishAI", "设置哈希表大小: " + hashSize + " MB");
                
                // 3. 获取设置值
                int skillLevel = 20; // 默认最高级别
                int multiPV = 1; // 默认单主变
                int contempt = 20; // 默认值
                try {
                    // 尝试获取Setting中的值
                    Class<?> pvmaClass = Class.forName("top.nones.chessgame.PvMActivity");
                    Object settingObj = pvmaClass.getField("setting").get(null);
                    if (settingObj != null) {
                        skillLevel = (int) settingObj.getClass().getField("skillLevel").get(settingObj);
                        multiPV = (int) settingObj.getClass().getField("multiPV").get(settingObj);
                        // 尝试获取Contempt设置，如果存在的话
                        try {
                            contempt = (int) settingObj.getClass().getField("contempt").get(settingObj);
                        } catch (NoSuchFieldException e) {
                            // 如果没有Contempt设置，使用默认值
                            LogUtils.i("PikafishAI", "没有找到Contempt设置，使用默认值: " + contempt);
                        }
                    }
                } catch (Exception e) {
                    LogUtils.e("PikafishAI", "获取设置值失败: " + e.getMessage());
                }
                
                // 4. 设置多主变（MultiPV）
                // 确保 MultiPV 至少为 2，让引擎考虑多个可能的走法，避免重复
                multiPV = Math.max(2, multiPV);
                sendCommand("setoption name MultiPV value " + multiPV);
                LogUtils.i("PikafishAI", "设置MultiPV: " + multiPV);
                
                // 5. 设置技能级别
                sendCommand("setoption name Skill Level value " + skillLevel);
                LogUtils.i("PikafishAI", "设置技能级别: " + skillLevel);
                
                // 6. 设置 Contempt 值，鼓励引擎寻求胜利而非和棋，减少循环走法
                sendCommand("setoption name Contempt value " + contempt);
                LogUtils.i("PikafishAI", "设置Contempt值: " + contempt);
                
                // 等待参数设置完成
                sendCommand("isready");
                
                // 等待就绪
                startTime = System.currentTimeMillis();
                while ((line = reader.readLine()) != null) {
                    Log.e("PikafishAI", "就绪响应: " + line);
                    LogUtils.d("PikafishAI", "就绪响应: " + line);
                    if (line.equals("readyok")) {
                        Log.e("PikafishAI", "就绪成功");
                        LogUtils.i("PikafishAI", "就绪成功");
                        break;
                    }
                    if (System.currentTimeMillis() - startTime > INIT_TIMEOUT_MS) {
                        Log.e("PikafishAI", "就绪超时");
                        LogUtils.e("PikafishAI", "就绪超时");
                        break;
                    }
                }
            }
            
            Log.e("PikafishAI", "初始化完成，状态: " + initialized);
            LogUtils.i("PikafishAI", "初始化完成，状态: " + initialized);
            
        } catch (Exception e) {
            Log.e("PikafishAI", "初始化失败: " + e.getMessage());
            LogUtils.e("PikafishAI", "初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private boolean isRunningInEmulator() {
        String brand = android.os.Build.BRAND;
        String model = android.os.Build.MODEL;
        String product = android.os.Build.PRODUCT;
        
        // 检查常见的模拟器标识
        if (brand.equals("generic") || model.contains("sdk") || model.contains("emulator") || 
            product.equals("sdk") || product.equals("google_sdk") || product.equals("sdk_gphone64_arm64")) {
            return true;
        }
        
        // 检查是否存在模拟器特有的文件
        try {
            File qemuFile = new File("/system/bin/qemu-props");
            if (qemuFile.exists()) {
                return true;
            }
        } catch (Exception e) {
            // 忽略异常
        }
        
        return false;
    }
    
    private void copyNNUEFile() {
        try {
            Log.e("PikafishAI", "开始复制神经网络文件");
            InputStream is = context.getAssets().open("pikafish.nnue");
            FileOutputStream os = new FileOutputStream(context.getCacheDir().getAbsolutePath() + "/pikafish.nnue");
            
            byte[] buffer = new byte[1024];
            int length;
            int totalBytes = 0;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
                totalBytes += length;
            }
            
            is.close();
            os.close();
            Log.e("PikafishAI", "复制神经网络文件成功，大小: " + totalBytes + " bytes");
            LogUtils.i("PikafishAI", "复制神经网络文件成功");
        } catch (Exception e) {
            Log.e("PikafishAI", "复制神经网络文件失败: " + e.getMessage());
            LogUtils.e("PikafishAI", "复制神经网络文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void copyBinary() {
        try {
            // 根据设备架构选择合适的二进制文件
            String binaryName = "pikafish-armv8";
            String prootName = "proot-v5.3.0-aarch64-static";
            String cpuAbi = android.os.Build.CPU_ABI;
            Log.e("PikafishAI", "设备架构: " + cpuAbi);
            LogUtils.i("PikafishAI", "设备架构: " + cpuAbi);
            
            // 根据CPU架构选择合适的二进制文件
            if (cpuAbi.contains("arm64")) {
                binaryName = "pikafish-armv8";
                prootName = "proot-v5.3.0-aarch64-static";
            } else if (cpuAbi.contains("arm")) {
                binaryName = "pikafish-armv8";
                prootName = "proot-v5.3.0-arm-static";
            }
            
            Log.e("PikafishAI", "选择的二进制文件: " + binaryName);
            Log.e("PikafishAI", "选择的proot文件: " + prootName);
            
            // 尝试打开文件，如果失败则尝试其他版本
            try {
                // 复制可执行文件
                Log.e("PikafishAI", "开始复制主二进制文件: " + binaryName);
                InputStream is = context.getAssets().open(binaryName);
                FileOutputStream os = new FileOutputStream(context.getCacheDir().getAbsolutePath() + "/pikafish");
                
                byte[] buffer = new byte[1024];
                int length;
                int totalBytes = 0;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                    totalBytes += length;
                }
                
                is.close();
                os.close();
                Log.e("PikafishAI", "复制主二进制文件成功，大小: " + totalBytes + " bytes");
                LogUtils.i("PikafishAI", "复制主二进制文件成功");
            } catch (Exception e) {
                Log.e("PikafishAI", "复制主二进制文件失败，尝试备用版本: " + e.getMessage());
                LogUtils.e("PikafishAI", "复制主二进制文件失败，尝试备用版本: " + e.getMessage());
                // 尝试备用版本
                binaryName = "pikafish-armv8-dotprod";
                Log.e("PikafishAI", "开始复制备用二进制文件: " + binaryName);
                InputStream is = context.getAssets().open(binaryName);
                FileOutputStream os = new FileOutputStream(context.getCacheDir().getAbsolutePath() + "/pikafish");
                
                byte[] buffer = new byte[1024];
                int length;
                int totalBytes = 0;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                    totalBytes += length;
                }
                
                is.close();
                os.close();
                Log.e("PikafishAI", "复制备用二进制文件成功，大小: " + totalBytes + " bytes");
                LogUtils.i("PikafishAI", "复制备用二进制文件成功");
            }
            
            // 复制神经网络文件（已经在copyNNUEFile()中处理）
            
            Log.e("PikafishAI", "二进制文件复制完成");
            LogUtils.i("PikafishAI", "二进制文件复制完成");
            
        } catch (Exception e) {
            Log.e("PikafishAI", "复制二进制文件失败: " + e.getMessage());
            LogUtils.e("PikafishAI", "复制二进制文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 根据设备内存情况计算最佳哈希表大小
     * @return 哈希表大小（MB）
     */
    private int getOptimalHashSize() {
        try {
            // 获取设备总内存
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory(); // 应用可使用的最大内存
            
            // 转换为MB
            int maxMemoryMB = (int) (maxMemory / (1024 * 1024));
            LogUtils.i("PikafishAI", "设备最大可用内存: " + maxMemoryMB + " MB");
            
            // 根据内存大小设置哈希表大小
            if (maxMemoryMB >= 2048) {
                return 512; // 2GB以上内存，使用512MB哈希表
            } else if (maxMemoryMB >= 1024) {
                return 256; // 1GB-2GB内存，使用256MB哈希表
            } else if (maxMemoryMB >= 512) {
                return 128; // 512MB-1GB内存，使用128MB哈希表
            } else {
                return 64; // 小于512MB内存，使用64MB哈希表
            }
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "计算哈希表大小失败: " + e.getMessage());
            return 128; // 出错时默认使用128MB
        }
    }
    
    private void sendCommand(String command) {
        try {
            writer.write(command + "\n");
            writer.flush();
            LogUtils.d("PikafishAI", "发送命令: " + command);
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "发送命令失败: " + e.getMessage());
        }
    }
    
    // 用于存储走法和评分的类
    public static class MoveWithScore {
        public Move move;
        public int score;

        public MoveWithScore(Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }

    public Move getBestMove(ChessInfo chessInfo) {
        return getBestMoveWithScore(chessInfo).move;
    }

    // 用于存储当前搜索深度
    private int currentDepth = 0;

    // 获取当前搜索深度
    public int getCurrentDepth() {
        return currentDepth;
    }

    public MoveWithScore getBestMoveWithScore(ChessInfo chessInfo) {
        // 检查是否在模拟器中运行
        if (isRunningInEmulator()) {
            Log.e("PikafishAI", "在模拟器中运行，尝试获取AI走法");
            LogUtils.i("PikafishAI", "在模拟器中运行，尝试获取AI走法");
        }
        
        try {
            if (!initialized) {
                Log.e("PikafishAI", "AI未初始化，尝试重新初始化");
                // 尝试重新初始化
                initialize();
                if (!initialized) {
                    Log.e("PikafishAI", "AI初始化失败，使用默认走法");
                    return new MoveWithScore(getDefaultMove(chessInfo), 0);
                }
            }
            
            // 先确保停止之前的搜索
            shouldStop = true;
            if (isSearching) {
                sendCommand("stop");
                // 短暂等待让stop命令生效
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // 重置标志位开始新搜索
            shouldStop = false;
            isSearching = true;
            
            // 重置当前深度
            currentDepth = 0;
            
            // 生成FEN字符串
            String fen = boardToFEN(chessInfo);
            LogUtils.i("PikafishAI", "生成的FEN: " + fen);
            
            // 发送位置信息
            sendCommand("position fen " + fen);
            
            // 发送思考命令，严格使用设置的深度和时间
            int depth = DEFAULT_DEPTH;
            int time = DEFAULT_TIME_MS;
            if (chessInfo != null && chessInfo.setting != null) {
                depth = chessInfo.setting.depth;
                // 直接使用用户设置的思考时间（现在mLevel保存的就是思考时间）
                int thinkingTime = chessInfo.setting.mLevel;
                time = thinkingTime * 1000; // 转换为毫秒
            }
            
            // 确保时间和深度设置合理
            time = Math.max(MIN_TIME_MS, time);
            depth = Math.max(MIN_DEPTH, depth);
            
            LogUtils.i("PikafishAI", "当前 AI 查找深度: " + depth + ", 时间限制: " + time + "ms");
            Log.e("PikafishAI", "当前 AI 查找深度: " + depth + ", 时间限制: " + time + "ms");
            
            // 检查是否处于强制变着模式
            boolean wasForceVariation = false;
            if (chessInfo != null && chessInfo.forceVariation) {
                wasForceVariation = true;
                // 强制变着模式：降低深度，增加随机性
                int randomness = chessInfo.variationRandomness;
                if (randomness <= 0) randomness = 3;
                
                // 根据随机性等级调整深度和时间
                depth = Math.max(3, depth - randomness);
                time = Math.max(500, time / randomness);
                
                LogUtils.i("PikafishAI", "强制变着模式：深度=" + depth + ", 时间=" + time + "ms, 随机性=" + randomness);
                
                // 设置 Contempt 值为负数，鼓励引擎接受和棋，从而寻找不同的走法
                sendCommand("setoption name Contempt value -" + (randomness * 10));
                
                // 强制变着模式：确保 MultiPV 至少为 3，让引擎考虑更多可能的走法
                sendCommand("setoption name MultiPV value 3");
                LogUtils.i("PikafishAI", "强制变着模式：设置MultiPV=3");
            } else {
                // 正常模式：使用默认参数
                int multiPV = 2; // 默认值
                int contempt = 20; // 默认值
                LogUtils.i("PikafishAI", "正常模式：使用默认参数 - MultiPV=" + multiPV + ", Contempt=" + contempt);
                // 确保 MultiPV 至少为 2
                multiPV = Math.max(2, multiPV);
                // 设置 MultiPV
                sendCommand("setoption name MultiPV value " + multiPV);
                LogUtils.i("PikafishAI", "正常模式：设置MultiPV=" + multiPV);
                // 设置 Contempt 值
                sendCommand("setoption name Contempt value " + contempt);
                LogUtils.i("PikafishAI", "正常模式：设置Contempt=" + contempt);
            }
            
            // 同时使用深度限制和时间限制，先达到哪个条件就停止
            sendCommand("go depth " + depth + " movetime " + time);
            
            // 读取最佳走法和评分
            final String[] bestMoveHolder = new String[1]; // 使用数组作为可变包装
            int score = 0;
            int nodes = 0;
            int nps = 0;
            long searchTime = 0;
            long startTime = System.currentTimeMillis();
            
            // 计算最大搜索时间：设置的时间 + 缓冲时间
            long maxSearchTime = time + MAX_SEARCH_TIME_BUFFER_MS;
            LogUtils.i("PikafishAI", "最大搜索时间: " + maxSearchTime + "ms (设置时间: " + time + "ms + 缓冲: " + MAX_SEARCH_TIME_BUFFER_MS + "ms)");
            
            // 超时检查线程
            final Thread currentThread = Thread.currentThread();
            Thread timeoutThread = new Thread(() -> {
                try {
                    Thread.sleep(maxSearchTime);
                    // 超时后检查是否已经收到bestmove
                    if (bestMoveHolder[0] == null) {
                        LogUtils.w("PikafishAI", "搜索超时，强制停止 (已耗时: " + maxSearchTime + "ms)");
                        sendCommand("stop");
                        // 等待1秒后如果仍然没有响应，中断读取
                        Thread.sleep(1000);
                        if (bestMoveHolder[0] == null) {
                            LogUtils.e("PikafishAI", "超时后仍无响应，强制中断读取");
                            // 中断主线程的读取循环
                            currentThread.interrupt();
                        }
                    }
                } catch (InterruptedException e) {
                    // 线程被中断，正常退出
                }
            });
            
            try {
                // 启动超时检查线程
                timeoutThread.start();
                
                // 使用非阻塞方式读取输入，设置最大循环次数
                int maxLoopCount = (int) (maxSearchTime / 5) + 500; // 减少休眠时间，增加循环次数
                int loopCount = 0;
                
                while (!Thread.currentThread().isInterrupted() && loopCount < maxLoopCount) {
                    loopCount++;
                    
                    // 检查是否超时（使用设置的时间 + 缓冲时间）
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    if (elapsedTime > maxSearchTime) {
                        LogUtils.w("PikafishAI", "搜索超时，强制停止 (已耗时: " + elapsedTime + "ms, 限制: " + maxSearchTime + "ms)");
                        sendCommand("stop");
                        // 如果已经超时且还没有bestmove，使用当前最佳走法
                        if (bestMoveHolder[0] == null) {
                            // 尝试从info行中提取ponder move作为备选
                            LogUtils.w("PikafishAI", "超时未收到bestmove，使用默认走法");
                        }
                        break;
                    }
                    
                    try {
                        // 使用 BufferedReader 的 ready() 方法检查是否有数据可读
                        if (reader.ready()) {
                            String line = reader.readLine();
                            if (line == null) {
                                LogUtils.w("PikafishAI", "读取到null，结束读取");
                                break;
                            }
                            
                            // 减少详细日志输出，提高性能
                            if (line.startsWith("info")) {
                                // 只在深度变化时输出日志
                                String[] parts = line.split(" ");
                                for (int i = 0; i < parts.length; i++) {
                                    if (parts[i].equals("depth") && i + 1 < parts.length) {
                                        try {
                                            int newDepth = Integer.parseInt(parts[i + 1]);
                                            if (newDepth > currentDepth) {
                                                currentDepth = newDepth;
                                                LogUtils.i("PikafishAI", "当前搜索深度: " + currentDepth);
                                            }
                                        } catch (NumberFormatException e) {
                                            // 忽略解析错误
                                        }
                                    } else if (parts[i].equals("score") && i + 2 < parts.length) {
                                        if (parts[i + 1].equals("cp")) {
                                            try {
                                                score = Integer.parseInt(parts[i + 2]);
                                            } catch (NumberFormatException e) {
                                                // 忽略解析错误
                                            }
                                        }
                                    } else if (parts[i].equals("nodes") && i + 1 < parts.length) {
                                        try {
                                            nodes = Integer.parseInt(parts[i + 1]);
                                        } catch (NumberFormatException e) {
                                            // 忽略解析错误
                                        }
                                    } else if (parts[i].equals("nps") && i + 1 < parts.length) {
                                        try {
                                            nps = Integer.parseInt(parts[i + 1]);
                                        } catch (NumberFormatException e) {
                                            // 忽略解析错误
                                        }
                                    } else if (parts[i].equals("time") && i + 1 < parts.length) {
                                        try {
                                            searchTime = Integer.parseInt(parts[i + 1]);
                                        } catch (NumberFormatException e) {
                                            // 忽略解析错误
                                        }
                                    } else if (parts[i].equals("pv") && i + 1 < parts.length && bestMoveHolder[0] == null) {
                                        // 提取pv中的第一个走法作为备选
                                        bestMoveHolder[0] = parts[i + 1];
                                    }
                                }
                            } else if (line.startsWith("bestmove")) {
                                String[] parts = line.split(" ");
                                if (parts.length > 1) {
                                    bestMoveHolder[0] = parts[1];
                                }
                                break;
                            }
                            
                            // 如果需要停止，发送stop命令并继续等待bestmove
                            if (shouldStop && bestMoveHolder[0] == null) {
                                LogUtils.i("PikafishAI", "收到停止信号，发送stop命令");
                                sendCommand("stop");
                            }
                        } else {
                            // 短暂休眠，避免CPU占用过高，根据循环次数调整休眠时间
                            int sleepTime = loopCount < 100 ? 5 : 10; // 前100次循环使用更短的休眠时间
                            Thread.sleep(sleepTime);
                        }
                    } catch (IOException e) {
                        LogUtils.e("PikafishAI", "读取输入流异常: " + e.getMessage());
                        break;
                    }
                }
                
                if (loopCount >= maxLoopCount) {
                    LogUtils.e("PikafishAI", "读取循环达到最大次数，强制退出");
                    sendCommand("stop");
                }
            } catch (Exception e) {
                LogUtils.e("PikafishAI", "读取响应失败，可能进程已崩溃: " + e.getMessage());
                // 尝试重新初始化
                close();
                initialize();
                if (initialized) {
                    LogUtils.i("PikafishAI", "重新初始化成功，再次尝试获取走法");
                    return getBestMoveWithScore(chessInfo);
                }
            } finally {
                isSearching = false;
                // 保存最终的搜索深度
                final int finalDepth = currentDepth;
                // 更新RoundView中的搜索深度
                try {
                    PvMActivity activity = top.nones.chessgame.PvMActivity.getInstance();
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
                // 不重置搜索深度，保持最后的值
                // currentDepth = 0;
            }
            
            // 计算实际搜索时间
            long actualSearchTime = System.currentTimeMillis() - startTime;
            LogUtils.i("PikafishAI", "搜索完成 - 深度: " + currentDepth + ", 评分: " + score + ", 节点数: " + nodes + ", 节点/秒: " + nps + ", 搜索时间: " + actualSearchTime + "ms, 已停止: " + shouldStop);
            
            if (bestMoveHolder[0] != null) {
                Move move = uciToMove(bestMoveHolder[0]);
                LogUtils.i("PikafishAI", "最佳走法: " + move + ", 评分: " + score);
                
                // 强制变着模式在AI走棋后仍然保持，直到局面改变
                // 不在这里重置强制变着模式标志位，让它在走棋后自然失效
                
                return new MoveWithScore(move, score);
            }
            
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "获取最佳走法失败: " + e.getMessage());
            e.printStackTrace();
            // 尝试重新初始化
            try {
                close();
                initialize();
                if (initialized) {
                    LogUtils.i("PikafishAI", "重新初始化成功，再次尝试获取走法");
                    return getBestMoveWithScore(chessInfo);
                }
            } catch (Exception ex) {
                LogUtils.e("PikafishAI", "重新初始化失败: " + ex.getMessage());
            }
        }
        
        // 如果获取AI走法失败，返回默认走法和0评分
        Log.e("PikafishAI", "获取AI走法失败，返回默认走法");
        return new MoveWithScore(getDefaultMove(chessInfo), 0);
    }
    
    private Move getDefaultMove(ChessInfo chessInfo) {
        // 在模拟器中返回一个简单的默认走法
        // 这里实现一个简单的走法逻辑，选择第一个可移动棋子的第一个合法移动
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                int piece = chessInfo.piece[y][x];
                // 检查是否是当前回合的棋子
                if ((chessInfo.IsRedGo && piece >= 8) || (!chessInfo.IsRedGo && piece <= 7)) {
                    // 获取该棋子的合法移动
                    List<Pos> possibleMoves = Rule.PossibleMoves(chessInfo.piece, x, y, piece);
                    if (!possibleMoves.isEmpty()) {
                        // 返回第一个合法移动
                        return new Move(new Pos(x, y), possibleMoves.get(0));
                    }
                }
            }
        }
        return null;
    }
    
    private String boardToFEN(ChessInfo chessInfo) {
        StringBuilder fen = new StringBuilder();
        
        // 生成棋盘部分，从红方底线开始（y=0）到黑方底线结束（y=9）
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
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (y < 9) {
                fen.append('/');
            }
        }
        
        // 生成回合部分，由于棋子颜色映射反转，回合信息也需要反转
        fen.append(' ');
        fen.append(chessInfo.IsRedGo ? 'b' : 'w');
        
        // 生成 castle 部分（中国象棋不需要）
        fen.append(" - - 0 1");
        
        return fen.toString();
    }
    
    private char pieceToFEN(int piece) {
        switch (piece) {
            case 1: return 'K'; // 黑将
            case 2: return 'A'; // 黑士
            case 3: return 'B'; // 黑象
            case 4: return 'N'; // 黑马
            case 5: return 'R'; // 黑车
            case 6: return 'C'; // 黑炮
            case 7: return 'P'; // 黑卒
            case 8: return 'k'; // 红帅
            case 9: return 'a'; // 红士
            case 10: return 'b'; // 红相
            case 11: return 'n'; // 红马
            case 12: return 'r'; // 红车
            case 13: return 'c'; // 红炮
            case 14: return 'p'; // 红兵
            default: return ' ';
        }
    }
    
    private Move uciToMove(String uci) {
        if (uci == null) {
            LogUtils.e("PikafishAI", "UCI坐标为null");
            return null;
        }
        
        if (uci.length() != 4) {
            LogUtils.e("PikafishAI", "无效的UCI坐标长度: " + uci.length() + "，坐标: " + uci);
            return null;
        }
        
        try {
            char fromFile = uci.charAt(0);
            char fromRank = uci.charAt(1);
            char toFile = uci.charAt(2);
            char toRank = uci.charAt(3);
            
            int fromX = fromFile - 'a';
            int fromY = 9 - (fromRank - '0');
            int toX = toFile - 'a';
            int toY = 9 - (toRank - '0');
            
            // 验证坐标范围
            if (fromX < 0 || fromX >= 9 || fromY < 0 || fromY >= 10 ||
                toX < 0 || toX >= 9 || toY < 0 || toY >= 10) {
                LogUtils.e("PikafishAI", "无效的UCI坐标: " + uci + "，转换后坐标: (" + fromX + "," + fromY + ") -> (" + toX + "," + toY + ")");
                return null;
            }
            
            return new Move(new Pos(fromX, fromY), new Pos(toX, toY));
            
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "解析UCI走法失败: " + e.getMessage() + "，坐标: " + uci);
            return null;
        }
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    // 中断AI搜索
    public void interrupt() {
        shouldStop = true;
        if (isSearching) {
            sendCommand("stop");
        }
    }
    
    // 更新设置
    public void updateSettings(int skillLevel, int multiPV) {
        if (initialized) {
            // 设置多主变（MultiPV）
            // 确保 MultiPV 至少为 2，让引擎考虑多个可能的走法，避免重复
            multiPV = Math.max(2, multiPV);
            sendCommand("setoption name MultiPV value " + multiPV);
            LogUtils.i("PikafishAI", "更新MultiPV: " + multiPV);
            
            // 设置技能级别
            sendCommand("setoption name Skill Level value " + skillLevel);
            LogUtils.i("PikafishAI", "更新技能级别: " + skillLevel);
            
            // 尝试获取Contempt设置
            int contempt = 20; // 默认值
            try {
                Class<?> pvmaClass = Class.forName("top.nones.chessgame.PvMActivity");
                Object settingObj = pvmaClass.getField("setting").get(null);
                if (settingObj != null) {
                    try {
                        contempt = (int) settingObj.getClass().getField("contempt").get(settingObj);
                    } catch (NoSuchFieldException e) {
                        LogUtils.i("PikafishAI", "没有找到Contempt设置，使用默认值: " + contempt);
                    }
                }
            } catch (Exception e) {
                LogUtils.e("PikafishAI", "获取Contempt设置失败: " + e.getMessage());
            }
            
            // 设置 Contempt 值
            sendCommand("setoption name Contempt value " + contempt);
            LogUtils.i("PikafishAI", "设置Contempt值: " + contempt);
            
            // 等待参数设置完成
            sendCommand("isready");
        }
    }
    
    // 更新设置（包含所有参数）
    public void updateSettings(int skillLevel, int multiPV, int depth, int thinkingTime) {
        if (initialized) {
            // 设置多主变（MultiPV）
            // 确保 MultiPV 至少为 2，让引擎考虑多个可能的走法，避免重复
            multiPV = Math.max(2, multiPV);
            sendCommand("setoption name MultiPV value " + multiPV);
            LogUtils.i("PikafishAI", "更新MultiPV: " + multiPV);
            
            // 设置技能级别
            sendCommand("setoption name Skill Level value " + skillLevel);
            LogUtils.i("PikafishAI", "更新技能级别: " + skillLevel);
            
            // 尝试获取Contempt设置
            int contempt = 20; // 默认值
            try {
                Class<?> pvmaClass = Class.forName("top.nones.chessgame.PvMActivity");
                Object settingObj = pvmaClass.getField("setting").get(null);
                if (settingObj != null) {
                    try {
                        contempt = (int) settingObj.getClass().getField("contempt").get(settingObj);
                    } catch (NoSuchFieldException e) {
                        LogUtils.i("PikafishAI", "没有找到Contempt设置，使用默认值: " + contempt);
                    }
                }
            } catch (Exception e) {
                LogUtils.e("PikafishAI", "获取Contempt设置失败: " + e.getMessage());
            }
            
            // 设置 Contempt 值
            sendCommand("setoption name Contempt value " + contempt);
            LogUtils.i("PikafishAI", "设置Contempt值: " + contempt);
            
            // 等待参数设置完成
            sendCommand("isready");
        }
    }
    
    public void close() {
        try {
            if (writer != null) {
                try {
                    sendCommand("quit");
                    writer.close();
                } catch (Exception e) {
                    LogUtils.e("PikafishAI", "关闭writer失败: " + e.getMessage());
                } finally {
                    writer = null;
                }
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    LogUtils.e("PikafishAI", "关闭reader失败: " + e.getMessage());
                } finally {
                    reader = null;
                }
            }
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception e) {
                    LogUtils.e("PikafishAI", "销毁进程失败: " + e.getMessage());
                } finally {
                    process = null;
                }
            }
            // 重置初始化状态
            initialized = false;
            LogUtils.i("PikafishAI", "资源已成功释放");
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "关闭失败: " + e.getMessage());
        }
    }
}