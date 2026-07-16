package ChessMove;

import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import Info.ChessPiece;
import Info.Pos;

public class Rule {

    private static final String TAG = "Rule";
    private static final int BOARD_COLS = 9;
    private static final int BOARD_ROWS = 10;

    public static int[][] copyBoard(int[][] piece) {
        int[][] copy = new int[BOARD_ROWS][BOARD_COLS];
        for (int i = 0; i < BOARD_ROWS; i++) {
            System.arraycopy(piece[i], 0, copy[i], 0, BOARD_COLS);
        }
        return copy;
    }
    public static int[][] area = {
            {3, 3, 3, 4, 4, 4, 3, 3, 3},
            {3, 3, 3, 4, 4, 4, 3, 3, 3},
            {3, 3, 3, 4, 4, 4, 3, 3, 3},
            {3, 3, 3, 3, 3, 3, 3, 3, 3},
            {3, 3, 3, 3, 3, 3, 3, 3, 3},

            {1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 2, 2, 2, 1, 1, 1},
            {1, 1, 1, 2, 2, 2, 1, 1, 1},
            {1, 1, 1, 2, 2, 2, 1, 1, 1}
    };
    public static int[][] offsetX = {
            {0, 0, 1, -1},             //帅 将
            {1, 1, -1, -1},            //仕 士
            {2, 2, -2, -2},            //相 象
            {1, 1, -1, -1},            //象眼
            {1, 1, -1, -1, 2, 2, -2, -2},  //马
            {0, 0, 0, 0, 1, 1, -1, -1},    //蹩马腿
            {0},                    //卒（未过河）
            {-1, 0, 1},               //过河卒
            {0},                    //兵（未过河）
            {-1, 0, 1},               //过河兵
            {1, 1, -1, -1, 1, 1, -1, -1}  //反向蹩马腿
    };
    public static int[][] offsetY = {
            {1, -1, 0, 0},             //帅 将
            {1, -1, 1, -1},            //仕 士
            {2, -2, 2, -2},            //相 象
            {1, -1, 1, -1},            //象眼
            {2, -2, 2, -2, 1, -1, 1, -1},  //马
            {1, -1, 1, -1, 0, 0, 0, 0},    //蹩马腿
            {-1},                    //卒（向前，向红方方向，向上移动）
            {0, -1, 0},               //过河卒（向上和横向移动）
            {1},                   //兵（向前，向黑方方向，整体反转后向下移动）
            {0, 1, 0},               //过河兵（整体反转后向下和横向移动）
            {1, -1, 1, -1, 1, -1, 1, -1}  //反向蹩马腿
    };

    public static List<Pos> PossibleMoves(int[][] piece, int fromX, int fromY, int PieceID) {
        List<Pos> ret = new ArrayList<Pos>(10); // 预分配容量，减少扩容
        
        // 参数验证
        if (piece == null || piece.length != 10 || fromX < 0 || fromX >= 9 || fromY < 0 || fromY >= 10) {
            return ret;
        }
        
        // 方向数组
        int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}; // 上下左右
        
