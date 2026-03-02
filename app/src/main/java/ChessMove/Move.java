package ChessMove;

import Info.Pos;

public class Move {
    public Pos fromPos;
    public Pos toPos;
    
    public Move(Pos from, Pos to) {
        this.fromPos = from;
        this.toPos = to;
    }
    
    @Override
    public String toString() {
        return "Move{from=" + fromPos + ", to=" + toPos + "}";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Move move = (Move) obj;
        return fromPos.equals(move.fromPos) && toPos.equals(move.toPos);
    }
    
    @Override
    public int hashCode() {
        int result = fromPos.hashCode();
        result = 31 * result + toPos.hashCode();
        return result;
    }
}