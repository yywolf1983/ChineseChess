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
        List<Pos> ret = new ArrayList<Pos>();
        
        // 参数验证
        if (piece == null || piece.length != 10) {
            return ret;
        }
        for (int i = 0; i < 10; i++) {
            if (piece[i] == null || piece[i].length != 9) {
                return ret;
            }
        }
        
        // 位置验证
        if (fromX < 0 || fromX >= 9 || fromY < 0 || fromY >= 10) {
            return ret;
        }
        
        int num;
        switch (PieceID) {
            case 1://黑将
                num = 0;
                for (int i = 0; i < offsetX[num].length; i++) {
                    int toX = fromX + offsetX[num][i];
                    int toY = fromY + offsetY[num][i];
                    if (InArea(toX, toY) == 2 && IsSameSide(PieceID, piece[toY][toX]) == false) {
                        ret.add(new Pos(toX, toY));
                    }
                }
                Pos eatPos1 = flyKing(1, fromX, fromY, piece);
                if (eatPos1.equals(new Pos(-1, -1)) == false) {
                    ret.add(eatPos1);
                }
                break;
            case 2://黑士
                num = 1;
                for (int i = 0; i < offsetX[num].length; i++) {
                    int toX = fromX + offsetX[num][i];
                    int toY = fromY + offsetY[num][i];
                    if (InArea(toX, toY) == 2 && IsSameSide(PieceID, piece[toY][toX]) == false) {
                        ret.add(new Pos(toX, toY));
                    }
                }
                break;
            case 3://黑象
                num = 2;
                for (int i = 0; i < offsetX[num].length; i++) {
                    int toX = fromX + offsetX[num][i];
                    int toY = fromY + offsetY[num][i];
                    int blockX = fromX + offsetX[num + 1][i];
                    int blockY = fromY + offsetY[num + 1][i];
                    if (InArea(toX, toY) >= 1 && InArea(toX, toY) <= 2 && IsSameSide(PieceID, piece[toY][toX]) == false && piece[blockY][blockX] == 0) {
                        ret.add(new Pos(toX, toY));
                    }
                }
                break;
            case 4://黑马
            case 11://红马
                num = 4;
                for (int i = 0; i < offsetX[num].length; i++) {
                    int toX = fromX + offsetX[num][i];
                    int toY = fromY + offsetY[num][i];
                    int blockX = fromX + offsetX[num + 1][i];
                    int blockY = fromY + offsetY[num + 1][i];
                    // 检查目标位置是否在棋盘内
                    if (toX >= 0 && toX < 9 && toY >= 0 && toY < 10) {
                        // 检查是否蹩马腿
                        if (blockX >= 0 && blockX < 9 && blockY >= 0 && blockY < 10 && piece[blockY][blockX] == 0) {
                            // 检查目标位置是否有己方棋子
                            if (!IsSameSide(PieceID, piece[toY][toX])) {
                                ret.add(new Pos(toX, toY));
                            }
                        }
                    }
                }
                break;
            case 5://黑车
            case 12: //红车
                for (int i = fromY + 1; i < 10; i++) {//向下走
                    if (CanMove(1, fromX, fromY, fromX, i, piece)) { //可以走时
                        ret.add(new Pos(fromX, i));
                    } else {//不可以走时直接 break
                        break;
                    }
                }
                for (int i = fromY - 1; i > -1; i--) {//向上走
                    if (CanMove(1, fromX, fromY, fromX, i, piece)) {//可以走时
                        ret.add(new Pos(fromX, i));
                    } else {//不可以走时
                        break;
                    }
                }
                for (int j = fromX - 1; j > -1; j--) {//向走走
                    if (CanMove(1, fromX, fromY, j, fromY, piece)) {//可以走时
                        ret.add(new Pos(j, fromY));
                    } else {//不可以走时
                        break;
                    }
                }
                for (int j = fromX + 1; j < 9; j++) {//向右走
                    if (CanMove(1, fromX, fromY, j, fromY, piece)) {//可以走时
                        ret.add(new Pos(j, fromY));
                    } else {//不可以走时
                        break;
                    }
                }
                break;
            case 6://黑炮
            case 13://红炮
                for (int i = fromY + 1; i < 10; i++) {//向下走
                    if (CanMove(2, fromX, fromY, fromX, i, piece)) { //可以走时
                        ret.add(new Pos(fromX, i));
                    }
                }
                for (int i = fromY - 1; i > -1; i--) {//向上走
                    if (CanMove(2, fromX, fromY, fromX, i, piece)) {//可以走时
                        ret.add(new Pos(fromX, i));
                    }
                }
                for (int j = fromX - 1; j > -1; j--) {//向走走
                    if (CanMove(2, fromX, fromY, j, fromY, piece)) {//可以走时
                        ret.add(new Pos(j, fromY));
                    }
                }
                for (int j = fromX + 1; j < 9; j++) {//向右走
                    if (CanMove(2, fromX, fromY, j, fromY, piece)) {//可以走时
                        ret.add(new Pos(j, fromY));
                    }
                }
                break;
            case 7://黑卒
                // 黑卒未过河（在己方区域，整体反转后在上方，y值较大）
                if (fromY >= 5) {
                    // 黑卒未过河，只能向下移动（向红方方向，y值减小）
                    num = 6;
                    for (int i = 0; i < offsetX[num].length; i++) {
                        int toX = fromX + offsetX[num][i];
                        int toY = fromY + offsetY[num][i];
                        // 检查目标位置是否在棋盘内
                        if (toX >= 0 && toX < 9 && toY >= 0 && toY < 10) {
                            // 检查目标位置是否有己方棋子
                            if (!IsSameSide(PieceID, piece[toY][toX])) {
                                ret.add(new Pos(toX, toY));
                            }
                        }
                    }
                } else {
                    // 黑卒已过河，可以向下和横向移动
                    num = 7;
                    for (int i = 0; i < offsetX[num].length; i++) {
                        int toX = fromX + offsetX[num][i];
                        int toY = fromY + offsetY[num][i];
                        // 检查目标位置是否在棋盘内
                        if (toX >= 0 && toX < 9 && toY >= 0 && toY < 10) {
                            // 检查目标位置是否有己方棋子
                            if (!IsSameSide(PieceID, piece[toY][toX])) {
                                ret.add(new Pos(toX, toY));
                            }
                        }
                    }
                }
                break;
            case 8://红帅
                num = 0;
                for (int i = 0; i < offsetX[num].length; i++) {
                    int toX = fromX + offsetX[num][i];
                    int toY = fromY + offsetY[num][i];
                    if (InArea(toX, toY) == 4 && IsSameSide(PieceID, piece[toY][toX]) == false) {
                        ret.add(new Pos(toX, toY));
                    }
                }
                Pos eatPos2 = flyKing(2, fromX, fromY, piece);
                if (eatPos2.equals(new Pos(-1, -1)) == false) {
                    ret.add(eatPos2);
                }
                break;
            case 9://红士
                num = 1;
                for (int i = 0; i < offsetX[num].length; i++) {
                    int toX = fromX + offsetX[num][i];
                    int toY = fromY + offsetY[num][i];
                    if (InArea(toX, toY) == 4 && IsSameSide(PieceID, piece[toY][toX]) == false) {
                        ret.add(new Pos(toX, toY));
                    }
                }
                break;
            case 10://红象
                num = 2;
                for (int i = 0; i < offsetX[num].length; i++) {
                    int toX = fromX + offsetX[num][i];
                    int toY = fromY + offsetY[num][i];
                    int blockX = fromX + offsetX[num + 1][i];
                    int blockY = fromY + offsetY[num + 1][i];
                    if (InArea(toX, toY) >= 3 && InArea(toX, toY) <= 4 && IsSameSide(PieceID, piece[toY][toX]) == false && piece[blockY][blockX] == 0) {
                        ret.add(new Pos(toX, toY));
                    }
                }
                break;
            case 14://红兵
                // 红兵未过河（在己方区域，整体反转后在上方）
                if (fromY >= 5) {
                    // 红兵已过河
                    num = 9;
                    for (int i = 0; i < offsetX[num].length; i++) {
                        int toX = fromX + offsetX[num][i];
                        int toY = fromY + offsetY[num][i];
                        // 检查目标位置是否在棋盘内
                        if (toX >= 0 && toX < 9 && toY >= 0 && toY < 10) {
                            // 检查目标位置是否有己方棋子
                            if (!IsSameSide(PieceID, piece[toY][toX])) {
                                ret.add(new Pos(toX, toY));
                            }
                        }
                    }
                } else {
                    // 红兵未过河
                    num = 8;
                    for (int i = 0; i < offsetX[num].length; i++) {
                        int toX = fromX + offsetX[num][i];
                        int toY = fromY + offsetY[num][i];
                        // 检查目标位置是否在棋盘内
                        if (toX >= 0 && toX < 9 && toY >= 0 && toY < 10) {
                            // 检查目标位置是否有己方棋子
                            if (!IsSameSide(PieceID, piece[toY][toX])) {
                                ret.add(new Pos(toX, toY));
                            }
                        }
                    }
                }
                break;
            default:
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
        
        // 找到王的位置
        if (isRedKing) {
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
        
        // 检查所有对方棋子的攻击
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                int pieceId = piece[y][x];
                if (pieceId == 0) continue;
                
                // 检查是否是对方的棋子
                boolean isEnemy = isRedKing ? (pieceId >= 1 && pieceId <= 7) : (pieceId >= 8 && pieceId <= 14);
                if (!isEnemy) continue;
                
                // 检查该棋子是否能攻击到王
                List<Pos> moves = PossibleMoves(piece, x, y, pieceId);
                if (moves != null) {
                    for (Pos move : moves) {
                        if (move.x == kingX && move.y == kingY) {
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }

    public static boolean isDead(int[][] piece, boolean isRedKing) {
        // 参数验证
        if (piece == null || piece.length != 10) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (piece[i] == null || piece[i].length != 9) {
                return false;
            }
        }
        
        if (isRedKing == true) {
            for (int i = 0; i <= 9; i++) {
                for (int j = 0; j <= 8; j++) {
                    if (piece[i][j] >= 8 && piece[i][j] <= 14) {
                        List<Pos> moves = PossibleMoves(piece, j, i, piece[i][j]);
                        Iterator<Pos> it = moves.iterator();
                        while (it.hasNext()) {
                            Pos pos = it.next();
                            int tmp = piece[pos.y][pos.x];
                            if (tmp == 1) {
                                return false;
                            }
                            piece[pos.y][pos.x] = piece[i][j];
                            piece[i][j] = 0;
                            if (isKingDanger(piece, true) == false) {
                                piece[i][j] = piece[pos.y][pos.x];
                                piece[pos.y][pos.x] = tmp;
                                return false;
                            }
                            piece[i][j] = piece[pos.y][pos.x];
                            piece[pos.y][pos.x] = tmp;
                        }
                    }
                }
            }
        } else {
            for (int i = 0; i <= 9; i++) {
                for (int j = 0; j <= 8; j++) {
                    if (piece[i][j] >= 1 && piece[i][j] <= 7) {
                        List<Pos> moves = PossibleMoves(piece, j, i, piece[i][j]);
                        Iterator<Pos> it = moves.iterator();
                        while (it.hasNext()) {
                            Pos pos = it.next();
                            int tmp = piece[pos.y][pos.x];
                            if (tmp == 8) {
                                return false;
                            }
                            piece[pos.y][pos.x] = piece[i][j];
                            piece[i][j] = 0;
                            if (isKingDanger(piece, false) == false) {
                                piece[i][j] = piece[pos.y][pos.x];
                                piece[pos.y][pos.x] = tmp;
                                return false;
                            }
                            piece[i][j] = piece[pos.y][pos.x];
                            piece[pos.y][pos.x] = tmp;
                        }
                    }
                }
            }
        }
        return true;
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


}