        switch (PieceID) {
            case 1: // 黑将
            case 8: // 红帅
                int area = PieceID == 1 ? 2 : 4;
                // 王的移动（上下左右）
                for (int[] dir : directions) {
                    int toX = fromX + dir[0];
                    int toY = fromY + dir[1];
                    if (InArea(toX, toY) == area && !IsSameSide(PieceID, piece[toY][toX])) {
                        ret.add(new Pos(toX, toY));
                    }
                }
                // 飞将检查
                Pos eatPos = flyKing(PieceID == 1 ? 1 : 2, fromX, fromY, piece);
                if (!eatPos.equals(new Pos(-1, -1))) {
                    ret.add(eatPos);
                }
                break;
                
            case 2: // 黑士
            case 9: // 红士
                area = PieceID == 2 ? 2 : 4;
                // 士的移动（斜着走）
                int[][] advisorMoves = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
                for (int[] move : advisorMoves) {
                    int toX = fromX + move[0];
                    int toY = fromY + move[1];
                    if (InArea(toX, toY) == area && !IsSameSide(PieceID, piece[toY][toX])) {
                        ret.add(new Pos(toX, toY));
                    }
                }
                break;
                
            case 3: // 黑象
            case 10: // 红相
                int minArea = PieceID == 3 ? 1 : 3;
                int maxArea = PieceID == 3 ? 2 : 4;
                // 象的移动（田字）
                int[][] elephantMoves = {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
                int[][] elephantLegs = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
                for (int i = 0; i < elephantMoves.length; i++) {
                    int[] move = elephantMoves[i];
                    int[] leg = elephantLegs[i];
                    int toX = fromX + move[0];
                    int toY = fromY + move[1];
                    int legX = fromX + leg[0];
                    int legY = fromY + leg[1];
                    if (InArea(toX, toY) >= minArea && InArea(toX, toY) <= maxArea && 
                        !IsSameSide(PieceID, piece[toY][toX]) && piece[legY][legX] == 0) {
                        ret.add(new Pos(toX, toY));
                    }
                }
                break;
                
            case 4: // 黑马
            case 11: // 红马
                // 马的移动（日字）
                int[][] knightMoves = {{1, 2}, {1, -2}, {-1, 2}, {-1, -2}, {2, 1}, {2, -1}, {-2, 1}, {-2, -1}};
                int[][] knightLegs = {{0, 1}, {0, -1}, {0, 1}, {0, -1}, {1, 0}, {1, 0}, {-1, 0}, {-1, 0}};
                for (int i = 0; i < knightMoves.length; i++) {
                    int[] move = knightMoves[i];
                    int[] leg = knightLegs[i];
                    int toX = fromX + move[0];
                    int toY = fromY + move[1];
                    int legX = fromX + leg[0];
                    int legY = fromY + leg[1];
                    if (toX >= 0 && toX < 9 && toY >= 0 && toY < 10 && 
                        legX >= 0 && legX < 9 && legY >= 0 && legY < 10 && 
                        piece[legY][legX] == 0 && !IsSameSide(PieceID, piece[toY][toX])) {
                        ret.add(new Pos(toX, toY));
                    }
                }
                break;
                
            case 5: // 黑车
            case 12: // 红车
                // 车的移动（直线）
                for (int[] dir : directions) {
                    int x = fromX + dir[0];
                    int y = fromY + dir[1];
                    while (x >= 0 && x < 9 && y >= 0 && y < 10) {
                        if (piece[y][x] == 0) {
                            ret.add(new Pos(x, y));
                        } else {
                            if (!IsSameSide(PieceID, piece[y][x])) {
                                ret.add(new Pos(x, y));
                            }
                            break;
                        }
                        x += dir[0];
                        y += dir[1];
                    }
                }
                break;
                
            case 6: // 黑炮
            case 13: // 红炮
                // 炮的移动（直线，需要炮架）
                for (int[] dir : directions) {
                    int x = fromX + dir[0];
                    int y = fromY + dir[1];
                    int obstacleCount = 0;
                    while (x >= 0 && x < 9 && y >= 0 && y < 10) {
                        if (piece[y][x] == 0) {
                            if (obstacleCount == 0) {
                                ret.add(new Pos(x, y));
                            }
                        } else {
                            obstacleCount++;
                            if (obstacleCount == 1) {
                                // 找到第一个炮架，继续前进寻找吃子目标
                                int nextX = x + dir[0];
                                int nextY = y + dir[1];
                                while (nextX >= 0 && nextX < 9 && nextY >= 0 && nextY < 10) {
                                    if (piece[nextY][nextX] != 0) {
                                        if (!IsSameSide(PieceID, piece[nextY][nextX])) {
                                            ret.add(new Pos(nextX, nextY));
                                        }
                                        break;
                                    }
                                    nextX += dir[0];
                                    nextY += dir[1];
                                }
                            }
                            // 即使遇到障碍物，也要继续检查后面的位置（用于重砲将的情况）
                        }
                        x += dir[0];
                        y += dir[1];
                    }
                }
                break;
                
            case 7: // 黑卒
                if (fromY >= 5) {
                    // 未过河，只能向下移动
                    int toY = fromY - 1;
                    if (toY >= 0 && !IsSameSide(PieceID, piece[toY][fromX])) {
                        ret.add(new Pos(fromX, toY));
                    }
                } else {
                    // 已过河，可以向下和横向移动
                    int[][] pawnMoves = {{0, -1}, {1, 0}, {-1, 0}};
                    for (int[] move : pawnMoves) {
                        int toX = fromX + move[0];
                        int toY = fromY + move[1];
                        if (toX >= 0 && toX < 9 && toY >= 0 && toY < 10 && !IsSameSide(PieceID, piece[toY][toX])) {
                            ret.add(new Pos(toX, toY));
                        }
                    }
                }
                break;
                
            case 14: // 红兵
                if (fromY >= 5) {
                    // 已过河，可以向下和横向移动
                    int[][] pawnMoves = {{0, 1}, {1, 0}, {-1, 0}};
                    for (int[] move : pawnMoves) {
                        int toX = fromX + move[0];
                        int toY = fromY + move[1];
                        if (toX >= 0 && toX < 9 && toY >= 0 && toY < 10 && !IsSameSide(PieceID, piece[toY][toX])) {
                            ret.add(new Pos(toX, toY));
                        }
                    }
                } else {
                    // 未过河，只能向上移动
                    int toY = fromY + 1;
                    if (toY < 10 && !IsSameSide(PieceID, piece[toY][fromX])) {
                        ret.add(new Pos(fromX, toY));
                    }
                }
                break;
        }
        return ret;
    }

