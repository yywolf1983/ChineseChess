package top.nones.chessgame;

import Info.ChessInfo;
import Info.ChessNotation;

public class FENHandler {
    // 从FEN字符串生成ChessInfo
    public ChessInfo fenToChessInfo(String fen) {
        ChessInfo info = new ChessInfo();
        try {
            if (fen != null && !fen.isEmpty() && info.piece != null) {
                // 简单的FEN解析实现
                String[] parts = fen.split(" ");
                if (parts.length > 0) {
                    // 解析棋盘部分
                    String boardPart = parts[0];
                    int rank = 9; // 从黑方底线开始（对应棋盘的y=9位置）
                    int file = 0;
                    
                    // 清空棋盘
                    for (int i = 0; i < 10; i++) {
                        if (info.piece[i] != null) {
                            for (int j = 0; j < 9; j++) {
                                info.piece[i][j] = 0;
                            }
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
                            if (piece != 0 && rank >= 0 && rank < 10 && file >= 0 && file < 9 && info.piece[rank] != null) {
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
    
    // 生成FEN字符串
    public String generateFEN(ChessInfo chessInfo) {
        if (chessInfo == null || chessInfo.piece == null) {
            return "";
        }
        
        StringBuilder fen = new StringBuilder();
        
        try {
            // 生成棋盘部分
            for (int rank = 9; rank >= 0; rank--) { // 从黑方底线开始
                int emptyCount = 0;
                for (int file = 0; file < 9; file++) {
                    if (chessInfo.piece[rank] != null) {
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
                    } else {
                        emptyCount++;
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
            fen.append(" " + (chessInfo.IsRedGo ? "w" : "b"));
            
            // 添加其他FEN部分（简化实现）
            fen.append(" - - 0 1");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        
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
    
    // 生成用于保存的FEN字符串
    public String generateFENForSave(ChessInfo chessInfo, String setupFEN, ChessNotation currentNotation) {
        // 检查是否有摆棋结束时的FEN信息
        if (setupFEN != null && !setupFEN.isEmpty()) {
            return setupFEN;
        }
        
        // 检查是否在摆棋模式下
        if (chessInfo != null && chessInfo.IsSetupMode) {
            // 在摆棋模式下，使用当前棋盘状态生成FEN
            return generateFEN(chessInfo);
        }
        
        // 检查是否有FEN信息在currentNotation中（加载的棋局）
        if (currentNotation != null && currentNotation.getFen() != null && !currentNotation.getFen().isEmpty()) {
            // 使用棋谱中已有的FEN作为初始状态，保持FEN不变
            return currentNotation.getFen();
        }
        
        // 使用标准初始状态（新局）
        ChessInfo initialInfo = new ChessInfo();
        return generateFEN(initialInfo);
    }
}