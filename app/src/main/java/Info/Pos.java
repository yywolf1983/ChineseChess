package Info;

import java.io.Serializable;

public class Pos implements Cloneable, Serializable {
    private static final long serialVersionUID = 3572115210886077953L;

    public int x;
    public int y;

    public Pos(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Pos)) {
            return false;
        }
        Pos pos = (Pos) obj;
        return this.x == pos.x && this.y == pos.y;
    }

    @Override
    public int hashCode() {
        // 必须与 equals 保持一致：以 x、y 计算哈希，否则作为 HashMap/HashSet 键时会失效。
        return 31 * x + y;
    }
}