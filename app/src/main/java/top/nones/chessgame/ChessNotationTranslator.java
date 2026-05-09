package top.nones.chessgame;

import java.util.ArrayList;
import java.util.List;
import Info.Pos;

/**
 * 中国象棋记谱与坐标转换器
 * 
 * 该类负责处理中国象棋记谱与棋盘坐标之间的相互转换，包括：
 * 1. 记谱转坐标：将标准的中国象棋记谱（如"炮二平五"）转换为棋盘坐标移动
 * 2. 坐标转记谱：将棋盘坐标移动转换为标准的中国象棋记谱
 * 3. 处理红黑方的不同计数方向：红方从右到左计数，黑方从左到右计数
 * 4. 处理特殊记谱：如"前卒"、"后马"、"中兵"、"一兵"等
 * 
 * 棋盘坐标说明：
 * - 棋盘横向（列）坐标：0-8，从左到右
 * - 棋盘纵向（行）坐标：0-9，从下到上
 * - 红方底线在 y=9，黑方底线在 y=0
 * 
 * 记谱规则说明：
 * - 红方使用中文数字（一、二、三...九），从右到左计数
 * - 黑方使用阿拉伯数字（1、2、3...9），从左到右计数
 * - 移动类型：进（向前）、退（向后）、平（横向移动）
 * - 特殊记谱：当同一列有多个相同棋子时，使用"前"、"中"、"后"或数字（一、二、三）来区分
 */
public class ChessNotationTranslator {

    /**
     * 将棋盘坐标转换为记谱列号
     * 
     * @param x         棋盘横向坐标（0-8）
     * @param isRed     是否为红方
     * @return          记谱列号（1-9）
     */
    public static int getNotationColumn(int x, boolean isRed) {
        if (isRed) {
            // 红方：从右到左计数，右为一
            return 9 - x;
        } else {
            // 黑方：从左到右计数，左为1
            return x + 1;
        }
    }

    /**
     * 将记谱列号转换为棋盘坐标
     * 
     * @param column    记谱列号（1-9）
     * @param isRed     是否为红方
     * @return          棋盘横向坐标（0-8）
     */
    public static int getBoardX(int column, boolean isRed) {
        if (isRed) {
            // 红方：从右到左计数，右为一
            return 9 - column;
        } else {
            // 黑方：从左到右计数，左为1
            return column - 1;
        }
    }

    /**
     * 将列号转换为中文数字（用于红方记谱）
     * 
     * @param column    列号（1-9）
     * @return          中文数字（一、二、三...九）
     */
    public static String getColChar(int column) {
        String result = Info.ChessPiece.toChineseNumber(column);
        return result.equals(String.valueOf(column)) ? "五" : result;
    }

    /**
     * 将中文数字转换为列号
     * 
     * @param colChar   中文数字（一、二、三...九）或阿拉伯数字（1-9）
     * @return          列号（1-9）
     */
    public static int getColNumber(char colChar) {
        return Info.ChessPiece.fromChineseNumber(colChar);
    }

    /**
     * 标准化走法字符串
     * 
     * @param moveString    原始走法字符串
     * @param isRed         是否为红方
     * @return              标准化后的走法字符串
     */
    public static String normalizeMoveString(String moveString, boolean isRed) {
        if (moveString == null) {
            return null;
        }

        // 移除空格和其他非必要字符
        String normalized = moveString.trim()
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
            normalized = normalized.replace("0", "零")
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
            normalized = normalized.replace("零", "0")
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

        return normalized;
    }

    /**
     * 检查走法是否为特殊走法（如"前卒"、"后马"、"中兵"、"一兵"等）
     * 
     * @param moveString    走法字符串
     * @return              是否为特殊走法
     */
    public static boolean isSpecialMove(String moveString) {
        if (moveString == null) {
            return false;
        }
        return moveString.contains("前") || moveString.contains("后") || moveString.contains("中") ||
                (moveString.length() > 2 && (Character.isDigit(moveString.charAt(0)) ||
                        (moveString.charAt(0) >= '一' && moveString.charAt(0) <= '九')));
    }

    /**
     * 按y坐标排序棋子位置列表
     * 
     * @param pieces    棋子位置列表
     * @param isRed     是否为红方
     */
    public static void sortPiecesByY(List<Info.Pos> pieces, boolean isRed) {
        if (pieces == null || pieces.size() <= 1) {
            return;
        }

        java.util.Collections.sort(pieces, (p1, p2) -> {
            if (isRed) {
                return Integer.compare(p2.y, p1.y);
            } else {
                return Integer.compare(p1.y, p2.y);
            }
        });
    }

    /**
     * 按优先级排序候选棋子
     * 
     * @param pieces        候选棋子位置列表
     * @param isRed         是否为红方
     * @param moveString    走法字符串
     */
    public static void sortCandidatePieces(List<Info.Pos> pieces, boolean isRed, String moveString) {
        if (pieces == null || pieces.size() <= 1) {
            return;
        }

        // 提取走法中的列号
        int moveCol = -1;
        if (moveString != null && moveString.length() > 1) {
            int colIndex = 1;
            if (moveString.length() > 2) {
                char secondChar = moveString.charAt(1);
                if (secondChar == '前' || secondChar == '后' || secondChar == '中') {
                    colIndex = 2;
                }
            }
            if (colIndex < moveString.length()) {
                char colChar = moveString.charAt(colIndex);
                moveCol = getColNumber(colChar);
            }
        }

        final int targetCol = moveCol;
        final boolean red = isRed;

        java.util.Collections.sort(pieces, (p1, p2) -> {
            int col1 = getNotationColumn(p1.x, red);
            int col2 = getNotationColumn(p2.x, red);

            // 根据列号匹配度排序
            if (targetCol != -1) {
                boolean match1 = (col1 == targetCol);
                boolean match2 = (col2 == targetCol);
                if (match1 && !match2) return -1;
                if (!match1 && match2) return 1;
                if (match1) {
                    // 两个都匹配，按y坐标排序
                    return red ? Integer.compare(p2.y, p1.y) : Integer.compare(p1.y, p2.y);
                }
                // 两个都不匹配，按列号距离排序
                int dist = Integer.compare(Math.abs(col1 - targetCol), Math.abs(col2 - targetCol));
                if (dist != 0) return dist;
            }

            // 按y坐标排序
            int yCmp = red ? Integer.compare(p2.y, p1.y) : Integer.compare(p1.y, p2.y);
            if (yCmp != 0) return yCmp;

            // y相同时按x排序
            return red ? Integer.compare(p2.x, p1.x) : Integer.compare(p1.x, p2.x);
        });
    }
}
