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
                // 设置思考时间
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
                Log.e("PikafishAI", "AI未初始化，尝试使用默认走法");
                return new MoveWithScore(getDefaultMove(chessInfo), 0);
            }
            
            // 重置当前深度
            currentDepth = 0;
            
            // 生成FEN字符串
            String fen = boardToFEN(chessInfo);
            LogUtils.i("PikafishAI", "生成的FEN: " + fen);
            
            // 发送位置信息
            sendCommand("position fen " + fen);
            
            // 发送思考命令，使用设置的深度
            int depth = 20; // 默认深度
            if (chessInfo != null && chessInfo.setting != null) {
                depth = chessInfo.setting.depth;
            }
            LogUtils.i("PikafishAI", "当前 AI 查找深度: " + depth);
            sendCommand("go depth " + depth);
            
            // 读取最佳走法和评分
            String bestMove = null;
            int score = 0;
            String line;
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
                            break;
                        }
                    }
                    // 解析深度信息
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("depth") && i + 1 < parts.length) {
                            try {
                                currentDepth = Integer.parseInt(parts[i + 1]);
                                LogUtils.i("PikafishAI", "当前搜索深度: " + currentDepth);
                            } catch (NumberFormatException e) {
                                // 忽略解析错误
                            }
                            break;
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
            
            if (bestMove != null) {
                Move move = uciToMove(bestMove);
                LogUtils.i("PikafishAI", "最佳走法: " + move + ", 评分: " + score);
                return new MoveWithScore(move, score);
            }
            
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "获取最佳走法失败: " + e.getMessage());
            e.printStackTrace();
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
                sendCommand("quit");
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
            if (process != null) {
                process.destroy();
            }
        } catch (Exception e) {
            LogUtils.e("PikafishAI", "关闭失败: " + e.getMessage());
        }
    }
}