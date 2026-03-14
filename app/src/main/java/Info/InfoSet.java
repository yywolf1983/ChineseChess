package Info;

import java.io.Serializable;
import java.util.Stack;

public class InfoSet implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;
    public Stack<ChessInfo> preInfo;
    public ChessInfo curInfo;

    public InfoSet() {
        preInfo = new Stack<ChessInfo>();
        curInfo = new ChessInfo();
    }

    public void pushInfo(ChessInfo info) throws CloneNotSupportedException {
        preInfo.push((ChessInfo) info.clone());
        curInfo = (ChessInfo) info.clone();
    }

    public void newInfo() {
        preInfo.clear();
        curInfo = new ChessInfo();
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        InfoSet infoSet = (InfoSet) super.clone();
        infoSet.preInfo = new Stack<ChessInfo>();
        for (ChessInfo info : preInfo) {
            infoSet.preInfo.push((ChessInfo) info.clone());
        }
        infoSet.curInfo = (ChessInfo) curInfo.clone();
        return infoSet;
    }
}