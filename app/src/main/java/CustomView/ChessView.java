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

    public ChessInfo chessInfo;

    /** 棋盘视图是否翻转（仅显示层，不动任何数据/算法） */
    public volatile boolean boardFlipped = false;

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
            // 加载棋盘图片并检查是否成功
            ChessBoard = decodeSampledBitmapFromResource(getResources(), R.drawable.chessboard, 768, 909);
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
        if (boardFlipped) {
            canvas.rotate(180, Board_width / 2f, Board_height / 2f);
        }

        canvas.drawColor(Color.WHITE);
        // 添加空指针检查，确保 ChessBoard 不为 null 时才绘制
        if (ChessBoard != null && cSrcRect != null && cDesRect != null) {
            // 绘制棋盘图片
            canvas.drawBitmap(ChessBoard, cSrcRect, cDesRect, null);
            // 添加日志，检查绘制参数
            LogUtils.i("ChessView", "Drawing chessboard: cSrcRect=" + cSrcRect.toString() + ", cDesRect=" + cDesRect.toString());
        } else {
            // 当棋盘图片加载失败时，绘制一个简单的棋盘网格
            drawChessboardGrid(canvas);
            LogUtils.i("ChessView", "Drawing chessboard grid instead of bitmap");
        }
        
        // 绘制传统坐标
        drawTraditionalCoordinates(canvas);
        
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
                            // 红方整体微调上移常量（设计单位，正值表示向上）
                            final int RED_UP = 6;
                            int up = (chessInfo.piece[i][j] >= 8) ? RED_UP : 0;
                            // 增大棋子尺寸：86x86，超出格子边界，让棋子更大更饱满
                            pDesRect = new Rect(Scale(j * 85 + 7), Scale(drawY * 85 + 45 - up), Scale(j * 85 + 93), Scale(drawY * 85 + 131 - up));
                            if (chessInfo.piece[i][j] <= 7) {
                                int num = chessInfo.piece[i][j] - 1;
                                if (BP != null && num >= 0 && num < BP.length && BP[num] != null) {
                                    if (boardFlipped) {
                                        canvas.save();
                                        canvas.rotate(180, Scale(j * 85 + 50), Scale(drawY * 85 + 80));
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
                                        canvas.rotate(180, Scale(j * 85 + 50), Scale(drawY * 85 + 88 - up));
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

        // 检查 chessInfo.Select 是否为 null
        if (chessInfo.Select != null && chessInfo.Select.length >= 2) {
            int drawX = chessInfo.Select[0], drawY = chessInfo.Select[1];
            if (drawX >= 0 && drawY >= 0 && drawY < 10 && drawX < 9 && chessInfo.piece != null && chessInfo.piece[drawY] != null && chessInfo.piece[drawY][drawX] > 0) {
                int piece = chessInfo.piece[drawY][drawX];
                boolean isRedPiece = piece >= 8 && piece <= 14;
                
                // 反转y坐标，确保选中效果显示在正确的位置
                int displayY = 9 - drawY;
                // 红方选中框同步上移 RED_UP，黑方不动
                int selUp = isRedPiece ? 6 : 0;
                // 绘制选中效果，无论当前是哪个玩家的回合
                if (isRedPiece && R_box != null) {
                    pSrcRect = new Rect(0, 0, R_box.getWidth(), R_box.getHeight());
                    pDesRect = new Rect(Scale(drawX * 85 + 7), Scale(displayY * 85 + 45 - selUp), Scale(drawX * 85 + 93), Scale(displayY * 85 + 131 - selUp));
                    if (boardFlipped) {
                        canvas.save();
                        canvas.rotate(180, Scale(drawX * 85 + 50), Scale(displayY * 85 + 88 - selUp));
                        canvas.drawBitmap(R_box, pSrcRect, pDesRect, null);
                        canvas.restore();
                    } else {
                        canvas.drawBitmap(R_box, pSrcRect, pDesRect, null);
                    }
                } else if (B_box != null) {
                    pSrcRect = new Rect(0, 0, B_box.getWidth(), B_box.getHeight());
                    pDesRect = new Rect(Scale(drawX * 85 + 7), Scale(displayY * 85 + 45), Scale(drawX * 85 + 93), Scale(displayY * 85 + 131));
                    if (boardFlipped) {
                        canvas.save();
                        canvas.rotate(180, Scale(drawX * 85 + 50), Scale(displayY * 85 + 88));
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
                        // 红方可移动提示同步上移 RED_UP，黑方不动
                        int hintUp = (chessInfo.piece[y][x] >= 8) ? 6 : 0;
                        if (Pot != null) {
                            pSrcRect = new Rect(0, 0, Pot.getWidth(), Pot.getHeight());
                            pDesRect = new Rect(Scale(x * 85 + 7), Scale(displayPosY * 85 + 45 - hintUp), Scale(x * 85 + 93), Scale(displayPosY * 85 + 131 - hintUp));
                            if (boardFlipped) {
                                canvas.save();
                                canvas.rotate(180, Scale(x * 85 + 50), Scale(displayPosY * 85 + 88 - hintUp));
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

            // 使用增大后的棋子尺寸 86x86 绘制走棋轨迹；红方轨迹同步上移 RED_UP，黑方不动
            int curUp = (real_curY >= 0 && real_curY < 10 && chessInfo.piece != null && chessInfo.piece[real_curY] != null && chessInfo.piece[real_curY][real_curX] >= 8) ? 6 : 0;
            int preUp = (chessInfo.prePos.y >= 0 && chessInfo.prePos.y < 10 && chessInfo.piece != null && chessInfo.piece[chessInfo.prePos.y] != null && chessInfo.piece[chessInfo.prePos.y][chessInfo.prePos.x] >= 8) ? 6 : 0;
            pDesRect = new Rect(Scale(draw_curX * 85 + 7), Scale(draw_curY * 85 + 45 - curUp), Scale(draw_curX * 85 + 93), Scale(draw_curY * 85 + 131 - curUp));
            tmpRect = new Rect(Scale(draw_preX * 85 + 7), Scale(draw_preY * 85 + 45 - preUp), Scale(draw_preX * 85 + 93), Scale(draw_preY * 85 + 131 - preUp));

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
                
                int fromUp = isRedMove ? 6 : 0;
                int toUp = isRedMove ? 6 : 0;
                int fromCenterX = Scale(fromX * 85 + 50);
                int fromCenterY = Scale(fromY * 85 + 88 - fromUp);
                int toCenterX = Scale(toX * 85 + 50);
                int toCenterY = Scale(toY * 85 + 88 - toUp);
                
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



    public int Scale(int x) {
        return x * Board_width / 768;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        Board_width = MeasureSpec.getSize(widthMeasureSpec);
        Board_height = Board_width * 909 / 750;

        // 添加空指针检查，确保 ChessBoard 不为 null
        if (ChessBoard != null) {
            cSrcRect = new Rect(0, 0, ChessBoard.getWidth(), ChessBoard.getHeight());
        } else {
            cSrcRect = new Rect(0, 0, Board_width, Board_height);
        }
        // 棋盘背景随棋子整体同步偏移：右移 8、下移 8（设计单位），使打印的网格线与棋子/提示点对齐
        // 同时把 View 测量尺寸也相应扩大（off），避免棋盘整体偏移后出现右/下裁剪
        int off = Scale(8);
        int viewW = Board_width + off;
        int viewH = viewW * 909 / 750;
        cDesRect = new Rect(0, 0, viewW, viewH);

        // 摆棋UI现在是浮动的，不需要额外增加View高度（尺寸已含偏移量）
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
        if (!boardFlipped || Board_width <= 0 || Board_height <= 0) {
            return event;
        }
        return MotionEvent.obtain(event.getDownTime(), event.getEventTime(),
                event.getAction(),
                Board_width - event.getX(),
                Board_height - event.getY(),
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
        gridPaint.setStrokeWidth(2);
        gridPaint.setAntiAlias(true);
        
        // 绘制棋盘边框
        int padding = 30;
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
        palacePaint.setStrokeWidth(2);
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
        
        Paint coordPaint = new Paint();
        coordPaint.setColor(Color.BLACK);
        coordPaint.setTextSize(Scale(20));
        coordPaint.setTextAlign(Paint.Align.CENTER);
        coordPaint.setAntiAlias(true);
        coordPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); // 设置粗体
        
        Paint redCoordPaint = new Paint(coordPaint);
        redCoordPaint.setColor(Color.RED); // 红方坐标用红色
        
        // 红方横坐标（从右到左：一、二、三、四、五、六、七、八、九）
        String[] redCoords = {"九", "八", "七", "六", "五", "四", "三", "二", "一"};
        // 黑方横坐标（从左到右：1、2、3、4、5、6、7、8、9）
        String[] blackCoords = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};
        
        // 绘制红方横坐标（底部）
        for (int i = 0; i < 9; i++) {
            int x = Scale(i * 85 + 50); // 中心点，与棋子/提示线对齐（整体右移8）
            int y = Scale(9 * 85 + 144); // 底部，随棋子整体下移 8 再上移 4
            canvas.drawText(redCoords[i], x, y, redCoordPaint);
        }
        
        // 绘制黑方横坐标（顶部，向下调整）
        for (int i = 0; i < 9; i++) {
            int x = Scale(i * 85 + 50); // 中心点，与棋子/提示线对齐
            int y = Scale(40); // 顶部，向下调整
            canvas.drawText(blackCoords[i], x, y, coordPaint);
        }
    }
}