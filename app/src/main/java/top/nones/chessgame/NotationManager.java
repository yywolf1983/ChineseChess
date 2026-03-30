package top.nones.chessgame;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import Info.ChessInfo;
import Info.ChessNotation;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class NotationManager {
    private PvMActivity activity;
    private ChessNotation currentNotation;
    private int currentMoveIndex = 0;
    private String setupFEN;
    
    // 保存棋谱相关的临时变量
    private String pendingSaveFileName;
    private String pendingSaveRedPlayer;
    private String pendingSaveBlackPlayer;
    private String pendingSaveDate;
    private String pendingSaveLocation;
    private String pendingSaveEvent;
    private String pendingSaveRound;
    
    public NotationManager(PvMActivity activity) {
        this.activity = activity;
    }
    
    public ChessNotation getCurrentNotation() {
        return currentNotation;
    }
    
    public void setCurrentNotation(ChessNotation notation) {
        this.currentNotation = notation;
        // 重置当前步数为0
        this.currentMoveIndex = 0;
        // 显示初始棋谱信息
        updateMoveInfoDisplay();
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
        // 先弹出对话框获取棋谱信息
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(activity);
        android.view.View dialogView = inflater.inflate(R.layout.dialog_save_notation, null);
        
        final android.widget.EditText redPlayerEditText = dialogView.findViewById(R.id.red_player_edit);
        final android.widget.EditText blackPlayerEditText = dialogView.findViewById(R.id.black_player_edit);
        final android.widget.EditText dateEditText = dialogView.findViewById(R.id.date_edit);
        final android.widget.EditText locationEditText = dialogView.findViewById(R.id.location_edit);
        final android.widget.EditText eventEditText = dialogView.findViewById(R.id.event_edit);
        final android.widget.EditText roundEditText = dialogView.findViewById(R.id.round_edit);
        
        // 设置默认值
        dateEditText.setText(new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()));
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
        builder.setTitle("保存棋谱");
        builder.setView(dialogView);
        builder.setPositiveButton("保存", (dialog, which) -> {
            // 生成默认文件名
            String fileName = "对局_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".pgn";
            
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
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            activity.startActivityForResult(intent, 1003);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
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
                androidx.documentfile.provider.DocumentFile documentFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(activity, uri);
                if (documentFile != null && documentFile.getName() != null) {
                    fileName = documentFile.getName();
                }
                
                // 解析棋谱内容
                ChessNotation notation = ChessNotation.parseFromContent(fileName, fileContent);
                if (notation != null) {
                    currentNotation = notation;
                    // 初始化棋盘状态为初始状态
                    activity.chessInfo = new ChessInfo();
                    activity.infoSet = new Info.InfoSet();
                    if (PvMActivity.setting != null) {
                        activity.chessInfo.setting = PvMActivity.setting;
                    }
                    if (activity.chessView != null) {
                        activity.chessView.setChessInfo(activity.chessInfo);
                    }
                    if (activity.roundView != null) {
                        activity.roundView.setChessInfo(activity.chessInfo);
                    }
                    currentMoveIndex = 0;
                    activity.continueGameRoundCount = 0;
                    
                    // 生成棋盘状态
                    BoardStateGenerator boardStateGenerator = new BoardStateGenerator(activity);
                    boardStateGenerator.generateBoardStateFromNotation(currentNotation, currentMoveIndex);
                    
                    if (activity.chessView != null) {
                        activity.chessView.requestDraw();
                    }
                    if (activity.roundView != null) {
                        activity.roundView.requestDraw();
                    }
                    Toast.makeText(activity, "棋谱加载成功: " + fileName, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "棋谱格式不正确", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(activity, "加载棋谱失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // 保存棋谱到URI
    public void saveChessNotationToUri(Uri uri) {
        try {
            // 使用保存对话框中输入的信息
            String fileName = pendingSaveFileName != null ? pendingSaveFileName : "棋谱.pgn";
            String redPlayer = pendingSaveRedPlayer != null ? pendingSaveRedPlayer : "";
            String blackPlayer = pendingSaveBlackPlayer != null ? pendingSaveBlackPlayer : "";
            String date = pendingSaveDate != null ? pendingSaveDate : "";
            String location = pendingSaveLocation != null ? pendingSaveLocation : "";
            String event = pendingSaveEvent != null ? pendingSaveEvent : "";
            String round = pendingSaveRound != null ? pendingSaveRound : "";
            
            // 创建棋谱对象
            ChessNotation notation = new ChessNotation();
            notation.setFileName(fileName);
            notation.setDate(new java.util.Date());
            notation.setPlayerRed(redPlayer);
            notation.setPlayerBlack(blackPlayer);
            notation.setMatchDate(date);
            notation.setLocation(location);
            notation.setEvent(event);
            notation.setRound(round);
            
            // 添加FEN信息
            if (activity.chessInfo != null) {
                FENHandler fenHandler = new FENHandler();
                String fen = fenHandler.generateFENForSave(activity.chessInfo, setupFEN, currentNotation);
                notation.setFen(fen);
            }
            
            // 提取走法记录
            if (activity.chessInfo != null && activity.infoSet != null && activity.infoSet.preInfo != null) {
                extractMoveRecords(notation);
            }
            
            // 生成棋谱内容
            String content = notation.toSaveContent();
            
            // 写入到选择的URI，确保完全覆盖文件内容
            // 先获取文件描述符，然后使用 FileOutputStream 来确保覆盖模式
            android.os.ParcelFileDescriptor pfd = activity.getContentResolver().openFileDescriptor(uri, "w");
            if (pfd != null) {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(pfd.getFileDescriptor());
                     OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {
                    // 写入新内容
                    writer.write(content);
                    writer.flush();
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
    
    // 提取走法记录
    private void extractMoveRecords(ChessNotation notation) {
        if (activity.chessInfo == null || activity.infoSet == null || activity.infoSet.preInfo == null) {
            return;
        }
        
        // 摆棋模式下不提取走法记录，因为游戏还未开始
        if (activity.chessInfo.IsSetupMode) {
            return;
        }
        
        // 清空现有的走法记录，确保不会添加到原有记录后面
        notation.getMoveRecords().clear();
        
        // 创建一个临时列表来存储所有ChessInfo对象，而不修改原栈
        java.util.List<ChessInfo> tempList = new java.util.ArrayList<>();
        java.util.Stack<ChessInfo> originalStack = new java.util.Stack<>();
        
        // 先将所有ChessInfo对象弹出到临时列表，同时保存到原始栈
        while (!activity.infoSet.preInfo.empty()) {
            ChessInfo info = activity.infoSet.preInfo.pop();
            tempList.add(info);
            originalStack.push(info);
        }
        
        // 恢复原栈
        while (!originalStack.empty()) {
            activity.infoSet.preInfo.push(originalStack.pop());
        }
        
        // 按照临时列表的顺序处理，保证走法记录顺序正确
        // 从索引1开始，跳过初始位置（setup position）
        for (int i = tempList.size() - 1; i >= 0; i--) {
            ChessInfo info = tempList.get(i);
            // 只有当prePos和curPos都不为null时，才添加到走法记录
            if (info.prePos != null && info.curPos != null) {
                addMoveToNotation(notation, info);
            }
        }
    }
    
    // 将走法添加到棋谱
    private void addMoveToNotation(ChessNotation notation, ChessInfo info) {
        if (info.prePos == null || info.curPos == null) {
            return;
        }
        
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
            MoveSimulator moveSimulator = new MoveSimulator(activity);
            String move = moveSimulator.generateMoveString(info, piece, info.prePos, info.curPos, isRed);
            
            if (move != null) {
                if (isRed) {
                    // 红方走法，添加新记录
                    notation.addMoveRecord(move, "");
                } else {
                    // 黑方走法，更新最后一条记录
                    if (!notation.getMoveRecords().isEmpty()) {
                        ChessNotation.MoveRecord lastRecord = notation.getMoveRecords().get(notation.getMoveRecords().size() - 1);
                        if (lastRecord.blackMove.isEmpty()) {
                            lastRecord.blackMove = move;
                        }
                    } else {
                        // 如果没有红方走法，单独添加黑方走法
                        notation.addMoveRecord("", move);
                    }
                }
            }
        }
    }
    
    // 上一步
    public void handlePrevButton() {
        System.out.println("PvMActivity: 点击上一步按钮");
        if (currentNotation != null) {
            java.util.List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();
            System.out.println("PvMActivity: 当前步数: " + currentMoveIndex);
            if (currentMoveIndex > 0) {
                currentMoveIndex--;
                System.out.println("PvMActivity: 执行上一步，新步数: " + currentMoveIndex);
                // 重新生成棋盘状态
                BoardStateGenerator boardStateGenerator = new BoardStateGenerator(activity);
                boardStateGenerator.generateBoardStateFromNotation(currentNotation, currentMoveIndex);
                // 显示当前步数信息
                updateMoveInfoDisplay();
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
        if (currentNotation != null) {
            java.util.List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();
            int moveRecordsSize = moveRecords != null ? moveRecords.size() : 0;
            int totalMoves = moveRecordsSize * 2;
            System.out.println("PvMActivity: 当前步数: " + currentMoveIndex + ", 总步数: " + totalMoves);
            if (moveRecords != null && !moveRecords.isEmpty() && currentMoveIndex < totalMoves) {
                currentMoveIndex++;
                System.out.println("PvMActivity: 执行下一步，新步数: " + currentMoveIndex);
                // 重新生成棋盘状态
                BoardStateGenerator boardStateGenerator = new BoardStateGenerator(activity);
                boardStateGenerator.generateBoardStateFromNotation(currentNotation, currentMoveIndex);
                // 显示当前步数信息
                updateMoveInfoDisplay();
            } else {
                System.out.println("PvMActivity: 已经是最后一步");
            }
        } else {
            System.out.println("PvMActivity: 没有加载棋谱");
        }
    }
    
    // 更新步数信息显示
    private void updateMoveInfoDisplay() {
        NotationUIUpdater uiUpdater = new NotationUIUpdater(activity);
        uiUpdater.updateMoveInfoDisplay(currentNotation, currentMoveIndex);
    }
}