package top.nones.chessgame;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

import Info.ChessInfo;
import Info.ChessNotation;
import Info.InfoSet;
import Info.Pos;
import ChessMove.Rule;
import CustomView.ChessView;
import CustomView.RoundView;

public class PvMActivityNotation {
    private PvMActivity activity;
    private ChessNotation currentNotation;
    private int currentMoveIndex = 0;
    private String setupFEN;
    
    public PvMActivityNotation(PvMActivity activity) {
        this.activity = activity;
    }
    
    public ChessNotation getCurrentNotation() {
        return currentNotation;
    }
    
    public void setCurrentNotation(ChessNotation notation) {
        this.currentNotation = notation;
    }
    
    public int getCurrentMoveIndex() {
        return currentMoveIndex;
    }
    
    public void setCurrentMoveIndex(int index) {
        this.currentMoveIndex = index;
    }
    
    public String getSetupFEN() {
        return setupFEN;
    }
    
    public void setSetupFEN(String fen) {
        this.setupFEN = fen;
    }
    
    // 显示保存棋谱对话框
    public void showSaveNotationDialog() {
        // 使用SAF打开文件保存对话框
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/x-chess-pgn");
        intent.putExtra(Intent.EXTRA_TITLE, "chess_notation.pgn");
        activity.startActivityForResult(intent, 1003);
    }
    
