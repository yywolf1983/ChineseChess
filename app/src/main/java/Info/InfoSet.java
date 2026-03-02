package Info;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Stack;

public class InfoSet implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;
    public Stack<ChessInfo> preInfo;
    public ChessInfo curInfo;
    public HashMap<Long, Integer> ZobristInfo;

    public InfoSet() {
        preInfo = new Stack<ChessInfo>();
        curInfo = new ChessInfo();
        ZobristInfo = new HashMap<Long, Integer>();
    }

    public void pushInfo(ChessInfo info) throws CloneNotSupportedException {
        preInfo.push((ChessInfo) info.clone());
        curInfo = (ChessInfo) info.clone();
        // 更新 Zobrist 信息
        Long key = info.ZobristKeyCheck;
        if (ZobristInfo.containsKey(key)) {
            ZobristInfo.put(key, ZobristInfo.get(key) + 1);
        } else {
            ZobristInfo.put(key, 1);
        }
    }

    public void newInfo() {
        preInfo.clear();
        curInfo = new ChessInfo();
        ZobristInfo.clear();
    }

    public void recallZobristInfo(long key) {
        if (ZobristInfo.containsKey(key)) {
            ZobristInfo.put(key, ZobristInfo.get(key) - 1);
            if (ZobristInfo.get(key) == 0) {
                ZobristInfo.remove(key);
            }
        }
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        InfoSet infoSet = (InfoSet) super.clone();
        infoSet.preInfo = new Stack<ChessInfo>();
        for (ChessInfo info : preInfo) {
            infoSet.preInfo.push((ChessInfo) info.clone());
        }
        infoSet.curInfo = (ChessInfo) curInfo.clone();
        infoSet.ZobristInfo = new HashMap<Long, Integer>(ZobristInfo);
        return infoSet;
    }
}