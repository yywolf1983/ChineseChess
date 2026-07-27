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
import Utils.LogUtils;

public class NotationManager {
    private PvMActivity activity;
    private ChessNotation currentNotation;
    private int currentMoveIndex = 0;
    // 回放模式：true=纯棋谱回放（点下一步/上一步，未手动落子）；
    // false=玩家已在加载的棋谱基础上手动落子，接管为实时对局
    private boolean replayMode = true;
    // 棋谱原始总步数（不随手动接管改写），用于「下一步」边界判断
    private int originalTotalMoves = 0;
    // 分歧点：手动接管时所处的已走步数（分歧前最后一步），-1 表示未分歧
    private int divergeAt = -1;
    // 是否已偏离棋谱主线（手动落子后偏离则下一步置灰）
    private boolean diverged = false;
    // 加载棋谱的不可变副本：用于导航重建与「是否与原谱重合」判定（不被接管改写）
    private ChessNotation originalNotation = null;
    // 分歧后的手动接管走法（按落子先后），仅用于偏离主线时的局面重建
    private java.util.List<String> manualMoves = new java.util.ArrayList<>();
    private java.util.List<Boolean> manualMoveIsRed = new java.util.ArrayList<>();
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
        // 加载/切换棋谱：进入纯回放模式（玩家手动落子前）
        this.replayMode = true;
        // 保留一份不可变原谱副本（用于导航重建与重合判定），并清除分歧状态
        this.manualMoves.clear();
        this.manualMoveIsRed.clear();
        this.divergeAt = -1;
        this.diverged = false;
        copyOriginalNotation(notation);
        // 重置当前步数为0
        this.currentMoveIndex = 0;
        // 不使用棋谱内嵌评分：加载后清空曲线，每一步由引擎实时评估累积
        if (activity.chessInfo != null) {
            activity.chessInfo.evalHistory.clear();
            activity.chessInfo.currentEvaluation = 0;
        }
        // 显示初始棋谱信息
        updateMoveInfoDisplay();
        // 同步上一步/下一步按钮的可用状态
        updateNavButtonsEnabled();
        if (activity.chessInfo != null) {
            activity.refreshScoreCurve();
        }
    }
    
    public int getCurrentMoveIndex() {
        return currentMoveIndex;
    }
    
    public void setCurrentMoveIndex(int index) {
        this.currentMoveIndex = index;
        // 同步上一步/下一步按钮的可用状态
        updateNavButtonsEnabled();
    }

    /** 是否处于纯棋谱回放模式（玩家尚未手动接管） */
    public boolean isReplayMode() {
        return replayMode;
    }

    /** 是否已脱离棋谱主线（玩家手动走子与原谱不符，进入接管状态） */
    public boolean isDiverged() {
        return diverged;
    }

    /** 设置回放模式（玩家手动落子接管后设为 false） */
    public void setReplayMode(boolean replay) {
        this.replayMode = replay;
    }

    /**
     * 玩家在加载的棋谱基础上手动落子（接管）：把这一手追加进棋谱走法，
     * 并推进回放指针，使后续「上一步/下一步」能在接管后的范围内正确导航，
     * 评分曲线也能继续延伸。
     *
     * @param move  本手记谱串（如 "炮二平五"）
     * @param isRed 本手是否红方走子（用于红黑配对）
     */
    public void appendManualMove(String move, boolean isRed) {
        if (currentNotation == null || move == null || move.isEmpty()) return;

        if (!diverged) {
            // 判断本手是否与原谱下一步「重合」：重合则视为继续棋谱主线（不接管）
            boolean coincide = false;
            String notationNext = getNotationMoveAtPly(currentMoveIndex + 1);
            if (notationNext != null) {
                // 注意：此刻 activity.chessInfo 已是「手动落子后」的真实局面（落子在调用本方法前已生效）。
                // 因此不要再重新解析/模拟手动走法（易因记谱歧义或解析差异导致误判），
                // 而是：从「落子前」局面出发，仅模拟“原谱下一步”，再与真实落子后局面对比棋子分布。
                ChessInfo preBoard = buildPreMoveBoard();
                if (preBoard != null) {
                    MoveSimulator sim = new MoveSimulator(activity);
                    boolean isRedNext = isRedForPly(currentMoveIndex + 1);
                    ChessInfo notatedNext = sim.simulateMove(preBoard, notationNext, isRedNext);
                    if (notatedNext != null && piecesEqual(notatedNext, activity.chessInfo)) {
                        coincide = true;
                    }
                }
            }
            if (coincide) {
                // 手动走子与原谱一致：仍属棋谱主线，直接前进，不接管、不改写棋谱
                currentMoveIndex++;
                updateMoveInfoDisplay();
                updateNavButtonsEnabled();
                return;
            }
            // 与棋谱不符：首次接管。截断原棋谱到当前位置（供保存），记录分歧点。
            if (replayMode) {
                java.util.List<ChessNotation.MoveRecord> records = currentNotation.getMoveRecords();
                int keepRecords = (currentMoveIndex + 1) / 2; // 当前步数对应的记录条数（每条含红黑两步）
                while (records.size() > keepRecords) {
                    records.remove(records.size() - 1);
                }
                // 原内嵌评分序列已与截断后的棋谱不一致，清空以免保存/重建时错位
                currentNotation.embeddedEvalSeries = null;
                replayMode = false;
            }
            divergeAt = currentMoveIndex;
            diverged = true;
            manualMoves.add(move);
            manualMoveIsRed.add(isRed);
            appendMoveToCurrentNotation(move, isRed);
            currentMoveIndex++;
            updateNavButtonsEnabled();
            return;
        }

        // 已分歧（接管中）：棋局继续，记录手动走法，下一步保持置灰
        manualMoves.add(move);
        manualMoveIsRed.add(isRed);
        appendMoveToCurrentNotation(move, isRed);
        currentMoveIndex++;
        updateNavButtonsEnabled();
    }

    // 把一手走法并入当前棋谱（供保存/显示使用），红黑配对写入记录
    private void appendMoveToCurrentNotation(String move, boolean isRed) {
        java.util.List<ChessNotation.MoveRecord> records = currentNotation.getMoveRecords();
        if (isRed) {
            // 红方走法：新建一条走法记录
            currentNotation.addMoveRecord(move, "");
        } else {
            // 黑方走法：优先填入上一条记录的黑方位，否则单独新建
            if (!records.isEmpty()) {
                ChessNotation.MoveRecord last = records.get(records.size() - 1);
                if (last.blackMove == null || last.blackMove.isEmpty()) {
                    last.blackMove = move;
                } else {
                    currentNotation.addMoveRecord("", move);
                }
            } else {
                currentNotation.addMoveRecord("", move);
            }
        }
    }
    
    public String getSetupFEN() {
        return setupFEN;
    }
    
    public void setSetupFEN(String fen) {
        this.setupFEN = fen;
    }
    
    // 显示保存棋谱对话框
    public void showSaveNotationDialog() {
        SaveNotationDialog dialog = new SaveNotationDialog(activity, (fileName, redPlayer, blackPlayer,
                date, location, event, round) -> {
            // 保存信息到成员变量，不包含.pgn扩展名
            pendingSaveFileName = fileName;
            pendingSaveRedPlayer = redPlayer;
            pendingSaveBlackPlayer = blackPlayer;
            pendingSaveDate = date;
            pendingSaveLocation = location;
            pendingSaveEvent = event;
            pendingSaveRound = round;

            // 为Intent添加.pgn扩展名，确保保存的文件有正确的后缀名
            String intentFileName = fileName.toLowerCase().endsWith(".pgn") ? fileName : fileName + ".pgn";

            // 使用SAF打开文件保存选择器
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_TITLE, intentFileName);
            activity.startActivityForResult(intent, 1003);
        });
        dialog.show();
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
        try (InputStream inputStream = activity.getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                }
                
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
                    // 加载/切换棋谱：进入纯回放模式（玩家手动落子前）
                    this.replayMode = true;
                    // 保留一份不可变原谱副本，并清除分歧状态
                    this.manualMoves.clear();
                    this.manualMoveIsRed.clear();
                    this.divergeAt = -1;
                    this.diverged = false;
                    copyOriginalNotation(notation);
                    // 初始化棋盘状态为初始状态
                    activity.chessInfo = new ChessInfo();
                    activity.infoSet = new Info.InfoSet();
                    // 不使用棋谱内嵌评分：加载后清空曲线，每一步由引擎实时评估累积
                    activity.chessInfo.evalHistory.clear();
                    activity.chessInfo.currentEvaluation = 0;
                    if (activity.setting != null) {
                        activity.chessInfo.setting = activity.setting;
                    }
                    if (activity.chessView != null) {
                        activity.chessView.setChessInfo(activity.chessInfo);
                    }
                    if (activity.roundView != null) {
                        activity.roundView.setChessInfo(activity.chessInfo);
                    }
                    currentMoveIndex = 0;
                    activity.continueGameRoundCount = 0;
                    // 更新setupFEN为加载的棋谱的FEN，清除之前的残留信息
                    setupFEN = notation.getFen();
                    
                    // 生成棋盘状态
                    BoardStateGenerator boardStateGenerator = new BoardStateGenerator(activity);
                    boardStateGenerator.generateBoardStateFromNotation(currentNotation, currentMoveIndex);
                    
                    // 加载后重置显示信息（第 0 步 / 共 N 步）
                    updateMoveInfoDisplay();
                    // 同步上一步/下一步按钮的可用状态
                    updateNavButtonsEnabled();
                    
                    if (activity.chessView != null) {
                        activity.chessView.requestDraw();
                    }
                    if (activity.roundView != null) {
                        activity.roundView.requestDraw();
                    }
                    // 加载后曲线初始为空，将随「下一步」逐步行棋记录（按 round 评分每步一点）
                    activity.refreshScoreCurve();
                    // 移除Toast提示，通过界面显示加载成功信息
                } else {
                    // 移除Toast提示，通过界面显示格式错误信息
                }
            }
        } catch (Exception e) {
            LogUtils.e("NotationManager", "加载棋谱失败", e);
            // 移除Toast提示，通过界面显示加载失败信息
        }
    }
    
    // 保存棋谱到URI
    public void saveChessNotationToUri(Uri uri) {
        if (activity == null) {
            return;
        }
        
        if (uri == null) {
            // 移除Toast提示，通过界面显示路径无效信息
            return;
        }
        
        try {
            // 使用保存对话框中输入的信息，并确保加上.pgn扩展名
            String fileName = pendingSaveFileName != null ? pendingSaveFileName : "棋谱";
            if (!fileName.toLowerCase().endsWith(".pgn")) {
                fileName += ".pgn";
            }
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
                // 依据开局 FEN 的 turn 字段判定红先/黑先（b=黑先），确保黑先棋谱保存为「黑 红」顺序，与解析逻辑一致
                boolean saveRedFirst = true;
                if (fen != null && !fen.trim().isEmpty()) {
                    String[] fenParts = fen.trim().split("\\s+");
                    if (fenParts.length >= 2) {
                        saveRedFirst = !"b".equalsIgnoreCase(fenParts[1].trim());
                    }
                }
                notation.setRedFirst(saveRedFirst);
            }
            
            // 提取走法记录
            if (activity.chessInfo != null && activity.infoSet != null && activity.infoSet.preInfo != null) {
                extractMoveRecords(notation);
            }
            // 将当前对局真实评分序列写入棋谱，便于回放时直接显示评分曲线
            if (activity.chessInfo != null) {
                notation.embeddedEvalSeries = activity.chessInfo.getEvalSnapshot();
            }
            
            // 生成棋谱内容
            String content = notation.toSaveContent();
            
            // 写入到选择的URI，确保完全覆盖文件内容
            // 先获取文件描述符，然后使用 FileOutputStream 来确保覆盖模式
            android.os.ParcelFileDescriptor pfd = activity.getContentResolver().openFileDescriptor(uri, "w");
            if (pfd != null) {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(pfd.getFileDescriptor());
                     OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {
                    // 先截断为 0，确保完全清空旧内容（部分文档提供方 "w" 模式不会自动截断）
                    fos.getChannel().truncate(0);
                    // 写入新内容
                    writer.write(content);
                    writer.flush();
                    // 再次按当前写入位置截断，确保没有残留原有信息
                    fos.getChannel().truncate(fos.getChannel().position());
                    // 强制刷新文件系统缓存
                    fos.getFD().sync();
                    // 移除Toast提示，通过界面显示保存成功信息
                } catch (Exception e) {
                    LogUtils.e("NotationManager", "保存棋谱写入失败", e);
                    // 移除Toast提示，通过界面显示保存失败信息
                } finally {
                    try {
                        pfd.close();
                    } catch (java.io.IOException e) {
                        LogUtils.e("NotationManager", "关闭文件描述符失败", e);
                    }
                }
            } else {
                // 移除Toast提示，通过界面显示创建文件描述符失败信息
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
            LogUtils.e("NotationManager", "保存棋谱失败", e);
            if (activity != null) {
                // 移除Toast提示，通过界面显示保存失败信息
            }
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
                    if (currentNotation.isRedFirst()) {
                        content.append((i + 1) + ". " + record.redMove + " " + record.blackMove + "\n");
                    } else {
                        content.append((i + 1) + ". " + record.blackMove + " " + record.redMove + "\n");
                    }
                }
            }
        }
        
        content.append("*");
        return content.toString();
    }
    
    // 提取走法记录
    // 设计：始终保存为「单线」棋谱。
    //   - 已加载棋谱（currentNotation != null）：单线 = 行棋位置以前的步子（原谱前 currentMoveIndex 手）
    //       + 以后重新行棋的步子（分歧后手动接管走法）。该单线由 buildNavNotation() 按「上一步/悔棋」回退后的
    //       currentMoveIndex / divergeAt / manualMoves 正确组合，无需再从 preInfo 重复追加
    //       （preInfo 不做回退裁剪，直接追加会造成重复或遗漏）。
    //   - 未加载棋谱（currentNotation == null，全新对局）：所有走法仅存于 infoSet.preInfo，按时间顺序追加。
    private void extractMoveRecords(ChessNotation notation) {
        if (activity.chessInfo == null || activity.infoSet == null || activity.infoSet.preInfo == null) {
            return;
        }

        if (currentNotation != null) {
            // 已加载棋谱：构造单线棋谱（行棋位置以前的步子 + 以后重新行棋的步子）
            ChessNotation nav = buildNavNotation();
            if (nav == null) {
                nav = new ChessNotation();
            }
            if (!diverged && originalNotation != null && currentMoveIndex < originalTotalMoves) {
                // 与原谱主线重合且未到谱尾：仅保留「行棋位置以前的步子」（前 currentMoveIndex 手），单线截断不越界
                ChessNotation truncated = new ChessNotation();
                truncated.setFen(nav.getFen());
                appendFirstPlies(truncated, originalNotation, currentMoveIndex);
                nav = truncated;
            }
            if (nav != null) {
                for (ChessNotation.MoveRecord r : nav.getMoveRecords()) {
                    if (r == null) continue;
                    notation.addMoveRecord(
                            r.redMove != null ? r.redMove : "",
                            r.blackMove != null ? r.blackMove : "");
                }
            }
            return;
        }

        // 未加载棋谱（全新对局）：所有走法仅存于 infoSet.preInfo，按时间顺序追加（悔棋已相应裁剪 preInfo）
        java.util.List<ChessInfo> tempList = new java.util.ArrayList<>();
        java.util.Stack<ChessInfo> originalStack = new java.util.Stack<>();
        while (!activity.infoSet.preInfo.empty()) {
            ChessInfo info = activity.infoSet.preInfo.pop();
            tempList.add(info);
            originalStack.push(info);
        }
        while (!originalStack.empty()) {
            activity.infoSet.preInfo.push(originalStack.pop());
        }
        for (int i = tempList.size() - 1; i >= 0; i--) {
            ChessInfo info = tempList.get(i);
            if (info.prePos != null && info.curPos != null) {
                addMoveToNotation(notation, info);
            }
        }
    }

    // 将 src 的前 plies 手（红黑各计一手）复制到 dst（用于「行棋位置以前的步子」单线截断）
    private void appendFirstPlies(ChessNotation dst, ChessNotation src, int plies) {
        java.util.List<ChessNotation.MoveRecord> recs = src.getMoveRecords();
        if (recs == null || plies <= 0) return;
        int count = 0;
        for (ChessNotation.MoveRecord r : recs) {
            if (r == null) continue;
            if (count >= plies) break;
            boolean redFirst = src.isRedFirst();
            dst.setRedFirst(redFirst);
            String firstMove = redFirst ? r.redMove : r.blackMove;
            String secondMove = redFirst ? r.blackMove : r.redMove;
            if (firstMove != null && !firstMove.isEmpty() && count < plies) {
                if (redFirst) dst.addMoveRecord(firstMove, "");
                else dst.addMoveRecord("", firstMove);
                count++;
            }
            if (secondMove != null && !secondMove.isEmpty() && count < plies) {
                java.util.List<ChessNotation.MoveRecord> drecs = dst.getMoveRecords();
                if (!drecs.isEmpty()) {
                    ChessNotation.MoveRecord last = drecs.get(drecs.size() - 1);
                    if (redFirst) last.blackMove = secondMove;
                    else last.redMove = secondMove;
                } else {
                    if (redFirst) dst.addMoveRecord("", secondMove);
                    else dst.addMoveRecord(secondMove, "");
                }
                count++;
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
                // 先手方（红先=红 / 黑先=黑）开新记录；后手方填入上一条记录对应槽位。
                // 修复黑先时第一步黑着法被孤立成 [red="",black=黑1]、第二步红着法又新建
                // [red=红1,black=""] 导致整盘配对崩坏、第二步被漏记的问题。
                boolean redFirst = notation.isRedFirst();
                boolean opener = (isRed == redFirst);
                if (opener) {
                    // 先手方走法：新开一条记录
                    if (redFirst) {
                        notation.addMoveRecord(move, "");
                    } else {
                        notation.addMoveRecord("", move);
                    }
                } else {
                    // 后手方走法：填入上一条记录的后手槽
                    if (!notation.getMoveRecords().isEmpty()) {
                        ChessNotation.MoveRecord lastRecord = notation.getMoveRecords().get(notation.getMoveRecords().size() - 1);
                        if (redFirst) {
                            if (lastRecord.blackMove.isEmpty()) {
                                lastRecord.blackMove = move;
                            }
                        } else {
                            if (lastRecord.redMove.isEmpty()) {
                                lastRecord.redMove = move;
                            }
                        }
                    } else {
                        // 异常：无先手记录，先单独建一条（塞进后手槽，等待先手补）
                        if (redFirst) {
                            notation.addMoveRecord("", move);
                        } else {
                            notation.addMoveRecord(move, "");
                        }
                    }
                }
            }
        }
    }
    
    // 上一步
    public void handlePrevButton() {
        Utils.LogUtils.d("NotationManager", "点击上一步按钮");
        if (currentNotation != null) {
            java.util.List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();
            Utils.LogUtils.d("NotationManager", "当前步数: " + currentMoveIndex);
            if (currentMoveIndex > 0) {
                currentMoveIndex--;
                // 偏离主线时：逐步回退手动接管走法（悔棋一次退一步）；
                // 回到分歧点（最后与原谱相符的位置）即清除分歧、重置回原谱线（界面/保存同步）。
                if (diverged) {
                    if (currentMoveIndex <= divergeAt) {
                        // 回到分歧点：清除分歧，棋谱重置回原谱线（上一步/下一步随之恢复可见）
                        diverged = false;
                        divergeAt = -1;
                        manualMoves.clear();
                        manualMoveIsRed.clear();
                        copyOriginalNotation(originalNotation);
                        replayMode = true;
                    } else if (!manualMoves.isEmpty()) {
                        manualMoves.remove(manualMoves.size() - 1);
                        manualMoveIsRed.remove(manualMoveIsRed.size() - 1);
                    }
                }
                Utils.LogUtils.d("NotationManager", "执行上一步，新步数: " + currentMoveIndex);
                // 重新生成棋盘状态
                BoardStateGenerator boardStateGenerator = new BoardStateGenerator(activity);
                boardStateGenerator.generateBoardStateFromNotation(buildNavNotation(), currentMoveIndex);
                // 显示当前步数信息
                updateMoveInfoDisplay();
                // 同步导航按钮状态
                updateNavButtonsEnabled();
                // 评估回退后的当前局面，刷新评分显示；曲线随显示裁剪自动缩短
                activity.refreshScoreCurve();
                activity.triggerPositionEvaluation();
            } else {
                Utils.LogUtils.d("NotationManager", "已经是第一步");
            }
        } else {
            Utils.LogUtils.d("NotationManager", "没有加载棋谱");
        }
    }
    
    // 下一步
    public void handleNextButton() {
        Utils.LogUtils.d("NotationManager", "点击下一步按钮");
        if (currentNotation != null) {
            java.util.List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();
            Utils.LogUtils.d("NotationManager", "当前步数: " + currentMoveIndex + ", 总步数: " + originalTotalMoves);
            if (moveRecords != null && !moveRecords.isEmpty() && canGoNext()) {
                currentMoveIndex++;
                Utils.LogUtils.d("NotationManager", "执行下一步，新步数: " + currentMoveIndex);
                // 重新生成棋盘状态
                BoardStateGenerator boardStateGenerator = new BoardStateGenerator(activity);
                boardStateGenerator.generateBoardStateFromNotation(buildNavNotation(), currentMoveIndex);
                // 显示当前步数信息
                updateMoveInfoDisplay();
                // 同步导航按钮状态
                updateNavButtonsEnabled();
                // 评估前进后的当前局面，引擎评分追加一个曲线点
                activity.refreshScoreCurve();
                activity.triggerPositionEvaluation();
            } else {
                Utils.LogUtils.d("NotationManager", "已经是最后一步");
            }
        } else {
            Utils.LogUtils.d("NotationManager", "没有加载棋谱");
        }
    }

    // 跳转到任意一步（0..originalTotalMoves）：回到原谱主线指定步，清除分歧/手动接管
    public void seekTo(int index) {
        Utils.LogUtils.d("NotationManager", "跳转至指定步: " + index);
        if (currentNotation == null) {
            Utils.LogUtils.d("NotationManager", "没有加载棋谱");
            return;
        }
        if (index < 0) index = 0;
        if (index > originalTotalMoves) index = originalTotalMoves;
        if (index == currentMoveIndex && !diverged) {
            return;
        }
        // 任意跳转即回到原谱主线该步：清除可能的分歧/手动接管
        diverged = false;
        divergeAt = -1;
        manualMoves.clear();
        manualMoveIsRed.clear();
        copyOriginalNotation(originalNotation);
        replayMode = true;
        currentMoveIndex = index;
        Utils.LogUtils.d("NotationManager", "执行跳转，新步数: " + currentMoveIndex);

        BoardStateGenerator boardStateGenerator = new BoardStateGenerator(activity);
        boardStateGenerator.generateBoardStateFromNotation(buildNavNotation(), currentMoveIndex);
        updateMoveInfoDisplay();
        updateNavButtonsEnabled();
        activity.refreshScoreCurve();          // 重新计算评分曲线
        activity.triggerPositionEvaluation();  // 触发当前局面的引擎评估
    }

    // 本次棋谱的原谱总步数（进度条最大值）
    public int getOriginalTotalMoves() {
        return originalTotalMoves;
    }

    // 根据棋谱加载状态与回放进度，更新「上一步/下一步/悔棋」按钮的可用性
    public void updateNavButtonsEnabled() {
        if (activity == null) {
            return;
        }

        android.widget.Button prev = activity.btnPrev;
        android.widget.Button next = activity.btnNext;
        android.widget.Button recall = activity.btnRecall;

        // 悔棋可用条件：① 未加载棋谱（正常对局，用史实栈撤销）；
        // ② 已加载棋谱但「脱离主线」接管走子——此时用悔棋逐步回退接管走法，
        //    回到脱离点前即恢复回放；仍处纯回放主线时悔棋置灰（由「上一步/下一步」导航）。
        if (recall != null) {
            boolean recallEnabled = (currentNotation == null) || diverged;
            recall.setEnabled(recallEnabled);
            recall.setAlpha(recallEnabled ? 1f : 0.4f);
        }

        if (prev == null || next == null) {
            return;
        }
        if (currentNotation == null) {
            // 未加载棋谱：上一步/下一步不可用（隐藏；悔棋可用，见上方）
            prev.setVisibility(android.view.View.GONE);
            next.setVisibility(android.view.View.GONE);
            return;
        }
        // 已脱离主线：上一步/下一步均隐藏，回退交由「悔棋」按钮逐步退回，直至回到脱离点（主线）；
        // 回到主线后，上一步/下一步恢复可见可用。
        if (diverged) {
            prev.setVisibility(android.view.View.GONE);
            next.setVisibility(android.view.View.GONE);
            return;
        }
        boolean prevEnabled = currentMoveIndex > 0;
        boolean nextEnabled = canGoNext();
        prev.setVisibility(prevEnabled ? android.view.View.VISIBLE : android.view.View.GONE);
        next.setVisibility(nextEnabled ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    // 是否可前进到「下一步」：
    // - 已偏离棋谱主线（手动落子后分歧）则无下一步，置灰；
    // - 在棋谱主线上则可继续，直至原谱总步数。
    public boolean canGoNext() {
        if (currentNotation == null) return false;
        if (diverged) return false;
        return currentMoveIndex < originalTotalMoves;
    }

    // 第 ply 手（1-based）是否红方走子：红先奇数手为红，黑先奇数手为黑
    private boolean isRedForPly(int ply) {
        boolean redFirst = (currentNotation != null) ? currentNotation.isRedFirst() : true;
        return redFirst ? (ply % 2 == 1) : (ply % 2 == 0);
    }

    // 取原谱第 ply 手（1-based）的记谱串（用于判断手动落子是否与原谱重合，不受接管改写影响）
    private String getNotationMoveAtPly(int targetPly) {
        if (originalNotation == null) return null;
        java.util.List<ChessNotation.MoveRecord> recs = originalNotation.getMoveRecords();
        if (recs == null) return null;
        boolean redFirst = (originalNotation != null) ? originalNotation.isRedFirst() : true;
        int ply = 0;
        for (ChessNotation.MoveRecord r : recs) {
            if (r == null) continue;
            String firstMove = redFirst ? r.redMove : r.blackMove;
            String secondMove = redFirst ? r.blackMove : r.redMove;
            if (firstMove != null && !firstMove.isEmpty()) {
                ply++;
                if (ply == targetPly) return firstMove;
            }
            if (secondMove != null && !secondMove.isEmpty()) {
                ply++;
                if (ply == targetPly) return secondMove;
            }
        }
        return null;
    }

    // 重建「当前步数对应」的棋盘（即手动落子前的局面）：从导航棋谱 FEN 起回放前 currentMoveIndex 手。
    // 由于 appendManualMove 被调用时 activity.chessInfo 已是落子后的局面，若要判断本手是否与原谱重合，
    // 必须先回到落子前的局面，再分别模拟「原谱下一步」与「手动走法」对比其结果。
    private ChessInfo buildPreMoveBoard() {
        ChessNotation nav = buildNavNotation();
        if (nav == null) return null;
        ChessInfo board;
        String fen = nav.getFen();
        if (fen != null && !fen.isEmpty()) {
            FENHandler fenHandler = new FENHandler();
            board = fenHandler.fenToChessInfo(fen);
        } else {
            board = new ChessInfo();
        }
        if (board == null) return null;
        java.util.List<ChessNotation.MoveRecord> recs = nav.getMoveRecords();
        if (recs != null) {
            MoveSimulator sim = new MoveSimulator(activity);
            int count = 0;
            for (ChessNotation.MoveRecord r : recs) {
                if (r == null) continue;
                if (count >= currentMoveIndex) break;
                boolean redFirst = nav.isRedFirst();
                String firstMove = redFirst ? r.redMove : r.blackMove;
                String secondMove = redFirst ? r.blackMove : r.redMove;
                if (firstMove != null && !firstMove.isEmpty() && count < currentMoveIndex) {
                    ChessInfo t = sim.simulateMove(board, firstMove, redFirst);
                    if (t != null) { board = t; count++; }
                }
                if (secondMove != null && !secondMove.isEmpty() && count < currentMoveIndex) {
                    ChessInfo t = sim.simulateMove(board, secondMove, !redFirst);
                    if (t != null) { board = t; count++; }
                }
            }
        }
        return board;
    }

    // 构建用于导航重建的棋谱：未分歧时返回不可变原谱；分歧后返回「原谱前 divergeAt 手 + 手动接管走法」
    public ChessNotation buildNavNotation() {
        if (!diverged || originalNotation == null) {
            return originalNotation;
        }
        ChessNotation nav = new ChessNotation();
        nav.setFen(originalNotation.getFen());
        nav.setRedFirst(originalNotation.isRedFirst());
        // 复制原谱前 divergeAt 手
        int ply = 0;
        java.util.List<ChessNotation.MoveRecord> orig = originalNotation.getMoveRecords();
        if (orig != null) {
            for (ChessNotation.MoveRecord r : orig) {
                if (ply >= divergeAt) break;
                boolean redFirst = nav.isRedFirst();
                String firstMove = redFirst ? r.redMove : r.blackMove;
                String secondMove = redFirst ? r.blackMove : r.redMove;
                if (firstMove != null && !firstMove.isEmpty() && ply < divergeAt) {
                    if (redFirst) nav.addMoveRecord(firstMove, "");
                    else nav.addMoveRecord("", firstMove);
                    ply++;
                }
                if (secondMove != null && !secondMove.isEmpty() && ply < divergeAt) {
                    java.util.List<ChessNotation.MoveRecord> nrecs = nav.getMoveRecords();
                    if (!nrecs.isEmpty()) {
                        ChessNotation.MoveRecord last = nrecs.get(nrecs.size() - 1);
                        if (redFirst) last.blackMove = secondMove;
                        else last.redMove = secondMove;
                    }
                    ply++;
                }
            }
        }
        // 追加手动接管走法（按先手方配对：红先=红开 / 黑先=黑开；直接使用记录的真实红黑）
        boolean redFirst = nav.isRedFirst();
        for (int i = 0; i < manualMoves.size(); i++) {
            boolean isRed = manualMoveIsRed.get(i);
            String m = manualMoves.get(i);
            boolean opener = (isRed == redFirst);
            if (opener) {
                if (redFirst) nav.addMoveRecord(m, "");
                else nav.addMoveRecord("", m);
            } else {
                java.util.List<ChessNotation.MoveRecord> nrecs = nav.getMoveRecords();
                if (!nrecs.isEmpty()) {
                    ChessNotation.MoveRecord last = nrecs.get(nrecs.size() - 1);
                    if (redFirst) {
                        if (last.blackMove == null || last.blackMove.isEmpty()) {
                            last.blackMove = m;
                        } else {
                            nav.addMoveRecord("", m);
                        }
                    } else {
                        if (last.redMove == null || last.redMove.isEmpty()) {
                            last.redMove = m;
                        } else {
                            nav.addMoveRecord(m, "");
                        }
                    }
                } else {
                    if (redFirst) nav.addMoveRecord("", m);
                    else nav.addMoveRecord(m, "");
                }
            }
        }
        return nav;
    }

    // 复制一份不可变原谱（仅保留 FEN 与走法记录），供导航重建与重合判定使用
    private void copyOriginalNotation(ChessNotation src) {
        originalNotation = new ChessNotation();
        if (src != null) {
            originalNotation.setFen(src.getFen());
            originalNotation.setRedFirst(src.isRedFirst());
            java.util.List<ChessNotation.MoveRecord> recs = src.getMoveRecords();
            if (recs != null) {
                for (ChessNotation.MoveRecord r : recs) {
                    if (r == null) continue;
                    originalNotation.addMoveRecord(
                            (r.redMove != null) ? r.redMove : "",
                            (r.blackMove != null) ? r.blackMove : "");
                }
            }
        }
        java.util.List<ChessNotation.MoveRecord> orecs = (originalNotation != null) ? originalNotation.getMoveRecords() : null;
        originalTotalMoves = (orecs != null) ? orecs.size() * 2 : 0;
    }

    // 仅比较两个局面的棋子分布是否一致（不比较行棋方标志）。
    // 用于判定手动落子结果是否与原谱下一步一致：此刻真实局面的行棋方尚未切换，
    // 而模拟结果已切换，故不能比较 IsRedGo，仅比对棋子摆放。
    private boolean piecesEqual(ChessInfo a, ChessInfo b) {
        if (a == null || b == null) return false;
        if (a.piece == null || b.piece == null) return false;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                if (a.piece[r][c] != b.piece[r][c]) return false;
            }
        }
        return true;
    }

    // 更新步数信息显示
    private void updateMoveInfoDisplay() {
        NotationUIUpdater uiUpdater = new NotationUIUpdater(activity);
        uiUpdater.updateMoveInfoDisplay(currentNotation, currentMoveIndex);
    }
}