    public static boolean isKingDanger(int[][] piece, boolean isRedKing) {
        if (piece == null || piece.length != BOARD_ROWS) {
            return false;
        }
        for (int i = 0; i < BOARD_ROWS; i++) {
            if (piece[i] == null || piece[i].length != BOARD_COLS) {
                return false;
            }
        }

        int kingX = -1, kingY = -1;
        boolean foundKing = false;
        int kingId = isRedKing ? ChessPiece.RED_KING : ChessPiece.BLACK_KING;
        // 只在九宫格范围内搜索王（红方0-2行，黑方7-9行，列3-5）
        int startY = isRedKing ? 0 : 7;
        int endY = isRedKing ? 3 : 10;
        for (int y = startY; y < endY; y++) {
            for (int x = 3; x <= 5; x++) {
                if (piece[y][x] == kingId) {
                    kingX = x;
                    kingY = y;
                    foundKing = true;
                    break;
                }
            }
            if (foundKing) break;
        }
        
        if (!foundKing) {
            return true;
        }
        
        int[][] attackDirections = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        
        // 1. 检查车和炮的直线攻击
        for (int[] dir : attackDirections) {
            int x = kingX + dir[0];
            int y = kingY + dir[1];
            int obstacleCount = 0;

            while (x >= 0 && x < BOARD_COLS && y >= 0 && y < BOARD_ROWS) {
                int pieceId = piece[y][x];
                if (pieceId != ChessPiece.EMPTY) {
                    boolean isEnemy = isRedKing ? ChessPiece.isBlack(pieceId) : ChessPiece.isRed(pieceId);
                    if (isEnemy) {
                        if (pieceId == ChessPiece.BLACK_ROOK || pieceId == ChessPiece.RED_ROOK) {
                            if (obstacleCount == 0) {
                                Log.d(TAG, "将军检测: 车在 (" + x + ", " + y + ") 将军!");
                                return true;
                            }
                        } else if (pieceId == ChessPiece.BLACK_CANNON || pieceId == ChessPiece.RED_CANNON) {
                            if (obstacleCount == 1) {
                                Log.d(TAG, "将军检测: 炮在 (" + x + ", " + y + ") 将军!");
                                return true;
                            }
                        }
                    }
                    obstacleCount++;
                }
                x += dir[0];
                y += dir[1];
            }
        }
        
        // 2. 检查马的攻击（从王出发检查 8 个日字点，避免遍历整个棋盘）
        int[][] knightOffsets = {{1, 2}, {1, -2}, {-1, 2}, {-1, -2},
                                 {2, 1}, {2, -1}, {-2, 1}, {-2, -1}};
        int enemyHorse = isRedKing ? ChessPiece.BLACK_HORSE : ChessPiece.RED_HORSE;
        for (int[] offset : knightOffsets) {
            int hx = kingX + offset[0];
            int hy = kingY + offset[1];
            if (hx >= 0 && hx < BOARD_COLS && hy >= 0 && hy < BOARD_ROWS
                    && piece[hy][hx] == enemyHorse) {
                // 蹩马腿：腿点在马与将之间、紧邻马的一步（沿 2 格长轴方向）
                int legX, legY;
                if (Math.abs(offset[0]) == 2) {
                    legX = hx - offset[0] / 2;
                    legY = hy;
                } else {
                    legX = hx;
                    legY = hy - offset[1] / 2;
                }
                if (piece[legY][legX] == ChessPiece.EMPTY) {
                    Log.d(TAG, "将军检测: 马在 (" + hx + ", " + hy + ") 将军!");
                    return true;
                }
            }
        }
        
        // 3. 检查兵/卒的攻击
        int[][] pawnMoves;
        if (isRedKing) {
            pawnMoves = new int[][]{{0, 1}, {1, 0}, {-1, 0}};
        } else {
            pawnMoves = new int[][]{{0, -1}, {1, 0}, {-1, 0}};
        }
        
        for (int[] move : pawnMoves) {
            int x = kingX + move[0];
            int y = kingY + move[1];
            
            if (x >= 0 && x < BOARD_COLS && y >= 0 && y < BOARD_ROWS) {
                int pieceId = piece[y][x];
                boolean isEnemy = isRedKing ? (pieceId == ChessPiece.BLACK_PAWN) : (pieceId == ChessPiece.RED_PAWN);
                if (isEnemy) {
                    Log.d(TAG, "将军检测: 卒在 (" + x + ", " + y + ") 将军!");
                    return true;
                }
            }
        }
        
        // 4. 检查将/帅的对面攻击（飞将）— 只在对方九宫格的同一列查找
        if (kingX >= 3 && kingX <= 5) {
            int enemyKingId = isRedKing ? ChessPiece.BLACK_KING : ChessPiece.RED_KING;
            int enemyStartY = isRedKing ? 7 : 0;
            int enemyEndY = isRedKing ? 10 : 3;
            boolean foundEnemyKing = false;
            int enemyKingY = -1;
            for (int y = enemyStartY; y < enemyEndY; y++) {
                if (piece[y][kingX] == enemyKingId) {
                    foundEnemyKing = true;
                    enemyKingY = y;
                    break;
                }
            }
            if (foundEnemyKing) {
                boolean pathClear = true;
                int flyStartY = Math.min(kingY, enemyKingY) + 1;
                int flyEndY = Math.max(kingY, enemyKingY);
                for (int y = flyStartY; y < flyEndY; y++) {
                    if (piece[y][kingX] != ChessPiece.EMPTY) {
                        pathClear = false;
                        break;
                    }
                }
                if (pathClear) {
                    Log.d(TAG, "将军检测: 将在 (" + kingX + ", " + enemyKingY + ") 将军!");
                    return true;
                }
            }
        }
        
        // 5. 检查士的攻击
        int[][] advisorMoves = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] move : advisorMoves) {
            int x = kingX + move[0];
            int y = kingY + move[1];
            
            boolean inPalace = isRedKing ?
                (x >= 3 && x <= 5 && y >= 0 && y <= 2) :
                (x >= 3 && x <= 5 && y >= 7 && y <= 9);
            
            if (inPalace) {
                int pieceId = piece[y][x];
                boolean isEnemy = isRedKing ? (pieceId == ChessPiece.BLACK_ADVISOR) : (pieceId == ChessPiece.RED_ADVISOR);
                if (isEnemy) {
                    Log.d(TAG, "将军检测: 士在 (" + x + ", " + y + ") 将军!");
                    return true;
                }
            }
        }
        
        return false;
    }



    public static int InArea(int x, int y) { //0 棋盘外 1 黑盘 2 黑十字 3 红盘 4 红十字
        if (x < 0 || x > 8 || y < 0 || y > 9) {
            return 0;
        }
        return area[y][x];
    }

    public static boolean IsSameSide(int fromID, int toID) {
        if (toID == 0) {
            return false;
        }
        if ((fromID <= 7 && toID <= 7) || (fromID >= 8 && toID >= 8)) {
            return true;
        } else {
            return false;
        }
    }

    public static Pos flyKing(int id, int fromX, int fromY, int[][] piece) {
        // 参数验证
        if (piece == null || piece.length != 10) {
            return new Pos(-1, -1);
        }
        for (int i = 0; i < 10; i++) {
            if (piece[i] == null || piece[i].length != 9) {
                return new Pos(-1, -1);
            }
        }
        
        // 位置验证
        if (fromX < 0 || fromX >= 9 || fromY < 0 || fromY >= 10) {
            return new Pos(-1, -1);
        }
        
        int cnt = 0;
        boolean flag = false;
        int targetY = -1;
        
        if (id == 1) {  //将
            // 将只能在自己的九宫格内
            if (fromY < 7 || fromX < 3 || fromX > 5) {
                return new Pos(-1, -1);
            }
            
            // 向上查找帅
            for (int i = fromY - 1; i >= 0; i--) {
                if (piece[i][fromX] > 0) {
                    if (piece[i][fromX] == 8) {
                        // 找到帅
                        flag = true;
                        targetY = i;
                    }
                    // 不管是不是帅，只要有棋子就停止
                    break;
                }
            }
        } else {       //帅
            // 帅只能在自己的九宫格内
            if (fromY > 2 || fromX < 3 || fromX > 5) {
                return new Pos(-1, -1);
            }
            
            // 向下查找将
            for (int i = fromY + 1; i <= 9; i++) {
                if (piece[i][fromX] > 0) {
                    if (piece[i][fromX] == 1) {
                        // 找到将
                        flag = true;
                        targetY = i;
                    }
                    // 不管是不是将，只要有棋子就停止
                    break;
                }
            }
        }
        
        if (flag && targetY != -1) {
            return new Pos(fromX, targetY);
        } else {
            return new Pos(-1, -1);
        }
    }

    public static boolean CanMove(int id, int fromX, int fromY, int toX, int toY, int[][] piece) {
        // 参数验证
        if (piece == null || piece.length != 10) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (piece[i] == null || piece[i].length != 9) {
                return false;
            }
        }
        
        // 位置验证
        if (fromX < 0 || fromX >= 9 || fromY < 0 || fromY >= 10 || toX < 0 || toX >= 9 || toY < 0 || toY >= 10) {
            return false;
        }
        
        // 检查是否是直线移动
        if (fromX != toX && fromY != toY) {
            return false;
        }
        
        // 检查目标位置是否有己方棋子
        if (IsSameSide(piece[fromY][fromX], piece[toY][toX])) {
            return false;
        }
        
        if (id == 1) {  //车
            // 检查路径上是否有其他棋子
            if (fromX == toX) {
                // 垂直移动
                int start = Math.min(fromY, toY) + 1;
                int end = Math.max(fromY, toY);
                for (int i = start; i < end; i++) {
                    if (piece[i][fromX] != 0) {
                        return false;
                    }
                }
            } else {
                // 水平移动
                int start = Math.min(fromX, toX) + 1;
                int end = Math.max(fromX, toX);
                for (int i = start; i < end; i++) {
                    if (piece[fromY][i] != 0) {
                        return false;
                    }
                }
            }
        } else {   //炮
            int count = 0;
            if (fromX == toX) {
                // 垂直移动
                int start = Math.min(fromY, toY) + 1;
                int end = Math.max(fromY, toY);
                for (int i = start; i < end; i++) {
                    if (piece[i][fromX] != 0) {
                        count++;
                    }
                }
            } else {
                // 水平移动
                int start = Math.min(fromX, toX) + 1;
                int end = Math.max(fromX, toX);
                for (int i = start; i < end; i++) {
                    if (piece[fromY][i] != 0) {
                        count++;
                    }
                }
            }
            
            // 炮的规则：无子移动时需要路径上没有棋子，吃子时有且只有一个炮架
            if (piece[toY][toX] == 0) {
                // 无子移动
                if (count != 0) {
                    return false;
                }
            } else {
                // 吃子
                if (count != 1) {
                    return false;
                }
            }
        }
        return true;
    }
    
    // 检查一个棋子是否能够解将
    public static boolean CanDefendCheck(int[][] piece, int fromX, int fromY, int pieceID) {
        boolean isRed = ChessPiece.isRed(pieceID);
        List<Pos> possibleMoves = PossibleMoves(piece, fromX, fromY, pieceID);

        for (Pos move : possibleMoves) {
            int capturedPiece = piece[move.y][move.x];
            boolean isCaptureKing = capturedPiece == ChessPiece.BLACK_KING
                    || capturedPiece == ChessPiece.RED_KING;
            if (isCaptureKing) {
                return true;
            }

            int[][] tempPiece = copyBoard(piece);
            tempPiece[move.y][move.x] = pieceID;
            tempPiece[fromY][fromX] = ChessPiece.EMPTY;

            if (!isKingDanger(tempPiece, isRed)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isCheckmate(int[][] piece, boolean isRed) {
        if (!isKingDanger(piece, isRed)) {
            return false;
        }
        for (int y = 0; y < BOARD_ROWS; y++) {
            for (int x = 0; x < BOARD_COLS; x++) {
                int pieceID = piece[y][x];
                if (pieceID == ChessPiece.EMPTY) continue;
                if (ChessPiece.isRed(pieceID) != isRed) continue;
                if (CanDefendCheck(piece, x, y, pieceID)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isStalemate(int[][] piece, boolean isRed) {
        if (isKingDanger(piece, isRed)) {
            return false;
        }
        for (int y = 0; y < BOARD_ROWS; y++) {
            for (int x = 0; x < BOARD_COLS; x++) {
                int pieceID = piece[y][x];
                if (pieceID == ChessPiece.EMPTY) continue;
                if (ChessPiece.isRed(pieceID) != isRed) continue;
                List<Pos> possibleMoves = PossibleMoves(piece, x, y, pieceID);
                for (Pos move : possibleMoves) {
                    int[][] tempPiece = copyBoard(piece);
                    tempPiece[move.y][move.x] = pieceID;
                    tempPiece[y][x] = ChessPiece.EMPTY;
                    if (!isKingDanger(tempPiece, isRed)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static boolean isKingFaceToFace(int[][] piece) {
        if (piece == null) {
            return false;
        }
        
        int redKingX = -1, redKingY = -1;
        int blackKingX = -1, blackKingY = -1;
        
        for (int i = 0; i < 10; i++) {
            if (piece[i] == null) {
                continue;
            }
            for (int j = 0; j < 9; j++) {
                if (piece[i][j] == 8) {
                    redKingX = j;
                    redKingY = i;
                } else if (piece[i][j] == 1) {
                    blackKingX = j;
                    blackKingY = i;
                }
            }
        }
        
        if (redKingX == -1 || blackKingX == -1) {
            return false;
        }
        
        if (redKingX != blackKingX) {
            return false;
        }
        
        int start = Math.min(redKingY, blackKingY) + 1;
        int end = Math.max(redKingY, blackKingY);
        for (int i = start; i < end; i++) {
            if (piece[i] == null || piece[i][redKingX] != 0) {
                return false;
            }
        }
        
        return true;
    }

}