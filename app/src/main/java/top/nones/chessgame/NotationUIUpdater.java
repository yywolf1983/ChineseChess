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
            if (currentNotation == null) {
                // 未加载棋谱（如新局）：清空显示信息
                activity.runOnUiThread(() -> {
                    if (activity.roundView != null) {
                        activity.roundView.setMoveInfoText("");
                    }
                });
                return;
            }
            if (currentNotation != null) {
                java.util.List<ChessNotation.MoveRecord> moveRecords = currentNotation.getMoveRecords();

                // 加载棋谱时顶部只显示「当前走法」（如「红方: 炮二平五」），不显示步数信息
                StringBuilder notationInfo = new StringBuilder();

                if (currentMoveIndex > 0 && moveRecords != null && !moveRecords.isEmpty()) {
                    boolean redFirst = currentNotation.isRedFirst();
                    int recordIndex = (currentMoveIndex - 1) / 2;
                    // 红先：奇数步=红、偶数步=黑；黑先：奇数步=黑、偶数步=红
                    boolean isBlackMove = redFirst ? (currentMoveIndex % 2 == 0) : (currentMoveIndex % 2 == 1);

                    if (recordIndex < moveRecords.size()) {
                        ChessNotation.MoveRecord record = moveRecords.get(recordIndex);
                        if (isBlackMove && !record.blackMove.isEmpty()) {
                            notationInfo.append("黑方: ").append(record.blackMove);
                        } else if (!isBlackMove && !record.redMove.isEmpty()) {
                            notationInfo.append("红方: ").append(record.redMove);
                        }
                    }
                }

                // 在RoundView中显示当前走法，保留支招信息
                final String finalNotationInfo = notationInfo.toString();
                activity.runOnUiThread(() -> {
                    if (activity.roundView != null) {
                        // 仅设置当前走法，不影响支招信息
                        activity.roundView.setMoveInfoText(finalNotationInfo);
                    }
                });
            }
        }
    }
}