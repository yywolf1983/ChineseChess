package Info;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ChessMove.Rule;

public class ChessInfo implements Cloneable, Serializable {
    private static final long serialVersionUID = -8764412462496314495L;

    public int[][] piece;
    public boolean IsRedGo;
    public Pos prePos;
    public Pos curPos;
    public boolean IsChecked;
    public List<Pos> ret;
    public int[] Select;
    public boolean isMachine;
    public int status;
    public int peaceRound;
    public int totalMoves; // 总走步数
    public int attackNum_B;
    public int attackNum_R;

    public boolean IsSetupMode;
    public Setting setting;
    
    // 支招相关字段
    public Pos suggestFromPos;
    public Pos suggestToPos;
    
    // 和棋判断相关字段
    public Map<String, Integer> positionHistory; // 局面历史记录，用于检测重复局面
    public int consecutiveCheckRed; // 红方连续将军次数
    public int consecutiveCheckBlack; // 黑方连续将军次数
    public boolean lastMoveWasCheck; // 上一步是否为将军
    
    // 长捉检测相关字段
    public int consecutiveAttackRed; // 红方连续攻击同一棋子次数
    public int consecutiveAttackBlack; // 黑方连续攻击同一棋子次数
    public Pos lastAttackedPiecePos; // 上一次被攻击的棋子位置
    public int lastAttackedPieceType; // 上一次被攻击的棋子类型
    
    // 规则判定统计
    public int forbiddenMoveRed; // 红方禁止着法计数
    public int forbiddenMoveBlack; // 黑方禁止着法计数
    
    // 强制变着相关字段
    public boolean forceVariation; // 是否处于强制变着模式
    public int variationRandomness; // 变着随机性等级（1-5）

    public ChessInfo() {
        init();
    }

    private void init() {
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
        ret = new ArrayList<>();
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
        
        // 初始化和棋判断相关字段
        positionHistory = new HashMap<>();
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
        calculateAttackPieces();
        
        // 记录初始局面
        recordCurrentPosition();
    }

