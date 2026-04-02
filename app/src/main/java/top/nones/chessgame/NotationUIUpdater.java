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
                
                // 在RoundView中显示步数信息，保留支招信息
                final String finalNotationInfo = notationInfo.toString();
                activity.runOnUiThread(() -> {
                    if (activity.roundView != null) {
                        // 只设置步数信息，不影响支招信息
                        activity.roundView.setMoveInfoText(finalNotationInfo);
                    }
                });
            }
        }
    }
}