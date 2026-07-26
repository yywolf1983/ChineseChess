package top.nones.chessgame;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.DialogInterface;
import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;
import java.io.FileOutputStream;
import java.io.IOException;

import Utils.GameResourceManager;
import CustomView.ModeIconDrawable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import ChessMove.Rule;
import CustomView.RoundView;
import Info.ChessInfo;
import Info.InfoSet;
import Info.Pos;
import Info.SaveInfo;
import Info.ChessNotation;
import Info.Setting;
import CustomView.ChessView;
import CustomView.SetupModeView;
import Utils.LogUtils;
import AICore.PikafishAI;
import ChessMove.Move;

public class PvMActivity extends AppCompatActivity implements View.OnTouchListener, View.OnClickListener {
    // 静态实例，使用WeakReference避免内存泄漏
    private static WeakReference<PvMActivity> weakInstance;
    
    // 获取Activity实例的方法
    public static PvMActivity getInstance() {
        return weakInstance != null ? weakInstance.get() : null;
    }
    
    // 从HomeActivity移动过来的静态变量和方法
    public static final int MIN_CLICK_DELAY_TIME = 100;
    // 防抖时间戳（实例变量，避免 Activity 重建后状态不一致）
    public long curClickTime = 0L;
    
    // 实例变量，不再使用静态变量避免内存泄漏
    public Setting setting;
    public MediaPlayer backMusic;
    public MediaPlayer selectMusic;
    public MediaPlayer clickMusic;
    public MediaPlayer captureMusic;
    public MediaPlayer checkMusic;
    public MediaPlayer winMusic;
    public SharedPreferences sharedPreferences;
    public RelativeLayout relativeLayout;
    public ChessInfo chessInfo;
    public InfoSet infoSet;
    public ChessView chessView;
    public RoundView roundView;
    // 按钮组底部「引擎计算结果」展示框容器（每行一个独立单行 TextView）
    public android.widget.LinearLayout engineResultContainer;
    // 结果框的滚动容器（用于整体显示/隐藏）
    public android.widget.ScrollView engineResultScroll;
    // 底部评分曲线视图（提示盒背景层，展示整局评分走势）
    public CustomView.ScoreCurveView scoreCurveView;

    // ========== 跟随支招（走子与支招变线一致时高亮并持续揭示后续步）==========
    /** 是否处于「跟随支招」状态：支招后仍在跟踪实际走子是否与候选变线一致 */
    public boolean suggestFollowActive = false;
    /** 支招后已走且与某条候选变线一致的着法前缀（按走子顺序） */
    public final java.util.List<ChessMove.Move> suggestFollowPrefix = new java.util.ArrayList<>();
    /** 支招时刻的局面快照，用于回放已走步并渲染完整变线记谱 */
    public ChessInfo suggestFollowStartInfo = null;

    // ========== 支招模拟行棋状态 ==========
    private boolean isSimulating = false;            // 是否处于模拟行棋演示中
    private ChessInfo simBackup = null;              // 进入模拟前的真实局面备份
    private final android.os.Handler simHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private java.util.List<ChessMove.Move> simMoves = null; // 当前模拟要走的步序列（来自某条候选变线）
    private int simStepIndex = 0;                    // 当前已演示到第几步
    private static final long SIM_START_DELAY_MS = 800;  // 进入模拟行棋前的初始停顿
    private static final long SIM_STEP_INTERVAL_MS = 600; // 模拟行棋每步间隔
    /** 模拟行棋跟随的步数 = 候选变线框中展示的步数（显示几步行棋就行几步），上限 12 步 */
    public static final int SIM_DISPLAY_STEPS = 12;
    // 进入模拟行棋前备份的真实支招线（含每条变线的箭头/记谱），返回时恢复
    private java.util.List<ChessMove.Move> simSuggestBackupMoves;
    private java.util.List<String> simSuggestBackupLabels;
    private java.util.List<String> simSuggestBackupNotations;
    private java.util.List<Boolean> simSuggestBackupIsRed;

    /** 清空按钮组底部的引擎结果框 */
    public void clearEngineResultBox() {
        // 模拟行棋演示期间不隐藏候选变线框，避免被中断的 AI 线程回调清掉
        if (isSimulating) return;
        if (engineResultContainer != null) {
            engineResultContainer.removeAllViews();
        }
        if (engineResultScroll != null) {
            engineResultScroll.setVisibility(android.view.View.GONE);
        }
    }

    /** 将当前整局评分历史刷新到评分曲线视图（提示盒背景层） */
    public void refreshScoreCurve() {
        if (scoreCurveView != null && chessInfo != null) {
            java.util.List<Integer> full = chessInfo.getEvalSnapshot();
            if (full == null) full = new java.util.ArrayList<>();
            int displayCount;
            if (notationManager != null && notationManager.getCurrentNotation() != null) {
                // 棋谱回放/导航：曲线显示到「当前已走步数」——下一步/手动落子增加、上一步/悔棋缩短
                displayCount = notationManager.getCurrentMoveIndex();
            } else {
                // 实时对局：显示全部已记录的曲线点
                displayCount = full.size();
            }
            if (displayCount < 0) displayCount = 0;
            if (displayCount > full.size()) displayCount = full.size();
            scoreCurveView.setScores(full.subList(0, displayCount));
        }
    }

    /**
     * 按当前 round 的评分记录一个曲线点：每走一步（或回放/导航一步）记录一次。
     * - 实时对局：更新最后一手对应的点；
     * - 棋谱回放/导航：按当前回放步数（currentMoveIndex）用引擎评分写入对应点；
     *   上一步/悔棋由显示裁剪同步回滚（曲线点索引 = 已走步数 - 1）。
     *
     * @param requestMoveIndex 触发本次评估时的回放步数（>=0 时仅在「当前步数一致」时采用，
     *                         用于丢弃快速回放/回退时乱序返回的旧引擎结果），传 -1 表示不校验。
     */
    public void recordRoundScore(int score) {
        recordRoundScore(score, -1);
    }

    public void recordRoundScore(int score, int requestMoveIndex) {
        if (chessInfo == null) return;
        if (notationManager != null && notationManager.getCurrentNotation() != null) {
            int currentIdx = notationManager.getCurrentMoveIndex(); // 已走步数（0=初始局面）
            // 仅采用与当前步数一致的评分，丢弃快速导航/回退时乱序返回的旧引擎结果
            if (requestMoveIndex >= 0 && requestMoveIndex != currentIdx) {
                return;
            }
            // 每一步都由引擎实时评估：第 k 步（1-based）对应曲线点 index k-1（曲线不含初始局面点）
            int pointIdx = currentIdx - 1;
            if (pointIdx >= 0) {
                chessInfo.setEvalAt(pointIdx, score);
            }
            refreshScoreCurve();
            return;
        }
        // 实时对局（未加载棋谱）：写入最后一手
        int idx = chessInfo.totalMoves - 1;
        if (idx >= 0) {
            chessInfo.setEvalAt(idx, score);
        }
        refreshScoreCurve();
    }
    public SetupModeView setupModeView;
    public android.widget.ImageView flipButton;

    /** 是否处于模拟行棋演示中（供触摸/AI 守卫判断） */
    public boolean isSimulating() {
        return isSimulating;
    }

    /** 点击回合信息条中的"最优一步"：以 600ms 一步模拟第 1 条候选变线（line 0），启动前停顿 800ms */
    public void onRoundBestMoveClick() {
        if (isSimulating()) {
            return;
        }
        if (roundView != null && roundView.hasBestMove()) {
            startSimulation(0);
        }
    }

