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
    
    // 生成棋盘状态
    public void generateBoardStateFromNotation() {
        BoardStateGenerator boardStateGenerator = new BoardStateGenerator(activity);
        boardStateGenerator.generateBoardStateFromNotation(notationManager.getCurrentNotation(), notationManager.getCurrentMoveIndex());
    }
    
    // 生成FEN
    public String generateFEN(ChessInfo chessInfo) {
        FENHandler fenHandler = new FENHandler();
        return fenHandler.generateFENForSave(chessInfo, notationManager.getSetupFEN(), notationManager.getCurrentNotation());
    }
}
