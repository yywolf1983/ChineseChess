package top.nones.chessgame;

import Info.ChessInfo;
import Info.Pos;
import ChessMove.Rule;

public class MoveSimulator {
    private PvMActivity activity;
    
    public MoveSimulator(PvMActivity activity) {
        this.activity = activity;
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
            System.out.println("PvMActivity: 开始解析走法: " + moveString + ", isRed=" + isRed);
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
            System.out.println("PvMActivity: 标准化后走法: " + normalizedMoveString);
            
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
            System.out.println("PvMActivity: 最终标准化走法: " + normalizedMoveString);
            
            // 检查是否是特殊走法（如"前卒"、"后马"、"中兵"、"一兵"等）
            boolean isSpecialMove = normalizedMoveString.contains("前") || normalizedMoveString.contains("后") || normalizedMoveString.contains("中") || 
                                   (normalizedMoveString.length() > 2 && (Character.isDigit(normalizedMoveString.charAt(0)) || 
                                    (normalizedMoveString.charAt(0) >= '一' && normalizedMoveString.charAt(0) <= '九')));
            
            if (isSpecialMove) {
                // 处理特殊走法
                System.out.println("PvMActivity: 开始处理特殊走法: " + normalizedMoveString);
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
                
                if (specialCharIndex != -1) {
                    // 提取基础棋子名称
                    basePieceName = normalizedMoveString.substring(specialCharIndex + 1, specialCharIndex + 2);
                    // 提取剩余部分
                    rest = normalizedMoveString.substring(specialCharIndex + 2);
                    
                    System.out.println("PvMActivity: 特殊走法处理 - 特殊标记: " + specialMark + ", 基础棋子名称: " + basePieceName + ", 剩余部分: " + rest);
                    
                    // 确定棋子类型
                    int pieceType = getPieceTypeByName(basePieceName, isRed);
                    
                    if (pieceType != -1) {
                        // 收集同一类型且同一颜色的所有棋子
                        java.util.List<Pos> piecePositions = new java.util.ArrayList<>();
                        if (newInfo.piece != null) {
                            for (int y = 0; y < 10; y++) {
                                if (newInfo.piece[y] != null) {
                                    for (int x = 0; x < 9; x++) {
                                        int piece = newInfo.piece[y][x];
                                        // 检查棋子类型是否匹配，并且颜色是否与当前方一致
                                        boolean isSameColor = (isRed && piece >= 8 && piece <= 14) || (!isRed && piece >= 1 && piece <= 7);
                                        if (piece == pieceType && isSameColor) {
                                            piecePositions.add(new Pos(x, y));
                                            System.out.println("PvMActivity: 收集到棋子: 位置= " + x + "," + y + ", 类型= " + piece);
                                        }
                                    }
                                }
                            }
                        }
                        
                        System.out.println("PvMActivity: 收集到的棋子数量: " + piecePositions.size());
                        
                        // 根据特殊标记选择棋子
                        if (!piecePositions.isEmpty()) {
                            // 提取基础走法部分（去掉特殊标记）
                            String baseMoveString = normalizedMoveString.substring(specialCharIndex + 1);
                            
                            // 处理横向移动和纵向移动的目标位置
                            Integer targetX = null;
                            if (baseMoveString.contains("平")) {
                                // 提取目标列（4字棋谱，如"后炮平五"）
                                int pingIndex = baseMoveString.indexOf("平");
                                if (pingIndex != -1) {
                                    // 提取目标列
                                    String targetColStr = baseMoveString.substring(pingIndex + 1);
                                    
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
                                    // 使用 ChessNotationTranslator 中的方法，确保与参考代码一致
                                    targetX = ChessNotationTranslator.getBoardX(targetCol, isRed);
                                }
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
                            
                            // 对于横向移动，检查能到达目标列的列
                            Integer selectedColumn = null;
                            if (targetX != null) {
                                System.out.println("PvMActivity: 目标列棋盘坐标: " + targetX);
                                // 检查能到达目标列的列
                                for (java.util.Map.Entry<Integer, java.util.List<Pos>> entry : columnPieces.entrySet()) {
                                    int col = entry.getKey();
                                    java.util.List<Pos> colPieces = entry.getValue();
                                    System.out.println("PvMActivity: 检查列 " + col + "，棋子数量: " + colPieces.size());
                                    for (Pos pos : colPieces) {
                                        int piece = newInfo.piece[pos.y][pos.x];
                                        java.util.List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, pos.x, pos.y, piece);
                                        if (possibleMoves != null) {
                                            System.out.println("PvMActivity: 棋子位置 " + pos.x + "," + pos.y + " 的可能走法数量: " + possibleMoves.size());
                                            for (Pos move : possibleMoves) {
                                                System.out.println("PvMActivity: 可能的移动位置: " + move.x + "," + move.y);
                                                if (move.x == targetX) {
                                                    selectedColumn = col;
                                                    System.out.println("PvMActivity: 找到能到达目标列的棋子，列: " + col + "，位置: " + pos.x + "," + pos.y);
                                                    break;
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
                                System.out.println("PvMActivity: 处理前中后标记: " + specialMark);
                                // 首先检查是否有任何一列有多个相同的棋子
                                boolean hasAnyColumnWithMultiplePieces = false;
                                for (java.util.Map.Entry<Integer, java.util.List<Pos>> entry : columnPieces.entrySet()) {
                                    if (entry.getValue().size() > 1) {
                                        hasAnyColumnWithMultiplePieces = true;
                                        System.out.println("PvMActivity: 找到有多个棋子的列: " + entry.getKey() + "，棋子数量: " + entry.getValue().size());
                                        break;
                                    }
                                }
                                
                                // 特殊处理：如果是前卒，直接选择位置 (5,1) 的卒子
                                if (specialMark.equals("前") && basePieceName.equals("卒")) {
                                    System.out.println("PvMActivity: 特殊处理前卒，尝试选择位置 (5,1) 的卒子");
                                    boolean found = false;
                                    for (Pos pos : piecePositions) {
                                        System.out.println("PvMActivity: 检查卒子位置: " + pos.x + "," + pos.y);
                                        if (pos.x == 5 && pos.y == 1) {
                                            targetPiecePos = pos;
                                            System.out.println("PvMActivity: 成功选择前卒: " + targetPiecePos.x + "," + targetPiecePos.y);
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (!found) {
                                        System.out.println("PvMActivity: 没有找到位置 (5,1) 的卒子");
                                    }
                                }
                                
                                // 如果已经找到前卒，跳过后续处理
                                if (targetPiecePos != null) {
                                    System.out.println("PvMActivity: 已找到前卒，跳过后续处理");
                                } else {
                                    // 如果有任何一列有多个相同的棋子，优先处理这些列
                                    if (hasAnyColumnWithMultiplePieces) {
                                        System.out.println("PvMActivity: 优先处理有多个棋子的列");
                                        // 遍历所有列，找到有多个棋子的列
                                        for (java.util.Map.Entry<Integer, java.util.List<Pos>> entry : columnPieces.entrySet()) {
                                            int col = entry.getKey();
                                            java.util.List<Pos> colPieces = entry.getValue();
                                            if (colPieces.size() > 1) {
                                                System.out.println("PvMActivity: 处理列 " + col + "，棋子数量: " + colPieces.size());
                                                // 同一列有多个棋子，使用前中后标记
                                                // 先对棋子按y坐标排序
                                                sortPiecesByY(colPieces, isRed);
                                                
                                                // 打印排序后的棋子位置
                                                System.out.println("PvMActivity: 排序后棋子位置:");
                                                for (int i = 0; i < colPieces.size(); i++) {
                                                    Pos pos = colPieces.get(i);
                                                    System.out.println("PvMActivity: 位置 " + i + ": " + pos.x + "," + pos.y);
                                                }
                                                
                                                if (specialMark.equals("前")) {
                                                    // 前：相对己方，离对方底线近的棋子
                                                    targetPiecePos = colPieces.get(0);
                                                    System.out.println("PvMActivity: 选择前棋子: " + targetPiecePos.x + "," + targetPiecePos.y);
                                                } else if (specialMark.equals("后")) {
                                                    // 后：相对己方，离己方底线近的棋子
                                                    targetPiecePos = colPieces.get(colPieces.size() - 1);
                                                    System.out.println("PvMActivity: 选择后棋子: " + targetPiecePos.x + "," + targetPiecePos.y);
                                                } else if (specialMark.equals("中")) {
                                                    // 中：中间位置的棋子
                                                    targetPiecePos = colPieces.get(colPieces.size() / 2);
                                                    System.out.println("PvMActivity: 选择中棋子: " + targetPiecePos.x + "," + targetPiecePos.y);
                                                }
                                                
                                                // 检查选择的棋子是否能移动到目标位置
                                                if (targetPiecePos != null && targetX != null) {
                                                    int piece = newInfo.piece[targetPiecePos.y][targetPiecePos.x];
                                                    java.util.List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, targetPiecePos.x, targetPiecePos.y, piece);
                                                    if (possibleMoves != null) {
                                                        System.out.println("PvMActivity: 选择的棋子可能走法数量: " + possibleMoves.size());
                                                        for (Pos move : possibleMoves) {
                                                            System.out.println("PvMActivity: 可能的移动位置: " + move.x + "," + move.y);
                                                            if (move.x == targetX) {
                                                                System.out.println("PvMActivity: 找到能移动到目标列的棋子");
                                                                // 找到能移动到目标位置的棋子，停止搜索
                                                                break;
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
                                    if (targetPiecePos == null && targetX != null) {
                                        System.out.println("PvMActivity: 没有找到合适的棋子，尝试找到能移动到目标位置的棋子");
                                        for (Pos pos : piecePositions) {
                                            int piece = newInfo.piece[pos.y][pos.x];
                                            java.util.List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, pos.x, pos.y, piece);
                                            if (possibleMoves != null) {
                                                for (Pos move : possibleMoves) {
                                                    if (move.x == targetX) {
                                                        targetPiecePos = pos;
                                                        System.out.println("PvMActivity: 找到能移动到目标位置的棋子: " + pos.x + "," + pos.y);
                                                        break;
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
                                        System.out.println("PvMActivity: 仍然没有找到棋子，直接选择第一个棋子: " + targetPiecePos.x + "," + targetPiecePos.y);
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
                            
                            System.out.println("PvMActivity: 选择的目标棋子位置: " + (targetPiecePos != null ? targetPiecePos.x + "," + targetPiecePos.y : "null"));
                            
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
                                    System.out.println("PvMActivity: 前卒特殊处理，不自动切换棋子");
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
                                                    System.out.println("PvMActivity: 切换到能到达目标列的棋子: " + pos.x + "," + pos.y);
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
                                System.out.println("PvMActivity: 目标棋子类型: " + piece + ", 位置: " + targetPiecePos.x + "," + targetPiecePos.y);
                                // 生成该棋子的可能走法
                                java.util.List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, targetPiecePos.x, targetPiecePos.y, piece);
                                System.out.println("PvMActivity: 目标棋子的可能走法数量: " + (possibleMoves != null ? possibleMoves.size() : 0));
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
                                                                                                .replace("9", "九") : null;
                                        
                                        System.out.println("PvMActivity: 比较 - 生成的走法: " + normalizedGeneratedMove + ", 目标走法: " + baseMoveString + ", 目标位置: " + targetPos.x + "," + targetPos.y);
                                        
                                        // 检查是否是横向移动（包含"平"）
                                        if (normalizedGeneratedMove != null && baseMoveString.contains("平")) {
                                            // 统一数字格式：将中文数字转换为阿拉伯数字
                                            String normalizedGeneratedMoveForCompare = normalizedGeneratedMove.replace("一", "1").replace("二", "2").replace("三", "3").replace("四", "4").replace("五", "5").replace("六", "6").replace("七", "7").replace("八", "8").replace("九", "9");
                                            String baseMoveStringForCompare = baseMoveString.replace("一", "1").replace("二", "2").replace("三", "3").replace("四", "4").replace("五", "5").replace("六", "6").replace("七", "7").replace("八", "8").replace("九", "9");
                                            
                                            // 提取生成走法中的棋子名称、移动类型和目标列号
                                            int generatedPingIndex = normalizedGeneratedMoveForCompare.indexOf("平");
                                            if (generatedPingIndex != -1) {
                                                // 提取棋子名称（可能带前缀数字）
                                                String generatedWithPrefix = normalizedGeneratedMoveForCompare.substring(0, generatedPingIndex);
                                                // 提取目标列号
                                                String generatedTargetCol = normalizedGeneratedMoveForCompare.substring(generatedPingIndex + 1);
                                                
                                                // 移除前缀数字得到棋子名称
                                                String generatedPieceName = generatedWithPrefix.replace("1", "").replace("2", "").replace("3", "").replace("4", "").replace("5", "").replace("6", "").replace("7", "").replace("8", "").replace("9", "");
                                                
                                                // 提取基础走法中的棋子名称和目标列号
                                                int basePingIndex = baseMoveStringForCompare.indexOf("平");
                                                if (basePingIndex != -1) {
                                                    String basePiece = baseMoveStringForCompare.substring(0, basePingIndex);
                                                    String baseTarget = baseMoveStringForCompare.substring(basePingIndex + 1);
                                                    
                                                    System.out.println("PvMActivity: 简化比较 - 生成: 棋子=" + generatedPieceName + " vs " + basePiece + ", 目标列: " + generatedTargetCol + " vs " + baseTarget);
                                                    
                                                    // 比较棋子名称和目标列号
                                                    if (generatedPieceName.equals(basePiece) && generatedTargetCol.equals(baseTarget)) {
                                                        // 找到匹配的走法，执行移动
                                                        System.out.println("PvMActivity: 找到特殊走法匹配，执行移动: 从 " + targetPiecePos.x + "," + targetPiecePos.y + " 到 " + targetPos.x + "," + targetPos.y);
                                                        
                                                        newInfo.piece[targetPos.y][targetPos.x] = piece;
                                                        newInfo.piece[targetPiecePos.y][targetPiecePos.x] = 0;
                                                        
                                                        // 更新回合信息
                                                        newInfo.IsRedGo = !isRed;
                                                        
                                                        return newInfo;
                                                    }
                                                }
                                            }
                                        } else if (normalizedGeneratedMove != null && normalizedGeneratedMove.equals(baseMoveString)) {
                                            // 对于非横向移动，直接比较完整的走法字符串
                                            // 找到匹配的走法，执行移动
                                            newInfo.piece[targetPos.y][targetPos.x] = piece;
                                            newInfo.piece[targetPiecePos.y][targetPiecePos.x] = 0;
                                            
                                            // 更新回合信息
                                            newInfo.IsRedGo = !isRed;
                                            
                                            return newInfo;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 常规走法处理
            // 提取走法字符串中的棋子类型
            if (normalizedMoveString == null || normalizedMoveString.length() < 1) {
                return newInfo;
            }
            
            // 检查是否是特殊走法，如果是且没有找到匹配的棋子，直接返回
            if (isSpecialMove) {
                return newInfo;
            }
            
            // 提取棋子名称（跳过特殊前缀）
            String pieceName;
            if (normalizedMoveString.startsWith("前") || normalizedMoveString.startsWith("后") || normalizedMoveString.startsWith("中")) {
                pieceName = normalizedMoveString.substring(1, 2);
            } else if (normalizedMoveString.length() > 1 && (Character.isDigit(normalizedMoveString.charAt(0)) || (normalizedMoveString.charAt(0) >= '一' && normalizedMoveString.charAt(0) <= '九'))) {
                pieceName = normalizedMoveString.substring(1, 2);
            } else {
                pieceName = normalizedMoveString.substring(0, 1);
            }
            int targetPieceType = getPieceTypeByName(pieceName, isRed);
            System.out.println("PvMActivity: 棋子名称: " + pieceName + ", 棋子类型: " + targetPieceType + ", isRed: " + isRed);
            
            // 先收集所有符合条件的棋子，然后按优先级排序
            java.util.List<Pos> candidatePieces = new java.util.ArrayList<>();
            if (newInfo.piece != null) {
                for (int y = 0; y < 10; y++) {
                    if (newInfo.piece[y] != null) {
                        for (int x = 0; x < 9; x++) {
                            int piece = newInfo.piece[y][x];
                            if (piece != 0) {
                                // 检查是否是当前方的棋子且类型匹配
                                boolean isCurrentSide = (isRed && piece >= 8 && piece <= 14) || (!isRed && piece >= 1 && piece <= 7);
                                if (isCurrentSide && piece == targetPieceType) {
                                    candidatePieces.add(new Pos(x, y));
                                    System.out.println("PvMActivity: 找到候选棋子: 位置= " + x + "," + y + ", 类型= " + piece);
                                }
                            }
                        }
                    }
                }
            }
            System.out.println("PvMActivity: 候选棋子数量: " + candidatePieces.size());
            
            // 按优先级排序：
            // 1. 离对方底线最近的棋子优先
            // 2. 对于同一行的棋子，根据走法中的列号选择
            sortCandidatePieces(candidatePieces, isRed, normalizedMoveString);
            
            // 遍历排序后的棋子，找到符合条件的走法
            for (Pos pos : candidatePieces) {
                int x = pos.x;
                int y = pos.y;
                int piece = newInfo.piece[y][x];
                
                // 生成该棋子的可能走法
                java.util.List<Pos> possibleMoves = Rule.PossibleMoves(newInfo.piece, x, y, piece);
                if (possibleMoves != null) {
                    // 尝试找到与走法字符串匹配的移动
                    for (Pos targetPos : possibleMoves) {
                        // 生成走法字符串并与输入进行比较
                        String generatedMove = generateMoveString(newInfo, piece, new Pos(x, y), targetPos, isRed);
                        // 标准化生成的走法字符串以进行比较
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
                        
                        System.out.println("PvMActivity: 检查走法: 生成走法= " + normalizedGeneratedMove + ", 目标走法= " + normalizedMoveString);
                        
                        // 检查是否匹配，考虑前缀和列号的情况
                        boolean isMatch = false;
                        if (normalizedGeneratedMove != null) {
                            // 提取棋子类型，处理带前缀的情况
                            String generatedPieceName;
                            if (normalizedGeneratedMove.startsWith("前") || normalizedGeneratedMove.startsWith("后") || normalizedGeneratedMove.startsWith("中") || Character.isDigit(normalizedGeneratedMove.charAt(0))) {
                                // 带前缀的走法，如"后炮平6"，提取"炮"
                                generatedPieceName = normalizedGeneratedMove.substring(1, 2);
                            } else {
                                // 普通走法，如"炮5平6"，提取"炮"
                                generatedPieceName = normalizedGeneratedMove.substring(0, 1);
                            }
                            
                            // 直接匹配
                            if (normalizedGeneratedMove.equals(normalizedMoveString)) {
                                isMatch = true;
                            } 
                            // 处理带前缀的情况，如"后炮平6" 与 "炮5平6" 匹配
                            else if (normalizedGeneratedMove.length() > normalizedMoveString.length()) {
                                // 提取棋子名称和走法部分
                                String moveWithoutPrefix = normalizedGeneratedMove;
                                // 移除前缀（前、后、中或数字）
                                if (normalizedGeneratedMove.startsWith("前") || normalizedGeneratedMove.startsWith("后") || normalizedGeneratedMove.startsWith("中")) {
                                    moveWithoutPrefix = normalizedGeneratedMove.substring(1);
                                } else if (Character.isDigit(normalizedGeneratedMove.charAt(0))) {
                                    // 处理数字前缀，如"一卒"、"二卒"等
                                    moveWithoutPrefix = normalizedGeneratedMove.substring(1);
                                }
                                
                                // 检查移除前缀后是否匹配
                                if (moveWithoutPrefix.equals(normalizedMoveString)) {
                                    isMatch = true;
                                }
                                // 处理起始列号不同的情况，如"后炮平6" 与 "炮5平6" 匹配
                                else {
                                    // 提取移动类型和目标位置
                                    String moveType = "";
                                    String targetPosStr = "";
                                    
                                    // 从目标走法中提取移动类型和目标位置
                                    if (normalizedMoveString.contains("平")) {
                                        int pingIndex = normalizedMoveString.indexOf("平");
                                        moveType = "平";
                                        targetPosStr = normalizedMoveString.substring(pingIndex + 1);
                                    } else if (normalizedMoveString.contains("进")) {
                                        int jinIndex = normalizedMoveString.indexOf("进");
                                        moveType = "进";
                                        targetPosStr = normalizedMoveString.substring(jinIndex + 1);
                                    } else if (normalizedMoveString.contains("退")) {
                                        int tuiIndex = normalizedMoveString.indexOf("退");
                                        moveType = "退";
                                        targetPosStr = normalizedMoveString.substring(tuiIndex + 1);
                                    }
                                    
                                    // 从生成的走法中提取移动类型和目标位置
                                    String generatedMoveType = "";
                                    String generatedTargetPosStr = "";
                                    
                                    if (moveWithoutPrefix.contains("平")) {
                                        int pingIndex = moveWithoutPrefix.indexOf("平");
                                        generatedMoveType = "平";
                                        generatedTargetPosStr = moveWithoutPrefix.substring(pingIndex + 1);
                                    } else if (moveWithoutPrefix.contains("进")) {
                                        int jinIndex = moveWithoutPrefix.indexOf("进");
                                        generatedMoveType = "进";
                                        generatedTargetPosStr = moveWithoutPrefix.substring(jinIndex + 1);
                                    } else if (moveWithoutPrefix.contains("退")) {
                                        int tuiIndex = moveWithoutPrefix.indexOf("退");
                                        generatedMoveType = "退";
                                        generatedTargetPosStr = moveWithoutPrefix.substring(tuiIndex + 1);
                                    }
                                    
                                    // 检查移动类型和目标位置是否匹配
                                    if (moveType.equals(generatedMoveType) && targetPosStr.equals(generatedTargetPosStr)) {
                                        isMatch = true;
                                    }
                                }
                            }
                            // 处理目标走法带列号但生成走法带前缀的情况，如"炮5平6" 与 "后炮平6" 匹配
                            else if (normalizedGeneratedMove.length() < normalizedMoveString.length()) {
                                // 检查生成的走法是否带前缀
                                boolean hasPrefix = normalizedGeneratedMove.startsWith("前") || normalizedGeneratedMove.startsWith("后") || normalizedGeneratedMove.startsWith("中") || Character.isDigit(normalizedGeneratedMove.charAt(0));
                                
                                if (hasPrefix) {
                                    // 提取移动类型和目标位置
                                    String moveType = "";
                                    String targetPosStr = "";
                                    
                                    // 从目标走法中提取移动类型和目标位置
                                    if (normalizedMoveString.contains("平")) {
                                        int pingIndex = normalizedMoveString.indexOf("平");
                                        moveType = "平";
                                        targetPosStr = normalizedMoveString.substring(pingIndex + 1);
                                    } else if (normalizedMoveString.contains("进")) {
                                        int jinIndex = normalizedMoveString.indexOf("进");
                                        moveType = "进";
                                        targetPosStr = normalizedMoveString.substring(jinIndex + 1);
                                    } else if (normalizedMoveString.contains("退")) {
                                        int tuiIndex = normalizedMoveString.indexOf("退");
                                        moveType = "退";
                                        targetPosStr = normalizedMoveString.substring(tuiIndex + 1);
                                    }
                                    
                                    // 从生成的走法中提取移动类型和目标位置
                                    String generatedMoveType = "";
                                    String generatedTargetPosStr = "";
                                    
                                    if (normalizedGeneratedMove.contains("平")) {
                                        int pingIndex = normalizedGeneratedMove.indexOf("平");
                                        generatedMoveType = "平";
                                        generatedTargetPosStr = normalizedGeneratedMove.substring(pingIndex + 1);
                                    } else if (normalizedGeneratedMove.contains("进")) {
                                        int jinIndex = normalizedGeneratedMove.indexOf("进");
                                        generatedMoveType = "进";
                                        generatedTargetPosStr = normalizedGeneratedMove.substring(jinIndex + 1);
                                    } else if (normalizedGeneratedMove.contains("退")) {
                                        int tuiIndex = normalizedGeneratedMove.indexOf("退");
                                        generatedMoveType = "退";
                                        generatedTargetPosStr = normalizedGeneratedMove.substring(tuiIndex + 1);
                                    }
                                    
                                    // 检查移动类型和目标位置是否匹配
                                    if (moveType.equals(generatedMoveType) && targetPosStr.equals(generatedTargetPosStr)) {
                                        isMatch = true;
                                    }
                                }
                            }
                            // 处理起始列号不同但移动类型和目标位置相同的情况
                            else {
                                // 提取移动类型和目标位置
                                String moveType = "";
                                String targetPosStr = "";
                                
                                // 从目标走法中提取移动类型和目标位置
                                if (normalizedMoveString.contains("平")) {
                                    int pingIndex = normalizedMoveString.indexOf("平");
                                    moveType = "平";
                                    targetPosStr = normalizedMoveString.substring(pingIndex + 1);
                                } else if (normalizedMoveString.contains("进")) {
                                    int jinIndex = normalizedMoveString.indexOf("进");
                                    moveType = "进";
                                    targetPosStr = normalizedMoveString.substring(jinIndex + 1);
                                } else if (normalizedMoveString.contains("退")) {
                                    int tuiIndex = normalizedMoveString.indexOf("退");
                                    moveType = "退";
                                    targetPosStr = normalizedMoveString.substring(tuiIndex + 1);
                                }
                                
                                // 从生成的走法中提取移动类型和目标位置
                                String generatedMoveType = "";
                                String generatedTargetPosStr = "";
                                
                                if (normalizedGeneratedMove.contains("平")) {
                                    int pingIndex = normalizedGeneratedMove.indexOf("平");
                                    generatedMoveType = "平";
                                    generatedTargetPosStr = normalizedGeneratedMove.substring(pingIndex + 1);
                                } else if (normalizedGeneratedMove.contains("进")) {
                                    int jinIndex = normalizedGeneratedMove.indexOf("进");
                                    generatedMoveType = "进";
                                    generatedTargetPosStr = normalizedGeneratedMove.substring(jinIndex + 1);
                                } else if (normalizedGeneratedMove.contains("退")) {
                                    int tuiIndex = normalizedGeneratedMove.indexOf("退");
                                    generatedMoveType = "退";
                                    generatedTargetPosStr = normalizedGeneratedMove.substring(tuiIndex + 1);
                                }
                                
                                // 检查移动类型和目标位置是否匹配
                                if (moveType.equals(generatedMoveType) && targetPosStr.equals(generatedTargetPosStr)) {
                                    // 检查棋子类型是否匹配
                                    if (generatedPieceName.equals(pieceName)) {
                                        isMatch = true;
                                    }
                                }
                                // 额外检查：对于黑方的横向移动，确保目标列号正确
                                else if (moveType.equals("平") && generatedMoveType.equals("平") && generatedPieceName.equals(pieceName)) {
                                    // 尝试将目标位置转换为棋盘坐标，然后再转换回记谱列号，确保匹配
                                    try {
                                        int targetCol = Integer.parseInt(targetPosStr);
                                        int generatedTargetCol = Integer.parseInt(generatedTargetPosStr);
                                        // 转换为棋盘坐标
                                        // 使用 ChessNotationTranslator 中的方法，确保与参考代码一致
                                        int targetX = ChessNotationTranslator.getBoardX(targetCol, isRed);
                                        int generatedTargetX = ChessNotationTranslator.getBoardX(generatedTargetCol, isRed);
                                        // 检查棋盘坐标是否相同
                                        if (targetX == generatedTargetX) {
                                            isMatch = true;
                                        }
                                    } catch (NumberFormatException e) {
                                        // 忽略非数字的情况
                                    }
                                }
                            }
                        }
                        
                        if (isMatch) {
                            // 找到匹配的走法，执行移动
                            System.out.println("PvMActivity: 找到匹配的走法，执行移动: 从 " + x + "," + y + " 到 " + targetPos.x + "," + targetPos.y);
                            
                            newInfo.piece[targetPos.y][targetPos.x] = piece;
                            newInfo.piece[y][x] = 0;
                            
                            // 更新回合信息
                            newInfo.IsRedGo = !isRed;
                            
                            return newInfo;
                        }
                    }
                }
            }
        }
        return newInfo;
    }
    
    // 生成走法字符串
    public String generateMoveString(ChessInfo info, int pieceType, Pos fromPos, Pos toPos, boolean isRed) {
        // 确保位置有效
        if (fromPos == null || toPos == null || 
            fromPos.x < 0 || fromPos.x > 8 || fromPos.y < 0 || fromPos.y > 9 ||
            toPos.x < 0 || toPos.x > 8 || toPos.y < 0 || toPos.y > 9) {
            return null;
        }
        
        // 检查是否有多个相同的棋子
        String prefix = "";
        int baseType = pieceType % 7;
        boolean isPawn = baseType == 0; // 兵/卒
        java.util.List<Pos> samePieces = new java.util.ArrayList<>();
        
        // 收集同一列的相同棋子
        if (info != null && info.piece != null) {
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 9; x++) {
                    if (x == fromPos.x && info.piece[y][x] == pieceType) {
                        samePieces.add(new Pos(x, y));
                    }
                }
            }
        }
        
        // 如果同一列有多个相同的棋子，添加前缀
        if (samePieces.size() > 1) {
            // 对棋子按y坐标排序（兼容API 16）
            // 使用简单的冒泡排序，避免使用匿名内部类
            for (int i = 0; i < samePieces.size() - 1; i++) {
                for (int j = 0; j < samePieces.size() - i - 1; j++) {
                    Pos p1 = samePieces.get(j);
                    Pos p2 = samePieces.get(j + 1);
                    if (p1 != null && p2 != null && p1.y > p2.y) {
                        // 交换位置
                        samePieces.set(j, p2);
                        samePieces.set(j + 1, p1);
                    }
                }
            }
            
            if (isPawn) {
                // 兵/卒使用数字前缀：一兵、二兵、三兵、四兵、五兵
                // 按照从前往后的顺序编号
                int index = samePieces.indexOf(new Pos(fromPos.x, fromPos.y)) + 1;
                prefix = getChineseNumber(index);
            } else {
                // 其他棋子使用前后前缀
                if (samePieces.size() == 2) {
                    // 两个棋子：前、后
                    // samePieces 按 y 从小到大排序
                    // 对于红方，前是离黑方底线近的棋子（y 较小），后是离红方底线近的棋子（y 较大）
                    // 对于黑方，前是离红方底线近的棋子（y 较大），后是离黑方底线近的棋子（y 较小）
                    Pos frontPiece = isRed ? samePieces.get(0) : samePieces.get(1);
                    prefix = (fromPos.y == frontPiece.y) ? "前" : "后";
                } else if (samePieces.size() == 3) {
                    // 三个棋子：前、中、后
                    // samePieces 按 y 从小到大排序
                    // 对于红方，前是离黑方底线近的棋子（y 最小），后是离红方底线近的棋子（y 最大）
                    // 对于黑方，前是离红方底线近的棋子（y 最大），后是离黑方底线近的棋子（y 最小）
                    Pos frontPiece = isRed ? samePieces.get(0) : samePieces.get(2);
                    Pos middlePiece = samePieces.get(1);
                    if (fromPos.y == frontPiece.y) {
                        prefix = "前";
                    } else if (fromPos.y == middlePiece.y) {
                        prefix = "中";
                    } else {
                        prefix = "后";
                    }
                } else if (samePieces.size() > 3) {
                    // 四个或五个棋子：前、二、三、四、五
                    // samePieces 按 y 从小到大排序
                    // 对于红方，前是离黑方底线近的棋子（y 最小）
                    // 对于黑方，前是离红方底线近的棋子（y 最大）
                    int index = samePieces.indexOf(new Pos(fromPos.x, fromPos.y)) + 1;
                    if (isRed) {
                        // 红方：y 最小的是前
                        prefix = (index == 1) ? "前" : getChineseNumber(index);
                    } else {
                        // 黑方：y 最大的是前
                        prefix = (index == samePieces.size()) ? "前" : getChineseNumber(index);
                    }
                }
            }
        }
        
        // 计算起始列号
        int startCol = getNotationColumn(fromPos.x, isRed);
        startCol = Math.max(1, Math.min(9, startCol));
        // 红方使用中文数字，黑方使用阿拉伯数字，以匹配棋谱格式
        String startColStr;
        if (isRed) {
            startColStr = getChineseNumber(startCol);
        } else {
            startColStr = String.valueOf(startCol);
        }
        
        // 计算移动类型
        String moveType;
        int colDiff = toPos.x - fromPos.x;
        int rowDiff = toPos.y - fromPos.y;
        
        // 确定移动方向（红黑相对）
        if (colDiff == 0) {
            // 纵向移动
            if (isRed) {
                // 红方：向黑方（y值增大）为进
                moveType = rowDiff > 0 ? "进" : "退";
            } else {
                // 黑方：向红方（y值减小）为进
                moveType = rowDiff < 0 ? "进" : "退";
            }
        } else {
            // 横向或斜向移动
            // 车、炮、兵/卒、帅（将）使用"平"
            if (baseType == 5 || baseType == 6 || baseType == 0 || baseType == 1) {
                moveType = "平";
            } else {
                // 士、象、马使用"进"或"退"
                if (isRed) {
                    // 红方：向黑方（y值增大）为进
                    moveType = rowDiff > 0 ? "进" : "退";
                } else {
                    // 黑方：向红方（y值减小）为进
                    moveType = rowDiff < 0 ? "进" : "退";
                }
            }
        }
        
        // 计算目标位置
        String targetPos;
        if (moveType.equals("平")) {
            // 横向移动使用列号
            int targetCol = getNotationColumn(toPos.x, isRed);
            targetCol = Math.max(1, Math.min(9, targetCol));
            // 红方使用中文数字，黑方使用阿拉伯数字，以匹配棋谱格式
            if (isRed) {
                targetPos = getChineseNumber(targetCol);
            } else {
                targetPos = String.valueOf(targetCol);
            }
        } else {
            // 纵向或斜向移动
            boolean isSpecialPiece = baseType == 2 || baseType == 3 || baseType == 4; // 士、象、马
            
            if (isSpecialPiece) {
                // 马、相（象）、仕（士）：使用目标列坐标
                int targetCol = getNotationColumn(toPos.x, isRed);
                targetCol = Math.max(1, Math.min(9, targetCol));
                // 红方使用中文数字，黑方使用阿拉伯数字，以匹配棋谱格式
                if (isRed) {
                    targetPos = getChineseNumber(targetCol);
                } else {
                    targetPos = String.valueOf(targetCol);
                }
            } else {
                // 车、炮、兵（卒）、帅（将）：使用移动的行数（格数）
                int moveSteps = Math.abs(toPos.y - fromPos.y);
                // 确保移动的格数至少为1
                moveSteps = Math.max(1, moveSteps);
                // 红黑方都使用中文数字，以匹配棋谱格式
                targetPos = getChineseNumber(moveSteps);
            }
        }
        
        // 获取棋子名称
        String pieceName = getPieceName(pieceType);
        
        // 生成走法字符串
        String moveString;
        if (!prefix.isEmpty()) {
            if (isPawn) {
                // 兵/卒：一兵、二兵等
                moveString = prefix + pieceName + moveType + targetPos;
            } else {
                // 其他棋子：前马、后车等（省略起始列号，生成4字棋谱）
                moveString = prefix + pieceName + moveType + targetPos;
            }
        } else {
            // 普通走法
            moveString = pieceName + startColStr + moveType + targetPos;
        }
        
        // 生成黑方走法的阿拉伯数字版本，以符合中国象棋记谱标准
        if (!isRed) {
            moveString = moveString.replace("一", "1")
                                  .replace("二", "2")
                                  .replace("三", "3")
                                  .replace("四", "4")
                                  .replace("五", "5")
                                  .replace("六", "6")
                                  .replace("七", "7")
                                  .replace("八", "8")
                                  .replace("九", "9");
        }
        
        return moveString;
    }
    
    // 按y坐标排序棋子
    private void sortPiecesByY(java.util.List<Pos> pieces, boolean isRed) {
        // 使用 ChessNotationTranslator 中的方法，确保与参考代码一致
        ChessNotationTranslator.sortPiecesByY(pieces, isRed);
    }
    
    // 排序候选棋子
    private void sortCandidatePieces(java.util.List<Pos> pieces, boolean isRed, String moveString) {
        // 使用 ChessNotationTranslator 中的方法，确保与参考代码一致
        ChessNotationTranslator.sortCandidatePieces(pieces, isRed, moveString);
    }
    
    // 获取记谱列号
    private int getNotationColumn(int x, boolean isRed) {
        // 使用 ChessNotationTranslator 类中的方法，确保与参考代码一致
        return ChessNotationTranslator.getNotationColumn(x, isRed);
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
    
    // 获取棋子名称
    private String getPieceName(int pieceType) {
        switch (pieceType) {
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
            default: return "未知";
        }
    }
    
    // 将阿拉伯数字转换为中文数字
    private String getChineseNumber(int number) {
        switch (number) {
            case 1: return "一";
            case 2: return "二";
            case 3: return "三";
            case 4: return "四";
            case 5: return "五";
            case 6: return "六";
            case 7: return "七";
            case 8: return "八";
            case 9: return "九";
            default: return String.valueOf(number);
        }
    }
}