package top.nones.chessgame;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Toast;

import Info.ChessInfo;
import Info.InfoSet;
import CustomView.ChessView;
import CustomView.SetupModeView;
import top.nones.chessgame.FENHandler;

public class PvMActivitySetup {
    private PvMActivity activity;
    private int selectedPieceID = 0;
    private int[] selectedBoardPiecePos = {-1, -1};
    
    public PvMActivitySetup(PvMActivity activity) {
        this.activity = activity;
    }
    
    public int getSelectedPieceID() {
        return selectedPieceID;
    }
    
    public void setSelectedPieceID(int pieceID) {
        this.selectedPieceID = pieceID;
    }
    
    public int[] getSelectedBoardPiecePos() {
        return selectedBoardPiecePos;
    }
    
    public void setSelectedBoardPiecePos(int[] pos) {
        this.selectedBoardPiecePos = pos;
    }
    
    // 放置棋子
    public void placePiece(int x, int y, int pieceID) {
        if (activity.chessInfo != null && activity.chessInfo.piece != null && x >= 0 && x < 9 && y >= 0 && y < 10) {
            // 检查棋子数量限制
            if (!checkPieceCount(pieceID)) {
                // 显示数量限制提示
                return;
            }
            
            // 检查位置合理性
            if (!isValidPiecePosition(pieceID, x, y)) {
                // 显示位置不合理提示
                return;
            }
            
            activity.chessInfo.piece[y][x] = pieceID;
            // 重新计算攻击棋子数量
            activity.chessInfo.attackNum_B = 0;
            activity.chessInfo.attackNum_R = 0;
            for (int i = 0; i < 10; i++) {
                if (activity.chessInfo.piece[i] != null) {
                    for (int j = 0; j < 9; j++) {
                        int piece = activity.chessInfo.piece[i][j];
                        if (piece != 0) {
                            // 黑方攻击棋子：车(5)、马(4)、炮(6)、卒(7)
                            if (piece == 4 || piece == 5 || piece == 6 || piece == 7) {
                                activity.chessInfo.attackNum_B++;
                            }
                            // 红方攻击棋子：车(12)、马(11)、炮(13)、兵(14)
                            else if (piece == 11 || piece == 12 || piece == 13 || piece == 14) {
                                activity.chessInfo.attackNum_R++;
                            }
                        }
                    }
                }
            }
            // 重新绘制界面
            if (activity.chessView != null) {
                activity.chessView.requestDraw();
                // 立即刷新
                activity.chessView.invalidate();
            }
            if (activity.setupModeView != null) {
                activity.setupModeView.invalidate();
                // 立即刷新
                activity.setupModeView.postInvalidate();
            }
            
            // 不再自动检查摆棋完成，由用户点击摆棋按钮结束
            
            // 检查和棋条件，确保摆棋模式下也能提示和棋
            if (activity.controlsManager != null && activity.chessInfo != null && activity.chessInfo.status == 1) {
                activity.controlsManager.checkGameStatus(activity.chessInfo.IsRedGo);
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
        if (activity.chessInfo != null && activity.chessInfo.IsSetupMode) {
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
                // 黑将只能在九宫格内（x: 3-5, y: 7-9）- 黑方在下
                return x >= 3 && x <= 5 && y >= 7 && y <= 9;
            case 8: // 红帅
                // 红帅只能在九宫格内（x: 3-5, y: 0-2）- 红方在上
                return x >= 3 && x <= 5 && y >= 0 && y <= 2;
            case 2: // 黑士
                // 黑士只能在九宫格内（x: 3-5, y: 7-9）且走斜线 - 黑方在下
                return (x >= 3 && x <= 5 && y >= 7 && y <= 9) && 
                       ((x == 3 && (y == 7 || y == 9)) || (x == 4 && y == 8) || (x == 5 && (y == 7 || y == 9)));
            case 9: // 红士
                // 红士只能在九宫格内（x: 3-5, y: 0-2）且走斜线 - 红方在上
                return (x >= 3 && x <= 5 && y >= 0 && y <= 2) && 
                       ((x == 3 && (y == 0 || y == 2)) || (x == 4 && y == 1) || (x == 5 && (y == 0 || y == 2)));
            case 3: // 黑象
                // 黑象只能在己方半场（y: 5-9）且不能过河 - 黑方在下
                return y >= 5 && y <= 9;
            case 10: // 红相
                // 红相只能在己方半场（y: 0-4）且不能过河 - 红方在上
                return y >= 0 && y <= 4;
            case 7: // 黑卒
                // 黑卒只能在己方半场（y: 5-9）- 黑方在下
                return y >= 5 && y <= 9;
            case 14: // 红兵
                // 红兵只能在己方半场（y: 0-4）- 红方在上
                return y >= 0 && y <= 4;
            case 4: // 黑马
            case 5: // 黑车
            case 6: // 黑炮
                // 黑方棋子只能在己方半场（y: 5-9）- 黑方在下
                return y >= 5 && y <= 9;
            case 11: // 红马
            case 12: // 红车
            case 13: // 红炮
                // 红方棋子只能在己方半场（y: 0-4）- 红方在上
                return y >= 0 && y <= 4;
            default:
                return false;
        }
    }
    
    // 检查棋子数量是否符合标准
    public boolean checkPieceCount(int pieceID) {
        if (pieceID == 0) return true; // 移除棋子总是允许的
        if (activity.chessInfo == null || activity.chessInfo.piece == null) return false;
        
        int count = 0;
        for (int i = 0; i < 10; i++) {
            if (activity.chessInfo.piece[i] != null) {
                for (int j = 0; j < 9; j++) {
                    if (activity.chessInfo.piece[i][j] == pieceID) {
                        count++;
                    }
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
    
    // 显示摆棋模式帮助信息
    public void showSetupHelp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("摆棋模式帮助");
        builder.setMessage("1. 点击左侧棋子选择区域选择棋子\n" +
                          "2. 点击棋盘放置选中的棋子\n" +
                          "3. 点击棋盘上已放置的棋子可移动或移除\n" +
                          "4. 点击清空棋盘按钮可清空除将/帅外的所有棋子\n" +
                          "\n棋子放置规则：\n" +
                          "- 将/帅：只能放在九宫格内\n" +
                          "- 士：只能放在九宫格内的斜线位置\n" +
                          "- 象/相：只能放在己方半场的田字中心\n" +
                          "- 卒/兵：只能放在己方兵线位置\n" +
                          "- 马、车、炮：可以自由放置\n" +
                          "\n摆棋完成后，会自动提示选择开局方。");
        builder.setPositiveButton("确定", null);
        builder.show();
    }
    
    // 检查摆棋是否完成
    public boolean checkSetupComplete() {
        if (activity.chessInfo == null || activity.chessInfo.piece == null) return false;
        
        // 只检查基本合法性：双方都有将/帅
        boolean hasRedKing = false;
        boolean hasBlackKing = false;
        
        for (int i = 0; i < 10; i++) {
            if (activity.chessInfo.piece[i] != null) {
                for (int j = 0; j < 9; j++) {
                    int piece = activity.chessInfo.piece[i][j];
                    if (piece == 1) { // 黑将
                        hasBlackKing = true;
                    } else if (piece == 8) { // 红帅
                        hasRedKing = true;
                    }
                }
            }
        }
        
        return hasRedKing && hasBlackKing;
    }
    
    // 结束摆棋并选择开局方
    public void finishSetup() {
        if (activity != null && checkSetupComplete()) {
            // 显示选择开局方的对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("选择开局方");
            builder.setMessage("请选择由哪一方开始下棋");
            builder.setPositiveButton("红方开始", (dialog, which) -> {
                if (activity != null && activity.chessInfo != null) {
                    // 红方开始，设置IsRedGo为true
                    activity.chessInfo.IsRedGo = true;
                    // 生成并保存摆棋结束时的FEN信息（在IsSetupMode被设置为false之前）
                    FENHandler fenHandler = new FENHandler();
                    String setupFEN = fenHandler.generateFEN(activity.chessInfo);
                    if (activity.notationManager != null) {
                        activity.notationManager.setSetupFEN(setupFEN);
                        System.out.println("PvMActivity: 摆棋结束，保存FEN: " + setupFEN);
                    }
                    // 退出摆棋模式
                    activity.chessInfo.IsSetupMode = false;
                    // 确保游戏状态为进行中
                    activity.chessInfo.status = 1;
                    // 重置infoSet，清空摆棋过程中的记录
                    activity.infoSet = new InfoSet();
                    // 将当前摆棋局面保存到infoSet中作为初始状态
                    try {
                        if (activity.infoSet != null) {
                            activity.infoSet.pushInfo(activity.chessInfo);
                        }
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                    }
                    // 重置时间
                    activity.redTime = 0;
                    activity.blackTime = 0;
                    activity.currentTurnStartTime = 0;
                    activity.updateTimeDisplay();
                    // 重新绘制界面
                    if (activity.chessView != null) {
                        activity.chessView.requestDraw();
                    }
                    if (activity.roundView != null) {
                        activity.roundView.requestDraw();
                    }
                }
            });
            builder.setNegativeButton("黑方开始", (dialog, which) -> {
                if (activity != null && activity.chessInfo != null) {
                    // 黑方开始，设置IsRedGo为false
                    activity.chessInfo.IsRedGo = false;
                    // 生成并保存摆棋结束时的FEN信息（在IsSetupMode被设置为false之前）
                    FENHandler fenHandler = new FENHandler();
                    String setupFEN = fenHandler.generateFEN(activity.chessInfo);
                    if (activity.notationManager != null) {
                        activity.notationManager.setSetupFEN(setupFEN);
                        System.out.println("PvMActivity: 摆棋结束，保存FEN: " + setupFEN);
                    }
                    // 退出摆棋模式
                    activity.chessInfo.IsSetupMode = false;
                    // 确保游戏状态为进行中
                    activity.chessInfo.status = 1;
                    // 重置infoSet，清空摆棋过程中的记录
                    activity.infoSet = new InfoSet();
                    // 将当前摆棋局面保存到infoSet中作为初始状态
                    try {
                        if (activity.infoSet != null) {
                            activity.infoSet.pushInfo(activity.chessInfo);
                        }
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                    }
                    // 重置时间
                    activity.redTime = 0;
                    activity.blackTime = 0;
                    activity.currentTurnStartTime = 0;
                    activity.updateTimeDisplay();
                    // 重新绘制界面
                    if (activity.chessView != null) {
                        activity.chessView.requestDraw();
                    }
                    if (activity.roundView != null) {
                        activity.roundView.requestDraw();
                    }
                }
            });
            builder.setCancelable(false); // 必须选择一个选项
            builder.show();
        } else {
            if (activity != null) {
                // 移除Toast提示，通过界面显示提示信息
            }
        }
    }
    
    // 处理摆棋模式的触摸事件
    public boolean handleSetupModeTouch(float x, float y, android.view.MotionEvent event) {
        if (activity != null && activity.chessInfo != null && activity.chessInfo.IsSetupMode) {
            // 检查是否点击在棋盘上
            if (activity.chessView != null && x >= 0 && x <= activity.chessView.Board_width && y >= 0 && y <= activity.chessView.Board_height) {
                int[] pos = activity.getPos(event);
                if (pos != null && pos.length >= 2) {
                    activity.chessInfo.Select = pos;
                    int i = pos[0];
                    int j = pos[1];

                    if (i >= 0 && i <= 8 && j >= 0 && j <= 9) {
                        // 获取点击位置的棋子ID
                        int boardPieceID = 0;
                        if (activity.chessInfo.piece != null && activity.chessInfo.piece.length > j && activity.chessInfo.piece[j] != null && activity.chessInfo.piece[j].length > i) {
                            boardPieceID = activity.chessInfo.piece[j][i];
                        }
                        
                        // 如果已经选中了棋盘上的棋子
                        if (selectedBoardPiecePos[0] != -1 && selectedBoardPiecePos[1] != -1) {
                            // 获取要操作的棋子ID
                            int pieceToOperate = 0;
                            if (activity.chessInfo.piece != null && activity.chessInfo.piece.length > selectedBoardPiecePos[1] && activity.chessInfo.piece[selectedBoardPiecePos[1]] != null && activity.chessInfo.piece[selectedBoardPiecePos[1]].length > selectedBoardPiecePos[0]) {
                                pieceToOperate = activity.chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]];
                            }
                            
                            // 检查是否是点击原位置（下架）
                            if (i == selectedBoardPiecePos[0] && j == selectedBoardPiecePos[1]) {
                                // 点击原位置，下架棋子
                                if (pieceToOperate != 1 && pieceToOperate != 8) { // 老将不能下架
                                    placePiece(selectedBoardPiecePos[0], selectedBoardPiecePos[1], 0);
                                    // 重置选中状态
                                    selectedBoardPiecePos[0] = -1;
                                    selectedBoardPiecePos[1] = -1;
                                }
                            }
                            // 点击的是空白区域（移动棋子）
                            else if (boardPieceID == 0) {
                                // 检查是否是老将
                                if (pieceToOperate == 1 || pieceToOperate == 8) {
                                    // 老将不能下架，但可以移动到合法位置
                                    // 检查新位置是否合理
                                    if (isValidPiecePosition(pieceToOperate, i, j)) {
                                        // 先将原位置设为0
                                        if (activity.chessInfo.piece != null && activity.chessInfo.piece.length > selectedBoardPiecePos[1] && activity.chessInfo.piece[selectedBoardPiecePos[1]] != null && activity.chessInfo.piece[selectedBoardPiecePos[1]].length > selectedBoardPiecePos[0]) {
                                            activity.chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]] = 0;
                                            // 再将新位置设为棋子ID
                                            placePiece(i, j, pieceToOperate);
                                            // 重置选中状态
                                            selectedBoardPiecePos[0] = -1;
                                            selectedBoardPiecePos[1] = -1;
                                        }
                                    }
                                } else {
                                    // 不是老将，可以移动
                                    // 检查新位置是否合理
                                    if (isValidPiecePosition(pieceToOperate, i, j)) {
                                        // 先将原位置设为0
                                        if (activity.chessInfo.piece != null && activity.chessInfo.piece.length > selectedBoardPiecePos[1] && activity.chessInfo.piece[selectedBoardPiecePos[1]] != null && activity.chessInfo.piece[selectedBoardPiecePos[1]].length > selectedBoardPiecePos[0]) {
                                            activity.chessInfo.piece[selectedBoardPiecePos[1]][selectedBoardPiecePos[0]] = 0;
                                            // 再将新位置设为棋子ID
                                            placePiece(i, j, pieceToOperate);
                                            // 重置选中状态
                                            selectedBoardPiecePos[0] = -1;
                                            selectedBoardPiecePos[1] = -1;
                                        }
                                    }
                                }
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
                            activity.chessInfo.Select = new int[]{i, j};
                            // 同时刷新两个视图
                            if (activity.chessView != null) {
                                activity.chessView.requestDraw();
                            }
                            if (activity.setupModeView != null) {
                                activity.setupModeView.invalidate();
                            }
                        }
                        // 点击空白区域，重置选中状态
                        else {
                            selectedBoardPiecePos[0] = -1;
                            selectedBoardPiecePos[1] = -1;
                            activity.chessInfo.Select = new int[]{-1, -1};
                            // 同时刷新两个视图
                            if (activity.chessView != null) {
                                activity.chessView.requestDraw();
                            }
                            if (activity.setupModeView != null) {
                                activity.setupModeView.invalidate();
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
    
    // 处理摆棋模式的切换
    public void toggleSetupMode() {
        if (activity.chessInfo != null) {
            if (activity.chessInfo.IsSetupMode) {
                // 关闭摆棋模式，检查摆棋是否完成
                finishSetup();
                // 隐藏摆棋模式视图
                if (activity.setupModeView != null) {
                    activity.setupModeView.setVisibility(View.GONE);
                }
                // 恢复回合信息视图
                if (activity.roundView != null) {
                    activity.roundView.setVisibility(View.VISIBLE);
                }

            } else {
                // 开启摆棋模式
                activity.chessInfo.IsSetupMode = true;
                // 显示摆棋模式视图
                if (activity.setupModeView != null) {
                    // 确保布局参数正确
                    android.widget.RelativeLayout.LayoutParams paramsSetup = (android.widget.RelativeLayout.LayoutParams) activity.setupModeView.getLayoutParams();
                    if (paramsSetup != null) {
                        paramsSetup.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
                        paramsSetup.addRule(android.widget.RelativeLayout.BELOW, R.id.roundView);
                        paramsSetup.width = android.widget.RelativeLayout.LayoutParams.MATCH_PARENT;
                        paramsSetup.height = android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT;
                        paramsSetup.setMargins(30, 10, 30, 10);
                        activity.setupModeView.setLayoutParams(paramsSetup);
                    }
                    // 先设置布局参数，再显示视图
                    activity.setupModeView.setVisibility(View.VISIBLE);
                    // 确保视图在最上层
                    activity.setupModeView.bringToFront();
                }
                // 隐藏回合信息视图
                if (activity.roundView != null) {
                    activity.roundView.setVisibility(View.GONE);
                }

                // 不需要清空缓存，保持当前局面
                // 更新视图中的chessInfo引用
                if (activity.chessView != null) {
                    activity.chessView.setChessInfo(activity.chessInfo);
                }
                if (activity.setupModeView != null) {
                    activity.setupModeView.setChessInfo(activity.chessInfo);
                }
                if (activity.roundView != null) {
                    activity.roundView.setChessInfo(activity.chessInfo);
                }
                // 重新绘制界面
                if (activity.chessView != null) {
                    activity.chessView.requestDraw();
                    // 立即刷新
                    activity.chessView.invalidate();
                }
                if (activity.setupModeView != null) {
                    activity.setupModeView.invalidate();
                    // 立即刷新
                    activity.setupModeView.postInvalidate();
                }
            }
        }
    }
}