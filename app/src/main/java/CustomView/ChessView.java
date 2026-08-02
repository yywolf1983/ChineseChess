package CustomView;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import Utils.LogUtils;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import top.nones.chessgame.R;

import java.util.Iterator;

import ChessMove.Rule;
import Info.ChessInfo;
import Info.Pos;

/**
 * Created by 77304 on 2021/4/5.
 */

public class ChessView extends SurfaceView implements SurfaceHolder.Callback {
    public Paint paint;

    public Bitmap ChessBoard;
    public Bitmap B_box, R_box, Pot;
    public Bitmap[] RP = new Bitmap[7];
    public Bitmap[] BP = new Bitmap[7];

    public Rect cSrcRect, cDesRect;

    public int Board_width, Board_height;

    /** 棋盘源图真实尺寸（chessboard.png 为 755×938，比例必须与此一致否则图片被纵向压缩） */
    private static final int BOARD_SRC_W = 755;
    private static final int BOARD_SRC_H = 938;

    /** View 实测尺寸（含 off 偏移），供翻转旋转中心与触摸映射使用，避免翻转后错位 */
    private int viewMeasuredW = 0, viewMeasuredH = 0;

    public ChessInfo chessInfo;

    /** 棋盘视图是否翻转（仅显示层，不动任何数据/算法） */
    public volatile boolean boardFlipped = false;

    /** 调试开关：为每个棋子绘制十字中心延长线，用于核对棋子是否落在中心点（用完请置 false） */
    public boolean drawPieceCrosshair = true;

    /** 调试开关：在棋盘图片上绘制整盘网格参考线（9 列×10 行中心点），用于核对棋盘图片网格是否准确 */
    public boolean drawBoardReference = true;

    /** 是否处于模拟行棋演示中（开启后绘制琥珀色边框 + 棋盘正中"模拟中"角标以区分真实对局） */
    public boolean isSimulating = false;