    /** 开始模拟某条候选变线（lineIndex 为引擎结果框中的真实变线序号），启动前停顿 800ms、以 600ms 一步演示 */
    public void startSimulation(int lineIndex) {
        // 已在模拟中：先返回（恢复真实局面），再重新模拟新选的变线
        if (isSimulating) stopSimulation();
        if (pikafishAI == null || chessInfo == null) return;

        java.util.List<PikafishAI.PvSequenceWithScore> lines = pikafishAI.getLastMultiPvLines();
        if (lines == null || lineIndex < 0 || lineIndex >= lines.size()) return;
        PikafishAI.PvSequenceWithScore line = lines.get(lineIndex);
        if (line == null || line.pvSequence == null || line.pvSequence.isEmpty()) return;

        // 备份真实局面，供返回时恢复
        try {
            simBackup = (ChessInfo) chessInfo.clone();
        } catch (CloneNotSupportedException e) {
            LogUtils.e("PvMActivity", "模拟行棋: 克隆局面失败", e);
            return;
        }

        // 先标记进入模拟：后续被中断 AI 线程的收尾回调中的清空
        // 会被 isSimulating 守卫跳过，避免刚设置的模拟支招被清掉
        isSimulating = true;
        if (chessView != null) {
            chessView.isSimulating = true;
            chessView.requestDraw();
        }

        // 停止任何正在进行的 AI 分析，避免干扰
        if (aiManager != null) aiManager.stopAIAnalysis();

        // 模拟行棋跟随展示：只走框中展示的步数（显示几步行棋就行几步）
        // 跟随模式下已走若干步，预览应从当前局面对应的后续着法开始，避免重放已走步
        int startIdx = suggestFollowActive ? Math.min(suggestFollowPrefix.size(), line.pvSequence.size()) : 0;
        int simCount = Math.min(startIdx + SIM_DISPLAY_STEPS, line.pvSequence.size());
        if (startIdx >= simCount) return; // 该变线已无后续可演示
        simMoves = new java.util.ArrayList<>(line.pvSequence.subList(startIdx, simCount));
        simStepIndex = 0;

        // 备份进入模拟前显示的支招线（返回真实局面时恢复）
        try {
            simSuggestBackupMoves = new java.util.ArrayList<>(chessInfo.suggestMoves);
            simSuggestBackupLabels = new java.util.ArrayList<>(chessInfo.suggestMoveLabels);
            simSuggestBackupNotations = new java.util.ArrayList<>(chessInfo.suggestMoveNotations);
            simSuggestBackupIsRed = new java.util.ArrayList<>(chessInfo.suggestMovesIsRed);
        } catch (Exception e) {
            simSuggestBackupMoves = null;
        }

        // 模拟行棋开始后不显示棋盘提示线（箭头），仅由棋子移动演示变线
        chessInfo.suggestMoves.clear();
        chessInfo.suggestMoveLabels.clear();
        chessInfo.suggestMovesIsRed.clear();

        // 支招按钮变为"返回"
        if (controlsManager != null) controlsManager.updateReturnButton(true);
        // 模拟演示期间，除"返回"外其余按钮置灰禁用
        if (controlsManager != null) controlsManager.setButtonsDisabledExceptReturn(true);
        if (roundView != null) roundView.setSimulating(true);
        highlightSimLine(lineIndex);

        // 进入模拟行棋前停顿 800ms，再开始逐步演示
        simHandler.postDelayed(this::runSimStep, SIM_START_DELAY_MS);
    }

    /** 逐步演示：每步以 600ms 间隔应用到棋盘（仅显示层），走完保持最终局面直到用户返回 */
    private void runSimStep() {
        if (!isSimulating || simMoves == null || simStepIndex >= simMoves.size()) {
            return; // 演示完成：保持最终局面，等待用户点"返回"
        }
        ChessMove.Move mv = simMoves.get(simStepIndex);
        if (mv == null || mv.fromPos == null || mv.toPos == null) {
            simStepIndex++;
            runSimStep();
            return;
        }

        // 应用一步到真实 chessInfo（模拟演示，不写历史栈）
        int movingPiece = chessInfo.piece[mv.fromPos.y][mv.fromPos.x];
        chessInfo.piece[mv.toPos.y][mv.toPos.x] = movingPiece;
        chessInfo.piece[mv.fromPos.y][mv.fromPos.x] = 0;
        chessInfo.IsRedGo = !chessInfo.IsRedGo;
        chessInfo.Select = new int[]{-1, -1};
        chessInfo.ret.clear();
        chessInfo.prePos = mv.fromPos;
        chessInfo.curPos = mv.toPos;
        simStepIndex++;

        // 移除已演示的支招箭头，使剩余支招线随模拟进度逐步跟随显示
        if (chessInfo.suggestMoves != null && !chessInfo.suggestMoves.isEmpty()) {
            chessInfo.suggestMoves.remove(0);
            if (chessInfo.suggestMoveLabels != null && !chessInfo.suggestMoveLabels.isEmpty())
                chessInfo.suggestMoveLabels.remove(0);
            if (chessInfo.suggestMovesIsRed != null && !chessInfo.suggestMovesIsRed.isEmpty())
                chessInfo.suggestMovesIsRed.remove(0);
        }

        if (chessView != null) chessView.requestDraw();
        if (roundView != null) roundView.requestDraw();

        simHandler.postDelayed(this::runSimStep, SIM_STEP_INTERVAL_MS);
    }

    /** 停止模拟并恢复进入模拟前的真实局面 */
    public void stopSimulation() {
        simHandler.removeCallbacksAndMessages(null);
        if (simBackup != null && chessInfo != null) {
            try {
                chessInfo.setInfo(simBackup);
            } catch (CloneNotSupportedException e) {
                LogUtils.e("PvMActivity", "模拟行棋: 恢复局面失败", e);
            }
            // 恢复进入模拟前显示的支招线（箭头/记谱），保持返回后的展示一致
            try {
                chessInfo.suggestMoves.clear();
                chessInfo.suggestMoveLabels.clear();
                chessInfo.suggestMoveNotations.clear();
                chessInfo.suggestMovesIsRed.clear();
                if (simSuggestBackupMoves != null) chessInfo.suggestMoves.addAll(simSuggestBackupMoves);
                if (simSuggestBackupLabels != null) chessInfo.suggestMoveLabels.addAll(simSuggestBackupLabels);
                if (simSuggestBackupNotations != null) chessInfo.suggestMoveNotations.addAll(simSuggestBackupNotations);
                if (simSuggestBackupIsRed != null) chessInfo.suggestMovesIsRed.addAll(simSuggestBackupIsRed);
            } catch (Exception e) {
                LogUtils.e("PvMActivity", "模拟行棋: 恢复支招线失败", e);
            }
        }
        simBackup = null;
        simMoves = null;
        simStepIndex = 0;
        isSimulating = false;
        if (chessView != null) {
            chessView.isSimulating = false;
            chessView.requestDraw();
        }

        if (controlsManager != null) controlsManager.updateReturnButton(false);
        // 退出模拟演示：恢复其余按钮可用
        if (controlsManager != null) controlsManager.setButtonsDisabledExceptReturn(false);
        if (roundView != null) roundView.setSimulating(false);
        clearSimLineHighlight();

        if (chessView != null) chessView.requestDraw();
        if (roundView != null) roundView.requestDraw();
    }

