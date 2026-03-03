package Info;

import android.content.Context;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChessNotation implements Serializable {
    private static final long serialVersionUID = 1L;
    private String fileName;
    private Date date;
    private List<MoveRecord> moveRecords;
    private String playerRed;
    private String playerBlack;
    private String matchDate;
    private String location;
    private String result;
    private String fen; // 开局FEN信息
    private String game;
    private String event;
    private String round;
    private String redTeam;
    private String blackTeam;
    private String ecco;
    private String opening;
    private String variation;

    public static class MoveRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        public String redMove;
        public String blackMove;

        public MoveRecord(String redMove, String blackMove) {
            this.redMove = redMove;
            this.blackMove = blackMove;
        }
    }

    public ChessNotation() {
        moveRecords = new ArrayList<>();
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public List<MoveRecord> getMoveRecords() {
        return moveRecords;
    }

    public void addMoveRecord(String redMove, String blackMove) {
        moveRecords.add(new MoveRecord(redMove, blackMove));
    }

    public String getPlayerRed() {
        return playerRed;
    }

    public void setPlayerRed(String playerRed) {
        this.playerRed = playerRed;
    }

    public String getPlayerBlack() {
        return playerBlack;
    }

    public void setPlayerBlack(String playerBlack) {
        this.playerBlack = playerBlack;
    }

    public String getMatchDate() {
        return matchDate;
    }

    public void setMatchDate(String matchDate) {
        this.matchDate = matchDate;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getFen() {
        return fen;
    }

    public void setFen(String fen) {
        this.fen = fen;
    }

    public String getGame() {
        return game;
    }

    public void setGame(String game) {
        this.game = game;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getRound() {
        return round;
    }

    public void setRound(String round) {
        this.round = round;
    }

    public String getRedTeam() {
        return redTeam;
    }

    public void setRedTeam(String redTeam) {
        this.redTeam = redTeam;
    }

    public String getBlackTeam() {
        return blackTeam;
    }

    public void setBlackTeam(String blackTeam) {
        this.blackTeam = blackTeam;
    }

    public String getEcco() {
        return ecco;
    }

    public void setEcco(String ecco) {
        this.ecco = ecco;
    }

    public String getOpening() {
        return opening;
    }

    public void setOpening(String opening) {
        this.opening = opening;
    }

    public String getVariation() {
        return variation;
    }

    public void setVariation(String variation) {
        this.variation = variation;
    }

    public void generateFromInfoSet(InfoSet infoSet) {
        // 简化实现，实际应用中可能需要更复杂的逻辑
        moveRecords.clear();
    }

    public boolean saveToFile(Context context, String fileName) {
        try {
            // 确保文件名不包含后缀
            String cleanFileName = fileName.replace(".pgn", "").replace(".txt", "");
            
            // 使用toSaveContent方法生成PGN内容
            String content = toSaveContent();
            
            // 使用外部存储空间保存棋谱
            java.io.File externalDir = context.getExternalFilesDir(null);
            if (externalDir == null) {
                return false;
            }
            // 确保目录存在
            if (!externalDir.exists()) {
                externalDir.mkdirs();
            }
            // 使用.pgn扩展名
            java.io.File file = new java.io.File(externalDir, cleanFileName + ".pgn");
            // 使用FileOutputStream确保完全覆盖文件内容
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(fos, "UTF-8");
            writer.write(content);
            writer.flush();
            writer.close();
            fos.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<String> getSavedNotations(Context context) {
        List<String> notations = new ArrayList<>();
        if (context != null) {
            // 从外部存储空间获取棋谱列表
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir != null) {
                if (!externalDir.exists()) {
                    externalDir.mkdirs();
                }
                // 同时支持.pgn和.txt文件
                File[] pgnFiles = externalDir.listFiles((dir, name) -> name.endsWith(".pgn"));
                if (pgnFiles != null) {
                    for (File file : pgnFiles) {
                        notations.add(file.getName().replace(".pgn", ""));
                    }
                }
                File[] txtFiles = externalDir.listFiles((dir, name) -> name.endsWith(".txt"));
                if (txtFiles != null) {
                    for (File file : txtFiles) {
                        String fileName = file.getName().replace(".txt", "");
                        // 避免重复
                        if (!notations.contains(fileName)) {
                            notations.add(fileName);
                        }
                    }
                }
            }
        }
        return notations;
    }

    public static ChessNotation loadFromFile(Context context, String fileName) {
        try {
            ChessNotation notation = SaveInfo.DeserializeChessNotation(context, fileName);
            return notation;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean deleteNotation(Context context, String fileName) {
        if (context != null) {
            // 从外部存储空间删除棋谱
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir != null) {
                // 尝试删除.pgn文件
                File pgnFile = new File(externalDir, fileName + ".pgn");
                boolean pgnDeleted = pgnFile.delete();
                // 尝试删除.txt文件（兼容旧格式）
                File txtFile = new File(externalDir, fileName + ".txt");
                boolean txtDeleted = txtFile.delete();
                // 只要删除了其中一个就算成功
                return pgnDeleted || txtDeleted;
            }
        }
        return false;
    }
    
    public static ChessNotation parseFromContent(String fileName, String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        
        ChessNotation notation = new ChessNotation();
        notation.setFileName(fileName);
        
        // 解析PGN格式的棋谱
        try {
            // 解析表头信息
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("[Game \"")) {
                    String value = extractPGNValue(line);
                    notation.setGame(value);
                } else if (line.startsWith("[Event \"")) {
                    String value = extractPGNValue(line);
                    notation.setEvent(value);
                } else if (line.startsWith("[Round \"")) {
                    String value = extractPGNValue(line);
                    notation.setRound(value);
                } else if (line.startsWith("[Date \"")) {
                    String value = extractPGNValue(line);
                    notation.setMatchDate(value);
                } else if (line.startsWith("[Site \"")) {
                    String value = extractPGNValue(line);
                    notation.setLocation(value);
                } else if (line.startsWith("[RedTeam \"")) {
                    String value = extractPGNValue(line);
                    notation.setRedTeam(value);
                } else if (line.startsWith("[Red \"")) {
                    String value = extractPGNValue(line);
                    notation.setPlayerRed(value);
                } else if (line.startsWith("[BlackTeam \"")) {
                    String value = extractPGNValue(line);
                    notation.setBlackTeam(value);
                } else if (line.startsWith("[Black \"")) {
                    String value = extractPGNValue(line);
                    notation.setPlayerBlack(value);
                } else if (line.startsWith("[Result \"")) {
                    String value = extractPGNValue(line);
                    notation.setResult(value);
                } else if (line.startsWith("[ECCO \"")) {
                    String value = extractPGNValue(line);
                    notation.setEcco(value);
                } else if (line.startsWith("[Opening \"")) {
                    String value = extractPGNValue(line);
                    notation.setOpening(value);
                } else if (line.startsWith("[Variation \"")) {
                    String value = extractPGNValue(line);
                    notation.setVariation(value);
                } else if (line.startsWith("[FEN \"")) {
                    String value = extractPGNValue(line);
                    notation.setFen(value);
                }
            }
            
            // 解析走法
            int moveIndex = 0;
            for (String line : lines) {
                line = line.trim();
                // 跳过空行、注释行和PGN表头行
                if (line.isEmpty() || line.startsWith("%") || line.startsWith("[")) {
                    continue;
                }
                // 处理行内注释
                line = removeInlineComments(line);
                // 移除行号
                line = line.replaceAll("\\d+\\.", "");
                
                // 分割红黑走法
                String[] moves = line.split("\\s+");
                for (String move : moves) {
                    move = move.trim();
                    if (move.isEmpty() || move.equals("1-0") || move.equals("0-1") || move.equals("1/2-1/2") || move.equals("*")) {
                        continue;
                    }
                    
                    // 移除括号内的注释
                    move = move.replaceAll("\\([^)]*\\)", "");
                    move = move.trim();
                    
                    if (!move.isEmpty()) {
                        if (moveIndex % 2 == 0) {
                            // 红方走法
                            notation.moveRecords.add(new MoveRecord(move, ""));
                        } else {
                            // 黑方走法
                            if (!notation.moveRecords.isEmpty()) {
                                MoveRecord lastRecord = notation.moveRecords.get(notation.moveRecords.size() - 1);
                                lastRecord.blackMove = move;
                            }
                        }
                        moveIndex++;
                    }
                }
            }
            
            return notation;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private static String extractPGNValue(String line) {
        int start = line.indexOf("\"");
        int end = line.lastIndexOf("\"");
        if (start >= 0 && end > start) {
            return line.substring(start + 1, end);
        }
        return "";
    }
    
    private static String removeInlineComments(String line) {
        // 移除 { ... } 形式的注释
        StringBuilder result = new StringBuilder();
        int i = 0;
        int n = line.length();
        while (i < n) {
            if (line.charAt(i) == '{') {
                // 找到匹配的 }
                int j = i + 1;
                int depth = 1;
                while (j < n && depth > 0) {
                    if (line.charAt(j) == '{') {
                        depth++;
                    } else if (line.charAt(j) == '}') {
                        depth--;
                    }
                    j++;
                }
                i = j;
            } else {
                result.append(line.charAt(i));
                i++;
            }
        }
        return result.toString();
    }
    
    private static String escapePGNString(String value) {
        if (value == null) {
            return "";
        }
        // 转义双引号
        return value.replace("\"", "\\\"");
    }
    
    public String toSaveContent() {
        StringBuilder sb = new StringBuilder();
        
        // 写入PGN表头（按照标准顺序）
        sb.append("[Game \"Chinese Chess\"]\n");
        sb.append("[Event \"").append(escapePGNString(event)).append("\"]\n");
        sb.append("[Round \"").append(escapePGNString(round)).append("\"]\n");
        sb.append("[Date \"").append(escapePGNString(matchDate)).append("\"]\n");
        sb.append("[Site \"").append(escapePGNString(location)).append("\"]\n");
        sb.append("[RedTeam \"").append(escapePGNString(redTeam)).append("\"]\n");
        sb.append("[Red \"").append(escapePGNString(playerRed)).append("\"]\n");
        sb.append("[BlackTeam \"").append(escapePGNString(blackTeam)).append("\"]\n");
        sb.append("[Black \"").append(escapePGNString(playerBlack)).append("\"]\n");
        sb.append("[Result \"").append(escapePGNString(result != null ? result : "*")).append("\"]\n");
        sb.append("[ECCO \"").append(escapePGNString(ecco)).append("\"]\n");
        sb.append("[Opening \"").append(escapePGNString(opening)).append("\"]\n");
        sb.append("[Variation \"").append(escapePGNString(variation)).append("\"]\n");
        if (fen != null && !fen.isEmpty()) {
            sb.append("[FEN \"").append(escapePGNString(fen)).append("\"]\n");
        }
        
        sb.append("\n");
        
        // 写入注释行
        sb.append("{#1,1#}\n\n");
        
        // 写入走法（按照标准格式）
        int moveNumber = 1;
        for (MoveRecord record : moveRecords) {
            if (record.redMove != null && !record.redMove.isEmpty()) {
                sb.append("  ").append(moveNumber).append(". ").append(record.redMove).append(" {#0,0#} ");
                if (record.blackMove != null && !record.blackMove.isEmpty()) {
                    sb.append(" " + record.blackMove).append(" {#50,0#}");
                }
                sb.append("\n");
                moveNumber++;
            }
        }
        
        return sb.toString();
    }
}
