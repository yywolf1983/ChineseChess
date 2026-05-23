package Info;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import ChessMove.Move;
import ChessMove.Rule;

public class ChessInfo implements Cloneable, Serializable {
    private static final long serialVersionUID = -8764412462496314495L;
    private static final int MAX_POSITION_HISTORY_SIZE = 100; // 最大局面历史记录数量

    // 线程安全锁对象
    private final transient Object lock = new Object();

    // 使用volatile确保可见性
    public volatile int[][] piece;
    public volatile boolean IsRedGo;
    public volatile Pos prePos;
    public volatile Pos curPos;
    public volatile boolean IsChecked;
    // 使用线程安全的List
    public volatile List<Pos> ret;
    public volatile int[] Select;
    public volatile boolean isMachine;
    public volatile int status;
    public volatile int peaceRound;
    public volatile int totalMoves; // 总走步数
    public volatile int attackNum_B;
    public volatile int attackNum_R;

    public volatile boolean IsSetupMode;
    public volatile Setting setting;
    
    // 支招相关字段（使用volatile确保可见性）
    public volatile Pos suggestFromPos;
    public volatile Pos suggestToPos;
    // 多步支招路线：每条路线是一个Move对象列表（包含from和to）
    // 使用线程安全的List
    public volatile List<Move> suggestMoves;
    // 支招步数标注：格式 "1", "2", "3", "4", "5"（用于棋盘圆圈显示）
    public volatile List<String> suggestMoveLabels;
    // 支招每步是否红方：用于颜色区分
    public volatile List<Boolean> suggestMovesIsRed;
    // 支招走法中文记谱：格式 "炮二平五", "士4进5"等（用于头部显示）
    public volatile List<String> suggestMoveNotations;
    
    // 和棋判断相关字段
    // 使用线程安全的Map
    public volatile Map<String, Integer> positionHistory; // 局面历史记录，用于检测重复局面
    public volatile int consecutiveCheckRed; // 红方连续将军次数
    public volatile int consecutiveCheckBlack; // 黑方连续将军次数
    public volatile boolean lastMoveWasCheck; // 上一步是否为将军
    
    // 长捉检测相关字段
    public volatile int consecutiveAttackRed; // 红方连续攻击同一棋子次数
    public volatile int consecutiveAttackBlack; // 黑方连续攻击同一棋子次数
    public volatile Pos lastAttackedPiecePos; // 上一次被攻击的棋子位置
    public volatile int lastAttackedPieceType; // 上一次被攻击的棋子类型
    
    // 规则判定统计
    public volatile int forbiddenMoveRed; // 红方禁止着法计数
    public volatile int forbiddenMoveBlack; // 黑方禁止着法计数
    
    // 强制变着相关字段
    public volatile boolean forceVariation; // 是否处于强制变着模式
    public volatile int variationRandomness; // 变着随机性等级（1-5）

    public ChessInfo() {
        init();
    }

