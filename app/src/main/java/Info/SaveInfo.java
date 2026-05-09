package Info;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Date;
import Utils.LogUtils;

public class SaveInfo {
    private static Context context;

    public static void init(Context c) {
        context = c;
    }

    public static boolean fileIsExists(String fileName) {
        if (context == null) {
            Log.e("SaveInfo", "Context is not initialized");
            return false;
        }
        try {
            File file = new File(context.getFilesDir(), fileName);
            return file.exists();
        } catch (Exception e) {
            Log.e("SaveInfo", "Error checking file existence: " + e.getMessage());
            return false;
        }
    }

    public static void SerializeChessInfo(ChessInfo info, String fileName) throws Exception {
        if (context == null) {
            throw new Exception("Context is not initialized");
        }
        File file = new File(context.getFilesDir(), fileName);
        FileOutputStream fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(info);
        oos.close();
        fos.close();
    }

    public static ChessInfo DeserializeChessInfo(String fileName) throws Exception {
        if (context == null) {
            throw new Exception("Context is not initialized");
        }
        File file = new File(context.getFilesDir(), fileName);
        FileInputStream fis = new FileInputStream(file);
        ObjectInputStream ois = new ObjectInputStream(fis);
        ChessInfo info = (ChessInfo) ois.readObject();
        ois.close();
        fis.close();
        return info;
    }

    public static void SerializeInfoSet(InfoSet infoSet, String fileName) throws Exception {
        if (context == null) {
            throw new Exception("Context is not initialized");
        }
        File file = new File(context.getFilesDir(), fileName);
        FileOutputStream fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(infoSet);
        oos.close();
        fos.close();
    }

    public static InfoSet DeserializeInfoSet(String fileName) throws Exception {
        if (context == null) {
            throw new Exception("Context is not initialized");
        }
        File file = new File(context.getFilesDir(), fileName);
        FileInputStream fis = new FileInputStream(file);
        ObjectInputStream ois = new ObjectInputStream(fis);
        InfoSet infoSet = (InfoSet) ois.readObject();
        ois.close();
        fis.close();
        return infoSet;
    }