    public void setInfo(ChessInfo info) throws CloneNotSupportedException {
        this.piece = new int[10][9];
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                this.piece[i][j] = info.piece[i][j];
            }
        }
        this.IsRedGo = info.IsRedGo;
        this.prePos = info.prePos != null ? (Pos) info.prePos.clone() : null;
        this.curPos = info.curPos != null ? (Pos) info.curPos.clone() : null;
        this.IsChecked = info.IsChecked;
        this.ret = new ArrayList<>();
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
        
        // 复制和棋判断相关字段
        this.positionHistory = new HashMap<>(info.positionHistory);
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

    public void updateAllInfo(Pos prePos, Pos curPos, int piece, int capturedPiece, boolean isCheck) {
        // 更新走棋信息
        this.prePos = prePos;
        this.curPos = curPos;
        
        // 清除支招提示线
        suggestFromPos = null;
        suggestToPos = null;
        
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
        updateConsecutiveCheck(isCheck);
        
        // 更新长捉检测
        updateConsecutiveAttack(prePos, curPos, capturedPiece);
        
        // 切换回合
        IsRedGo = !IsRedGo;
        
        // 记录当前局面（在切换回合后记录，确保局面哈希包含回合信息）
        recordCurrentPosition();
    }
    
    // 更新连续将军计数
    private void updateConsecutiveCheck(boolean isCheck) {
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
    private void updateConsecutiveAttack(Pos fromPos, Pos toPos, int capturedPiece) {
        // 检查是否是攻击性移动（移动后位置有棋子被吃，或者移动的棋子是攻击性棋子）
        boolean isAttackMove = false;
        int movingPieceType = piece[fromPos.y][fromPos.x];
        
        // 检查是否是攻击性棋子：车、马、炮、兵/卒
        boolean isAttackingPiece = false;
        if (movingPieceType == 4 || movingPieceType == 5 || movingPieceType == 6 || movingPieceType == 7 ||  // 黑方攻击性棋子
            movingPieceType == 11 || movingPieceType == 12 || movingPieceType == 13 || movingPieceType == 14) { // 红方攻击性棋子
            isAttackingPiece = true;
        }
        
        // 如果是攻击性棋子且有吃子，或者攻击性棋子移动到可以攻击对方棋子的位置
        if (capturedPiece != 0 && isAttackingPiece) {
            isAttackMove = true;
        } else if (isAttackingPiece) {
            // 检查移动后是否可以攻击对方棋子
            List<Pos> possibleAttacks = Rule.PossibleMoves(piece, toPos.x, toPos.y, movingPieceType);
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
        
        if (isAttackMove) {
            // 检查是否在攻击同一棋子
            if (lastAttackedPiecePos != null && lastAttackedPieceType != 0) {
                // 检查当前攻击的棋子是否与上次攻击的是同一棋子
                if (capturedPiece != 0 && capturedPiece == lastAttackedPieceType) {
                    // 吃掉了上次攻击的棋子，重置计数
                    resetConsecutiveAttack();
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
                        resetConsecutiveAttack();
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
            resetConsecutiveAttack();
        }
    }
    
    // 重置连续攻击计数
    private void resetConsecutiveAttack() {
        consecutiveAttackRed = 0;
        consecutiveAttackBlack = 0;
        lastAttackedPiecePos = null;
        lastAttackedPieceType = 0;
    }
    
    // 记录当前局面
    public void recordCurrentPosition() {
        String positionHash = generatePositionHash();
        Integer count = positionHistory.get(positionHash);
        if (count == null) {
            count = 0;
        }
        positionHistory.put(positionHash, count + 1);
    }
    
    // 生成局面哈希
    public String generatePositionHash() {
        StringBuilder sb = new StringBuilder();
        // 添加棋盘状态
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                sb.append(piece[i][j]);
            }
        }
        // 添加当前回合（谁走棋）
        sb.append(IsRedGo ? "R" : "B");
        return sb.toString();
    }
    
    // 检查是否三次重复局面
    public boolean isThreefoldRepetition() {
        String currentHash = generatePositionHash();
        Integer count = positionHistory.get(currentHash);
        return count != null && count >= 3;
    }
    
    // 检查是否长将（连续将军超过规定次数）
    public boolean isPerpetualCheck() {
        // 连续将军超过3次判为长将
        return consecutiveCheckRed >= 3 || consecutiveCheckBlack >= 3;
    }
    
    // 获取长将方（用于显示）
    public String getPerpetualCheckSide() {
        if (consecutiveCheckRed >= 3) return "红方";
        if (consecutiveCheckBlack >= 3) return "黑方";
        return null;
    }
    
    // 获取长捉方（用于显示）
    public String getPerpetualAttackSide() {
        if (consecutiveAttackRed >= 3) return "红方"; // 连续3次攻击同一棋子判为长捉
        if (consecutiveAttackBlack >= 3) return "黑方";
        return null;
    }
    
    // 检查是否单方长将
    public boolean isOneSidePerpetualCheck() {
        return (consecutiveCheckRed >= 3 && consecutiveCheckBlack == 0) || 
               (consecutiveCheckBlack >= 3 && consecutiveCheckRed == 0);
    }
    
    // 检查是否单方长捉
    public boolean isOneSidePerpetualAttack() {
        return (consecutiveAttackRed >= 3 && consecutiveAttackBlack == 0) || 
               (consecutiveAttackBlack >= 3 && consecutiveAttackRed == 0);
    }
    
    // 检查是否双方长将
    public boolean isBothSidesPerpetualCheck() {
        return consecutiveCheckRed >= 2 && consecutiveCheckBlack >= 2; // 双方各连续将军2次
    }
    
    // 检查是否双方长捉
    public boolean isBothSidesPerpetualAttack() {
        return consecutiveAttackRed >= 2 && consecutiveAttackBlack >= 2; // 双方各连续攻击2次
    }
    
    // 检查是否双方闲着（无攻击意图）
    public boolean isBothSidesIdle() {
        // 双方都没有将军也没有攻击
        return consecutiveCheckRed == 0 && consecutiveCheckBlack == 0 && 
               consecutiveAttackRed == 0 && consecutiveAttackBlack == 0 && 
               peaceRound >= 10; // 连续10回合无吃子且无攻击
    }
    
    // 检查是否一方禁止一方允许
    public boolean isOneForbiddenOneAllowed() {
        // 一方有禁止着法（长将或长捉），另一方没有
        boolean redForbidden = (consecutiveCheckRed >= 3 || consecutiveAttackRed >= 3);
        boolean blackForbidden = (consecutiveCheckBlack >= 3 || consecutiveAttackBlack >= 3);
        return (redForbidden && !blackForbidden) || (!redForbidden && blackForbidden);
    }
    
    // 获取禁止方
    public String getForbiddenSide() {
        boolean redForbidden = (consecutiveCheckRed >= 3 || consecutiveAttackRed >= 3);
        boolean blackForbidden = (consecutiveCheckBlack >= 3 || consecutiveAttackBlack >= 3);
        
        if (redForbidden && !blackForbidden) return "红方";
        if (!redForbidden && blackForbidden) return "黑方";
        return null;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        ChessInfo info = (ChessInfo) super.clone();
        info.piece = new int[10][9];
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                info.piece[i][j] = this.piece[i][j];
            }
        }
        info.prePos = this.prePos != null ? (Pos) this.prePos.clone() : null;
        info.curPos = this.curPos != null ? (Pos) this.curPos.clone() : null;
        info.ret = new ArrayList<>();
        for (Pos pos : this.ret) {
            info.ret.add((Pos) pos.clone());
        }
        info.Select = this.Select.clone();
        info.IsRedGo = this.IsRedGo;
        info.IsChecked = this.IsChecked;
        info.isMachine = this.isMachine;
        info.status = this.status;
        info.peaceRound = this.peaceRound;
        info.totalMoves = this.totalMoves; // 复制总走步数
        info.attackNum_B = this.attackNum_B;
        info.attackNum_R = this.attackNum_R;

        info.IsSetupMode = this.IsSetupMode;
        info.setting = this.setting;
        info.suggestFromPos = this.suggestFromPos != null ? (Pos) this.suggestFromPos.clone() : null;
        info.suggestToPos = this.suggestToPos != null ? (Pos) this.suggestToPos.clone() : null;
        
        // 复制和棋判断相关字段
        info.positionHistory = new HashMap<>(this.positionHistory);
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
        
        return info;
    }
    
    /**
     * 计算双方的攻击棋子数量
     * 攻击棋子包括：车、马、炮、兵/卒
     */
    private void calculateAttackPieces() {
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
}