    /** 高亮当前正在模拟的候选变线 */
    private void highlightSimLine(int lineIndex) {
        if (engineResultContainer == null) return;
        for (int i = 0; i < engineResultContainer.getChildCount(); i++) {
            android.view.View v = engineResultContainer.getChildAt(i);
            Integer tag = (Integer) v.getTag();
            // 斑马纹与高亮都设在内容行 colRow（rowView）上；其 minWidth 已撑满整行宽度
            android.view.View rowView = (v instanceof android.view.ViewGroup && ((android.view.ViewGroup) v).getChildCount() > 0)
                    ? ((android.view.ViewGroup) v).getChildAt(0) : v;
            float density = getResources().getDisplayMetrics().density;
            rowView.setBackground(makeEngineRowBg(density, i % 2 == 0, tag != null && tag == lineIndex)); // 命中：立体金棕；否则立体木纹斑马
        }
    }

    /** 清除候选变线高亮，恢复斑马纹（立体木纹背景） */
    private void clearSimLineHighlight() {
        if (engineResultContainer == null) return;
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < engineResultContainer.getChildCount(); i++) {
            android.view.View v = engineResultContainer.getChildAt(i);
            android.view.View rowView = (v instanceof android.view.ViewGroup && ((android.view.ViewGroup) v).getChildCount() > 0)
                    ? ((android.view.ViewGroup) v).getChildAt(0) : v;
            rowView.setBackground(makeEngineRowBg(density, i % 2 == 0, false));
        }
    }

    /** 生成引擎结果行（支招栏）背景：圆角 + 上亮下暗渐变（立体感）+ 亮色描边（明显分隔线条） */
    android.graphics.drawable.GradientDrawable makeEngineRowBg(float density, boolean even, boolean highlight) {
        int top, bottom, stroke;
        if (highlight) {
            top = 0xFFB58A5C; bottom = 0xFF7A5A38; stroke = 0xFFFFD28A;
        } else if (even) {
            top = 0xFF4A3A28; bottom = 0xFF2A1F14; stroke = 0xFF7A6040;
        } else {
            top = 0xFF64503A; bottom = 0xFF3A2C1D; stroke = 0xFF8A7048;
        }
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        d.setCornerRadius(6f * density);
        d.setStroke((int) (1.5f * density), stroke);
        return d;
    }
    // 进入摆棋前保存各按钮原始可用状态；退出时只恢复原本可用的，
    // 原本就因「非加载棋局/无历史」而置灰的（如上一步/下一步）保持禁用
    private final java.util.Map<Integer, Boolean> setupButtonEnabledState = new java.util.HashMap<>();
    private Boolean flipButtonOriginalEnabled = null;
    // 模式按钮是内嵌图标-文字容器(非直接Button)，特记其进入摆棋前的可用状态
    private Boolean modeButtonOriginalEnabled = null;

    // 按钮组ID（在 PvMActivityInit 中给按钮组根布局设置该 ID 并赋值此字段）
    public int buttonGroupId = 10001;
    // 上一步/下一步按钮引用，用于在「未加载棋谱」时禁用
    public android.widget.Button btnPrev;
    public android.widget.Button btnNext;
    // 悔棋按钮引用，加载棋谱时置灰禁用
    public android.widget.Button btnRecall;
    // 对战模式：0-双人对战, 1-人机对战(玩家红), 2-人机对战(玩家黑), 3-双机对战
    public int gameMode = 0;

    // 模式按钮短名（与 ModePickerDialog.MODE_NAMES 对应，用于按钮文字）
    private static final String[] MODE_SHORT_NAMES = {"双人", "执红", "执黑", "双机"};

    // 模式按钮背景渐变（随当前模式变化，呼应 ModePickerDialog 的主题色）
    // 每项为 {渐变起始, 渐变中间, 渐变末尾, 描边}
    private static final int[][] MODE_BTN_BG = {
            {0xFF74D4C2, 0xFF52A899, 0xFF378C7D, 0xFFAAF0E2}, // 0 双人：teal（与支招原色互换）
            {0xFFD83434, 0xFFB82828, 0xFF9C2020, 0xFFF08A8A}, // 1 玩家红：红（提亮活泼）
            {0xFF4A4A4A, 0xFF3A3A3A, 0xFF2C2C2C, 0xFF8C8C8C}, // 2 玩家黑：黑灰（提亮避免死黑）
            {0xFF5C9CEC, 0xFF4A82D2, 0xFF386BB8, 0xFFA8CCF5}, // 3 双机：蓝（提亮活泼）
    };

    /** 根据背景色亮度返回对比图标色（背景亮用深色，背景暗用白色） */
    private static int contrastColor(int base) {
        int r = android.graphics.Color.red(base);
        int g = android.graphics.Color.green(base);
        int b = android.graphics.Color.blue(base);
        double lum = 0.299 * r + 0.587 * g + 0.114 * b;
        return lum > 150 ? 0xFF222222 : 0xFFFFFFFF;
    }

    /** 更新「模式」按钮：左、右各一个 glyph 表示对战双方，文字居中显示短模式名 */
    public void updateModeButton() {
        try {
            View btn = findViewById(R.id.btn_mode);
            if (btn == null) return;
            float density = getResources().getDisplayMetrics().density;
            ImageView iconLeft = btn.findViewById(R.id.mode_icon_left);
            ImageView iconRight = btn.findViewById(R.id.mode_icon_right);
            TextView modeText = btn.findViewById(R.id.mode_text);

            int m = Math.max(0, Math.min(MODE_SHORT_NAMES.length - 1, gameMode));
            if (modeText != null) modeText.setText(MODE_SHORT_NAMES[m]);

            // 模式按钮背景与图标均随当前模式变化：背景用主题渐变，图标用对比色保证可见
            int iconColor = 0xFFFFFFFF;
            if (m < MODE_BTN_BG.length) {
                int[] c = MODE_BTN_BG[m];
                iconColor = contrastColor(c[1]);
                android.graphics.drawable.GradientDrawable shape =
                        new android.graphics.drawable.GradientDrawable();
                shape.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TL_BR);
                shape.setColors(new int[]{c[0], c[1], c[2]});
                shape.setCornerRadius(10 * density);
                shape.setStroke((int) (1 * density), c[3]);
                android.graphics.drawable.RippleDrawable ripple =
                        new android.graphics.drawable.RippleDrawable(
                                android.content.res.ColorStateList.valueOf(0x33FFFFFF), shape, null);
                btn.setBackground(ripple);
            }

            ModeIconDrawable leftD = new ModeIconDrawable(this, gameMode, density, ModeIconDrawable.SIDE_LEFT, iconColor);
            ModeIconDrawable rightD = new ModeIconDrawable(this, gameMode, density, ModeIconDrawable.SIDE_RIGHT, iconColor);
            if (iconLeft != null) iconLeft.setImageDrawable(leftD);
            if (iconRight != null) iconRight.setImageDrawable(rightD);

            // 同步刷新右上角「切换模式」图标按钮（仅图标，显示双方对照）
            android.view.View ms = findViewById(R.id.btn_mode_switch);
            if (ms instanceof android.widget.ImageView) {
                ((android.widget.ImageView) ms).setImageDrawable(
                        new ModeIconDrawable(this, gameMode, density, ModeIconDrawable.SIDE_BOTH, 0xFFFFFFFF));
            }
        } catch (Exception e) {
            LogUtils.e("PvMActivity", "updateModeButton failed", e);
        }
    }

    // 继续对局后的回合计数器，用于控制和棋提示的频率
    public int continueGameRoundCount = 0;
    // AI相关变量
    public volatile PikafishAI pikafishAI;
    
    // 行棋时间记录
    public long redTime = 0; // 红方行棋时间（毫秒）
    public long blackTime = 0; // 黑方行棋时间（毫秒）
    public long currentTurnStartTime = 0; // 当前回合开始时间
    
    // 时间更新线程
    private ScheduledExecutorService timeUpdateExecutor;

    // 局面评估线程池（单线程，新任务取消旧任务）
    private ScheduledExecutorService evaluationExecutor;
    
    // 模块管理器
    public PvMActivityNotation notationManager;
    public PvMActivitySetup setupManager;
    public PvMActivityControls controlsManager;
    public PvMActivityAI aiManager;
    public PvMActivityGame gameManager;
    
    // 相机拍照临时文件
    private java.io.File cameraImageFile;
    
    // 识别服务
    private ChessRecognitionService recognitionService;
    private PhotoCaptureManager photoCaptureManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pvm);
        // 初始化静态实例
        weakInstance = new WeakReference<>(this);
        relativeLayout = (RelativeLayout) findViewById(R.id.relativeLayout);

        // 恢复保存的状态
        if (savedInstanceState != null) {
            String filePath = savedInstanceState.getString("camera_image_file");
            if (filePath != null) {
                cameraImageFile = new java.io.File(filePath);
            }
        }

        // 先初始化模块
        initModules();
        
        // 初始化拍照管理器
        photoCaptureManager = new PhotoCaptureManager(this);
        
        // 使用PvMActivityInit类处理初始化逻辑
        PvMActivityInit init = new PvMActivityInit(this);
        init.init();
        init.initViews();
        init.initBackgroundTasks();
        // 识别服务(ONNX)的初始化延迟到 PikafishAI 初始化完成后触发，
        // 避免两个重量级 native 初始化并发执行导致内存压力崩溃
        
        // 初始化时间更新线程
        initTimeUpdateExecutor();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存相机文件路径，防止Activity重建时丢失
        if (cameraImageFile != null) {
            outState.putString("camera_image_file", cameraImageFile.getAbsolutePath());
        }
    }
    
    // 初始化时间更新线程
    private void initTimeUpdateExecutor() {
        timeUpdateExecutor = Executors.newSingleThreadScheduledExecutor();
        evaluationExecutor = Executors.newSingleThreadScheduledExecutor();
        // 每100毫秒更新一次时间显示
        timeUpdateExecutor.scheduleAtFixedRate(() -> {
            if (currentTurnStartTime > 0 && chessInfo != null && roundView != null) {
                runOnUiThread(() -> {
                    // 计算当前回合已经经过的时间
                    long elapsed = System.currentTimeMillis() - currentTurnStartTime;
                    // 显示当前行棋方的时间，同时保留对方时间
                    if (chessInfo.IsRedGo) {
                        roundView.setTime(elapsed, blackTime);
                    } else {
                        roundView.setTime(redTime, elapsed);
                    }
                });
            } else if (chessInfo != null && roundView != null) {
                // 行棋后保留当前时间
                runOnUiThread(() -> {
                    roundView.setTime(redTime, blackTime);
                });
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
    }
    
    // 初始化模块
    private void initModules() {
        notationManager = new PvMActivityNotation(this);
        setupManager = new PvMActivitySetup(this);
        controlsManager = new PvMActivityControls(this);
        aiManager = new PvMActivityAI(this);
        gameManager = new PvMActivityGame(this);
    }
    
    // 递归设置按钮监听器，处理嵌套布局
    private void setupButtonListeners(ViewGroup viewGroup) {
        controlsManager.setupButtonListeners(viewGroup);
    }

    /**
     * 摆棋模式下切换"摆棋/完成"按钮的视觉状态，并禁用/恢复其它按钮。
     * @param entering true=进入摆棋（显示"完成"、变色、禁用其它按钮）；false=退出摆棋（还原）
     */
    public void applySetupModeButtonUI(boolean entering) {
        try {
            View setupBtn = findViewById(R.id.btn_setup);
            if (setupBtn instanceof Button) {
                Button btn = (Button) setupBtn;
                if (entering) {
                    btn.setText("完成");
                    btn.setBackgroundResource(R.drawable.bg_board_btn_done);
                    btn.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_done, 0, 0);
                } else {
                    btn.setText("摆棋");
                    btn.setBackgroundResource(R.drawable.bg_board_btn_setup);
                    btn.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_setup, 0, 0);
                }
            }
            // 进入摆棋时禁用其它按钮，退出时恢复
            setNonSetupButtonsEnabled(!entering);
        } catch (Exception e) {
            LogUtils.e("PvMActivity", "applySetupModeButtonUI failed", e);
        }
    }

    // 递归禁用/启用按钮组中除摆棋外的所有按钮。
    // 进入摆棋(enabled=false)时记录各按钮原始可用状态再全部禁用；
    // 退出摆棋(enabled=true)时只恢复原本可用的，原本禁用的保持禁用。
    private void setNonSetupButtonsEnabled(boolean enabled) {
        try {
            if (enabled) {
                // 退出摆棋：仅当确实存在进入时保存的状态才恢复按钮。
                // 若为空（如新局时点此，并非从摆棋退出），不改动其它按钮，
                // 避免把原本就因「非加载棋局/无历史」而置灰的上一步/下一步错误启用。
                if (!setupButtonEnabledState.isEmpty() || flipButtonOriginalEnabled != null
                    || modeButtonOriginalEnabled != null) {
                    View bg = findViewById(buttonGroupId);
                    if (bg instanceof ViewGroup) {
                        restoreButtonsRecursively((ViewGroup) bg, R.id.btn_setup);
                    }
                    setupButtonEnabledState.clear();
                    flipButtonOriginalEnabled = null;
                }
                // 退出摆棋：恢复模式按钮（仅恢复原本可用的，原本禁用的保持灰）
                View modeBtn = findViewById(R.id.btn_mode);
                if (modeBtn != null && modeButtonOriginalEnabled != null) {
                    boolean orig = modeButtonOriginalEnabled;
                    modeBtn.setEnabled(orig);
                    modeBtn.setAlpha(orig ? 1f : 0.4f);
                    modeButtonOriginalEnabled = null;
                }
            } else {
                // 进入摆棋：先记录原始状态，再全部禁用
                View bg = findViewById(buttonGroupId);
                if (bg instanceof ViewGroup) {
                    saveAndDisableRecursively((ViewGroup) bg, R.id.btn_setup);
                }
                // 模式按钮是容器(内嵌图标-文字)，递归不会禁用；摆棋时切换模式会破坏局面，显式置灰禁用
                View modeBtn = findViewById(R.id.btn_mode);
                if (modeBtn != null) {
                    modeButtonOriginalEnabled = modeBtn.isEnabled();
                    modeBtn.setEnabled(false);
                    modeBtn.setAlpha(0.4f);
                }
            }
            // 翻转按钮在摆棋模式下始终可用（仅翻转换看，不影响摆棋），保持高亮
            // 注意：当前翻转按钮已并入按钮组(R.id.btn_flip)，PvMActivity.flipButton 字段为 null，
            // 须直接按 id 取按钮，不能用 flipButton 字段（否则永远走不进此分支）
            View flipBtn = findViewById(R.id.btn_flip);
            if (flipBtn != null) {
                flipBtn.setEnabled(true);
                flipBtn.setAlpha(1f);
            }
            // 摆棋面板（SetupModeView：清空棋盘 / 拍照 / 图片）
            // 属于摆棋功能本身，无论进入还是退出摆棋都必须保持可用，绝不能禁用。
            // 它本身不在主按钮组里，这里做显式保证以防布局变化时被误伤。
            if (setupModeView != null) {
                setupModeView.setEnabled(true);
                setupModeView.setAlpha(1f);
            }
            // 棋盘视图在摆棋模式下用于放置棋子，同样必须保持可交互
            if (chessView != null) {
                chessView.setEnabled(true);
            }
        } catch (Exception e) {
            LogUtils.e("PvMActivity", "setNonSetupButtonsEnabled failed", e);
        }
    }

    private void saveAndDisableRecursively(ViewGroup viewGroup, int excludeId) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof Button) {
                if (child.getId() != excludeId) {
                    setupButtonEnabledState.put(child.getId(), child.isEnabled());
                    child.setEnabled(false);
                    child.setAlpha(0.4f);
                }
            } else if (child instanceof ViewGroup) {
                saveAndDisableRecursively((ViewGroup) child, excludeId);
            }
        }
    }

    private void restoreButtonsRecursively(ViewGroup viewGroup, int excludeId) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof Button) {
                if (child.getId() != excludeId) {
                    Boolean orig = setupButtonEnabledState.get(child.getId());
                    boolean enable = orig != null ? orig : true;
                    child.setEnabled(enable);
                    child.setAlpha(enable ? 1f : 0.4f);
                }
            } else if (child instanceof ViewGroup) {
                restoreButtonsRecursively((ViewGroup) child, excludeId);
            }
        }
    }

    /**
     * 停止所有 AI 相关工作：分析、引擎搜索、局面评分，并复位支招按钮。
     * 在摆棋开始（无论通过按钮还是拍照识别）时调用。
     */
    public void stopAllAI() {
        if (aiManager != null) {
            aiManager.stopAIAnalysis();
        }
        if (pikafishAI != null) {
            pikafishAI.interrupt();
        }
        // 取消进行中的局面评分任务，避免摆棋期间引擎仍在算分
        if (evaluationExecutor != null && !evaluationExecutor.isShutdown()) {
            try {
                evaluationExecutor.shutdownNow();
            } catch (Exception e) {
                LogUtils.e("PvMActivity", "stopAllAI: shutdown evaluation failed", e);
            }
            try {
                evaluationExecutor = Executors.newSingleThreadScheduledExecutor();
            } catch (Exception e) {
                LogUtils.e("PvMActivity", "stopAllAI: recreate evaluation failed", e);
            }
        }
        if (controlsManager != null) {
            controlsManager.updateSuggestButton(false);
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        return controlsManager.handleTouch(view, event);
    }

    @Override
    public void onClick(View view) {
        try {
            long currentTime = System.currentTimeMillis();
            if (currentTime - curClickTime < MIN_CLICK_DELAY_TIME) {
                LogUtils.d("PvMActivity", "Button click skipped due to debounce");
                return;
            }
            curClickTime = currentTime;

            int viewId = view.getId();
            LogUtils.d("PvMActivity", "Button clicked: " + viewId);

            // 模拟行棋演示中：点击支招/返回按钮以外的任何按钮，先退出模拟、恢复正常局面
            // 翻转按钮( btn_flip )在模拟中仍可使用，仅翻转显示、不退出模拟
            if (isSimulating && viewId != R.id.btn_statistics && viewId != R.id.btn_flip) {
                stopSimulation();
            }

            if (viewId == R.id.btn_retry) {
                controlsManager.handleRetryButton();
            } else if (viewId == R.id.btn_prev) {
                // 上一步
                controlsManager.handlePrevButton();
            } else if (viewId == R.id.btn_next) {
                // 下一步
                controlsManager.handleNextButton();
            } else if (viewId == R.id.btn_recall) {
                controlsManager.handleRecallButton();
            } else if (viewId == R.id.btn_save) {
                // 保存棋谱 - 使用SAF选择保存位置
                notationManager.showSaveNotationDialog();
            } else if (viewId == R.id.btn_settings) {
                controlsManager.handleSettingsButton();
            } else if (viewId == R.id.btn_mode) {
                // 切换对战模式
                controlsManager.handleModeButton();
            } else if (viewId == R.id.btn_load) {
                // 加载棋谱 - 使用SAF选择文件
                notationManager.showLoadNotationDialog();
            } else if (viewId == R.id.btn_statistics) {
                // 模拟行棋演示中：此按钮为"返回"，点击恢复正常局面；否则触发支招
                if (isSimulating) {
                    stopSimulation();
                } else {
                    controlsManager.handleStatisticsButton();
                }
            } else if (viewId == R.id.btn_setup) {
                // 切换摆棋模式
                setupManager.toggleSetupMode();
            } else if (viewId == R.id.btn_flip) {
                // 翻转棋盘（仅显示层）
                if (chessView != null) {
                    chessView.toggleFlip();
                }
            }
        } catch (Exception e) {
            LogUtils.e("PvMActivity", "Error in button click handler", e);
        }
    }
    
    // 格式化时间（毫秒转分:秒）
    public String formatTime(long milliseconds) {
        int seconds = (int) (milliseconds / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    /**
     * 公共方法：触发当前局面快速评分（depth 1，约300ms返回）
     * 在任何棋子移动、悔棋、摆棋结束、棋谱导航等场景后调用
     */
    public void triggerPositionEvaluation() {
        if (pikafishAI != null && pikafishAI.isInitialized() && chessInfo != null) {
            // 保存当前局面快照，避免评估过程中棋盘被修改
            final ChessInfo snapshotInfo = new ChessInfo();
            for (int r = 0; r < 10; r++) {
                for (int c = 0; c < 9; c++) {
                    snapshotInfo.piece[r][c] = chessInfo.piece[r][c];
                }
            }
            snapshotInfo.IsRedGo = chessInfo.IsRedGo;
            snapshotInfo.setting = chessInfo.setting;
            final boolean isRedTurnNow = chessInfo.IsRedGo;
            // 记录触发评估时的回放步数，用于回放乱序校验（仅加载/回放棋谱时有意义）
            final int requestMoveIndex = (notationManager != null && notationManager.getCurrentNotation() != null)
                    ? notationManager.getCurrentMoveIndex() : -1;
            evaluationExecutor.submit(() -> {
                int score = pikafishAI.evaluatePositionQuickly(snapshotInfo);
                score = normalizeScore(score, isRedTurnNow);
                final int finalScore = score;
                final int reqIdx = requestMoveIndex;
                runOnUiThread(() -> {
                    if (roundView != null) {
                        roundView.setMoveScore(finalScore);
                    }
                    // 按当前局面记录曲线点（每走一步记录一次；回退时随之回滚）
                    recordRoundScore(finalScore, reqIdx);
                });
            });
        }
    }
    
    // 标准化评分，确保显示优势方积分
    public static int normalizeScore(int score, boolean isRedTurn) {
        // 确保评分始终以红方为基准
        // 当黑方行棋时，引擎返回的评分是基于黑方视角的，需要取反
        return isRedTurn ? score : -score;
    }
    
    // 更新时间显示
    public void updateTimeDisplay() {
        if (roundView != null) {
            roundView.setTime(redTime, blackTime);
        }
    }
    
    // 开始当前回合计时
    public void startTurnTimer() {
        // 只有在currentTurnStartTime为0时才重新开始计时
        // 这样可以避免点击棋子时重置时间
        if (currentTurnStartTime == 0) {
            currentTurnStartTime = System.currentTimeMillis();
        }
    }
    
    // 结束当前回合计时
    public void stopTurnTimer() {
        if (currentTurnStartTime > 0) {
            // 计算当前回合已经经过的时间
            long elapsed = System.currentTimeMillis() - currentTurnStartTime;
            // 更新当前行棋方的时间
            if (chessInfo != null) {
                // 现在stopTurnTimer是在updateAllInfo之前调用的
                // 所以chessInfo.IsRedGo还没有切换，使用当前的IsRedGo来更新时间
                if (chessInfo.IsRedGo) {
                    // 当前是红方回合，更新红方时间
                    redTime = elapsed;
                } else {
                    // 当前是黑方回合，更新黑方时间
                    blackTime = elapsed;
                }
            }
            // 行棋后保留当前时间，不归零
            currentTurnStartTime = 0;
            // 更新时间显示
            updateTimeDisplay();
        }
    }
    
    // 生成走法字符串
    public String generateMoveString(ChessInfo chessInfo, int piece, Pos fromPos, Pos toPos, boolean isRed) {
        // 参数检查
        if (fromPos == null || toPos == null) {
            return "未知走法";
        }

        // 基本棋子类型（与红黑无关）：0=兵/卒 1=帅/将 2=士 3=象 4=马 5=车 6=炮
        int baseType = piece % 7;
        boolean isPawn = (baseType == 0);
        String prefix = "";

        // 收集同列相同棋子（用于「前/后」或数字前缀）
        boolean isSameColumn = false;
        boolean isSameRow = false;
        java.util.List<Pos> samePieces = new java.util.ArrayList<>();
        if (chessInfo != null && chessInfo.piece != null) {
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 9; x++) {
                    if (x == fromPos.x && chessInfo.piece[y][x] == piece) {
                        samePieces.add(new Pos(x, y));
                    }
                }
            }
        }

        if (samePieces.size() > 1) {
            isSameColumn = true;
            // 按 y 排序（红方 y 大为前，黑方 y 小为前）
            for (int i = 0; i < samePieces.size() - 1; i++) {
                for (int j = 0; j < samePieces.size() - i - 1; j++) {
                    Pos p1 = samePieces.get(j);
                    Pos p2 = samePieces.get(j + 1);
                    if (p1 != null && p2 != null && p1.y > p2.y) {
                        samePieces.set(j, p2);
                        samePieces.set(j + 1, p1);
                    }
                }
            }
            if (isPawn) {
                int index = samePieces.indexOf(new Pos(fromPos.x, fromPos.y)) + 1;
                prefix = getColChar(index);
            } else {
                if (samePieces.size() == 2) {
                    Pos frontPiece = isRed ? samePieces.get(1) : samePieces.get(0);
                    prefix = (fromPos.y == frontPiece.y) ? "前" : "后";
                } else if (samePieces.size() == 3) {
                    Pos frontPiece = isRed ? samePieces.get(2) : samePieces.get(0);
                    Pos middlePiece = samePieces.get(1);
                    if (fromPos.y == frontPiece.y) prefix = "前";
                    else if (fromPos.y == middlePiece.y) prefix = "中";
                    else prefix = "后";
                } else if (samePieces.size() > 3) {
                    int index = samePieces.indexOf(new Pos(fromPos.x, fromPos.y)) + 1;
                    if (isRed) prefix = (index == samePieces.size()) ? "前" : getColChar(index);
                    else prefix = (index == 1) ? "前" : getColChar(index);
                }
            }
        } else {
            // 再检查同一行是否有多个相同棋子
            samePieces.clear();
            if (chessInfo != null && chessInfo.piece != null) {
                for (int y = 0; y < 10; y++) {
                    for (int x = 0; x < 9; x++) {
                        if (y == fromPos.y && chessInfo.piece[y][x] == piece) {
                            samePieces.add(new Pos(x, y));
                        }
                    }
                }
            }
            if (samePieces.size() > 1) {
                isSameRow = true;
                for (int i = 0; i < samePieces.size() - 1; i++) {
                    for (int j = 0; j < samePieces.size() - i - 1; j++) {
                        Pos p1 = samePieces.get(j);
                        Pos p2 = samePieces.get(j + 1);
                        if (p1 != null && p2 != null && p1.x > p2.x) {
                            samePieces.set(j, p2);
                            samePieces.set(j + 1, p1);
                        }
                    }
                }
                if (samePieces.size() == 2) {
                    Pos frontPiece = isRed ? samePieces.get(1) : samePieces.get(0);
                    prefix = (fromPos.x == frontPiece.x) ? "前" : "后";
                } else if (samePieces.size() == 3) {
                    Pos frontPiece = isRed ? samePieces.get(2) : samePieces.get(0);
                    Pos middlePiece = samePieces.get(1);
                    if (fromPos.x == frontPiece.x) prefix = "前";
                    else if (fromPos.x == middlePiece.x) prefix = "中";
                    else prefix = "后";
                }
            }
        }

        // 起始列号（中文数字）
        int startCol = isRed ? (9 - fromPos.x) : (fromPos.x + 1);
        startCol = Math.max(1, Math.min(9, startCol));
        String startColStr = getColChar(startCol);

        // 移动类型
        String moveType;
        int colDiff = toPos.x - fromPos.x;
        int rowDiff = toPos.y - fromPos.y;
        if (colDiff == 0) {
            // 纵向移动：车、炮、兵、帅/将、士、象、马 都用进/退
            moveType = (isRed ? (rowDiff > 0) : (rowDiff < 0)) ? "进" : "退";
        } else {
            // 横向移动：车、炮、兵/卒、帅/将 用「平」；士、象、马用进/退
            if (baseType == 5 || baseType == 6 || baseType == 0 || baseType == 1) {
                moveType = "平";
            } else {
                moveType = (isRed ? (rowDiff > 0) : (rowDiff < 0)) ? "进" : "退";
            }
        }

        // 目标位置
        String targetPos;
        if ("平".equals(moveType)) {
            int targetCol = isRed ? (9 - toPos.x) : (toPos.x + 1);
            targetCol = Math.max(1, Math.min(9, targetCol));
            targetPos = getColChar(targetCol);
        } else {
            boolean isSpecialPiece = (baseType == 2 || baseType == 3 || baseType == 4); // 士、象、马
            if (isSpecialPiece) {
                // 斜向移动：用目标列号
                int targetCol = isRed ? (9 - toPos.x) : (toPos.x + 1);
                targetCol = Math.max(1, Math.min(9, targetCol));
                targetPos = getColChar(targetCol);
            } else {
                // 车、炮、兵/卒、帅/将 纵向移动：用步数（格数）
                int moveSteps = Math.max(1, Math.abs(toPos.y - fromPos.y));
                targetPos = getColChar(moveSteps);
            }
        }

        String pieceName = getPieceName(piece, isRed);

        // 组装走法串（红方用中文数字，黑方再转阿拉伯数字，与棋谱规范一致）
        String moveString;
        if ((isSameColumn || isSameRow) && !prefix.isEmpty()) {
            if (isPawn) {
                moveString = prefix + pieceName + moveType + targetPos;
            } else {
                moveString = prefix + pieceName + startColStr + moveType + targetPos;
            }
        } else {
            moveString = pieceName + startColStr + moveType + targetPos;
        }

        if (!isRed) {
            // 黑方记谱：中文数字转为「全角」阿拉伯数字，与本仓库 PGN 约定一致
            // （如 车６进１、卒５平４ 均用全角 １２３４５６７８９，而非半角 123456789）
            moveString = toFullWidthDigits(moveString);
        }

        return moveString;
    }

    // 中文数字 → 全角阿拉伯数字（用于黑方记谱，符合本仓库 PGN 约定）
    private String toFullWidthDigits(String s) {
        if (s == null) return null;
        return s.replace("零", "０").replace("一", "１").replace("二", "２")
                .replace("三", "３").replace("四", "４").replace("五", "５")
                .replace("六", "６").replace("七", "７").replace("八", "８")
                .replace("九", "９");
    }

    // 列号/步数 → 中文数字（一~九）
    private String getColChar(int col) {
        String[] cols = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (col >= 1 && col <= 9) {
            return cols[col];
        }
        return String.valueOf(col);
    }
    
    // 获取棋子名称
    private String getPieceName(int piece, boolean isRed) {
        switch (piece) {
            case 1: return "将"; // 黑将
            case 2: return "士"; // 黑士
            case 3: return "象"; // 黑象
            case 4: return "马"; // 黑马
            case 5: return "车"; // 黑车
            case 6: return "炮"; // 黑炮
            case 7: return "卒"; // 黑卒
            case 8: return "帅"; // 红帅
            case 9: return "仕"; // 红士
            case 10: return "相"; // 红相
            case 11: return "马"; // 红马
            case 12: return "车"; // 红车
            case 13: return "炮"; // 红炮
            case 14: return "兵"; // 红兵
            default: return "未知";
        }
    }
    
    // 获取棋盘坐标
    public int[] getPos(MotionEvent e) {
        int[] pos = new int[2];
        if (chessView == null || e == null) {
            pos[0] = pos[1] = -1;
            return pos;
        }
        double x = e.getX();
        double y = e.getY();
        int[] dis = new int[]{
                chessView.Scale(3), chessView.Scale(41), chessView.Scale(80), chessView.Scale(85)
        };
        x = x - dis[0];
        y = y - dis[1];
        if (x % dis[3] <= dis[2] && y % dis[3] <= dis[2]) {
            pos[0] = (int) Math.floor(x / dis[3]);
            pos[1] = 9 - (int) Math.floor(y / dis[3]);
            // 反转y坐标，与绘制时的逻辑保持一致
            if (pos[0] >= 9 || pos[1] >= 10 || pos[1] < 0) {
                pos[0] = pos[1] = -1;
            }
        } else {
            pos[0] = pos[1] = -1;
        }
        return pos;
    }
    
    // 相机拍照
    public void dispatchCameraIntent() {
        if (photoCaptureManager != null) {
            photoCaptureManager.handleCameraClick();
        }
    }
    
    // 图片选择
    public void dispatchGalleryIntent() {
        if (photoCaptureManager != null) {
            photoCaptureManager.handleGalleryClick();
        }
    }
    
    // 创建临时图片文件
    private java.io.File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "CHESS_" + timeStamp + "_";
        java.io.File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }
        java.io.File image = java.io.File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        LogUtils.d("PvMActivity", "创建图片文件: " + image.getAbsolutePath());
        return image;
    }

    // 初始化识别服务
    private volatile boolean recognitionInitDone = false;

    void initRecognitionService() {
        if (recognitionService == null) {
            new Thread(() -> {
                try {
                    recognitionService = new ChessRecognitionService(this);
                    recognitionService.initialize();
                    recognitionInitDone = true;
                    LogUtils.d("PvMActivity", "Recognition service initialized");
                } catch (Exception e) {
                    LogUtils.e("PvMActivity", "Failed to init recognition: " + e.getMessage());
                    recognitionInitDone = true; // 标记完成，避免死等
                }
            }).start();
        }
    }
    
    // 处理识别结果
    public void processRecognitionResult(android.graphics.Bitmap bitmap) {
        if (recognitionService == null) {
            initRecognitionService();
        }
        
        // 在后台线程中处理，避免ANR
        new Thread(() -> {
            try {
                // 等待初始化完成（最多等 10 秒）
                int waited = 0;
                while (!recognitionInitDone && waited < 100) {
                    Thread.sleep(100);
                    waited++;
                }
                if (recognitionService == null) {
                    runOnUiThread(() -> Toast.makeText(this, "识别服务未就绪", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                final android.graphics.Bitmap finalBitmap = bitmap;
                ChessInfo recognizedInfo = recognitionService.recognize(finalBitmap);
                if (recognizedInfo != null) {
                    runOnUiThread(() -> {
                        try {
                            applyRecognitionResult(recognizedInfo);
                            if (chessInfo != null) {
                                String side = chessInfo.IsRedGo ? "红方先行" : "黑方先行";
                                Toast.makeText(PvMActivity.this, "识别成功，" + side, Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            LogUtils.e("PvMActivity", "应用识别结果失败: " + e.getMessage(), e);
                            Toast.makeText(PvMActivity.this, "应用识别结果失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        } finally {
                            // 释放缩放后的bitmap
                            if (finalBitmap != null && !finalBitmap.isRecycled()) {
                                finalBitmap.recycle();
                            }
                        }
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show());
                    // 释放bitmap
                    if (finalBitmap != null && !finalBitmap.isRecycled()) {
                        finalBitmap.recycle();
                    }
                }
            } catch (Exception e) {
                LogUtils.e("PvMActivity", "Recognition error: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(this, "出错: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    // 应用识别结果到棋盘：识别结果 → FEN → chessInfo → 棋盘显示（链路一致）
    private void applyRecognitionResult(ChessInfo recognizedInfo) {
        if (recognizedInfo != null && chessInfo != null) {
            FENHandler fenHandler = new FENHandler();
            
            // 1. 统计识别结果中的棋子数量
            int recognizedPieceCount = 0;
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    if (recognizedInfo.piece[i][j] > 0) recognizedPieceCount++;
                }
            }
            
            // 2. 识别结果 → FEN
            String fen = fenHandler.generateFEN(recognizedInfo);
            LogUtils.d("PvMActivity", "识别棋子数=" + recognizedPieceCount + ", FEN=" + fen);
            
            // 3. FEN → ChessInfo（验证 FEN 解析正确性）
            ChessInfo parsedInfo = fenHandler.fenToChessInfo(fen);
            
            // 4. 自检：比对 FEN 往返是否一致
            int matchCount = 0, mismatchCount = 0;
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    if (recognizedInfo.piece[i][j] == parsedInfo.piece[i][j]) {
                        matchCount++;
                    } else {
                        mismatchCount++;
                    }
                }
            }
            LogUtils.d("PvMActivity", "FEN往返自检: 匹配=" + matchCount + "/90, 不匹配=" + mismatchCount);
            if (mismatchCount > 0) {
                LogUtils.e("PvMActivity", "FEN往返不一致！位置差异=" + mismatchCount);
            }
            
            // 5. 复制 FEN 解析后的棋子到 chessInfo
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    chessInfo.piece[i][j] = parsedInfo.piece[i][j];
                }
            }
            chessInfo.IsRedGo = parsedInfo.IsRedGo;
            
            // 6. 保存识别的 FEN 到 notationManager（关键！）
            if (notationManager != null) {
                notationManager.setSetupFEN(fen);
                LogUtils.d("PvMActivity", "已保存识别FEN到notationManager: " + fen);
            }
            
            // 7. 进入摆棋模式
            if (!chessInfo.IsSetupMode) {
                // 停止所有 AI（分析、引擎搜索、局面评分）并复位支招按钮
                stopAllAI();
                stopTurnTimer();
                chessInfo.IsSetupMode = true;
                gameMode = 0; // 重置为双人模式，防止 AI 在摆棋模式下触发
                if (setupModeView != null) {
                    android.widget.RelativeLayout.LayoutParams paramsSetup = (android.widget.RelativeLayout.LayoutParams) setupModeView.getLayoutParams();
                    if (paramsSetup != null) {
                        paramsSetup.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
                        paramsSetup.addRule(android.widget.RelativeLayout.BELOW, R.id.roundView);
                        paramsSetup.width = android.widget.RelativeLayout.LayoutParams.MATCH_PARENT;
                        paramsSetup.height = android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT;
                        paramsSetup.setMargins(
                            getResources().getDimensionPixelOffset(R.dimen.setup_mode_margin_left),
                            getResources().getDimensionPixelOffset(R.dimen.setup_mode_margin_top),
                            getResources().getDimensionPixelOffset(R.dimen.setup_mode_margin_right),
                            getResources().getDimensionPixelOffset(R.dimen.setup_mode_margin_bottom)
                        );
                        setupModeView.setLayoutParams(paramsSetup);
                    }
                    setupModeView.setVisibility(View.VISIBLE);
                    setupModeView.bringToFront();
                }
                if (roundView != null) {
                    roundView.setVisibility(View.GONE);
                }
                // 摆棋按钮变为"完成"：变色 + 变图标，并禁用其它按钮
                applySetupModeButtonUI(true);
            }
            
            // 8. 重新计算攻击棋子数量
            chessInfo.attackNum_B = 0;
            chessInfo.attackNum_R = 0;
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    int piece = chessInfo.piece[i][j];
                    if (piece != 0) {
                        if (piece == 4 || piece == 5 || piece == 6 || piece == 7) {
                            chessInfo.attackNum_B++;
                        }
                        if (piece == 11 || piece == 12 || piece == 13 || piece == 14) {
                            chessInfo.attackNum_R++;
                        }
                    }
                }
            }
            
            // 9. 更新所有视图
            if (chessView != null) {
                chessView.setChessInfo(chessInfo);
                chessView.requestDraw();
                chessView.invalidate();
            }
            if (roundView != null) {
                roundView.setChessInfo(chessInfo);
                roundView.requestDraw();
            }
            if (setupModeView != null) {
                setupModeView.setChessInfo(chessInfo);
                setupModeView.invalidate();
                setupModeView.postInvalidate();
            }
            
            // 初始化支招相关列表（不能设为null，否则AI代码会崩溃）
            chessInfo.suggestMoves = new ArrayList<>();
            chessInfo.suggestMoveLabels = new ArrayList<>();
            chessInfo.suggestMovesIsRed = new ArrayList<>();
            chessInfo.suggestMoveNotations = new ArrayList<>();
            chessInfo.suggestFromPos = null;
            chessInfo.suggestToPos = null;
            
            // 10. 重置游戏状态（为退出摆棋模式做准备）
            chessInfo.status = 1; // 游戏状态：进行中
            // 重置 infoSet，清空之前的记录
            infoSet = new InfoSet();
            try {
                infoSet.pushInfo(chessInfo);
                LogUtils.d("PvMActivity", "已重置infoSet并保存识别局面");
            } catch (CloneNotSupportedException e) {
                LogUtils.e("PvMActivity", "重置infoSet失败: " + e.getMessage());
            }
            
            // 11. 验证显示：从 chessInfo 重新生成 FEN，确认显示与 FEN 一致
            String displayFen = fenHandler.generateFEN(chessInfo);
            if (!fen.equals(displayFen)) {
                LogUtils.e("PvMActivity", "FEN→显示不一致！原FEN=" + fen + ", 显示FEN=" + displayFen);
            } else {
                LogUtils.d("PvMActivity", "FEN→显示验证通过 ✓, 棋子数=" + recognizedPieceCount 
                    + ", 红攻击子=" + chessInfo.attackNum_R + ", 黑攻击子=" + chessInfo.attackNum_B);
            }
        }
    }
    
    // 处理Activity结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        LogUtils.d("PvMActivity", "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            // 从NotationActivity返回，加载选中的棋谱
                String fen = data.getStringExtra("fen");
                if (fen != null) {
                    // 加载棋谱
                    ChessNotation notation = new ChessNotation();
                    notation.setFen(fen);
                    notationManager.setCurrentNotation(notation);
                    notationManager.setCurrentMoveIndex(0);
                    notationManager.generateBoardStateFromNotation();
                }
        } else if (requestCode == 1002 && resultCode == RESULT_OK && data != null) {
            // 从文件选择器返回，加载选中的棋谱文件
            Uri uri = data.getData();
            if (uri != null) {
                notationManager.loadChessNotationFromUri(uri);
            }
        } else if (requestCode == 1003 && resultCode == RESULT_OK && data != null) {
            // 从文件保存对话框返回，保存棋谱
            Uri uri = data.getData();
            if (uri != null) {
                notationManager.saveChessNotationToUri(uri);
            }
        } else if ((requestCode == 100 || requestCode == 101) && resultCode == RESULT_OK) {
            // 处理相机拍照或图片选择结果 - 使用 PhotoCaptureManager
            if (photoCaptureManager != null) {
                photoCaptureManager.handleActivityResult(requestCode, resultCode, data);
            }
        }
    }
    
    // 处理权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (photoCaptureManager != null) {
            photoCaptureManager.handlePermissionResult(requestCode, grantResults);
        }
    }
    
    // 处理返回键
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 显示退出确认对话框
            new AlertDialog.Builder(this)
                    .setTitle("退出游戏")
                    .setMessage("确定要退出游戏吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        finish();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    // 生命周期方法
    @Override
    protected void onPause() {
        super.onPause();
        // 暂停音乐（添加 try-catch 防止资源已释放时的 IllegalStateException）
        if (backMusic != null) {
            try {
                if (backMusic.isPlaying()) {
                    backMusic.pause();
                }
            } catch (IllegalStateException e) {
                LogUtils.w("PvMActivity", "backMusic 状态异常，资源可能已释放: " + e.getMessage());
                backMusic = null;
            }
        }
        // 暂停时间更新线程
        if (timeUpdateExecutor != null) {
            timeUpdateExecutor.shutdownNow();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 停止AI分析
        if (aiManager != null) {
            aiManager.stopAIAnalysis();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 恢复音乐（添加 try-catch 防止资源已释放时的 IllegalStateException）
        if (backMusic != null && setting != null && setting.isMusicPlay) {
            try {
                if (!backMusic.isPlaying()) {
                    backMusic.start();
                }
            } catch (IllegalStateException e) {
                LogUtils.w("PvMActivity", "backMusic 状态异常，资源可能已释放: " + e.getMessage());
                backMusic = null;
            }
        }
        // 重新初始化时间更新线程
        initTimeUpdateExecutor();
        
        // 安全重置相机/图片选择器标志（防止状态卡住）
        if (photoCaptureManager != null) {
            photoCaptureManager.resetFlagsOnResume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        final PikafishAI aiToClose = pikafishAI;
        final PvMActivityAI aiManagerToShutdown = aiManager;

        // 关闭时间更新线程（主线程避免等待）
        if (timeUpdateExecutor != null) {
            timeUpdateExecutor.shutdownNow();
        }
        if (evaluationExecutor != null) {
            evaluationExecutor.shutdownNow();
        }

        // 释放 MediaPlayer 资源
        releaseMediaPlayers();

        // 将可能阻塞的关闭逻辑放到后台，避免destroy阶段卡顿
        new Thread(() -> {
            long cleanupStartMs = System.currentTimeMillis();
            if (aiToClose != null) {
                aiToClose.close();
            }
            if (aiManagerToShutdown != null) {
                aiManagerToShutdown.shutdown();
            }
            LogUtils.i("Perf", "onDestroy background cleanup cost=" + (System.currentTimeMillis() - cleanupStartMs) + "ms");
        }, "pvm-destroy-cleanup").start();

        // 清理静态引用
        if (weakInstance != null && weakInstance.get() == this) {
            weakInstance.clear();
            weakInstance = null;
        }
    }

    private void releaseMediaPlayers() {
        GameResourceManager.getInstance().resetBackMusic();
        backMusic = null;
    }
}