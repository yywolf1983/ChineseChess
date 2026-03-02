package Utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogUtils {
    // 日志标签
    public static final String TAG = "ChineseChess";
    
    // 日志文件路径
    private static String logFilePath;
    
    // 日志文件是否启用
    private static boolean logFileEnabled = true;
    
    // 初始化日志工具
    public static void init(Context context) {
        if (context != null) {
            // 初始化日志文件路径
            File logDir = new File(context.getExternalFilesDir(null), "logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            String timeStamp = new SimpleDateFormat("yyyyMMdd").format(new Date());
            logFilePath = logDir.getAbsolutePath() + File.separator + "chess_log_" + timeStamp + ".txt";
            
            // 记录初始化信息
            i("LogUtils", "Log system initialized, log file: " + logFilePath);
        }
    }
    
    // 启用或禁用日志文件
    public static void setLogFileEnabled(boolean enabled) {
        logFileEnabled = enabled;
        i("LogUtils", "Log file enabled: " + enabled);
    }
    
    // 获取日志文件路径
    public static String getLogFilePath() {
        return logFilePath;
    }
    
    // VERBOSE级别的日志
    public static void v(String tag, String message) {
        Log.v(TAG, "[" + tag + "] " + message);
        writeToFile("VERBOSE", tag, message);
    }
    
    // DEBUG级别的日志
    public static void d(String tag, String message) {
        Log.d(TAG, "[" + tag + "] " + message);
        writeToFile("DEBUG", tag, message);
    }
    
    // INFO级别的日志
    public static void i(String tag, String message) {
        Log.i(TAG, "[" + tag + "] " + message);
        writeToFile("INFO", tag, message);
    }
    
    // WARN级别的日志
    public static void w(String tag, String message) {
        Log.w(TAG, "[" + tag + "] " + message);
        writeToFile("WARN", tag, message);
    }
    
    // ERROR级别的日志
    public static void e(String tag, String message) {
        Log.e(TAG, "[" + tag + "] " + message);
        writeToFile("ERROR", tag, message);
    }
    
    // 写入日志到文件
    private static void writeToFile(String level, String tag, String message) {
        if (!logFileEnabled || logFilePath == null) {
            return;
        }
        
        try {
            FileWriter fileWriter = new FileWriter(logFilePath, true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            
            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
            String logLine = timeStamp + " [" + level + "] [" + tag + "] " + message + "\n";
            
            bufferedWriter.write(logLine);
            bufferedWriter.close();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to log file: " + e.getMessage());
        }
    }
    
    // 清除日志文件
    public static void clearLogFile() {
        if (logFilePath != null) {
            File logFile = new File(logFilePath);
            if (logFile.exists()) {
                logFile.delete();
                i("LogUtils", "Log file cleared");
            }
        }
    }
    
    // 获取日志文件大小
    public static long getLogFileSize() {
        if (logFilePath != null) {
            File logFile = new File(logFilePath);
            if (logFile.exists()) {
                long size = logFile.length();
                i("LogUtils", "Log file size: " + size + " bytes");
                return size;
            }
        }
        return 0;
    }
    
    // 记录AI移动详细信息
    public static void logAIMove(String tag, String message, String fen, String move) {
        String fullMessage = message + " | FEN: " + fen + " | Move: " + move;
        d(tag, fullMessage);
    }
    
    // 记录棋盘状态
    public static void logBoardState(String tag, int[][] board) {
        StringBuilder sb = new StringBuilder();
        sb.append("Board state:\n");
        for (int y = 9; y >= 0; y--) {
            for (int x = 0; x < 9; x++) {
                sb.append(String.format("%3d", board[y][x]));
            }
            sb.append("\n");
        }
        d(tag, sb.toString());
    }
}
