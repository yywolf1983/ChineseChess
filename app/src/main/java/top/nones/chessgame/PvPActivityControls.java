package top.nones.chessgame;

import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import Info.ChessInfo;
import Info.InfoSet;
import Utils.LogUtils;

public class PvPActivityControls {
    private PvPActivity activity;
    private ChessInfo chessInfo;
    private InfoSet infoSet;
    private PvPActivityGame gameModule;
    
    // 保存棋谱相关的临时变量
    private String pendingSaveFileName;
    private String pendingSaveRedPlayer;
    private String pendingSaveBlackPlayer;
    private String pendingSaveDate;
    private String pendingSaveLocation;
    private String pendingSaveEvent;
    private String pendingSaveRound;

    public PvPActivityControls(PvPActivity activity, ChessInfo chessInfo, InfoSet infoSet, PvPActivityGame gameModule) {
        this.activity = activity;
        this.chessInfo = chessInfo;
        this.infoSet = infoSet;
        this.gameModule = gameModule;
    }

    public void onClick(View view) {
        long lastClickTime = System.currentTimeMillis();
        if (lastClickTime - PvPActivityInit.getCurClickTime() < PvPActivityInit.getMinClickDelayTime()) {
            return;
        }
        PvPActivityInit.setCurClickTime(lastClickTime);
        PvPActivityInit.setLastClickTime(lastClickTime);

        if (PvPActivityInit.getSelectMusic() != null) {
            PvPActivityInit.playEffect(PvPActivityInit.getSelectMusic());
        }
        int viewId = view.getId();
        if (viewId == R.id.btn_retry) {
            handleRetryButton();
        } else if (viewId == R.id.btn_recall) {
            handleRecallButton();
        } else if (viewId == R.id.btn_save) {
            // 保存棋谱，让用户指定文件名
            handleSaveButton();
        } else if (viewId == R.id.btn_setup) {
            // 切换摆棋模式
            if (chessInfo != null) {
                if (chessInfo.IsSetupMode) {
                    // 关闭摆棋模式，检查摆棋是否完成
                    finishSetup();
                } else {
                    // 开启摆棋模式
                    chessInfo.IsSetupMode = true;
                    // 清空原来的缓存
                    if (infoSet != null) {
                        infoSet.newInfo();
                    }
                    // 重新绘制界面
                    if (activity.getChessView() != null) {
                        activity.getChessView().requestDraw();
                    }
                    if (activity.getRoundView() != null) {
                        activity.getRoundView().requestDraw();
                    }
                    Toast.makeText(activity, "摆棋模式已开启", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // 显示棋子选择对话框
    public void showPieceSelectionDialog(final int x, final int y) {
        final String[] blackPieces = {"黑将", "黑士", "黑象", "黑马", "黑车", "黑炮", "黑卒", "移除棋子"};
        final String[] redPieces = {"红帅", "红士", "红象", "红马", "红车", "红炮", "红兵", "移除棋子"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("选择棋子");
        
        // 显示黑棋和红棋选项
        builder.setItems(blackPieces, (dialog, which) -> {
            int pieceID = 0;
            switch (which) {
                case 0: pieceID = 1; break; // 黑将
                case 1: pieceID = 2; break; // 黑士
                case 2: pieceID = 3; break; // 黑象
                case 3: pieceID = 4; break; // 黑马
                case 4: pieceID = 5; break; // 黑车
                case 5: pieceID = 6; break; // 黑炮
                case 6: pieceID = 7; break; // 黑卒
                case 7: pieceID = 0; break; // 移除棋子
            }
            gameModule.placePiece(x, y, pieceID);
        });
        
        builder.setNegativeButton("红棋", (dialog, which) -> {
            AlertDialog.Builder redBuilder = new AlertDialog.Builder(activity);
            redBuilder.setTitle("选择红棋");
            redBuilder.setItems(redPieces, (dialog1, which1) -> {
                int pieceID = 0;
                switch (which1) {
                    case 0: pieceID = 8; break; // 红帅
                    case 1: pieceID = 9; break; // 红士
                    case 2: pieceID = 10; break; // 红象
                    case 3: pieceID = 11; break; // 红马
                    case 4: pieceID = 12; break; // 红车
                    case 5: pieceID = 13; break; // 红炮
                    case 6: pieceID = 14; break; // 红兵
                    case 7: pieceID = 0; break; // 移除棋子
                }
                gameModule.placePiece(x, y, pieceID);
            });
            redBuilder.show();
        });
        
        builder.show();
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
    private boolean checkSetupComplete() {
        if (chessInfo == null || chessInfo.piece == null) return false;
        
        // 只检查基本合法性：双方都有将/帅
        boolean hasRedKing = false;
        boolean hasBlackKing = false;
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                int piece = chessInfo.piece[i][j];
                if (piece == 1) { // 黑将
                    hasBlackKing = true;
                } else if (piece == 8) { // 红帅
                    hasRedKing = true;
                }
            }
        }
        
        return hasRedKing && hasBlackKing;
    }
    
    // 结束摆棋并选择开局方
    private void finishSetup() {
        if (checkSetupComplete()) {
            // 显示选择开局方的对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("选择开局方");
            builder.setMessage("请选择由哪一方开始下棋");
            builder.setPositiveButton("红方开始", (dialog, which) -> {
                // 红方开始，设置IsRedGo为true
                chessInfo.IsRedGo = true;
                // 退出摆棋模式
                chessInfo.IsSetupMode = false;
                // 确保游戏状态为进行中
                chessInfo.status = 1;
                // 重置infoSet，清空摆棋过程中的记录
                infoSet = new InfoSet();
                // 将当前摆棋局面保存到infoSet中作为初始状态
                try {
                    infoSet.pushInfo(chessInfo);
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
                Toast.makeText(activity, "摆棋完成！红方开始", Toast.LENGTH_SHORT).show();
                // 重新绘制界面
                if (activity.getChessView() != null) {
                    activity.getChessView().requestDraw();
                }
            });
            builder.setNegativeButton("黑方开始", (dialog, which) -> {
                // 黑方开始，设置IsRedGo为false
                chessInfo.IsRedGo = false;
                // 退出摆棋模式
                chessInfo.IsSetupMode = false;
                // 确保游戏状态为进行中
                chessInfo.status = 1;
                // 重置infoSet，清空摆棋过程中的记录
                infoSet = new InfoSet();
                // 将当前摆棋局面保存到infoSet中作为初始状态
                try {
                    infoSet.pushInfo(chessInfo);
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
                Toast.makeText(activity, "摆棋完成！黑方开始", Toast.LENGTH_SHORT).show();
                // 重新绘制界面
                if (activity.getChessView() != null) {
                    activity.getChessView().requestDraw();
                }
            });
            builder.setCancelable(false); // 必须选择一个选项
            builder.show();
        } else {
            Toast.makeText(activity, "棋子放置不完整，请继续放置棋子", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleRetryButton() {
        // 完全重置游戏状态
        try {
            // 创建新的ChessInfo对象
            chessInfo = new ChessInfo();
            // 创建新的InfoSet对象
            infoSet = new InfoSet();
            // 重新推入初始状态
            infoSet.pushInfo(chessInfo);
            // 重新绘制界面
            if (activity.getChessView() != null) {
                activity.getChessView().setChessInfo(chessInfo);
                activity.getChessView().requestDraw();
            }
            if (activity.getRoundView() != null) {
                activity.getRoundView().setChessInfo(chessInfo);
                activity.getRoundView().requestDraw();
            }
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        Toast.makeText(activity, "游戏已重置", Toast.LENGTH_SHORT).show();
    }

    private void handleRecallButton() {
        if (infoSet != null && infoSet.preInfo != null && chessInfo != null && infoSet.curInfo != null) {
            // 确保保留至少一个初始状态，只允许悔到初始状态，但不会把初始状态也悔掉
            if (infoSet.preInfo.size() > 1) {
                // 只悔一步棋，不使用循环
                ChessInfo tmp = infoSet.preInfo.pop();
                try {
                    chessInfo.setInfo(tmp);
                    infoSet.curInfo.setInfo(tmp);
                    // 重新绘制界面
                    if (activity.getChessView() != null) {
                        activity.getChessView().requestDraw();
                    }
                    if (activity.getRoundView() != null) {
                        activity.getRoundView().requestDraw();
                    }
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
            } else if (infoSet.preInfo.size() == 1) {
                // 只剩一个状态了，这就是初始状态，直接恢复它但不弹出
                ChessInfo tmp = infoSet.preInfo.peek();
                try {
                    chessInfo.setInfo(tmp);
                    infoSet.curInfo.setInfo(tmp);
                    // 重新绘制界面
                    if (activity.getChessView() != null) {
                        activity.getChessView().requestDraw();
                    }
                    if (activity.getRoundView() != null) {
                        activity.getRoundView().requestDraw();
                    }
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 保存棋谱（默认名称）
    public void handleSaveButton() {
        // 创建一个布局用于输入对局信息
        android.view.LayoutInflater inflater = activity.getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_save_notation, null);
        
        final android.widget.EditText redPlayerEditText = dialogView.findViewById(R.id.red_player_edit);
        final android.widget.EditText blackPlayerEditText = dialogView.findViewById(R.id.black_player_edit);
        final android.widget.EditText dateEditText = dialogView.findViewById(R.id.date_edit);
        final android.widget.EditText locationEditText = dialogView.findViewById(R.id.location_edit);
        final android.widget.EditText eventEditText = dialogView.findViewById(R.id.event_edit);
        final android.widget.EditText roundEditText = dialogView.findViewById(R.id.round_edit);
        
        // 设置默认值
        dateEditText.setText(new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()));
        
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("保存棋谱");
        builder.setView(dialogView);
        builder.setPositiveButton("保存", (dialog, which) -> {
            // 生成默认文件名
            String fileName = "双人对局_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".pgn";
            
            String redPlayer = redPlayerEditText.getText().toString().trim();
            String blackPlayer = blackPlayerEditText.getText().toString().trim();
            String date = dateEditText.getText().toString().trim();
            String location = locationEditText.getText().toString().trim();
            String event = eventEditText.getText().toString().trim();
            String round = roundEditText.getText().toString().trim();
            
            // 保存信息到成员变量
            pendingSaveFileName = fileName;
            pendingSaveRedPlayer = redPlayer;
            pendingSaveBlackPlayer = blackPlayer;
            pendingSaveDate = date;
            pendingSaveLocation = location;
            pendingSaveEvent = event;
            pendingSaveRound = round;
            
            // 使用SAF打开文件保存选择器
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(android.content.Intent.EXTRA_TITLE, fileName);
            activity.startActivityForResult(intent, 1003);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // 保存棋谱到URI
    public void saveChessNotationToUri(android.net.Uri uri) {
        try {
            // 使用保存对话框中输入的信息
            String fileName = pendingSaveFileName != null ? pendingSaveFileName : "双人对局.pgn";
            String redPlayer = pendingSaveRedPlayer != null ? pendingSaveRedPlayer : "";
            String blackPlayer = pendingSaveBlackPlayer != null ? pendingSaveBlackPlayer : "";
            String date = pendingSaveDate != null ? pendingSaveDate : "";
            String location = pendingSaveLocation != null ? pendingSaveLocation : "";
            String event = pendingSaveEvent != null ? pendingSaveEvent : "";
            String round = pendingSaveRound != null ? pendingSaveRound : "";
            
            // 创建棋谱对象
            Info.ChessNotation notation = new Info.ChessNotation();
            notation.setFileName(fileName);
            notation.setDate(new java.util.Date());
            notation.setPlayerRed(redPlayer);
            notation.setPlayerBlack(blackPlayer);
            notation.setMatchDate(date);
            notation.setLocation(location);
            notation.setEvent(event);
            notation.setRound(round);
            
            // 添加FEN信息
            if (chessInfo != null) {
                String fen;
                // 在非摆棋模式下，使用infoSet中的初始状态生成FEN，确保保存的是摆棋完成时的局面
                if (!chessInfo.IsSetupMode && infoSet != null && !infoSet.preInfo.empty()) {
                    // 获取infoSet中的第一个元素（初始状态）
                    java.util.Stack<Info.ChessInfo> tempStack = new java.util.Stack<>();
                    Info.ChessInfo initialInfo = null;
                    
                    // 弹出所有元素，找到第一个元素
                    while (!infoSet.preInfo.empty()) {
                        Info.ChessInfo info = infoSet.preInfo.pop();
                        tempStack.push(info);
                        if (initialInfo == null) {
                            initialInfo = info;
                        }
                    }
                    
                    // 恢复原栈
                    while (!tempStack.empty()) {
                        infoSet.preInfo.push(tempStack.pop());
                    }
                    
                    // 使用初始状态生成FEN
                    if (initialInfo != null) {
                        fen = generateFEN(initialInfo);
                    } else {
                        // 如果没有初始状态，使用当前状态
                        fen = generateFEN(chessInfo);
                    }
                } else {
                    // 在摆棋模式下，使用当前棋盘状态生成FEN
                    fen = generateFEN(chessInfo);
                }
                notation.setFen(fen);
            }
            
            // 只有在非摆棋模式下才提取走法记录
            if (chessInfo != null && !chessInfo.IsSetupMode && infoSet != null && infoSet.preInfo != null) {
                // 创建一个临时列表来存储所有ChessInfo对象，而不修改原栈
                java.util.List<Info.ChessInfo> tempList = new java.util.ArrayList<>();
                java.util.Stack<Info.ChessInfo> originalStack = new java.util.Stack<>();
                
                // 先将所有ChessInfo对象弹出到临时列表，同时保存到原始栈
                while (!infoSet.preInfo.empty()) {
                    Info.ChessInfo info = infoSet.preInfo.pop();
                    tempList.add(info);
                    originalStack.push(info);
                }
                
                // 恢复原栈
                while (!originalStack.empty()) {
                    infoSet.preInfo.push(originalStack.pop());
                }
                
                // 按照临时列表的顺序处理，保证走法记录顺序正确
                for (int i = tempList.size() - 1; i >= 0; i--) {
                    Info.ChessInfo info = tempList.get(i);
                    
                    // 生成走法记录
                    if (info.prePos != null && info.curPos != null) {
                        // 尝试获取移动的棋子类型
                        int piece = 0;
                        boolean isRed = false;
                        
                        // 首先尝试从当前位置获取棋子
                        if (info.piece != null && info.curPos.y >= 0 && info.curPos.y < info.piece.length && 
                            info.curPos.x >= 0 && info.curPos.x < info.piece[info.curPos.y].length) {
                            piece = info.piece[info.curPos.y][info.curPos.x];
                            isRed = piece >= 8 && piece <= 14;
                        }
                        
                        if (piece != 0) {
                            String move = gameModule.generateMoveString(piece, info.prePos, info.curPos, isRed);
                            
                            if (move != null) {
                                if (isRed) {
                                    // 红方走法，添加新记录
                                    notation.addMoveRecord(move, "");
                                } else {
                                    // 黑方走法，更新最后一条记录
                                    if (!notation.getMoveRecords().isEmpty()) {
                                        Info.ChessNotation.MoveRecord lastRecord = notation.getMoveRecords().get(notation.getMoveRecords().size() - 1);
                                        if (lastRecord.blackMove.isEmpty()) {
                                            lastRecord.blackMove = move;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 只有在非摆棋模式下才处理当前chessInfo中的走法记录（最后一步）
            if (chessInfo != null && !chessInfo.IsSetupMode && chessInfo.prePos != null && chessInfo.curPos != null) {
                // 尝试获取移动的棋子类型
                int piece = 0;
                boolean isRed = false;
                
                // 首先尝试从当前位置获取棋子
                if (chessInfo.piece != null && chessInfo.curPos.y >= 0 && chessInfo.curPos.y < chessInfo.piece.length && 
                    chessInfo.curPos.x >= 0 && chessInfo.curPos.x < chessInfo.piece[chessInfo.curPos.y].length) {
                    piece = chessInfo.piece[chessInfo.curPos.y][chessInfo.curPos.x];
                    isRed = piece >= 8 && piece <= 14;
                }
                
                if (piece != 0) {
                    String move = gameModule.generateMoveString(piece, chessInfo.prePos, chessInfo.curPos, isRed);
                    
                    if (move != null) {
                        if (isRed) {
                            // 红方走法，添加新记录
                            notation.addMoveRecord(move, "");
                        } else {
                            // 黑方走法，更新最后一条记录
                            if (!notation.getMoveRecords().isEmpty()) {
                                Info.ChessNotation.MoveRecord lastRecord = notation.getMoveRecords().get(notation.getMoveRecords().size() - 1);
                                if (lastRecord.blackMove.isEmpty()) {
                                    lastRecord.blackMove = move;
                                }
                            }
                        }
                    }
                }
            }
            
            // 生成棋谱内容
            String content = notation.toSaveContent();
            
            // 写入到选择的URI，使用"w"模式确保从文件开头写入
            android.os.ParcelFileDescriptor pfd = activity.getContentResolver().openFileDescriptor(uri, "w");
            if (pfd != null) {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(pfd.getFileDescriptor());
                     java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(fos, "UTF-8")) {
                    // 先将文件截断为0，确保完全清空
                    fos.getChannel().truncate(0);
                    // 写入新内容
                    writer.write(content);
                    writer.flush();
                    // 再次截断文件，确保没有残留信息
                    // 使用文件通道的当前位置作为截断点，确保正确处理UTF-8编码的字符
                    fos.getChannel().truncate(fos.getChannel().position());
                    // 强制刷新文件系统缓存
                    fos.getFD().sync();
                    Toast.makeText(activity, "棋谱保存成功: " + fileName, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(activity, "保存棋谱失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                } finally {
                    try {
                        pfd.close();
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                Toast.makeText(activity, "无法创建文件描述符", Toast.LENGTH_SHORT).show();
            }
            
            // 清空临时变量
            pendingSaveFileName = null;
            pendingSaveRedPlayer = null;
            pendingSaveBlackPlayer = null;
            pendingSaveDate = null;
            pendingSaveLocation = null;
            pendingSaveEvent = null;
            pendingSaveRound = null;
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(activity, "保存棋谱失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 生成FEN字符串
    private String generateFEN(Info.ChessInfo chessInfo) {
        StringBuilder fen = new StringBuilder();
        
        // 生成棋盘部分，从黑方底线开始（y=9）到红方底线结束（y=0），符合标准FEN格式
        for (int y = 9; y >= 0; y--) {
            int emptyCount = 0;
            for (int x = 0; x < 9; x++) {
                int piece = chessInfo.piece[y][x];
                if (piece == 0) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(pieceToFEN(piece));
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (y > 0) {
                fen.append('/');
            }
        }
        
        // 生成回合部分，符合标准FEN格式
        // 'w' 表示白方（红方）走，'b' 表示黑方走
        fen.append(' ');
        fen.append(chessInfo.IsRedGo ? 'w' : 'b');
        
        // 生成 castle 部分（中国象棋不需要）
        fen.append(" - - 0 1");
        
        return fen.toString();
    }
    
    // 将棋子ID转换为FEN符号
    private char pieceToFEN(int piece) {
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

    // Getters and Setters
    public String getPendingSaveFileName() {
        return pendingSaveFileName;
    }

    public void setPendingSaveFileName(String pendingSaveFileName) {
        this.pendingSaveFileName = pendingSaveFileName;
    }

    public String getPendingSaveRedPlayer() {
        return pendingSaveRedPlayer;
    }

    public void setPendingSaveRedPlayer(String pendingSaveRedPlayer) {
        this.pendingSaveRedPlayer = pendingSaveRedPlayer;
    }

    public String getPendingSaveBlackPlayer() {
        return pendingSaveBlackPlayer;
    }

    public void setPendingSaveBlackPlayer(String pendingSaveBlackPlayer) {
        this.pendingSaveBlackPlayer = pendingSaveBlackPlayer;
    }

    public String getPendingSaveDate() {
        return pendingSaveDate;
    }

    public void setPendingSaveDate(String pendingSaveDate) {
        this.pendingSaveDate = pendingSaveDate;
    }

    public String getPendingSaveLocation() {
        return pendingSaveLocation;
    }

    public void setPendingSaveLocation(String pendingSaveLocation) {
        this.pendingSaveLocation = pendingSaveLocation;
    }

    public String getPendingSaveEvent() {
        return pendingSaveEvent;
    }

    public void setPendingSaveEvent(String pendingSaveEvent) {
        this.pendingSaveEvent = pendingSaveEvent;
    }

    public String getPendingSaveRound() {
        return pendingSaveRound;
    }

    public void setPendingSaveRound(String pendingSaveRound) {
        this.pendingSaveRound = pendingSaveRound;
    }
}