    /** "模拟中"角标的呼吸/点循环动画：仅在 isSimulating 期间自调度重绘 */
    private long simStartTime = 0;
    private final android.os.Handler simAnimHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable simAnimRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isSimulating) return;
            requestDraw();
            simAnimHandler.postDelayed(this, 120);
        }
    };

    /** 设置模拟态；进入时启动角标动画循环，退出时停止并收尾重绘一次 */
    public void setSimulating(boolean simulating) {
        if (this.isSimulating == simulating) return;
        this.isSimulating = simulating;
        if (simulating) {
            simStartTime = System.currentTimeMillis();
            simAnimHandler.removeCallbacks(simAnimRunnable);
            simAnimHandler.post(simAnimRunnable);
        } else {
            simAnimHandler.removeCallbacks(simAnimRunnable);
            requestDraw();
        }
    }

    /** 当前模拟角标脉动系数（约 0.56~1.0 呼吸），供边框与角标共用 */
    private float simPulse() {
        long elapsed = simStartTime > 0 ? System.currentTimeMillis() - simStartTime : 0;
        return (float) (0.78f + 0.22f * Math.sin(elapsed / 1200.0 * Math.PI * 2));
    }




    public ChessView(Context context, ChessInfo chessInfo) {
        super(context);
        this.chessInfo = chessInfo;
        getHolder().addCallback(this);
        init();
    }
    
    // 设置ChessInfo对象
    public void setChessInfo(ChessInfo chessInfo) {
        this.chessInfo = chessInfo;
    }

    public void init() {
        try {
            // 加载棋盘图片并检查是否成功。
            // 这里先用 768 宽兜底做一次基础解码；真正按屏幕实际宽度“只解码所需尺寸”的重载，
            // 会在 onMeasure 中根据 Board_width 完成（并回收此兜底图），以避免窄屏过度解码浪费内存。
            ChessBoard = decodeSampledBitmapFromResource(getResources(), R.drawable.chessboard, 768, BOARD_SRC_H);
            if (ChessBoard == null) {
                LogUtils.e("ChessView", "Failed to load chessboard image");
            } else {
                LogUtils.i("ChessView", "Successfully loaded chessboard image: " + ChessBoard.getWidth() + "x" + ChessBoard.getHeight());
            }

            B_box = decodeSampledBitmapFromResource(getResources(), R.drawable.b_box, 100, 100);
            R_box = decodeSampledBitmapFromResource(getResources(), R.drawable.r_box, 100, 100);
            Pot = decodeSampledBitmapFromResource(getResources(), R.drawable.pot, 100, 100);

            RP[0] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_shuai, 100, 100);
            RP[1] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_shi, 100, 100);
            RP[2] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_xiang, 100, 100);
            RP[3] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_ma, 100, 100);
            RP[4] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_ju, 100, 100);
            RP[5] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_pao, 100, 100);
            RP[6] = decodeSampledBitmapFromResource(getResources(), R.drawable.r_bing, 100, 100);

            BP[0] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_jiang, 100, 100);
            BP[1] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_shi, 100, 100);
            BP[2] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_xiang, 100, 100);
            BP[3] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_ma, 100, 100);
            BP[4] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_ju, 100, 100);
            BP[5] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_pao, 100, 100);
            BP[6] = decodeSampledBitmapFromResource(getResources(), R.drawable.b_zu, 100, 100);
        } catch (Exception e) {
            LogUtils.e("ChessView", "Error loading images: " + e.getMessage());
            // 确保即使图片加载失败，应用也能继续运行
        }
    }

    // 优化图片加载，减少内存使用
    private android.graphics.Bitmap decodeSampledBitmapFromResource(android.content.res.Resources res, int resId, int reqWidth, int reqHeight) {
        // 首先获取图片的边界信息，而不加载整个图片
        final android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeResource(res, resId, options);

        // 计算采样率
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // 使用计算出的采样率加载图片
        options.inJustDecodeBounds = false;
        // 内存/质量优化：
        //  - inPreferredConfig = RGB_565：棋盘与棋子均为不透明图，565 比默认 ARGB_8888 省一半内存，且无 alpha 通道需求。
        //  - inScaled = false：关闭按设备密度自动放大，避免 drawable 在 hdpi/xxhdpi 设备上被解码成更大尺寸、额外占内存；
        //    绘制时我们再按需缩放。
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inScaled = false;
        // 关闭 dither，565 下无意义且省一点开销
        options.inDither = false;
        return android.graphics.BitmapFactory.decodeResource(res, resId, options);
    }

    // 计算图片采样率
    private int calculateInSampleSize(android.graphics.BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // 原始图片的宽高
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // 计算最大的采样率，使得采样后的图片宽高仍大于等于需求的宽高
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public void Draw(Canvas canvas) {
        canvas.save();
        // 棋盘翻转：Canvas 180° 旋转，仅显示层操作，不动任何数据
        // 旋转中心用 View 实测尺寸中心，保证翻转后内容原地居中、不跳动
        if (boardFlipped) {
            float cx = (viewMeasuredW > 0 ? viewMeasuredW : Board_width) / 2f;
            float cy = (viewMeasuredH > 0 ? viewMeasuredH : Board_height) / 2f;
            canvas.rotate(180, cx, cy);
        }

        // 不填充纯白底（避免 boardTop 上移产生的顶部/底部空白带呈现「白色空白」）。
        // 改为填充与木纹背景协调的深棕底色，使留白融入整体、观感上不再有白边，
        // 且棋盘 View 屏幕位置完全不变、反转原地旋转不移动。
        canvas.drawColor(0xFF2A1E14);
        // 添加空指针检查，确保 ChessBoard 不为 null 时才绘制
        if (ChessBoard != null) {
            // 等比例整图绘制：图片本身为 755×938，Scale() 基准同为源图宽 755，
            // cDesRect 宽高比 755:938 与源图一致，因此绝不会产生任何拉伸/压缩变形。
            canvas.drawBitmap(ChessBoard, cSrcRect, cDesRect, null);
        } else {
            // 当棋盘图片加载失败时，绘制一个简单的棋盘网格
            drawChessboardGrid(canvas);
            LogUtils.i("ChessView", "Drawing chessboard grid instead of bitmap");
        }

        // 添加空指针检查，确保 chessInfo 不为 null
        if (chessInfo == null) {
            return;
        }
        
        Rect pSrcRect, pDesRect;

        // 检查 chessInfo.piece 是否为 null
        if (chessInfo.piece != null) {
            for (int i = 0; i < chessInfo.piece.length; i++) {
                if (chessInfo.piece[i] != null) {
                    for (int j = 0; j < chessInfo.piece[i].length; j++) {
                        if (chessInfo.piece[i][j] > 0) {
                            // 反转y坐标，确保红棋在屏幕下方，黑棋在屏幕上方
                            int drawY = 9 - i;
                            // 红方整体微调偏移常量（设计单位，负值表示向下）
                            final int RED_UP = 0;
                            int up = (chessInfo.piece[i][j] >= 8) ? RED_UP : 0;
                            // 等比例：落子行对齐真实格线 BOARD_TOP + drawY*GRID，绘制 PIECE×PIECE 棋子
                            int cx = sy(j * GRID + HALF) - Scale(1) + drawOffX; // 所有棋子整体左移 1dp（随分辨率缩放）+ 居中偏移
                            int blackOff = (chessInfo.piece[i][j] <= 7) ? Scale(1) : 0; // 黑棋额外下移（随分辨率缩放）
                            int cy = sy(gridY(drawY, up)) + Scale(1) + blackOff + drawOffY; // 所有棋子下移（随分辨率缩放）+ 居中偏移
                            int ph = sy(PIECE_H);
                            pDesRect = new Rect(cx - ph, cy - ph, cx + ph, cy + ph);

                            if (chessInfo.piece[i][j] <= 7) {
                                int num = chessInfo.piece[i][j] - 1;
                                if (BP != null && num >= 0 && num < BP.length && BP[num] != null) {
                                    if (boardFlipped) {
                                        canvas.save();
                                        canvas.rotate(180, cx, cy);
                                        pSrcRect = new Rect(0, 0, BP[num].getWidth(), BP[num].getHeight());
                                        canvas.drawBitmap(BP[num], pSrcRect, pDesRect, null);
                                        canvas.restore();
                                    } else {
                                        pSrcRect = new Rect(0, 0, BP[num].getWidth(), BP[num].getHeight());
                                        canvas.drawBitmap(BP[num], pSrcRect, pDesRect, null);
                                    }
                                }
                            }
                            if (chessInfo.piece[i][j] >= 8) {
                                int num = chessInfo.piece[i][j] - 8;
                                if (RP != null && num >= 0 && num < RP.length && RP[num] != null) {
                                    if (boardFlipped) {
                                        canvas.save();
                                        canvas.rotate(180, cx, cy);
                                        pSrcRect = new Rect(0, 0, RP[num].getWidth(), RP[num].getHeight());
                                        canvas.drawBitmap(RP[num], pSrcRect, pDesRect, null);
                                        canvas.restore();
                                    } else {
                                        pSrcRect = new Rect(0, 0, RP[num].getWidth(), RP[num].getHeight());
                                        canvas.drawBitmap(RP[num], pSrcRect, pDesRect, null);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 绘制传统坐标（放在棋子之后，使坐标文字压在棋子边缘之上、靠近棋子且清晰可见）
        drawTraditionalCoordinates(canvas);

        // 检查 chessInfo.Select 是否为 null
        if (chessInfo.Select != null && chessInfo.Select.length >= 2) {
            int drawX = chessInfo.Select[0], drawY = chessInfo.Select[1];
            if (drawX >= 0 && drawY >= 0 && drawY < 10 && drawX < 9 && chessInfo.piece != null && chessInfo.piece[drawY] != null && chessInfo.piece[drawY][drawX] > 0) {
                int piece = chessInfo.piece[drawY][drawX];
                boolean isRedPiece = piece >= 8 && piece <= 14;
                
                // 反转y坐标，确保选中效果显示在正确的位置
                int displayY = 9 - drawY;
                // 选中框：红黑统一对齐，无额外偏移
                int selUp = 0;
                // 绘制选中效果，等比例格中心对齐
                int selCx = sy(drawX * GRID + HALF) - Scale(1) + drawOffX;
                int selCy = sy(gridY(displayY, selUp)) + drawOffY;
                int selPh = sy(PIECE_H);
                if (isRedPiece && R_box != null) {
                    pSrcRect = new Rect(0, 0, R_box.getWidth(), R_box.getHeight());
                    pDesRect = new Rect(selCx - selPh, selCy - selPh, selCx + selPh, selCy + selPh);
                    if (boardFlipped) {
                        canvas.save();
                        canvas.rotate(180, selCx, selCy);
                        canvas.drawBitmap(R_box, pSrcRect, pDesRect, null);
                        canvas.restore();
                    } else {
                        canvas.drawBitmap(R_box, pSrcRect, pDesRect, null);
                    }
                } else if (B_box != null) {
                    pSrcRect = new Rect(0, 0, B_box.getWidth(), B_box.getHeight());
                    pDesRect = new Rect(selCx - selPh, selCy - selPh, selCx + selPh, selCy + selPh);
                    if (boardFlipped) {
                        canvas.save();
                        canvas.rotate(180, selCx, selCy);
                        canvas.drawBitmap(B_box, pSrcRect, pDesRect, null);
                        canvas.restore();
                    } else {
                        canvas.drawBitmap(B_box, pSrcRect, pDesRect, null);
                    }
                }
                
                // 绘制可移动位置，使用增大后的棋子尺寸 86x86
                if (chessInfo.ret != null) {
                    Iterator<Pos> it = chessInfo.ret.iterator();
                    while (it.hasNext()) {
                        Pos pos = it.next();
                        int x = pos.x, y = pos.y;
                        // 反转y坐标，确保可移动位置显示在正确的位置
                        int displayPosY = 9 - y;
                        // 红方可移动提示点：复用棋子真实中心，确保落在棋子中心（与当前棋子位置精确对齐）
                        if (Pot != null) {
                            pSrcRect = new Rect(0, 0, Pot.getWidth(), Pot.getHeight());
                            int potCx = pieceCenterX(x);
                            int potCy = pieceCenterY(displayPosY, chessInfo.piece[y][x] <= 7);
                            int potPh = sy(PIECE_H);
                            pDesRect = new Rect(potCx - potPh, potCy - potPh, potCx + potPh, potCy + potPh);
                            if (boardFlipped) {
                                canvas.save();
                                canvas.rotate(180, potCx, potCy);
                                canvas.drawBitmap(Pot, pSrcRect, pDesRect, null);
                                canvas.restore();
                            } else {
                                canvas.drawBitmap(Pot, pSrcRect, pDesRect, null);
                            }
                        }
                    }
                }
            }
        }

        if (chessInfo.prePos != null && chessInfo.curPos != null && !chessInfo.prePos.equals(new Pos(-1, -1)) && !chessInfo.IsChecked) {
            // 直接使用原始坐标获取棋子颜色
            int real_curX = chessInfo.curPos.x;
            int real_curY = chessInfo.curPos.y;

            // 反转y坐标，确保走棋轨迹显示在正确的位置
            int draw_preX = chessInfo.prePos.x;
            int draw_preY = 9 - chessInfo.prePos.y;
            int draw_curX = chessInfo.curPos.x;
            int draw_curY = 9 - chessInfo.curPos.y;

            Rect tmpRect;

            // 走棋轨迹：复用棋子真实中心，确保高亮框精确对齐到棋子（含黑棋下移）
            boolean trailIsBlack = chessInfo.piece[real_curY][real_curX] <= 7;
            int curCx = pieceCenterX(draw_curX);
            int curCy = pieceCenterY(draw_curY, trailIsBlack);
            int preCx = pieceCenterX(draw_preX);
            int preCy = pieceCenterY(draw_preY, trailIsBlack);
            int traPh = sy(PIECE_H);
            pDesRect = new Rect(curCx - traPh, curCy - traPh, curCx + traPh, curCy + traPh);
            tmpRect = new Rect(preCx - traPh, preCy - traPh, preCx + traPh, preCy + traPh);

            if (real_curY >= 0 && real_curY < 10 && real_curX >= 0 && real_curX < 9 && chessInfo.piece != null && chessInfo.piece[real_curY] != null && chessInfo.piece[real_curY][real_curX] >= 1 && chessInfo.piece[real_curY][real_curX] <= 7) {
                if (B_box != null) {
                    pSrcRect = new Rect(0, 0, B_box.getWidth(), B_box.getHeight());
                    canvas.drawBitmap(B_box, pSrcRect, pDesRect, null);
                    canvas.drawBitmap(B_box, pSrcRect, tmpRect, null);
                    // 移动的棋子上面加一层透明度
                    Paint overlayPaint = new Paint();
                    overlayPaint.setColor(Color.argb(20, 0, 200, 255));
                    overlayPaint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(pDesRect, overlayPaint);
                }
            } else {
                if (R_box != null) {
                    pSrcRect = new Rect(0, 0, R_box.getWidth(), R_box.getHeight());
                    canvas.drawBitmap(R_box, pSrcRect, pDesRect, null);
                    canvas.drawBitmap(R_box, pSrcRect, tmpRect, null);
                    // 移动的棋子上面加一层透明度
                    Paint overlayPaint = new Paint();
                    overlayPaint.setColor(Color.argb(20, 255, 200, 0));
                    overlayPaint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(pDesRect, overlayPaint);
                }
            }
        }

        // 绘制多步支招提示线（在所有棋子之上）
        if (chessInfo.suggestMoves != null && !chessInfo.suggestMoves.isEmpty() && chessInfo.suggestMoveLabels != null) {
            Paint suggestPaint = new Paint();
            suggestPaint.setAntiAlias(true);
            suggestPaint.setTextAlign(Paint.Align.CENTER);
            
            int redColor = Color.rgb(220, 50, 50);
            int blackColor = Color.rgb(50, 80, 200);
            
            for (int i = 0; i < chessInfo.suggestMoves.size() && i < chessInfo.suggestMoveLabels.size(); i++) {
                ChessMove.Move move = chessInfo.suggestMoves.get(i);
                if (move == null || move.fromPos == null || move.toPos == null) continue;
                
                boolean isRedMove = false;
                if (chessInfo.suggestMovesIsRed != null && i < chessInfo.suggestMovesIsRed.size()) {
                    isRedMove = chessInfo.suggestMovesIsRed.get(i);
                }
                int stepColor = isRedMove ? redColor : blackColor;
                
                int fromX = move.fromPos.x;
                int fromY = 9 - move.fromPos.y;
                
                int toX = move.toPos.x;
                int toY = 9 - move.toPos.y;
                
                // 提示线端点：复用棋子真实中心（含黑棋额外下移），确保对齐到当前棋子位置
                boolean fromIsBlack = chessInfo.piece[move.fromPos.y][move.fromPos.x] <= 7;
                boolean toIsBlack = chessInfo.piece[move.toPos.y][move.toPos.x] <= 7;
                int fromCenterX = pieceCenterX(fromX);
                int fromCenterY = pieceCenterY(fromY, fromIsBlack);
                int toCenterX = pieceCenterX(toX);
                int toCenterY = pieceCenterY(toY, toIsBlack);
                
                String label = chessInfo.suggestMoveLabels.get(i);
                
                // 序号1（i==0，即将要走的一步）用粗实线突出；
                // 序号2 及之后（i>=1）用虚线，弱化后续变化线，形成层次。
                boolean isDashed = i >= 1;
                
                // 连线（美化箭头）
                suggestPaint.setColor(stepColor);
                suggestPaint.setStyle(Paint.Style.STROKE);
                suggestPaint.setStrokeCap(Paint.Cap.ROUND);
                suggestPaint.setStrokeJoin(Paint.Join.ROUND);
                if (isDashed) {
                    suggestPaint.setStrokeWidth(Scale(5));
                    suggestPaint.setPathEffect(new android.graphics.DashPathEffect(
                            new float[]{Scale(14), Scale(10)}, 0));
                    suggestPaint.setAlpha(200);
                } else {
                    suggestPaint.setStrokeWidth(Scale(8));
                    suggestPaint.setPathEffect(null);
                    suggestPaint.setAlpha(235);
                }
                drawArrow(canvas, fromCenterX, fromCenterY, toCenterX, toCenterY, suggestPaint);
                
                // 起点圆圈
                suggestPaint.setColor(stepColor);
                suggestPaint.setStyle(Paint.Style.FILL);
                suggestPaint.setPathEffect(null);
                suggestPaint.setAlpha(i == 0 ? 235 : 190);
                int circleRadius = Scale(i == 0 ? 20 : 16);
                canvas.drawCircle(fromCenterX, fromCenterY, circleRadius, suggestPaint);
                
                // 步数标签
                suggestPaint.setColor(Color.WHITE);
                suggestPaint.setTextSize(Scale(i == 0 ? 38 : 30));
                suggestPaint.setAlpha(255);
                suggestPaint.setFakeBoldText(true);
                canvas.drawText(label, fromCenterX, fromCenterY + Scale(i == 0 ? 11 : 9), suggestPaint);
            }
        }

        // 模拟行棋标识：琥珀色描边边框，明确区分演示与真实对局（暖色，区别于青色走子高亮/红蓝支招箭头）
        if (isSimulating) {
            float pulse = simPulse();
            Paint simBorder = new Paint();
            simBorder.setAntiAlias(true);
            simBorder.setStyle(Paint.Style.STROKE);
            simBorder.setColor(Color.argb((int) (140 + 115 * pulse), 232, 163, 61));
            simBorder.setStrokeWidth(Scale(8));
            int inset = Scale(4);
            android.graphics.RectF bRect = new android.graphics.RectF(inset, inset, Board_width - inset, Board_height - inset);
            canvas.drawRoundRect(bRect, Scale(16), Scale(16), simBorder);
        }

        canvas.restore();

        // 角标绘制在 restore 之后，位于棋盘正中（不受棋盘翻转影响）
        if (isSimulating) {
            float pulse = simPulse();
            // 文字："模拟中" + 循环点（0~3 个），每 500ms 变化一次
            long elapsed = simStartTime > 0 ? System.currentTimeMillis() - simStartTime : 0;
            int dotCount = (int) ((elapsed / 500) % 4);
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < dotCount; i++) dots.append('.');
            String simLabel = "模拟中" + dots.toString();
            int padX = Scale(18), padY = Scale(10);
            int ts = Scale(34);
            // 深墨蓝半透明胶囊 + 琥珀描边与文字：在暖色木盘上清晰可辨，且与控制面板同族、沉稳不刺眼
            android.graphics.Paint badgeBg = new android.graphics.Paint();
            badgeBg.setAntiAlias(true);
            badgeBg.setColor(Color.argb((int) (224 * (0.56f + 0.44f * pulse)), 30, 43, 58));
            android.graphics.Paint badgeStroke = new android.graphics.Paint();
            badgeStroke.setAntiAlias(true);
            badgeStroke.setStyle(Paint.Style.STROKE);
            badgeStroke.setStrokeWidth(Scale(2));
            badgeStroke.setColor(Color.argb((int) (255 * (0.4f + 0.6f * pulse)), 232, 163, 61));
            android.graphics.Paint labelP = new android.graphics.Paint();
            labelP.setAntiAlias(true);
            labelP.setColor(Color.argb(255, 240, 190, 110));
            labelP.setTextSize(ts);
            labelP.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            labelP.setTextAlign(android.graphics.Paint.Align.LEFT);
            float textW = labelP.measureText(simLabel);
            int bw = (int) (textW + 2 * padX);
            int bh = (int) (ts + 2 * padY);
            int bx = (Board_width - bw) / 2;
            int by = (Board_height - bh) / 2;
            android.graphics.RectF badgeRect = new android.graphics.RectF(bx, by, bx + bw, by + bh);
            canvas.drawRoundRect(badgeRect, Scale(10), Scale(10), badgeBg);
            canvas.drawRoundRect(badgeRect, Scale(10), Scale(10), badgeStroke);
            canvas.drawText(simLabel, bx + padX, by + bh - padY - Scale(3), labelP);
        }
    }



    // 等比例基准：棋盘源图 755×938，9 列
    // 实测源图真实横格线（10 条，对应 9 行落子交叉点）位于 y≈80,164,247,...,830，
    // 即顶部边框+空白带约 80px（顶框线 y≈11，第一排格线 y≈80），之后每格约 84px。
    // 落子行 r(0..9) 的格中心 y = boardTop + r*GRID，使棋子精确对齐背景格线，消除顶部空白。
    // boardTop 在 onMeasure 中按 Board_width 计算，可整体上下平移（整体上移 41px）。
    private static final float GRID = 84f;            // 每格设计单位（贴合源图真实格距 ≈84）
    private static final float HALF = 44f;            // 格中心偏移 = 44（起始列 x 坐标，不再等于 GRID/2）
    private float boardTop = 44f;                     // 第一排落子点 y 的基准（源图单位），运行时重算；起始行 y = boardTop + HALF = 88
    private static final float PIECE = 84f;           // 棋子边长设计单位（= GRID，占满整格）
    private static final float PIECE_H = PIECE / 2f;  // 棋子半边 = 42

    // 棋盘整体绘制缩放（所有格子/棋子/提示/坐标/背景图统一缩小，并在 View 中居中）。
    // 默认在 onMeasure 中按可用屏幕宽高自适应计算（尽量铺满且完整适应、不溢出），
    // 也可手动设固定值（如 0.5f）强制缩小。改此值即可整体缩放，不影响各元素之间的相对对齐。
    private float boardDrawScale = 1.0f;
    private int drawOffX = 0;                         // 缩小后棋盘在 View 内的水平居中偏移（屏幕像素）
    private int drawOffY = 0;                         // 缩小后棋盘在 View 内的垂直居中偏移（屏幕像素）

    // 落子行 r(0..9) 的格中心 y（源图单位）：对齐真实格线，并叠加额外上下偏移 up
    private float gridY(int r, int up) {
        return boardTop + r * GRID + HALF - up;
    }

    // 棋子真实中心的屏幕坐标（与棋子绘制完全一致）：
    //  X = 列中心 - 整体左移 1dp；Y = 行中心 + 整体下移 1dp（黑棋额外下移 1dp）。
    //  并叠加 boardDrawScale 缩放后的居中偏移 drawOffX/drawOffY，使整盘缩小并在 View 中居中。
    //  提示点 / 走棋轨迹框 / 支招提示线的端点均复用此函数，确保全部对齐到当前棋子位置。
    private int pieceCenterX(int displayX) {
        return sy(displayX * GRID + HALF) - Scale(1) + drawOffX;
    }
    private int pieceCenterY(int displayY, boolean isBlack) {
        int y = sy(gridY(displayY, 0)) + Scale(1) + drawOffY;
        return isBlack ? y + Scale(1) : y;
    }

    // 调试：在棋盘图片上绘制整盘网格参考线（纯格中心点，不含棋子偏移），颜色青色，
    // 用于与棋盘图片印刷的网格线、棋子十字线（品红）对比，核对棋盘图片是否准确。
    private void drawBoardReference(Canvas canvas) {
        Paint linePaint = new Paint();
        linePaint.setColor(Color.CYAN);
        linePaint.setStrokeWidth(Math.max(1, Scale(1)));
        linePaint.setAntiAlias(true);
        linePaint.setAlpha(180);

        Paint dotPaint = new Paint();
        dotPaint.setColor(Color.CYAN);
        dotPaint.setAntiAlias(true);

        int w = (viewMeasuredW > 0 ? viewMeasuredW : getWidth());
        int h = getHeight();

        // 9 条列参考线（displayX = 0..8）
        for (int cx = 0; cx < 9; cx++) {
            int x = pieceCenterX(cx);
            canvas.drawLine(x, 0, x, h, linePaint);
        }
        // 10 条行参考线（displayY = 0..9）
        for (int ry = 0; ry < 10; ry++) {
            int y = sy(gridY(ry, 0)) + Scale(1) + drawOffY; // 与棋子基础下移一致（不含黑棋额外下移）+ 居中偏移
            canvas.drawLine(0, y, w, y, linePaint);
        }
        // 交点小圆点
        int dotR = Math.max(1, Scale(2));
        for (int cx = 0; cx < 9; cx++) {
            int x = pieceCenterX(cx);
            for (int ry = 0; ry < 10; ry++) {
                int y = sy(gridY(ry, 0)) + Scale(1) + drawOffY;
                canvas.drawCircle(x, y, dotR, dotPaint);
            }
        }
    }

    // 暴露当前 boardTop 的屏幕像素值，供点击命中检测对齐
    public int getBoardTopScaled() {
        return sy(boardTop) + drawOffY;
    }

    // 暴露缩小后棋盘的居中偏移，供点击命中检测（getPos）对齐
    public int getDrawOffX() { return drawOffX; }
    public int getDrawOffY() { return drawOffY; }

    // 坐标换算：按 750 基准、boardDrawScale 缩放（Scale 内部已四舍五入），避免像素量化误差
    private int sy(float v) {
        return Scale(Math.round(v));
    }

    // 仅做缩放（不偏移）：返回源图单位 x 在缩小后的像素长度，叠加 drawOffX/Y 才是屏幕坐标
    public int Scale(int x) {
        // 统一以棋盘源图宽度 BOARD_SRC_W(755) 为基准（源图 755×938），并乘以 boardDrawScale 整体缩放，
        // 与背景 cDesRect 比例一致，避免横向压缩变形。
        // 使用浮点四舍五入，确保不同 Board_width 下缩放比例均匀一致（消除整数除法截断在不同分辨率下的不均匀）。
        return Math.round((float) x * (Board_width * boardDrawScale) / (float) BOARD_SRC_W);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        Board_width = MeasureSpec.getSize(widthMeasureSpec);
        // 高度按源图真实比例 755×938 计算（与绘制矩形比例一致，避免图片被纵向压缩）
        Board_height = Math.round(Board_width * (float) BOARD_SRC_H / BOARD_SRC_W);

        // 起始行坐标：gridY(0,0) = boardTop + HALF = 起始 y。用户指定起始 (44, 88)，
        // 即 boardTop = 88 - HALF(44) = 44，保证所有分辨率下比例一致（不随屏幕像素偏上下）。
        boardTop = 88f - HALF;

        // 按真实显示宽度按需重载棋盘图：只在尺寸变化（或尚未加载）时重新解码，
        // 保证棋盘图只解码“当前屏幕所需”的像素量，避免 init 时用退回值过度解码、浪费内存。
        int neededW = Board_width;
        int neededH = Board_height;
        if (ChessBoard == null || Math.abs(ChessBoard.getWidth() - neededW) > 2) {
            Bitmap old = ChessBoard;
            ChessBoard = decodeSampledBitmapFromResource(getResources(), R.drawable.chessboard, neededW, neededH);
            if (old != null && old != ChessBoard) {
                old.recycle(); // 释放上一张，避免内存堆积
            }
        }

        // 添加空指针检查，确保 ChessBoard 不为 null
        if (ChessBoard != null) {
            cSrcRect = new Rect(0, 0, ChessBoard.getWidth(), ChessBoard.getHeight());
        } else {
            cSrcRect = new Rect(0, 0, Board_width, Board_height);
        }
        // 测量宽度严格等于可用宽度 Board_width（= 父布局宽度），不再额外加 off，
        // 避免 View 比父布局宽导致 CENTER_HORIZONTAL 时整盘左移、左右棋子被裁切/超出屏幕。
        int viewW = Board_width;

        // 自适应：棋盘（源图 755×938）按可用屏幕宽高等比缩放，尽量铺满且完整适应屏幕、不溢出不裁切。
        // 当父布局高度充足时 boardDrawScale=1（铺满宽度）；高度不足（矮屏/横屏）时按高度约束自动缩小。
        int specH = MeasureSpec.getSize(heightMeasureSpec);
        if (specH > 0) {
            boardDrawScale = Math.min(1.0f, (specH * (float) BOARD_SRC_W) / ((float) BOARD_SRC_H * Board_width));
        } else {
            boardDrawScale = 1.0f; // 高度无约束（wrap_content）：按宽度铺满
        }

        // 背景按源图 755×938 比例，与 Scale(755 基准) 一致，等比例无压缩
        int fullH = Math.round(Board_width * (float) BOARD_SRC_H / BOARD_SRC_W);

        // 棋盘左右边距固定为 MARGIN_X（2px）：左距 2，绘制宽度 = 可用宽 - 2*边距，
        // 高度按源图 755×938 比例跟随，不居中（贴近左边缘）。矮屏高度约束时 drawW 可能更小，仍左对齐。
        final int MARGIN_X = 2;
        int maxDrawW = Math.max(1, viewW - 2 * MARGIN_X);
        int drawW = Math.min(Math.round(Board_width * boardDrawScale), maxDrawW);
        int drawH = Math.round(drawW * (float) BOARD_SRC_H / BOARD_SRC_W);
        drawOffX = MARGIN_X;
        drawOffY = 0;

        // 背景图片按 boardDrawScale 缩小，绘制在左对齐区域（整图等比例，无拉伸）
        cDesRect = new Rect(drawOffX, drawOffY, drawOffX + drawW, drawOffY + drawH);

        // View 高度 = 缩小后棋盘高度（紧凑，适应内容）
        int viewH = drawH;

        // 摆棋UI现在是浮动的，不需要额外增加View高度（尺寸已含偏移量）
        viewMeasuredW = viewW;
        viewMeasuredH = viewH;
        setMeasuredDimension(viewW, viewH);

        paint = new Paint();
        paint.setTextSize(Scale(57));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(1);
        paint.setAntiAlias(true);
        paint.setColor(Color.RED);
    }


    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // 当 Surface 创建时，立即绘制一次棋盘
        if (holder != null) {
            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                try {
                    Draw(canvas);
                    LogUtils.i("ChessView", "Surface created, drawing initial chessboard");
                } catch (Exception e) {
                    LogUtils.e("ChessView", "Error drawing on surface creation: " + e.getMessage());
                } finally {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // View 销毁时回收所有 bitmap，避免显存/内存泄漏（SurfaceView 不会自动回收这些手动加载的图）。
        recycleBitmaps();
    }

    // 统一回收本 View 加载的所有 bitmap，供 surfaceDestroyed / onDetachedFromWindow 调用
    private void recycleBitmaps() {
        if (ChessBoard != null && !ChessBoard.isRecycled()) { ChessBoard.recycle(); ChessBoard = null; }
        if (B_box != null && !B_box.isRecycled()) { B_box.recycle(); }
        if (R_box != null && !R_box.isRecycled()) { R_box.recycle(); }
        if (Pot != null && !Pot.isRecycled()) { Pot.recycle(); }
        for (Bitmap b : RP) { if (b != null && !b.isRecycled()) b.recycle(); }
        for (Bitmap b : BP) { if (b != null && !b.isRecycled()) b.recycle(); }
    }

    // 绘制方法，添加空指针检查
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (canvas != null && chessInfo != null) {
            Draw(canvas);
        }
    }

    // 外部调用的绘制方法
    public void requestDraw() {
        SurfaceHolder holder = getHolder();
        if (holder != null) {
            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                try {
                    Draw(canvas);
                } catch (Exception e) {
                    // 捕获异常，避免崩溃
                } finally {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }

    /**
     * 翻转棋盘视图（仅显示层，触摸坐标需用 transformTouchForFlip 转换）
     */
    public void toggleFlip() {
        boardFlipped = !boardFlipped;
        requestDraw();
    }

    /**
     * 当棋盘翻转时，将触摸坐标从屏幕空间映射到数据空间。
     * 调用者必须用返回的 MotionEvent 代替原始事件，并在使用后 recycle（如果非同一对象）。
     *
     * @param event 原始触摸事件（坐标在 ChessView 坐标系中）
     * @return 转换后的事件（坐标映射到未翻转时的数据坐标系）；未翻转时返回原事件
     */
    public MotionEvent transformTouchForFlip(MotionEvent event) {
        if (!boardFlipped || viewMeasuredW <= 0 || viewMeasuredH <= 0) {
            return event;
        }
        return MotionEvent.obtain(event.getDownTime(), event.getEventTime(),
                event.getAction(),
                viewMeasuredW - event.getX(),
                viewMeasuredH - event.getY(),
                event.getMetaState());
    }

    
    // 处理触摸事件，实现摆棋窗口的拖动功能
    // 绘制带箭头的线段（美化：圆头线帽 + 实心三角形箭头，箭头大小随线宽自适应）
    private void drawArrow(Canvas canvas, float fromX, float fromY, float toX, float toY, Paint paint) {
        double angle = Math.atan2(toY - fromY, toX - fromX);
        
        // 箭头尺寸随线宽变化，粗线（序号1）箭头更大更醒目
        float strokeW = paint.getStrokeWidth();
        float arrowLength = strokeW * 3.0f + Scale(16);
        float arrowAngle = (float) Math.PI / 7f; // 约 25.7 度，更修长美观
        
        // 线段只画到箭头根部，避免线头穿出三角形形成毛刺
        float baseX = (float) (toX - arrowLength * 0.72f * Math.cos(angle));
        float baseY = (float) (toY - arrowLength * 0.72f * Math.sin(angle));
        canvas.drawLine(fromX, fromY, baseX, baseY, paint);
        
        // 箭头三角形两翼坐标
        float x1 = (float) (toX - arrowLength * Math.cos(angle - arrowAngle));
        float y1 = (float) (toY - arrowLength * Math.sin(angle - arrowAngle));
        float x2 = (float) (toX - arrowLength * Math.cos(angle + arrowAngle));
        float y2 = (float) (toY - arrowLength * Math.sin(angle + arrowAngle));
        
        android.graphics.Path arrowPath = new android.graphics.Path();
        arrowPath.moveTo(toX, toY);
        arrowPath.lineTo(x1, y1);
        arrowPath.lineTo(x2, y2);
        arrowPath.close();
        
        // 保存并临时切换为实心填充（且清除虚线，让箭头始终为实心三角形）
        Paint.Style oldStyle = paint.getStyle();
        android.graphics.PathEffect oldEffect = paint.getPathEffect();
        paint.setStyle(Paint.Style.FILL);
        paint.setPathEffect(null);
        canvas.drawPath(arrowPath, paint);
        
        // 恢复调用方原有画笔状态
        paint.setStyle(oldStyle);
        paint.setPathEffect(oldEffect);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 让父类处理触摸事件
        return super.onTouchEvent(event);
    }

    // 绘制棋盘网格的方法，当棋盘图片加载失败时使用
    private void drawChessboardGrid(Canvas canvas) {
        if (canvas == null) return;
        
        Paint gridPaint = new Paint();
        gridPaint.setColor(Color.BLACK);
        gridPaint.setStrokeWidth(Scale(2));
        gridPaint.setAntiAlias(true);

        // 绘制棋盘边框（回退路径同样使用 750 基准，保证与正常显示一致）
        int padding = Scale(30);
        int gridSize = Math.min(Board_width - 2 * padding, Board_height - 2 * padding) / 9;
        int startX = padding;
        int startY = padding;
        int endX = startX + 8 * gridSize;
        int endY = startY + 9 * gridSize;
        
        canvas.drawRect(startX, startY, endX, endY, gridPaint);
        
        // 绘制横线
        for (int i = 0; i <= 9; i++) {
            int y = startY + i * gridSize;
            canvas.drawLine(startX, y, endX, y, gridPaint);
        }
        
        // 绘制竖线
        for (int i = 0; i <= 8; i++) {
            int x = startX + i * gridSize;
            canvas.drawLine(x, startY, x, endY, gridPaint);
        }
        
        // 绘制九宫格
        Paint palacePaint = new Paint();
        palacePaint.setColor(Color.BLACK);
        palacePaint.setStrokeWidth(Scale(2));
        palacePaint.setAntiAlias(true);
        
        // 红方九宫格
        int palaceStartY = startY;
        int palaceEndY = startY + 2 * gridSize;
        int palaceMidX = startX + 3 * gridSize;
        canvas.drawLine(startX + 3 * gridSize, palaceStartY, startX + 5 * gridSize, palaceEndY, palacePaint);
        canvas.drawLine(startX + 5 * gridSize, palaceStartY, startX + 3 * gridSize, palaceEndY, palacePaint);
        
        // 黑方九宫格
        palaceStartY = startY + 7 * gridSize;
        palaceEndY = startY + 9 * gridSize;
        canvas.drawLine(startX + 3 * gridSize, palaceStartY, startX + 5 * gridSize, palaceEndY, palacePaint);
        canvas.drawLine(startX + 5 * gridSize, palaceStartY, startX + 3 * gridSize, palaceEndY, palacePaint);
    }
    
    // 绘制传统坐标
    private void drawTraditionalCoordinates(Canvas canvas) {
        if (canvas == null) return;

        // 棋盘翻转时整张画布已旋转 180°，坐标位置随之镜像（红方坐标落到屏幕上方，黑方落下方），
        // 这正符合「反转坐标跟随」的需求。但文字会随之倒置，故整体单独反向旋转 180° 保持正立。
        // 为提升在深色木框上的可读性，每个坐标先绘制圆角底 pill，再绘制文字。
        Paint coordPaint = new Paint();
        coordPaint.setColor(Color.BLACK);
        coordPaint.setTextSize(Scale(20));
        coordPaint.setTextAlign(Paint.Align.CENTER);
        coordPaint.setAntiAlias(true);
        coordPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); // 设置粗体

        Paint redCoordPaint = new Paint(coordPaint);
        redCoordPaint.setColor(Color.RED); // 红方坐标用红色

        // 黑方坐标专用 paint：更粗（更大字号 + 粗体）
        Paint blackCoordPaint = new Paint(coordPaint);
        blackCoordPaint.setTextSize(Scale(26));

        // 红方横坐标（从右到左：一、二、三、四、五、六、七、八、九）
        String[] redCoords = {"九", "八", "七", "六", "五", "四", "三", "二", "一"};
        // 黑方横坐标（从左到右：1、2、3、4、5、6、7、8、9）
        String[] blackCoords = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};

        // 偏移量（距框线源图单位）：
        //   未反转时黑方(上方)=topOff=-4，红方(下方)=botOff=32
        //   翻转后整画布旋180°：黑坐标(绘制在顶框线上方)转到屏幕下方，红坐标(绘制在底框线下方)转到屏幕上方。
        //   故翻转后「屏幕上方=红」由 botOff 控制、「屏幕下方=黑」由 topOff 控制。
        //   按用户校准：反转后 上(红)=12、下(黑)=12。
        // 翻转后几何：屏幕下方=黑方(由 topOff 控制，字号大 Scale(26) 字身高，需更大偏移才不压棋子)，
        //           屏幕上方=红方(由 botOff 控制，字号小 Scale(20) 字身矮，用小偏移即贴近框线/棋子)。
        // 因字号不同，不能简单上下对称，黑方偏移需明显大于红方。
        int topOff = boardFlipped ? sy(28) : sy(-4);   // 翻转时控制屏幕下方(黑方)
        int botOff = boardFlipped ? sy(6) : sy(32);    // 翻转时控制屏幕上方(红方)
        int blackY = sy(boardTop) - topOff + drawOffY;                        // 黑坐标：顶框线上方空白带
        int redY = sy(boardTop + 9 * GRID + GRID) + botOff + drawOffY;        // 红坐标：底框线下方空白带

        // 绘制红方横坐标（从右到左：九、八、…、一）。下方坐标不要背景 pill（传 null）
        for (int i = 0; i < 9; i++) {
            int x = sy(i * GRID + HALF) - Scale(1) + drawOffX;
            drawCoordText(canvas, redCoords[i], x, redY, redCoordPaint, null);
        }

        // 绘制黑方横坐标（从左到右：1、2、…、9）。黑方坐标也不要背景 pill（传 null）
        for (int i = 0; i < 9; i++) {
            int x = sy(i * GRID + HALF) - Scale(1) + drawOffX;
            drawCoordText(canvas, blackCoords[i], x, blackY, blackCoordPaint, null);
        }
    }

    // 绘制单个坐标（圆角底 pill + 文字）；翻转时整体单独反向旋转 180° 保持正立，
    // 而文字的(x,y)位置仍由调用方按原始坐标系给出（整机翻转会自然把红方坐标镜像到上方）。
    private void drawCoordText(Canvas canvas, String text, int x, int y, Paint paint, Paint pillPaint) {
        if (boardFlipped) {
            canvas.save();
            canvas.rotate(180, x, y);
            drawCoordPillText(canvas, text, x, y, paint, pillPaint);
            canvas.restore();
        } else {
            drawCoordPillText(canvas, text, x, y, paint, pillPaint);
        }
    }

    // 在 (x,y) 处绘制「圆角底 + 居中文字」。y 为文字垂直中心。
    private void drawCoordPillText(Canvas canvas, String text, int x, int y, Paint paint, Paint pillPaint) {
        paint.setTextAlign(Paint.Align.CENTER);
        float textW = paint.measureText(text);
        float textSize = paint.getTextSize();
        float padX = Scale(5);
        float padY = Scale(3);
        float pillW = textW + padX * 2;
        float pillH = textSize + padY * 2;
        float left = x - pillW / 2f;
        float top = y - pillH / 2f;
        // 圆角半径取高度一半，呈现胶囊形；pillPaint 为 null 时不画背景（仅文字）
        if (pillPaint != null) {
            canvas.drawRoundRect(left, top, left + pillW, top + pillH, pillH / 2f, pillH / 2f, pillPaint);
        }
        // 文字垂直居中：基线 = y + (ascent+descent)/2（ascent 为负，descent 为正）
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = y + (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, x, baseline, paint);
    }
}