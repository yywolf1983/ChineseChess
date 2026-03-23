package top.nones.chessgame;

import Info.ChessNotation;

public class NotationUIUpdater {
    private PvMActivity activity;
    
    public NotationUIUpdater(PvMActivity activity) {
        this.activity = activity;
    }
    
    // 更新步数信息显示
    public void updateMoveInfoDisplay(ChessNotation currentNotation, int currentMoveIndex) {
        if (activity != null) {
            // 确保aiInfoTextView被初始化
            if (activity.aiInfoTextView == null && activity.aiManager != null) {
                activity.aiManager.initAIInfoTextView();
            }
            
            if (activity.aiInfoTextView != null) {
                if (currentNotation != null) {
                    java.util.List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();
                    int totalMoves = moveRecords != null ? moveRecords.size() * 2 : 0;
                    
                    // 构建当前棋谱信息
                    StringBuilder notationInfo = new StringBuilder();
                    notationInfo.append("第 ").append(currentMoveIndex).append(" 步 / 共 " ).append(totalMoves).append(" 步");
                    
                    // 如果有当前步的走法，也显示出来
                    if (currentMoveIndex > 0 && moveRecords != null && !moveRecords.isEmpty()) {
                        int recordIndex = (currentMoveIndex - 1) / 2;
                        boolean isBlackMove = (currentMoveIndex % 2 == 0);
                        
                        if (recordIndex < moveRecords.size()) {
                            ChessNotation.MoveRecord record = moveRecords.get(recordIndex);
                            notationInfo.append(" | ");
                            if (isBlackMove && !record.blackMove.isEmpty()) {
                                notationInfo.append("黑方: " ).append(record.blackMove);
                            } else if (!isBlackMove && !record.redMove.isEmpty()) {
                                notationInfo.append("红方: " ).append(record.redMove);
                            }
                        }
                    }
                    
                    // 使用aiInfoTextView显示棋谱信息
                    final String finalNotationInfo = notationInfo.toString();
                    activity.runOnUiThread(() -> {
                        activity.aiInfoTextView.setText(finalNotationInfo);
                    });
                } else {
                    // 没有加载棋谱，恢复默认文本
                    activity.runOnUiThread(() -> {
                        activity.aiInfoTextView.setText("点击支招-AI建议");
                    });
                }
            }
        }
    }
}