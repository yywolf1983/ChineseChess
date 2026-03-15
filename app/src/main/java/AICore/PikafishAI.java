package AICore;

import android.content.Context;
import android.util.Log;

import Info.ChessInfo;
import Info.Pos;
import ChessMove.Move;
import ChessMove.Rule;
import Utils.LogUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PikafishAI {
    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean initialized = false;
    private Context context;
    
    public PikafishAI(Context context) {
        this.context = context;
        initialize();
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
            long timeout = 10000; // 10秒超时
            
            while ((line = reader.readLine()) != null) {
                Log.e("PikafishAI", "初始化响应: " + line);
                LogUtils.d("PikafishAI", "初始化响应: " + line);
                if (line.equals("uciok")) {
                    initialized = true;
                    Log.e("PikafishAI", "UCI初始化成功");
                    LogUtils.i("PikafishAI", "UCI初始化成功");
                    break;
                }
                if (System.currentTimeMillis() - startTime > timeout) {
                    Log.e("PikafishAI", "UCI初始化超时");
                    LogUtils.e("PikafishAI", "UCI初始化超时");
                    break;
                }
            }
            
            if (initialized) {
                // 设置基本参数
                // 1. 设置线程数（根据设备CPU核心数）
                int threadCount = Runtime.getRuntime().availableProcessors();
                sendCommand("setoption name Threads value " + threadCount);
                LogUtils.i("PikafishAI", "设置线程数: " + threadCount);
                
                // 2. 设置哈希表大小（根据设备内存情况）
                int hashSize = getOptimalHashSize();
                sendCommand("setoption name Hash value " + hashSize);
                LogUtils.i("PikafishAI", "设置哈希表大小: " + hashSize + " MB");
                
                // 3. 获取设置值
                int skillLevel = 20; // 默认最高级别
                int multiPV = 1; // 默认单主变
                try {
                    // 尝试获取Setting中的值
                    Class<?> pvmaClass = Class.forName("top.nones.chessgame.PvMActivity");
                    Object settingObj = pvmaClass.getField("setting").get(null);
                    if (settingObj != null) {
                        skillLevel = (int) settingObj.getClass().getField("skillLevel").get(settingObj);
                        multiPV = (int) settingObj.getClass().getField("multiPV").get(settingObj);
                    }
                } catch (Exception e) {
                    LogUtils.e("PikafishAI", "获取设置值失败: " + e.getMessage());
                }
                
                // 4. 设置多主变（MultiPV）
                sendCommand("setoption name MultiPV value " + multiPV);
                LogUtils.i("PikafishAI", "设置MultiPV: " + multiPV);
                
                // 5. 设置技能级别
                sendCommand("setoption name Skill Level value " + skillLevel);
                LogUtils.i("PikafishAI", "设置技能级别: " + skillLevel);
                
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
                    if (System.currentTimeMillis() - startTime > timeout) {
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
    
    /**
     * 评估局面复杂度
     * @param chessInfo 棋盘信息
     * @return 复杂度评分（0-100）
     */
    private int evaluatePositionComplexity(ChessInfo chessInfo) {
        if (chessInfo == null || chessInfo.piece == null) {
            return 50; // 默认中等复杂度
        }
        
        int complexity = 50; // 基础复杂度
        
        // 1. 计算棋子数量
        int pieceCount = 0;
        int redPieceCount = 0;
        int blackPieceCount = 0;
        int attackPieceCount = 0;
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                int piece = chessInfo.piece[i][j];
                if (piece != 0) {
                    pieceCount++;
                    if (piece >= 8) {
                        redPieceCount++;
                    } else {
                        blackPieceCount++;
                    }
                    // 攻击型棋子：车、马、炮、兵/卒
                    if (piece == 5 || piece == 4 || piece == 6 || piece == 7 || 
                        piece == 12 || piece == 11 || piece == 13 || piece == 14) {
                        attackPieceCount++;
                    }
                }
            }
        }
        
        // 2. 基于棋子数量调整复杂度
        // 棋子数量适中时复杂度较高
        if (pieceCount >= 20 && pieceCount <= 25) {
            complexity += 10;
        } else if (pieceCount < 10) {
            complexity -= 15; // 残局复杂度较低
        }
        
        // 3. 基于攻击型棋子数量调整复杂度
        if (attackPieceCount > 10) {
            complexity += 15; // 攻击型棋子多，复杂度高
        }
        
        // 4. 计算可移动性（合法走法数量）
        int legalMovesCount = 0;
        boolean isRed = chessInfo.IsRedGo;
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                int piece = chessInfo.piece[i][j];
                if (piece != 0) {
                    boolean pieceIsRed = piece >= 8;
                    if (pieceIsRed == isRed) {
                        java.util.List<Info.Pos> moves = ChessMove.Rule.PossibleMoves(chessInfo.piece, j, i, piece);
                        legalMovesCount += moves.size();
                    }
                }
            }
        }
        
        // 5. 基于可移动性调整复杂度
        if (legalMovesCount > 30) {
            complexity += 20; // 可移动性高，复杂度高
        } else if (legalMovesCount < 10) {
            complexity -= 10; // 可移动性低，复杂度低
        }
        
        // 6. 检查是否有将军
        if (ChessMove.Rule.isKingDanger(chessInfo.piece, isRed)) {
            complexity += 15; // 将军局面复杂度高
        }
        
        // 7. 确保复杂度在0-100之间
        complexity = Math.max(0, Math.min(100, complexity));
        
        return complexity;
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
            
            // 重置当前深度
            currentDepth = 0;
            
            // 生成FEN字符串
            String fen = boardToFEN(chessInfo);
            LogUtils.i("PikafishAI", "生成的FEN: " + fen);
            
            // 发送位置信息
            sendCommand("position fen " + fen);
            
            // 发送思考命令，优化时间管理
            int depth = 20; // 默认深度
            int time = 10000; // 默认时间限制（毫秒）
            if (chessInfo != null && chessInfo.setting != null) {
                depth = chessInfo.setting.depth;
                // 根据mLevel计算思考时间：mLevel * 2 + 1 秒
                int thinkingTime = chessInfo.setting.mLevel * 2 + 1;
                time = thinkingTime * 1000; // 转换为毫秒
            }
            
            // 评估局面复杂度，动态调整搜索参数
            int complexity = evaluatePositionComplexity(chessInfo);
            LogUtils.i("PikafishAI", "局面复杂度评估: " + complexity);
            
            // 根据复杂度调整搜索参数
            if (complexity > 70) {
                // 复杂局面，增加搜索深度
                depth = Math.min(depth + 2, 35); // 深度最多增加到35
                LogUtils.i("PikafishAI", "复杂局面，增加深度至: " + depth);
            } else if (complexity < 30) {
                // 简单局面，减少搜索深度
                depth = Math.max(depth - 2, 5); // 深度最少为5
                LogUtils.i("PikafishAI", "简单局面，减少深度至: " + depth);
            }
            
            LogUtils.i("PikafishAI", "当前 AI 查找深度: " + depth + ", 时间限制: " + time + "ms");
            Log.e("PikafishAI", "当前 AI 查找深度: " + depth + ", 时间限制: " + time + "ms");
            
            // 使用时间限制搜索，不限制深度
            sendCommand("go movetime " + time);
            
            // 读取最佳走法和评分
            String bestMove = null;
            int score = 0;
            int nodes = 0;
            int nps = 0;
            long searchTime = 0;
            String line;
            long startTime = System.currentTimeMillis();
            try {
                while ((line = reader.readLine()) != null) {
                    LogUtils.d("PikafishAI", "响应: " + line);
                    if (line.startsWith("info")) {
                        // 解析评分信息
                        String[] parts = line.split(" ");
                        for (int i = 0; i < parts.length; i++) {
                            if (parts[i].equals("score") && i + 2 < parts.length) {
                                if (parts[i + 1].equals("cp")) {
                                    try {
                                        score = Integer.parseInt(parts[i + 2]);
                                    } catch (NumberFormatException e) {
                                        // 忽略解析错误
                                    }
                                }
                            } else if (parts[i].equals("depth") && i + 1 < parts.length) {
                                try {
                                    currentDepth = Integer.parseInt(parts[i + 1]);
                                    LogUtils.i("PikafishAI", "当前搜索深度: " + currentDepth);
                                } catch (NumberFormatException e) {
                                    // 忽略解析错误
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
                            }
                        }
                    } else if (line.startsWith("bestmove")) {
                        String[] parts = line.split(" ");
                        if (parts.length > 1) {
                            bestMove = parts[1];
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                LogUtils.e("PikafishAI", "读取响应失败，可能进程已崩溃: " + e.getMessage());
                // 尝试重新初始化
                close();
                initialize();
                if (initialized) {
                    LogUtils.i("PikafishAI", "重新初始化成功，再次尝试获取走法");
                    return getBestMoveWithScore(chessInfo);
                }
            }
            
            // 计算实际搜索时间
            long actualSearchTime = System.currentTimeMillis() - startTime;
            LogUtils.i("PikafishAI", "搜索完成 - 深度: " + currentDepth + ", 评分: " + score + ", 节点数: " + nodes + ", 节点/秒: " + nps + ", 搜索时间: " + actualSearchTime + "ms");
            
            if (bestMove != null) {
                Move move = uciToMove(bestMove);
                LogUtils.i("PikafishAI", "最佳走法: " + move + ", 评分: " + score);
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