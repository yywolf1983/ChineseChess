package top.nones.chessgame;

import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import ChessMove.Rule;
import CustomView.ChessView;
import Info.ChessInfo;
import Info.InfoSet;
import Info.Pos;
import Utils.LogUtils;

public class PvPActivityGame {
    private PvPActivity activity;
    private ChessInfo chessInfo;
    private InfoSet infoSet;
    private ChessView chessView;
    private PvPActivityRound roundView;
    
    // 摆棋模式下选中的棋子ID
    private int selectedPieceID = 0;
    // 摆棋模式下选中的棋盘上的棋子位置
    private int[] selectedBoardPiecePos = {-1, -1};

    public PvPActivityGame(PvPActivity activity, ChessInfo chessInfo, InfoSet infoSet, ChessView chessView) {
        this.activity = activity;
        this.chessInfo = chessInfo;
        this.infoSet = infoSet;
        this.chessView = chessView;
    }

    public void setRoundView(PvPActivityRound roundView) {
        this.roundView = roundView;
    }

    public boolean onTouch(View view, MotionEvent event) {
        long lastClickTime = System.currentTimeMillis();
        if (lastClickTime - PvPActivityInit.getCurClickTime() < PvPActivityInit.getMinClickDelayTime()) {
            return false;
        }
        PvPActivityInit.setCurClickTime(lastClickTime);
        PvPActivityInit.setLastClickTime(lastClickTime);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            if (chessInfo != null && chessInfo.status == 1 && chessView != null) {
                // 摆棋模式处理
                if (chessInfo.IsSetupMode) {
                    handleSetupModeTouch(x, y, event);
                } 
                // 正常游戏模式处理
                else {
                    handleNormalModeTouch(x, y, event);
                }
            }
        }
        return false;
    }

    private void handleSetupModeTouch(float x, float y, MotionEvent event) {
        if (x >= 0 && x <= chessView.Board_width && y >= 0 && y <= chessView.Board_height) {
            chessInfo.Select = getPos(event);
            if (chessInfo.Select != null) {
                int i = chessInfo.Select[0], j = chessInfo.Select[1];
                if (i >= 0 && i <= 8 && j >= 0 && j <= 9 && chessInfo.piece != null) {
                    // 获取点击位置的棋子ID
                    int boardPieceID = chessInfo.piece[j][i];
                    
                    // 如果已经选中了棋盘上的棋子
                    if (selectedBoardPiecePos[0] != -1 && selectedBoardPiecePos[1] != -1) {
                        // 点击的是空白区域
                        if (boardPieceID == 0) {
                            handleMoveSelectedPiece(i, j);
                        }
                    }
                    // 如果已经选中了棋子选择区域的棋子，放置到棋盘上
                    else if (selectedPieceID > 0) {
                        placePiece(i, j, selectedPieceID);
                        // 重置选中状态
                        selectedPieceID = 0;
                    }
                    // 如果点击的是棋盘上的棋子，选中该棋子
                    else if (boardPieceID > 0) {
                        selectedBoardPiecePos[0] = i;
                        selectedBoardPiecePos[1] = j;
                        // 显示选中效果
                        chessInfo.Select = new int[]{i, j};
                        chessView.requestDraw();
                        Toast.makeText(activity, "已选中棋盘上的棋子，点击空白区域移动或下架", Toast.LENGTH_SHORT).show();
                    }
                    // 点击空白区域，重置选中状态
                    else {
                        selectedBoardPiecePos[0] = -1;
                        selectedBoardPiecePos[1] = -1;
                        chessInfo.Select = new int[]{-1, -1};
                        chessView.requestDraw();
                    }
                }
            }
        }
    }

    private void handleMoveSelectedPiece(int i, int j) {
        // 获取要操作的棋子ID
        int pieceToOperate = chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]];
        // 检查是否是老将
        if (pieceToOperate == 1 || pieceToOperate == 8) {
            // 老将不能下架，但可以移动到合法位置
            // 检查新位置是否合理
            if (isValidPiecePosition(pieceToOperate, i, j)) {
                // 先将原位置设为0
                chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]] = 0;
                // 再将新位置设为棋子ID
                placePiece(i, j, pieceToOperate);
                // 重置选中状态
                selectedBoardPiecePos[0] = -1;
                selectedBoardPiecePos[1] = -1;
            } else {
                Toast.makeText(activity, "该位置不适合放置老将", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 不是老将，可以下架或移动
            // 检查是否是点击原位置（下架）还是点击新位置（移动）
            if (i == selectedBoardPiecePos[0] && j == selectedBoardPiecePos[1]) {
                // 点击原位置，下架棋子
                if (pieceToOperate != 1 && pieceToOperate != 8) { // 老将不能下架
                    placePiece(selectedBoardPiecePos[0], selectedBoardPiecePos[1], 0);
                    // 重置选中状态
                    selectedBoardPiecePos[0] = -1;
                    selectedBoardPiecePos[1] = -1;
                } else {
                    Toast.makeText(activity, "老将不能下架", Toast.LENGTH_SHORT).show();
                }
            } else {
                // 点击新位置，移动棋子
                // 检查新位置是否合理
                if (isValidPiecePosition(pieceToOperate, i, j)) {
                    // 先将原位置设为0
                    chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]] = 0;
                    // 再将新位置设为棋子ID
                    placePiece(i, j, pieceToOperate);
                    // 重置选中状态
                    selectedBoardPiecePos[0] = -1;
                    selectedBoardPiecePos[1] = -1;
                } else {
                    Toast.makeText(activity, "该位置不适合放置此棋子", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void handleNormalModeTouch(float x, float y, MotionEvent event) {
        if (x >= 0 && x <= chessView.Board_width && y >= 0 && y <= chessView.Board_height) {
            chessInfo.Select = getPos(event);
            if (chessInfo.Select != null) {
                int i = chessInfo.Select[0], j = chessInfo.Select[1];
                if (i >= 0 && i <= 8 && j >= 0 && j <= 9 && chessInfo.piece != null) {
                    if (chessInfo.IsRedGo == true) {
                        handleRedMove(i, j);
                    } else {
                        handleBlackMove(i, j);
                    }
                }
            }
        }
    }

    private void handleRedMove(int i, int j) {
        if (chessInfo.IsChecked == false) {
            if (chessInfo.piece[j][i] >= 8 && chessInfo.piece[j][i] <= 14) {
                chessInfo.prePos = new Pos(i, j);
                chessInfo.IsChecked = true;
                chessInfo.ret = Rule.PossibleMoves(chessInfo.piece, i, j, chessInfo.piece[j][i]);
                if (PvPActivityInit.getSelectMusic() != null) {
                    PvPActivityInit.playEffect(PvPActivityInit.getSelectMusic());
                }
            }
        } else {
            if (chessInfo.piece[j][i] >= 8 && chessInfo.piece[j][i] <= 14) {
                chessInfo.prePos = new Pos(i, j);
                chessInfo.ret = Rule.PossibleMoves(chessInfo.piece, i, j, chessInfo.piece[j][i]);
                if (PvPActivityInit.getSelectMusic() != null) {
                    PvPActivityInit.playEffect(PvPActivityInit.getSelectMusic());
                }
            } else if (chessInfo.ret != null && chessInfo.ret.contains(new Pos(i, j))) {
                executeMove(i, j, true);
            }
        }
    }

    private void handleBlackMove(int i, int j) {
        if (chessInfo.IsChecked == false) {
            if (chessInfo.piece[j][i] >= 1 && chessInfo.piece[j][i] <= 7) {
                chessInfo.prePos = new Pos(i, j);
                chessInfo.IsChecked = true;
                chessInfo.ret = Rule.PossibleMoves(chessInfo.piece, i, j, chessInfo.piece[j][i]);
                if (PvPActivityInit.getSelectMusic() != null) {
                    PvPActivityInit.playEffect(PvPActivityInit.getSelectMusic());
                }
            }
        } else {
            if (chessInfo.piece[j][i] >= 1 && chessInfo.piece[j][i] <= 7) {
                chessInfo.prePos = new Pos(i, j);
                chessInfo.ret = Rule.PossibleMoves(chessInfo.piece, i, j, chessInfo.piece[j][i]);
                if (PvPActivityInit.getSelectMusic() != null) {
                    PvPActivityInit.playEffect(PvPActivityInit.getSelectMusic());
                }
            } else if (chessInfo.ret != null && chessInfo.ret.contains(new Pos(i, j))) {
                executeMove(i, j, false);
            }
        }
    }

    private void executeMove(int i, int j, boolean isRed) {
        int tmp = chessInfo.piece[j][i];
        chessInfo.piece[j][i] = chessInfo.piece[chessInfo.prePos.y][chessInfo.prePos.x];
        chessInfo.piece[chessInfo.prePos.y][chessInfo.prePos.x] = 0;
        
        if (Rule.isKingDanger(chessInfo.piece, isRed)) {
            chessInfo.piece[chessInfo.prePos.y][chessInfo.prePos.x] = chessInfo.piece[j][i];
            chessInfo.piece[j][i] = tmp;
            Toast.makeText(activity, isRed ? "帅被将军" : "将被将军", Toast.LENGTH_SHORT).show();
        } else {
            chessInfo.IsChecked = false;
            chessInfo.IsRedGo = !isRed;
            chessInfo.curPos = new Pos(i, j);

            // 生成并记录标准象棋记谱走法
            int piece = chessInfo.piece[j][i];
            String moveString = generateMoveString(piece, chessInfo.prePos, chessInfo.curPos, isRed);
            if (moveString != null) {
                LogUtils.i("Move", (isRed ? "红方" : "黑方") + "走棋: " + moveString);
            }

            chessInfo.updateAllInfo(chessInfo.prePos, chessInfo.curPos, chessInfo.piece[j][i], tmp);

            try {
                if (infoSet != null) {
                    infoSet.pushInfo(chessInfo);
                }
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }

            if (PvPActivityInit.getClickMusic() != null) {
                PvPActivityInit.playEffect(PvPActivityInit.getClickMusic());
            }

            checkGameStatus(!isRed);

            if (chessView != null) {
                chessView.requestDraw();
            }
            if (roundView != null) {
                roundView.requestDraw();
            }
        }
    }

    private void checkGameStatus(boolean isRed) {
        int key = 0;
        if (Rule.isKingDanger(chessInfo.piece, !isRed)) {
            key = 1;
        }
        if (Rule.isDead(chessInfo.piece, !isRed)) {
            key = 2;
        }
        if (key == 1) {
            if (PvPActivityInit.getCheckMusic() != null) {
                PvPActivityInit.playEffect(PvPActivityInit.getCheckMusic());
            }
            Toast.makeText(activity, "将军", Toast.LENGTH_SHORT).show();
        } else if (key == 2) {
            if (PvPActivityInit.getWinMusic() != null) {
                PvPActivityInit.playEffect(PvPActivityInit.getWinMusic());
            }
            chessInfo.status = 2;
            Toast.makeText(activity, isRed ? "红方获得胜利" : "黑方获得胜利", Toast.LENGTH_SHORT).show();
        }

        if (chessInfo.status == 1) {
            if (chessInfo.peaceRound >= 60) {
                chessInfo.status = 2;
                Toast.makeText(activity, "双方60回合内未吃子，此乃和棋", Toast.LENGTH_SHORT).show();
            } else if (chessInfo.attackNum_B == 0 && chessInfo.attackNum_R == 0) {
                chessInfo.status = 2;
                Toast.makeText(activity, "双方都无攻击性棋子，此乃和棋", Toast.LENGTH_SHORT).show();
            } else if (infoSet != null && infoSet.ZobristInfo != null && infoSet.ZobristInfo.get(chessInfo.ZobristKeyCheck) >= 4) {
                chessInfo.status = 2;
                Toast.makeText(activity, "重复局面出现4次，此乃和棋", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public int[] getPos(MotionEvent e) {
        int[] pos = new int[2];
        double x = e.getX();
        double y = e.getY();
        int[] dis = new int[]{
                chessView.Scale(3), chessView.Scale(41), chessView.Scale(80), chessView.Scale(85)
        };
        x = x - dis[0];
        y = y - dis[1];
        if (x % dis[3] <= dis[2] && y % dis[3] <= dis[2]) {
            pos[0] = (int) Math.floor(x / dis[3]);
            pos[1] = (int) Math.floor(y / dis[3]);
            // 反转y坐标，与ChessView中的显示逻辑一致
            pos[1] = 9 - pos[1];
            if (pos[0] >= 9 || pos[1] >= 10 || pos[1] < 0) {
                pos[0] = pos[1] = -1;
            }
        } else {
            pos[0] = pos[1] = -1;
        }
        return pos;
    }

    // 放置棋子
    public void placePiece(int x, int y, int pieceID) {
        if (chessInfo != null && x >= 0 && x < 9 && y >= 0 && y < 10) {
            // 检查棋子数量限制
            if (!checkPieceCount(pieceID)) {
                Toast.makeText(activity, "棋子数量已达到上限", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 检查位置合理性
            if (!isValidPiecePosition(pieceID, x, y)) {
                Toast.makeText(activity, "该位置不适合放置此棋子", Toast.LENGTH_SHORT).show();
                return;
            }
            
            chessInfo.piece[y][x] = pieceID;
            // 重新计算攻击棋子数量
            chessInfo.attackNum_B = 0;
            chessInfo.attackNum_R = 0;
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    int piece = chessInfo.piece[i][j];
                    if (piece != 0) {
                        // 黑方攻击棋子：车(5)、马(4)、炮(6)、卒(7)
                        if (piece == 4 || piece == 5 || piece == 6 || piece == 7) {
                            chessInfo.attackNum_B++;
                        }
                        // 红方攻击棋子：车(12)、马(11)、炮(13)、兵(14)
                        else if (piece == 11 || piece == 12 || piece == 13 || piece == 14) {
                            chessInfo.attackNum_R++;
                        }
                    }
                }
            }
            // 重新绘制界面
            if (chessView != null) {
                chessView.requestDraw();
            }
        }
    }
    
    // 检查棋子位置是否合理
    public boolean isValidPiecePosition(int pieceID, int x, int y) {
        // 检查坐标是否在棋盘范围内
        if (x < 0 || x >= 9 || y < 0 || y >= 10) {
            return false;
        }
        
        // 摆棋模式下的位置限制
        if (chessInfo != null && chessInfo.IsSetupMode) {
            switch (pieceID) {
                case 1: // 黑将
                case 8: // 红帅
                    // 将帅只能在九宫格内
                    if (pieceID == 1) { // 黑将
                        // 黑将九宫格：x: 3-5, y: 7-9（因为坐标已经反转）
                        return x >= 3 && x <= 5 && y >= 7 && y <= 9;
                    } else { // 红帅
                        // 红帅九宫格：x: 3-5, y: 0-2（因为坐标已经反转）
                        return x >= 3 && x <= 5 && y >= 0 && y <= 2;
                    }
                case 2: // 黑士
                case 9: // 红士
                    // 士只能在九宫格内且走斜线位置
                    if (pieceID == 2) { // 黑士
                        // 黑士九宫格：x: 3-5, y: 7-9（因为坐标已经反转）
                        return (x >= 3 && x <= 5 && y >= 7 && y <= 9) && 
                               ((x == 3 && (y == 7 || y == 9)) || (x == 4 && y == 8) || (x == 5 && (y == 7 || y == 9)));
                    } else { // 红士
                        // 红士九宫格：x: 3-5, y: 0-2（因为坐标已经反转）
                        return (x >= 3 && x <= 5 && y >= 0 && y <= 2) && 
                               ((x == 3 && (y == 0 || y == 2)) || (x == 4 && y == 1) || (x == 5 && (y == 0 || y == 2)));
                    }
                case 3: // 黑象
                case 10: // 红相
                    // 相只能在己方半场
                    if (pieceID == 3) { // 黑象
                        // 黑象位置：在己方半场（因为坐标已经反转）
                        return y >= 5 && y <= 9;
                    } else { // 红相
                        // 红相位置：在己方半场（因为坐标已经反转）
                        return y >= 0 && y <= 4;
                    }
                case 7: // 黑卒
                    // 摆棋模式下黑卒可以自由摆放
                    return true;
                case 14: // 红兵
                    // 摆棋模式下红兵可以自由摆放
                    return true;
                case 4: // 黑马
                case 11: // 红马
                    // 马可以自由摆放
                    return true;
                case 5: // 黑车
                case 12: // 红车
                    // 车可以自由摆放
                    return true;
                case 6: // 黑炮
                case 13: // 红炮
                    // 炮可以自由摆放
                    return true;
                default:
                    // 其他棋子默认可以自由摆放
                    return true;
            }
        }
        
        // 正常游戏模式下的位置限制
        switch (pieceID) {
            case 1: // 黑将
                // 黑将只能在九宫格内
                return x >= 3 && x <= 5 && y >= 7 && y <= 9;
            case 8: // 红帅
                // 红帅只能在九宫格内
                return x >= 3 && x <= 5 && y >= 0 && y <= 2;
            case 2: // 黑士
                // 黑士只能在九宫格内
                return x >= 3 && x <= 5 && y >= 7 && y <= 9;
            case 9: // 红士
                // 红士只能在九宫格内
                return x >= 3 && x <= 5 && y >= 0 && y <= 2;
            case 3: // 黑象
                // 黑象只能在己方半场
                return y >= 5 && y <= 9;
            case 10: // 红相
                // 红相只能在己方半场
                return y >= 0 && y <= 4;
            default:
                // 其他棋子可以在任何位置
                return true;
        }
    }
    
    // 检查棋子数量是否符合标准
    public boolean checkPieceCount(int pieceID) {
        if (pieceID == 0) return true; // 移除棋子总是允许的
        
        int count = 0;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (chessInfo.piece[i][j] == pieceID) {
                    count++;
                }
            }
        }
        
        // 标准中国象棋棋子数量限制
        switch (pieceID) {
            case 1: // 黑将
            case 8: // 红帅
                return count < 1;
            case 2: // 黑士
            case 3: // 黑象
            case 4: // 黑马
            case 5: // 黑车
            case 6: // 黑炮
            case 9: // 红士
            case 10: // 红相
            case 11: // 红马
            case 12: // 红车
            case 13: // 红炮
                return count < 2;
            case 7: // 黑卒
            case 14: // 红兵
                return count < 5;
            default:
                return true;
        }
    }

    // 生成标准象棋记谱走法
    public String generateMoveString(int pieceType, Info.Pos fromPos, Info.Pos toPos, boolean isRed) {
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
        boolean isSameColumn = false;
        boolean isSameRow = false;
        java.util.List<Info.Pos> samePieces = new java.util.ArrayList<>();
        
        // 收集同一列的相同棋子
        if (chessInfo != null && chessInfo.piece != null) {
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 9; x++) {
                    if (x == fromPos.x && chessInfo.piece[y][x] == pieceType) {
                        samePieces.add(new Info.Pos(x, y));
                    }
                }
            }
        }
        
        // 如果同一列有多个相同的棋子，添加前缀
        if (samePieces.size() > 1) {
            isSameColumn = true;
            // 对棋子按y坐标排序（兼容API 16）
            for (int i = 0; i < samePieces.size() - 1; i++) {
                for (int j = 0; j < samePieces.size() - i - 1; j++) {
                    Info.Pos p1 = samePieces.get(j);
                    Info.Pos p2 = samePieces.get(j + 1);
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
                int index = samePieces.indexOf(new Info.Pos(fromPos.x, fromPos.y)) + 1;
                prefix = getColChar(index);
            } else {
                // 其他棋子使用前后前缀
                if (samePieces.size() == 2) {
                    // 两个棋子：前、后
                    Info.Pos frontPiece = isRed ? samePieces.get(1) : samePieces.get(0);
                    prefix = (fromPos.y == frontPiece.y) ? "前" : "后";
                } else if (samePieces.size() == 3) {
                    // 三个棋子：前、中、后
                    Info.Pos frontPiece = isRed ? samePieces.get(2) : samePieces.get(0);
                    Info.Pos middlePiece = samePieces.get(1);
                    if (fromPos.y == frontPiece.y) {
                        prefix = "前";
                    } else if (fromPos.y == middlePiece.y) {
                        prefix = "中";
                    } else {
                        prefix = "后";
                    }
                } else if (samePieces.size() > 3) {
                    // 四个或五个棋子：前、二、三、四、五
                    int index = samePieces.indexOf(new Info.Pos(fromPos.x, fromPos.y)) + 1;
                    if (isRed) {
                        // 红方：y 最大的是前
                        prefix = (index == samePieces.size()) ? "前" : getColChar(index);
                    } else {
                        // 黑方：y 最小的是前
                        prefix = (index == 1) ? "前" : getColChar(index);
                    }
                }
            }
        } else {
            // 检查同一行是否有多个相同的棋子
            samePieces.clear();
            if (chessInfo != null && chessInfo.piece != null) {
                for (int y = 0; y < 10; y++) {
                    for (int x = 0; x < 9; x++) {
                        if (y == fromPos.y && chessInfo.piece[y][x] == pieceType) {
                            samePieces.add(new Info.Pos(x, y));
                        }
                    }
                }
            }
            
            if (samePieces.size() > 1) {
                isSameRow = true;
                // 对棋子按x坐标排序（兼容API 16）
                for (int i = 0; i < samePieces.size() - 1; i++) {
                    for (int j = 0; j < samePieces.size() - i - 1; j++) {
                        Info.Pos p1 = samePieces.get(j);
                        Info.Pos p2 = samePieces.get(j + 1);
                        if (p1 != null && p2 != null && p1.x > p2.x) {
                            // 交换位置
                            samePieces.set(j, p2);
                            samePieces.set(j + 1, p1);
                        }
                    }
                }
                
                // 其他棋子使用前后前缀
                if (samePieces.size() == 2) {
                    // 两个棋子：前、后
                    // 对于红方，右边的棋子是"前"；对于黑方，左边的棋子是"前"
                    Info.Pos frontPiece = isRed ? samePieces.get(1) : samePieces.get(0);
                    prefix = (fromPos.x == frontPiece.x) ? "前" : "后";
                } else if (samePieces.size() == 3) {
                    // 三个棋子：前、中、后
                    // 对于红方，从右到左为前、中、后；对于黑方，从左到右为前、中、后
                    Info.Pos frontPiece = isRed ? samePieces.get(2) : samePieces.get(0);
                    Info.Pos middlePiece = samePieces.get(1);
                    if (fromPos.x == frontPiece.x) {
                        prefix = "前";
                    } else if (fromPos.x == middlePiece.x) {
                        prefix = "中";
                    } else {
                        prefix = "后";
                    }
                }
            }
        }
        
        // 计算起始列号
        int startCol;
        if (isRed) {
            // 红方：从右到左计数，右为一
            startCol = 9 - fromPos.x;
        } else {
            // 黑方：从左到右计数，左为1（对应红方的九）
            startCol = fromPos.x + 1;
        }
        startCol = Math.max(1, Math.min(9, startCol));
        // 红黑方都使用中文数字，以匹配棋谱格式
        String startColStr = getColChar(startCol);
        
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
            int targetCol;
            if (isRed) {
                // 红方：从右到左计数，右为一
                targetCol = 9 - toPos.x;
            } else {
                // 黑方：从左到右计数，左为1
                targetCol = toPos.x + 1;
            }
            targetCol = Math.max(1, Math.min(9, targetCol));
            // 红黑方都使用中文数字，以匹配棋谱格式
            targetPos = getColChar(targetCol);
        } else {
            // 纵向或斜向移动
            boolean isSpecialPiece = baseType == 2 || baseType == 3 || baseType == 4; // 士、象、马
            
            if (isSpecialPiece) {
                // 马、相（象）、仕（士）：使用目标列坐标
                int targetCol;
                if (isRed) {
                    // 红方：从右到左计数，右为一
                    targetCol = 9 - toPos.x;
                } else {
                    // 黑方：从左到右计数，左为1
                    targetCol = toPos.x + 1;
                }
                targetCol = Math.max(1, Math.min(9, targetCol));
                // 红黑方都使用中文数字，以匹配棋谱格式
                targetPos = getColChar(targetCol);
            } else {
                // 车、炮、兵（卒）、帅（将）：使用移动的行数（格数）
                int moveSteps = Math.abs(toPos.y - fromPos.y);
                // 确保移动的格数至少为1
                moveSteps = Math.max(1, moveSteps);
                // 红黑方都使用中文数字，以匹配棋谱格式
                targetPos = getColChar(moveSteps);
            }
        }
        
        // 获取棋子名称
        String pieceName = getPieceName(pieceType);
        
        // 生成走法字符串
        String moveString;
        if ((isSameColumn || isSameRow) && !prefix.isEmpty()) {
            if (isPawn) {
                // 兵/卒：一兵、二兵等
                moveString = prefix + pieceName + moveType + targetPos;
            } else {
                // 其他棋子：前马、后车等
                moveString = prefix + pieceName + startColStr + moveType + targetPos;
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
    
    private String getPieceName(int pieceType) {
        switch (pieceType) {
            case 1: return "将";
            case 2: return "士";
            case 3: return "象";
            case 4: return "马";
            case 5: return "车";
            case 6: return "炮";
            case 7: return "卒";
            case 8: return "帅";
            case 9: return "仕";
            case 10: return "相";
            case 11: return "马";
            case 12: return "车";
            case 13: return "炮";
            case 14: return "兵";
            default: return "未知";
        }
    }
    
    private String getColChar(int col) {
        switch (col) {
            case 1: return "一";
            case 2: return "二";
            case 3: return "三";
            case 4: return "四";
            case 5: return "五";
            case 6: return "六";
            case 7: return "七";
            case 8: return "八";
            case 9: return "九";
            default: return "";
        }
    }

    // Getters and Setters
    public int getSelectedPieceID() {
        return selectedPieceID;
    }

    public void setSelectedPieceID(int selectedPieceID) {
        this.selectedPieceID = selectedPieceID;
    }

    public int[] getSelectedBoardPiecePos() {
        return selectedBoardPiecePos;
    }

    public void setSelectedBoardPiecePos(int[] selectedBoardPiecePos) {
        this.selectedBoardPiecePos = selectedBoardPiecePos;
    }
}
