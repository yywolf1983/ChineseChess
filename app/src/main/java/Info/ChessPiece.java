package Info;

/**
 * 棋子类型常量定义
 *
 * 棋子编码规则：
 *   黑方 1~7：将 士 象 马 车 炮 卒
 *   红方 8~14：帅 仕 相 马 车 炮 兵
 */
public final class ChessPiece {

    private ChessPiece() { /* 工具类，禁止实例化 */ }

    // ── 黑方 ──
    public static final int BLACK_KING   = 1;
    public static final int BLACK_ADVISOR = 2;
    public static final int BLACK_ELEPHANT = 3;
    public static final int BLACK_HORSE  = 4;
    public static final int BLACK_ROOK   = 5;
    public static final int BLACK_CANNON = 6;
    public static final int BLACK_PAWN   = 7;

    // ── 红方 ──
    public static final int RED_KING     = 8;
    public static final int RED_ADVISOR  = 9;
    public static final int RED_ELEPHANT = 10;
    public static final int RED_HORSE    = 11;
    public static final int RED_ROOK     = 12;
    public static final int RED_CANNON   = 13;
    public static final int RED_PAWN     = 14;

    /** 空位 */
    public static final int EMPTY = 0;

    // ── 判断工具 ──

    /** 是否红方棋子 */
    public static boolean isRed(int piece) {
        return piece >= RED_KING && piece <= RED_PAWN;
    }

    /** 是否黑方棋子 */
    public static boolean isBlack(int piece) {
        return piece >= BLACK_KING && piece <= BLACK_PAWN;
    }

    /** 是否有效棋子 */
    public static boolean isValid(int piece) {
        return piece >= BLACK_KING && piece <= RED_PAWN;
    }

    /**
     * 根据棋子编码返回中文名称
     */
    public static String getName(int piece) {
        switch (piece) {
            case BLACK_KING:    return "将";
            case BLACK_ADVISOR: return "士";
            case BLACK_ELEPHANT:return "象";
            case BLACK_HORSE:   return "马";
            case BLACK_ROOK:    return "车";
            case BLACK_CANNON:  return "炮";
            case BLACK_PAWN:    return "卒";
            case RED_KING:      return "帅";
            case RED_ADVISOR:   return "仕";
            case RED_ELEPHANT:  return "相";
            case RED_HORSE:     return "马";
            case RED_ROOK:      return "车";
            case RED_CANNON:    return "炮";
            case RED_PAWN:      return "兵";
            default:            return "未知";
        }
    }

    /**
     * 根据基础棋子名称和颜色返回棋子编码
     * 支持红黑双方的不同写法（如"帅"/"将"都映射到王）
     */
    public static int getTypeByName(String name, boolean isRed) {
        if (name == null) return EMPTY;
        switch (name) {
            case "将": case "帅": return isRed ? RED_KING   : BLACK_KING;
            case "士": case "仕": return isRed ? RED_ADVISOR : BLACK_ADVISOR;
            case "象": case "相": return isRed ? RED_ELEPHANT: BLACK_ELEPHANT;
            case "马":            return isRed ? RED_HORSE   : BLACK_HORSE;
            case "车":            return isRed ? RED_ROOK    : BLACK_ROOK;
            case "炮":            return isRed ? RED_CANNON  : BLACK_CANNON;
            case "卒": case "兵": return isRed ? RED_PAWN    : BLACK_PAWN;
            default:              return EMPTY;
        }
    }

    /**
     * 将阿拉伯数字转换为中文数字
     */
    public static String toChineseNumber(int number) {
        switch (number) {
            case 1: return "一";
            case 2: return "二";
            case 3: return "三";
            case 4: return "四";
            case 5: return "五";
            case 6: return "六";
            case 7: return "七";
            case 8: return "八";
            case 9: return "九";
            default: return String.valueOf(number);
        }
    }

    /**
     * 中文数字转阿拉伯数字
     */
    public static int fromChineseNumber(char c) {
        switch (c) {
            case '一': case '1': return 1;
            case '二': case '2': return 2;
            case '三': case '3': return 3;
            case '四': case '4': return 4;
            case '五': case '5': return 5;
            case '六': case '6': return 6;
            case '七': case '7': return 7;
            case '八': case '8': return 8;
            case '九': case '9': return 9;
            default: return -1;
        }
    }

    /**
     * 将字符串中的中文数字统一转换为阿拉伯数字
     */
    public static String chineseToArabic(String s) {
        if (s == null) return null;
        return s.replace("零", "0")
                .replace("一", "1").replace("二", "2").replace("三", "3")
                .replace("四", "4").replace("五", "5").replace("六", "6")
                .replace("七", "7").replace("八", "8").replace("九", "9");
    }
}
