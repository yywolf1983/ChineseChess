package Info;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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
        
        // 计算初始攻击棋子数量
        calculateAttackPieces();
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
    }

    public void updateAllInfo(Pos prePos, Pos curPos, int piece, int capturedPiece) {
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
        
        // 切换回合
        IsRedGo = !IsRedGo;
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