    // 具名内部类，用于创建带有 DiscardOldestPolicy 的 LinkedHashMap
    private static class PositionHistoryMap extends LinkedHashMap<String, Integer> {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > MAX_POSITION_HISTORY_SIZE;
        }
    }

    private void init() {
        synchronized (lock) {
            piece = new int[10][9];
            // 初始化棋盘
            // 红方（y=0是红方底线，符合整体反转的布局）
            piece[0][0] = 12; // 车
            piece[0][1] = 11; // 马
            piece[0][2] = 10; // 象
            piece[0][3] = 9; // 士
            piece[0][4] = 8; // 帅
            piece[0][5] = 9; // 士
            piece[0][6] = 10; // 象
            piece[0][7] = 11; // 马
            piece[0][8] = 12; // 车
            piece[2][1] = 13; // 炮
            piece[2][7] = 13; // 炮
            piece[3][0] = 14; // 兵
            piece[3][2] = 14; // 兵
            piece[3][4] = 14; // 兵
            piece[3][6] = 14; // 兵
            piece[3][8] = 14; // 兵
            // 黑方（y=9是黑方底线，符合整体反转的布局）
            piece[9][0] = 5; // 车
            piece[9][1] = 4; // 马
            piece[9][2] = 3; // 象
            piece[9][3] = 2; // 士
            piece[9][4] = 1; // 将
            piece[9][5] = 2; // 士
            piece[9][6] = 3; // 象
            piece[9][7] = 4; // 马
            piece[9][8] = 5; // 车
            piece[7][1] = 6; // 炮
            piece[7][7] = 6; // 炮
            piece[6][0] = 7; // 卒
            piece[6][2] = 7; // 卒
            piece[6][4] = 7; // 卒
            piece[6][6] = 7; // 卒
            piece[6][8] = 7; // 卒

            IsRedGo = true;
            prePos = null;
            curPos = null;
            IsChecked = false;
            ret = new CopyOnWriteArrayList<>();
            Select = new int[]{-1, -1};
            isMachine = false;
            status = 1;
            peaceRound = 0;
            totalMoves = 0; // 初始化总走步数
            attackNum_B = 0;
            attackNum_R = 0;

            IsSetupMode = false;
            
            // 初始化支招相关字段
            suggestFromPos = null;
            suggestToPos = null;
            suggestMoves = new CopyOnWriteArrayList<>();
            suggestMoveLabels = new CopyOnWriteArrayList<>();
            suggestMovesIsRed = new CopyOnWriteArrayList<>();
            suggestMoveNotations = new CopyOnWriteArrayList<>();
            
            // 初始化和棋判断相关字段 - 使用线程安全的Map
            positionHistory = Collections.synchronizedMap(new PositionHistoryMap());
            consecutiveCheckRed = 0;
            consecutiveCheckBlack = 0;
            lastMoveWasCheck = false;
            
            // 初始化长捉检测相关字段
            consecutiveAttackRed = 0;
            consecutiveAttackBlack = 0;
            lastAttackedPiecePos = null;
            lastAttackedPieceType = 0;
            
            // 初始化规则判定统计
            forbiddenMoveRed = 0;
            forbiddenMoveBlack = 0;
            
            // 初始化强制变着相关字段
            forceVariation = false;
            variationRandomness = 0;
            
            // 计算初始攻击棋子数量
            calculateAttackPiecesInternal();
            
            // 记录初始局面
            recordCurrentPositionInternal();
        }
    }

    public void setInfo(ChessInfo info) throws CloneNotSupportedException {
        synchronized (lock) {
            // 创建深拷贝的棋盘
            int[][] newPiece = new int[10][9];
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    newPiece[i][j] = info.piece[i][j];
                }
            }
            this.piece = newPiece;
            
            this.IsRedGo = info.IsRedGo;
            this.prePos = info.prePos != null ? (Pos) info.prePos.clone() : null;
            this.curPos = info.curPos != null ? (Pos) info.curPos.clone() : null;
            this.IsChecked = info.IsChecked;
            
            // 使用线程安全的List
            this.ret = new CopyOnWriteArrayList<>();
            for (Pos pos : info.ret) {
                this.ret.add((Pos) pos.clone());
            }
            this.Select = info.Select.clone();
            this.isMachine = info.isMachine;
            this.status = info.status;
            this.peaceRound = info.peaceRound;
            this.totalMoves = info.totalMoves;
            this.attackNum_B = info.attackNum_B;
            this.attackNum_R = info.attackNum_R;

            this.IsSetupMode = info.IsSetupMode;
            this.setting = info.setting;
            this.suggestFromPos = info.suggestFromPos != null ? (Pos) info.suggestFromPos.clone() : null;
            this.suggestToPos = info.suggestToPos != null ? (Pos) info.suggestToPos.clone() : null;
            
            // 复制多步支招相关字段 - 使用线程安全的List
            this.suggestMoves = new CopyOnWriteArrayList<>();
            if (info.suggestMoves != null) {
                for (Move move : info.suggestMoves) {
                    this.suggestMoves.add(new Move(move.fromPos != null ? (Pos) move.fromPos.clone() : null,
                                                   move.toPos != null ? (Pos) move.toPos.clone() : null));
                }
            }
            this.suggestMoveLabels = new CopyOnWriteArrayList<>(info.suggestMoveLabels);
            this.suggestMovesIsRed = new CopyOnWriteArrayList<>(info.suggestMovesIsRed);
            this.suggestMoveNotations = new CopyOnWriteArrayList<>(info.suggestMoveNotations);
            
            // 复制和棋判断相关字段 - 使用线程安全的Map
            this.positionHistory = Collections.synchronizedMap(new PositionHistoryMap());
            this.positionHistory.putAll(info.positionHistory);
            this.consecutiveCheckRed = info.consecutiveCheckRed;
            this.consecutiveCheckBlack = info.consecutiveCheckBlack;
            this.lastMoveWasCheck = info.lastMoveWasCheck;
            
            // 复制长捉检测相关字段
            this.consecutiveAttackRed = info.consecutiveAttackRed;
            this.consecutiveAttackBlack = info.consecutiveAttackBlack;
            this.lastAttackedPiecePos = info.lastAttackedPiecePos != null ? (Pos) info.lastAttackedPiecePos.clone() : null;
            this.lastAttackedPieceType = info.lastAttackedPieceType;
            
            // 复制规则判定统计
            this.forbiddenMoveRed = info.forbiddenMoveRed;
            this.forbiddenMoveBlack = info.forbiddenMoveBlack;
        }
    }

    public void updateAllInfo(Pos prePos, Pos curPos, int piece, int capturedPiece, boolean isCheck) {
        synchronized (lock) {
            updateAllInfoInternal(prePos, curPos, piece, capturedPiece, isCheck);
        }
    }
    
    // 内部版本，不获取锁（用于已持有锁的情况）
    private void updateAllInfoInternal(Pos prePos, Pos curPos, int piece, int capturedPiece, boolean isCheck) {
        // 更新走棋信息
        this.prePos = prePos;
        this.curPos = curPos;
        
        // 清除支招提示线
        suggestFromPos = null;
        suggestToPos = null;
        suggestMoves.clear();
        suggestMoveLabels.clear();
        suggestMovesIsRed.clear();
        suggestMoveNotations.clear();
        
        // 增加总走步数
        totalMoves++;
        
        // 检查是否吃子
        if (capturedPiece != 0) {
            peaceRound = 0;
            // 减少被吃方的攻击棋子数量
            // 黑方攻击棋子：车(5)、马(4)、炮(6)、卒(7)
            if (capturedPiece == 4 || capturedPiece == 5 || capturedPiece == 6 || capturedPiece == 7) {
                attackNum_B--;
            }
            // 红方攻击棋子：车(12)、马(11)、炮(13)、兵(14)
            else if (capturedPiece == 11 || capturedPiece == 12 || capturedPiece == 13 || capturedPiece == 14) {
                attackNum_R--;
            }
        } else {
            peaceRound++;
        }
        
        // 更新长将检测
        updateConsecutiveCheckInternal(isCheck);
        
        // 只有当移动的是攻击性棋子时才更新长捉检测
        int movingPieceType = this.piece[prePos.y][prePos.x];
        if (isAttackingPiece(movingPieceType)) {
            // 更新长捉检测
            updateConsecutiveAttackInternal(prePos, curPos, movingPieceType, capturedPiece);
        } else {
            // 非攻击性移动，重置连续攻击计数
            resetConsecutiveAttackInternal();
        }
        
        // 切换回合
        IsRedGo = !IsRedGo;
        
        // 记录当前局面（在切换回合后记录，确保局面哈希包含回合信息）
        recordCurrentPositionInternal();
    }
    
    // 检查是否是攻击性棋子
    private boolean isAttackingPiece(int pieceType) {
        return pieceType == 4 || pieceType == 5 || pieceType == 6 || pieceType == 7 ||  // 黑方攻击性棋子
               pieceType == 11 || pieceType == 12 || pieceType == 13 || pieceType == 14; // 红方攻击性棋子
    }
    
    // 更新连续将军计数
    public void updateConsecutiveCheck(boolean isCheck) {
        synchronized (lock) {
            updateConsecutiveCheckInternal(isCheck);
        }
    }
    
    private void updateConsecutiveCheckInternal(boolean isCheck) {
        lastMoveWasCheck = isCheck;
        
        if (isCheck) {
            // 当前走棋方将军
            if (!IsRedGo) {
                // 红方走棋并将军
                consecutiveCheckRed++;
                consecutiveCheckBlack = 0; // 重置对方的连续将军计数
            } else {
                // 黑方走棋并将军
                consecutiveCheckBlack++;
                consecutiveCheckRed = 0; // 重置对方的连续将军计数
            }
        } else {
            // 没有将军，重置当前走棋方的连续将军计数
            if (!IsRedGo) {
                consecutiveCheckRed = 0;
            } else {
                consecutiveCheckBlack = 0;
            }
        }
    }
    
    // 更新连续攻击（长捉）计数
    private void updateConsecutiveAttackInternal(Pos fromPos, Pos toPos, int movingPieceType, int capturedPiece) {
        // 检查是否是攻击性移动（移动后位置有棋子被吃，或者移动的棋子是攻击性棋子）
        boolean isAttackMove = false;
        
        // 如果是攻击性棋子且有吃子，或者攻击性棋子移动到可以攻击对方棋子的位置
        if (capturedPiece != 0) {
            isAttackMove = true;
        } else {
            // 检查移动后是否可以攻击对方棋子
            List<Pos> possibleAttacks = Rule.PossibleMoves(piece, toPos.x, toPos.y, movingPieceType);
            if (!possibleAttacks.isEmpty()) {
                for (Pos attackPos : possibleAttacks) {
                    int targetPiece = piece[attackPos.y][attackPos.x];
                    if (targetPiece != 0) {
                        // 检查是否是对方棋子
                        boolean movingPieceIsRed = movingPieceType >= 8 && movingPieceType <= 14;
                        boolean targetPieceIsRed = targetPiece >= 8 && targetPiece <= 14;
                        if (movingPieceIsRed != targetPieceIsRed) {
                            isAttackMove = true;
                            break;
                        }
                    }
                }
            }
        }
        
        if (isAttackMove) {
            // 检查是否在攻击同一棋子
            if (lastAttackedPiecePos != null && lastAttackedPieceType != 0) {
                // 检查当前攻击的棋子是否与上次攻击的是同一棋子
                if (capturedPiece != 0 && capturedPiece == lastAttackedPieceType) {
                    // 吃掉了上次攻击的棋子，重置计数
                    resetConsecutiveAttackInternal();
                } else {
                    // 检查是否在攻击同一位置
                    List<Pos> possibleAttacks = Rule.PossibleMoves(piece, toPos.x, toPos.y, movingPieceType);
                    boolean attackingSamePiece = false;
                    for (Pos attackPos : possibleAttacks) {
                        if (attackPos.equals(lastAttackedPiecePos) && piece[attackPos.y][attackPos.x] == lastAttackedPieceType) {
                            attackingSamePiece = true;
                            break;
                        }
                    }
                    
                    if (attackingSamePiece) {
                        // 连续攻击同一棋子
                        if (!IsRedGo) {
                            consecutiveAttackRed++;
                            consecutiveAttackBlack = 0; // 重置对方的连续攻击计数
                        } else {
                            consecutiveAttackBlack++;
                            consecutiveAttackRed = 0; // 重置对方的连续攻击计数
                        }
                    } else {
                        // 攻击不同的棋子，重置计数
                        resetConsecutiveAttackInternal();
                        // 记录新的被攻击棋子
                        for (Pos attackPos : possibleAttacks) {
                            int targetPiece = piece[attackPos.y][attackPos.x];
                            if (targetPiece != 0) {
                                boolean movingPieceIsRed = movingPieceType >= 8 && movingPieceType <= 14;
                                boolean targetPieceIsRed = targetPiece >= 8 && targetPiece <= 14;
                                if (movingPieceIsRed != targetPieceIsRed) {
                                    lastAttackedPiecePos = attackPos;
                                    lastAttackedPieceType = targetPiece;
                                    break;
                                }
                            }
                        }
                    }
                }
            } else {
                // 第一次攻击，记录被攻击的棋子
                List<Pos> possibleAttacks = Rule.PossibleMoves(piece, toPos.x, toPos.y, movingPieceType);
                for (Pos attackPos : possibleAttacks) {
                    int targetPiece = piece[attackPos.y][attackPos.x];
                    if (targetPiece != 0) {
                        boolean movingPieceIsRed = movingPieceType >= 8 && movingPieceType <= 14;
                        boolean targetPieceIsRed = targetPiece >= 8 && targetPiece <= 14;
                        if (movingPieceIsRed != targetPieceIsRed) {
                            lastAttackedPiecePos = attackPos;
                            lastAttackedPieceType = targetPiece;
                            break;
                        }
                    }
                }
            }
        } else {
            // 非攻击性移动，重置连续攻击计数
            resetConsecutiveAttackInternal();
        }
    }
    
    // 重置连续攻击计数
    private void resetConsecutiveAttackInternal() {
        consecutiveAttackRed = 0;
        consecutiveAttackBlack = 0;
        lastAttackedPiecePos = null;
        lastAttackedPieceType = 0;
    }
    
    // 记录当前局面
    public void recordCurrentPosition() {
        synchronized (lock) {
            recordCurrentPositionInternal();
        }
    }
    
    private void recordCurrentPositionInternal() {
        String positionHash = generatePositionHashInternal();
        Integer count = positionHistory.get(positionHash);
        if (count == null) {
            count = 0;
        }
        positionHistory.put(positionHash, count + 1);
    }
    
    // 生成局面哈希（优化版本）
    public String generatePositionHash() {
        synchronized (lock) {
            return generatePositionHashInternal();
        }
    }
    
    private String generatePositionHashInternal() {
        // 使用StringBuilder的预分配容量，减少扩容
        StringBuilder sb = new StringBuilder(90 + 1); // 10x9 + 1 for turn
        // 添加棋盘状态
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                // 使用数字字符而不是字符串连接，提高性能
                sb.append((char)('0' + piece[i][j]));
            }
        }
        // 添加当前回合（谁走棋）
        sb.append(IsRedGo ? 'R' : 'B');
        return sb.toString();
    }
    
    // 检查是否三次重复局面
    public boolean isThreefoldRepetition() {
        synchronized (lock) {
            String currentHash = generatePositionHashInternal();
            Integer count = positionHistory.get(currentHash);
            return count != null && count >= 3;
        }
    }
    
    // 检查是否长将（连续将军超过规定次数）
    public boolean isPerpetualCheck() {
        synchronized (lock) {
            // 连续将军超过3次判为长将
            return consecutiveCheckRed >= 3 || consecutiveCheckBlack >= 3;
        }
    }
    
    // 获取长将方（用于显示）
    public String getPerpetualCheckSide() {
        synchronized (lock) {
            if (consecutiveCheckRed >= 3) return "红方";
            if (consecutiveCheckBlack >= 3) return "黑方";
            return null;
        }
    }
    
    // 获取长捉方（用于显示）
    public String getPerpetualAttackSide() {
        synchronized (lock) {
            if (consecutiveAttackRed >= 3) return "红方"; // 连续3次攻击同一棋子判为长捉
            if (consecutiveAttackBlack >= 3) return "黑方";
            return null;
        }
    }
    
    // 检查是否单方长将
    public boolean isOneSidePerpetualCheck() {
        synchronized (lock) {
            return (consecutiveCheckRed >= 3 && consecutiveCheckBlack == 0) || 
                   (consecutiveCheckBlack >= 3 && consecutiveCheckRed == 0);
        }
    }
    
    // 检查是否单方长捉
    public boolean isOneSidePerpetualAttack() {
        synchronized (lock) {
            return (consecutiveAttackRed >= 3 && consecutiveAttackBlack == 0) || 
                   (consecutiveAttackBlack >= 3 && consecutiveAttackRed == 0);
        }
    }
    
    // 检查是否双方长将
    public boolean isBothSidesPerpetualCheck() {
        synchronized (lock) {
            return consecutiveCheckRed >= 2 && consecutiveCheckBlack >= 2; // 双方各连续将军2次
        }
    }
    
    // 检查是否双方长捉
    public boolean isBothSidesPerpetualAttack() {
        synchronized (lock) {
            return consecutiveAttackRed >= 2 && consecutiveAttackBlack >= 2; // 双方各连续攻击2次
        }
    }
    
    // 检查是否双方闲着（无攻击意图）
    public boolean isBothSidesIdle() {
        synchronized (lock) {
            // 双方都没有将军也没有攻击
            return consecutiveCheckRed == 0 && consecutiveCheckBlack == 0 && 
                   consecutiveAttackRed == 0 && consecutiveAttackBlack == 0 && 
                   peaceRound >= 10; // 连续10回合无吃子且无攻击
        }
    }
    
    // 检查是否一方禁止一方允许
    public boolean isOneForbiddenOneAllowed() {
        synchronized (lock) {
            // 一方有禁止着法（长将或长捉），另一方没有
            boolean redForbidden = (consecutiveCheckRed >= 3 || consecutiveAttackRed >= 3);
            boolean blackForbidden = (consecutiveCheckBlack >= 3 || consecutiveAttackBlack >= 3);
            return (redForbidden && !blackForbidden) || (!redForbidden && blackForbidden);
        }
    }
    
    // 获取禁止方
    public String getForbiddenSide() {
        synchronized (lock) {
            boolean redForbidden = (consecutiveCheckRed >= 3 || consecutiveAttackRed >= 3);
            boolean blackForbidden = (consecutiveCheckBlack >= 3 || consecutiveAttackBlack >= 3);
            
            if (redForbidden && !blackForbidden) return "红方";
            if (!redForbidden && blackForbidden) return "黑方";
            return null;
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        ChessInfo info = (ChessInfo) super.clone();
        
        synchronized (lock) {
            // 深拷贝棋盘
            info.piece = new int[10][9];
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    info.piece[i][j] = this.piece[i][j];
                }
            }
            
            info.prePos = this.prePos != null ? (Pos) this.prePos.clone() : null;
            info.curPos = this.curPos != null ? (Pos) this.curPos.clone() : null;
            
            // 使用线程安全的List
            info.ret = new CopyOnWriteArrayList<>();
            for (Pos pos : this.ret) {
                info.ret.add((Pos) pos.clone());
            }
            
            info.Select = this.Select.clone();
            info.IsRedGo = this.IsRedGo;
            info.IsChecked = this.IsChecked;
            info.isMachine = this.isMachine;
            info.status = this.status;
            info.peaceRound = this.peaceRound;
            info.totalMoves = this.totalMoves;
            info.attackNum_B = this.attackNum_B;
            info.attackNum_R = this.attackNum_R;

            info.IsSetupMode = this.IsSetupMode;
            info.setting = this.setting;
            info.suggestFromPos = this.suggestFromPos != null ? (Pos) this.suggestFromPos.clone() : null;
            info.suggestToPos = this.suggestToPos != null ? (Pos) this.suggestToPos.clone() : null;
            
            // 复制多步支招相关字段 - 使用线程安全的List
            info.suggestMoves = new CopyOnWriteArrayList<>();
            if (this.suggestMoves != null) {
                for (Move move : this.suggestMoves) {
                    info.suggestMoves.add(new Move(move.fromPos != null ? (Pos) move.fromPos.clone() : null,
                                                   move.toPos != null ? (Pos) move.toPos.clone() : null));
                }
            }
            info.suggestMoveLabels = new CopyOnWriteArrayList<>(this.suggestMoveLabels);
            info.suggestMovesIsRed = new CopyOnWriteArrayList<>(this.suggestMovesIsRed);
            info.suggestMoveNotations = new CopyOnWriteArrayList<>(this.suggestMoveNotations);
            
            // 复制和棋判断相关字段 - 使用线程安全的Map
            info.positionHistory = Collections.synchronizedMap(new PositionHistoryMap());
            info.positionHistory.putAll(this.positionHistory);
            info.consecutiveCheckRed = this.consecutiveCheckRed;
            info.consecutiveCheckBlack = this.consecutiveCheckBlack;
            info.lastMoveWasCheck = this.lastMoveWasCheck;
            
            // 复制长捉检测相关字段
            info.consecutiveAttackRed = this.consecutiveAttackRed;
            info.consecutiveAttackBlack = this.consecutiveAttackBlack;
            info.lastAttackedPiecePos = this.lastAttackedPiecePos != null ? (Pos) this.lastAttackedPiecePos.clone() : null;
            info.lastAttackedPieceType = this.lastAttackedPieceType;
            
            // 复制规则判定统计
            info.forbiddenMoveRed = this.forbiddenMoveRed;
            info.forbiddenMoveBlack = this.forbiddenMoveBlack;
            
            // 复制强制变着相关字段
            info.forceVariation = this.forceVariation;
            info.variationRandomness = this.variationRandomness;
        }
        
        return info;
    }
    
    /**
     * 计算双方的攻击棋子数量
     * 攻击棋子包括：车、马、炮、兵/卒
     */
    private void calculateAttackPiecesInternal() {
        attackNum_B = 0;
        attackNum_R = 0;
        
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                int piece = this.piece[i][j];
                if (piece != 0) {
                    // 黑方攻击棋子：车(5)、马(4)、炮(6)、卒(7)
                    if (piece == 4 || piece == 5 || piece == 6 || piece == 7) {
                        attackNum_B++;
                    }
                    // 红方攻击棋子：车(12)、马(11)、炮(13)、兵(14)
                    else if (piece == 11 || piece == 12 || piece == 13 || piece == 14) {
                        attackNum_R++;
                    }
                }
            }
        }
    }
    
    /**
     * 反序列化时初始化字段
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        
        synchronized (lock) {
            // 确保 positionHistory 被初始化为线程安全版本
            if (positionHistory == null) {
                positionHistory = Collections.synchronizedMap(new PositionHistoryMap());
            } else if (!(positionHistory instanceof java.util.concurrent.ConcurrentMap)) {
                // 如果不是线程安全的Map，包装为线程安全的
                PositionHistoryMap newMap = new PositionHistoryMap();
                newMap.putAll(positionHistory);
                positionHistory = Collections.synchronizedMap(newMap);
            }
            
            // 确保其他必要字段被初始化为线程安全版本
            if (ret == null) {
                ret = new CopyOnWriteArrayList<>();
            } else if (!(ret instanceof java.util.concurrent.CopyOnWriteArrayList)) {
                ret = new CopyOnWriteArrayList<>(ret);
            }
            
            if (Select == null) {
                Select = new int[]{-1, -1};
            }
            
            // 确保支招相关字段是线程安全的
            if (suggestMoves == null) {
                suggestMoves = new CopyOnWriteArrayList<>();
            } else if (!(suggestMoves instanceof java.util.concurrent.CopyOnWriteArrayList)) {
                suggestMoves = new CopyOnWriteArrayList<>(suggestMoves);
            }
            
            if (suggestMoveLabels == null) {
                suggestMoveLabels = new CopyOnWriteArrayList<>();
            } else if (!(suggestMoveLabels instanceof java.util.concurrent.CopyOnWriteArrayList)) {
                suggestMoveLabels = new CopyOnWriteArrayList<>(suggestMoveLabels);
            }
            
            if (suggestMovesIsRed == null) {
                suggestMovesIsRed = new CopyOnWriteArrayList<>();
            } else if (!(suggestMovesIsRed instanceof java.util.concurrent.CopyOnWriteArrayList)) {
                suggestMovesIsRed = new CopyOnWriteArrayList<>(suggestMovesIsRed);
            }
            
            if (suggestMoveNotations == null) {
                suggestMoveNotations = new CopyOnWriteArrayList<>();
            } else if (!(suggestMoveNotations instanceof java.util.concurrent.CopyOnWriteArrayList)) {
                suggestMoveNotations = new CopyOnWriteArrayList<>(suggestMoveNotations);
            }
        }
    }
    
    // 提供获取锁对象的方法（用于其他类需要协同操作时）
    public Object getLock() {
        return lock;
    }
}