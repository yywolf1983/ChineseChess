package AICore;

import Info.ChessInfo;
import Info.Pos;
import ChessMove.Rule;
import ChessMove.Move;
import Utils.LogUtils;

import java.util.Random;

/**
 * 中国象棋开局库（仅用于双机对战 gameMode==3）。
 *
 * 每局开始时随机选取一个开局，按开局谱的前若干步（默认 2 步 = 红方一步 + 黑方一步）
 * 走子，避免每局开局千篇一律；超出开局库范围后交还引擎正常计算。
 *
 * 走法采用与 PikafishAI.uciToMove 完全相同的 UCI 约定：
 *   x = 字母 - 'a'（a=0 左 … i=8 右）
 *   y = 9 - 数字（数字 9=红底线 y=0，数字 0=黑底线 y=9）
 * 例如：红中炮“炮二平五” = h7e7，黑屏风马“马8进7” = b0c2。
 *
 * 每一步都会用 Rule.PossibleMoves 做合法性校验，任何非法着法都会放弃开局库、
 * 直接交还引擎，保证不会走出规则之外的棋。
 */
public class OpeningBook {
    // 每个开局：第 0 项为名称，其后为交替着法（红先、黑后……）
    private static final String[][] OPENINGS = {
        // —— 中炮类 ——
        {"中炮对屏风马",       "h7e7", "b0c2"},
        {"中炮对屏风马(右马)", "h7e7", "h0g2"},
        {"中炮对顺炮",         "h7e7", "b2e2"},
        {"中炮对半途列炮",     "h7e7", "h2e2"},
        {"中炮对反宫马",       "h7e7", "h2g2"},
        {"中炮对进7卒",        "h7e7", "g3g4"},
        {"中炮对进3卒",        "h7e7", "c3c4"},
        {"中炮对飞左象",       "h7e7", "c0e2"},
        {"中炮对飞右象",       "h7e7", "g0e2"},
        {"中炮对左三步虎",     "h7e7", "b2c2"},
        {"中炮(炮八平五)对屏风马", "b7e7", "b0c2"},
        {"中炮(炮八平五)对屏风马(右马)", "b7e7", "h0g2"},
        {"中炮(炮八平五)对顺炮",   "b7e7", "h2e2"},
        {"中炮(炮八平五)对进卒",   "b7e7", "g3g4"},
        {"中炮对屏风马进三兵", "h7e7", "b0c2", "h9g7", "g3g4"},

        // —— 飞相类 ——
        {"飞相局对右士角炮",   "c9e7", "b2d2"},
        {"飞相局对挺卒",       "c9e7", "g3g4"},
        {"飞相对进马",         "c9e7", "b0c2"},
        {"飞相对右中炮",       "c9e7", "h2e2"},
        {"飞相对飞象",         "c9e7", "c0e2"},
        {"飞相(相三进五)对挺卒", "g9e7", "c3c4"},
        {"飞相(相三进五)对进马", "g9e7", "b0c2"},
        {"飞相(相三进五)对左中炮", "g9e7", "h2e2"},

        // —— 起马类 ——
        {"起马局对挺卒",       "h9g7", "g3g4"},
        {"起马对进3卒",        "h9g7", "c3c4"},
        {"起马对左中炮",       "h9g7", "h2e2"},
        {"起马对士角炮",       "h9g7", "b2d2"},
        {"两头蛇对屏风马",     "h9g7", "b0c2"},
        {"起马(马八进七)对挺卒", "b9c7", "c3c4"},
        {"起马(马八进七)对进7卒", "b9c7", "g3g4"},

        // —— 仙人指路类 ——
        {"仙人指路对挺卒",     "c6c5", "g3g4"},
        {"仙人指路对卒底炮",   "c6c5", "c3c4"},
        {"仙人指路对中炮",     "c6c5", "h2e2"},
        {"仙人指路对起马",     "c6c5", "b0c2"},
        {"仙人指路(兵七进一)对挺卒", "g6g5", "c3c4"},
        {"仙人指路(兵七进一)对飞象", "g6g5", "c0e2"},
        {"对兵局",             "g6g5", "g3g4"},

        // —— 挺兵类 ——
        {"挺边兵对挺卒",       "a6a5", "c3c4"},
        {"挺中兵对挺卒",       "e6e5", "c3c4"},

        // —— 其它炮类 ——
        {"过宫炮对左中炮",     "h7d7", "b2e2"},
        {"过宫炮对屏风马",     "h7d7", "b0c2"},
        {"过宫炮对进马",       "h7d7", "h0g2"},
        {"士角炮对屏风马",     "h7f7", "b0c2"},
        {"士角炮对起马",       "h7f7", "h0g2"},
        {"金钩炮对挺卒",       "h7g7", "c3c4"},
        {"炮八平六对挺卒",     "b7d7", "g3g4"},
        {"炮八平七对挺卒",     "b7c7", "g3g4"},
        {"过宫炮(左)对中炮",   "b7f7", "h2e2"},
    };

    /** 开局阶段最多走多少步（ply）。2 = 红方一步 + 黑方一步，之后交还引擎。 */
    public static final int DEFAULT_BOOK_PLIES = 2;

    private String[] selectedLine = null;
    private int bookPlyIndex = 0; // 已经返回/应用的着法数
    private final Random random = new Random();

    /** 重新随机选取一个开局（新对局时调用）。 */
    public void reset() {
        if (OPENINGS.length > 0) {
            selectedLine = OPENINGS[random.nextInt(OPENINGS.length)];
        } else {
            selectedLine = null;
        }
        bookPlyIndex = 0;
        if (selectedLine != null) {
            LogUtils.i("OpeningBook", "随机选取开局: " + selectedLine[0]);
        }
    }

