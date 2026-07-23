package top.nones.chessgame;

import android.net.Uri;
import Info.ChessInfo;
import Info.ChessNotation;

public class PvMActivityNotation {
    private PvMActivity activity;
    private NotationManager notationManager;
    
    public PvMActivityNotation(PvMActivity activity) {
        this.activity = activity;
        this.notationManager = new NotationManager(activity);
    }
    
    public ChessNotation getCurrentNotation() {
        return notationManager.getCurrentNotation();
    }
    
    public void setCurrentNotation(ChessNotation notation) {
        notationManager.setCurrentNotation(notation);
    }
    
    public int getCurrentMoveIndex() {
        return notationManager.getCurrentMoveIndex();
    }
    
    public void setCurrentMoveIndex(int index) {
        notationManager.setCurrentMoveIndex(index);
    }
    
    // 是否处于纯棋谱回放模式（玩家尚未手动接管）
    public boolean isReplayMode() {
        return notationManager.isReplayMode();
    }

    // 是否已脱离棋谱主线（玩家手动走子与原谱不符，进入接管状态）
    public boolean isDiverged() {
        return notationManager.isDiverged();
    }
    
    // 设置回放模式（玩家手动落子接管后设为 false）
    public void setReplayMode(boolean replay) {
        notationManager.setReplayMode(replay);
    }
    
    // 玩家在加载的棋谱基础上手动落子（接管）：追加走法并推进回放指针
    public void appendManualMove(String move, boolean isRed) {
        notationManager.appendManualMove(move, isRed);
    }
    
    public String getSetupFEN() {
        return notationManager.getSetupFEN();
    }
    
    public void setSetupFEN(String fen) {
        notationManager.setSetupFEN(fen);
    }
    
    // 显示保存棋谱对话框
    public void showSaveNotationDialog() {
        notationManager.showSaveNotationDialog();
    }
    
    // 显示加载棋谱对话框
    public void showLoadNotationDialog() {
        notationManager.showLoadNotationDialog();
    }
    
    // 从URI加载棋谱
    public void loadChessNotationFromUri(Uri uri) {
        notationManager.loadChessNotationFromUri(uri);
    }
    
    // 保存棋谱到URI
    public void saveChessNotationToUri(Uri uri) {
        notationManager.saveChessNotationToUri(uri);
    }
    
    // 上一步
    public void handlePrevButton() {
        notationManager.handlePrevButton();
    }
    
    // 下一步
    public void handleNextButton() {
        notationManager.handleNextButton();
    }
    
    // 同步上一步/下一步按钮的可用状态
    public void updateNavButtonsEnabled() {
        notationManager.updateNavButtonsEnabled();
    }
    
    // 生成棋盘状态
    public void generateBoardStateFromNotation() {
        BoardStateGenerator boardStateGenerator = new BoardStateGenerator(activity);
        boardStateGenerator.generateBoardStateFromNotation(notationManager.buildNavNotation(), notationManager.getCurrentMoveIndex());
    }
    
    // 生成FEN
    public String generateFEN(ChessInfo chessInfo) {
        FENHandler fenHandler = new FENHandler();
        return fenHandler.generateFENForSave(chessInfo, notationManager.getSetupFEN(), notationManager.getCurrentNotation());
    }
}