    public static void SerializeChessNotation(Context context, ChessNotation notation, String fileName) throws Exception {
        if (context == null) {
            throw new Exception("Context is not initialized");
        }
        // 使用外部存储空间保存棋谱
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir == null) {
            throw new Exception("External storage not available");
        }
        // 确保目录存在
        if (!externalDir.exists()) {
            externalDir.mkdirs();
        }
        // 只使用.pgn扩展名
        String cleanFileName = fileName.replace(".pgn", "");
        File file = new File(externalDir, cleanFileName + ".pgn");
        // 先删除旧的.pgn文件，确保完全覆盖
        if (file.exists()) {
            file.delete();
        }
        java.io.OutputStreamWriter writer = null;
        try {
            // 使用 FileOutputStream 确保完全覆盖旧文件内容
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            writer = new java.io.OutputStreamWriter(fos, "UTF-8");
            
            // 写入PGN标签
            writer.write("[Game \"Chinese Chess\"]\n");
            writer.write("[Event \"\"]\n");
            writer.write("[Round \"\"]\n");
            if (notation.getMatchDate() != null && !notation.getMatchDate().isEmpty()) {
                writer.write("[Date \"" + notation.getMatchDate() + "\"]\n");
            } else {
                writer.write("[Date \"\"]\n");
            }
            if (notation.getLocation() != null && !notation.getLocation().isEmpty()) {
                writer.write("[Site \"" + notation.getLocation() + "\"]\n");
            } else {
                writer.write("[Site \"\"]\n");
            }
            writer.write("[RedTeam \"\"]\n");
            if (notation.getPlayerRed() != null && !notation.getPlayerRed().isEmpty()) {
                writer.write("[Red \"" + notation.getPlayerRed() + "\"]\n");
            } else {
                writer.write("[Red \"Red Player\"]\n");
            }
            writer.write("[BlackTeam \"\"]\n");
            if (notation.getPlayerBlack() != null && !notation.getPlayerBlack().isEmpty()) {
                writer.write("[Black \"" + notation.getPlayerBlack() + "\"]\n");
            } else {
                writer.write("[Black \"Black Player\"]\n");
            }
            if (notation.getResult() != null && !notation.getResult().isEmpty()) {
                // 转换结果为PGN格式
                String pgnResult = "*";
                if (notation.getResult().contains("红胜")) {
                    pgnResult = "1-0";
                } else if (notation.getResult().contains("黑胜")) {
                    pgnResult = "0-1";
                } else if (notation.getResult().contains("和")) {
                    pgnResult = "1/2-1/2";
                }
                writer.write("[Result \"" + pgnResult + "\"]\n");
            } else {
                writer.write("[Result \"*\"]\n");
            }
            writer.write("[ECCO \"\"]\n");
            writer.write("[Opening \"\"]\n");
            writer.write("[Variation \"\"]\n");
            if (notation.getFen() != null && !notation.getFen().isEmpty()) {
                writer.write("[FEN \"" + notation.getFen() + "\"]\n");
            }
            writer.write("\n");
            writer.write("{#1,1#}\n");
            writer.write("\n");

            
            // 写入走法记录
            List<ChessNotation.MoveRecord> moveRecords = notation.getMoveRecords();
            if (moveRecords != null) {
                for (int i = 0; i < moveRecords.size(); i++) {
                    ChessNotation.MoveRecord record = moveRecords.get(i);
                    if (record != null) {
                        String redMove = record.redMove != null ? record.redMove : "";
                        String blackMove = record.blackMove != null ? record.blackMove : "";
                        writer.write("   " + (i + 1) + ". " + redMove + " {#0,0#}\n");
                        if (!blackMove.isEmpty()) {
                            writer.write("      " + blackMove + " {#50,0#}\n");
                        }
                    }
                }
            }
            
            // 写入结果
            writer.write("   *\n");
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception e) {
                    LogUtils.e("SaveInfo", "操作失败", e);
                }
            }
        }
    }

    public static ChessNotation DeserializeChessNotation(Context context, String fileName) throws Exception {
        if (context == null) {
            throw new Exception("Context is not initialized");
        }
        // 从外部存储空间加载棋谱
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir == null) {
            throw new Exception("External storage not available");
        }
        // 确保目录存在
        if (!externalDir.exists()) {
            externalDir.mkdirs();
        }
        // 只使用.pgn扩展名
        String cleanFileName = fileName;
        if (!fileName.endsWith(".pgn")) {
            cleanFileName = fileName + ".pgn";
        }
        File file = new File(externalDir, cleanFileName);
        if (!file.exists()) {
            throw new Exception("File not found: " + file.getAbsolutePath());
        }
        FileInputStream fis = null;
        InputStreamReader reader = null;
        BufferedReader bufferedReader = null;
        try {
            fis = new FileInputStream(file);
            reader = new InputStreamReader(fis, "UTF-8");
            bufferedReader = new BufferedReader(reader);
            
            ChessNotation notation = new ChessNotation();
            notation.setDate(new Date());
            
            String line;
            boolean isMoveRecords = false;
            String currentRedMove = null;
            
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                
                // 保存原始行用于判断是否是红方走法
                String originalLine = line;
                
                // 移除注释，例如 炮二平四 {#0,0#}
                int commentStart = line.indexOf("{");
                if (commentStart != -1) {
                    line = line.substring(0, commentStart).trim();
                }
                
                // 跳过空行
                if (line.isEmpty()) {
                    // 空行，开始走法记录
                    isMoveRecords = true;
                    continue;
                }
                
                if (line.startsWith("[Game")) {
                    // PGN标签，跳过
                } else if (line.startsWith("[Event")) {
                    // PGN标签，跳过
                } else if (line.startsWith("[Round")) {
                    // PGN标签，跳过
                } else if (line.startsWith("[Date")) {
                    // 解析日期
                    int start = line.indexOf('"') + 1;
                    int end = line.lastIndexOf('"');
                    if (start > 0 && end > start) {
                        notation.setMatchDate(line.substring(start, end));
                    }
                } else if (line.startsWith("[Site")) {
                    // 解析地点
                    int start = line.indexOf('"') + 1;
                    int end = line.lastIndexOf('"');
                    if (start > 0 && end > start) {
                        notation.setLocation(line.substring(start, end));
                    }
                } else if (line.startsWith("[RedTeam")) {
                    // PGN标签，跳过
                } else if (line.startsWith("[Red")) {
                    // 解析红方
                    int start = line.indexOf('"') + 1;
                    int end = line.lastIndexOf('"');
                    if (start > 0 && end > start) {
                        notation.setPlayerRed(line.substring(start, end));
                    }
                } else if (line.startsWith("[BlackTeam")) {
                    // PGN标签，跳过
                } else if (line.startsWith("[Black")) {
                    // 解析黑方
                    int start = line.indexOf('"') + 1;
                    int end = line.lastIndexOf('"');
                    if (start > 0 && end > start) {
                        notation.setPlayerBlack(line.substring(start, end));
                    }
                } else if (line.startsWith("[Result")) {
                    // 解析结果
                    int start = line.indexOf('"') + 1;
                    int end = line.lastIndexOf('"');
                    if (start > 0 && end > start) {
                        String pgnResult = line.substring(start, end);
                        // 转换PGN结果为中文
                        if (pgnResult.equals("1-0")) {
                            notation.setResult("红胜");
                        } else if (pgnResult.equals("0-1")) {
                            notation.setResult("黑胜");
                        } else if (pgnResult.equals("1/2-1/2")) {
                            notation.setResult("和棋");
                        }
                    }
                } else if (line.startsWith("[ECCO")) {
                    // PGN标签，跳过
                } else if (line.startsWith("[Opening")) {
                    // PGN标签，跳过
                } else if (line.startsWith("[Variation")) {
                    // PGN标签，跳过
                } else if (line.startsWith("[FEN")) {
                    // 解析FEN信息
                    int start = line.indexOf('"') + 1;
                    int end = line.lastIndexOf('"');
                    if (start > 0 && end > start) {
                        notation.setFen(line.substring(start, end));
                    }
                } else if (line.equals("*") || line.equals("   *")) {
                    // 结果行，跳过
                    continue;
                } else if (line.equals("0-1") || line.equals("1-0") || line.equals("1/2-1/2")) {
                    // 结果行，跳过
                    continue;
                } else if (line.contains("感谢使用鲨鱼象棋软件")) {
                    // 致谢信息，跳过
                    continue;
                } else {
                    // 开始解析走法记录
                    isMoveRecords = true;
                    
                    // 检查是否是红方走法（以回合数开头），使用原始行进行判断
                    if (originalLine.matches(".*\\d+\\.\\s+.*")) {
                        // 这是红方走法
                        // 移除回合数和点
                        int dotIndex = originalLine.indexOf(".");
                        if (dotIndex != -1) {
                            String movePart = originalLine.substring(dotIndex + 1).trim();
                            // 移除注释
                            int moveCommentStart = movePart.indexOf("{");
                            if (moveCommentStart != -1) {
                                movePart = movePart.substring(0, moveCommentStart).trim();
                            }
                            // 处理红方走法：全角转半角 + 数字转中文
                            movePart = top.nones.chessgame.ChessNotationTranslator.normalizeMoveString(movePart, true);
                            // 提取红方走法
                            currentRedMove = movePart;
                        }
                    } else if (line.trim().length() > 0) {
                        // 这是黑方走法
                        // 处理黑方走法：全角转半角 + 数字转阿拉伯
                        String movePart = top.nones.chessgame.ChessNotationTranslator.normalizeMoveString(line.trim(), false);
                        
                        if (currentRedMove != null) {
                            // 有对应的红方走法，添加完整的走法记录
                            Log.d("SaveInfo", "添加走法记录: 红方=" + currentRedMove + ", 黑方=" + movePart);
                            notation.addMoveRecord(currentRedMove, movePart);
                            currentRedMove = null;
                        } else {
                            // 没有对应的红方走法，单独添加黑方走法
                            Log.d("SaveInfo", "添加黑方走法: " + movePart);
                            notation.addMoveRecord("", movePart);
                        }
                    }
                }
            }
            
            // 处理最后一个红方走法（如果有的话）
            if (currentRedMove != null) {
                notation.addMoveRecord(currentRedMove, "");
            }
            return notation;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (Exception e) {
                    LogUtils.e("SaveInfo", "操作失败", e);
                }
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    LogUtils.e("SaveInfo", "操作失败", e);
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {
                    LogUtils.e("SaveInfo", "操作失败", e);
                }
            }
        }
    }
}