    /**
     * 返回当前局面下的开局库着法；若不在开局阶段或着法非法则返回 null（交还引擎）。
     *
     * @param chessInfo 当前局面（用于合法性校验与判断是否回到初始局面）
     * @param maxPlies  开局阶段最多走的步数
     */
    public Move getBookMove(ChessInfo chessInfo, int maxPlies) {
        if (chessInfo == null || chessInfo.piece == null) {
            return null;
        }
        // 上一局开局库已用完，且当前又回到了初始局面 → 视为新对局，重新随机选取
        if (selectedLine == null || (bookPlyIndex >= maxPlies && isStartPosition(chessInfo))) {
            reset();
        }

        // 仅在「双机对战初始局面」使用开局库：必须精确等于标准初始布局。
        // 一旦本局已经走过开局库着法（bookPlyIndex>0）则继续既定谱着；
        // 但若第一步之前就不是标准初始局面（缺子 / 自定义摆棋 / 残局），则整局放弃开局库，
        // 少一个子都不要引用。
        if (bookPlyIndex == 0 && !isStartPosition(chessInfo)) {
            LogUtils.w("OpeningBook", "非标准初始局面（缺子或自定义布局），放弃开局库");
            selectedLine = null;
            bookPlyIndex = maxPlies; // 标记本局不再引用，避免反复尝试
            return null;
        }

        if (selectedLine == null) {
            return null;
        }
        if (bookPlyIndex >= maxPlies) {
            return null;
        }
        if (bookPlyIndex >= selectedLine.length - 1) {
            // 该开局谱已走完
            return null;
        }

        String uci = selectedLine[bookPlyIndex + 1];
        if (!isValidMove(chessInfo, uci)) {
            LogUtils.w("OpeningBook", "开局库着法非法，放弃开局库: " + uci);
            bookPlyIndex = maxPlies; // 标记已用完，避免反复尝试非法着法
            return null;
        }

        bookPlyIndex++;
        return uciToMove(uci);
    }

    /** 标准初始局面 FEN（与 FENHandler 约定一致：小写=黑，大写=红，红先走=w）。 */
    private static final String STANDARD_INITIAL_FEN =
            "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";

    /** 当前局面是否为标准初始局面（用于检测新对局），通过比对 FEN 判断。 */
    private boolean isStartPosition(ChessInfo info) {
        if (info == null || info.piece == null) {
            return false;
        }
        // 红方先走才算初始局面
        if (!info.IsRedGo) {
            return false;
        }
        return STANDARD_INITIAL_FEN.equals(boardToFen(info));
    }

    /** 将当前局面转换为 FEN 字符串（棋盘部分 + 行棋方），约定同 FENHandler。 */
    private String boardToFen(ChessInfo info) {
        StringBuilder fen = new StringBuilder();
        for (int rank = 9; rank >= 0; rank--) { // 从黑方底线开始
            int emptyCount = 0;
            for (int file = 0; file < 9; file++) {
                int piece = info.piece[rank][file];
                if (piece == 0) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(fenChar(piece));
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (rank > 0) {
                fen.append('/');
            }
        }
        fen.append(' ').append(info.IsRedGo ? 'w' : 'b').append(" - - 0 1");
        return fen.toString();
    }

    /** 棋子类型 → FEN 字符（小写=黑，大写=红）。 */
    private char fenChar(int piece) {
        switch (piece) {
            case 1: return 'k'; case 2: return 'a'; case 3: return 'b';
            case 4: return 'n'; case 5: return 'r'; case 6: return 'c';
            case 7: return 'p'; case 8: return 'K'; case 9: return 'A';
            case 10: return 'B'; case 11: return 'N'; case 12: return 'R';
            case 13: return 'C'; case 14: return 'P';
            default: return ' ';
        }
    }

    /** 校验 UCI 着法在当前局面是否合法（轮到走子方、起子存在、目标在可行着法内）。 */
    private boolean isValidMove(ChessInfo chessInfo, String uci) {
        if (uci == null || uci.length() != 4) {
            return false;
        }
        try {
            int fromX = uci.charAt(0) - 'a';
            int fromY = 9 - (uci.charAt(1) - '0');
            int toX = uci.charAt(2) - 'a';
            int toY = 9 - (uci.charAt(3) - '0');
            if (fromX < 0 || fromX >= 9 || fromY < 0 || fromY >= 10
                    || toX < 0 || toX >= 9 || toY < 0 || toY >= 10) {
                return false;
            }
            int piece = chessInfo.piece[fromY][fromX];
            if (piece == 0) {
                return false;
            }
            boolean isRed = piece >= 8;
            if (isRed != chessInfo.IsRedGo) {
                return false;
            }
            java.util.List<Pos> moves = Rule.PossibleMoves(chessInfo.piece, fromX, fromY, piece);
            return moves.contains(new Pos(toX, toY));
        } catch (Exception e) {
            return false;
        }
    }

    /** 与 PikafishAI.uciToMove 相同的 UCI→Move 转换。 */
    private Move uciToMove(String uci) {
        if (uci == null || uci.length() != 4) {
            return null;
        }
        try {
            int fromX = uci.charAt(0) - 'a';
            int fromY = 9 - (uci.charAt(1) - '0');
            int toX = uci.charAt(2) - 'a';
            int toY = 9 - (uci.charAt(3) - '0');
            if (fromX < 0 || fromX >= 9 || fromY < 0 || fromY >= 10
                    || toX < 0 || toX >= 9 || toY < 0 || toY >= 10) {
                return null;
            }
            return new Move(new Pos(fromX, fromY), new Pos(toX, toY));
        } catch (Exception e) {
            return null;
        }
    }
}
