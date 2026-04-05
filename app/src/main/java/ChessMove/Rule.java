package ChessMove;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import Info.Pos;

public class Rule {
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
                                break;
                            }
                            if (obstacleCount >= 2) {
                                break;
                            }
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
        // 参数验证
        if (piece == null || piece.length != 10) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (piece[i] == null || piece[i].length != 9) {
                return false;
            }
        }
        
        int kingX = -1, kingY = -1;
        boolean foundKing = false;
        
        // 在整个棋盘上搜索王的位置
        if (isRedKing) {
            // 搜索红帅
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 9; x++) {
                    if (piece[y][x] == 8) { // 红帅
                        kingX = x;
                        kingY = y;
                        foundKing = true;
                        break;
                    }
                }
                if (foundKing) break;
            }
        } else {
            // 搜索黑将
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 9; x++) {
                    if (piece[y][x] == 1) { // 黑将
                        kingX = x;
                        kingY = y;
                        foundKing = true;
                        break;
                    }
                }
                if (foundKing) break;
            }
        }
        
        if (!foundKing) {
            return true; // 王不存在，视为被将军
        }
        
        // 优化：只检查可能攻击到王的对方棋子
        // 定义攻击方向
        int[][] attackDirections = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}; // 车、炮的直线方向
        int[][] knightMoves = {{1, 2}, {1, -2}, {-1, 2}, {-1, -2}, {2, 1}, {2, -1}, {-2, 1}, {-2, -1}}; // 马的走法
        
        // 1. 检查车和炮的直线攻击
        for (int[] dir : attackDirections) {
            int x = kingX + dir[0];
            int y = kingY + dir[1];
            int obstacleCount = 0;
            
            while (x >= 0 && x < 9 && y >= 0 && y < 10) {
                int pieceId = piece[y][x];
                if (pieceId != 0) {
                    // 检查是否是对方的车或炮
                    boolean isEnemy = isRedKing ? (pieceId >= 1 && pieceId <= 7) : (pieceId >= 8 && pieceId <= 14);
                    if (isEnemy) {
                        if (pieceId == 5 || pieceId == 12) { // 车
                            // 车需要在直线上没有障碍物才能攻击到王
                            if (obstacleCount == 0) {
                                return true;
                            }
                        } else if (pieceId == 6 || pieceId == 13) { // 炮
                            // 炮需要有一个炮架才能攻击
                            if (obstacleCount == 1) {
                                return true;
                            }
                        }
                        break;
                    } else {
                        obstacleCount++;
                    }
                }
                x += dir[0];
                y += dir[1];
            }
        }
        
        // 2. 检查马的攻击
        // 遍历整个棋盘，寻找对方的马
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                int pieceId = piece[y][x];
                boolean isEnemy = isRedKing ? (pieceId == 4) : (pieceId == 11);
                if (isEnemy) {
                    // 检查马是否能攻击到王
                    int dx = kingX - x;
                    int dy = kingY - y;
                    // 马的攻击模式是日字，即 (±2, ±1) 或 (±1, ±2)
                    if ((Math.abs(dx) == 2 && Math.abs(dy) == 1) || (Math.abs(dx) == 1 && Math.abs(dy) == 2)) {
                        // 检查马腿
                        int legX = x + dx / 2;
                        int legY = y + dy / 2;
                        if (legX >= 0 && legX < 9 && legY >= 0 && legY < 10 && piece[legY][legX] == 0) {
                            return true;
                        }
                    }
                }
            }
        }
        
        // 3. 检查兵/卒的攻击
        int[][] pawnMoves;
        if (isRedKing) {
            pawnMoves = new int[][]{{0, 1}, {1, 0}, {-1, 0}}; // 黑卒的可能攻击方向
        } else {
            pawnMoves = new int[][]{{0, -1}, {1, 0}, {-1, 0}}; // 红兵的可能攻击方向
        }
        
        for (int[] move : pawnMoves) {
            int x = kingX + move[0];
            int y = kingY + move[1];
            
            if (x >= 0 && x < 9 && y >= 0 && y < 10) {
                int pieceId = piece[y][x];
                boolean isEnemy = isRedKing ? (pieceId == 7) : (pieceId == 14);
                if (isEnemy) {
                    return true;
                }
            }
        }
        
        // 4. 检查将/帅的对面攻击
        if (kingX >= 0 && kingX < 9) {
            // 搜索对方的王
            int enemyKingId = isRedKing ? 1 : 8;
            int enemyKingX = -1;
            int enemyKingY = -1;
            boolean foundEnemyKing = false;
            
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 9; x++) {
                    if (piece[y][x] == enemyKingId) {
                        enemyKingX = x;
                        enemyKingY = y;
                        foundEnemyKing = true;
                        break;
                    }
                }
                if (foundEnemyKing) break;
            }
            
            // 如果找到对方的王，检查是否在同一条直线上且中间没有其他棋子
            if (foundEnemyKing && enemyKingX == kingX) {
                boolean pathClear = true;
                int startY = Math.min(kingY, enemyKingY) + 1;
                int endY = Math.max(kingY, enemyKingY);
                
                for (int y = startY; y < endY; y++) {
                    if (piece[y][kingX] != 0) {
                        pathClear = false;
                        break;
                    }
                }
                
                if (pathClear) {
                    return true;
                }
            }
        }
        
        // 5. 检查士的攻击（士只能在九宫格内斜着走）
        int[][] advisorMoves = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] move : advisorMoves) {
            int x = kingX + move[0];
            int y = kingY + move[1];
            
            // 检查是否在九宫格内
            boolean inPalace = isRedKing ? 
                (x >= 3 && x <= 5 && y >= 0 && y <= 2) : 
                (x >= 3 && x <= 5 && y >= 7 && y <= 9);
            
            if (inPalace) {
                int pieceId = piece[y][x];
                boolean isEnemy = isRedKing ? (pieceId == 2) : (pieceId == 9);
                if (isEnemy) {
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
        // 获取当前玩家颜色
        boolean isRed = pieceID >= 8 && pieceID <= 14;
        
        // 获取所有可能的移动位置
        List<Pos> possibleMoves = PossibleMoves(piece, fromX, fromY, pieceID);
        
        // 检查是否有任何移动可以解将
        for (Pos move : possibleMoves) {
            // 创建棋盘的临时副本
            int[][] tempPiece = new int[10][9];
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    tempPiece[i][j] = piece[i][j];
                }
            }
            
            // 检查是否吃掉了对方的老将
            int capturedPiece = piece[move.y][move.x];
            boolean isCaptureKing = capturedPiece == 1 || capturedPiece == 8;
            
            // 执行移动
            tempPiece[move.y][move.x] = pieceID;
            tempPiece[fromY][fromX] = 0;
            
            // 如果吃掉了对方老将，这个移动可以解将（游戏结束）
            if (isCaptureKing) {
                return true;
            }
            
            // 检查移动后是否还被将军
            if (!isKingDanger(tempPiece, isRed)) {
                return true;
            }
        }
        
        return false;
    }


}