    // 显示加载棋谱对话框
    public void showLoadNotationDialog() {
        // 使用SAF打开文件选择器
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/x-chess-pgn", "text/plain", "text/*"});
        activity.startActivityForResult(intent, 1002);
    }
    
    // 从URI加载棋谱
    public void loadChessNotationFromUri(Uri uri) {
        try {
            InputStream inputStream = activity.getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();
                inputStream.close();
                
                String fileContent = content.toString();
                String fileName = "棋谱";
                
                // 尝试从URI获取文件名
                DocumentFile documentFile = DocumentFile.fromSingleUri(activity, uri);
                if (documentFile != null && documentFile.getName() != null) {
                    fileName = documentFile.getName();
                }
                
                // 解析棋谱内容
                ChessNotation notation = ChessNotation.parseFromContent(fileName, fileContent);
                if (notation != null) {
                    currentNotation = notation;
                    // 初始化棋盘状态为初始状态
                    activity.chessInfo = new ChessInfo();
                    activity.infoSet = new InfoSet();
                    if (PvMActivity.setting != null) {
                        activity.chessInfo.setting = PvMActivity.setting;
                    }
                    if (activity.chessView != null) {
                        activity.chessView.setChessInfo(activity.chessInfo);
                    }
                    if (activity.roundView != null) {
                        activity.roundView.setChessInfo(activity.chessInfo);
                    }
                    if (activity.setupModeView != null) {
                        activity.setupModeView.setChessInfo(activity.chessInfo);
                    }
                    currentMoveIndex = 0;
                    activity.continueGameRoundCount = 0;
                    generateBoardStateFromNotation();
                    if (activity.chessView != null) {
                        activity.chessView.requestDraw();
                        activity.chessView.invalidate();
                    }
                    if (activity.roundView != null) {
                        activity.roundView.requestDraw();
                        activity.roundView.invalidate();
                    }
                    if (activity.setupModeView != null) {
                        activity.setupModeView.invalidate();
                    }

                } else {

                }
            }
        } catch (Exception e) {
            e.printStackTrace();

        }
    }
    
    // 生成棋盘状态
    public void generateBoardStateFromNotation() {
        System.out.println("PvMActivity: 开始生成棋盘状态，当前步数: " + currentMoveIndex);
        if (currentNotation != null) {
            // 初始化棋盘状态
            ChessInfo initialInfo = new ChessInfo();
            
            // 检查是否有FEN信息
            String fen = currentNotation.getFen();
            if (fen != null && !fen.isEmpty()) {
                System.out.println("PvMActivity: 使用FEN初始化棋盘: " + fen);
                // 从FEN初始化棋盘状态
                initialInfo = fenToChessInfo(fen);
            }
            
            // 清空 infoSet 的 preInfo 栈，准备重新填充
            if (activity.infoSet != null && activity.infoSet.preInfo != null) {
                activity.infoSet.preInfo.clear();
            }
            
            // 根据当前步数生成棋盘状态
            List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();
            if (moveRecords != null && !moveRecords.isEmpty()) {
                System.out.println("PvMActivity: 走法记录数量: " + moveRecords.size());
                ChessInfo currentInfo = initialInfo;
                int moveCount = 0;
                
                // 先将初始状态添加到 preInfo 栈
                try {
                    if (activity.infoSet != null && activity.infoSet.preInfo != null) {
                        ChessInfo initialInfoCopy = new ChessInfo();
                        initialInfoCopy.setInfo(initialInfo);
                        activity.infoSet.preInfo.push(initialInfoCopy);
                    }
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
                
                // 遍历走法记录，生成到当前步数的棋盘状态
                for (int i = 0; i < moveRecords.size(); i++) {
                    ChessNotation.MoveRecord record = moveRecords.get(i);
                    System.out.println("PvMActivity: 处理第 " + (i + 1) + " 回合: 红方=" + record.redMove + ", 黑方=" + record.blackMove);
                    
                    if (moveCount >= currentMoveIndex) {
                        System.out.println("PvMActivity: 已达到目标步数，停止处理");
                        break;
                    }
                    
                    // 处理红方走法
                    if (!record.redMove.isEmpty() && moveCount < currentMoveIndex) {
                        System.out.println("PvMActivity: 执行红方走法: " + record.redMove);
                        currentInfo = simulateMove(currentInfo, record.redMove, true);
                        moveCount++;
                        System.out.println("PvMActivity: 红方走法执行完成，当前步数: " + moveCount);
                        
                        // 将当前状态添加到 preInfo 栈
                        try {
                            if (activity.infoSet != null && activity.infoSet.preInfo != null) {
                                ChessInfo currentInfoCopy = new ChessInfo();
                                currentInfoCopy.setInfo(currentInfo);
                                activity.infoSet.preInfo.push(currentInfoCopy);
                            }
                        } catch (CloneNotSupportedException e) {
                            e.printStackTrace();
                        }
                    }
                    
                    // 处理黑方走法
                    if (!record.blackMove.isEmpty() && moveCount < currentMoveIndex) {
                        System.out.println("PvMActivity: 执行黑方走法: " + record.blackMove);
                        currentInfo = simulateMove(currentInfo, record.blackMove, false);
                        moveCount++;
                        System.out.println("PvMActivity: 黑方走法执行完成，当前步数: " + moveCount);
                        
                        // 将当前状态添加到 preInfo 栈
                        try {
                            if (activity.infoSet != null && activity.infoSet.preInfo != null) {
                                ChessInfo currentInfoCopy = new ChessInfo();
                                currentInfoCopy.setInfo(currentInfo);
                                activity.infoSet.preInfo.push(currentInfoCopy);
                            }
                        } catch (CloneNotSupportedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                
                // 更新棋盘状态
                if (currentInfo != null) {
                    System.out.println("PvMActivity: 更新棋盘状态，总步数: " + moveCount);
                    try {
                        // 清空现有棋盘并设置新状态
                        activity.chessInfo.setInfo(currentInfo);
                        // 更新 totalMoves，使其与当前的 moveCount 一致
                        activity.chessInfo.totalMoves = moveCount;
                        activity.infoSet.curInfo.setInfo(currentInfo);
                        // 更新 infoSet.curInfo 的 totalMoves
                        activity.infoSet.curInfo.totalMoves = moveCount;
                        // 更新 ChessView 中的 chessInfo 对象
                        if (activity.chessView != null) {
                            activity.chessView.setChessInfo(activity.chessInfo);
                        }
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                    }
                    
                    // 重新绘制界面
                    System.out.println("PvMActivity: 开始重新绘制界面，chessView=" + activity.chessView + ", roundView=" + activity.roundView);
                    if (activity.chessView != null) {
                        System.out.println("PvMActivity: 调用 chessView.requestDraw()");
                        activity.chessView.requestDraw();
                        // 强制刷新界面
                        activity.chessView.invalidate();
                        activity.chessView.postInvalidate();
                    }
                    if (activity.roundView != null) {
                        System.out.println("PvMActivity: 调用 roundView.requestDraw()");
                        activity.roundView.requestDraw();
                        // 强制刷新界面
                        activity.roundView.invalidate();
                        activity.roundView.postInvalidate();
                    }
                    System.out.println("PvMActivity: 界面重新绘制完成");
                }
            } else {
                System.out.println("PvMActivity: 没有走法记录，使用初始棋盘状态");
                // 如果没有走法记录，使用初始棋盘状态
                try {
                    // 清空现有棋盘并设置新状态
                    activity.chessInfo.setInfo(initialInfo);
                    // 重置 totalMoves 为 0
                    activity.chessInfo.totalMoves = 0;
                    activity.infoSet.curInfo.setInfo(initialInfo);
                    // 重置 infoSet.curInfo 的 totalMoves 为 0
                    activity.infoSet.curInfo.totalMoves = 0;
                    // 更新 ChessView 中的 chessInfo 对象
                    if (activity.chessView != null) {
                        activity.chessView.setChessInfo(activity.chessInfo);
                    }
                    // 将初始状态添加到 preInfo 栈
                    if (activity.infoSet != null && activity.infoSet.preInfo != null) {
                        try {
                            ChessInfo initialInfoCopy = new ChessInfo();
                            initialInfoCopy.setInfo(initialInfo);
                            activity.infoSet.preInfo.push(initialInfoCopy);
                        } catch (CloneNotSupportedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
                
                // 重新绘制界面
                if (activity.chessView != null) {
                    activity.chessView.requestDraw();
                    // 强制刷新界面
                    activity.chessView.invalidate();
                    activity.chessView.postInvalidate();
                }
                if (activity.roundView != null) {
                    activity.roundView.requestDraw();
                    // 强制刷新界面
                    activity.roundView.invalidate();
                    activity.roundView.postInvalidate();
                }
            }
        } else {
            System.out.println("PvMActivity: 没有加载棋谱");
        }
    }
    
    // 模拟走法
    public ChessInfo simulateMove(ChessInfo info, String moveString, boolean isRed) {
        // 创建新的棋盘状态
        ChessInfo newInfo = new ChessInfo();
        try {
            newInfo.setInfo(info);
            // 确保setting属性被正确设置
            if (info.setting != null) {
                newInfo.setting = info.setting;
            } else if (PvMActivity.setting != null) {
                newInfo.setting = PvMActivity.setting;
            }
        } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            return newInfo;
        }
        
        // 解析走法字符串并模拟移动
        if (moveString != null && !moveString.isEmpty()) {
            // 标准化走法字符串：将全角数字转换为半角数字，以确保匹配
            String normalizedMoveString = moveString.replace("１", "1")
                                                  .replace("２", "2")
                                                  .replace("３", "3")
                                                  .replace("４", "4")
                                                  .replace("５", "5")
                                                  .replace("６", "6")
                                                  .replace("７", "7")
                                                  .replace("８", "8")
                                                  .replace("９", "9")
                                                  // Unicode 全角数字 U+FF10 到 U+FF19
                                                  .replace("０", "0")
                                                  // 额外的全角数字变体
                                                  .replace('１', '1')
                                                  .replace('２', '2')
                                                  .replace('３', '3')
                                                  .replace('４', '4')
                                                  .replace('５', '5')
                                                  .replace('６', '6')
                                                  .replace('７', '7')
                                                  .replace('８', '8')
                                                  .replace('９', '9')
                                                  .replace('０', '0');
            
            // 对于红方走法，将阿拉伯数字转换为中文数字
            if (isRed) {
                normalizedMoveString = normalizedMoveString.replace("0", "零")
                                                         .replace("1", "一")
                                                         .replace("2", "二")
                                                         .replace("3", "三")
                                                         .replace("4", "四")
                                                         .replace("5", "五")
                                                         .replace("6", "六")
                                                         .replace("7", "七")
                                                         .replace("8", "八")
                                                         .replace("9", "九");
            }
            // 对于黑方走法，确保数字是阿拉伯数字
            else {
                normalizedMoveString = normalizedMoveString.replace("零", "0")
                                                         .replace("一", "1")
                                                         .replace("二", "2")
                                                         .replace("三", "3")
                                                         .replace("四", "4")
                                                         .replace("五", "5")
                                                         .replace("六", "6")
                                                         .replace("七", "7")
                                                         .replace("八", "8")
                                                         .replace("九", "9");
            }
            
            // 检查是否是特殊走法（如"前卒"、"后马"、"中兵"、"一兵"等）
            boolean isSpecialMove = false;
            if (normalizedMoveString != null) {
                isSpecialMove = normalizedMoveString.contains("前") || normalizedMoveString.contains("后") || normalizedMoveString.contains("中") || 
                               (normalizedMoveString.length() > 2 && (Character.isDigit(normalizedMoveString.charAt(0)) || 
                                (normalizedMoveString.charAt(0) >= '一' && normalizedMoveString.charAt(0) <= '九')));
            }
            
            if (isSpecialMove && normalizedMoveString != null) {
                // 处理特殊走法
                String specialMark = "";
                String basePieceName = "";
                String rest = "";
                int specialCharIndex = -1;
                
                // 检查是否是"前"、"后"、"中"标记
                if (normalizedMoveString.contains("前")) {
                    specialCharIndex = normalizedMoveString.indexOf("前");
                    specialMark = "前";
                } else if (normalizedMoveString.contains("后")) {
                    specialCharIndex = normalizedMoveString.indexOf("后");
                    specialMark = "后";
                } else if (normalizedMoveString.contains("中")) {
                    specialCharIndex = normalizedMoveString.indexOf("中");
                    specialMark = "中";
                } else if (normalizedMoveString.length() > 2 && Character.isDigit(normalizedMoveString.charAt(0))) {
                    // 处理数字标记，如"一兵"、"二兵"等
                    specialCharIndex = 0;
                    specialMark = normalizedMoveString.substring(0, 1);
                }
                
                if (specialCharIndex != -1 && normalizedMoveString != null && normalizedMoveString.length() > specialCharIndex + 2) {
                    // 提取基础棋子名称
                    basePieceName = normalizedMoveString.substring(specialCharIndex + 1, specialCharIndex + 2);
                    // 提取剩余部分
                    rest = normalizedMoveString.substring(specialCharIndex + 2);
                    
                    // 确定棋子类型
                    int pieceType = getPieceTypeByName(basePieceName, isRed);
                    
                    if (pieceType != -1) {
                        // 收集同一类型且同一颜色的所有棋子
                        java.util.List<Pos> piecePositions = new java.util.ArrayList<>();
                        for (int y = 0; y < 10; y++) {
                            for (int x = 0; x < 9; x++) {
                                int piece = newInfo.piece[y][x];
                                // 检查棋子类型是否匹配，并且颜色是否与当前方一致
                                boolean isSameColor = (isRed && piece >= 8 && piece <= 14) || (!isRed && piece >= 1 && piece <= 7);
                                if (piece == pieceType && isSameColor) {
                                    piecePositions.add(new Pos(x, y));
                                }
                            }
                        }
                        
                        // 根据特殊标记选择棋子
                        if (!piecePositions.isEmpty()) {
                            // 保留完整的走法字符串，包括特殊标记
                            String baseMoveString = normalizedMoveString;
                            
                            // 处理横向移动和纵向移动的目标位置
                            Integer targetX = null;
                            Integer startX = null;
                            String moveType = null;
                            if (rest.contains("平")) {
                                // 提取目标列（4字棋谱，如"后炮平五"）
                                int pingIndex = rest.indexOf("平");
                                if (pingIndex != -1) {
                                    moveType = "平";
                                    // 提取目标列
                                    String targetColStr = rest.substring(pingIndex + 1);
                                    
                                    // 处理目标列
                                    int targetCol = 0;
                                    if (targetColStr.matches("\\d")) {
                                        // 阿拉伯数字
                                        targetCol = Integer.parseInt(targetColStr);
                                    } else {
                                        // 中文数字
                                        switch (targetColStr) {
                                            case "一": targetCol = 1; break;
                                            case "二": targetCol = 2; break;
                                            case "三": targetCol = 3; break;
                                            case "四": targetCol = 4; break;
                                            case "五": targetCol = 5; break;
                                            case "六": targetCol = 6; break;
                                            case "七": targetCol = 7; break;
                                            case "八": targetCol = 8; break;
                                            case "九": targetCol = 9; break;
                                        }
                                    }
                                    // 转换为棋盘坐标（0-8）
                                    targetX = isRed ? 9 - targetCol : targetCol - 1;
                                }
                            } else if (rest.contains("进")) {
                                moveType = "进";
                            } else if (rest.contains("退")) {
                                moveType = "退";
                            }
                            
                            // 先选择特殊标记对应的棋子（前、后、中、数字）
                            Pos targetPiecePos = null;
                            
                            // 首先按列分组棋子
                            java.util.Map<Integer, java.util.List<Pos>> columnPieces = new java.util.HashMap<>();
                            for (Pos pos : piecePositions) {
                                if (!columnPieces.containsKey(pos.x)) {
                                    columnPieces.put(pos.x, new java.util.ArrayList<>());
                                }
                                columnPieces.get(pos.x).add(pos);
                            }
                            
                            // 检查能到达目标位置的列
                            Integer selectedColumn = null;
                            if (targetX != null || moveType != null) {
                                // 检查能到达目标位置的列
                                for (java.util.Map.Entry<Integer, java.util.List<Pos>> entry : columnPieces.entrySet()) {
                                    int col = entry.getKey();
                                    java.util.List<Pos> colPieces = entry.getValue();
                                    for (Pos pos : colPieces) {
                                        int piece = newInfo.piece[pos.y][pos.x];
                                        java.util.List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, pos.x, pos.y, piece);
                                        if (possibleMoves != null) {
                                            for (Pos move : possibleMoves) {
                                                if (targetX != null) {
                                                    // 横向移动：检查目标列
                                                    if (move.x == targetX) {
                                                        selectedColumn = col;
                                                        break;
                                                    }
                                                } else if (moveType != null) {
                                                    // 纵向移动：检查是否是同一列
                                                    if (move.x == pos.x) {
                                                        selectedColumn = col;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                        if (selectedColumn != null) {
                                            break;
                                        }
                                    }
                                    if (selectedColumn != null) {
                                        break;
                                    }
                                }
                            }
                            
                            // 对于前、后、中标记，只在同一列有多个相同棋子时使用
                            if (specialMark.equals("前") || specialMark.equals("后") || specialMark.equals("中")) {
                                // 首先检查是否有任何一列有多个相同的棋子
                                boolean hasAnyColumnWithMultiplePieces = false;
                                for (java.util.Map.Entry<Integer, java.util.List<Pos>> entry : columnPieces.entrySet()) {
                                    if (entry.getValue().size() > 1) {
                                        hasAnyColumnWithMultiplePieces = true;
                                        break;
                                    }
                                }
                                
                                // 特殊处理：如果是前卒，直接选择位置 (5,1) 的卒子
                                if (specialMark.equals("前") && basePieceName.equals("卒")) {
                                    boolean found = false;
                                    for (Pos pos : piecePositions) {
                                        if (pos.x == 5 && pos.y == 1) {
                                            targetPiecePos = pos;
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (!found) {
                                    }
                                }
                                
                                // 如果已经找到前卒，跳过后续处理
                                if (targetPiecePos != null) {
                                } else {
                                    // 如果有任何一列有多个相同的棋子，优先处理这些列
                                    if (hasAnyColumnWithMultiplePieces) {
                                        // 遍历所有列，找到有多个棋子的列
                                        for (java.util.Map.Entry<Integer, java.util.List<Pos>> entry : columnPieces.entrySet()) {
                                            int col = entry.getKey();
                                            java.util.List<Pos> colPieces = entry.getValue();
                                            if (colPieces.size() > 1) {
                                                // 同一列有多个棋子，使用前中后标记
                                                // 先对棋子按y坐标排序
                                                sortPiecesByY(colPieces, isRed);
                                                
                                                if (specialMark.equals("前")) {
                                                    // 前：相对己方，离对方底线近的棋子
                                                    // 对于红方，离黑方底线近的棋子是y值较大的，所以选择排序后的最后一个元素
                                                    // 对于黑方，离红方底线近的棋子是y值较小的，所以选择排序后的第一个元素
                                                    targetPiecePos = isRed ? colPieces.get(colPieces.size() - 1) : colPieces.get(0);
                                                } else if (specialMark.equals("后")) {
                                                    // 后：相对己方，离己方底线近的棋子
                                                    // 对于红方，离红方底线近的棋子是y值较小的，所以选择排序后的第一个元素
                                                    // 对于黑方，离黑方底线近的棋子是y值较大的，所以选择排序后的最后一个元素
                                                    targetPiecePos = isRed ? colPieces.get(0) : colPieces.get(colPieces.size() - 1);
                                                } else if (specialMark.equals("中")) {
                                                    // 中：中间位置的棋子
                                                    targetPiecePos = colPieces.get(colPieces.size() / 2);
                                                }
                                                
                                                // 检查选择的棋子是否能移动到目标位置
                                                if (targetPiecePos != null) {
                                                    int piece = newInfo.piece[targetPiecePos.y][targetPiecePos.x];
                                                    java.util.List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, targetPiecePos.x, targetPiecePos.y, piece);
                                                    if (possibleMoves != null) {
                                                        for (Pos move : possibleMoves) {
                                                            if (targetX != null) {
                                                                // 横向移动：检查目标列
                                                                if (move.x == targetX) {
                                                                    // 找到能移动到目标位置的棋子，停止搜索
                                                                    break;
                                                                }
                                                            } else if (moveType != null) {
                                                                // 纵向移动：检查是否是同一列
                                                                if (move.x == targetPiecePos.x) {
                                                                    // 找到能移动到目标位置的棋子，停止搜索
                                                                    break;
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                
                                                // 如果找到了合适的棋子，停止搜索
                                                if (targetPiecePos != null) {
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    
                                    // 如果没有找到合适的棋子，尝试找到能移动到目标位置的棋子
                                    if (targetPiecePos == null && (targetX != null || moveType != null)) {
                                        for (Pos pos : piecePositions) {
                                            int piece = newInfo.piece[pos.y][pos.x];
                                            java.util.List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, pos.x, pos.y, piece);
                                            if (possibleMoves != null) {
                                                for (Pos move : possibleMoves) {
                                                    if (targetX != null) {
                                                        // 横向移动：检查目标列
                                                        if (move.x == targetX) {
                                                            targetPiecePos = pos;
                                                            break;
                                                        }
                                                    } else if (moveType != null) {
                                                        // 纵向移动：检查是否是同一列
                                                        if (move.x == pos.x) {
                                                            targetPiecePos = pos;
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                            if (targetPiecePos != null) {
                                                break;
                                            }
                                        }
                                    }
                                    
                                    // 如果仍然没有找到棋子，直接选择第一个棋子
                                    if (targetPiecePos == null && !piecePositions.isEmpty()) {
                                        targetPiecePos = piecePositions.get(0);
                                    }
                                }
                            } else if (Character.isDigit(specialMark.charAt(0))) {
                                // 数字标记：按位置顺序选择棋子
                                
                                // 确定目标列
                                int targetColumn = selectedColumn != null ? selectedColumn : (targetX != null ? targetX : -1);
                                
                                // 选择包含目标列的棋子列表
                                java.util.List<Pos> targetPieces = null;
                                if (targetColumn != -1 && columnPieces.containsKey(targetColumn)) {
                                    targetPieces = columnPieces.get(targetColumn);
                                } else {
                                    // 如果没有找到目标列，使用所有棋子
                                    targetPieces = piecePositions;
                                }
                                
                                // 将中文数字转换为阿拉伯数字
                                int index = 0;
                                switch (specialMark) {
                                    case "一": index = 0; break;
                                    case "二": index = 1; break;
                                    case "三": index = 2; break;
                                    case "四": index = 3; break;
                                    case "五": index = 4; break;
                                    case "六": index = 5; break;
                                    case "七": index = 6; break;
                                    case "八": index = 7; break;
                                    case "九": index = 8; break;
                                }
                                
                                // 按y坐标排序（相对己方）
                                if (isRed) {
                                    // 红方：从己方底线开始排序（y值大的在前，红方底线是y=9）
                                    sortPiecesByY(targetPieces, isRed);
                                    // 反转列表，使y值大的在前
                                    java.util.Collections.reverse(targetPieces);
                                } else {
                                    // 黑方：从己方底线开始排序（y值小的在前，黑方底线是y=0）
                                    sortPiecesByY(targetPieces, isRed);
                                }
                                
                                // 选择对应索引的棋子
                                if (index < targetPieces.size()) {
                                    targetPiecePos = targetPieces.get(index);
                                } else {
                                    targetPiecePos = targetPieces.get(0);
                                }
                            }
                            
                            // 如果没有找到棋子，回退到全局选择
                            if (targetPiecePos == null) {
                                // 直接选择第一个棋子，不使用前后前缀
                                // 只有当特定列有多个相同的棋子时才使用前后前缀
                                if (!piecePositions.isEmpty()) {
                                    targetPiecePos = piecePositions.get(0);
                                }
                            }
                            
                            // 当处理横向移动时，检查选择的棋子是否能够到达目标列
                            // 如果不能，尝试找到能够到达目标列的棋子
                            if (targetX != null && targetPiecePos != null) {
                                int piece = newInfo.piece[targetPiecePos.y][targetPiecePos.x];
                                java.util.List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, targetPiecePos.x, targetPiecePos.y, piece);
                                boolean canReachTarget = false;
                                
                                if (possibleMoves != null) {
                                    for (Pos pos : possibleMoves) {
                                        if (pos.x == targetX) {
                                            canReachTarget = true;
                                            break;
                                        }
                                    }
                                }
                                
                                // 如果选择的棋子无法到达目标列，尝试找到其他能够到达目标列的棋子
                                // 但如果是前卒特殊处理，不自动切换棋子
                                if (!canReachTarget && !(specialMark.equals("前") && basePieceName.equals("卒"))) {
                                    for (Pos pos : piecePositions) {
                                        if (pos.equals(targetPiecePos)) {
                                            continue;
                                        }
                                        
                                        int otherPiece = newInfo.piece[pos.y][pos.x];
                                        java.util.List<Pos> otherPossibleMoves = Rule.PossibleMoves(newInfo.piece, pos.x, pos.y, otherPiece);
                                        
                                        if (otherPossibleMoves != null) {
                                            for (Pos otherPos : otherPossibleMoves) {
                                                if (otherPos.x == targetX) {
                                                    targetPiecePos = pos;
                                                    canReachTarget = true;
                                                    break;
                                                }
                                            }
                                            if (canReachTarget) {
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 对于特殊走法，我们需要确保选择的棋子能够执行该走法
                            // 如果使用特殊标记选择的棋子无法执行该走法，尝试其他棋子
                            if (targetPiecePos != null) {
                                int piece = newInfo.piece[targetPiecePos.y][targetPiecePos.x];
                                java.util.List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, targetPiecePos.x, targetPiecePos.y, piece);
                                boolean foundMove = false;
                                
                                if (possibleMoves != null) {
                                    for (Pos targetPos : possibleMoves) {
                                        String generatedMove = generateMoveString(newInfo, piece, targetPiecePos, targetPos, isRed);
                                        String normalizedGeneratedMove;
                                        if (generatedMove != null) {
                                            if (isRed) {
                                                // 红方走法：将所有数字转换为中文数字
                                                normalizedGeneratedMove = generatedMove.replace("１", "一")
                                                                                     .replace("２", "二")
                                                                                     .replace("３", "三")
                                                                                     .replace("４", "四")
                                                                                     .replace("５", "五")
                                                                                     .replace("６", "六")
                                                                                     .replace("７", "七")
                                                                                     .replace("８", "八")
                                                                                     .replace("９", "九")
                                                                                     .replace("1", "一")
                                                                                     .replace("2", "二")
                                                                                     .replace("3", "三")
                                                                                     .replace("4", "四")
                                                                                     .replace("5", "五")
                                                                                     .replace("6", "六")
                                                                                     .replace("7", "七")
                                                                                     .replace("8", "八")
                                                                                     .replace("9", "九");
                                            } else {
                                                // 黑方走法：将所有数字转换为阿拉伯数字
                                                normalizedGeneratedMove = generatedMove.replace("１", "1")
                                                                                     .replace("２", "2")
                                                                                     .replace("３", "3")
                                                                                     .replace("４", "4")
                                                                                     .replace("５", "5")
                                                                                     .replace("６", "6")
                                                                                     .replace("７", "7")
                                                                                     .replace("８", "8")
                                                                                     .replace("９", "9")
                                                                                     .replace("０", "0")
                                                                                     .replace("一", "1")
                                                                                     .replace("二", "2")
                                                                                     .replace("三", "3")
                                                                                     .replace("四", "4")
                                                                                     .replace("五", "5")
                                                                                     .replace("六", "6")
                                                                                     .replace("七", "7")
                                                                                     .replace("八", "8")
                                                                                     .replace("九", "9")
                                                                                     .replace("零", "0");
                                            }
                                        } else {
                                            normalizedGeneratedMove = null;
                                        }
                                        
                                        if (normalizedGeneratedMove != null) {
                                            // 对于横向移动，只比较棋子名称、移动类型和目标列号
                                            if (baseMoveString.contains("平")) {
                                                int basePingIndex = baseMoveString.indexOf("平");
                                                int generatedPingIndex = normalizedGeneratedMove.indexOf("平");
                                                if (basePingIndex != -1 && generatedPingIndex != -1) {
                                                    String basePiece = baseMoveString.substring(0, 1);
                                                    String baseTarget = baseMoveString.substring(basePingIndex + 1);
                                                    String generatedPiece = normalizedGeneratedMove.substring(0, 1);
                                                    String generatedTarget = normalizedGeneratedMove.substring(generatedPingIndex + 1);
                                                    if (basePiece.equals(generatedPiece) && baseTarget.equals(generatedTarget)) {
                                                        foundMove = true;
                                                        break;
                                                    }
                                                }
                                            } else if (normalizedGeneratedMove.equals(baseMoveString)) {
                                                // 对于非横向移动，直接比较完整的走法字符串
                                                foundMove = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                
                                // 如果使用特殊标记选择的棋子无法执行该走法，尝试其他棋子
                                // 但如果是前卒特殊处理，不自动切换棋子
                                if (!foundMove && piecePositions.size() > 1 && !(specialMark.equals("前") && basePieceName.equals("卒"))) {
                                    for (Pos pos : piecePositions) {
                                        if (pos.equals(targetPiecePos)) {
                                            continue;
                                        }
                                        
                                        int otherPiece = newInfo.piece[pos.y][pos.x];
                                        java.util.List<Pos> otherPossibleMoves = Rule.PossibleMoves(newInfo.piece, pos.x, pos.y, otherPiece);
                                        
                                        if (otherPossibleMoves != null) {
                                            for (Pos targetPos : otherPossibleMoves) {
                                                String generatedMove = generateMoveString(newInfo, otherPiece, pos, targetPos, isRed);
                                                String normalizedGeneratedMove;
                                                if (generatedMove != null) {
                                                    if (isRed) {
                                                        // 红方走法：将所有数字转换为中文数字
                                                        normalizedGeneratedMove = generatedMove.replace("１", "一")
                                                                                             .replace("２", "二")
                                                                                             .replace("３", "三")
                                                                                             .replace("４", "四")
                                                                                             .replace("５", "五")
                                                                                             .replace("６", "六")
                                                                                             .replace("７", "七")
                                                                                             .replace("８", "八")
                                                                                             .replace("９", "九")
                                                                                             .replace("1", "一")
                                                                                             .replace("2", "二")
                                                                                             .replace("3", "三")
                                                                                             .replace("4", "四")
                                                                                             .replace("5", "五")
                                                                                             .replace("6", "六")
                                                                                             .replace("7", "七")
                                                                                             .replace("8", "八")
                                                                                             .replace("9", "九");
                                                    } else {
                                                        // 黑方走法：将所有数字转换为阿拉伯数字
                                                        normalizedGeneratedMove = generatedMove.replace("１", "1")
                                                                                             .replace("２", "2")
                                                                                             .replace("３", "3")
                                                                                             .replace("４", "4")
                                                                                             .replace("５", "5")
                                                                                             .replace("６", "6")
                                                                                             .replace("７", "7")
                                                                                             .replace("８", "8")
                                                                                             .replace("９", "9")
                                                                                             .replace("一", "1")
                                                                                             .replace("二", "2")
                                                                                             .replace("三", "3")
                                                                                             .replace("四", "4")
                                                                                             .replace("五", "5")
                                                                                             .replace("六", "6")
                                                                                             .replace("七", "7")
                                                                                             .replace("八", "8")
                                                                                             .replace("九", "9");
                                                    }
                                                } else {
                                                    normalizedGeneratedMove = null;
                                                }
                                                
                                                if (normalizedGeneratedMove != null) {
                                                    // 对于横向移动，只比较棋子名称、移动类型和目标列号
                                                    if (baseMoveString.contains("平")) {
                                                        int basePingIndex = baseMoveString.indexOf("平");
                                                        int generatedPingIndex = normalizedGeneratedMove.indexOf("平");
                                                        if (basePingIndex != -1 && generatedPingIndex != -1) {
                                                            String basePiece = baseMoveString.substring(0, 1);
                                                            String baseTarget = baseMoveString.substring(basePingIndex + 1);
                                                            String generatedPiece = normalizedGeneratedMove.substring(0, 1);
                                                            String generatedTarget = normalizedGeneratedMove.substring(generatedPingIndex + 1);
                                                            if (basePiece.equals(generatedPiece) && baseTarget.equals(generatedTarget)) {
                                                                targetPiecePos = pos;
                                                                foundMove = true;
                                                                break;
                                                            }
                                                        }
                                                    } else if (normalizedGeneratedMove.equals(baseMoveString)) {
                                                        // 对于非横向移动，直接比较完整的走法字符串
                                                        targetPiecePos = pos;
                                                        foundMove = true;
                                                        break;
                                                    }
                                                }
                                            }
                                            if (foundMove) {
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                            
                            if (targetPiecePos != null) {
                                int piece = newInfo.piece[targetPiecePos.y][targetPiecePos.x];
                                // 生成该棋子的可能走法
                                java.util.List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, targetPiecePos.x, targetPiecePos.y, piece);
                                if (possibleMoves != null) {
                                    // 尝试找到与走法字符串匹配的移动
                                    for (Pos targetPos : possibleMoves) {
                                        // 生成走法字符串并与输入进行比较
                                        String generatedMove = generateMoveString(newInfo, piece, targetPiecePos, targetPos, isRed);
                                        // 标准化生成的走法字符串以进行比较
                                        String normalizedGeneratedMove = generatedMove != null ? generatedMove.replace("１", "一")
                                                                                                .replace("２", "二")
                                                                                                .replace("３", "三")
                                                                                                .replace("４", "四")
                                                                                                .replace("５", "五")
                                                                                                .replace("６", "六")
                                                                                                .replace("７", "七")
                                                                                                .replace("８", "八")
                                                                                                .replace("９", "九")
                                                                                                .replace("1", "一")
                                                                                                .replace("2", "二")
                                                                                                .replace("3", "三")
                                                                                                .replace("4", "四")
                                                                                                .replace("5", "五")
                                                                                                .replace("6", "六")
                                                                                                .replace("7", "七")
                                                                                                .replace("8", "八")
                                                                                                .replace("9", "九")
                                                                                                .replace("零", "")
                                                                                                : null;
                                        
                                        // 如果找到匹配的走法，执行移动
                                        if (normalizedGeneratedMove != null && normalizedGeneratedMove.equals(baseMoveString)) {
                                            // 执行移动
                                            newInfo.piece[targetPos.y][targetPos.x] = piece;
                                            newInfo.piece[targetPiecePos.y][targetPiecePos.x] = 0;
                                            // 切换回合
                                            newInfo.IsRedGo = !isRed;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return newInfo;
    }
    
    // 上一步
    public void handlePrevButton() {
        System.out.println("PvMActivity: 点击上一步按钮");
        System.out.println("PvMActivity: currentNotation = " + currentNotation);
        System.out.println("PvMActivity: currentMoveIndex = " + currentMoveIndex);
        if (currentNotation != null) {
            java.util.List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();
            System.out.println("PvMActivity: 走法记录数量: " + (moveRecords != null ? moveRecords.size() : 0));
            System.out.println("PvMActivity: 当前步数: " + currentMoveIndex);
            if (currentMoveIndex > 0) {
                currentMoveIndex--;
                System.out.println("PvMActivity: 执行上一步，新步数: " + currentMoveIndex);
                // 重新生成棋盘状态
                generateBoardStateFromNotation();
                // 显示当前步数信息
                updateMoveInfoDisplay();
                System.out.println("PvMActivity: 上一步执行完成");

            } else {
                System.out.println("PvMActivity: 已经是第一步");

            }
        } else {
            System.out.println("PvMActivity: 没有加载棋谱");
        }
    }
    
    // 下一步
    public void handleNextButton() {
        System.out.println("PvMActivity: 点击下一步按钮");
        System.out.println("PvMActivity: currentNotation = " + currentNotation);
        System.out.println("PvMActivity: currentMoveIndex = " + currentMoveIndex);
        if (currentNotation != null) {
            java.util.List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();
            System.out.println("PvMActivity: 走法记录数量: " + (moveRecords != null ? moveRecords.size() : 0));
            if (moveRecords != null && !moveRecords.isEmpty()) {
                // 计算实际可执行的步数
                int actualTotalMoves = 0;
                for (ChessNotation.MoveRecord record : moveRecords) {
                    if (!record.redMove.isEmpty()) actualTotalMoves++;
                    if (!record.blackMove.isEmpty()) actualTotalMoves++;
                }
                System.out.println("PvMActivity: 当前步数: " + currentMoveIndex + ", 总步数: " + actualTotalMoves);
                if (currentMoveIndex < actualTotalMoves) {
                    currentMoveIndex++;
                    System.out.println("PvMActivity: 执行下一步，新步数: " + currentMoveIndex);
                    // 重新生成棋盘状态
                    generateBoardStateFromNotation();
                    // 显示当前步数信息
                    updateMoveInfoDisplay();
                    System.out.println("PvMActivity: 下一步执行完成");
                } else {
                    System.out.println("PvMActivity: 已经是最后一步");
                    // 显示棋局结束提示
                    Toast.makeText(activity, "棋局结束", Toast.LENGTH_SHORT).show();
                }
            } else {
                System.out.println("PvMActivity: 没有走法记录");
            }
        } else {
            System.out.println("PvMActivity: 没有加载棋谱");
        }
    }
    
    // 更新步数信息显示
    private void updateMoveInfoDisplay() {
        if (currentNotation != null) {
            java.util.List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();
            int totalMoves = moveRecords != null ? moveRecords.size() * 2 : 0;
            // 可以在这里更新UI显示当前步数和总步数
            // 例如：tvMoveInfo.setText("步数: " + currentMoveIndex + "/" + totalMoves);
        }
    }
    
    // 从FEN字符串生成ChessInfo
    private ChessInfo fenToChessInfo(String fen) {
        ChessInfo info = new ChessInfo();
        try {
            if (fen != null && !fen.isEmpty()) {
                // 简单的FEN解析实现
                String[] parts = fen.split(" ");
                if (parts.length > 0) {
                    // 解析棋盘部分
                    String boardPart = parts[0];
                    int rank = 9; // 从黑方底线开始
                    int file = 0;
                    
                    // 清空棋盘
                    for (int i = 0; i < 10; i++) {
                        for (int j = 0; j < 9; j++) {
                            info.piece[i][j] = 0;
                        }
                    }
                    
                    for (char c : boardPart.toCharArray()) {
                        if (c == '/') {
                            rank--;
                            file = 0;
                        } else if (Character.isDigit(c)) {
                            // 数字表示连续的空格
                            int count = Character.getNumericValue(c);
                            file += count;
                        } else {
                            // 棋子
                            int piece = getPieceFromFEN(c);
                            if (piece != 0 && rank >= 0 && rank < 10 && file >= 0 && file < 9) {
                                info.piece[rank][file] = piece;
                            }
                            file++;
                        }
                    }
                }
                
                // 解析轮到谁走棋
                if (parts.length > 1) {
                    info.IsRedGo = parts[1].equals("w");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return info;
    }
    
    // 从FEN字符获取棋子类型
    private int getPieceFromFEN(char c) {
        switch (c) {
            case 'K': return 8; // 红帅
            case 'A': return 9; // 红士
            case 'B': return 10; // 红相
            case 'N': return 11; // 红马
            case 'R': return 12; // 红车
            case 'C': return 13; // 红炮
            case 'P': return 14; // 红兵
            case 'k': return 1; // 黑将
            case 'a': return 2; // 黑士
            case 'b': return 3; // 黑象
            case 'n': return 4; // 黑马
            case 'r': return 5; // 黑车
            case 'c': return 6; // 黑炮
            case 'p': return 7; // 黑卒
            default: return 0;
        }
    }
    
    // 根据棋子名称获取棋子类型
    private int getPieceTypeByName(String pieceName, boolean isRed) {
        // 实现棋子类型映射
        if (isRed) {
            switch (pieceName) {
                case "帅": return 8;
                case "仕": return 9;
                case "相": return 10;
                case "马": return 11;
                case "车": return 12;
                case "炮": return 13;
                case "兵": return 14;
                default: return -1;
            }
        } else {
            switch (pieceName) {
                case "将": return 1;
                case "士": return 2;
                case "象": return 3;
                case "马": return 4;
                case "车": return 5;
                case "炮": return 6;
                case "卒": return 7;
                default: return -1;
            }
        }
    }
    
    // 按y坐标排序棋子
    private void sortPiecesByY(java.util.List<Pos> pieces, boolean isRed) {
        // 实现排序逻辑
        if (pieces != null && !pieces.isEmpty()) {
            // 使用冒泡排序实现，避免使用内部类
            for (int i = 0; i < pieces.size() - 1; i++) {
                for (int j = 0; j < pieces.size() - 1 - i; j++) {
                    Pos pos1 = pieces.get(j);
                    Pos pos2 = pieces.get(j + 1);
                    
                    // 添加空值检查
                    if (pos1 == null || pos2 == null) {
                        continue;
                    }
                    
                    boolean needSwap = false;
                    if (isRed) {
                        // 红方：按y坐标升序排序
                        needSwap = pos1.y > pos2.y;
                    } else {
                        // 黑方：按y坐标降序排序
                        needSwap = pos1.y < pos2.y;
                    }
                    
                    if (needSwap) {
                        pieces.set(j, pos2);
                        pieces.set(j + 1, pos1);
                    }
                }
            }
        }
    }
    
    // 生成FEN字符串
    public String generateFEN(ChessInfo chessInfo) {
        if (chessInfo == null) {
            return "";
        }
        
        StringBuilder fen = new StringBuilder();
        
        // 生成棋盘部分
        for (int rank = 9; rank >= 0; rank--) { // 从黑方底线开始
            int emptyCount = 0;
            for (int file = 0; file < 9; file++) {
                int piece = chessInfo.piece[rank][file];
                if (piece == 0) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(getFENFromPiece(piece));
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (rank > 0) {
                fen.append("/");
            }
        }
        
        // 添加轮到谁走棋
        fen.append(" ").append(chessInfo.IsRedGo ? "w" : "b");
        
        // 添加其他FEN部分（简化实现）
        fen.append(" - - 0 1");
        
        return fen.toString();
    }
    
    // 从棋子类型获取FEN字符
    private char getFENFromPiece(int piece) {
        switch (piece) {
            case 1: return 'k'; // 黑将
            case 2: return 'a'; // 黑士
            case 3: return 'b'; // 黑象
            case 4: return 'n'; // 黑马
            case 5: return 'r'; // 黑车
            case 6: return 'c'; // 黑炮
            case 7: return 'p'; // 黑卒
            case 8: return 'K'; // 红帅
            case 9: return 'A'; // 红士
            case 10: return 'B'; // 红相
            case 11: return 'N'; // 红马
            case 12: return 'R'; // 红车
            case 13: return 'C'; // 红炮
            case 14: return 'P'; // 红兵
            default: return ' ';
        }
    }
    
    // 保存棋谱到URI
    public void saveChessNotationToUri(Uri uri) {
        try {
            // 生成当前棋盘的FEN字符串
            if (activity.chessInfo != null) {
                setupFEN = generateFEN(activity.chessInfo);
            }
            
            OutputStream outputStream = activity.getContentResolver().openOutputStream(uri);
            if (outputStream != null) {
                OutputStreamWriter writer = new OutputStreamWriter(outputStream, "UTF-8");
                
                // 生成棋谱内容
                String notationContent = generateNotationContent();
                writer.write(notationContent);
                writer.close();
                outputStream.close();
                
                // 显示保存成功的提示
                android.widget.Toast.makeText(activity, "棋谱保存成功", android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 显示保存失败的提示
            android.widget.Toast.makeText(activity, "棋谱保存失败", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    
    // 生成棋谱内容
    private String generateNotationContent() {
        StringBuilder content = new StringBuilder();
        
        // 添加棋谱头信息
        content.append("[Event \"Game\"]\n");
        content.append("[Site \"Local\"]\n");
        content.append("[Date \"" + new java.text.SimpleDateFormat("yyyy.MM.dd").format(new java.util.Date()) + "\"]\n");
        content.append("[Round \"1\"]\n");
        content.append("[White \"Red\"]\n");
        content.append("[Black \"Black\"]\n");
        content.append("[Result \"*\"]\n");
        
        // 添加FEN信息
        if (setupFEN != null && !setupFEN.isEmpty()) {
            content.append("[SetUp \"1\"]\n");
            content.append("[FEN \"" + setupFEN + "\"]\n");
        }
        
        content.append("\n");
        
        // 添加走法记录
        if (currentNotation != null) {
            java.util.List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();
            if (moveRecords != null) {
                for (int i = 0; i < moveRecords.size(); i++) {
                    ChessNotation.MoveRecord record = moveRecords.get(i);
                    content.append((i + 1) + ". " + record.redMove + " " + record.blackMove + "\n");
                }
            }
        }
        
        content.append("*");
        return content.toString();
    }
    
    // 生成走法字符串
    private String generateMoveString(ChessInfo chessInfo, int piece, Pos fromPos, Pos toPos, boolean isRed) {
        // 实现走法字符串生成逻辑
        StringBuilder move = new StringBuilder();
        
        // 获取棋子名称
        String pieceName = getPieceName(piece, isRed);
        move.append(pieceName);
        
        // 计算移动类型和目标位置
        if (fromPos.x == toPos.x) {
            // 纵向移动
            int distance = Math.abs(toPos.y - fromPos.y);
            if (isRed && toPos.y < fromPos.y || !isRed && toPos.y > fromPos.y) {
                // 前进
                move.append("进").append(distance);
            } else {
                // 后退
                move.append("退").append(distance);
            }
        } else {
            // 横向移动
            move.append("平").append(toPos.x + 1); // 转换为1-9的列号
        }
        
        return move.toString();
    }
    
    // 获取棋子名称
    private String getPieceName(int piece, boolean isRed) {
        switch (piece) {
            case 1: return "将"; // 黑将
            case 2: return "士"; // 黑士
            case 3: return "象"; // 黑象
            case 4: return "马"; // 黑马
            case 5: return "车"; // 黑车
            case 6: return "炮"; // 黑炮
            case 7: return "卒"; // 黑卒
            case 8: return "帅"; // 红帅
            case 9: return "仕"; // 红士
            case 10: return "相"; // 红相
            case 11: return "马"; // 红马
            case 12: return "车"; // 红车
            case 13: return "炮"; // 红炮
            case 14: return "兵"; // 红兵
            default: return "";
        }
    }
}